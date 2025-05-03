package timeclock.payroll; // Or your appropriate package

// --- Keep ALL existing imports ---
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Time;
import java.sql.Date;
import java.sql.Types;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;


public class ShowPayroll {

    private static final Logger logger = Logger.getLogger(ShowPayroll.class.getName());
    private static final String SCHEDULE_DEFAULT_ZONE_ID_STR = "America/Denver";
    private static final double ROUNDING_FACTOR = 10000.0;
    private static final double WEEKLY_OT_THRESHOLD = 40.0;
    private static final String NOT_APPLICABLE_DISPLAY = "N/A";

    // --- ** ADDED MISSING HELPER METHOD ** ---
    /** Helper method to check string validity */
    public static boolean isValid(String s) { // Made public static
        return s != null && !s.trim().isEmpty();
    }
    // --- ** END ADDED HELPER ** ---


    /** Core Calculation Method (No DB Update) */
    public static List<Map<String, Object>> calculatePayrollData(LocalDate startDate, LocalDate endDate) {
        // ... (Method content remains unchanged from previous correct version) ...
        List<Map<String, Object>> calculatedPayrollData = new ArrayList<>(); if (startDate == null || endDate == null) { logger.severe("calculatePayrollData called with null dates."); return calculatedPayrollData; } logger.info("Starting payroll calculation for period: " + startDate + " to " + endDate); boolean weeklyOtEnabled = false; boolean dailyOtEnabled = false; double dailyThreshold = 8.0; String firstDayOfWeekSetting = "SUNDAY"; double otMultiplier = 1.5; try { weeklyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty("Overtime", "false")); dailyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty("OvertimeDaily", "false")); dailyThreshold = Double.parseDouble(Configuration.getProperty("OvertimeDailyThreshold", "8.0")); firstDayOfWeekSetting = Configuration.getProperty("FirstDayOfWeek", "SUNDAY").toUpperCase(); otMultiplier = Double.parseDouble(Configuration.getProperty("OvertimeRate", "1.5")); if (otMultiplier < 1.0) otMultiplier = 1.5; } catch (Exception e) { logger.log(Level.SEVERE, "Error reading settings.", e); } ZoneId scheduleZone = ZoneId.of(SCHEDULE_DEFAULT_ZONE_ID_STR); Instant startInstant = startDate.atStartOfDay(scheduleZone).toInstant(); Instant endInstant = endDate.plusDays(1).atStartOfDay(scheduleZone).toInstant(); Timestamp startTs = Timestamp.from(startInstant); Timestamp endTs = Timestamp.from(endInstant); String sql = "SELECT e.EID, e.FIRST_NAME, e.LAST_NAME, e.WAGE_TYPE, e.WAGE, s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH, p.PUNCH_ID, p.DATE, p.IN_1, p.OUT_1, p.PUNCH_TYPE FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.SCHEDULE = s.NAME INNER JOIN PUNCHES p ON e.EID = p.EID WHERE e.ACTIVE = TRUE AND p.IN_1 >= ? AND p.IN_1 < ? ORDER BY e.EID, p.DATE, p.IN_1"; Map<Integer, List<Map<String, Object>>> punchesByEmployee = new HashMap<>(); Map<Integer, Map<String, Object>> employeeMetaInfo = new HashMap<>(); try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) { ps.setTimestamp(1, startTs); ps.setTimestamp(2, endTs); try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { int eid = rs.getInt("EID"); employeeMetaInfo.computeIfAbsent(eid, k -> { Map<String, Object> d = new HashMap<>(); try { d.put("EID", eid); d.put("FirstName", rs.getString("FIRST_NAME")); d.put("LastName", rs.getString("LAST_NAME")); d.put("WageType", rs.getString("WAGE_TYPE")); d.put("Wage", rs.getDouble("WAGE")); d.put("AutoLunch", rs.getBoolean("AUTO_LUNCH")); int hr = rs.getInt("HRS_REQUIRED"); d.put("HoursRequired", rs.wasNull() ? null : hr); int ll = rs.getInt("LUNCH_LENGTH"); d.put("LunchLength", rs.wasNull() ? null : ll); } catch (SQLException sqle) { throw new RuntimeException(sqle); } return d; }); Map<String, Object> punch = new HashMap<>(); punch.put("PunchID", rs.getLong("PUNCH_ID")); Timestamp inTs = rs.getTimestamp("IN_1"); punch.put("In", (inTs != null) ? inTs.toInstant() : null); Timestamp outTs = rs.getTimestamp("OUT_1"); punch.put("Out", (outTs != null) ? outTs.toInstant() : null); punch.put("Date", rs.getDate("DATE").toLocalDate()); punch.put("PunchType", rs.getString("PUNCH_TYPE")); punchesByEmployee.computeIfAbsent(eid, k -> new ArrayList<>()).add(punch); } } } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching data for payroll calculation", e); return calculatedPayrollData; } catch (RuntimeException e) { logger.log(Level.SEVERE, "Error processing results during fetch", e); return calculatedPayrollData; } logger.info("Fetched data for " + employeeMetaInfo.size() + " employees."); for (Map.Entry<Integer, List<Map<String, Object>>> entry : punchesByEmployee.entrySet()) { int eid = entry.getKey(); List<Map<String, Object>> punches = entry.getValue(); Map<String, Object> empInfo = employeeMetaInfo.get(eid); Map<LocalDate, Double> dailyWorkedTotals = new HashMap<>(); Map<LocalDate, Double> weeklyWorkedTotals = new HashMap<>(); double periodTotalWorkedHours = 0.0; boolean autoLunch = (Boolean) empInfo.getOrDefault("AutoLunch", false); Integer hrsRequired = (Integer) empInfo.get("HoursRequired"); Integer lunchLength = (Integer) empInfo.get("LunchLength"); String wageType = (String) empInfo.getOrDefault("WageType", ""); double wage = (Double) empInfo.getOrDefault("Wage", 0.0); for (Map<String, Object> punch : punches) { Instant inInst = (Instant) punch.get("In"); Instant outInst = (Instant) punch.get("Out"); double adjustedTotalHours = 0.0; if (inInst != null && outInst != null && outInst.isAfter(inInst)) { Duration dur = Duration.between(inInst, outInst); double rawTotalHours = dur.toNanos() / (3_600.0 * 1_000_000_000.0); rawTotalHours = Math.round(rawTotalHours * ROUNDING_FACTOR) / ROUNDING_FACTOR; adjustedTotalHours = rawTotalHours; if (autoLunch && hrsRequired != null && lunchLength != null && hrsRequired > 0 && lunchLength > 0 && rawTotalHours > hrsRequired) { double lunchDeduction = (double) lunchLength / 60.0; adjustedTotalHours = Math.max(0, rawTotalHours - lunchDeduction); } } String punchType = (String) punch.get("PunchType"); if (adjustedTotalHours > 0 && !"Vacation Time".equalsIgnoreCase(punchType) && !"Sick Time".equalsIgnoreCase(punchType) && !"Personal Time".equalsIgnoreCase(punchType) && !"Holiday Time".equalsIgnoreCase(punchType) && !"Bereavement".equalsIgnoreCase(punchType)) { LocalDate punchDate = (LocalDate) punch.get("Date"); dailyWorkedTotals.put(punchDate, dailyWorkedTotals.getOrDefault(punchDate, 0.0) + adjustedTotalHours); LocalDate weekStartDate = ShowPunches.calculateWeekStart(punchDate, firstDayOfWeekSetting); weeklyWorkedTotals.put(weekStartDate, weeklyWorkedTotals.getOrDefault(weekStartDate, 0.0) + adjustedTotalHours); periodTotalWorkedHours += adjustedTotalHours; } } periodTotalWorkedHours = Math.round(periodTotalWorkedHours * ROUNDING_FACTOR) / ROUNDING_FACTOR; double calculatedPeriodOt = 0.0; double calculatedPeriodReg = periodTotalWorkedHours; if ("Hourly".equalsIgnoreCase(wageType) && weeklyOtEnabled && periodTotalWorkedHours > 0) { double totalWeeklyOt = 0.0; for (double weeklyTotal : weeklyWorkedTotals.values()){ totalWeeklyOt += Math.max(0.0, weeklyTotal - WEEKLY_OT_THRESHOLD); } totalWeeklyOt = Math.round(totalWeeklyOt * ROUNDING_FACTOR) / ROUNDING_FACTOR; double potentialTotalDailyOt = 0.0; if (dailyOtEnabled && dailyThreshold > 0) { for (double dailyWorkedTotal : dailyWorkedTotals.values()) { potentialTotalDailyOt += Math.max(0.0, dailyWorkedTotal - dailyThreshold); } potentialTotalDailyOt = Math.round(potentialTotalDailyOt * ROUNDING_FACTOR) / ROUNDING_FACTOR; } calculatedPeriodOt = Math.max(totalWeeklyOt, potentialTotalDailyOt); calculatedPeriodReg = periodTotalWorkedHours - calculatedPeriodOt; if (periodTotalWorkedHours > WEEKLY_OT_THRESHOLD) { calculatedPeriodReg = Math.min(calculatedPeriodReg, WEEKLY_OT_THRESHOLD); calculatedPeriodOt = periodTotalWorkedHours - calculatedPeriodReg; } } calculatedPeriodReg = Math.round(calculatedPeriodReg * 100.0) / 100.0; calculatedPeriodOt = Math.round(calculatedPeriodOt * 100.0) / 100.0; double totalPay = 0.0; if ("Hourly".equalsIgnoreCase(wageType)) { totalPay = (calculatedPeriodReg * wage) + (calculatedPeriodOt * wage * otMultiplier); } else if ("Salary".equalsIgnoreCase(wageType)) { long daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate) + 1; double dailyRate = wage / 365.0; totalPay = dailyRate * daysInPeriod; } totalPay = Math.floor(totalPay * 100) / 100.0; Map<String, Object> employeeResult = new HashMap<>(empInfo); employeeResult.put("RegularHours", calculatedPeriodReg); employeeResult.put("OvertimeHours", calculatedPeriodOt); employeeResult.put("TotalHours", Math.round((calculatedPeriodReg + calculatedPeriodOt) * 100.0) / 100.0); employeeResult.put("TotalPay", totalPay); calculatedPayrollData.add(employeeResult); logger.fine("EID " + eid + " Calculated Payroll: Reg=" + calculatedPeriodReg + ", OT=" + calculatedPeriodOt + ", Pay=" + totalPay); } logger.info("Payroll calculation complete. Employees processed: " + calculatedPayrollData.size()); return calculatedPayrollData;
    }

    /** Formats pre-calculated payroll data into HTML table rows. */
    public static Map<String, Object> showPayroll(List<Map<String, Object>> calculatedData) {
        Map<String, Object> result = new HashMap<>(); StringBuilder tableRows = new StringBuilder(); double grandTotal = 0.0;
        result.put("payrollHtml", "<tr><td colspan='9' style='text-align:center;'>No data calculated.</td></tr>"); result.put("grandTotal", 0.0);
        if (calculatedData == null || calculatedData.isEmpty()) { logger.warning("showPayroll called with empty data."); return result; }
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US); boolean foundData = false;
        for (Map<String, Object> rowData : calculatedData) { foundData = true; int eid = (Integer) rowData.getOrDefault("EID", 0); String fN = (String) rowData.getOrDefault("FirstName", ""); String lN = (String) rowData.getOrDefault("LastName", ""); String wt = (String) rowData.getOrDefault("WageType", "N/A"); double w = (Double) rowData.getOrDefault("Wage", 0.0); double rh = (Double) rowData.getOrDefault("RegularHours", 0.0); double ot = (Double) rowData.getOrDefault("OvertimeHours", 0.0); double th = (Double) rowData.getOrDefault("TotalHours", 0.0); double tp = (Double) rowData.getOrDefault("TotalPay", 0.0); grandTotal += tp; String fw = ("Salary".equalsIgnoreCase(wt)) ? currencyFormatter.format(w) + "/yr" : currencyFormatter.format(w) + "/hr"; String ftp = currencyFormatter.format(tp); tableRows.append("<tr><td>").append(eid).append("</td><td>").append(fN).append("</td><td>").append(lN).append("</td><td>").append(wt).append("</td><td style='text-align: right;'>").append(String.format("%.2f", rh)).append("</td><td style='text-align: right;'>").append(String.format("%.2f", ot)).append("</td><td style='text-align: right;'>").append(String.format("%.2f", th)).append("</td><td style='text-align: right;'>").append(fw).append("</td><td style='text-align: right; font-weight: bold;'>").append(ftp).append("</td></tr>\n"); }
        if (foundData) { result.put("payrollHtml", tableRows.toString()); } else { result.put("payrollHtml", "<tr><td colspan='9' style='text-align:center;'>No payroll data processed.</td></tr>"); grandTotal = 0.0; }
        result.put("grandTotal", Math.round(grandTotal * 100.0) / 100.0);
        logger.info("Payroll HTML formatting complete."); return result;
    }


    /** Prepares raw data list (already calculated) for export. */
    public static List<Map<String, Object>> getRawPayrollData(List<Map<String, Object>> calculatedData) {
        logger.info("Returning pre-calculated raw payroll data for export.");
        return calculatedData != null ? calculatedData : new ArrayList<>();
    }

    /** Exception Report */
    public static String showExceptionReport() { logger.warning("showExceptionReport uses SimpleDateFormat"); SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy"); SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm:ss a"); StringBuilder html = new StringBuilder(); String sql = "SELECT p.PUNCH_ID, ed.EID, ed.FIRST_NAME, ed.LAST_NAME, p.DATE, p.IN_1, p.OUT_1 FROM EMPLOYEE_DATA ed JOIN PUNCHES p ON ed.EID = p.EID WHERE p.OUT_1 IS NULL AND ed.ACTIVE = TRUE ORDER BY ed.EID, p.DATE, p.IN_1"; try (Connection con = DatabaseConnection.getConnection(); PreparedStatement psGetExceptions = con.prepareStatement(sql);) { ResultSet rs = psGetExceptions.executeQuery(); boolean found = false; while (rs.next()) { found = true; long punchId = rs.getLong("PUNCH_ID"); int eid = rs.getInt("EID"); String firstName = rs.getString("FIRST_NAME"); String lastName = rs.getString("LAST_NAME"); Date date = rs.getDate("DATE"); Timestamp inTs = rs.getTimestamp("IN_1"); Timestamp outTs = rs.getTimestamp("OUT_1"); String formattedDate = (date != null) ? dateFormat.format(date) : "N/A"; String inCellClass = "", inCellContent = ""; if (inTs != null) { inCellContent = timeFormat.format(inTs); } else { inCellClass = " class='empty-cell'"; inCellContent = "<span class='missing-punch-placeholder'>Missing</span>"; } String outCellClass = "", outCellContent = ""; if (outTs != null) { outCellContent = timeFormat.format(outTs); } else { outCellClass = " class='empty-cell'"; outCellContent = "<span class='missing-punch-placeholder'>Missing</span>"; } html.append("<tr data-punch-id=\"").append(punchId).append("\">").append("<td>").append(eid).append("</td>").append("<td>").append(firstName != null ? firstName : "").append("</td>").append("<td>").append(lastName != null ? lastName : "").append("</td>").append("<td>").append(formattedDate).append("</td>").append("<td").append(inCellClass).append(">").append(inCellContent).append("</td>").append("<td").append(outCellClass).append(">").append(outCellContent).append("</td>").append("</tr>\n"); } rs.close(); if (!found) { return "NO_EXCEPTIONS"; } } catch (Exception e) { logger.log(Level.SEVERE, "Error executing exception report query", e); return "<tr><td colspan='6' class='report-error-row'>Error retrieving exceptions.</td></tr>"; } return html.toString(); }
    // Other Report Stubs
    public static String showTardyReport() { return "<tr><td colspan='5' style='text-align:center;'>Not Implemented</td></tr>"; }
    public static String showWhosInReport() { return "<tr><td colspan='5' style='text-align:center;'>Not Implemented</td></tr>"; }
    public static Map<String, String> showTimeCardReport(int id) { Map<String, String> resultMap = new HashMap<>(); resultMap.put("html", "<tr><td colspan='5'>Not Implemented</td></tr>"); resultMap.put("employeeName", "Employee " + id); resultMap.put("employeeId", String.valueOf(id)); return resultMap; }

} // End Class