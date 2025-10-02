package timeclock.departments;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
// import java.sql.Statement; // Not used if PreparedStatement is used
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;

public class ShowDepartments {

    private static final Logger logger = Logger.getLogger(ShowDepartments.class.getName());

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    public static String showDepartments(int tenantId) {
        StringBuilder tableRows = new StringBuilder();
        final int numberOfColumns = 3; // Department Name, Description, Department Supervisor


        if (tenantId <= 0) {
            logger.warning("[ShowDepartments] Invalid TenantID: " + tenantId + ". Returning error row.");
            return "<tr><td colspan='" + numberOfColumns + "' class='report-error-row'>Invalid session or tenant context.</td></tr>";
        }

        String sql = "SELECT NAME, DESCRIPTION, SUPERVISOR FROM departments WHERE TenantID = ? ORDER BY NAME ASC";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, tenantId);
            logger.fine("[ShowDepartments] Executing query for TenantID: " + tenantId);

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                int rowCount = 0;
                while (rs.next()) {
                    found = true;
                    rowCount++;
                    String name = rs.getString("NAME");
                    String description = rs.getString("DESCRIPTION");
                    String supervisor = rs.getString("SUPERVISOR");

                    logger.fine("[ShowDepartments] Processing DB row " + rowCount + ": Name='" + name + "', Desc='" + description + "', Super='" + supervisor + "'");

                    tableRows.append("<tr data-name=\"").append(escapeHtml(name != null ? name : ""))
                             .append("\" data-description=\"").append(escapeHtml(description != null ? description : ""))
                             .append("\" data-supervisor=\"").append(escapeHtml(supervisor != null ? supervisor : ""))
                             .append("\">");
                    tableRows.append("<td>").append(escapeHtml(name != null ? name : "")).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(description != null ? description : "")).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(supervisor != null ? supervisor : "")).append("</td>");
                    tableRows.append("</tr>\n");
                }

                if (!found) {
                    tableRows.append("<tr><td colspan='").append(numberOfColumns)
                             .append("' class='report-message-row'>No departments found. Use 'Add Department' to create one.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[ShowDepartments] SQLException retrieving departments for TenantID: " + tenantId, e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='").append(numberOfColumns)
                     .append("' class='report-error-row'>Error retrieving departments: ").append(escapeHtml(e.getMessage())).append("</td></tr>");
        } catch (Exception e) { // Catch any other unexpected errors
            logger.log(Level.SEVERE, "[ShowDepartments] Unexpected error retrieving departments for TenantID: " + tenantId, e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='").append(numberOfColumns)
                     .append("' class='report-error-row'>An unexpected error occurred.</td></tr>");
        }
        logger.fine("[ShowDepartments] Returning HTML for TenantID " + tenantId + ". Length: " + tableRows.length());
        return tableRows.toString();
    }
}