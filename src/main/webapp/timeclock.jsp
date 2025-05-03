<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="false" %> <%-- Session set to false --%>
<%@ page import="timeclock.Configuration" %>
<%@ page import="timeclock.punches.ShowPunches" %> <%-- Used for data fetching --%>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.DateTimeParseException" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.sql.Time" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.ArrayList" %>

<% /* --- Java Code Block --- */ %>
<%
    String pageError = null;
    // Message/Error parameters are now handled by JavaScript Modal
    // String successMessage = request.getParameter("message"); // Read by JS
    // String generalError = request.getParameter("error"); // Read by JS

    String userTimeZoneId = "America/Denver"; // Consider making dynamic

    int employeeIdToDisplay = 0;
    String eidParam = request.getParameter("eid"); // EID from redirect or dropdown submit
    if (ShowPunches.isValid(eidParam)) {
        try {
            employeeIdToDisplay = Integer.parseInt(eidParam.trim());
            if (employeeIdToDisplay <= 0) { pageError = "Invalid Employee ID in URL."; employeeIdToDisplay = 0; }
        } catch (NumberFormatException nfe) { pageError = "Invalid EID format in URL."; employeeIdToDisplay = 0; }
    }
    final int eid = employeeIdToDisplay; // Use final variable

    // Get Pay Period Info
    LocalDate periodStartDate = null; LocalDate periodEndDate = null; String payPeriodMessage = "Pay Period Not Set";
    DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);
    try {
        Map<String, LocalDate> periodInfo = ShowPunches.getCurrentPayPeriodInfo();
        if (periodInfo != null) {
            periodStartDate = periodInfo.get("startDate"); periodEndDate = periodInfo.get("endDate");
            if (periodStartDate != null && periodEndDate != null) {
                payPeriodMessage = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);
            } else { pageError = (pageError == null ? "" : pageError + " ") + "Pay period dates invalid.";}
        } else { if (pageError == null) pageError = "Pay period dates not found in settings."; }
    } catch (Exception e) { if (pageError == null) pageError = "Error retrieving period settings."; e.printStackTrace(); }

    // Prepare display variables
    Map<String, Object> employeeInfo = null; Map<String, Object> timecardData = null;
    String employeeName = "N/A"; String scheduleName = "N/A"; String scheduleTimeStr = ""; String autoLunchStr = "Off"; boolean isScheduleOpen = true;
    String punchTableHtml = "";
    double totalRegularHours = 0.0; double totalOvertimeHours = 0.0; double periodTotalHours = 0.0;
    double vacationHours = 0.0; double sickHours = 0.0; double personalHours = 0.0;
    NumberFormat hoursFormatter = NumberFormat.getNumberInstance(); hoursFormatter.setMinimumFractionDigits(2); hoursFormatter.setMaximumFractionDigits(2);

    // Get employee list for dropdown
    List<Map<String, Object>> employeeDropdownList = ShowPunches.getActiveEmployeesForDropdown();

    // Fetch Timecard Data
    if (eid > 0 && pageError == null && periodStartDate != null && periodEndDate != null) {
        try {
            employeeInfo = ShowPunches.getEmployeeTimecardInfo(eid);
            if (employeeInfo != null) {
                // Populate employee details
                employeeName = (String) employeeInfo.getOrDefault("employeeName", "Error");
                scheduleName = (String) employeeInfo.getOrDefault("scheduleName", "N/A"); isScheduleOpen = "Open".equalsIgnoreCase(scheduleName);
                Time shiftStart = (Time) employeeInfo.get("shiftStart"); Time shiftEnd = (Time) employeeInfo.get("shiftEnd"); DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a"); scheduleTimeStr = (shiftStart != null && shiftEnd != null) ? shiftStart.toLocalTime().format(tf) + " - " + shiftEnd.toLocalTime().format(tf) : "N/A";
                boolean autoLunch = (Boolean) employeeInfo.getOrDefault("autoLunch", false); Integer hrsReq = (Integer) employeeInfo.get("hoursRequired"); Integer lunchLen = (Integer) employeeInfo.get("lunchLength"); autoLunchStr = autoLunch ? "On | Req: " + (hrsReq != null ? hrsReq : "?") + "hr | Len: " + (lunchLen != null ? lunchLen : "?") + "m" : "Off";
                vacationHours = (Double) employeeInfo.getOrDefault("vacationHours", 0.0); sickHours = (Double) employeeInfo.getOrDefault("sickHours", 0.0); personalHours = (Double) employeeInfo.getOrDefault("personalHours", 0.0);

                // Get timecard HTML and calculated totals
                timecardData = ShowPunches.getTimecardPunchData(eid, periodStartDate, periodEndDate, employeeInfo, userTimeZoneId);
                if (timecardData != null) {
                    punchTableHtml = (String) timecardData.getOrDefault("punchTableHtml", "<tr><td colspan='5' class='report-error-row'>Error loading punches.</td></tr>");
                    totalRegularHours = (Double) timecardData.getOrDefault("totalRegularHours", 0.0);
                    totalOvertimeHours = (Double) timecardData.getOrDefault("totalOvertimeHours", 0.0);
                    periodTotalHours = Math.round((totalRegularHours + totalOvertimeHours) * 100.0) / 100.0;
                } else { if(pageError==null)pageError="Error processing timecard data."; punchTableHtml = "<tr><td colspan='5' class='report-error-row'>Error processing punches.</td></tr>"; }
            } else { if(pageError==null)pageError="Employee data not found: EID=" + eid; employeeName = "Not Found"; punchTableHtml = "<tr><td colspan='5' style='text-align:center;'>Employee data not found.</td></tr>"; }
        } catch (Exception e) { if(pageError==null)pageError="Unexpected error loading timecard."; e.printStackTrace(); punchTableHtml = "<tr><td colspan='5' class='report-error-row'>Error loading timecard. Check logs.</td></tr>"; }
    } else if (eid <= 0 && pageError == null) {
        punchTableHtml = "<tr><td colspan='5' style='text-align:center; font-style:italic; padding: 20px;'>Please select an employee to view timecard.</td></tr>";
    } else if (pageError != null) {
         punchTableHtml = "<tr><td colspan='5' class='report-error-row'>" + pageError + "</td></tr>";
    }

    String formattedVacation = hoursFormatter.format(vacationHours); String formattedSick = hoursFormatter.format(sickHours); String formattedPersonal = hoursFormatter.format(personalHours);
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Time Clock<%= eid > 0 ? " - " + employeeName : "" %></title>
    <link rel="stylesheet" href="css/timeclock.css?v=7">
    <link rel="stylesheet" href="css/navbar.css">
    <link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin><link href="https://fonts.googleapis.com/css2?family=Orbitron:wght@400..900&display=swap" rel="stylesheet">
    <%-- Ensure Modal CSS is included (e.g., in timeclock.css) --%>
    <style>
        /* Basic Modal Styles (Include here OR in timeclock.css) */
        .modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); align-items: center; justify-content: center; }
        .modal.modal-visible { display: flex; }
        .modal-content { background-color: #fefefe; margin: auto; padding: 25px 30px; border: 1px solid #888; width: 90%; max-width: 450px; border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.2); text-align: center; position: relative; margin-top: -10vh; /* Position slightly above center */ }
        .modal-content h2 { margin-top: 0; color: #333; font-size: 1.4em; margin-bottom: 15px; }
        .modal-content p { margin-top: 0; margin-bottom: 25px; font-size: 1.1em; line-height: 1.6; color: #333; text-align: left; }
        .modal-content.error p { color: #a94442; font-weight: bold; }
        .close-button { color: #aaa; position: absolute; top: 10px; right: 15px; font-size: 28px; font-weight: bold; line-height: 1; cursor: pointer; }
        .close-button:hover, .close-button:focus { color: black; text-decoration: none; }
        .modal-footer { margin-top: 20px; padding-top: 15px; border-top: 1px solid #eee; display: flex; justify-content: center; gap: 10px; }
        .modal-ok-button { background-color: #5cb85c; color: white; padding: 10px 24px; border: none; border-radius: 4px; cursor: pointer; font-size: 1em; font-weight: bold; transition: background-color 0.2s; }
        .modal-ok-button:hover { background-color: #4cae4c; }
        .modal-content.error .modal-ok-button { background-color: #c13030; } .modal-content.error .modal-ok-button:hover { background-color: #a12828; }
    </style>
</head>
<body>

    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div id="main-clock-container"><jsp:include page="clock.html" /></div>

    <div class="employee-selector-container">
        <label for="employeeSelect">View Employee:</label>
        <select id="employeeSelect" name="eid_select" onchange="navigateToEmployee(this.value)">
            <option value="">-- Select Employee --</option>
            <% if (employeeDropdownList == null || employeeDropdownList.isEmpty()) { %>
                <option value="" disabled>No active employees found</option>
            <% } else { %>
                <% for (Map<String, Object> emp : employeeDropdownList) { %>
                    <% int currentDropdownEid = (Integer) emp.get("eid"); String displayName = (String) emp.get("displayName"); %>
                    <option value="<%= currentDropdownEid %>" <%= (currentDropdownEid == eid) ? "selected" : "" %>>
                        <%= displayName %> (<%= currentDropdownEid %>)
                    </option>
                <% } %>
            <% } %>
        </select>
    </div>

    <div class="timecard-container">
        <div class="timecard">
            <div class="timecard-header">
                <h1>Time Card</h1>
                <div class="timecard-pay-period"><%= payPeriodMessage %></div>
            </div>

            <%-- Notification Bar for initial page load errors --%>
            <% if (pageError != null && !pageError.isEmpty()) { %>
                <div id="notification-bar" class="error-message"><%= pageError %></div>
            <% } %>

             <%-- Display timecard content --%>
             <% if(eid > 0 && employeeInfo != null && pageError == null) { %>
                <div class="timecard-info">
                    <div class="info-left">
                        <div><strong>EID:</strong> <%= eid %></div>
                        <div><strong>Employee:</strong> <%= employeeName %></div>
                    </div>
                    <div class="info-right">
                        <div><strong>Schedule:</strong> <%= scheduleName %></div>
                        <% if (!isScheduleOpen) { %>
                            <div><strong>Hours:</strong> <%= scheduleTimeStr %></div>
                        <% } %>
                         <div><strong>Auto Lunch:</strong> <%= autoLunchStr %></div>
                    </div>
                </div>

                <div class="table-container timecard-table-container">
                    <table id="punches" class="punches timecard-table">
                        <thead> <tr> <th>Date</th><th>IN</th><th>OUT</th><th>Total Hours</th><th>Punch Type</th> </tr> </thead>
                        <tbody> <%= punchTableHtml %> </tbody>
                         <tfoot> <tr> <td colspan="3" style="text-align: right; font-weight: bold; border-top: 2px solid #b0a991;">Period Totals:</td> <td style="text-align: right; font-weight: bold; border-top: 2px solid #b0a991;"><%= String.format("%.2f", periodTotalHours) %></td> <td style="font-weight: normal; font-size: 0.9em; border-top: 2px solid #b0a991;"> <% if("Hourly".equalsIgnoreCase((String)employeeInfo.getOrDefault("wageType",""))) { %> (Reg: <%= String.format("%.2f", totalRegularHours) %> | OT: <%= String.format("%.2f", totalOvertimeHours) %>) <% } %> </td> </tr> </tfoot>
                    </table>
                </div>

                <div class="accrual-balances">
                    <h3 class="accrual-title">Available Accrued Hours</h3>
                    <div class="balance-item"> <span class="balance-label">Vacation:</span> <span class="balance-value"><%= formattedVacation %></span> </div>
                    <div class="balance-item"> <span class="balance-label">Sick:</span> <span class="balance-value"><%= formattedSick %></span> </div>
                    <div class="balance-item"> <span class="balance-label">Personal:</span> <span class="balance-value"><%= formattedPersonal %></span> </div>
                </div>

                <div class="punch-buttons">
                    <form method="post" action="PunchInAndOutServlet" style="display: contents;"> <input type="hidden" name="punchAction" value="IN"> <input type="hidden" name="punchEID" value="<%= eid %>"> <button type="submit" class="punch-button punch-in">PUNCH IN</button> </form>
                    <form method="post" action="PunchInAndOutServlet" style="display: contents;"> <input type="hidden" name="punchAction" value="OUT"> <input type="hidden" name="punchEID" value="<%= eid %>"> <button type="submit" class="punch-button punch-out">PUNCH OUT</button> </form>
                </div>
            <% } else { %>
                 <%-- Show empty table structure or error message --%>
                 <div class="table-container timecard-table-container"> <table class="punches timecard-table"> <thead> <tr> <th>Date</th><th>IN</th><th>OUT</th><th>Total Hours</th><th>Punch Type</th> </tr> </thead> <tbody> <%= punchTableHtml %> </tbody> </table> </div>
                 <% if (pageError == null) { %>
                     <p style="text-align: center; margin-top: 20px; font-style: italic;">Select an employee from the dropdown above to view their timecard.</p>
                 <% } %>
            <% } %>

        </div> <%-- End timecard div --%>
    </div> <%-- End timecard-container div --%>

    <%-- Notification Modal HTML Structure --%>
    <div id="notificationModal" class="modal">
        <div class="modal-content">
            <span class="close-button" id="closeNotificationModal">&times;</span>
            <h2>Notification</h2>
            <p id="notificationMessage"></p>
            <div class="modal-footer" style="justify-content: center;">
                <button type="button" id="okButton" class="modal-ok-button">OK</button>
            </div>
        </div>
    </div>

    <%-- Link External JavaScript file --%>
    <script src="js/timeclock.js?v=1"></script> <%-- Link the new JS file --%>

</body>
</html>