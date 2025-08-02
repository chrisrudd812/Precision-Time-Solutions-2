package timeclock.employees;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject; 
import org.mindrot.jbcrypt.BCrypt; 

@WebServlet("/EmployeeInfoServlet")
public class EmployeeInfoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(EmployeeInfoServlet.class.getName());
    private static final SimpleDateFormat SCHEDULE_TIME_FORMAT = new SimpleDateFormat("hh:mm a");

    private Integer getTenantIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object tenantIdObj = session.getAttribute("TenantID");
        return (tenantIdObj instanceof Integer) ? (Integer) tenantIdObj : null;
    }

    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }

    private void writeJsonResponse(HttpServletResponse response, String jsonString, int statusCode) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        try (PrintWriter out = response.getWriter()) {
            out.print(jsonString);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        logger.info("EmployeeInfoServlet received GET action: " + action);
        String eidStr = request.getParameter("eid"); 
        int globalEid = 0; 
        
        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            logger.warning("Action '" + action + "' failed: No TenantID in session.");
            JSONObject jsonError = new JSONObject().put("success", false).put("error", "Your session has expired. Please log in again.");
            writeJsonResponse(response, jsonError.toString(), HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if ("resetPassword".equals(action) || "getScheduleInfo".equals(action) || "getEmployeeDetails".equals(action)) {
            if (!isValid(eidStr)) {
                logger.warning("Action '" + action + "' failed: Missing EID parameter.");
                writeJsonResponse(response, new JSONObject().put("success", false).put("error", "Missing Employee ID.").toString(), HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            try {
                globalEid = Integer.parseInt(eidStr);
            } catch (NumberFormatException e) {
                logger.warning("Action '" + action + "' failed: Invalid EID format '" + eidStr + "'.");
                writeJsonResponse(response, new JSONObject().put("success", false).put("error", "Invalid Employee ID format.").toString(), HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
        }

        switch (action != null ? action.trim() : "") {
            case "getScheduleInfo":
                handleGetScheduleInfo(request, response, tenantId, globalEid);
                break;
            case "getEmployeeDetails":
                handleGetEmployeeDetails(request, response, tenantId, globalEid);
                break;
            case "resetPassword":
                handleResetPassword(request, response, tenantId, globalEid);
                break;
            default:
                logger.warning("Unknown GET action: " + action);
                response.sendRedirect("employees.jsp?error=" + URLEncoder.encode("Unknown action.", StandardCharsets.UTF_8));
                break;
        }
    }

    private void handleGetEmployeeDetails(HttpServletRequest request, HttpServletResponse response, int tenantId, int globalEid) throws IOException {
        logger.info("HANDLE_GET_EMPLOYEE_DETAILS: Starting for EID=" + globalEid + ", TenantID=" + tenantId);
        JSONObject jsonResponse = new JSONObject();
        // ** THIS IS THE FIX: Changed WORK_SCHED to WORK_SCHEDULE **
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, SUPERVISOR, " +
                     "PERMISSIONS, WORK_SCHEDULE, ADDRESS, CITY, STATE, ZIP, PHONE, EMAIL, HIRE_DATE, " +
                     "WAGE_TYPE, WAGE, ACCRUAL_POLICY FROM EMPLOYEE_DATA WHERE EID = ? AND TenantID = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setInt(1, globalEid);
            pstmt.setInt(2, tenantId);
            logger.info("HANDLE_GET_EMPLOYEE_DETAILS: Executing query...");

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    logger.info("HANDLE_GET_EMPLOYEE_DETAILS: Employee record found. Building JSON object.");
                    JSONObject employee = new JSONObject();
                    employee.put("eid", rs.getInt("EID"));
                    employee.put("tenantemployeenumber", rs.getObject("TenantEmployeeNumber"));
                    employee.put("firstname", rs.getString("FIRST_NAME"));
                    employee.put("lastname", rs.getString("LAST_NAME"));
                    employee.put("dept", rs.getString("DEPT"));
                    employee.put("schedule", rs.getString("SCHEDULE"));
                    employee.put("supervisor", rs.getString("SUPERVISOR"));
                    employee.put("permissions", rs.getString("PERMISSIONS"));
                    // ** THIS IS THE FIX: Changed worksched key to use WORK_SCHEDULE from DB **
                    employee.put("worksched", rs.getString("WORK_SCHEDULE"));
                    employee.put("address", rs.getString("ADDRESS"));
                    employee.put("city", rs.getString("CITY"));
                    employee.put("state", rs.getString("STATE"));
                    employee.put("zip", rs.getString("ZIP"));
                    employee.put("phone", rs.getString("PHONE"));
                    employee.put("email", rs.getString("EMAIL"));
                    employee.put("hiredate", rs.getDate("HIRE_DATE"));
                    employee.put("wagetype", rs.getString("WAGE_TYPE"));
                    employee.put("wage", rs.getBigDecimal("WAGE"));
                    employee.put("accrualpolicy", rs.getString("ACCRUAL_POLICY"));
                    
                    jsonResponse.put("success", true);
                    jsonResponse.put("employee", employee);
                    logger.info("HANDLE_GET_EMPLOYEE_DETAILS: Successfully built JSON. Sending response.");
                    writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_OK);
                } else {
                    logger.warning("HANDLE_GET_EMPLOYEE_DETAILS: No employee record found for EID=" + globalEid + ", TenantID=" + tenantId);
                    jsonResponse.put("success", false).put("error", "Employee not found for this company.");
                    writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_NOT_FOUND);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "HANDLE_GET_EMPLOYEE_DETAILS: An exception occurred for EID: " + globalEid, e);
            jsonResponse.put("success", false).put("error", "A server error occurred while fetching employee data.");
            writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    
    private void handleResetPassword(HttpServletRequest request, HttpServletResponse response, int tenantId, int globalEid) throws IOException {
        String defaultPin = "1234";
        String defaultPinHash = BCrypt.hashpw(defaultPin, BCrypt.gensalt(12));
        String sql = "UPDATE EMPLOYEE_DATA SET PasswordHash = ?, RequiresPasswordChange = TRUE WHERE EID = ? AND TenantID = ?";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, defaultPinHash);
            ps.setInt(2, globalEid);
            ps.setInt(3, tenantId);
            int rows = ps.executeUpdate();
            if (rows > 0) {
                response.sendRedirect("employees.jsp?eid=" + globalEid + "&message=" + URLEncoder.encode("PIN has been reset to " + defaultPin, StandardCharsets.UTF_8));
            } else {
                response.sendRedirect("employees.jsp?eid=" + globalEid + "&error=" + URLEncoder.encode("Employee not found, could not reset PIN.", StandardCharsets.UTF_8));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error resetting PIN for EID " + globalEid, e);
            response.sendRedirect("employees.jsp?eid=" + globalEid + "&error=" + URLEncoder.encode("Database error during PIN reset.", StandardCharsets.UTF_8));
        }
    }
    private void handleGetScheduleInfo(HttpServletRequest request, HttpServletResponse response, int tenantId, int globalEid) throws IOException {
        JSONObject jsonResponse = new JSONObject();
        try {
            Map<String, Object> info = ShowPunches.getEmployeeTimecardInfo(tenantId, globalEid);
            if (info != null && !info.isEmpty()) {
                jsonResponse.put("success", true);
                jsonResponse.put("employeeName", info.get("employeeName"));
                jsonResponse.put("scheduleName", info.get("scheduleName"));
                Time shiftStart = (Time) info.get("shiftStart");
                Time shiftEnd = (Time) info.get("shiftEnd");
                jsonResponse.put("shiftStart", shiftStart != null ? SCHEDULE_TIME_FORMAT.format(shiftStart) : "N/A");
                jsonResponse.put("shiftEnd", shiftEnd != null ? SCHEDULE_TIME_FORMAT.format(shiftEnd) : "N/A");
                writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_OK);
            } else {
                jsonResponse.put("success", false).put("error", "Employee or schedule information not found.");
                writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in getScheduleInfo for EID:" + globalEid, e);
            jsonResponse.put("success", false).put("error", "A server error occurred.");
            writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}