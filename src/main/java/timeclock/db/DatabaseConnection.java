package timeclock.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConnection {

    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());

    // --- Environment Variable Keys ---
    // These are the names of the environment variables you will set.
    private static final String DB_HOST_KEY = "DB_HOST";
    private static final String DB_PORT_KEY = "DB_PORT";
    private static final String DB_NAME_KEY = "DB_NAME";
    private static final String DB_USER_KEY = "DB_USER";
    private static final String DB_PASSWORD_KEY = "DB_PASSWORD";

    // --- Configuration values read from environment variables ---
    private static final String DB_HOST;
    private static final String DB_PORT;
    private static final String DB_NAME;
    private static final String DB_USER;
    private static final String DB_PASSWORD;
    private static final String DB_URL;
    private static final String DB_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    // Static block to load the driver and environment variables when the class is first used.
    static {
        // Load database configuration from environment variables
        DB_HOST = getEnvVariable(DB_HOST_KEY);
        DB_NAME = getEnvVariable(DB_NAME_KEY);
        DB_USER = getEnvVariable(DB_USER_KEY);
        DB_PASSWORD = getEnvVariable(DB_PASSWORD_KEY);
        
        // Use a default port if the environment variable is not set
        String port = System.getenv(DB_PORT_KEY);
        DB_PORT = (port == null || port.isEmpty()) ? "3306" : port;

        // Construct the JDBC URL
        DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME +
                 "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        // Load the MySQL JDBC driver class
        try {
            Class.forName(DB_DRIVER_CLASS);
            logger.info("MySQL JDBC Driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "FATAL: MySQL JDBC Driver not found! Ensure mysql-connector-j.jar is in WEB-INF/lib.", e);
            throw new RuntimeException("Failed to load MySQL driver", e);
        }
    }

    /**
     * Helper method to get an environment variable and throw an error if it's missing.
     * @param key The name of the environment variable.
     * @return The value of the environment variable.
     * @throws RuntimeException if the environment variable is not set.
     */
    private static String getEnvVariable(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            String errorMessage = "FATAL: Required environment variable '" + key + "' is not set.";
            logger.severe(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        return value;
    }

    /**
     * Gets a connection to the MySQL database using credentials from environment variables.
     * @return A Connection object.
     * @throws SQLException if a database access error occurs.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
}