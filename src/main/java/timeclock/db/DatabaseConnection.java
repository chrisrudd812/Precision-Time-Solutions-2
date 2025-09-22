package timeclock.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class DatabaseConnection implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(DatabaseConnection.class.getName());
    private static HikariDataSource dataSource;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("Initializing HikariCP database connection pool...");
        try {
            // --- ADD THIS LINE TO EXPLICITLY LOAD THE DRIVER ---
            Class.forName("com.mysql.cj.jdbc.Driver");
            
            // --- Load Environment Variables ---
            String dbHost = getEnvVariable("DB_HOST");
            String dbPort = System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306";
            String dbName = getEnvVariable("DB_NAME");
            String dbUser = getEnvVariable("DB_USER");
            String dbPassword = getEnvVariable("DB_PASSWORD");
            String dbUrl = "jdbc:mysql://" + dbHost + ":" + dbPort + "/" + dbName;

            // --- Configure HikariCP ---
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            config.setUsername(dbUser);
            config.setPassword(dbPassword);
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.setMaximumPoolSize(20);

            dataSource = new HikariDataSource(config);
            logger.info("Database connection pool initialized successfully.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "FATAL: Database connection pool could not be initialized!", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        if (dataSource != null) {
            logger.info("Closing database connection pool.");
            dataSource.close();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database connection pool is not available.");
        }
        return dataSource.getConnection();
    }

    private static String getEnvVariable(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            String errorMessage = "FATAL: Required environment variable '" + key + "' is not set.";
            logger.severe(errorMessage);
            throw new RuntimeException(errorMessage);
        }
        return value;
    }
}