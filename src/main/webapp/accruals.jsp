<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>
<% Logger jspLogger = Logger.getLogger("accruals.jsp"); %>
<%@ page import="timeclock.accruals.ShowAccruals,
                 jakarta.servlet.http.HttpSession,
                 java.util.List,
                 java.util.Map,
                 java.util.ArrayList,
                 java.net.URLEncoder,
                 java.nio.charset.StandardCharsets" %>
<%!
    private String escapeJspHtml(String input) { if (input == null) return ""; return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private String escapeForJavaScriptString(String input) { if (input == null) return ""; return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("/", "\\/"); }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String pageLevelError = request.getParameter("error"); 
    String pageLevelSuccess = request.getParameter("message"); 
    boolean isReopenModalRequest = request.getParameter("reopenModal") != null;
    
    boolean inSetupWizardMode_JSP = false;
    String currentWizardStepForPage_JSP = null;
    String companyNameSignup_Accruals = "Your Company";
    // *** MODIFIED: Added check for accrualAdded parameter ***
    boolean accrualJustAddedInWizard_JSP = "true".equalsIgnoreCase(request.getParameter("accrualAdded"));

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_Accruals = (String) companyNameObj;
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

    List<Map<String, String>> allAccrualPoliciesForDropdown = new ArrayList<>();
    List<Map<String, String>> allEmployeesForDropdown = new ArrayList<>();
    String accrualRowsHtml = "";
    String dataFetchError = null;

    if (tenantId != null && tenantId > 0) {
        try {
            allAccrualPoliciesForDropdown = ShowAccruals.getAccrualPoliciesForTenant(tenantId);
            allEmployeesForDropdown = ShowAccruals.getEmployeesForTenant(tenantId, false);
            accrualRowsHtml = ShowAccruals.showAccruals(tenantId);
        } catch (Throwable t) {
            dataFetchError = "A critical error occurred while loading page data: " + t.toString();
            jspLogger.log(Level.SEVERE, "[accruals.JSP DEBUG] CRITICAL FAILURE in data fetching block.", t);
        }
    } else {
        dataFetchError = "Invalid session or tenant context.";
    }
    
    if (dataFetchError != null) {
        accrualRowsHtml = "<tr><td colspan='4' class='report-error-row'>" + escapeJspHtml(dataFetchError) + "</td></tr>";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Manage Accrual Policies<% if(inSetupWizardMode_JSP) { %> - Company Setup<% } %></title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %> 
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/accruals.css?v=<%= System.currentTimeMillis() %>"> 
</head>
<body>
    <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Accrual Policies for <%= escapeJspHtml(companyNameSignup_Accruals) %></h2>
            <p>Configure time off policies (Vacation, Sick, etc.). You can assign them to employees later.</p>
        </div>
    <% } %>

    <div class="parent-container">
        <h1>Manage Accrual Policies <% if(inSetupWizardMode_JSP) { %> <span class="setup-label">(Setup)</span> <% } %></h1>

        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message" id="pageNotificationDiv_Success_Accrual"><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty()) { %><div class="page-message error-message" id="pageNotificationDiv_Error_Accrual"><%= escapeJspHtml(pageLevelError) %></div><% } %>
       
        <div id="button-container" class="main-action-buttons">
            <button type="button" id="btnAddPolicy" class="glossy-button text-green"><i class="fas fa-plus"></i> Add Accrual Policy</button>
            <button type="button" id="btnEditPolicy" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Accrual Policy</button>
            <button type="button" id="btnDeletePolicy" class="glossy-button text-red" disabled><i class="fas fa-trash-alt"></i> Delete Accrual Policy</button>
        </div>
        
        <h4 style="color: #6c757d; margin: 7px auto 0 auto; font-size: 0.9em;"><span class="instruction-text">💡 Select a row to edit or delete</span></h4>
        
        <div class="content-display-area">
            <div class="table-container report-table-container">
                <table id="accrualsTable" class="report-table sortable" data-initial-sort-column="0" data-initial-sort-direction="asc">
                    <thead>
                        <tr>
                            <th class="sortable" data-sort-type="string">Policy Name</th>
                            <th class="sortable" data-sort-type="number">Annual Vacation Days</th>
                            <th class="sortable" data-sort-type="number">Annual Sick Days</th>
                            <th class="sortable" data-sort-type="number">Annual Personal Days</th>
                        </tr>
                    </thead>
                    <tbody>
                        <%= accrualRowsHtml %>
                    </tbody>
                </table>
            </div>
        </div>
        
        <form action="<%= request.getContextPath() %>/AddAndDeleteAccrualPoliciesServlet" method="POST" id="deleteAccrualForm" style="display:none;"><input type="hidden" name="action" value="deleteAndReassignAccrualPolicy"><input type="hidden" name="hiddenAccrualNameToDelete" id="hiddenDeleteAccrualName" value=""><input type="hidden" id="targetAccrualPolicyForReassignment" name="targetAccrualPolicyForReassignment" value=""></form>
    
        <div id="adjustAccrualBalanceSection">
            <div class="manage-accrual-form-outer-box">
                 <h2>Adjust Employee Accrual Balance</h2>
                <p class="page-description">Manually add, subtract, or set an employee's accrued time off balance. This is for corrections or initial setup.</p>
                <div class="form-elements-gradient-wrapper">
                    <form action="<%= request.getContextPath() %>/AccrualServlet" method="POST" id="adjustAccrualBalanceForm">
                        <input type="hidden" name="action" value="adjustAccruedBalanceAction">
                        <fieldset id="adjustmentFormFieldset">
                            <div class="scope-selection-top">
                                <span class="toggle-switch-label">Single Employee</span>
                                <label class="switch">
                                    <input type="checkbox" id="allEmployeesToggle" name="isGlobalAdd" value="true">
                                    <span class="slider round"></span>
                                </label>
                                <span class="toggle-switch-label">All Employees</span>
                            </div>
                        
                            <div class="form-item" id="employeeSelectContainer">
                               <label for="employeeSelect">Select Employee: <span class="required-asterisk">*</span></label>
                                <select id="employeeSelect" name="targetEmployeeId">
                                    <option value="">-- Select an Employee --</option>
                                    <% for (Map<String, String> emp : allEmployeesForDropdown) { %>
                                        <option value="<%= escapeJspHtml(emp.get("id")) %>"><%= escapeJspHtml(emp.get("lastName")) %>, <%= escapeJspHtml(emp.get("firstName")) %></option>
                                    <% } %>
                                </select>
                            </div>
                             <div class="form-row">
                                <div class="form-item">
                                    <label>Accrual Type: <span class="required-asterisk">*</span></label>
                                    <div class="radio-group" id="adjustmentType">
                                        <label><input type="radio" name="accrualTypeColumn" value="VACATION_HOURS" checked> Vacation</label>
                                        <label><input type="radio" name="accrualTypeColumn" value="SICK_HOURS"> Sick</label>
                                        <label><input type="radio" name="accrualTypeColumn" value="PERSONAL_HOURS"> Personal</label>
                                    </div>
                                </div>
                                <div class="form-item">
                                    <label for="adjustmentHours">Hours: <span class="required-asterisk">*</span></label>
                                    <input type="number" id="adjustmentHours" name="accrualHours" step="0.01" min="0" placeholder="e.g., 8.0" required>
                                </div>
                            </div>
                            <div class="form-item">
                                <label>Action: <span class="required-asterisk">*</span></label>
                                <div class="radio-group" id="adjustmentAction">
                                    <label><input type="radio" name="adjustmentOperation" value="add" checked> Add to Balance</label>
                                    <label><input type="radio" name="adjustmentOperation" value="subtract"> Subtract from Balance</label>
                                     <label><input type="radio" name="adjustmentOperation" value="set"> Set balance to</label>
                                </div>
                            </div>
                         </fieldset>
                         <div class="button-row">
                            <button type="submit" id="submitAdjustmentBtn" class="glossy-button text-blue"><i class="fas fa-check"></i> Apply Adjustment</button>
                         </div>
                    </form>
                </div>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    <%@ include file="/WEB-INF/includes/accruals-modals.jspf" %>

    <script type="text/javascript">
         window.appRootPath = "<%= request.getContextPath() %>";
        window.allAvailableAccrualPoliciesForReassign = [ <% if (allAccrualPoliciesForDropdown != null) { boolean firstPolicy = true; for (Map<String, String> policy : allAccrualPoliciesForDropdown) { if (policy != null && policy.get("name") != null) { if (!firstPolicy) { out.print(","); } %> { "name": "<%= escapeForJavaScriptString(policy.get("name")) %>" }<% firstPolicy = false; }}} %> ];
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        // *** MODIFIED: Added itemJustAdded_Page for consistent wizard logic ***
        window.itemJustAdded_Page = <%= accrualJustAddedInWizard_JSP %>;
        window.currentWizardStep_Page = "<%= currentWizardStepForPage_JSP != null ? escapeForJavaScriptString(currentWizardStepForPage_JSP) : "" %>";
        window.COMPANY_NAME_SIGNUP_JS_accruals = "<%= escapeForJavaScriptString(companyNameSignup_Accruals) %>";
    </script>
    
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="<%= request.getContextPath() %>/js/accruals.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>