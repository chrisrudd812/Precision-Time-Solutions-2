package timeclock.reports;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;
import timeclock.Configuration;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/PrintTimecardsServlet")
public class PrintTimecardsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PrintTimecardsServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);

        Integer tenantId = null;
        String userPermissions = null;
        String errorMessage = null;
        String userTimeZoneId = null;

        if (session != null) {
            tenantId = (Integer) session.getAttribute("TenantID");
            userPermissions = (String) session.getAttribute("Permissions");
            userTimeZoneId = (String) session.getAttribute("userTimeZoneId");
        }

        if (tenantId == null || tenantId <= 0) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name()));
            return;
        }

        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
             response.sendRedirect(request.getContextPath() + "/timeclock.jsp?error=" + URLEncoder.encode("Access denied.", StandardCharsets.UTF_8.name()));
            return;
        }
        
        if (userTimeZoneId == null || userTimeZoneId.trim().isEmpty()) {
            userTimeZoneId = Configuration.getProperty(tenantId, "DefaultTimeZone", "America/Denver");
        }

        String filterType = request.getParameter("filterType");
        String filterValue = request.getParameter("filterValue");



        List<Map<String, Object>> printableTimecardsData = new ArrayList<>();
        List<Map<String, String>> allEmployees = new ArrayList<>();
        String pageTitle = "Time Card Report";
        String payPeriodMessageForPrint = "Pay Period Not Set";

        try {
            // Get all employees for the selector dropdown
            allEmployees = getAllEmployeesForSelector(tenantId);
            
            Map<String, LocalDate> periodInfo = ShowPunches.getCurrentPayPeriodInfo(tenantId);
            if (periodInfo == null || periodInfo.get("startDate") == null || periodInfo.get("endDate") == null) {
                throw new ServletException("Could not determine current pay period.");
            }
            LocalDate periodStartDate = periodInfo.get("startDate");
            LocalDate periodEndDate = periodInfo.get("endDate");
            DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);
            payPeriodMessageForPrint = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);
            
            if(session != null) {
                session.setAttribute("payPeriodMessageForPrint", payPeriodMessageForPrint);
            }

            List<Integer> employeeIdsToProcess = new ArrayList<>();
            
            // For single employee reports without a filterValue, don't process any employees yet
            // The dropdown will be shown for selection
            if ("single".equalsIgnoreCase(filterType) && (filterValue == null || filterValue.trim().isEmpty())) {
                // Leave employeeIdsToProcess empty - dropdown will be shown
            } else {
                employeeIdsToProcess = getEmployeeIdsForReport(tenantId, filterType, filterValue);
            }

            if (employeeIdsToProcess.isEmpty() && !("single".equalsIgnoreCase(filterType) && (filterValue == null || filterValue.trim().isEmpty()))) {
                errorMessage = "No active employees found for the selected filter criteria.";
            } else if (!employeeIdsToProcess.isEmpty()) {
                for (int eid : employeeIdsToProcess) {
                    Map<String, Object> employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId, eid);
                    if (employeeInfo == null) {
                        logger.warning("Could not retrieve full employeeInfo for EID: " + eid);
                        continue;
                    }

                    Map<String, Object> printableCard = new HashMap<>();
                    populatePrintableCardHeader(printableCard, employeeInfo, eid, tenantId);
                    
                    Map<String, Object> timecardPunchData = ShowPunches.getTimecardPunchData(tenantId, eid, periodStartDate, periodEndDate, employeeInfo, userTimeZoneId);

                    if (timecardPunchData != null) {
                        printableCard.put("punchesList", timecardPunchData.get("punches"));
                        populatePrintableCardTotals(printableCard, timecardPunchData);
                    }
                    printableTimecardsData.add(printableCard);
                }
            }
        } catch (Exception e) {
            errorMessage = "Error preparing report: " + e.getMessage();
            logger.log(Level.SEVERE, "Error in PrintTimecardsServlet for TenantID: " + tenantId, e);
        }

        request.setAttribute("printableTimecards", printableTimecardsData);
        request.setAttribute("allEmployees", allEmployees);
        // For single employee view, we need to find the TenantEmployeeNumber that matches the filterValue
        String selectedTenantEmployeeNumber = null;
        if ("single".equalsIgnoreCase(filterType) && filterValue != null) {
            selectedTenantEmployeeNumber = filterValue;
        }
        request.setAttribute("selectedEmployeeId", selectedTenantEmployeeNumber);
        request.setAttribute("pageTitle", pageTitle);
        request.setAttribute("payPeriodMessageForPrint", payPeriodMessageForPrint);
        if (errorMessage != null) {
            request.setAttribute("errorMessage", errorMessage);
        }
        RequestDispatcher dispatcher = request.getRequestDispatcher("/print_timecards_view.jsp");
        dispatcher.forward(request, response);
    }
    
    private List<Integer> getEmployeeIdsForReport(int tenantId, String filterType, String filterValue) throws SQLException {
        List<Integer> eids = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT EID FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE ");
        List<Object> params = new ArrayList<>();
        params.add(tenantId);

        if ("department".equalsIgnoreCase(filterType)) {
            sql.append("AND DEPT = ? ");
            params.add(filterValue);
        } else if ("schedule".equalsIgnoreCase(filterType)) {
            sql.append("AND SCHEDULE = ? ");
            params.add(filterValue);
        } else if ("supervisor".equalsIgnoreCase(filterType)) {
            sql.append("AND SUPERVISOR = ? ");
            params.add(filterValue);
        } else if ("single".equalsIgnoreCase(filterType)) {
            sql.append("AND TenantEmployeeNumber = ? ");
            params.add(Integer.parseInt(filterValue));
        }

        sql.append("ORDER BY LAST_NAME, FIRST_NAME");

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    eids.add(rs.getInt("EID"));
                }

            }
        }
        return eids;
    }
    
    private List<Map<String, String>> getAllEmployeesForSelector(int tenantId) throws SQLException {
        List<Map<String, String>> employees = new ArrayList<>();
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME FROM employee_data " +
                    "WHERE TenantID = ? AND ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME";
        
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> emp = new HashMap<>();
                    Integer tenantEmpNum = (Integer) rs.getObject("TenantEmployeeNumber");
                    int eid = rs.getInt("EID");
                    String dropdownValue = String.valueOf(tenantEmpNum != null ? tenantEmpNum : eid);
                    emp.put("eid", dropdownValue);
                    
                    String firstName = rs.getString("FIRST_NAME");
                    String lastName = rs.getString("LAST_NAME");
                    String fullName = (lastName != null ? lastName : "") + ", " + 
                        (firstName != null ? firstName : "");
                    
                    emp.put("name", fullName);
                    employees.add(emp);

                }
            }
        }
        return employees;
    }

    private void populatePrintableCardHeader(Map<String, Object> card, Map<String, Object> empInfo, int eid, int tenantId) {
        NumberFormat hoursFormatter = NumberFormat.getNumberInstance(Locale.US);
        hoursFormatter.setMinimumFractionDigits(2);
        hoursFormatter.setMaximumFractionDigits(2);

        Object tenantEmpNumObj = empInfo.get("tenantEmployeeNumber");
        String displayId = "#" + (tenantEmpNumObj != null ? timeclock.util.Helpers.formatEmployeeId(tenantId, (Integer)tenantEmpNumObj) : "EID:" + eid);
        card.put("displayEmployeeId", displayId);
        card.put("employeeName", empInfo.getOrDefault("employeeName", "N/A"));
        card.put("department", empInfo.getOrDefault("department", "N/A"));
        card.put("supervisor", empInfo.getOrDefault("supervisor", "N/A"));
        card.put("email", empInfo.getOrDefault("email", ""));
        String scheduleName = (String) empInfo.getOrDefault("scheduleName", "N/A");
        card.put("scheduleName", scheduleName);
        
        Time shiftStart = (Time) empInfo.get("shiftStart");
        Time shiftEnd = (Time) empInfo.get("shiftEnd");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);
        card.put("scheduleTimeStr", (shiftStart != null && shiftEnd != null) ? shiftStart.toLocalTime().format(tf) + " - " + shiftEnd.toLocalTime().format(tf) : "N/A");
        
        boolean autoLunch = (Boolean) empInfo.getOrDefault("autoLunch", false);

        Object hrsReqObj = empInfo.getOrDefault("hoursRequired", 0.0);
        double hrsReqDouble = (hrsReqObj instanceof Number) ? ((Number) hrsReqObj).doubleValue() : 0.0;
        String hrsReqStr = String.format(Locale.US, "%.2f", hrsReqDouble);
        
        String lunchLenStr = String.valueOf(empInfo.getOrDefault("lunchLength", 0));
        card.put("autoLunchStr", autoLunch ? "On (Req: " + hrsReqStr + "hr | Len: " + lunchLenStr + "m)" : "Off");
        
        card.put("formattedVacation", hoursFormatter.format(empInfo.getOrDefault("vacationHours", 0.0)));
        card.put("formattedSick", hoursFormatter.format(empInfo.getOrDefault("sickHours", 0.0)));
        card.put("formattedPersonal", hoursFormatter.format(empInfo.getOrDefault("personalHours", 0.0)));
        card.put("wageType", empInfo.getOrDefault("wageType", ""));
    }

    private void populatePrintableCardTotals(Map<String, Object> card, Map<String, Object> punchData) {
        card.put("punchTableError", punchData.get("error"));
        double reg = (Double) punchData.getOrDefault("totalRegularHours", 0.0);
        double ot = (Double) punchData.getOrDefault("totalOvertimeHours", 0.0);
        double dt = (Double) punchData.getOrDefault("totalDoubleTimeHours", 0.0);
        double hot = (Double) punchData.getOrDefault("totalHolidayOvertimeHours", 0.0);
        double dot = (Double) punchData.getOrDefault("totalDaysOffOvertimeHours", 0.0);
        card.put("totalRegularHours", reg);
        card.put("totalOvertimeHours", ot);
        card.put("totalDoubleTimeHours", dt);
        card.put("totalHolidayOvertimeHours", hot);
        card.put("totalDaysOffOvertimeHours", dot);
        card.put("periodTotalHours", Math.round((reg + ot + dt + hot + dot) * 100.0) / 100.0);
    }
}