package timeclock.punches;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;
// import timeclock.util.IPAddressUtil; // Not used in the provided snippet

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
// import java.sql.Date; // No longer needed for java.sql.Date if using LocalDate
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time; // Needed for java.sql.Time
import java.sql.Timestamp;
// import java.sql.Types; // Not directly used
import java.time.Instant;
import java.time.LocalDate; // For punch date
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
// import java.time.format.DateTimeFormatter; // Not directly used
// import java.time.format.TextStyle; // Not directly used
// import java.util.ArrayList; // Not directly used
// import java.util.HashMap; // Not directly used
// import java.util.List; // Not directly used
// import java.util.Locale; // Not directly used
// import java.util.Map; // Not directly used
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/PunchInAndOutServlet")
public class PunchInAndOutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PunchInAndOutServlet.class.getName());

    // private static final String GLOBAL_MAX_DEVICES_KEY = "MaxDevicesPerUserGlobal"; // Not used in current logic
    // private static final String DEFAULT_SYSTEM_MAX_DEVICES = "2"; // Not used in current logic
    private static final String PACIFIC_TIME_FALLBACK_ID = "America/Los_Angeles";
    private static final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";


    private boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !"undefined".equalsIgnoreCase(s) && !"null".equalsIgnoreCase(s) && !"Unknown".equalsIgnoreCase(s);
    }

    // calculateDistance not used in this specific logic, can be kept if used elsewhere or removed.

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());

        HttpSession session = request.getSession(false);
        String punchAction = request.getParameter("punchAction");
        String eidStr = request.getParameter("punchEID");
        String deviceFingerprintHash = request.getParameter("deviceFingerprintHash");
        // Location params not directly used in late/early logic but kept for context
        // String clientLatitudeStr = request.getParameter("clientLatitude");
        // String clientLongitudeStr = request.getParameter("clientLongitude");
        // String clientAccuracyStr = request.getParameter("clientLocationAccuracy");
        String browserTimeZoneIdStr = request.getParameter("browserTimeZoneId");

        String redirectPage = "timeclock.jsp";
        String messageForRedirect = null;
        boolean isError = false;
        boolean isSuccess = false;
        String deviceRegistrationMessagePart = null; // Assuming this is handled by restriction checks
        // boolean accuracyOverrideOccurred_flag = false; // Assuming this is handled by restriction checks

        Integer tenantIdInteger = null;
        Integer sessionEid = null;
        String userPermissions = null;
        String sessionUserTimeZoneId = null;

        logger.info("[PunchServlet] ----- PUNCH ATTEMPT START -----");
        logger.info(String.format("[PunchServlet] Params: Action=%s, EIDStr=%s, BrowserTZ=%s, FP=%s",
            punchAction, eidStr, browserTimeZoneIdStr,
            (isValid(deviceFingerprintHash) && deviceFingerprintHash.length() > 10 ? deviceFingerprintHash.substring(0,10)+"..." : deviceFingerprintHash)
            // Removed Lat, Lon, Acc from this specific log line for brevity, they are still available if needed
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

        ZoneId effectiveZoneIdForScheduleComparison = null; // This will be used for late/early checks
        if (!isError) {
            String zoneIdToUse = null;
            try {
                if (isValid(browserTimeZoneIdStr)) {
                    zoneIdToUse = browserTimeZoneIdStr;
                    logger.info("[PunchServlet][TZ-Logic] Using Browser Timezone for schedule comparison checks: " + zoneIdToUse);
                } else if (isValid(sessionUserTimeZoneId)) {
                    zoneIdToUse = sessionUserTimeZoneId;
                    logger.info("[PunchServlet][TZ-Logic] Using Session/Profile Timezone for schedule comparison checks: " + zoneIdToUse);
                } else {
                    String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone", DEFAULT_TENANT_FALLBACK_TIMEZONE);
                    zoneIdToUse = tenantDefaultTz;
                    logger.info("[PunchServlet][TZ-Logic] Using Tenant DefaultTimeZone (or its fallback '" + DEFAULT_TENANT_FALLBACK_TIMEZONE +"') for schedule comparison checks: " + zoneIdToUse);
                }
                effectiveZoneIdForScheduleComparison = ZoneId.of(zoneIdToUse);
            } catch (Exception e) {
                logger.log(Level.WARNING, "[PunchServlet][TZ-Logic] Invalid timezone ID encountered ('" + zoneIdToUse + "'). Defaulting to " + PACIFIC_TIME_FALLBACK_ID + ".", e);
                effectiveZoneIdForScheduleComparison = ZoneId.of(PACIFIC_TIME_FALLBACK_ID);
            }
            logger.info("[PunchServlet][TZ-Logic] Effective ZoneId for schedule comparisons: " + effectiveZoneIdForScheduleComparison.getId());
        }

        // --- PUNCH RESTRICTION CHECKS ---
        if (!isError && ("IN".equals(punchAction) || "OUT".equals(punchAction))) {
            // Placeholder for your detailed restriction checks.
            // If any restriction check fails:
            // messageForRedirect = "Punch restricted: <reason>"; isError = true;
            logger.info("[PunchServlet] Placeholder: Detailed Punch Restriction Checks would be performed here. They need to be re-integrated.");
        } else if (!isError) {
            messageForRedirect = "Invalid punch action specified."; isError = true;
        }
        // --- END PUNCH RESTRICTION CHECKS ---

        if (!isError) {
            logger.info("[PunchServlet] ALL PRE-CHECKS PASSED. Proceeding with DB punch: " + punchAction + " for EID " + punchEID);
            Connection con = null;
            try {
                con = DatabaseConnection.getConnection();
                con.setAutoCommit(false);
                Timestamp punchTimestampUtc = Timestamp.from(Instant.now());
                LocalDate punchDateForDb = punchTimestampUtc.toInstant().atZone(ZoneOffset.UTC).toLocalDate();

                // --- Get Schedule and Grace Period ---
                Time scheduledStartTime = null;
                Time scheduledEndTime = null;
                int gracePeriodMinutes = 5; // Default

                String empDetailsSql = "SELECT s.SHIFT_START, s.SHIFT_END " +
                                       "FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME " +
                                       "WHERE e.EID = ? AND e.TenantID = ?";
                try (PreparedStatement psEmp = con.prepareStatement(empDetailsSql)) {
                    psEmp.setInt(1, punchEID);
                    psEmp.setInt(2, tenantId);
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
                } catch (Exception eCfg) {
                    logger.log(Level.WARNING, "Could not get GracePeriod for T:" + tenantId, eCfg);
                }
                logger.info("[PunchServlet] For EID " + punchEID + ": SchedStart=" + scheduledStartTime + ", SchedEnd=" + scheduledEndTime + ", Grace=" + gracePeriodMinutes);
                // --- End Get Schedule and Grace Period ---


                if ("IN".equals(punchAction)) {
                    // ... (existing check for already punched IN) ...
                    String statusSql = "SELECT IN_1, OUT_1 FROM PUNCHES WHERE TenantID = ? AND EID = ? AND DATE = ? ORDER BY PUNCH_ID DESC LIMIT 1";
                    Timestamp lastInUtc = null; Timestamp lastOutUtc = null;
                    try (PreparedStatement psStatus = con.prepareStatement(statusSql)) {
                        psStatus.setInt(1, tenantId); psStatus.setInt(2, punchEID);
                        psStatus.setDate(3, java.sql.Date.valueOf(punchDateForDb));
                        try (ResultSet rsStatus = psStatus.executeQuery()) {
                            if (rsStatus.next()) { lastInUtc = rsStatus.getTimestamp("IN_1"); lastOutUtc = rsStatus.getTimestamp("OUT_1"); }
                        }
                    }
                    if (lastInUtc != null && lastOutUtc == null) {
                        messageForRedirect = "You are already punched IN. Punch OUT first."; isError = true;
                    } else {
                        boolean isLate = false;
                        if (scheduledStartTime != null && effectiveZoneIdForScheduleComparison != null) {
                            LocalTime actualInLocal = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneIdForScheduleComparison).toLocalTime();
                            LocalTime scheduledStartLocal = scheduledStartTime.toLocalTime();
                            if (actualInLocal.isAfter(scheduledStartLocal.plusMinutes(gracePeriodMinutes))) {
                                isLate = true;
                            }
                            logger.info("[PunchServlet] LATE CHECK (IN): EID=" + punchEID + ", ActualInLocal=" + actualInLocal + ", SchedStartLocal=" + scheduledStartLocal + ", Grace=" + gracePeriodMinutes + ", IsLate=" + isLate);
                        }

                        String insertSql = "INSERT INTO PUNCHES (TenantID, EID, DATE, IN_1, PUNCH_TYPE, LATE) VALUES (?, ?, ?, ?, 'User Initiated', ?)";
                        try (PreparedStatement psInsert = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                            psInsert.setInt(1,tenantId);
                            psInsert.setInt(2,punchEID);
                            psInsert.setDate(3, java.sql.Date.valueOf(punchDateForDb));
                            psInsert.setTimestamp(4, punchTimestampUtc);
                            psInsert.setBoolean(5, isLate); // Set LATE flag

                            int rowsAffected = psInsert.executeUpdate();
                            if (rowsAffected > 0){
                                // Optionally get generated PUNCH_ID if needed immediately
                                messageForRedirect = "Punch IN submitted successfully.";
                                // if (deviceRegistrationMessagePart != null) messageForRedirect = deviceRegistrationMessagePart + ". " + messageForRedirect; // If device reg logic is added
                                con.commit();
                                isSuccess = true;
                                logger.info("[PunchServlet] Punch IN recorded (Late=" + isLate + ") and committed for EID: " + punchEID);
                            } else {
                                messageForRedirect = "Failed to record PUNCH IN."; isError = true; if(con!=null) con.rollback();
                            }
                        }
                    }
                } else if ("OUT".equals(punchAction)) {
                    String statusSql = "SELECT PUNCH_ID, IN_1 FROM PUNCHES WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL ORDER BY PUNCH_ID DESC LIMIT 1";
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
                        messageForRedirect = "No open PUNCH IN record found to PUNCH OUT against."; isError = true;
                    } else {
                        boolean isEarlyOut = false;
                        if (scheduledEndTime != null && effectiveZoneIdForScheduleComparison != null) {
                            LocalTime actualOutLocal = ZonedDateTime.ofInstant(punchTimestampUtc.toInstant(), effectiveZoneIdForScheduleComparison).toLocalTime();
                            LocalTime scheduledEndLocal = scheduledEndTime.toLocalTime();
                            if (actualOutLocal.isBefore(scheduledEndLocal.minusMinutes(gracePeriodMinutes))) {
                                isEarlyOut = true;
                            }
                             logger.info("[PunchServlet] EARLY OUT CHECK: EID=" + punchEID + ", ActualOutLocal=" + actualOutLocal + ", SchedEndLocal=" + scheduledEndLocal + ", Grace=" + gracePeriodMinutes + ", IsEarlyOut=" + isEarlyOut);
                        }

                        String updateSql = "UPDATE PUNCHES SET OUT_1 = ?, EARLY_OUTS = ? WHERE PUNCH_ID = ?";
                        try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                            psUpdate.setTimestamp(1, punchTimestampUtc);
                            psUpdate.setBoolean(2, isEarlyOut); // Set EARLY_OUTS flag
                            psUpdate.setLong(3, lastPunchIdForOut);
                            if (psUpdate.executeUpdate() > 0){
                                messageForRedirect = "Punch OUT submitted successfully.";
                                ShowPunches.calculateAndUpdatePunchTotal(con, tenantId, punchEID, lastInForOutUtc, punchTimestampUtc, lastPunchIdForOut);
                                con.commit();
                                isSuccess = true;
                                logger.info("[PunchServlet] Punch OUT recorded (EarlyOut=" + isEarlyOut + ") and committed for PUNCH_ID: " + lastPunchIdForOut);
                            } else {
                                messageForRedirect = "Failed to record PUNCH OUT."; isError = true; if(con!=null) con.rollback();
                            }
                        }
                    }
                }
                if (isError && con != null && !con.isClosed() && !con.getAutoCommit()) {
                     logger.info("[PunchServlet] Rolling back DB transaction due to error during punch operation: " + messageForRedirect);
                     con.rollback();
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
        boolean firstQueryParamAddedToUrl = false;

        if (isError) {
            if(messageForRedirect == null) messageForRedirect = "An unspecified error occurred during punch processing.";
            redirUrlBuilder.append("?error=").append(URLEncoder.encode(messageForRedirect, StandardCharsets.UTF_8.name()));
            firstQueryParamAddedToUrl = true;
        } else if (isSuccess) {
            if (messageForRedirect == null) messageForRedirect = "Punch processed successfully.";
            // if (deviceRegistrationMessagePart != null) { // Assuming handled by restriction check logic
            //     messageForRedirect = deviceRegistrationMessagePart + ". " + messageForRedirect;
            // }
            redirUrlBuilder.append("?message=").append(URLEncoder.encode(messageForRedirect, StandardCharsets.UTF_8.name()));
            firstQueryParamAddedToUrl = true;
            redirUrlBuilder.append("&punchStatus=success");
        } else {
             if(messageForRedirect == null) messageForRedirect = "Punch status unclear. Please check your timecard.";
             redirUrlBuilder.append(firstQueryParamAddedToUrl ? "&" : "?").append("error=").append(URLEncoder.encode(messageForRedirect, StandardCharsets.UTF_8.name()));
             firstQueryParamAddedToUrl = true;
        }

        // if (accuracyOverrideOccurred_flag) { // Assuming handled by restriction check logic
        //     redirUrlBuilder.append(firstQueryParamAddedToUrl?"&":"?").append("accuracyOverride=true");
        //     firstQueryParamAddedToUrl = true;
        // }

        String eidUrlParamVal = request.getParameter("eid");
        if (punchEID > 0 && "Administrator".equalsIgnoreCase(userPermissions)) {
            // Keep existing EID param if admin was viewing another employee's card and punched for them (if allowed)
            // Or if admin punched for self but was viewing own card via EID param
            if ((isValid(eidUrlParamVal) && Integer.parseInt(eidUrlParamVal) == punchEID) || punchEID != sessionEid.intValue()) {
                 redirUrlBuilder.append(firstQueryParamAddedToUrl ? "&" : "?").append("eid=").append(punchEID);
            }
        } else if (punchEID > 0 && punchEID == sessionEid.intValue() && isValid(eidUrlParamVal) && Integer.parseInt(eidUrlParamVal) == punchEID) {
            // User punched for self, and was viewing own card via eid param, keep it.
             redirUrlBuilder.append(firstQueryParamAddedToUrl ? "&" : "?").append("eid=").append(punchEID);
        }

        logger.info("[PunchServlet] Final Redirect URL: " + redirUrlBuilder.toString());
        if (!response.isCommitted()) {
            response.sendRedirect(redirUrlBuilder.toString());
        } else {
            logger.warning("[PunchServlet] Response already committed. Final Intended Message: " + messageForRedirect + " (isError: " + isError + ", isSuccess: " + isSuccess + ")");
        }
    }
}