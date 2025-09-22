package timeclock.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;

/**
 * A utility class containing static helper methods used throughout the application.
 */
public class Helpers {
    private static final Logger logger = Logger.getLogger(Helpers.class.getName());

    /**
     * A private constructor to prevent this utility class from being instantiated.
     */
    private Helpers() {}

    /**
     * A robust check to see if a string is null, empty, or contains common
     * placeholder values like "undefined" or "null".
     * @param s The string to validate.
     * @return true if the string is valid and contains actual content, false otherwise.
     */
    public static boolean isStringValid(String s) {
        return s != null && !s.trim().isEmpty() && !"undefined".equalsIgnoreCase(s.trim()) && !"null".equalsIgnoreCase(s.trim());
    }

    /**
     * Escapes characters in a String to be safe for inclusion in HTML output.
     * @param text The string to escape. Can be null.
     * @return The escaped string, or an empty string if the input was null.
     */
    public static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * A general-purpose sanitizer that trims and escapes a string.
     * @param input The raw string input to sanitize. Can be null.
     * @return The sanitized string, or an empty string if the input was null.
     */
    public static String sanitize(String input) {
        if (input == null) {
            return "";
        }
        return escapeHtml(input.trim());
    }

    /**
     * Quickly determines if a location check is actually required for a given tenant.
     * It checks if the global setting is on AND if at least one geofence location is enabled.
     * @param tenantId The ID of the tenant.
     * @return true if a location check must be performed, false otherwise.
     */
    public static boolean isLocationCheckRequired(int tenantId) {
        boolean isGloballyEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByLocation", "false"));
        if (!isGloballyEnabled) {
            return false; // Feature is off, no check needed.
        }

        String sql = "SELECT 1 FROM geofence_locations WHERE TenantID = ? AND IsEnabled = TRUE LIMIT 1";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                // If a row is found, a check is required.
                return rs.next();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking if location check is required for TenantID: " + tenantId, e);
            // Fail-safe: If the check fails, assume restriction is on to maintain security.
            return true;
        }
    }
}