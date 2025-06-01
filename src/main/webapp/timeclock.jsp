<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.Configuration" %>
<%@ page import="timeclock.punches.ShowPunches" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.sql.Time" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspTimeclockLogger = Logger.getLogger("timeclock_jsp_timezone_final");

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
     private String escapeForJavaScriptString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
%>
<%
    String pageError = null;
    HttpSession currentSession_timeclock = request.getSession(false);
    Integer tenantId_timeclock = null;
    Integer sessionEid_timeclock = null;
    String userPermissions_timeclock = "User";

    if (currentSession_timeclock != null) {
        Object tenantIdObj = currentSession_timeclock.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId_timeclock = (Integer) tenantIdObj; }
        Object eidObj = currentSession_timeclock.getAttribute("EID");
        if (eidObj instanceof Integer) { sessionEid_timeclock = (Integer) eidObj; }
        Object permObj = currentSession_timeclock.getAttribute("Permissions");
        if (permObj instanceof String && !((String)permObj).trim().isEmpty()) { userPermissions_timeclock = (String) permObj; }
    }

    if (tenantId_timeclock == null || tenantId_timeclock <= 0 || sessionEid_timeclock == null || sessionEid_timeclock <= 0) {
        jspTimeclockLogger.warning("Session expired or invalid for timeclock.jsp. Invalidating and redirecting to login.");
        if(currentSession_timeclock != null) currentSession_timeclock.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired or invalid. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    boolean reportMode = "true".equalsIgnoreCase(request.getParameter("reportMode"));
    String titleSuffix = reportMode ? " Report" : "";
    
    // --- Timezone Logic ---
    final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
    final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver"; // Fallback if tenant doesn't have one explicitly.
    String userTimeZoneId = null;

    // 1. Attempt to get user-specific timezone from session (set at login)
    if (currentSession_timeclock != null) {
        Object userTimeZoneIdObj = currentSession_timeclock.getAttribute("userTimeZoneId"); 
        if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
            jspTimeclockLogger.info("[TIMEZONE] Using userTimeZoneId from session: " + userTimeZoneId + " for EID: " + sessionEid_timeclock);
        }
    }

    // 2. If not in session (or invalid), try Tenant's DefaultTimeZone
    if (!ShowPunches.isValid(userTimeZoneId) && tenantId_timeclock != null && tenantId_timeclock > 0) {
        String tenantDefaultTz = Configuration.getProperty(tenantId_timeclock, "DefaultTimeZone"); // Get without internal fallback first
        if (ShowPunches.isValid(tenantDefaultTz)) {
            userTimeZoneId = tenantDefaultTz;
            jspTimeclockLogger.info("[TIMEZONE] Using Tenant DefaultTimeZone from SETTINGS: " + userTimeZoneId + " for EID: " + sessionEid_timeclock + ", Tenant: " + tenantId_timeclock);
        } else {
            // If tenant default is not set in DB, use the application's defined default for tenants
            userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
            jspTimeclockLogger.info("[TIMEZONE] Tenant DefaultTimeZone not set in DB. Using application default for tenant: " + userTimeZoneId + " for EID: " + sessionEid_timeclock);
        }
    }

    // 3. If still not found or invalid (e.g., if all above were null/empty), use the ultimate Pacific Time fallback
    if (!ShowPunches.isValid(userTimeZoneId)) {
        userTimeZoneId = PACIFIC_TIME_FALLBACK;
        jspTimeclockLogger.warning("[TIMEZONE] User/Tenant timezone not determined or invalid. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for EID: " + sessionEid_timeclock);
    }

    // 4. Validate the determined ZoneId to prevent errors in formatting downstream
    try {
        ZoneId.of(userTimeZoneId); 
    } catch (Exception e) {
        jspTimeclockLogger.log(Level.SEVERE, "[TIMEZONE] CRITICAL: Invalid effective userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. EID: " + sessionEid_timeclock, e);
        userTimeZoneId = "UTC"; 
        String tzErrorMsg = "A critical error occurred with timezone configuration. Displaying times in UTC. Please contact support.";
        pageError = (pageError == null) ? tzErrorMsg : pageError + " " + tzErrorMsg;
    }
    jspTimeclockLogger.info("[TIMEZONE] Timeclock.jsp final effective userTimeZoneId for display: " + userTimeZoneId + " for user EID: " + sessionEid_timeclock);
    // --- End Timezone Logic ---

    int globalEidForDisplay = 0;
    List<Map<String, Object>> employeeDropdownList = new ArrayList<>();

    if ("Administrator".equalsIgnoreCase(userPermissions_timeclock)) {
        if (tenantId_timeclock > 0) {
             employeeDropdownList = ShowPunches.getActiveEmployeesForDropdown(tenantId_timeclock);
        }
        String eidParam = request.getParameter("eid");
        if (ShowPunches.isValid(eidParam)) {
            try {
                globalEidForDisplay = Integer.parseInt(eidParam.trim());
                if (globalEidForDisplay <= 0 && reportMode && employeeDropdownList != null && !employeeDropdownList.isEmpty()) {
                    globalEidForDisplay = 0; 
                } else if (globalEidForDisplay <= 0) {
                     globalEidForDisplay = sessionEid_timeclock;
                }
            } catch (NumberFormatException nfe) {
                globalEidForDisplay = (reportMode && employeeDropdownList != null && !employeeDropdownList.isEmpty()) ? 0 : sessionEid_timeclock;
            }
        } else { 
            globalEidForDisplay = (!reportMode && sessionEid_timeclock != null) ? sessionEid_timeclock : 0; 
        }
    } else { 
        globalEidForDisplay = sessionEid_timeclock;
    }

    LocalDate periodStartDate = null; LocalDate periodEndDate = null;
    String payPeriodMessage = "Pay Period Not Set";
    DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);

    if (tenantId_timeclock > 0) {
        try {
            Map<String, LocalDate> periodInfo = ShowPunches.getCurrentPayPeriodInfo(tenantId_timeclock);
            if (periodInfo != null && periodInfo.get("startDate") != null && periodInfo.get("endDate") != null) {
                periodStartDate = periodInfo.get("startDate"); periodEndDate = periodInfo.get("endDate");
                payPeriodMessage = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);
            } else { pageError = (pageError == null ? "" : pageError + " ").trim() + "Pay period dates not configured."; }
        } catch (Exception e) {
            jspTimeclockLogger.log(Level.WARNING, "Error retrieving pay period for tenant " + tenantId_timeclock, e);
            pageError = (pageError == null ? "" : pageError + " ").trim() + "Error retrieving pay period.";
        }
    } else if (pageError == null) {
        pageError = "Invalid tenant configuration.";
    }

    Map<String, Object> employeeInfo = null; Integer tenantEmployeeNumberToDisplay = null;
    String employeeName = "N/A"; String department = "N/A"; String supervisor = "N/A";
    String scheduleName = "N/A"; boolean isScheduleOpen = true; String scheduleTimeStr = "";
    String autoLunchStr = "Off"; String wageTypeStr = "N/A";
    double vacationHours = 0.0, sickHours = 0.0, personalHours = 0.0;

    StringBuilder timeclockPunchTableBuilder = new StringBuilder();
    String punchTableHtmlOutput;

    double totalRegularHours = 0.0, totalOvertimeHours = 0.0, totalDoubleTimeHours = 0.0, periodTotalHours = 0.0;
    NumberFormat hoursFormatter = NumberFormat.getNumberInstance(Locale.US);
    hoursFormatter.setMinimumFractionDigits(2); hoursFormatter.setMaximumFractionDigits(2);

    String defaultPunchTableErrorHtml = "<tr><td colspan='6' class='report-error-row'>Error loading punches. Check server logs.</td></tr>";
    String defaultPunchTableSelectEmployeeHtml = "<tr><td colspan='6' style='text-align:center; font-style:italic; padding: 20px;'>Please select an employee to view their timecard.</td></tr>";

    if (tenantId_timeclock > 0 && globalEidForDisplay > 0 && pageError == null && periodStartDate != null && periodEndDate != null) {
        try {
            employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId_timeclock, globalEidForDisplay);
            if (employeeInfo != null) {
                employeeName = escapeHtml((String) employeeInfo.getOrDefault("employeeName", "N/A"));
                Object tenObj = employeeInfo.get("tenantEmployeeNumber");
                if (tenObj instanceof Integer) { tenantEmployeeNumberToDisplay = (Integer) tenObj; }
                else if (tenObj != null) { try { tenantEmployeeNumberToDisplay = Integer.parseInt(tenObj.toString()); } catch (NumberFormatException e) { /* ignore */ } }

                department = escapeHtml((String) employeeInfo.getOrDefault("department", "N/A"));
                supervisor = escapeHtml((String) employeeInfo.getOrDefault("supervisor", "N/A"));
                scheduleName = escapeHtml((String) employeeInfo.getOrDefault("scheduleName", "N/A"));
                if (scheduleName == null || scheduleName.trim().isEmpty() || scheduleName.toLowerCase().contains("open")) {
                    isScheduleOpen = true; scheduleName = (scheduleName == null || scheduleName.trim().isEmpty()) ? "Open (Flexible)" : scheduleName;
                } else { isScheduleOpen = false; }

                Time shiftStart = (Time) employeeInfo.get("shiftStart"); Time shiftEnd = (Time) employeeInfo.get("shiftEnd");
                DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);
                if (shiftStart != null && shiftEnd != null) {
                     scheduleTimeStr = shiftStart.toLocalTime().format(tf) + " - " + shiftEnd.toLocalTime().format(tf);
                } else {
                     scheduleTimeStr = "N/A";
                }

                boolean autoLunchDb = employeeInfo.containsKey("autoLunch") ? (Boolean) employeeInfo.get("autoLunch") : false;
                Object hrsReqObj = employeeInfo.get("hoursRequired"); Object lunchLenObj = employeeInfo.get("lunchLength");
                String hrsReqStr = (hrsReqObj instanceof Number) ? String.format(Locale.US, "%.2f", ((Number)hrsReqObj).doubleValue()) : (hrsReqObj != null ? escapeHtml(hrsReqObj.toString()) : "?");
                String lunchLenStr = (lunchLenObj != null) ? escapeHtml(lunchLenObj.toString()) : "?";
                autoLunchStr = autoLunchDb ? "On (Req: " + hrsReqStr + "hr | Len: " + lunchLenStr + "m)" : "Off";

                wageTypeStr = escapeHtml((String) employeeInfo.getOrDefault("wageType", "N/A"));
                vacationHours = (Double) employeeInfo.getOrDefault("vacationHours", 0.0);
                sickHours = (Double) employeeInfo.getOrDefault("sickHours", 0.0);
                personalHours = (Double) employeeInfo.getOrDefault("personalHours", 0.0);

                Map<String, Object> timecardData = ShowPunches.getTimecardPunchData(tenantId_timeclock, globalEidForDisplay, periodStartDate, periodEndDate, employeeInfo, userTimeZoneId);
                
                if (timecardData != null) {
                    String tcDataError = (String)timecardData.get("error");
                    if (tcDataError != null) { // Check for error from getTimecardPunchData
                        pageError = ((pageError == null ? "" : pageError + " ") + tcDataError).trim();
                        timeclockPunchTableBuilder.append("<tr><td colspan='6' class='report-error-row'>").append(escapeHtml(tcDataError)).append("</td></tr>");
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> punchesList = (List<Map<String, String>>) timecardData.get("punches");
                        if (punchesList != null && !punchesList.isEmpty()) {
                            for (Map<String, String> punch : punchesList) {
                                timeclockPunchTableBuilder.append("<tr data-punch-date=\"").append(escapeHtml(punch.get("punchDate"))).append("\">");
                                timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("dayOfWeek"))).append("</td>");
                                timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("friendlyPunchDate"))).append("</td>");
                                String timeIn = punch.get("timeIn"); String inTimeCssClass = punch.get("inTimeCssClass");
                                timeclockPunchTableBuilder.append("<td>");
                                if (inTimeCssClass != null && !inTimeCssClass.isEmpty()) timeclockPunchTableBuilder.append("<span class=\"").append(escapeHtml(inTimeCssClass)).append("\">");
                                timeclockPunchTableBuilder.append(escapeHtml(timeIn));
                                if (inTimeCssClass != null && !inTimeCssClass.isEmpty()) timeclockPunchTableBuilder.append("</span>");
                                timeclockPunchTableBuilder.append("</td>");
                                String timeOut = punch.get("timeOut"); String outTimeCssClass = punch.get("outTimeCssClass");
                                timeclockPunchTableBuilder.append("<td>");
                                if (outTimeCssClass != null && !outTimeCssClass.isEmpty()) timeclockPunchTableBuilder.append("<span class=\"").append(escapeHtml(outTimeCssClass)).append("\">");
                                timeclockPunchTableBuilder.append(escapeHtml(timeOut));
                                if (outTimeCssClass != null && !outTimeCssClass.isEmpty()) timeclockPunchTableBuilder.append("</span>");
                                timeclockPunchTableBuilder.append("</td>");
                                timeclockPunchTableBuilder.append("<td style=\"text-align:right;\">").append(escapeHtml(punch.get("totalHours"))).append("</td>");
                                timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("punchType"))).append("</td>");
                                timeclockPunchTableBuilder.append("</tr>\n");
                            }
                        } else {
                            timeclockPunchTableBuilder.append("<tr><td colspan='6' class='report-message-row'>No punches recorded for this period.</td></tr>");
                        }
                    }
                    totalRegularHours = (Double) timecardData.getOrDefault("totalRegularHours", 0.0);
                    totalOvertimeHours = (Double) timecardData.getOrDefault("totalOvertimeHours", 0.0);
                    totalDoubleTimeHours = (Double) timecardData.getOrDefault("totalDoubleTimeHours", 0.0);
                    periodTotalHours = Math.round((totalRegularHours + totalOvertimeHours + totalDoubleTimeHours) * 100.0) / 100.0;
                } else {
                    pageError = ((pageError == null ? "" : pageError + " ") + "Error processing timecard data.").trim();
                    timeclockPunchTableBuilder.append(defaultPunchTableErrorHtml);
                }
            } else {
                pageError = ((pageError == null ? "" : pageError + " ") + "Employee data not found for EID: " + globalEidForDisplay).trim();
                employeeName = "Not Found";
                timeclockPunchTableBuilder.append("<tr><td colspan='6' style='text-align:center;'>Employee data not found for EID ").append(globalEidForDisplay).append(".</td></tr>");
            }
        } catch (Exception e) {
            jspTimeclockLogger.log(Level.SEVERE, "Unexpected error loading timecard for EID " + globalEidForDisplay, e);
            pageError = ((pageError == null ? "" : pageError + " ") + "Unexpected error: " + escapeHtml(e.getMessage())).trim();
            timeclockPunchTableBuilder.append(defaultPunchTableErrorHtml);
        }
    } else if (globalEidForDisplay <= 0 && pageError == null && "Administrator".equalsIgnoreCase(userPermissions_timeclock) && !reportMode) {
        timeclockPunchTableBuilder.append(defaultPunchTableSelectEmployeeHtml);
    } else if (pageError != null && timeclockPunchTableBuilder.length() == 0) {
        timeclockPunchTableBuilder.append("<tr><td colspan='6' class='report-error-row'>").append(escapeHtml(pageError)).append("</td></tr>");
    }

    punchTableHtmlOutput = timeclockPunchTableBuilder.toString();
    String formattedVacation = hoursFormatter.format(vacationHours);
    String formattedSick = hoursFormatter.format(sickHours);
    String formattedPersonal = hoursFormatter.format(personalHours);
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Time Card<%= titleSuffix %><% if (globalEidForDisplay > 0 && !"N/A".equals(employeeName) && !"Not Found".equals(employeeName)) { %> - <%= escapeHtml(employeeName) %><% } %></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/timeclock.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400..900&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <script src="https://cdn.jsdelivr.net/npm/@fingerprintjs/fingerprintjs/dist/fp.min.js"></script>
</head>
<body>
    <% if ("Administrator".equalsIgnoreCase(userPermissions_timeclock)) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>
    <% if (!reportMode) { %>
        <div id="main-clock-container"><jsp:include page="clock.html" /></div>
    <% } %>
    <% if ("Administrator".equalsIgnoreCase(userPermissions_timeclock)) { %>
        <div class="employee-selector-container">
            <label for="employeeSelect">View Employee:</label>
            <select id="employeeSelect" name="eid_select" onchange="navigateToEmployee(this.value, <%= reportMode %>)">
                <option value="0">-- Select Employee --</option>
                <% if (employeeDropdownList == null || employeeDropdownList.isEmpty()) { %>
                    <option value="" disabled>No active employees</option>
                <% } else {
                       for (Map<String, Object> emp : employeeDropdownList) {
                           int currentDropdownGlobalEid = (Integer) emp.get("eid");
                           String displayName = (String) emp.get("displayName"); %>
                           <option value="<%= currentDropdownGlobalEid %>" <%= (currentDropdownGlobalEid == globalEidForDisplay) ? "selected" : "" %>>
                               <%= escapeHtml(displayName) %>
                           </option>
                <%     }
                   }
                %>
            </select>
        </div>
    <% } %>
    <div class="timecard-container">
        <div class="timecard" id="printableTimecardArea">
            <div class="timecard-header">
                <h1>Time Card<%= titleSuffix %></h1>
                <div class="timecard-pay-period"><%= escapeHtml(payPeriodMessage) %></div>
            </div>
            
            <% if (pageError != null && (punchTableHtmlOutput == null || !punchTableHtmlOutput.contains("report-error-row")) ) { %>
                <div id="notification-bar" class="error-message" style="margin-bottom: 15px; display:block;"><%= escapeHtml(pageError) %></div>
            <% } %>

            <% if( (globalEidForDisplay > 0 || (reportMode && globalEidForDisplay == 0)) && (pageError == null || (pageError !=null && punchTableHtmlOutput != null && punchTableHtmlOutput.contains("report-error-row") ) ) ) { %>
                 <% if(globalEidForDisplay > 0 && employeeInfo != null) { %>
                    <div class="timecard-info">
                        <div class="info-left">
                            <div class="info-item"><strong>Employee ID:</strong> <span class="info-value"><%= (tenantEmployeeNumberToDisplay != null && tenantEmployeeNumberToDisplay > 0) ? tenantEmployeeNumberToDisplay : String.valueOf(globalEidForDisplay) %></span></div>
                            <div class="info-item"><strong>Employee:</strong> <span class="info-value"><%= escapeHtml(employeeName) %></span></div>
                            <div class="info-item"><strong>Department:</strong> <span class="info-value"><%= escapeHtml(department) %></span></div>
                            <div class="info-item"><strong>Supervisor:</strong> <span class="info-value"><%= escapeHtml(supervisor) %></span></div>
                        </div>
                        <div class="info-right">
                            <div class="info-item"><strong>Schedule:</strong> <span class="info-value"><%= escapeHtml(scheduleName) %></span></div>
                            <% if (!isScheduleOpen) { %><div class="info-item"><strong>Hours:</strong> <span class="info-value"><%= escapeHtml(scheduleTimeStr) %></span></div><% } %>
                            <div class="info-item"><strong>Auto Lunch:</strong> <span class="info-value"><%= escapeHtml(autoLunchStr) %></span></div>
                            <div class="info-item"><strong>Wage Type:</strong> <span class="info-value"><%= wageTypeStr %></span></div>
                        </div>
                    </div>
                <% } %>
                <div id="timecardTableContainer" class="table-container timecard-table-container">
                    <table id="punches" class="punches timecard-table">
                        <thead><tr><th>Day</th><th>Date</th><th>IN</th><th>OUT</th><th>Total Hours</th><th>Punch Type</th></tr></thead>
                        <tbody><%= punchTableHtmlOutput %></tbody>
                        <% if(globalEidForDisplay > 0 && employeeInfo != null) { %>
                            <tfoot>
                                <tr>
                                    <td colspan="4" class="period-total-label">Period Totals:</td>
                                    <td class="period-total-value"><%= String.format(Locale.US, "%.2f", periodTotalHours) %></td>
                                    <td class="period-total-spacer"></td>
                                </tr>
                                <% if("Hourly".equalsIgnoreCase(wageTypeStr)) { %>
                                <tr class="hours-breakdown-row">
                                    <td colspan="6" style = "padding-right:128px">(Reg: <%= String.format(Locale.US, "%.2f", totalRegularHours) %> | OT: <%= String.format(Locale.US, "%.2f", totalOvertimeHours) %> <% if(totalDoubleTimeHours > 0.001) { %> | DT: <%= String.format(Locale.US, "%.2f", totalDoubleTimeHours) %> <% } %>)</td>
                                </tr>
                                <% } %>
                            </tfoot>
                        <% } %>
                    </table>
                </div>
                 <% if(globalEidForDisplay > 0 && employeeInfo != null) { %>
                    <div class="accrual-balances">
                        <h3 class="accrual-title">Available Accrued Hours</h3>
                        <div class="balance-item"> <span class="balance-label">Vacation:</span> <span class="balance-value"><%= formattedVacation %></span> </div>
                        <div class="balance-item"> <span class="balance-label">Sick:</span> <span class="balance-value"><%= formattedSick %></span> </div>
                        <div class="balance-item"> <span class="balance-label">Personal:</span> <span class="balance-value"><%= formattedPersonal %></span> </div>
                    </div>
                 <% } %>
                 <% if (!reportMode && globalEidForDisplay > 0 && globalEidForDisplay == sessionEid_timeclock) { %>
                    <div class="punch-buttons">
                        <form id="punchInForm" method="post" action="PunchInAndOutServlet" style="display: contents;">
                            <input type="hidden" name="punchAction" value="IN">
                            <input type="hidden" name="punchEID" value="<%= globalEidForDisplay %>">
                            <input type="hidden" name="deviceFingerprintHash" id="deviceFingerprintHash_IN" value="">
                            <input type="hidden" name="clientLatitude" id="clientLatitude_IN" value="">
                            <input type="hidden" name="clientLongitude" id="clientLongitude_IN" value="">
                            <input type="hidden" name="clientLocationAccuracy" id="clientLocationAccuracy_IN" value="">
                            <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneId_IN" value="">
                            <button type="submit" class="punch-button punch-in">PUNCH IN</button>
                        </form>
                        <form id="punchOutForm" method="post" action="PunchInAndOutServlet" style="display: contents;">
                            <input type="hidden" name="punchAction" value="OUT">
                            <input type="hidden" name="punchEID" value="<%= globalEidForDisplay %>">
                            <input type="hidden" name="deviceFingerprintHash" id="deviceFingerprintHash_OUT" value="">
                            <input type="hidden" name="clientLatitude" id="clientLatitude_OUT" value="">
                            <input type="hidden" name="clientLongitude" id="clientLongitude_OUT" value="">
                            <input type="hidden" name="clientLocationAccuracy" id="clientLocationAccuracy_OUT" value="">
                            <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneId_OUT" value="">
                            <button type="submit" class="punch-button punch-out">PUNCH OUT</button>
                        </form>
                    </div>
                <% } else if (reportMode && globalEidForDisplay > 0) { %>
                    <div class="report-actions-container"> <button type="button" id="btnPrintThisTimecard" class="report-action-button">Print This Time Card</button> </div>
                <% } %>
            <% } else if (pageError == null && globalEidForDisplay <= 0 && "Administrator".equalsIgnoreCase(userPermissions_timeclock) && !reportMode) { %>
                <div class="table-container timecard-table-container"> <table id="punches" class="punches timecard-table"> <thead> <tr><th>Day</th><th>Date</th><th>IN</th><th>OUT</th><th>Total Hours</th><th>Punch Type</th></tr></thead> <tbody><%= defaultPunchTableSelectEmployeeHtml %></tbody> </table> </div>
            <% } else if (pageError != null && (punchTableHtmlOutput == null || punchTableHtmlOutput.isEmpty() || !punchTableHtmlOutput.contains("report-error-row")) ) { %>
                <div class="table-container timecard-table-container"> <table id="punches" class="punches timecard-table"> <thead> <tr><th>Day</th><th>Date</th><th>IN</th><th>OUT</th><th>Total Hours</th><th>Punch Type</th></tr></thead> <tbody><tr><td colspan="6" class="report-error-row"><%= escapeHtml(pageError) %></td></tr></tbody> </table> </div>
            <% } %>
        </div>
    </div>
    
    <div id="notificationModal" class="modal"><div class="modal-content"><span class="close-button" id="closeNotificationModal">&times;</span><h2 id="notificationModalTitle">Notification</h2><p id="notificationMessage"></p><div class="modal-footer" style="justify-content: center;"><button type="button" id="okButton" class="modal-ok-button">OK</button></div></div></div>

    <script type="text/javascript">
        const currentUserPermissions_tc = "<%= escapeForJavaScriptString(userPermissions_timeclock) %>";
        const loggedInEid_tc = <%= sessionEid_timeclock != null ? sessionEid_timeclock : 0 %>;
        const displayedEid_tc = <%= globalEidForDisplay %>;
        const isReportMode_tc = <%= reportMode %>;
        const sessionTimeoutDuration_Js = <%
            int userTimeoutEffective = 30 * 60;
            if (currentSession_timeclock != null) {
                userTimeoutEffective = currentSession_timeclock.getMaxInactiveInterval();
                if (userTimeoutEffective <= 0) {
                    userTimeoutEffective = ("Administrator".equalsIgnoreCase(userPermissions_timeclock)) ? (120*60) : (30*60); 
                }
            }
            out.print(userTimeoutEffective);
        %>;
        const effectiveUserTimeZoneId_tc = "<%= escapeForJavaScriptString(userTimeZoneId) %>";
        console.log("[Timeclock.jsp] Effective TimeZoneId for JS: " + effectiveUserTimeZoneId_tc);
    </script>
    <script src="${pageContext.request.contextPath}/js/timeclock.js?v=<%= System.currentTimeMillis() %>"></script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>