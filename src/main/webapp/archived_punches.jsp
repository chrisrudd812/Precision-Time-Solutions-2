<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.reports.ShowReports" %>
<%@ page import="timeclock.punches.ShowPunches" %>
<%@ page import="timeclock.Configuration" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap"%>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.ZoneId" %> <%-- Added for Timezone Validation --%>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.DateTimeParseException" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspLogger = Logger.getLogger("archived_punches_jsp_v2");
    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    Integer sessionEidForLog = null;
    String paramError = null; // Initialize error message

    // --- Standardized Timezone Logic (Aligned with timeclock.jsp) ---
    final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
    final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
    String userTimeZoneId = null; // Start with null

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        Object eidObj = currentSession.getAttribute("EID");
        if (eidObj instanceof Integer) { sessionEidForLog = (Integer) eidObj; }

        // 1. Attempt to get user-specific timezone from session (set at login)
        Object userTimeZoneIdObj = currentSession.getAttribute("userTimeZoneId");
        if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
            jspLogger.info("[ARCHIVED_PUNCHES_TZ] Using userTimeZoneId from session: " + userTimeZoneId + " for EID: " + sessionEidForLog);
        }
    }

    // Check TenantID *before* proceeding with timezone logic that might depend on it
    if (tenantId == null || tenantId <= 0) {
        if(currentSession != null) currentSession.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired or invalid. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    // 2. If not in session (or invalid), try Tenant's DefaultTimeZone, then app's tenant fallback
    if (!ShowPunches.isValid(userTimeZoneId)) {
        String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone"); // Get without internal fallback first
        if (ShowPunches.isValid(tenantDefaultTz)) {
            userTimeZoneId = tenantDefaultTz;
            jspLogger.info("[ARCHIVED_PUNCHES_TZ] Using Tenant DefaultTimeZone from SETTINGS: " + userTimeZoneId + " for Tenant: " + tenantId);
        } else {
            // If tenant default is not set in DB or was invalid, use the application's defined default for tenants
            userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
            jspLogger.info("[ARCHIVED_PUNCHES_TZ] Tenant DefaultTimeZone not set/invalid. Using application default: " + userTimeZoneId + " for Tenant: " + tenantId);
        }
    }

    // 3. If still not found or invalid, use the ultimate Pacific Time fallback
    if (!ShowPunches.isValid(userTimeZoneId)) {
        userTimeZoneId = PACIFIC_TIME_FALLBACK;
        jspLogger.warning("[ARCHIVED_PUNCHES_TZ] User/Tenant timezone not determined. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for Tenant: " + tenantId);
    }

    // 4. Validate the determined ZoneId to prevent errors in formatting downstream
    try {
        ZoneId.of(userTimeZoneId); // Validate the final determined ZoneId
    } catch (Exception e) {
        jspLogger.log(Level.SEVERE, "[ARCHIVED_PUNCHES_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Tenant: " + tenantId, e);
        userTimeZoneId = "UTC";
        String tzErrorMsg = "A critical error occurred with timezone configuration. Displaying times in UTC. Please contact support.";
        paramError = (paramError == null) ? tzErrorMsg : paramError + " " + tzErrorMsg;
    }
    jspLogger.info("[ARCHIVED_PUNCHES_TZ] Archived_punches.jsp final effective userTimeZoneId: " + userTimeZoneId + " for Tenant: " + tenantId);
    // --- End Standardized Timezone Logic ---


    String eidStr = request.getParameter("employeesDropDown");
    String startDateStrParam = request.getParameter("startDate");
    String endDateStrParam = request.getParameter("endDate");
    boolean isFormSubmitted = request.getParameter("startDate") != null || request.getParameter("endDate") != null || request.getParameter("employeesDropDown") != null;
    int eid = 0;
    LocalDate startDate = null;
    LocalDate endDate = null;
    // paramError is now initialized above

    LocalDate defaultEndDate = LocalDate.now();
    LocalDate defaultStartDate = defaultEndDate.minusMonths(1).withDayOfMonth(1);

    if (isFormSubmitted) {
        if (ShowPunches.isValid(eidStr)) {
            try {
                eid = Integer.parseInt(eidStr.trim());
                if (eid < 0) { paramError = (paramError == null ? "" : paramError + " ") + "Invalid Employee ID. Showing 'All Employees'."; eid = 0; }
            } catch (NumberFormatException e) { paramError = (paramError == null ? "" : paramError + " ") + "Employee ID not a number. Showing 'All Employees'."; eid = 0; }
        }
        try {
            startDate = (ShowPunches.isValid(startDateStrParam)) ? LocalDate.parse(startDateStrParam) : defaultStartDate;
            endDate = (ShowPunches.isValid(endDateStrParam)) ? LocalDate.parse(endDateStrParam) : defaultEndDate;
            if (startDate.isAfter(endDate)) {
                paramError = ((paramError == null ? "" : paramError + " ") + "Start date after end date. Using default range.").trim();
                startDate = defaultStartDate; endDate = defaultEndDate;
            }
        } catch (DateTimeParseException e) {
            paramError = ((paramError == null ? "" : paramError + " ") + "Invalid date format. Using default range.").trim();
            startDate = defaultStartDate; endDate = defaultEndDate;
        }
    } else {
        startDate = (ShowPunches.isValid(startDateStrParam)) ? LocalDate.parse(startDateStrParam) : defaultStartDate;
        endDate = (ShowPunches.isValid(endDateStrParam)) ? LocalDate.parse(endDateStrParam) : defaultEndDate;
        if (ShowPunches.isValid(request.getParameter("eid"))) {
             try { eid = Integer.parseInt(request.getParameter("eid")); } catch (NumberFormatException e) { eid = 0;}
        } else { eid = 0; }
    }

    List<Map<String, Object>> employeeDropdownList = new ArrayList<>();
    if (tenantId > 0) {
        try {
            employeeDropdownList = ShowPunches.getActiveEmployeesForDropdown(tenantId);
            if (employeeDropdownList == null) employeeDropdownList = new ArrayList<>();
        } catch (Exception e) {
            paramError = ((paramError == null ? "" : paramError + " ") + "Error loading employee dropdown.").trim();
            jspLogger.log(Level.WARNING, "Error employee dropdown T" + tenantId, e);
        }
    }

    String tableRowsHtml = "";
    boolean showEmployeeColumnsInHeader = (eid <= 0 && isFormSubmitted && paramError == null && startDate != null && endDate != null);

    if (!isFormSubmitted && paramError == null) {
        tableRowsHtml = "<tr><td colspan='7' class='report-message-row'>Select filters and change a selection to load data.</td></tr>";
        showEmployeeColumnsInHeader = (eid <=0); // Show emp cols if EID is 0 even initially
    } else if (paramError != null) {
         showEmployeeColumnsInHeader = (eid <=0); // Default based on EID even on error
         tableRowsHtml = "<tr><td colspan='" + ( showEmployeeColumnsInHeader ? 7 : 5) + "' class='report-error-row'>" + escapeJspHtml(paramError) + "</td></tr>";
    } else if (startDate == null || endDate == null) {
        showEmployeeColumnsInHeader = (eid <=0);
        tableRowsHtml = "<tr><td colspan='" + ( showEmployeeColumnsInHeader ? 7 : 5) + "' class='report-error-row'>Please select valid Start and End dates.</td></tr>";
    } else {
        jspLogger.info("[archived_punches.jsp] Calling ShowReports.showArchivedPunchesReport with TenantID: " + tenantId +
                       ", EID: " + eid + ", StartDate: " + startDate + ", EndDate: " + endDate +
                       ", TimeZone: " + userTimeZoneId);
        try {
            tableRowsHtml = ShowReports.showArchivedPunchesReport(tenantId, eid, userTimeZoneId, startDate, endDate);
            // Re-evaluate based on loaded data, but primarily stick to EID.
            showEmployeeColumnsInHeader = (eid <= 0); // Stick to EID selection for header consistency
            if (showEmployeeColumnsInHeader && (tableRowsHtml.contains("report-message-row") || tableRowsHtml.contains("report-error-row"))) {
                 // If all employees and no data/error, we still want the 7-col header,
                 // but the row itself will have the correct colspan set by ShowReports.
            } else if (eid > 0) {
                 showEmployeeColumnsInHeader = false;
            }

        } catch (Exception e) {
            showEmployeeColumnsInHeader = (eid <=0);
            tableRowsHtml = "<tr><td colspan='" + (showEmployeeColumnsInHeader ? 7 : 5) + "' class='report-error-row'>Unexpected error fetching report. Check logs.</td></tr>";
            jspLogger.log(Level.SEVERE, "Error showArchivedPunchesReport T:" + tenantId + ", EID:" + eid, e);
        }
    }
    String startDateValueToUse = (startDate != null) ? startDate.toString() : "";
    String endDateValueToUse = (endDate != null) ? endDate.toString() : "";
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>View Archived Punches</title>
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/archived_punches.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <div class="parent-container reports-container">
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
             </div>
         </form>

        <%-- Display paramError only if it exists and hasn't been shown in the table --%>
        <% if (paramError != null && !paramError.isEmpty() && !tableRowsHtml.contains("report-error-row")) { %>
            <div class="page-message error-message" style="margin-top:15px;"><%= escapeJspHtml(paramError) %></div>
        <% } %>

        <div class="report-display-area">
             <h2 id="reportTitle" class="report-title">Archived Punches Results</h2>
             <div class="report-actions" style="<%= (isFormSubmitted && paramError == null && startDate != null && endDate != null && !tableRowsHtml.contains("report-message-row") && !tableRowsHtml.contains("report-error-row") ) ? "display: flex;" : "display: none;" %>">
                <button type="button" id="printArchivedPunchesBtn" class="glossy-button text-blue">
                    <i class="fas fa-print"></i> Print This View
                </button>
            </div>
             <div id="reportOutput" class="report-output">
                <div class="table-container report-table-container">
                    <table class="report-table archive-table" id="archivedPunchesTable">
                        <thead>
                            <tr>
                                <% if (showEmployeeColumnsInHeader) { %>
                                    <th data-sort-type="number">Emp ID</th>
                                    <th data-sort-type="string">Employee Name</th>
                                <% } %>
                                <th data-sort-type="date">Date</th>
                                <th data-sort-type="string" class="center-text">IN Punch</th>
                                <th data-sort-type="string" class="center-text">OUT Punch</th>
                                <th data-sort-type="number" class="center-text">Total Hours</th>
                                <th data-sort-type="string" class="center-text">Punch Type</th>
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