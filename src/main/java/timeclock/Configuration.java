package timeclock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.db.DatabaseConnection;

/**
 * Manages application configuration settings stored in the database.
 * Includes methods for saving/getting properties both with new connections
 * and with existing connections (for transactions).
 * ** UPDATED for Multi-Tenancy and Enhanced Error Handling **
 */
public class Configuration {

    private static final Logger logger = Logger.getLogger(Configuration.class.getName());

    // --- SQL statements for the multi-tenant SETTINGS table ---
    private static final String GET_SQL =
        "SELECT setting_value FROM SETTINGS WHERE setting_key = ? AND TenantID = ?";

    private static final String SAVE_SQL =
        "INSERT INTO SETTINGS (TenantID, setting_key, setting_value) VALUES (?, ?, ?) " +
        "ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";

    /**
     * Saves a setting key-value pair for a specific tenant using a NEW connection.
     * If the key already exists for that tenant, its value is updated.
     * This method manages its own connection and **throws SQLException** on failure.
     *
     * @param tenantId The ID of the tenant.
     * @param key      The setting key. Cannot be null or empty.
     * @param value    The setting value. Can be null.
     * @throws SQLException If a database access error occurs.
     */
    public static void saveProperty(int tenantId, String key, String value) throws SQLException {
        // --- Validation ---
        if (tenantId <= 0) {
            logger.warning("Attempted to save setting with invalid TenantID: " + tenantId);
            throw new IllegalArgumentException("Invalid TenantID: " + tenantId);
        }
        if (key == null || key.trim().isEmpty()) {
            logger.warning("Attempted to save setting with null or empty key for TenantID: " + tenantId);
            throw new IllegalArgumentException("Setting key cannot be null or empty.");
        }

        logger.fine("Attempting to save setting (NEW connection) for TenantID=" + tenantId + ": Key='" + key + "', Value='" + value + "'");

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SAVE_SQL)) {

            ps.setInt(1, tenantId);
            ps.setString(2, key.trim());
            ps.setString(3, value);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                logger.info("Setting saved/updated successfully in DB for TenantID=" + tenantId + ", Key=" + key);
            } else {
                 logger.fine("Setting save executed in DB (value might be unchanged): TenantID=" + tenantId + ", Key=" + key + ", Rows Affected=" + rowsAffected);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error saving setting (NEW connection) to database for TenantID=" + tenantId + ", Key=" + key, e);
            throw e; // --- Re-throw the exception ---
        }
    }

    /**
     * Saves a setting key-value pair using an *existing* database connection.
     * This allows the save operation to participate in a larger transaction.
     * It DOES NOT close the provided connection. It WILL throw SQLException on failure.
     *
     * @param con      The existing database connection. Must not be null.
     * @param tenantId The ID of the tenant.
     * @param key      The setting key.
     * @param value    The setting value.
     * @throws SQLException If a database access error occurs.
     * @throws IllegalArgumentException If con is null or inputs are invalid.
     */
    public static void saveProperty(Connection con, int tenantId, String key, String value) throws SQLException {
        if (con == null) {
            throw new IllegalArgumentException("Connection object cannot be null when using transactional saveProperty.");
        }
        if (tenantId <= 0) {
            logger.warning("Attempted to save setting with invalid TenantID: " + tenantId);
            throw new IllegalArgumentException("Invalid TenantID: " + tenantId);
        }
        if (key == null || key.trim().isEmpty()) {
            logger.warning("Attempted to save setting with null or empty key for TenantID: " + tenantId);
            throw new IllegalArgumentException("Setting key cannot be null or empty.");
        }

        logger.fine("Attempting to save setting (EXISTING connection) for TenantID=" + tenantId + ": Key='" + key + "'");

        try (PreparedStatement ps = con.prepareStatement(SAVE_SQL)) {
            ps.setInt(1, tenantId);
            ps.setString(2, key.trim());
            ps.setString(3, value);

            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                logger.info("Setting saved/updated successfully (existing connection) for TenantID=" + tenantId + ", Key=" + key);
            } else {
                 logger.fine("Setting save executed (existing connection): TenantID=" + tenantId + ", Key=" + key + ", Rows Affected=" + rowsAffected);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error saving setting (existing connection) for TenantID=" + tenantId + ", Key=" + key, e);
            throw e; // Re-throw the exception so the caller knows it failed
        }
    }

    /**
     * Retrieves a setting value for a specific tenant from the database.
     * Returns null if the key is not found for that tenant or if a database error occurs.
     *
     * @param tenantId The ID of the tenant.
     * @param key      The setting key.
     * @return The setting value as a String, or null if not found or an error occurred.
     */
    public static String getProperty(int tenantId, String key) {
        if (tenantId <= 0) { logger.warning("Attempted to get setting with invalid TenantID: " + tenantId); return null; }
        if (key == null || key.trim().isEmpty()) { logger.warning("Attempted to get setting with null or empty key for TenantID: " + tenantId); return null; }

        logger.fine("Attempting to get setting from DB for TenantID=" + tenantId + ": Key='" + key + "'");
        String value = null;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(GET_SQL)) {

            ps.setString(1, key.trim());
            ps.setInt(2, tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    value = rs.getString("setting_value");
                } else {
                    logger.fine("Setting not found in DB for TenantID=" + tenantId + ", Key=" + key);
                }
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error getting setting from database for TenantID=" + tenantId + ", Key=" + key, e);
            return null; // Return null on error
        }

        logger.fine("Retrieved setting from DB for TenantID=" + tenantId + ", Key=" + key + ", Value=" + value);
        return value;
    }

     /**
      * Retrieves a setting value, returning a default value if not found or an error occurs.
      *
      * @param tenantId     The ID of the tenant.
      * @param key          The setting key.
      * @param defaultValue The value to return if the key is not found.
      * @return The setting value or the defaultValue.
      */
     public static String getProperty(int tenantId, String key, String defaultValue) {
         String value = getProperty(tenantId, key);
         return (value != null) ? value : defaultValue;
     }

     /**
      * Main method for basic testing.
      */
     public static void main(String[] args) {
         System.out.println("Testing Multi-Tenant Configuration class with database...");
         int testTenantId = 1; // Use a valid test tenant ID

         System.out.println("\n--- Testing Tenant " + testTenantId + " ---");
         try {
             // Example Usage for Tenant 1
             saveProperty(testTenantId, "TestKey", "Tenant1Value123_NEW");
             String testVal = getProperty(testTenantId, "TestKey");
             System.out.println("Read TestKey for Tenant " + testTenantId + ": " + testVal);

             saveProperty(testTenantId, "TestKey", "Tenant1NewValue456_NEW");
             testVal = getProperty(testTenantId, "TestKey", "Default");
             System.out.println("Read TestKey after update for Tenant " + testTenantId + ": " + testVal);

             String nonExistent = getProperty(testTenantId, "NoSuchKey", "DefaultValueForKey");
             System.out.println("Read NoSuchKey for Tenant " + testTenantId + ": " + nonExistent);
         } catch(SQLException e) {
             System.err.println("A database error occurred during testing: " + e.getMessage());
             e.printStackTrace();
         }
     }
}