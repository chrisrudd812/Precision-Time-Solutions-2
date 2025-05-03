package timeclock.scheduling;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List; // Import List
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;
// Import needed for escaping HTML attributes
import org.apache.commons.text.StringEscapeUtils; // Add Apache Commons Text dependency if not present

public class ShowSchedules {

    private static final Logger logger = Logger.getLogger(ShowSchedules.class.getName());
    // Formatter for time in data attributes (suitable for <input type="time">)
    private static final DateTimeFormatter DATA_ATTR_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    // Formatter for display time
    private static final DateTimeFormatter DISPLAY_TIME_FORMATTER_AMPM = DateTimeFormatter.ofPattern("hh:mm a");
    private static final String NOT_APPLICABLE_DISPLAY = "N/A";

    // Helper method to format time for display, returning null if time is null
    private static String formatTimeForDisplay(Time time) {
        if (time == null) return null;
        try {
            return time.toLocalTime().format(DISPLAY_TIME_FORMATTER_AMPM);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to format display time: " + time, e);
            return null;
        }
    }

    // Helper method to format time for data attributes (HH:mm), returns empty string if null
     private static String formatTimeForDataAttr(Time time) {
         if (time == null) return ""; // Use empty string for null in data attributes
         try {
             return time.toLocalTime().format(DATA_ATTR_TIME_FORMAT);
         } catch (Exception e) {
             logger.log(Level.WARNING, "Failed to format data attribute time: " + time, e);
             return ""; // Return empty on error
         }
     }

    // Helper method to format lunch length display string (incl. N/A), never null
    private static String formatLunchLengthDisplay(int length, boolean isApplicable) {
        if (!isApplicable) return NOT_APPLICABLE_DISPLAY;
        return String.valueOf(length) + " minutes";
    }

     // Helper method to format hours required display string (incl. N/A), never null
    private static String formatHoursRequiredDisplay(int hours, boolean isApplicable) {
        if (!isApplicable) return NOT_APPLICABLE_DISPLAY;
        return String.valueOf(hours);
    }

    /**
     * Generates HTML table rows with data attributes for editing.
     * Handles special display logic for "Open".
     * Dynamically merges consecutive blank/empty cells for other schedules.
     * @return String containing HTML table rows (<tr>...</tr>).
     */
    public static String showSchedules() {
        StringBuilder tableRows = new StringBuilder();
        String sql = "SELECT NAME, SHIFT_START, LUNCH_START, LUNCH_END, SHIFT_END, " +
                     "DAYS_WORKED, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH, WORK_SCHEDULE " +
                     "FROM SCHEDULES ORDER BY NAME";

        try (Connection con = DatabaseConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            boolean foundSchedules = false;
            while (rs.next()) {
                foundSchedules = true;
                // --- Retrieve All Raw Data ---
                String scheduleName = rs.getString("NAME");
                Time shiftStart = rs.getTime("SHIFT_START");
                Time lunchStart = rs.getTime("LUNCH_START");
                Time lunchEnd = rs.getTime("LUNCH_END");
                Time shiftEnd = rs.getTime("SHIFT_END");
                String daysWorked = rs.getString("DAYS_WORKED");
                boolean autoLunch = rs.getBoolean("AUTO_LUNCH");
                int hoursRequired = rs.getInt("HRS_REQUIRED");
                if (rs.wasNull()) hoursRequired = 0;
                int lunchLength = rs.getInt("LUNCH_LENGTH");
                if (rs.wasNull()) lunchLength = 0;
                String workSchedule = rs.getString("WORK_SCHEDULE");

                // --- Format values needed for data attributes or display ---
                String dataShiftStart = formatTimeForDataAttr(shiftStart);
                String dataLunchStart = formatTimeForDataAttr(lunchStart);
                String dataLunchEnd = formatTimeForDataAttr(lunchEnd);
                String dataShiftEnd = formatTimeForDataAttr(shiftEnd);
                // Escape potential quotes/special chars in string attributes
                String dataName = StringEscapeUtils.escapeHtml4(scheduleName != null ? scheduleName : "");
                String dataDaysWorked = StringEscapeUtils.escapeHtml4(daysWorked != null ? daysWorked : "");
                String dataWorkSchedule = StringEscapeUtils.escapeHtml4(workSchedule != null ? workSchedule : "");


                // --- Build HTML Row ---
                tableRows.append("<tr")
                       // Add class based on actual autoLunch data value
                       .append(autoLunch ? " class='auto-lunch-on'" : "")
                       // *** Add data-* attributes for JavaScript Edit function ***
                       .append(" data-name='").append(dataName).append("'")
                       .append(" data-shift-start='").append(dataShiftStart).append("'")
                       .append(" data-lunch-start='").append(dataLunchStart).append("'")
                       .append(" data-lunch-end='").append(dataLunchEnd).append("'")
                       .append(" data-shift-end='").append(dataShiftEnd).append("'")
                       .append(" data-days-worked='").append(dataDaysWorked).append("'")
                       .append(" data-auto-lunch='").append(autoLunch).append("'") // "true" or "false"
                       .append(" data-hours-required='").append(hoursRequired).append("'") // Raw number
                       .append(" data-lunch-length='").append(lunchLength).append("'") // Raw number
                       .append(" data-work-schedule='").append(dataWorkSchedule).append("'")
                       .append(">"); // End of opening <tr> tag

                // 1. Append Schedule Name cell (always present)
                tableRows.append("<td>").append(scheduleName).append("</td>"); // Display original unescaped name

                // 2. Handle "Open" schedule explicitly first
                if ("Open".equalsIgnoreCase(scheduleName)) {
                    // Append the single N/A cell spanning 9 columns
                    tableRows.append("<td colspan='9' style='text-align: center; font-style: italic; color: #555;'>")
                           .append(NOT_APPLICABLE_DISPLAY)
                           .append("</td>");
                } else {
                    // --- Case 2: All other schedules (Use dynamic merging for display) ---

                    // Prepare List of Cell Content (String or null if truly empty/blank) for DISPLAY
                    List<String> cellData = new ArrayList<>();
                    cellData.add(formatTimeForDisplay(shiftStart)); // Use display format helper
                    cellData.add(formatTimeForDisplay(lunchStart));
                    cellData.add(formatTimeForDisplay(lunchEnd));
                    cellData.add(formatTimeForDisplay(shiftEnd));
                    String safeDaysWorked = (daysWorked != null ? daysWorked.trim() : null);
                    cellData.add(safeDaysWorked == null || safeDaysWorked.isEmpty() ? null : safeDaysWorked);
                    cellData.add(autoLunch ? "On" : "Off");
                    cellData.add(!autoLunch ? null : formatHoursRequiredDisplay(hoursRequired, true));
                    cellData.add(!autoLunch ? null : formatLunchLengthDisplay(lunchLength, true));
                    String safeWorkSchedule = (workSchedule != null ? workSchedule.trim() : null);
                    cellData.add(safeWorkSchedule == null || safeWorkSchedule.isEmpty() ? null : safeWorkSchedule);

                    // --- Dynamic Merging Loop for DISPLAY ---
                    int consecutiveNullCount = 0;
                    final String NA_MERGED_CELL_FORMAT = "<td colspan='%d' style='text-align: center; font-style: italic; color: #555;'>N/A</td>";
                    for (int i = 0; i < cellData.size(); i++) {
                        String value = cellData.get(i);
                        if (value == null) {
                            consecutiveNullCount++;
                        } else {
                            if (consecutiveNullCount > 0) {
                                tableRows.append(String.format(NA_MERGED_CELL_FORMAT, consecutiveNullCount));
                                consecutiveNullCount = 0;
                            }
                            // Add data-length attribute to the Lunch Length display cell (index 7) if autoLunch is ON
                            if (i == 7 && autoLunch) {
                                 tableRows.append("<td data-length='").append(lunchLength).append("'>");
                            } else {
                                 tableRows.append("<td>");
                            }
                            tableRows.append(value).append("</td>");
                        }
                    }
                    if (consecutiveNullCount > 0) { // Trailing nulls
                        tableRows.append(String.format(NA_MERGED_CELL_FORMAT, consecutiveNullCount));
                    }
                    // --- End Dynamic Merging Loop ---
                } // End else (for non-"Open" schedules)

                // Close the table row
                tableRows.append("</tr>\n");

            } // End while loop

            if (!foundSchedules) {
                tableRows.append("<tr><td colspan='10' style='text-align: center;'>No schedules found. Click button below to add a schedule.</td></tr>");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving schedules from database", e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='10' style='color: red; text-align: center;'>Error retrieving schedules: ").append(e.getMessage()).append("</td></tr>");
        } catch (Exception e) {
             logger.log(Level.SEVERE, "Unexpected error generating schedule list", e);
             tableRows.setLength(0);
             tableRows.append("<tr><td colspan='10' style='color: red; text-align: center;'>An unexpected error occurred generating the schedule list.</td></tr>");
        }

        return tableRows.toString();
    }
}