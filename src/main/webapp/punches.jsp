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
    Integer adminEidForLog = null; // Used for logging, equivalent to sessionEid_timeclock

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
    String pageError = request.getParameter("error"); // Keep existing pageError handling

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

    // --- Timezone Logic for Punches Page (Aligned with timeclock.jsp) ---
    final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles"; // Consistent name
    final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver"; // Consistent name
    String userTimeZoneId = null; // Consistent variable name

    // 1. Attempt to get user-specific timezone from session (set at login)
    if (punchesSession != null) {
        Object userTimeZoneIdObj = punchesSession.getAttribute("userTimeZoneId");
        if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
            jspPunchesLogger.info("[PUNCHES_JSP_TZ] Using userTimeZoneId from session: " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
        }
    }

    // 2. If not in session (or invalid), try Tenant's DefaultTimeZone, then app's tenant fallback
    if (!ShowPunches.isValid(userTimeZoneId) && tenantId != null && tenantId > 0) {
        String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone"); // Get without internal fallback first
        if (ShowPunches.isValid(tenantDefaultTz)) {
            userTimeZoneId = tenantDefaultTz;
            jspPunchesLogger.info("[PUNCHES_JSP_TZ] Using Tenant DefaultTimeZone from SETTINGS: " + userTimeZoneId + " for Admin EID: " + adminEidForLog + ", Tenant: " + tenantId);
        } else {
            // If tenant default is not set in DB or was invalid, use the application's defined default for tenants
            userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
            jspPunchesLogger.info("[PUNCHES_JSP_TZ] Tenant DefaultTimeZone not set/invalid in DB. Using application default for tenant: " + userTimeZoneId + " for Admin EID: " + adminEidForLog + ", Tenant: " + tenantId);
        }
    }

    // 3. If still not found or invalid, use the ultimate Pacific Time fallback
    if (!ShowPunches.isValid(userTimeZoneId)) {
        userTimeZoneId = PACIFIC_TIME_FALLBACK;
        jspPunchesLogger.warning("[PUNCHES_JSP_TZ] User/Tenant timezone not determined or invalid. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
    }

    // 4. Validate the determined ZoneId to prevent errors in formatting downstream
    try {
        ZoneId.of(userTimeZoneId); // Validate the final determined ZoneId
    } catch (Exception e) {
        jspPunchesLogger.log(Level.SEVERE, "[PUNCHES_JSP_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Admin EID: " + adminEidForLog, e);
        userTimeZoneId = "UTC";
        String tzErrorMsg = "A critical error occurred with timezone configuration. Displaying times in UTC. Please contact support.";
        // Consistent error appending
        pageError = (pageError == null || pageError.isEmpty()) ? tzErrorMsg : pageError + " " + tzErrorMsg;
    }
    jspPunchesLogger.info("[PUNCHES_JSP_TZ] Punches.jsp final effective userTimeZoneId for display: " + userTimeZoneId + " for Admin EID: " + adminEidForLog);
    // --- End Timezone Logic ---

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
    DateTimeFormatter isoDateFormatter = DateTimeFormatter.ISO_LOCAL_DATE;

    String scheduleNameForDisplay = "N/A";
    Boolean autoLunchStatus = null;
    Integer lunchLengthForDisplay = null;
    Double hrsRequiredForLunchTrigger = null;
    Map<String, Object> employeeInfo = null;


    if ((pageError == null || pageError.isEmpty()) && tenantId > 0) { // Check pageError before DB calls
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
            if (employeeInfo.get("hoursRequired") != null) hrsRequiredForLunchTrigger = (Double) employeeInfo.get("hoursRequired");

            // Use the updated userTimeZoneId
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
                            punchTableHtmlBuilder.append("<tr data-punch-id=\"").append(escapeHtml(punch.get("punchId"))).append("\" ")
                                .append("data-date=\"").append(escapeHtml(punch.get("punchDate"))).append("\" ")
                                .append("data-timein=\"").append(escapeHtml(punch.get("timeInRaw"))).append("\" ") // Assuming timeInRaw and timeOutRaw are for JS data attributes
                                .append("data-timeout=\"").append(escapeHtml(punch.get("timeOutRaw"))).append("\" ")
                                .append("data-totalhours=\"").append(escapeHtml(punch.get("totalHours"))).append("\" ")
                                .append("data-type=\"").append(escapeHtml(punch.get("punchType"))).append("\" ")
                                .append("data-eid=\"").append(eidToLoad).append("\">");
                            punchTableHtmlBuilder.append("<td>").append(escapeHtml(punch.get("friendlyPunchDate"))).append("</td>");
                            punchTableHtmlBuilder.append("<td>").append(escapeHtml(punch.get("dayOfWeek"))).append("</td>");
                            String timeIn = punch.get("timeIn"); String inTimeCssClass = punch.get("inTimeCssClass");
                            punchTableHtmlBuilder.append("<td>");
                            if (inTimeCssClass != null && !inTimeCssClass.isEmpty()) punchTableHtmlBuilder.append("<span class=\"").append(escapeHtml(inTimeCssClass)).append("\">");
                            punchTableHtmlBuilder.append(escapeHtml(timeIn));
                            if (inTimeCssClass != null && !inTimeCssClass.isEmpty()) punchTableHtmlBuilder.append("</span>");
                            punchTableHtmlBuilder.append("</td>");
                            String timeOut = punch.get("timeOut"); String outTimeCssClass = punch.get("outTimeCssClass");
                            punchTableHtmlBuilder.append("<td>");
                            if (outTimeCssClass != null && !outTimeCssClass.isEmpty()) punchTableHtmlBuilder.append("<span class=\"").append(escapeHtml(outTimeCssClass)).append("\">");
                            punchTableHtmlBuilder.append(escapeHtml(timeOut));
                            if (outTimeCssClass != null && !outTimeCssClass.isEmpty()) punchTableHtmlBuilder.append("</span>");
                            punchTableHtmlBuilder.append("</td>");
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
                String tcDataErrorMsg = "Error retrieving timecard data.";
                pageError = (pageError == null || pageError.isEmpty()) ? tcDataErrorMsg : pageError + " " + tcDataErrorMsg;
                punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-error-row'>").append(escapeHtml(tcDataErrorMsg)).append("</td></tr>");
            }
        } else {
             String empNotFoundMsg = "Employee (EID: " + eidToLoad + ") not found.";
             pageError = (pageError == null || pageError.isEmpty()) ? empNotFoundMsg : pageError + " " + empNotFoundMsg;
            punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-error-row'>").append(escapeHtml(empNotFoundMsg)).append("</td></tr>");
            employeeNameForDisplay = "Employee Not Found";
        }
    } else if (eidToLoad <= 0 && (pageError == null || pageError.isEmpty())) {
        punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-message-row'>Please select an employee to view their punches.</td></tr>");
    }

    if (pageError != null && !pageError.isEmpty() && punchTableHtmlBuilder.indexOf("report-error-row") == -1 && punchTableHtmlBuilder.indexOf("report-message-row") == -1 ){
        punchTableHtmlBuilder.setLength(0);
        punchTableHtmlBuilder.append("<tr><td colspan='6' class='report-error-row'>").append(escapeHtml(pageError)).append("</td></tr>");
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
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container">
        <h1><i class="fas fa-user-clock"></i> Edit Employee Punches</h1>

         <%-- Display pageError if it exists and is not empty --%>
        <% if (pageError != null && !pageError.isEmpty()) { %>
            <div id="notification-bar-punches" class="error-message" style="margin-bottom: 15px; display:block;"><%= escapeHtml(pageError) %></div>
        <% } else if (pageMessage != null && !pageMessage.isEmpty()) { %>
            <div id="notification-bar-punches" class="success-message" style="margin-bottom: 15px; display:block;"><%= escapeHtml(pageMessage) %></div>
        <% } %>


        <form method="GET" action="punches.jsp" id="filterPunchesForm" class="filter-form-punches">
            <div class="form-item">
                <label for="employeeSelectPunches">Employee:</label>
                <select name="eid" id="employeeSelectPunches" required onchange="this.form.submit()">
                    <option value="0">-- Select Employee --</option>
                    <% if (employeeList != null) { for (Map<String, Object> emp : employeeList) { %>
                        <option value="<%= emp.get("eid") %>" <%= ((Integer)emp.get("eid")).intValue() == eidToLoad ? "selected" : "" %>>
                            <%= escapeHtml((String)emp.get("displayName")) %>
                        </option>
                    <% }} %>
                </select>
            </div>
            <div class="pay-period-display">Pay Period: <%= escapeHtml(payPeriodDisplay) %></div>
        </form>

        <% if (eidToLoad > 0 && employeeInfo != null && (pageError == null || pageError.isEmpty())) { %>
            <div class="employee-schedule-info-bar">
                <span>Schedule: <strong><%= escapeHtml(scheduleNameForDisplay == null || scheduleNameForDisplay.trim().isEmpty() ? "Default/None" : scheduleNameForDisplay) %></strong></span>
                <% if (autoLunchStatus != null) { %>
                    <span>Auto-Lunch: <strong class="<%= autoLunchStatus ? "text-success-dark" : "text-danger-dark" %>"><%= autoLunchStatus ? "Enabled" : "Disabled" %></strong></span>
                    <% if (autoLunchStatus && lunchLengthForDisplay != null && lunchLengthForDisplay > 0) { %>
                        <span>Lunch: <strong><%= lunchLengthForDisplay %> mins</strong></span>
                    <% } %>
                    <% if (autoLunchStatus && hrsRequiredForLunchTrigger != null && hrsRequiredForLunchTrigger > 0) { %>
                         <span>(Threshold: <strong><%= String.format(Locale.US, "%.2f", hrsRequiredForLunchTrigger) %> hrs</strong>)</span>
                    <% } %>
                <% } else if (scheduleNameForDisplay != null && !scheduleNameForDisplay.equals("N/A") && !scheduleNameForDisplay.trim().isEmpty()){ %>
                     <span>Auto-Lunch: <em>Not Configured for this Schedule</em></span>
                <% } %>
            </div>
        <% } %>

        <% if (eidToLoad > 0 && (pageError == null || pageError.isEmpty()) && currentPayPeriodStartDate != null) { %>
            <h3 class="employee-punches-title">Punches for: <%= escapeHtml(employeeNameForDisplay) %></h3>
        <% } else if (eidToLoad > 0 && pageError != null && !pageError.isEmpty()) { %>
            <h3 class="employee-punches-title" style="color: #721c24;">Error loading punches for: <%= escapeHtml(employeeNameForDisplay) %></h3>
        <% } %>

        <div class="table-container report-table-container">
            <table id="punchesTable" class="report-table">
                <thead>
                    <tr>
                        <th>Date</th><th>Day</th><th>Time In</th><th>Time Out</th><th>Total Hours</th><th>Type</th>
                    </tr>
                </thead>
                <tbody>
                    <%= punchTableHtmlBuilder.toString() %>
                </tbody>
                <% if (eidToLoad > 0 && currentPayPeriodStartDate != null && (pageError == null || pageError.isEmpty()) &&
                       punchTableHtmlBuilder.indexOf("report-error-row") == -1 &&
                       punchTableHtmlBuilder.indexOf("report-message-row") == -1 &&
                       periodTotalHoursForDisplay > 0.001) { // Check for small positive value %>
                <tfoot>
                    <tr>
                        <td colspan="4" style="text-align:right; font-weight:bold;" class="period-total-label">Period Total:</td>
                        <td style="text-align:right; font-weight:bold;" class="period-total-value"><%= String.format(Locale.US, "%.2f", periodTotalHoursForDisplay) %></td>
                        <td class="period-total-spacer"></td>
                    </tr>
                    <% if(employeeInfo != null && "Hourly".equalsIgnoreCase((String)employeeInfo.getOrDefault("wageType",""))) { %>
                    <tr class="hours-breakdown-row">
                        <td colspan="6" style="text-align:right; font-size:0.9em;">
                            (Reg: <%= String.format(Locale.US, "%.2f", totalRegularHoursForDisplay) %>,
                             OT: <%= String.format(Locale.US, "%.2f", totalOvertimeHoursForDisplay) %>
                             <% if(totalDoubleTimeHoursForDisplay > 0.001) { %>
                                , DT: <%= String.format(Locale.US, "%.2f", totalDoubleTimeHoursForDisplay) %>
                             <% } %>)
                        </td>
                    </tr>
                    <% } %>
                </tfoot>
                <% } %>
            </table>
        </div>

        <% if (eidToLoad > 0 && (pageError == null || pageError.isEmpty()) && currentPayPeriodStartDate != null) { %>
            <div class="action-buttons-bottom">
                <button type="button" id="addHoursBtn" class="glossy-button text-green">
                    <i class="fas fa-plus-circle"></i> Add Hours
                </button>
                <button type="button" id="addTimedPunchBtn" class="glossy-button text-blue">
                    <i class="fas fa-clock"></i> Add Timed Punch
                </button>
                <button type="button" id="editRowBtn" class="glossy-button text-yellow" disabled>
                    <i class="fas fa-edit"></i> Edit Row
                </button>
                <button type="button" id="deleteRowBtn" class="glossy-button text-red-punch" disabled>
                    <i class="fas fa-trash-alt"></i> Delete Row
                </button>
            </div>
        <% } %>
    </div>

    <%-- Add Hours Modal --%>
    <div id="addHoursModal" class="modal">
        <div class="modal-content">
            <span class="close">&times;</span>
            <h2 id="addHoursModalTitle">Add Entry</h2>
            <form id="addHoursForm" action="AddEditAndDeletePunchesServlet" method="POST">
                <input type="hidden" name="action" value="addHours" id="addHours_actionInput">
                <input type="hidden" name="eid" id="addHours_eidInput" value="<%= eidToLoad > 0 ? eidToLoad : "" %>">
                <input type="hidden" name="currentPayPeriodStart" value="<%= currentPayPeriodStartDate != null ? currentPayPeriodStartDate.format(isoDateFormatter) : "" %>">
                <input type="hidden" name="currentPayPeriodEnd" value="<%= currentPayPeriodEndDate != null ? currentPayPeriodEndDate.format(isoDateFormatter) : "" %>">
                <input type="hidden" name="userTimeZone" id="addHours_userTimeZone" value="<%= escapeHtml(userTimeZoneId) %>">
                <div class="form-item">
                    <label for="addHours_dateInput">Date:<span class="required-asterisk">*</span></label>
                    <input type="date" id="addHours_dateInput" name="punchDate" required autofocus>
                </div>
                <div class="form-item" id="addHours_punchTypeContainer">
                    <label for="addHours_typeSelect">Type:<span class="required-asterisk">*</span></label>
                    <select id="addHours_typeSelect" name="punchType" required>
                        <option value="Supervisor Override">Supervisor Override</option>
                        <option value="Vacation">Vacation</option>
                        <option value="Sick">Sick</option>
                        <option value="Personal">Personal</option>
                        <option value="Holiday">Holiday</option>
                        <option value="Bereavement">Bereavement</option>
                        <option value="Other">Other</option>
                    </select>
                </div>
                <div class="form-row" id="addHours_timeFieldsRowDiv" style="display:none;">
                    <div class="form-item">
                        <label for="addHours_timeInInput">Time In:<span class="required-asterisk">*</span></label>
                        <input type="time" id="addHours_timeInInput" name="timeIn" step="1">
                    </div>
                    <div class="form-item">
                        <label for="addHours_timeOutInput">Time Out:</label>
                        <input type="time" id="addHours_timeOutInput" name="timeOut" step="1">
                    </div>
                </div>
                <div class="form-item" id="addHours_totalHoursDiv" style="display:none;">
                    <label for="addHours_totalHoursInput">Total Hours:<span class="required-asterisk">*</span></label>
                    <input type="number" id="addHours_totalHoursInput" name="totalHoursManual" step="0.01" min="0.01" max="24" placeholder="e.g., 8.00">
                </div>
                <div class="button-row">
                    <button type="button" class="glossy-button text-red close-modal-btn">
                        <i class="fas fa-times"></i> Cancel
                    </button>
                    <button type="submit" class="glossy-button text-green">
                        <i class="fas fa-save"></i> <span id="addHoursSubmitBtnText">Save</span>
                    </button>
                </div>
            </form>
        </div>
    </div>

    <%-- Edit Punch Modal --%>
    <div id="editPunchModal" class="modal">
        <div class="modal-content">
            <span class="close">&times;</span>
            <h2 id="editPunchModalTitleElem">Edit Punch Record</h2>
            <form id="editPunchForm" action="AddEditAndDeletePunchesServlet" method="POST">
                <input type="hidden" name="action" value="editPunch">
                <input type="hidden" name="punchId" id="editPunch_idInput">
                <input type="hidden" name="editEmployeeId" id="editPunch_eidInput" value="<%= eidToLoad > 0 ? eidToLoad : "" %>">
                <input type="hidden" name="currentPayPeriodStart" value="<%= currentPayPeriodStartDate != null ? currentPayPeriodStartDate.format(isoDateFormatter) : "" %>">
                <input type="hidden" name="currentPayPeriodEnd" value="<%= currentPayPeriodEndDate != null ? currentPayPeriodEndDate.format(isoDateFormatter) : "" %>">
                <input type="hidden" name="userTimeZone" id="editPunch_userTimeZone" value="<%= escapeHtml(userTimeZoneId) %>">

                <div class="form-item">
                    <label for="editPunch_dateInput">Date:<span class="required-asterisk">*</span></label>
                    <input type="date" id="editPunch_dateInput" name="editDate" required>
                </div>
                <div class="form-row" id="editPunch_timeFieldsRowDiv">
                    <div class="form-item">
                        <label for="editPunch_timeInInput">Time In:</label>
                        <input type="time" id="editPunch_timeInInput" name="editInTime" step="1">
                    </div>
                    <div class="form-item">
                        <label for="editPunch_timeOutInput">Time Out:</label>
                        <input type="time" id="editPunch_timeOutInput" name="editOutTime" step="1">
                    </div>
                </div>
                <div class="form-item" id="editPunch_totalHoursDiv" style="display:none;">
                    <label for="editPunch_totalHoursInput">Total Hours (Accrued):<span class="required-asterisk">*</span></label>
                    <input type="number" id="editPunch_totalHoursInput" name="totalHoursManual" step="0.01" min="0.01" max="24" placeholder="e.g., 8.00">
                </div>
                <div class="form-item" id="editPunch_punchTypeContainer">
                    <label for="editPunch_typeSelect">Punch Type:</label>
                    <select id="editPunch_typeSelect" name="editPunchType" required></select>
                </div>
                <div class="button-row">
                    <button type="button" class="glossy-button text-red close-modal-btn">
                        <i class="fas fa-times"></i> Cancel
                    </button>
                    <button type="submit" class="glossy-button text-blue">
                        <i class="fas fa-save"></i> Save Changes
                    </button>
                </div>
            </form>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script>
        window.SELECTED_EID_ON_LOAD = <%= eidToLoad %>;
        // The JavaScript variable name is kept for compatibility with punches.js,
        // but it's now populated by the consistently named 'userTimeZoneId' from JSP scriptlet.
        window.EFFECTIVE_TIME_ZONE_ID = "<%= escapeForJavaScriptString(userTimeZoneId) %>";
        console.log("[Punches.jsp] Effective TimeZoneId for JS: " + window.EFFECTIVE_TIME_ZONE_ID);
    </script>
    <script src="${pageContext.request.contextPath}/js/punches.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>