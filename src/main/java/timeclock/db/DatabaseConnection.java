package timeclock.db;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource; // Import DataSource
import org.apache.derby.jdbc.EmbeddedDataSource;

public class DatabaseConnection {

    private static DataSource dataSource;

    // Private constructor to prevent direct instantiation
    private DatabaseConnection() {}

    // Initialize the DataSource (only once)
    public static synchronized void initDataSource() {
        if (dataSource == null) {
            EmbeddedDataSource ds = new EmbeddedDataSource();
            ds.setDatabaseName("src/main/webapp/TimeclockDB");
            ds.setCreateDatabase("create"); // Create if it doesn't exist
            dataSource = ds;
        }
    }

    // Get a connection from the DataSource
    public static Connection getConnection() throws SQLException {
        if (dataSource == null) {
            initDataSource(); // Initialize if not already done
        }
        return dataSource.getConnection();
    }
}