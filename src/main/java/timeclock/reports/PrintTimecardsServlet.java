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
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
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
        Integer sessionEidForLog = null;
        String errorMessage = null;

        final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
        final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
        String userTimeZoneId = null;

        if (session != null) {
            Object tenantIdObjSession = session.getAttribute("TenantID");
            if (tenantIdObjSession instanceof Integer) {
                tenantId = (Integer) tenantIdObjSession;
            }
            userPermissions = (String) session.getAttribute("Permissions");
            Object eidObj = session.getAttribute("EID");
            if (eidObj instanceof Integer) {
                sessionEidForLog = (Integer) eidObj;
            }

            Object userTimeZoneIdObj = session.getAttribute("userTimeZoneId");
            if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
                userTimeZoneId = (String) userTimeZoneIdObj;
                logger.info("[PrintTimecardsServlet_TZ] Using userTimeZoneId from session: " + userTimeZoneId + " for EID: " + sessionEidForLog);
            }
        }

        if (tenantId == null || tenantId <= 0) {
            logger.warning("[PrintTimecardsServlet] Access denied: Invalid TenantID in session (" + tenantId + ")");
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired or invalid. Please log in.", StandardCharsets.UTF_8.name()));
            return;
        }

        if (!ShowPunches.isValid(userTimeZoneId)) {
            String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone");
            if (ShowPunches.isValid(tenantDefaultTz)) {
                userTimeZoneId = tenantDefaultTz;
                logger.info("[PrintTimecardsServlet_TZ] Using Tenant DefaultTimeZone from SETTINGS: " + userTimeZoneId + " for Tenant: " + tenantId);
            } else {
                userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
                logger.info("[PrintTimecardsServlet_TZ] Tenant DefaultTimeZone not set/invalid. Using application default: " + userTimeZoneId + " for Tenant: " + tenantId);
            }
        }

        if (!ShowPunches.isValid(userTimeZoneId)) {
            userTimeZoneId = PACIFIC_TIME_FALLBACK;
            logger.warning("[PrintTimecardsServlet_TZ] User/Tenant timezone not determined. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for Tenant: " + tenantId);
        }

        try {
            ZoneId.of(userTimeZoneId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PrintTimecardsServlet_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Tenant: " + tenantId, e);
            userTimeZoneId = "UTC";
            errorMessage = "A critical error occurred with timezone configuration. Report times may be shown in UTC.";
        }
        logger.info("[PrintTimecardsServlet_TZ] Final effective userTimeZoneId: " + userTimeZoneId + " for Tenant: " + tenantId);

        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            logger.warning("[PrintTimecardsServlet] Access Denied for user. Actual Permissions: '" + userPermissions + "' for TenantID: " + tenantId + ". Required: 'Administrator'.");
            response.sendRedirect(request.getContextPath() + "/timeclock.jsp?reportMode=false&error=" + URLEncoder.encode("Access denied for this report.", StandardCharsets.UTF_8.name()));
            return;
        }
        
        if (errorMessage != null && userTimeZoneId.equals("UTC")) {
            logger.warning("[PrintTimecardsServlet] Proceeding with report generation using UTC due to prior timezone error for T:" + tenantId);
        }

        String filterType = request.getParameter("filterType");
        String filterValueParam = request.getParameter("filterValue");
        String filterValue = null;
        if (filterValueParam != null && !filterValueParam.isEmpty()) {
            try {
                filterValue = URLDecoder.decode(filterValueParam, StandardCharsets.UTF_8.name());
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Failed to decode filterValue: " + filterValueParam, e);
                errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Invalid filter value provided.";
            }
        }

        logger.info("[PrintTimecardsServlet] Request for TenantID: " + tenantId + ", FilterType: " + filterType + ", FilterValue: " + filterValue + ", Final TimeZone for Processing: " + userTimeZoneId);

        List<Map<String, Object>> printableTimecardsData = new ArrayList<>();
        String pageTitle = "Time Card Report";
        String payPeriodMessageForPrint = "Pay Period Not Set";

        try {
            if (errorMessage == null) {
                Map<String, LocalDate> periodInfo = ShowPunches.getCurrentPayPeriodInfo(tenantId);
                if (periodInfo == null || periodInfo.get("startDate") == null || periodInfo.get("endDate") == null) {
                    throw new ServletException("Could not determine current pay period for TenantID: " + tenantId + ". Please check Pay Period settings.");
                }
                LocalDate periodStartDate = periodInfo.get("startDate");
                LocalDate periodEndDate = periodInfo.get("endDate");
                DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);
                payPeriodMessageForPrint = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);

                List<Map<String, Object>> employeesToProcess = new ArrayList<>();
                try (Connection con = DatabaseConnection.getConnection()) {
                    if (con == null) {
                        throw new SQLException("Failed to establish database connection for fetching employees.");
                    }
                    StringBuilder sqlEmployees = new StringBuilder("SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME FROM EMPLOYEE_DATA WHERE TenantID = ? AND ACTIVE = TRUE ");
                    List<Object> params = new ArrayList<>();
                    params.add(tenantId);

                    if (filterType == null || filterType.trim().isEmpty()) {
                        throw new ServletException("Report filter type not specified.");
                    }

                    switch (filterType.toLowerCase()) {
                        case "all":
                            pageTitle = "All Employees - Time Card Report";
                            break;
                        // ... other cases ...
                        case "department":
                            if (filterValue == null || filterValue.trim().isEmpty()) throw new ServletException("Department name not specified for report.");
                            pageTitle = "Department: " + filterValue + " - Time Card Report";
                            sqlEmployees.append("AND DEPT = ? ");
                            params.add(filterValue);
                            break;
                        case "schedule":
                            if (filterValue == null || filterValue.trim().isEmpty()) throw new ServletException("Schedule name not specified for report.");
                            pageTitle = "Schedule: " + filterValue + " - Time Card Report";
                            sqlEmployees.append("AND SCHEDULE = ? ");
                            params.add(filterValue);
                            break;
                        case "supervisor":
                            if (filterValue == null || filterValue.trim().isEmpty()) throw new ServletException("Supervisor name not specified for report.");
                            pageTitle = "Supervisor: " + filterValue + " - Time Card Report";
                            sqlEmployees.append("AND SUPERVISOR = ? ");
                            params.add(filterValue);
                            break;
                        default:
                            throw new ServletException("Invalid report filter type: " + filterType);
                    }
                    sqlEmployees.append("ORDER BY LAST_NAME, FIRST_NAME");

                    try (PreparedStatement psEmployees = con.prepareStatement(sqlEmployees.toString())) {
                        for (int i = 0; i < params.size(); i++) {
                            psEmployees.setObject(i + 1, params.get(i));
                        }
                        try (ResultSet rs = psEmployees.executeQuery()) {
                            while (rs.next()) {
                                Map<String, Object> emp = new HashMap<>();
                                emp.put("EID", rs.getInt("EID"));
                                employeesToProcess.add(emp);
                            }
                        }
                    }
                }

                if (employeesToProcess.isEmpty()) {
                    errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "No active employees found for the selected filter criteria.";
                    logger.info("[PrintTimecardsServlet] " + errorMessage + " for T:" + tenantId + ", Filter: " + filterType + "/" + filterValue);
                } else {
                    NumberFormat hoursFormatter = NumberFormat.getNumberInstance(Locale.US);
                    hoursFormatter.setMinimumFractionDigits(2);
                    hoursFormatter.setMaximumFractionDigits(2);

                    for (Map<String, Object> empBasicInfo : employeesToProcess) {
                        int eid = (Integer) empBasicInfo.get("EID");
                        Map<String, Object> printableCard = new HashMap<>();
                        Map<String, Object> employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId, eid);

                        if (employeeInfo != null) {
                            String displayEmployeeIdStr;
                            Object tenObj = employeeInfo.get("tenantEmployeeNumber");
                            Integer tenantEmpNoForCard = null;
                            if (tenObj instanceof Integer) {
                                tenantEmpNoForCard = (Integer) tenObj;
                            } else if (tenObj != null) {
                                try { tenantEmpNoForCard = Integer.parseInt(tenObj.toString()); } catch (NumberFormatException e) { /* ignore parsing error */ }
                            }

                            if (tenantEmpNoForCard != null && tenantEmpNoForCard > 0) {
                                displayEmployeeIdStr = "#" + tenantEmpNoForCard;
                            } else {
                                displayEmployeeIdStr = "EID: " + eid;
                            }
                            // *** ADDED LOGGING HERE ***
                            logger.info("[PrintTimecardsServlet] For EID " + eid + ": tenantEmpNoForCard from employeeInfo = " + tenObj + 
                                        ", parsed tenantEmpNoForCard = " + tenantEmpNoForCard + 
                                        ", final displayEmployeeIdStr = '" + displayEmployeeIdStr + "'");
                            
                            printableCard.put("displayEmployeeId", displayEmployeeIdStr);
                            printableCard.put("eid", eid); // Keep for other potential uses
                            printableCard.put("tenantEmployeeNumber", tenantEmpNoForCard); // Keep for other potential uses

                            printableCard.put("employeeName", employeeInfo.getOrDefault("employeeName", "N/A"));
                            printableCard.put("department", employeeInfo.getOrDefault("department", "N/A"));
                            printableCard.put("supervisor", employeeInfo.getOrDefault("supervisor", "N/A"));
                            String scheduleName = (String) employeeInfo.getOrDefault("scheduleName", "N/A");
                            printableCard.put("scheduleName", scheduleName);
                            Time shiftStart = (Time) employeeInfo.get("shiftStart");
                            Time shiftEnd = (Time) employeeInfo.get("shiftEnd");
                            DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a", Locale.US);
                            printableCard.put("scheduleTimeStr", (shiftStart != null && shiftEnd != null) ? shiftStart.toLocalTime().format(tf) + " - " + shiftEnd.toLocalTime().format(tf) : "N/A");
                            printableCard.put("isScheduleOpen", scheduleName == null || scheduleName.trim().isEmpty() || scheduleName.toLowerCase().contains("open") );
                            boolean autoLunch = (Boolean) employeeInfo.getOrDefault("autoLunch", false);
                            Object hrsReqObj = employeeInfo.get("hoursRequired");
                            Object lunchLenObj = employeeInfo.get("lunchLength");
                            String hrsReqStr = (hrsReqObj != null) ? String.format(Locale.US, "%.2f", Double.parseDouble(hrsReqObj.toString())) : "?";
                            String lunchLenStr = (lunchLenObj != null) ? lunchLenObj.toString() : "?";
                            printableCard.put("autoLunchStr", autoLunch ? "On (Req: " + hrsReqStr + "hr | Len: " + lunchLenStr + "m)" : "Off");
                            printableCard.put("formattedVacation", hoursFormatter.format(employeeInfo.getOrDefault("vacationHours", 0.0)));
                            printableCard.put("formattedSick", hoursFormatter.format(employeeInfo.getOrDefault("sickHours", 0.0)));
                            printableCard.put("formattedPersonal", hoursFormatter.format(employeeInfo.getOrDefault("personalHours", 0.0)));
                            printableCard.put("wageType", employeeInfo.getOrDefault("wageType",""));

                            Map<String, Object> timecardPunchData = ShowPunches.getTimecardPunchData(tenantId, eid, periodStartDate, periodEndDate, employeeInfo, userTimeZoneId);
                            if (timecardPunchData != null) {
                                printableCard.put("punchesList", timecardPunchData.get("punches"));
                                printableCard.put("punchTableError", timecardPunchData.get("error"));
                                double reg = (Double) timecardPunchData.getOrDefault("totalRegularHours", 0.0);
                                double ot = (Double) timecardPunchData.getOrDefault("totalOvertimeHours", 0.0);
                                double dt = (Double) timecardPunchData.getOrDefault("totalDoubleTimeHours", 0.0);
                                printableCard.put("totalRegularHours", reg);
                                printableCard.put("totalOvertimeHours", ot);
                                printableCard.put("totalDoubleTimeHours", dt);
                                printableCard.put("periodTotalHours", Math.round((reg + ot + dt) * 100.0) / 100.0);
                            } else {
                                printableCard.put("punchTableError", "Error processing punch data for EID " + eid + ".");
                                printableCard.put("periodTotalHours", 0.0); printableCard.put("totalRegularHours", 0.0); printableCard.put("totalOvertimeHours", 0.0); printableCard.put("totalDoubleTimeHours", 0.0);
                                printableCard.put("punchesList", new ArrayList<>());
                            }
                            printableTimecardsData.add(printableCard);
                        } else {
                            logger.warning("Could not retrieve full employeeInfo for EID: " + eid + " in TenantID: " + tenantId + " for printing.");
                            Map<String, Object> errorCard = new HashMap<>();
                            errorCard.put("employeeName", "Data Error for EID: " + eid);
                            errorCard.put("displayEmployeeId", "EID: " + eid);
                            errorCard.put("eid", eid);
                            errorCard.put("punchTableError", "Could not load complete employee data.");
                            errorCard.put("punchesList", new ArrayList<>());
                            printableTimecardsData.add(errorCard);
                        }
                    }
                }
            }
            request.setAttribute("printableTimecards", printableTimecardsData);
            request.setAttribute("pageTitle", pageTitle);
            request.setAttribute("payPeriodMessageForPrint", payPeriodMessageForPrint);
            if (errorMessage != null) {
                request.setAttribute("errorMessage", errorMessage);
            }
            RequestDispatcher dispatcher = request.getRequestDispatcher("/print_timecards_view.jsp");
            dispatcher.forward(request, response);
            return;

        } catch (ServletException | SQLException e) {
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "Error preparing report: " + e.getMessage();
            logger.log(Level.SEVERE, "Error in PrintTimecardsServlet for TenantID: " + tenantId, e);
        } catch (Exception e) {
            errorMessage = (errorMessage == null ? "" : errorMessage + " ") + "An unexpected server error occurred: " + e.getMessage();
            logger.log(Level.SEVERE, "Unexpected Error in PrintTimecardsServlet for TenantID: " + tenantId, e);
        }

        if (errorMessage != null) {
            response.setContentType("text/html");
            try (PrintWriter out = response.getWriter()) {
                out.println("<html><head><title>Report Error</title>");
                out.println("<style>body { font-family: Arial, sans-serif; margin: 20px; } .error { color: red; }</style>");
                out.println("</head><body>");
                out.println("<h1>Error Generating Report</h1>");
                out.println("<p class='error'>" + errorMessage.replace("<", "&lt;").replace(">", "&gt;") + "</p>");
                out.println("<p><a href='" + request.getContextPath() + "/reports.jsp'>Return to Reports</a> or <a href='" + request.getContextPath() + "/timeclock.jsp'>Return to Time Clock</a></p>");
                out.println("</body></html>");
            }
            logger.severe("Final error state for PrintTimecardsServlet T:" + tenantId + " - " + errorMessage);
        }
    }
}