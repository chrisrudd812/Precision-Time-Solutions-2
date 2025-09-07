<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@ page import="jakarta.servlet.http.HttpSession"%>
<%@ page import="timeclock.scheduling.ShowSchedules"%>
<%@ page import="timeclock.reports.ShowReports"%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.nio.charset.StandardCharsets"%>
<%@ page import="java.util.logging.Logger"%>
<%@ page import="java.util.logging.Level"%>

<%!
    // Helper function to prevent XSS attacks by escaping HTML characters.
    private String escapeJspHtml(String input) { if (input == null) return ""; return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    // Helper function to safely embed Java strings into JavaScript code.
    private String escapeForJavaScriptString(String input) { if (input == null) return ""; return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("/", "\\/"); }
%>
<%
    // --- SESSION AND PERMISSION VALIDATION ---
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");
    boolean isReopenModalRequest = request.getParameter("reopenModal") != null;

    // --- WIZARD SETUP LOGIC ---
    boolean inSetupWizardMode_JSP = false;
    String currentWizardStepForPage_JSP = null;
    String companyNameSignup_Sched = "Your Company"; // Default company name
    boolean scheduleJustAddedInWizard_JSP = "true".equalsIgnoreCase(request.getParameter("scheduleAdded"));

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }

        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_Sched = (String) companyNameObj;
        }

        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("step");

            if (wizardStepFromParam != null && !wizardStepFromParam.trim().isEmpty()) {
                currentWizardStepForPage_JSP = wizardStepFromParam.trim();
                if (!currentWizardStepForPage_JSP.equals(sessionWizardStep)) {
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                }
            } else {
                currentWizardStepForPage_JSP = sessionWizardStep;
            }
        }
        
        if (pageLevelSuccess == null && currentSession.getAttribute("successMessage") != null) {
            pageLevelSuccess = (String) currentSession.getAttribute("successMessage");
            currentSession.removeAttribute("successMessage");
        }
        if (pageLevelError == null && currentSession.getAttribute("errorMessage") != null) {
            pageLevelError = (String) currentSession.getAttribute("errorMessage");
            currentSession.removeAttribute("errorMessage");
        }

        String userPermissions = (String) currentSession.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            pageLevelError = "Access Denied.";
        }
    } else {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    // --- DATA FETCHING ---
    List<Map<String, String>> allSchedulesForDropdown = new ArrayList<>();
    String scheduleRowsHtml = "";
    String dataFetchError = null;

    if (tenantId != null && tenantId > 0 ) {
        try {
            allSchedulesForDropdown = ShowReports.getSchedulesForTenant(tenantId);
            scheduleRowsHtml = ShowSchedules.showSchedules(tenantId);
        } catch (Exception e) {
            dataFetchError = "Could not load schedule data: " + e.getMessage();
        }
    } else {
         dataFetchError = "Invalid session or tenant context.";
    }
    
    if (dataFetchError != null) {
        scheduleRowsHtml = "<tr><td colspan='9' class='report-error-row'>" + escapeJspHtml(dataFetchError) + "</td></tr>";
    } else if ((scheduleRowsHtml == null || scheduleRowsHtml.isEmpty())){
        scheduleRowsHtml = "<tr><td colspan='9' class='report-message-row'>No schedules found. Use 'Add Schedule' to create one.</td></tr>";
    }
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Schedule Management<% if (inSetupWizardMode_JSP) { %> - Company Setup<% } %></title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/scheduling.css?v=<%=System.currentTimeMillis()%>">
</head>
<body class="reports-page">
    <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Schedules for <%= escapeJspHtml(companyNameSignup_Sched) %></h2>
            <p>Define standard work schedules for your company. You can assign employees to them later.</p>
        </div>
    <% } %>

    <div class="parent-container reports-container">
        <h1>Schedule Management <% if (inSetupWizardMode_JSP) { %><span class="setup-label">(Setup)</span><% } %></h1>

        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message" id="pageNotificationDiv_Success_Sched"><%=escapeJspHtml(pageLevelSuccess)%></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && !isReopenModalRequest) { %><div class="page-message error-message" id="pageNotificationDiv_Error_Sched"><%=escapeJspHtml(pageLevelError)%></div><% } %>

        <div id="button-container" class="main-action-buttons">
            <button type="button" id="addScheduleButton" class="glossy-button text-green"><i class="fas fa-calendar-plus"></i> Add Schedule</button>
            <button type="button" id="editScheduleButton" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Schedule</button>
            <button type="button" id="deleteScheduleButton" class="glossy-button text-red" disabled><i class="fas fa-calendar-times"></i> Delete Schedule</button>
        </div>
        <h4 class="instruction-text" id="instructionTextH4_Scheduling">To Edit or Delete: First select a row from the table below.</h4>

        <div class="report-display-area">
            <div id="reportOutput_schedules" class="report-output">
                <div class="table-container report-table-container">
                    <table id="schedulesTable"
       class="report-table sortable"
       data-initial-sort-column="0"
       data-initial-sort-direction="asc">
    <thead>
        <tr>
            <th class="sortable" data-sort-type="string">Schedule Name</th>
            <th class="sortable" data-sort-type="string">Start Shift</th>
            <th class="sortable" data-sort-type="string">Start Lunch</th>
            <th class="sortable" data-sort-type="string">End Lunch</th>
            <th class="sortable" data-sort-type="string">End Shift</th>
            <th class="sortable" data-sort-type="string">Days Scheduled</th>
            <th class="sortable" data-sort-type="string">Auto Lunch</th>
            <th class="sortable" data-sort-type="number">Hrs Req.</th>
            <th class="sortable" data-sort-type="number">Lunch (min)</th>
        </tr>
    </thead>
    <tbody>
        <%=scheduleRowsHtml%>
    </tbody>
</table>
                </div>
            </div>
        </div>
        
        <form action="<%=request.getContextPath()%>/AddEditAndDeleteSchedulesServlet" method="POST" id="deleteScheduleForm" style="display: none;"><input type="hidden" name="action" value="deleteAndReassignSchedule"><input type="hidden" name="scheduleNameToDelete" id="hiddenDeleteScheduleName" value=""><input type="hidden" name="targetScheduleForReassignment" id="hiddenTargetScheduleForReassignment" value=""></form>
    </div>

    <%-- Modals --%>
    <%@ include file="/WEB-INF/includes/scheduling-modals.jspf"%>
    <%@ include file="/WEB-INF/includes/notification-modals.jspf"%>
    
    <script type="text/javascript">
        // Pass JSP variables to the global window object for access in external JS
        window.appRootPath = "<%= request.getContextPath() %>";
        window.allAvailableSchedulesForReassign = [ <% if (allSchedulesForDropdown != null) { boolean firstSched = true; for (Map<String, String> sched : allSchedulesForDropdown) { if (sched != null && sched.get("name") != null) { if (!firstSched) { out.print(","); } %> { "name": "<%= escapeForJavaScriptString(sched.get("name")) %>" }<% firstSched = false; }}} %> ];
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= currentWizardStepForPage_JSP != null ? escapeForJavaScriptString(currentWizardStepForPage_JSP) : "" %>";
        window.COMPANY_NAME_SIGNUP_JS_SCHED = "<%= escapeForJavaScriptString(companyNameSignup_Sched) %>";
    </script>
	<%@ include file="/WEB-INF/includes/common-scripts.jspf"%>
	<script src="<%= request.getContextPath() %>/js/scheduling.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>
