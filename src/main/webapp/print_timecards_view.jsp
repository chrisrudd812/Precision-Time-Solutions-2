<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Locale" %>
<%@ page import="timeclock.punches.ShowPunches" %>
<%@ page import="com.google.gson.Gson" %>

<%!
    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
%>
<%
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> timecards = (List<Map<String, Object>>) request.getAttribute("printableTimecards");
    @SuppressWarnings("unchecked")
    List<Map<String, String>> allEmployees = (List<Map<String, String>>) request.getAttribute("allEmployees");
    String pageTitle = (String) request.getAttribute("pageTitle");
    String payPeriodMessage = (String) request.getAttribute("payPeriodMessageForPrint");
    String globalErrorMessage = (String) request.getAttribute("errorMessage");
    String selectedEmployeeId = (String) request.getAttribute("selectedEmployeeId");

    boolean isSingleTimecard = (timecards != null && timecards.size() == 1);

    if (pageTitle == null) pageTitle = "Time Card Print View";
    if (payPeriodMessage == null) payPeriodMessage = "Pay Period Not Set";
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Print Preview: <%= escapeJspHtml(pageTitle) %></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/print_timecards_view.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="print-preview-body reports-page">

    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="print-controls">
        <div class="report-context-message"><%= escapeJspHtml(pageTitle) %></div>
        <div>
            <button id="printTimecardsBtn" onclick="window.print();" class="print-action-btn glossy-button text-blue"><i class="fas fa-print"></i> <%= isSingleTimecard ? "Print" : "Print All" %></button>
            <button id="emailTimecardsBtn" class="print-action-btn glossy-button text-purple"><i class="fas fa-envelope"></i> <%= isSingleTimecard ? "Email" : "Email All" %></button>
            <button id="closeTabBtn" class="print-action-btn glossy-button text-red"><i class="fas fa-times-circle"></i> Close</button>
        </div>
    </div>

    <% if (isSingleTimecard) { %>
    <div class="employee-selector-section">
        <div class="selector-container">
            <label for="employeeSelect">Select Employee:</label>
            <select id="employeeSelect" onchange="loadEmployeeTimecard()">
                <option value="">-- Choose Employee --</option>
                <% if (allEmployees != null) {
                    for (Map<String, String> emp : allEmployees) {
                        String eid = emp.get("eid");
                        String name = emp.get("name");
                        String selected = (selectedEmployeeId != null && selectedEmployeeId.equals(eid)) ? "selected" : "";
                %>
                    <option value="<%= eid %>" <%= selected %>><%= escapeJspHtml(name) %></option>
                <% }} %>
            </select>
        </div>
    </div>
    <% } %>

    <div class="print-view-main-content">
        <%
            // [FIX] Moved the formatter declaration here to resolve the scope issue.
            NumberFormat hoursPrintFormatter = NumberFormat.getNumberInstance(Locale.US);
            hoursPrintFormatter.setMinimumFractionDigits(2);
            hoursPrintFormatter.setMaximumFractionDigits(2);

            if (globalErrorMessage != null && !globalErrorMessage.isEmpty()) {
        %>
            <div class="timecard-print-page" style="text-align:center; padding: 20px; background-color: #fff1f1; border: 1px solid #e0b4b4;">
                <h2 style="color: #721c24;">Error Generating Report</h2>
                <p style="color: #721c24;"><%= escapeJspHtml(globalErrorMessage) %></p>
                <p><a href="${pageContext.request.contextPath}/reports.jsp">Return to Reports Menu</a></p>
            </div>
        <% } else if (timecards != null && !timecards.isEmpty()) { %>
             <% for (Map<String, Object> cardData : timecards) { %>
                <div class="timecard-print-page timecard">
                    <div class="timecard-header">
                        <h1><%= escapeJspHtml((String)cardData.getOrDefault("employeeName", "N/A")) %> (<%= escapeJspHtml((String)cardData.getOrDefault("displayEmployeeId", "ID N/A")) %>)</h1>
                        <div class="timecard-pay-period"><%= escapeJspHtml(payPeriodMessage) %></div>
                    </div>
                    <div class="timecard-info">
                         <div class="info-left">
                            <div><strong>Department:</strong> <%= escapeJspHtml((String)cardData.getOrDefault("department", "N/A")) %></div>
                            <div><strong>Supervisor:</strong> <%= escapeJspHtml((String)cardData.getOrDefault("supervisor", "N/A")) %></div>
                        </div>
                        <div class="info-right">
                             <div><strong>Schedule:</strong> <%= escapeJspHtml((String)cardData.getOrDefault("scheduleName", "N/A")) %></div>
                             <% if(!(Boolean)cardData.getOrDefault("isScheduleOpen", true)) { %>
                                 <div><strong>Hours:</strong> <%= escapeJspHtml((String)cardData.getOrDefault("scheduleTimeStr", "N/A")) %></div>
                             <% } %>
                             <div><strong>Auto Lunch:</strong> <%= escapeJspHtml((String)cardData.getOrDefault("autoLunchStr", "Off")) %></div>
                         </div>
                    </div>
                    <div class="timecard-table-container">
                        <table class="punches report-table">
                             <thead>
                                <tr>
                                    <th>Day</th>
                                    <th>Date</th>
                                    <th>IN</th>
                                    <th>OUT</th>
                                    <th>Total Hours</th>
                                    <th>Punch Type</th>
                                </tr>
                              </thead>
                            <tbody>
                                <%
                                     String punchTableError = (String) cardData.get("punchTableError");
                                     @SuppressWarnings("unchecked")
                                    List<Map<String, String>> punchesList = (List<Map<String, String>>) cardData.get("punchesList");
                                     if (punchTableError != null && !punchTableError.isEmpty()) {
                                %>
                                    <tr><td colspan="6" class="report-error-row"><%= escapeJspHtml(punchTableError) %></td></tr>
                                <%
                                    } else if (punchesList != null && !punchesList.isEmpty()) {
                                        for (Map<String, String> punch : punchesList) {
                                %>
                                    <tr>
                                        <td><%= escapeJspHtml(punch.get("dayOfWeek")) %></td>
                                        <td><%= escapeJspHtml(punch.get("friendlyPunchDate")) %></td>
                                        <td><%= punch.get("timeIn") %></td>
                                        <td><%= punch.get("timeOut") %></td>
                                        <td style="text-align:right;"><%= escapeJspHtml(punch.get("totalHours")) %></td>
                                        <td><%= escapeJspHtml(punch.get("punchType")) %></td>
                                    </tr>
                                <%
                                        }
                                    } else {
                                %>
                                    <tr><td colspan="6" class="report-message-row">No punch data for this period.</td></tr>
                                <%
                                     }
                                %>
                            </tbody>
                             <tfoot>
                                <tr>
                                    <td colspan="4" style="text-align: right; font-weight: bold;">Period Totals:</td>
                                    <td style="text-align: right; font-weight: bold;">
                                        <%= hoursPrintFormatter.format(cardData.getOrDefault("periodTotalHours", 0.0)) %>
                                    </td>
                                    <%-- [FIX] Added the 'hours-breakdown' class --%>
                                    <td class="hours-breakdown">
                                         <% if("Hourly".equalsIgnoreCase((String)cardData.getOrDefault("wageType",""))) {
                                              double totalRegular = (Double)cardData.getOrDefault("totalRegularHours", 0.0);
                                              double totalOvertime = (Double)cardData.getOrDefault("totalOvertimeHours", 0.0);
                                              double totalDoubleTime = (Double)cardData.getOrDefault("totalDoubleTimeHours", 0.0);
                                         %>
                                            (Reg: <%= hoursPrintFormatter.format(totalRegular) %> |
                                              OT: <%= hoursPrintFormatter.format(totalOvertime) %>
                                             <% if (totalDoubleTime > 0.001) { %> | DT: <%= hoursPrintFormatter.format(totalDoubleTime) %><% } %>)
                                         <% } %>
                                    </td>
                                </tr>
                             </tfoot>
                        </table>
                    </div>
                    <div class="accrual-balances">
                         <h3 class="accrual-title">Accrued PTO Hours</h3>
                        <div class="balance-item">
                            <span class="balance-label">Vacation:</span>
                             <span class="balance-value"><%= escapeJspHtml((String)cardData.getOrDefault("formattedVacation", "0.00")) %></span>
                        </div>
                        <div class="balance-item">
                            <span class="balance-label">Sick:</span>
                             <span class="balance-value"><%= escapeJspHtml((String)cardData.getOrDefault("formattedSick", "0.00")) %></span>
                        </div>
                        <div class="balance-item">
                            <span class="balance-label">Personal:</span>
                             <span class="balance-value"><%= escapeJspHtml((String)cardData.getOrDefault("formattedPersonal", "0.00")) %></span>
                        </div>
                    </div>
                </div>
             <% } %>
        <% } else { %>
            <div class="timecard-print-page">
                <h2>Report Generated</h2>
                <p>No timecards to display or print for the selected criteria.</p>
            </div>
        <% } %>
    </div>

    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>
    <script>
        window.appRootPath = "<%= request.getContextPath() %>";
        window.payPeriodMessage = "<%= payPeriodMessage.replace("\"", "\\\"") %>";
        window.timecardDataForEmail = JSON.parse('<%= new Gson().toJson(timecards) %>');
        
        function loadEmployeeTimecard() {
            const select = document.getElementById('employeeSelect');
            const selectedEid = select.value;
            if (selectedEid) {
                window.location.href = window.appRootPath + '/PrintTimecardsServlet?filterType=single&filterValue=' + selectedEid;
            }
        }
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/print_timecards_view.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>