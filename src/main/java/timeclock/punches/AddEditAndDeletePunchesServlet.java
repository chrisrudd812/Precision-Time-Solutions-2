package timeclock.punches;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches; // For helpers
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.Date;
// Time and ResultSet no longer needed here after removing schedule fetch
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(description = "Handles Adding, Editing, and Deleting Punches via AJAX (NO Flag Recalc), Returns JSON", urlPatterns = { "/AddEditAndDeletePunchesServlet" })
public class AddEditAndDeletePunchesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddEditAndDeletePunchesServlet.class.getName());
    private static final double ROUNDING_FACTOR = 10000.0;

    // Zone assumed for parsing user's date/time input
    private static final ZoneId USER_INPUT_ZONE_ID = ZoneId.of("America/Denver");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME; // HH:mm:ss

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // --- Basic Setup & Parameter Logging ---
        logger.info("--- AddEditAndDeletePunchesServlet doPost ---");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        logger.info("Request Content-Type: " + request.getContentType());
        try {
             Enumeration<String> paramNames = request.getParameterNames();
             logger.info("Parameters:");
             if (!paramNames.hasMoreElements()) {
                 logger.info("  >> NO PARAMETERS FOUND <<");
             } else {
                 for (String paramName : Collections.list(paramNames)) {
                     logger.info("  >> '" + paramName + "'=" + request.getParameter(paramName));
                 }
             }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Param log error", e);
        }
        String action = request.getParameter("action");
        logger.info("Action Param: " + action);

        // Determine EID carefully
        String eidStr = null;
        if ("editPunch".equals(action)) {
            eidStr = request.getParameter("editEmployeeId");
        } else if ("addHours".equals(action)) {
             eidStr = request.getParameter("addHoursEmployeeId");
        } else if ("deletePunch".equals(action)) {
             // Delete might send EID differently, use 'eid' as fallback for context
             eidStr = request.getParameter("eid"); // Check if 'deleteEmployeeId' etc. is sent by JS
        }
        // General fallback if not found yet
        if (eidStr == null || eidStr.trim().isEmpty()) {
            eidStr = request.getParameter("eid");
        }
        logger.info("EID Param: " + eidStr);

        // --- Initial Checks ---
        if (action == null || action.trim().isEmpty()) { sendJsonResponse(response, false, "Missing action parameter."); return; }
        int eid = -1; // Default EID if not applicable or not found/parsed
        if (eidStr != null && !eidStr.trim().isEmpty()) { try { eid = Integer.parseInt(eidStr.trim()); } catch (NumberFormatException e) { sendJsonResponse(response, false, "Invalid EID format."); return; } }
        // Check if EID is required for the action
        if (eid <= 0 && ("editPunch".equals(action) || "addHours".equals(action))) { sendJsonResponse(response, false, "Missing or invalid EID for this action."); return; }

        // --- Route action ---
        switch (action) {
            case "editPunch": handleUpdate(request, response, eid); break;
            case "addHours": handleAdd(request, response, eid); break;
            case "deletePunch": handleDelete(request, response, eid); break; // Pass eid for context
            default: sendJsonResponse(response, false, "Invalid action specified."); break;
        }
    }

    // ========================================================================
    // handleUpdate - Simplified: Does NOT recalculate/save LATE/EARLY flags
    // Includes LocalTime fix.
    // ========================================================================
    private void handleUpdate(HttpServletRequest request, HttpServletResponse response, int eid) throws IOException {
        String punchIdStr = request.getParameter("editPunchId"); String dateStr = request.getParameter("editDate");
        String inTimeStr = request.getParameter("editInTime"); String outTimeStr = request.getParameter("editOutTime");
        String punchType = request.getParameter("editPunchType"); String errorMessage = null;
        logger.info("-> handleUpdate for EID: " + eid + ", PunchID: " + punchIdStr + " (Flags NOT updated)");

        // Basic validation
        if (punchIdStr==null || punchIdStr.trim().isEmpty() || dateStr==null || dateStr.trim().isEmpty()) { sendJsonResponse(response, false, "Missing Punch ID or Date."); return; }
        long punchId; try { punchId = Long.parseLong(punchIdStr.trim()); } catch (NumberFormatException e) { sendJsonResponse(response, false, "Invalid Punch ID."); return; }

        // Variable declarations
        LocalDate localPunchDate = null; LocalTime localInTime = null; LocalTime localOutTime = null; // Declared here for scope
        Timestamp utcInTimestamp = null; Timestamp utcOutTimestamp = null; Instant utcInInstant = null; Instant utcOutInstant = null;
        Double rawTotalHours = null; Double adjustedTotalHours = null; LocalDate utcDbDate = null;
        Connection con = null;

        try {
            con = DatabaseConnection.getConnection();

            // Step 1: Parse Input & Convert to UTC
            logger.fine("Parsing input times...");
            localPunchDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
            if (inTimeStr != null && !inTimeStr.trim().isEmpty()) {
                 localInTime = LocalTime.parse(inTimeStr.trim(), TIME_FORMATTER);
                 LocalDateTime ldtIn = LocalDateTime.of(localPunchDate, localInTime);
                 utcInInstant = ldtIn.atZone(USER_INPUT_ZONE_ID).toInstant();
                 utcInTimestamp = Timestamp.from(utcInInstant);
             } else {
                 localInTime = null; // Ensure it's null if string is empty/null
                 utcInInstant = null;
                 utcInTimestamp = null;
             }
            if (outTimeStr != null && !outTimeStr.trim().isEmpty()) {
                // *** CORRECTED: Added LocalTime declaration ***
                localOutTime = LocalTime.parse(outTimeStr.trim(), TIME_FORMATTER);
                LocalDateTime ldtOut = LocalDateTime.of(localPunchDate, localOutTime);
                // Basic overnight check needs localInTime declared outside this block
                if (utcInInstant != null && localInTime != null && localOutTime != null && localOutTime.isBefore(localInTime)) {
                     ldtOut = ldtOut.plusDays(1);
                     logger.fine("Adjusted OUT time to next day due to overnight.");
                }
                utcOutInstant = ldtOut.atZone(USER_INPUT_ZONE_ID).toInstant();
                utcOutTimestamp = Timestamp.from(utcOutInstant);
             } else {
                 localOutTime = null; // Ensure it's null if string is empty/null
                 utcOutInstant = null;
                 utcOutTimestamp = null;
             }
            logger.info("Parsed Times: localIn=" + localInTime + ", localOut=" + localOutTime);
            logger.info("Converted UTC Instants: utcIn=" + utcInInstant + ", utcOut=" + utcOutInstant);


            // Step 2: Determine UTC Date for DB (Based on IN time preferentially)
            if (utcInInstant != null) { utcDbDate = utcInInstant.atZone(ZoneOffset.UTC).toLocalDate(); }
            else if (utcOutInstant != null) { utcDbDate = utcOutInstant.atZone(ZoneOffset.UTC).toLocalDate(); } // Use OUT if IN is missing
            else if (localPunchDate != null) { utcDbDate = localPunchDate.atStartOfDay(USER_INPUT_ZONE_ID).toInstant().atZone(ZoneOffset.UTC).toLocalDate(); } // Fallback to input date
            else { logger.warning("Cannot determine UTC date for punch " + punchId + " - Date/Times missing."); /* DB might get null date */ }
            logger.info("Using UTC Date for DB: " + utcDbDate);


            // Step 3: Calculate Hours & Apply Lunch
             rawTotalHours = null;
             if (utcInInstant != null && utcOutInstant != null && utcOutInstant.isAfter(utcInInstant)) {
                 Duration duration = Duration.between(utcInInstant, utcOutInstant);
                 rawTotalHours = duration.toMillis() / 3_600_000.0; // Use milliseconds
                 rawTotalHours = Math.round(rawTotalHours * ROUNDING_FACTOR) / ROUNDING_FACTOR; // Round intermediate
                 logger.fine("Calculated Raw Hours: " + rawTotalHours);
             } else if (utcInInstant != null && utcOutInstant != null) {
                 logger.warning("OUT time not after IN time for punch " + punchId + ". Setting Raw Hours to 0.");
                 rawTotalHours = 0.0;
             } else {
                  logger.fine("Cannot calculate Raw Hours (IN or OUT missing).");
             }

             adjustedTotalHours = rawTotalHours; // Start with raw
             if (adjustedTotalHours != null && adjustedTotalHours > 0) {
                 try {
                     // Ensure connection is passed correctly if needed by applyAutoLunch
                     adjustedTotalHours = ShowPunches.applyAutoLunch(con, eid, rawTotalHours);
                     adjustedTotalHours = Math.round(adjustedTotalHours * 100.0) / 100.0; // Round final value
                     logger.fine("Applied auto-lunch (if applicable). Adjusted Total Hours: " + adjustedTotalHours);
                 } catch (Exception e) {
                     logger.log(Level.WARNING, "Auto-lunch calculation failed during edit for EID " + eid + ". Using raw hours.", e);
                     adjustedTotalHours = (rawTotalHours != null) ? Math.round(rawTotalHours * 100.0) / 100.0 : 0.0; // Fallback to rounded raw
                 }
             } else {
                 // Ensure adjustedTotalHours is 0.0 or null, appropriately rounded if needed
                 if (rawTotalHours == null) {
                     adjustedTotalHours = null; // Keep it null if raw was null
                 } else {
                     // Round even if zero before assignment
                     adjustedTotalHours = Math.round(rawTotalHours * 100.0) / 100.0;
                 }
                 logger.fine("Skipped auto-lunch calculation (Raw hours null or zero). Final Adjusted Hours: " + adjustedTotalHours);
             }


            // Step 4: Database Update (REMOVED LATE/EARLY_OUTS)
            logger.fine("Preparing database update (flags not updated)...");
            String updateSql = "UPDATE PUNCHES SET DATE = ?, IN_1 = ?, OUT_1 = ?, TOTAL = ?, OT = ?, PUNCH_TYPE = ? WHERE PUNCH_ID = ?"; // Flags Removed
            try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setDate(1, (utcDbDate != null) ? java.sql.Date.valueOf(utcDbDate) : null);
                ShowPunches.setOptionalTimestamp(ps, 2, utcInTimestamp);
                ShowPunches.setOptionalTimestamp(ps, 3, utcOutTimestamp);
                ShowPunches.setOptionalDouble(ps, 4, adjustedTotalHours); // Use calculated adjusted total
                ps.setDouble(5, 0.0); // OT always 0 on manual edit
                ps.setString(6, (punchType != null && !punchType.trim().isEmpty()) ? punchType.trim() : null);
                ps.setLong(7, punchId); // WHERE PUNCH_ID (Index is now 7)

                int rowsAffected = ps.executeUpdate(); logger.info("executeUpdate result: " + rowsAffected);
                if (rowsAffected > 0) { sendJsonResponse(response, true, "Punch updated successfully."); return; }
                else { sendJsonResponse(response, false, "Update failed. Record not found or no changes."); return; }
            } // PS closes

        } catch (DateTimeParseException e) { errorMessage = "Invalid date or time format entered."; logger.log(Level.WARNING, errorMessage + " for PunchID " + punchIdStr, e); }
        catch (SQLException e) { errorMessage = "Database error during update."; logger.log(Level.SEVERE, "SQL Error updating PunchID " + punchIdStr, e); }
        catch (Exception e) { errorMessage = "Update Error: " + e.getMessage(); logger.log(Level.SEVERE, "Error in handleUpdate PunchID " + punchIdStr, e); e.printStackTrace(); } // Print stack trace for unexpected errors
        finally { if (con != null) { try { con.close(); logger.fine("DB connection closed."); } catch (SQLException ex) {} } }
        // Send error JSON response if an exception occurred
        if (errorMessage != null) { sendJsonResponse(response, false, errorMessage); }
        else { sendJsonResponse(response, false, "An unknown error occurred during update."); }
    } // --- End handleUpdate ---

    // --- handleAdd method ---
    private void handleAdd(HttpServletRequest request, HttpServletResponse response, int eid) throws IOException {
         logger.info("Processing handleAdd for EID: " + eid);
         String dateStr = request.getParameter("addHoursDate"); String totalHoursStr = request.getParameter("addHoursTotal"); String punchType = request.getParameter("addHoursPunchTypeDropDown"); String errorMessage = null;
         if (dateStr == null || dateStr.trim().isEmpty() || totalHoursStr == null || totalHoursStr.trim().isEmpty() || punchType == null || punchType.trim().isEmpty()) { sendJsonResponse(response, false, "Missing required fields (Date, Hours, Type) for adding hours."); return; }
         LocalDate localPunchDate = null; Double totalHoursToAdd = null; LocalDate utcDbDate = null; Connection con = null;
         try { con = DatabaseConnection.getConnection(); localPunchDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER); totalHoursToAdd = Double.parseDouble(totalHoursStr.trim()); if (totalHoursToAdd <= 0) { throw new IllegalArgumentException("Hours must be positive."); } utcDbDate = localPunchDate.atStartOfDay(USER_INPUT_ZONE_ID).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
             String insertSql = "INSERT INTO PUNCHES (EID, DATE, IN_1, OUT_1, TOTAL, OT, PUNCH_TYPE, LATE, EARLY_OUTS) VALUES (?, ?, ?, ?, ?, ?, ?, false, false)"; // Explicitly false for LATE/EARLY
             try (PreparedStatement ps = con.prepareStatement(insertSql)) { ps.setInt(1, eid); ps.setDate(2, java.sql.Date.valueOf(utcDbDate)); ps.setNull(3, Types.TIMESTAMP); ps.setNull(4, Types.TIMESTAMP); ShowPunches.setOptionalDouble(ps, 5, totalHoursToAdd); ps.setDouble(6, 0.0); ps.setString(7, punchType.trim());
                 int r = ps.executeUpdate(); if (r > 0) { sendJsonResponse(response, true, "Hours added successfully."); return; } else { sendJsonResponse(response, false, "Add failed (db insert)."); return; } }
          } catch (DateTimeParseException e) {errorMessage="Invalid date format."; logger.log(Level.WARNING, errorMessage, e);} catch (NumberFormatException e) {errorMessage="Invalid hours format."; logger.log(Level.WARNING, errorMessage, e);} catch (IllegalArgumentException e) {errorMessage=e.getMessage(); logger.log(Level.WARNING, errorMessage);} catch (SQLException e) { errorMessage = "Database error while adding hours."; logger.log(Level.SEVERE, errorMessage, e); } catch (Exception e) { errorMessage = "Unexpected server error while adding hours."; logger.log(Level.SEVERE, errorMessage, e); e.printStackTrace();}
          finally { if (con != null) { try { con.close();} catch (SQLException ex) {} } }
          if (errorMessage != null) { sendJsonResponse(response, false, errorMessage); } else { sendJsonResponse(response, false, "Unknown error adding hours."); }
    } // --- End handleAdd ---

     // --- handleDelete method ---
     private void handleDelete(HttpServletRequest request, HttpServletResponse response, int eid) throws IOException {
        String punchIdStr = request.getParameter("deletePunchId"); String errorMessage = null; logger.info("Processing handleDelete for PunchID: " + punchIdStr);
        if (punchIdStr == null || punchIdStr.trim().isEmpty()) { sendJsonResponse(response, false, "Missing Punch ID for delete."); return; } long punchId; try { punchId = Long.parseLong(punchIdStr.trim()); } catch (NumberFormatException e) { sendJsonResponse(response, false, "Invalid Punch ID format."); return; }
        String deleteSql = "DELETE FROM PUNCHES WHERE PUNCH_ID = ?"; Connection con = null;
        try { con = DatabaseConnection.getConnection(); try (PreparedStatement ps = con.prepareStatement(deleteSql)) { ps.setLong(1, punchId); int r = ps.executeUpdate(); if (r > 0) { sendJsonResponse(response, true, "Punch deleted successfully."); return; } else { sendJsonResponse(response, false, "Delete failed (Record not found?)."); return; } }
         } catch (SQLException e) { errorMessage = "Database error during delete."; logger.log(Level.SEVERE, errorMessage, e); } catch (Exception e) { errorMessage = "Unexpected server error during delete."; logger.log(Level.SEVERE, errorMessage, e); e.printStackTrace();}
         finally { if (con != null) { try { con.close();} catch (SQLException ex) {} } }
         if (errorMessage != null) { sendJsonResponse(response, false, errorMessage); } else { sendJsonResponse(response, false, "Unknown error during delete."); }
    } // --- End handleDelete ---

    // --- sendJsonResponse helper ---
    private void sendJsonResponse(HttpServletResponse response, boolean success, String message) throws IOException {
        PrintWriter out = null; String json = "{\"success\": false, \"error\": \"Failed to generate JSON response.\"}";
        try { logger.fine("Attempting to send JSON Response. Success=" + success + ", Message=" + message); if (response.isCommitted()) { logger.warning("Response already committed before sendJsonResponse!"); return; } String escapedMessage = message != null ? message.replace("\"", "\\\"") : ""; json = String.format("{\"success\": %b, \"%s\": \"%s\"}", success, (success ? "message" : "error"), escapedMessage); logger.fine("Generated JSON String: " + json); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); logger.fine("Getting PrintWriter..."); out = response.getWriter(); logger.fine("PrintWriter obtained. Writing JSON..."); out.print(json); logger.fine("JSON written to PrintWriter. Flushing..."); out.flush(); logger.info("Successfully sent JSON Response: " + json);
        } catch (IOException ioException) { logger.log(Level.SEVERE, "IOException within sendJsonResponse: " + json, ioException); }
        catch (Exception e) { logger.log(Level.SEVERE, "Unexpected Exception within sendJsonResponse: " + json, e); }
    }

    // --- encode helper ---
    private String encode(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    }

} // End Servlet Class