<%@page import="timeclock.employees.ShowEmployees"%>
<%@page import="timeclock.reports.ShowReports"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.sql.*"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.time.LocalDate"%>
<%@ page import="java.time.format.DateTimeFormatter"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspEmployeesPageLogger = Logger.getLogger("employees_jsp_wizard_v_complete_final");
    private String escapeJspHtml(String input) { if (input == null) return ""; return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private String escapeForJavaScriptString(String input) { if (input == null) return ""; return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t"); }
    private boolean isValidWizardStep(String step) { 
        if (step == null) return false; 
        switch(step) { 
            case "departments": 
            case "scheduling": 
            case "accruals": 
            case "employees_prompt": // Step name when coming from accruals/other pages to start employee setup
            case "editAdminProfile": 
            case "addMoreEmployees": 
            case "setupComplete": 
                return true; 
            default: return false;
        } 
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String loggedInAdminEid_Session = ""; 
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");

    boolean inSetupWizardMode_JSP = false; 
    String currentWizardStep_JSP = ""; 
    String adminEidForWizardEdit_JSP = ""; 
    boolean initialWizardModalShouldShow_AdminProfileIntro_JSP = false;
    boolean initialWizardModalShouldShow_PromptAddEmployees_JSP = false;
    boolean initialWizardModalShouldShow_SetupComplete_JSP = false;
    
    List<Map<String, String>> departmentsList = new ArrayList<>();
    List<Map<String, String>> schedulesList = new ArrayList<>();
    List<Map<String, String>> accrualsList = new ArrayList<>();
    
    String selectedEIDParam = request.getParameter("eid"); 
    int globalEidFromUrl = 0; 
    String requestAction = request.getParameter("action"); 
    String requestWizardFlag = request.getParameter("setup_wizard");
    String companyNameSignup_Employees = "Your Company";

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID"); 
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        
        Object eidSessionObj = currentSession.getAttribute("EID"); 
        if (eidSessionObj instanceof Integer) { loggedInAdminEid_Session = String.valueOf(eidSessionObj); }

        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_Employees = (String) companyNameObj;
        } else {
             // Fallback if not in session, could try to fetch from DB if really needed, but default is safer here.
             jspEmployeesPageLogger.warning("[employees.jsp] CompanyNameSignup not found in session, using default.");
        }


        if (pageLevelSuccess == null && currentSession.getAttribute("successMessage") != null) {
            pageLevelSuccess = (String) currentSession.getAttribute("successMessage");
            currentSession.removeAttribute("successMessage");
        }
        if (pageLevelError == null && currentSession.getAttribute("errorMessage") != null) {
            pageLevelError = (String) currentSession.getAttribute("errorMessage");
            currentSession.removeAttribute("errorMessage");
        }

        // Determine wizard mode and current step
        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true; // General flag
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String effectiveStepForPageLogic = sessionWizardStep; 

            jspEmployeesPageLogger.info("[employees.jsp] Wizard Active. Session Step: " + sessionWizardStep + 
                                 ", Request Action Param: " + requestAction + 
                                 ", Request Wizard Flag: " + requestWizardFlag);

            if ("true".equals(requestWizardFlag) && requestAction != null && !requestAction.isEmpty()) {
                if ("review_admin".equals(requestAction) || "edit_admin_profile".equals(requestAction)) {
                    effectiveStepForPageLogic = "editAdminProfile";
                } else if ("prompt_add_employees".equals(requestAction)) {
                    effectiveStepForPageLogic = "addMoreEmployees";
                } else if ("setup_complete".equals(requestAction)) {
                    effectiveStepForPageLogic = "setupComplete";
                } else {
                    jspEmployeesPageLogger.warning("[employees.jsp] Unrecognized wizard 'action' parameter: " + requestAction + ". Maintaining session step: " + sessionWizardStep);
                }
            } else if ("employees_prompt".equals(sessionWizardStep) || 
                       (currentSession != null && "accruals".equals(sessionWizardStep) && "true".equals(request.getParameter("setup_wizard")) )) {
                // Transitioning from Accruals (or a general prompt to go to employees)
                effectiveStepForPageLogic = "editAdminProfile"; // Always start employee setup with admin profile
                jspEmployeesPageLogger.info("[employees.jsp] Transitioned from previous wizard step to 'editAdminProfile'.");
            }

            if (!isValidWizardStep(effectiveStepForPageLogic)) {
                jspEmployeesPageLogger.warning("[employees.jsp] Effective step '" + effectiveStepForPageLogic + "' is invalid. Defaulting to admin profile edit.");
                effectiveStepForPageLogic = "editAdminProfile"; 
            }
            
            currentWizardStep_JSP = effectiveStepForPageLogic;
            if (inSetupWizardMode_JSP && currentSession != null && !currentWizardStep_JSP.equals(sessionWizardStep)) { 
                 currentSession.setAttribute("wizardStep", currentWizardStep_JSP);
                 jspEmployeesPageLogger.info("[employees.jsp] Synced session wizardStep to: " + currentWizardStep_JSP);
            }

            if ("editAdminProfile".equals(currentWizardStep_JSP)) {
                adminEidForWizardEdit_JSP = loggedInAdminEid_Session;
                if (adminEidForWizardEdit_JSP == null || adminEidForWizardEdit_JSP.isEmpty()) {
                    pageLevelError = (pageLevelError == null ? "" : pageLevelError + " ") + "Error: Administrator ID missing for profile setup.";
                    inSetupWizardMode_JSP = false; 
                } else if (currentSession != null && currentSession.getAttribute("admin_profile_intro_shown_employees_wizard") == null) {
                    initialWizardModalShouldShow_AdminProfileIntro_JSP = true;
                }
            } else if ("addMoreEmployees".equals(currentWizardStep_JSP)) {
                initialWizardModalShouldShow_PromptAddEmployees_JSP = true;
            } else if ("setupComplete".equals(currentWizardStep_JSP)) {
                initialWizardModalShouldShow_SetupComplete_JSP = true;
            }
             jspEmployeesPageLogger.info("[employees.jsp] Wizard Logic Final - Step: " + currentWizardStep_JSP + ", AdminEID: " + adminEidForWizardEdit_JSP + ", ShowIntro: " + initialWizardModalShouldShow_AdminProfileIntro_JSP + ", ShowPrompt: " + initialWizardModalShouldShow_PromptAddEmployees_JSP + ", ShowComplete: " + initialWizardModalShouldShow_SetupComplete_JSP);

        } else { 
            inSetupWizardMode_JSP = false; 
            if(currentSession != null) { 
                currentSession.removeAttribute("startSetupWizard"); 
                currentSession.removeAttribute("wizardStep"); 
                currentSession.removeAttribute("admin_profile_intro_shown_employees_wizard"); 
                currentSession.removeAttribute("employeeJustAddedInWizardName");
            }
            jspEmployeesPageLogger.info("[employees.jsp] Not in wizard mode ('startSetupWizard' session attribute is not true or session is null).");
        }
    } else { 
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session required. Please log in.", StandardCharsets.UTF_8.name())); 
        return; 
    }
    
    if (tenantId == null || tenantId <= 0) { 
        if (currentSession != null) currentSession.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired or invalid tenant. Please log in.", StandardCharsets.UTF_8.name())); 
        return; 
    }
    String userPermissions = currentSession.getAttribute("Permissions") != null ? (String) currentSession.getAttribute("Permissions") : "User";
     if (!"Administrator".equalsIgnoreCase(userPermissions)) {
        pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? "Access Denied." : pageLevelError + " Access Denied.";
    }

    if(pageLevelError == null) {
        try { 
            departmentsList = ShowReports.getDepartmentsForTenant(tenantId); 
            schedulesList = ShowReports.getSchedulesForTenant(tenantId); 
            accrualsList = ShowReports.getAccrualPoliciesForTenant(tenantId); 
        } catch (Exception e) { 
            pageLevelError = (pageLevelError == null ? "" : pageLevelError + " ") + "Error loading page dropdown data: " + e.getMessage(); 
            jspEmployeesPageLogger.log(Level.SEVERE, "Error loading dropdown data for employees.jsp, T:" + tenantId, e);
        }
    }

    if (selectedEIDParam != null && !selectedEIDParam.trim().isEmpty()) { 
        try { 
            globalEidFromUrl = Integer.parseInt(selectedEIDParam.trim()); 
            if (globalEidFromUrl <= 0) globalEidFromUrl = 0;
        } catch (NumberFormatException nfe) { 
            globalEidFromUrl = 0; 
            pageLevelError = (pageLevelError == null ? "" : pageLevelError + " ") + "Invalid Employee ID in URL parameter.";
        } 
    }
    
    String employeeRowsHtml = ""; 
    if (pageLevelError == null) { 
        try { 
            employeeRowsHtml = ShowEmployees.showEmployees(tenantId); 
        } catch (Exception e) { 
            pageLevelError = (pageLevelError == null ? "" : pageLevelError + " ") + "Could not load employee list: " + e.getMessage(); 
            jspEmployeesPageLogger.log(Level.SEVERE, "Error calling ShowEmployees.showEmployees, T:" + tenantId, e);
        }
    }
    if (pageLevelError != null && (employeeRowsHtml == null || employeeRowsHtml.trim().isEmpty())) { 
        employeeRowsHtml = "<tr><td colspan='12' class='report-error-row'>" + escapeJspHtml(pageLevelError) + "</td></tr>"; 
    } else if ((employeeRowsHtml == null || employeeRowsHtml.trim().isEmpty()) && pageLevelError == null) {
        employeeRowsHtml = "<tr><td colspan='12' class='report-message-row'>No active employees found. Add an employee to get started.</td></tr>";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Employee Management<% if (inSetupWizardMode_JSP && !"setupComplete".equals(currentWizardStep_JSP)) { %> - Company Setup<% } %></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/employees.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style> 
        #wizardNavigationControls_Employees { margin-top: 20px; padding: 15px; background-color: #e7f3fe; border: 1px solid #b3d7f9; border-radius: 5px; text-align: center; display: none; }
        #wizardNavigationControls_Employees p { margin-top: 0; margin-bottom: 10px; font-size: 0.95em; }
        #wizardNavigationControls_Employees button { margin: 5px; }
        table#employeesTable th { white-space: nowrap !important; }
        .newly-added-highlight td {
            background-color: #fff3cd !important; 
            transition: background-color 1.5s ease-out;
        }
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; }
        .modal.modal-visible { display: flex !important; }
        .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 650px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left; }
        .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee; text-align:center;}
        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; }
        .button-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
        .page-message { padding: 10px 15px; margin: 0 auto 20px auto; border-radius: 4px; text-align: center; max-width: calc(100% - 30px); font-size: 0.9em; }
        .success-message { background-color: #d4edda; color: #155724; border:1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border:1px solid #f5c6cb; }
        .info-message { background-color: #d1ecf1; color: #0c5460; border-color: #bee5eb; }
        .required-asterisk { color: #dc3545; margin-left: 2px; font-weight: bold; }
        .wizard-modal .modal-content { text-align: center;}
        .wizard-modal .modal-content p { margin-bottom: 15px; line-height: 1.6; font-size: 1.05em;}
        .wizard-modal .button-row { justify-content: space-around !important; margin-top: 25px;}
    </style>
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <div class="parent-container reports-container">
        <h1>Employee Management<% 
            if (inSetupWizardMode_JSP && !"setupComplete".equals(currentWizardStep_JSP)) { 
                out.print(" <span style='font-size:0.8em; color:#555;'>(Setup)</span>"); 
            } 
        %></h1>
        
        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message" id="pageNotification_Success_Emp"><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && (employeeRowsHtml == null || !employeeRowsHtml.contains("report-error-row"))) { 
        %><div class="page-message error-message" id="pageNotification_Error_Emp"><%= escapeJspHtml(pageLevelError) %></div><% 
        } %>
        
        <%-- Wizard Modals --%>
        <% if (inSetupWizardMode_JSP) { %>
            <div id="wizardAdminProfileIntroModal" class="modal" style="z-index: 10003;"> <div class="modal-content" style="max-width: 550px;"> <span class="close" id="closeWizardAdminProfileIntroModal">&times;</span> <h2>Company Setup: Administrator Profile</h2> <div style="padding: 15px 25px; text-align: left; line-height: 1.6; font-size: 0.95em;"> <p>Welcome, Administrator! The next step is to review and complete your employee profile for <strong><%= escapeJspHtml(companyNameSignup_Employees) %></strong>.</p> <p>We've pre-filled some information from your company signup. Please verify all details, especially selecting your <strong>Department</strong>, <strong>Schedule</strong>, <strong>Accrual Policy</strong>, and confirming your <strong>Hire Date</strong> and <strong>Wage</strong>.</p> <p>Click "OK, Let's Review!" to open your profile for editing.</p> </div> <div class="button-row" style="justify-content: center; padding: 15px 25px;"> <button type="button" id="okAdminProfileIntroButton" class="glossy-button text-blue" style="min-width:150px;"> <i class="fas fa-check-circle"></i> OK, Let's Review! </button> </div> </div> </div>
            
            <div id="wizardPromptAddEmployeesModal" class="modal" style="z-index: 10003;"> <div class="modal-content" style="max-width: 550px;"> <span class="close" id="closeWizardPromptAddEmployeesModal">&times;</span> <h2>Company Setup: Add Employees</h2> <div id="wizardPromptAddEmployeesMessage" style="padding: 15px 25px; text-align: left; line-height: 1.6; font-size: 0.95em;"> {/* JS populates this */} </div> <div class="button-row" style="justify-content: space-between; padding: 15px 25px;"> <button type="button" id="wizardTriggerAddEmployeeModalButton" class="glossy-button text-green" style="flex-grow:1;"> <i class="fas fa-user-plus"></i> Add Another Employee </button> <button type="button" id="wizardDoneAddingEmployeesButton" class="glossy-button text-orange" style="flex-grow:1;"> <i class="fas fa-flag-checkered"></i> Finish Setup </button> </div> </div> </div>
            
            <div id="wizardSetupCompleteModal" class="modal" style="z-index: 10003;"> <div class="modal-content" style="max-width: 500px;"> <span class="close" id="closeWizardSetupCompleteModalXBtn">&times;</span> <h2>Setup Complete!</h2> <div style="padding: 20px 25px; text-align: center; line-height: 1.6; font-size: 1em;"> <p><i class="fas fa-check-circle fa-3x" style="color: #28a745; margin-bottom:15px;"></i></p> <p>Congratulations, <strong><%= escapeJspHtml(companyNameSignup_Employees) %></strong> is all set up!</p> <p>You can now manage employees, run reports, and use all features of YourTimeClock.</p> </div> <div class="button-row" style="justify-content: space-around; padding: 15px 25px;"> <button type="button" id="wizardHelpButton" class="glossy-button text-blue" style="flex-basis: 48%;"> <i class="fas fa-question-circle"></i> View Help </button> <button type="button" id="wizardOkFinalButton" class="glossy-button text-green" style="flex-basis: 48%;"> <i class="fas fa-thumbs-up"></i> Go to Employees Page </button> </div> </div> </div>
            
            <div id="wizardNavigationControls_Employees" style="display:none;"> 
                <% if ("editAdminProfile".equals(currentWizardStep_JSP)) { %> 
                    <p>You are currently editing your Administrator profile. Please review all fields and save. When finished:</p> 
                    <button type="button" id="persistentProceedAfterAdminEditButton" class="glossy-button text-green"> Save & Continue Setup <i class="fas fa-angle-double-right"></i> </button> 
                <% } else if ("addMoreEmployees".equals(currentWizardStep_JSP)) { %> 
                    <p>You are in the 'Add Employees' step. Use "Add Employee" above to add more team members. When you're finished for now:</p> 
                    <button type="button" id="persistentDoneAddingEmployeesButton" class="glossy-button text-orange"> Finish Setup <i class="fas fa-flag-checkered"></i> </button> 
                <% } %> 
            </div>
        <% } %>
        
        <div id="button-container" class="main-action-buttons"> <button type="button" id="addEmployeeButton" class="glossy-button text-green"><i class="fas fa-user-plus"></i> Add Employee</button> <button type="button" id="editEmployeeButton" class="glossy-button text-orange" disabled><i class="fas fa-user-edit"></i> Edit Employee</button> <button type="button" id="deactivateEmployeeButton" class="glossy-button text-red" disabled><i class="fas fa-user-slash"></i> Deactivate Employee</button> </div>
        
        <div class="table-container report-table-container employee-table-container"> 
            <table id="employeesTable" class="report-table employee-table"> 
                <thead> <tr> <th data-sort-type="number">Emp ID</th> <th data-sort-type="string">First Name</th> <th data-sort-type="string">Last Name</th> <th data-sort-type="string">Department</th> <th data-sort-type="string">Schedule</th> <th data-sort-type="string">Supervisor</th> <th data-sort-type="string">Permissions</th> <th data-sort-type="string">E-mail</th> <th data-sort-type="date">Hire Date</th> <th data-sort-type="string">Work Sched</th> <th data-sort-type="string">Wage Type</th> <th data-sort-type="currency">Wage</th> </tr></thead> 
                <tbody><%= employeeRowsHtml %></tbody> 
            </table> 
        </div>
        
        <div id="employeeDetailsSection" style="display: none;"> 
            <div class="details-grid"> 
                <div class="detail-group"> <h3>Personal Information</h3> <p><label>Employee ID:</label> <span id="detailEID">--</span></p> <p><label>First Name:</label> <span id="detailFirstName">--</span></p> <p><label>Last Name:</label> <span id="detailLastName">--</span></p> <p><label>Address:</label> <span id="detailAddress">--</span></p> <p><label>City:</label> <span id="detailCity">--</span></p> <p><label>State:</label> <span id="detailState">--</span></p> <p><label>Zip:</label> <span id="detailZip">--</span></p> <p><label>Phone:</label> <span id="detailPhone">--</span></p> <p><label>E-mail:</label> <span id="detailEmail">--</span></p> </div> 
                <div class="detail-group"> <h3>Company Information</h3> <p><label>Department:</label> <span id="detailDept">--</span></p> <p><label>Schedule:</label> <span id="detailSchedule">--</span></p> <p><label>Supervisor:</label> <span id="detailSupervisor">--</span></p> <p><label>Permissions:</label> <span id="detailPermissions">--</span></p> <p><label>Hire Date:</label> <span id="detailHireDate">--</span></p> <p><label>Work Schedule:</label> <span id="detailWorkSched">--</span></p> <p><label>Wage Type:</label> <span id="detailWageType">--</span></p> <p><label>Wage:</label> <span id="detailWage">--</span></p> </div> 
                <div class="detail-group" id="accrualInfoGroup"> <h3>Accrual Information</h3> <p><label>Accrual Policy:</label> <span id="detailAccrualPolicy">--</span></p> <p><label>Vacation Hours:</label> <span id="detailVacHours">--</span></p> <p><label>Sick Hours:</label> <span id="detailSickHours">--</span></p> <p><label>Personal Hours:</label> <span id="detailPersHours">--</span></p> <br><form action="<%=request.getContextPath()%>/EmployeeInfoServlet" method="get" id="resetPasswordForm" style="display: inline-block; margin-top:15px;"> <input type="hidden" name="action" value="resetPassword"> <input type="hidden" name="eid" id="resetFormEid" value=""> <button type="submit" id="btnResetPassword" class="glossy-button text-orange" disabled> <i class="fas fa-key"></i> Reset PIN </button> </form> </div> 
            </div> 
        </div>
    </div>

    <%-- Standard Add/Edit Employee Modals --%>
    <div id="addEmployeeModal" class="modal"> <div class="modal-content"> <span class="close" id="closeAddEmployeeModal">&times;</span><h2>Add Employee</h2> <form id="addEmployeeForm" action="<%=request.getContextPath()%>/AddEditAndDeleteEmployeesServlet" method="post"> <input type="hidden" name="action" value="addEmployee"/> <div class="form-item"><label for="addEIDDisplay">Employee ID:</label><input type="text" id="addEIDDisplay" name="addEID_display" placeholder="System Assigned" readonly disabled></div> <div class="form-row"> <div class="form-item"><label for="addFirstName">First Name:<span class="required-asterisk">*</span></label><input type="text" id="addFirstName" name="addFirstName" required autofocus autocomplete="off" maxlength="50"></div> <div class="form-item"><label for="addLastName">Last Name:<span class="required-asterisk">*</span></label><input type="text" id="addLastName" name="addLastName" required autocomplete="off" maxlength="50"></div> </div> <div class="form-row"> <div class="form-item"><label for="addDepartmentsDropDown">Department:</label><select id="addDepartmentsDropDown" name="addDepartmentsDropDown"><option value="None">None</option><% if(departmentsList != null){ for(Map<String,String> d : departmentsList){ String n = d.get("name"); if(!"None".equalsIgnoreCase(n)){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }}} %></select></div> <div class="form-item"><label for="addSchedulesDropDown">Schedule:</label><select id="addSchedulesDropDown" name="addSchedulesDropDown"><option value="">-- Unassigned --</option><% if(schedulesList != null){ for(Map<String,String> s : schedulesList){ String n = s.get("name"); %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }} %></select></div> </div> <div class="form-row"> <div class="form-item"><label for="addSupervisor">Supervisor:</label><input type="text" id="addSupervisor" name="addSupervisor" value="None" maxlength="100"></div> <div class="form-item"><label for="addAccrualsDropDown">Accrual Policy:</label><select id="addAccrualsDropDown" name="addAccrualsDropDown"><option value="None">None</option><option value="Standard">Standard</option><% if(accrualsList != null){ for(Map<String,String> a : accrualsList){ String n = a.get("name"); if(!("None".equalsIgnoreCase(n)||"Standard".equalsIgnoreCase(n))){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }}} %></select></div> </div> <div class="form-row"> <div class="form-item"><label for="addPermissionsDropDown">Permissions:</label><select id="addPermissionsDropDown" name="addPermissionsDropDown"><option value="User" selected>User</option><option value="Administrator">Administrator</option></select></div> <div class="form-item"><label for="addWorkScheduleDropDown">Work Schedule:</label><select id="addWorkScheduleDropDown" name="addWorkScheduleDropDown"><option value="Full Time" selected>Full Time</option><option value="Part Time">Part Time</option><option value="Temporary">Temporary</option><option value="Seasonal">Seasonal</option><option value="Contractor">Contractor</option></select></div> </div> <div class="form-item"><label for="addAddress">Address:</label><input type="text" id="addAddress" name="addAddress" autocomplete="off" maxlength="255"></div> <div class="form-row"> <div class="form-item"><label for="addCity">City:</label><input type="text" id="addCity" name="addCity" autocomplete="off" maxlength="100"></div> <div class="form-item"><label for="addState">State:</label><select id="addState" name="addState"><option value="">Select State</option><%@include file="/WEB-INF/includes/states_options.jspf" %></select></div> <div class="form-item"><label for="addZip">Zip Code:</label><input type="text" id="addZip" name="addZip" pattern="^\d{5}(-\d{4})?$" title="5 or 9 digits (ZIP+4)" maxlength="10" autocomplete="off"></div> </div> <div class="form-row"> <div class="form-item"><label for="addPhone">Phone Number:</label><input type="tel" id="addPhone" name="addPhone" autocomplete="off" pattern="^[0-9\s()+\-]*$" title="Digits and ()-+" maxlength="20"></div> <div class="form-item"><label for="addEmail">E-mail:<span class="required-asterisk">*</span></label><input type="email" id="addEmail" name="addEmail" autocomplete="off" required maxlength="100"><small>Mandatory for login.</small></div> </div> <div class="form-row"> <div class="form-item"><label for="addHireDate">Hire Date:<span class="required-asterisk">*</span></label><input type="date" id="addHireDate" name="addHireDate" value="<%= LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE) %>" required></div> <div class="form-item"><label for="addWageTypeDropDown">Wage Type:</label><select id="addWageTypeDropDown" name="addWageTypeDropDown"><option value="Hourly" selected>Hourly</option><option value="Salary">Salary</option></select></div> </div> <div class="form-item"><label for="addWage">Wage:<span class="required-asterisk">*</span></label><div class="input-with-symbol"><span class="currency-symbol">$</span><input type="number" id="addWage" name="addWage" step="0.01" min="0.01" placeholder="Annual or Hourly" required></div></div> <div class="button-row"><button type="submit" class="glossy-button text-green"><i class="fas fa-check"></i> Submit</button><button type="button" id="cancelAddEmployee" class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button></div></form> </div> </div>
    
    <div id="editEmployeeModal" class="modal"> <div class="modal-content"> <span class="close" id="closeEditEmployeeModal">&times;</span><h2>Edit Employee</h2> <form id="editEmployeeForm" action="<%=request.getContextPath()%>/AddEditAndDeleteEmployeesServlet" method="post"> <input type="hidden" name="action" value="editEmployee"/><input type="hidden" id="hiddenEditEID" name="eid"/> <div class="form-item"><label for="editEIDDisplay">Employee ID:</label><input type="text" id="editEIDDisplay" name="editEID_display" readonly disabled></div> <div class="form-row"> <div class="form-item"><label for="editFirstName">First Name:<span class="required-asterisk">*</span></label><input type="text" id="editFirstName" name="firstName" required autocomplete="off" maxlength="50"></div> <div class="form-item"><label for="editLastName">Last Name:<span class="required-asterisk">*</span></label><input type="text" id="editLastName" name="lastName" autocomplete="off" required maxlength="50"></div> </div> <div class="form-row"> <div class="form-item"><label for="editDepartmentsDropDown">Department:</label><select id="editDepartmentsDropDown" name="departmentsDropDown"><option value="None">None</option><% if(departmentsList != null){ for(Map<String,String> d : departmentsList){ String n = d.get("name"); if(!"None".equalsIgnoreCase(n)){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }}} %></select></div> <div class="form-item"><label for="editSchedulesDropDown">Schedule:</label><select id="editSchedulesDropDown" name="schedulesDropDown"><option value="">-- Unassigned --</option><% if(schedulesList != null){ for(Map<String,String> s : schedulesList){ String n = s.get("name"); %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }} %></select></div> </div> <div class="form-row"> <div class="form-item"><label for="editSupervisor">Supervisor:</label><input type="text" id="editSupervisor" name="supervisor" maxlength="100"></div> <div class="form-item"><label for="editAccrualsDropDown">Accrual Policy:</label><select id="editAccrualsDropDown" name="accrualsDropDown"><option value="None">None</option><option value="Standard">Standard</option><% if(accrualsList != null){ for(Map<String,String> a : accrualsList){ String n = a.get("name"); if(!("None".equalsIgnoreCase(n)||"Standard".equalsIgnoreCase(n))){ %><option value="<%=escapeJspHtml(n)%>"><%=escapeJspHtml(n)%></option><% }}} %></select></div> </div> <div class="form-row"> <div class="form-item"><label for="editPermissionsDropDown">Permissions:</label><select id="editPermissionsDropDown" name="permissionsDropDown"><option value="User">User</option><option value="Administrator">Administrator</option></select></div> <div class="form-item"><label for="editWorkScheduleDropDown">Work Schedule:</label><select id="editWorkScheduleDropDown" name="workScheduleDropDown"><option>Full Time</option><option>Part Time</option><option>Temporary</option><option>Seasonal</option><option>Contractor</option></select></div> </div> <div class="form-item"><label for="editAddress">Address:</label><input type="text" id="editAddress" name="address" autocomplete="off" maxlength="255"></div> <div class="form-row"> <div class="form-item"><label for="editCity">City:</label><input type="text" id="editCity" name="city" autocomplete="off" maxlength="100"></div> <div class="form-item"><label for="editState">State:</label><select id="editState" name="state"><option value="">Select State</option><%@include file="/WEB-INF/includes/states_options.jspf" %></select></div> <div class="form-item"><label for="editZip">Zip Code:</label><input type="text" id="editZip" name="zip" pattern="^\d{5}(-\d{4})?$" title="5 or 9 digits (ZIP+4)" maxlength="10" autocomplete="off"></div> </div> <div class="form-row"> <div class="form-item"><label for="editPhone">Phone Number:</label><input type="tel" id="editPhone" name="phone" autocomplete="off" pattern="^[0-9\s()+\-]*$" title="Digits and ()-+" maxlength="20"></div> <div class="form-item"><label for="editEmail">E-mail:<span class="required-asterisk">*</span></label><input type="email" id="editEmail" name="email" autocomplete="off" required maxlength="100"><small>Mandatory for login.</small></div> </div> <div class="form-row"> <div class="form-item"><label for="editHireDate">Hire Date:<span class="required-asterisk">*</span></label><input type="date" id="editHireDate" name="hireDate" required></div> <div class="form-item"><label for="editWageTypeDropDown">Wage Type:</label><select id="editWageTypeDropDown" name="wageTypeDropDown"><option>Hourly</option><option>Salary</option></select></div> </div> <div class="form-item"><label for="editWage">Wage:<span class="required-asterisk">*</span></label><div class="input-with-symbol"><span class="currency-symbol">$</span><input type="number" id="editWage" name="wage" step="0.01" min="0.01" placeholder="Annual or Hourly" required></div></div> <div class="button-row"><button type="submit" class="glossy-button text-green"><i class="fas fa-save"></i> Update</button><button type="button" id="cancelEditEmployee" class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button></div></form> </div> </div>
    
    <div id="notificationModalGeneral" class="modal"> <div class="modal-content"> <span class="close" id="closeNotificationModalGeneral_Emp" data-close-modal-id="notificationModalGeneral">&times;</span> <h2 id="notificationModalGeneralTitle">Notification</h2> <p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center;"></p> <div class="button-row" style="justify-content: center;"> <button type="button" id="okButtonNotificationModalGeneral" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue"><i class="fas fa-thumbs-up"></i> OK</button> </div> </div> </div>

    <script type="text/javascript">
        const appRootPath = "<%= request.getContextPath() %>"; 
        
        window.globalEidToSelectOnLoad_Js = <%= globalEidFromUrl %>;
        window.loggedInAdminEid_Js = "<%= loggedInAdminEid_Session != null ? escapeForJavaScriptString(loggedInAdminEid_Session) : "" %>";
        window.initialWizardAction_Js = "<%= requestAction != null ? escapeForJavaScriptString(requestAction) : "" %>"; 
        window.inSetupWizardMode_Js = <%= inSetupWizardMode_JSP %>; 
        window.currentWizardStep_Js = "<%= currentWizardStep_JSP != null ? escapeForJavaScriptString(currentWizardStep_JSP) : "" %>";
        window.adminEidForWizardEdit_Js = "<%= adminEidForWizardEdit_JSP != null && !adminEidForWizardEdit_JSP.isEmpty() ? escapeForJavaScriptString(adminEidForWizardEdit_JSP) : "" %>";
        window.initialWizardModalShouldShow_AdminProfileIntro_Js = <%= initialWizardModalShouldShow_AdminProfileIntro_JSP %>;
        window.initialWizardModalShouldShow_PromptAddEmployees_Js = <%= initialWizardModalShouldShow_PromptAddEmployees_JSP %>;
        window.initialWizardModalShouldShow_SetupComplete_Js = <%= initialWizardModalShouldShow_SetupComplete_JSP %>;
        window.employeeJustAddedName_Js = "<% 
            String justAddedName = null;
            if (currentSession != null) {
                justAddedName = (String) currentSession.getAttribute("employeeJustAddedInWizardName");
                if (justAddedName != null && !justAddedName.isEmpty()) { 
                    out.print(escapeForJavaScriptString(justAddedName)); 
                    currentSession.removeAttribute("employeeJustAddedInWizardName"); 
                    // Client-side console log will show this value from JS variable
                } else { 
                    out.print(""); 
                }
            } else {
                out.print("");
            }
        %>";
        window.COMPANY_NAME_SIGNUP_JS = "<%= companyNameSignup_Employees != null ? escapeForJavaScriptString(companyNameSignup_Employees) : "Your Company" %>";
        
        // This console log replaces the jspEmployeesLogger call that was here previously.
        console.log("[employees.jsp JS Globals] InWizardMode: " + window.inSetupWizardMode_Js + 
                               ", CurrentStep: " + window.currentWizardStep_Js +
                               ", InitialAction: " + window.initialWizardAction_Js +
                               ", AdminEIDForEdit: " + window.adminEidForWizardEdit_Js +
                               ", ShowAdminIntro: " + window.initialWizardModalShouldShow_AdminProfileIntro_Js +
                               ", ShowPromptAdd: " + window.initialWizardModalShouldShow_PromptAddEmployees_Js +
                               ", ShowSetupComplete: " + window.initialWizardModalShouldShow_SetupComplete_Js +
                               ", EmployeeJustAdded: " + window.employeeJustAddedName_Js +
                               ", GlobalEIDFromUrl: " + window.globalEidToSelectOnLoad_Js +
                               ", CompanyName: " + window.COMPANY_NAME_SIGNUP_JS
                               );
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/employees.js?v=<%= System.currentTimeMillis() %>"></script> 
</body>
</html>