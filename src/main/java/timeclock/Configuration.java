package timeclock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.db.DatabaseConnection;

public class Configuration {

    private static final Logger logger = Logger.getLogger(Configuration.class.getName());

    private static final String GET_SQL = "SELECT setting_value FROM settings WHERE setting_key = ? AND TenantID = ?";
    private static final String SAVE_SQL = "INSERT INTO settings (TenantID, setting_key, setting_value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";

    /**
     * Retrieves a setting using an EXISTING connection (for use within a transaction).
     * @param con The active database connection.
     * @param tenantId The ID of the tenant.
     * @param key The setting key.
     * @return The value, or null if not found.
     * @throws SQLException if a database error occurs.
     */
    public static String getProperty(Connection con, int tenantId, String key) throws SQLException {
        if (con == null) {
            throw new IllegalArgumentException("Connection object cannot be null.");
        }
        if (tenantId <= 0 || key == null || key.trim().isEmpty()) {
            return null; 
        }

        String value = null;
        try (PreparedStatement ps = con.prepareStatement(GET_SQL)) {
            ps.setString(1, key.trim());
            ps.setInt(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    value = rs.getString("setting_value");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting setting (existing connection) for TenantID=" + tenantId + ", Key=" + key, e);
            throw e;
        }
        return value;
    }

    /**
     * Retrieves a setting using a NEW connection.
     */
    public static String getProperty(int tenantId, String key) {
        String value = null;
        try (Connection con = DatabaseConnection.getConnection()) {
            value = getProperty(con, tenantId, key);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting setting (new connection) for TenantID=" + tenantId + ", Key=" + key, e);
            return null;
        }
        return value;
    }

    public static String getProperty(int tenantId, String key, String defaultValue) {
        String value = getProperty(tenantId, key);
        return (value != null) ? value : defaultValue;
    }
    
    /**
     * NEW: The missing overloaded method that accepts a Connection and a default value.
     * This is the method the servlet was trying to call.
     */
    public static String getProperty(Connection con, int tenantId, String key, String defaultValue) throws SQLException {
         String value = getProperty(con, tenantId, key);
         return (value != null) ? value : defaultValue;
    }

    // --- (Save methods are unchanged) ---
    public static void saveProperty(int tenantId, String key, String value) throws SQLException {
        if (tenantId <= 0 || key == null || key.trim().isEmpty()) { throw new IllegalArgumentException("Invalid input for saveProperty."); }
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(SAVE_SQL)) {
            ps.setInt(1, tenantId);
            ps.setString(2, key.trim());
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }
}