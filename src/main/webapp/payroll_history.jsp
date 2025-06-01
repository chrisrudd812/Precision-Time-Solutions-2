<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.sql.Connection" %>
<%@ page import="java.sql.PreparedStatement" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.sql.Timestamp" %>
<%@ page import="java.sql.Date" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.text.NumberFormat" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Locale" %>
<%@ page import="timeclock.db.DatabaseConnection" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%!
    private static final Logger jspLogger = Logger.getLogger("payroll_history_jsp");
    private static final String JSP_NOT_APPLICABLE_DISPLAY = "N/A";
    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) {
            tenantId = (Integer) tenantIdObj;
        }
    }

    if (tenantId == null || tenantId <= 0) {
        response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Session expired or invalid tenant. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    List<Map<String, Object>> historyData = new ArrayList<>();
    String pageError = null;

    SimpleDateFormat timestampFormat = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");
    SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy");
    SimpleDateFormat attributeDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);

    String sql = "SELECT processed_date, period_start_date, period_end_date, grand_total " +
                 "FROM payroll_history WHERE TenantID = ? ORDER BY processed_date DESC";
    jspLogger.info("Fetching payroll history for TenantID: " + tenantId);

    try (Connection con = DatabaseConnection.getConnection();
         PreparedStatement ps = con.prepareStatement(sql)) {
        ps.setInt(1, tenantId);
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("processed_date", rs.getTimestamp("processed_date"));
                row.put("period_start_date", rs.getDate("period_start_date"));
                row.put("period_end_date", rs.getDate("period_end_date"));
                row.put("grand_total", rs.getBigDecimal("grand_total"));
                historyData.add(row);
            }
        }
        if (historyData.isEmpty()) {
             jspLogger.info("No payroll history records found for TenantID: " + tenantId);
        }
    } catch (SQLException e) { pageError = "Error retrieving payroll history: " + e.getMessage(); jspLogger.log(Level.SEVERE, "DB error fetching payroll history T:" + tenantId, e);
    } catch (Exception e) { pageError = "Unexpected error: " + e.getMessage(); jspLogger.log(Level.SEVERE, "Unexpected error on payroll_history.jsp T:" + tenantId, e); }
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Payroll History</title>
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=23"> <%-- Main theme --%>
    <link rel="stylesheet" href="css/payroll_history.css?v=3"> <%-- Specific overrides --%>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reports-container">
        <h1>Payroll History</h1>

        <% if (pageError != null) { %>
            <div class="page-message error-message"><%= escapeJspHtml(pageError) %></div>
        <% } %>

        <% if (pageError == null) { %>
            <div class="report-display-area" style="padding-top:10px;">
                <div class="table-container report-table-container">
                    <table class="report-table" id="historyTable">
                        <thead>
                            <tr>
                                <th data-sort-type="date">Processed Date/Time</th>
                                <th data-sort-type="date">Period Start Date</th>
                                <th data-sort-type="date">Period End Date</th>
                                <th style="text-align: right;" data-sort-type="currency">Grand Total</th>
                            </tr>
                        </thead>
                        <tbody id="historyTableBody">
                            <% if (historyData.isEmpty()) { %>
                                <tr><td colspan="4" class="report-message-row">No payroll history records found for your company.</td></tr>
                            <% } else { %>
                                <% for (Map<String, Object> row : historyData) { %>
                                    <%
                                        Timestamp processedTs = (Timestamp) row.get("processed_date");
                                        Date startDateSql = (Date) row.get("period_start_date");
                                        Date endDateSql = (Date) row.get("period_end_date");
                                        BigDecimal total = (BigDecimal) row.get("grand_total");

                                        String formattedProcessedDate = (processedTs != null) ? timestampFormat.format(processedTs) : JSP_NOT_APPLICABLE_DISPLAY;
                                        String formattedStartDate = (startDateSql != null) ? dateFormat.format(startDateSql) : JSP_NOT_APPLICABLE_DISPLAY;
                                        String formattedEndDate = (endDateSql != null) ? dateFormat.format(endDateSql) : JSP_NOT_APPLICABLE_DISPLAY;
                                        String formattedTotal = (total != null) ? currencyFormatter.format(total) : currencyFormatter.format(0.0);

                                        String attrStartDate = (startDateSql != null) ? attributeDateFormat.format(startDateSql) : "";
                                        String attrEndDate = (endDateSql != null) ? attributeDateFormat.format(endDateSql) : "";
                                    %>
                                    <tr data-start-date="<%= attrStartDate %>" data-end-date="<%= attrEndDate %>"
                                        data-processed-ts="<%= processedTs != null ? processedTs.getTime() : "" %>">
                                        <td><%= formattedProcessedDate %></td>
                                        <td><%= formattedStartDate %></td>
                                        <td><%= formattedEndDate %></td>
                                        <td class="currency" style="text-align: right;"><%= formattedTotal %></td>
                                    </tr>
                                <% } %>
                            <% } %>
                        </tbody>
                    </table>
                </div>
            </div>

            <%-- MODIFIED Button Container --%>
            <div id="button-container" class="payroll-history-actions">
                <div class="action-left">
                    <button type="button" id="btnViewDetails" class="glossy-button text-green" disabled>
                        <i class="fas fa-search-dollar"></i> View Period Details
                    </button>
                </div>
                <div class="action-right">
                    <button type="button" id="btnPrintHistory" class="glossy-button text-blue">
                        <i class="fas fa-print"></i> Print / Save History
                    </button>
                </div>
            </div>
        <% } %>
    </div>

    <%-- Notification Modal (Standard structure from reports.css) --%>
    <div id="notificationModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeNotificationModal">&times;</span>
            <h2>Notification</h2>
            <p id="notificationMessage"></p>
            <div class="button-row" style="justify-content: center;">
                <button type="button" id="okButton" class="glossy-button text-blue">OK</button>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="js/payroll_history.js?v=3"></script> <%-- Incremented version --%>
</body>
</html>