package timeclock.reports;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;


public class ShowReports {

    private static final Logger logger = Logger.getLogger(ShowReports.class.getName());
    private static final String NOT_APPLICABLE_DISPLAY = "N/A";
    private static final String UTC_ZONE_ID = "UTC";

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    public static boolean isValid(String s) {
        return s != null && !s.trim().isEmpty() && !"null".equalsIgnoreCase(s) && !"undefined".equalsIgnoreCase(s);
    }


    // --- Methods to populate dropdowns (Tenant-Aware) ---
    public static List<Map<String, String>> getDepartmentsForTenant(int tenantId) {
        List<Map<String, String>> departments = new ArrayList<>();
        if (tenantId <= 0) { logger.warning("getDepartmentsForTenant invalid TenantID: " + tenantId); return departments; }
        String sql = "SELECT NAME FROM DEPARTMENTS WHERE TenantID = ? ORDER BY NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { Map<String, String> dept = new HashMap<>(); dept.put("name", rs.getString("NAME")); departments.add(dept); }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching departments for dropdown T:" + tenantId, e); }
        return departments;
    }

    public static List<Map<String, String>> getSchedulesForTenant(int tenantId) {
        List<Map<String, String>> schedules = new ArrayList<>();
        if (tenantId <= 0) { logger.warning("getSchedulesForTenant invalid TenantID: " + tenantId); return schedules; }
        String sql = "SELECT NAME FROM SCHEDULES WHERE TenantID = ? ORDER BY NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { Map<String, String> sched = new HashMap<>(); sched.put("name", rs.getString("NAME")); schedules.add(sched); }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching schedules for dropdown T:" + tenantId, e); }
        return schedules;
    }

    public static List<String> getSupervisorsForTenant(int tenantId) {
        List<String> supervisors = new ArrayList<>();
        if (tenantId <= 0) { logger.warning("getSupervisorsForTenant invalid TenantID: " + tenantId); return supervisors; }
        String sql = "SELECT DISTINCT SUPERVISOR FROM EMPLOYEE_DATA WHERE TenantID = ? AND SUPERVISOR IS NOT NULL AND SUPERVISOR <> '' ORDER BY SUPERVISOR ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { supervisors.add(rs.getString("SUPERVISOR")); } }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching distinct supervisors T:" + tenantId, e); }
        return supervisors;
    }

    public static List<Map<String, String>> getAccrualPoliciesForTenant(int tenantId) {
        List<Map<String, String>> accrualPolicies = new ArrayList<>();
        if (tenantId <= 0) { logger.warning("getAccrualPoliciesForTenant invalid TenantID: " + tenantId); return accrualPolicies; }
        String sql = "SELECT NAME FROM ACCRUALS WHERE TenantID = ? ORDER BY NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { Map<String, String> policy = new HashMap<>(); policy.put("name", rs.getString("NAME")); accrualPolicies.add(policy); }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching accrual policies T:" + tenantId, e); }
        return accrualPolicies;
    }
    // --- End Dropdown Methods ---


    // --- Report Generation Methods (Tenant-Aware) ---
    public static String showExceptionReport(int tenantId, LocalDate periodStartDate, LocalDate periodEndDate, String userTimeZoneIdStr) {
        DateTimeFormatter dateFormatForDisplay = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);
        DateTimeFormatter timeFormatForDisplay = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);
        
        ZoneId displayZoneId;
        try {
            if (!ShowPunches.isValid(userTimeZoneIdStr)) {
                throw new Exception("User TimeZoneId string is invalid or null.");
            }
            displayZoneId = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[ShowReports.showExceptionReport] Invalid userTimeZoneIdStr: '" + userTimeZoneIdStr + "'. Defaulting to UTC. Error: " + e.getMessage());
            displayZoneId = ZoneId.of(UTC_ZONE_ID);
        }
        logger.info("[ShowReports.showExceptionReport] Using displayZoneId: " + displayZoneId + " for TenantID: " + tenantId);

        StringBuilder html = new StringBuilder();
        boolean foundExceptions = false;

        if (tenantId <= 0) {
            return "<tr><td colspan='6' class='report-error-row'>Invalid tenant context.</td></tr>";
        }
        if (periodStartDate == null || periodEndDate == null) {
            logger.warning("[ShowReports.showExceptionReport] Called with null pay period dates for TenantID: " + tenantId);
            return "<tr><td colspan='6' class='report-error-row'>Pay period dates not set. Cannot generate report.</td></tr>";
        }
        if (periodStartDate.isAfter(periodEndDate)) {
            logger.warning("[ShowReports.showExceptionReport] Start date is after end date for TenantID: " + tenantId);
            return "<tr><td colspan='6' class='report-error-row'>Start date cannot be after end date.</td></tr>";
        }


        String sql = "SELECT p.PUNCH_ID, ed.EID, ed.TenantEmployeeNumber, ed.FIRST_NAME, ed.LAST_NAME, p.DATE, p.IN_1, p.OUT_1 " +
                     "FROM EMPLOYEE_DATA ed JOIN PUNCHES p ON ed.TenantID = p.TenantID AND ed.EID = p.EID " +
                     "WHERE p.TenantID = ? " +
                     "  AND p.OUT_1 IS NULL " +
                     "  AND ed.ACTIVE = TRUE " +
                     "  AND p.PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Sample Data', 'Regular') " + 
                     "  AND p.DATE BETWEEN ? AND ? " +
                     "ORDER BY ed.LAST_NAME, ed.FIRST_NAME, p.DATE, p.IN_1";

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setDate(2, Date.valueOf(periodStartDate));
            ps.setDate(3, Date.valueOf(periodEndDate));

            logger.info("[ShowReports.showExceptionReport] Executing for T:" + tenantId + " for period " + periodStartDate + " to " + periodEndDate + " with TZ: " + displayZoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    foundExceptions = true;
                    long pId = rs.getLong("PUNCH_ID");
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                    String dEID = (tENo != null && tENo > 0) ? String.valueOf(tENo) : String.valueOf(gEID);
                    String fN = rs.getString("FIRST_NAME");
                    String lN = rs.getString("LAST_NAME");
                    Date punchDbDate = rs.getDate("DATE");
                    Timestamp inTsUtc = rs.getTimestamp("IN_1");

                    LocalDate displayDate = (punchDbDate != null) ? punchDbDate.toLocalDate() : LocalDate.now(displayZoneId);

                    String fDt = displayDate.format(dateFormatForDisplay);
                    String iC = (inTsUtc != null) ? timeFormatForDisplay.format(ZonedDateTime.ofInstant(inTsUtc.toInstant(), displayZoneId)) : "<span class='missing-punch-placeholder'>Missing IN</span>";
                    String oC = "<span class='missing-punch-placeholder'>Missing OUT</span>";

                    html.append("<tr data-punch-id=\"").append(pId).append("\" data-eid=\"").append(gEID).append("\">")
                        .append("<td>").append(dEID).append("</td>")
                        .append("<td>").append(escapeHtml(fN)).append("</td>")
                        .append("<td>").append(escapeHtml(lN)).append("</td>")
                        .append("<td>").append(fDt).append("</td>")
                        .append("<td>").append(iC).append("</td>")
                        .append("<td class='empty-cell'>").append(oC).append("</td>")
                        .append("</tr>\n");
                }
            }
            if (!foundExceptions) {
                logger.info("[ShowReports.showExceptionReport] No exceptions found for T:" + tenantId + " in period.");
                return "NO_EXCEPTIONS";
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[ShowReports.showExceptionReport] Error ExceptionReport T:" + tenantId, e);
            return "<tr><td colspan='6' class='report-error-row'>Error generating exception report from database.</td></tr>";
        }
        return html.toString();
    }

    public static String showTardyReport(int tenantId) {
        if (tenantId <= 0) { return "<tr><td colspan='5' class='report-error-row'>Invalid tenant.</td></tr>"; }
        StringBuilder html = new StringBuilder(); boolean found = false;
        String q = "SELECT ed.EID,ed.TenantEmployeeNumber,ed.FIRST_NAME,ed.LAST_NAME,SUM(CASE WHEN p.LATE=TRUE THEN 1 ELSE 0 END)AS late_count,SUM(CASE WHEN p.EARLY_OUTS=TRUE THEN 1 ELSE 0 END)AS early_out_count FROM EMPLOYEE_DATA ed LEFT JOIN (SELECT TenantID,EID,LATE,EARLY_OUTS FROM PUNCHES UNION ALL SELECT TenantID,EID,LATE,EARLY_OUTS FROM ARCHIVED_PUNCHES)p ON ed.TenantID=p.TenantID AND ed.EID=p.EID WHERE ed.TenantID=? AND ed.ACTIVE=TRUE GROUP BY ed.TenantID,ed.EID,ed.TenantEmployeeNumber,ed.FIRST_NAME,ed.LAST_NAME HAVING SUM(CASE WHEN p.LATE=TRUE THEN 1 ELSE 0 END)>0 OR SUM(CASE WHEN p.EARLY_OUTS=TRUE THEN 1 ELSE 0 END)>0 ORDER BY ed.LAST_NAME,ed.FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, tenantId);
            logger.info("[ShowReports.showTardyReport] Executing for T:" + tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found = true; Integer tENo=rs.getObject("TenantEmployeeNumber")!=null?rs.getInt("TenantEmployeeNumber"):null; String dEID=(tENo!=null&&tENo>0)?String.valueOf(tENo):String.valueOf(rs.getInt("EID"));
                    html.append("<tr data-eid=\"").append(rs.getInt("EID")).append("\">").append("<td>").append(dEID).append("</td><td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td><td>").append(rs.getInt("late_count")).append("</td><td>").append(rs.getInt("early_out_count")).append("</td></tr>\n");
                }
            }
            if (!found) { logger.info("[ShowReports.showTardyReport] No tardies/early outs for T:" + tenantId); return "<tr><td colspan='5' class='report-message-row'>No tardiness or early outs found.</td></tr>"; }
        } catch (SQLException e) { logger.log(Level.SEVERE,"[ShowReports.showTardyReport] Error TardyReport T:"+tenantId,e); return "<tr><td colspan='5' class='report-error-row'>Error retrieving tardy report.</td></tr>"; }
        return html.toString();
    }

    public static String showWhosInReport(int tenantId) {
        if (tenantId <= 0) { return "<tr><td colspan='6' class='report-error-row'>Invalid tenant.</td></tr>"; }
        StringBuilder html = new StringBuilder(); boolean found = false;
        String q = "SELECT ed.EID,ed.TenantEmployeeNumber,ed.FIRST_NAME,ed.LAST_NAME,ed.DEPT,ed.SCHEDULE,ed.EMAIL FROM EMPLOYEE_DATA ed JOIN PUNCHES p ON ed.TenantID=p.TenantID AND ed.EID=p.EID WHERE ed.TenantID=? AND ed.ACTIVE=TRUE AND p.DATE=CURDATE() AND p.OUT_1 IS NULL AND p.IN_1=(SELECT MAX(p2.IN_1)FROM PUNCHES p2 WHERE p2.TenantID=ed.TenantID AND p2.EID=ed.EID AND p2.DATE=CURDATE())ORDER BY ed.LAST_NAME,ed.FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, tenantId);
            logger.info("[ShowReports.showWhosInReport] Executing for T:" + tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found=true; Integer tENo=rs.getObject("TenantEmployeeNumber")!=null?rs.getInt("TenantEmployeeNumber"):null; String dEID=(tENo!=null&&tENo>0)?String.valueOf(tENo):String.valueOf(rs.getInt("EID"));
                    html.append("<tr data-eid=\"").append(rs.getInt("EID")).append("\">")
                        .append("<td>").append(dEID).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("DEPT"))).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("SCHEDULE"))).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("EMAIL"))).append("</td>")
                        .append("</tr>\n");
                }
            }
            if(!found) { logger.info("[ShowReports.showWhosInReport] Nobody clocked in for T:" + tenantId); return "<tr><td colspan='6' class='report-message-row'>No employees currently clocked in.</td></tr>"; }
        } catch (SQLException e) { logger.log(Level.SEVERE,"[ShowReports.showWhosInReport] Error WhosIn T:"+tenantId,e); return "<tr><td colspan='6' class='report-error-row'>Error retrieving who's in report.</td></tr>"; }
        return html.toString();
    }

    public static String showArchivedPunchesReport(int tenantId, int globalEidFromJsp, String userTimeZoneIdStr, LocalDate startDate, LocalDate endDate) {
        StringBuilder htmlRows = new StringBuilder();
        final int colspanAllEmployeesView = 7;
        final int colspanSingleEmployeeView = 5;
        int currentReportColspan = (globalEidFromJsp <= 0) ? colspanAllEmployeesView : colspanSingleEmployeeView;

        logger.info("[ShowReports.showArchivedPunchesReport] Invoked. TenantID: " + tenantId +
                    ", Input GlobalEID: " + globalEidFromJsp + ", Start: " + startDate + ", End: " + endDate +
                    ", UserTZ: " + userTimeZoneIdStr);

        if (tenantId <= 0) { return "<tr><td colspan='" + currentReportColspan + "' class='report-error-row'>Invalid Tenant context.</td></tr>"; }
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) { return "<tr><td colspan='" + currentReportColspan + "' class='report-error-row'>Invalid date range.</td></tr>"; }

        ZoneId targetZone;
        try {
            if (!ShowPunches.isValid(userTimeZoneIdStr)) {
                 throw new Exception("User TimeZoneId string is invalid or null for archived report.");
            }
            targetZone = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[ShowReports.showArchivedPunchesReport] Invalid userTimeZoneIdStr: '" + userTimeZoneIdStr + "'. Defaulting to UTC. Error: " + e.getMessage());
            targetZone = ZoneId.of(UTC_ZONE_ID);
        }
        logger.info("[ShowReports.showArchivedPunchesReport] Using targetZone: " + targetZone + " for TenantID: " + tenantId);

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH).withZone(targetZone);
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);

        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append("SELECT p.PUNCH_ID, p.EID AS PUNCH_OWNER_EID, p.DATE AS PUNCH_TABLE_DATE, p.IN_1, p.OUT_1, p.TOTAL, p.PUNCH_TYPE");
        List<Object> params = new ArrayList<>();

        if (globalEidFromJsp <= 0) {
            sqlBuilder.append(", e.TenantEmployeeNumber, e.FIRST_NAME, e.LAST_NAME ");
            sqlBuilder.append("FROM ARCHIVED_PUNCHES p JOIN EMPLOYEE_DATA e ON p.EID = e.EID AND p.TenantID = e.TenantID ");
            sqlBuilder.append("WHERE p.TenantID = ? AND p.DATE BETWEEN ? AND ? ");
            params.add(tenantId);
            params.add(java.sql.Date.valueOf(startDate));
            params.add(java.sql.Date.valueOf(endDate));
            sqlBuilder.append("ORDER BY e.LAST_NAME ASC, e.FIRST_NAME ASC, p.DATE ASC, p.IN_1 ASC");
        } else {
            sqlBuilder.append(" FROM ARCHIVED_PUNCHES p ");
            sqlBuilder.append("WHERE p.TenantID = ? AND p.EID = ? AND p.DATE BETWEEN ? AND ? ");
            params.add(tenantId);
            params.add(globalEidFromJsp);
            params.add(java.sql.Date.valueOf(startDate));
            params.add(java.sql.Date.valueOf(endDate));
            sqlBuilder.append("ORDER BY p.DATE ASC, p.IN_1 ASC");
        }

        String finalSql = sqlBuilder.toString();

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(finalSql)) {
            for (int i = 0; i < params.size(); i++) { ps.setObject(i + 1, params.get(i)); }
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    long punchId = rs.getLong("PUNCH_ID"); int punchOwnerGlobalEid = rs.getInt("PUNCH_OWNER_EID");
                    Timestamp inTsUtc = rs.getTimestamp("IN_1"); Timestamp outTsUtc = rs.getTimestamp("OUT_1");
                    double total = rs.getDouble("TOTAL"); if (rs.wasNull()) total = 0.0;
                    String punchType = rs.getString("PUNCH_TYPE"); Date punchTableDate = rs.getDate("PUNCH_TABLE_DATE");

                    String formattedDate = NOT_APPLICABLE_DISPLAY;
                    if (inTsUtc != null) {
                        formattedDate = dateFormatter.format(ZonedDateTime.ofInstant(inTsUtc.toInstant(), targetZone));
                    } else if (punchTableDate != null) {
                        formattedDate = dateFormatter.format(punchTableDate.toLocalDate());
                    }

                    String formattedIn = (inTsUtc != null) ? timeFormatter.format(ZonedDateTime.ofInstant(inTsUtc.toInstant(), targetZone)) : "<span class='missing-punch-placeholder'>Missing</span>";
                    String formattedOut = (outTsUtc != null) ? timeFormatter.format(ZonedDateTime.ofInstant(outTsUtc.toInstant(), targetZone)) : "<span class='missing-punch-placeholder'>Missing</span>";
                    String formattedTotal = String.format(Locale.US, "%.2f", total);
                    String safePunchType = (punchType != null ? escapeHtml(punchType) : "");

                    htmlRows.append("<tr class=\"archived-row\" data-punch-id=\"").append(punchId).append("\">");
                    if (globalEidFromJsp <= 0) {
                        Integer tenEmpNo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                        String displayArchiveEid = (tenEmpNo != null && tenEmpNo > 0) ? String.valueOf(tenEmpNo) : String.valueOf(punchOwnerGlobalEid);
                        htmlRows.append("<td>").append(displayArchiveEid).append("</td>");
                        htmlRows.append("<td>").append(escapeHtml(rs.getString("LAST_NAME"))).append(", ").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td>");
                    }
                    htmlRows.append("<td>").append(formattedDate).append("</td>")
                          .append("<td>").append(formattedIn).append("</td>")
                          .append("<td>").append(formattedOut).append("</td>")
                          .append("<td class='hours-cell'>").append(formattedTotal).append("</td>")
                          .append("<td>").append(safePunchType).append("</td></tr>\n");
                }
                if (!found) { htmlRows.append("<tr><td colspan='").append(currentReportColspan).append("' class='report-message-row'>No archived punch records found for the selected criteria.</td></tr>"); }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching archived punches T:" + tenantId + ", EID_F:" + globalEidFromJsp, e);
            htmlRows.setLength(0);
            htmlRows.append("<tr><td colspan='").append(currentReportColspan).append("' class='report-error-row'>Error loading archived data: ").append(escapeHtml(e.getMessage())).append("</td></tr>");
        }
        return htmlRows.toString();
    }

    private static String generateEmployeeReportRows(int tenantId, String whereClause, Object... params) {
        StringBuilder html = new StringBuilder();
        final int COLS = 7;
        if (tenantId <= 0) { return "<tr><td colspan='" + COLS + "' class='report-error-row'>Invalid tenant.</td></tr>"; }
        String baseSql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, SUPERVISOR, EMAIL FROM EMPLOYEE_DATA WHERE TenantID = ? ";
        String finalSql = baseSql + (whereClause != null ? whereClause : "") + " ORDER BY LAST_NAME, FIRST_NAME";
        List<Object> allParams = new ArrayList<>(); allParams.add(tenantId); if (params != null) { for (Object p : params) { allParams.add(p); } }
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(finalSql)) {
            for (int i = 0; i < allParams.size(); i++) { ps.setObject(i + 1, allParams.get(i)); }
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false; while (rs.next()) {
                    found = true; int gEID = rs.getInt("EID"); Integer tENo = rs.getObject("TenantEmployeeNumber")!=null?rs.getInt("TenantEmployeeNumber"):null; String dEID = (tENo!=null&&tENo>0)?String.valueOf(tENo):String.valueOf(gEID);
                    html.append("<tr data-eid=\"").append(gEID).append("\"><td>").append(dEID).append("</td><td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("DEPT"))).append("</td><td>").append(escapeHtml(rs.getString("SCHEDULE"))).append("</td><td>").append(escapeHtml(rs.getString("SUPERVISOR"))).append("</td><td>").append(escapeHtml(rs.getString("EMAIL"))).append("</td></tr>\n");
                } if (!found) { html.append("<tr><td colspan='").append(COLS).append("' class='report-message-row'>No employees found.</td></tr>"); }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error EmployeeReport T:"+tenantId, e); html.setLength(0); html.append("<tr><td colspan='").append(COLS).append("' class='report-error-row'>Error data.</td></tr>"); }
        return html.toString();
    }
    public static String showActiveEmployeesReport(int tenantId) { return generateEmployeeReportRows(tenantId, "AND ACTIVE = TRUE"); }
    public static String showInactiveEmployeesReport(int tenantId) { return generateEmployeeReportRows(tenantId, "AND ACTIVE = FALSE"); }
    public static String showEmployeesByDepartmentReport(int tenantId, String department) { if (!isValid(department)) return "<tr><td colspan='7' class='report-error-row'>Dept not specified.</td></tr>"; return generateEmployeeReportRows(tenantId, "AND DEPT = ? AND ACTIVE = TRUE", department); }
    public static String showEmployeesByScheduleReport(int tenantId, String schedule) { if (!isValid(schedule)) return "<tr><td colspan'7' class='report-error-row'>Sched not specified.</td></tr>"; return generateEmployeeReportRows(tenantId, "AND SCHEDULE = ? AND ACTIVE = TRUE", schedule); }
    public static String showEmployeesBySupervisorReport(int tenantId, String supervisor) { if (!isValid(supervisor)) return "<tr><td colspan'7' class='report-error-row'>Super not specified.</td></tr>"; return generateEmployeeReportRows(tenantId, "AND SUPERVISOR = ? AND ACTIVE = TRUE", supervisor); }

    // --- NEW REPORTS ---
    public static String showAccrualBalanceReport(int tenantId) {
        StringBuilder html = new StringBuilder();
        final int COLS = 6;
        if (tenantId <= 0) { return "<tr><td colspan='" + COLS + "' class='report-error-row'>Invalid tenant.</td></tr>"; }
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, VACATION_HOURS, SICK_HOURS, PERSONAL_HOURS FROM EMPLOYEE_DATA WHERE TenantID = ? AND ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                    String dEID = (tENo != null && tENo > 0) ? String.valueOf(tENo) : String.valueOf(gEID);
                    html.append("<tr data-eid=\"").append(gEID).append("\">")
                        .append("<td>").append(dEID).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td>")
                        .append("<td>").append(String.format(Locale.US, "%.2f", rs.getBigDecimal("VACATION_HOURS"))).append("</td>")
                        .append("<td>").append(String.format(Locale.US, "%.2f", rs.getBigDecimal("SICK_HOURS"))).append("</td>")
                        .append("<td>").append(String.format(Locale.US, "%.2f", rs.getBigDecimal("PERSONAL_HOURS"))).append("</td>")
                        .append("</tr>\n");
                }
                if (!found) {
                    html.append("<tr><td colspan='").append(COLS).append("' class='report-message-row'>No active employees found to display accrual balances.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error generating Accrual Balance Report for T:" + tenantId, e);
            html.setLength(0);
            html.append("<tr><td colspan='").append(COLS).append("' class='report-error-row'>Error generating accrual report.</td></tr>");
        }
        return html.toString();
    }

    public static String showSystemAccessReport(int tenantId) {
        StringBuilder html = new StringBuilder();
        final int COLS = 4;
        if (tenantId <= 0) { return "<tr><td colspan='" + COLS + "' class='report-error-row'>Invalid tenant.</td></tr>"; }
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, EMAIL FROM EMPLOYEE_DATA WHERE TenantID = ? AND PERMISSIONS = 'Administrator' AND ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                    String dEID = (tENo != null && tENo > 0) ? String.valueOf(tENo) : String.valueOf(gEID);
                    html.append("<tr data-eid=\"").append(gEID).append("\">")
                        .append("<td>").append(dEID).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td>")
                        .append("<td>").append(escapeHtml(rs.getString("EMAIL"))).append("</td>")
                        .append("</tr>\n");
                }
                if (!found) {
                    html.append("<tr><td colspan='").append(COLS).append("' class='report-message-row'>No active employees with administrator permissions found.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error generating System Access Report for T:" + tenantId, e);
            html.setLength(0);
            html.append("<tr><td colspan='").append(COLS).append("' class='report-error-row'>Error generating system access report.</td></tr>");
        }
        return html.toString();
    }
}
