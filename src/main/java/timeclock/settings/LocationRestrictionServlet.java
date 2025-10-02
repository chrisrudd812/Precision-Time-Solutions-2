package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;
import timeclock.util.Helpers; // <-- NEW: IMPORT THE HELPERS CLASS

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/LocationRestrictionServlet")
public class LocationRestrictionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LocationRestrictionServlet.class.getName());

    // --- NEW HELPER METHOD TO UPDATE THE SESSION ---
    /**
     * Re-calculates if a location check is required and updates the session attribute.
     * This ensures the user's session is always in sync with the latest settings.
     * @param session The user's HttpSession.
     * @param tenantId The user's TenantID.
     */
    private void updateSessionLocationFlag(HttpSession session, int tenantId) {
        if (session == null) return;
        boolean locationCheckIsRequired = Helpers.isLocationCheckRequired(tenantId);
        session.setAttribute("locationCheckIsRequired", locationCheckIsRequired);

    }

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object tenantIdObj = session.getAttribute("TenantID");
            if (tenantIdObj instanceof Integer) {
                return (Integer) tenantIdObj;
            }
        }
        return null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String permissions = (String) session.getAttribute("Permissions");
            return "Administrator".equalsIgnoreCase(permissions);
        }
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Integer tenantId = getTenantId(request);
        if (tenantId == null || !isAdmin(request)) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Access Denied.", "UTF-8"));
            return;
        }
        
        loadDataAndForwardToJsp(request, response, tenantId, null);
    }
    
    private void loadDataAndForwardToJsp(HttpServletRequest request, HttpServletResponse response, Integer tenantId, String pageLoadError) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        boolean pageIsActuallyInWizardMode = false;
        String wizardStepToReturnToOnSettingsPage = null;
        
        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
            String currentSessionWizardStep = (String) session.getAttribute("wizardStep");
            if ("settings_setup".equals(currentSessionWizardStep)) {
                pageIsActuallyInWizardMode = true;
                wizardStepToReturnToOnSettingsPage = "settings_setup";
            }
        }

        List<Map<String, Object>> locations = new ArrayList<>();
        String effectivePageLoadError = pageLoadError;

        String sql = "SELECT LocationID, LocationName, Latitude, Longitude, RadiusMeters, IsEnabled FROM geofence_locations WHERE TenantID = ? ORDER BY LocationName";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> loc = new LinkedHashMap<>();
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
            logger.log(Level.SEVERE, "Error fetching geofence locations for TenantID: " + tenantId, e);
            if (effectivePageLoadError == null) effectivePageLoadError = "Error loading location data from the database.";
        }

        boolean isGloballyEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByLocation", "false"));

        request.setAttribute("locations", locations);
        request.setAttribute("isGloballyEnabled", isGloballyEnabled);
        if (effectivePageLoadError != null) request.setAttribute("pageLoadErrorMessage", effectivePageLoadError);
        
        request.setAttribute("pageIsInWizardMode", pageIsActuallyInWizardMode);
        request.setAttribute("wizardReturnStepForJSP", wizardStepToReturnToOnSettingsPage);
        
        request.getRequestDispatcher("/configureLocationRestrictions.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Integer tenantId = getTenantId(request);
        HttpSession session = request.getSession(false);
        
        if (tenantId == null || !isAdmin(request) || session == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Access Denied.", "UTF-8"));
            return;
        }

        String action = request.getParameter("action");
        String successMessage = null;
        String errorMessage = null;

        try (Connection conn = DatabaseConnection.getConnection()) {
            if ("addLocation".equals(action) || "editLocation".equals(action)) {
                String name = request.getParameter("locationName");
                BigDecimal latitude = new BigDecimal(request.getParameter("latitude"));
                BigDecimal longitude = new BigDecimal(request.getParameter("longitude"));
                int radius = Integer.parseInt(request.getParameter("radiusMeters"));
                boolean isEnabled = "true".equalsIgnoreCase(request.getParameter("isEnabled"));

                if ("addLocation".equals(action)) {
                    String sql = "INSERT INTO geofence_locations (TenantID, LocationName, Latitude, Longitude, RadiusMeters, IsEnabled) VALUES (?, ?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setInt(1, tenantId);
                        pstmt.setString(2, name);
                        pstmt.setBigDecimal(3, latitude);
                        pstmt.setBigDecimal(4, longitude);
                        pstmt.setInt(5, radius);
                        pstmt.setBoolean(6, isEnabled);
                        pstmt.executeUpdate();
                        successMessage = "New location '" + name + "' added successfully.";
                        updateSessionLocationFlag(session, tenantId); // <-- UPDATE SESSION
                    }
                } else { // editLocation
                    int locationID = Integer.parseInt(request.getParameter("locationID"));
                    String sql = "UPDATE geofence_locations SET LocationName=?, Latitude=?, Longitude=?, RadiusMeters=?, IsEnabled=? WHERE LocationID=? AND TenantID=?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, name);
                        pstmt.setBigDecimal(2, latitude);
                        pstmt.setBigDecimal(3, longitude);
                        pstmt.setInt(4, radius);
                        pstmt.setBoolean(5, isEnabled);
                        pstmt.setInt(6, locationID);
                        pstmt.setInt(7, tenantId);
                        pstmt.executeUpdate();
                        successMessage = "Location '" + name + "' updated successfully.";
                        updateSessionLocationFlag(session, tenantId); // <-- UPDATE SESSION
                    }
                }
            } else if ("deleteLocation".equals(action)) {
                int locationID = Integer.parseInt(request.getParameter("locationID"));
                String sql = "DELETE FROM geofence_locations WHERE LocationID = ? AND TenantID = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, locationID);
                    pstmt.setInt(2, tenantId);
                    pstmt.executeUpdate();
                    successMessage = "Location deleted successfully.";
                    updateSessionLocationFlag(session, tenantId); // <-- UPDATE SESSION
                }
            } else if ("toggleLocationStatus".equals(action)) {
                // This block handles the AJAX request from the toggle switch
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                String jsonResponse;
                try {
                    int locationID = Integer.parseInt(request.getParameter("locationID"));
                    boolean isEnabled = "true".equalsIgnoreCase(request.getParameter("isEnabled"));
                    String sql = "UPDATE geofence_locations SET IsEnabled = ? WHERE LocationID = ? AND TenantID = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setBoolean(1, isEnabled);
                        pstmt.setInt(2, locationID);
                        pstmt.setInt(3, tenantId);
                        if(pstmt.executeUpdate() > 0) {
                             jsonResponse = "{\"success\": true, \"message\": \"Status updated.\"}";
                             updateSessionLocationFlag(session, tenantId); // <-- UPDATE SESSION
                        } else {
                            throw new SQLException("Location not found.");
                        }
                    }
                } catch (Exception e) {
                     response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                     jsonResponse = "{\"success\": false, \"message\": \"" + e.getMessage() + "\"}";
                }
                response.getWriter().write(jsonResponse);
                return; // IMPORTANT: Return here to avoid redirecting on an AJAX call
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing location action '" + action + "' for TenantID: " + tenantId, e);
            errorMessage = "An error occurred: " + e.getMessage();
        }

        // Redirect back to the configuration page for non-AJAX actions
        if (successMessage != null) {
            session.setAttribute("saveSuccessMessage", successMessage);
        }
        if (errorMessage != null) {
            session.setAttribute("errorMessageJSP", errorMessage);
        }
        response.sendRedirect(request.getContextPath() + "/LocationRestrictionServlet");
    }
}