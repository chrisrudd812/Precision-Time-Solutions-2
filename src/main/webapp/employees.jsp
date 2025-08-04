<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@ page import="jakarta.servlet.http.HttpSession"%>
<%@ page import="timeclock.employees.ShowEmployees"%>
<%@ page import="timeclock.reports.ShowReports"%>
<%@ page import="java.util.*"%>
<%@ page import="java.net.URLEncoder"%>
<%@ page import="java.nio.charset.StandardCharsets"%>
<%@ page import="java.text.DecimalFormat" %>

<%!
    private String escapeJspHtml(String input) { if (input == null) return ""; return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
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
    String adminUserDataJson = "null";

    if (currentSession != null) {
        tenantId = (Integer) currentSession.getAttribute("TenantID");
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) { companyNameSignup_Employees = (String) companyNameObj; }

        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("step");
            currentWizardStepForPage_JSP = (wizardStepFromParam != null && !wizardStepFromParam.isEmpty()) ? wizardStepFromParam : sessionWizardStep;
            if (currentWizardStepForPage_JSP != null && !currentWizardStepForPage_JSP.equals(sessionWizardStep)) { currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP); }

            Map<String, String> adminData = new HashMap<>();
            adminData.put("firstName", (String) currentSession.getAttribute("AdminFirstName"));
            adminData.put("lastName", (String) currentSession.getAttribute("AdminLastName"));
            adminData.put("email", (String) currentSession.getAttribute("AdminEmail"));
            StringBuilder adminJsonBuilder = new StringBuilder("{");
            adminJsonBuilder.append("\"firstName\":\"").append(escapeForJavaScriptString(adminData.get("firstName"))).append("\",");
            adminJsonBuilder.append("\"lastName\":\"").append(escapeForJavaScriptString(adminData.get("lastName"))).append("\",");
            adminJsonBuilder.append("\"email\":\"").append(escapeForJavaScriptString(adminData.get("email"))).append("\"");
            adminJsonBuilder.append("}");
            adminUserDataJson = adminJsonBuilder.toString();
        }

        String userPermissions = (String) currentSession.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) { pageLevelError = "Access Denied."; }
    } else {
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
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/employees.css?v=<%=System.currentTimeMillis()%>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; }
        /* **FIX**: Added padding to the wizard modal's text paragraphs */
        #wizardGenericModalTextContainer p { padding: 0 15px; }
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
        
        <div id="button-container" class="main-action-buttons">
            <button type="button" id="addEmployeeButton" class="glossy-button text-green"><i class="fas fa-user-plus"></i> Add Employee</button>
            <button type="button" id="editEmployeeButton" class="glossy-button text-orange" disabled><i class="fas fa-user-edit"></i> Edit Employee</button>
            <button type="button" id="deleteEmployeeButton" class="glossy-button text-red" disabled><i class="fas fa-user-times"></i> Delete Employee</button>
        </div>
        <h4 style="text-align: left; color: #6c757d; margin-bottom: 10px; font-size: 0.9em;">To Edit or Delete: First select a row from the table below.</h4>

        <div class="report-display-area" style="padding-top: 10px;">
            <div id="reportOutput_employees" class="table-container report-table-container">
                <table class="report-table" id="employeesTable">
                    <thead><tr><th>ID</th><th>First Name</th><th>Last Name</th><th>Department</th><th>Schedule</th><th>Supervisor</th><th>Permissions</th><th>Email</th><th>Hire Date</th><th>Work Sched.</th><th>Wage Type</th><th>Wage</th></tr></thead>
                    <tbody><%= employeeRowsHtml %></tbody>
                </table>
            </div>
        </div>
    </div>

    <%-- Modals --%>
    <div id="wizardGenericModal" class="modal"><div class="modal-content"><span class="close" id="closeWizardGenericModal">&times;</span><h2 id="wizardGenericModalTitle"></h2><div id="wizardGenericModalTextContainer"><p id="wizardGenericModalText1"></p><p id="wizardGenericModalText2"></p></div><div class="button-row" id="wizardGenericModalButtonRow"></div></div></div>
    <%@ include file="/WEB-INF/includes/employees-modals.jspf" %>
    <div id="notificationModalGeneral" class="modal"><div class="modal-content" style="max-width: 480px;"><span class="close">&times;</span><h2 id="notificationModalGeneralTitle"></h2><p id="notificationModalGeneralMessage"></p><div class="button-row"><button type="button" id="okButtonNotificationModalGeneral" class="glossy-button text-blue">OK</button></div></div></div>

    <script>
        window.appRootPath = "<%= request.getContextPath() %>";
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= escapeForJavaScriptString(currentWizardStepForPage_JSP) %>";
        window.adminUserData = JSON.parse('<%= adminUserDataJson %>');
        window.departmentsData = JSON.parse('<%= departmentsJson %>');
        window.schedulesData = JSON.parse('<%= schedulesJson %>');
        window.accrualPoliciesData = JSON.parse('<%= accrualPoliciesJson %>');
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="<%= request.getContextPath() %>/js/employees.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>
