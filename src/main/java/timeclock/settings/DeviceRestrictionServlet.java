package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.Configuration;
import timeclock.util.Helpers; // Make sure you have this utility class

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;

@WebServlet("/DeviceRestrictionServlet")
public class DeviceRestrictionServlet extends HttpServlet {
    private static final long serialVersionUID = 2L; // Version updated
    private static final Logger logger = Logger.getLogger(DeviceRestrictionServlet.class.getName());
    private static final String GLOBAL_MAX_DEVICES_KEY = "MaxDevicesPerUserGlobal";
    private static final String DEFAULT_SYSTEM_MAX_DEVICES = "2";
    private static final String WIZARD_RETURN_STEP_settings = "settings_setup";

    // --- Utility Methods ---

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return (session != null && session.getAttribute("TenantID") instanceof Integer) 
                ? (Integer) session.getAttribute("TenantID") : null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && "Administrator".equalsIgnoreCase((String) session.getAttribute("Permissions"));
    }

    private String encodeURL(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private void sendJsonResponse(HttpServletResponse response, int statusCode, String status, String message, JSONObject additionalData) throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JSONObject json = (additionalData != null) ? additionalData : new JSONObject();
        json.put("status", status);
        json.put("message", message);
        
        try (PrintWriter out = response.getWriter()) {
            out.print(json.toString());
            out.flush();
        }
    }

    // --- doGet: Load data for the JSP ---

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Integer tenantId = getTenantId(request);

        if (tenantId == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeURL("Session expired."));
            return;
        }
        if (!isAdmin(request)) {
            request.setAttribute("pageLoadErrorMessage", "Access Denied. Administrator privileges required.");
            request.getRequestDispatcher("/configureDeviceRestrictions.jsp").forward(request, response);
            return;
        }

        try {
            loadAndForward(request, response, tenantId, null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Critical error during page load.", e);
            request.setAttribute("pageLoadErrorMessage", "A critical error occurred while loading page data: " + e.getMessage());
            request.getRequestDispatcher("/configureDeviceRestrictions.jsp").forward(request, response);
        }
    }

    private void loadAndForward(HttpServletRequest request, HttpServletResponse response, Integer tenantId, String initialPageLoadError) throws ServletException, IOException, SQLException {
        HttpSession session = request.getSession(false);
        boolean pageIsActuallyInWizardMode = false;
        String wizardStepToReturnToOnSettingsPage = null;
        
        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
            String currentSessionWizardStep = (String) session.getAttribute("wizardStep");
            if (WIZARD_RETURN_STEP_settings.equals(currentSessionWizardStep)) {
                pageIsActuallyInWizardMode = true;
                wizardStepToReturnToOnSettingsPage = WIZARD_RETURN_STEP_settings;
            }
        }

        // Load Global Max Devices
        String currentGlobalMaxDevices = Configuration.getProperty(tenantId, GLOBAL_MAX_DEVICES_KEY, DEFAULT_SYSTEM_MAX_DEVICES);
        request.setAttribute("currentGlobalMaxDevices", currentGlobalMaxDevices);
        
        // Load Employee Devices
        List<Map<String, Object>> employeeDeviceList = new ArrayList<>();
        String sql = "SELECT e.EID, e.FIRST_NAME, e.LAST_NAME, ed.DeviceID, ed.DeviceDescription, ed.RegisteredDate, ed.LastUsedDate, ed.IsEnabled " +
                     "FROM employee_data e LEFT JOIN employee_devices ed ON e.EID = ed.EID AND e.TenantID = ed.TenantID " +
                     "WHERE e.TenantID = ? AND e.ACTIVE = TRUE ORDER BY e.LAST_NAME, e.FIRST_NAME, ed.RegisteredDate DESC";
        
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                Map<Integer, Map<String, Object>> employeeMap = new LinkedHashMap<>(); // Preserve order
                while (rs.next()) {
                    Integer eid = rs.getInt("EID");
                    Map<String, Object> employee = employeeMap.computeIfAbsent(eid, k -> {
                        Map<String, Object> newEmployee = new HashMap<>();
                        try {
                            newEmployee.put("EID", eid);
                            newEmployee.put("FirstName", rs.getString("FIRST_NAME"));
                            newEmployee.put("LastName", rs.getString("LAST_NAME"));
                            newEmployee.put("devices", new ArrayList<Map<String, Object>>());
                        } catch (SQLException e) { /* should not happen here */ }
                        return newEmployee;
                    });
                    
                    if (rs.getObject("DeviceID") != null) {
                        Map<String, Object> device = new HashMap<>();
                        device.put("DeviceID", rs.getInt("DeviceID"));
                        device.put("DeviceDescription", rs.getString("DeviceDescription"));
                        device.put("RegisteredDate", rs.getTimestamp("RegisteredDate"));
                        device.put("LastUsedDate", rs.getTimestamp("LastUsedDate"));
                        device.put("IsEnabled", rs.getBoolean("IsEnabled"));
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> devices = (List<Map<String, Object>>) employee.get("devices");
                        devices.add(device);
                    }
                }
                employeeDeviceList.addAll(employeeMap.values());
            }
        }
        
        request.setAttribute("employeeDeviceList", employeeDeviceList);
        if (initialPageLoadError != null) request.setAttribute("pageLoadErrorMessage", initialPageLoadError);
        
        request.setAttribute("pageIsInWizardMode", pageIsActuallyInWizardMode);
        request.setAttribute("wizardReturnStepForJSP", wizardStepToReturnToOnSettingsPage);
        
        request.getRequestDispatcher("/configureDeviceRestrictions.jsp").forward(request, response);
    }

    // --- doPost: Handle AJAX requests from the JSP ---

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Integer tenantId = getTenantId(request);
        String action = request.getParameter("action");
        String logPrefix = "[DeviceServlet doPost T:" + tenantId + " A:" + action + "] ";

        if (tenantId == null) {
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "error", "Session expired. Please log in again.", null);
            return;
        }
        if (!isAdmin(request)) {
            sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN, "error", "Access Denied.", null);
            return;
        }
        if (action == null || action.trim().isEmpty()) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Invalid action specified.", null);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            switch (action) {
                case "saveGlobalMaxDevices":
                    handleSaveGlobalMax(conn, request, response, tenantId);
                    break;
                case "toggleDeviceStatus":
                    handleToggleStatus(conn, request, response, tenantId);
                    break;
                case "deleteDevice":
                    handleDeleteDevice(conn, request, response, tenantId);
                    break;
                case "updateDeviceDescription":
                    handleUpdateDescription(conn, request, response, tenantId);
                    break;
                default:
                    sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, "error", "Unknown action: " + action, null);
            }
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, logPrefix + "Invalid input.", e);
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, "error", e.getMessage(), null);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, logPrefix + "Database error.", e);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error", "A database error occurred.", null);
        } catch (Exception e) {
            logger.log(Level.SEVERE, logPrefix + "Unexpected error.", e);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "error", "An unexpected server error occurred.", null);
        }
    }
    
    // --- Action Handlers ---

    private void handleSaveGlobalMax(Connection conn, HttpServletRequest req, HttpServletResponse res, int tenantId) throws SQLException, IOException {
        String maxDevicesStr = req.getParameter("maxDevices");
        int maxDevices = Integer.parseInt(maxDevicesStr);
        if (maxDevices < 0 || maxDevices > 20) {
            throw new IllegalArgumentException("Max devices must be between 0 and 20.");
        }
        Configuration.saveProperty(tenantId, GLOBAL_MAX_DEVICES_KEY, String.valueOf(maxDevices));
        sendJsonResponse(res, HttpServletResponse.SC_OK, "success", "Global limit saved to " + maxDevices + ".", null);
    }
    
    private void handleToggleStatus(Connection conn, HttpServletRequest req, HttpServletResponse res, int tenantId) throws SQLException, IOException {
        int deviceId = Integer.parseInt(req.getParameter("deviceId"));
        boolean isEnabled = Boolean.parseBoolean(req.getParameter("isEnabled"));
        String sql = "UPDATE employee_devices SET IsEnabled = ? WHERE DeviceID = ? AND TenantID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setBoolean(1, isEnabled);
            pstmt.setInt(2, deviceId);
            pstmt.setInt(3, tenantId);
            if (pstmt.executeUpdate() > 0) {
                sendJsonResponse(res, HttpServletResponse.SC_OK, "success", "Device status updated.", null);
            } else {
                throw new SQLException("Device not found or no update was necessary.");
            }
        }
    }

    private void handleDeleteDevice(Connection conn, HttpServletRequest req, HttpServletResponse res, int tenantId) throws SQLException, IOException {
        int deviceId = Integer.parseInt(req.getParameter("deviceId"));
        String sql = "DELETE FROM employee_devices WHERE DeviceID = ? AND TenantID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, deviceId);
            pstmt.setInt(2, tenantId);
            if (pstmt.executeUpdate() > 0) {
                sendJsonResponse(res, HttpServletResponse.SC_OK, "success", "Device deleted successfully.", null);
            } else {
                throw new SQLException("Device not found.");
            }
        }
    }

    private void handleUpdateDescription(Connection conn, HttpServletRequest req, HttpServletResponse res, int tenantId) throws SQLException, IOException {
        int deviceId = Integer.parseInt(req.getParameter("deviceId"));
        String description = Helpers.sanitize(req.getParameter("description")); // Use a sanitizer if you have one
        if (description == null || description.trim().isEmpty()) {
            description = "Registered Device";
        }
        if (description.length() > 255) {
            description = description.substring(0, 255);
        }
        String sql = "UPDATE employee_devices SET DeviceDescription = ? WHERE DeviceID = ? AND TenantID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, description.trim());
            pstmt.setInt(2, deviceId);
            pstmt.setInt(3, tenantId);
            if (pstmt.executeUpdate() > 0) {
                JSONObject data = new JSONObject();
                data.put("newDescription", description.trim());
                sendJsonResponse(res, HttpServletResponse.SC_OK, "success", "Description updated.", data);
            } else {
                throw new SQLException("Device not found.");
            }
        }
    }
}