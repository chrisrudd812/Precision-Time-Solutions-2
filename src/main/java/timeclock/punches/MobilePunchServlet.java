package timeclock.punches;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;
import timeclock.util.Helpers;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/mobile/punch")
public class MobilePunchServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(MobilePunchServlet.class.getName());
    private static final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> responseMap = new HashMap<>();

        try {
            String tenantIdStr = request.getParameter("tenantId");
            String eidStr = request.getParameter("eid");
            
            if (tenantIdStr == null || eidStr == null) {
                responseMap.put("success", false);
                responseMap.put("message", "Missing parameters");
                sendJsonResponse(response, responseMap);
                return;
            }

            int tenantId = Integer.parseInt(tenantIdStr);
            int eid = Integer.parseInt(eidStr);

            // Get employee info using ShowPunches method
            Map<String, Object> employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId, eid);
            if (employeeInfo == null) {
                responseMap.put("success", false);
                responseMap.put("message", "Employee not found");
                sendJsonResponse(response, responseMap);
                return;
            }

            // Build employee data for mobile response
            Map<String, Object> employee = new HashMap<>();
            String[] nameParts = ((String) employeeInfo.get("employeeName")).split(" ", 2);
            employee.put("firstName", nameParts.length > 0 ? nameParts[0] : "");
            employee.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
            employee.put("department", employeeInfo.get("department"));
            employee.put("supervisor", employeeInfo.get("supervisor"));
            employee.put("wageType", employeeInfo.get("wageType"));
            employee.put("schedule", employeeInfo.get("scheduleName"));
            
            // Show shift times as schedule hours
            java.sql.Time shiftStart = (java.sql.Time) employeeInfo.get("shiftStart");
            java.sql.Time shiftEnd = (java.sql.Time) employeeInfo.get("shiftEnd");
            if (shiftStart != null && shiftEnd != null) {
                employee.put("scheduleHours", shiftStart.toString() + " - " + shiftEnd.toString());
            } else {
                employee.put("scheduleHours", "8.0");
            }
            
            // Format auto lunch properly
            Boolean autoLunch = (Boolean) employeeInfo.get("autoLunch");
            Object hoursReq = employeeInfo.get("hoursRequired");
            Object lunchLength = employeeInfo.get("lunchLength");
            
            if (Boolean.TRUE.equals(autoLunch) && hoursReq != null && lunchLength != null) {
                employee.put("autoLunch", "On");
                employee.put("lunchThreshold", hoursReq);
                employee.put("lunchLength", lunchLength);
            } else {
                employee.put("autoLunch", "Off");
            }
            
            employee.put("vacationHours", employeeInfo.get("vacationHours"));
            employee.put("sickHours", employeeInfo.get("sickHours"));
            employee.put("personalHours", employeeInfo.get("personalHours"));

            // Get current pay period from Configuration
            Map<String, LocalDate> payPeriodInfo = ShowPunches.getCurrentPayPeriodInfo(tenantId);
            LocalDate payPeriodStart, payPeriodEnd;
            
            if (payPeriodInfo != null) {
                payPeriodStart = payPeriodInfo.get("startDate");
                payPeriodEnd = payPeriodInfo.get("endDate");
            } else {
                // Fallback to current week if no pay period configured
                LocalDate today = LocalDate.now();
                payPeriodStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
                payPeriodEnd = payPeriodStart.plusDays(6);
            }

            // Get timezone for mobile display
            String timeZone = Configuration.getProperty(tenantId, "DefaultTimeZone", "America/Denver");
            
            // Get punch data using ShowPunches (same as web app)
            Map<String, Object> punchData = ShowPunches.getTimecardPunchData(
                tenantId, eid, payPeriodStart, payPeriodEnd, employeeInfo, timeZone
            );

            // Transform web format to mobile format
            @SuppressWarnings("unchecked")
            List<Map<String, String>> webPunches = (List<Map<String, String>>) punchData.get("punches");
            List<Map<String, Object>> mobilePunches = new ArrayList<>();
            
            if (webPunches != null) {
                for (Map<String, String> punch : webPunches) {
                    Map<String, Object> mobilePunch = new HashMap<>();
                    mobilePunch.put("id", punch.get("punchId"));
                    // Fix date format for mobile (MM/dd instead of full date)
                    String punchDate = punch.get("punchDate");
                    if (punchDate != null && punchDate.length() > 5) {
                        try {
                            java.time.LocalDate date = java.time.LocalDate.parse(punchDate);
                            mobilePunch.put("date", date.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd")));
                        } catch (Exception e) {
                            mobilePunch.put("date", punchDate);
                        }
                    } else {
                        mobilePunch.put("date", punchDate);
                    }
                    mobilePunch.put("dow", punch.get("dayOfWeek"));
                    // Format times without seconds (h:mm a)
                    String timeIn = punch.get("timeIn");
                    String timeOut = punch.get("timeOut");
                    mobilePunch.put("in", formatTimeWithoutSeconds(timeIn));
                    mobilePunch.put("out", formatTimeWithoutSeconds(timeOut));
                    mobilePunch.put("hours", punch.get("totalHours"));
                    mobilePunch.put("type", punch.get("punchType"));
                    mobilePunch.put("isLate", !punch.get("inTimeCssClass").isEmpty());
                    mobilePunch.put("isEarlyOut", !punch.get("outTimeCssClass").isEmpty());
                    mobilePunches.add(mobilePunch);
                }
            }

            responseMap.put("success", true);
            responseMap.put("employee", employee);
            // Add row banding for mobile app
            String currentDate = null;
            String currentBand = "bandA";
            for (Map<String, Object> punch : mobilePunches) {
                String punchDate = (String) punch.get("date");
                if (!punchDate.equals(currentDate)) {
                    currentDate = punchDate;
                    currentBand = "bandA".equals(currentBand) ? "bandB" : "bandA";
                }
                punch.put("band", currentBand);
            }
            
            responseMap.put("punches", mobilePunches);
            responseMap.put("payPeriodStart", payPeriodStart.toString());
            responseMap.put("payPeriodEnd", payPeriodEnd.toString());
            responseMap.put("totalRegularHours", punchData.get("totalRegularHours"));
            responseMap.put("totalOvertimeHours", punchData.get("totalOvertimeHours"));
            responseMap.put("totalDoubleTimeHours", punchData.get("totalDoubleTimeHours"));
            
            double regular = (Double) punchData.get("totalRegularHours");
            double overtime = (Double) punchData.get("totalOvertimeHours");
            double doubletime = (Double) punchData.get("totalDoubleTimeHours");
            responseMap.put("periodTotal", regular + overtime + doubletime);

        } catch (Exception e) {
            logger.severe("Mobile timecard error: " + e.getMessage());
            e.printStackTrace();
            responseMap.put("success", false);
            responseMap.put("message", e.getMessage());
        }

        sendJsonResponse(response, responseMap);
    }

    private String formatTimeWithoutSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return "";
        try {
            // Parse time like "07:15:00 AM" and format to "7:15 AM"
            java.time.LocalTime time = java.time.LocalTime.parse(timeStr, 
                java.time.format.DateTimeFormatter.ofPattern("hh:mm:ss a"));
            return time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
        } catch (Exception e) {
            // If parsing fails, try to remove seconds manually
            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                if (parts.length >= 2) {
                    String ampm = timeStr.contains("AM") ? " AM" : timeStr.contains("PM") ? " PM" : "";
                    return parts[0] +":" + parts[1] + ampm;
                }
            }
            return timeStr;
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            JsonObject jsonRequest = gson.fromJson(request.getReader(), JsonObject.class);
            
            // Extract parameters from JSON
            String punchAction = jsonRequest.get("punchAction").getAsString();
            int tenantId = jsonRequest.get("tenantId").getAsInt();
            int eid = jsonRequest.get("eid").getAsInt();
            String browserTimeZoneIdStr = jsonRequest.has("timeZoneId") ? jsonRequest.get("timeZoneId").getAsString() : null;
            String latitudeStr = jsonRequest.has("latitude") ? jsonRequest.get("latitude").getAsString() : null;
            String longitudeStr = jsonRequest.has("longitude") ? jsonRequest.get("longitude").getAsString() : null;
            String deviceFingerprintHash = jsonRequest.has("deviceFingerprint") ? jsonRequest.get("deviceFingerprint").getAsString() : null;

            Map<String, Object> result = processPunch(tenantId, eid, punchAction, browserTimeZoneIdStr, 
                                                    latitudeStr, longitudeStr, deviceFingerprintHash);
            sendJsonResponse(response, result);
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing mobile punch", e);
            sendErrorResponse(response, "An unexpected server error occurred.", 500);
        }
    }

    private Map<String, Object> processPunch(int tenantId, int eid, String punchAction, 
                                           String browserTimeZoneIdStr, String latitudeStr, 
                                           String longitudeStr, String deviceFingerprintHash) {
        
        Map<String, Object> result = new HashMap<>();
        Connection con = null;
        
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);

            // Step 1: Validate punch action
            if ("IN".equals(punchAction)) {
                if (hasOpenPunch(con, tenantId, eid)) {
                    result.put("success", false);
                    result.put("message", "You are already clocked in. Please clock out before clocking in again.");
                    return result;
                }
            } else if ("OUT".equals(punchAction)) {
                if (!hasOpenPunch(con, tenantId, eid)) {
                    result.put("success", false);
                    result.put("message", "No open work punch found to clock out against. You are already clocked out.");
                    return result;
                }
            } else {
                result.put("success", false);
                result.put("message", "Invalid punch action specified.");
                return result;
            }

            // Get current time
            Timestamp punchTimestampUtc = Timestamp.from(Instant.now());
            ZoneId effectiveZoneId;
            try {
                String zoneIdToUse = Helpers.isStringValid(browserTimeZoneIdStr) ? 
                    browserTimeZoneIdStr : Configuration.getProperty(tenantId, "DefaultTimeZone", DEFAULT_TENANT_FALLBACK_TIMEZONE);
                effectiveZoneId = ZoneId.of(zoneIdToUse);
            } catch (Exception e) {
                effectiveZoneId = ZoneId.of(DEFAULT_TENANT_FALLBACK_TIMEZONE);
            }
            ZonedDateTime punchTimeInUserZone = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneId);

            // Step 2: Restriction checks
            checkTimeDayRestriction(con, tenantId, punchTimeInUserZone);
            checkDeviceRestriction(con, tenantId, eid, deviceFingerprintHash);
            
            double userLat = Helpers.isStringValid(latitudeStr) ? Double.parseDouble(latitudeStr) : Double.NaN;
            double userLon = Helpers.isStringValid(longitudeStr) ? Double.parseDouble(longitudeStr) : Double.NaN;
            checkLocationRestriction(con, tenantId, userLat, userLon);

            // Step 3: Process the punch
            LocalDate punchDateForDb = punchTimeInUserZone.toLocalDate();
            
            if ("IN".equals(punchAction)) {
                String insertSql = "INSERT INTO punches (TenantID, EID, DATE, IN_1, PUNCH_TYPE, LATE) VALUES (?, ?, ?, ?, 'User Initiated', ?)";
                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, tenantId);
                    ps.setInt(2, eid);
                    ps.setDate(3, java.sql.Date.valueOf(punchDateForDb));
                    ps.setTimestamp(4, punchTimestampUtc);
                    ps.setBoolean(5, false); // isLate - simplified for mobile
                    
                    if (ps.executeUpdate() > 0) {
                        result.put("success", true);
                        result.put("message", "Punch IN successful.");
                        result.put("punchTime", formatPunchTime(punchTimeInUserZone));
                    } else {
                        throw new SQLException("Failed to record punch.");
                    }
                }
            } else { // "OUT" action
                String sql = "SELECT PUNCH_ID, IN_1 FROM punches WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL ORDER BY PUNCH_ID DESC LIMIT 1";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setInt(1, tenantId);
                    ps.setInt(2, eid);
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        long punchId = rs.getLong("PUNCH_ID");
                        Timestamp inTime = rs.getTimestamp("IN_1");
                        
                        String updateSql = "UPDATE punches SET OUT_1 = ?, EARLY_OUTS = ? WHERE PUNCH_ID = ?";
                        try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                            psUpdate.setTimestamp(1, punchTimestampUtc);
                            psUpdate.setBoolean(2, false); // isEarlyOut - simplified for mobile
                            psUpdate.setLong(3, punchId);
                            
                            if (psUpdate.executeUpdate() > 0) {
                                result.put("success", true);
                                result.put("message", "Punch OUT successful.");
                                result.put("punchTime", formatPunchTime(punchTimeInUserZone));
                                ShowPunches.calculateAndUpdatePunchTotal(con, tenantId, eid, inTime, punchTimestampUtc, punchId);
                            } else {
                                throw new SQLException("Failed to update punch record.");
                            }
                        }
                    }
                }
            }
            
            con.commit();
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            logger.log(Level.WARNING, "Punch failed for EID " + eid + ": " + e.getMessage(), e);
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { 
                    logger.log(Level.WARNING, "Rollback failed", ex); 
                }
            }
        } finally {
            if (con != null) {
                try { con.close(); } catch (SQLException ex) { 
                    logger.log(Level.WARNING, "Connection close failed", ex); 
                }
            }
        }
        
        return result;
    }

    private String formatPunchTime(ZonedDateTime punchTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("h:mm a");
        return punchTime.format(formatter);
    }

    // Copy restriction check methods from PunchInAndOutServlet
    private boolean hasOpenPunch(Connection con, int tenantId, int eid) throws SQLException {
        String sql = "SELECT IN_1 FROM punches WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL AND PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Regular') ORDER BY IN_1 DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setInt(2, eid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp lastInTime = rs.getTimestamp("IN_1");
                    if (lastInTime != null) {
                        long hoursSinceLastPunch = (System.currentTimeMillis() - lastInTime.getTime()) / (1000 * 60 * 60);
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
                            throw new Exception("Punch is outside the allowed time window for " + dayOfWeek + " (" + 
                                startTime.toLocalTime().format(tf) + " - " + endTime.toLocalTime().format(tf) + ").");
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
                psInsert.setString(4, "Mobile Device (Auto-Registered)");
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
            throw new Exception("Location access is required but was not provided. Please tap the location icon in your browser's address bar and select 'Allow', then try punching again. If the issue persists, ensure location services are enabled on your device.");
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
            double distance = calculateHaversineDistance(userLat, userLon, 
                (double) location.get("lat"), (double) location.get("lon"));
            if (distance <= (int) location.get("radius")) {
                isWithinAnyFence = true;
                break;
            }
        }
        
        if (!isWithinAnyFence) {
            throw new Exception("You are not within an authorized punch location. Please move closer to an approved area and try again.");
        }
    }

    private void sendJsonResponse(HttpServletResponse response, Map<String, Object> data) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.write(gson.toJson(data));
            out.flush();
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        sendJsonResponse(response, Map.of("success", false, "message", message));
    }
}