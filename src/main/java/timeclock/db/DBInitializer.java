package timeclock.db; // Ensure this package matches yours

import java.util.logging.Logger;

/**
 * MODIFIED FOR PRODUCTION:
 * This initializer is now disabled. In a production environment, database
 * setup and data seeding should be handled by explicit migration scripts, not
 * automatically by the application.
 */
public class DBInitializer {

    private static final Logger logger = Logger.getLogger(DBInitializer.class.getName());
    private static boolean initialized = false;

    /**
     * MODIFIED FOR PRODUCTION:
     * This method is now a no-op (it does nothing). It will log a message
     * and return immediately to prevent sample data from being added to the live database.
     */
    public static synchronized void initialize() {
        if (!initialized) {
            logger.info("DBInitializer: In production environment. Sample data initialization is SKIPPED.");
            initialized = true; // Mark as "initialized" to prevent repeated logging.
        }
        // The original logic to check tables and add data has been removed.
    }

    // The main method is left for consistency but has no effect in the deployed app.
    public static void main(String[] args) {
         System.out.println("--- Running DBInitializer main (Production Mode: Logic is disabled) ---");
         initialize();
    }
}