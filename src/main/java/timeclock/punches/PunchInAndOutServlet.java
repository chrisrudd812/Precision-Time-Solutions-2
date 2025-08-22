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
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/PunchInAndOutServlet")
public class PunchInAndOutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PunchInAndOutServlet.class.getName());

    private static final String PACIFIC_TIME_FALLBACK_ID = "America/Los_Angeles";
    private static final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";


    private boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !"undefined".equalsIgnoreCase(s) && !"null".equalsIgnoreCase(s) && !"Unknown".equalsIgnoreCase(s);
    }

    /**
     * [NEW METHOD] Handles device validation and auto-registration if needed.
     * This must be called within an existing database transaction.
     * @return A map containing the result: success (boolean) and message (String).
     */
    private Map<String, Object> handleDeviceRegistrationAndValidation(Connection con, int tenantId, int eid, String fingerprintHash) throws SQLException {
        Map<String, Object> result = new HashMap<>();
        String logPrefix = String.format("[DeviceValidation T:%d E:%d] ", tenantId, eid);

        // 1. Check if device restrictions are even enabled for this tenant
        boolean restrictionsEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByDevice"));
        if (!restrictionsEnabled) {
            logger.info(logPrefix + "Device restriction is not enabled for this tenant. Skipping check.");
            result.put("success", true);
            result.put("message", null); // No message needed for success
            return result;
        }
        logger.info(logPrefix + "Device restriction is ENABLED. Proceeding with validation.");

        // 2. Check if this device is already registered for this employee
        String checkSql = "SELECT DeviceID, IsEnabled FROM employee_devices WHERE TenantID = ? AND EID = ? AND DeviceFingerprintHash = ?";
        try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
            psCheck.setInt(1, tenantId);
            psCheck.setInt(2, eid);
            psCheck.setString(3, fingerprintHash);
            try (ResultSet rs = psCheck.executeQuery()) {
                if (rs.next()) { // Device is already registered
                    boolean isEnabled = rs.getBoolean("IsEnabled");
                    if (isEnabled) {
                        logger.info(logPrefix + "Device is already registered and ENABLED.");
                        result.put("success", true);
                        return result;
                    } else {
                        logger.warning(logPrefix + "Punch BLOCKED. Device is registered but DISABLED.");
                        result.put("success", false);
                        result.put("message", "Punch failed: This device has been disabled for punching.");
                        return result;
                    }
                }
            }
        }

        // 3. Device is NOT registered. Check if we can add it.
        logger.info(logPrefix + "Device is not registered. Checking device limits.");
        int maxDevices = 2; // Default
        try {
            String maxDevicesStr = Configuration.getProperty(tenantId, "MaxDevicesPerUserGlobal", "2");
            maxDevices = Integer.parseInt(maxDevicesStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, logPrefix + "Could not parse MaxDevicesPerUserGlobal config.", e);
        }

        String countSql = "SELECT COUNT(DeviceID) FROM employee_devices WHERE TenantID = ? AND EID = ?";
        int currentDeviceCount = 0;
        try (PreparedStatement psCount = con.prepareStatement(countSql)) {
            psCount.setInt(1, tenantId);
            psCount.setInt(2, eid);
            try (ResultSet rsCount = psCount.executeQuery()) {
                if (rsCount.next()) {
                    currentDeviceCount = rsCount.getInt(1);
                }
            }
        }
        logger.info(logPrefix + "Employee has " + currentDeviceCount + " devices registered. The limit is " + maxDevices + ".");

        if (currentDeviceCount >= maxDevices) {
            logger.warning(logPrefix + "Punch BLOCKED. Max device limit of " + maxDevices + " has been reached.");
            result.put("success", false);
            result.put("message", "Punch failed: Your maximum device limit has been reached. Please contact a supervisor to register this new device.");
            return result;
        }

        // 4. Limit not reached. Register the new device.
        logger.info(logPrefix + "Device limit not reached. Auto-registering new device.");
        String insertSql = "INSERT INTO employee_devices (TenantID, EID, DeviceFingerprintHash, DeviceDescription, RegisteredDate, LastUsedDate, IsEnabled) VALUES (?, ?, ?, ?, ?, ?, TRUE)";
        try (PreparedStatement psInsert = con.prepareStatement(insertSql)) {
            Timestamp now = Timestamp.from(Instant.now());
            psInsert.setInt(1, tenantId);
            psInsert.setInt(2, eid);
            psInsert.setString(3, fingerprintHash);
            psInsert.setString(4, "New Device (Auto-Registered)");
            psInsert.setTimestamp(5, now);
            psInsert.setTimestamp(6, now);
            int rowsAffected = psInsert.executeUpdate();
            if (rowsAffected > 0) {
                logger.info(logPrefix + "New device successfully registered.");
                result.put("success", true);
                result.put("message", "This new device was successfully registered");
                return result;
            } else {
                throw new SQLException("Failed to insert new device record, no rows affected.");
            }
        }
    }


    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());

        HttpSession session = request.getSession(false);
        String punchAction = request.getParameter("punchAction");
        String eidStr = request.getParameter("punchEID");
        String deviceFingerprintHash = request.getParameter("deviceFingerprintHash");
        String browserTimeZoneIdStr = request.getParameter("browserTimeZoneId");

        String redirectPage = "timeclock.jsp";
        String messageForRedirect = null;
        boolean isError = false;
        boolean isSuccess = false;
        String deviceRegistrationMessagePart = null;

        Integer tenantIdInteger = null;
        Integer sessionEid = null;
        String userPermissions = null;
        String sessionUserTimeZoneId = null;

        logger.info("[PunchServlet] ----- PUNCH ATTEMPT START -----");
        logger.info(String.format("[PunchServlet] Params: Action=%s, EIDStr=%s, BrowserTZ=%s, FP=%s",
            punchAction, eidStr, browserTimeZoneIdStr,
            (isValid(deviceFingerprintHash) && deviceFingerprintHash.length() > 10 ? deviceFingerprintHash.substring(0,10)+"..." : deviceFingerprintHash)
        ));


        if (session != null) {
            tenantIdInteger = (Integer) session.getAttribute("TenantID");
            sessionEid = (Integer) session.getAttribute("EID");
            userPermissions = (String) session.getAttribute("Permissions");
            Object sessionTzObj = session.getAttribute("userTimeZoneId");
            if (sessionTzObj instanceof String && isValid((String)sessionTzObj)) {
                sessionUserTimeZoneId = (String) sessionTzObj;
            }
            logger.info("[PunchServlet] Session Data: TenantID=" + tenantIdInteger + ", SessionEID=" + sessionEid +
                        ", Perms=" + userPermissions + ", SessionUserTZ=" + sessionUserTimeZoneId);
        } else {
            messageForRedirect = "Session expired or invalid. Please log in.";
            isError = true;
        }

        if (!isError && (tenantIdInteger == null || sessionEid == null)) {
            messageForRedirect = "Session data incomplete. Please log in again.";
            isError = true;
        }

        if (isError) {
            logger.warning("[PunchServlet] Session/Initial Error: " + messageForRedirect);
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode(messageForRedirect, StandardCharsets.UTF_8.name()));
            return;
        }

        int tenantId = tenantIdInteger.intValue();
        int punchEID = -1;
        try {
            if (isValid(eidStr)) {
                punchEID = Integer.parseInt(eidStr);
                if (punchEID <= 0 && !isError) {
                    messageForRedirect = "No valid employee ID identified for punch."; isError = true;
                }
            } else if(!isError) {
                messageForRedirect = "Employee ID not provided for punch."; isError = true;
            }
        } catch (NumberFormatException e) {
            if(!isError) messageForRedirect = "Invalid Employee ID format."; isError = true;
        }

        if (!isError && !"Administrator".equalsIgnoreCase(userPermissions) && punchEID != sessionEid.intValue()) {
            messageForRedirect = "Access Denied: Cannot punch for other employees."; isError = true;
        }
        if (!isError && (!isValid(deviceFingerprintHash) || "fingerprintjs_not_loaded".equals(deviceFingerprintHash))) {
            messageForRedirect = "Device identification failed. Punch cannot be processed."; isError = true;
            logger.warning("[PunchServlet] Fingerprint issue for EID " + punchEID + ", T:" + tenantId + ". FP: '" + deviceFingerprintHash + "'");
        }

        ZoneId effectiveZoneIdForScheduleComparison = null;
        if (!isError) {
            String zoneIdToUse = null;
            try {
                if (isValid(browserTimeZoneIdStr)) {
                    zoneIdToUse = browserTimeZoneIdStr;
                } else if (isValid(sessionUserTimeZoneId)) {
                    zoneIdToUse = sessionUserTimeZoneId;
                } else {
                    zoneIdToUse = Configuration.getProperty(tenantId, "DefaultTimeZone", DEFAULT_TENANT_FALLBACK_TIMEZONE);
                }
                effectiveZoneIdForScheduleComparison = ZoneId.of(zoneIdToUse);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[PunchServlet][TZ-Logic] Invalid timezone ID encountered ('" + zoneIdToUse + "'). Defaulting to " + PACIFIC_TIME_FALLBACK_ID + ".", e);
                effectiveZoneIdForScheduleComparison = ZoneId.of(PACIFIC_TIME_FALLBACK_ID);
            }
        }
        
        if (!isError && !("IN".equals(punchAction) || "OUT".equals(punchAction))) {
             messageForRedirect = "Invalid punch action specified."; isError = true;
        }


        if (!isError) {
            logger.info("[PunchServlet] ALL PRE-CHECKS PASSED. Proceeding with DB transaction for: " + punchAction + " for EID " + punchEID);
            Connection con = null;
            try {
                con = DatabaseConnection.getConnection();
                con.setAutoCommit(false);

                // --- [NEW] DEVICE VALIDATION & REGISTRATION ---
                Map<String, Object> deviceValidationResult = handleDeviceRegistrationAndValidation(con, tenantId, punchEID, deviceFingerprintHash);
                boolean deviceCheckSuccess = (boolean) deviceValidationResult.get("success");
                if (!deviceCheckSuccess) {
                    messageForRedirect = (String) deviceValidationResult.get("message");
                    isError = true;
                } else {
                    deviceRegistrationMessagePart = (String) deviceValidationResult.get("message");
                }
                // --- END DEVICE VALIDATION ---

                if (!isError) { // Only proceed if device check passed
                    Timestamp punchTimestampUtc = Timestamp.from(Instant.now());
                    LocalDate punchDateForDb = punchTimestampUtc.toInstant().atZone(ZoneOffset.UTC).toLocalDate();

                    Time scheduledStartTime = null; Time scheduledEndTime = null; int gracePeriodMinutes = 5;
                    String empDetailsSql = "SELECT s.SHIFT_START, s.SHIFT_END FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME WHERE e.EID = ? AND e.TenantID = ?";
                    try (PreparedStatement psEmp = con.prepareStatement(empDetailsSql)) {
                        psEmp.setInt(1, punchEID); psEmp.setInt(2, tenantId);
                        try (ResultSet rsEmp = psEmp.executeQuery()) {
                            if (rsEmp.next()) {
                                scheduledStartTime = rsEmp.getTime("SHIFT_START");
                                scheduledEndTime = rsEmp.getTime("SHIFT_END");
                            }
                        }
                    }
                    try {
                        String graceStr = Configuration.getProperty(tenantId, "GracePeriod", "5");
                        if (isValid(graceStr)) gracePeriodMinutes = Integer.parseInt(graceStr);
                    } catch (Exception eCfg) { logger.log(Level.WARNING, "Could not get GracePeriod for T:" + tenantId, eCfg); }


                    if ("IN".equals(punchAction)) {
                        String statusSql = "SELECT IN_1, OUT_1 FROM PUNCHES WHERE TenantID = ? AND EID = ? AND DATE = ? ORDER BY PUNCH_ID DESC LIMIT 1";
                        Timestamp lastInUtc = null; Timestamp lastOutUtc = null;
                        try (PreparedStatement psStatus = con.prepareStatement(statusSql)) {
                            psStatus.setInt(1, tenantId); psStatus.setInt(2, punchEID);
                            psStatus.setDate(3, java.sql.Date.valueOf(punchDateForDb));
                            try (ResultSet rsStatus = psStatus.executeQuery()) { if (rsStatus.next()) { lastInUtc = rsStatus.getTimestamp("IN_1"); lastOutUtc = rsStatus.getTimestamp("OUT_1"); } }
                        }
                        if (lastInUtc != null && lastOutUtc == null) {
                            messageForRedirect = "You are already punched IN. Punch OUT first."; isError = true;
                        } else {
                            boolean isLate = false;
                            if (scheduledStartTime != null && effectiveZoneIdForScheduleComparison != null) {
                                LocalTime actualInLocal = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneIdForScheduleComparison).toLocalTime();
                                LocalTime scheduledStartLocal = scheduledStartTime.toLocalTime();
                                if (actualInLocal.isAfter(scheduledStartLocal.plusMinutes(gracePeriodMinutes))) isLate = true;
                            }
                            String insertSql = "INSERT INTO PUNCHES (TenantID, EID, DATE, IN_1, PUNCH_TYPE, LATE) VALUES (?, ?, ?, ?, 'User Initiated', ?)";
                            try (PreparedStatement psInsert = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                                psInsert.setInt(1,tenantId); psInsert.setInt(2,punchEID);
                                psInsert.setDate(3, java.sql.Date.valueOf(punchDateForDb));
                                psInsert.setTimestamp(4, punchTimestampUtc); psInsert.setBoolean(5, isLate);
                                if (psInsert.executeUpdate() > 0){
                                    messageForRedirect = "Punch IN submitted successfully."; isSuccess = true;
                                } else { messageForRedirect = "Failed to record PUNCH IN."; isError = true; }
                            }
                        }
                    } else if ("OUT".equals(punchAction)) {
                        // [FIX] Query now only looks for open punches on WORK-related punch types
                        String statusSql = "SELECT PUNCH_ID, IN_1 FROM PUNCHES WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL " +
                                           "AND PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Regular', 'Sample Data') " +
                                           "ORDER BY PUNCH_ID DESC LIMIT 1";
                        Timestamp lastInForOutUtc = null; long lastPunchIdForOut = -1;
                        try (PreparedStatement psStatus = con.prepareStatement(statusSql)) {
                            psStatus.setInt(1, tenantId); psStatus.setInt(2, punchEID);
                            try (ResultSet rsStatus = psStatus.executeQuery()) {
                                if (rsStatus.next()) {
                                    lastInForOutUtc = rsStatus.getTimestamp("IN_1");
                                    lastPunchIdForOut = rsStatus.getLong("PUNCH_ID");
                                }
                            }
                        }
                        if (lastInForOutUtc == null || lastPunchIdForOut == -1) {
                            messageForRedirect = "No open work punch found to clock out against."; isError = true;
                        } else {
                            boolean isEarlyOut = false;
                            if (scheduledEndTime != null && effectiveZoneIdForScheduleComparison != null) {
                                LocalTime actualOutLocal = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneIdForScheduleComparison).toLocalTime();
                                LocalTime scheduledEndLocal = scheduledEndTime.toLocalTime();
                                if (actualOutLocal.isBefore(scheduledEndLocal.minusMinutes(gracePeriodMinutes))) isEarlyOut = true;
                            }
                            String updateSql = "UPDATE PUNCHES SET OUT_1 = ?, EARLY_OUTS = ? WHERE PUNCH_ID = ?";
                            try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                                psUpdate.setTimestamp(1, punchTimestampUtc);
                                psUpdate.setBoolean(2, isEarlyOut);
                                psUpdate.setLong(3, lastPunchIdForOut);
                                if (psUpdate.executeUpdate() > 0){
                                    messageForRedirect = "Punch OUT submitted successfully.";
                                    ShowPunches.calculateAndUpdatePunchTotal(con, tenantId, punchEID, lastInForOutUtc, punchTimestampUtc, lastPunchIdForOut);
                                    isSuccess = true;
                                } else { messageForRedirect = "Failed to record PUNCH OUT."; isError = true; }
                            }
                        }
                    }
                } // End if(!isError) after device check

                if (isError) {
                    logger.info("[PunchServlet] Rolling back DB transaction due to error: " + messageForRedirect);
                    con.rollback();
                } else {
                    con.commit();
                    logger.info("[PunchServlet] Transaction committed successfully for EID " + punchEID);
                }
            } catch (SQLException e_punch) {
                logger.log(Level.SEVERE, "DB error during punch for EID " + punchEID + ", T:" + tenantId, e_punch);
                messageForRedirect = "Database error during punch operation. " + e_punch.getMessage(); isError = true;
                if(con!=null){try{if(!con.getAutoCommit() && !con.isClosed())con.rollback();}catch(SQLException se_rb){logger.log(Level.SEVERE, "Rollback fail", se_rb);}}
            } finally {
                if(con!=null){try{ if(!con.isClosed()) { con.setAutoCommit(true); con.close();} }catch(SQLException e_final){logger.log(Level.WARNING, "Error closing connection.", e_final);}}
            }
        }

        StringBuilder redirUrlBuilder = new StringBuilder(request.getContextPath() + "/" + redirectPage);
        
        if (isError) {
            if(messageForRedirect == null) messageForRedirect = "An unspecified error occurred.";
            redirUrlBuilder.append("?error=").append(URLEncoder.encode(messageForRedirect, StandardCharsets.UTF_8.name()));
        } else if (isSuccess) {
            if (messageForRedirect == null) messageForRedirect = "Punch processed successfully.";
            if (deviceRegistrationMessagePart != null) {
                messageForRedirect = deviceRegistrationMessagePart + ". " + messageForRedirect;
            }
            redirUrlBuilder.append("?message=").append(URLEncoder.encode(messageForRedirect, StandardCharsets.UTF_8.name()));
            redirUrlBuilder.append("&punchStatus=success");
        } else {
             if(messageForRedirect == null) messageForRedirect = "Punch status unclear. Please check your timecard.";
             redirUrlBuilder.append("?error=").append(URLEncoder.encode(messageForRedirect, StandardCharsets.UTF_8.name()));
        }

        String eidUrlParamVal = request.getParameter("eid");
        if (punchEID > 0 && "Administrator".equalsIgnoreCase(userPermissions)) {
            if ((isValid(eidUrlParamVal) && Integer.parseInt(eidUrlParamVal) == punchEID) || punchEID != sessionEid.intValue()) {
                 redirUrlBuilder.append("&eid=").append(punchEID);
            }
        } else if (punchEID > 0 && punchEID == sessionEid.intValue() && isValid(eidUrlParamVal) && Integer.parseInt(eidUrlParamVal) == punchEID) {
             redirUrlBuilder.append("&eid=").append(punchEID);
        }

        logger.info("[PunchServlet] Final Redirect URL: " + redirUrlBuilder.toString());
        if (!response.isCommitted()) {
            response.sendRedirect(redirUrlBuilder.toString());
        } else {
            logger.warning("[PunchServlet] Response already committed. Final Message: " + messageForRedirect + " (isError: " + isError + ")");
        }
    }
}