package timeclock.punches; // Ensure this matches your project structure

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.Configuration; // Needed for GracePeriod
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches; // Needed for applyAutoLunch, setOptionalDouble

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(description = "Handles In and Out Punching, Redirects with EID", urlPatterns = { "/PunchInAndOutServlet" })
public class PunchInAndOutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PunchInAndOutServlet.class.getName());
    private static final double ROUNDING_FACTOR = 10000.0; // For rounding raw hours
    // Zone used for comparing punch time against schedule for late/early flags
    private static final String SCHEDULE_DEFAULT_ZONE_ID_STR = "America/Denver";
    private static final ZoneId SCHEDULE_DEFAULT_ZONE = ZoneId.of(SCHEDULE_DEFAULT_ZONE_ID_STR);

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String punchAction = request.getParameter("punchAction");
        String punchEidStr = request.getParameter("punchEID"); // Parameter from timeclock.jsp form
        logger.info("Received punch request: action=" + punchAction + ", eid=" + punchEidStr);

        int eid = -1;
        try {
            if (punchEidStr != null && !punchEidStr.trim().isEmpty()) {
                eid = Integer.parseInt(punchEidStr.trim());
                 if (eid <= 0) throw new NumberFormatException("EID must be positive");
            } else {
                throw new NumberFormatException("Missing EID");
            }
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "Invalid or missing EID for punch request: " + punchEidStr, e);
            // Redirect without EID if invalid or missing
            response.sendRedirect(buildRedirectUrl(-1, null, "Invalid or missing employee ID provided."));
            return;
        }

        if ("IN".equals(punchAction)) {
            punchIn(request, response, eid);
        } else if ("OUT".equals(punchAction)) {
            punchOut(request, response, eid);
        } else {
            logger.warning("Punch request received with invalid or missing action parameter: " + punchAction);
            // Redirect with EID since it was validated
             response.sendRedirect(buildRedirectUrl(eid, null, "Invalid request action."));
        }
    }

    // --- Punch IN Logic ---
    protected void punchIn(HttpServletRequest request, HttpServletResponse response, int eid) throws IOException {
        logger.info("--- Processing Punch IN for EID: " + eid + " ---");
        String insertSql = "INSERT INTO PUNCHES (EID, DATE, IN_1, LATE, PUNCH_TYPE) VALUES (?, ?, ?, ?, 'User Initiated')";
        String checkSql = "SELECT punch_id FROM PUNCHES WHERE EID = ? AND DATE = ? AND OUT_1 IS NULL LIMIT 1";
        String scheduleSql = "SELECT s.SHIFT_START FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.SCHEDULE = s.NAME WHERE e.EID = ?";
        boolean alreadyPunchedIn = false;
        boolean isLate = false;
        Time shiftStart = null;
        Instant nowInstant = Instant.now();
        Timestamp utcInTimestamp = Timestamp.from(nowInstant);
        LocalDate todayUtc = nowInstant.atZone(ZoneOffset.UTC).toLocalDate(); // Use UTC date for DB record

        try (Connection con = DatabaseConnection.getConnection()) {
            // 1. Check if already punched in today (UTC date)
            try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                psCheck.setInt(1, eid);
                psCheck.setDate(2, Date.valueOf(todayUtc));
                try (ResultSet rsCheck = psCheck.executeQuery()) {
                    if (rsCheck.next()) {
                        alreadyPunchedIn = true;
                    }
                }
            }
            if (alreadyPunchedIn) {
                logger.warning("EID " + eid + " already punched in today (UTC Date: " + todayUtc + ").");
                response.sendRedirect(buildRedirectUrl(eid, null, "Already punched in today.")); // Use helper
                return;
            }

            // 2. Check Schedule for Lateness
            try (PreparedStatement psSchedule = con.prepareStatement(scheduleSql)) {
                psSchedule.setInt(1, eid);
                try (ResultSet rsSched = psSchedule.executeQuery()) {
                    if (rsSched.next()) {
                        shiftStart = rsSched.getTime("SHIFT_START");
                        if (shiftStart != null) {
                            try {
                                String gracePeriodStr = Configuration.getProperty("GracePeriod", "0");
                                int gracePeriod = Integer.parseInt(gracePeriodStr.trim());
                                // Compare current time in SCHEDULE_ZONE against shift start + grace
                                LocalTime nowInScheduleZone = LocalTime.now(SCHEDULE_DEFAULT_ZONE);
                                LocalTime shiftStartTimeLocal = shiftStart.toLocalTime();
                                LocalTime gracePeriodStartLocal = shiftStartTimeLocal.plusMinutes(gracePeriod);
                                isLate = nowInScheduleZone.isAfter(gracePeriodStartLocal);
                                logger.fine("Lateness Check: Now=" + nowInScheduleZone + ", GraceStart=" + gracePeriodStartLocal + " -> isLate=" + isLate);
                            } catch (Exception e) { logger.log(Level.WARNING, "Error checking lateness logic for EID "+eid, e); isLate = false;} // Default to not late on error
                        } else { logger.fine("No shift start time found, cannot check lateness."); }
                    } else { logger.warning("No employee/schedule found for EID " + eid + " for lateness check."); }
                }
            }

            // 3. Execute punch IN insert
            logger.fine("Executing punch IN insert with UTC Timestamp: " + utcInTimestamp);
            try (PreparedStatement psPunchIn = con.prepareStatement(insertSql)) {
                psPunchIn.setInt(1, eid);
                psPunchIn.setDate(2, Date.valueOf(todayUtc)); // Store UTC date
                psPunchIn.setTimestamp(3, utcInTimestamp);    // Store UTC timestamp
                psPunchIn.setBoolean(4, isLate);              // Store calculated lateness flag
                int rowsAffected = psPunchIn.executeUpdate();
                if (rowsAffected > 0) {
                    logger.info("EID " + eid + " punched IN successfully." + (isLate ? " (Late)" : ""));
                    response.sendRedirect(buildRedirectUrl(eid, "Punch IN recorded.", null)); // Use helper WITH EID
                } else {
                    logger.warning("Punch IN failed for EID " + eid + " - no rows inserted.");
                    response.sendRedirect(buildRedirectUrl(eid, null, "Punch IN failed.")); // Use helper WITH EID
                }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "DB error Punch IN EID " + eid, e); response.sendRedirect(buildRedirectUrl(eid, null, "Database error during punch.")); }
        catch (Exception e) { logger.log(Level.SEVERE, "Unexpected error Punch IN EID " + eid, e); response.sendRedirect(buildRedirectUrl(eid, null, "Server error during punch.")); }
        logger.info("--- Punch IN Processing Complete for EID: " + eid + " ---");
    }

    // --- Punch OUT Logic ---
    protected void punchOut(HttpServletRequest request, HttpServletResponse response, int eid) throws IOException {
        logger.info("--- Processing Punch OUT for EID: " + eid + " ---");
        LocalDate todayUtc = Instant.now().atZone(ZoneOffset.UTC).toLocalDate(); // Use UTC Date to find open punch
        String findOpenPunchSql = "SELECT punch_id, IN_1 FROM PUNCHES WHERE EID = ? AND DATE = ? AND OUT_1 IS NULL ORDER BY IN_1 DESC LIMIT 1";
        String updatePunchSql = "UPDATE PUNCHES SET OUT_1 = ?, TOTAL = ?, OT = ?, EARLY_OUTS = ? WHERE punch_id = ?";
        String scheduleSql = "SELECT s.SHIFT_END FROM EMPLOYEE_DATA e LEFT JOIN SCHEDULES s ON e.SCHEDULE = s.NAME WHERE e.EID = ?";
        Timestamp inTimestampUtc = null; long punchId = -1; Time shiftEnd = null; boolean earlyOut = false;
        Instant nowInstant = Instant.now(); Timestamp utcOutTimestamp = Timestamp.from(nowInstant);
        Connection con = null;

         try {
             con = DatabaseConnection.getConnection();
             // 1. Find open punch
             logger.fine("Finding open punch for EID " + eid + " on UTC date " + todayUtc);
             try (PreparedStatement psFind = con.prepareStatement(findOpenPunchSql)) {
                 psFind.setInt(1, eid); psFind.setDate(2, Date.valueOf(todayUtc));
                 try (ResultSet rsFind = psFind.executeQuery()) {
                     if (rsFind.next()) {
                         punchId = rsFind.getLong("punch_id"); inTimestampUtc = rsFind.getTimestamp("IN_1");
                         if (inTimestampUtc == null || punchId <= 0) { throw new SQLException("Invalid IN_1 or punch_id found in open punch query."); }
                         logger.info("Found open punch ID: " + punchId + " with IN_1 (UTC): " + inTimestampUtc);
                     } else {
                         logger.warning("EID " + eid + " punch OUT fail: No open punch found for UTC Date: " + todayUtc);
                         response.sendRedirect(buildRedirectUrl(eid, null, "No open punch found to clock out.")); // Use helper WITH EID
                         if(con != null) try { con.close(); } catch (SQLException sqle) {}
                         return;
                     }
                 }
             }

             // 2. Calculate Raw Hours
             double rawTotalHours = 0.0; Instant inInstant = inTimestampUtc.toInstant();
             if (nowInstant.isAfter(inInstant)) {
                 Duration duration = Duration.between(inInstant, nowInstant);
                 rawTotalHours = duration.toMillis() / 3_600_000.0; // Use millis
                 rawTotalHours = Math.round(rawTotalHours * ROUNDING_FACTOR) / ROUNDING_FACTOR;
             } else {
                  logger.warning("Punch OUT time ("+nowInstant+") is not after IN time ("+inInstant+"). Setting raw hours to 0.");
             }
             logger.fine("Raw hours calculated: " + rawTotalHours);

             // 3. Apply Auto-Lunch using helper
             double adjustedTotalHours = rawTotalHours; // Default if auto-lunch fails
             try {
                adjustedTotalHours = ShowPunches.applyAutoLunch(con, eid, rawTotalHours);
                adjustedTotalHours = Math.round(adjustedTotalHours * 100.0) / 100.0; // Round final total
             } catch (Exception lunchEx) {
                 logger.log(Level.WARNING, "Failed to apply auto-lunch during punch OUT for EID "+eid+". Using raw hours.", lunchEx);
                 adjustedTotalHours = Math.round(rawTotalHours * 100.0) / 100.0; // Use rounded raw
             }
             logger.fine("Adjusted hours after auto-lunch: " + adjustedTotalHours);

             // 4. OT is explicitly set to 0
             double calculatedOtHours = 0.0; logger.fine("Setting OT to 0.0 upon punch out.");

             // 5. Check for Early Out
             try(PreparedStatement psSched = con.prepareStatement(scheduleSql)) {
                 psSched.setInt(1, eid);
                 try(ResultSet rsSched = psSched.executeQuery()){
                     if(rsSched.next()){
                         shiftEnd = rsSched.getTime("SHIFT_END");
                         if (shiftEnd != null) {
                             try {
                                 String gracePeriodStr = Configuration.getProperty("GracePeriod", "0");
                                 int gracePeriod = Integer.parseInt(gracePeriodStr.trim());
                                 LocalTime nowInScheduleZone = LocalTime.now(SCHEDULE_DEFAULT_ZONE);
                                 LocalTime shiftEndTimeLocal = shiftEnd.toLocalTime();
                                 LocalTime gracePeriodEndLocal = shiftEndTimeLocal.minusMinutes(gracePeriod);
                                 earlyOut = nowInScheduleZone.isBefore(gracePeriodEndLocal);
                                 logger.fine("Early Out Check: Now=" + nowInScheduleZone + ", GraceEnd=" + gracePeriodEndLocal + " -> isEarly=" + earlyOut);
                             } catch(Exception e) { logger.log(Level.WARNING, "Error checking early out logic", e); earlyOut = false;}
                         } else { logger.fine("No shift end time, cannot check early out."); }
                     } else { logger.warning("Could not find schedule for EID " + eid + " for early out check.");}
                 }
             }

             // 6. Update the Punch Record
             logger.fine("Executing punch OUT update for punch_id " + punchId);
             try (PreparedStatement psUpdate = con.prepareStatement(updatePunchSql)) {
                 psUpdate.setTimestamp(1, utcOutTimestamp);
                 ShowPunches.setOptionalDouble(psUpdate, 2, adjustedTotalHours); // Save ADJUSTED TOTAL
                 ShowPunches.setOptionalDouble(psUpdate, 3, calculatedOtHours);  // Save OT AS 0.0
                 psUpdate.setBoolean(4, earlyOut);                               // Save early out flag
                 psUpdate.setLong(5, punchId);
                 int rowsAffected = psUpdate.executeUpdate();
                 if (rowsAffected > 0) {
                     logger.info("EID " + eid + " punched OUT successfully. Saved Total=" + String.format("%.2f", adjustedTotalHours) + (earlyOut ? " (Early)" : ""));
                     response.sendRedirect(buildRedirectUrl(eid, "Punch OUT recorded.", null)); // Use helper WITH EID
                 } else {
                     logger.warning("Punch OUT failed - no rows updated for punch_id: " + punchId);
                     response.sendRedirect(buildRedirectUrl(eid, null, "Punch OUT failed.")); // Use helper WITH EID
                 }
             } // PS closes

         } catch (SQLException e) { logger.log(Level.SEVERE, "DB error Punch OUT EID " + eid, e); response.sendRedirect(buildRedirectUrl(eid, null, "Database error during punch out.")); }
         catch (Exception e) { logger.log(Level.SEVERE, "Unexpected error Punch OUT EID " + eid, e); response.sendRedirect(buildRedirectUrl(eid, null, "Server error during punch out.")); }
         finally { if (con != null) { try { con.close(); } catch (SQLException e) { logger.log(Level.SEVERE, "Failed to close DB conn", e); } } }
         logger.info("--- Punch OUT Processing Complete for EID: " + eid + " ---");
    }


    // --- Helper Methods ---

    /** Builds redirect URL for timeclock.jsp including EID */
    private String buildRedirectUrl(int eid, String message, String error) {
        String baseUrl = "timeclock.jsp"; // Target page
        StringBuilder url = new StringBuilder(baseUrl);
        boolean firstParam = true;
        try {
            // Append EID only if it's a valid positive integer
            if (eid > 0) {
                 url.append(firstParam ? "?" : "&").append("eid=").append(eid);
                 firstParam = false;
            }

            if (message != null && !message.isEmpty()) {
                url.append(firstParam ? "?" : "&").append("message=").append(encodeUrlParam(message));
                firstParam = false;
            } else if (error != null && !error.isEmpty()) {
                 url.append(firstParam ? "?" : "&").append("error=").append(encodeUrlParam(error));
                 firstParam = false;
            }
        } catch (IOException e) {
             logger.log(Level.SEVERE, "Failed to encode URL parameters for redirect", e);
             // Fallback URL - attempt to include EID if valid
             return baseUrl + (eid > 0 ? "?eid="+eid : "") + "&error=Server+Message+Encoding+Error";
        }
        logger.fine("Redirecting Punch In/Out to: " + url.toString());
        return url.toString();
    }

    /** Helper for URL encoding */
    private String encodeUrlParam(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    }

} // End Servlet Class