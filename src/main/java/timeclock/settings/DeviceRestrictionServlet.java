package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.Configuration;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

@WebServlet("/DeviceRestrictionServlet")
public class DeviceRestrictionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(DeviceRestrictionServlet.class.getName());
    private static final String GLOBAL_MAX_DEVICES_KEY = "MaxDevicesPerUserGlobal";
    private static final String DEFAULT_SYSTEM_MAX_DEVICES = "2";
    private static final String WIZARD_RETURN_STEP_SETTINGS = "settings_setup";

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("TenantID") instanceof Integer) {
            return (Integer) session.getAttribute("TenantID");
        }
        return null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return "Administrator".equalsIgnoreCase((String) session.getAttribute("Permissions"));
        }
        return false;
    }

    private String encodeURL(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }
    
    private String escapeJson(String s) {
        if (s == null) return "null"; // JSON representation of null
        return JSONObject.quote(s);
    }

    // MODIFIED: Declares throws SQLException
    private void loadAndForward(HttpServletRequest request, HttpServletResponse response, Integer tenantId,
                                String initialPageLoadError, boolean fromWizardContextOnGet)
            throws ServletException, IOException, SQLException { 

        String logPrefix = "[DeviceRestrictionServlet loadAndForward T:" + (tenantId != null ? tenantId : "null") + "] ";
        logger.info(logPrefix + "Called. initialPageLoadError: '" + initialPageLoadError + "', fromWizardContextOnGet: " + fromWizardContextOnGet);

        String effectivePageLoadError = initialPageLoadError;
        List<Map<String, Object>> employeeDeviceList = new ArrayList<>();
        String currentGlobalMaxDevices = DEFAULT_SYSTEM_MAX_DEVICES;

        HttpSession session = request.getSession(false);
        boolean actualWizardModeForJSP = false;
        String wizardReturnStepForJSP = null;

        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
             String currentSessionWizardStep = (String) session.getAttribute("wizardStep");
             if (WIZARD_RETURN_STEP_SETTINGS.equals(currentSessionWizardStep) || fromWizardContextOnGet) {
                 actualWizardModeForJSP = true;
                 wizardReturnStepForJSP = WIZARD_RETURN_STEP_SETTINGS;
             }
        }
        request.setAttribute("pageIsInWizardMode", actualWizardModeForJSP);
        request.setAttribute("wizardReturnStepForJSP", wizardReturnStepForJSP);
        logger.info(logPrefix + "Request attributes set: pageIsInWizardMode=" + actualWizardModeForJSP + ", wizardReturnStepForJSP='" + wizardReturnStepForJSP + "'");

        if (tenantId != null && tenantId > 0) {
            // Configuration.getProperty does not throw checked SQLException
            String tempMaxDevices = Configuration.getProperty(tenantId, GLOBAL_MAX_DEVICES_KEY); 
            logger.info(logPrefix + "Loaded GLOBAL_MAX_DEVICES_KEY '" + GLOBAL_MAX_DEVICES_KEY + "': '" + tempMaxDevices + "'");
            if (tempMaxDevices == null) {
                // Configuration.saveProperty(int,String,String) DOES throw SQLException
                // This will now propagate up to doGet if it occurs
                Configuration.saveProperty(tenantId, GLOBAL_MAX_DEVICES_KEY, DEFAULT_SYSTEM_MAX_DEVICES);
                currentGlobalMaxDevices = DEFAULT_SYSTEM_MAX_DEVICES;
                logger.info(logPrefix + "Initialized " + GLOBAL_MAX_DEVICES_KEY + " to " + DEFAULT_SYSTEM_MAX_DEVICES);
            } else {
                currentGlobalMaxDevices = tempMaxDevices;
            }

            String sql = "SELECT e.EID, e.FIRST_NAME, e.LAST_NAME, e.TenantEmployeeNumber, ed.DeviceID, ed.DeviceFingerprintHash, ed.DeviceDescription, ed.RegisteredDate, ed.LastUsedDate, ed.IsEnabled FROM employee_data e LEFT JOIN employee_devices ed ON e.EID = ed.EID AND e.TenantID = ed.TenantID WHERE e.TenantID = ? AND e.ACTIVE = TRUE ORDER BY e.LAST_NAME, e.FIRST_NAME, ed.RegisteredDate DESC";
            // This try-with-resources will also propagate SQLException if getConnection or prepareStatement fails
            try (Connection conn_list = DatabaseConnection.getConnection(); 
                 PreparedStatement pstmt_list = conn_list.prepareStatement(sql)) {
                pstmt_list.setInt(1, tenantId);
                try (ResultSet rs = pstmt_list.executeQuery()) {
                    Map<Integer, Map<String, Object>> employeeMap = new HashMap<>();
                    while (rs.next()) {
                        Integer employeeEID = rs.getInt("EID");
                        Map<String, Object> employeeEntry = employeeMap.computeIfAbsent(employeeEID, k -> {
                            Map<String, Object> newEntry = new HashMap<>();
                            try { 
                                newEntry.put("EID", employeeEID); newEntry.put("FirstName", rs.getString("FIRST_NAME")); newEntry.put("LastName", rs.getString("LAST_NAME"));
                                newEntry.put("TenantEmployeeNumber", rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : "N/A");
                                newEntry.put("devices", new ArrayList<Map<String, Object>>()); employeeDeviceList.add(newEntry);
                            } catch (SQLException se_inner) { logger.finer("Minor issue processing employee data row: " + se_inner.getMessage());} // Log and continue
                            return newEntry;
                        });
                        if (rs.getObject("DeviceID") != null && employeeEntry != null && employeeEntry.get("devices") != null) {
                            Map<String, Object> device = new HashMap<>();
                            device.put("DeviceID", rs.getInt("DeviceID")); device.put("DeviceFingerprintHash", rs.getString("DeviceFingerprintHash")); device.put("DeviceDescription", rs.getString("DeviceDescription")); device.put("RegisteredDate", rs.getTimestamp("RegisteredDate")); device.put("LastUsedDate", rs.getTimestamp("LastUsedDate")); device.put("IsEnabled", rs.getBoolean("IsEnabled"));
                            @SuppressWarnings("unchecked") List<Map<String, Object>> devicesList = (List<Map<String, Object>>) employeeEntry.get("devices");
                            if(devicesList != null) devicesList.add(device);
                        }
                    }
                } 
            } 
        } else {
             if (effectivePageLoadError == null && isAdmin(request)) effectivePageLoadError = "Invalid tenant context.";
             else if (effectivePageLoadError == null && !isAdmin(request)) effectivePageLoadError = "Access Denied.";
             logger.warning(logPrefix + "TenantId null or invalid. effectivePageLoadError: " + effectivePageLoadError);
        }

        request.setAttribute("currentGlobalMaxDevices", currentGlobalMaxDevices);
        request.setAttribute("employeeDeviceList", employeeDeviceList);
        if (effectivePageLoadError != null) request.setAttribute("pageLoadErrorMessage", effectivePageLoadError);
        
        logger.info(logPrefix + "Forwarding to /configureDeviceRestrictions.jsp");
        request.getRequestDispatcher("/configureDeviceRestrictions.jsp").forward(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer tenantId = getTenantId(request);
        String logPrefix = "[DeviceRestrictionServlet doGet T:" + (tenantId != null ? tenantId : "null") + "] ";
        logger.info(logPrefix + "Called.");
        String pageLoadError = null;

        if (tenantId == null) {
            logger.warning(logPrefix + "Session expired or invalid tenantId. Redirecting to login.");
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeURL("Session expired or invalid."));
            return;
        }
        if (!isAdmin(request)) {
            pageLoadError = "Access Denied. Administrator privileges required.";
            logger.warning(logPrefix + pageLoadError);
        }
        
        boolean fromWizardOnGet = false;
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
            String sessionWizardStep = (String) session.getAttribute("wizardStep");
            if (WIZARD_RETURN_STEP_SETTINGS.equals(sessionWizardStep)){
                 fromWizardOnGet = true;
            }
            logger.info(logPrefix + "Wizard mode check: startSetupWizard=" + session.getAttribute("startSetupWizard") + ", sessionWizardStep='" + sessionWizardStep + "', fromWizardOnGet=" + fromWizardOnGet);
        } else {
             logger.info(logPrefix + "Not in wizard mode (session flags not set for this stage).");
        }
        
        try {
            // loadAndForward now declares throws SQLException
            loadAndForward(request, response, tenantId, pageLoadError, fromWizardOnGet);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, logPrefix + "SQL error during page load dispatch.", e);
            request.setAttribute("pageLoadErrorMessage", "A critical database error occurred while preparing data: " + e.getMessage());
            // Set wizard attributes again so JSP can render error page correctly in wizard context
            request.setAttribute("pageIsInWizardMode", fromWizardOnGet); 
            request.setAttribute("wizardReturnStepForJSP", fromWizardOnGet ? WIZARD_RETURN_STEP_SETTINGS : null);
            request.setAttribute("employeeDeviceList", new ArrayList<>()); // Ensure empty list on error
            request.setAttribute("currentGlobalMaxDevices", DEFAULT_SYSTEM_MAX_DEVICES); // Reset to default
            request.getRequestDispatcher("/configureDeviceRestrictions.jsp").forward(request, response);
        } catch (Exception eAll) { // Catch other unexpected runtime errors
             logger.log(Level.SEVERE, logPrefix + "Unexpected error during page load dispatch.", eAll);
            request.setAttribute("pageLoadErrorMessage", "An unexpected error occurred: " + eAll.getMessage());
            request.setAttribute("pageIsInWizardMode", fromWizardOnGet);
            request.setAttribute("wizardReturnStepForJSP", fromWizardOnGet ? WIZARD_RETURN_STEP_SETTINGS : null);
            request.setAttribute("employeeDeviceList", new ArrayList<>());
            request.setAttribute("currentGlobalMaxDevices", DEFAULT_SYSTEM_MAX_DEVICES);
            request.getRequestDispatcher("/configureDeviceRestrictions.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Integer tenantId = getTenantId(request);
        String action = request.getParameter("action");
        String logPrefix = "[DeviceRestrictionServlet doPost T:" + (tenantId != null ? tenantId : "null") + " A:" + action + "] ";
        logger.info(logPrefix + "Called. Params: wizardModeActive=" + request.getParameter("wizardModeActive") + 
                    ", wizardReturnStep=" + request.getParameter("wizardReturnStep"));

        boolean isRequestFromWizardFormForGlobalMax = "saveGlobalMaxDevices".equals(action) && 
                                                      "true".equalsIgnoreCase(request.getParameter("wizardModeActive"));
        
        PrintWriter out = null;
        JSONObject jsonResponse = new JSONObject(); // Always create for AJAX, conditionally use for wizard errors

        if (!isRequestFromWizardFormForGlobalMax) { // If not the wizard form post, assume AJAX and set JSON headers
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            out = response.getWriter(); // Get writer only for AJAX responses
        }

        String operationStatusMessage = null; // To store success/error messages
        boolean operationErrorOccurred = false;

        if (tenantId == null) {
            operationStatusMessage = "Session expired or invalid.";
            operationErrorOccurred = true;
            logger.warning(logPrefix + operationStatusMessage);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        } else if (!isAdmin(request)) {
            operationStatusMessage = "Access Denied.";
            operationErrorOccurred = true;
            logger.warning(logPrefix + operationStatusMessage);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

        Connection conn = null;
        if (!operationErrorOccurred) { // Proceed only if initial checks pass
            try {
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);

                if ("saveGlobalMaxDevices".equals(action)) {
                    String maxDevicesStr = request.getParameter("maxDevices");
                    int maxDevices = Integer.parseInt(maxDevicesStr); 
                    if (maxDevices < 0 || maxDevices > 20) {
                        throw new IllegalArgumentException("Max devices value must be between 0 and 20.");
                    }
                    Configuration.saveProperty(tenantId, GLOBAL_MAX_DEVICES_KEY, String.valueOf(maxDevices)); // Throws SQLException
                    conn.commit(); 
                    operationStatusMessage = "Global max devices setting saved to " + maxDevices + ".";
                    logger.info(logPrefix + operationStatusMessage);

                    if (isRequestFromWizardFormForGlobalMax) {
                        String wizardReturnToStep = request.getParameter("wizardReturnStep");
                        String redirectUrl = request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" +
                                             encodeURL(wizardReturnToStep != null ? wizardReturnToStep : WIZARD_RETURN_STEP_SETTINGS) +
                                             "&message=" + encodeURL(operationStatusMessage) +
                                             "&restrictionConfigured=deviceGlobal";
                        logger.info(logPrefix + "Wizard form post successful. Redirecting to: " + redirectUrl);
                        response.sendRedirect(redirectUrl); 
                        return; 
                    } else { 
                        jsonResponse.put("status", "success").put("message", operationStatusMessage).put("newMax", maxDevices);
                        response.setStatus(HttpServletResponse.SC_OK);
                    }
                } else if ("toggleDeviceStatus".equals(action) || "deleteDevice".equals(action) || "updateDeviceDescription".equals(action)) {
                    int deviceId = Integer.parseInt(request.getParameter("deviceId")); String sql; int rowsAffected = 0;
                    if ("toggleDeviceStatus".equals(action)) { boolean newStatus = Boolean.parseBoolean(request.getParameter("isEnabled")); sql = "UPDATE employee_devices SET IsEnabled = ? WHERE DeviceID = ? AND TenantID = ?"; try (PreparedStatement pstmt = conn.prepareStatement(sql)) { pstmt.setBoolean(1, newStatus); pstmt.setInt(2, deviceId); pstmt.setInt(3, tenantId); rowsAffected = pstmt.executeUpdate(); if (rowsAffected > 0) jsonResponse.put("status", "success").put("message", "Device status updated."); }
                    } else if ("deleteDevice".equals(action)) { sql = "DELETE FROM employee_devices WHERE DeviceID = ? AND TenantID = ?"; try (PreparedStatement pstmt = conn.prepareStatement(sql)) { pstmt.setInt(1, deviceId); pstmt.setInt(2, tenantId); rowsAffected = pstmt.executeUpdate(); if (rowsAffected > 0) jsonResponse.put("status", "success").put("message", "Device deleted."); }
                    } else { String description = request.getParameter("description"); if (description == null || description.trim().isEmpty()) description = "Registered Device"; if (description.length() > 255) description = description.substring(0, 255); sql = "UPDATE employee_devices SET DeviceDescription = ? WHERE DeviceID = ? AND TenantID = ?"; try (PreparedStatement pstmt = conn.prepareStatement(sql)) { pstmt.setString(1, description.trim()); pstmt.setInt(2, deviceId); pstmt.setInt(3, tenantId); rowsAffected = pstmt.executeUpdate(); if (rowsAffected > 0) jsonResponse.put("status", "success").put("message", "Description updated.").put("newDescription", escapeJson(description.trim())); } }
                    if (rowsAffected > 0) { conn.commit(); } else { conn.rollback(); if (!jsonResponse.has("status")) { jsonResponse.put("status", "error").put("message", "Operation failed or device not found."); response.setStatus(HttpServletResponse.SC_NOT_FOUND); } }
                    if (!jsonResponse.has("status")) {jsonResponse.put("status", "success");} // Default to success if no error and not set
                    response.setStatus(HttpServletResponse.SC_OK);
                } else {
                    conn.rollback();
                    operationErrorOccurred = true;
                    operationStatusMessage = "Invalid action specified.";
                    logger.warning(logPrefix + operationStatusMessage);
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                }
            } catch (IllegalArgumentException e_input) {
                operationErrorOccurred = true;
                operationStatusMessage = "Invalid input: " + e_input.getMessage();
                logger.log(Level.WARNING, logPrefix + operationStatusMessage, e_input);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                try { if (conn != null && !conn.getAutoCommit()) conn.rollback(); } catch (SQLException ex_rb) { logger.log(Level.WARNING, logPrefix + "Rollback failed.", ex_rb); }
            } catch (SQLException e_sql) {
                operationErrorOccurred = true;
                operationStatusMessage = "Database error: " + e_sql.getMessage();
                logger.log(Level.SEVERE, logPrefix + operationStatusMessage, e_sql);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try { if (conn != null && !conn.getAutoCommit()) conn.rollback(); } catch (SQLException ex_rb) { logger.log(Level.WARNING, logPrefix + "Rollback failed.", ex_rb); }
            } catch (Exception e_gen) {
                operationErrorOccurred = true;
                operationStatusMessage = "Unexpected server error: " + e_gen.getMessage();
                logger.log(Level.SEVERE, logPrefix + operationStatusMessage, e_gen);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try { if (conn != null && !conn.getAutoCommit()) conn.rollback(); } catch (SQLException ex_rb) { logger.log(Level.WARNING, logPrefix + "Rollback failed.", ex_rb); }
            } finally {
                if (conn != null) { try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e_close) { logger.log(Level.WARNING, logPrefix + "Failed to close DB connection.", e_close); } }
            }
        } // End of if (!operationErrorOccurred)

        // Final response handling
        if (response.isCommitted()) {
            return; // Redirect already handled
        }

        if (isRequestFromWizardFormForGlobalMax && operationErrorOccurred) {
            // A wizard form post for global max devices failed. Re-render the JSP with error.
            logger.info(logPrefix + "Wizard form post for global max failed. Calling loadAndForward with error: " + operationStatusMessage);
            boolean fromWizardContextForReload = false;
            HttpSession currentSession = request.getSession(false);
            if (currentSession != null && Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
                String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
                 if (WIZARD_RETURN_STEP_SETTINGS.equals(sessionWizardStep)){ fromWizardContextForReload = true; }
            }
            try {
                loadAndForward(request, response, tenantId, operationStatusMessage, fromWizardContextForReload);
            } catch (SQLException | ServletException | IOException eLFE) { // loadAndForward can throw these now
                 logger.log(Level.SEVERE, logPrefix + "Critical error in loadAndForward after POST error.", eLFE);
                 // If forward fails, send a simple error response
                 if(!response.isCommitted()) {
                    response.setContentType("text/plain");
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to display error page after initial error.");
                 }
            }
        } else if (!isRequestFromWizardFormForGlobalMax && out != null) { // AJAX request
            if (operationErrorOccurred) { // Ensure error status is in JSON if not already set by action block
                if (!jsonResponse.has("status")) jsonResponse.put("status", "error");
                if (!jsonResponse.has("message")) jsonResponse.put("message", escapeJson(operationStatusMessage));
            } else if (!jsonResponse.has("status")) { // If no error and no specific success, set default success
                 jsonResponse.put("status", "success").put("message", "Action completed.");
            }
            out.print(jsonResponse.toString());
            out.flush();
        }
        // If it was a wizard form post and it was successful, a redirect would have occurred and we would have returned.
    }
}