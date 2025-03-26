package timeclock.accruals;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection; // Use connection pooling

public class ShowAccruals {

    private static final Logger logger = Logger.getLogger(ShowAccruals.class.getName());

    public static String showAccruals() {
        StringBuilder tableRows = new StringBuilder();

        try (Connection con = DatabaseConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM CHRIS.ACCRUALS")) {

            while (rs.next()) {
                String name = rs.getString("NAME");
                String vacation = rs.getString("VACATION");
                String sick = rs.getString("SICK");
                String personal = rs.getString("PERSONAL");

                // Build the HTML table row *without* onclick
                tableRows.append("<tr><td>")
                       .append(name)
                       .append("</td><td>")
                       .append(vacation)
                       .append("</td><td>")
                       .append(sick)
                       .append("</td><td>")
                       .append(personal)
                       .append("</td></tr>");
            }

            if (tableRows.length() == 0) {
                tableRows.append("<tr><td colspan='4'>No policies found. Click button below to add Accrual Policy</td></tr>");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving accrual policies", e);
            tableRows.setLength(0); // Clear any partial results
            tableRows.append("<tr><td colspan='4'>Error retrieving accrual policies: ").append(e.getMessage()).append("</td></tr>");
        }

        return tableRows.toString();
    }
}