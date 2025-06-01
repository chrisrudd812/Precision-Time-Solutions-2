<%@page import="timeclock.reports.ShowReports"%>
<%@page import="timeclock.accruals.ShowAccruals"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>
<%@ page import="timeclock.punches.ShowPunches" %>

<%!
    private static final Logger jspAccrualsPageLogger = Logger.getLogger("accruals_jsp_wizard_v4_complete"); 

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
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
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
    String companyNameSignup_Accruals = "Your Company";
    boolean accrualPolicyJustAddedInWizard_JSP = "true".equalsIgnoreCase(request.getParameter("accrualAdded"));
    boolean showSpecificIntroModal_Accruals_JSP = false;
    String loggedInAdminEid_JSP = ""; // Added for completeness if needed by JS common patterns, not directly by accruals v18 wizard

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_Accruals = (String) companyNameObj;
        }
        Object eidSessionObj = currentSession.getAttribute("wizardAdminEid"); 
        if (eidSessionObj instanceof Integer) { loggedInAdminEid_JSP = String.valueOf(eidSessionObj); }
        else { Object sessionEid = currentSession.getAttribute("EID"); if (sessionEid instanceof Integer) loggedInAdminEid_JSP = String.valueOf(sessionEid); }


        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("wizardStep");

            jspAccrualsPageLogger.info("[accruals.jsp] Wizard Mode Active. Session step: " + sessionWizardStep + ", URL wizardStepParam: " + wizardStepFromParam);

            if (wizardStepFromParam != null && !wizardStepFromParam.trim().isEmpty()) {
                currentWizardStepForPage_JSP = wizardStepFromParam.trim();
                if (!currentWizardStepForPage_JSP.equals(sessionWizardStep)) {
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                }
            } else {
                currentWizardStepForPage_JSP = sessionWizardStep;
            }
            
            // Logic for accruals page initial wizard state
            // This page is reached when wizardStep is "accruals_prompt" (set by previous page's "Next" action)
            if ("accruals_prompt".equals(currentWizardStepForPage_JSP) || 
                ("scheduling".equals(sessionWizardStep) && "true".equals(request.getParameter("setup_wizard")))) { // Coming from scheduling
                 
                currentWizardStepForPage_JSP = "accruals_prompt"; // Canonical step for generic prompt
                 if (!currentWizardStepForPage_JSP.equals(sessionWizardStep)){ 
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                 }
                 // Determine if the specific intro modal should be shown
                 if (currentSession.getAttribute("accruals_intro_shown_wizard") == null && !accrualPolicyJustAddedInWizard_JSP) {
                    showSpecificIntroModal_Accruals_JSP = true; 
                 }
            } else if (currentWizardStepForPage_JSP == null || !currentWizardStepForPage_JSP.startsWith("accruals")) {
                 if (request.getParameter("setup_wizard") != null){ 
                    currentWizardStepForPage_JSP = "accruals_prompt";
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                     if (currentSession.getAttribute("accruals_intro_shown_wizard") == null) {
                         showSpecificIntroModal_Accruals_JSP = true;
                     }
                 } else {
                    inSetupWizardMode_JSP = false; 
                 }
            }
            jspAccrualsPageLogger.info("[accruals.jsp] Final currentWizardStepForPage_JSP: " + currentWizardStepForPage_JSP + 
                                 ", accrualPolicyJustAdded: " + accrualPolicyJustAddedInWizard_JSP + 
                                 ", showSpecificIntro: " + showSpecificIntroModal_Accruals_JSP);
        }  else {
             jspAccrualsPageLogger.info("[accruals.jsp] Not in wizard mode ('startSetupWizard' session attribute not true).");
             inSetupWizardMode_JSP = false;
             if(currentSession != null) { 
                currentSession.removeAttribute("startSetupWizard"); 
                currentSession.removeAttribute("wizardStep"); 
                currentSession.removeAttribute("accruals_intro_shown_wizard");
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
            pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? "Access Denied. Administrator privileges required." : pageLevelError + " Access Denied.";
        }
    } else {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
        return; 
    }
   
    if (tenantId == null || tenantId <= 0) {
        pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? "Invalid session or tenant context." : pageLevelError + " Invalid session or tenant context.";
    }
    
    List<Map<String, String>> allAccrualPoliciesForDropdown = new ArrayList<>();
    List<Map<String, Object>> activeEmployeeList_accruals = new ArrayList<>();
    String employeeListError = null; 

    if (pageLevelError == null && tenantId != null && tenantId > 0) {
        try {
            allAccrualPoliciesForDropdown = ShowReports.getAccrualPoliciesForTenant(tenantId);
            activeEmployeeList_accruals = ShowPunches.getActiveEmployeesForDropdown(tenantId);
        } catch (Exception e) {
            jspAccrualsPageLogger.log(Level.SEVERE, "Error fetching dropdown data for T:" + tenantId, e);
            pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? "Could not load page data." : pageLevelError + " Could not load page data.";
            employeeListError = "Error loading employee list.";
        }
    }
    
    String accrualRowsHtml = "";
    if (pageLevelError == null && tenantId != null && tenantId > 0) {
        try {
            accrualRowsHtml = ShowAccruals.showAccruals(tenantId);
        } catch (Exception e) {
            jspAccrualsPageLogger.log(Level.SEVERE, "Error calling ShowAccruals.showAccruals for T:" + tenantId, e);
            pageLevelError = (pageLevelError == null || pageLevelError.isEmpty()) ? ("Could not load accrual policies: " + e.getMessage()) : pageLevelError + (" Could not load accrual policies: " + e.getMessage());
        }
    }
    
    if (pageLevelError != null && (accrualRowsHtml == null || accrualRowsHtml.isEmpty() || !accrualRowsHtml.contains("report-error-row"))) {
        accrualRowsHtml = "<tr><td colspan='4' class='report-error-row'>" + escapeJspHtml(pageLevelError) + "</td></tr>";
    } else if ((accrualRowsHtml == null || accrualRowsHtml.isEmpty()) && pageLevelError == null) {
        accrualRowsHtml = "<tr><td colspan='4' class='report-message-row'>No accrual policies defined yet. Add one to get started.</td></tr>";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Manage Accrual Policies<% if(inSetupWizardMode_JSP && !"setupComplete".equals(currentWizardStepForPage_JSP)) { %> - Company Setup<% } %></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%= System.currentTimeMillis() %>"> 
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/accruals.css?v=<%= System.currentTimeMillis() %>"> 
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; }
        .modal.modal-visible { display: flex !important; }
        .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 600px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left; }
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
        <h1>Manage Accrual Policies <% if(inSetupWizardMode_JSP && !"setupComplete".equals(currentWizardStepForPage_JSP)) { %> <span style="font-size: 0.8em; color: #555;">(Setup)</span> <% } %></h1>

        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message" id="pageNotificationDiv_Success_Acc"><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && (accrualRowsHtml == null || !accrualRowsHtml.contains("report-error-row"))) { 
        %><div class="page-message error-message" id="pageNotificationDiv_Error_Acc"><%= escapeJspHtml(pageLevelError) %></div><% 
        } %>
        
        <%-- Specific Wizard Intro Modal (controlled by showSpecificIntroModal_Accruals_JSP flag from scriptlet) --%>
        <div id="setupWizardModal_Accruals" class="modal" style="z-index: 10001; <%= !showSpecificIntroModal_Accruals_JSP ? "display: none;" : "" %>"> 
            <div class="modal-content" style="max-width: 600px;"> 
                <span class="close" id="closeSetupWizardAccrualsModal_X">&times;</span> 
                <h2>Company Setup: Accrual Policies</h2> 
                <div style="padding: 15px 25px; text-align: left; line-height: 1.6; font-size: 0.95em;"> 
                    <p>Next, let's configure Accrual Policies for time off (e.g., Vacation, Sick days) for <strong><%= escapeJspHtml(companyNameSignup_Accruals) %></strong>. Default "None" and "Standard" policies already exist.</p> 
                    <p>You can define specific policies now using "Add Accrual Policy" on this page, or skip this step for now. Manage policies later via Navbar > Accruals.</p> 
                </div> 
                <%-- BUTTON ORDER FIXED --%>
                <div class="button-row" style="justify-content: space-between; padding: 15px 25px;"> 
                    <button type="button" id="manageAccrualsButtonWizard" class="glossy-button text-blue" style="flex-grow: 1; margin-right: 5px;"> <i class="fas fa-cogs"></i> Add/Manage Policies </button> 
                    <button type="button" id="skipAccrualsButtonWizard" class="glossy-button text-orange" style="flex-grow: 1; margin-left: 5px;"> <i class="fas fa-forward"></i> Skip to Next Step </button> 
                </div> 
            </div> 
        </div>
        
        <div id="button-container" class="main-action-buttons">
            <button type="button" id="btnAddPolicy" class="glossy-button text-green"><i class="fas fa-plus"></i> Add Accrual Policy</button>
            <button type="button" id="btnEditPolicy" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Accrual Policy</button>
            <button type="button" id="btnDeletePolicy" class="glossy-button text-red" disabled><i class="fas fa-trash-alt"></i> Delete Accrual Policy</button>
        </div>
        <h4 style="text-align: left; color: #6c757d; margin-bottom: 10px; font-size: 0.9em;" id="instructionTextH4_Accruals">To Edit or Delete Policies: First select a row from the table below.</h4>
        
        <div class="report-display-area" style="padding-top: 10px;">
            <div id="reportOutput_accruals" class="report-output">
                <div class="table-container report-table-container">
                    <table class="report-table" id="accrualsTable">
                        <thead><tr><th data-sort-type="string">Policy Name</th><th data-sort-type="number">Annual Vacation Days</th><th data-sort-type="number">Annual Sick Days</th><th data-sort-type="number">Annual Personal Days</th></tr></thead>
                        <tbody><%= accrualRowsHtml %></tbody>
                    </table>
                </div>
            </div>
        </div>
        
        <div id="adjustAccrualBalanceSection" style="margin-top: 25px;">
            <div class="manage-accrual-form-outer-box"> 
                <h2>Adjust Employee Accrual Balance(s)</h2>
                <p class="page-description">
                    This administrative action directly modifies an employee's (or all active employees') current accrued hours balance.
                    It does not interact with accrual policies or create timesheet entries.
                </p>
                <form id="adjustAccrualBalanceForm" action="<%= request.getContextPath() %>/AccrualServlet" method="post">
                    <div class="form-elements-gradient-wrapper"> 
                        <fieldset id="adjustmentFormFieldset">
                            <input type="hidden" name="action" value="adjustAccruedBalanceAction"> 
                            <input type="hidden" id="isGlobalAdjustmentInput" name="isGlobalAdd" value="false">
                            <input type="hidden" id="adjustmentTypeInput" name="adjustmentOperation" value="add">
                            <div class="form-item scope-selection-top">
                                <label class="toggle-switch-label">Apply to All Active Employees?</label>
                                <label class="switch">
                                    <input type="checkbox" id="applyToAllToggle" name="applyToAll">
                                    <span class="slider round"></span>
                                </label>
                            </div>
                            <div class="form-item" id="employeeSelectContainerAdjust">
                                <label for="targetEmployeeIdAdjust">Employee: <span class="required-asterisk">*</span></label>
                                <select id="targetEmployeeIdAdjust" name="targetEmployeeId" required>
                                    <option value="">-- Select Employee --</option>
                                    <% for (Map<String, Object> emp : activeEmployeeList_accruals) {
                                        Integer empGlobalEid = (Integer) emp.get("eid");
                                        String displayName = (String) emp.get("displayName");
                                    %>
                                        <option value="<%= empGlobalEid %>"><%= escapeJspHtml(displayName) %></option>
                                    <% } %>
                                    <% if (activeEmployeeList_accruals.isEmpty() && employeeListError == null) { %>
                                        <option value="" disabled>No active employees found</option>
                                    <% } else if (employeeListError != null) { %>
                                         <option value="" disabled><%= escapeJspHtml(employeeListError) %></option>
                                    <% } %>
                                </select>
                            </div>
                            <div class="form-item">
                                <label>Operation: <span class="required-asterisk">*</span></label>
                                <div class="radio-group">
                                    <input type="radio" id="operationAdd" name="adjustmentOperationRadio" value="add" checked>
                                    <label for="operationAdd">Add to Balance</label>
                                    <input type="radio" id="operationSubtract" name="adjustmentOperationRadio" value="subtract">
                                    <label for="operationSubtract">Subtract from Balance</label>
                                    <input type="radio" id="operationSet" name="adjustmentOperationRadio" value="set">
                                    <label for="operationSet">Set Balance To</label>
                                </div>
                            </div>
                            <div class="form-row">
                                <div class="form-item">
                                    <label for="accrualTypeColumnAdjust">Accrual Type: <span class="required-asterisk">*</span></label>
                                    <select id="accrualTypeColumnAdjust" name="accrualTypeColumn" required>
                                        <option value="">-- Select Type --</option>
                                        <option value="VACATION_HOURS">Vacation</option>
                                        <option value="SICK_HOURS">Sick</option>
                                        <option value="PERSONAL_HOURS">Personal</option>
                                    </select>
                                 </div>
                                 <div class="form-item">
                                     <label for="accrualHoursAdjust" id="accrualHoursAdjustLabel">Hours: <span class="required-asterisk">*</span></label>
                                     <input type="number" id="accrualHoursAdjust" name="accrualHours" step="0.01" min="0" required placeholder="e.g., 8.00">
                                 </div>
                            </div>
                        </fieldset>
                    </div> 
                    <div class="button-row">
                        <button type="submit" class="glossy-button text-green">
                            <i class="fas fa-check-circle"></i> <span id="submitAdjustButtonText">Update Balance</span>
                        </button>
                    </div>
                </form>
            </div> 
        </div>
        
        <form action="<%= request.getContextPath() %>/AddAndDeleteAccrualPoliciesServlet" method="POST" id="deleteAccrualForm" style="display:none;">
            <input type="hidden" name="action" value="deleteAndReassignAccrualPolicy">
            <input type="hidden" name="hiddenAccrualNameToDelete" id="hiddenDeleteAccrualName" value="">
            <input type="hidden" name="targetAccrualPolicyForReassignment" id="hiddenTargetAccrualPolicyForReassignment" value="">
        </form>
    </div>

    <%-- Standard Modals for Add/Edit/Delete Policy and Notifications --%>
    <div id="addAccrualModal" class="modal"> <div class="modal-content"> <span class="close" id="closeAddAccrual_X_Btn">&times;</span> <h2>Add Accrual Policy</h2> <form id="addAccrualForm" action="<%= request.getContextPath() %>/AddAndDeleteAccrualPoliciesServlet" method="post"> <input type="hidden" name="action" value="addAccrual"> <div class="form-item"><label for="addAccrualName">Policy Name: <span class="required-asterisk">*</span></label><input type="text" id="addAccrualName" name="addAccrualName" maxlength="50" required autofocus></div> <div class="form-item"><label for="addVacationDays">Annual Vacation Days: <span class="required-asterisk">*</span></label><input type="number" id="addVacationDays" name="addVacationDays" required value="0" min="0" step="1"></div> <div class="form-item"><label for="addSickDays">Annual Sick Days: <span class="required-asterisk">*</span></label><input type="number" id="addSickDays" name="addSickDays" required value="0" min="0" step="1"></div> <div class="form-item"><label for="addPersonalDays">Annual Personal Days: <span class="required-asterisk">*</span></label><input type="number" id="addPersonalDays" name="addPersonalDays" required value="0" min="0" step="1"></div> <div class="button-row"> <button type="submit" class="submit-btn glossy-button text-green"><i class="fas fa-check"></i> Submit</button> <button type="button" id="cancelAddAccrualBtn" class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button> </div> </form> </div> </div>
    <div id="editAccrualModal" class="modal"> <div class="modal-content"> <span class="close" id="closeEditAccrual_X_Btn">&times;</span> <h2>Edit Accrual Policy</h2> <form id="editAccrualForm" action="<%= request.getContextPath() %>/AddAndDeleteAccrualPoliciesServlet" method="post"> <input type="hidden" name="action" value="editAccrual"> <input type="hidden" id="originalAccrualName" name="originalAccrualName"> <div class="form-item"><label for="editAccrualName">Policy Name: <span class="required-asterisk">*</span></label><input type="text" id="editAccrualName" name="editAccrualName" maxlength="50" required disabled></div> <div class="form-item"><label for="editVacationDays">Annual Vacation Days: <span class="required-asterisk">*</span></label><input type="number" id="editVacationDays" name="editVacationDays" required min="0" step="1"></div> <div class="form-item"><label for="editSickDays">Annual Sick Days: <span class="required-asterisk">*</span></label><input type="number" id="editSickDays" name="editSickDays" required min="0" step="1"></div> <div class="form-item"><label for="editPersonalDays">Annual Personal Days: <span class="required-asterisk">*</span></label><input type="number" id="editPersonalDays" name="editPersonalDays" required min="0" step="1"></div> <div class="button-row"> <button type="submit" class="submit-btn glossy-button text-green"><i class="fas fa-save"></i> Update</button> <button type="button" id="cancelEditAccrualBtn" class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button> </div> </form> </div> </div>
    <div id="deleteAndReassignAccrualModal" class="modal"> <div class="modal-content"> <span class="close" id="closeDeleteReassignAccrualModalBtn">&times;</span> <h2>Delete Accrual Policy & Reassign</h2> <p id="deleteReassignAccrualModalMessage" style="margin-bottom: 15px; line-height: 1.6;"></p> <div class="form-item" style="margin-bottom: 20px;"> <label for="targetReassignAccrualSelect">Reassign affected employees to policy:</label> <select id="targetReassignAccrualSelect" name="targetReassignAccrualSelect_form"> </select> </div> <div id="deleteReassignAccrualModalError" class="error-message" style="display:none; margin-top:10px; margin-bottom:15px;"></div> <div class="button-row"> <button type="button" id="confirmDeleteAndReassignAccrualBtn" class="glossy-button text-red"><i class="fas fa-trash-alt"></i> Delete & Reassign</button> <button type="button" id="cancelDeleteReassignAccrualBtn" class="cancel-btn glossy-button text-grey"><i class="fas fa-times"></i> Cancel</button> </div> </div> </div>
    <div id="notificationModalGeneral" class="modal"><div class="modal-content" style="max-width: 480px;"><span class="close" id="closeNotificationModalGeneralXBtn_Accrual" data-close-modal-id="notificationModalGeneral">&times;</span><h2 id="notificationModalGeneralTitle">Notification</h2><p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center; line-height:1.6;"></p><div class="button-row" style="justify-content: center;"><button type="button" id="okButtonNotificationModalGeneral" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue"><i class="fas fa-thumbs-up"></i> OK</button></div></div></div>

    <%-- NEW Generic Wizard Prompt Modal for Accruals Page --%>
    <% if (inSetupWizardMode_JSP) { %>
        <div id="wizardGenericPromptModal_Accruals" class="modal wizard-modal" style="display:none; z-index: 10002;">
            <div class="modal-content">
                <span class="close" id="closeWizardGenericPromptModal_Accruals">&times;</span>
                <h2 id="wizardGenericPromptModalTitle_Accruals">Setup Step</h2>
                <div id="wizardGenericPromptModalTextContainer_Accruals" style="margin-top:15px;">
                    <p id="wizardGenericPromptModalText1_Accruals"></p>
                    <p id="wizardGenericPromptModalText2_Accruals" style="margin-top:10px; font-size:0.9em;"></p>
                </div>
                <div id="wizardGenericPromptModalButtonRow_Accruals" class="button-row" style="margin-top:25px;">
                    <%-- Buttons will be dynamically added by JavaScript --%>
                </div>
            </div>
        </div>
    <% } %>

    <script type="text/javascript">
        const appRootPath = "<%= request.getContextPath() %>";
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript">
        // Page-specific JS variables for accruals.js
        window.allAvailableAccrualPoliciesForReassign = [
            <% if (allAccrualPoliciesForDropdown != null) {
                boolean firstPolicy = true;
                for (Map<String, String> policy : allAccrualPoliciesForDropdown) {
                    if (policy != null && policy.get("name") != null) {
                        if (!firstPolicy) { out.print(","); } %>
                        { "name": "<%= escapeForJavaScriptString(policy.get("name")) %>" }<%
                        firstPolicy = false;
                    }
                }
               } %>
        ];
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= currentWizardStepForPage_JSP != null ? escapeForJavaScriptString(currentWizardStepForPage_JSP) : "" %>";
        window.COMPANY_NAME_SIGNUP_JS = "<%= companyNameSignup_Accruals != null ? escapeForJavaScriptString(companyNameSignup_Accruals) : "Your Company" %>";
        window.ACCRUAL_POLICY_JUST_ADDED_WIZARD = <%= accrualPolicyJustAddedInWizard_JSP %>;
        window.showSpecificIntroModal_Accruals_JS = <%= showSpecificIntroModal_Accruals_JSP %>;
        
        console.log("[accruals.jsp JS Globals] InWizardMode: " + window.inWizardMode_Page + 
                           ", CurrentStep: " + window.currentWizardStep_Page +
                           ", PolicyJustAdded: " + window.ACCRUAL_POLICY_JUST_ADDED_WIZARD +
                           ", ShowSpecificIntro: " + window.showSpecificIntroModal_Accruals_JS +
                           ", CompanyName: " + window.COMPANY_NAME_SIGNUP_JS
                           );
    </script>
    <script src="<%= request.getContextPath() %>/js/accruals.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>