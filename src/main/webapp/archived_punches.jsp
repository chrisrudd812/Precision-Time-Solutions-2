<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.reports.ShowReports" %>
<%@ page import="timeclock.punches.ShowPunches" %>
<%@ page import="timeclock.Configuration" %>
<%@ page import="timeclock.util.Helpers" %>
<%@ page import="java.util.*" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.DateTimeParseException" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspLogger = Logger.getLogger("archived_punches_jsp_v3");
    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String userTimeZoneId = "America/Denver"; // Default
    String paramError = null;

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
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired or invalid. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    String eidStr = request.getParameter("employeesDropDown");
    String startDateStrParam = request.getParameter("startDate");
    String endDateStrParam = request.getParameter("endDate");
    
    boolean isFormSubmitted = request.getParameter("search") != null;
    
    int eid = 0;
    LocalDate startDate = null;
    LocalDate endDate = null;

    LocalDate defaultEndDate = LocalDate.now();
    LocalDate defaultStartDate = defaultEndDate.minusMonths(1).withDayOfMonth(1);

    if (isFormSubmitted) {
        if (Helpers.isStringValid(eidStr)) {
            try { eid = Integer.parseInt(eidStr.trim()); } catch (NumberFormatException e) { eid = 0; }
        }
        try {
            startDate = Helpers.isStringValid(startDateStrParam) ? LocalDate.parse(startDateStrParam) : defaultStartDate;
            endDate = Helpers.isStringValid(endDateStrParam) ? LocalDate.parse(endDateStrParam) : defaultEndDate;
            if (startDate.isAfter(endDate)) {
                paramError = "Start date cannot be after end date. Using default range.";
                startDate = defaultStartDate; endDate = defaultEndDate;
            }
        } catch (DateTimeParseException e) {
            paramError = "Invalid date format. Using default range.";
            startDate = defaultStartDate; endDate = defaultEndDate;
        }
    } else {
        if (Helpers.isStringValid(startDateStrParam) && Helpers.isStringValid(endDateStrParam)) {
            try {
                startDate = LocalDate.parse(startDateStrParam);
                endDate = LocalDate.parse(endDateStrParam);
            } catch (DateTimeParseException e) {
                paramError = "Invalid date format from URL. Using default range.";
                startDate = defaultStartDate;
                endDate = defaultEndDate;
            }
        } else {
            startDate = defaultStartDate;
            endDate = defaultEndDate;
        }
        if (Helpers.isStringValid(request.getParameter("eid"))) {
             try { eid = Integer.parseInt(request.getParameter("eid")); } catch (NumberFormatException e) { eid = 0;}
        }
    }

    List<Map<String, Object>> employeeDropdownList = ShowPunches.getActiveEmployeesForDropdown(tenantId);
    String tableRowsHtml = "";
    boolean showEmployeeColumnsInHeader = (eid <= 0);
    int tableColspan = showEmployeeColumnsInHeader ? 8 : 6;

    if (isFormSubmitted && paramError == null) {
        tableRowsHtml = ShowReports.showArchivedPunchesReport(tenantId, eid, userTimeZoneId, startDate, endDate);
    } else if (paramError != null) {
        tableRowsHtml = "<tr><td colspan='" + tableColspan + "' class='report-error-row'>" + escapeJspHtml(paramError) + "</td></tr>";
    } else {
        tableRowsHtml = "<tr><td colspan='" + tableColspan + "' class='report-message-row'>Select filters and click 'Apply' to load data.</td></tr>";
    }
    
    String startDateValueToUse = startDate.toString();
    String endDateValueToUse = endDate.toString();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>View Archived Punches</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/archived_punches.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <div class="parent-container">
        <h1>View Archived Punches</h1>
        <form id="archiveViewForm" action="archived_punches.jsp" method="GET" class="archive-controls-form">
             <div class="archive-controls-flex">
                 <div class="form-item-container employee-select">
                     <label for="employeesDropDown">Employee:</label>
                     <select id="employeesDropDown" name="employeesDropDown">
                         <option value="0" <%= (eid == 0) ? "selected" : "" %>>All Employees</option>
                         <% for (Map<String, Object> employee : employeeDropdownList) {
                             Integer currentDropdownGlobalEid = (Integer) employee.get("eid");
                             String displayName = (String) employee.get("displayName");
                             String selectedAttr = (eid > 0 && currentDropdownGlobalEid != null && currentDropdownGlobalEid.equals(eid)) ? "selected" : "";
                         %>
                             <option value="<%= currentDropdownGlobalEid %>" <%= selectedAttr %>><%= escapeJspHtml(displayName) %></option>
                         <% } %>
                     </select>
                 </div>
                 <div class="form-item-container date-range-item">
                     <label for="startDate">Start Date:</label>
                     <input type="date" id="startDate" name="startDate" value="<%= startDateValueToUse %>" required>
                 </div>
                 <div class="form-item-container date-range-item">
                      <label for="endDate">End Date:</label>
                      <input type="date" id="endDate" name="endDate" value="<%= endDateValueToUse %>" required>
                  </div>
                  <div class="form-item-container button-item">
                      <button type="submit" name="search" value="true" id="applyFiltersBtn" class="glossy-button text-green">
                          <i class="fas fa-search"></i> Apply
                      </button>
                  </div>
             </div>
        </form>

        <div class="content-display-area">
             <h2 id="reportTitle" class="report-title">Archived Punches Results</h2>
             <div class="report-actions">
                <button type="button" id="printArchivedPunchesBtn" class="glossy-button text-blue">
                    <i class="fas fa-print"></i> Print This View
                </button>
                <br>
            </div>
             <div id="reportOutput" class="report-output">
                 <div class="table-container report-table-container">
                    <table class="report-table archive-table" id="archivedPunchesTable">
                        <thead>
                            <tr>
                                <% if (showEmployeeColumnsInHeader) { %>
                                    <th class="sortable" data-sort-type="number">Emp ID</th>
                                    <th class="sortable" data-sort-type="string">First Name</th>
                                    <th class="sortable" data-sort-type="string">Last Name</th>
                                <% } %>
                                <th class="sortable" data-sort-type="date">Date</th>
                                <th class="sortable" data-sort-type="string" class="center-text">IN Punch</th>
                                <th class="sortable" data-sort-type="string" class="center-text">OUT Punch</th>
                                <th class="sortable" data-sort-type="number" class="center-text">Total Hours</th>
                                <th class="sortable" data-sort-type="string" class="center-text">Punch Type</th>
                            </tr>
                        </thead>
                        <tbody>
                            <%= tableRowsHtml %>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="js/archived_punches.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>