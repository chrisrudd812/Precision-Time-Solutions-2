package timeclock.punches;

import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.Configuration;
import timeclock.db.DatabaseConnection;

public class ShowMobilePunches {
    private static final Logger logger = Logger.getLogger(ShowMobilePunches.class.getName());

    public static Map<String, Object> getTimecardPunchData(
            int tenantId, int globalEID, LocalDate payPeriodStartDate, LocalDate payPeriodEndDate,
            Map<String, Object> employeeInfo, String userTimeZoneIdStr) {

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> punches = new ArrayList<>();
        ZoneId displayZoneId = ZoneId.of(isValid(userTimeZoneIdStr) ? userTimeZoneIdStr : "UTC");

        try (Connection con = DatabaseConnection.getConnection()) {
            Map<String, Object> scheduleDetails = loadScheduleDetails(con, tenantId, (String) employeeInfo.get("schedule"));
            int gracePeriodMinutes = Integer.parseInt(Configuration.getProperty(tenantId, "GracePeriod", "0"));

            String punchDetailSql = "SELECT PUNCH_ID, IN_1, OUT_1, TOTAL, PUNCH_TYPE, `DATE` FROM punches " +
                                  "WHERE EID = ? AND TenantID = ? AND `DATE` BETWEEN ? AND ? ORDER BY `DATE` ASC, IN_1 ASC";
            try (PreparedStatement ps = con.prepareStatement(punchDetailSql)) {
                ps.setInt(1, globalEID);
                ps.setInt(2, tenantId);
                ps.setDate(3, java.sql.Date.valueOf(payPeriodStartDate));
                ps.setDate(4, java.sql.Date.valueOf(payPeriodEndDate));

                DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("h:mm a").withZone(displayZoneId);
                DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("MM/dd");

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> punch = new HashMap<>();
                        Timestamp inUtc = rs.getTimestamp("IN_1");
                        Timestamp outUtc = rs.getTimestamp("OUT_1");
                        ZonedDateTime inZdt = (inUtc != null) ? inUtc.toInstant().atZone(displayZoneId) : null;
                        ZonedDateTime outZdt = (outUtc != null) ? outUtc.toInstant().atZone(displayZoneId) : null;
                        LocalDate punchDate = rs.getDate("DATE").toLocalDate();

                        punch.put("id", rs.getLong("PUNCH_ID"));
                        punch.put("date", punchDate.format(dateFmt));
                        punch.put("dow", punchDate.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
                        punch.put("in", (inZdt != null) ? timeFmt.format(inZdt) : "");
                        punch.put("out", (outZdt != null) ? timeFmt.format(outZdt) : "");
                        punch.put("type", rs.getString("PUNCH_TYPE"));
                        punch.put("hours", String.format("%.2f", rs.getDouble("TOTAL")));
                        punch.put("isLate", isPunchLate(inZdt, scheduleDetails, gracePeriodMinutes));
                        punch.put("isEarlyOut", isPunchEarlyOut(outZdt, scheduleDetails, gracePeriodMinutes));
                        punches.add(punch);
                    }
                }
            }
            result.put("punches", punches);

            String totalsSql = "SELECT SUM(TOTAL), SUM(OT), SUM(DT) FROM punches WHERE EID = ? AND TenantID = ? AND `DATE` BETWEEN ? AND ?";
            try (PreparedStatement ps = con.prepareStatement(totalsSql)) {
                ps.setInt(1, globalEID);
                ps.setInt(2, tenantId);
                ps.setDate(3, java.sql.Date.valueOf(payPeriodStartDate));
                ps.setDate(4, java.sql.Date.valueOf(payPeriodEndDate));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        result.put("totalRegularHours", rs.getDouble(1));
                        result.put("totalOvertimeHours", rs.getDouble(2));
                        result.put("totalDoubleTimeHours", rs.getDouble(3));
                    } else {
                        result.put("totalRegularHours", 0.0);
                        result.put("totalOvertimeHours", 0.0);
                        result.put("totalDoubleTimeHours", 0.0);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in ShowMobilePunches for T:" + tenantId + ", EID:" + globalEID, e);
            result.put("error", "Failed to load punch data.");
        }
        return result;
    }

    private static boolean isPunchLate(ZonedDateTime punchInTime, Map<String, Object> schedule, int graceMinutes) {
        if (punchInTime == null || schedule.isEmpty()) return false;
        List<DayOfWeek> scheduledDays = (List<DayOfWeek>) schedule.get("days");
        LocalTime shiftStartTime = (LocalTime) schedule.get("start");
        if (shiftStartTime == null || scheduledDays == null || !scheduledDays.contains(punchInTime.getDayOfWeek())) {
            return false;
        }
        return punchInTime.toLocalTime().isAfter(shiftStartTime.plusMinutes(graceMinutes));
    }

    private static boolean isPunchEarlyOut(ZonedDateTime punchOutTime, Map<String, Object> schedule, int graceMinutes) {
        if (punchOutTime == null || schedule.isEmpty()) return false;
        List<DayOfWeek> scheduledDays = (List<DayOfWeek>) schedule.get("days");
        LocalTime shiftEndTime = (LocalTime) schedule.get("end");
        if (shiftEndTime == null || scheduledDays == null || !scheduledDays.contains(punchOutTime.getDayOfWeek())) {
            return false;
        }
        return punchOutTime.toLocalTime().isBefore(shiftEndTime.minusMinutes(graceMinutes));
    }

    private static Map<String, Object> loadScheduleDetails(Connection con, int tenantId, String scheduleName) throws SQLException {
        Map<String, Object> details = new HashMap<>();
        if (!isValid(scheduleName)) return details;
        String sql = "SELECT SHIFT_START, SHIFT_END, DAYS_WORKED FROM schedules WHERE TenantID = ? AND NAME = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setString(2, scheduleName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Time shiftStart = rs.getTime("SHIFT_START");
                    Time shiftEnd = rs.getTime("SHIFT_END");
                    if (shiftStart != null) details.put("start", shiftStart.toLocalTime());
                    if (shiftEnd != null) details.put("end", shiftEnd.toLocalTime());
                    details.put("days", getScheduledDays(rs.getString("DAYS_WORKED")));
                }
            }
        }
        return details;
    }

    private static List<DayOfWeek> getScheduledDays(String daysWorkedStr) {
        List<DayOfWeek> scheduledDays = new ArrayList<>();
        if (daysWorkedStr != null && daysWorkedStr.length() == 7) {
            if (daysWorkedStr.charAt(0) == 'S') scheduledDays.add(DayOfWeek.SUNDAY);
            if (daysWorkedStr.charAt(1) == 'M') scheduledDays.add(DayOfWeek.MONDAY);
            if (daysWorkedStr.charAt(2) == 'T') scheduledDays.add(DayOfWeek.TUESDAY);
            if (daysWorkedStr.charAt(3) == 'W') scheduledDays.add(DayOfWeek.WEDNESDAY);
            if (daysWorkedStr.charAt(4) == 'H') scheduledDays.add(DayOfWeek.THURSDAY);
            if (daysWorkedStr.charAt(5) == 'F') scheduledDays.add(DayOfWeek.FRIDAY);
            if (daysWorkedStr.charAt(6) == 'A') scheduledDays.add(DayOfWeek.SATURDAY);
        }
        return scheduledDays;
    }

    private static boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s);
    }
}