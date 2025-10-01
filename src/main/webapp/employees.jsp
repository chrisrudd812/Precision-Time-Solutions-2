<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@ page import="jakarta.servlet.http.HttpSession"%>
<%@ page import="timeclock.employees.ShowEmployees"%>
<%@ page import="timeclock.reports.ShowReports"%>
<%@ page import="java.util.List"%>
<%@ page import="java.util.Map"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.nio.charset.StandardCharsets"%>
<%@ page import="java.text.DecimalFormat" %>

<%!
    private String escapeJspHtml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeForJavaScriptString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("/", "\\/");
    }

    private String buildJsonArray(List<Map<String, String>> dataList) {
        StringBuilder json = new StringBuilder("[");
        if (dataList != null) {
            for (int i = 0; i < dataList.size(); i++) {
                Map<String, String> item = dataList.get(i);
                json.append("{");
                int keyCount = 0;
                for (Map.Entry<String, String> entry : item.entrySet()) {
                    json.append("\"").append(escapeForJavaScriptString(entry.getKey())).append("\":\"").append(escapeForJavaScriptString(entry.getValue())).append("\"");
                    if (++keyCount < item.size()) {
                        json.append(",");
                    }
                }
                json.append("}");
                if (i < dataList.size() - 1) {
                    json.append(",");
                }
            }
        }
        json.append("]");
        return json.toString();
    }
%>
<%
    // Enhanced logging for session debugging
    java.util.logging.Logger pageLogger = java.util.logging.Logger.getLogger("employees_jsp_debug");
    pageLogger.info("=== EMPLOYEES.JSP ACCESS ATTEMPT ===");
    pageLogger.info("[DEBUG] User-Agent: " + request.getHeader("User-Agent"));
    pageLogger.info("[DEBUG] Remote Address: " + request.getRemoteAddr());
    pageLogger.info("[DEBUG] Session ID from request: " + request.getRequestedSessionId());
    
    HttpSession currentSession = request.getSession(false);
    pageLogger.info("[DEBUG] Session from request.getSession(false): " + (currentSession != null ? currentSession.getId() : "NULL"));
    
    Integer tenantId = null;
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");
    boolean isReopenModalRequest = request.getParameter("reopenModal") != null;

    boolean inSetupWizardMode_JSP = false;
    String currentWizardStepForPage_JSP = null;
    String companyNameSignup_Employees = "Your Company";
    String companyIdentifier_Employees = "";
    Integer adminEid = null;
    boolean employeeJustAddedInWizard_JSP = "true".equalsIgnoreCase(request.getParameter("empAdded"));

    if (currentSession != null) {
        pageLogger.info("[DEBUG] Session found! Session ID: " + currentSession.getId());
        pageLogger.info("[DEBUG] Session creation time: " + new java.util.Date(currentSession.getCreationTime()));
        pageLogger.info("[DEBUG] Session last accessed: " + new java.util.Date(currentSession.getLastAccessedTime()));
        pageLogger.info("[DEBUG] Session max inactive interval: " + currentSession.getMaxInactiveInterval() + " seconds");
        
        tenantId = (Integer) currentSession.getAttribute("TenantID");
        pageLogger.info("[DEBUG] TenantID from session: " + tenantId);
        
        String userPermissions = (String) currentSession.getAttribute("Permissions");
        pageLogger.info("[DEBUG] User permissions from session: '" + userPermissions + "'");
        
        Integer eid = (Integer) currentSession.getAttribute("EID");
        pageLogger.info("[DEBUG] EID from session: " + eid);
        
        String userFirstName = (String) currentSession.getAttribute("UserFirstName");
        String userLastName = (String) currentSession.getAttribute("UserLastName");
        pageLogger.info("[DEBUG] User name from session: '" + userFirstName + " " + userLastName + "'");
        
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_Employees = (String) companyNameObj;
        }
        
        Object companyIdObj = currentSession.getAttribute("GeneratedCompanyID");
        if (companyIdObj instanceof String) {
            companyIdentifier_Employees = (String) companyIdObj;
        }

        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            adminEid = (Integer) currentSession.getAttribute("wizardAdminEid");

            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("step");
            currentWizardStepForPage_JSP = (wizardStepFromParam != null && !wizardStepFromParam.isEmpty()) ? wizardStepFromParam : sessionWizardStep;
            
            if ("initialPinSetRequired".equals(currentWizardStepForPage_JSP)) {
                currentWizardStepForPage_JSP = "verify_admin_prompt";
                currentSession.setAttribute("wizardStep", "verify_admin_prompt");
            }
            
            if (currentWizardStepForPage_JSP != null && !currentWizardStepForPage_JSP.equals(sessionWizardStep)) { 
                currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
            }
        }

        // userPermissions already retrieved above for logging
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            pageLogger.warning("[DEBUG] ACCESS DENIED - User permissions: '" + userPermissions + "' is not Administrator");
            pageLevelError = "Access Denied.";
        } else {
            pageLogger.info("[DEBUG] ACCESS GRANTED - User has Administrator permissions");
        }
    } else {
        pageLogger.warning("[DEBUG] NO SESSION FOUND - Redirecting to login");
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name()));
        return;
    }

    List<Map<String, String>> departments = (tenantId != null) ? ShowReports.getDepartmentsForTenant(tenantId) : new ArrayList<>();
    List<Map<String, String>> schedules = (tenantId != null) ? ShowReports.getSchedulesForTenant(tenantId) : new ArrayList<>();
    List<Map<String, String>> accrualPolicies = (tenantId != null) ? ShowReports.getAccrualPoliciesForTenant(tenantId) : new ArrayList<>();
    String employeeRowsHtml = (tenantId != null) ? ShowEmployees.showEmployees(tenantId) : "<tr><td colspan='12' class='report-error-row'>Invalid session.</td></tr>";
    String departmentsJson = buildJsonArray(departments);
    String schedulesJson = buildJsonArray(schedules);
    String accrualPoliciesJson = buildJsonArray(accrualPolicies);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Employee Management<% if (inSetupWizardMode_JSP) { %> - Company Setup<% } %></title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/employees.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="css/modals.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="employees-page">
    <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Manage Employees for <%= escapeJspHtml(companyNameSignup_Employees) %></h2>
            <p>Final step! Confirm your admin details, then add your other employees.</p>
        </div>
    <% } %>

    <div class="parent-container reports-container">
        <h1>Employee Management <% if(inSetupWizardMode_JSP) { %><span style="font-size: 0.8em; color: #555;">(Setup)</span><% } %></h1>
        
        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div id="pageNotificationDiv_Success_Emp" class="page-message success-message"><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty()) { %><div id="pageNotificationDiv_Error_Emp" class="page-message error-message"><%= escapeJspHtml(pageLevelError) %></div><% } %>
        
        <div id="button-container" class="main-action-buttons" style="margin-bottom: 0">
            <button type="button" id="addEmployeeButton" class="glossy-button text-green"><i class="fas fa-user-plus"></i> Add Employee</button>
            <button type="button" id="editEmployeeButton" class="glossy-button text-orange" disabled><i class="fas fa-user-edit"></i> Edit Employee</button>
            <button type="button" id="deleteEmployeeButton" class="glossy-button text-red" disabled><i class="fas fa-user-times"></i> Deactivate Employee</button>
        </div>
        
        <h4 style="color: #6c757d; margin: 7px auto 0 auto; font-size: 0.9em;"><span class="instruction-text">💡 Click on a table row to view or edit employee details</span></h4>

        <div id="reportOutput_employees" class="table-container report-table-container" style="background-color: #fff; margin-top: 4px;">
            <table class="report-table sortable" id="employeesTable" data-initial-sort-column="2" data-initial-sort-direction="asc">
                <thead>
                    <tr>
                         <th class="sortable" data-sort-type="number">ID</th>
                         <th class="sortable" data-sort-type="string">First Name</th>
                        <th class="sortable" data-sort-type="string">Last Name</th>
                         <th class="sortable" data-sort-type="string">Department</th>
                         <th class="sortable" data-sort-type="string">Schedule</th>
                        <th class="sortable" data-sort-type="string">Supervisor</th>
                         <th class="sortable" data-sort-type="string">Permissions</th>
                         <th class="sortable" data-sort-type="string">Email</th>
                         <th class="sortable" data-sort-type="string">Work Sched.</th>
                    </tr>
                </thead>
                <tbody><%= employeeRowsHtml %></tbody>
            </table>
        </div>

        <div id="employeeDetailsSection" style="display: none;">
            <h2>Selected Employee Details</h2>
            <div class="details-grid">
                <div class="detail-group">
                    <h3><i class="fas fa-user"></i> Personal Information</h3>
                    <div class="detail-content">
                        <p><label>Employee ID:</label><span id="detailEID">--</span></p>
                        <p><label>Name:</label><span id="detailFullName">--</span></p>
                        <p><label>Address:</label><span id="detailFullAddress">--</span></p>
                        <p><label>Phone:</label><span id="detailPhone">--</span></p>
                        <p><label>E-mail:</label><span id="detailEmail">--</span></p>
                    </div>
                </div>
                <div class="detail-group">
                    <h3><i class="fas fa-building"></i> Company Information</h3>
                    <div class="detail-content">
                        <p><label>Department:</label><span id="detailDept">--</span></p>
                        <p><label>Schedule:</label><span id="detailSchedule">--</span></p>
                        <p><label>Supervisor:</label><span id="detailSupervisor">--</span></p>
                        <p><label>Permissions:</label><span id="detailPermissions">--</span></p>
                        <p><label>Hire Date:</label><span id="detailHireDate">--</span></p>
                        <p><label>Work Schedule:</label><span id="detailWorkSched">--</span></p>
                        <p><label>Wage:</label><span id="detailWageInfo">--</span></p>
                    </div>
                </div>
                <div class="detail-group">
                    <h3><i class="fas fa-calendar-check"></i> PTO Information</h3>
                    <div class="detail-content">
                        <p><label>PTO Policy:</label><span id="detailAccrualPolicy">--</span></p>
                        <p><label>Vacation Hours:</label><span id="detailVacHours">--</span></p>
                        <p><label>Sick Hours:</label><span id="detailSickHours">--</span></p>
                        <p><label>Personal Hours:</label><span id="detailPersHours">--</span></p>
                    </div>
                </div>
                <div class="detail-group" id="securityGroup">
                    <h3><i class="fas fa-shield-alt"></i> Security Management</h3>
                    <div class="detail-content">
                        <div class="security-item">
                            <h4>PIN Management</h4>
                            <div class="security-description">Reset employee's PIN to default (1234). Employee must change on next login.</div>
                            <div class="security-status" id="pinStatus">Status: Active</div>
                            <form action="<%=request.getContextPath()%>/EmployeeInfoServlet" method="get" id="resetPasswordForm">
                                <input type="hidden" name="action" value="resetPassword">
                                <input type="hidden" name="eid" id="resetFormEid" value="">
                                <button type="submit" id="btnResetPassword" class="glossy-button text-blue" disabled><i class="fas fa-key"></i> Reset PIN</button>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
    
     <%@ include file="/WEB-INF/includes/employees-modals.jspf" %>
     <%@ include file="/WEB-INF/includes/modals.jspf" %>

    <div id="deactivateConfirmModal" class="modal modal-state-error">
         <div class="modal-content" style="max-width: 500px;">
            <div class="modal-header">
                <h2 class="modal-title-error"><i class="fas fa-user-times"></i> <span>Confirm Deactivation</span></h2>
            </div>
            <div class="modal-body">
                <p>Are you sure you want to deactivate <strong id="deactivateEmployeeName"></strong>?</p>
                <div class="form-item">
                    <label for="deactivationReasonSelect">Reason <span class="required-asterisk">*</span></label>
                    <select id="deactivationReasonSelect" name="deactivationReason" required>
                        <option value="">-- Select a Reason --</option>
                        <option value="Termination">Termination</option>
                        <option value="Laid Off">Laid Off</option>
                        <option value="Quit">Quit</option>
                        <option value="Temporary Employee">Temporary Employee</option>
                        <option value="Seasonal Worker">Seasonal Worker</option>
                        <option value="Contractor">Contractor</option>
                    </select>
                </div>
                <p class="deactivate-note" style="font-size: 0.9em; margin-top: 1rem;">This employee can be reactivated via Reports &gt; Employee Reports &gt; Inactive Employees.</p>
            </div>
            <div class="button-row">
                <button type="button" id="confirmDeactivateBtn" class="glossy-button text-red"><i class="fas fa-exclamation-triangle"></i> Deactivate</button>
                <button type="button" class="cancel-btn glossy-button text-grey"><i class="fas fa-times"></i> Cancel</button>
            </div>
        </div>
    </div>
  
    <div id="reactivateEmployeeModal" class="modal modal-state-success">
        <div class="modal-content" style="max-width: 500px;">
            <div class="modal-header">
                <h2 class="modal-title-success"><i class="fas fa-user-check"></i> <span>Employee Exists</span></h2>
            </div>
            <div class="modal-body">
                <p>An inactive employee <strong id="reactivateName" style="display: none;"></strong>with the email <strong id="reactivateEmail"></strong> already exists.</p>
                <p>Would you like to reactivate this employee's account?</p>
            </div>
            <div class="button-row">
                <button type="button" id="confirmReactivateBtn" class="glossy-button text-green">Reactivate Employee</button>
                <button type="button" class="cancel-btn glossy-button text-grey">Cancel</button>
            </div>
         </div>
    </div>

    <div id="payrollModal" class="modal modal-state-success">
        <div class="modal-content" style="max-width: 500px;">
            <div class="modal-header">
                <h2 class="modal-title-success"><i class="fas fa-calendar-check"></i> <span>Time to Run Payroll</span></h2>
            </div>
            <div class="modal-body">
                <p>The pay period has ended. Would you like to run payroll now?</p>
            </div>
            <div class="button-row">
                <button type="button" id="runPayrollBtn" class="glossy-button text-green"><i class="fas fa-play"></i> Run Payroll</button>
                <button type="button" id="payrollNoBtn" class="glossy-button text-grey"><i class="fas fa-clock"></i> Later</button>
            </div>
        </div>
    </div>

    <script>
        window.appRootPath = "<%= request.getContextPath() %>";
        window.inSetupWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.itemJustAdded_Page = <%= employeeJustAddedInWizard_JSP %>;
        <% if (inSetupWizardMode_JSP && adminEid != null) { %>
            window.wizardAdminEid = <%= adminEid %>;
        <% } %>
        window.companyNameSignup = "<%= escapeForJavaScriptString(companyNameSignup_Employees) %>";
        window.companyIdentifier = "<%= escapeForJavaScriptString(companyIdentifier_Employees) %>";
        window.currentWizardStep_Page = "<%= escapeForJavaScriptString(currentWizardStepForPage_JSP) %>";
        window.departmentsData = JSON.parse('<%= departmentsJson %>');
        window.schedulesData = JSON.parse('<%= schedulesJson %>');
        window.accrualPoliciesData = JSON.parse('<%= accrualPoliciesJson %>');
        window.showPayrollModal = <%= "true".equals(request.getParameter("showPayrollModal")) %>;
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="<%= request.getContextPath() %>/js/employees.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>