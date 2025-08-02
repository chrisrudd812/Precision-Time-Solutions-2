<%@page import="timeclock.employees.ShowEmployees"%>
<%@page import="timeclock.reports.ShowReports"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.sql.*, java.util.*, java.time.*, java.time.format.*, java.net.*, java.nio.charset.StandardCharsets, java.util.logging.*" %>

<%!
    private String escapeJspHtml(String input) { if (input == null) return ""; return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private String escapeForJavaScriptString(String input) { if (input == null) return ""; return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
    private boolean isValidWizardStep(String step) { 
        if (step == null) return false; 
        switch(step) {
            case "editAdminProfile": case "addMoreEmployees": case "setupComplete": return true; 
            default: return false;
        } 
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");
    boolean inSetupWizardMode_JSP = false; 
    String currentWizardStep_JSP = ""; 
    String adminEidForWizardEdit_JSP = "";
    boolean initialWizardModalShouldShow_AdminProfileIntro_JSP = false;
    String companyNameSignup_Employees = "Your Company";
    
    List<Map<String, String>> departmentsList = new ArrayList<>();
    List<Map<String, String>> schedulesList = new ArrayList<>();
    List<Map<String, String>> accrualsList = new ArrayList<>();
    
    String requestAction = request.getParameter("action");
    String employeeRowsHtml = "";
    String employeeTableError = null;

    if (currentSession == null || currentSession.getAttribute("TenantID") == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session required.", StandardCharsets.UTF_8));
        return; 
    }

    tenantId = (Integer) currentSession.getAttribute("TenantID");
    Object eidSessionObj = currentSession.getAttribute("EID");
    String loggedInAdminEid_Session = (eidSessionObj instanceof Integer) ? String.valueOf(eidSessionObj) : "";

    Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
    if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
        companyNameSignup_Employees = (String) companyNameObj;
    }

    if (pageLevelSuccess == null && currentSession.getAttribute("successMessage") != null) {
        pageLevelSuccess = (String) currentSession.getAttribute("successMessage");
        currentSession.removeAttribute("successMessage");
    }
    
    if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
        inSetupWizardMode_JSP = true;
        String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
        if ("edit_admin_profile".equals(requestAction)) { currentWizardStep_JSP = "editAdminProfile"; } 
        else if ("prompt_add_employees".equals(requestAction)) { currentWizardStep_JSP = "addMoreEmployees"; }
        else { currentWizardStep_JSP = isValidWizardStep(sessionWizardStep) ? sessionWizardStep : ""; }

        if (isValidWizardStep(currentWizardStep_JSP) && !currentWizardStep_JSP.equals(sessionWizardStep)) {
            currentSession.setAttribute("wizardStep", currentWizardStep_JSP);
        }

        if ("editAdminProfile".equals(currentWizardStep_JSP)) {
            adminEidForWizardEdit_JSP = loggedInAdminEid_Session;
            if (adminEidForWizardEdit_JSP.isEmpty()) { pageLevelError = "Error: Admin ID missing."; inSetupWizardMode_JSP = false; 
            } else if (currentSession.getAttribute("admin_profile_intro_shown_employees_wizard") == null) {
                initialWizardModalShouldShow_AdminProfileIntro_JSP = true;
            }
        }
    }
    
    try { 
        departmentsList = ShowReports.getDepartmentsForTenant(tenantId);
        schedulesList = ShowReports.getSchedulesForTenant(tenantId); 
        accrualsList = ShowReports.getAccrualPoliciesForTenant(tenantId); 
        employeeRowsHtml = ShowEmployees.showEmployees(tenantId);
    } catch (Exception e) { 
        employeeTableError = "Could not load employee list: " + e.getMessage();
    }
    
    if (employeeTableError != null) { 
        employeeRowsHtml = "<tr><td colspan='12' class='report-error-row'>" + escapeJspHtml(employeeTableError) + "</td></tr>";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Employee Management<% if (inSetupWizardMode_JSP) { %> - Company Setup<% } %></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/employees.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    
    <link rel="icon" href="<%= request.getContextPath() %>/Images/favicon.png" type="image/png">
    
    <style>
        .wizard-modal .modal-content { text-align: center; }
        .wizard-modal .modal-content p { margin-bottom: 15px; line-height: 1.6; font-size: 1.05em; }
        #deactivateConfirmModal .modal-content p { padding: 15px 25px 0; text-align: center; line-height: 1.6; }
        .detail-group { display: flex; flex-direction: column; justify-content: flex-start; }
        .detail-group form { margin-top: auto; padding-top: 1rem; align-self: center; }
    </style>
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <div class="parent-container reports-container">
        <h1>Employee Management<% if (inSetupWizardMode_JSP) { %> <span style='font-size:0.8em; color:#555;'>(Setup)</span><% } %></h1>
        <% if (pageLevelSuccess != null) { %><div class="page-message success-message"><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null) { %><div class="page-message error-message"><%= escapeJspHtml(pageLevelError) %></div><% } %>
        
        <% if (inSetupWizardMode_JSP) { %>
            <div id="wizardAdminProfileIntroModal" class="modal wizard-modal"><div class="modal-content"><span class="close" data-close-modal-id="wizardAdminProfileIntroModal">&times;</span><h2>Company Setup: Administrator Profile</h2><div style="padding: 15px 25px;"><p>The next step is to review and complete your employee profile for <strong><%= escapeJspHtml(companyNameSignup_Employees) %></strong>.</p><p>Please verify all details, especially your <strong>Department</strong>, <strong>Schedule</strong>, and <strong>Accrual Policy</strong>.</p></div><div class="button-row" style="justify-content: center;"><button type="button" id="okAdminProfileIntroButton" class="glossy-button text-blue"><i class="fas fa-check-circle"></i> OK, Let's Review!</button></div></div></div>
            <div id="wizardPromptAddEmployeesModal" class="modal wizard-modal"><div class="modal-content"><span class="close" data-close-modal-id="wizardPromptAddEmployeesModal">&times;</span><h2>Company Setup: Add Employees</h2><div id="wizardPromptAddEmployeesMessage" style="padding: 15px 25px;"></div><div class="button-row" style="justify-content: space-between;"><button type="button" id="wizardTriggerAddEmployeeModalButton" class="glossy-button text-green" style="flex-grow:1;"><i class="fas fa-user-plus"></i> Add Another Employee</button><button type="button" id="wizardDoneAddingEmployeesButton" class="glossy-button text-orange" style="flex-grow:1;"><i class="fas fa-flag-checkered"></i> Finish Setup</button></div></div></div>
            <div id="wizardSetupCompleteModal" class="modal wizard-modal"><div class="modal-content"><span class="close" data-close-modal-id="wizardSetupCompleteModal">&times;</span><h2>Setup Complete!</h2><p><i class="fas fa-check-circle fa-3x" style="color: #28a745;"></i></p><p>Congratulations, <strong><%= escapeJspHtml(companyNameSignup_Employees) %></strong> is all set up!</p><div class="button-row"><button type="button" id="wizardHelpButton" class="glossy-button text-blue" style="flex-basis: 48%;"><i class="fas fa-question-circle"></i> View Help</button><button type="button" id="wizardOkFinalButton" class="glossy-button text-green" style="flex-basis: 48%;"><i class="fas fa-thumbs-up"></i> Go to Employee Management</button></div></div></div>
        <% } %>

        <div id="button-container" class="main-action-buttons"><button type="button" id="addEmployeeButton" class="glossy-button text-green"><i class="fas fa-user-plus"></i> Add Employee</button><button type="button" id="editEmployeeButton" class="glossy-button text-orange" disabled><i class="fas fa-user-edit"></i> Edit Employee</button><button type="button" id="deactivateEmployeeButton" class="glossy-button text-red" disabled><i class="fas fa-user-slash"></i> Deactivate Employee</button></div>
        <div class="table-container report-table-container employee-table-container"><table id="employeesTable" class="report-table employee-table"><thead><tr><th>Emp ID</th><th>First Name</th><th>Last Name</th><th>Department</th><th>Schedule</th><th>Supervisor</th><th>Permissions</th><th>E-mail</th><th>Hire Date</th><th>Work Sched</th><th>Wage Type</th><th>Wage</th></tr></thead><tbody><%= employeeRowsHtml %></tbody></table></div>
        <div id="employeeDetailsSection" style="display: none;"><div class="details-grid"><div class="detail-group"><h3>Personal Information</h3><p><label>Employee ID:</label><span id="detailEID">--</span></p><p><label>First Name:</label><span id="detailFirstName">--</span></p><p><label>Last Name:</label><span id="detailLastName">--</span></p><p><label>Address:</label><span id="detailAddress">--</span></p><p><label>City:</label><span id="detailCity">--</span></p><p><label>State:</label><span id="detailState">--</span></p><p><label>Zip:</label><span id="detailZip">--</span></p><p><label>Phone:</label><span id="detailPhone">--</span></p><p><label>E-mail:</label><span id="detailEmail">--</span></p></div><div class="detail-group"><h3>Company Information</h3><p><label>Department:</label><span id="detailDept">--</span></p><p><label>Schedule:</label><span id="detailSchedule">--</span></p><p><label>Supervisor:</label><span id="detailSupervisor">--</span></p><p><label>Permissions:</label><span id="detailPermissions">--</span></p><p><label>Hire Date:</label><span id="detailHireDate">--</span></p><p><label>Work Schedule:</label><span id="detailWorkSched">--</span></p><p><label>Wage Type:</label><span id="detailWageType">--</span></p><p><label>Wage:</label><span id="detailWage">--</span></p></div><div class="detail-group" id="accrualInfoGroup"><h3>Accrual Information</h3><p><label>Accrual Policy:</label><span id="detailAccrualPolicy">--</span></p><p><label>Vacation Hours:</label><span id="detailVacHours">--</span></p><p><label>Sick Hours:</label><span id="detailSickHours">--</span></p><p><label>Personal Hours:</label><span id="detailPersHours">--</span></p><form action="<%=request.getContextPath()%>/EmployeeInfoServlet" method="get" id="resetPasswordForm"><input type="hidden" name="action" value="resetPassword"><input type="hidden" name="eid" id="resetFormEid" value=""><button type="submit" id="btnResetPassword" class="glossy-button text-orange" disabled><i class="fas fa-key"></i> Reset PIN</button></form></div></div></div>
    </div>

    <div id="addEmployeeModal" class="modal"><div class="modal-content"><span class="close" id="closeAddEmployeeModal">&times;</span><h2>Add Employee</h2><form id="addEmployeeForm" action="<%=request.getContextPath()%>/AddEditAndDeleteEmployeesServlet" method="post"><input type="hidden" name="action" value="addEmployee"/><div class="form-item"><label>Employee ID:</label><input type="text" placeholder="System Assigned" readonly disabled></div><div class="form-row"><div class="form-item"><label for="addFirstName">First Name:<span class="required-asterisk">*</span></label><input type="text" id="addFirstName" name="addFirstName" required maxlength="50"></div><div class="form-item"><label for="addLastName">Last Name:<span class="required-asterisk">*</span></label><input type="text" id="addLastName" name="addLastName" required maxlength="50"></div></div><div class="form-row"><div class="form-item"><label for="addDepartmentsDropDown">Department:</label><select id="addDepartmentsDropDown" name="addDepartmentsDropDown"><option value="None">None</option><% for(Map<String,String> d : departmentsList){ String n=d.get("name"); if(!"None".equalsIgnoreCase(n)){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }} %></select></div><div class="form-item"><label for="addSchedulesDropDown">Schedule:</label><select id="addSchedulesDropDown" name="addSchedulesDropDown"><option value="">-- Unassigned --</option><% for(Map<String,String> s : schedulesList){ String n=s.get("name"); %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% } %></select></div></div><div class="form-row"><div class="form-item"><label for="addSupervisor">Supervisor:</label><input type="text" id="addSupervisor" name="addSupervisor" value="None" maxlength="100"></div><div class="form-item"><label for="addAccrualsDropDown">Accrual Policy:</label><select id="addAccrualsDropDown" name="addAccrualsDropDown"><option value="None">None</option><% for(Map<String,String> a : accrualsList){ String n=a.get("name"); if(!"None".equalsIgnoreCase(n)){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }} %></select></div></div><div class="form-row"><div class="form-item"><label for="addPermissionsDropDown">Permissions:</label><select id="addPermissionsDropDown" name="addPermissionsDropDown"><option value="User" selected>User</option><option value="Administrator">Administrator</option></select></div><div class="form-item"><label for="addWorkScheduleDropDown">Work Schedule:</label><select id="addWorkScheduleDropDown" name="addWorkScheduleDropDown"><option>Full Time</option><option>Part Time</option><option>Temporary</option><option>Seasonal</option><option>Contractor</option></select></div></div><div class="form-item"><label for="addAddress">Address:</label><input type="text" id="addAddress" name="addAddress" maxlength="255"></div><div class="form-row"><div class="form-item"><label for="addCity">City:</label><input type="text" id="addCity" name="addCity" maxlength="100"></div><div class="form-item"><label for="addState">State:</label><select id="addState" name="addState"><option value="">Select State</option><%@include file="/WEB-INF/includes/states_options.jspf" %></select></div><div class="form-item"><label for="addZip">Zip Code:</label><input type="text" id="addZip" name="addZip" pattern="\d{5}(?:-\d{4})?" title="e.g. 12345 or 12345-6789" maxlength="10"></div></div><div class="form-row"><div class="form-item"><label for="addPhone">Phone Number:</label><input type="tel" id="addPhone" name="addPhone" maxlength="20"></div><div class="form-item"><label for="addEmail">E-mail:<span class="required-asterisk">*</span></label><input type="email" id="addEmail" name="addEmail" required maxlength="100"></div></div><div class="form-row"><div class="form-item"><label for="addHireDate">Hire Date:<span class="required-asterisk">*</span></label><input type="date" id="addHireDate" name="addHireDate" value="<%=LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)%>" required></div><div class="form-item"><label for="addWageTypeDropDown">Wage Type:</label><select id="addWageTypeDropDown" name="addWageTypeDropDown"><option>Hourly</option><option>Salary</option></select></div></div><div class="form-item"><label for="addWage">Wage:<span class="required-asterisk">*</span></label><div class="input-with-symbol"><span>$</span><input type="number" id="addWage" name="addWage" step="0.01" min="0.01" required></div></div><div class="button-row"><button type="submit" class="glossy-button text-green">Submit</button><button type="button" id="cancelAddEmployee" class="cancel-btn glossy-button text-red">Cancel</button></div></form></div></div>
    <div id="editEmployeeModal" class="modal"><div class="modal-content"><span class="close" id="closeEditEmployeeModal">&times;</span><h2>Edit Employee</h2><form id="editEmployeeForm" action="<%=request.getContextPath()%>/AddEditAndDeleteEmployeesServlet" method="post"><input type="hidden" name="action" value="editEmployee"/><input type="hidden" id="hiddenEditEID" name="eid"/><div class="form-item"><label>Employee ID:</label><input type="text" id="editEIDDisplay" readonly disabled></div><div class="form-row"><div class="form-item"><label for="editFirstName">First Name:<span class="required-asterisk">*</span></label><input type="text" id="editFirstName" name="firstName" required></div><div class="form-item"><label for="editLastName">Last Name:<span class="required-asterisk">*</span></label><input type="text" id="editLastName" name="lastName" required></div></div><div class="form-row"><div class="form-item"><label for="editDepartmentsDropDown">Department:</label><select id="editDepartmentsDropDown" name="departmentsDropDown"><option value="None">None</option><% for(Map<String,String> d : departmentsList){ String n=d.get("name"); if(!"None".equalsIgnoreCase(n)){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }} %></select></div><div class="form-item"><label for="editSchedulesDropDown">Schedule:</label><select id="editSchedulesDropDown" name="schedulesDropDown"><option value="">-- Unassigned --</option><% for(Map<String,String> s : schedulesList){ String n=s.get("name"); %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% } %></select></div></div><div class="form-row"><div class="form-item"><label for="editSupervisor">Supervisor:</label><input type="text" id="editSupervisor" name="supervisor" maxlength="100"></div><div class="form-item"><label for="editAccrualsDropDown">Accrual Policy:</label><select id="editAccrualsDropDown" name="accrualsDropDown"><option value="None">None</option><% for(Map<String,String> a : accrualsList){ String n=a.get("name"); if(!"None".equalsIgnoreCase(n)){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }} %></select></div></div><div class="form-row"><div class="form-item"><label for="editPermissionsDropDown">Permissions:</label><select id="editPermissionsDropDown" name="permissionsDropDown"><option value="User">User</option><option value="Administrator">Administrator</option></select></div><div class="form-item"><label for="editWorkScheduleDropDown">Work Schedule:</label><select id="editWorkScheduleDropDown" name="workScheduleDropDown"><option>Full Time</option><option>Part Time</option><option>Temporary</option><option>Seasonal</option><option>Contractor</option></select></div></div><div class="form-item"><label for="editAddress">Address:</label><input type="text" id="editAddress" name="address" maxlength="255"></div><div class="form-row"><div class="form-item"><label for="editCity">City:</label><input type="text" id="editCity" name="city" maxlength="100"></div><div class="form-item"><label for="editState">State:</label><select id="editState" name="state"><option value="">Select State</option><%@include file="/WEB-INF/includes/states_options.jspf" %></select></div><div class="form-item"><label for="editZip">Zip Code:</label><input type="text" id="editZip" name="zip" pattern="\d{5}(?:-\d{4})?" title="e.g. 12345 or 12345-6789" maxlength="10"></div></div><div class="form-row"><div class="form-item"><label for="editPhone">Phone Number:</label><input type="tel" id="editPhone" name="phone" maxlength="20"></div><div class="form-item"><label for="editEmail">E-mail:<span class="required-asterisk">*</span></label><input type="email" id="editEmail" name="email" required></div></div><div class="form-row"><div class="form-item"><label for="editHireDate">Hire Date:<span class="required-asterisk">*</span></label><input type="date" id="editHireDate" name="hireDate" required></div><div class="form-item"><label for="editWageTypeDropDown">Wage Type:</label><select id="editWageTypeDropDown" name="wageTypeDropDown"><option>Hourly</option><option>Salary</option></select></div></div><div class="form-item"><label for="editWage">Wage:<span class="required-asterisk">*</span></label><div class="input-with-symbol"><span>$</span><input type="number" id="editWage" name="wage" step="0.01" min="0.01" required></div></div><div class="button-row"><button type="submit" class="glossy-button text-green">Update</button><button type="button" id="cancelEditEmployee" class="cancel-btn glossy-button text-red">Cancel</button></div></form></div></div>
    <div id="deactivateConfirmModal" class="modal"><div class="modal-content"><span class="close" id="closeDeactivateModal">&times;</span><h2>Confirm Deactivation</h2><p>Are you sure you want to deactivate <strong id="deactivateEmployeeName"></strong>?</p><div class="button-row"><button type="button" id="confirmDeactivateBtn" class="glossy-button text-red">Deactivate</button><button type="button" id="cancelDeactivateBtn" class="cancel-btn glossy-button text-grey">Cancel</button></div></div></div>
    <form id="deactivateEmployeeForm" action="<%=request.getContextPath()%>/AddEditAndDeleteEmployeesServlet" method="post" style="display:none;"><input type="hidden" name="action" value="deactivateEmployee"><input type="hidden" name="eid" id="deactivateEidInput"></form>
    
    <script type="text/javascript">
        window.inSetupWizardMode_Js = <%= inSetupWizardMode_JSP %>; 
        window.currentWizardStep_Js = "<%= currentWizardStep_JSP != null ? escapeForJavaScriptString(currentWizardStep_JSP) : "" %>";
        window.adminEidForWizardEdit_Js = "<%= adminEidForWizardEdit_JSP != null && !adminEidForWizardEdit_JSP.isEmpty() ? escapeForJavaScriptString(adminEidForWizardEdit_JSP) : "" %>";
        window.initialWizardModalShouldShow_AdminProfileIntro_Js = <%= initialWizardModalShouldShow_AdminProfileIntro_JSP %>;
        window.COMPANY_NAME_SIGNUP_JS = "<%= companyNameSignup_Employees != null ? escapeForJavaScriptString(companyNameSignup_Employees) : "Your Company" %>";
        window.employeeJustAddedName_Js = "<% String justAddedName = (String) currentSession.getAttribute("employeeJustAddedInWizardName"); if (justAddedName != null) { out.print(escapeForJavaScriptString(justAddedName)); currentSession.removeAttribute("employeeJustAddedInWizardName"); } %>";
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/employees.js?v=<%= System.currentTimeMillis() %>"></script> 
</body>
</html>