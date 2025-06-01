<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.util.Locale" %>
<%@ page import="timeclock.punches.ShowPunches" %> <%-- For ShowPunches.escapeHtml if needed, or use local --%>

<%!
    // Helper to escape HTML, can be removed if ShowPunches.escapeHtml is used and preferred
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
    String pageTitle = (String) request.getAttribute("pageTitle");
    String payPeriodMessage = (String) request.getAttribute("payPeriodMessageForPrint");
    String globalErrorMessage = (String) request.getAttribute("errorMessage");


    if (pageTitle == null) pageTitle = "Time Card Print View";
    if (payPeriodMessage == null) payPeriodMessage = "Pay Period Not Set";

    NumberFormat hoursPrintFormatter = NumberFormat.getNumberInstance(Locale.US);
    hoursPrintFormatter.setMinimumFractionDigits(2);
    hoursPrintFormatter.setMaximumFractionDigits(2);
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Print Preview: <%= escapeJspHtml(pageTitle) %></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/timeclock.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/print_timecards_view.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="print-preview-body">

    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="print-controls">
        <div class="report-context-message"><%= escapeJspHtml(pageTitle) %></div>
        <button onclick="window.print();" class="print-action-btn">Print All Time Cards</button>
        <button onclick="window.location.href='${pageContext.request.contextPath}/reports.jsp';" class="close-btn">Close Preview</button>
    </div>

    <div class="print-view-main-content">
        <% if (globalErrorMessage != null && !globalErrorMessage.isEmpty()) { %>
            <div class="timecard-print-page" style="text-align:center; padding: 20px; background-color: #fff1f1; border: 1px solid #e0b4b4;">
                <h2 style="color: #721c24;">Error Generating Report</h2>
                <p style="color: #721c24;"><%= escapeJspHtml(globalErrorMessage) %></p>
                <p><a href="${pageContext.request.contextPath}/reports.jsp">Return to Reports Menu</a></p>
            </div>
        <% } else if (timecards != null && !timecards.isEmpty()) { %>
            <% for (Map<String, Object> cardData : timecards) { %>
                <div class="timecard-print-page timecard">
                    <div class="timecard-header">
                        <%-- Use displayEmployeeId provided by the servlet --%>
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
                        <table class="punches timecard-table">
                            <thead>
                                <tr>
                                    <th>Day</th> <%-- Added Day column --%>
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
                                        <td>
                                            <% String inTimeCssClass = punch.get("inTimeCssClass");
                                               if (inTimeCssClass != null && !inTimeCssClass.isEmpty()) { %><span class="<%= escapeJspHtml(inTimeCssClass) %>"><% } %>
                                            <%= escapeJspHtml(punch.get("timeIn")) %>
                                            <% if (inTimeCssClass != null && !inTimeCssClass.isEmpty()) { %></span><% } %>
                                        </td>
                                        <td>
                                            <% String outTimeCssClass = punch.get("outTimeCssClass");
                                               if (outTimeCssClass != null && !outTimeCssClass.isEmpty()) { %><span class="<%= escapeJspHtml(outTimeCssClass) %>"><% } %>
                                            <%= escapeJspHtml(punch.get("timeOut")) %>
                                            <% if (outTimeCssClass != null && !outTimeCssClass.isEmpty()) { %></span><% } %>
                                        </td>
                                        <td style="text-align:right;"><%= escapeJspHtml(punch.get("totalHours")) %></td>
                                        <td><%= escapeJspHtml(punch.get("punchType")) %></td>
                                    </tr>
                                <%
                                        } // End for punch
                                    } else {
                                %>
                                    <tr><td colspan="6" class="report-message-row">No punch data for this period.</td></tr>
                                <%
                                    } // End if/else punchesList
                                %>
                            </tbody>
                            <tfoot>
                                <tr>
                                    <td colspan="4" style="text-align: right; font-weight: bold;">Period Totals:</td> <%-- Colspan updated --%>
                                    <td style="text-align: right; font-weight: bold;">
                                        <%= hoursPrintFormatter.format(cardData.getOrDefault("periodTotalHours", 0.0)) %>
                                    </td>
                                    <td style="font-weight: normal; font-size: 0.9em;">
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
                        <h3 class="accrual-title">Available Accrued Hours</h3>
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
                </div> <%-- End timecard-print-page --%>
            <% } // End for cardData %>
        <% } else { %>
            <div style="text-align:center; padding: 30px; background-color: #fff; border: 1px solid #ddd; border-radius: 5px; max-width: 600px; margin: 20px auto;">
                <h2>Report Generated</h2>
                <p>No timecards to display or print for the selected criteria.</p>
                 <p><a href="${pageContext.request.contextPath}/reports.jsp">Return to Reports Menu</a></p>
            </div>
        <% } // End if timecards %>
    </div> <%-- End print-view-main-content --%>

</body>
</html>