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
    private static final Logger jspSchedulingPageLogger = Logger.getLogger("scheduling_jsp_wizard_v3_complete");

    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private String escapeForJavaScriptString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("/", "\\/");
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");

    boolean inSetupWizardMode_JSP = false;
    String currentWizardStepForPage_JSP = null; 
    String companyNameSignup_Sched = "Your Company"; 
    boolean scheduleJustAddedInWizard_JSP = "true".equalsIgnoreCase(request.getParameter("scheduleAdded"));
    // Removed showSpecificWizardIntroModal as generic modal flow will handle prompts

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
            String wizardStepFromParam = request.getParameter("wizardStep");

            jspSchedulingPageLogger.info("[scheduling.jsp] Wizard Mode Active. Session step: " + sessionWizardStep + ", URL wizardStepParam: " + wizardStepFromParam);

            if (wizardStepFromParam != null && !wizardStepFromParam.trim().isEmpty()) {
                currentWizardStepForPage_JSP = wizardStepFromParam.trim();
                if (!currentWizardStepForPage_JSP.equals(sessionWizardStep)) {
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                }
            } else {
                currentWizardStepForPage_JSP = sessionWizardStep;
            }
            
            if (currentWizardStepForPage_JSP == null || !currentWizardStepForPage_JSP.startsWith("schedules")) {
                 if (request.getParameter("setup_wizard") != null && (currentWizardStepForPage_JSP == null || currentWizardStepForPage_JSP.equals("departments"))) {
                    currentWizardStepForPage_JSP = "schedules_prompt"; // Default for this page if coming from prior step
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                 } else if (currentWizardStepForPage_JSP != null && !currentWizardStepForPage_JSP.startsWith("schedules")) {
                    // If step is unrelated, may exit wizard mode for this page load
                    // For now, JS will check if the step is valid for its wizardStagesSched
                 }
            }
            jspSchedulingPageLogger.info("[scheduling.jsp] Final currentWizardStepForPage_JSP for JS: " + currentWizardStepForPage_JSP + ", scheduleJustAdded: " + scheduleJustAddedInWizard_JSP);
        } else {
            inSetupWizardMode_JSP = false;
            if(currentSession != null) {
                currentSession.removeAttribute("startSetupWizard");
                currentSession.removeAttribute("wizardStep");
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
            pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? "Access Denied." : pageLevelError + " Access Denied.";
        }
    } else {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    if (tenantId == null || tenantId <= 0) {
        pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? "Invalid session or tenant context." : pageLevelError + " Invalid session or tenant context.";
    }

    List<Map<String, String>> allSchedulesForDropdown = new ArrayList<>();
    String scheduleRowsHtml = "";

    if (pageLevelError == null && tenantId != null && tenantId > 0 ) {
        try {
            allSchedulesForDropdown = ShowReports.getSchedulesForTenant(tenantId); 
            scheduleRowsHtml = ShowSchedules.showSchedules(tenantId); 
        } catch (Exception e) {
            jspSchedulingPageLogger.log(Level.SEVERE, "Error fetching schedule data for T:" + tenantId, e);
            pageLevelError = (pageLevelError == null ? "" : pageLevelError + " ") + "Could not load schedule data: " + e.getMessage();
        }
    }
    
    if (pageLevelError != null && (scheduleRowsHtml == null || scheduleRowsHtml.isEmpty() || !scheduleRowsHtml.contains("report-error-row"))) {
        scheduleRowsHtml = "<tr><td colspan='9' class='report-error-row'>" + escapeJspHtml(pageLevelError) + "</td></tr>";
    } else if ((scheduleRowsHtml == null || scheduleRowsHtml.isEmpty()) && pageLevelError == null){
        scheduleRowsHtml = "<tr><td colspan='9' class='report-message-row'>No schedules found. Use 'Add Schedule' to create one.</td></tr>";
    }
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Schedule Management<% if (inSetupWizardMode_JSP && !"setupComplete".equals(currentWizardStepForPage_JSP)) { %> - Company Setup<% } %></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/scheduling.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; }
        .modal.modal-visible { display: flex !important; }
        .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 550px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left;}
        .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee; text-align:center;}
        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; }
        .button-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
        .page-message { padding: 10px 15px; margin: 0 auto 20px auto; border-radius: 4px; text-align:center;}
        .success-message { background-color: #d4edda; color: #155724; border:1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border:1px solid #f5c6cb; }
        .info-message { background-color: #d1ecf1; color: #0c5460; border-color: #bee5eb; }
        .form-item { margin-bottom: 15px; }
        .form-item label { display: block; margin-bottom: 5px; font-weight: bold; }
        .form-item input[type="text"], .form-item input[type="time"], .form-item input[type="number"], .form-item select { width: calc(100% - 22px); padding: 8px 10px; border-radius: 4px; border: 1px solid #ccc; box-sizing: border-box; }
        .required-asterisk { color: #dc3545; margin-left: 2px; font-weight: bold; }
        .wizard-modal .modal-content { text-align: center;}
        .wizard-modal .modal-content p { margin-bottom: 15px; line-height: 1.6; font-size: 1.05em;}
        .wizard-modal .button-row { justify-content: space-around !important; margin-top: 25px;}
    </style>
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf"%>

    <div class="parent-container reports-container">
        <h1>Schedule Management <% if (inSetupWizardMode_JSP && !"setupComplete".equals(currentWizardStepForPage_JSP)) { %><span style="font-size: 0.8em; color: #555;">(Setup)</span><% } %></h1>

        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message" id="pageNotificationDiv_Success_Sched"><%=escapeJspHtml(pageLevelSuccess)%></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && (scheduleRowsHtml == null || !scheduleRowsHtml.contains("report-error-row"))) { 
        %><div class="page-message error-message" id="pageNotificationDiv_Error_Sched"><%=escapeJspHtml(pageLevelError)%></div><% 
        } %>

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
                                <th data-sort-type="string">Schedule Name</th>
                                <th class="center-text" data-sort-type="string">Start Shift</th>
                                <th class="center-text" data-sort-type="string">Start Lunch</th>
                                <th class="center-text" data-sort-type="string">End Lunch</th>
                                <th class="center-text" data-sort-type="string">End Shift</th>
                                <th data-sort-type="string">Days Scheduled</th>
                                <th class="center-text" data-sort-type="string">Auto Lunch</th>
                                <th class="center-text" data-sort-type="number">Hrs Req.</th>
                                <th class="center-text" data-sort-type="number">Lunch (min)</th>
                            </tr>
                        </thead>
                        <tbody><%=scheduleRowsHtml%></tbody>
                    </table>
                </div>
            </div>
        </div>
        
        <form action="<%=request.getContextPath()%>/AddEditAndDeleteSchedulesServlet" method="POST" id="deleteScheduleForm" style="display: none;">
            <input type="hidden" name="action" value="deleteAndReassignSchedule"> 
            <input type="hidden" name="scheduleNameToDelete" id="hiddenDeleteScheduleName" value="">
            <input type="hidden" name="targetScheduleForReassignment" id="hiddenTargetScheduleForReassignment" value="">
        </form>
    </div>

    <%-- Standard Modals (Add, Edit, Delete/Reassign, General Notification) --%>
    <div id="addScheduleModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeAddModal" data-close-modal-id="addScheduleModal">&times;</span>
            <h2>Add Schedule</h2>
            <form id="addScheduleForm" action="<%=request.getContextPath()%>/AddEditAndDeleteSchedulesServlet" method="post">
                <input type="hidden" name="action" value="add">
                <div class="form-item"><label for="addScheduleName">Schedule Name: <span class="required-asterisk">*</span></label><input type="text" id="addScheduleName" name="scheduleName" required autofocus maxlength="50"></div>
                <h3 class="modal-section-header">Shift Times</h3>
                <div class="form-item"><label for="addShiftStart">Start Shift:</label><input type="time" id="addShiftStart" name="shiftStart"></div>
                <div class="form-item"><label for="addShiftEnd">End Shift:</label><input type="time" id="addShiftEnd" name="shiftEnd"></div>
                <h3 class="modal-section-header">Lunch Times (Optional)</h3>
                <div class="form-item"><label for="addLunchStart">Start Lunch:</label><input type="time" id="addLunchStart" name="lunchStart"></div>
                <div class="form-item"><label for="addLunchEnd">End Lunch:</label><input type="time" id="addLunchEnd" name="lunchEnd"></div>
                <div class="form-item"> <label>Days Worked:</label> <div class="checkbox-group"> <span class="styled-checkbox"><input type="checkbox" id="addDaySun" name="days" value="Sun" data-day-char="S"><label for="addDaySun">Sun</label></span> <span class="styled-checkbox"><input type="checkbox" id="addDayMon" name="days" value="Mon" data-day-char="M"><label for="addDayMon">Mon</label></span> <span class="styled-checkbox"><input type="checkbox" id="addDayTue" name="days" value="Tue" data-day-char="T"><label for="addDayTue">Tue</label></span> <span class="styled-checkbox"><input type="checkbox" id="addDayWed" name="days" value="Wed" data-day-char="W"><label for="addDayWed">Wed</label></span> <span class="styled-checkbox"><input type="checkbox" id="addDayThu" name="days" value="Thu" data-day-char="H"><label for="addDayThu">Thu</label></span> <span class="styled-checkbox"><input type="checkbox" id="addDayFri" name="days" value="Fri" data-day-char="F"><label for="addDayFri">Fri</label></span> <span class="styled-checkbox"><input type="checkbox" id="addDaySat" name="days" value="Sat" data-day-char="A"><label for="addDaySat">Sat</label></span> </div> </div>
                <div class="auto-lunch-section"> <div class="form-item auto-lunch-toggle-group" style="flex-basis: 100%; margin-bottom: 10px;"> <span class="styled-checkbox"><input type="checkbox" id="addAutoLunch" name="autoLunch" value="true"><label for="addAutoLunch">Enable Auto Lunch Deduction</label></span> </div> <div class="form-row"> <div class="form-item"> <label for="addHoursRequired">Hours Required (for Auto Lunch):</label> <input type="number" id="addHoursRequired" name="hoursRequired" min="0" max="24" step="0.01" placeholder="e.g., 6.00" disabled> </div> <div class="form-item"> <label for="addLunchLength">Lunch Length (minutes):</label> <input type="number" id="addLunchLength" name="lunchLength" min="0" max="120" step="1" placeholder="e.g., 30" disabled> </div> </div> </div>
                <div class="button-row">
                    <button type="submit" class="glossy-button text-green"><i class="fas fa-check"></i> Submit</button>
                    <button type="button" id="cancelAddSchedule" class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button>
                </div>
            </form>
        </div>
    </div>

    <div id="editScheduleModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeEditModal" data-close-modal-id="editScheduleModal">&times;</span>
            <h2>Edit Schedule</h2>
            <form id="editScheduleForm" action="<%=request.getContextPath()%>/AddEditAndDeleteSchedulesServlet" method="post">
                <input type="hidden" name="action" value="edit">
                <input type="hidden" name="originalScheduleName" id="hiddenEditOriginalName">
                <div class="form-item"><label for="editScheduleName">Schedule Name:</label><input type="text" id="editScheduleName" name="scheduleName" readonly disabled></div>
                <h3 class="modal-section-header">Shift Times</h3> <div class="form-item"><label for="editShiftStart">Start Shift:</label><input type="time" id="editShiftStart" name="shiftStart"></div> <div class="form-item"><label for="editShiftEnd">End Shift:</label><input type="time" id="editShiftEnd" name="shiftEnd"></div> <h3 class="modal-section-header">Lunch Times (Optional)</h3> <div class="form-item"><label for="editLunchStart">Start Lunch:</label><input type="time" id="editLunchStart" name="lunchStart"></div> <div class="form-item"><label for="editLunchEnd">End Lunch:</label><input type="time" id="editLunchEnd" name="lunchEnd"></div>
                <div class="form-item"> <label>Days Worked:</label> <div class="checkbox-group"> <span class="styled-checkbox"><input type="checkbox" id="editDaySun" name="days" value="Sun" data-day-char="S"><label for="editDaySun">Sun</label></span> <span class="styled-checkbox"><input type="checkbox" id="editDayMon" name="days" value="Mon" data-day-char="M"><label for="editDayMon">Mon</label></span> <span class="styled-checkbox"><input type="checkbox" id="editDayTue" name="days" value="Tue" data-day-char="T"><label for="editDayTue">Tue</label></span> <span class="styled-checkbox"><input type="checkbox" id="editDayWed" name="days" value="Wed" data-day-char="W"><label for="editDayWed">Wed</label></span> <span class="styled-checkbox"><input type="checkbox" id="editDayThu" name="days" value="Thu" data-day-char="H"><label for="editDayThu">Thu</label></span> <span class="styled-checkbox"><input type="checkbox" id="editDayFri" name="days" value="Fri" data-day-char="F"><label for="editDayFri">Fri</label></span> <span class="styled-checkbox"><input type="checkbox" id="editDaySat" name="days" value="Sat" data-day-char="A"><label for="editDaySat">Sat</label></span> </div> </div>
                <div class="auto-lunch-section"> <div class="form-item auto-lunch-toggle-group" style="flex-basis: 100%; margin-bottom: 10px;"> <span class="styled-checkbox"><input type="checkbox" id="editAutoLunch" name="autoLunch" value="true"><label for="editAutoLunch">Enable Auto Lunch Deduction</label></span> </div> <div class="form-row"> <div class="form-item"> <label for="editHoursRequired">Hours Required (for Auto Lunch):</label> <input type="number" id="editHoursRequired" name="hoursRequired" min="0" max="24" step="0.01"> </div> <div class="form-item"> <label for="editLunchLength">Lunch Length (minutes):</label> <input type="number" id="editLunchLength" name="lunchLength" min="0" max="120" step="1"> </div> </div> </div>
                <div class="button-row">
                    <button type="submit" class="glossy-button text-green"><i class="fas fa-save"></i> Update</button>
                    <button type="button" id="cancelEditSchedule" class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button>
                </div>
            </form>
        </div>
    </div>

    <div id="deleteAndReassignSchedModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeDeleteReassignSchedModalBtn">&times;</span>
            <h2>Delete Schedule & Reassign Employees</h2>
            <p id="deleteReassignSchedModalMessage" style="margin-bottom: 15px; line-height: 1.6;"></p>
            <div class="form-item" style="margin-bottom: 20px;">
                <label for="targetReassignSchedSelect">Reassign affected employees to schedule:</label>
                <select id="targetReassignSchedSelect" name="targetReassignSchedSelect_form">
                </select>
            </div>
            <div id="deleteReassignSchedModalError" class="error-message" style="display:none; margin-top:10px; margin-bottom:15px;"></div>
            <div class="button-row">
                <button type="button" id="confirmDeleteAndReassignSchedBtn" class="glossy-button text-red"><i class="fas fa-trash-alt"></i> Delete & Reassign</button>
                <button type="button" id="cancelDeleteReassignSchedBtn" class="cancel-btn glossy-button text-grey"><i class="fas fa-times"></i> Cancel</button>
            </div>
        </div>
    </div>
	
	<div id="notificationModalGeneral" class="modal"><div class="modal-content" style="max-width: 480px;"><span class="close" id="closeNotificationModalGeneralXBtn_Sched" data-close-modal-id="notificationModalGeneral">&times;</span><h2 id="notificationModalGeneralTitle">Notification</h2><p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center; line-height:1.6;"></p><div class="button-row" style="justify-content: center;"><button type="button" id="okButtonNotificationModalGeneral" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue"><i class="fas fa-thumbs-up"></i> OK</button></div></div></div>

    <%-- GENERIC WIZARD MODAL for Scheduling Page --%>
    <% if (inSetupWizardMode_JSP) { %>
        <div id="wizardGenericModal_Scheduling" class="modal wizard-modal" style="display:none; z-index: 10002;">
            <div class="modal-content">
                <span class="close" id="closeWizardGenericModal_Scheduling">&times;</span>
                <h2 id="wizardGenericModalTitle_Scheduling">Setup Step</h2>
                <div id="wizardGenericModalTextContainer_Scheduling" style="margin-top:15px;">
                    <p id="wizardGenericModalText1_Scheduling"></p>
                    <p id="wizardGenericModalText2_Scheduling" style="margin-top:10px; font-size:0.9em;"></p>
                </div>
                <div id="wizardGenericModalButtonRow_Scheduling" class="button-row" style="margin-top:25px;">
                    <%-- Buttons will be dynamically added by JavaScript --%>
                </div>
            </div>
        </div>
    <% } %>

    <script type="text/javascript">
        const appRootPath = "<%= request.getContextPath() %>";
    </script>
	<%@ include file="/WEB-INF/includes/common-scripts.jspf"%>
	<script type="text/javascript">
        window.allAvailableSchedulesForReassign = [
            <% if (allSchedulesForDropdown != null) { 
                boolean firstSched = true;
                for (Map<String, String> sched : allSchedulesForDropdown) {
                    if (sched != null && sched.get("name") != null) {
                        if (!firstSched) { out.print(","); } %>
                        { "name": "<%= escapeForJavaScriptString(sched.get("name")) %>" }<%
                        firstSched = false;
                    }
                }
               } %>
        ];
        
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= currentWizardStepForPage_JSP != null ? escapeForJavaScriptString(currentWizardStepForPage_JSP) : "" %>";
        window.COMPANY_NAME_SIGNUP_JS = "<%= companyNameSignup_Sched != null ? escapeForJavaScriptString(companyNameSignup_Sched) : "Your Company" %>";
        window.SCHEDULE_JUST_ADDED_WIZARD = <%= scheduleJustAddedInWizard_JSP %>;
    </script>
	<script src="<%= request.getContextPath() %>/js/scheduling.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>