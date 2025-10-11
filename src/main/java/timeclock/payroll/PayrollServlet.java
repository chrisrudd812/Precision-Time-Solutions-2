package timeclock.payroll;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import timeclock.Configuration;
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;
import timeclock.settings.StateOvertimeRules;
import timeclock.settings.StateOvertimeRuleDetail;
import timeclock.subscription.SubscriptionUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@WebServlet("/PayrollServlet")
public class PayrollServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PayrollServlet.class.getName());

    private static final String UTC_ZONE_ID_SERVLET = "UTC";
    private static final String DEFAULT_TENANT_PROCESSING_ZONE_ID_STR = "America/Denver";

    private static final double DEFAULT_HOURS_PER_ACCRUAL_DAY = 8.0;
    private static final String payroll_history_TABLE = "payroll_history";
    private static final String INSERT_payroll_history_SQL = "INSERT INTO " + payroll_history_TABLE
            + " (TenantID, processed_date, period_start_date, period_end_date, grand_total) VALUES (?, ?, ?, ?, ?)";

    private String encodeUrlParam(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private void rollback(Connection con) {
        if (con != null) {
            try {
                if (!con.isClosed() && !con.getAutoCommit()) {
                    logger.warning("Rolling back transaction.");
                    con.rollback();
                }
            } catch (SQLException rbEx) {
                logger.log(Level.SEVERE, "Transaction rollback failed!", rbEx);
            }
        } else {
            logger.warning("Rollback requested but connection is null.");
        }
    }

    private Integer getTenantIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object tIdObj = session.getAttribute("TenantID");
        if (tIdObj instanceof Integer) {
            Integer id = (Integer) tIdObj;
            return (id > 0) ? id : null;
        }
        return null;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String action = request.getParameter("action");

        final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
        final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";


        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);
        String userTimeZoneId = null;

        if (session != null) {
            Object userTimeZoneIdObj = session.getAttribute("userTimeZoneId");
            if (userTimeZoneIdObj instanceof String && isValidTimeZone((String)userTimeZoneIdObj)) {
                userTimeZoneId = (String) userTimeZoneIdObj;
            }
        }

        if (tenantId != null && tenantId > 0) {
            if (!isValidTimeZone(userTimeZoneId)) {
                String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone");
                if (isValidTimeZone(tenantDefaultTz)) {
                    userTimeZoneId = tenantDefaultTz;
                } else {
                    userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
                }
            }
        } else if (!isValidTimeZone(userTimeZoneId)){
             userTimeZoneId = PACIFIC_TIME_FALLBACK;
        }

        if (!isValidTimeZone(userTimeZoneId)) {
            userTimeZoneId = PACIFIC_TIME_FALLBACK;
        }

        try {
            ZoneId.of(userTimeZoneId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[PayrollServlet_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Tenant: " + tenantId, e);
            userTimeZoneId = UTC_ZONE_ID_SERVLET;
        }


        if (tenantId == null) {
            logger.log(Level.WARNING, "PayrollServlet action '" + action + "' failed: TenantID is null after session processing.");
            if ("exceptionReport".equals(action)) {
                response.setContentType("text/plain;charset=UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                try (PrintWriter out = response.getWriter()) {
                    out.print("<tr><td colspan='6' class='report-error-row'>ERROR: Session expired or invalid.</td></tr>");
                }
            } else {
                response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeUrlParam("Session expired or invalid. Please log in."));
            }
            return;
        }

        switch (action != null ? action.trim() : "") {
            case "closePayPeriod":
                handleClosePayPeriod(request, response, tenantId);
                break;
            case "exportPayroll":
                handleExportPayroll(request, response, tenantId);
                break;
            case "exceptionReport":
                handleExceptionReport(request, response, tenantId, userTimeZoneId);
                break;
            default:
                logger.warning("Unknown POST action: '" + action + "' for TenantID: " + tenantId);
                response.sendRedirect(request.getContextPath() + "/payroll.jsp?error=" + encodeUrlParam("Unknown payroll request."));
                break;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        logger.warning("Unsupported GET request received for PayrollServlet. Redirecting to payroll page.");
        response.sendRedirect("payroll.jsp?error=" + encodeUrlParam("Invalid request method. Please use POST."));
    }

    private void handleExceptionReport(HttpServletRequest request, HttpServletResponse response, int tenantId, String userTimeZoneId)
            throws IOException {

        String reportHtmlOrFlag = "<tr><td colspan='6' class='report-error-row'>Error initializing report.</td></tr>";
        LocalDate periodStartDate = null;
        LocalDate periodEndDate = null;

        try {
            String startDateStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
            String endDateStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");

            if (isValidString(startDateStr) && isValidString(endDateStr)) {
                periodStartDate = LocalDate.parse(startDateStr.trim());
                periodEndDate = LocalDate.parse(endDateStr.trim());
            } else {
                logger.warning("Exception report failed: Pay period dates not found for TenantID: " + tenantId);
                reportHtmlOrFlag = "<tr><td colspan='6' class='report-error-row'>Pay period start/end dates not set in Settings.</td></tr>";
                response.setContentType("text/plain;charset=UTF-8");
                response.setHeader("Cache-Control", "no-cache");
                try (PrintWriter out = response.getWriter()) { out.print(reportHtmlOrFlag); out.flush(); }
                return;
            }
            reportHtmlOrFlag = ShowPayroll.showExceptionReport(tenantId, periodStartDate, periodEndDate, userTimeZoneId);
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
            try (PrintWriter out = response.getWriter()) {
                out.print(reportHtmlOrFlag);
                out.flush();
            }
        } catch (DateTimeParseException dtpe) {
            logger.log(Level.SEVERE, "Error parsing pay period dates for exception report T:" + tenantId, dtpe);
            reportHtmlOrFlag = "<tr><td colspan='6' class='report-error-row'>Invalid date format in settings. Please check Settings.</td></tr>";
            sendErrorResponse(response, reportHtmlOrFlag);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error generating exception report T:" + tenantId, e);
            sendErrorResponse(response, "<tr><td colspan='6' class='report-error-row'>Server error generating report.</td></tr>");
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        if (!response.isCommitted()) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.print(message);
                out.flush();
            }
        }
    }

    private void handleExportPayroll(HttpServletRequest request, HttpServletResponse response, int tenantId)
            throws IOException {

        LocalDate sd = null, ed = null; String eMsg = null;
        try {
            String sds = Configuration.getProperty(tenantId, "PayPeriodStartDate"); String eds = Configuration.getProperty(tenantId, "PayPeriodEndDate");
            if (isValidString(sds) && isValidString(eds)) { sd = LocalDate.parse(sds.trim()); ed = LocalDate.parse(eds.trim()); }
            else { eMsg = "Pay period dates not defined in Settings."; }
        } catch (Exception e) { eMsg = "Error retrieving pay period settings. T:" + tenantId; logger.log(Level.SEVERE, eMsg, e); }
        if (sd == null || ed == null) { response.sendRedirect("payroll.jsp?error=" + encodeUrlParam(eMsg != null ? eMsg : "Invalid pay period.")); return; }
        
        String exportFormat = request.getParameter("exportFormat");
        String selectedColumnsParam = request.getParameter("selectedColumns");
        
        if (exportFormat == null) exportFormat = "excel";
        if (selectedColumnsParam == null || selectedColumnsParam.trim().isEmpty()) {
            selectedColumnsParam = "EID,FirstName,LastName,RegularHours,OvertimeHours,TotalPay";
        }
        
        String[] selectedColumns = selectedColumnsParam.split(",");
        Map<String, String> columnMapping = getColumnMapping();
        
        List<Map<String, Object>> expD;
        try { List<Map<String, Object>> calcD = ShowPayroll.calculatePayrollData(tenantId, sd, ed); expD = ShowPayroll.getRawPayrollData(calcD); }
        catch (Exception e) { logger.log(Level.SEVERE, "Error calculating payroll for export T:" + tenantId, e); response.sendRedirect("payroll.jsp?error=" + encodeUrlParam("Error processing data: " + e.getMessage())); return; }
        
        if ("csv".equals(exportFormat)) {
            exportAsCSV(response, expD, selectedColumns, columnMapping, tenantId, sd, ed);
        } else {
            exportAsExcel(response, expD, selectedColumns, columnMapping, tenantId, sd, ed);
        }
    }
    
    private Map<String, String> getColumnMapping() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("EID", "Employee ID");
        mapping.put("FirstName", "First Name");
        mapping.put("LastName", "Last Name");
        mapping.put("WageType", "Wage Type");
        mapping.put("RegularHours", "Regular Hours");
        mapping.put("OvertimeHours", "Overtime Hours");
        mapping.put("DoubleTimeHours", "Double Time Hours");
        mapping.put("HolidayOTHours", "Holiday OT Hours");
        mapping.put("DaysOffOTHours", "Days Off OT Hours");
        mapping.put("TotalOvertimeHours", "Total Overtime");
        mapping.put("TotalPaidHours", "Total Paid Hours");
        mapping.put("Wage", "Wage");
        mapping.put("TotalPay", "Total Pay");
        return mapping;
    }
    
    private void exportAsCSV(HttpServletResponse response, List<Map<String, Object>> data, String[] columns, Map<String, String> columnMapping, int tenantId, LocalDate sd, LocalDate ed) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        String fileName = "payroll_" + tenantId + "_" + sd + "_to_" + ed + ".csv";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        
        try (PrintWriter writer = response.getWriter()) {
            // Write headers
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) writer.print(",");
                writer.print("\"" + columnMapping.getOrDefault(columns[i], columns[i]) + "\"");
            }
            writer.println();
            
            // Write data
            if (data != null && !data.isEmpty()) {
                for (Map<String, Object> row : data) {
                    for (int i = 0; i < columns.length; i++) {
                        if (i > 0) writer.print(",");
                        Object value = row.getOrDefault(columns[i], "");
                        String strValue = formatValueForCSV(value, columns[i]);
                        writer.print("\"" + strValue.replace("\"", "\"\"") + "\"");
                    }
                    writer.println();
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during CSV export T:" + tenantId, e);
            if (!response.isCommitted()) {
                response.sendRedirect("payroll.jsp?error=" + encodeUrlParam("Error creating CSV file: " + e.getMessage()));
            }
        }
    }
    
    private void exportAsExcel(HttpServletResponse response, List<Map<String, Object>> data, String[] columns, Map<String, String> columnMapping, int tenantId, LocalDate sd, LocalDate ed) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String fileName = "payroll_" + tenantId + "_" + sd + "_to_" + ed + ".xlsx";
        response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        
        try (Workbook wb = new XSSFWorkbook(); OutputStream o = response.getOutputStream()) {
            Sheet sh = wb.createSheet("Payroll_" + sd + "_" + ed);
            CellStyle hS = wb.createCellStyle(); Font hF = wb.createFont(); hF.setBold(true); hS.setFont(hF);
            hS.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            hS.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CreationHelper cH = wb.getCreationHelper();
            CellStyle cS = wb.createCellStyle(); cS.setDataFormat(cH.createDataFormat().getFormat("$#,##0.00"));
            CellStyle hrS = wb.createCellStyle(); hrS.setDataFormat(cH.createDataFormat().getFormat("0.00"));
            
            // Create header row
            Row hrR = sh.createRow(0);
            for (int i = 0; i < columns.length; i++) {
                Cell c = hrR.createCell(i);
                c.setCellValue(columnMapping.getOrDefault(columns[i], columns[i]));
                c.setCellStyle(hS);
            }
            
            int rN = 1;
            if (data != null && !data.isEmpty()) {
                for (Map<String, Object> row : data) {
                    Row r = sh.createRow(rN++);
                    for (int i = 0; i < columns.length; i++) {
                        Cell cell = r.createCell(i);
                        Object value = row.get(columns[i]); // Use get() instead of getOrDefault() to see null values
                        if (value == null && "TotalOvertimeHours".equals(columns[i])) {
                            logger.warning("TotalOvertimeHours is null for row: " + row.keySet());
                        }
                        setCellValue(cell, value != null ? value : "", columns[i], cS, hrS);
                    }
                }
            } else {
                Row r = sh.createRow(rN++);
                r.createCell(0).setCellValue("No payroll data found for this period.");
            }
            
            // Auto-size columns with minimum widths
            for (int i = 0; i < columns.length; i++) {
                try { 
                    sh.autoSizeColumn(i);
                    int currentWidth = sh.getColumnWidth(i);
                    int minWidth = getMinColumnWidth(columns[i]);
                    if (currentWidth < minWidth) {
                        sh.setColumnWidth(i, minWidth);
                    }
                } catch (Exception ign) { logger.finest("Could not autosize column " + i + " during export."); }
            }
            
            wb.write(o);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during Excel export T:" + tenantId, e);
            if (!response.isCommitted()) {
                response.sendRedirect("payroll.jsp?error=" + encodeUrlParam("Error creating Excel file: " + e.getMessage()));
            }
        }
    }
    
    private String formatValueForCSV(Object value, String columnKey) {
        if (value == null) return "";
        if (columnKey.contains("Hours") && value instanceof Double) {
            return String.format("%.2f", (Double) value);
        }
        if ((columnKey.equals("Wage") || columnKey.equals("TotalPay")) && value instanceof Double) {
            return String.format("%.2f", (Double) value);
        }
        return String.valueOf(value);
    }
    
    private void setCellValue(Cell cell, Object value, String columnKey, CellStyle currencyStyle, CellStyle hoursStyle) {
        if (value == null) {
            cell.setCellValue("");
            return;
        }
        
        if (value instanceof Double) {
            double doubleValue = (Double) value;
            cell.setCellValue(doubleValue);
            if (columnKey.equals("Wage") || columnKey.equals("TotalPay")) {
                cell.setCellStyle(currencyStyle);
            } else if (columnKey.contains("Hours")) {
                cell.setCellStyle(hoursStyle);
            }
        } else {
            cell.setCellValue(String.valueOf(value));
        }
    }
    
    private int getMinColumnWidth(String columnKey) {
        // Excel column width units: 1 unit = 1/256th of character width
        switch (columnKey) {
            case "EID": return 2560; // ~10 characters
            case "FirstName": case "LastName": return 3840; // ~15 characters
            case "WageType": return 2560; // ~10 characters
            case "RegularHours": case "OvertimeHours": case "DoubleTimeHours":
            case "HolidayOTHours": case "DaysOffOTHours": case "TotalOvertimeHours":
            case "TotalPaidHours": return 3072; // ~12 characters
            case "Wage": case "TotalPay": return 3584; // ~14 characters
            default: return 2560; // ~10 characters default
        }
    }

    private void handleClosePayPeriod(HttpServletRequest request, HttpServletResponse response, int tenantId)
            throws IOException {

        LocalDate csd = null, ced = null; String pt = "WEEKLY"; int ppy = 52;
        String opMsg = "Initialization failed."; boolean oS = false;
        Connection con = null;

        String tenantProcessingTimeZoneIdStr = Configuration.getProperty(tenantId, "DefaultTimeZone", DEFAULT_TENANT_PROCESSING_ZONE_ID_STR);
        ZoneId processingZone;
        try {
            if(!isValidTimeZone(tenantProcessingTimeZoneIdStr)) throw new Exception("Tenant DefaultTimeZone is invalid for processing.");
            processingZone = ZoneId.of(tenantProcessingTimeZoneIdStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Invalid Tenant DefaultTimeZone for processing: '" + tenantProcessingTimeZoneIdStr + "' for T:" + tenantId + ". Defaulting. Error: " + e.getMessage());
            processingZone = ZoneId.of(DEFAULT_TENANT_PROCESSING_ZONE_ID_STR);
        }


        try {
            String sds = Configuration.getProperty(tenantId, "PayPeriodStartDate");
            String eds = Configuration.getProperty(tenantId, "PayPeriodEndDate");
            pt = Configuration.getProperty(tenantId, "PayPeriodType", "WEEKLY").toUpperCase(Locale.ENGLISH);
            if (!isValidString(sds) || !isValidString(eds)) { throw new Exception("Current Pay Period dates are not set in Settings."); }
            csd = LocalDate.parse(sds.trim()); ced = LocalDate.parse(eds.trim());
            switch (pt) {
                case "DAILY": ppy = 365; break; case "WEEKLY": ppy = 52; break; case "BIWEEKLY": ppy = 26; break;
                case "SEMIMONTHLY": ppy = 24; break; case "MONTHLY": ppy = 12; break;
                default: ppy = 52; logger.warning("Unknown PayPeriodType '" + pt + "', defaulting to 52 for T:"+tenantId); break;
            }
        } catch (Exception e) {
            opMsg = "Error reading settings: " + e.getMessage();
            oS = false;
            // Fall through to redirect at the end
        }

        if (csd != null && ced != null) {
            int arcC = 0, delC = 0, accUC = 0;
            BigDecimal fGT = BigDecimal.ZERO;
            try {
                con = DatabaseConnection.getConnection();
                con.setAutoCommit(false);

                if (!updatePunchOtForPeriod(con, tenantId, csd, ced, processingZone)) {
                    throw new SQLException("Failed to update Overtime/Double Time values on punches.");
                }
                List<Map<String, Object>> fpD = ShowPayroll.calculatePayrollData(tenantId, csd, ced);
                fGT = fpD.stream().map(rD -> BigDecimal.valueOf((Double) rD.getOrDefault("TotalPay", 0.0))).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);

                String cL = "TenantID, PUNCH_ID, EID, DATE, IN_1, OUT_1, TOTAL, OT, DT, LATE, EARLY_OUTS, PUNCH_TYPE";
                String aSQL = "INSERT INTO archived_punches (" + cL + ") SELECT " + cL + " FROM punches WHERE TenantID=? AND DATE BETWEEN ? AND ?";
                try (PreparedStatement psA = con.prepareStatement(aSQL)) {
                    psA.setInt(1, tenantId); psA.setDate(2, Date.valueOf(csd)); psA.setDate(3, Date.valueOf(ced));
                    arcC = psA.executeUpdate();
                }

                String dSQL = "DELETE FROM punches WHERE TenantID=? AND DATE BETWEEN ? AND ?";
                try (PreparedStatement psD = con.prepareStatement(dSQL)) {
                    psD.setInt(1, tenantId); psD.setDate(2, Date.valueOf(csd)); psD.setDate(3, Date.valueOf(ced));
                    delC = psD.executeUpdate();
                }
                if (arcC != delC) { throw new SQLException("Mismatch archiving/deleting punches. Rolling back."); }

                String gASQL = "SELECT e.EID, e.VACATION_HOURS, e.SICK_HOURS, e.PERSONAL_HOURS, a.VACATION AS AV, a.SICK AS ASick, a.PERSONAL AS APers FROM employee_data e JOIN accruals a ON e.TenantID = a.TenantID AND e.ACCRUAL_POLICY = a.NAME WHERE e.TenantID = ? AND e.ACTIVE = TRUE";
                String uASQL = "UPDATE employee_data SET VACATION_HOURS=?,SICK_HOURS=?,PERSONAL_HOURS=? WHERE EID=? AND TenantID=?";
                try (PreparedStatement psG = con.prepareStatement(gASQL); PreparedStatement psU = con.prepareStatement(uASQL)) {
                    psG.setInt(1, tenantId);
                    try (ResultSet rs = psG.executeQuery()) {
                        while (rs.next()) {
                            double hpa = DEFAULT_HOURS_PER_ACCRUAL_DAY;
                            double vA = (rs.getInt("AV") / (double) ppy) * hpa;
                            double sA = (rs.getInt("ASick") / (double) ppy) * hpa;
                            double pA = (rs.getInt("APers") / (double) ppy) * hpa;
                            psU.setDouble(1, Math.round((rs.getDouble("VACATION_HOURS") + vA) * 100.0) / 100.0);
                            psU.setDouble(2, Math.round((rs.getDouble("SICK_HOURS") + sA) * 100.0) / 100.0);
                            psU.setDouble(3, Math.round((rs.getDouble("PERSONAL_HOURS") + pA) * 100.0) / 100.0);
                            psU.setInt(4, rs.getInt("EID")); psU.setInt(5, tenantId);
                            psU.addBatch();
                        }
                        int[] cts = psU.executeBatch();
                        accUC = Arrays.stream(cts).filter(c -> c >= 0 || c == Statement.SUCCESS_NO_INFO).sum();
                    }
                }

                LocalDate nsd = ced.plusDays(1); LocalDate ned;
                switch (pt) {
                    case "DAILY": ned = nsd; break;
                    case "WEEKLY": ned = nsd.plusDays(6); break;
                    case "BIWEEKLY": ned = nsd.plusDays(13); break;
                    case "SEMIMONTHLY": ned = (nsd.getDayOfMonth() == 1) ? nsd.withDayOfMonth(15) : nsd.with(TemporalAdjusters.lastDayOfMonth()); break;
                    case "MONTHLY": ned = nsd.with(TemporalAdjusters.lastDayOfMonth()); break;
                    default: ned = nsd.plusDays(6); break;
                }
                Configuration.saveProperty(tenantId, "PayPeriodStartDate", nsd.toString());
                Configuration.saveProperty(tenantId, "PayPeriodEndDate", ned.toString());

                try (PreparedStatement psH = con.prepareStatement(INSERT_payroll_history_SQL)) {
                    psH.setInt(1, tenantId); psH.setTimestamp(2, Timestamp.from(Instant.now()));
                    psH.setDate(3, Date.valueOf(csd)); psH.setDate(4, Date.valueOf(ced));
                    psH.setBigDecimal(5, fGT);
                    psH.executeUpdate();
                }

                con.commit();
                oS = true;
                opMsg = String.format(Locale.US, "Period (%s to %s) closed. %d punches archived. %d PTO balance(s) updated. Payroll Total: $%.2f. Next period: %s to %s.", csd, ced, arcC, accUC, fGT.doubleValue(), nsd, ned);            } catch (Exception e) {
                oS = false; opMsg = "Error during close: " + e.getMessage();
                rollback(con);
            } finally {
                if (con != null) { try { con.setAutoCommit(true); con.close(); } catch (SQLException e) {} }
            }
        }
        
        String redirectUrl = "payroll.jsp?";
        if (oS) {
            redirectUrl += "message=" + encodeUrlParam(opMsg) + "&msgType=success";
        } else {
            redirectUrl += "error=" + encodeUrlParam(opMsg) + "&msgType=error";
        }
        response.sendRedirect(redirectUrl);
    }

    private boolean updatePunchOtForPeriod(Connection con, int tenantId, LocalDate startDate, LocalDate endDate, ZoneId scheduleZone) throws SQLException {
        // Check if tenant has Pro plan for state-based overtime
        boolean hasProPlan = SubscriptionUtils.hasProPlan(tenantId);
        String overtimeType = Configuration.getProperty(tenantId, "OvertimeType", "manual");
        
        boolean weeklyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "Overtime", "true"));
        boolean dailyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDaily", "false"));
        double dailyThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDailyThreshold", "8.0"));
        boolean doubleTimeEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDoubleTimeEnabled", "false"));
        double doubleTimeThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDoubleTimeThreshold", "12.0"));
        boolean seventhDayOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeSeventhDayEnabled", "false"));
        double seventhDayOTThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeSeventhDayOTThreshold", "0.0"));
        double seventhDayDTThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeSeventhDayDTThreshold", "8.0"));
        String firstDayOfWeekSetting = Configuration.getProperty(tenantId, "FirstDayOfWeek", "SUNDAY").toUpperCase(Locale.ENGLISH);

        String punchesSql = "SELECT p.PUNCH_ID,p.EID,p.DATE AS PUNCH_UTC_DATE,p.IN_1 AS IN_UTC,p.OUT_1 AS OUT_UTC,p.PUNCH_TYPE, s.AUTO_LUNCH,s.HRS_REQUIRED,s.LUNCH_LENGTH,e.STATE FROM punches p JOIN employee_data e ON p.EID=e.EID AND p.TenantID=e.TenantID LEFT JOIN schedules s ON e.SCHEDULE=s.NAME AND e.TenantID=s.TenantID WHERE p.TenantID=? AND e.ACTIVE=TRUE AND e.WAGE_TYPE='Hourly' AND p.DATE BETWEEN ? AND ? ORDER BY p.EID,p.DATE,p.IN_1";
        Map<Integer, List<Map<String, Object>>> punchesByEidMap = new HashMap<>();
        try (PreparedStatement psP = con.prepareStatement(punchesSql)) {
            psP.setInt(1, tenantId); psP.setDate(2, Date.valueOf(startDate)); psP.setDate(3, Date.valueOf(endDate));
            try (ResultSet rs = psP.executeQuery()) {
                while (rs.next()) {
                    int eid = rs.getInt("EID"); Map<String, Object> pD = new HashMap<>();
                    pD.put("PUNCH_ID", rs.getLong("PUNCH_ID"));
                    Timestamp iTs = rs.getTimestamp("IN_UTC"); pD.put("In", iTs != null ? iTs.toInstant() : null);
                    Timestamp oTs = rs.getTimestamp("OUT_UTC"); pD.put("Out", oTs != null ? oTs.toInstant() : null);
                    pD.put("PunchType", rs.getString("PUNCH_TYPE"));
                    pD.put("AutoLunch", rs.getBoolean("s.AUTO_LUNCH"));
                    Object hrObj = rs.getObject("s.HRS_REQUIRED"); pD.put("HoursRequired", hrObj != null ? ((Number)hrObj).doubleValue() : null);
                    Object llObj = rs.getObject("s.LUNCH_LENGTH"); pD.put("LunchLength", llObj != null ? ((Number)llObj).intValue() : null);
                    pD.put("EmployeeState", rs.getString("e.STATE"));
                    punchesByEidMap.computeIfAbsent(eid, k -> new ArrayList<>()).add(pD);
                }
            }
        }

        String updateOtSql = "UPDATE punches SET OT=?, DT=?, TOTAL=? WHERE PUNCH_ID=? AND TenantID=?";
        try (PreparedStatement psUOt = con.prepareStatement(updateOtSql)) {
            for (Map.Entry<Integer, List<Map<String, Object>>> empPunchesEntry : punchesByEidMap.entrySet()) {
                List<Map<String, Object>> punches = empPunchesEntry.getValue();
                Map<LocalDate, Double> dailyWorkHours = new HashMap<>();
                Map<LocalDate, Double> dailyDtHoursProcessed = new HashMap<>();
                Map<LocalDate, Double> dailyOtHoursProcessed = new HashMap<>();
                
                // Get employee's state for Pro plan users
                String employeeState = null;
                if (!punches.isEmpty()) {
                    employeeState = (String) punches.get(0).get("EmployeeState");
                }
                
                // Get state-specific overtime rules based on overtime type setting
                StateOvertimeRuleDetail stateRules = null;
                if (hasProPlan && "employee_state".equals(overtimeType) && employeeState != null && !employeeState.trim().isEmpty()) {
                    stateRules = StateOvertimeRules.getRulesForState(employeeState);
                }
                
                // Use state rules if available, otherwise use FLSA standards for employee_state mode
                boolean effectiveDailyOtEnabled, effectiveDoubleTimeEnabled;
                double effectiveDailyThreshold, effectiveDoubleTimeThreshold;
                
                if (stateRules != null) {
                    // Use state-specific rules
                    effectiveDailyOtEnabled = stateRules.isDailyOTEnabled();
                    effectiveDailyThreshold = stateRules.getDailyOTThreshold();
                    effectiveDoubleTimeEnabled = stateRules.isDoubleTimeEnabled();
                    effectiveDoubleTimeThreshold = stateRules.getDoubleTimeThreshold();
                } else if (hasProPlan && "employee_state".equals(overtimeType)) {
                    // Use FLSA standards for states without special rules in employee_state mode
                    effectiveDailyOtEnabled = false;
                    effectiveDailyThreshold = 0.0;
                    effectiveDoubleTimeEnabled = false;
                    effectiveDoubleTimeThreshold = 0.0;
                } else {
                    // Use tenant configuration for other modes
                    effectiveDailyOtEnabled = dailyOtEnabled;
                    effectiveDailyThreshold = dailyThreshold;
                    effectiveDoubleTimeEnabled = doubleTimeEnabled;
                    effectiveDoubleTimeThreshold = doubleTimeThreshold;
                }

                for (Map<String, Object> punch : punches) {
                    Instant iI = (Instant) punch.get("In"); Instant oI = (Instant) punch.get("Out");
                    double calculatedTotalHours = 0;
                    if (iI != null && oI != null && oI.isAfter(iI)) {
                        Duration dr = Duration.between(iI, oI); double rawHours = dr.toMillis() / 3_600_000.0;
                        calculatedTotalHours = rawHours;
                        boolean empAutoLunch = (Boolean) punch.getOrDefault("AutoLunch", false);
                        Double empHrsRequired = (Double) punch.get("HoursRequired");
                        Integer empLunchLength = (Integer) punch.get("LunchLength");
                        if (empAutoLunch && empHrsRequired != null && empLunchLength != null && empHrsRequired > 0 && empLunchLength > 0 && rawHours > empHrsRequired) {
                            calculatedTotalHours = Math.max(0, rawHours - (empLunchLength / 60.0));
                        }
                        calculatedTotalHours = Math.round(calculatedTotalHours * ShowPayroll.ROUNDING_FACTOR) / ShowPayroll.ROUNDING_FACTOR;
                    }
                    punch.put("CalculatedTotalHours", calculatedTotalHours);
                    if (ShowPayroll.isPunchTypeConsideredWorkForOT((String)punch.get("PunchType")) && calculatedTotalHours > 0 && iI != null) {
                        LocalDate punchDateInScheduleZone = ZonedDateTime.ofInstant(iI, scheduleZone).toLocalDate();
                        dailyWorkHours.put(punchDateInScheduleZone, dailyWorkHours.getOrDefault(punchDateInScheduleZone, 0.0) + calculatedTotalHours);
                    }
                }

                Map<LocalDate, Double> weeklyHoursForFLSA = new HashMap<>();

                for (Map<String, Object> punch : punches) {
                    double punchOtHours = 0; double punchDtHours = 0;
                    double calculatedTotalHours = (Double) punch.getOrDefault("CalculatedTotalHours", 0.0);
                    String punchType = (String) punch.get("PunchType");
                    Instant inInstant = (Instant) punch.get("In");

                    if (ShowPayroll.isPunchTypeConsideredWorkForOT(punchType) && calculatedTotalHours > 0 && inInstant != null) {
                        LocalDate punchDateInScheduleZone = ZonedDateTime.ofInstant(inInstant, scheduleZone).toLocalDate();
                        double remainingHoursInPunch = calculatedTotalHours;

                        if (effectiveDoubleTimeEnabled && dailyWorkHours.getOrDefault(punchDateInScheduleZone,0.0) > effectiveDoubleTimeThreshold) {
                            double dayTotal = dailyWorkHours.get(punchDateInScheduleZone);
                            double alreadyProcessedDt = dailyDtHoursProcessed.getOrDefault(punchDateInScheduleZone, 0.0);
                            double potentialDtForDay = Math.max(0, dayTotal - effectiveDoubleTimeThreshold - alreadyProcessedDt);
                            double dtAppliedToThisPunch = Math.min(remainingHoursInPunch, potentialDtForDay);
                            if (dtAppliedToThisPunch > 0) {
                                punchDtHours += dtAppliedToThisPunch;
                                remainingHoursInPunch -= dtAppliedToThisPunch;
                                dailyDtHoursProcessed.put(punchDateInScheduleZone, alreadyProcessedDt + dtAppliedToThisPunch);
                            }
                        }

                        if (effectiveDailyOtEnabled && dailyWorkHours.getOrDefault(punchDateInScheduleZone,0.0) > effectiveDailyThreshold) {
                            double dayTotalAfterDtConsideration = dailyWorkHours.get(punchDateInScheduleZone) - dailyDtHoursProcessed.getOrDefault(punchDateInScheduleZone,0.0);
                            double alreadyProcessedOt = dailyOtHoursProcessed.getOrDefault(punchDateInScheduleZone, 0.0);
                            double potentialOtForDay = Math.max(0, dayTotalAfterDtConsideration - effectiveDailyThreshold - alreadyProcessedOt);
                            double otAppliedToThisPunch = Math.min(remainingHoursInPunch, potentialOtForDay);
                            if (otAppliedToThisPunch > 0) {
                                punchOtHours += otAppliedToThisPunch;
                                remainingHoursInPunch -= otAppliedToThisPunch;
                                dailyOtHoursProcessed.put(punchDateInScheduleZone, alreadyProcessedOt + otAppliedToThisPunch);
                            }
                        }
                        
                        if(remainingHoursInPunch > 0) {
                            LocalDate weekStartDate = ShowPunches.calculateWeekStart(punchDateInScheduleZone, firstDayOfWeekSetting);
                            weeklyHoursForFLSA.put(weekStartDate, weeklyHoursForFLSA.getOrDefault(weekStartDate, 0.0) + remainingHoursInPunch);
                        }
                    }
                    punch.put("ProcessedOt", punchOtHours);
                    punch.put("ProcessedDt", punchDtHours);
                }

                 Map<LocalDate, Integer> daysWorkedInWeekMap = new HashMap<>();
                 if(seventhDayOtEnabled){
                     for (Map<String, Object> punch : punches){
                         if(ShowPayroll.isPunchTypeConsideredWorkForOT((String)punch.get("PunchType")) && (Double)punch.getOrDefault("CalculatedTotalHours",0.0) > 0){
                             Instant inInstant = (Instant)punch.get("In");
                             if(inInstant != null){
                                 LocalDate punchDateInScheduleZone = ZonedDateTime.ofInstant(inInstant, scheduleZone).toLocalDate();
                                 LocalDate weekStartDate = ShowPunches.calculateWeekStart(punchDateInScheduleZone, firstDayOfWeekSetting);
                                 daysWorkedInWeekMap.put(weekStartDate, daysWorkedInWeekMap.getOrDefault(weekStartDate,0) | (1 << punchDateInScheduleZone.getDayOfWeek().getValue()));
                             }
                         }
                     }
                 }

                for (Map<String, Object> punch : punches) {
                    double punchOtHours = (Double)punch.getOrDefault("ProcessedOt", 0.0);
                    double punchDtHours = (Double)punch.getOrDefault("ProcessedDt", 0.0);
                    double calculatedTotalHours = (Double) punch.getOrDefault("CalculatedTotalHours", 0.0);
                    double hoursRemainingForWeeklyOtCalc = calculatedTotalHours - punchOtHours - punchDtHours;
                    String punchType = (String) punch.get("PunchType");
                    Instant inInstant = (Instant) punch.get("In");

                    if (ShowPayroll.isPunchTypeConsideredWorkForOT(punchType) && hoursRemainingForWeeklyOtCalc > 0 && inInstant != null) {
                        LocalDate punchDateInScheduleZone = ZonedDateTime.ofInstant(inInstant, scheduleZone).toLocalDate();
                        LocalDate weekStartDate = ShowPunches.calculateWeekStart(punchDateInScheduleZone, firstDayOfWeekSetting);

                        if(seventhDayOtEnabled && Integer.bitCount(daysWorkedInWeekMap.getOrDefault(weekStartDate,0)) == 7 && punchDateInScheduleZone.equals(weekStartDate.plusDays(6))){
                            double hoursOnSeventhDay = hoursRemainingForWeeklyOtCalc;
                            if(hoursOnSeventhDay > seventhDayDTThreshold && seventhDayDTThreshold >=0){
                                double dtForSeventh = hoursOnSeventhDay - seventhDayDTThreshold;
                                punchDtHours += dtForSeventh;
                                hoursRemainingForWeeklyOtCalc -= dtForSeventh;
                                hoursOnSeventhDay -= dtForSeventh;
                            }
                            if(hoursOnSeventhDay > 0 && seventhDayOTThreshold >=0) {
                               double otForSeventh = Math.min(hoursOnSeventhDay, (seventhDayOTThreshold == 0 && hoursOnSeventhDay > 0 ? hoursOnSeventhDay : seventhDayOTThreshold));
                               punchOtHours += otForSeventh;
                               hoursRemainingForWeeklyOtCalc -= otForSeventh;
                            }
                        }

                        if (weeklyOtEnabled && weeklyHoursForFLSA.getOrDefault(weekStartDate, 0.0) > ShowPayroll.WEEKLY_OT_THRESHOLD_FLSA) {
                            double weeklyOtPool = weeklyHoursForFLSA.get(weekStartDate) - ShowPayroll.WEEKLY_OT_THRESHOLD_FLSA;
                            if (weeklyOtPool > 0) {
                                double otFromWeekly = Math.min(hoursRemainingForWeeklyOtCalc, weeklyOtPool);
                                punchOtHours += otFromWeekly;
                                weeklyHoursForFLSA.put(weekStartDate, weeklyHoursForFLSA.get(weekStartDate) - otFromWeekly);
                            }
                        }
                    }
                    punchOtHours = Math.round(punchOtHours * 100.0) / 100.0;
                    punchDtHours = Math.round(punchDtHours * 100.0) / 100.0;

                    psUOt.setDouble(1, punchOtHours);
                    psUOt.setDouble(2, punchDtHours);
                    psUOt.setDouble(3, calculatedTotalHours);
                    psUOt.setLong(4, (Long) punch.get("PUNCH_ID"));
                    psUOt.setInt(5, tenantId);
                    psUOt.addBatch();
                }
            }
            psUOt.executeBatch();
        }

        return true;
    }

    private boolean isValidTimeZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.trim().isEmpty()) {
            return false;
        }
        try {
            ZoneId.of(timeZoneId.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidString(String str) {
        return str != null && !str.trim().isEmpty();
    }
}