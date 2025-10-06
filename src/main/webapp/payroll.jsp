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

<%@ page import="java.util.ArrayList" %>
<%@ page import="timeclock.util.Helpers" %>


<%!
    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String pageError = null; 
    String userTimeZoneId = null; 

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        Object userTimeZoneIdObj = currentSession.getAttribute("userTimeZoneId");
        if (userTimeZoneIdObj instanceof String && Helpers.isStringValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
        }
    }

    if (tenantId == null || tenantId <= 0) {
        if(currentSession != null) currentSession.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    if (!Helpers.isStringValid(userTimeZoneId)) {
        userTimeZoneId = Configuration.getProperty(tenantId, "DefaultTimeZone", "America/Denver");
    }

    try {
        ZoneId.of(userTimeZoneId);
    } catch (Exception e) {
        userTimeZoneId = "UTC";
        pageError = "A critical error occurred with timezone configuration. Please contact support.";
    }

    boolean dataReady = false;
    String payrollTableHtml = "<tr><td colspan='10' class='report-message-row'>Payroll data not loaded.</td></tr>";
    String formattedGrandTotal = "$0.00";
    String payPeriodMessage = "Pay Period Not Set";
    LocalDate periodStartDate = null;
    LocalDate periodEndDate = null;
    DateTimeFormatter longDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.ENGLISH);
    NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);

    if (pageError == null) {
        try {
            String startDateStr = Configuration.getProperty(tenantId, "PayPeriodStartDate");
            String endDateStr = Configuration.getProperty(tenantId, "PayPeriodEndDate");
            if (Helpers.isStringValid(startDateStr) && Helpers.isStringValid(endDateStr)) {
                periodStartDate = LocalDate.parse(startDateStr.trim());
                periodEndDate = LocalDate.parse(endDateStr.trim());
                payPeriodMessage = periodStartDate.format(longDateFormatter) + " to " + periodEndDate.format(longDateFormatter);
            } else { pageError = "Pay period start/end dates not found. Please visit the Settings page."; }
        } catch (Exception e) { pageError = "Error retrieving pay period settings."; }
    }

    if (periodStartDate != null && periodEndDate != null && pageError == null) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> calculatedData = ShowPayroll.calculatePayrollData(tenantId, periodStartDate, periodEndDate);
            if (calculatedData != null) { 
                Map<String, Object> displayData = ShowPayroll.showPayroll(calculatedData);
                payrollTableHtml = (String) displayData.getOrDefault("payrollHtml", "<tr><td colspan='10' class='report-error-row'>Error formatting data.</td></tr>");
                double grandTotalValue = (Double) displayData.getOrDefault("grandTotal", 0.0);
                formattedGrandTotal = currencyFormatter.format(grandTotalValue);
                dataReady = true; 
            } else { 
                payrollTableHtml = "<tr><td colspan='10' class='report-message-row'>No payroll data available for this period.</td></tr>";
            }
        } catch (Exception e) {
            pageError = "Error processing payroll: " + e.getMessage();
            payrollTableHtml = "<tr><td colspan='10' class='report-error-row'>Error processing payroll. Check server logs.</td></tr>";
        }
    } else if (pageError == null) { 
        pageError = "Pay period dates are not correctly set.";
    }

    String successMessageFromRedirect = request.getParameter("message");
    String errorMessageFromRedirect = request.getParameter("error");
    if (errorMessageFromRedirect != null && !errorMessageFromRedirect.isEmpty()) {
        pageError = (pageError != null ? pageError + "<br/>" : "") + "Servlet Error: " + escapeJspHtml(errorMessageFromRedirect);
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Payroll Processing</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/payroll.css?v=<%= System.currentTimeMillis() %>">
    <style>
        /* Force table container height and visibility */
        #payroll-table-container {
            min-height: 400px !important;
            height: auto !important;
            max-height: 60vh !important;
            display: block !important;
            visibility: visible !important;
            overflow-y: auto !important;
        }
        
        /* Mobile width adjustment */
        @media (max-width: 480px) {
            body.payroll-page .parent-container {
                max-width: 100% !important;
            }
        }
        
        /* Laptop view - prevent container scrolling */
        @media (min-width: 481px) {
            .parent-container {
                max-height: none !important;
                overflow-y: visible !important;
            }
        }
        #payrollTable {
            display: table !important;
            width: 100% !important;
            height: auto !important;
        }
        #payrollTable tbody {
            display: table-row-group !important;
        }
        #payrollTable tr {
            display: table-row !important;
        }
        #payrollTable td {
            display: table-cell !important;
        }
    </style>
</head>
<body class="payroll-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container" style="max-height: 90vh; overflow-y: auto;">
        <h1>Payroll Processing</h1>
        <%-- MODIFIED: Added a unique ID to the H2 tag --%>
        <h2 id="payPeriodHeader">Pay Period: <%= escapeJspHtml(payPeriodMessage) %></h2>

        <% if (successMessageFromRedirect != null && !successMessageFromRedirect.isEmpty()) { %>
            <div class="page-message success-message" id="pageNotificationDiv_Success_Payroll" style="display: none;"><%= escapeJspHtml(successMessageFromRedirect) %></div>
        <% } else if (pageError != null && !pageError.isEmpty()) { %>
            <div class="page-message error-message" id="pageNotificationDiv_Error_Payroll"><%= pageError %></div>
        <% } %>

        <% if(dataReady) { %>
        <p class="instructions">Review payroll below. Run the 'Exception Report' first to check for missing punches before closing the pay period.</p>
        <h4 style="color: #6c757d; margin: 7px auto 10px auto; font-size: 0.9em; text-align: center;"><span class="instruction-text">💡 Click on a payroll table row to edit that employee's punches</span></h4>
        <div id="payroll-table-container" class="table-container report-table-container">
            <table id="payrollTable" class="report-table sortable">
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
                        <th class="sortable" data-sort-type="currency">Wage</th>
                        <th class="sortable" data-sort-type="currency">Total Pay</th>
                    </tr>
                </thead>
                <tbody><%= payrollTableHtml %></tbody>
                <tfoot>
                    <tr>
                        <td colspan="9" style="text-align: right; padding-right: 15px;">Payroll Grand Total:</td>
                        <td style="font-weight: bold; text-align: right;"><%= formattedGrandTotal %></td>
                    </tr>
                </tfoot>
            </table>
        </div>
        <div id="payroll-actions-container">
            <button id="btnExceptionReport" type="button" class="glossy-button text-orange"><i class="fas fa-exclamation-triangle"></i> Exception Report</button>
            <button id="btnAddHolidayPTO" type="button" class="glossy-button text-purple"><i class="fas fa-calendar-plus"></i> Add Holiday / PTO</button>
            <form method="post" action="PayrollServlet"><input type="hidden" name="action" value="exportPayroll"><button id="btnExportPayroll" type="submit" class="glossy-button text-green"><i class="fas fa-file-excel"></i> Export Payroll</button></form>
            <button id="btnPrintPayroll" type="button" class="glossy-button text-blue"><i class="fas fa-print"></i> Print Payroll Summary</button>
            <button id="btnPrintAllTimeCards" type="button" class="glossy-button text-purple"><i class="fas fa-print"></i> Print / Email Time Cards</button>
            <form id="closePayPeriodForm" method="post" action="PayrollServlet"><input type="hidden" name="action" value="closePayPeriod"><button id="btnClosePayPeriodActual" type="button" class="glossy-button text-red"><i class="fas fa-lock"></i> Close Pay Period</button></form>
        </div>
        <% } %>
    </div>

    <%-- MODALS --%>
    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    <%@ include file="/WEB-INF/includes/payroll-modals.jspf" %>

    <script type="text/javascript">
        window.appRootPath = "<%= request.getContextPath() %>";
        window.payPeriodEndDateJs = "<%= periodEndDate != null ? periodEndDate.toString() : "" %>";
        window.effectiveTimeZoneIdJs = "<%= escapeJspHtml(userTimeZoneId) %>";
        <% if (periodStartDate != null && periodEndDate != null) { %>
        window.PAY_PERIOD_START = "<%= periodStartDate.toString() %>";
        window.PAY_PERIOD_END = "<%= periodEndDate.toString() %>";
        <% } %>
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/payroll.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>