package timeclock.reports;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
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
import timeclock.util.Helpers;

public class ShowReports {
    private static final Logger logger = Logger.getLogger(ShowReports.class.getName());

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

    public static List<Map<String, String>> getDepartmentsForTenant(int tenantId) {
        List<Map<String, String>> departments = new ArrayList<>();
        if (tenantId <= 0) { return departments; }
        String sql = "SELECT NAME FROM departments WHERE TenantID = ? ORDER BY NAME ASC";
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
        if (tenantId <= 0) { return schedules; }
        String sql = "SELECT NAME FROM schedules WHERE TenantID = ? ORDER BY NAME ASC";
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
        if (tenantId <= 0) { return supervisors; }
        String sql = "SELECT DISTINCT SUPERVISOR FROM employee_data WHERE TenantID = ? AND SUPERVISOR IS NOT NULL AND SUPERVISOR <> '' ORDER BY SUPERVISOR ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { supervisors.add(rs.getString("SUPERVISOR")); } }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching distinct supervisors T:" + tenantId, e); }
        return supervisors;
    }

    public static List<Map<String, String>> getAccrualPoliciesForTenant(int tenantId) {
        List<Map<String, String>> accrualPolicies = new ArrayList<>();
        if (tenantId <= 0) { return accrualPolicies; }
        String sql = "SELECT NAME FROM accruals WHERE TenantID = ? ORDER BY NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) { Map<String, String> policy = new HashMap<>(); policy.put("name", rs.getString("NAME")); accrualPolicies.add(policy); }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching accrual policies T:" + tenantId, e); }
        return accrualPolicies;
    }

    public static String showExceptionReport(int tenantId, LocalDate periodStartDate, LocalDate periodEndDate, String userTimeZoneIdStr) {
        DateTimeFormatter dateFormatForDisplay = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);
        DateTimeFormatter timeFormatForDisplay = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);
        
        ZoneId displayZoneId;
        try {
            if (!Helpers.isStringValid(userTimeZoneIdStr)) { throw new Exception("User TimeZoneId string is invalid or null."); }
            displayZoneId = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            displayZoneId = ZoneId.of(UTC_ZONE_ID);
        }
        
        LocalDate todayInUserTz = LocalDate.now(displayZoneId);
        StringBuilder html = new StringBuilder();
        boolean foundExceptions = false;

        String sql = "SELECT p.PUNCH_ID, ed.EID, ed.TenantEmployeeNumber, ed.FIRST_NAME, ed.LAST_NAME, p.DATE, p.IN_1, p.OUT_1 " +
                     "FROM employee_data ed JOIN punches p ON ed.TenantID = p.TenantID AND ed.EID = p.EID " +
                     "WHERE p.TenantID = ? AND p.OUT_1 IS NULL AND ed.ACTIVE = TRUE " +
                     "AND p.PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Sample Data', 'Regular') " + 
                     "AND p.DATE BETWEEN ? AND ? AND p.DATE < ? " + 
                     "ORDER BY ed.LAST_NAME, ed.FIRST_NAME, p.DATE, p.IN_1";

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setDate(2, Date.valueOf(periodStartDate));
            ps.setDate(3, Date.valueOf(periodEndDate));
            ps.setDate(4, Date.valueOf(todayInUserTz));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    foundExceptions = true;
                    long pId = rs.getLong("PUNCH_ID");
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                    String dEID = (tENo != null && tENo > 0) ? String.valueOf(tENo) : String.valueOf(gEID);
                    String fN = rs.getString("FIRST_NAME");
                    String lN = rs.getString("LAST_NAME");
                    Timestamp inTsUtc = rs.getTimestamp("IN_1");
                    String fDt = ZonedDateTime.ofInstant(inTsUtc.toInstant(), displayZoneId).format(dateFormatForDisplay);
                    String iC = ZonedDateTime.ofInstant(inTsUtc.toInstant(), displayZoneId).format(timeFormatForDisplay);
                    String oC = "<span class='missing-punch-placeholder'>Missing OUT</span>";

                    html.append("<tr data-punch-id=\"").append(pId).append("\" data-eid=\"").append(gEID).append("\"><td>").append(dEID).append("</td><td>").append(escapeHtml(fN)).append("</td><td>").append(escapeHtml(lN)).append("</td><td>").append(fDt).append("</td><td>").append(iC).append("</td><td class='empty-cell'>").append(oC).append("</td></tr>\n");
                }
            }
            if (!foundExceptions) { return "NO_EXCEPTIONS"; }
        } catch (SQLException e) {
            return "<tr><td colspan='6' class='report-error-row'>Error generating exception report.</td></tr>";
        }
        return html.toString();
    }
    
    public static String showTardyReport(int tenantId, LocalDate startDate, LocalDate endDate) {
        if (tenantId <= 0) { return "<tr><td colspan='5' class='report-error-row'>Invalid tenant.</td></tr>"; }
        StringBuilder html = new StringBuilder();
        boolean found = false;

        String q = "SELECT ed.EID, ed.TenantEmployeeNumber, ed.FIRST_NAME, ed.LAST_NAME, p_summary.total_late_punches, p_summary.total_early_outs " +
                   "FROM employee_data ed " +
                   "JOIN ( " +
                   "    SELECT EID, TenantID, SUM(LATE) AS total_late_punches, SUM(EARLY_OUTS) AS total_early_outs " +
                   "    FROM ( " +
                   "        SELECT TenantID, EID, LATE, EARLY_OUTS, `DATE` FROM punches " +
                   "        UNION ALL " +
                   "        SELECT TenantID, EID, LATE, EARLY_OUTS, `DATE` FROM archived_punches " +
                   "    ) p " +
                   "    WHERE p.TenantID = ? ";
        
        if (startDate != null && endDate != null) {
            q += "   AND p.`DATE` BETWEEN ? AND ? ";
        }

        q += "    GROUP BY EID, TenantID " +
             ") p_summary ON ed.EID = p_summary.EID AND ed.TenantID = p_summary.TenantID " +
             "WHERE ed.TenantID = ? AND ed.ACTIVE = TRUE " +
             "AND (p_summary.total_late_punches > 0 OR p_summary.total_early_outs > 0) " +
             "ORDER BY ed.LAST_NAME, ed.FIRST_NAME";
             
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(q)) {
            int paramIndex = 1;
            ps.setInt(paramIndex++, tenantId); // For the subquery
            
            if (startDate != null && endDate != null) {
                ps.setDate(paramIndex++, Date.valueOf(startDate));
                ps.setDate(paramIndex++, Date.valueOf(endDate));
            }
            
            ps.setInt(paramIndex++, tenantId); // For the outer query

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found = true;
                    int eid = rs.getInt("EID");
                    int lateCount = rs.getInt("total_late_punches");
                    int earlyOutCount = rs.getInt("total_early_outs");
                    Integer tENo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null; 
                    String dEID = (tENo != null && tENo > 0) ? String.valueOf(tENo) : String.valueOf(eid);
                    html.append("<tr data-eid=\"").append(eid).append("\"><td>").append(dEID).append("</td><td>")
                        .append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td><td>")
                        .append(escapeHtml(rs.getString("LAST_NAME"))).append("</td><td>")
                        .append(lateCount).append("</td><td>")
                        .append(earlyOutCount).append("</td></tr>\n");
                }
            }
            if (!found) { 
                return "<tr><td colspan='5' class='report-message-row'>No tardiness or early outs found for the selected period.</td></tr>"; 
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error generating tardy report for T:" + tenantId, e);
            return "<tr><td colspan='5' class='report-error-row'>Error retrieving tardy report.</td></tr>"; 
        }

        return html.toString();
    }

    public static String showWhosInReport(int tenantId, String userTimeZoneIdStr) {
        if (tenantId <= 0) { return "<tr><td colspan='6' class='report-error-row'>Invalid tenant.</td></tr>"; }
        ZoneId userZoneId;
        try {
            if (!isValid(userTimeZoneIdStr)) throw new Exception("Invalid TZ string");
            userZoneId = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            userZoneId = ZoneId.of(UTC_ZONE_ID);
        }
        LocalDate todayInUserTz = LocalDate.now(userZoneId);
        StringBuilder html = new StringBuilder(); 
        boolean found = false;
        String q = "SELECT ed.EID,ed.TenantEmployeeNumber,ed.FIRST_NAME,ed.LAST_NAME,ed.DEPT,ed.SCHEDULE,ed.EMAIL " + 
                   "FROM employee_data ed JOIN punches p ON ed.TenantID=p.TenantID AND ed.EID=p.EID " +
                   "WHERE ed.TenantID=? AND ed.ACTIVE=TRUE AND p.DATE=? AND p.OUT_1 IS NULL " + 
                   "AND p.IN_1=(SELECT MAX(p2.IN_1) FROM punches p2 WHERE p2.TenantID=ed.TenantID AND p2.EID=ed.EID AND p2.DATE=?) " +
                   "ORDER BY ed.LAST_NAME,ed.FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(q)) {
            ps.setInt(1, tenantId);
            ps.setDate(2, java.sql.Date.valueOf(todayInUserTz));
            ps.setDate(3, java.sql.Date.valueOf(todayInUserTz));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    found=true; Integer tENo=rs.getObject("TenantEmployeeNumber")!=null?rs.getInt("TenantEmployeeNumber"):null; String dEID=(tENo!=null&&tENo>0)?String.valueOf(tENo):String.valueOf(rs.getInt("EID"));
                    html.append("<tr data-eid=\"").append(rs.getInt("EID")).append("\"><td>").append(dEID).append("</td><td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("DEPT"))).append("</td><td>").append(escapeHtml(rs.getString("SCHEDULE"))).append("</td><td>").append(escapeHtml(rs.getString("EMAIL"))).append("</td></tr>\n");
                }
            }
            if(!found) { return "<tr><td colspan='6' class='report-message-row'>No employees currently clocked in.</td></tr>"; }
        } catch (SQLException e) { return "<tr><td colspan='6' class='report-error-row'>Error retrieving who's in report.</td></tr>"; }
        return html.toString();
    }
    
    public static String showArchivedPunchesReport(int tenantId, int eid, String userTimeZoneIdStr, LocalDate startDate, LocalDate endDate) {
        StringBuilder html = new StringBuilder();
        if (tenantId <= 0 || startDate == null || endDate == null || startDate.isAfter(endDate)) {
            return "<tr><td colspan='8' class='report-error-row'>Invalid parameters for report.</td></tr>";
        }

        ZoneId displayZoneId;
        try {
            displayZoneId = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            displayZoneId = ZoneId.of("UTC");
        }
        
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("hh:mm:ss a");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

        String sqlBase = "SELECT ed.TenantEmployeeNumber, ed.FIRST_NAME, ed.LAST_NAME, a.DATE, a.IN_1, a.OUT_1, a.TOTAL, a.PUNCH_TYPE " +
                         "FROM archived_punches a " +
                         "JOIN employee_data ed ON a.EID = ed.EID AND a.TenantID = ed.TenantID " +
                         "WHERE a.TenantID = ? AND a.DATE BETWEEN ? AND ? ";
        
        if (eid > 0) {
            sqlBase += "AND a.EID = ? ";
        }
        
        String sql = sqlBase + "ORDER BY ed.LAST_NAME, ed.FIRST_NAME, a.DATE, a.IN_1";

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            int paramIndex = 1;
            ps.setInt(paramIndex++, tenantId);
            ps.setDate(paramIndex++, Date.valueOf(startDate));
            ps.setDate(paramIndex++, Date.valueOf(endDate));
            if (eid > 0) {
                ps.setInt(paramIndex++, eid);
            }

            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    Integer tenNo = (Integer) rs.getObject("TenantEmployeeNumber");
                    String displayEid = (tenNo != null && tenNo > 0) ? tenNo.toString() : "N/A";
                    String firstName = rs.getString("FIRST_NAME");
                    String lastName = rs.getString("LAST_NAME");
                    Date punchDate = rs.getDate("DATE");
                    Timestamp inTime = rs.getTimestamp("IN_1");
                    Timestamp outTime = rs.getTimestamp("OUT_1");
                    double totalHours = rs.getDouble("TOTAL");
                    String punchType = rs.getString("PUNCH_TYPE");

                    html.append("<tr>");
                    html.append("<td>").append(escapeHtml(displayEid)).append("</td>");
                    html.append("<td>").append(escapeHtml(firstName)).append("</td>");
                    html.append("<td>").append(escapeHtml(lastName)).append("</td>");
                    html.append("<td>").append(punchDate.toLocalDate().format(dateFormatter)).append("</td>");
                    html.append("<td>").append(inTime != null ? ZonedDateTime.ofInstant(inTime.toInstant(), displayZoneId).format(timeFormatter) : "N/A").append("</td>");
                    html.append("<td>").append(outTime != null ? ZonedDateTime.ofInstant(outTime.toInstant(), displayZoneId).format(timeFormatter) : "N/A").append("</td>");
                    html.append("<td>").append(String.format("%.2f", totalHours)).append("</td>");
                    html.append("<td>").append(escapeHtml(punchType)).append("</td>");
                    html.append("</tr>\n");
                }
                if (!found) {
                    int colspan = 8;
                    html.append("<tr><td colspan='").append(colspan).append("' class='report-message-row'>No archived punches found for the selected criteria.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching archived punches for TenantID: " + tenantId, e);
            int colspan = 8;
            return "<tr><td colspan='" + colspan + "' class='report-error-row'>A database error occurred.</td></tr>";
        }
        return html.toString();
    }

    private static String generateEmployeeReportRows(int tenantId, String whereClause, Object... params) {
        StringBuilder html = new StringBuilder();
        final int COLS = 7;
        String baseSql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, SUPERVISOR, EMAIL FROM employee_data WHERE TenantID = ? ";
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
        } catch (SQLException e) { html.setLength(0); html.append("<tr><td colspan='").append(COLS).append("' class='report-error-row'>Error data.</td></tr>"); }
        return html.toString();
    }
    public static String showActiveEmployeesReport(int tenantId) { return generateEmployeeReportRows(tenantId, "AND ACTIVE = TRUE"); }
    
    public static String showInactiveEmployeesReport(int tenantId) {
        StringBuilder tableRows = new StringBuilder();
        final int VISIBLE_COLUMNS = 8;
        SimpleDateFormat dateFormatDisplay = new SimpleDateFormat("MM/dd/yyyy");
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, EMAIL, DeactivationReason, DeactivationDate " +
                     "FROM employee_data WHERE TenantID = ? AND ACTIVE = FALSE ORDER BY DeactivationDate DESC, LAST_NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    int globalEid = rs.getInt("EID");
                    Integer tenEmpNo = (Integer) rs.getObject("TenantEmployeeNumber");
                    String dEid = (tenEmpNo != null && tenEmpNo > 0) ? tenEmpNo.toString() : String.valueOf(globalEid);
                    Date deactivationDate = rs.getDate("DeactivationDate");
                    String displayDeactivationDate = (deactivationDate != null) ? dateFormatDisplay.format(deactivationDate) : "N/A";
                    String deactivationReason = rs.getString("DeactivationReason");
                    tableRows.append("<tr data-eid=\"").append(globalEid).append("\"><td>").append(escapeHtml(dEid)).append("</td><td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("DEPT"))).append("</td><td>").append(escapeHtml(rs.getString("SCHEDULE"))).append("</td><td>").append(escapeHtml(rs.getString("EMAIL"))).append("</td><td>").append(escapeHtml(displayDeactivationDate)).append("</td><td>").append(escapeHtml(deactivationReason)).append("</td></tr>\n");
                }
                if (!found) { tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-message-row'>No inactive employees.</td></tr>"); }
            }
        } catch (SQLException e) {
            tableRows.setLength(0); tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-error-row'>Error retrieving inactive employee data.</td></tr>");
        }
        return tableRows.toString();
    }

    public static String showEmployeesByDepartmentReport(int tenantId, String department) { return generateEmployeeReportRows(tenantId, "AND DEPT = ? AND ACTIVE = TRUE", department); }
    public static String showEmployeesByScheduleReport(int tenantId, String schedule) { return generateEmployeeReportRows(tenantId, "AND SCHEDULE = ? AND ACTIVE = TRUE", schedule); }
    public static String showEmployeesBySupervisorReport(int tenantId, String supervisor) { return generateEmployeeReportRows(tenantId, "AND SUPERVISOR = ? AND ACTIVE = TRUE", supervisor); }
    
    public static String showAccrualBalanceReport(int tenantId) {
        StringBuilder html = new StringBuilder();
        final int COLS = 6;
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, VACATION_HOURS, SICK_HOURS, PERSONAL_HOURS FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                    String dEID = (tENo != null && tENo > 0) ? String.valueOf(tENo) : String.valueOf(gEID);
                    html.append("<tr data-eid=\"").append(gEID).append("\"><td>").append(dEID).append("</td><td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td><td>").append(String.format(Locale.US, "%.2f", rs.getBigDecimal("VACATION_HOURS"))).append("</td><td>").append(String.format(Locale.US, "%.2f", rs.getBigDecimal("SICK_HOURS"))).append("</td><td>").append(String.format(Locale.US, "%.2f", rs.getBigDecimal("PERSONAL_HOURS"))).append("</td></tr>\n");
                }
                if (!found) {
                    html.append("<tr><td colspan='").append(COLS).append("' class='report-message-row'>No active employees found.</td></tr>");
                }
            }
        } catch (SQLException e) {
            html.setLength(0); html.append("<tr><td colspan='").append(COLS).append("' class='report-error-row'>Error generating accrual report.</td></tr>");
        }
        return html.toString();
    }

    public static String showSystemAccessReport(int tenantId) {
        StringBuilder html = new StringBuilder();
        final int COLS = 4;
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, EMAIL FROM employee_data WHERE TenantID = ? AND PERMISSIONS = 'Administrator' AND ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : null;
                    String dEID = (tENo != null && tENo > 0) ? String.valueOf(tENo) : String.valueOf(gEID);
                    html.append("<tr data-eid=\"").append(gEID).append("\"><td>").append(dEID).append("</td><td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td><td>").append(escapeHtml(rs.getString("EMAIL"))).append("</td></tr>\n");
                }
                if (!found) {
                    html.append("<tr><td colspan='").append(COLS).append("' class='report-message-row'>No active administrators found.</td></tr>");
                }
            }
        } catch (SQLException e) {
            html.setLength(0); html.append("<tr><td colspan='").append(COLS).append("' class='report-error-row'>Error generating system access report.</td></tr>");
        }
        return html.toString();
    }
}