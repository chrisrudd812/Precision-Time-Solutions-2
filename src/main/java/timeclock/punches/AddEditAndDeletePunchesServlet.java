package timeclock.punches;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.sql.Statement;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/AddEditAndDeletePunchesServlet")
public class AddEditAndDeletePunchesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddEditAndDeletePunchesServlet.class.getName());

    private static final DateTimeFormatter DATE_FORMATTER_FROM_USER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME_FORMATTER_FROM_USER = DateTimeFormatter.ISO_LOCAL_TIME;

    private static boolean isValid(String s) {
        return s != null && !s.trim().isEmpty();
    }
    private static void setOptionalDouble(PreparedStatement ps, int parameterIndex, Double value) throws SQLException {
        if (value != null && !value.isNaN() && !value.isInfinite()) {
            ps.setDouble(parameterIndex, value);
        } else {
            ps.setNull(parameterIndex, Types.DOUBLE);
        }
    }
    private static void setOptionalTimestamp(PreparedStatement ps, int i, Timestamp ts) throws SQLException {
        if (ts != null) {
            ps.setTimestamp(i, ts);
        } else {
            ps.setNull(i, Types.TIMESTAMP);
        }
    }

    private boolean isHoursOnlyType(String punchType) {
        if (punchType == null) return false;
        String pTypeLower = punchType.trim().toLowerCase();
        // MODIFICATION: Removed "bereavement" from this server-side check
        return pTypeLower.equals("vacation") || pTypeLower.equals("vacation time") ||
               pTypeLower.equals("sick") || pTypeLower.equals("sick time") ||
               pTypeLower.equals("personal") || pTypeLower.equals("personal time") ||
               pTypeLower.equals("holiday") || pTypeLower.equals("holiday time") ||
               pTypeLower.equals("other");
    }

    private String getAccrualColumn(String punchType) {
        if (punchType == null) return null;
        String pTypeLower = punchType.toLowerCase().trim();
        if (pTypeLower.equals("vacation") || pTypeLower.equals("vacation time")) return "VACATION_HOURS";
        if (pTypeLower.equals("sick") || pTypeLower.equals("sick time")) return "SICK_HOURS";
        if (pTypeLower.equals("personal") || pTypeLower.equals("personal time")) return "PERSONAL_HOURS";
        return null;
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        logParameters(request);

        HttpSession session = request.getSession(false);
        Integer tenantId = null;
        Integer loggedInUserEid = null;

        if (session != null) {
            Object tenantIdObj = session.getAttribute("TenantID");
            if (tenantIdObj instanceof Integer) tenantId = (Integer) tenantIdObj;
            Object userEidObj = session.getAttribute("EID");
            if (userEidObj instanceof Integer) loggedInUserEid = (Integer) userEidObj;
        }

        if (tenantId == null || tenantId <= 0) {
            logger.warning("[Servlet] Session error: Invalid TenantID. TenantID: " + tenantId);
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, false, "Session error: Invalid tenant context. Please log in again.", null);
            return;
        }
        if (loggedInUserEid == null || loggedInUserEid <= 0) {
            logger.warning("[Servlet] Session error: Invalid logged-in User EID. UserEID: " + loggedInUserEid);
            sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, false, "Session error: Invalid user context. Please log in again.", null);
            return;
        }

        String action = request.getParameter("action");
        if (!isValid(action)) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Missing action parameter.", null);
            return;
        }
        action = action.trim();

        int targetEid = 0;
        String eidStr = null;

        if ("editPunch".equals(action)) {
            eidStr = request.getParameter("editEmployeeId");
            if (!isValid(eidStr)) {

                eidStr = request.getParameter("eid");
            }

        } else if ("addHours".equals(action)){
             eidStr = request.getParameter("eid");

        }


        if (isValid(eidStr)) {
            try {
                targetEid = Integer.parseInt(eidStr.trim());
            } catch (NumberFormatException e) {
                String paramNameUsed = "eid";
                if ("editPunch".equals(action)) {
                    paramNameUsed = request.getParameter("editEmployeeId") != null ? "editEmployeeId" : "eid";
                }
                logger.warning("[Servlet] Invalid Target Employee ID format: '" + eidStr + "' (param name used: " + paramNameUsed + ") for action: " + action);
                if (("editPunch".equals(action) || "addHours".equals(action))) {
                    sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Employee ID format: " + escapeHtml(eidStr), null);
                    return;
                }
            }
        }

        if (targetEid <= 0 && ("editPunch".equals(action) || "addHours".equals(action))) {
            logger.warning("[Servlet] Missing or invalid Target Employee ID (" + targetEid + " from param '" + eidStr + "') for action: " + action);
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Missing or invalid Employee ID for this action.", null);
            return;
        }


        switch (action) {
            case "editPunch":
                handleEditPunch(request, response, tenantId, targetEid);
                break;
            case "addHours":
                handleAddHoursOrTimedPunch(request, response, tenantId, targetEid);
                break;
            case "addGlobalHoursSubmit":
                handleAddGlobalHours(request, response, tenantId, loggedInUserEid);
                break;
            case "deletePunch":
                handleDeletePunch(request, response, tenantId);
                break;
            default:
                sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid action specified: " + escapeHtml(action), null);
                break;
        }
    }

    private void handleEditPunch(HttpServletRequest request, HttpServletResponse response, Integer tenantId, int contextEid) throws IOException {
        if (tenantId == null || tenantId <= 0) {
            logger.warning("[Servlet] handleEditPunch: Called with invalid TenantID.");
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Tenant context.", null);
            return;
        }

        String punchIdStr = request.getParameter("editPunchId");
        if (!isValid(punchIdStr)) punchIdStr = request.getParameter("punchId");
        String dateStr = request.getParameter("editDate");
        if (!isValid(dateStr)) dateStr = request.getParameter("punchDate");
        String newPunchTypeStr = request.getParameter("editPunchType");
        if (!isValid(newPunchTypeStr)) newPunchTypeStr = request.getParameter("punchType");
        String inTimeStr = request.getParameter("editInTime");
        if (!isValid(inTimeStr)) inTimeStr = request.getParameter("timeIn");
        String outTimeStr = request.getParameter("editOutTime");
        if (!isValid(outTimeStr)) outTimeStr = request.getParameter("timeOut");
        String totalHoursManualStr = request.getParameter("totalHoursManual");
        String userTimeZoneStr = request.getParameter("userTimeZone");

        ZoneId userInputZoneId = ZoneId.systemDefault();
        if (isValid(userTimeZoneStr)) {
            try { userInputZoneId = ZoneId.of(userTimeZoneStr); }
            catch (Exception e) { logger.warning("[Servlet] Invalid userTimeZone for editPunch: " + userTimeZoneStr + ". Falling back."); }
        }



        if (!isValid(punchIdStr) || !isValid(dateStr) || !isValid(newPunchTypeStr)) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Punch ID, Date, and Type are required for edit.", null); return;
        }
        long punchId; try { punchId = Long.parseLong(punchIdStr.trim()); } catch (NumberFormatException e) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Punch ID format.", null); return; }

        String newPunchType = newPunchTypeStr.trim();
        LocalDate localPunchDate;
        try { localPunchDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER_FROM_USER); }
        catch (DateTimeParseException e) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Date format. Expected yyyy-MM-dd.", null); return; }

        Connection con = null;
        boolean accrualAdjusted = false;
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);
            String originalPunchTypeDb = null; Double originalTotalHoursDb = null; int punchOwnerEid = -1;
            String fetchSql = "SELECT PUNCH_TYPE, TOTAL, EID FROM punches WHERE PUNCH_ID = ? AND TenantID = ?";
            try (PreparedStatement psFetch = con.prepareStatement(fetchSql)) {
                psFetch.setLong(1, punchId); psFetch.setInt(2, tenantId);
                try (ResultSet rsFetch = psFetch.executeQuery()) {
                    if (rsFetch.next()) {
                        originalPunchTypeDb = rsFetch.getString("PUNCH_TYPE");
                        originalTotalHoursDb = rsFetch.getDouble("TOTAL"); if (rsFetch.wasNull()) originalTotalHoursDb = null;
                        punchOwnerEid = rsFetch.getInt("EID");
                    } else { throw new SQLException("Punch (ID: " + punchId + ") not found for edit."); }
                }
            }
            if (punchOwnerEid != contextEid) {
                 logger.warning("Context EID from form (" + contextEid + ") does not match punch owner EID (" + punchOwnerEid + ") for punch ID " + punchId + ". Using actual punch owner EID for operations.");
            }
            String originalAccrualCol = getAccrualColumn(originalPunchTypeDb);
            if (originalAccrualCol != null && originalTotalHoursDb != null && originalTotalHoursDb > 0) {

                String restoreSql = "UPDATE employee_data SET " + originalAccrualCol + " = " + originalAccrualCol + " + ? WHERE EID = ? AND TenantID = ?";
                try (PreparedStatement psRestore = con.prepareStatement(restoreSql)) {
                    psRestore.setDouble(1, originalTotalHoursDb); psRestore.setInt(2, punchOwnerEid); psRestore.setInt(3, tenantId);
                    if (psRestore.executeUpdate() > 0) {
                        accrualAdjusted = true;
                    }
                }
            }
            Timestamp newUtcInTimestamp = null; Timestamp newUtcOutTimestamp = null;
            LocalDate newUtcDbDate = null; Double newFinalTotalHoursForAccrual = null;
            boolean isNewTypeHoursOnly = isHoursOnlyType(newPunchType);
            String updatePunchSql; PreparedStatement psUpdatePunch;
            if (isNewTypeHoursOnly) {

                if (!isValid(totalHoursManualStr)) { throw new SQLException("Total Hours value is required for type '" + newPunchType + "'."); }
                try {
                    newFinalTotalHoursForAccrual = Double.parseDouble(totalHoursManualStr.trim());
                    if (newFinalTotalHoursForAccrual <= 0.00 || newFinalTotalHoursForAccrual > 160) { throw new SQLException("Hours must be > 0 and <= 160."); }
                    newFinalTotalHoursForAccrual = Math.round(newFinalTotalHoursForAccrual * 100.0) / 100.0;
                } catch (NumberFormatException e) { throw new SQLException("Invalid format for Total Hours: '" + totalHoursManualStr + "'."); }
                newUtcDbDate = localPunchDate.atStartOfDay(userInputZoneId).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                updatePunchSql = "UPDATE punches SET DATE = ?, IN_1 = NULL, OUT_1 = NULL, TOTAL = ?, PUNCH_TYPE = ? WHERE PUNCH_ID = ? AND TenantID = ? AND EID = ?";
                psUpdatePunch = con.prepareStatement(updatePunchSql);
                psUpdatePunch.setDate(1, java.sql.Date.valueOf(newUtcDbDate));
                setOptionalDouble(psUpdatePunch, 2, newFinalTotalHoursForAccrual);
                psUpdatePunch.setString(3, newPunchType); psUpdatePunch.setLong(4, punchId);
                psUpdatePunch.setInt(5, tenantId); psUpdatePunch.setInt(6, punchOwnerEid);
            } else {

                try {
                    if (isValid(inTimeStr)) {
                        LocalTime localInTime = LocalTime.parse(inTimeStr.trim(), TIME_FORMATTER_FROM_USER);
                        newUtcInTimestamp = Timestamp.from(LocalDateTime.of(localPunchDate, localInTime).atZone(userInputZoneId).toInstant());
                    }
                    if (isValid(outTimeStr)) {
                        LocalTime localOutTime = LocalTime.parse(outTimeStr.trim(), TIME_FORMATTER_FROM_USER);
                        LocalDateTime ldtOut = LocalDateTime.of(localPunchDate, localOutTime);
                        if (newUtcInTimestamp != null && localOutTime.isBefore(LocalDateTime.ofInstant(newUtcInTimestamp.toInstant(), userInputZoneId).toLocalTime())) {
                            ldtOut = ldtOut.plusDays(1);
                        }
                        newUtcOutTimestamp = Timestamp.from(ldtOut.atZone(userInputZoneId).toInstant());
                    }
                } catch (DateTimeParseException e_time) { throw new SQLException("Invalid Time format: " + e_time.getMessage()); }
                if (newUtcInTimestamp != null) { newUtcDbDate = newUtcInTimestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate(); }
                else if (newUtcOutTimestamp != null) { newUtcDbDate = newUtcOutTimestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate(); }
                else { newUtcDbDate = localPunchDate.atStartOfDay(userInputZoneId).toInstant().atZone(ZoneOffset.UTC).toLocalDate(); }
                updatePunchSql = "UPDATE punches SET DATE = ?, IN_1 = ?, OUT_1 = ?, PUNCH_TYPE = ? WHERE PUNCH_ID = ? AND TenantID = ? AND EID = ?";
                psUpdatePunch = con.prepareStatement(updatePunchSql);
                psUpdatePunch.setDate(1, java.sql.Date.valueOf(newUtcDbDate));
                setOptionalTimestamp(psUpdatePunch, 2, newUtcInTimestamp); setOptionalTimestamp(psUpdatePunch, 3, newUtcOutTimestamp);
                psUpdatePunch.setString(4, newPunchType); psUpdatePunch.setLong(5, punchId);
                psUpdatePunch.setInt(6, tenantId); psUpdatePunch.setInt(7, punchOwnerEid);
            }
            if (psUpdatePunch.executeUpdate() == 0) { throw new SQLException("Failed to update punch record (ID: " + punchId + ")."); }
            psUpdatePunch.close();
            if (!isNewTypeHoursOnly && newUtcInTimestamp != null && newUtcOutTimestamp != null) {
                if (newUtcOutTimestamp.toInstant().isAfter(newUtcInTimestamp.toInstant())) {
                    ShowPunches.calculateAndUpdatePunchTotal(con, tenantId, punchOwnerEid, newUtcInTimestamp, newUtcOutTimestamp, punchId);
                } else {
                    String updateTotalToNullSql = "UPDATE punches SET TOTAL = NULL WHERE PUNCH_ID = ?";
                    try (PreparedStatement psNullTotal = con.prepareStatement(updateTotalToNullSql)) { psNullTotal.setLong(1, punchId); psNullTotal.executeUpdate(); }
                }
            } else if (!isNewTypeHoursOnly && (newUtcInTimestamp == null || newUtcOutTimestamp == null)) {
                String updateTotalToNullSql = "UPDATE punches SET TOTAL = NULL WHERE PUNCH_ID = ?";
                try (PreparedStatement psNullTotal = con.prepareStatement(updateTotalToNullSql)) { psNullTotal.setLong(1, punchId); psNullTotal.executeUpdate(); }
            }
            String newEffectiveAccrualCol = getAccrualColumn(newPunchType);
            if (newEffectiveAccrualCol != null && isNewTypeHoursOnly && newFinalTotalHoursForAccrual != null && newFinalTotalHoursForAccrual > 0) {
                String deductSql = "UPDATE employee_data SET " + newEffectiveAccrualCol + " = " + newEffectiveAccrualCol + " - ? WHERE EID = ? AND TenantID = ?";
                try (PreparedStatement psDeduct = con.prepareStatement(deductSql)) {
                    psDeduct.setDouble(1, newFinalTotalHoursForAccrual); psDeduct.setInt(2, punchOwnerEid); psDeduct.setInt(3, tenantId);
                    if(psDeduct.executeUpdate() > 0) {
                        accrualAdjusted = true;
                    } else {
                        logger.warning("Failed to deduct accrual for EID " + punchOwnerEid);
                    }
                }
            }
            con.commit();
            
            String successMsg = "Punch record updated successfully.";
            if (accrualAdjusted) {
                successMsg += " Accrual balance adjusted.";
            }
            successMsg += " Overtime will be recalculated during payroll processing.";
            sendJsonResponse(response, HttpServletResponse.SC_OK, true, successMsg, null);

        } catch (SQLException e_sql) {
            rollback(con); logger.log(Level.SEVERE, "SQL error in handleEditPunch for PunchID " + punchIdStr, e_sql);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Database error: " + escapeHtml(e_sql.getMessage()), null);
        } catch (Exception e_gen) {
            rollback(con); logger.log(Level.SEVERE, "General error in handleEditPunch for PunchID " + punchIdStr, e_gen);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Server error: " + escapeHtml(e_gen.getMessage()), null);
        } finally {
            if (con != null) { try { con.setAutoCommit(true); con.close(); } catch (SQLException e_close) { logger.log(Level.WARNING, "Error closing connection in handleEditPunch", e_close); } }
        }
    }

    private void handleAddHoursOrTimedPunch(HttpServletRequest request, HttpServletResponse response, Integer tenantId, int eid) throws IOException {
        if (tenantId == null || tenantId <= 0) {
            logger.warning("[Servlet] handleAddHoursOrTimedPunch: Called with invalid TenantID.");
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Tenant context.", null);
            return;
        }
        String dateStr = request.getParameter("punchDate");
        String punchTypeStr = request.getParameter("punchType");
        String totalHoursManualStr = request.getParameter("totalHoursManual");
        String inTimeStr = request.getParameter("timeIn");
        String outTimeStr = request.getParameter("timeOut");
        String userTimeZoneStr = request.getParameter("userTimeZone");

        ZoneId userInputZoneId = ZoneId.systemDefault();
        if (isValid(userTimeZoneStr)) {
            try { userInputZoneId = ZoneId.of(userTimeZoneStr); }
            catch (Exception e) { logger.warning("[Servlet] Invalid userTimeZone for addHours: " + userTimeZoneStr + ". Falling back."); }
        }



        if (!isValid(dateStr) || !isValid(punchTypeStr)) {
            sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Date and Punch Type are required.", null); return;
        }
        String punchType = punchTypeStr.trim();
        boolean isHoursOnly = isHoursOnlyType(punchType);

        LocalDate localPunchDate; Double finalTotalHoursForAccrual = null;
        LocalDate utcDbDate; Timestamp utcInTimestamp = null; Timestamp utcOutTimestamp = null;

        try {
            localPunchDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER_FROM_USER);
            utcDbDate = localPunchDate.atStartOfDay(userInputZoneId).toInstant().atZone(ZoneOffset.UTC).toLocalDate();
            if (isHoursOnly) {
                if (!isValid(totalHoursManualStr)) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Total Hours are required for type '" + escapeHtml(punchType) + "'.", null); return; }
                finalTotalHoursForAccrual = Double.parseDouble(totalHoursManualStr.trim());
                if (finalTotalHoursForAccrual <= 0.00 || finalTotalHoursForAccrual > 160) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Total Hours must be > 0 and <= 160.", null); return; }
                finalTotalHoursForAccrual = Math.round(finalTotalHoursForAccrual * 100.0) / 100.0;
            } else {
                if (!isValid(inTimeStr)) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Time In is required for type '" + escapeHtml(punchType) + "'.", null); return; }
                try {
                    LocalTime localInTime = LocalTime.parse(inTimeStr.trim(), TIME_FORMATTER_FROM_USER);
                    utcInTimestamp = Timestamp.from(LocalDateTime.of(localPunchDate, localInTime).atZone(userInputZoneId).toInstant());
                    utcDbDate = utcInTimestamp.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
                    if (isValid(outTimeStr)) {
                        LocalTime localOutTime = LocalTime.parse(outTimeStr.trim(), TIME_FORMATTER_FROM_USER);
                        LocalDateTime ldtOut = LocalDateTime.of(localPunchDate, localOutTime);
                        if (localOutTime.isBefore(localInTime)) ldtOut = ldtOut.plusDays(1);
                        utcOutTimestamp = Timestamp.from(ldtOut.atZone(userInputZoneId).toInstant());
                    }
                } catch (DateTimeParseException e_time) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Time format: " + escapeHtml(e_time.getParsedString()), null); return; }
            }
        } catch (DateTimeParseException e_date) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid date format.", null); return;}
          catch (NumberFormatException e_num)  { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid number format for hours.", null); return;}
          catch (Exception e_parse) { logger.log(Level.SEVERE, "Error parsing inputs for add punch", e_parse); sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Error processing input data.", null); return;}

        String accrualColumn = getAccrualColumn(punchType);
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection(); con.setAutoCommit(false);
            String insertSql = "INSERT INTO punches (TenantID, EID, DATE, IN_1, OUT_1, TOTAL, PUNCH_TYPE, OT, LATE, EARLY_OUTS) VALUES (?, ?, ?, ?, ?, ?, ?, 0.0, FALSE, FALSE)";
            long newPunchId = -1;
            try (PreparedStatement psInsert = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                psInsert.setInt(1, tenantId); psInsert.setInt(2, eid);
                psInsert.setDate(3, java.sql.Date.valueOf(utcDbDate));
                setOptionalTimestamp(psInsert, 4, utcInTimestamp); setOptionalTimestamp(psInsert, 5, utcOutTimestamp);
                if (isHoursOnly) { setOptionalDouble(psInsert, 6, finalTotalHoursForAccrual); }
                else { psInsert.setNull(6, Types.DOUBLE); }
                psInsert.setString(7, punchType);
                if (psInsert.executeUpdate() == 0) throw new SQLException("Insert punch failed.");
                try (ResultSet generatedKeys = psInsert.getGeneratedKeys()) {
                    if (generatedKeys.next()) newPunchId = generatedKeys.getLong(1);
                    else throw new SQLException("Creating punch failed, no ID.");
                }

            }
            if (!isHoursOnly && utcInTimestamp != null && utcOutTimestamp != null) {
                if (utcOutTimestamp.toInstant().isAfter(utcInTimestamp.toInstant())) {
                    ShowPunches.calculateAndUpdatePunchTotal(con, tenantId, eid, utcInTimestamp, utcOutTimestamp, newPunchId);
                } else { logger.warning("New timed punch ID " + newPunchId + " has OUT not after IN."); }
            }
            if (isHoursOnly && accrualColumn != null && finalTotalHoursForAccrual != null && finalTotalHoursForAccrual > 0) {
                String updateSql = "UPDATE employee_data SET " + accrualColumn + " = " + accrualColumn + " - ? WHERE EID = ? AND TenantID = ?";
                try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                    psUpdate.setDouble(1, finalTotalHoursForAccrual); psUpdate.setInt(2, eid); psUpdate.setInt(3, tenantId);
                    if (psUpdate.executeUpdate() == 0) logger.warning("Failed to update " + accrualColumn + " for EID " + eid);
                }
            }
            con.commit();
            String successMessage = escapeHtml(punchType) + (isHoursOnly ? " hours" : " punch") + " added successfully." + (isHoursOnly && accrualColumn != null ? " Accrual balance adjusted." : "");
            sendJsonResponse(response, HttpServletResponse.SC_OK, true, successMessage, null);
        } catch (SQLException e_sql) {
            rollback(con); logger.log(Level.SEVERE, "SQL Error in handleAddHoursOrTimedPunch for EID " + eid, e_sql);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Database error: " + escapeHtml(e_sql.getMessage()), null);
        } catch (Exception e_gen) {
            rollback(con); logger.log(Level.SEVERE, "General error in handleAddHoursOrTimedPunch for EID " + eid, e_gen);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Server error: " + escapeHtml(e_gen.getMessage()), null);
        } finally {
            if (con != null) { try { con.setAutoCommit(true); con.close(); } catch (SQLException e_close) { logger.log(Level.WARNING, "Error closing connection", e_close); } }
        }
    }

    private void handleAddGlobalHours(HttpServletRequest request, HttpServletResponse response, Integer tenantId, int loggedInUserEid) throws IOException {
        if (tenantId == null || tenantId <= 0) {
             response.sendRedirect("add_global_data.jsp?error=" + URLEncoder.encode("Invalid Tenant context.", StandardCharsets.UTF_8.name()));
             return;
        }
        String dateStr = request.getParameter("addHoursDate");
        String totalHoursStr = request.getParameter("addHoursTotal");
        String punchTypeStr = request.getParameter("addHoursPunchTypeDropDown");



        if (!isValid(dateStr) || !isValid(totalHoursStr) || !isValid(punchTypeStr)) {
            response.sendRedirect("add_global_data.jsp?error=" + URLEncoder.encode("Date, Total Hours, and Punch Type are required.", StandardCharsets.UTF_8.name()));
            return;
        }

        LocalDate localPunchDate; double totalHours; String punchType = punchTypeStr.trim();
        try {
            localPunchDate = LocalDate.parse(dateStr.trim(), DATE_FORMATTER_FROM_USER);
            totalHours = Double.parseDouble(totalHoursStr.trim());
            if (totalHours <= 0.00 || totalHours > 160) {
                response.sendRedirect("add_global_data.jsp?error=" + URLEncoder.encode("Total Hours must be > 0 and <= 160.", StandardCharsets.UTF_8.name()));
                return;
            }
            totalHours = Math.round(totalHours * 100.0) / 100.0;
        } catch (DateTimeParseException | NumberFormatException e) {
            response.sendRedirect("add_global_data.jsp?error=" + URLEncoder.encode("Invalid date or hours format.", StandardCharsets.UTF_8.name()));
            return;
        }

        if (!isHoursOnlyType(punchType)) {
            response.sendRedirect("add_global_data.jsp?error=" + URLEncoder.encode("Invalid punch type for global entry. Only hours-based types are allowed.", StandardCharsets.UTF_8.name()));
            return;
        }

        Connection con = null; int employeesAffected = 0; List<Integer> activeEmployeeIds = new ArrayList<>();
        String fetchActiveEmployeesSql = "SELECT EID FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE";
        try {
            con = DatabaseConnection.getConnection(); con.setAutoCommit(false);
            try (PreparedStatement psFetch = con.prepareStatement(fetchActiveEmployeesSql)) {
                psFetch.setInt(1, tenantId);
                try (ResultSet rs = psFetch.executeQuery()) { while (rs.next()) { activeEmployeeIds.add(rs.getInt("EID")); } }
            }
            if (activeEmployeeIds.isEmpty()) {
                response.sendRedirect("add_global_data.jsp?message=" + URLEncoder.encode("No active employees found to add global hours to.", StandardCharsets.UTF_8.name()));
                return;
            }
            String insertPunchSql = "INSERT INTO punches (TenantID, EID, DATE, IN_1, OUT_1, TOTAL, PUNCH_TYPE, OT, LATE, EARLY_OUTS) VALUES (?, ?, ?, NULL, NULL, ?, ?, 0.0, FALSE, FALSE)";
            String accrualColumn = getAccrualColumn(punchType);
            String updateAccrualSql = (accrualColumn != null) ? "UPDATE employee_data SET " + accrualColumn + " = " + accrualColumn + " - ? WHERE EID = ? AND TenantID = ?" : null;

            try (PreparedStatement psInsert = con.prepareStatement(insertPunchSql);
                 PreparedStatement psUpdateAccrual = (updateAccrualSql != null) ? con.prepareStatement(updateAccrualSql) : null) {
                for (int eid : activeEmployeeIds) {
                    psInsert.setInt(1, tenantId); psInsert.setInt(2, eid);
                    psInsert.setDate(3, java.sql.Date.valueOf(localPunchDate));
                    setOptionalDouble(psInsert, 4, totalHours);
                    psInsert.setString(5, punchType);
                    psInsert.addBatch();
                    if (psUpdateAccrual != null) {
                        psUpdateAccrual.setDouble(1, totalHours); psUpdateAccrual.setInt(2, eid); psUpdateAccrual.setInt(3, tenantId);
                        psUpdateAccrual.addBatch();
                    }
                }
                int[] insertCounts = psInsert.executeBatch();
                employeesAffected = Arrays.stream(insertCounts).filter(c -> c >=0 || c == Statement.SUCCESS_NO_INFO).map(c -> c == Statement.SUCCESS_NO_INFO ? 1 : c).sum();

                if (psUpdateAccrual != null) {
                    int[] updateCounts = psUpdateAccrual.executeBatch();

                }
            }
            con.commit();
            String successMsg = String.format("%s (%.2f hours) added globally for %d active employee(s).", escapeHtml(punchType), totalHours, employeesAffected) + (accrualColumn != null ? " Accrual balances adjusted." : "");
            response.sendRedirect("add_global_data.jsp?message=" + URLEncoder.encode(successMsg, StandardCharsets.UTF_8.name()));
        } catch (SQLException e_sql) {
            rollback(con); logger.log(Level.SEVERE, "SQL Error in handleAddGlobalHours", e_sql);
            response.sendRedirect("add_global_data.jsp?error=" + URLEncoder.encode("Database error: " + escapeHtml(e_sql.getMessage()), StandardCharsets.UTF_8.name()));
        } catch (Exception e_gen) {
            rollback(con); logger.log(Level.SEVERE, "General error in handleAddGlobalHours", e_gen);
            response.sendRedirect("add_global_data.jsp?error=" + URLEncoder.encode("Server error: " + escapeHtml(e_gen.getMessage()), StandardCharsets.UTF_8.name()));
        } finally {
            if (con != null) { try { con.setAutoCommit(true); con.close(); } catch (SQLException e_close) { logger.log(Level.WARNING, "Error closing connection", e_close); } }
        }
    }

    private void handleDeletePunch(HttpServletRequest request, HttpServletResponse response, Integer tenantId) throws IOException {
         if (tenantId == null || tenantId <= 0) {
             logger.warning("[Servlet] handleDeletePunch: Called with invalid TenantID.");
             sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Tenant context.", null);
             return;
        }
        String punchIdStr = request.getParameter("punchId");
        if (!isValid(punchIdStr)) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Missing Punch ID for deletion.", null); return; }
        long punchId;
        try { punchId = Long.parseLong(punchIdStr.trim()); }
        catch (NumberFormatException e) { sendJsonResponse(response, HttpServletResponse.SC_BAD_REQUEST, false, "Invalid Punch ID format: " + escapeHtml(punchIdStr), null); return; }

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection(); con.setAutoCommit(false);
            String originalPunchType = null; Double originalHours = null; int punchOwnerEid = -1;
            String getSql = "SELECT EID, TOTAL, PUNCH_TYPE FROM punches WHERE PUNCH_ID = ? AND TenantID = ?";
            try (PreparedStatement psGet = con.prepareStatement(getSql)) {
                psGet.setLong(1, punchId); psGet.setInt(2, tenantId);
                try (ResultSet rs = psGet.executeQuery()) {
                    if (rs.next()) { punchOwnerEid = rs.getInt("EID"); originalPunchType = rs.getString("PUNCH_TYPE"); originalHours = rs.getDouble("TOTAL"); if (rs.wasNull()) originalHours = null; }
                    else { logger.warning("handleDeletePunch: Punch record ID " + punchId + " not found for TenantID " + tenantId); sendJsonResponse(response, HttpServletResponse.SC_NOT_FOUND, false, "Punch record not found.", null); con.rollback(); return;}
                }
            }
            if (punchOwnerEid <= 0) { throw new SQLException("Invalid EID (<=0) retrieved for punch ID " + punchId); }
            String accrualCol = getAccrualColumn(originalPunchType); boolean restoredAccrual = false;
            if (accrualCol != null && originalHours != null && originalHours > 0) {

                String restoreSql = "UPDATE employee_data SET " + accrualCol + " = " + accrualCol + " + ? WHERE EID = ? AND TenantID = ?";
                try (PreparedStatement psRestore = con.prepareStatement(restoreSql)) {
                    psRestore.setDouble(1, originalHours); psRestore.setInt(2, punchOwnerEid); psRestore.setInt(3, tenantId);
                    if (psRestore.executeUpdate() > 0) { restoredAccrual = true; }
                    else { logger.warning("Failed to restore accrual hours for EID " + punchOwnerEid + " (or no change needed)."); }
                }
            }
            String deleteSql = "DELETE FROM punches WHERE PUNCH_ID = ? AND TenantID = ?";
            try (PreparedStatement psDel = con.prepareStatement(deleteSql)) {
                psDel.setLong(1, punchId); psDel.setInt(2, tenantId);
                int rowsDeleted = psDel.executeUpdate();
                if (rowsDeleted > 0) {
                    con.commit(); String successMsg = "Punch deleted successfully" + (restoredAccrual ? ". Accrued hours were restored." : ".");
                    sendJsonResponse(response, HttpServletResponse.SC_OK, true, successMsg, null);
                } else { con.rollback(); logger.warning("Delete failed for punch ID " + punchId + ". Row not found or not deleted."); sendJsonResponse(response, HttpServletResponse.SC_NOT_FOUND, false, "Delete failed. Punch not found or no change made.", null); }
            }
        } catch (SQLException e_sql) {
            rollback(con); logger.log(Level.SEVERE, "SQL error in handleDeletePunch for PunchID " + punchIdStr, e_sql);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "Database error during punch deletion: " + escapeHtml(e_sql.getMessage()), null);
        } catch (Exception e_gen) {
            rollback(con); logger.log(Level.SEVERE, "General error in handleDeletePunch for PunchID " + punchIdStr, e_gen);
            sendJsonResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, false, "An unexpected server error occurred during punch deletion.", null);
        } finally {
            if (con != null) { try { con.setAutoCommit(true); con.close(); } catch (SQLException e_close) { logger.log(Level.WARNING, "Error closing connection in handleDeletePunch", e_close); } }
        }
    }

    private void sendJsonResponse(HttpServletResponse response, int statusCode, boolean success, String message, String additionalJsonKeyValuePairs) throws IOException {
        response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); response.setStatus(statusCode);
        String statusType = success ? "message" : "error";
        String escapedMessage = (message == null) ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append(String.format("{\"success\": %b, \"%s\": \"%s\"", success, statusType, escapedMessage));
        if (additionalJsonKeyValuePairs != null && !additionalJsonKeyValuePairs.trim().isEmpty()) {
            jsonBuilder.append(", \"additionalData\": {").append(additionalJsonKeyValuePairs).append("}");
        }
        jsonBuilder.append("}");
        response.getWriter().write(jsonBuilder.toString());
    }

    private void rollback(Connection con) {
        if (con != null) { try { if (!con.getAutoCommit()) { con.rollback(); } } catch (SQLException rbEx) { logger.log(Level.SEVERE, "Rollback failed!", rbEx); } }
    }

    private void logParameters(HttpServletRequest request) {
        // Debug logging removed for production
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}