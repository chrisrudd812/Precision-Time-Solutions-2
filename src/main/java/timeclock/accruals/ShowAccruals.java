package timeclock.accruals; // Ensure this matches your package

import java.sql.Connection;
import java.sql.PreparedStatement; // Use PreparedStatement
import java.sql.ResultSet;
import java.sql.SQLException;
// import java.sql.Statement; // No longer needed if using PreparedStatement
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;

public class ShowAccruals {

    private static final Logger logger = Logger.getLogger(ShowAccruals.class.getName());

    // Helper to escape HTML, useful if displaying user-inputted data
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    /**
     * Generates HTML table rows for displaying accrual policies for a SPECIFIC tenant.
     * @param tenantId The ID of the tenant whose accrual policies to display.
     * @return String containing HTML table rows (<tr>...</tr>).
     */
    public static String showAccruals(int tenantId) { // Added tenantId parameter
        StringBuilder tableRows = new StringBuilder();
        final int numberOfColumns = 4; // Policy Name, Vacation, Sick, Personal

        logger.info("[ShowAccruals] Called showAccruals for TenantID: " + tenantId);

        if (tenantId <= 0) {
            logger.warning("[ShowAccruals] Invalid TenantID: " + tenantId + ". Returning error row.");
            return "<tr><td colspan='" + numberOfColumns + "' class='report-error-row'>Invalid session or tenant context.</td></tr>";
        }

        // SQL query now filters by TenantID
        String sql = "SELECT NAME, VACATION, SICK, PERSONAL FROM ACCRUALS WHERE TenantID = ? ORDER BY NAME ASC";
        logger.info("[ShowAccruals] SQL Query: " + sql + " with TenantID: " + tenantId);

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, tenantId);
            logger.fine("[ShowAccruals] Executing query for TenantID: " + tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                int rowCount = 0;
                while (rs.next()) {
                    found = true;
                    rowCount++;
                    String name = rs.getString("NAME");
                    int vacation = rs.getInt("VACATION"); // Assuming these are INT in DB
                    int sick = rs.getInt("SICK");
                    int personal = rs.getInt("PERSONAL");

                    logger.fine("[ShowAccruals] Processing DB row " + rowCount + ": Name='" + name + "', Vac='" + vacation + "', Sick='" + sick + "', Pers='" + personal + "'");

                    // Add data-* attributes for JavaScript to use for editing
                    tableRows.append("<tr data-name=\"").append(escapeHtml(name))
                             .append("\" data-vacation=\"").append(vacation)
                             .append("\" data-sick=\"").append(sick)
                             .append("\" data-personal=\"").append(personal)
                             .append("\">");
                    tableRows.append("<td>").append(escapeHtml(name)).append("</td>");
                    tableRows.append("<td>").append(vacation).append("</td>");
                    tableRows.append("<td>").append(sick).append("</td>");
                    tableRows.append("<td>").append(personal).append("</td>");
                    tableRows.append("</tr>\n");
                }
                logger.info("[ShowAccruals] Processed " + rowCount + " accrual policies from DB for TenantID: " + tenantId);

                if (!found) {
                    logger.info("[ShowAccruals] No accrual policies found in DB for TenantID: " + tenantId);
                    tableRows.append("<tr><td colspan='").append(numberOfColumns)
                             .append("' class='report-message-row'>No accrual policies found. Use 'Add Accrual Policy' to create one.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[ShowAccruals] SQLException retrieving accrual policies for TenantID: " + tenantId, e);
            tableRows.setLength(0); // Clear any partial results
            tableRows.append("<tr><td colspan='").append(numberOfColumns)
                     .append("' class='report-error-row'>Error retrieving accrual policies: ").append(escapeHtml(e.getMessage())).append("</td></tr>");
        } catch (Exception e) { // Catch any other unexpected errors
            logger.log(Level.SEVERE, "[ShowAccruals] Unexpected error retrieving accrual policies for TenantID: " + tenantId, e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='").append(numberOfColumns)
                     .append("' class='report-error-row'>An unexpected error occurred.</td></tr>");
        }
        logger.fine("[ShowAccruals] Returning HTML for TenantID " + tenantId + ". Length: " + tableRows.length());
        return tableRows.toString();
    }

    // Removed static block with Derby driver loading, as DatabaseConnection handles connections.
}