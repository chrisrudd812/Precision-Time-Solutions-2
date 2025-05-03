package timeclock.punches;

// --- Full list of necessary imports ---
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;


public class ShowPunches {

    private static final Logger logger = Logger.getLogger(ShowPunches.class.getName());
    private static final String NOT_APPLICABLE_DISPLAY = "N/A";
    private static final double ROUNDING_FACTOR = 10000.0;
    private static final String SCHEDULE_DEFAULT_ZONE_ID_STR = "America/Denver"; // Default if not specified elsewhere
    private static final double WEEKLY_OT_THRESHOLD = 40.0; // Default OT threshold

    // --- Utility Methods ---
    public static boolean isValid(String s) {
        return s != null && !s.trim().isEmpty();
    }

    public static void setOptionalDouble(PreparedStatement ps, int i, Double d) throws SQLException {
        if (d != null && !d.isNaN() && !d.isInfinite()) {
            ps.setDouble(i, d);
        } else {
            ps.setNull(i, Types.DOUBLE);
        }
    }

    public static void setOptionalTimestamp(PreparedStatement ps, int i, Timestamp ts) throws SQLException {
        if (ts != null) {
            ps.setTimestamp(i, ts);
        } else {
            ps.setNull(i, Types.TIMESTAMP);
        }
    }

    public static LocalDate calculateWeekStart(LocalDate currentDate, String firstDayOfWeekSetting) {
        DayOfWeek firstDay;
        try {
            firstDay = DayOfWeek.valueOf(firstDayOfWeekSetting.trim().toUpperCase(Locale.ENGLISH));
        } catch (Exception e) {
            firstDay = DayOfWeek.SUNDAY; // Default to Sunday on error
            logger.warning("Invalid FirstDayOfWeek setting '" + firstDayOfWeekSetting + "', defaulting to SUNDAY.");
        }
        return currentDate.with(TemporalAdjusters.previousOrSame(firstDay));
    }

    // --- Data Fetching Methods ---

    /** Fetches basic employee and schedule info needed for timecard display and calculations */
    public static Map<String, Object> getEmployeeTimecardInfo(int eid) {
        Map<String, Object> info = new HashMap<>();
        String sql = "SELECT e.EID, e.FIRST_NAME, e.LAST_NAME, e.SCHEDULE AS ScheduleName, e.WAGE_TYPE, " +
                     "s.SHIFT_START, s.SHIFT_END, s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH, " +
                     "e.VACATION_HOURS, e.SICK_HOURS, e.PERSONAL_HOURS " +
                     "FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.SCHEDULE = s.NAME WHERE e.EID = ?";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, eid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info.put("eid", rs.getInt("EID"));
                    info.put("employeeName", rs.getString("FIRST_NAME") + " " + rs.getString("LAST_NAME"));
                    info.put("scheduleName", rs.getString("ScheduleName"));
                    info.put("wageType", rs.getString("WAGE_TYPE"));
                    info.put("shiftStart", rs.getTime("SHIFT_START"));
                    info.put("shiftEnd", rs.getTime("SHIFT_END"));
                    info.put("autoLunch", rs.getBoolean("AUTO_LUNCH"));
                    int hrsRequired = rs.getInt("HRS_REQUIRED"); info.put("hoursRequired", rs.wasNull() ? null : hrsRequired);
                    int lunchLength = rs.getInt("LUNCH_LENGTH"); info.put("lunchLength", rs.wasNull() ? null : lunchLength);
                    info.put("vacationHours", rs.getDouble("VACATION_HOURS"));
                    info.put("sickHours", rs.getDouble("SICK_HOURS"));
                    info.put("personalHours", rs.getDouble("PERSONAL_HOURS"));
                    return info;
                } else { logger.warning("No employee info found for EID: " + eid); return null; }
            }
        } catch (Exception e) { logger.log(Level.SEVERE, "Error fetching employee info for EID: " + eid, e); return null; }
    }

    /** Gets current pay period start/end dates from Configuration */
     public static Map<String, LocalDate> getCurrentPayPeriodInfo() {
         Map<String, LocalDate> periodInfo = new HashMap<>();
         try {
             String startDateStr = Configuration.getProperty("PayPeriodStartDate");
             String endDateStr = Configuration.getProperty("PayPeriodEndDate");
             if (isValid(startDateStr) && isValid(endDateStr)) {
                 periodInfo.put("startDate", LocalDate.parse(startDateStr.trim()));
                 periodInfo.put("endDate", LocalDate.parse(endDateStr.trim()));
                 return periodInfo;
             } else { logger.warning("Pay period start/end dates not found or invalid in configuration."); return null; }
         } catch (Exception e) { logger.log(Level.SEVERE, "Error getting pay period dates from configuration", e); return null; }
     }

    /**
     * Calculates LATE/EARLY flags dynamically for display.
     * Generates data map for timeclock.jsp display.
     * Applies styling class based on dynamic calculation.
     * Recalculates period Reg/OT hours for display.
     * CORRECTED Weekly Aggregation + Detailed Logging.
     */
    public static Map<String, Object> getTimecardPunchData(int eid, LocalDate startDate, LocalDate endDate,
                                                             Map<String, Object> employeeInfo,
                                                             String userTimeZoneId) {
        Map<String, Object> result = new HashMap<>();
        StringBuilder html = new StringBuilder();
        boolean foundPunches = false;

        result.put("punchTableHtml", "<tr><td colspan='5' style='text-align:center; font-style:italic;'>No punches found for this period.</td></tr>");
        result.put("totalRegularHours", 0.0); result.put("totalOvertimeHours", 0.0);
        if (startDate == null || endDate == null || employeeInfo == null) { return result; }

        // --- TimeZones and Formatters ---
        ZoneId targetZone; ZoneId scheduleZone;
        try { targetZone = ZoneId.of(userTimeZoneId); } catch (Exception e) { targetZone = ZoneId.systemDefault(); }
        try { scheduleZone = ZoneId.of(SCHEDULE_DEFAULT_ZONE_ID_STR); } catch (Exception e) { scheduleZone = ZoneId.systemDefault(); }
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a").withZone(targetZone);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy").withZone(targetZone);
        DateTimeFormatter dbDateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        // --- Settings & Schedule Info ---
        int gracePeriod = 0; boolean weeklyOtEnabled = false; boolean dailyOtEnabled = false; double dailyThreshold = 8.0; String firstDayOfWeekSetting = "SUNDAY";
        try { gracePeriod = Integer.parseInt(Configuration.getProperty("GracePeriod", "0").trim()); weeklyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty("Overtime", "false")); dailyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty("OvertimeDaily", "false")); dailyThreshold = Double.parseDouble(Configuration.getProperty("OvertimeDailyThreshold", "8.0")); firstDayOfWeekSetting = Configuration.getProperty("FirstDayOfWeek", "SUNDAY").toUpperCase(); } catch (Exception e) { logger.warning("Error reading settings: "+e.getMessage()); }
        Time shiftStartDb = (Time) employeeInfo.get("shiftStart"); Time shiftEndDb = (Time) employeeInfo.get("shiftEnd");
        LocalTime scheduleStartTimeLocal = (shiftStartDb != null) ? shiftStartDb.toLocalTime() : null;
        LocalTime scheduleEndTimeLocal = (shiftEndDb != null) ? shiftEndDb.toLocalTime() : null;
        LocalTime gracePeriodStartTimeLocal = null; LocalTime gracePeriodEndTimeLocal = null;
        if (scheduleStartTimeLocal != null && gracePeriod > 0) gracePeriodStartTimeLocal = scheduleStartTimeLocal.plusMinutes(gracePeriod);
        if (scheduleEndTimeLocal != null && gracePeriod > 0) gracePeriodEndTimeLocal = scheduleEndTimeLocal.minusMinutes(gracePeriod);
        logger.info("Display Calc: Schedule=" + scheduleStartTimeLocal + "-" + scheduleEndTimeLocal + ", Grace=" + gracePeriod + ", SchedZone=" + scheduleZone.getId() + ", WeekStartDay=" + firstDayOfWeekSetting);


        // --- Data Aggregation & HTML Generation ---
        Map<LocalDate, Double> dailyWorkedTotals = new HashMap<>(); Map<LocalDate, Double> weeklyWorkedTotals = new HashMap<>();
        String sqlGetPunches = "SELECT `PUNCH_ID`, `DATE`, `IN_1`, `OUT_1`, `TOTAL`, `PUNCH_TYPE` FROM PUNCHES WHERE EID = ? AND `DATE` BETWEEN ? AND ? ORDER BY `DATE` ASC, `IN_1` ASC";
        LocalDate queryEndDate = endDate.plusDays(1); // Query one day extra for UTC boundary

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement psGetPunches = con.prepareStatement(sqlGetPunches)) {
            psGetPunches.setInt(1, eid);
            psGetPunches.setDate(2, java.sql.Date.valueOf(startDate));
            psGetPunches.setDate(3, java.sql.Date.valueOf(queryEndDate)); // Use end date + 1 day
            logger.info("Executing punch query for EID " + eid + " between UTC Dates " + startDate + " and " + queryEndDate);

            try (ResultSet rsPunches = psGetPunches.executeQuery()) {
                while (rsPunches.next()) {
                    long punchId = rsPunches.getLong("PUNCH_ID"); Date punchDbUtcDate = rsPunches.getDate("DATE"); Timestamp inTimestampUtc = rsPunches.getTimestamp("IN_1"); Timestamp outTimestampUtc = rsPunches.getTimestamp("OUT_1"); double totalHoursFromDb = rsPunches.getDouble("TOTAL"); if(rsPunches.wasNull()) totalHoursFromDb=0.0; String punchType = rsPunches.getString("PUNCH_TYPE"); String safePunchType = (punchType != null ? punchType : "");
                    Instant inInstant = (inTimestampUtc != null) ? inTimestampUtc.toInstant() : null;
                    Instant outInstant = (outTimestampUtc != null) ? outTimestampUtc.toInstant() : null;

                    // Determine local start date for filtering and weekly aggregation
                    LocalDate punchStartLocalDate = null;
                    if (inInstant != null) {
                        punchStartLocalDate = ZonedDateTime.ofInstant(inInstant, scheduleZone).toLocalDate();
                    } else if (punchDbUtcDate != null) {
                        punchStartLocalDate = punchDbUtcDate.toLocalDate(); // Fallback if no IN time
                        logger.warning("PunchID " + punchId + " using UTC date for filtering/weekly aggregation due to null IN time.");
                    }

                    // Filter in Java: Skip punch if its local start date is outside the strict pay period
                    if (punchStartLocalDate == null || punchStartLocalDate.isBefore(startDate) || punchStartLocalDate.isAfter(endDate)) {
                        logger.fine("Skipping PunchID " + punchId + " (Local Start Date " + punchStartLocalDate + " outside period " + startDate + " - " + endDate + ")");
                        continue; // Go to the next record
                    }

                    // If we reach here, the punch belongs in the pay period display
                    if (!foundPunches) { html.setLength(0); foundPunches = true; }

                    // Aggregate totals + Logging
                    LocalDate punchDateForDaily = (punchDbUtcDate != null) ? punchDbUtcDate.toLocalDate() : punchStartLocalDate; // Use UTC date for daily typically
                    boolean isWorkedType = !"Vacation Time".equalsIgnoreCase(safePunchType) && !"Sick Time".equalsIgnoreCase(safePunchType) && !"Personal Time".equalsIgnoreCase(safePunchType) && !"Holiday Time".equalsIgnoreCase(safePunchType) && !"Bereavement".equalsIgnoreCase(safePunchType);
                    logger.info("Processing PunchID " + punchId + ", DB Date: " + punchDbUtcDate + ", Type: " + safePunchType + ", Hours: " + totalHoursFromDb + ", IsWorked: " + isWorkedType + ", Local Start Date for Weekly: " + punchStartLocalDate);

                    if (isWorkedType && totalHoursFromDb > 0) {
                        // Aggregate Daily Totals (using UTC date from DB)
                        if (punchDateForDaily != null) { double currentDailyTotal = dailyWorkedTotals.getOrDefault(punchDateForDaily, 0.0); dailyWorkedTotals.put(punchDateForDaily, currentDailyTotal + totalHoursFromDb); logger.info(" -> Aggregated Daily[" + punchDateForDaily + "]=" + dailyWorkedTotals.get(punchDateForDaily)); } else { logger.warning(" -> Cannot aggregate daily total for PunchID " + punchId + " due to missing DB Date."); }
                        // Aggregate Weekly Totals (using LOCAL START date of punch)
                        if (punchStartLocalDate != null) { LocalDate weekStartDate = calculateWeekStart(punchStartLocalDate, firstDayOfWeekSetting); double currentWeeklyTotal = weeklyWorkedTotals.getOrDefault(weekStartDate, 0.0); weeklyWorkedTotals.put(weekStartDate, currentWeeklyTotal + totalHoursFromDb); logger.info(" -> Aggregated Weekly[" + weekStartDate + "]=" + weeklyWorkedTotals.get(weekStartDate) + " (based on local date " + punchStartLocalDate + ")"); } else { logger.warning(" -> Cannot aggregate weekly total for PunchID " + punchId + " due to missing Local Start Date."); }
                    } else { logger.info(" -> Not aggregating PunchID " + punchId + " (Type: " + safePunchType + " or Hours: " + totalHoursFromDb + ")"); }

                    // Format data
                    String formattedDate = NOT_APPLICABLE_DISPLAY; if (inInstant != null) { formattedDate = dateFormatter.format(inInstant); } else if (punchDbUtcDate != null) { try{ formattedDate = punchDbUtcDate.toLocalDate().format(dbDateFormatter); } catch(Exception e) { formattedDate="Error";} }
                    String formattedIn = (inInstant != null) ? timeFormatter.format(inInstant) : ""; String formattedOut = (outInstant != null) ? timeFormatter.format(outInstant) : ""; String formattedTotal = String.format("%.3f", totalHoursFromDb);

                    // Dynamically Calculate Flags and CSS Class
                    boolean isLate = false; boolean isEarlyOut = false; String inTagClass = ""; String outTagClass = "";
                    try { LocalTime lct=(gracePeriodStartTimeLocal!=null)?gracePeriodStartTimeLocal:scheduleStartTimeLocal; LocalTime ect=(gracePeriodEndTimeLocal!=null)?gracePeriodEndTimeLocal:scheduleEndTimeLocal; if(inInstant!=null&&lct!=null){isLate=ZonedDateTime.ofInstant(inInstant,scheduleZone).toLocalTime().isAfter(lct);if(isLate)inTagClass=" class=\"lateOrEarlyOutTag\"";} if(outInstant!=null&&ect!=null){isEarlyOut=ZonedDateTime.ofInstant(outInstant,scheduleZone).toLocalTime().isBefore(ect);if(isEarlyOut)outTagClass=" class=\"lateOrEarlyOutTag\"";} } catch (Exception e) { logger.log(Level.WARNING, "Error calculating style for punch " + punchId, e); }
                    String inTag = "<td" + inTagClass + ">"; String outTag = "<td" + outTagClass + ">";

                    // Append HTML row
                    html.append("<tr data-punch-id=\"").append(punchId).append("\">").append("<td>").append(formattedDate).append("</td>").append(inTag).append(formattedIn).append("</td>").append(outTag).append(formattedOut).append("</td>").append("<td style='text-align: right;'>").append(formattedTotal).append("</td>").append("<td>").append(safePunchType).append("</td>").append("</tr>\n");
                } // End while
            } // Close RS

            // --- Recalculate Period OT/Regular FOR DISPLAY (Includes Detailed Logging & Original Sanity Check) ---
            if (foundPunches) {
                double periodWorkedTotalHours = 0.0; logger.info("Calculating period total from daily worked totals: " + dailyWorkedTotals);
                for (double dailyWorkedTotal : dailyWorkedTotals.values()) { periodWorkedTotalHours += dailyWorkedTotal; }
                periodWorkedTotalHours = Math.round(periodWorkedTotalHours * ROUNDING_FACTOR) / ROUNDING_FACTOR; logger.info("Period Worked Total (Summed & Rounded): " + periodWorkedTotalHours);

                double calculatedReg = 0.0; double calculatedOt = 0.0;
                String wageType = (String) employeeInfo.getOrDefault("wageType", "");
                logger.info("OT Check Conditions: wageType=" + wageType + ", weeklyOtEnabled=" + weeklyOtEnabled + ", periodWorkedTotalHours=" + periodWorkedTotalHours);

                if ("Hourly".equalsIgnoreCase(wageType) && weeklyOtEnabled && periodWorkedTotalHours > 0) {
                     logger.info("--> Applying OT rules...");
                     double totalWeeklyOt = 0.0; logger.info("Calculating Weekly OT from weekly totals (based on Local Start Date): " + weeklyWorkedTotals + " (Threshold=" + WEEKLY_OT_THRESHOLD + ")");
                     for (Map.Entry<LocalDate, Double> weekEntry : weeklyWorkedTotals.entrySet()){ double weeklyTotal = Math.round(weekEntry.getValue() * ROUNDING_FACTOR) / ROUNDING_FACTOR; totalWeeklyOt += Math.max(0.0, weeklyTotal - WEEKLY_OT_THRESHOLD); }
                     totalWeeklyOt = Math.round(totalWeeklyOt * ROUNDING_FACTOR) / ROUNDING_FACTOR; logger.info("Calculated totalWeeklyOt: " + totalWeeklyOt);
                     double potentialTotalDailyOt = 0.0; logger.info("Calculating Daily OT (Enabled=" + dailyOtEnabled + ", Threshold=" + dailyThreshold + ")");
                     if (dailyOtEnabled && dailyThreshold > 0) { for (double dailyWorkedTotal : dailyWorkedTotals.values()) { potentialTotalDailyOt += Math.max(0.0, dailyWorkedTotal - dailyThreshold); } potentialTotalDailyOt = Math.round(potentialTotalDailyOt * ROUNDING_FACTOR) / ROUNDING_FACTOR; }
                     logger.info("Calculated potentialTotalDailyOt: " + potentialTotalDailyOt);

                     calculatedOt = Math.max(totalWeeklyOt, potentialTotalDailyOt);
                     calculatedReg = periodWorkedTotalHours - calculatedOt;
                     logger.info("Initial Calculation: calculatedOt=" + calculatedOt + ", calculatedReg=" + calculatedReg);

                     // Original Sanity Check Block
                     if (periodWorkedTotalHours > WEEKLY_OT_THRESHOLD && totalWeeklyOt > 0) {
                         logger.info("Applying original weekly OT sanity check...");
                         int numberOfWeeks = weeklyWorkedTotals.isEmpty() ? 1 : weeklyWorkedTotals.size();
                         double maxRegular = WEEKLY_OT_THRESHOLD * numberOfWeeks;
                         logger.info("Sanity Check Values: currentReg=" + calculatedReg + ", maxRegular=" + maxRegular + ", weekMapSize=" + numberOfWeeks);
                         if (calculatedReg > maxRegular) { calculatedReg = maxRegular; calculatedOt = periodWorkedTotalHours - calculatedReg; logger.info("Sanity Check Applied: cappedReg=" + calculatedReg + ", recalculatedOt=" + calculatedOt); }
                         else { logger.info("Sanity Check: calculatedReg was not > maxRegular. No change."); }
                     } else { logger.info("Skipping original weekly OT sanity check (Condition not met: periodTotal=" + periodWorkedTotalHours + ", totalWeeklyOt=" + totalWeeklyOt +")"); }

                } else {
                    logger.info("--> Skipping OT rules (WageType/WeeklyEnabled/Hours condition FALSE).");
                    calculatedReg = periodWorkedTotalHours; calculatedOt = 0.0;
                }

                calculatedReg = Math.max(0.0, calculatedReg); calculatedOt = Math.max(0.0, calculatedOt);
                result.put("totalRegularHours", Math.round(calculatedReg * 100.0) / 100.0);
                result.put("totalOvertimeHours", Math.round(calculatedOt * 100.0) / 100.0);
                result.put("punchTableHtml", html.toString());
                logger.info("Final Timecard Display Totals (Set in Result): Reg=" + result.get("totalRegularHours") + ", OT=" + result.get("totalOvertimeHours"));
            } else {
                 // Ensure default message is set if no punches passed the filter
                 if (html.length() == 0) { result.put("punchTableHtml", "<tr><td colspan='5' style='text-align:center; font-style:italic;'>No punches found for this period.</td></tr>"); }
                 else { result.put("punchTableHtml", html.toString()); } // Should not happen
                 result.put("totalRegularHours", 0.0); result.put("totalOvertimeHours", 0.0);
            }

        } catch (Exception e) { logger.log(Level.SEVERE, "Error getTimecardPunchData EID: " + eid, e); result.put("punchTableHtml", "<tr><td colspan='5' class='report-error-row'>Error processing data.</td></tr>"); result.put("totalRegularHours", 0.0); result.put("totalOvertimeHours", 0.0); }
        return result;
    }

    /** Fetches a list of active employees for a dropdown menu. */
    public static List<Map<String, Object>> getActiveEmployeesForDropdown() {
        List<Map<String, Object>> employeeList = new ArrayList<>();
        String sql = "SELECT EID, FIRST_NAME, LAST_NAME FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE ORDER BY LAST_NAME ASC, FIRST_NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> employee = new HashMap<>(); int eid = rs.getInt("EID"); String firstName = rs.getString("FIRST_NAME"); String lastName = rs.getString("LAST_NAME"); employee.put("eid", eid);
                String ln = (lastName != null && !lastName.trim().isEmpty()) ? lastName.trim() : ""; String fn = (firstName != null && !firstName.trim().isEmpty()) ? firstName.trim() : ""; employee.put("displayName", ln + (ln.isEmpty() || fn.isEmpty() ? "" : ", ") + fn);
                employeeList.add(employee);
            }
        } catch (Exception e) { logger.log(Level.SEVERE, "Error fetching active employees for dropdown", e); }
        return employeeList;
    }

    /** Applies auto-lunch deduction based on schedule settings. */
    public static double applyAutoLunch(Connection con, int eid, double rawTotalHours) {
        boolean autoLunch = false; Integer hrsRequired = null; Integer lunchLength = null; if (con == null) { logger.severe("applyAutoLunch null connection EID: "+eid); return rawTotalHours; }
        String sql = "SELECT s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.SCHEDULE = s.NAME WHERE e.EID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) { ps.setInt(1, eid); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) { autoLunch = rs.getBoolean("AUTO_LUNCH"); int hrs = rs.getInt("HRS_REQUIRED"); hrsRequired = rs.wasNull() ? null : hrs; int len = rs.getInt("LUNCH_LENGTH"); lunchLength = rs.wasNull() ? null : len; } else { logger.warning("No schedule info found for auto-lunch check, EID: " + eid); return rawTotalHours; } }
        } catch (SQLException e) { logger.log(Level.SEVERE, "SQL Error fetching schedule for auto-lunch EID: " + eid, e); return rawTotalHours; }
        if (autoLunch && hrsRequired != null && lunchLength != null && hrsRequired > 0 && lunchLength > 0 && rawTotalHours > hrsRequired) { double lunchDeduction = (double) lunchLength / 60.0; logger.fine("Applied auto-lunch (" + lunchDeduction + " hrs) for EID: " + eid); return Math.max(0, rawTotalHours - lunchDeduction); }
        else { return rawTotalHours; }
    }

    /** Generates HTML table rows for punches.jsp (Edit Punches page - No Styling) */
    public static String getPunchTableRows(int eid, String userTimeZoneId) {
        StringBuilder htmlRows = new StringBuilder(); ZoneId targetZone; try { targetZone = ZoneId.of(userTimeZoneId); } catch (Exception e) { targetZone = ZoneId.systemDefault(); }
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a").withZone(targetZone); DateTimeFormatter dateFormatterForInstant = DateTimeFormatter.ofPattern("MM/dd/yyyy").withZone(targetZone); DateTimeFormatter dateFormatterForLocalDate = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String sql = "SELECT PUNCH_ID, DATE, IN_1, OUT_1, TOTAL, PUNCH_TYPE FROM PUNCHES WHERE EID = ? ORDER BY DATE DESC, IN_1 DESC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, eid);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false; while (rs.next()) { found = true; long punchId = rs.getLong(1); Date dateDb = rs.getDate(2); Timestamp inTs = rs.getTimestamp(3); Timestamp outTs = rs.getTimestamp(4); double total=rs.getDouble(5); if(rs.wasNull())total=0.0; String type=rs.getString(6); Instant iIn=(inTs!=null)?inTs.toInstant():null; Instant iOut=(outTs!=null)?outTs.toInstant():null; String fDate=NOT_APPLICABLE_DISPLAY; if(iIn!=null){fDate=dateFormatterForInstant.format(iIn);}else if(dateDb!=null){try{fDate=dateDb.toLocalDate().format(dateFormatterForLocalDate);}catch(Exception e){fDate="Err";}} String fIn=(iIn!=null)?timeFormatter.format(iIn):""; String fOut=(iOut!=null)?timeFormatter.format(iOut):""; String fTotal=String.format("%.3f", total); String sType=(type!=null)?type:""; htmlRows.append("<tr data-punch-id=\"").append(punchId).append("\"><td>").append(fDate).append("</td><td>").append(fIn).append("</td><td>").append(fOut).append("</td><td style='text-align: right;'>").append(fTotal).append("</td><td>").append(sType).append("</td></tr>\n"); }
                if (!found) { return "<tr><td colspan='5' class='report-message-row'>No punch records found.</td></tr>"; }
            }
        } catch (Exception e) { logger.log(Level.SEVERE, "Error getPunchTableRows EID: " + eid, e); return "<tr><td colspan='5' class='report-error-row'>Error loading data.</td></tr>"; }
        return htmlRows.toString();
    }

    /** Generates HTML table rows for ARCHIVED punches. */
    public static String showArchivedPunchesReport(int eid, String userTimeZoneId, LocalDate startDate, LocalDate endDate) {
        StringBuilder htmlRows = new StringBuilder(); if (eid <= 0 || startDate == null || endDate == null || startDate.isAfter(endDate)) { return "<tr><td colspan='5' class='report-error-row'>Invalid Input.</td></tr>"; } ZoneId targetZone; try { targetZone = ZoneId.of(userTimeZoneId); } catch (Exception e) { targetZone = ZoneId.systemDefault(); } DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a").withZone(targetZone); DateTimeFormatter dateFormatterForInstant = DateTimeFormatter.ofPattern("MM/dd/yyyy").withZone(targetZone); DateTimeFormatter dateFormatterForLocalDate = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        String sql = "SELECT PUNCH_ID, DATE, IN_1, OUT_1, TOTAL, PUNCH_TYPE FROM ARCHIVED_PUNCHES WHERE EID = ? AND DATE BETWEEN ? AND ? ORDER BY DATE DESC, IN_1 DESC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) { ps.setInt(1, eid); ps.setDate(2, java.sql.Date.valueOf(startDate)); ps.setDate(3, java.sql.Date.valueOf(endDate)); try (ResultSet rs = ps.executeQuery()) { boolean found = false; while (rs.next()) { found = true; long pId=rs.getLong(1);Date dDb=rs.getDate(2);Timestamp iTs=rs.getTimestamp(3);Timestamp oTs=rs.getTimestamp(4);double tot=rs.getDouble(5);if(rs.wasNull())tot=0.0;String pTyp=rs.getString(6);Instant iIn=(iTs!=null)?iTs.toInstant():null;Instant iOut=(oTs!=null)?oTs.toInstant():null;String fD=NOT_APPLICABLE_DISPLAY;if(iIn!=null)fD=dateFormatterForInstant.format(iIn);else if(dDb!=null)try{fD=dDb.toLocalDate().format(dateFormatterForLocalDate);}catch(Exception e){fD="Err";}String fI=(iIn!=null)?timeFormatter.format(iIn):"";String fO=(iOut!=null)?timeFormatter.format(iOut):"";String fT=String.format("%.3f",tot);String sT=(pTyp!=null)?pTyp:""; htmlRows.append("<tr class=\"archived-row\" data-punch-id=\"").append(pId).append("\"><td>").append(fD).append("</td><td>").append(fI).append("</td><td>").append(fO).append("</td><td style='text-align: right;'>").append(fT).append("</td><td>").append(sT).append("</td></tr>\n"); } if (!found) { return "<tr><td colspan='5' class='report-message-row'>No archived records found.</td></tr>"; } }
        } catch (Exception e) { logger.log(Level.SEVERE, "Error fetching archived punches EID: " + eid, e); return "<tr><td colspan='5' class='report-error-row'>Error loading archived data.</td></tr>"; }
        return htmlRows.toString();
    }

} // End Class