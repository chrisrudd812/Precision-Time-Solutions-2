<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="timeclock.departments.ShowDepartments" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="timeclock.reports.ShowReports" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%!
    // Helper to escape HTML characters to prevent XSS.
    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    // Helper to safely embed Java strings into JavaScript.
    private String escapeForJS(String input) { 
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("/", "\\/");
    }
    
    // Helper to build a JSON array string from a List of Maps.
    private String buildJsonArray(List<Map<String, String>> dataList) {
        StringBuilder json = new StringBuilder("[");
        if (dataList != null) {
            for (int i = 0; i < dataList.size(); i++) {
                Map<String, String> item = dataList.get(i);
                json.append("{");
                int keyCount = 0;
                for (Map.Entry<String, String> entry : item.entrySet()) {
                    json.append("\"").append(escapeForJS(entry.getKey())).append("\":\"").append(escapeForJS(entry.getValue())).append("\"");
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
    // --- SESSION AND PERMISSION VALIDATION ---
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String userPermissions = "User"; 
    String pageLevelError = request.getParameter("error");
    String pageLevelSuccess = request.getParameter("message");
    boolean isReopenModalRequest = request.getParameter("reopenModal") != null;
    
    // --- WIZARD SETUP LOGIC ---
    boolean inSetupWizardMode_JSP = false;
    String currentWizardStepForPage_JSP = null;
    String companyNameSignup_JSP = "Your Company"; 
    boolean departmentJustAddedInWizard_JSP = "true".equalsIgnoreCase(request.getParameter("deptAdded"));

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if(tenantIdObj instanceof Integer) tenantId = (Integer) tenantIdObj;
        
        userPermissions = (String) currentSession.getAttribute("Permissions");

        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("step");
            currentWizardStepForPage_JSP = (wizardStepFromParam != null && !wizardStepFromParam.isEmpty()) ? wizardStepFromParam : sessionWizardStep;
            if(currentWizardStepForPage_JSP != null && !currentWizardStepForPage_JSP.equals(sessionWizardStep)) {
                currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
            }
        }
        
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_JSP = (String) companyNameObj;
        }
    }

    if (tenantId == null) {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name()));
        return;
    }
    if (!"Administrator".equalsIgnoreCase(userPermissions)) {
        pageLevelError = "Access Denied.";
    }

    // --- DATA FETCHING ---
    List<Map<String, String>> allDeptsForReassignDropdown = (tenantId != null) ? ShowReports.getDepartmentsForTenant(tenantId) : new ArrayList<>();
    String departmentRowsHtml = (tenantId != null) ? ShowDepartments.showDepartments(tenantId) : "<tr><td colspan='3' class='report-error-row'>Invalid session.</td></tr>";
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <title>Department Management<% if (inSetupWizardMode_JSP) { %> - Company Setup<% } %></title>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/departments.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="reports-page">
    <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Departments for <%= escapeJspHtml(companyNameSignup_JSP) %></h2>
            <p>Define the departments within your company. You can add some now or skip this step.</p>
        </div>
    <% } %>

    <div class="parent-container reports-container">
        <h1>Manage Departments<% if (inSetupWizardMode_JSP) { %> <span style="font-size:0.8em; color:#555;">(Setup)</span><% } %></h1>
        
        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message"><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && !isReopenModalRequest) { %><div class="page-message error-message"><%= escapeJspHtml(pageLevelError) %></div><% } %>
        
        <div id="button-container" class="main-action-buttons">
            <button type="button" id="addDepartmentButton" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add Department</button>
            <button type="button" id="editDepartmentButton" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Department</button>
            <button type="button" id="deleteDepartmentButton" class="glossy-button text-red" disabled><i class="fas fa-trash-alt"></i> Delete Department</button>
        </div>
        <h4 style="text-align: left; color: #6c757d; margin-bottom: 10px; font-size: 0.9em;">To Edit or Delete: Select a row from the table.</h4>
        <div class="table-container report-table-container department-table-container">
            <table id="departmentsTable"
       class="report-table sortable"
       data-initial-sort-column="0"
       data-initial-sort-direction="asc">
    <thead>
        <tr>
            <th class="sortable" data-sort-type="string">Name</th>
            <th class="sortable" data-sort-type="string">Description</th>
            <th class="sortable" data-sort-type="string">Supervisor</th>
        </tr>
    </thead>
    <tbody>
        <%= departmentRowsHtml %>
    </tbody>
</table>
        </div>
    </div>

    <%-- Modals --%>
    <div id="wizardGenericModal" class="modal"><div class="modal-content"><span class="close">&times;</span><h2 id="wizardGenericModalTitle"></h2><p id="wizardGenericModalText1"></p><p id="wizardGenericModalText2"></p><div class="button-row" id="wizardGenericModalButtonRow"></div></div></div>
    <%@ include file="/WEB-INF/includes/departments-modals.jspf" %>
    <div id="notificationModalGeneral" class="modal"><div class="modal-content" style="max-width:480px;"><span class="close">&times;</span><h2 id="notificationModalGeneralTitle"></h2><p id="notificationModalGeneralMessage"></p><div class="button-row"><button type="button" id="okButtonNotificationModalGeneral" class="glossy-button text-blue">OK</button></div></div></div>

    <script>
        // --- Page-specific data from JSP ---
        window.appRootPath = "<%= request.getContextPath() %>";
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= escapeForJS(currentWizardStepForPage_JSP) %>";
        window.itemJustAdded_Page = <%= departmentJustAddedInWizard_JSP %>;
        window.companyName = "<%= escapeForJS(companyNameSignup_JSP) %>";
        window.allAvailableDepartmentsForReassign = JSON.parse('<%= buildJsonArray(allDeptsForReassignDropdown) %>');
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="<%= request.getContextPath() %>/js/departments.js?v=<%= System.currentTimeMillis() %>"></script> 
</body>
</html>
