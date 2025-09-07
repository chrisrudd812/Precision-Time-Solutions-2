<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@ page import="jakarta.servlet.http.HttpSession"%>
<%@ page import="timeclock.employees.ShowEmployees"%>
<%@ page import="timeclock.reports.ShowReports"%>
<%@ page import="java.util.*"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.nio.charset.StandardCharsets"%>
<%@ page import="java.text.DecimalFormat" %>

<%!
    private String escapeJspHtml(String input) { if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    private String escapeForJavaScriptString(String input) { if (input == null) return ""; return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("/", "\\/"); }
    private String buildJsonArray(List<Map<String, String>> dataList) {
        StringBuilder json = new StringBuilder("[");
        if (dataList != null) {
            for (int i = 0; i < dataList.size(); i++) {
                Map<String, String> item = dataList.get(i);
                json.append("{");
                int keyCount = 0;
                for (Map.Entry<String, String> entry : item.entrySet()) {
                    json.append("\"").append(escapeForJavaScriptString(entry.getKey())).append("\":\"").append(escapeForJavaScriptString(entry.getValue())).append("\"");
                    if (++keyCount < item.size()) json.append(",");
                }
                json.append("}");
                if (i < dataList.size() - 1) json.append(",");
            }
        }
        json.append("]");
        return json.toString();
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");
    boolean isReopenModalRequest = request.getParameter("reopenModal") != null;

    boolean inSetupWizardMode_JSP = false;
    String currentWizardStepForPage_JSP = null;
    String companyNameSignup_Employees = "Your Company";
    String companyIdentifier_Employees = "";
    Integer adminEid = null; 

    if (currentSession != null) {
        tenantId = (Integer) currentSession.getAttribute("TenantID");
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) { companyNameSignup_Employees = (String) companyNameObj; }
        
        Object companyIdObj = currentSession.getAttribute("GeneratedCompanyID");
        if (companyIdObj instanceof String) { companyIdentifier_Employees = (String) companyIdObj; }


        // ## DEBUG START: Log the state of the session attribute when the page renders ##
        boolean isWizardAttributePresent = (currentSession.getAttribute("startSetupWizard") != null && ((Boolean)currentSession.getAttribute("startSetupWizard")));
        System.out.println("--- WIZARD DEBUG (employees.jsp): Rendering page. Is 'startSetupWizard' present in session? " + isWizardAttributePresent + " ---");
        // ## DEBUG END ##

        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            adminEid = (Integer) currentSession.getAttribute("wizardAdminEid");

            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("step");
            currentWizardStepForPage_JSP = (wizardStepFromParam != null && !wizardStepFromParam.isEmpty()) ? wizardStepFromParam : sessionWizardStep;
            if (currentWizardStepForPage_JSP != null && !currentWizardStepForPage_JSP.equals(sessionWizardStep)) { currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP); }
        }

        String userPermissions = (String) currentSession.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) { pageLevelError = "Access Denied."; }
    } else {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name()));
        return;
    }

    List<Map<String, String>> departments = (tenantId != null) ? ShowReports.getDepartmentsForTenant(tenantId) : new ArrayList<>();
    List<Map<String, String>> schedules = (tenantId != null) ? ShowReports.getSchedulesForTenant(tenantId) : new ArrayList<>();
    List<Map<String, String>> accrualPolicies = (tenantId != null) ?
    ShowReports.getAccrualPoliciesForTenant(tenantId) : new ArrayList<>();
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
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/employees.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; }
        .modal-content h2 { cursor: move; user-select: none; }
        #wizardGenericModal .modal-content p { padding: 10px 25px !important; }
    </style>
</head>
<body class="reports-page">
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
        
        <div id="button-container" class="main-action-buttons">
            <button type="button" id="addEmployeeButton" class="glossy-button text-green"><i class="fas fa-user-plus"></i> Add Employee</button>
            <button type="button" id="editEmployeeButton" class="glossy-button text-orange" disabled><i class="fas fa-user-edit"></i> Edit Employee</button>
            <button type="button" id="deleteEmployeeButton" class="glossy-button text-red" disabled><i class="fas fa-user-times"></i> Deactivate Employee</button>
        </div>
        <h4 style="text-align: left; color: #6c757d; margin-bottom: 10px; font-size: 0.9em;">To Edit or Delete: First select a row from the table below.</h4>

        <div class="report-display-area" style="padding-top: 10px;">
            <div id="reportOutput_employees" class="table-container report-table-container" style="background-color: #fff;">
                <table class="report-table sortable" id="employeesTable"
                       data-initial-sort-column="2"
                       data-initial-sort-direction="asc">
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
                             <th class="sortable" data-sort-type="date">Hire Date</th>
                            <th class="sortable" data-sort-type="string">Work Sched.</th>
                        </tr>
                      </thead>
                    <tbody><%= employeeRowsHtml %></tbody>
                </table>
            </div>
        </div>

        <div id="employeeDetailsSection" style="display: none;">
            <h2>Selected Employee Details</h2>
               <div class="details-grid">
                <div class="detail-group">
                    <h3>Personal Information</h3>
                     <p><label>Employee ID:</label><span id="detailEID">--</span></p>
                    <p><label>First Name:</label><span id="detailFirstName">--</span></p>
                     <p><label>Last Name:</label><span id="detailLastName">--</span></p>
                    <p><label>Address:</label><span id="detailAddress">--</span></p>
                    <p><label>City:</label><span id="detailCity">--</span></p>
                     <p><label>State:</label><span id="detailState">--</span></p>
                    <p><label>Zip:</label><span id="detailZip">--</span></p>
                          <p><label>Phone:</label><span id="detailPhone">--</span></p>
                    <p><label>E-mail:</label><span id="detailEmail">--</span></p>
                </div>
                 <div class="detail-group">
                    <h3>Company Information</h3>
                       <p><label>Department:</label><span id="detailDept">--</span></p>
                    <p><label>Schedule:</label><span id="detailSchedule">--</span></p>
                    <p><label>Supervisor:</label><span id="detailSupervisor">--</span></p>
                     <p><label>Permissions:</label><span id="detailPermissions">--</span></p>
                     <p><label>Hire Date:</label><span id="detailHireDate">--</span></p>
                     <p><label>Work Schedule:</label><span id="detailWorkSched">--</span></p>
                    <p><label>Wage Type:</label><span id="detailWageType">--</span></p>
                    <p><label>Wage:</label><span id="detailWage">--</span></p>
                </div>
                 <div class="detail-group" id="accrualInfoGroup">
                     <h3>Accrual Info</h3>
                    <p><label>Accrual Policy:</label><span id="detailAccrualPolicy">--</span></p>
                    <p><label>Vacation Hours:</label><span id="detailVacHours">--</span></p>
                     <p><label>Sick Hours:</label><span id="detailSickHours">--</span></p>
                     <p><label>Personal Hours:</label><span id="detailPersHours">--</span></p>
                    <form action="<%=request.getContextPath()%>/EmployeeInfoServlet" method="get" id="resetPasswordForm">
                        <input type="hidden" name="action" value="resetPassword">
                         <input type="hidden" name="eid" id="resetFormEid" value="">
                          <button type="submit" id="btnResetPassword" class="glossy-button text-blue" disabled><i class="fas fa-key"></i> Reset PIN</button>
                    </form>
                </div>
            </div>
        </div>
    </div>
    
     <%@ include file="/WEB-INF/includes/employees-modals.jspf" %>
      <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>

    <div id="deactivateConfirmModal" class="modal">
         <div class="modal-content" style="max-width: 500px;">
            <span class="close">&times;</span>
            <h2>Confirm Deactivation</h2>
            <p>Are you sure you want to deactivate <strong id="deactivateEmployeeName"></strong>?</p>
            
            <div class="form-item" style="padding: 0 25px 15px;">
                <label for="deactivationReasonSelect" style="display:block; text-align:left; margin-bottom:5px;">Reason <span class="required-asterisk">*</span></label>
                <select id="deactivationReasonSelect" name="deactivationReason" required style="width: 100%; height: 40px; font-size: 1em; border: 1px solid #cbd5e0; border-radius: 4px;">
                    <option value="">-- Select a Reason --</option>
                    <option value="Termination">Termination</option>
                    <option value="Laid Off">Laid Off</option>
                    <option value="Quit">Quit</option>
                    <option value="Temporary Employee">Temporary Employee</option>
                    <option value="Seasonal Worker">Seasonal Worker</option>
                    <option value="Contractor">Contractor</option>
                </select>
            </div>

              <p class="deactivate-note">This employee can be reactivated via Reports &gt; Employee Reports &gt; Inactive Employees.</p>
            <div class="button-row">
                <button type="button" id="confirmDeactivateBtn" class="glossy-button text-red">Deactivate</button>
                <button type="button" class="cancel-btn glossy-button text-grey">Cancel</button>
            </div>
        </div>
    </div>

     <div id="reactivateEmployeeModal" class="modal">
        <div class="modal-content" style="max-width: 500px;">
               <span class="close">&times;</span>
            <h2>Employee Exists</h2>
            <p style="text-align: center;padding-left: 20px;padding-right: 20px; ">An inactive employee with the email <strong id="reactivateEmail"></strong> already exists.</p>
            <p style="text-align: center;">Would you like to reactivate this employee's account?</p>
            <div class="button-row">
                  <button type="button" id="confirmReactivateBtn" class="glossy-button text-green">Reactivate Employee</button>
                <button type="button" class="cancel-btn glossy-button text-grey">Cancel</button>
            </div>
          </div>
    </div>

    <script>
        window.appRootPath = "<%= request.getContextPath() %>";
        window.inSetupWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        <% if (inSetupWizardMode_JSP && adminEid != null) { %>
            window.wizardAdminEid = <%= adminEid %>;
        <% } %>
        window.companyNameSignup = "<%= escapeForJavaScriptString(companyNameSignup_Employees) %>";
        window.companyIdentifier = "<%= escapeForJavaScriptString(companyIdentifier_Employees) %>";
        window.currentWizardStep_Page = "<%= escapeForJavaScriptString(currentWizardStepForPage_JSP) %>";
        window.departmentsData = JSON.parse('<%= departmentsJson %>');
        window.schedulesData = JSON.parse('<%= schedulesJson %>');
        window.accrualPoliciesData = JSON.parse('<%= accrualPoliciesJson %>');
        
        console.log('%c--- WIZARD DEBUG (employees.jsp): Page loaded. Client-side wizard mode flag (inSetupWizardMode_Page) is set to: <%= inSetupWizardMode_JSP %> ---', 'color: #ffc107; font-weight: bold;');
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="<%= request.getContextPath() %>/js/employees.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>