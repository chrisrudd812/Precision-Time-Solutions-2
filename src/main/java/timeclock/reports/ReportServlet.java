package timeclock.reports;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.Configuration;
import timeclock.punches.ShowPunches;
import timeclock.util.Helpers;

@WebServlet("/ReportServlet")
public class ReportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ReportServlet.class.getName());
    private static final String UTC_ZONE_ID_SERVLET = "UTC";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String reportType = request.getParameter("reportType");
        String filterValueParam = request.getParameter("filterValue");
        String filterValue = null;

        HttpSession session = request.getSession(false);
        Integer tenantIdObject = null;
        String userTimeZoneId = null;
        if (session != null) {
            Object tenantIdObjSession = session.getAttribute("TenantID");
            if (tenantIdObjSession instanceof Integer) {
                tenantIdObject = (Integer) tenantIdObjSession;
            }

            final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
            final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";

            Object userTimeZoneIdObj = session.getAttribute("userTimeZoneId");
            if (userTimeZoneIdObj instanceof String && Helpers.isStringValid((String)userTimeZoneIdObj)) {
                userTimeZoneId = (String) userTimeZoneIdObj;
            }

            if (tenantIdObject != null && tenantIdObject > 0) {
                if (!Helpers.isStringValid(userTimeZoneId)) {
                    String tenantDefaultTz = Configuration.getProperty(tenantIdObject, "DefaultTimeZone");
                    if (Helpers.isStringValid(tenantDefaultTz)) {
                        userTimeZoneId = tenantDefaultTz;
                    } else {
                        userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
                    }
                }
            } else if (!Helpers.isStringValid(userTimeZoneId)){
                 userTimeZoneId = PACIFIC_TIME_FALLBACK;
            }


            if (!Helpers.isStringValid(userTimeZoneId)) {
                userTimeZoneId = PACIFIC_TIME_FALLBACK;
            }

            try {
                ZoneId.of(userTimeZoneId);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ReportServlet_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Tenant: " + tenantIdObject, e);
                userTimeZoneId = UTC_ZONE_ID_SERVLET;
            }
        }


        if (tenantIdObject == null || tenantIdObject.intValue() <= 0) {
            logger.log(Level.WARNING, "ReportServlet request for '" + reportType + "' failed: Missing or invalid TenantID in session.");
            writeJsonResponse(response, false, null, "Session expired or invalid tenant. Please log in.", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if (!Helpers.isStringValid(userTimeZoneId)){
            userTimeZoneId = "America/Denver";
        }


        final int tenantId = tenantIdObject.intValue();

        if (filterValueParam != null && !filterValueParam.isEmpty()) {
            try {
                filterValue = URLDecoder.decode(filterValueParam, StandardCharsets.UTF_8.name());
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Failed to decode filterValue: " + filterValueParam, e);
            }
        }



        String reportHtml = null;
        String message = null;
        boolean success = false;
        int statusCode = HttpServletResponse.SC_OK;

        try {
            if (reportType == null || reportType.trim().isEmpty()) {
                message = "Error: reportType parameter is missing.";
                statusCode = HttpServletResponse.SC_BAD_REQUEST;
                logger.warning(message + " For TenantID: " + tenantId);
            } else {
                switch (reportType) {
                    case "exception":
                        LocalDate periodStartDateEx = null, periodEndDateEx = null;
                        try {
                            String startStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
                            String endStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");
                            if (Helpers.isStringValid(startStr) && Helpers.isStringValid(endStr)) {
                                periodStartDateEx = LocalDate.parse(startStr.trim());
                                periodEndDateEx = LocalDate.parse(endStr.trim());
                            } else { message = "Pay period dates not configured in settings."; }
                        } catch (Exception e) { message = "Error retrieving pay period settings."; }
                        
                        if (periodStartDateEx != null && periodEndDateEx != null) {
                            reportHtml = ShowReports.showExceptionReport(tenantId, periodStartDateEx, periodEndDateEx, userTimeZoneId);
                            if ("NO_EXCEPTIONS".equals(reportHtml)) { message = "No exceptions found."; reportHtml = null; success = true;}
                            else if (reportHtml != null && !reportHtml.contains("report-error-row")) { success = true; message = "Exception report loaded."; }
                            else { message = "Error generating exception report.";}
                        } else { statusCode = HttpServletResponse.SC_BAD_REQUEST; }
                        break;
                    case "tardy":
                        // [FIX] Reverted to using simple LocalDate to match the user's provided query logic
                        LocalDate tardyStartDate = null, tardyEndDate = null;
                        String dateFilter = (filterValue != null) ? filterValue : "all";
                        try {
                            ZoneId tz = ZoneId.of(userTimeZoneId);
                            LocalDate today = LocalDate.now(tz);
                            if ("period".equals(dateFilter)) {
                                String startStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
                                String endStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");
                                tardyStartDate = LocalDate.parse(startStr.trim());
                                tardyEndDate = LocalDate.parse(endStr.trim());
                            } else if ("ytd".equals(dateFilter)) {
                                tardyStartDate = today.with(TemporalAdjusters.firstDayOfYear());
                                tardyEndDate = today;
                            }
                        } catch (Exception e) { message = "Error calculating date range."; logger.log(Level.SEVERE, "Error parsing tardy date range", e); }
                        
                        // Pass the simple dates to the new report method
                        reportHtml = ShowReports.showTardyReport(tenantId, tardyStartDate, tardyEndDate);
                        if (reportHtml.contains("report-message-row")) { message = "No tardiness or early outs found for the selected period."; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "Tardy report loaded."; }
                        else { message = "Error generating tardy report."; }
                        break;
                    case "archivedPunches":
                        try {
                            String startDateStr = request.getParameter("startDate");
                            String endDateStr = request.getParameter("endDate");
                            if (!Helpers.isStringValid(startDateStr) || !Helpers.isStringValid(endDateStr)) {
                                message = "Start and End dates are required.";
                                statusCode = HttpServletResponse.SC_BAD_REQUEST;
                            } else {
                                LocalDate startDate = LocalDate.parse(startDateStr);
                                LocalDate endDate = LocalDate.parse(endDateStr);
                                reportHtml = ShowReports.showArchivedPunchesReport(tenantId, 0, userTimeZoneId, startDate, endDate);
                                if (reportHtml.contains("report-message-row")) {
                                    message = "No archived punches found for the selected date range.";
                                    reportHtml = null;
                                    success = true;
                                } else if (!reportHtml.contains("report-error-row")) {
                                    success = true;
                                    message = "Archived punches report loaded.";
                                } else {
                                    message = "An error occurred while generating the report.";
                                    statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                                }
                            }
                        } catch (DateTimeParseException e) {
                            message = "Invalid date format provided.";
                            statusCode = HttpServletResponse.SC_BAD_REQUEST;
                        }
                        break;
                    case "whosin":
                        reportHtml = ShowReports.showWhosInReport(tenantId, userTimeZoneId);
                        if (reportHtml.contains("report-message-row")) { message = "No employees currently clocked in."; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "Who's In report loaded."; }
                        else { message = "Error generating who's in report."; }
                        break;
                    case "activeEmployees":
                        reportHtml = ShowReports.showActiveEmployeesReport(tenantId);
                         if (reportHtml.contains("report-message-row")) { message = "No active employees found."; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "Active employees loaded.";}
                        else { message = "Error generating active employees report.";}
                        break;
                    case "inactiveEmployees":
                        reportHtml = ShowReports.showInactiveEmployeesReport(tenantId);
                        if (reportHtml.contains("report-message-row")) { message = "No inactive employees found."; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "Inactive employees loaded.";}
                        else { message = "Error generating inactive employees report.";}
                        break;
                    case "employeesByDept":
                        if(filterValue == null) { message = "Error: Department not specified."; statusCode = HttpServletResponse.SC_BAD_REQUEST; break; }
                        reportHtml = ShowReports.showEmployeesByDepartmentReport(tenantId, filterValue);
                        if (reportHtml.contains("report-message-row")) { message = "No employees found for department: " + filterValue; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "Report for department '" + filterValue + "' loaded.";}
                        else { message = "Error generating report for department: " + filterValue; }
                        break;
                    case "employeesBySched":
                        if(filterValue == null) { message = "Error: Schedule not specified."; statusCode = HttpServletResponse.SC_BAD_REQUEST; break; }
                        reportHtml = ShowReports.showEmployeesByScheduleReport(tenantId, filterValue);
                        if (reportHtml.contains("report-message-row")) { message = "No employees found for schedule: " + filterValue; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "Report for schedule '" + filterValue + "' loaded.";}
                        else { message = "Error generating report for schedule: " + filterValue;}
                        break;
                    case "employeesBySup":
                        if(filterValue == null) { message = "Error: Supervisor not specified."; statusCode = HttpServletResponse.SC_BAD_REQUEST; break; }
                        reportHtml = ShowReports.showEmployeesBySupervisorReport(tenantId, filterValue);
                        if (reportHtml.contains("report-message-row")) { message = "No employees found for supervisor: " + filterValue; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "Report for supervisor '" + filterValue + "' loaded.";}
                        else { message = "Error generating report for supervisor: " + filterValue;}
                        break;
                    case "accrualBalance":
                        reportHtml = ShowReports.showAccrualBalanceReport(tenantId);
                        if (reportHtml.contains("report-message-row")) { message = "No employees found for accrual report."; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "PTO Balance report loaded.";}
                        else { message = "Error generating PTO Balance report.";}
                        break;
                    case "systemAccess":
                        reportHtml = ShowReports.showSystemAccessReport(tenantId);
                        if (reportHtml.contains("report-message-row")) { message = "No administrators found."; reportHtml = null; success = true;}
                        else if (!reportHtml.contains("report-error-row")) { success = true; message = "System Access report loaded.";}
                        else { message = "Error generating System Access report.";}
                        break;
                    default:
                        message = "Error: Unknown reportType specified: " + reportType;
                        statusCode = HttpServletResponse.SC_BAD_REQUEST;
                        logger.warning(message + " For TenantID: " + tenantId);
                        break;
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in ReportServlet for TenantID: " + tenantId, e);
            message = "An unexpected server error occurred.";
            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        writeJsonResponse(response, success, reportHtml, message, statusCode);
    }
    
    private void writeJsonResponse(HttpServletResponse response, boolean success, String html, String message, int statusCode) throws IOException {
        if (response.isCommitted()) { return; }
        response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); response.setStatus(statusCode);
        PrintWriter out = response.getWriter();
        out.print("{\"success\":" + success + ",");
        out.print("\"html\":" + (html == null ? "null" : "\"" + html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"") + ",");
        out.print("\"message\":" + (message == null ? "null" : "\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"") + "}");
        out.flush();
    }
}