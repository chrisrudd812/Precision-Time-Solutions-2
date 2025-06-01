package timeclock.accruals;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder; 
import java.nio.charset.StandardCharsets; 
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;


@WebServlet("/AccrualServlet")
public class AccrualServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AccrualServlet.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER_FROM_USER = DateTimeFormatter.ISO_LOCAL_DATE;

    private boolean isValid(String s) {
        return s != null && !s.trim().isEmpty();
    }
     private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        logger.info("--- AccrualServlet doPost Received ---");
        logParameters(request);

        HttpSession session = request.getSession(false);
        Integer tenantId = null;

        if (session != null) {
            Object tenantIdObj = session.getAttribute("TenantID");
            if (tenantIdObj instanceof Integer) {
                tenantId = (Integer) tenantIdObj;
            }
        }

        if (tenantId == null || tenantId <= 0) {
            logger.log(Level.WARNING, "Accrual action failed: Missing or invalid TenantID in session.");
            sendJsonResponse(response, false, "Session expired or invalid tenant. Please log in.", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String action = request.getParameter("action");
        // Updated to accept "adjustAccruedBalanceAction" from the consolidated form
        if (!"addAccruedHours".equals(action) && !"adjustAccruedBalanceAction".equals(action) ) {
             logger.warning("Invalid action received in AccrualServlet: " + action + " for TenantID: " + tenantId);
             sendJsonResponse(response, false, "Invalid action specified: " + escapeHtml(action), HttpServletResponse.SC_BAD_REQUEST);
             return;
        }

        try {
            handleAdjustAccruedHours(request, response, tenantId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unhandled exception in handleAdjustAccruedHours for TenantID: " + tenantId, e);
            sendJsonResponse(response, false, "An unexpected critical server error occurred: " + escapeHtml(e.getMessage()), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void handleAdjustAccruedHours(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        logger.info("Processing Accrual Adjustment for TenantID: " + tenantId);

        String hoursStr = request.getParameter("accrualHours");
        String accrualColumnName = request.getParameter("accrualTypeColumn"); // This is correct based on form
        boolean isGlobal = "true".equalsIgnoreCase(request.getParameter("isGlobalAdd"));
        String targetEidStr = request.getParameter("targetEmployeeId");
        // String accrualDateStr = request.getParameter("accrualDate"); // Removed as per user request
        String adjustmentOperation = request.getParameter("adjustmentOperation");


        List<Integer> targetEids = new ArrayList<>();
        String operationSummaryForSuccess = "";
        // LocalDate accrualDate = null; // Removed

        Connection con = null;

        try {
            // --- 1. Validation ---
            if (!isValid(hoursStr)) throw new IllegalArgumentException("Missing Hours value.");
            if (!isValid(accrualColumnName)) throw new IllegalArgumentException("Missing Accrual Type.");
            if (!isValid(adjustmentOperation)) throw new IllegalArgumentException("Missing adjustment operation type.");
            // if (!isValid(accrualDateStr)) throw new IllegalArgumentException("Missing Date of Accrual."); // Removed

            // try { accrualDate = LocalDate.parse(accrualDateStr.trim(), DATE_FORMATTER_FROM_USER); }
            // catch (DateTimeParseException e) { throw new IllegalArgumentException("Invalid Date of Accrual format. Expected yyyy-MM-dd.");}


            if (!("VACATION_HOURS".equals(accrualColumnName) || "SICK_HOURS".equals(accrualColumnName) || "PERSONAL_HOURS".equals(accrualColumnName))) {
                throw new IllegalArgumentException("Invalid Accrual Type specified: " + escapeHtml(accrualColumnName));
            }

            double hoursValue;
            try {
                hoursValue = Double.parseDouble(hoursStr.trim());
                if ("add".equals(adjustmentOperation) || "subtract".equals(adjustmentOperation)) {
                    if (hoursValue <= 0) throw new NumberFormatException("Hours to add/subtract must be a positive value.");
                } else if ("set".equals(adjustmentOperation)) {
                    if (hoursValue < 0) throw new NumberFormatException("Balance to set cannot be negative.");
                } else {
                    throw new IllegalArgumentException("Invalid adjustment operation: " + escapeHtml(adjustmentOperation));
                }
                hoursValue = Math.round(hoursValue * 100.0) / 100.0;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid value for Hours: '" + escapeHtml(hoursStr) + "'. " + e.getMessage());
            }

            logger.info("Accrual Adjustment Details: Type=" + accrualColumnName + ", Hours=" + hoursValue +
                        ", Operation=" + adjustmentOperation + ", Global=" + isGlobal + ", TargetEIDStr=" + targetEidStr);

            con = DatabaseConnection.getConnection();
            if (con == null) throw new SQLException("Failed to establish database connection.");
            con.setAutoCommit(false);

            // --- 2. Determine Target Employee(s) ---
            if (isGlobal) {
                String sqlGetActive = "SELECT EID FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE AND TenantID = ?";
                try (PreparedStatement ps = con.prepareStatement(sqlGetActive)) {
                    ps.setInt(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) targetEids.add(rs.getInt("EID"));
                    }
                }
                if (targetEids.isEmpty()) {
                    sendJsonResponse(response, true, "No active employees found. No hours adjusted.", HttpServletResponse.SC_OK);
                    try { if(con != null && !con.isClosed()) con.rollback(); } catch (SQLException ex) { logger.log(Level.WARNING, "Rollback failed.", ex); }
                    return;
                }
                String opPastTense = adjustmentOperation.equals("set") ? "Set" : adjustmentOperation + "ed";
                operationSummaryForSuccess = String.format(Locale.US, "Globally %s %.2f hours for %s for %d active employee(s).",
                                                            opPastTense, hoursValue, accrualColumnName.replace("_HOURS", "").replace("_", " "), targetEids.size());
            } else {
                if (!isValid(targetEidStr)) throw new IllegalArgumentException("Employee must be selected.");
                int targetEid;
                try {
                    targetEid = Integer.parseInt(targetEidStr.trim());
                    if (targetEid <= 0) throw new NumberFormatException("EID must be positive.");
                    if (!isEmployeeOfTenant(con, tenantId, targetEid)) throw new SecurityException("Selected employee does not belong to your company or is inactive.");
                    targetEids.add(targetEid);
                } catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid Employee ID: " + escapeHtml(targetEidStr)); }
                
                String displayIdentifier = "EID " + targetEids.get(0); // Default
                String empNameSql = "SELECT TenantEmployeeNumber, FIRST_NAME, LAST_NAME FROM EMPLOYEE_DATA WHERE EID = ? AND TenantID = ?";
                try (PreparedStatement psName = con.prepareStatement(empNameSql)) {
                    psName.setInt(1, targetEids.get(0));
                    psName.setInt(2, tenantId);
                    try (ResultSet rsName = psName.executeQuery()) {
                        if (rsName.next()) {
                            Integer tenNo = rsName.getObject("TenantEmployeeNumber") != null ? rsName.getInt("TenantEmployeeNumber") : null;
                            String fn = rsName.getString("FIRST_NAME");
                            String ln = rsName.getString("LAST_NAME");
                            String namePart = (isValid(fn) ? fn : "") + (isValid(ln) ? " " + ln : "");
                            if (tenNo != null && tenNo > 0) {
                                displayIdentifier = "#" + tenNo + (!namePart.trim().isEmpty() ? " (" + namePart.trim() + ")" : "");
                            } else if (!namePart.trim().isEmpty()){
                                displayIdentifier = namePart.trim() + " (EID: " + targetEids.get(0) + ")";
                            }
                        }
                    }
                }

                String opPastTense = adjustmentOperation.equals("set") ? "Set" : adjustmentOperation + "ed";
                 operationSummaryForSuccess = String.format(Locale.US, "%s %.2f hours for %s for employee %s.",
                                                            opPastTense.substring(0,1).toUpperCase() + opPastTense.substring(1),
                                                            hoursValue, accrualColumnName.replace("_HOURS", "").replace("_", " "), displayIdentifier);
            }

            // --- 3. Execute Update(s) ---
            String sqlOperator = "+";
            if ("subtract".equals(adjustmentOperation)) {
                sqlOperator = "-";
            }
            String updateSql;
            if ("set".equals(adjustmentOperation)) {
                updateSql = "UPDATE EMPLOYEE_DATA SET " + accrualColumnName + " = ? WHERE EID = ? AND TenantID = ?";
            } else {
                updateSql = "UPDATE EMPLOYEE_DATA SET " + accrualColumnName + " = COALESCE(" + accrualColumnName + ", 0) " + sqlOperator + " ? WHERE EID = ? AND TenantID = ?";
            }
            
            int successfulUpdates = 0;
            try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                for (int eid : targetEids) {
                    psUpdate.setDouble(1, hoursValue);
                    psUpdate.setInt(2, eid);
                    psUpdate.setInt(3, tenantId);
                    int rowsAffected = psUpdate.executeUpdate();
                    if (rowsAffected > 0) successfulUpdates++;
                    else logger.warning("Update for " + accrualColumnName + " for EID: " + eid + " (TenantID: " + tenantId + ") affected 0 rows.");
                }
            }

            // --- 4. Commit or Rollback & Respond ---
            boolean overallSuccess = (successfulUpdates > 0 && successfulUpdates == targetEids.size());
            if (isGlobal && successfulUpdates > 0 && successfulUpdates < targetEids.size()){
                logger.warning("Global accrual adjustment: " + successfulUpdates + " succeeded, " + (targetEids.size() - successfulUpdates) + " failed for TenantID " + tenantId + ". Committing partial success.");
                overallSuccess = true;
            }

            if (overallSuccess) {
                con.commit();
                logger.info("Transaction committed. " + operationSummaryForSuccess);
                sendJsonResponse(response, true, operationSummaryForSuccess, HttpServletResponse.SC_OK);
            } else {
                rollback(con);
                String failureDetail = String.format("Accrual adjustment failed. Succeeded for %d of %d targeted employees. Transaction rolled back.", successfulUpdates, targetEids.size());
                logger.warning(failureDetail + " For TenantID: " + tenantId);
                sendJsonResponse(response, false, failureDetail, HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }

        } catch (IllegalArgumentException | SecurityException e) {
            logger.log(Level.WARNING, "Input/Validation Error for TenantID " + tenantId + " in handleAdjustAccruedHours: " + e.getMessage());
            rollback(con);
            sendJsonResponse(response, false, "Invalid input: " + escapeHtml(e.getMessage()), HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error during accrual update for TenantID " + tenantId + " in handleAdjustAccruedHours", e);
            rollback(con);
            sendJsonResponse(response, false, "Database error: " + escapeHtml(e.getMessage()), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected Error during accrual update for TenantID " + tenantId + " in handleAdjustAccruedHours", e);
            rollback(con);
            sendJsonResponse(response, false, "Unexpected server error: " + escapeHtml(e.getMessage()), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (con != null) {
                try {
                    if (!con.getAutoCommit() && !con.isClosed()) { con.setAutoCommit(true); }
                    if (!con.isClosed()) con.close();
                } catch (SQLException ex) { logger.log(Level.WARNING, "Failed to close/reset connection for T:" + tenantId, ex); }
            }
        }
    }

    private boolean isEmployeeOfTenant(Connection con, int tenantId, int eid) throws SQLException {
        // ... (Keep existing from previous version) ...
        String sql = "SELECT EID FROM EMPLOYEE_DATA WHERE EID = ? AND TenantID = ? AND ACTIVE = TRUE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, eid);
            ps.setInt(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void logParameters(HttpServletRequest request) {
        // ... (Keep existing from previous version) ...
        if (logger.isLoggable(Level.INFO)) {
            logger.info("--- AccrualServlet Request Parameters ---");
            Enumeration<String> paramNames = request.getParameterNames();
            if (!paramNames.hasMoreElements()) { logger.info("No parameters found."); }
            else {
                for (String paramName : Collections.list(paramNames)) {
                    String[] paramValues = request.getParameterValues(paramName);
                    logger.info(String.format("Param: %s = %s", paramName, Arrays.toString(paramValues)));
                }
            }
            logger.info("--- End AccrualServlet Request Parameters ---");
        }
    }

    private void sendJsonResponse(HttpServletResponse response, boolean success, String message, int statusCode) throws IOException {
        // ... (Keep existing from previous version) ...
        if (response.isCommitted()) { logger.warning("Response already committed! Cannot send JSON: Success=" + success + ", Msg=" + message); return; }
        response.setStatus(statusCode);
        PrintWriter out = null;
        String jsonString;
        try {
            String key = success ? "message" : "error";
            String escapedMessage = (message == null) ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
            jsonString = String.format("{\"success\": %b, \"%s\": \"%s\"}", success, key, escapedMessage);
            out = response.getWriter();
            out.print(jsonString);
            out.flush();
            logger.info("Sent JSON Response: " + jsonString + " with status " + statusCode);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Exception within sendJsonResponse. Message: " + message, e);
            if (!response.isCommitted()) {
                try { response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error constructing JSON response."); }
                catch (IllegalStateException ise) { logger.warning("Could not sendError, response already committed.");}
            }
        }
    }

    private void rollback(Connection con) {
        // ... (Keep existing from previous version) ...
        if (con != null) {
            try {
                if (!con.getAutoCommit() && !con.isClosed()) {
                    logger.warning("Rolling back transaction due to error.");
                    con.rollback();
                }
            } catch (SQLException rbEx) { logger.log(Level.SEVERE, "Transaction rollback failed!", rbEx); }
        } else { logger.warning("Rollback requested but connection was null."); }
    }
}