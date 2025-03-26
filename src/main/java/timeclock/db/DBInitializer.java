package timeclock.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DBInitializer {

    private static boolean initialized = false; // Flag to track initialization

    static {
        try {
             // System.setProperty("derby.system.home", "src/main/webapp/TimeclockDB;create=true");
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace(); // Log the exception
            throw new RuntimeException("Failed to load Derby driver", e); // Critical: Re-throw
        }
    }


    public static synchronized void initialize() {
		 // Check if initialization has already occurred
        if (initialized) {
            return; // Do nothing if already initialized
        }

        try (Connection con = DriverManager.getConnection("jdbc:derby:src/main/webapp/TimeclockDB;create=true")) {
            // Check if the CHRIS schema exists, create it if not
            DatabaseMetaData dbmd = con.getMetaData();
            try (Statement stmt = con.createStatement()) { // Use try-with-resources here too
                ResultSet schemaRs = dbmd.getSchemas();
            	boolean chrisSchemaExists = false;
            	while (schemaRs.next()) {
            	    String schemaName = schemaRs.getString("TABLE_SCHEM");
            	    if ("CHRIS".equalsIgnoreCase(schemaName)) {
            	        chrisSchemaExists = true;
            	        break;
            	    }
            	}
            	schemaRs.close();

                if (!chrisSchemaExists) {
                    stmt.executeUpdate("CREATE SCHEMA CHRIS");
                    System.out.println("Schema CHRIS created.");
                }


                // --- Check if ACCRUALS table exists, create if not ---
                ResultSet rs = dbmd.getTables(null, "CHRIS", "ACCRUALS", null); //Use metadata
                if (!rs.next()) {
                    // Use try-with-resources for the PreparedStatement
                    try (PreparedStatement psCreateTable = con.prepareStatement(
                            "CREATE TABLE CHRIS.ACCRUALS (" +
                            "NAME VARCHAR(30) NOT NULL PRIMARY KEY, " +  // Added primary key constraint
                            "VACATION INT, " +  // Changed to INT
                            "SICK INT, " +      // Changed to INT
                            "PERSONAL INT)")) { // Changed to INT
                        psCreateTable.executeUpdate();
                    }
					 System.out.println("Created table CHRIS.ACCRUALS");
                    // Add some initial data (Optional)
                    try (PreparedStatement psInsert = con.prepareStatement(
                        "INSERT INTO CHRIS.ACCRUALS (NAME, VACATION, SICK, PERSONAL) VALUES (?, ?, ?, ?)"))
                    {
                    	psInsert.setString(1, "None");
                    	psInsert.setInt(2, 0);
                    	psInsert.setInt(3, 0);
                    	psInsert.setInt(4, 0);
                    	psInsert.executeUpdate();

                        psInsert.setString(1, "Standard");
                        psInsert.setInt(2, 5);
                        psInsert.setInt(3, 5);
                        psInsert.setInt(4, 5);
                        psInsert.executeUpdate();

                        psInsert.setString(1, "Executive");
                        psInsert.setInt(2, 30);
                        psInsert.setInt(3, 30);
                        psInsert.setInt(4, 30);
                        psInsert.executeUpdate();
						System.out.println("Added default values.");
                    }
                }
				rs.close();
            }

        } catch (SQLException e) {
            System.out.println("Could not create schema/table");
            e.printStackTrace(); // Log the exception details.
            throw new RuntimeException("Database initialization failed", e); // Critical: Re-throw
        }
         initialized = true;
    }
	//Main Method, for testing ONLY
	public static void main(String[] args) {
		initialize();
	}
}