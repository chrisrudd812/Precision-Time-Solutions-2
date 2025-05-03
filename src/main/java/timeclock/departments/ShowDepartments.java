package timeclock.departments;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection; // Use connection pooling

public class ShowDepartments {

    private static final Logger logger = Logger.getLogger(ShowDepartments.class.getName());

    public static String showDepartments() {
        StringBuilder tableRows = new StringBuilder();

        try (Connection con = DatabaseConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM DEPARTMENTS")) {

            while (rs.next()) {
                String name = rs.getString("NAME");
                String description = rs.getString("DESCRIPTION");
                String supervisor = rs.getString("SUPERVISOR");

                // Build the HTML table row *without* onclick
                tableRows.append("<tr><td>")
                       .append(name)
                       .append("</td><td>")
                       .append(description)
                       .append("</td><td>")
                       .append(supervisor)
                       .append("</td></tr>");
            }

            if (tableRows.length() == 0) {
                tableRows.append("<tr><td colspan='4'>No departments found. Click button below to add Departments</td></tr>");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving departments", e);
            tableRows.setLength(0); // Clear any partial results
            tableRows.append("<tr><td colspan='4'>Error retrieving departments: ").append(e.getMessage()).append("</td></tr>");
        }

        return tableRows.toString();
    }
}