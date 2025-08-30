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
        tenantId_timeclock = (Integer) currentSession_timeclock.getAttribute("TenantID");
        sessionEid_timeclock = (Integer) currentSession_timeclock.getAttribute("EID");
        userPermissions_timeclock = (String) currentSession_timeclock.getAttribute("Permissions");
    }

    if (tenantId_timeclock == null || sessionEid_timeclock == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name()));
        return;
    }

    boolean reportMode = "true".equalsIgnoreCase(request.getParameter("reportMode"));
    String titleSuffix = reportMode ? " Report" : "";
    String userTimeZoneId = (String) currentSession_timeclock.getAttribute("userTimeZoneId");
    if (!ShowPunches.isValid(userTimeZoneId)) {
        userTimeZoneId = Configuration.getProperty(tenantId_timeclock, "DefaultTimeZone", "America/Denver");
    }

    int globalEidForDisplay = 0;
    List<Map<String, Object>> employeeDropdownList = new ArrayList<>();
    if ("Administrator".equalsIgnoreCase(userPermissions_timeclock)) {
        employeeDropdownList = ShowPunches.getActiveEmployeesForDropdown(tenantId_timeclock);
        String eidParam = request.getParameter("eid");
        if (ShowPunches.isValid(eidParam)) {
            try { globalEidForDisplay = Integer.parseInt(eidParam.trim()); } catch (NumberFormatException nfe) { globalEidForDisplay = 0; }
        } else {
            globalEidForDisplay = reportMode ? 0 : sessionEid_timeclock;
        }
    } else { 
        globalEidForDisplay = sessionEid_timeclock;
    }

    LocalDate periodStartDate = null; LocalDate periodEndDate = null;
    String payPeriodMessage = "Pay Period Not Set";
    DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);

    Map<String, LocalDate> periodInfo = ShowPunches.getCurrentPayPeriodInfo(tenantId_timeclock);
    if (periodInfo != null) {
        periodStartDate = periodInfo.get("startDate");
        periodEndDate = periodInfo.get("endDate");
        payPeriodMessage = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);
    } else {
        pageError = "Pay period dates not configured.";
    }

    Map<String, Object> employeeInfo = null; Integer tenantEmployeeNumberToDisplay = null;
    String employeeName = "N/A", department = "N/A", supervisor = "N/A", scheduleName = "N/A", scheduleTimeStr = "N/A", autoLunchStr = "Off", wageTypeStr = "N/A";
    double vacationHours = 0.0, sickHours = 0.0, personalHours = 0.0;
    StringBuilder timeclockPunchTableBuilder = new StringBuilder();
    double totalRegularHours = 0.0, totalOvertimeHours = 0.0, totalDoubleTimeHours = 0.0, periodTotalHours = 0.0;
    NumberFormat hoursFormatter = NumberFormat.getNumberInstance(Locale.US);
    hoursFormatter.setMinimumFractionDigits(2); hoursFormatter.setMaximumFractionDigits(2);

    if (globalEidForDisplay > 0 && pageError == null) {
        employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId_timeclock, globalEidForDisplay);
        if (employeeInfo != null) {
            employeeName = escapeHtml((String) employeeInfo.getOrDefault("employeeName", "N/A"));
            tenantEmployeeNumberToDisplay = (Integer) employeeInfo.get("tenantEmployeeNumber");
            department = escapeHtml((String) employeeInfo.getOrDefault("department", "N/A"));
            supervisor = escapeHtml((String) employeeInfo.getOrDefault("supervisor", "N/A"));
            scheduleName = escapeHtml((String) employeeInfo.getOrDefault("scheduleName", "N/A"));
            Time shiftStart = (Time) employeeInfo.get("shiftStart");
            Time shiftEnd = (Time) employeeInfo.get("shiftEnd");
            if (shiftStart != null && shiftEnd != null) {
                 scheduleTimeStr = shiftStart.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a")) + " - " + shiftEnd.toLocalTime().format(DateTimeFormatter.ofPattern("hh:mm a"));
            }
            autoLunchStr = (Boolean) employeeInfo.getOrDefault("autoLunch", false) ? "On" : "Off";
            wageTypeStr = escapeHtml((String) employeeInfo.getOrDefault("wageType", "N/A"));
            vacationHours = (Double) employeeInfo.getOrDefault("vacationHours", 0.0);
            sickHours = (Double) employeeInfo.getOrDefault("sickHours", 0.0);
            personalHours = (Double) employeeInfo.getOrDefault("personalHours", 0.0);

            Map<String, Object> timecardData = ShowPunches.getTimecardPunchData(tenantId_timeclock, globalEidForDisplay, periodStartDate, periodEndDate, employeeInfo, userTimeZoneId);
            if (timecardData != null) {
                if (timecardData.get("error") != null) {
                    pageError = (pageError == null ? "" : pageError + " ") + timecardData.get("error");
                } else {
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> punchesList = (List<Map<String, String>>) timecardData.get("punches");
                    if (punchesList != null && !punchesList.isEmpty()) {
                        for (Map<String, String> punch : punchesList) {
                            String inTime = escapeHtml(punch.get("timeIn"));
                            String outTime = escapeHtml(punch.get("timeOut"));
                            String inTimeCssClass = punch.get("inTimeCssClass");
                            String outTimeCssClass = punch.get("outTimeCssClass");
                            if (inTimeCssClass != null && !inTimeCssClass.isEmpty()) {
                                inTime = "<span class='" + inTimeCssClass + "'>" + inTime + "</span>";
                            }
                            if (outTimeCssClass != null && !outTimeCssClass.isEmpty()) {
                                outTime = "<span class='" + outTimeCssClass + "'>" + outTime + "</span>";
                            }

                            timeclockPunchTableBuilder.append("<tr>");
                            timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("dayOfWeek"))).append("</td>");
                            timeclockPunchTableBuilder.append("<td>").append(escapeHtml(punch.get("friendlyPunchDate"))).append("</td>");
                            timeclockPunchTableBuilder.append("<td>").append(inTime).append("</td>");
                            timeclockPunchTableBuilder.append("<td>").append(outTime).append("</td>");
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
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Time Card<%= titleSuffix %></title>
    
    <%-- [FIX START] This script block makes the location restriction setting available to timeclock.js --%>
    <script>
        const IS_LOCATION_RESTRICTION_ENABLED = <%= "true".equalsIgnoreCase(Configuration.getProperty(tenantId_timeclock, "RestrictByLocation", "false")) %>;
    </script>
    <%-- [FIX END] --%>
    
    <link rel="stylesheet" href="css/timeclock.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    
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
                    <tbody><%= timeclockPunchTableBuilder.toString() %></tbody>
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
                        <input type="hidden" name="latitude" id="latitude_IN" value="">
                         <input type="hidden" name="longitude" id="longitude_IN" value="">
                        <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneId_IN" value="">
                        <input type="hidden" name="deviceType" id="deviceType_IN" value="">
                        <button type="submit" class="punch-button punch-in">PUNCH IN</button>
                    </form>
                    <form id="punchOutForm" method="post" action="PunchInAndOutServlet" style="display: contents;">
                        <input type="hidden" name="punchAction" value="OUT">
                        <input type="hidden" name="punchEID" value="<%= globalEidForDisplay %>">
                        <input type="hidden" name="deviceFingerprintHash" id="deviceFingerprintHash_OUT" value="">
                        <input type="hidden" name="latitude" id="latitude_OUT" value="">
                        <input type="hidden" name="longitude" id="longitude_OUT" value="">
                         <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneId_OUT" value="">
                        <input type="hidden" name="deviceType" id="deviceType_OUT" value="">
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