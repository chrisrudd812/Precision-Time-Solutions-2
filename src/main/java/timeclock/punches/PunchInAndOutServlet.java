package timeclock.punches;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;
import timeclock.util.Helpers; // Import the helpers class

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/PunchInAndOutServlet")
public class PunchInAndOutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PunchInAndOutServlet.class.getName());
    private static final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
    
    // Helper methods (getScheduleDetails, hasOpenPunch, all check... methods) are unchanged.
    private Map<String, Object> getScheduleDetails(Connection con, int tenantId, int eid) throws SQLException {
        Map<String, Object> scheduleDetails = new HashMap<>();
        String sql = "SELECT s.SHIFT_START, s.SHIFT_END, s.DAYS_WORKED " +
                     "FROM employee_data e LEFT JOIN schedules s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME " +
                     "WHERE e.TenantID = ? AND e.EID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setInt(2, eid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    scheduleDetails.put("shiftStart", rs.getTime("SHIFT_START"));
                    scheduleDetails.put("shiftEnd", rs.getTime("SHIFT_END"));
                    scheduleDetails.put("daysWorkedStr", rs.getString("DAYS_WORKED"));
                }
            }
        }
        return scheduleDetails;
    }
    
    private boolean hasOpenPunch(Connection con, int tenantId, int eid) throws SQLException {
        String sql = "SELECT IN_1 FROM punches WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL AND PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Regular') ORDER BY IN_1 DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setInt(2, eid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp lastInTime = rs.getTimestamp("IN_1");
                    if (lastInTime != null) {
                        long hoursSinceLastPunch = (System.currentTimeMillis() - lastInTime.getTime()) / (1000 * 60 * 60);
                        // Only consider it an open punch if it's within 20 hours
                        return hoursSinceLastPunch <= 20;
                    }
                }
                return false;
            }
        }
    }

    private void checkTimeDayRestriction(Connection con, int tenantId, ZonedDateTime punchTimeInUserZone) throws Exception {
        boolean isRestrictionGloballyEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByTimeDay", "false"));
        if (!isRestrictionGloballyEnabled) return;
        String dayOfWeek = punchTimeInUserZone.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        LocalTime currentTime = punchTimeInUserZone.toLocalTime();
        String sql = "SELECT IsRestricted, AllowedStartTime, AllowedEndTime FROM day_time_restrictions WHERE TenantID = ? AND DayOfWeek = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setString(2, dayOfWeek);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getBoolean("IsRestricted")) {
                    Time startTime = rs.getTime("AllowedStartTime");
                    Time endTime = rs.getTime("AllowedEndTime");
                    if (startTime != null && endTime != null) {
                        if (currentTime.isBefore(startTime.toLocalTime()) || currentTime.isAfter(endTime.toLocalTime())) {
                            DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a");
                            throw new Exception("Punch is outside the allowed time window for " + dayOfWeek + " (" + startTime.toLocalTime().format(tf) + " - " + endTime.toLocalTime().format(tf) + ").");
                        }
                    }
                } else {
                    if (!"true".equalsIgnoreCase(Configuration.getProperty(tenantId, "allowUnselectedDays", "true"))) {
                        throw new Exception("Punching is not allowed on " + dayOfWeek + " per company policy.");
                    }
                }
            }
        }
    }
    
    private void checkDeviceRestriction(Connection con, int tenantId, int eid, String deviceFingerprint) throws Exception {
        boolean isRestrictionEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByDevice", "false"));
        if (!isRestrictionEnabled) return;
        if (!Helpers.isStringValid(deviceFingerprint)) {
            throw new Exception("Could not identify your device. Please ensure scripts are running correctly and try again.");
        }
        List<Map<String, Object>> registeredDevices = new ArrayList<>();
        String getDevicesSql = "SELECT DeviceID, DeviceFingerprintHash, IsEnabled FROM employee_devices WHERE TenantID = ? AND EID = ?";
        try (PreparedStatement ps = con.prepareStatement(getDevicesSql)) {
            ps.setInt(1, tenantId);
            ps.setInt(2, eid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> device = new HashMap<>();
                    device.put("id", rs.getInt("DeviceID"));
                    device.put("hash", rs.getString("DeviceFingerprintHash"));
                    device.put("enabled", rs.getBoolean("IsEnabled"));
                    registeredDevices.add(device);
                }
            }
        }
        for (Map<String, Object> device : registeredDevices) {
            if (deviceFingerprint.equals(device.get("hash"))) {
                if ((Boolean) device.get("enabled")) {
                    String updateSql = "UPDATE employee_devices SET LastUsedDate = NOW() WHERE DeviceID = ?";
                    try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                        psUpdate.setInt(1, (Integer) device.get("id"));
                        psUpdate.executeUpdate();
                    }
                    return;
                } else {
                    throw new Exception("This device has been disabled. Please use an approved device or contact your administrator.");
                }
            }
        }
        int maxDevices = Integer.parseInt(Configuration.getProperty(tenantId, "MaxDevicesPerUserGlobal", "2"));
        if (registeredDevices.size() < maxDevices) {
            String insertSql = "INSERT INTO employee_devices (TenantID, EID, DeviceFingerprintHash, DeviceDescription, RegisteredDate, LastUsedDate, IsEnabled) VALUES (?, ?, ?, ?, NOW(), NOW(), TRUE)";
            try (PreparedStatement psInsert = con.prepareStatement(insertSql)) {
                psInsert.setInt(1, tenantId);
                psInsert.setInt(2, eid);
                psInsert.setString(3, deviceFingerprint);
                psInsert.setString(4, "New Device (Auto-Registered)");
                psInsert.executeUpdate();
                return;
            }
        } else {
            throw new Exception("You have reached the maximum number of registered devices. To use this device, an administrator must remove an existing one first.");
        }
    }
    
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void checkLocationRestriction(Connection con, int tenantId, double userLat, double userLon) throws Exception {
        if (!Helpers.isLocationCheckRequired(tenantId)) {
            return;
        }
        if (Double.isNaN(userLat) || Double.isNaN(userLon)) {
             throw new Exception("Location access is required but was not provided. On mobile devices, please tap the location icon in your browser's address bar and select 'Allow', then try punching again. If the issue persists, ensure location services are enabled on your device.");
        }
        List<Map<String, Object>> enabledLocations = new ArrayList<>();
        String getLocationsSql = "SELECT Latitude, Longitude, RadiusMeters FROM geofence_locations WHERE TenantID = ? AND IsEnabled = TRUE";
        try (PreparedStatement ps = con.prepareStatement(getLocationsSql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> loc = new HashMap<>();
                    loc.put("lat", rs.getBigDecimal("Latitude").doubleValue());
                    loc.put("lon", rs.getBigDecimal("Longitude").doubleValue());
                    loc.put("radius", rs.getInt("RadiusMeters"));
                    enabledLocations.add(loc);
                }
            }
        }
        boolean isWithinAnyFence = false;
        for (Map<String, Object> location : enabledLocations) {
            double distance = calculateHaversineDistance(userLat, userLon, (double) location.get("lat"), (double) location.get("lon"));
            if (distance <= (int) location.get("radius")) {
                isWithinAnyFence = true;
                break;
            }
        }
        if (!isWithinAnyFence) {
            throw new Exception("You are not within an authorized punch location. Please move closer to an approved area and try again.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String punchAction = request.getParameter("punchAction");
        String eidStr = request.getParameter("punchEID");
        String browserTimeZoneIdStr = request.getParameter("browserTimeZoneId");
        String latitudeStr = request.getParameter("latitude");
        String longitudeStr = request.getParameter("longitude");
        String deviceFingerprintHash = request.getParameter("deviceFingerprintHash");
        
        HttpSession session = request.getSession(false);
        String messageForRedirect = null;
        boolean isError = false;

        if (session == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", "UTF-8"));
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        Integer sessionEid = (Integer) session.getAttribute("EID");
        int punchEID = (eidStr != null && !eidStr.isEmpty()) ? Integer.parseInt(eidStr) : sessionEid;

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);
            
            // =================================================================
            //  OPTIMIZED ORDER OF OPERATIONS
            // =================================================================

            // STEP 1 (FASTEST): Check for invalid punch actions first.
            if ("IN".equals(punchAction)) {
                if (hasOpenPunch(con, tenantId, punchEID)) {
                    throw new Exception("You are already clocked in. Please clock out before clocking in again.");
                }
            } else if ("OUT".equals(punchAction)) {
                if (!hasOpenPunch(con, tenantId, punchEID)) {
                    throw new Exception("No open work punch found to clock out against. You are already clocked out.");
                }
            } else {
                throw new Exception("Invalid punch action specified.");
            }

            // If the action is valid, get the current time and proceed.
            Timestamp punchTimestampUtc = Timestamp.from(Instant.now());
            ZoneId effectiveZoneId;
            try {
                String zoneIdToUse = Helpers.isStringValid(browserTimeZoneIdStr) ? browserTimeZoneIdStr : Configuration.getProperty(tenantId, "DefaultTimeZone", DEFAULT_TENANT_FALLBACK_TIMEZONE);
                effectiveZoneId = ZoneId.of(zoneIdToUse);
            } catch (Exception e) {
                effectiveZoneId = ZoneId.of(DEFAULT_TENANT_FALLBACK_TIMEZONE);
            }
            ZonedDateTime punchTimeInUserZone = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneId);

            // STEP 2: Restriction Checks (in order from fastest to slowest overall process)
            checkTimeDayRestriction(con, tenantId, punchTimeInUserZone);
            checkDeviceRestriction(con, tenantId, punchEID, deviceFingerprintHash);
            
            double userLat = Helpers.isStringValid(latitudeStr) ? Double.parseDouble(latitudeStr) : Double.NaN;
            double userLon = Helpers.isStringValid(longitudeStr) ? Double.parseDouble(longitudeStr) : Double.NaN;
            checkLocationRestriction(con, tenantId, userLat, userLon);

            // STEP 3: All checks passed. Process the punch.
            LocalDate punchDateForDb = punchTimeInUserZone.toLocalDate();
            boolean isLate = false;
            boolean isEarlyOut = false;

            if ("IN".equals(punchAction)) {
                String insertSql = "INSERT INTO punches (TenantID, EID, DATE, IN_1, PUNCH_TYPE, LATE) VALUES (?, ?, ?, ?, 'User Initiated', ?)";
                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, tenantId); ps.setInt(2, punchEID);
                    ps.setDate(3, java.sql.Date.valueOf(punchDateForDb));
                    ps.setTimestamp(4, punchTimestampUtc); ps.setBoolean(5, isLate);
                    if (ps.executeUpdate() > 0) messageForRedirect = "Punch IN successful.";
                    else throw new SQLException("Failed to record punch.");
                }
            } else { // "OUT" action
                String sql = "SELECT PUNCH_ID, IN_1 FROM punches WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL ORDER BY PUNCH_ID DESC LIMIT 1";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, tenantId); ps.setInt(2, punchEID);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        long punchId = rs.getLong("PUNCH_ID");
                        Timestamp inTime = rs.getTimestamp("IN_1");
                        String updateSql = "UPDATE punches SET OUT_1 = ?, EARLY_OUTS = ? WHERE PUNCH_ID = ?";
                        try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                            psUpdate.setTimestamp(1, punchTimestampUtc);
                            psUpdate.setBoolean(2, isEarlyOut);
                            psUpdate.setLong(3, punchId);
                            if (psUpdate.executeUpdate() > 0) {
                                messageForRedirect = "Punch OUT successful.";
                                ShowPunches.calculateAndUpdatePunchTotal(con, tenantId, punchEID, inTime, punchTimestampUtc, punchId);
                            } else throw new SQLException("Failed to update punch record.");
                        }
                    }
                }
            }
            
            con.commit();

        } catch (Exception e) {
            isError = true;
            messageForRedirect = e.getMessage();
            logger.log(Level.WARNING, "Punch failed for EID " + punchEID + ": " + e.getMessage(), e);
            if(con != null) try { con.rollback(); } catch (SQLException ex) { logger.log(Level.WARNING, "Rollback failed", ex); }
        } finally {
            if(con != null) try { con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Connection close failed", ex); }
        }

        String redirectUrl = request.getContextPath() + "/timeclock.jsp";
        if (messageForRedirect != null) {
            String punchStatus = isError ? "error" : "success";
            String paramType = isError ? "error" : "message";
            redirectUrl += "?punchStatus=" + punchStatus + "&" + paramType + "=" + URLEncoder.encode(messageForRedirect, "UTF-8");
        }
        response.sendRedirect(redirectUrl);
    }
}