package timeclock.punches;

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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.Configuration;
import timeclock.db.DatabaseConnection;

public class ShowPunches {

    private static final Logger logger = Logger.getLogger(ShowPunches.class.getName());
    private static final String NOT_APPLICABLE_DISPLAY = "";
    private static final double FINAL_ROUNDING_HOURS = 100.0;
    private static final double WEEKLY_OT_THRESHOLD_FLSA = 40.0;
    private static final String ULTIMATE_DISPLAY_FALLBACK_ZONE_ID = "UTC";

    public static boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !"undefined".equalsIgnoreCase(s) && !"null".equalsIgnoreCase(s) && !"Unknown".equalsIgnoreCase(s);
    }

    public static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
    
    private static double getDoubleConfigProperty(int tenantId, String key, String defaultValue) {
        String valueStr = Configuration.getProperty(tenantId, key, defaultValue);
        if (valueStr == null || valueStr.trim().isEmpty()) {
            valueStr = defaultValue;
        }
        try {
            return Double.parseDouble(valueStr.trim());
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Could not parse double from config key '" + key + "' with value '" + valueStr + "'. Using default: " + defaultValue, e);
            return Double.parseDouble(defaultValue);
        }
    }

    public static void setOptionalDouble(PreparedStatement ps, int parameterIndex, Double value) throws SQLException {
        if (value != null && !value.isNaN() && !value.isInfinite()) {
            ps.setDouble(parameterIndex, value);
        } else {
            ps.setNull(parameterIndex, Types.DOUBLE);
        }
    }

    public static void setOptionalTimestamp(PreparedStatement ps, int i, Timestamp ts) throws SQLException {
        if (ts != null) { ps.setTimestamp(i, ts); } else { ps.setNull(i, Types.TIMESTAMP); }
    }

    public static LocalDate calculateWeekStart(LocalDate currentDate, String firstDayOfWeekSetting) {
        DayOfWeek firstDay = DayOfWeek.SUNDAY;
        try {
            if (isValid(firstDayOfWeekSetting)) { firstDay = DayOfWeek.valueOf(firstDayOfWeekSetting.trim().toUpperCase(Locale.ENGLISH)); }
        } catch (Exception e) { firstDay = DayOfWeek.SUNDAY; }
        return currentDate.with(TemporalAdjusters.previousOrSame(firstDay));
    }

    public static boolean isHoursOnlyType(String punchType) {
        if (punchType == null) return false;
        String pTypeLower = punchType.trim().toLowerCase();
        return pTypeLower.contains("vacation") || pTypeLower.contains("sick") || pTypeLower.contains("personal") || pTypeLower.contains("holiday") || pTypeLower.contains("bereavement") || pTypeLower.contains("other");
    }

    public static boolean isWorkPunchType(String punchType) {
        if (punchType == null) return false;
        String pTypeLower = punchType.trim().toLowerCase();
        return pTypeLower.equals("regular") || pTypeLower.equals("supervisor override") || pTypeLower.equals("user initiated") || pTypeLower.equals("sample data");
    }
    
    private static List<String> getScheduledDays(String daysWorkedStr) {
        List<String> scheduledDays = new ArrayList<>();
        if (daysWorkedStr != null && daysWorkedStr.length() == 7) {
            if (daysWorkedStr.charAt(0) == 'S') scheduledDays.add("SUNDAY");
            if (daysWorkedStr.charAt(1) == 'M') scheduledDays.add("MONDAY");
            if (daysWorkedStr.charAt(2) == 'T') scheduledDays.add("TUESDAY");
            if (daysWorkedStr.charAt(3) == 'W') scheduledDays.add("WEDNESDAY");
            if (daysWorkedStr.charAt(4) == 'H') scheduledDays.add("THURSDAY");
            if (daysWorkedStr.charAt(5) == 'F') scheduledDays.add("FRIDAY");
            if (daysWorkedStr.charAt(6) == 'A') scheduledDays.add("SATURDAY");
        }
        return scheduledDays;
    }

    public static Map<String, Object> getEmployeeTimecardInfo(int tenantId, int globalEID) {
        Map<String, Object> info = new HashMap<>();
        String sql = "SELECT e.EID, e.TenantEmployeeNumber, e.FIRST_NAME, e.LAST_NAME, e.DEPT, e.SUPERVISOR, " +
                     "e.SCHEDULE AS ScheduleName, e.WAGE_TYPE, " +
                     "s.SHIFT_START, s.SHIFT_END, s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH, s.DAYS_WORKED, " +
                     "e.VACATION_HOURS, e.SICK_HOURS, e.PERSONAL_HOURS " +
                     "FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME " +
                     "WHERE e.EID = ? AND e.TenantID = ? AND e.ACTIVE = TRUE";
        if (tenantId <= 0 || globalEID <= 0) return null;
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, globalEID); ps.setInt(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info.put("eid", rs.getInt("EID"));
                    info.put("tenantEmployeeNumber", rs.getObject("TenantEmployeeNumber"));
                    info.put("employeeName", rs.getString("FIRST_NAME") + " " + rs.getString("LAST_NAME"));
                    info.put("department", rs.getString("DEPT"));
                    info.put("supervisor", rs.getString("SUPERVISOR"));
                    info.put("scheduleName", rs.getString("ScheduleName"));
                    info.put("wageType", rs.getString("WAGE_TYPE"));
                    info.put("shiftStart", rs.getTime("SHIFT_START"));
                    info.put("shiftEnd", rs.getTime("SHIFT_END"));
                    info.put("autoLunch", rs.getBoolean("AUTO_LUNCH"));
                    info.put("hoursRequired", rs.getObject("HRS_REQUIRED"));
                    info.put("lunchLength", rs.getObject("LUNCH_LENGTH"));
                    info.put("daysWorkedStr", rs.getString("DAYS_WORKED"));
                    info.put("vacationHours", rs.getDouble("VACATION_HOURS"));
                    info.put("sickHours", rs.getDouble("SICK_HOURS"));
                    info.put("personalHours", rs.getDouble("PERSONAL_HOURS"));
                    return info;
                } else { return null; }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getEmployeeTimecardInfo] Error EID: " + globalEID, e);
            return null;
        }
    }

    public static Map<String, LocalDate> getCurrentPayPeriodInfo(int tenantId) {
        Map<String, LocalDate> periodInfo = new HashMap<>();
        if (tenantId <= 0) return null;
        try {
            String startDateStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
            String endDateStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");
            if (isValid(startDateStr) && isValid(endDateStr)) {
                periodInfo.put("startDate", LocalDate.parse(startDateStr.trim()));
                periodInfo.put("endDate", LocalDate.parse(endDateStr.trim()));
                return periodInfo;
            } else { return null; }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getCurrentPayPeriodInfo] Error T:" + tenantId, e);
            return null;
        }
    }

    public static List<Map<String, Object>> getActiveEmployeesForDropdown(int tenantId) {
        List<Map<String, Object>> employeeList = new ArrayList<>();
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE AND TenantID = ? ORDER BY LAST_NAME, FIRST_NAME";
        if (tenantId <= 0) return employeeList;
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> employee = new HashMap<>();
                    int globalEID = rs.getInt("EID");
                    Integer tenantEmpNo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                    String displayName = rs.getString("LAST_NAME") + ", " + rs.getString("FIRST_NAME") + " (#" + (tenantEmpNo != null ? tenantEmpNo : globalEID) + ")";
                    employee.put("eid", globalEID);
                    employee.put("displayName", displayName);
                    employeeList.add(employee);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getActiveEmployeesForDropdown] Error T:" + tenantId, e);
        }
        return employeeList;
    }
    
    public static double applyAutoLunch(Connection con, int tenantId, int eid, double rawTotalHours) {
        String sql = "SELECT s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH FROM EMPLOYEE_DATA e JOIN SCHEDULES s ON e.TenantID=s.TenantID AND e.SCHEDULE=s.NAME WHERE e.EID=? AND e.TenantID=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, eid); ps.setInt(2, tenantId);
            ResultSet rs = ps.executeQuery();
            if (rs.next() && rs.getBoolean("AUTO_LUNCH")) {
                double hrsReq = rs.getDouble("HRS_REQUIRED");
                int lenMin = rs.getInt("LUNCH_LENGTH");
                if (!rs.wasNull() && hrsReq > 0 && lenMin > 0 && rawTotalHours > hrsReq) {
                    return Math.max(0, rawTotalHours - (lenMin / 60.0));
                }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error in applyAutoLunch", e); }
        return rawTotalHours;
    }

    public static void calculateAndUpdatePunchTotal(Connection con, int tenantId, int eid, Timestamp in, Timestamp out, long punchId) throws SQLException {
         if (in == null || out == null) {
            String sql = "UPDATE PUNCHES SET TOTAL=NULL, OT=0, DT=0 WHERE PUNCH_ID=?";
            try(PreparedStatement ps = con.prepareStatement(sql)) { ps.setLong(1, punchId); ps.executeUpdate(); }
            return;
        }
        Duration d = Duration.between(in.toInstant(), out.toInstant());
        double totalHours = d.isNegative() ? 0 : d.getSeconds() / 3600.0;
        totalHours = applyAutoLunch(con, tenantId, eid, totalHours);
        totalHours = Math.round(totalHours * 100.0) / 100.0;
        String sql = "UPDATE PUNCHES SET TOTAL=? WHERE PUNCH_ID=?";
        try(PreparedStatement ps = con.prepareStatement(sql)) { ps.setDouble(1, totalHours); ps.setLong(2, punchId); ps.executeUpdate(); }
    }

    public static Map<String, Object> getTimecardPunchData(int tenantId, int globalEID, LocalDate payPeriodStartDate, LocalDate payPeriodEndDate, Map<String, Object> employeeInfo, String userTimeZoneIdStr) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> punchesDataList = new ArrayList<>();
        result.put("punches", punchesDataList);
        result.put("totalRegularHours", 0.0); result.put("totalOvertimeHours", 0.0); result.put("totalDoubleTimeHours", 0.0);

        if (tenantId <= 0 || globalEID <= 0 || payPeriodStartDate == null || payPeriodEndDate == null || employeeInfo == null || !isValid(userTimeZoneIdStr)) {
            result.put("error", "Invalid input or timezone to load timecard data.");
            return result;
        }

        ZoneId displayZoneId;
        try { displayZoneId = ZoneId.of(userTimeZoneIdStr); } 
        catch (Exception e) { displayZoneId = ZoneId.of(ULTIMATE_DISPLAY_FALLBACK_ZONE_ID); }
        
        Instant periodStartInstant = payPeriodStartDate.atStartOfDay(displayZoneId).toInstant();
        Instant periodEndInstant = payPeriodEndDate.plusDays(1).atStartOfDay(displayZoneId).toInstant();

        DateTimeFormatter timeFormatterForDisplay = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH).withZone(displayZoneId);
        DateTimeFormatter timeFormatterForRaw = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFormatterForDisplay = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);
        DateTimeFormatter isoDateFormatterForDataAttr = DateTimeFormatter.ISO_LOCAL_DATE;

        int gracePeriodMinutes = Integer.parseInt(Configuration.getProperty(tenantId, "GracePeriod", "5"));
        String daysWorkedStr = (String) employeeInfo.get("daysWorkedStr");
        List<String> scheduledDays = getScheduledDays(daysWorkedStr);
        
        String sqlGetPunches = "SELECT PUNCH_ID, IN_1, OUT_1, TOTAL, PUNCH_TYPE, `DATE` AS UTC_DB_DATE FROM PUNCHES WHERE EID = ? AND TenantID = ? AND " +
                               "((IN_1 IS NOT NULL AND IN_1 >= ? AND IN_1 < ?) OR (IN_1 IS NULL AND `DATE` BETWEEN ? AND ?)) " +
                               "ORDER BY `DATE` ASC, IN_1 ASC, PUNCH_ID ASC";

        try (Connection con = DatabaseConnection.getConnection(); 
             PreparedStatement psGetPunches = con.prepareStatement(sqlGetPunches)) {
            
            psGetPunches.setInt(1, globalEID); 
            psGetPunches.setInt(2, tenantId);
            psGetPunches.setTimestamp(3, Timestamp.from(periodStartInstant));
            psGetPunches.setTimestamp(4, Timestamp.from(periodEndInstant));
            psGetPunches.setDate(5, java.sql.Date.valueOf(payPeriodStartDate));
            psGetPunches.setDate(6, java.sql.Date.valueOf(payPeriodEndDate));

            Map<LocalDate, Double> dailyAggregatedWorkHours = new LinkedHashMap<>();

            try (ResultSet rsPunches = psGetPunches.executeQuery()) {
                while (rsPunches.next()) {
                    Map<String, String> punchMap = new HashMap<>();
                    long currentPunchId = rsPunches.getLong("PUNCH_ID");
                    Timestamp inTimestampUtc = rsPunches.getTimestamp("IN_1");
                    Timestamp outTimestampUtc = rsPunches.getTimestamp("OUT_1");
                    String punchType = rsPunches.getString("PUNCH_TYPE");
                    double totalHours = rsPunches.getDouble("TOTAL");
                    
                    LocalDate displayPunchDate;
                    ZonedDateTime zdtIn = null;
                    ZonedDateTime zdtOut = null;

                    if (inTimestampUtc != null) {
                        zdtIn = ZonedDateTime.ofInstant(inTimestampUtc.toInstant(), displayZoneId);
                        displayPunchDate = zdtIn.toLocalDate();
                    } else {
                        Date punchDbDate = rsPunches.getDate("UTC_DB_DATE");
                        displayPunchDate = (punchDbDate != null) ? punchDbDate.toLocalDate() : LocalDate.now(displayZoneId);
                    }
                    if (outTimestampUtc != null) {
                        zdtOut = ZonedDateTime.ofInstant(outTimestampUtc.toInstant(), displayZoneId);
                    }
                    
                    punchMap.put("punchId", Long.toString(currentPunchId));
                    punchMap.put("punchDate", displayPunchDate.format(isoDateFormatterForDataAttr));
                    punchMap.put("timeInRaw", (zdtIn != null) ? zdtIn.toLocalTime().format(timeFormatterForRaw) : "");
                    punchMap.put("timeOutRaw", (zdtOut != null) ? zdtOut.toLocalTime().format(timeFormatterForRaw) : "");
                    punchMap.put("dayOfWeek", displayPunchDate.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                    punchMap.put("friendlyPunchDate", displayPunchDate.format(dateFormatterForDisplay));
                    
                    String inTimeDisplay = (zdtIn != null) ? timeFormatterForDisplay.format(zdtIn) : NOT_APPLICABLE_DISPLAY;
                    String outTimeDisplay = (zdtOut != null) ? timeFormatterForDisplay.format(zdtOut) : NOT_APPLICABLE_DISPLAY;
                    
                    punchMap.put("inTimeCssClass", "");
                    punchMap.put("outTimeCssClass", "");

                    String dayOfWeekFullName = displayPunchDate.getDayOfWeek().name();

                    if (isWorkPunchType(punchType) && scheduledDays.contains(dayOfWeekFullName)) {
                        Time scheduledStartTimeSql = (Time) employeeInfo.get("shiftStart");
                        Time scheduledEndTimeSql = (Time) employeeInfo.get("shiftEnd");

                        if (scheduledStartTimeSql != null && zdtIn != null) {
                            if (zdtIn.toLocalTime().isAfter(scheduledStartTimeSql.toLocalTime().plusMinutes(gracePeriodMinutes))) {
                                punchMap.put("inTimeCssClass", "lateOrEarlyOutTag");
                            }
                        }
                        if (scheduledEndTimeSql != null && zdtOut != null) {
                            if (zdtOut.toLocalTime().isBefore(scheduledEndTimeSql.toLocalTime().minusMinutes(gracePeriodMinutes))) {
                                punchMap.put("outTimeCssClass", "lateOrEarlyOutTag");
                            }
                        }
                    }

                    punchMap.put("timeIn", inTimeDisplay);
                    punchMap.put("timeOut", outTimeDisplay);
                    punchMap.put("punchType", punchType);
                    punchMap.put("totalHours", String.format(Locale.US, "%.2f", totalHours));
                    punchesDataList.add(punchMap);
                    
                    // Aggregate daily totals for OT calculation
                    if (isWorkPunchType(punchType) && !rsPunches.wasNull()) {
                         dailyAggregatedWorkHours.put(displayPunchDate, dailyAggregatedWorkHours.getOrDefault(displayPunchDate, 0.0) + totalHours);
                    }
                }
            }
            
            // *** BUG FIX: Entire OT calculation block is rewritten for correctness ***
            double calculatedPeriodRegular = 0.0;
            double calculatedPeriodOt = 0.0;
            double calculatedPeriodDt = 0.0;
            String wageType = (String) employeeInfo.getOrDefault("wageType", "");

            if (!"Hourly".equalsIgnoreCase(wageType)) {
                // For non-hourly, all hours are regular
                for (double hours : dailyAggregatedWorkHours.values()) {
                    calculatedPeriodRegular += hours;
                }
            } else {
                // Get OT settings
                boolean dailyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDaily", "false"));
                double dailyOtThreshold = getDoubleConfigProperty(tenantId, "OvertimeDailyThreshold", "8.0");
                boolean doubleTimeEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDoubleTimeEnabled", "false"));
                double doubleTimeThreshold = getDoubleConfigProperty(tenantId, "OvertimeDoubleTimeThreshold", "12.0");
                boolean seventhDayOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeSeventhDayEnabled", "false"));
                double seventhDayOTThreshold = getDoubleConfigProperty(tenantId, "OvertimeSeventhDayOTThreshold", "0.0");
                double seventhDayDTThreshold = getDoubleConfigProperty(tenantId, "OvertimeSeventhDayDTThreshold", "8.0");
                String firstDayOfWeekSetting = Configuration.getProperty(tenantId, "FirstDayOfWeek", "SUNDAY").toUpperCase(Locale.ENGLISH);

                Map<LocalDate, Double> dailyNetRegularHours = new LinkedHashMap<>();

                // Loop 1: Calculate daily OT/DT first
                for (LocalDate date = payPeriodStartDate; !date.isAfter(payPeriodEndDate); date = date.plusDays(1)) {
                    double hoursWorkedToday = dailyAggregatedWorkHours.getOrDefault(date, 0.0);
                    if (hoursWorkedToday <= 0) {
                        dailyNetRegularHours.put(date, 0.0);
                        continue;
                    }

                    double todaysReg = hoursWorkedToday;
                    double todaysOt = 0;
                    double todaysDt = 0;

                    if (doubleTimeEnabled && todaysReg > doubleTimeThreshold) {
                        todaysDt = todaysReg - doubleTimeThreshold;
                        todaysReg = doubleTimeThreshold;
                    }
                    if (dailyOtEnabled && todaysReg > dailyOtThreshold) {
                        todaysOt = todaysReg - dailyOtThreshold;
                        todaysReg = dailyOtThreshold;
                    }
                    
                    calculatedPeriodDt += todaysDt;
                    calculatedPeriodOt += todaysOt;
                    dailyNetRegularHours.put(date, Math.max(0, todaysReg));
                }

                // Loop 2: Calculate weekly and 7th day rules
                LocalDate currentEvalDate = payPeriodStartDate;
                while (!currentEvalDate.isAfter(payPeriodEndDate)) {
                    LocalDate weekStart = calculateWeekStart(currentEvalDate, firstDayOfWeekSetting);
                    LocalDate weekEnd = weekStart.plusDays(6);
                    
                    double weeklyNetRegularSum = 0;
                    int daysWorkedInWeek = 0;
                    
                    for (LocalDate dateInWeek = weekStart; !dateInWeek.isAfter(weekEnd); dateInWeek = dateInWeek.plusDays(1)) {
                        if (dailyAggregatedWorkHours.getOrDefault(dateInWeek, 0.0) > 0.001) {
                           daysWorkedInWeek++;
                        }
                        if (payPeriodStartDate.isAfter(dateInWeek) || payPeriodEndDate.isBefore(dateInWeek)) continue;
                        weeklyNetRegularSum += dailyNetRegularHours.getOrDefault(dateInWeek, 0.0);
                    }

                    // 7th Day Logic (only applies if 7 consecutive days worked in the WORK WEEK)
                    if (seventhDayOtEnabled && daysWorkedInWeek == 7) {
                        LocalDate seventhDay = weekEnd;
                        if (dailyAggregatedWorkHours.containsKey(seventhDay)) {
                            double hoursOnSeventhDay = dailyNetRegularHours.getOrDefault(seventhDay, 0.0);
                            if (hoursOnSeventhDay > 0) {
                                double seventhDayDt = 0;
                                double seventhDayOt = 0;

                                if (hoursOnSeventhDay > seventhDayDTThreshold) {
                                    seventhDayDt = hoursOnSeventhDay - seventhDayDTThreshold;
                                }
                                seventhDayOt = hoursOnSeventhDay - seventhDayDt;

                                calculatedPeriodDt += seventhDayDt;
                                calculatedPeriodOt += seventhDayOt;
                                weeklyNetRegularSum -= (seventhDayOt + seventhDayDt); // Remove these hours from weekly calc
                            }
                        }
                    }

                    // Weekly OT Logic (on remaining net regular hours)
                    if (weeklyNetRegularSum > WEEKLY_OT_THRESHOLD_FLSA) {
                        double weeklyOt = weeklyNetRegularSum - WEEKLY_OT_THRESHOLD_FLSA;
                        calculatedPeriodOt += weeklyOt;
                    }
                    
                    currentEvalDate = weekEnd.plusDays(1);
                }

                // Loop 3: Sum up final regular hours
                double totalWorkHours = 0;
                for (double hours : dailyAggregatedWorkHours.values()) {
                    totalWorkHours += hours;
                }
                calculatedPeriodRegular = Math.max(0, totalWorkHours - calculatedPeriodOt - calculatedPeriodDt);
            }
            
            // Add non-work hours (Vacation, etc.) to the regular total for payroll.
            double nonWorkPaidHoursTotal = 0;
            for(Map<String, String> punch : punchesDataList) {
                if (isHoursOnlyType(punch.get("punchType"))) {
                    try { nonWorkPaidHoursTotal += Double.parseDouble(punch.get("totalHours")); } catch(NumberFormatException e) { /* ignore */ }
                }
            }
            calculatedPeriodRegular += nonWorkPaidHoursTotal;

            result.put("totalRegularHours", Math.max(0, Math.round(calculatedPeriodRegular * FINAL_ROUNDING_HOURS) / FINAL_ROUNDING_HOURS));
            result.put("totalOvertimeHours", Math.max(0, Math.round(calculatedPeriodOt * FINAL_ROUNDING_HOURS) / FINAL_ROUNDING_HOURS));
            result.put("totalDoubleTimeHours", Math.max(0, Math.round(calculatedPeriodDt * FINAL_ROUNDING_HOURS) / FINAL_ROUNDING_HOURS));
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getTimecardPunchData] Error for T:" + tenantId + ", EID:" + globalEID, e);
            result.put("error", "Failed to retrieve or calculate timecard data: " + e.getMessage());
        }
        return result;
    }
}