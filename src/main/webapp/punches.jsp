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
        if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
            jspPunchesLogger.info("[PUNCHES_JSP_TZ] Using userTimeZoneId from session: " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
        }
    }

    if (!ShowPunches.isValid(userTimeZoneId) && tenantId != null && tenantId > 0) {
        String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone");
        if (ShowPunches.isValid(tenantDefaultTz)) {
            userTimeZoneId = tenantDefaultTz;
            jspPunchesLogger.info("[PUNCHES_JSP_TZ] Using Tenant DefaultTimeZone from SETTINGS: " + userTimeZoneId + " for Admin EID: " + adminEidForLog + ", Tenant: " + tenantId);
        } else {
            userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
            jspPunchesLogger.info("[PUNCHES_JSP_TZ] Tenant DefaultTimeZone not set/invalid in DB. Using application default for tenant: " + userTimeZoneId + " for Admin EID: " + adminEidForLog + ", Tenant: " + tenantId);
        }
    }

    if (!ShowPunches.isValid(userTimeZoneId)) {
        userTimeZoneId = PACIFIC_TIME_FALLBACK;
        jspPunchesLogger.warning("[PUNCHES_JSP_TZ] User/Tenant timezone not determined or invalid. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
    }

    try {
        ZoneId.of(userTimeZoneId);
    } catch (Exception e) {
        jspPunchesLogger.log(Level.SEVERE, "[PUNCHES_JSP_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Admin EID: " + adminEidForLog, e);
        userTimeZoneId = "UTC";
        String tzErrorMsg = "A critical error occurred with timezone configuration. Displaying times in UTC. Please contact support.";
        pageError = (pageError == null || pageError.isEmpty()) ? tzErrorMsg : pageError + " " + tzErrorMsg;
    }
    jspPunchesLogger.info("[PUNCHES_JSP_TZ] Punches.jsp final effective userTimeZoneId for display: " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
    
    StringBuilder punchTableHtmlBuilder = new StringBuilder();
    String employeeNameForDisplay = "N/A";
    double periodTotalHoursForDisplay = 0.0;
    double totalRegularHoursForDisplay = 0.0;
    double totalOvertimeHoursForDisplay = 0.0;
    double totalDoubleTimeHoursForDisplay = 0.0;
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

            // *** BUG FIX: Safely cast the 'hoursRequired' value to a Double ***
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
                            // The timeIn and timeOut values now come pre-wrapped with HTML from ShowPunches.java
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
                    periodTotalHoursForDisplay = Math.round((totalRegularHoursForDisplay + totalOvertimeHoursForDisplay + totalDoubleTimeHoursForDisplay) * 100.0) / 100.0;
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
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/punches.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reports-container">
        <h1><i class="fas fa-user-clock"></i> Edit Employee Punches</h1>

        <% if (pageError != null) { %><div id="pageNotificationDiv_Error_Punches" class="page-message error-message"><%= escapeHtml(pageError) %></div><% } %>
        <% if (pageMessage != null) { %><div id="pageNotificationDiv_Success_Punches" class="page-message success-message"><%= escapeHtml(pageMessage) %></div><% } %>

        <form method="GET" action="punches.jsp" id="filterPunchesForm" class="filter-form-punches">
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
            </div>
        <% } %>

        <h3 class="employee-punches-title">Punches for: <%= employeeNameForDisplay %></h3>
        
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
                            <td colspan="4" class="period-total-label">Period Total:</td>
                            <td colspan="2" class="period-total-value">
                                <%= String.format(Locale.US, "%.2f", periodTotalHoursForDisplay) %>
                                <% if(employeeInfo != null && "Hourly".equalsIgnoreCase((String)employeeInfo.getOrDefault("wageType",""))) { %>
                                    <span class="hours-breakdown-span">
                                         (Reg: <%= String.format(Locale.US, "%.2f", totalRegularHoursForDisplay) %>,
                                         OT: <%= String.format(Locale.US, "%.2f", totalOvertimeHoursForDisplay) %>
                                         <% if(totalDoubleTimeHoursForDisplay > 0.001) { %>
                                         , DT: <%= String.format(Locale.US, "%.2f", totalDoubleTimeHoursForDisplay) %>
                                         <% } %>)
                                    </span>
                                <% } %>
                            </td>
                        </tr>
                    </tfoot>
                    <% } %>
                </table>
            </div>
        </div>

        <% if (eidToLoad > 0) { %>
            <div id="button-container" class="main-action-buttons">
                <button type="button" id="addHoursBtn" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add Hours</button>
                <button type="button" id="addTimedPunchBtn" class="glossy-button text-blue"><i class="fas fa-clock"></i> Add Timed Punch</button>
                <button type="button" id="editRowBtn" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Row</button>
                <button type="button" id="deleteRowBtn" class="glossy-button text-red" disabled><i class="fas fa-trash-alt"></i> Delete Row</button>
            </div>
        <% } %>
    </div>

    <div id="addHoursModal" class="modal">
        <div class="modal-content">
            <span class="close close-modal-btn">&times;</span>
            <h2 id="addHoursModalTitle">Add Entry</h2>
            <form id="addHoursForm" action="AddEditAndDeletePunchesServlet" method="POST">
                <input type="hidden" name="action" value="addHours">
                <input type="hidden" name="eid" id="addHours_eidInput" value="<%= eidToLoad %>">
                <input type="hidden" name="userTimeZone" value="<%= escapeHtml(userTimeZoneId) %>">
                <fieldset class="form-section">
                    <legend>Date <span class="required-asterisk">*</span></legend>
                    <input type="date" name="punchDate" id="addHours_dateInput" required>
                </fieldset>
                <fieldset class="form-section" id="addHours_punchTypeContainer">
                    <legend>Type <span class="required-asterisk">*</span></legend>
                    <select name="punchType" id="addHours_typeSelect" required></select>
                </fieldset>
                <fieldset class="form-section" id="addHours_timeFieldsRowDiv" style="display:none;">
                    <div class="form-row" style="margin: 0;">
                        <div class="form-item"><label>Time In <span class="required-asterisk">*</span></label><input type="time" name="timeIn" id="addHours_timeInInput" step="1"></div>
                        <div class="form-item"><label>Time Out</label><input type="time" name="timeOut" id="addHours_timeOutInput" step="1"></div>
                    </div>
                </fieldset>
                <fieldset class="form-section" id="addHours_totalHoursDiv" style="display:none;">
                    <legend>Total Hours <span class="required-asterisk">*</span></legend>
                    <input type="number" name="totalHoursManual" id="addHours_totalHoursInput" step="0.01" min="0.01" max="24">
                </fieldset>
                <div class="button-row">
                    <button type="button" class="glossy-button text-red close-modal-btn">Cancel</button>
                    <button type="submit" class="glossy-button text-green"><span>Save</span></button>
                </div>
            </form>
        </div>
    </div>

    <div id="editPunchModal" class="modal">
        <div class="modal-content">
            <span class="close close-modal-btn">&times;</span>
            <h2>Edit Punch Record</h2>
            <form id="editPunchForm" action="AddEditAndDeletePunchesServlet" method="POST">
                <input type="hidden" name="action" value="editPunch">
                <input type="hidden" name="punchId" id="editPunch_idInput">
                <input type="hidden" name="editEmployeeId" id="editPunch_eidInput" value="<%= eidToLoad %>">
                <input type="hidden" name="userTimeZone" value="<%= escapeHtml(userTimeZoneId) %>">
                <fieldset class="form-section">
                    <legend>Date <span class="required-asterisk">*</span></legend>
                    <input type="date" name="editDate" id="editPunch_dateInput" required>
                </fieldset>
                <fieldset class="form-section" id="editPunch_timeFieldsRowDiv">
                    <div class="form-row" style="margin: 0;">
                        <div class="form-item"><label>Time In</label><input type="time" name="editInTime" id="editPunch_timeInInput" step="1"></div>
                        <div class="form-item"><label>Time Out</label><input type="time" name="editOutTime" id="editPunch_timeOutInput" step="1"></div>
                    </div>
                </fieldset>
                <fieldset class="form-section" id="editPunch_totalHoursDiv" style="display:none;">
                    <legend>Total Hours <span class="required-asterisk">*</span></legend>
                    <input type="number" name="totalHoursManual" id="editPunch_totalHoursInput" step="0.01" min="0.01" max="24">
                </fieldset>
                <fieldset class="form-section" id="editPunch_punchTypeContainer">
                    <legend>Punch Type</legend>
                    <select name="editPunchType" id="editPunch_typeSelect" required></select>
                </fieldset>
                <div class="button-row">
                    <button type="button" class="glossy-button text-red close-modal-btn">Cancel</button>
                    <button type="submit" class="glossy-button text-blue">Save Changes</button>
                </div>
            </form>
        </div>
    </div>
    
    <div id="confirmationModal" class="modal">
        <div class="modal-content">
            <h2 id="confirmationModalTitle">Confirm Action</h2>
            <p id="confirmationModalMessage" style="padding: 20px 25px; text-align: center; line-height: 1.5; margin: 0;"></p>
            <div class="button-row">
                <button type="button" id="cancelDeleteBtn" class="glossy-button text-grey">Cancel</button>
                <button type="button" id="confirmDeleteBtn" class="glossy-button text-red">Confirm</button>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>
    <script>
        window.SELECTED_EID_ON_LOAD = <%= eidToLoad %>;
        window.EFFECTIVE_TIME_ZONE_ID = "<%= escapeForJavaScriptString(userTimeZoneId) %>";
    </script>
    <script src="${pageContext.request.contextPath}/js/punches.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>