package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.Configuration;
import timeclock.util.Helpers; // <-- IMPORT THE HELPERS CLASS

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/SettingsServlet")
public class SettingsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SettingsServlet.class.getName());

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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        HttpSession session = request.getSession(false);
        Integer tenantIdAsInteger = getTenantId(request);

        if (tenantIdAsInteger == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Error: Session invalid or tenant not identified.");
            return;
        }
        int tenantId = tenantIdAsInteger.intValue();

        String userPermissions = (String) session.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("Error: Access Denied.");
            return;
        }

        String settingKey = request.getParameter("settingKey");
        String settingValue = request.getParameter("settingValue");
        
        // --- (No changes to validation logic) ---
        if (settingKey == null || settingKey.trim().isEmpty() || settingValue == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Error: Missing setting key or value was null.");
            return;
        }
        settingKey = settingKey.trim();

        if (settingKey.contains("Threshold") || settingKey.equals("GracePeriod") || settingKey.endsWith("Rate") || settingKey.equals("EmployeeIdStartNumber") || settingKey.equals("EmployeeIdPadding")) {
            try {
                Double.parseDouble(settingValue);
            } catch (NumberFormatException e) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("Error: A valid number is required for " + settingKey);
                return;
            }
        }

        try {
            Configuration.saveProperty(tenantId, settingKey, settingValue);

            // --- NEW LOGIC TO UPDATE THE SESSION ---
            // If the setting we just changed was the main location toggle...
            if ("RestrictByLocation".equals(settingKey)) {
                // ...re-calculate the requirement using our smart helper method...
                boolean locationCheckIsRequired = Helpers.isLocationCheckRequired(tenantId);
                // ...and update the session immediately.
                session.setAttribute("locationCheckIsRequired", locationCheckIsRequired);
            }
            // Handle employee ID start number change
            else if ("EmployeeIdStartNumber".equals(settingKey)) {
                try (Connection con = timeclock.db.DatabaseConnection.getConnection()) {
                    int startNumber = Integer.parseInt(settingValue);
                    
                    // Update existing employees to use the new numbering scheme
                    String updateSql = "UPDATE employee_data SET TenantEmployeeNumber = ? WHERE TenantID = ? AND TenantEmployeeNumber = 1";
                    try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                        psUpdate.setInt(1, startNumber);
                        psUpdate.setInt(2, tenantId);
                        int updated = psUpdate.executeUpdate();
                        if (updated > 0) {
                            logger.info("Updated " + updated + " employee(s) to start with number " + startNumber);
                        }
                    }
                } catch (SQLException sqlEx) {
                    logger.log(Level.WARNING, "Failed to update employee numbering", sqlEx);
                }
            }
            // --- END OF NEW LOGIC ---

            response.getWriter().write("OK");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "[SettingsServlet] Error saving setting for TenantID: " + tenantId + ", Key=" + settingKey, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error: An unexpected server error occurred during save.");
        }
    }
}