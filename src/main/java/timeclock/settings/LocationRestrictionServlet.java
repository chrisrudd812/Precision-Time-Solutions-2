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

@WebServlet("/LocationRestrictionServlet")
public class LocationRestrictionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LocationRestrictionServlet.class.getName());
    private static final String WIZARD_RETURN_STEP_SETTINGS = "settings_setup";

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object tenantIdObj = session.getAttribute("TenantID");
            if (tenantIdObj instanceof Integer) return (Integer) tenantIdObj;
        }
        return null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) return "Administrator".equalsIgnoreCase((String) session.getAttribute("Permissions"));
        return false;
    }
    
    private String encodeURL(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private void loadAndForward(HttpServletRequest request, HttpServletResponse response, Integer tenantId, 
                                String saveSuccessMsg, String saveErrorMsg, String initialLoadErrorMsg)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        boolean pageIsActuallyInWizardMode = false;
        String wizardStepToReturnToOnSettingsPage = null;

        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
            String currentSessionWizardStep = (String) session.getAttribute("wizardStep");
            if (WIZARD_RETURN_STEP_SETTINGS.equals(currentSessionWizardStep)) {
                pageIsActuallyInWizardMode = true;
                wizardStepToReturnToOnSettingsPage = WIZARD_RETURN_STEP_SETTINGS;
            }
        }
        
        if ("true".equalsIgnoreCase(request.getParameter("wizardModeActive")) && wizardStepToReturnToOnSettingsPage == null){
             wizardStepToReturnToOnSettingsPage = request.getParameter("wizardReturnStep");
             if(wizardStepToReturnToOnSettingsPage != null) pageIsActuallyInWizardMode = true;
        }

        if (pageIsActuallyInWizardMode && wizardStepToReturnToOnSettingsPage != null) {
            if (saveSuccessMsg != null && !saveSuccessMsg.isEmpty()) {
                response.sendRedirect(request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" +
                                     encodeURL(wizardStepToReturnToOnSettingsPage) + "&message=" + encodeURL(saveSuccessMsg) +
                                     "&restrictionConfigured=location");
                return;
            }
            if (saveErrorMsg != null && !saveErrorMsg.isEmpty() && request.getMethod().equalsIgnoreCase("POST") ) {
                 response.sendRedirect(request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" +
                                     encodeURL(wizardStepToReturnToOnSettingsPage) + "&error=" + encodeURL(saveErrorMsg) +
                                     "&restrictionConfigured=location");
                return;
            }
        }

        List<Map<String, Object>> locations = new ArrayList<>();
        String effectivePageLoadError = initialLoadErrorMsg;

        if (tenantId != null && tenantId > 0) {
            String sql = "SELECT LocationID, LocationName, Latitude, Longitude, RadiusMeters, IsEnabled FROM geofence_locations WHERE TenantID = ? ORDER BY LocationName ASC";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, tenantId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> loc = new HashMap<>();
                        loc.put("locationID", rs.getInt("LocationID"));
                        loc.put("locationName", rs.getString("LocationName"));
                        loc.put("latitude", rs.getBigDecimal("Latitude"));
                        loc.put("longitude", rs.getBigDecimal("Longitude"));
                        loc.put("radiusMeters", rs.getInt("RadiusMeters"));
                        loc.put("isEnabled", rs.getBoolean("IsEnabled"));
                        locations.add(loc);
                    }
                }
            } catch (SQLException e) {
                if (effectivePageLoadError == null) effectivePageLoadError = "Error loading existing location settings: " + e.getMessage();
            }
        } else {
             if (effectivePageLoadError == null) effectivePageLoadError = "Invalid tenant context.";
        }

        request.setAttribute("locations", locations);
        if (saveSuccessMsg != null) request.setAttribute("saveSuccessMessage", saveSuccessMsg);
        if (saveErrorMsg != null) request.setAttribute("errorMessageJSP", saveErrorMsg);
        if (effectivePageLoadError != null) request.setAttribute("pageLoadErrorMessage", effectivePageLoadError);
        
        request.setAttribute("pageIsInWizardMode", pageIsActuallyInWizardMode);
        request.setAttribute("wizardReturnStepForJSP", wizardStepToReturnToOnSettingsPage);

        request.getRequestDispatcher("/configureLocationRestrictions.jsp").forward(request, response);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Integer tenantId = getTenantId(request);
        String pageLoadErr = null;

        if (tenantId == null || tenantId <= 0) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeURL("Session expired or invalid."));
            return;
        }
        if (!isAdmin(request)) {
            pageLoadErr = "Access Denied. Administrator privileges required.";
        }
        loadAndForward(request, response, tenantId, null, null, pageLoadErr);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Integer tenantId = getTenantId(request);
        String action = request.getParameter("action");
        String successMessage = null;
        String errorMessage = null;

        if (tenantId == null || !isAdmin(request)) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeURL("Access Denied or session expired."));
            return;
        }

        if ("updateLocationStatus".equals(action)) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            PrintWriter out = response.getWriter();
            try {
                int locationID = Integer.parseInt(request.getParameter("locationID"));
                boolean isEnabled = Boolean.parseBoolean(request.getParameter("isEnabled"));
                String sql = "UPDATE geofence_locations SET IsEnabled = ? WHERE LocationID = ? AND TenantID = ?";
                try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setBoolean(1, isEnabled);
                    pstmt.setInt(2, locationID);
                    pstmt.setInt(3, tenantId);
                    if (pstmt.executeUpdate() > 0) {
                        out.print("{\"success\": true, \"message\": \"Status updated.\"}");
                    } else {
                        throw new SQLException("Location not found, no rows updated.");
                    }
                }
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                out.print("{\"success\": false, \"error\": \"Failed to update status: " + e.getMessage() + "\"}");
            }
            out.flush();
            return;
        }
        
        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);
            if ("addLocation".equals(action) || "editLocation".equals(action)) {
                String name = request.getParameter("locationName");
                String latStr = request.getParameter("latitude");
                String lonStr = request.getParameter("longitude");
                String radiusStr = request.getParameter("radiusMeters");
                boolean isEnabled = "true".equalsIgnoreCase(request.getParameter("isEnabled"));
                if (name == null || name.trim().isEmpty() || latStr == null || latStr.trim().isEmpty() || 
                    lonStr == null || lonStr.trim().isEmpty() || radiusStr == null || radiusStr.trim().isEmpty()) {
                    throw new IllegalArgumentException("Location Name, Latitude, Longitude, and Radius are required.");
                }
                BigDecimal latitude = new BigDecimal(latStr.trim());
                BigDecimal longitude = new BigDecimal(lonStr.trim());
                int radius = Integer.parseInt(radiusStr.trim());
                if (radius < 10 || radius > 10000) throw new IllegalArgumentException("Radius must be between 10 and 10000 meters.");
                if (latitude.compareTo(new BigDecimal("-90")) < 0 || latitude.compareTo(new BigDecimal("90")) > 0) throw new IllegalArgumentException("Latitude must be between -90 and 90.");
                if (longitude.compareTo(new BigDecimal("-180")) < 0 || longitude.compareTo(new BigDecimal("180")) > 0) throw new IllegalArgumentException("Longitude must be between -180 and 180.");

                if ("addLocation".equals(action)) {
                    String sql = "INSERT INTO geofence_locations (TenantID, LocationName, Latitude, Longitude, RadiusMeters, IsEnabled) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setInt(1, tenantId); pstmt.setString(2, name.trim()); pstmt.setBigDecimal(3, latitude);
                        pstmt.setBigDecimal(4, longitude); pstmt.setInt(5, radius); pstmt.setBoolean(6, isEnabled);
                        pstmt.executeUpdate();
                        successMessage = "Location '" + name.trim() + "' added successfully.";
                    }
                } else { // editLocation
                    int locationID = Integer.parseInt(request.getParameter("locationID"));
                    String sql = "UPDATE geofence_locations SET LocationName = ?, Latitude = ?, Longitude = ?, RadiusMeters = ?, IsEnabled = ? WHERE LocationID = ? AND TenantID = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, name.trim()); pstmt.setBigDecimal(2, latitude); pstmt.setBigDecimal(3, longitude);
                        pstmt.setInt(4, radius); pstmt.setBoolean(5, isEnabled); pstmt.setInt(6, locationID); pstmt.setInt(7, tenantId);
                        if (pstmt.executeUpdate() > 0) successMessage = "Location '" + name.trim() + "' updated successfully.";
                        else errorMessage = "Location not found or no changes made.";
                    }
                }
            } else if ("deleteLocation".equals(action)) {
                int locationID = Integer.parseInt(request.getParameter("locationID"));
                String sql = "DELETE FROM geofence_locations WHERE LocationID = ? AND TenantID = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, locationID); pstmt.setInt(2, tenantId);
                    if (pstmt.executeUpdate() > 0) successMessage = "Location deleted successfully.";
                    else errorMessage = "Location not found or already deleted.";
                }
            } else {
                errorMessage = "Invalid action specified.";
            }
            conn.commit();
        } catch (Exception e) {
            errorMessage = "Operation failed: " + e.getMessage();
            if (conn != null) try { conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Rollback failed.", ex); }
        } finally {
            if (conn != null) try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { /* ignore */ }
        }
        loadAndForward(request, response, tenantId, successMessage, errorMessage, null);
    }
}