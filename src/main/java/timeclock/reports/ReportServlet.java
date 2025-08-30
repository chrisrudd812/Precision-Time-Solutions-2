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
import java.time.ZoneId; // Import ZoneId
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.Configuration;
import timeclock.punches.ShowPunches; // Import for ShowPunches.isValid

@WebServlet("/ReportServlet")
public class ReportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ReportServlet.class.getName());
    private static final String UTC_ZONE_ID_SERVLET = "UTC"; // Constant for UTC fallback

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
        String userTimeZoneId = null; // Will hold the determined timezone
        Integer sessionEidForLog = null;


        if (session != null) {
            Object tenantIdObjSession = session.getAttribute("TenantID");
            if (tenantIdObjSession instanceof Integer) {
                tenantIdObject = (Integer) tenantIdObjSession;
            }
            Object eidObj = session.getAttribute("EID");
             if (eidObj instanceof Integer) {
                sessionEidForLog = (Integer) eidObj;
            }

            // --- Standardized Timezone Logic For Servlet ---
            final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
            final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";

            Object userTimeZoneIdObj = session.getAttribute("userTimeZoneId");
            if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
                userTimeZoneId = (String) userTimeZoneIdObj;
                logger.info("[ReportServlet_TZ] Using userTimeZoneId from session: " + userTimeZoneId + " for EID: " + sessionEidForLog);
            }

            if (tenantIdObject != null && tenantIdObject > 0) { // Ensure tenantId is valid before using it for TZ fallback
                if (!ShowPunches.isValid(userTimeZoneId)) {
                    String tenantDefaultTz = Configuration.getProperty(tenantIdObject, "DefaultTimeZone");
                    if (ShowPunches.isValid(tenantDefaultTz)) {
                        userTimeZoneId = tenantDefaultTz;
                        logger.info("[ReportServlet_TZ] Using Tenant DefaultTimeZone from SETTINGS: " + userTimeZoneId + " for Tenant: " + tenantIdObject);
                    } else {
                        userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
                        logger.info("[ReportServlet_TZ] Tenant DefaultTimeZone not set/invalid. Using application default: " + userTimeZoneId + " for Tenant: " + tenantIdObject);
                    }
                }
            } else if (!ShowPunches.isValid(userTimeZoneId)){ // If tenantId is not valid, can't use tenant default
                 userTimeZoneId = PACIFIC_TIME_FALLBACK; // Go to system fallback if session and tenant based failed
                 logger.info("[ReportServlet_TZ] TenantId invalid, using system fallback (Pacific) " + userTimeZoneId);
            }


            if (!ShowPunches.isValid(userTimeZoneId)) { // Final system fallback if still not valid
                userTimeZoneId = PACIFIC_TIME_FALLBACK;
                logger.warning("[ReportServlet_TZ] User/Tenant timezone not determined. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for Tenant: " + tenantIdObject);
            }

            try {
                ZoneId.of(userTimeZoneId);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[ReportServlet_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Tenant: " + tenantIdObject, e);
                userTimeZoneId = UTC_ZONE_ID_SERVLET;
                // message below will reflect this if it causes an issue for a report
            }
            logger.info("[ReportServlet_TZ] Final effective userTimeZoneId for request: " + userTimeZoneId + " for Tenant: " + tenantIdObject);
            // --- End Standardized Timezone Logic ---

        }


        if (tenantIdObject == null || tenantIdObject.intValue() <= 0) {
            logger.log(Level.WARNING, "ReportServlet request for '" + reportType + "' failed: Missing or invalid TenantID in session.");
            writeJsonResponse(response, false, null, "Session expired or invalid tenant. Please log in.", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        // Ensure userTimeZoneId has a valid default if all else failed (e.g. session was null initially)
        if (!ShowPunches.isValid(userTimeZoneId)){
            userTimeZoneId = "America/Denver"; // Ultimate fallback if session was null and logic wasn't fully run
            logger.warning("[ReportServlet_TZ] userTimeZoneId was still invalid after all checks, defaulting to America/Denver. THIS SHOULD NOT HAPPEN.");
        }


        final int tenantId = tenantIdObject.intValue();

        if (filterValueParam != null && !filterValueParam.isEmpty()) {
            try {
                filterValue = URLDecoder.decode(filterValueParam, StandardCharsets.UTF_8.name());
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Failed to decode filterValue: " + filterValueParam, e);
            }
        }

        logger.info("ReportServlet received request for TenantID: " + tenantId + ", reportType: " + reportType + ", filterValue: " + filterValue + ", EffectiveTZ: " + userTimeZoneId);

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
                        LocalDate periodStartDate = null;
                        LocalDate periodEndDate = null;
                        try {
                            String startDateStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
                            String endDateStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");
                            if (ShowPunches.isValid(startDateStr) && ShowPunches.isValid(endDateStr)) {
                                periodStartDate = LocalDate.parse(startDateStr.trim());
                                periodEndDate = LocalDate.parse(endDateStr.trim());
                            } else {
                                message = "Pay period start/end dates not configured in settings.";
                                statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                                logger.warning(message + " For TenantID: " + tenantId);
                                reportHtml = "<tr><td colspan='6' class='report-error-row'>" + message + "</td></tr>";
                                success = false;
                            }
                        } catch (DateTimeParseException e) {
                            message = "Invalid date format for pay period in settings.";
                            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                            logger.log(Level.WARNING, message + " For TenantID: " + tenantId, e);
                            reportHtml = "<tr><td colspan='6' class='report-error-row'>" + message + "</td></tr>";
                            success = false;
                        } catch (Exception e) {
                            message = "Error retrieving pay period settings.";
                            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                            logger.log(Level.WARNING, message + " For TenantID: " + tenantId, e);
                            reportHtml = "<tr><td colspan='6' class='report-error-row'>" + message + "</td></tr>";
                            success = false;
                        }

                        if (periodStartDate != null && periodEndDate != null) {
                            reportHtml = ShowReports.showExceptionReport(tenantId, periodStartDate, periodEndDate, userTimeZoneId);
                            if ("NO_EXCEPTIONS".equals(reportHtml)) { message = "No exceptions found for the current pay period."; reportHtml = null; success = true;}
                            else if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error generating exception report."; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; success = false; }
                            else if (reportHtml != null) { success = true; message = "Exception report loaded."; }
                        } else if (success == false && message == null) {
                            message = "Could not load exception report due to missing pay period configuration.";
                             if (reportHtml == null) reportHtml = "<tr><td colspan='6' class='report-error-row'>" + message + "</td></tr>";
                        }
                        break;
                    case "tardy":
                        reportHtml = ShowReports.showTardyReport(tenantId);
                        if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No tardiness or early outs found."; reportHtml = null; success = true;}
                        else if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error generating tardy report."; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null) { success = true; message = "Tardy report loaded."; }
                        break;
                    case "whosin":
                        reportHtml = ShowReports.showWhosInReport(tenantId);
                        if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No employees currently clocked in."; reportHtml = null; success = true;}
                        else if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error generating who's in report."; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null) { success = true; message = "Who's In report loaded."; }
                        break;
                    case "activeEmployees":
                        reportHtml = ShowReports.showActiveEmployeesReport(tenantId);
                        if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error active employees report."; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No active employees."; reportHtml = null; success = true;}
                        else if (reportHtml != null) { success = true; message = "Active employees loaded.";}
                        break;
                    case "inactiveEmployees":
                        reportHtml = ShowReports.showInactiveEmployeesReport(tenantId);
                        if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error inactive employees report."; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No inactive employees."; reportHtml = null; success = true;}
                        else if (reportHtml != null) { success = true; message = "Inactive employees loaded.";}
                        break;
                    case "employeesByDept":
                        if(filterValue == null) { message = "Error: Department not specified."; statusCode = HttpServletResponse.SC_BAD_REQUEST; break; }
                        reportHtml = ShowReports.showEmployeesByDepartmentReport(tenantId, filterValue);
                        if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error report for department: " + filterValue; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No employees for department: " + filterValue; reportHtml = null; success = true;}
                        else if (reportHtml != null) { success = true; message = "Report for department '" + filterValue + "' loaded.";}
                        break;
                    case "employeesBySched":
                        if(filterValue == null) { message = "Error: Schedule not specified."; statusCode = HttpServletResponse.SC_BAD_REQUEST; break; }
                        reportHtml = ShowReports.showEmployeesByScheduleReport(tenantId, filterValue);
                        if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error report for schedule: " + filterValue; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No employees for schedule: " + filterValue; reportHtml = null; success = true;}
                        else if (reportHtml != null) { success = true; message = "Report for schedule '" + filterValue + "' loaded.";}
                        break;
                    case "employeesBySup":
                        if(filterValue == null) { message = "Error: Supervisor not specified."; statusCode = HttpServletResponse.SC_BAD_REQUEST; break; }
                        reportHtml = ShowReports.showEmployeesBySupervisorReport(tenantId, filterValue);
                        if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error report for supervisor: " + filterValue; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No employees for supervisor: " + filterValue; reportHtml = null; success = true;}
                        else if (reportHtml != null) { success = true; message = "Report for supervisor '" + filterValue + "' loaded.";}
                        break;
                    // --- NEW CASES ---
                    case "accrualBalance":
                        reportHtml = ShowReports.showAccrualBalanceReport(tenantId);
                        if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error generating Accrual Balance report."; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No employees found for accrual report."; reportHtml = null; success = true;}
                        else if (reportHtml != null) { success = true; message = "Accrual Balance report loaded.";}
                        break;
                    case "systemAccess":
                        reportHtml = ShowReports.showSystemAccessReport(tenantId);
                        if (reportHtml != null && reportHtml.contains("report-error-row")) { message = "Error generating System Access report."; reportHtml = null; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; }
                        else if (reportHtml != null && reportHtml.contains("report-message-row")) { message = "No administrators found."; reportHtml = null; success = true;}
                        else if (reportHtml != null) { success = true; message = "System Access report loaded.";}
                        break;
                    default:
                        message = "Error: Unknown reportType specified: " + reportType;
                        statusCode = HttpServletResponse.SC_BAD_REQUEST;
                        logger.warning(message + " For TenantID: " + tenantId);
                        break;
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error generating report: " + reportType + " for TenantID: " + tenantId, e);
            message = "An unexpected server error occurred while generating the report.";
            reportHtml = null; success = false; statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }
        writeJsonResponse(response, success, reportHtml, message, statusCode);
    }

    private void writeJsonResponse(HttpServletResponse response, boolean success, String html, String message, int statusCode) throws IOException {
        if (response.isCommitted()) { logger.warning("Response committed! Cannot send JSON for report: " + message); return; }
        response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); response.setStatus(statusCode);
        StringBuilder json = new StringBuilder("{");
        json.append("\"success\": ").append(success).append(",");
        json.append("\"html\": ");
        if (html != null) { json.append("\"").append(html.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t")).append("\""); }
        else { json.append("null"); }
        json.append(","); json.append("\"message\": ");
        if (message != null) { json.append("\"").append(message.replace("\\", "\\\\").replace("\"", "\\\"")).append("\""); }
        else { json.append("null"); }
        json.append("}");
        try (PrintWriter out = response.getWriter()) { out.print(json.toString()); out.flush(); logger.fine("Sent JSON Response. Success: " + success + (message != null ? " Message: " + message : "")); }
        catch (IllegalStateException e) { logger.log(Level.SEVERE, "Failed to get writer for JSON: " + json.toString(), e); }
    }
}
