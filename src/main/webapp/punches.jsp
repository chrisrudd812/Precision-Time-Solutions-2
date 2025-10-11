<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.punches.ShowPunches" %>
<%@ page import="timeclock.Configuration" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.TextStyle" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="timeclock.util.Helpers" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspPunchesLogger = Logger.getLogger("punches_jsp_v_final_tz_updated");

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
     private String escapeForJavaScriptString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("/", "\\/");
    }
%>
<%
    // Check if navbar should be hidden
    String hideNavParam = request.getParameter("hideNav");
    boolean hideNavbar = "true".equalsIgnoreCase(hideNavParam);
    
    // This entire block of Java scriptlet code remains unchanged.
    HttpSession punchesSession = request.getSession(false);
    Integer tenantId = null;
    String userPermissions = null;
    Integer adminEidForLog = null; 

    if (punchesSession != null) {
        Object tenantIdObj = punchesSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        Object permObj = punchesSession.getAttribute("Permissions");
        if (permObj instanceof String) { userPermissions = (String) permObj; }
        Object eidObj = punchesSession.getAttribute("EID");
        if (eidObj instanceof Integer) { adminEidForLog = (Integer) eidObj; }
    }

    if (tenantId == null || tenantId <= 0 || !"Administrator".equalsIgnoreCase(userPermissions)) {
        jspPunchesLogger.warning("Access Denied to punches.jsp. TenantID: " + tenantId + ", UserPermissions: " + userPermissions + ", AdminEID: " + adminEidForLog);
        if(punchesSession != null) punchesSession.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Access Denied. Administrator privileges required or session invalid.", StandardCharsets.UTF_8.name()));
        return;
    }

    String pageMessage = request.getParameter("message");
    String pageError = request.getParameter("error");

    int eidToLoad = 0;
    String eidParam = request.getParameter("eid");
    if (eidParam != null && !eidParam.isEmpty()) {
        try {
            eidToLoad = Integer.parseInt(eidParam.trim());
        } catch (NumberFormatException e) {
            String invalidEidMsg = "Invalid Employee ID in URL: '" + escapeHtml(eidParam) + "'.";
            pageError = (pageError == null || pageError.isEmpty()) ? invalidEidMsg : pageError + " " + invalidEidMsg;
            jspPunchesLogger.warning(invalidEidMsg + " TenantID: " + tenantId + ", AdminEID: " + adminEidForLog);
            eidToLoad = 0;
        }
    }

    List<Map<String, Object>> employeeList = new ArrayList<>();
    if (tenantId > 0) {
        employeeList = ShowPunches.getActiveEmployeesForDropdown(tenantId);
    } else {
        String tenantInvalidMsg = "System error: Tenant ID invalid. Cannot load employees.";
        pageError = (pageError == null || pageError.isEmpty()) ? tenantInvalidMsg : pageError + " " + tenantInvalidMsg;
        jspPunchesLogger.severe("Critical: TenantID was null or zero after permission check. TenantID: " + tenantId + ", AdminEID: " + adminEidForLog);
    }

    final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
    final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
    String userTimeZoneId = null;
    if (punchesSession != null) {
        Object userTimeZoneIdObj = punchesSession.getAttribute("userTimeZoneId");
        if (userTimeZoneIdObj instanceof String && Helpers.isStringValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
            jspPunchesLogger.info("[punches_JSP_TZ] Using userTimeZoneId from session: " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
        }
    }

    if (!Helpers.isStringValid(userTimeZoneId) && tenantId != null && tenantId > 0) {
        String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone");
        if (Helpers.isStringValid(tenantDefaultTz)) {
            userTimeZoneId = tenantDefaultTz;
            jspPunchesLogger.info("[punches_JSP_TZ] Using Tenant DefaultTimeZone from settings: " + userTimeZoneId + " for Admin EID: " + adminEidForLog + ", Tenant: " + tenantId);
        } else {
            userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
            jspPunchesLogger.info("[punches_JSP_TZ] Tenant DefaultTimeZone not set/invalid in DB. Using application default for tenant: " + userTimeZoneId + " for Admin EID: " + adminEidForLog + ", Tenant: " + tenantId);
        }
    }

    if (!Helpers.isStringValid(userTimeZoneId)) {
        userTimeZoneId = PACIFIC_TIME_FALLBACK;
        jspPunchesLogger.warning("[punches_JSP_TZ] User/Tenant timezone not determined or invalid. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
    }

    try {
        ZoneId.of(userTimeZoneId);
    } catch (Exception e) {
        jspPunchesLogger.log(Level.SEVERE, "[punches_JSP_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Admin EID: " + adminEidForLog, e);
        userTimeZoneId = "UTC";
        String tzErrorMsg = "A critical error occurred with timezone configuration. Displaying times in UTC. Please contact support.";
        pageError = (pageError == null || pageError.isEmpty()) ? tzErrorMsg : pageError + " " + tzErrorMsg;
    }
    jspPunchesLogger.info("[punches_JSP_TZ] Punches.jsp final effective userTimeZoneId for display: " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
    StringBuilder punchTableHtmlBuilder = new StringBuilder();
    String employeeNameForDisplay = "N/A";
    double periodTotalHoursForDisplay = 0.0;
    double totalRegularHoursForDisplay = 0.0;
    double totalOvertimeHoursForDisplay = 0.0;
    double totalDoubleTimeHoursForDisplay = 0.0;
    double totalHolidayOvertimeHoursForDisplay = 0.0;
    double totalDaysOffOvertimeHoursForDisplay = 0.0;
    String payPeriodDisplay = "Pay Period Not Configured";
    LocalDate currentPayPeriodStartDate = null;
    LocalDate currentPayPeriodEndDate = null;
    DateTimeFormatter friendlyDateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    String scheduleNameForDisplay = "N/A";
    Boolean autoLunchStatus = null;
    Integer lunchLengthForDisplay = null;
    Double hrsRequiredForLunchTrigger = null;
    Map<String, Object> employeeInfo = null;

    if ((pageError == null || pageError.isEmpty()) && tenantId > 0) { 
        try {
            Map<String, LocalDate> currentPeriod = ShowPunches.getCurrentPayPeriodInfo(tenantId);
            if (currentPeriod != null && currentPeriod.get("startDate") != null && currentPeriod.get("endDate") != null) {
                currentPayPeriodStartDate = currentPeriod.get("startDate");
                currentPayPeriodEndDate = currentPeriod.get("endDate");
                payPeriodDisplay = currentPayPeriodStartDate.format(friendlyDateFormatter) + " - " + currentPayPeriodEndDate.format(friendlyDateFormatter);
            } else {
                String ppConfigError = "Current pay period is not configured. Please set it in Company Settings.";
                pageError = (pageError == null || pageError.isEmpty()) ? ppConfigError : pageError + " " + ppConfigError;
            }
        } catch (Exception e) {
             jspPunchesLogger.log(java.util.logging.Level.SEVERE, "Error getting current pay period for TenantID: " + tenantId + ", AdminEID: " + adminEidForLog, e);
             String ppLoadError = "Error loading current pay period information.";
             pageError = (pageError == null || pageError.isEmpty()) ? ppLoadError : pageError + " " + ppLoadError;
        }
    }

    if (eidToLoad > 0 && currentPayPeriodStartDate != null && currentPayPeriodEndDate != null && (pageError == null || pageError.isEmpty())) {
        employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId, eidToLoad);
        if (employeeInfo != null) {
            employeeNameForDisplay = escapeHtml((String) employeeInfo.getOrDefault("employeeName", "N/A"));
            scheduleNameForDisplay = escapeHtml((String) employeeInfo.getOrDefault("scheduleName", "N/A"));
            if (employeeInfo.get("autoLunch") != null) autoLunchStatus = (Boolean) employeeInfo.get("autoLunch");
            if (employeeInfo.get("lunchLength") != null) lunchLengthForDisplay = (Integer) employeeInfo.get("lunchLength");

            if (employeeInfo.get("hoursRequired") != null) {
                Object hrsReqObj = employeeInfo.get("hoursRequired");
                if (hrsReqObj instanceof Number) {
                    hrsRequiredForLunchTrigger = ((Number) hrsReqObj).doubleValue();
                }
            }

            Map<String, Object> timecardData = ShowPunches.getTimecardPunchData(tenantId, eidToLoad, currentPayPeriodStartDate, currentPayPeriodEndDate, employeeInfo, userTimeZoneId);
            if (timecardData != null) {
                String dataError = (String) timecardData.get("error");
                if (dataError != null) {
                     pageError = (pageError == null || pageError.isEmpty()) ? dataError : pageError + " " + dataError;
                     punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-error-row'>").append(escapeHtml(dataError)).append("</td></tr>");
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> punchesList = (List<Map<String, String>>) timecardData.get("punches");
                    if (punchesList != null && !punchesList.isEmpty()) {
                        for (Map<String, String> punch : punchesList) {
                            punchTableHtmlBuilder.append("<tr data-punch-id=\"").append(escapeHtml(punch.get("punchId"))).append("\" data-date=\"").append(escapeHtml(punch.get("punchDate"))).append("\" data-timein=\"").append(escapeHtml(punch.get("timeInRaw"))).append("\" data-timeout=\"").append(escapeHtml(punch.get("timeOutRaw"))).append("\" data-totalhours=\"").append(escapeHtml(punch.get("totalHours"))).append("\" data-type=\"").append(escapeHtml(punch.get("punchType"))).append("\" data-eid=\"").append(eidToLoad).append("\">");
                            punchTableHtmlBuilder.append("<td>").append(escapeHtml(punch.get("friendlyPunchDate"))).append("</td>");
                            punchTableHtmlBuilder.append("<td>").append(escapeHtml(punch.get("dayOfWeek"))).append("</td>");
                            punchTableHtmlBuilder.append("<td>").append(punch.get("timeIn")).append("</td>");
                            punchTableHtmlBuilder.append("<td>").append(punch.get("timeOut")).append("</td>");
                            punchTableHtmlBuilder.append("<td style=\"text-align:right;\">").append(escapeHtml(punch.get("totalHours"))).append("</td>");
                            punchTableHtmlBuilder.append("<td>").append(escapeHtml(punch.get("punchType"))).append("</td>");
                            punchTableHtmlBuilder.append("</tr>\n");
                        }
                    } else {
                        punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-message-row'>No punches for this employee in the current pay period.</td></tr>");
                    }
                    totalRegularHoursForDisplay = (Double) timecardData.getOrDefault("totalRegularHours", 0.0);
                    totalOvertimeHoursForDisplay = (Double) timecardData.getOrDefault("totalOvertimeHours", 0.0);
                    totalDoubleTimeHoursForDisplay = (Double) timecardData.getOrDefault("totalDoubleTimeHours", 0.0);
                    totalHolidayOvertimeHoursForDisplay = (Double) timecardData.getOrDefault("totalHolidayOvertimeHours", 0.0);
                    totalDaysOffOvertimeHoursForDisplay = (Double) timecardData.getOrDefault("totalDaysOffOvertimeHours", 0.0);
                    periodTotalHoursForDisplay = Math.round((totalRegularHoursForDisplay + totalOvertimeHoursForDisplay + totalDoubleTimeHoursForDisplay + totalHolidayOvertimeHoursForDisplay + totalDaysOffOvertimeHoursForDisplay) * 100.0) / 100.0;
                }
            } else {
                pageError = "Error retrieving timecard data.";
                punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-error-row'>Error retrieving timecard data.</td></tr>");
            }
        } else {
             pageError = "Employee (EID: " + eidToLoad + ") not found.";
             punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-error-row'>Employee not found.</td></tr>");
            employeeNameForDisplay = "Employee Not Found";
        }
    } else if (eidToLoad <= 0 && (pageError == null || pageError.isEmpty())) {
        punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-message-row'>Please select an employee to view their punches.</td></tr>");
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Edit Employee Punches</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <%-- ADDED link to modals.css --%>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/modals.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/punches.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="reports-page<%= hideNavbar ? " no-navbar" : "" %>">
    <% if (!hideNavbar) { %>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="parent-container reports-container">
        <h1><i class="fas fa-user-clock"></i> Edit Employee Punches</h1>

        <% if (pageError != null) { %><div id="pageNotificationDiv_Error_Punches" class="page-message error-message"><%= escapeHtml(pageError) %></div><% } %>
        <% if (pageMessage != null) { %><div id="pageNotificationDiv_Success_Punches" class="page-message success-message"><%= escapeHtml(pageMessage) %></div><% } %>

        <form method="GET" action="punches.jsp" id="filterPunchesForm" class="filter-form-punches">
            <% if (hideNavbar) { %><input type="hidden" name="hideNav" value="true"><% } %>
            <div class="form-item">
                <label for="employeeSelectPunches">Employee:</label>
                <select name="eid" id="employeeSelectPunches" required onchange="this.form.submit()">
                    <option value="">-- Select Employee --</option>
                    <% for (Map<String, Object> emp : employeeList) { %>
                        <option value="<%= emp.get("eid") %>" <%= ((Integer)emp.get("eid")).intValue() == eidToLoad ? "selected" : "" %>>
                            <%= escapeHtml((String)emp.get("displayName")) %>
                        </option>
                    <% } %>
                </select>
            </div>
            <div class="pay-period-display">Pay Period: <%= escapeHtml(payPeriodDisplay) %></div>
        </form>

        <% if (eidToLoad > 0 && employeeInfo != null) { %>
            <div class="employee-schedule-info-bar">
                <span>Wage Type: <strong><%= escapeHtml((String) employeeInfo.getOrDefault("wageType", "N/A")) %></strong></span>
                <span>Schedule: <strong><%= scheduleNameForDisplay %></strong></span>
                <% if (autoLunchStatus != null) { %>
                    <span>Auto-Lunch: <strong class="<%= autoLunchStatus ? "text-success-dark" : "text-danger-dark" %>"><%= autoLunchStatus ? "Enabled" : "Disabled" %></strong></span>
                    <% if (autoLunchStatus && lunchLengthForDisplay != null && lunchLengthForDisplay > 0) { %>
                        <span>Lunch: <strong><%= lunchLengthForDisplay %> mins</strong></span>
                    <% } %>
                    <% if (autoLunchStatus && hrsRequiredForLunchTrigger != null && hrsRequiredForLunchTrigger > 0) { %>
                         <span>(Threshold: <strong><%= String.format(Locale.US, "%.2f", hrsRequiredForLunchTrigger) %> hrs</strong>)</span>
                    <% } %>
                <% } %>
                <span class="instruction-text">💡 Click on a table row to edit that punch record</span>
            </div>
        <% } %>

        <h3 class="employee-punches-title">Showing Punches For: <%= employeeNameForDisplay %></h3>
        
        <div class="report-display-area">
            <div class="table-container report-table-container punches-table-container">
                <table id="punchesTable" class="report-table">
                    <thead>
                         <tr>
                            <th class="sortable">Date</th>
                            <th class="sortable">Day</th>
                            <th class="sortable">Time In</th>
                            <th class="sortable">Time Out</th>
                            <th class="sortable">Total Hours</th>
                            <th class="sortable">Type</th>
                         </tr>
                    </thead>
                    <tbody>
                        <%= punchTableHtmlBuilder.toString() %>
                    </tbody>
                    <% if (eidToLoad > 0 && periodTotalHoursForDisplay > 0.001) { %>
                    <tfoot>
                        <tr class="footer-summary-row">
                            <td colspan="5" style="text-align: right; padding-right: 9px;">Period Total: &nbsp;&nbsp;&nbsp;<%= String.format(Locale.US, "%.2f", periodTotalHoursForDisplay) %>
                                <% if(employeeInfo != null && "Hourly".equalsIgnoreCase((String)employeeInfo.getOrDefault("wageType",""))) { %></td>
                            <td>
                                    <span class="hours-breakdown-span">
                                         (Reg: <%= String.format(Locale.US, "%.2f", totalRegularHoursForDisplay) %>,
                                         OT: <%= String.format(Locale.US, "%.2f", totalOvertimeHoursForDisplay) %>
                                         <% if(totalHolidayOvertimeHoursForDisplay > 0.001) { %>
                                         , Holiday OT: <%= String.format(Locale.US, "%.2f", totalHolidayOvertimeHoursForDisplay) %>
                                         <% } %>
                                         <% if(totalDaysOffOvertimeHoursForDisplay > 0.001) { %>
                                         , Days Off OT: <%= String.format(Locale.US, "%.2f", totalDaysOffOvertimeHoursForDisplay) %>
                                         <% } %>
                                         <% if(totalDoubleTimeHoursForDisplay > 0.001) { %>
                                         , DT: <%= String.format(Locale.US, "%.2f", totalDoubleTimeHoursForDisplay) %>
                                         <% } %>)
                                    </span>
                                <% } else { %></td>
                            <td></td>
                                <% } %>
                        </tr>
                    </tfoot>
                    <% } %>
                </table>
            </div>
        </div>

        <% if (eidToLoad > 0) { %>
            <div id="button-container" class="main-action-buttons">
                <button type="button" id="addHoursBtn" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add PTO</button>
                <button type="button" id="addTimedPunchBtn" class="glossy-button text-blue"><i class="fas fa-clock"></i> Add Punch</button>
                <button type="button" id="editRowBtn" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Row</button>
                <button type="button" id="deleteRowBtn" class="glossy-button text-red" disabled><i class="fas fa-trash-alt"></i> Delete Row</button>
            </div>
        <% } %>
    </div>

    <%-- MODIFIED: Removed inline modals and added new includes --%>
    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    <%@ include file="/WEB-INF/includes/punches-modals.jspf" %>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    
    <script>
        window.SELECTED_EID_ON_LOAD = <%= eidToLoad %>;
        window.EFFECTIVE_TIME_ZONE_ID = "<%= escapeForJavaScriptString(userTimeZoneId) %>";
        <% if (currentPayPeriodStartDate != null && currentPayPeriodEndDate != null) { %>
        window.PAY_PERIOD_START = "<%= currentPayPeriodStartDate.toString() %>";
        window.PAY_PERIOD_END = "<%= currentPayPeriodEndDate.toString() %>";
        <% } %>
    </script>
    <script src="${pageContext.request.contextPath}/js/punches.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>