<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="timeclock.Configuration"%>
<%@ page import="timeclock.payroll.ShowPayroll"%> <%-- Use ShowPayroll --%>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.DateTimeParseException" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %> <%-- Import List --%>
<%@ page import="java.text.NumberFormat" %>

<% /* --- Java Code Block (Calculate & Prepare Data) --- */ %>
<%
    boolean dataReady = false; // Track if data is ready for display
    String pageError = null;
    List<Map<String, Object>> calculatedData = null; // Store calculated data
    String payrollTableHtml = ""; // For display HTML
    String formattedGrandTotal = "$0.00"; // For display total

    String payPeriodMessage = "Not Yet Set";
    LocalDate periodStartDate = null;
    LocalDate periodEndDate = null;
    String longFormatPattern = "EEEE, MMMM d, uuuu";
    DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern(longFormatPattern, Locale.ENGLISH);
    NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);

    // --- Get Pay Period ---
    try {
        String startDateStr = Configuration.getProperty("PayPeriodStartDate"); String endDateStr = Configuration.getProperty("PayPeriodEndDate");
        if (ShowPayroll.isValid(startDateStr) && ShowPayroll.isValid(endDateStr)) { // Use helper if available
            try { periodStartDate = LocalDate.parse(startDateStr.trim()); periodEndDate = LocalDate.parse(endDateStr.trim()); payPeriodMessage = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter); }
            catch (DateTimeParseException e) { pageError = "Invalid Date Format in Settings"; }
        } else { pageError = "Pay period dates not found in settings."; }
    } catch (Exception e) { pageError = "Error retrieving period settings"; e.printStackTrace(); }

    // --- Calculate Payroll Data & Prepare Display (if period is valid) ---
    if (periodStartDate != null && periodEndDate != null && pageError == null) {
        try {
            // STEP 1: Perform the calculation (NO DB Update)
            calculatedData = ShowPayroll.calculatePayrollData(periodStartDate, periodEndDate);

            // STEP 2: Generate HTML display from calculated data
            Map<String, Object> displayData = ShowPayroll.showPayroll(calculatedData); // Pass calculated data
            payrollTableHtml = (String) displayData.getOrDefault("payrollHtml", "<tr><td colspan='9' class='report-error-row'>Error formatting calculated data.</td></tr>");
            double grandTotalValue = (Double) displayData.getOrDefault("grandTotal", 0.0);
            formattedGrandTotal = currencyFormatter.format(grandTotalValue);
            dataReady = true; // Mark data as ready for display

        } catch (Exception e) { pageError = "Error calculating or displaying payroll data."; e.printStackTrace(); payrollTableHtml = "<tr><td colspan='9' class='report-error-row'>Error calculating/displaying payroll. Check logs.</td></tr>"; }
    } else if (pageError == null) { pageError = "Pay period dates not set or invalid."; }

    // --- Check for redirect messages ---
    String successMessage = request.getParameter("message"); String errorMessage = request.getParameter("error");
    if (errorMessage != null && !errorMessage.isEmpty()) { pageError = errorMessage; successMessage = null; dataReady = false; }
%>

<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"> <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Payroll</title>
    <link rel="stylesheet" href="css/payroll.css?v=13"> <link rel="stylesheet" href="css/navbar.css">
</head>
<body>
    <div class="parent-container">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
        <h1>Payroll Processing</h1>
        <h2><strong>Pay Period: <%= payPeriodMessage %></strong></h2>

        <%-- Display Success/Error Bar --%>
        <% if (successMessage != null && !successMessage.isEmpty()) { %><div id="notification-bar" class="success-message"><%= successMessage %></div><% } else if (pageError != null && !pageError.isEmpty()) { %><div id="notification-bar" class="error-message"><%= pageError %></div><% } %>

        <%-- Show content only if data is ready OR there was only a processing error (show error in table) --%>
        <% if(dataReady || (pageError != null && !pageError.contains("settings") && !pageError.contains("Pay period dates"))) { %>
            <p class="instructions"> Run the 'Exception Report' first to check for missing punches before closing the pay period. </p>
            <%-- Payroll Table --%>
            <div id="payroll-table-container" class="table-container"> <table id="payrollTable" class="punches"> <thead> <tr> <th>Employee ID</th><th>First Name</th><th>Last Name</th><th>Wage Type</th> <th>Regular Hours</th><th>Overtime Hours</th><th>Total Hours</th><th>Wage</th> <th style="text-align: right;">Total Pay</th> </tr> </thead> <tbody> <%= payrollTableHtml %> </tbody> <tfoot> <tr> <td colspan="8" style="text-align: right; font-weight: bold;">Payroll Grand Total:</td> <td style="text-align: right; font-weight: bold;"><%= formattedGrandTotal %></td> </tr> </tfoot> </table> </div>
            <%-- Action Buttons --%>
            <div class="button-container"> <button id="btnExceptionReport" type="button" name="btnExceptionReport">Exception Report</button> <form method="post" action="PayrollServlet" style="margin: 0; padding: 0; display: inline;"> <input type="hidden" name="action" value="exportPayroll"> <input id="btnExportPayroll" name="btnExportPayroll" type="submit" value="Export Payroll"> </form> <button id="btnPrintPayroll" name="btnPrintPayroll" type="button">Print</button> <form method="post" action="PayrollServlet" style="margin: 0; padding: 0; display: inline;" onsubmit="return confirmClosePeriod();"> <input type="hidden" name="action" value="closePayPeriod"> <button id="btnClosePayPeriod" name="btnClosePayPeriod" type="submit">Close Pay Period</button> </> </form> </div>
        <% } else { %> <p style="text-align: center; margin-top: 30px; font-weight:bold;"> <%= pageError != null ? pageError : "Payroll data could not be loaded." %> </p> <% } %>
    </div> <%-- End parent-container --%>

    <%-- MODALS (Unchanged) --%>
    <div id="notificationModal" class="modal"> <div class="modal-content"> <span class="close-button" id="closeNotificationModal">&times;</span> <p id="notificationMessage"></p> <button id="okButton" class="modal-ok-button">OK</button> </div> </div>
    <div id="exceptionReportModal" class="report-modal modal"> <div class="modal-content report-modal-content"> <span class="close-button" id="closeExceptionReportModal">&times;</span> <h2>Exception Report</h2> <p id="exceptionReportInstructions" class="modal-instructions"><i>Select a row below to make corrections.</i></p> <div class="modal-table-container"> <table id="exceptionReportTable"> <thead><tr><th>EID</th><th>First Name</th><th>Last Name</th><th>Date</th><th>IN Time</th><th>OUT Time</th></tr></thead> <tbody id="exceptionReportTbody"></tbody> </table> </div> <div class="modal-footer"> <button id="editExceptionButton" class="modal-edit-button" disabled>Fix Missing Punches</button> <button id="closeExceptionReportButton" class="modal-ok-button" type="button" style="background-color: #aaa;">Close</button> </div> </div> </div>
    <div id="editPunchModal" class="modal"> <div class="modal-content"> <span class="close-button" id="closeEditPunchModal">&times;</span> <h2>Edit Punch Record</h2> <div class="edit-punch-info"> <strong id="editPunchEmployeeName"></strong><br> <span id="editPunchScheduleInfo"></span> </div> <hr style="margin: 15px 0;"> <form id="editPunchForm" action="AddEditAndDeletePunchesServlet" method="post"> <input type="hidden" id="editPunchIdField" name="editPunchId"> <input type="hidden" name="action" value="editPunch"> <input type="hidden" id="editPunchEmployeeIdField" name="editEmployeeId" value=""> <input type="hidden" id="editUserTimeZone" name="userTimeZone" value="<%= (String) session.getAttribute("userTimeZoneId") != null ? (String) session.getAttribute("userTimeZoneId") : "UTC" %>"> <div class="form-row"> <label for="editDate">Date:</label> <input type="date" id="editDate" name="editDate" required> </div> <div class="form-row"> <label for="editInTime">IN Time (HH:MM:SS):</label> <input type="time" id="editInTime" name="editInTime" step="1"> </div> <div class="form-row"> <label for="editOutTime">OUT Time (HH:MM:SS):</label> <input type="time" id="editOutTime" name="editOutTime" step="1"> </div> <div class="form-row"> <label for="editPunchType">Punch Type:</label> <select id="editPunchType" name="editPunchType" required> <option value="User Initiated">User Initiated</option> <option value="Supervisor Override">Supervisor Override</option> <option value="Vacation Time">Vacation Time</option> <option value="Sick Time">Sick Time</option> <option value="Personal Time">Personal Time</option> <option value="Holiday Time">Holiday Time</option> <option value="Bereavement">Bereavement</option> <option value="Other">Other</option> </select> </div> <div class="modal-footer"> <button type="submit">Save Changes</button> <button type="button" id="cancelEditPunch">Cancel</button> </div> </form> </div> </div>

<script> function confirmClosePeriod() { const warningMessage = "Are you sure you want to close the current pay period?\n\nThis will UPDATE calculated Overtime, archive punches, run accruals, and set the next period dates.\n\nPlease ensure all reports and adjustments are complete first.\nThis action CANNOT be easily undone."; return window.confirm(warningMessage); } </script>
<script src="js/payroll.js?v=13"></script>
</body>
</html>