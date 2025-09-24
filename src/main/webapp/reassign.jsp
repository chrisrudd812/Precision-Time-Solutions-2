<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="timeclock.reports.ShowReports" %> <%-- Assuming this class has the necessary static methods --%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%!
    // Helper to escape for JavaScript string literals within JSP
    private String escapeForJS(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n");
    }
%>

<%
    HttpSession reassignSession = request.getSession(false);
    Integer tenantId = null;
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage"); // From servlet GET if auth failed

    String initialReassignTypeFromJSP = (String) request.getAttribute("type"); // From servlet GET or POST forward
    if (initialReassignTypeFromJSP == null || initialReassignTypeFromJSP.trim().isEmpty()) {
        initialReassignTypeFromJSP = request.getParameter("type"); // From direct navbar link
    }
    if (initialReassignTypeFromJSP == null || initialReassignTypeFromJSP.trim().isEmpty()) {
        initialReassignTypeFromJSP = "department"; // Default
    }

    String successMessage = (String) request.getAttribute("successMessage");
    String errorMessageFromServlet = (String) request.getAttribute("errorMessage");

    if (reassignSession != null) {
        Object tenantIdObj = reassignSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) {
            tenantId = (Integer) tenantIdObj;
        }
        // Permission check already handled by servlet's doGet for this simplified model
    } else {
        // Should have been caught by servlet's doGet, but as a fallback:
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    if (tenantId == null || tenantId <= 0) {
        if (pageLoadErrorMessage == null) pageLoadErrorMessage = "Tenant information missing or invalid. Cannot load data.";
    }

    // Data lists fetched directly in JSP using helper methods (like employees.jsp)
    List<Map<String, String>> departmentsData = new ArrayList<>();
    List<Map<String, String>> schedulesData = new ArrayList<>();
    List<Map<String, String>> accrualPoliciesData = new ArrayList<>();
    List<String> supervisorNamesData = new ArrayList<>(); // ShowReports.getSupervisorsForTenant returns List<String>

    if (tenantId != null && tenantId > 0 && pageLoadErrorMessage == null) {
        try {
            // IMPORTANT: These methods are assumed to exist in ShowReports or equivalent classes
            // and return List<Map<String, String>> with a "name" key, or List<String> for supervisors.
            // This mimics how employees.jsp fetches its dropdown data.
            departmentsData = ShowReports.getDepartmentsForTenant(tenantId); // As used in employees.jsp
            schedulesData = ShowReports.getSchedulesForTenant(tenantId);     // As used in employees.jsp
            accrualPoliciesData = ShowReports.getAccrualPoliciesForTenant(tenantId); // As used in employees.jsp
            supervisorNamesData = ShowReports.getSupervisorsForTenant(tenantId); // As used in navbar.jspf
        } catch (Exception e) {
            if (pageLoadErrorMessage == null && errorMessageFromServlet == null) {
                 pageLoadErrorMessage = "Error loading data for reassignment dropdowns (JSP): " + e.getMessage();
            }
            e.printStackTrace(); 
        }
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reassign Employees</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    
    <style>
    
    body.reassign-page .parent-container {
    max-width: 55%;
    min-height: 0;
    flex-grow: 0;
}
        .reassign-container { padding: 20px; max-width: 800px; max-height: 60vh; margin: 20px auto; background-color:#fff; border-radius:8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .form-row { display: flex; flex-wrap: wrap; justify-content: space-between; margin-bottom: 20px; align-items: flex-end; gap: 15px;}
        .form-row > .form-item { flex: 1; min-width: 200px; }
        .form-item label { display: block; margin-bottom: 6px; font-weight: 600; color: #333; }
        .form-item select, .form-item button {
            width: 100%;
            padding: 10px 12px;
            border-radius: 5px;
            border: 1px solid #ccc;
            box-sizing: border-box;
            font-size: 0.95em;
        }
        .form-item select { background-color: #f9f9f9; }
        .form-item button { background-color: #007bff; color: white; cursor: pointer; font-weight: 500; transition: background-color 0.2s ease-in-out; }
        .form-item button:hover { background-color: #0056b3; }
        .form-item button:disabled { background-color: #b0c4de; color:#e0e0e0; cursor: not-allowed; border-color: #adb5bd;}
        .notification-area { margin-bottom:20px; padding: 12px 15px; border-radius: 5px; font-size: 0.95em; display: flex; align-items: center; }
        .notification-area .fas { margin-right: 10px; font-size: 1.2em; }
        .success { background-color: #d1e7dd; color: #0f5132; border: 1px solid #badbcc;}
        .error { background-color: #f8d7da; color: #842029; border: 1px solid #f5c2c7;}
        #validationMessage.error { margin-top: 5px; padding: 8px; font-size: 0.9em; }
        h1 { text-align: center; color: #005A9C; margin-bottom: 25px; border-bottom: 1px solid #eee; padding-bottom: 15px; }
    </style>
</head>
<body class="reassign-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reassign-container">
        <h1><i class="fas fa-exchange-alt"></i> Reassign Employees</h1>

        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %>
            <div class="notification-area error"><i class="fas fa-times-circle"></i> <%= pageLoadErrorMessage %></div>
        <% } %>
        <% if (successMessage != null && !successMessage.isEmpty()) { %>
            <div class="notification-area success"><i class="fas fa-check-circle"></i> <%= successMessage %></div>
        <% } %>
        <% if (errorMessageFromServlet != null && !errorMessageFromServlet.isEmpty()) { %>
            <div class="notification-area error"><i class="fas fa-exclamation-triangle"></i> <%= errorMessageFromServlet %></div>
        <% } %>

        <form id="reassignForm" action="${pageContext.request.contextPath}/ReassignServlet" method="POST">
            <div class="form-item" style="margin-bottom: 25px;">
                <label for="reassignType">Reassign Employees Based On:</label>
                <select id="reassignType" name="reassignType">
                    <option value="department">Department</option>
                    <option value="schedule">Schedule</option>
                    <option value="accrual_policy">Accrual Policy</option>
                    <option value="supervisor">Supervisor</option>
                </select>
            </div>

            <div class="form-row">
                <div class="form-item">
                    <label for="fromValue">From:</label>
                    <select id="fromValue" name="fromValue" required>
                        <option value="">-- Select Current --</option>
                        <%-- Options will be populated by JavaScript --%>
                    </select>
                </div>
                <div class="form-item">
                    <label for="toValue">To:</label>
                    <select id="toValue" name="toValue" required>
                        <option value="">-- Select New --</option>
                        <%-- Options will be populated by JavaScript --%>
                    </select>
                </div>
                <div class="form-item">
                    <button type="submit" id="applyButton"><i class="fas fa-save"></i> Apply Reassignment</button>
                </div>
            </div>
            <div id="validationMessage" class="error" style="display:none;"></div>
        </form>
    </div>

    <script>
        // Manually construct the allData JavaScript object using data fetched by JSP scriptlets
        const allData = {
            department: [
                <% if (departmentsData != null) { for (int i = 0; i < departmentsData.size(); i++) { Map<String, String> dept = departmentsData.get(i); if (dept != null && dept.get("name") != null) { %>
                    { name: "<%= escapeForJS(dept.get("name")) %>" }<%= (i < departmentsData.size() - 1) ? "," : "" %>
                <% }}} %>
            ],
            schedule: [
                <% if (schedulesData != null) { for (int i = 0; i < schedulesData.size(); i++) { Map<String, String> sched = schedulesData.get(i); if (sched != null && sched.get("name") != null) { %>
                    { name: "<%= escapeForJS(sched.get("name")) %>" }<%= (i < schedulesData.size() - 1) ? "," : "" %>
                <% }}} %>
            ],
            accrual_policy: [
                <% if (accrualPoliciesData != null) { for (int i = 0; i < accrualPoliciesData.size(); i++) { Map<String, String> policy = accrualPoliciesData.get(i); if (policy != null && policy.get("name") != null) { %>
                    { name: "<%= escapeForJS(policy.get("name")) %>" }<%= (i < accrualPoliciesData.size() - 1) ? "," : "" %>
                <% }}} %>
            ],
            supervisor: [
                <% if (supervisorNamesData != null) { for (int i = 0; i < supervisorNamesData.size(); i++) { String supervisorName = supervisorNamesData.get(i); if (supervisorName != null && !supervisorName.trim().isEmpty()) { %>
                    { name: "<%= escapeForJS(supervisorName) %>" }<%= (i < supervisorNamesData.size() - 1) ? "," : "" %>
                <% }}} %>
            ]
        };
        const initialReassignTypeJS = "<%= escapeForJS(initialReassignTypeFromJSP) %>";

        // For debugging:
        console.log("reassign.jsp: initialReassignTypeJS =", initialReassignTypeJS);
        // To avoid overly long console output, just log presence or counts
        console.log("reassign.jsp: allData.department count =", allData.department ? allData.department.length : 0);
        console.log("reassign.jsp: allData.schedule count =", allData.schedule ? allData.schedule.length : 0);
        console.log("reassign.jsp: allData.accrual_policy count =", allData.accrual_policy ? allData.accrual_policy.length : 0);
        console.log("reassign.jsp: allData.supervisor count =", allData.supervisor ? allData.supervisor.length : 0);
    </script>
    <script src="${pageContext.request.contextPath}/js/reassign.js?v=<%= System.currentTimeMillis() %>"></script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>