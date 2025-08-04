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

        // Check if the setup wizard is active in the session.
        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("step");

            // Prioritize the step from the URL parameter, updating the session if needed.
            if (wizardStepFromParam != null && !wizardStepFromParam.trim().isEmpty()) {
                currentWizardStepForPage_JSP = wizardStepFromParam.trim();
                if (!currentWizardStepForPage_JSP.equals(sessionWizardStep)) {
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                }
            } else {
                currentWizardStepForPage_JSP = sessionWizardStep;
            }
        }
        
        // Retrieve and clear any success/error messages stored in the session.
        if (pageLevelSuccess == null && currentSession.getAttribute("successMessage") != null) {
            pageLevelSuccess = (String) currentSession.getAttribute("successMessage");
            currentSession.removeAttribute("successMessage");
        }
        if (pageLevelError == null && currentSession.getAttribute("errorMessage") != null) {
            pageLevelError = (String) currentSession.getAttribute("errorMessage");
            currentSession.removeAttribute("errorMessage");
        }

        // Verify user has Administrator permissions.
        String userPermissions = (String) currentSession.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            pageLevelError = "Access Denied.";
        }
    } else {
        // If no session, redirect to login page.
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
    
    // Prepare messages for display in the table if there's an error or no data.
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
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/scheduling.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; }
        .wizard-header h2 { margin-top:0; margin-bottom: 5px; font-weight: 500;}
        .wizard-header p { margin-bottom:0; font-size:0.9em; opacity: 0.9;}
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; }
        .modal.modal-visible { display: flex !important; }
        .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 550px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left;}
        .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee; text-align:center;}
        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; }
        .page-message { padding: 10px 15px; margin: 0 auto 20px auto; border-radius: 4px; text-align:center;}
        .success-message { background-color: #d4edda; color: #155724; border:1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border:1px solid #f5c6cb; }
    </style>
</head>
<body class="reports-page">
    <%-- Conditionally hide the standard navbar and show a wizard-specific header --%>
    <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Schedules for <%= escapeJspHtml(companyNameSignup_Sched) %></h2>
            <p>Define standard work schedules for your company. You can assign employees to them later.</p>
        </div>
    <% } %>

    <div class="parent-container reports-container">
        <h1>Schedule Management <% if (inSetupWizardMode_JSP) { %><span style="font-size: 0.8em; color: #555;">(Setup)</span><% } %></h1>

        <%-- Display any success or error messages passed from the servlet --%>
        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message" id="pageNotificationDiv_Success_Sched"><%=escapeJspHtml(pageLevelSuccess)%></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && !isReopenModalRequest) { %><div class="page-message error-message" id="pageNotificationDiv_Error_Sched"><%=escapeJspHtml(pageLevelError)%></div><% } %>

        <div id="button-container" class="main-action-buttons">
            <button type="button" id="addScheduleButton" class="glossy-button text-green"><i class="fas fa-calendar-plus"></i> Add Schedule</button>
            <button type="button" id="editScheduleButton" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Schedule</button>
            <button type="button" id="deleteScheduleButton" class="glossy-button text-red" disabled><i class="fas fa-calendar-times"></i> Delete Schedule</button>
        </div>
        <h4 style="text-align: left; color: #6c757d; margin-bottom: 10px; font-size: 0.9em;" id="instructionTextH4_Scheduling">To Edit or Delete: First select a row from the table below.</h4>

        <div class="report-display-area" style="padding-top: 10px;">
            <div id="reportOutput_schedules" class="report-output">
                <div class="table-container report-table-container">
                    <table class="report-table" id="schedulesTable">
                        <thead>
                            <tr>
                                <th>Schedule Name</th><th>Start Shift</th><th>Start Lunch</th><th>End Lunch</th><th>End Shift</th><th>Days Scheduled</th><th>Auto Lunch</th><th>Hrs Req.</th><th>Lunch (min)</th>
                            </tr>
                        </thead>
                        <tbody><%=scheduleRowsHtml%></tbody>
                    </table>
                </div>
            </div>
        </div>
        
        <%-- Hidden form used for deleting schedules --%>
        <form action="<%=request.getContextPath()%>/AddEditAndDeleteSchedulesServlet" method="POST" id="deleteScheduleForm" style="display: none;"><input type="hidden" name="action" value="deleteAndReassignSchedule"><input type="hidden" name="scheduleNameToDelete" id="hiddenDeleteScheduleName" value=""><input type="hidden" name="targetScheduleForReassignment" id="hiddenTargetScheduleForReassignment" value=""></form>
    </div>

    <%-- Modals --%>
    
    <!-- **FIX START**: Added the generic wizard modal HTML, which was missing. -->
    <div id="wizardGenericModal" class="modal">
        <div class="modal-content" style="max-width: 600px;">
            <span class="close" id="closeWizardGenericModal" data-close-modal-id="wizardGenericModal">&times;</span>
            <h2 id="wizardGenericModalTitle"></h2>
            <p id="wizardGenericModalText1" style="padding: 10px 15px; font-size: 1.1em;"></p>
            <p id="wizardGenericModalText2" style="padding: 0px 15px 15px; font-size: 1em; color: #555;"></p>
            <div class="button-row" id="wizardGenericModalButtonRow" style="justify-content: center; padding-top: 15px; border-top: 1px solid #eee;">
                <!-- Buttons will be injected by JS -->
            </div>
        </div>
    </div>
    <!-- **FIX END** -->

    <div id="addScheduleModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeAddModal" data-close-modal-id="addScheduleModal">&times;</span>
            <h2>Add Schedule</h2>
            <form id="addScheduleForm" action="<%=request.getContextPath()%>/AddEditAndDeleteSchedulesServlet" method="post">
                <input type="hidden" name="action" value="add">
                <%-- Add wizard mode flag to form submission if active --%>
                <% if(inSetupWizardMode_JSP) { %><input type="hidden" name="setup_wizard" value="true"><% } %>
                <div class="form-item"><label for="addScheduleName">Schedule Name: <span class="required-asterisk">*</span></label><input type="text" id="addScheduleName" name="scheduleName" required autofocus maxlength="50"></div>
                <div class="form-item"><label for="addShiftStart">Start Shift:</label><input type="time" id="addShiftStart" name="shiftStart"></div>
                <div class="form-item"><label for="addShiftEnd">End Shift:</label><input type="time" id="addShiftEnd" name="shiftEnd"></div>
                <div class="form-item"><label for="addLunchStart">Start Lunch:</label><input type="time" id="addLunchStart" name="lunchStart"></div>
                <div class="form-item"><label for="addLunchEnd">End Lunch:</label><input type="time" id="addLunchEnd" name="lunchEnd"></div>
                <div class="form-item"><label>Days Worked:</label><div class="checkbox-group"><span class="styled-checkbox"><input type="checkbox" id="addDaySun" name="days" value="Sun"><label for="addDaySun">Sun</label></span><span class="styled-checkbox"><input type="checkbox" id="addDayMon" name="days" value="Mon"><label for="addDayMon">Mon</label></span><span class="styled-checkbox"><input type="checkbox" id="addDayTue" name="days" value="Tue"><label for="addDayTue">Tue</label></span><span class="styled-checkbox"><input type="checkbox" id="addDayWed" name="days" value="Wed"><label for="addDayWed">Wed</label></span><span class="styled-checkbox"><input type="checkbox" id="addDayThu" name="days" value="Thu"><label for="addDayThu">Thu</label></span><span class="styled-checkbox"><input type="checkbox" id="addDayFri" name="days" value="Fri"><label for="addDayFri">Fri</label></span><span class="styled-checkbox"><input type="checkbox" id="addDaySat" name="days" value="Sat"><label for="addDaySat">Sat</label></span></div></div>
                <div class="form-item"><span class="styled-checkbox"><input type="checkbox" id="addAutoLunch" name="autoLunch" value="true"><label for="addAutoLunch">Enable Auto Lunch Deduction</label></span></div>
                <div class="form-row"><div class="form-item"><label for="addHoursRequired">Hours Required:</label><input type="number" id="addHoursRequired" name="hoursRequired" min="0" max="24" step="0.01" disabled></div><div class="form-item"><label for="addLunchLength">Lunch Length (min):</label><input type="number" id="addLunchLength" name="lunchLength" min="0" max="120" step="1" disabled></div></div>
                <div class="button-row"><button type="submit" class="glossy-button text-green">Submit</button><button type="button" id="cancelAddSchedule" class="cancel-btn glossy-button text-red">Cancel</button></div>
            </form>
        </div>
    </div>
    
    <%-- Other Modals (Edit, Delete, Notification) --%>
    <%@ include file="/WEB-INF/includes/scheduling-modals.jspf"%>
	<div id="notificationModalGeneral" class="modal"><div class="modal-content" style="max-width: 480px;"><span class="close" data-close-modal-id="notificationModalGeneral">&times;</span><h2 id="notificationModalGeneralTitle">Notification</h2><p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center; line-height:1.6;"></p><div class="button-row" style="justify-content: center;"><button type="button" id="okButtonNotificationModalGeneral" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue">OK</button></div></div></div>
    
    <script type="text/javascript">
        // Pass server-side data to the client-side JavaScript.
        window.appRootPath = "<%= request.getContextPath() %>";
        window.allAvailableSchedulesForReassign = [ <% if (allSchedulesForDropdown != null) { boolean firstSched = true; for (Map<String, String> sched : allSchedulesForDropdown) { if (sched != null && sched.get("name") != null) { if (!firstSched) { out.print(","); } %> { "name": "<%= escapeForJavaScriptString(sched.get("name")) %>" }<% firstSched = false; }}} %> ];
        // **FIX**: Pass wizard-related flags to JavaScript.
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= currentWizardStepForPage_JSP != null ? escapeForJavaScriptString(currentWizardStepForPage_JSP) : "" %>";
        window.COMPANY_NAME_SIGNUP_JS_SCHED = "<%= escapeForJavaScriptString(companyNameSignup_Sched) %>";
    </script>
	<%@ include file="/WEB-INF/includes/common-scripts.jspf"%>
	<script src="<%= request.getContextPath() %>/js/scheduling.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>
