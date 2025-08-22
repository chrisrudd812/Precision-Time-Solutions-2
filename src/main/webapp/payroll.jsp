<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.Configuration" %>
<%@ page import="timeclock.payroll.ShowPayroll" %>
<%@ page import="timeclock.punches.ShowPunches" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.DateTimeParseException" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>
<%@ page import="java.util.ArrayList" %>

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
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    if (currentSession != null) {
        tenantId = (Integer) currentSession.getAttribute("TenantID");
    }

    if (tenantId == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name()));
        return;
    }

    String userTimeZoneId = Configuration.getProperty(tenantId, "DefaultTimeZone", "America/Denver");
    String pageError = null;
    boolean dataReady = false;
    String payrollTableHtml = "";
    String formattedGrandTotal = "$0.00";
    String payPeriodMessage = "Pay Period Not Set";
    LocalDate periodStartDate = null;
    LocalDate periodEndDate = null;
    DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);

    try {
        String startDateStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
        String endDateStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");
        if (startDateStr != null && endDateStr != null) {
            periodStartDate = LocalDate.parse(startDateStr.trim());
            periodEndDate = LocalDate.parse(endDateStr.trim());
            payPeriodMessage = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);

            List<Map<String, Object>> calculatedData = ShowPayroll.calculatePayrollData(tenantId, periodStartDate, periodEndDate);
            Map<String, Object> displayData = ShowPayroll.showPayroll(calculatedData);
            payrollTableHtml = (String) displayData.get("payrollHtml");
            double grandTotalValue = (Double) displayData.get("grandTotal");
            formattedGrandTotal = NumberFormat.getCurrencyInstance(Locale.US).format(grandTotalValue);
            dataReady = true;
        } else {
            pageError = "Pay period start/end dates not found in settings.";
        }
    } catch (Exception e) {
        pageError = "Error calculating payroll: " + e.getMessage();
        Logger.getLogger("payroll.jsp").log(Level.SEVERE, "Error on payroll.jsp for TenantID " + tenantId, e);
    }
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Payroll Processing</title>
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/payroll.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="reports-page payroll-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reports-container">
        <h1>Payroll Processing</h1>
        <h2><%= escapeJspHtml(payPeriodMessage) %></h2>

        <% if (pageError != null) { %>
            <div class="page-message error-message"><%= escapeJspHtml(pageError) %></div>
        <% } %>

        <% if(dataReady) { %>
            <p class="instructions"> Review payroll details below. Run the 'Exception Report' to check for missing punches before closing the pay period. </p>
            <div id="payroll-table-container" class="table-container report-table-container">
                <table id="payrollTable" 
       class="report-table sortable" 
       data-initial-sort-column="0" 
       data-initial-sort-direction="asc">
    <thead>
        <tr>
            <th class="sortable" data-sort-type="number">Emp ID</th>
            <th class="sortable" data-sort-type="string">First Name</th>
            <th class="sortable" data-sort-type="string">Last Name</th>
            <th class="sortable" data-sort-type="string">Wage Type</th>
            <th class="sortable" data-sort-type="number">Regular Hours</th>
            <th class="sortable" data-sort-type="number">Overtime Hours</th>
            <th class="sortable" data-sort-type="number">Double Time Hours</th>
            <th class="sortable" data-sort-type="number">Total Paid Hours</th>
            <th class="sortable" data-sort-type="string">Wage</th>
            <th class="sortable" data-sort-type="string">Total Pay</th>
        </tr>
    </thead>
    <tbody>
        <%= payrollTableHtml %>
    </tbody>
    <tfoot>
        <tr>
            <td colspan="9" style="text-align: right; font-weight: bold;">Payroll Grand Total:</td>
            <td style="text-align: right; font-weight: bold;"><%= formattedGrandTotal %></td>
        </tr>
    </tfoot>
</table>
            </div>

            <div id="payroll-actions-container">
                <button id="btnExceptionReport" type="button" class="glossy-button text-orange full-width-button">
                    <i class="fas fa-exclamation-triangle"></i> Exception Report
                </button>
                <form method="post" action="PayrollServlet" style="display: contents;">
                    <input type="hidden" name="action" value="exportPayroll">
                    <button id="btnExportPayroll" type="submit" class="glossy-button text-green full-width-button">
                        <i class="fas fa-file-excel"></i> Export Payroll
                    </button>
                </form>
                <button id="btnPrintPayroll" type="button" class="glossy-button text-blue full-width-button">
                    <i class="fas fa-print"></i> Print Payroll Summary
                </button>
                <button id="btnPrintAllTimeCards" type="button" class="glossy-button text-info full-width-button">
                    <i class="fas fa-print"></i> Print All Time Cards
                </button>
                <form id="closePayPeriodForm" method="post" action="PayrollServlet" style="display: contents;">
                    <input type="hidden" name="action" value="closePayPeriod">
                    <button id="btnClosePayPeriodActual" type="button" class="glossy-button text-red full-width-button">
                        <i class="fas fa-lock"></i> Close Pay Period
                    </button>
                </form>
            </div>
        <% } %>
    </div>

    <%-- General Notification Modal --%>
    <div id="notificationModalGeneral" class="modal"> 
        <div class="modal-content">
            <span class="close" data-close-modal-id="notificationModalGeneral">&times;</span>
            <h2 id="notificationModalGeneralTitle">Notification</h2> 
            <p id="notificationModalGeneralMessage"></p> 
            <div class="button-row" style="justify-content: center;">
                <button type="button" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue">OK</button>
            </div>
        </div>
    </div>
    
    <%-- Exception Report Modal --%>
    <div id="exceptionReportModal" class="report-modal modal">
        <div class="modal-content report-modal-content">
            <span class="close" id="closeExceptionReportModal">&times;</span>
            <h2>Exception Report</h2>
            <p id="exceptionReportInstructions" class="modal-instructions"><i>Select a row below to make corrections. Click "Fix Missing Punches" to edit.</i></p>
            <div class="modal-table-container table-container report-table-container">
                <table id="exceptionReportTable" class="report-table">
                    <thead><tr><th>Emp ID</th><th>First Name</th><th>Last Name</th><th>Date</th><th>IN Time</th><th>OUT Time</th></tr></thead>
                    <tbody id="exceptionReportTbody"><tr><td colspan="6" style="text-align:center;padding:20px;">Click "Exception Report" to load data.</td></tr></tbody>
                </table>
            </div>
            <div class="button-row modal-footer">
                <button id="editExceptionButton" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Fix Missing Punches</button>
                <button id="closeExceptionReportButton" class="glossy-button text-red" type="button"><i class="fas fa-times"></i> Close Report</button>
            </div>
        </div>
    </div>

    <%-- Edit Punch Modal (from Exception Report) --%>
    <div id="editPunchModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeEditPunchModal">&times;</span>
            <h2>Edit Punch Record</h2>
            <div class="edit-punch-info">
                <p><strong>Employee:</strong> <span id="editPunchEmployeeName"></span></p>
                <p><strong>Schedule:</strong> <span id="editPunchScheduleInfo"></span></p>
            </div>
            <form id="editPunchForm" action="AddEditAndDeletePunchesServlet" method="post">
                <input type="hidden" id="editPunchIdField" name="editPunchId">
                <input type="hidden" name="action" value="editPunch">
                <input type="hidden" id="editPunchEmployeeIdField" name="editEmployeeId">
                <input type="hidden" id="editUserTimeZone" name="userTimeZone" value="<%= escapeJspHtml(userTimeZoneId) %>">
                <input type="hidden" name="editPunchType" value="Supervisor Override">
                <div class="form-item"><label for="editDate">Date: <span class="required-asterisk">*</span></label><input type="date" id="editDate" name="editDate" required></div>
                <div class="form-item"><label for="editInTime">IN Time (HH:MM:SS):</label><input type="time" id="editInTime" name="editInTime" step="1"></div>
                <div class="form-item"><label for="editOutTime">OUT Time (HH:MM:SS):</label><input type="time" id="editOutTime" name="editOutTime" step="1"></div>
            </form>
            <div class="button-row">
                <button type="submit" form="editPunchForm" class="glossy-button text-green"><i class="fas fa-save"></i> Save Changes</button>
                <button type="button" id="cancelEditPunch" class="glossy-button text-red"><i class="fas fa-times"></i> Cancel</button>
            </div>
        </div>
    </div>

    <%-- Custom Confirmation Modal for Closing Pay Period --%>
    <div id="closePeriodConfirmModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeConfirmModalSpanBtn">&times;</span>
            <h2 id="confirmModalTitle">Confirm Action</h2>
            <p id="confirmModalMessage" style="white-space: pre-wrap; text-align: left; max-height: 450px; overflow-y: auto;"></p>
            <div class="button-row" style="justify-content: flex-end; gap: 10px;">
                <button type="button" id="confirmModalCancelBtn" class="glossy-button text-blue">Cancel</button>
                <button type="button" id="confirmModalOkBtn" class="glossy-button text-red">Confirm</button>
            </div>
        </div>
    </div>

    <script type="text/javascript">
        const payPeriodEndDateJs = "<%= periodEndDate != null ? periodEndDate.toString() : "" %>";
        const effectiveTimeZoneIdJs = "<%= escapeJspHtml(userTimeZoneId) %>";
        const currentTenantIdJs = <%= tenantId != null ? tenantId : 0 %>;
        const appRootPath = "<%= request.getContextPath() %>";
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/payroll.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>