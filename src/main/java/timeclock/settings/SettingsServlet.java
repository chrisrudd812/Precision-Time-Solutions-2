package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.Configuration;
import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles saving individual configuration settings via AJAX requests
 * from the settings.jsp page.
 * ** UPDATED for Enhanced Error Handling **
 */
@WebServlet("/SettingsServlet")
public class SettingsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SettingsServlet.class.getName());

    /**
     * Retrieves the TenantID from the current user's session.
     * @param request The HttpServletRequest.
     * @return The TenantID as an Integer, or null if not found or session is invalid.
     */
    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false); // Do not create a new session
        if (session != null) {
            Object tenantIdObj = session.getAttribute("TenantID");
            if (tenantIdObj instanceof Integer) {
                return (Integer) tenantIdObj;
            }
        }
        return null;
    }

    /**
     * Handles POST requests to save a single setting.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        logger.info("[SettingsServlet] Received POST to save a setting.");

        HttpSession session = request.getSession(false);
        Integer tenantIdAsInteger = getTenantId(request);

        // --- Session and Permission Checks ---
        if (tenantIdAsInteger == null) {
            logger.warning("[SettingsServlet] TenantID is null in session. Cannot save setting.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Error: Session invalid or tenant not identified.");
            return;
        }
        int tenantId = tenantIdAsInteger.intValue();

        String userPermissions = (String) session.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            logger.warning("[SettingsServlet] User (TenantID: " + tenantId + ") is not Administrator. Save setting denied.");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Error: Access Denied.");
            return;
        }

        // --- Get Parameters ---
        String settingKey = request.getParameter("settingKey");
        String settingValue = request.getParameter("settingValue");

        logger.info("[SettingsServlet] Attempting to save for TenantID: " + tenantId + ", Key='" + settingKey + "', Value='" + settingValue + "'");
        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");

        // --- Parameter Validation ---
        if (settingKey == null || settingKey.trim().isEmpty() || settingValue == null) {
            logger.warning("[SettingsServlet] Missing settingKey or settingValue (value was null). Key: " + settingKey);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Error: Missing setting key or value was null.");
            return;
        }
        settingKey = settingKey.trim();

        // --- MODIFIED: Server-side Validation to catch empty strings for numeric fields ---
        if (settingKey.contains("Threshold") || settingKey.equals("GracePeriod") || settingKey.endsWith("Rate")) {
            // This block now validates numeric fields even if the value is an empty string.
            try {
                // An empty string will correctly throw a NumberFormatException here.
                Double.parseDouble(settingValue);
            } catch (NumberFormatException e) {
                logger.warning("[SettingsServlet] Invalid number format for Key: " + settingKey + ", Value: '" + settingValue + "'");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                // Provide a user-friendly error message.
                response.getWriter().write("Error: A valid number is required for " + settingKey);
                return;
            }
        }

        // --- Save Setting and Handle Errors ---
        try {
            // Call the 3-argument saveProperty (which now throws SQLException)
            Configuration.saveProperty(tenantId, settingKey, settingValue);

            response.getWriter().write("OK"); // Send "OK" only on full success
            logger.info("[SettingsServlet] Setting saved successfully for TenantID: " + tenantId + ", Key=" + settingKey + ", Value=" + settingValue);

        } catch (IllegalArgumentException e) {
             logger.log(Level.WARNING, "[SettingsServlet] Invalid argument saving setting for TenantID: " + tenantId + ", Key=" + settingKey + ": " + e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Error: Invalid input - " + e.getMessage());
        } catch (SQLException e) { // <-- Catch SQLException explicitly
            logger.log(Level.SEVERE, "[SettingsServlet] SQLException saving setting for TenantID: " + tenantId + ", Key=" + settingKey, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error: Database error during save. Check logs.");
        } catch (Exception e) { // Catch any other unexpected exceptions
            logger.log(Level.SEVERE, "[SettingsServlet] Unexpected error saving setting for TenantID: " + tenantId + ", Key=" + settingKey, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error: An unexpected server error occurred during save.");
        }
    }
}