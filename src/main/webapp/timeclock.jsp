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
        if(currentSession_timeclock != null) currentSession_timeclock.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired or invalid. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    boolean reportMode = "true".equalsIgnoreCase(request.getParameter("reportMode"));
    String titleSuffix = reportMode ? " Report" : "";
    final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
    final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
    String userTimeZoneId = null;
    if (currentSession_timeclock != null) {
        Object userTimeZoneIdObj = currentSession_timeclock.getAttribute("userTimeZoneId");
        if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
        }
    }

    if (!ShowPunches.isValid(userTimeZoneId) && tenantId_timeclock != null && tenantId_timeclock > 0) {
        String tenantDefaultTz = Configuration.getProperty(tenantId_timeclock, "DefaultTimeZone");
        if (ShowPunches.isValid(tenantDefaultTz)) {
            userTimeZoneId = tenantDefaultTz;
        } else {
            userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
        }
    }

    if (!ShowPunches.isValid(userTimeZoneId)) {
        userTimeZoneId = PACIFIC_TIME_FALLBACK;
    }

    try {
        ZoneId.of(userTimeZoneId);
    } catch (Exception e) {
        userTimeZoneId = "UTC"; 
        String tzErrorMsg = "A critical error occurred with timezone configuration. Displaying times in UTC.";
        pageError = (pageError == null) ? tzErrorMsg : pageError + " " + tzErrorMsg;
    }

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
            } catch (NumberFormatException nfe) {
                globalEidForDisplay = (reportMode && !employeeDropdownList.isEmpty()) ? 0 : sessionEid_timeclock;
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
                periodStartDate = periodInfo.get("startDate");
                periodEndDate = periodInfo.get("endDate");
                payPeriodMessage = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);
            } else { pageError = (pageError == null ? "" : pageError + " ").trim() + "Pay period dates not configured."; }
        } catch (Exception e) {
            pageError = (pageError == null ? "" : pageError + " ").trim() + "Error retrieving pay period.";
        }
    } else if (pageError == null) {
        pageError = "Invalid tenant configuration.";
    }

    Map<String, Object> employeeInfo = null; Integer tenantEmployeeNumberToDisplay = null;
    String employeeName = "N/A";
    String department = "N/A"; String supervisor = "N/A";
    String scheduleName = "N/A"; boolean isScheduleOpen = true; String scheduleTimeStr = "";
    String autoLunchStr = "Off"; String wageTypeStr = "N/A";
    double vacationHours = 0.0, sickHours = 0.0, personalHours = 0.0;
    StringBuilder timeclockPunchTableBuilder = new StringBuilder();
    String punchTableHtmlOutput;
    double totalRegularHours = 0.0, totalOvertimeHours = 0.0, totalDoubleTimeHours = 0.0, periodTotalHours = 0.0;
    NumberFormat hoursFormatter = NumberFormat.getNumberInstance(Locale.US);
    hoursFormatter.setMinimumFractionDigits(2); hoursFormatter.setMaximumFractionDigits(2);
    
    if (tenantId_timeclock > 0 && globalEidForDisplay > 0 && pageError == null && periodStartDate != null && periodEndDate != null) {
        try {
            employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId_timeclock, globalEidForDisplay);
            if (employeeInfo != null) {
                employeeName = escapeHtml((String) employeeInfo.getOrDefault("employeeName", "N/A"));
                Object tenObj = employeeInfo.get("tenantEmployeeNumber");
                if (tenObj instanceof Integer) { tenantEmployeeNumberToDisplay = (Integer) tenObj; }
                department = escapeHtml((String) employeeInfo.getOrDefault("department", "N/A"));
                supervisor = escapeHtml((String) employeeInfo.getOrDefault("supervisor", "N/A"));
                scheduleName = escapeHtml((String) employeeInfo.getOrDefault("scheduleName", "N/A"));
                isScheduleOpen = (scheduleName == null || scheduleName.trim().isEmpty() || scheduleName.toLowerCase().contains("open"));
                Time shiftStart = (Time) employeeInfo.get("shiftStart");
                Time shiftEnd = (Time) employeeInfo.get("shiftEnd");
                if (shiftStart != null && shiftEnd != null) {
                     scheduleTimeStr = shiftStart.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) + " - " + shiftEnd.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a"));
                } else { scheduleTimeStr = "N/A"; }
                boolean autoLunchDb = employeeInfo.containsKey("autoLunch") ? (Boolean) employeeInfo.get("autoLunch") : false;
                autoLunchStr = autoLunchDb ? "On" : "Off";
                wageTypeStr = escapeHtml((String) employeeInfo.getOrDefault("wageType", "N/A"));
                vacationHours = (Double) employeeInfo.getOrDefault("vacationHours", 0.0);
                sickHours = (Double) employeeInfo.getOrDefault("sickHours", 0.0);
                personalHours = (Double) employeeInfo.getOrDefault("personalHours", 0.0);
                Map<String, Object> timecardData = ShowPunches.getTimecardPunchData(tenantId_timeclock, globalEidForDisplay, periodStartDate, periodEndDate, employeeInfo, userTimeZoneId);
                if (timecardData != null) {
                    String tcDataError = (String)timecardData.get("error");
                    if (tcDataError != null) {
                        pageError = ((pageError == null ? "" : pageError + " ") + tcDataError).trim();
                    } else {
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> punchesList = (List<Map<String, String>>) timecardData.get("punches");
                        if (punchesList != null && !punchesList.isEmpty()) {
                            for (Map<String, String> punch : punchesList) {
                                timeclockPunchTableBuilder.append("<tr>");
                                timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("dayOfWeek"))).append("</td>");
                                timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("friendlyPunchDate"))).append("</td>");
                                timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("timeIn"))).append("</td>");
                                timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("timeOut"))).append("</td>");
                                timeclockPunchTableBuilder.append("<td style='text-align:right;'>").append(escapeHtml(punch.get("totalHours"))).append("</td>");
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
                    periodTotalHours = totalRegularHours + totalOvertimeHours + totalDoubleTimeHours;
                }
            }
        } catch (Exception e) {
            pageError = ((pageError == null ? "" : pageError + " ") + "Unexpected error: " + escapeHtml(e.getMessage())).trim();
        }
    }
    punchTableHtmlOutput = timeclockPunchTableBuilder.toString();
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Time Card<%= titleSuffix %></title>
    
    <link rel="stylesheet" href="css/timeclock.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
    <link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400..900&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <script src="https://cdn.jsdelivr.net/npm/@fingerprintjs/fingerprintjs/dist/fp.min.js"></script>
    <style>
        .modal .close-button:hover,
        .modal .close-button:focus {
            color: #c00;
            text-decoration: none;
            cursor: pointer;
        }
    </style>
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
                <% for (Map<String, Object> emp : employeeDropdownList) { %>
                   <option value="<%= emp.get("eid") %>" <%= ((Integer)emp.get("eid")).intValue() == globalEidForDisplay ? "selected" : "" %>><%= escapeHtml((String)emp.get("displayName")) %></option>
                <% } %>
            </select>
        </div>
    <% } %>
    <div class="timecard-container">
        <div class="timecard" id="printableTimecardArea">
            <div class="timecard-header">
                <h1>Time Card<%= titleSuffix %></h1>
                <div class="timecard-pay-period"><%= escapeHtml(payPeriodMessage) %></div>
            </div>
            
            <% if (pageError != null) { %>
                <div id="notification-bar" class="error-message"><%= escapeHtml(pageError) %></div>
            <% } %>

             <% if (globalEidForDisplay > 0 && employeeInfo != null) { %>
                <div class="timecard-info">
                    <div class="info-left">
                        <div class="info-item"><strong>Employee ID:</strong> <span class="info-value"><%= tenantEmployeeNumberToDisplay != null ? tenantEmployeeNumberToDisplay : globalEidForDisplay %></span></div>
                        <div class="info-item"><strong>Employee:</strong> <span class="info-value"><%= employeeName %></span></div>
                        <div class="info-item"><strong>Department:</strong> <span class="info-value"><%= department %></span></div>
                        <div class="info-item"><strong>Supervisor:</strong> <span class="info-value"><%= supervisor %></span></div>
                    </div>
                    <div class="info-right">
                        <div class="info-item"><strong>Schedule:</strong> <span class="info-value"><%= scheduleName %></span></div>
                        <div class="info-item"><strong>Hours:</strong> <span class="info-value"><%= scheduleTimeStr %></span></div>
                        <div class="info-item"><strong>Auto Lunch:</strong> <span class="info-value"><%= autoLunchStr %></span></div>
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
                            <td class="period-total-value"><%= hoursFormatter.format(periodTotalHours) %></td>
                            <td class="period-total-spacer"></td>
                        </tr>
                        <% if("Hourly".equalsIgnoreCase(wageTypeStr)) { %>
                        <tr class="hours-breakdown-row">
                            <td colspan="6" style="text-align:right; padding-right:10px;">(Reg: <%= hoursFormatter.format(totalRegularHours) %> | OT: <%= hoursFormatter.format(totalOvertimeHours) %> | DT: <%= hoursFormatter.format(totalDoubleTimeHours) %>)</td>
                        </tr>
                        <% } %>
                    </tfoot>
                    <% } %>
                </table>
            </div>
             <% if(globalEidForDisplay > 0 && employeeInfo != null) { %>
                <div class="accrual-balances">
                    <h3 class="accrual-title">Available Accrued Hours</h3>
                    <div class="balance-item"> <span class="balance-label">Vacation:</span> <span class="balance-value"><%= hoursFormatter.format(vacationHours) %></span> </div>
                    <div class="balance-item"> <span class="balance-label">Sick:</span> <span class="balance-value"><%= hoursFormatter.format(sickHours) %></span> </div>
                    <div class="balance-item"> <span class="balance-label">Personal:</span> <span class="balance-value"><%= hoursFormatter.format(personalHours) %></span> </div>
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
            <% } %>
        </div>
    </div>
    
    <div id="notificationModal" class="modal">
        <div class="modal-content">
            <span class="close-button" id="closeNotificationModal">&times;</span>
            <h2 id="notificationModalTitle">Notification</h2>
            <p id="notificationMessage"></p>
            <div class="modal-footer" style="justify-content: center;">
                <button type="button" id="okButton" class="modal-ok-button">OK</button>
            </div>
        </div>
    </div>

    <script type="text/javascript">
        const currentUserPermissions_tc = "<%= escapeForJavaScriptString(userPermissions_timeclock) %>";
        const sessionTimeoutDuration_Js = <%= currentSession_timeclock.getMaxInactiveInterval() %>;
        const app_contextPath = "<%= request.getContextPath() %>";
    </script>
    <script src="js/timeclock.js?v=<%= System.currentTimeMillis() %>"></script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>
