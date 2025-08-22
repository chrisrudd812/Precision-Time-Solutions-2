<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.Configuration" %>
<%@ page import="timeclock.punches.ShowPunches" %> <%-- Added for isValid --%>
<%@ page import="java.time.ZoneId" %> <%-- Added for Timezone --%>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %> <%-- Added for Logging Level --%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%!
    private static final Logger reportsJspLogger = Logger.getLogger("reports_jsp_v_tz");

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
    String initialReportType = request.getParameter("report");
    boolean reportSelected = (initialReportType != null && !initialReportType.trim().isEmpty());

    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    Integer sessionEidForLog = null;
    String pageError = null; // For capturing errors

    // --- Standardized Timezone Logic (Aligned with timeclock.jsp) ---
    final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
    final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
    String userTimeZoneId = null; // Start with null

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        Object eidObj = currentSession.getAttribute("EID");
        if (eidObj instanceof Integer) { sessionEidForLog = (Integer) eidObj; }

        // 1. Attempt to get user-specific timezone from session
        Object userTimeZoneIdObj = currentSession.getAttribute("userTimeZoneId");
        if (userTimeZoneIdObj instanceof String && ShowPunches.isValid((String)userTimeZoneIdObj)) {
            userTimeZoneId = (String) userTimeZoneIdObj;
            reportsJspLogger.info("[REPORTS_JSP_TZ] Using userTimeZoneId from session: " + userTimeZoneId + " for EID: " + sessionEidForLog);
        }
    }

    // Check TenantID *before* proceeding
    if (tenantId == null || tenantId <= 0) {
        if(currentSession != null) currentSession.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired or invalid. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    // 2. If not in session (or invalid), try Tenant's DefaultTimeZone, then app's tenant fallback
    if (!ShowPunches.isValid(userTimeZoneId)) {
        String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone"); // Get without internal fallback
        if (ShowPunches.isValid(tenantDefaultTz)) {
            userTimeZoneId = tenantDefaultTz;
            reportsJspLogger.info("[REPORTS_JSP_TZ] Using Tenant DefaultTimeZone from SETTINGS: " + userTimeZoneId + " for Tenant: " + tenantId);
        } else {
            userTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
            reportsJspLogger.info("[REPORTS_JSP_TZ] Tenant DefaultTimeZone not set/invalid. Using application default: " + userTimeZoneId + " for Tenant: " + tenantId);
        }
    }

    // 3. If still not found or invalid, use the ultimate Pacific Time fallback
    if (!ShowPunches.isValid(userTimeZoneId)) {
        userTimeZoneId = PACIFIC_TIME_FALLBACK;
        reportsJspLogger.warning("[REPORTS_JSP_TZ] User/Tenant timezone not determined. Defaulting to system fallback (Pacific Time): " + userTimeZoneId + " for Tenant: " + tenantId);
    }

    // 4. Validate the determined ZoneId
    try {
        ZoneId.of(userTimeZoneId); // Validate the final ZoneId
    } catch (Exception e) {
        reportsJspLogger.log(Level.SEVERE, "[REPORTS_JSP_TZ] CRITICAL: Invalid userTimeZoneId resolved: '" + userTimeZoneId + "'. Falling back to UTC. Tenant: " + tenantId, e);
        userTimeZoneId = "UTC";
        String tzErrorMsg = "A critical error occurred with timezone configuration. Report times may be shown in UTC. Please contact support.";
        pageError = (pageError == null) ? tzErrorMsg : pageError + " " + tzErrorMsg;
    }
    reportsJspLogger.info("[REPORTS_JSP_TZ] Reports.jsp final effective userTimeZoneId: " + userTimeZoneId + " for Tenant: " + tenantId);
    // --- End Standardized Timezone Logic ---
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Reports</title>
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reports-container">
        <h1>Reports</h1>

        <%-- Display any page-level errors (like timezone issues) --%>
        <% if (pageError != null) { %>
            <div class="page-message error-message" id="pageErrorNotification"><%= escapeJspHtml(pageError) %></div>
        <% } %>

        <div class="report-display-area">
            <h2 id="reportTitle" class="report-title">
                <%= reportSelected ? "Loading Report..." : "Select Report" %>
            </h2>
            <p id="reportDescription" class="report-description">
                 <%= reportSelected ? "" : "Please choose a report from the 'Reports' menu in the navigation bar." %>
            </p>

            <div id="reportActions" class="report-actions" style="display: none;">
                <button id="fixMissingPunchesBtnReports" class="glossy-button text-orange" style="display: none;" disabled>
                    <i class="fas fa-edit"></i> Fix Missing Punches
                </button>
                <button id="printReportBtn" class="glossy-button">
                    <i class="fas fa-print"></i> Print Report
                </button>
            </div>

            <div id="loadingIndicator" class="loading-indicator" style="<%= reportSelected ? "display: flex;" : "display: none;" %>">
                <div class="spinner"></div>
                Loading report...
            </div>

            <div id="reportOutput" class="report-output">
                <% if (!reportSelected) { %>
                     <p class="report-placeholder">Report results will appear here once selected from the menu.</p>
                <% } %>
            </div>
        </div>
    </div> <%-- End parent-container --%>

    <%-- Edit Punch Modal --%>
    <div id="editPunchModalReports" class="modal">
        <div class="modal-content">
            <span class="close" id="closeEditPunchModalReports">&times;</span>
            <h2>Edit Punch Record</h2>
            <div class="edit-punch-info">
                <p><strong>Employee:</strong> <span id="reports_editPunchEmployeeName"></span></p>
                <p><strong>Schedule:</strong> <span id="reports_editPunchScheduleInfo"></span></p>
            </div>
            <form id="editPunchFormReports" action="AddEditAndDeletePunchesServlet" method="post">
                <input type="hidden" id="reports_editPunchIdField" name="editPunchId">
                <input type="hidden" name="action" value="editPunch">
                <input type="hidden" id="reports_editEmployeeIdField" name="editEmployeeId">
                <%-- Use the correctly determined userTimeZoneId --%>
                <input type="hidden" id="reports_editUserTimeZone" name="userTimeZone" value="<%= escapeJspHtml(userTimeZoneId) %>">
                <input type="hidden" name="editPunchType" value="Supervisor Override">

                <div class="form-item">
                    <label for="reports_editDate">Date: <span class="required-asterisk">*</span></label>
                    <input type="date" id="reports_editDate" name="editDate" required>
                </div>
                <div class="form-item">
                    <label for="reports_editInTime">IN Time (HH:MM:SS):</label>
                    <input type="time" id="reports_editInTime" name="editInTime" step="1">
                </div>
                <div class="form-item">
                    <label for="reports_editOutTime">OUT Time (HH:MM:SS):</label>
                    <input type="time" id="reports_editOutTime" name="editOutTime" step="1">
                </div>
            </form>
            <div class="button-row">
                <button type="submit" form="editPunchFormReports" class="glossy-button text-green">
                    <i class="fas fa-save"></i> Save Changes
                </button>
                <button type="button" id="reports_cancelEditPunch" class="glossy-button text-red">
                    <i class="fas fa-times"></i> Cancel
                </button>
            </div>
        </div>
    </div>

    <%-- Generic Notification Modal --%>
    <div id="notificationModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeNotificationModal">&times;</span>
            <h2 id="notificationModalTitle">Notification</h2>
            <p id="notificationMessage"></p>
            <div class="button-row" style="justify-content: center;">
                <button type="button" id="okButton" class="glossy-button text-blue">OK</button>
            </div>
        </div>
    </div>

    <script type="text/javascript">
        // Pass the determined timezone to JS for AJAX calls etc.
        const effectiveUserTimeZoneId = "<%= escapeJspHtml(userTimeZoneId) %>";
        const initialReport = "<%= initialReportType != null ? escapeJspHtml(initialReportType) : "" %>";
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="js/reports.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>