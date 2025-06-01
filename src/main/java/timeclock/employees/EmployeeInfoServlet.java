package timeclock.employees; // Ensure this matches your package

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches; // For getEmployeeTimecardInfo

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
        if (tenantIdObj instanceof Integer) {
            Integer id = (Integer) tenantIdObj;
            return (id > 0) ? id : null;
        }
        return null;
    }

    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }

    private String buildRedirectUrl(String page, int globalEid, String successMessage, String errorMessage) throws IOException {
        StringBuilder url = new StringBuilder(page);
        boolean firstParam = true;
        
        // Only add EID if it's relevant (e.g., redirecting to a specific employee view after an action)
        // For reset password, we might want to re-select the employee row
        if (globalEid > 0) { 
            url.append(firstParam ? "?" : "&").append("eid=").append(globalEid);
            firstParam = false;
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("error=").append(URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
        } else if (successMessage != null && !successMessage.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("message=").append(URLEncoder.encode(successMessage, StandardCharsets.UTF_8.name()));
        }
        logger.info("[EmployeeInfoServlet] Redirecting to: " + url.toString());
        return url.toString();
    }

    private void writeJsonResponse(HttpServletResponse response, String jsonString, int statusCode) throws IOException {
        if (response.isCommitted()) { logger.warning("Response already committed! JSON not sent: " + jsonString); return; }
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        try (PrintWriter out = response.getWriter()) { out.print(jsonString); out.flush(); }
        catch (IllegalStateException e) { logger.log(Level.SEVERE, "Failed to get writer for JSON: " + jsonString, e); }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String action = request.getParameter("action");
        String eidStr = request.getParameter("eid"); 
        int globalEid = 0; 
        String redirectPage = "employees.jsp";

        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            logger.warning("EmployeeInfoServlet action '" + action + "' fail: No TenantID in session.");
            if ("getScheduleInfo".equals(action)) {
                JSONObject jsonError = new JSONObject().put("success", false).put("message", "Session expired. Log in.");
                writeJsonResponse(response, jsonError.toString(), HttpServletResponse.SC_UNAUTHORIZED);
            } else {
                response.sendRedirect(buildRedirectUrl("login.jsp", -1, null, "Session error. Log in."));
            }
            return;
        }

        if ("resetPassword".equals(action) || "getScheduleInfo".equals(action)) {
            if (!isValid(eidStr)) {
                String errorMsg = "Missing Employee ID for action: " + action;
                if ("getScheduleInfo".equals(action)) {
                    JSONObject jsonError = new JSONObject().put("success", false).put("message", errorMsg);
                    writeJsonResponse(response, jsonError.toString(), HttpServletResponse.SC_BAD_REQUEST);
                } else {
                    response.sendRedirect(buildRedirectUrl(redirectPage, -1, null, errorMsg));
                }
                return;
            }
            try {
                globalEid = Integer.parseInt(eidStr);
                if (globalEid <= 0) throw new NumberFormatException("Global EID must be positive.");
            } catch (NumberFormatException e) {
                String errorMsg = "Invalid Employee ID format: " + eidStr;
                if ("getScheduleInfo".equals(action)) {
                    JSONObject jsonError = new JSONObject().put("success", false).put("message", errorMsg);
                    writeJsonResponse(response, jsonError.toString(), HttpServletResponse.SC_BAD_REQUEST);
                } else {
                    response.sendRedirect(buildRedirectUrl(redirectPage, -1, null, errorMsg));
                }
                return;
            }
        }

        switch (action != null ? action.trim() : "") {
            case "getScheduleInfo":
                handleGetScheduleInfo(request, response, tenantId, globalEid);
                break;
            case "resetPassword":
                handleResetPassword(request, response, tenantId, globalEid, redirectPage);
                break;
            default:
                logger.warning("Unknown GET action: " + action + " for T:" + tenantId);
                response.sendRedirect(buildRedirectUrl(redirectPage, (globalEid > 0 ? globalEid : -1), null, "Unknown action."));
                break;
        }
    }

    private void handleResetPassword(HttpServletRequest request, HttpServletResponse response, int tenantId, int globalEid, String redirectPage) throws IOException {
        String successMessage = null; 
        String errorMessage = null;
        logger.info("Attempting PIN reset for T:" + tenantId + ", Global EID:" + globalEid);

        String fetchEmployeeDetailsSql = "SELECT FIRST_NAME, LAST_NAME, TenantEmployeeNumber FROM EMPLOYEE_DATA WHERE EID = ? AND TenantID = ?";
        String updatePinSql = "UPDATE EMPLOYEE_DATA SET PasswordHash = ?, RequiresPasswordChange = TRUE WHERE EID = ? AND TenantID = ?";
        String defaultPin = "1234";
        String defaultPinHash = BCrypt.hashpw(defaultPin, BCrypt.gensalt(12));

        Connection con = null;
        PreparedStatement psFetch = null;
        PreparedStatement psUpdate = null;
        ResultSet rsFetch = null;

        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false); // Manage transaction

            String employeeName = "Employee";
            String tenantEmployeeIdNum = String.valueOf(globalEid); // Fallback to global EID if specific not found

            psFetch = con.prepareStatement(fetchEmployeeDetailsSql);
            psFetch.setInt(1, globalEid);
            psFetch.setInt(2, tenantId);
            rsFetch = psFetch.executeQuery();

            if (rsFetch.next()) {
                String fetchedFirstName = rsFetch.getString("FIRST_NAME");
                String fetchedLastName = rsFetch.getString("LAST_NAME");
                Integer fetchedTenantEmpNoObj = (Integer) rsFetch.getObject("TenantEmployeeNumber");

                if (isValid(fetchedFirstName)) {
                    employeeName = fetchedFirstName;
                    if (isValid(fetchedLastName)) {
                        employeeName += " " + fetchedLastName;
                    }
                }
                if (fetchedTenantEmpNoObj != null && fetchedTenantEmpNoObj > 0) {
                    tenantEmployeeIdNum = String.valueOf(fetchedTenantEmpNoObj);
                }

                psUpdate = con.prepareStatement(updatePinSql);
                psUpdate.setString(1, defaultPinHash);
                psUpdate.setInt(2, globalEid);
                psUpdate.setInt(3, tenantId);
                int rowsUpdated = psUpdate.executeUpdate();

                if (rowsUpdated > 0) {
                    con.commit(); // Commit successful update
                    successMessage = "PIN reset for " + employeeName + " (ID: " + tenantEmployeeIdNum + "). Default PIN is " + defaultPin + ".";
                    logger.info(successMessage + " T:" + tenantId);
                } else {
                    con.rollback(); // Rollback if update failed despite finding employee
                    errorMessage = "Failed to reset PIN (employee found but update failed).";
                    logger.warning("PIN reset failed (update affected 0 rows) for EID:" + globalEid + ", T:" + tenantId);
                }
            } else {
                con.rollback(); // Rollback as employee not found for this tenant
                errorMessage = "Failed to reset PIN. Employee not found for your company.";
                logger.warning("PIN reset failed (EID not found for tenant) for EID:" + globalEid + ", T:" + tenantId);
            }
        } catch (SQLException e) {
            if (con != null) { try { con.rollback(); } catch (SQLException se) { logger.log(Level.SEVERE, "Rollback failed", se); } }
            logger.log(Level.SEVERE, "DB error resetting PIN for EID:" + globalEid + ", T:" + tenantId, e);
            errorMessage = "Database error during PIN reset: " + e.getMessage();
        } catch (Exception e) { // Catch any other unexpected errors
            if (con != null) { try { con.rollback(); } catch (SQLException se) { logger.log(Level.SEVERE, "Rollback failed", se); } }
            logger.log(Level.SEVERE, "Unexpected error during PIN reset for EID:" + globalEid + ", T:" + tenantId, e);
            errorMessage = "Server error during PIN reset: " + e.getMessage();
        } finally {
            try { if (rsFetch != null) rsFetch.close(); } catch (SQLException e) { /* ignored */ }
            try { if (psFetch != null) psFetch.close(); } catch (SQLException e) { /* ignored */ }
            try { if (psUpdate != null) psUpdate.close(); } catch (SQLException e) { /* ignored */ }
            if (con != null) {
                try { con.setAutoCommit(true); } catch (SQLException e) { /* ignored */ } // Reset auto-commit
                try { con.close(); } catch (SQLException e) { /* ignored */ }
            }
        }
        response.sendRedirect(buildRedirectUrl(redirectPage, globalEid, successMessage, errorMessage));
    }

    private void handleGetScheduleInfo(HttpServletRequest request, HttpServletResponse response, int tenantId, int globalEid) throws IOException {
        JSONObject jsonResponse = new JSONObject();
        logger.info("Fetching schedule info for T:" + tenantId + ", Global EID:" + globalEid);
        try {
            Map<String, Object> info = ShowPunches.getEmployeeTimecardInfo(tenantId, globalEid);
            if (info != null) {
                jsonResponse.put("success", true);
                jsonResponse.put("employeeName", info.getOrDefault("employeeName", "N/A"));
                jsonResponse.put("scheduleName", info.getOrDefault("scheduleName", "N/A"));
                Time shiftStart = (Time) info.get("shiftStart");
                Time shiftEnd = (Time) info.get("shiftEnd");
                jsonResponse.put("shiftStart", shiftStart != null ? SCHEDULE_TIME_FORMAT.format(shiftStart) : "N/A");
                jsonResponse.put("shiftEnd", shiftEnd != null ? SCHEDULE_TIME_FORMAT.format(shiftEnd) : "N/A");
                jsonResponse.put("globalEid", globalEid);
                writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_OK);
            } else {
                logger.warning("No employee info found (getScheduleInfo) T:" + tenantId + ", EID:" + globalEid);
                jsonResponse.put("success", false);
                jsonResponse.put("message", "Employee information not found for your company or for this EID.");
                writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching schedule info (getScheduleInfo) T:" + tenantId + ", EID:" + globalEid, e);
            jsonResponse.put("success", false);
            jsonResponse.put("message", "Server error: " + e.getMessage());
            writeJsonResponse(response, jsonResponse.toString(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}