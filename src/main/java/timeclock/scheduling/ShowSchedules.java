package timeclock.scheduling;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;
import org.apache.commons.text.StringEscapeUtils;

public class ShowSchedules {

    private static final Logger logger = Logger.getLogger(ShowSchedules.class.getName());
    private static final DateTimeFormatter DATA_ATTR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER_AMPM = DateTimeFormatter.ofPattern("hh:mm a");
    private static final String NOT_APPLICABLE_DISPLAY = "N/A";

    private static String escapeHtml(String input) {
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
        final int numberOfColumns = 9;

        logger.info("[ShowSchedules] Called showSchedules for TenantID: " + tenantId);

        if (tenantId <= 0) {
            logger.warning("[ShowSchedules] Invalid TenantID: " + tenantId);
            return "<tr><td colspan='" + numberOfColumns + "' class='report-error-row'>Invalid session or tenant context.</td></tr>";
        }

        String sql = "SELECT NAME, SHIFT_START, LUNCH_START, LUNCH_END, SHIFT_END, " +
                     "DAYS_WORKED, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH " +
                     "FROM SCHEDULES WHERE TenantID = ? ORDER BY NAME ASC";
        logger.info("[ShowSchedules] SQL: " + sql + " with TenantID: " + tenantId);

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean foundSchedules = false;
                while (rs.next()) {
                    foundSchedules = true;
                    String scheduleName = rs.getString("NAME");
                    Time shiftStart = rs.getTime("SHIFT_START");
                    Time lunchStart = rs.getTime("LUNCH_START");
                    Time lunchEnd = rs.getTime("LUNCH_END");
                    Time shiftEnd = rs.getTime("SHIFT_END");
                    String daysWorked = rs.getString("DAYS_WORKED");
                    boolean autoLunch = rs.getBoolean("AUTO_LUNCH");
                    double hoursRequiredDouble = rs.getDouble("HRS_REQUIRED");
                    if (rs.wasNull() && !autoLunch) hoursRequiredDouble = 0.0;
                    int lunchLength = rs.getInt("LUNCH_LENGTH");
                    if (rs.wasNull() && !autoLunch) lunchLength = 0;

                    String dataName = StringEscapeUtils.escapeHtml4(scheduleName != null ? scheduleName : "");
                    String dataShiftStart = formatTimeForDataAttr(shiftStart);
                    String dataLunchStart = formatTimeForDataAttr(lunchStart);
                    String dataLunchEnd = formatTimeForDataAttr(lunchEnd);
                    String dataShiftEnd = formatTimeForDataAttr(shiftEnd);
                    
                    // *** MODIFICATION: Ensure data-days-worked stores the raw SMTWHFA string from DB ***
                    String dataDaysWorked = StringEscapeUtils.escapeHtml4(daysWorked != null ? daysWorked : "-------");

                    tableRows.append("<tr data-name='").append(dataName).append("'")
                            .append(" data-shift-start='").append(dataShiftStart).append("'")
                            .append(" data-lunch-start='").append(dataLunchStart).append("'")
                            .append(" data-lunch-end='").append(dataLunchEnd).append("'")
                            .append(" data-shift-end='").append(dataShiftEnd).append("'")
                            .append(" data-days-worked='").append(dataDaysWorked).append("'") // Correctly stores the raw string now
                            .append(" data-auto-lunch='").append(autoLunch).append("'")
                            .append(" data-hours-required='").append(hoursRequiredDouble).append("'")
                            .append(" data-lunch-length='").append(lunchLength).append("'")
                            .append(">");

                    tableRows.append("<td>").append(escapeHtml(scheduleName)).append("</td>");

                    boolean isOpenTypeSchedule = "Open".equalsIgnoreCase(scheduleName) || "Open with Auto Lunch".equalsIgnoreCase(scheduleName) || "Open w/ Auto Lunch".equalsIgnoreCase(scheduleName) ;

                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(shiftStart) != null ? formatTimeForDisplay(shiftStart) : NOT_APPLICABLE_DISPLAY)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(lunchStart) != null ? formatTimeForDisplay(lunchStart) : NOT_APPLICABLE_DISPLAY)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(lunchEnd) != null ? formatTimeForDisplay(lunchEnd) : NOT_APPLICABLE_DISPLAY)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(escapeHtml(formatTimeForDisplay(shiftEnd) != null ? formatTimeForDisplay(shiftEnd) : NOT_APPLICABLE_DISPLAY)).append("</td>");

                    String displayDaysWorked = NOT_APPLICABLE_DISPLAY;
                    if (daysWorked != null && !daysWorked.trim().isEmpty() && !daysWorked.equals("-------")) {
                        StringBuilder formattedDays = new StringBuilder();
                        String[] dayCodes = {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"};
                        boolean firstDay = true;
                        // Corrected logic to handle SMTWHFA format
                        if (daysWorked.length() == 7) {
                            if (daysWorked.charAt(0) == 'S') { formattedDays.append(dayCodes[0]); firstDay = false; }
                            if (daysWorked.charAt(1) == 'M') { if (!firstDay) formattedDays.append(", "); formattedDays.append(dayCodes[1]); firstDay = false; }
                            if (daysWorked.charAt(2) == 'T') { if (!firstDay) formattedDays.append(", "); formattedDays.append(dayCodes[2]); firstDay = false; }
                            if (daysWorked.charAt(3) == 'W') { if (!firstDay) formattedDays.append(", "); formattedDays.append(dayCodes[3]); firstDay = false; }
                            if (daysWorked.charAt(4) == 'H') { if (!firstDay) formattedDays.append(", "); formattedDays.append(dayCodes[4]); firstDay = false; }
                            if (daysWorked.charAt(5) == 'F') { if (!firstDay) formattedDays.append(", "); formattedDays.append(dayCodes[5]); firstDay = false; }
                            if (daysWorked.charAt(6) == 'A') { if (!firstDay) formattedDays.append(", "); formattedDays.append(dayCodes[6]); firstDay = false; }
                        }
                        if (formattedDays.length() > 0) displayDaysWorked = formattedDays.toString();
                    } else if (isOpenTypeSchedule) {
                         displayDaysWorked = "All Days (Flexible)";
                    }

                    tableRows.append("<td>").append(escapeHtml(displayDaysWorked)).append("</td>");
                    tableRows.append("<td class='center-text'>").append(autoLunch ? "On" : "Off").append("</td>");
                    tableRows.append("<td class='center-text'>").append(autoLunch ? String.valueOf(hoursRequiredDouble) : NOT_APPLICABLE_DISPLAY).append("</td>");
                    tableRows.append("<td class='center-text'>").append(autoLunch ? (lunchLength + " mins") : NOT_APPLICABLE_DISPLAY).append("</td>");
                    tableRows.append("</tr>\n");
                }
                if (!foundSchedules) {
                    tableRows.append("<tr><td colspan='").append(numberOfColumns).append("' class='report-message-row'>No schedules found. Use 'Add Schedule' to create one.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[ShowSchedules] SQLException for TenantID: " + tenantId, e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='").append(numberOfColumns).append("' class='report-error-row'>Error retrieving schedules: ").append(escapeHtml(e.getMessage())).append("</td></tr>");
        }
        return tableRows.toString();
    }
}