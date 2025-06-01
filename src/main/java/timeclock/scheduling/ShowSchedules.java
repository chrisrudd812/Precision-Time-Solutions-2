package timeclock.scheduling; // Ensure this matches your package

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;
import org.apache.commons.text.StringEscapeUtils; // For HTML escaping data attributes

public class ShowSchedules {

    private static final Logger logger = Logger.getLogger(ShowSchedules.class.getName());
    private static final DateTimeFormatter DATA_ATTR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER_AMPM = DateTimeFormatter.ofPattern("hh:mm a");
    private static final String NOT_APPLICABLE_DISPLAY = "N/A";

    private static String escapeHtml(String input) { // Simple escape for display in TD
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String formatTimeForDisplay(Time time) {
        if (time == null) return null;
        try { return time.toLocalTime().format(DISPLAY_TIME_FORMATTER_AMPM); }
        catch (Exception e) { logger.log(Level.WARNING, "Failed to format display time: " + time, e); return null; }
    }

    private static String formatTimeForDataAttr(Time time) {
        if (time == null) return "";
        try { return time.toLocalTime().format(DATA_ATTR_TIME_FORMAT); }
        catch (Exception e) { logger.log(Level.WARNING, "Failed to format data attr time: " + time, e); return ""; }
    }

    public static String showSchedules(int tenantId) {
        StringBuilder tableRows = new StringBuilder();
        final int numberOfColumns = 9; // Adjusted from 10 (WORK_SCHEDULE removed)

        logger.info("[ShowSchedules] Called showSchedules for TenantID: " + tenantId);

        if (tenantId <= 0) {
            logger.warning("[ShowSchedules] Invalid TenantID: " + tenantId);
            return "<tr><td colspan='" + numberOfColumns + "' class='report-error-row'>Invalid session or tenant context.</td></tr>";
        }

        // REMOVED WORK_SCHEDULE from SELECT
        String sql = "SELECT NAME, SHIFT_START, LUNCH_START, LUNCH_END, SHIFT_END, " +
                     "DAYS_WORKED, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH " +
                     // ", WORK_SCHEDULE " + // WORK_SCHEDULE REMOVED
                     "FROM SCHEDULES WHERE TenantID = ? ORDER BY NAME ASC";
        logger.info("[ShowSchedules] SQL: " + sql + " with TenantID: " + tenantId);

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean foundSchedules = false;
                int rowCount = 0;
                while (rs.next()) {
                    foundSchedules = true;
                    rowCount++;
                    String scheduleName = rs.getString("NAME");
                    Time shiftStart = rs.getTime("SHIFT_START");
                    Time lunchStart = rs.getTime("LUNCH_START");
                    Time lunchEnd = rs.getTime("LUNCH_END");
                    Time shiftEnd = rs.getTime("SHIFT_END");
                    String daysWorked = rs.getString("DAYS_WORKED"); // This is the "SMTWTFS" string or similar
                    boolean autoLunch = rs.getBoolean("AUTO_LUNCH");
                    double hoursRequiredDouble = rs.getDouble("HRS_REQUIRED"); // Use getDouble for precision
                    if (rs.wasNull() && !autoLunch) hoursRequiredDouble = 0.0;
                    int lunchLength = rs.getInt("LUNCH_LENGTH");
                    if (rs.wasNull() && !autoLunch) lunchLength = 0;
                    // String workSchedule = rs.getString("WORK_SCHEDULE"); // REMOVED

                    logger.finer("[ShowSchedules] Processing DB row " + rowCount + ": Name='" + scheduleName + "'");

                    String dataName = StringEscapeUtils.escapeHtml4(scheduleName != null ? scheduleName : "");
                    String dataShiftStart = formatTimeForDataAttr(shiftStart);
                    String dataLunchStart = formatTimeForDataAttr(lunchStart);
                    String dataLunchEnd = formatTimeForDataAttr(lunchEnd);
                    String dataShiftEnd = formatTimeForDataAttr(shiftEnd);
                    String dataDaysWorked = StringEscapeUtils.escapeHtml4(daysWorked != null ? daysWorked : "-------"); // Default for data attr if null
                    // String dataWorkSchedule = StringEscapeUtils.escapeHtml4(workSchedule != null ? workSchedule : ""); // REMOVED

                    tableRows.append("<tr data-name='").append(dataName).append("'")
                            .append(" data-shift-start='").append(dataShiftStart).append("'")
                            .append(" data-lunch-start='").append(dataLunchStart).append("'")
                            .append(" data-lunch-end='").append(dataLunchEnd).append("'")
                            .append(" data-shift-end='").append(dataShiftEnd).append("'")
                            .append(" data-days-worked='").append(dataDaysWorked).append("'") // Keep raw "SMTWTFS" string
                            .append(" data-auto-lunch='").append(autoLunch).append("'")
                            .append(" data-hours-required='").append(hoursRequiredDouble).append("'") // Store as double
                            .append(" data-lunch-length='").append(lunchLength).append("'")
                            // .append(" data-work-schedule='").append(dataWorkSchedule).append("'") // REMOVED
                            .append(">");

                    tableRows.append("<td>").append(escapeHtml(scheduleName)).append("</td>");

                    // Special handling for "Open" or "Open w/ Auto Lunch" still applies for time fields
                    boolean isOpenTypeSchedule = "Open".equalsIgnoreCase(scheduleName) || "Open with Auto Lunch".equalsIgnoreCase(scheduleName) || "Open w/ Auto Lunch".equalsIgnoreCase(scheduleName) ;

                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(shiftStart) != null ? formatTimeForDisplay(shiftStart) : NOT_APPLICABLE_DISPLAY)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(lunchStart) != null ? formatTimeForDisplay(lunchStart) : NOT_APPLICABLE_DISPLAY)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(lunchEnd) != null ? formatTimeForDisplay(lunchEnd) : NOT_APPLICABLE_DISPLAY)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(shiftEnd) != null ? formatTimeForDisplay(shiftEnd) : NOT_APPLICABLE_DISPLAY)).append("</td>");

                    // Format daysWorked for display (e.g., "Mon, Tue, Wed")
                    String displayDaysWorked = NOT_APPLICABLE_DISPLAY;
                    if (daysWorked != null && !daysWorked.trim().isEmpty() && !daysWorked.equals("-------")) {
                        StringBuilder formattedDays = new StringBuilder();
                        String[] dayCodes = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                        boolean firstDay = true;
                        for (int i = 0; i < daysWorked.length() && i < dayCodes.length; i++) {
                            if (daysWorked.charAt(i) != '-') { // Assuming '-' means not worked
                                if (!firstDay) formattedDays.append(", ");
                                formattedDays.append(dayCodes[i]);
                                firstDay = false;
                            }
                        }
                        if (formattedDays.length() > 0) displayDaysWorked = formattedDays.toString();
                    } else if (daysWorked != null && daysWorked.equals("-------") && isOpenTypeSchedule) {
                         displayDaysWorked = "All Days (Flexible)";
                    }


                    tableRows.append("<td>").append(escapeHtml(displayDaysWorked)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(autoLunch ? "On" : "Off").append("</td>");
                    tableRows.append("<td class='center-text'>").append(autoLunch ? String.valueOf(hoursRequiredDouble) : NOT_APPLICABLE_DISPLAY).append("</td>");
                    tableRows.append("<td class='center-text'>").append(autoLunch ? (lunchLength + " mins") : NOT_APPLICABLE_DISPLAY).append("</td>");
                    // tableRows.append("<td class='center-text'>").append(escapeHtml(workSchedule != null ? workSchedule : NOT_APPLICABLE_DISPLAY)).append("</td>"); // REMOVED

                    tableRows.append("</tr>\n");
                }
                logger.info("[ShowSchedules] Processed " + rowCount + " schedules for TenantID: " + tenantId);
                if (!foundSchedules) {
                    logger.info("[ShowSchedules] No schedules found in DB for TenantID: " + tenantId);
                    tableRows.append("<tr><td colspan='").append(numberOfColumns).append("' class='report-message-row'>No schedules found. Use 'Add Schedule' to create one.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[ShowSchedules] SQLException retrieving schedules for TenantID: " + tenantId, e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='").append(numberOfColumns).append("' class='report-error-row'>Error retrieving schedules: ").append(escapeHtml(e.getMessage())).append("</td></tr>");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowSchedules] Unexpected error retrieving schedules for TenantID: " + tenantId, e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='").append(numberOfColumns).append("' class='report-error-row'>An unexpected error occurred.</td></tr>");
        }
        return tableRows.toString();
    }
}