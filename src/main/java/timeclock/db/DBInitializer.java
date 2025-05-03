package timeclock.db; // Ensure this package matches yours

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
// Removed imports no longer needed: DatabaseMetaData, PreparedStatement

// Ensure AddSampleData class exists and is imported if you use the sample data logic
import timeclock.db.AddSampleData;

/**
 * Initializes database aspects on application startup.
 * For MySQL setup, assumes tables are created externally (e.g., via SQL script).
 * Focuses on conditionally adding sample data if the main table is empty.
 */
public class DBInitializer {

    private static final Logger logger = Logger.getLogger(DBInitializer.class.getName());
    private static boolean initialized = false; // Flag to track initialization WITHIN a single application run

    /**
     * Checks if the EMPLOYEE_DATA table is empty and calls AddSampleData if it is.
     * Assumes DatabaseConnection is configured for MySQL and tables exist.
     * Should be called once on application startup (e.g., by AppLifecycleListener).
     */
    public static synchronized void initialize() {
        // Check if initialization has already occurred IN THIS RUN
        if (initialized) {
            logger.info("DBInitializer: Already initialized within this application run. Skipping.");
            return;
        }
        logger.info("DBInitializer: Starting initialization check (for sample data)...");

        try (Connection con = DatabaseConnection.getConnection()) { // Get connection from updated MySQL connection class

            logger.info("DBInitializer: Connection successful via DatabaseConnection.");

            // --- Check if EMPLOYEE_DATA is empty ---
            boolean employeeTableIsEmpty = false;
            // Use try-with-resources for Statement and ResultSet
            try (Statement stmt = con.createStatement();
                 // Check the EMPLOYEE_DATA table directly (NO "" prefix)
                 ResultSet rsCount = stmt.executeQuery("SELECT COUNT(*) FROM EMPLOYEE_DATA")) {

                // Check if the query returned a result and if the count is 0
                if (rsCount.next() && rsCount.getInt(1) == 0) {
                    employeeTableIsEmpty = true;
                    logger.info("DBInitializer: EMPLOYEE_DATA table found to be empty.");
                } else {
                    // Table has data or count failed
                     logger.info("DBInitializer: EMPLOYEE_DATA table is not empty (or count check failed).");
                }
            } catch (SQLException countEx) {
                 // Log if the COUNT(*) query fails
                 logger.log(Level.WARNING, "DBInitializer: Could not execute 'SELECT COUNT(*)' on EMPLOYEE_DATA.", countEx);
                 // Safely assume table is not empty if we can't check it
                 employeeTableIsEmpty = false;
            }

            // --- Conditionally Add Sample Data ---
            // Make sure AddSampleData class itself has been updated to remove "" prefix from its INSERT statements!
            if (employeeTableIsEmpty) {
                 logger.info("DBInitializer: Attempting to add sample data via AddSampleData.addSampleData()...");
                 try {
                    AddSampleData.addSampleData(); // Call your updated sample data method
                    logger.info("DBInitializer: Sample data addition process presumed complete.");
                 } catch (Exception e) {
                    // Catch potential errors during sample data insertion
                    logger.log(Level.SEVERE, "DBInitializer: Error occurred during AddSampleData.addSampleData() execution.", e);
                 }
            } else {
                 logger.info("DBInitializer: Skipping sample data addition.");
            }
            // --- End Sample Data Logic ---

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "DBInitializer: Database connection or operation failed.", e);
            // Don't mark as initialized if we couldn't even connect or run basic checks
            return;
        } catch (Exception e) {
             // Catch any other unexpected errors
             logger.log(Level.SEVERE, "DBInitializer: Unexpected non-SQL error during execution.", e);
             return;
        }

        // If we reached here without critical errors, mark as initialized for this run
        initialized = true;
        logger.info("DBInitializer: Initialization check complete.");
    }

    // Optional: Main method for basic standalone testing of this initializer's logic
    public static void main(String[] args) {
         System.out.println("--- Running DBInitializer main (tests empty check & sample data add) ---");
         // For standalone testing, you might want to reset the flag if you run it multiple times
         // initialized = false;
         initialize();
         System.out.println("\n--- Second call (should be skipped if first succeeded) ---");
         initialize();
    }
}