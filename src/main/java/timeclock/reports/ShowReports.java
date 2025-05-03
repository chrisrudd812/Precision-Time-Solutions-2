package timeclock.reports;

import java.sql.*;
import java.text.SimpleDateFormat; // Used only in showExceptionReport currently
import java.time.DayOfWeek; // Needed for helper in ShowPunches, but not directly used here yet
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters; // Needed for helper in ShowPunches
import java.util.HashMap;
import java.util.Locale; // Needed for helper in ShowPunches
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

import timeclock.Configuration; // Keep if needed by other methods
import timeclock.db.DatabaseConnection;


public class ShowReports {

    private static final Logger logger = Logger.getLogger(ShowReports.class.getName());
    private static final String NOT_APPLICABLE_DISPLAY = "N/A"; // Used in archive report

    /**
     * Exception Report - Finds punches with TOTAL = 0 for ACTIVE employees.
     * Returns "NO_EXCEPTIONS" if none found, otherwise HTML table rows.
     * Rows include data-punch-id.
     * Adds class="empty-cell" and placeholder text for null IN/OUT times.
     */
    public static String showExceptionReport() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
        SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss a"); // Use format with seconds
        StringBuilder html = new StringBuilder();
        boolean foundExceptions = false; // Flag to track if any rows are generated

        String sql = "SELECT p.PUNCH_ID, ed.EID, ed.FIRST_NAME, ed.LAST_NAME, p.DATE, p.IN_1, p.OUT_1 "
                + "FROM EMPLOYEE_DATA ed JOIN PUNCHES p ON ed.EID = p.EID "
                + "WHERE p.TOTAL = ? AND ed.ACTIVE = TRUE ORDER BY ed.EID, p.DATE, p.IN_1";

            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement psGetExceptions = con.prepareStatement(sql)) { // Corrected closing parenthesis placement

                psGetExceptions.setDouble(1, 0.0);
                logger.info("Executing Exception Report query (TOTAL = 0.0)...");
                ResultSet rs = psGetExceptions.executeQuery();

                while (rs.next()) {
                    foundExceptions = true; // Mark that we found at least one
                    long punchId = rs.getLong("PUNCH_ID");
                    int eid = rs.getInt("EID");
                    String firstName = rs.getString("FIRST_NAME");
                    String lastName = rs.getString("LAST_NAME");
                    Date date = rs.getDate("DATE");
                    Timestamp inTs = rs.getTimestamp("IN_1");
                    Timestamp outTs = rs.getTimestamp("OUT_1");

                    String formattedDate = (date != null) ? dateFormat.format(date) : "N/A";
                    String inCellClass = "", inCellContent = "";
                    if (inTs != null) { inCellContent = timeFormat.format(inTs); }
                    else { inCellClass = " class='empty-cell'"; inCellContent = "<span class='missing-punch-placeholder'>Missing Punch</span>"; }
                    String outCellClass = "", outCellContent = "";
                    if (outTs != null) { outCellContent = timeFormat.format(outTs); }
                    else { outCellClass = " class='empty-cell'"; outCellContent = "<span class='missing-punch-placeholder'>Missing Punch</span>"; }

                    // Add data-punch-id for potential JS interaction later
                    html.append("<tr data-punch-id=\"").append(punchId).append("\">")
                          .append("<td>").append(eid).append("</td>")
                          .append("<td>").append(firstName != null ? firstName : "").append("</td>")
                          .append("<td>").append(lastName != null ? lastName : "").append("</td>")
                          .append("<td>").append(formattedDate).append("</td>")
                          .append("<td").append(inCellClass).append(">").append(inCellContent).append("</td>")
                          .append("<td").append(outCellClass).append(">").append(outCellContent).append("</td>")
                          .append("</tr>\n");
                }
                rs.close();

                // Check the flag AFTER the loop finishes
                if (!foundExceptions) {
                    logger.info("No exceptions found.");
                    return "NO_EXCEPTIONS";
                } else {
                     logger.info("Found exceptions.");
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error executing exception report query", e);
                // Return HTML row indicating error
                return "<tr><td colspan='6' class='report-error-row'>Error retrieving exceptions from database.</td></tr>";
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error during exception report", e);
                // Return HTML row indicating error
                return "<tr><td colspan='6' class='report-error-row'>An unexpected error occurred while generating the report.</td></tr>";
            }
        return html.toString();
    }

    /**
     * Tardy Report - Summarizes late punches and early outs for active employees.
     * @return HTML table rows or a message row.
     */
    public static String showTardyReport() {
        StringBuilder html = new StringBuilder();
        boolean found = false;
        // Consider adding date range filtering in the future
        String tardyReportQuery = "SELECT ed.eid, ed.first_name, ed.last_name, "
                + "SUM(CASE WHEN p.LATE = TRUE THEN 1 ELSE 0 END) AS late_count, "
                + "SUM(CASE WHEN p.EARLY_OUTS = TRUE THEN 1 ELSE 0 END) AS early_out_count "
                + "FROM employee_data ed "
                // Joining potentially large tables, might need optimization or date limits
                + "LEFT JOIN (SELECT EID, LATE, EARLY_OUTS FROM PUNCHES UNION ALL SELECT EID, LATE, EARLY_OUTS FROM ARCHIVED_PUNCHES) p ON ed.eid = p.EID "
                + "WHERE ed.ACTIVE = TRUE "
                + "GROUP BY ed.eid, ed.first_name, ed.last_name "
                + "HAVING SUM(CASE WHEN p.LATE = TRUE THEN 1 ELSE 0 END) > 0 OR SUM(CASE WHEN p.EARLY_OUTS = TRUE THEN 1 ELSE 0 END) > 0 "
                + "ORDER BY ed.eid";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(tardyReportQuery); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found = true;
                // Add data attributes if needed for JS later
                html.append("<tr data-eid=\"").append(rs.getInt(1)).append("\">")
                    .append("<td>").append(rs.getInt(1)).append("</td><td>").append(rs.getString(2)).append("</td><td>")
                    .append(rs.getString(3)).append("</td><td>").append(rs.getInt(4)).append("</td><td>").append(rs.getInt(5)).append("</td></tr>\n");
            }
            if (!found) { return "<tr><td colspan='5' class='report-message-row'>No tardiness or early outs found.</td></tr>"; }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error running tardy report", e); return "<tr><td colspan='5' class='report-error-row'>Error retrieving tardy report.</td></tr>"; }
        return html.toString();
    }

    /**
     * Who's In Report - Lists currently clocked-in employees.
     * @return HTML table rows or a message row.
     */
    public static String showWhosInReport() {
        StringBuilder html = new StringBuilder();
        boolean found = false;
        // This logic assumes the latest punch for today with a null OUT_1 means "IN".
        // Might need refinement if employees can have multiple IN/OUTs per day without full pairs.
        String whosInQuery = "SELECT ed.EID, ed.FIRST_NAME, ed.LAST_NAME, ed.DEPT, ed.SCHEDULE "
                + "FROM EMPLOYEE_DATA ed "
                + "INNER JOIN PUNCHES p ON ed.EID = p.EID "
                + "WHERE p.DATE = CURRENT_DATE AND ed.ACTIVE = TRUE "
                // Ensure we only get employees whose *latest* punch for today is an IN punch (OUT is null)
                + "AND p.IN_1 = (SELECT MAX(p2.IN_1) FROM PUNCHES p2 WHERE p2.EID = ed.EID AND p2.DATE = CURRENT_DATE) "
                + "AND p.OUT_1 IS NULL "
                + "ORDER BY ed.EID";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(whosInQuery); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                found = true;
                 // Add data attributes if needed for JS later
                html.append("<tr data-eid=\"").append(rs.getInt(1)).append("\">")
                    .append("<td>").append(rs.getInt(1)).append("</td><td>").append(rs.getString(2)).append("</td><td>")
                    .append(rs.getString(3)).append("</td><td>").append(rs.getString(4)).append("</td><td>")
                    .append(rs.getString(5)).append("</td></tr>\n");
            }
            if (!found) { return "<tr><td colspan='5' class='report-message-row'>No employees currently clocked in.</td></tr>"; }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error running who's in report", e); return "<tr><td colspan='5' class='report-error-row'>Error retrieving who's in report.</td></tr>"; }
        return html.toString();
    }

    /**
     * Generates HTML table rows for ARCHIVED punches within a date range for a specific employee.
     * Fetches punches from ARCHIVED_PUNCHES table.
     * Formats timestamps based on the user's specified time zone.
     * Adds data-punch-id attribute.
     *
     * @param eid Employee ID
     * @param userTimeZoneId The IANA Time Zone ID string for the user viewing the page
     * @param startDate The start date of the range (inclusive)
     * @param endDate The end date of the range (inclusive)
     * @return String containing HTML table rows (<tr>...</tr>) or a message row if none found/error.
     */
    public static String showArchivedPunchesReport(int eid, String userTimeZoneId, LocalDate startDate, LocalDate endDate) {
        StringBuilder htmlRows = new StringBuilder();
        logger.info("Executing showArchivedPunchesReport for EID: " + eid + " from " + startDate + " to " + endDate + " with User Zone: " + userTimeZoneId);

        // Validate inputs
        if (eid <= 0) {
             return "<tr><td colspan='5' class='report-error-row'>Invalid Employee ID selected.</td></tr>";
        }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            logger.warning("Invalid date range provided for archived punches query.");
            return "<tr><td colspan='5' class='report-error-row'>Invalid date range selected.</td></tr>";
        }

        // --- Setup Target TimeZone and Formatters ---
        ZoneId targetZone;
        try {
            targetZone = ZoneId.of(userTimeZoneId);
        } catch (Exception e) {
            logger.warning("Invalid userTimeZoneId in showArchivedPunchesReport: '" + userTimeZoneId + "'. Defaulting display to America/Denver.");
            targetZone = ZoneId.of("America/Denver"); // Use consistent fallback
        }

        // Consistent formatters
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a").withZone(targetZone);
        DateTimeFormatter dateFormatterForInstant = DateTimeFormatter.ofPattern("MM/dd/yyyy").withZone(targetZone);
        DateTimeFormatter dateFormatterForLocalDate = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        // final String NOT_APPLICABLE_DISPLAY = "N/A"; // Already defined globally

        // SQL to get data from ARCHIVED_PUNCHES table within the date range
        // Verify column names match your ARCHIVED_PUNCHES table structure
        String sql = "SELECT PUNCH_ID, DATE, IN_1, OUT_1, TOTAL, PUNCH_TYPE " +
                     "FROM ARCHIVED_PUNCHES WHERE EID = ? AND DATE BETWEEN ? AND ? " +
                     "ORDER BY DATE DESC, IN_1 DESC"; // Order most recent first

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, eid);
            ps.setDate(2, java.sql.Date.valueOf(startDate)); // Start date
            ps.setDate(3, java.sql.Date.valueOf(endDate));   // End date

            logger.info("Executing Archived Punches query for EID: " + eid + ", Dates: " + startDate + " to " + endDate);

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    long punchId = rs.getLong("PUNCH_ID"); // Assuming PUNCH_ID exists
                    Date dateDb = rs.getDate("DATE");
                    Timestamp inTsUtc = rs.getTimestamp("IN_1");
                    Timestamp outTsUtc = rs.getTimestamp("OUT_1");
                    double total = rs.getDouble("TOTAL");
                    if (rs.wasNull()) total = 0.0;
                    String punchType = rs.getString("PUNCH_TYPE");

                    Instant inInstant = (inTsUtc != null) ? inTsUtc.toInstant() : null;
                    Instant outInstant = (outTsUtc != null) ? outTsUtc.toInstant() : null;

                    // Date Formatting Logic (same as getPunchTableRows)
                    String formattedDate = NOT_APPLICABLE_DISPLAY;
                    if (inInstant != null) {
                        formattedDate = dateFormatterForInstant.format(inInstant);
                    } else if (dateDb != null) {
                        try { formattedDate = dateDb.toLocalDate().format(dateFormatterForLocalDate); }
                        catch (Exception fmtEx) { logger.warning("Could not format date from ARCHIVED DATE column for punch ID " + punchId + ": " + fmtEx.getMessage()); formattedDate = "Error"; }
                    }

                    String formattedIn = (inInstant != null) ? timeFormatter.format(inInstant) : "";
                    String formattedOut = (outInstant != null) ? timeFormatter.format(outInstant) : "";
                    String formattedTotal = String.format("%.3f", total); // Consistent formatting
                    String safePunchType = (punchType != null ? punchType : "");

                    // Add data-punch-id (using archive punch id). Add class. No onclick.
                    htmlRows.append("<tr class=\"archived-row\" data-punch-id=\"").append(punchId).append("\">")
                            .append("<td>").append(formattedDate).append("</td>")
                            .append("<td>").append(formattedIn).append("</td>")
                            .append("<td>").append(formattedOut).append("</td>")
                            .append("<td style='text-align: right;'>").append(formattedTotal).append("</td>")
                            .append("<td>").append(safePunchType).append("</td>")
                            .append("</tr>\n");
                } // End while loop

                if (!found) {
                    logger.info("No archived punches found for EID " + eid + " in range " + startDate + " to " + endDate);
                    return "<tr><td colspan='5' class='report-message-row'>No archived punch records found for the selected employee and date range.</td></tr>";
                }
            } // ResultSet rs closed
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error fetching archived punches for EID: " + eid, e);
            return "<tr><td colspan='5' class='report-error-row'>Error loading archived punch data from database.</td></tr>";
        } catch (Exception e) {
             logger.log(Level.SEVERE, "Unexpected error fetching archived punches for EID: " + eid, e);
             return "<tr><td colspan='5' class='report-error-row'>An unexpected error occurred while loading archived data.</td></tr>";
        }

        return htmlRows.toString();
    }


    // Time Card Report (Example Stub - Still needs implementation)
    public static Map<String, String> showTimeCardReport(int id) {
        // TODO: Implement actual time card report generation.
        // Requires fetching punches (current and possibly archived depending on date range),
        // calculating daily/weekly totals, potentially applying OT rules more thoroughly.
        // Might reuse logic from ShowPunches.getTimecardPunchData but adapted for report format.
        logger.warning("showTimeCardReport(id) not fully implemented in ShowReports.");
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("html", "<tr><td colspan='5' class='report-message-row'>Time Card Report Not Implemented Yet</td></tr>");
        resultMap.put("employeeName", "Employee " + id); // Need to fetch actual name
        resultMap.put("employeeId", String.valueOf(id));
        // Add other necessary info like date range, totals etc.
        return resultMap;
    }

} // End Class