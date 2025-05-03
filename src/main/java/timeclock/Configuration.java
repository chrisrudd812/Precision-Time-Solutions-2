package timeclock; // Ensure this package declaration is correct

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
// Removed file I/O imports: FileOutputStream, IOException, InputStream, OutputStream, URISyntaxException, URL
// Removed: java.util.Properties
import java.util.logging.Level;
import java.util.logging.Logger;

// Ensure your DatabaseConnection class provides MySQL connections
import timeclock.db.DatabaseConnection;

/**
 * Manages application configuration settings stored in the database.
 */
public class Configuration {

    private static final Logger logger = Logger.getLogger(Configuration.class.getName());

    // SQL statements for the SETTINGS table
    private static final String GET_SQL = "SELECT setting_value FROM SETTINGS WHERE setting_key = ?";
    // Uses MySQL's convenient "INSERT ... ON DUPLICATE KEY UPDATE ..." syntax
    private static final String SAVE_SQL = "INSERT INTO SETTINGS (setting_key, setting_value) VALUES (?, ?) ON DUPLICATE KEY UPDATE setting_value = ?";

    /**
     * Saves a setting key-value pair to the SETTINGS table in the database.
     * If the key already exists, its value is updated.
     *
     * @param key   The setting key (e.g., "FirstDayOfWeek") - Cannot be null or empty.
     * @param value The setting value (e.g., "Monday") - Can be null.
     */
    public static void saveProperty(String key, String value) {
        // Basic validation
        if (key == null || key.trim().isEmpty()) {
            logger.warning("Attempted to save setting with null or empty key.");
            return; // Do not proceed if key is invalid
        }
        logger.fine("Attempting to save setting to DB: Key='" + key + "', Value='" + value + "'");

        // Use try-with-resources for automatic closing of Connection and PreparedStatement
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(SAVE_SQL)) {

            // Set parameters for the SQL query
            ps.setString(1, key.trim()); // The key to insert or update
            ps.setString(2, value);      // The value to use if inserting
            ps.setString(3, value);      // The value to use if updating

            // Execute the INSERT/UPDATE operation
            int rowsAffected = ps.executeUpdate();

            // Log the outcome (rowsAffected might be 1 for insert, 2 for update, or 0 if value didn't change)
            if (rowsAffected > 0) {
                logger.info("Setting saved/updated successfully in DB: Key=" + key);
            } else {
                 logger.fine("Setting save executed in DB (value might be unchanged): Key=" + key + ", Rows Affected=" + rowsAffected);
            }

        } catch (SQLException e) {
            // Log errors if saving fails
            logger.log(Level.SEVERE, "Error saving setting to database: Key=" + key, e);
            // In a real application, you might re-throw a custom exception here
            // throw new ConfigurationException("Failed to save setting " + key, e);
        }
    }

    /**
     * Retrieves a setting value from the SETTINGS table in the database.
     * Returns null if the key is not found or if a database error occurs.
     *
     * @param key The setting key (e.g., "FirstDayOfWeek") - Cannot be null or empty.
     * @return The setting value as a String, or null if not found or an error occurred.
     */
    public static String getProperty(String key) {
        // Basic validation
        if (key == null || key.trim().isEmpty()) {
            logger.warning("Attempted to get setting with null or empty key.");
            return null;
        }
        logger.fine("Attempting to get setting from DB: Key='" + key + "'");
        String value = null;

        // Use try-with-resources for Connection, PreparedStatement, and ResultSet
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(GET_SQL)) {

            ps.setString(1, key.trim()); // Set the key parameter for the WHERE clause

            try (ResultSet rs = ps.executeQuery()) {
                // Check if a result was returned
                if (rs.next()) {
                    value = rs.getString("setting_value"); // Retrieve the value
                } else {
                    // Key not found in the database
                    logger.fine("Setting not found in DB for Key=" + key);
                }
            } // ResultSet is automatically closed here

        } catch (SQLException e) {
            // Log errors if reading fails
            logger.log(Level.SEVERE, "Error getting setting from database: Key=" + key, e);
            return null; // Return null on error
        } // Connection and PreparedStatement are automatically closed here

        logger.fine("Retrieved setting from DB: Key=" + key + ", Value=" + value);
        return value; // Return the found value or null
    }

     /**
      * Retrieves a setting value from the SETTINGS table, returning a default value
      * if the key is not found or an error occurs.
      *
      * @param key          The setting key (e.g., "FirstDayOfWeek").
      * @param defaultValue The value to return if the key is not found in the database.
      * @return The setting value from the database, or the defaultValue.
      */
     public static String getProperty(String key, String defaultValue) {
         String value = getProperty(key); // Call the primary getProperty method
         return (value != null) ? value : defaultValue; // Return found value or the default
     }

     // Optional: Main method for testing database operations directly (if needed)
     public static void main(String[] args) {
         System.out.println("Testing Configuration class with database...");
         // Example Usage
         saveProperty("TestKey", "TestValue123");
         String testVal = getProperty("TestKey");
         System.out.println("Read TestKey: " + testVal);

         saveProperty("TestKey", "NewValue456");
         testVal = getProperty("TestKey", "Default");
         System.out.println("Read TestKey after update: " + testVal);

         String nonExistent = getProperty("NoSuchKey", "DefaultValueForKey");
         System.out.println("Read NoSuchKey: " + nonExistent);
     }
}