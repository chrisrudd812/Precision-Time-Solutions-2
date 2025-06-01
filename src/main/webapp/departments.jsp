<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="timeclock.departments.ShowDepartments" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="timeclock.reports.ShowReports" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspDepartmentsPageLogger = Logger.getLogger("departments_jsp_wizard_v_context_fix");

    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private String escapeForJS(String input) { 
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("/", "\\/").replace("<", "\\u003C").replace(">", "\\u003E");
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String userPermissions = "User"; 
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");
    Boolean isWizardMode_JSP = false;
    String wizardStep_JSP = null;
    String wizardWelcomeMessage_JSP = null;
    Integer wizardAdminEid_JSP = null; 
    String companyNameSignup_JSP = "Your Company"; 
    boolean departmentJustAddedInWizard_JSP = "true".equalsIgnoreCase(request.getParameter("deptAdded"));
    String wizardStepFromParam_JSP = request.getParameter("step"); // Changed from wizardStep to step to match settings.jsp redirect

    String THE_APP_CONTEXT_PATH = request.getContextPath(); // Capture context path for use in URLs

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID"); if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        Object userPermsObj = currentSession.getAttribute("Permissions"); if (userPermsObj instanceof String) { userPermissions = (String) userPermsObj; }
        if (userPermissions == null || userPermissions.isEmpty()) userPermissions = "User";
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup"); if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) { companyNameSignup_JSP = (String) companyNameObj; }
        if (pageLevelSuccess == null && currentSession.getAttribute("successMessage") != null) { pageLevelSuccess = (String) currentSession.getAttribute("successMessage"); currentSession.removeAttribute("successMessage");}
        if (pageLevelError == null && currentSession.getAttribute("errorMessage") != null) { pageLevelError = (String) currentSession.getAttribute("errorMessage"); currentSession.removeAttribute("errorMessage");}
        Object wizardModeObj = currentSession.getAttribute("startSetupWizard"); if (wizardModeObj instanceof Boolean) { isWizardMode_JSP = (Boolean) wizardModeObj; }
        
        if (isWizardMode_JSP) {
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            // Use step parameter from URL if available and valid, otherwise session
            if (wizardStepFromParam_JSP != null && !wizardStepFromParam_JSP.trim().isEmpty() && "departments".equals(wizardStepFromParam_JSP.trim())) {
                wizardStep_JSP = wizardStepFromParam_JSP.trim();
                if (!wizardStep_JSP.equals(sessionWizardStep)) {
                    currentSession.setAttribute("wizardStep", wizardStep_JSP); // Update session if param is the new source of truth
                }
            } else {
                wizardStep_JSP = sessionWizardStep; // Fallback to session
            }

            // If still not 'departments' but should be (e.g. coming from settings)
            if (!"departments".equals(wizardStep_JSP) && "true".equalsIgnoreCase(request.getParameter("setup_wizard"))) {
                 if("settings_setup".equals(sessionWizardStep) || "initialPinSetRequired".equals(sessionWizardStep) || "pinChangePending".equals(sessionWizardStep)) {
                    wizardStep_JSP = "departments"; // Force to departments if coming from a prior wizard step
                    currentSession.setAttribute("wizardStep", wizardStep_JSP);
                 }
            }
            
            Object adminEidObj = currentSession.getAttribute("wizardAdminEid"); if (adminEidObj instanceof Integer) { wizardAdminEid_JSP = (Integer) adminEidObj; }
            if ("departments".equals(wizardStep_JSP) || "departments_initial".equals(wizardStep_JSP) || "departments_after_add".equals(wizardStep_JSP)) { // Ensure it's for this page
                Object welcomeMsgObj = currentSession.getAttribute("wizardWelcomeMessage"); if (welcomeMsgObj instanceof String) { wizardWelcomeMessage_JSP = (String) welcomeMsgObj; }
            }
             jspDepartmentsPageLogger.info("[departments.jsp] Wizard Mode. Final Step: " + wizardStep_JSP + ", DeptAdded: " + departmentJustAddedInWizard_JSP + ", Welcome: " + (wizardWelcomeMessage_JSP != null));
        } else { if(currentSession != null) { currentSession.removeAttribute("startSetupWizard"); currentSession.removeAttribute("wizardStep"); }}
    }

    if (tenantId == null || tenantId <= 0) { if (currentSession != null) currentSession.invalidate(); response.sendRedirect(THE_APP_CONTEXT_PATH + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name())); return; }
    if (!"Administrator".equalsIgnoreCase(userPermissions)) { pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? "Access Denied." : pageLevelError + " Access Denied.";}
    List<Map<String, String>> allDeptsForReassignDropdown = new ArrayList<>(); String departmentRowsHtml = "";
    if (pageLevelError == null) { if (tenantId > 0) { try { allDeptsForReassignDropdown = ShowReports.getDepartmentsForTenant(tenantId); departmentRowsHtml = ShowDepartments.showDepartments(tenantId); } catch (Exception e) { pageLevelError = "Could not load department data. " + e.getMessage(); }}}
    if (pageLevelError != null && (departmentRowsHtml == null || departmentRowsHtml.isEmpty() || !departmentRowsHtml.contains("report-error-row"))) { departmentRowsHtml = "<tr><td colspan='3' class='report-error-row'>" + escapeJspHtml(pageLevelError) + "</td></tr>"; }
    else if ((departmentRowsHtml == null || departmentRowsHtml.trim().isEmpty()) && pageLevelError == null) { departmentRowsHtml = "<tr><td colspan='3' class='report-message-row'>No departments found.</td></tr>"; }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Department Management<% if (Boolean.TRUE.equals(isWizardMode_JSP) && (wizardStep_JSP != null && !wizardStep_JSP.equals("setupComplete"))) { %> - Company Setup<% } %></title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="<%= THE_APP_CONTEXT_PATH %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= THE_APP_CONTEXT_PATH %>/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= THE_APP_CONTEXT_PATH %>/css/payroll.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style> /* Your existing styles for departments.jsp from Turn 37 */ 
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; } .modal.modal-visible { display: flex !important; } .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 600px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left; } .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee; text-align:center;} .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; } .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; border-radius: 0 0 5px 5px; } .wizard-header h2 { margin-top:0; margin-bottom: 5px; font-weight: 500;} .wizard-header p { margin-bottom:0; font-size:0.9em; opacity: 0.9;} .page-message { padding: 10px 15px; margin: 0 auto 20px auto; border-radius: 4px; text-align: center; max-width: calc(100% - 30px); font-size: 0.9em; display: flex; align-items:center; } .page-message i { margin-right: 8px; font-size: 1.2em;} .success-message { background-color: #d4edda; color: #155724; border:1px solid #c3e6cb; } .error-message { background-color: #f8d7da; color: #721c24; border:1px solid #f5c6cb; } .info-message { background-color: #d1ecf1; color: #0c5460; border-color: #bee5eb; }
    </style>
</head>
<body class="reports-page">
    <% if (!Boolean.TRUE.equals(isWizardMode_JSP) || !"departments".equals(wizardStep_JSP)) { // Show navbar if not in this specific wizard step %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Departments for <%= escapeJspHtml(companyNameSignup_JSP) %></h2>
            <p>Define the departments within your company. The 'None' department is a default. You can add more now or skip this step.</p>
        </div>
    <% } %>

    <div class="parent-container reports-container">
        <h1>Manage Departments<% if (Boolean.TRUE.equals(isWizardMode_JSP) && "departments".equals(wizardStep_JSP)) { %> <span style="font-size:0.8em; color:#555;">(Setup Step)</span><% } %></h1>
        <%-- Messages display --%>
        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message" id="pageSuccessMessage_Dept"><i class="fas fa-check-circle"></i><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && (departmentRowsHtml == null || !departmentRowsHtml.contains("report-error-row"))) { %><div class="page-message error-message" id="pageErrorMessage_Dept"><i class="fas fa-exclamation-triangle"></i><%= escapeJspHtml(pageLevelError) %></div><% } %>
        <% if (Boolean.TRUE.equals(isWizardMode_JSP) && wizardWelcomeMessage_JSP != null && ("departments".equals(wizardStep_JSP) || "departments_initial".equals(wizardStep_JSP)) && !departmentJustAddedInWizard_JSP ) { %><div class="page-message info-message" id="wizardPageWelcomeMsg"><i class="fas fa-info-circle"></i><%= escapeJspHtml(wizardWelcomeMessage_JSP) %></div><% } %>
        
        <div id="button-container" class="main-action-buttons">
            <button type="button" id="addDepartmentButton" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add Department</button>
            <button type="button" id="editDepartmentButton" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Department</button>
            <button type="button" id="deleteDepartmentButton" class="glossy-button text-red" disabled><i class="fas fa-trash-alt"></i> Delete Department</button>
        </div>
        <h4 style="text-align: left; color: #6c757d; margin-bottom: 10px; font-size: 0.9em;" id="instructionTextH4">To Edit or Delete: Select a row from the table.</h4>
        <div class="table-container report-table-container department-table-container" id="departmentTableContainer">
            <table id="departmentsTable" class="report-table">
                <thead><tr><th data-sort-type="string">Name</th><th data-sort-type="string">Description</th><th data-sort-type="string">Supervisor</th></tr></thead>
                <tbody><%= departmentRowsHtml %></tbody>
            </table>
        </div>

        <% if (Boolean.TRUE.equals(isWizardMode_JSP) && ("departments".equals(wizardStep_JSP) || "departments_initial".equals(wizardStep_JSP) || "departments_after_add".equals(wizardStep_JSP))) { %>
            <div class="wizard-navigation" style="text-align: right; margin-top: 30px; padding-top:20px; border-top: 1px solid #eee;">
                 <button type="button" id="wizardDepartmentsSkipOrNextButton" class="glossy-button text-blue" style="padding: 10px 20px; font-size: 1.1em;">
                    Next: Schedules Setup <i class="fas fa-arrow-right"></i>
                </button>
            </div>
        <% } %>
    </div>

    <%-- Modals as per your Turn 37 --%>
    <div id="addDepartmentModal" class="modal"> <div class="modal-content"> <span class="close" id="closeAddDeptModalX">&times;</span> <h2>Add New Department</h2> <form id="addDepartmentForm" action="<%=THE_APP_CONTEXT_PATH%>/AddAndDeleteDepartmentsServlet" method="post"> <input type="hidden" name="action" value="addDepartment"> <div class="form-item"> <label for="addDeptName">Name:<span class="required-asterisk">*</span></label> <input type="text" id="addDeptName" name="addDepartmentName" required maxlength="100" autofocus></div> <div class="form-item"><label for="addDeptDescription">Description:</label><input type="text" id="addDeptDescription" name="addDescription" maxlength="255"></div> <div class="form-item"><label for="addDeptSupervisor">Supervisor:</label><input type="text" id="addDeptSupervisor" name="addSupervisor" maxlength="100"></div> <div class="button-row"><button type="submit" class="glossy-button text-green">Submit</button><button type="button" id="cancelAddDeptButton" class="glossy-button text-red">Cancel</button></div></form></div></div>
    <div id="editDepartmentModal" class="modal"><div class="modal-content"><span class="close" id="closeEditDeptModalX">&times;</span><h2>Edit Department</h2><form id="editDepartmentForm" action="<%=THE_APP_CONTEXT_PATH%>/AddAndDeleteDepartmentsServlet" method="post"><input type="hidden" name="action" value="editDepartment"><input type="hidden" id="originalDeptName" name="originalDepartmentName"><div class="form-item"><label for="editDeptNameDisplay">Name:</label><input type="text" id="editDeptNameDisplay" name="editDeptNameDisplay_form" readonly disabled></div><div class="form-item"><label for="editDeptDescription">Description:</label><input type="text" id="editDeptDescription" name="editDescription" maxlength="255"></div><div class="form-item"><label for="editDeptSupervisor">Supervisor:</label><input type="text" id="editDeptSupervisor" name="editSupervisor" maxlength="100"></div><div class="button-row"><button type="submit" class="glossy-button text-green">Update</button><button type="button" id="cancelEditDeptButton" class="glossy-button text-red">Cancel</button></div></form></div></div>
    <form action="<%=THE_APP_CONTEXT_PATH%>/AddAndDeleteDepartmentsServlet" method="POST" id="deleteDepartmentForm" style="display:none;"><input type="hidden" name="action" value="deleteAndReassignDepartment"><input type="hidden" name="departmentNameToDelete" id="hiddenDeleteDepartmentName" value=""><input type="hidden" name="targetDepartmentForReassignment" id="hiddenTargetDepartmentForReassignment" value=""></form>
    <div id="deleteAndReassignDeptModal" class="modal"><div class="modal-content"><span class="close" id="closeDeleteReassignModalBtn">&times;</span><h2>Delete & Reassign</h2><p id="deleteReassignModalMessage"></p><div class="form-item"><label for="targetReassignDeptSelect">Reassign to:</label><select id="targetReassignDeptSelect" name="targetReassignDeptSelect_form"></select></div><div id="deleteReassignModalError" class="error-message" style="display:none;"></div><div class="button-row"><button type="button" id="confirmDeleteAndReassignBtn" class="glossy-button text-red">Delete & Reassign</button><button type="button" id="cancelDeleteReassignBtn" class="glossy-button text-grey">Cancel</button></div></div></div>
    <div id="notificationModalGeneral" class="modal"><div class="modal-content" style="max-width:480px;"><span class="close" id="closeNotificationModalGeneralXBtn_Dept" data-close-modal-id="notificationModalGeneral">&times;</span><h2 id="notificationModalGeneralTitle">Notification</h2><p id="notificationModalGeneralMessage" style="padding:15px 20px;text-align:center;line-height:1.6;"></p><div class="button-row" style="justify-content:center;"><button type="button" id="okButtonNotificationModalGeneral" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue">OK</button></div></div></div>
    <% if (Boolean.TRUE.equals(isWizardMode_JSP)) { %><div id="wizardGenericModal" class="modal wizard-modal" style="display:none; z-index:10002;"><div class="modal-content"><span class="close" id="closeWizardGenericModal">&times;</span><h2 id="wizardGenericModalTitle"></h2><div id="wizardGenericModalTextContainer" style="margin-top:15px;"><p id="wizardGenericModalText1"></p><p id="wizardGenericModalText2" style="margin-top:10px; font-size:0.9em;"></p></div><div id="wizardGenericModalButtonRow" class="button-row" style="margin-top:25px;"></div></div></div><% } %>

    <script type="text/javascript">
        // Make appRootPath globally available for other scripts like departments.js
        const appRootPath = "<%= THE_APP_CONTEXT_PATH %>"; 
        console.log("[departments.jsp] Global appRootPath for JS: '" + appRootPath + "'");
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript">
        const IS_WIZARD_MODE_DEPARTMENTS = <%= Boolean.TRUE.equals(isWizardMode_JSP) %>;
        const CURRENT_WIZARD_STEP_DEPARTMENTS = "<%= wizardStep_JSP != null ? escapeForJS(wizardStep_JSP) : "" %>";
        const WIZARD_ADMIN_EID_DEPARTMENTS = <%= wizardAdminEid_JSP == null ? "null" : wizardAdminEid_JSP %>;
        const DEPARTMENT_JUST_ADDED_WIZARD = <%= departmentJustAddedInWizard_JSP %>;
        const COMPANY_NAME_SIGNUP_JS = "<%= companyNameSignup_JSP != null ? escapeForJS(companyNameSignup_JSP) : "Your Company" %>";
        window.allAvailableDepartmentsForReassign = [ <% if (allDeptsForReassignDropdown != null) { boolean firstDept = true; for (Map<String, String> dept : allDeptsForReassignDropdown) { if (dept != null && dept.get("name") != null) { if (!firstDept) { out.print(","); } %> { "name": "<%= escapeForJS(dept.get("name")) %>" }<% firstDept = false; }}} %> ];
    </script>
    <script src="<%= THE_APP_CONTEXT_PATH %>/js/departments.js?v=<%= System.currentTimeMillis() %>"></script> 

    <% if (Boolean.TRUE.equals(isWizardMode_JSP) && ("departments".equals(wizardStep_JSP) || "departments_initial".equals(wizardStep_JSP) || "departments_after_add".equals(wizardStep_JSP))) { %>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            const wizardDeptsNextButton = document.getElementById('wizardDepartmentsSkipOrNextButton');
            // appRootPath is already defined globally above this script block.
            
            console.log("[departments.jsp 'Next' Button JS] Initial appRootPath (from global const): '" + appRootPath + "'");

            if (wizardDeptsNextButton) {
                wizardDeptsNextButton.addEventListener('click', function() {
                    console.log("[departments.jsp 'Next' Button JS] Inside click - Using appRootPath: '" + appRootPath + "'");
                    
                    const servletPath = "/WizardStatusServlet"; // Servlet is mapped relative to context root
                    let fetchUrl = appRootPath + servletPath;
                    // Handle if appRootPath is "/" (shouldn't be if context is /Clockify) or empty
                    if (appRootPath === "/" && servletPath.startsWith("/")) { fetchUrl = servletPath; }
                    else if (appRootPath === "") { fetchUrl = servletPath; } // Should be "/WizardStatusServlet"

                    console.log("[departments.jsp JS] Wizard 'Next: Schedules Setup' button clicked. Attempting to POST to: '" + fetchUrl + "'");
                    
                    this.disabled = true;
                    this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Proceeding...';

                    fetch(fetchUrl, {
                        method: 'POST',
                        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                        body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': 'schedules_prompt'})
                    })
                    .then(response => {
                        if (!response.ok) {
                            const status = response.status; const statusText = response.statusText;
                            return response.text().then(text => { throw { name: "HTTPError", status: status, statusText: statusText, message: `Server responded with ${status}${statusText ? (": " + statusText) : ""}`, responseText: text }; });
                        }
                        return response.json();
                    })
                    .then(data => {
                        if (data.success && data.nextStep) {
                            console.log("[departments.jsp JS] Wizard step successfully set to '" + data.nextStep + "'. Redirecting.");
                            let redirectUrl = "";
                            if (appRootPath && appRootPath !== "/") {
                                redirectUrl = `${appRootPath}/scheduling.jsp?setup_wizard=true&step=${data.nextStep}`;
                            } else { // Root context
                                redirectUrl = `/scheduling.jsp?setup_wizard=true&step=${data.nextStep}`;
                            }
                            if (appRootPath === "" && !redirectUrl.startsWith("/")) { // Ensure leading slash if root context was empty
                                redirectUrl = "/" + redirectUrl;
                            }
                            console.log("[departments.jsp JS] Redirecting to: '" + redirectUrl + "'");
                            window.location.href = redirectUrl;
                        } else {
                            console.warn("[departments.jsp JS] Failed to set wizard step:", (data.error || "Unknown error"));
                            alert("Error proceeding: " + (data.error || "Wizard step update failed."));
                            this.disabled = false; this.innerHTML = 'Next: Schedules Setup <i class="fas fa-arrow-right"></i>';
                        }
                    })
                    .catch(error => {
                        console.error("[departments.jsp JS] Error during fetch/processing:", error);
                        let errorMsg = "Could not proceed.";
                        if (error && error.name === "HTTPError") { errorMsg = `Server error: ${error.status} ${error.statusText}.`; console.error("Server response:", error.responseText); }
                        else if (error && error.message) { errorMsg = error.message; }
                        alert(errorMsg + " Check console for details.");
                        this.disabled = false; this.innerHTML = 'Next: Schedules Setup <i class="fas fa-arrow-right"></i>';
                    });
                });
            } else {
                console.warn("[departments.jsp 'Next' Button JS] Wizard Next button ('wizardDepartmentsSkipOrNextButton') not found.");
            }
        });
    </script>
    <% } %>
</body>
</html>