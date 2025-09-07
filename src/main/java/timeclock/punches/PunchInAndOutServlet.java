package timeclock.punches;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
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

    private boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !"undefined".equalsIgnoreCase(s) && !"null".equalsIgnoreCase(s);
    }

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
        String sql = "SELECT 1 FROM punches WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL AND PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Regular') LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setInt(2, eid);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void checkTimeDayRestriction(Connection con, int tenantId, ZonedDateTime punchTimeInUserZone) throws Exception {
        boolean isRestrictionGloballyEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByTimeDay", "false"));
        if (!isRestrictionGloballyEnabled) {
            return; 
        }

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
                        LocalTime allowedStart = startTime.toLocalTime();
                        LocalTime allowedEnd = endTime.toLocalTime();

                        if (currentTime.isBefore(allowedStart) || currentTime.isAfter(allowedEnd)) {
                            DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a");
                            throw new Exception("Punch is outside the allowed time window for " + dayOfWeek + " (" + allowedStart.format(tf) + " - " + allowedEnd.format(tf) + ").");
                        }
                    }
                } else {
                    boolean allowPunchOnUnrestrictedDays = "true".equalsIgnoreCase(
                        Configuration.getProperty(tenantId, "allowUnselectedDays", "true")
                    );

                    if (!allowPunchOnUnrestrictedDays) {
                        throw new Exception("Punching is not allowed on " + dayOfWeek + " per company policy.");
                    }
                }
            }
        }
    }


    private void checkLocationRestriction(Connection con, int tenantId, double userLat, double userLon) throws Exception {
        boolean isRestrictionEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByLocation", "false"));
        if (!isRestrictionEnabled) {
            return; 
        }

        List<Map<String, Object>> enabledLocations = new ArrayList<>();
        String sql = "SELECT LocationName, Latitude, Longitude, RadiusMeters FROM geofence_locations WHERE TenantID = ? AND IsEnabled = TRUE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> loc = new HashMap<>();
                    loc.put("name", rs.getString("LocationName"));
                    loc.put("lat", rs.getBigDecimal("Latitude").doubleValue());
                    loc.put("lon", rs.getBigDecimal("Longitude").doubleValue());
                    loc.put("radius", rs.getInt("RadiusMeters"));
                    enabledLocations.add(loc);
                }
            }
        }

        if (enabledLocations.isEmpty()) {
            return; 
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

    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth's radius in meters
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private void checkDeviceRestriction(Connection con, int tenantId, int eid, String deviceFingerprint) throws Exception {
        boolean isRestrictionEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByDevice", "false"));
        logger.log(Level.INFO, "[DEVICE-CHECK] For TenantID {0}, EID {1}: RestrictByDevice is set to {2}", new Object[]{tenantId, eid, isRestrictionEnabled});

        if (!isRestrictionEnabled) {
            logger.info("[DEVICE-CHECK] Device check is disabled. Skipping.");
            return;
        }

        if (!isValid(deviceFingerprint)) {
            logger.warning("[DEVICE-CHECK] Device restriction is ON, but fingerprint was not provided. Punch REJECTED.");
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
        logger.log(Level.INFO, "[DEVICE-CHECK] Found {0} registered devices for EID {1}.", new Object[]{registeredDevices.size(), eid});
        
        for (Map<String, Object> device : registeredDevices) {
            if (deviceFingerprint.equals(device.get("hash"))) {
                if ((Boolean) device.get("enabled")) {
                    logger.info("[DEVICE-CHECK] Device is already registered and enabled. Updating LastUsedDate and allowing punch.");
                    String updateSql = "UPDATE employee_devices SET LastUsedDate = NOW() WHERE DeviceID = ?";
                    try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                        psUpdate.setInt(1, (Integer) device.get("id"));
                        psUpdate.executeUpdate();
                    }
                    return;
                } else {
                    logger.warning("[DEVICE-CHECK] Device is registered but DISABLED. Punch REJECTED.");
                    throw new Exception("This device has been disabled. Please use an approved device or contact your administrator.");
                }
            }
        }

        int maxDevices = Integer.parseInt(Configuration.getProperty(tenantId, "MaxDevicesPerUserGlobal", "2"));
        logger.log(Level.INFO, "[DEVICE-CHECK] Device is new. Max allowed devices: {0}. Currently registered: {1}.", new Object[]{maxDevices, registeredDevices.size()});

        if (registeredDevices.size() < maxDevices) {
            logger.info("[DEVICE-CHECK] User is under the device limit. Registering new device...");
            String insertSql = "INSERT INTO employee_devices (TenantID, EID, DeviceFingerprintHash, DeviceDescription, RegisteredDate, LastUsedDate, IsEnabled) VALUES (?, ?, ?, ?, NOW(), NOW(), TRUE)";
            try (PreparedStatement psInsert = con.prepareStatement(insertSql)) {
                psInsert.setInt(1, tenantId);
                psInsert.setInt(2, eid);
                psInsert.setString(3, deviceFingerprint);
                psInsert.setString(4, "New Device (Auto-Registered)");
                psInsert.executeUpdate();
                logger.info("[DEVICE-CHECK] New device registered successfully. Allowing punch.");
                return;
            }
        } else {
            logger.log(Level.WARNING, "[DEVICE-CHECK] User has reached the maximum number of allowed devices ({0}). Punch from new device REJECTED.", maxDevices);
            throw new Exception("You have reached the maximum number of registered devices. To use this device, an administrator must remove an existing one first.");
        }
    }


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

        ZoneId effectiveZoneId;
        try {
            String zoneIdToUse = isValid(browserTimeZoneIdStr) ? browserTimeZoneIdStr : Configuration.getProperty(tenantId, "DefaultTimeZone", DEFAULT_TENANT_FALLBACK_TIMEZONE);
            effectiveZoneId = ZoneId.of(zoneIdToUse);
        } catch (Exception e) {
            effectiveZoneId = ZoneId.of(DEFAULT_TENANT_FALLBACK_TIMEZONE);
        }

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);
            
            Timestamp punchTimestampUtc = Timestamp.from(Instant.now());
            ZonedDateTime punchTimeInUserZone = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneId);
            LocalDate punchDateForDb = punchTimeInUserZone.toLocalDate();

            // --- ALL RESTRICTION CHECKS HAPPEN HERE ---
            checkTimeDayRestriction(con, tenantId, punchTimeInUserZone);
            checkDeviceRestriction(con, tenantId, punchEID, deviceFingerprintHash);
            
            // --- [FIX START] REWRITTEN LOCATION CHECK LOGIC ---
            // First, check if the location restriction feature is enabled at all.
            boolean isLocationRestrictionEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByLocation", "false"));

            // Only perform location checks if the feature is turned on.
            if (isLocationRestrictionEnabled) {
                // If the feature is on, we REQUIRE location data.
                if (isValid(latitudeStr) && isValid(longitudeStr)) {
                    // We have the data, so validate it.
                    checkLocationRestriction(con, tenantId, Double.parseDouble(latitudeStr), Double.parseDouble(longitudeStr));
                } else {
                    // The feature is on, but the browser did not provide location. This is an error.
                    throw new Exception("Location is required for punching, but it could not be determined. Please enable location services in your browser and try again.");
                }
            }
            // If isLocationRestrictionEnabled is false, this entire block is skipped, and no location error can occur.
            // --- [FIX END] ---


            Map<String, Object> scheduleInfo = getScheduleDetails(con, tenantId, punchEID);
            boolean isLate = false;
            boolean isEarlyOut = false;

            if (!scheduleInfo.isEmpty()) {
                // ... (schedule logic is unchanged)
            }

            if ("IN".equals(punchAction)) {
                if (hasOpenPunch(con, tenantId, punchEID)) {
                    throw new Exception("You are already clocked in. Please clock out before clocking in again.");
                }

                String insertSql = "INSERT INTO punches (TenantID, EID, DATE, IN_1, PUNCH_TYPE, LATE) VALUES (?, ?, ?, ?, 'User Initiated', ?)";
                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, tenantId);
                    ps.setInt(2, punchEID);
                    ps.setDate(3, java.sql.Date.valueOf(punchDateForDb));
                    ps.setTimestamp(4, punchTimestampUtc);
                    ps.setBoolean(5, isLate);
                    if (ps.executeUpdate() > 0) messageForRedirect = "Punch IN successful.";
                    else throw new SQLException("Failed to record punch.");
                }
            } else if ("OUT".equals(punchAction)) {
                String sql = "SELECT PUNCH_ID, IN_1 FROM punches WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL AND PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Regular') ORDER BY PUNCH_ID DESC LIMIT 1";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, tenantId);
                    ps.setInt(2, punchEID);
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
                    } else {
                    	isError = true; messageForRedirect = "No open work punch found to clock out against.";
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
        String punchStatus = isError ? "error" : "success";
        String paramType = isError ? "error" : "message";
        redirectUrl += "?punchStatus=" + punchStatus + "&" + paramType + "=" + URLEncoder.encode(messageForRedirect, "UTF-8");
        
        response.sendRedirect(redirectUrl);
    }
}