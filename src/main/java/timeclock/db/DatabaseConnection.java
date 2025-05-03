package timeclock.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseConnection {

    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());

    // --- MySQL Configuration ---
    // *** IMPORTANT: UPDATE THESE VALUES ***
    private static final String DB_HOST = "localhost"; // Or "127.0.0.1"
    private static final String DB_PORT = "3306";     // Default MySQL port
    private static final String DB_NAME = "timeclock_db"; // The database you created
    private static final String DB_USER = "chris"; 
    private static final String DB_PASSWORD = "root"; 

    // Construct the JDBC URL for MySQL
    // Includes parameters for timezone handling and SSL (adjust SSL for production)
    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME +
                                        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

    // MySQL Driver Class Name (for Connector/J 8.x)
    private static final String DB_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";

    // Static block to load the driver when the class is loaded
    static {
        try {
            // Load the MySQL JDBC driver class
            Class.forName(DB_DRIVER_CLASS);
            logger.info("MySQL JDBC Driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, "FATAL: MySQL JDBC Driver not found in classpath! " +
                                 "Ensure mysql-connector-j-....jar is in WEB-INF/lib.", e);
            throw new RuntimeException("Failed to load MySQL driver", e);
        }
    }

    /**
     * Gets a connection to the MySQL database.
     * Remember to close the connection in a finally block or use try-with-resources
     * where this method is called.
     * @return A Connection object.
     * @throws SQLException if a database access error occurs (e.g., wrong password, DB down)
     */
    public static Connection getConnection() throws SQLException {
        
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

}