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

    public static void setOptionalDouble(PreparedStatement ps, int parameterIndex, Double value) throws SQLException {
        if (value != null && !value.isNaN() && !value.isInfinite()) {
            ps.setDouble(parameterIndex, value);
        } else {
            ps.setNull(parameterIndex, Types.DOUBLE);
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
        DayOfWeek firstDay = DayOfWeek.SUNDAY;
        try {
            if (isValid(firstDayOfWeekSetting)) {
                firstDay = DayOfWeek.valueOf(firstDayOfWeekSetting.trim().toUpperCase(Locale.ENGLISH));
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "[ShowPunches.calculateWeekStart] Invalid FirstDayOfWeek setting '" + firstDayOfWeekSetting + "', defaulting to SUNDAY.", e);
            firstDay = DayOfWeek.SUNDAY;
        }
        return currentDate.with(TemporalAdjusters.previousOrSame(firstDay));
    }

    public static boolean isHoursOnlyType(String punchType) {
        if (punchType == null) return false;
        String pTypeLower = punchType.trim().toLowerCase();
        return pTypeLower.equals("vacation") || pTypeLower.equals("vacation time") ||
               pTypeLower.equals("sick") || pTypeLower.equals("sick time") ||
               pTypeLower.equals("personal") || pTypeLower.equals("personal time") ||
               pTypeLower.equals("holiday") || pTypeLower.equals("holiday time") ||
               pTypeLower.equals("bereavement") ||
               pTypeLower.equals("other");
    }

    public static boolean isWorkPunchType(String punchType) {
        if (punchType == null) return false;
        String pTypeLower = punchType.trim().toLowerCase();
        return pTypeLower.equals("regular") ||
               pTypeLower.equals("supervisor override") ||
               pTypeLower.equals("user initiated") ||
               pTypeLower.equals("sample data");
    }

    public static Map<String, Object> getEmployeeTimecardInfo(int tenantId, int globalEID) {
        Map<String, Object> info = new HashMap<>();
        String sql = "SELECT e.EID, e.TenantEmployeeNumber, e.FIRST_NAME, e.LAST_NAME, e.DEPT, e.SUPERVISOR, " +
                     "e.SCHEDULE AS ScheduleName, e.WAGE_TYPE, " +
                     "s.SHIFT_START, s.SHIFT_END, s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH, " +
                     "e.VACATION_HOURS, e.SICK_HOURS, e.PERSONAL_HOURS " +
                     "FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME " +
                     "WHERE e.EID = ? AND e.TenantID = ? AND e.ACTIVE = TRUE";
        if (tenantId <= 0 || globalEID <= 0) {
            logger.warning("[ShowPunches.getEmployeeTimecardInfo] Invalid tenantId or globalEID. TenantID: " + tenantId + ", EID: " + globalEID);
            return null;
        }
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, globalEID); ps.setInt(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    info.put("eid", rs.getInt("EID"));
                    info.put("tenantEmployeeNumber", rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null);
                    info.put("employeeName", rs.getString("FIRST_NAME") + " " + rs.getString("LAST_NAME"));
                    info.put("department", rs.getString("DEPT"));
                    info.put("supervisor", rs.getString("SUPERVISOR"));
                    info.put("scheduleName", rs.getString("ScheduleName"));
                    info.put("wageType", rs.getString("WAGE_TYPE"));
                    info.put("shiftStart", rs.getTime("SHIFT_START"));
                    info.put("shiftEnd", rs.getTime("SHIFT_END"));
                    info.put("autoLunch", rs.getBoolean("AUTO_LUNCH"));
                    double hrsReqDb = rs.getDouble("HRS_REQUIRED");
                    info.put("hoursRequired", rs.wasNull() ? null : hrsReqDb);
                    int lunchLength = rs.getInt("LUNCH_LENGTH");
                    info.put("lunchLength", rs.wasNull() ? null : lunchLength);
                    info.put("vacationHours", rs.getDouble("VACATION_HOURS"));
                    info.put("sickHours", rs.getDouble("SICK_HOURS"));
                    info.put("personalHours", rs.getDouble("PERSONAL_HOURS"));
                    return info;
                } else {
                    logger.warning("[ShowPunches.getEmployeeTimecardInfo] No employee data found for EID: " + globalEID + ", TenantID: " + tenantId);
                    return null;
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getEmployeeTimecardInfo] Error fetching employee info for EID: " + globalEID + ", TenantID: " + tenantId, e);
            return null;
        }
    }

    public static Map<String, LocalDate> getCurrentPayPeriodInfo(int tenantId) {
        Map<String, LocalDate> periodInfo = new HashMap<>();
        if (tenantId <= 0) {
            logger.warning("[ShowPunches.getCurrentPayPeriodInfo] Invalid TenantID: " + tenantId);
            return null;
        }
        try {
            String startDateStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
            String endDateStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");
            if (isValid(startDateStr) && isValid(endDateStr)) {
                try {
                    periodInfo.put("startDate", LocalDate.parse(startDateStr.trim()));
                    periodInfo.put("endDate", LocalDate.parse(endDateStr.trim()));
                    return periodInfo;
                } catch (DateTimeParseException e) {
                    logger.log(Level.SEVERE, "[ShowPunches.getCurrentPayPeriodInfo] Error parsing pay period dates for T:" + tenantId + ". StartDateStr: '" + startDateStr + "', EndDateStr: '" + endDateStr + "'", e);
                    return null;
                }
            } else {
                logger.warning("[ShowPunches.getCurrentPayPeriodInfo] Pay period start/end dates not fully configured for T:" + tenantId);
                return null;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getCurrentPayPeriodInfo] General error getting pay period dates for T:" + tenantId, e);
            return null;
        }
    }

    public static List<Map<String, Object>> getActiveEmployeesForDropdown(int tenantId) {
        List<Map<String, Object>> employeeList = new ArrayList<>();
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE AND TenantID = ? ORDER BY LAST_NAME ASC, FIRST_NAME ASC";
        
        logger.info("[ShowPunches.getActiveEmployeesForDropdown] Attempting to fetch for TenantID: " + tenantId);

        if (tenantId <= 0) {
            logger.warning("[ShowPunches.getActiveEmployeesForDropdown] Invalid TenantID provided: " + tenantId + ". Returning empty list.");
            return employeeList;
        }

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            if (con == null) {
                logger.severe("[ShowPunches.getActiveEmployeesForDropdown] Database connection is NULL for TenantID: " + tenantId);
                return employeeList;
            }
            logger.fine("[ShowPunches.getActiveEmployeesForDropdown] Database connection obtained for TenantID: " + tenantId);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, tenantId);
                String queryForLog = ps.toString();
                logger.fine("[ShowPunches.getActiveEmployeesForDropdown] Executing query: " + queryForLog.substring(queryForLog.indexOf(": ") + 2));
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        Map<String, Object> employee = new HashMap<>();
                        int globalEID = rs.getInt("EID");
                        Integer tenantEmpNo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                        String firstName = rs.getString("FIRST_NAME");
                        String lastName = rs.getString("LAST_NAME");

                        employee.put("eid", globalEID);
                        String ln = (isValid(lastName)) ? lastName.trim() : "";
                        String fn = (isValid(firstName)) ? firstName.trim() : "";
                        String tenantEmpNoStr = (tenantEmpNo != null && tenantEmpNo > 0) ? String.valueOf(tenantEmpNo) : String.valueOf(globalEID);
                        employee.put("displayName", ln + (ln.isEmpty() || fn.isEmpty() ? "" : ", ") + fn + " (#" + tenantEmpNoStr + ")");
                        employeeList.add(employee);
                    }
                    logger.info("[ShowPunches.getActiveEmployeesForDropdown] Found " + count + " active employees for TenantID: " + tenantId);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[ShowPunches.getActiveEmployeesForDropdown] SQL Error fetching active employees for dropdown, TenantID: " + tenantId, e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getActiveEmployeesForDropdown] General Error fetching active employees for dropdown, TenantID: " + tenantId, e);
        } finally {
            if (con != null) {
                try {
                    if (!con.isClosed()) {
                        con.close();
                        logger.fine("[ShowPunches.getActiveEmployeesForDropdown] Database connection closed for TenantID: " + tenantId);
                    }
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "[ShowPunches.getActiveEmployeesForDropdown] Error closing connection for TenantID: " + tenantId, e);
                }
            }
        }
        return employeeList;
    }

    public static double applyAutoLunch(Connection con, int tenantId, int globalEID, double rawTotalHours) {
        boolean autoLunchEnabled = false; Double hoursRequiredForLunch = null; Integer lunchLengthMinutes = null;
        String sql = "SELECT s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME WHERE e.EID = ? AND e.TenantID = ?";
        if (con == null) { 
            logger.severe("[ShowPunches.applyAutoLunch] Database connection is null for TenantID: " + tenantId + ", EID: " + globalEID);
            return rawTotalHours; 
        }
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, globalEID); ps.setInt(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    autoLunchEnabled = rs.getBoolean("AUTO_LUNCH");
                    double hrsReqDb = rs.getDouble("HRS_REQUIRED"); hoursRequiredForLunch = rs.wasNull() ? null : hrsReqDb;
                    int lunchLen = rs.getInt("LUNCH_LENGTH"); lunchLengthMinutes = rs.wasNull() ? null : lunchLen;
                } else { 
                    logger.info("[ShowPunches.applyAutoLunch] No schedule info found for auto-lunch check. EID: " + globalEID + ", TenantID: " + tenantId);
                    return rawTotalHours; 
                }
            }
        } catch (SQLException e) { 
            logger.log(Level.SEVERE, "[ShowPunches.applyAutoLunch] SQL Error fetching schedule for auto-lunch. EID:" + globalEID + ", TenantID: " + tenantId, e); 
            return rawTotalHours; 
        }
        if (autoLunchEnabled && hoursRequiredForLunch != null && lunchLengthMinutes != null && hoursRequiredForLunch > 0 && lunchLengthMinutes > 0 && rawTotalHours > hoursRequiredForLunch) {
            double deduction = lunchLengthMinutes / 60.0;
            logger.info("[ShowPunches.applyAutoLunch] Auto-lunch CALCULATION for EID " + globalEID + ". Original: " + String.format(Locale.US, "%.2f",rawTotalHours) + ", Deducting: " + String.format(Locale.US, "%.2f",deduction) + " (Req: " + hoursRequiredForLunch + "hrs, Len: " + lunchLengthMinutes + "min)");
            return Math.max(0, rawTotalHours - deduction);
        }
        return rawTotalHours;
    }

    public static void calculateAndUpdatePunchTotal(Connection con, int tenantId, int eid, Timestamp punchInUtc, Timestamp punchOutUtc, long punchId) throws SQLException {
        if (punchId <= 0) { 
            logger.warning("[ShowPunches.calculateAndUpdatePunchTotal] Invalid punchId: " + punchId + " for EID: " + eid + ", TenantID: " + tenantId); 
            return; 
        }
        if (punchInUtc == null || punchOutUtc == null) {
            logger.info("[ShowPunches.calculateAndUpdatePunchTotal] Incomplete punch (P_ID=" + punchId + ", EID=" + eid + "). Setting TOTAL to NULL. Resetting OT/DT to 0.");
            String updateSql = "UPDATE PUNCHES SET TOTAL = NULL, OT = 0, DT = 0 WHERE PUNCH_ID = ? AND TenantID = ? AND EID = ?";
            try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                psUpdate.setLong(1, punchId); psUpdate.setInt(2, tenantId); psUpdate.setInt(3, eid);
                psUpdate.executeUpdate();
            }
            return;
        }
        double finalTotalHours;
        try {
            Duration duration = Duration.between(punchInUtc.toInstant(), punchOutUtc.toInstant());
            if (duration.isNegative() || duration.isZero()) {
                logger.warning("[ShowPunches.calculateAndUpdatePunchTotal] Punch In not before Punch Out for P_ID=" + punchId + ", EID=" + eid + ". Setting total to 0.");
                finalTotalHours = 0.0;
            } else {
                double exactHours = duration.getSeconds() / 3600.0;
                finalTotalHours = applyAutoLunch(con, tenantId, eid, exactHours);
            }
            finalTotalHours = Math.round(finalTotalHours * FINAL_ROUNDING_HOURS) / FINAL_ROUNDING_HOURS;
            String updateSql = "UPDATE PUNCHES SET TOTAL = ? WHERE PUNCH_ID = ? AND TenantID = ? AND EID = ?";
            try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                setOptionalDouble(psUpdate, 1, finalTotalHours); 
                psUpdate.setLong(2, punchId);
                psUpdate.setInt(3, tenantId); 
                psUpdate.setInt(4, eid);
                if (psUpdate.executeUpdate() <= 0) { 
                    logger.warning("[ShowPunches.calculateAndUpdatePunchTotal] Failed to update TOTAL for P_ID: " + punchId + ", EID: " + eid); 
                } else { 
                    logger.info("[ShowPunches.calculateAndUpdatePunchTotal] Updated P_ID: " + punchId + " (EID: " + eid + ") with TOTAL (incl. auto-lunch): " + String.format(Locale.US, "%.2f", finalTotalHours)); 
                }
            }
        } catch (Exception e) { 
            logger.log(Level.SEVERE, "[ShowPunches.calculateAndUpdatePunchTotal] Error for P_ID: " + punchId + ", EID: " + eid, e); 
            throw new SQLException("Error calculating/updating total for P_ID " + punchId + ": " + e.getMessage(), e); 
        }
    }

    public static Map<String, Object> getTimecardPunchData(int tenantId, int globalEID, LocalDate payPeriodStartDate, LocalDate payPeriodEndDate, Map<String, Object> employeeInfo, String userTimeZoneIdStr) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, String>> punchesDataList = new ArrayList<>();
        result.put("punches", punchesDataList);
        result.put("totalRegularHours", 0.0); result.put("totalOvertimeHours", 0.0); result.put("totalDoubleTimeHours", 0.0);

        if (tenantId <= 0 || globalEID <= 0 || payPeriodStartDate == null || payPeriodEndDate == null || employeeInfo == null || !isValid(userTimeZoneIdStr)) {
            logger.warning("[ShowPunches.getTimecardPunchData] Invalid input. TenantID: " + tenantId + ", EID: " + globalEID +
                           ", Start: " + payPeriodStartDate + ", End: " + payPeriodEndDate +
                           ", EmpInfoNull: " + (employeeInfo == null) + ", UserTZ: '" + userTimeZoneIdStr + "'");
            result.put("error", "Invalid input or timezone to load timecard data.");
            return result;
        }

        ZoneId displayZoneId;
        try {
            displayZoneId = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[ShowPunches.getTimecardPunchData] CRITICAL - Invalid userTimeZoneIdStr: '" + userTimeZoneIdStr +
                                     "'. Defaulting to " + ULTIMATE_DISPLAY_FALLBACK_ZONE_ID + ".", e);
            displayZoneId = ZoneId.of(ULTIMATE_DISPLAY_FALLBACK_ZONE_ID);
            result.put("error", (result.get("error") == null ? "" : result.get("error") + " ") + "A timezone display error occurred. Times are shown in UTC.");
        }
        
        // --- [FIX] Calculate full timestamp range for the query based on the display timezone ---
        Instant periodStartInstant = payPeriodStartDate.atStartOfDay(displayZoneId).toInstant();
        Instant periodEndInstant = payPeriodEndDate.plusDays(1).atStartOfDay(displayZoneId).toInstant();

        logger.info("[ShowPunches.getTimecardPunchData] Method Start. TenantID: " + tenantId + ", EID: " + globalEID +
                    ", Period (Local): " + payPeriodStartDate + " to " + payPeriodEndDate +
                    ", DisplayZoneId: " + displayZoneId.getId() + 
                    ", Query Range (UTC): " + periodStartInstant + " to " + periodEndInstant);

        DateTimeFormatter timeFormatterForDisplay = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH).withZone(displayZoneId);
        DateTimeFormatter timeFormatterForRaw = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
        DateTimeFormatter dateFormatterForDisplay = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);
        DateTimeFormatter isoDateFormatterForDataAttr = DateTimeFormatter.ISO_LOCAL_DATE;

        int gracePeriodMinutes = 5;
        try {
            String gracePeriodStr = Configuration.getProperty(tenantId, "GracePeriod", "5");
            if (isValid(gracePeriodStr)) gracePeriodMinutes = Integer.parseInt(gracePeriodStr.trim());
        } catch (Exception e) { logger.log(Level.WARNING, "[ShowPunches.getTimecardPunchData] Error fetching/parsing GracePeriod for T:" + tenantId, e); }
        logger.info("[ShowPunches.getTimecardPunchData] For TenantID " + tenantId + ", EID: " + globalEID + ", GracePeriod used: " + gracePeriodMinutes + " minutes.");

        String firstDayOfWeekSetting = Configuration.getProperty(tenantId, "FirstDayOfWeek", "SUNDAY").toUpperCase(Locale.ENGLISH);
        Map<LocalDate, Double> dailyAggregatedWorkHours = new LinkedHashMap<>();
        double periodTotalAllDisplayedHours = 0.0;
        
        // --- [FIX] Modified SQL to query by UTC timestamp (IN_1) instead of the UTC DATE column ---
        // This correctly includes punches made late in the evening in a local timezone.
        // It also fetches non-work punches (e.g., vacation) that fall within the date range.
        String sqlGetPunches = "SELECT `PUNCH_ID`, `EID`, `DATE` AS UTC_DB_DATE, `IN_1` AS UTC_IN_TIMESTAMP, `OUT_1` AS UTC_OUT_TIMESTAMP, `TOTAL`, `OT`, `DT`, `PUNCH_TYPE` " +
                               "FROM PUNCHES WHERE EID = ? AND TenantID = ? AND " +
                               "((IN_1 >= ? AND IN_1 < ?) OR (IN_1 IS NULL AND `DATE` BETWEEN ? AND ?)) " +
                               "ORDER BY `DATE` ASC, `IN_1` ASC";

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement psGetPunches = con.prepareStatement(sqlGetPunches)) {
            psGetPunches.setInt(1, globalEID); 
            psGetPunches.setInt(2, tenantId);
            psGetPunches.setTimestamp(3, Timestamp.from(periodStartInstant));
            psGetPunches.setTimestamp(4, Timestamp.from(periodEndInstant));
            psGetPunches.setDate(5, java.sql.Date.valueOf(payPeriodStartDate)); // For hours-only types
            psGetPunches.setDate(6, java.sql.Date.valueOf(payPeriodEndDate));   // For hours-only types

            try (ResultSet rsPunches = psGetPunches.executeQuery()) {
                while (rsPunches.next()) {
                    Map<String, String> punchMap = new HashMap<>();
                    long currentPunchId = rsPunches.getLong("PUNCH_ID");
                    Timestamp inTimestampUtc = rsPunches.getTimestamp("UTC_IN_TIMESTAMP");
                    Timestamp outTimestampUtc = rsPunches.getTimestamp("UTC_OUT_TIMESTAMP");
                    String punchType = rsPunches.getString("PUNCH_TYPE");
                    if (punchType == null) punchType = "N/A";

                    LocalDate displayPunchDate;
                    ZonedDateTime zdtIn = null; ZonedDateTime zdtOut = null;

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
                    punchMap.put("friendlyPunchDate", displayPunchDate.format(dateFormatterForDisplay));
                    punchMap.put("dayOfWeek", displayPunchDate.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));

                    String inTimeDisplay = (zdtIn != null) ? timeFormatterForDisplay.format(zdtIn) : NOT_APPLICABLE_DISPLAY;
                    String outTimeDisplay = (zdtOut != null) ? timeFormatterForDisplay.format(zdtOut) : NOT_APPLICABLE_DISPLAY;
                    String timeInRaw = (zdtIn != null) ? zdtIn.toLocalTime().format(timeFormatterForRaw) : NOT_APPLICABLE_DISPLAY;
                    String timeOutRaw = (zdtOut != null) ? zdtOut.toLocalTime().format(timeFormatterForRaw) : NOT_APPLICABLE_DISPLAY;

                    punchMap.put("inTimeCssClass", "");
                    punchMap.put("outTimeCssClass", "");

                    Time scheduledStartTimeSql = (Time) employeeInfo.get("shiftStart");
                    Time scheduledEndTimeSql = (Time) employeeInfo.get("shiftEnd");
                    
                    if (isWorkPunchType(punchType) && employeeInfo != null) {
                        if (scheduledStartTimeSql != null && zdtIn != null) {
                            LocalTime actualInLocal = zdtIn.toLocalTime();
                            LocalTime scheduledStartLocal = scheduledStartTimeSql.toLocalTime();
                            LocalTime graceStart = scheduledStartLocal.plusMinutes(gracePeriodMinutes);
                            if (actualInLocal.isAfter(graceStart)) {
                                punchMap.put("inTimeCssClass", "lateOrEarlyOutTag");
                            }
                        }
                        if (scheduledEndTimeSql != null && zdtOut != null) {
                            LocalTime actualOutLocal = zdtOut.toLocalTime();
                            LocalTime scheduledEndLocal = scheduledEndTimeSql.toLocalTime();
                            LocalTime graceEnd = scheduledEndLocal.minusMinutes(gracePeriodMinutes);
                            if (actualOutLocal.isBefore(graceEnd)) {
                                punchMap.put("outTimeCssClass", "lateOrEarlyOutTag");
                            }
                        }
                    }

                    punchMap.put("timeIn", inTimeDisplay);
                    punchMap.put("timeOut", outTimeDisplay);
                    punchMap.put("timeInRaw", timeInRaw);
                    punchMap.put("timeOutRaw", timeOutRaw);
                    punchMap.put("punchType", punchType);

                    double hoursToDisplay;
                    Double storedTotal = rsPunches.getObject("TOTAL") != null ? rsPunches.getDouble("TOTAL") : null;

                    if (isHoursOnlyType(punchType)) {
                        hoursToDisplay = (storedTotal != null) ? storedTotal : 0.0;
                    } else if (inTimestampUtc != null && outTimestampUtc != null) {
                        Duration d = Duration.between(inTimestampUtc.toInstant(), outTimestampUtc.toInstant());
                        if (!d.isNegative() && !d.isZero()) {
                            double rawDurationHours = d.toMillis() / 3_600_000.0;
                            hoursToDisplay = applyAutoLunch(con, tenantId, globalEID, rawDurationHours);
                        } else { hoursToDisplay = 0.0; }
                    } else {
                        hoursToDisplay = 0.0;
                    }
                    hoursToDisplay = Math.round(hoursToDisplay * FINAL_ROUNDING_HOURS) / FINAL_ROUNDING_HOURS;
                    punchMap.put("totalHours", String.format(Locale.US, "%.2f", hoursToDisplay));
                    punchesDataList.add(punchMap);

                    if (isWorkPunchType(punchType)) {
                        periodTotalAllDisplayedHours += hoursToDisplay;
                        dailyAggregatedWorkHours.put(displayPunchDate, dailyAggregatedWorkHours.getOrDefault(displayPunchDate, 0.0) + hoursToDisplay);
                    } else if (isHoursOnlyType(punchType)){
                        periodTotalAllDisplayedHours += hoursToDisplay;
                    }
                }
            }

            // --- OT Calculation Logic ---
            double calculatedPeriodRegular = 0.0;
            double calculatedPeriodOt = 0.0;
            double calculatedPeriodDt = 0.0;
            String wageType = (String) employeeInfo.getOrDefault("wageType", "");

            if (!"Hourly".equalsIgnoreCase(wageType)) {
                calculatedPeriodRegular = periodTotalAllDisplayedHours;
            } else {
                boolean weeklyOtFixedEnabled_calc = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "Overtime", "true"));
                boolean dailyOtEnabled_calc = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDaily", "false"));
                double dailyOtThreshold_calc = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDailyThreshold", "8.0"));
                boolean doubleTimeEnabled_calc = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDoubleTimeEnabled", "false"));
                double doubleTimeThreshold_calc = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDoubleTimeThreshold", "12.0"));
                boolean seventhDayOtEnabled_calc = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeSeventhDayEnabled", "false"));
                double seventhDayOTThreshold_calc = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeSeventhDayOTThreshold", "0.0"));
                double seventhDayDTThreshold_calc = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeSeventhDayDTThreshold", "8.0"));

                Map<LocalDate, Double> dailyHoursNetOfDailyRules = new LinkedHashMap<>();

                for (LocalDate date = payPeriodStartDate; !date.isAfter(payPeriodEndDate); date = date.plusDays(1)) {
                    double hoursWorkedToday = dailyAggregatedWorkHours.getOrDefault(date, 0.0);
                    if (hoursWorkedToday <= 0) {
                        dailyHoursNetOfDailyRules.put(date, 0.0);
                        continue;
                    }
                    double todaysReg = hoursWorkedToday;
                    double todaysOt = 0;
                    double todaysDt = 0;

                    if (doubleTimeEnabled_calc && todaysReg > doubleTimeThreshold_calc) {
                        todaysDt = todaysReg - doubleTimeThreshold_calc;
                        todaysReg = doubleTimeThreshold_calc;
                    }
                    if (dailyOtEnabled_calc && todaysReg > dailyOtThreshold_calc) {
                        todaysOt = todaysReg - dailyOtThreshold_calc;
                        todaysReg = dailyOtThreshold_calc;
                    }
                    calculatedPeriodDt += todaysDt;
                    calculatedPeriodOt += todaysOt;
                    dailyHoursNetOfDailyRules.put(date, Math.max(0, todaysReg));
                }

                LocalDate currentEvalDate = payPeriodStartDate;
                while (!currentEvalDate.isAfter(payPeriodEndDate)) {
                    LocalDate weekStart = calculateWeekStart(currentEvalDate, firstDayOfWeekSetting);
                    LocalDate weekEnd = weekStart.plusDays(6);
                    double hoursInWeekNetOfDailyRules = 0;
                    int daysWorkedInWorkWeek = 0;
                    List<LocalDate> workDaysThisWeek = new ArrayList<>();

                    for (LocalDate dateInWeek = weekStart; !dateInWeek.isAfter(weekEnd); dateInWeek = dateInWeek.plusDays(1)) {
                        if (dateInWeek.isBefore(payPeriodStartDate) || dateInWeek.isAfter(payPeriodEndDate)) continue;
                        hoursInWeekNetOfDailyRules += dailyHoursNetOfDailyRules.getOrDefault(dateInWeek, 0.0);
                        if (dailyAggregatedWorkHours.getOrDefault(dateInWeek, 0.0) > 0.001) {
                           daysWorkedInWorkWeek++;
                           workDaysThisWeek.add(dateInWeek);
                        }
                    }
                    Collections.sort(workDaysThisWeek);

                    if (seventhDayOtEnabled_calc && daysWorkedInWorkWeek >= 7 && !workDaysThisWeek.isEmpty()) {
                         LocalDate seventhDayActual = workDaysThisWeek.get(workDaysThisWeek.size()-1);
                         if (seventhDayActual.equals(weekStart.plusDays(6))) {
                            double hoursOnSeventhDay = dailyHoursNetOfDailyRules.getOrDefault(seventhDayActual, 0.0);
                            if (hoursOnSeventhDay > 0) {
                                double seventhDtApplied = 0;
                                double seventhOtApplied = 0;
                                if (hoursOnSeventhDay > seventhDayDTThreshold_calc) {
                                    seventhDtApplied = hoursOnSeventhDay - seventhDayDTThreshold_calc;
                                    calculatedPeriodDt += seventhDtApplied;
                                    hoursOnSeventhDay -= seventhDtApplied;
                                }
                                if (hoursOnSeventhDay > seventhDayOTThreshold_calc) {
                                   seventhOtApplied = hoursOnSeventhDay - seventhDayOTThreshold_calc;
                                   calculatedPeriodOt += seventhOtApplied;
                                } else if (seventhDayOTThreshold_calc == 0 && hoursOnSeventhDay > 0) {
                                   seventhOtApplied = hoursOnSeventhDay;
                                   calculatedPeriodOt += seventhOtApplied;
                                }
                                hoursInWeekNetOfDailyRules -= (seventhDtApplied + seventhOtApplied);
                                dailyHoursNetOfDailyRules.put(seventhDayActual, Math.max(0, dailyHoursNetOfDailyRules.get(seventhDayActual) - seventhDtApplied - seventhOtApplied));
                            }
                        }
                    }

                    if (weeklyOtFixedEnabled_calc && hoursInWeekNetOfDailyRules > WEEKLY_OT_THRESHOLD_FLSA) {
                        double weeklyOtToAdd = hoursInWeekNetOfDailyRules - WEEKLY_OT_THRESHOLD_FLSA;
                        calculatedPeriodOt += weeklyOtToAdd;
                    }
                    currentEvalDate = weekEnd.plusDays(1);
                }

                calculatedPeriodRegular = 0;
                for(double regHours : dailyHoursNetOfDailyRules.values()){
                    calculatedPeriodRegular += Math.max(0,regHours);
                }
            }

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
            logger.log(Level.SEVERE, "[ShowPunches.getTimecardPunchData] Error during data retrieval or OT calculation for T:" + tenantId + ", EID:" + globalEID, e);
            result.put("error", "Failed to retrieve or calculate timecard data: " + e.getMessage());
        }
        return result;
    }
}