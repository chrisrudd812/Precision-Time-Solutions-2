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
        // Fast exit if time restrictions are disabled globally
        if (!"true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByTimeDay", "false"))) {
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
    
    private void checkDeviceRestriction(Connection con, int tenantId, int eid, String deviceFingerprint, HttpServletRequest request) throws Exception {
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
            String insertSql = "INSERT INTO employee_devices (TenantID, EID, DeviceFingerprintHash, DeviceDescription, RegisteredDate, LastUsedDate, IsEnabled, UserAgentAtRegistration, IPAddressAtRegistration) VALUES (?, ?, ?, ?, NOW(), NOW(), TRUE, ?, ?)";
            try (PreparedStatement psInsert = con.prepareStatement(insertSql)) {
                psInsert.setInt(1, tenantId);
                psInsert.setInt(2, eid);
                psInsert.setString(3, deviceFingerprint);
                psInsert.setString(4, generateDeviceDescription(request.getHeader("User-Agent")));
                String userAgent = request.getHeader("User-Agent");
                psInsert.setString(5, userAgent != null ? userAgent.substring(0, Math.min(userAgent.length(), 100)) : null);
                psInsert.setString(6, getClientIpAddress(request));
                psInsert.executeUpdate();
                return;
            }
        } else {
            throw new Exception("You have reached the maximum number of registered devices. To use this device, an administrator must remove an existing one first.");
        }
    }
    
    private String generateDeviceDescription(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return "Unknown Device";
        }
        
        // Check for mobile devices
        if (userAgent.contains("Mobile") || userAgent.contains("Android") || userAgent.contains("iPhone")) {
            if (userAgent.contains("iPhone")) return "iPhone";
            if (userAgent.contains("iPad")) return "iPad";
            if (userAgent.contains("Android")) return "Android Device";
            return "Mobile Device";
        }
        
        // Check for desktop browsers
        if (userAgent.contains("Chrome")) return "Desktop - Chrome";
        if (userAgent.contains("Firefox")) return "Desktop - Firefox";
        if (userAgent.contains("Safari") && !userAgent.contains("Chrome")) return "Desktop - Safari";
        if (userAgent.contains("Edge")) return "Desktop - Edge";
        
        return "Desktop Computer";
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddr();
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

    private void checkLocationRestriction(Connection con, int tenantId, double userLat, double userLon, String accuracyStr) throws Exception {
        if (Double.isNaN(userLat) || Double.isNaN(userLon)) {
            throw new Exception("Location access is required but was not provided. Please ensure location services are enabled and try again.");
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
            String accuracyInfo = "";
            if (accuracyStr != null && !accuracyStr.isEmpty()) {
                try {
                    double accuracy = Double.parseDouble(accuracyStr);
                    accuracyInfo = " (Location accuracy: " + Math.round(accuracy) + " meters)";
                } catch (NumberFormatException e) {
                    // Ignore if accuracy parsing fails
                }
            }
            throw new Exception("You are not within an authorized punch location. Please move closer to an approved area and try again." + accuracyInfo);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String punchAction = request.getParameter("punchAction");
        String eidStr = request.getParameter("punchEID");
        
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
            
            // STEP 1 (FASTEST): Check punch status FIRST - fail immediately if already punched in/out
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

            // Only get other parameters if punch status check passes
            String browserTimeZoneIdStr = request.getParameter("browserTimeZoneId");
            String latitudeStr = request.getParameter("latitude");
            String longitudeStr = request.getParameter("longitude");
            String accuracyStr = request.getParameter("accuracy");
            String deviceFingerprintHash = request.getParameter("deviceFingerprintHash");
            
            con.setAutoCommit(false);
            
            // Get current time for valid punches
            Timestamp punchTimestampUtc = Timestamp.from(Instant.now());
            ZoneId effectiveZoneId;
            try {
                String zoneIdToUse = Helpers.isStringValid(browserTimeZoneIdStr) ? browserTimeZoneIdStr : Configuration.getProperty(tenantId, "DefaultTimeZone", DEFAULT_TENANT_FALLBACK_TIMEZONE);
                effectiveZoneId = ZoneId.of(zoneIdToUse);
            } catch (Exception e) {
                effectiveZoneId = ZoneId.of(DEFAULT_TENANT_FALLBACK_TIMEZONE);
            }
            ZonedDateTime punchTimeInUserZone = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneId);

            // STEP 2: Additional restriction checks (only for valid punches)
            checkTimeDayRestriction(con, tenantId, punchTimeInUserZone);
            checkDeviceRestriction(con, tenantId, punchEID, deviceFingerprintHash, request);
            
            if (Helpers.isLocationCheckRequired(tenantId)) {
                double userLat = Helpers.isStringValid(latitudeStr) ? Double.parseDouble(latitudeStr) : Double.NaN;
                double userLon = Helpers.isStringValid(longitudeStr) ? Double.parseDouble(longitudeStr) : Double.NaN;
                checkLocationRestriction(con, tenantId, userLat, userLon, accuracyStr);
            }

            // STEP 3: Process the punch
            LocalDate punchDateForDb = punchTimeInUserZone.toLocalDate();
            
            // Get schedule details for late/early calculations
            Map<String, Object> scheduleDetails = getScheduleDetails(con, tenantId, punchEID);
            Time shiftStart = (Time) scheduleDetails.get("shiftStart");
            Time shiftEnd = (Time) scheduleDetails.get("shiftEnd");
            
            // Calculate late/early status
            boolean isLate = false;
            boolean isEarlyOut = false;
            
            // Check if today is a scheduled work day
            String daysWorkedStr = (String) scheduleDetails.get("daysWorkedStr");
            
            // Convert day of week to single letter format: M=Monday, T=Tuesday, W=Wednesday, H=Thursday, F=Friday, S=Saturday, U=Sunday
            String todayLetter = "";
            switch (punchTimeInUserZone.getDayOfWeek()) {
                case MONDAY: todayLetter = "M"; break;
                case TUESDAY: todayLetter = "T"; break;
                case WEDNESDAY: todayLetter = "W"; break;
                case THURSDAY: todayLetter = "H"; break;
                case FRIDAY: todayLetter = "F"; break;
                case SATURDAY: todayLetter = "S"; break;
                case SUNDAY: todayLetter = "U"; break;
            }
            
            boolean isTodayScheduled = (daysWorkedStr != null && daysWorkedStr.contains(todayLetter));
            
            // Only calculate late/early if it's a scheduled work day and schedule times exist
            if (isTodayScheduled && shiftStart != null && shiftEnd != null) {
                if ("IN".equals(punchAction)) {
                    int gracePeriodMinutes = Integer.parseInt(Configuration.getProperty(tenantId, "GracePeriod", "0"));
                    LocalTime punchTime = punchTimeInUserZone.toLocalTime();
                    LocalTime allowedStartTime = shiftStart.toLocalTime().plusMinutes(gracePeriodMinutes);
                    isLate = punchTime.isAfter(allowedStartTime);
                } else if ("OUT".equals(punchAction)) {
                    LocalTime punchTime = punchTimeInUserZone.toLocalTime();
                    isEarlyOut = punchTime.isBefore(shiftEnd.toLocalTime());
                }
            }

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