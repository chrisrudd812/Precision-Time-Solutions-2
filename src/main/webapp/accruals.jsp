<%@page import="timeclock.reports.ShowReports"%>
<%@page import="timeclock.accruals.ShowAccruals"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>

<%!
    // Helper function to prevent XSS attacks by escaping HTML characters.
    private String escapeJspHtml(String input) { if (input == null) return ""; return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    // Helper function to safely embed Java strings into JavaScript code.
    private String escapeForJavaScriptString(String input) { if (input == null) return ""; return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("/", "\\/"); }
%>
<%
    // --- SESSION AND PERMISSION VALIDATION ---
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String pageLevelError = request.getParameter("error"); 
    String pageLevelSuccess = request.getParameter("message"); 
    boolean isReopenModalRequest = request.getParameter("reopenModal") != null;
    
    // --- WIZARD SETUP LOGIC ---
    boolean inSetupWizardMode_JSP = false;
    String currentWizardStepForPage_JSP = null; 
    String companyNameSignup_Accruals = "Your Company"; // Default company name

    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        
        Object companyNameObj = currentSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_Accruals = (String) companyNameObj;
        }

        // Check if the setup wizard is active in the session.
        if (Boolean.TRUE.equals(currentSession.getAttribute("startSetupWizard"))) {
            inSetupWizardMode_JSP = true;
            String sessionWizardStep = (String) currentSession.getAttribute("wizardStep");
            String wizardStepFromParam = request.getParameter("step");

            // Prioritize the step from the URL parameter, updating the session if needed.
            if (wizardStepFromParam != null && !wizardStepFromParam.trim().isEmpty()) {
                currentWizardStepForPage_JSP = wizardStepFromParam.trim();
                if (!currentWizardStepForPage_JSP.equals(sessionWizardStep)) {
                    currentSession.setAttribute("wizardStep", currentWizardStepForPage_JSP);
                }
            } else {
                currentWizardStepForPage_JSP = sessionWizardStep;
            }
        }
        
        // Retrieve and clear any success/error messages stored in the session.
        if (pageLevelSuccess == null && currentSession.getAttribute("successMessage") != null) {
            pageLevelSuccess = (String) currentSession.getAttribute("successMessage");
            currentSession.removeAttribute("successMessage");
        }
        if (pageLevelError == null && currentSession.getAttribute("errorMessage") != null) {
            pageLevelError = (String) currentSession.getAttribute("errorMessage");
            currentSession.removeAttribute("errorMessage");
        }

        // Verify user has Administrator permissions.
        String userPermissions = (String) currentSession.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            pageLevelError = "Access Denied.";
        }
    } else {
        // If no session, redirect to login page.
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
        return; 
    }
   
    // --- DATA FETCHING ---
    List<Map<String, String>> allAccrualPoliciesForDropdown = new ArrayList<>();
    String accrualRowsHtml = "";
    String dataFetchError = null;

    if (tenantId != null && tenantId > 0) {
        try {
            allAccrualPoliciesForDropdown = ShowReports.getAccrualPoliciesForTenant(tenantId);
            accrualRowsHtml = ShowAccruals.showAccruals(tenantId);
        } catch (Exception e) {
            dataFetchError = "Could not load page data: " + e.getMessage();
        }
    } else {
        dataFetchError = "Invalid session or tenant context.";
    }
    
    // Prepare messages for display in the table if there's an error or no data.
    if (dataFetchError != null) {
        accrualRowsHtml = "<tr><td colspan='4' class='report-error-row'>" + escapeJspHtml(dataFetchError) + "</td></tr>";
    } else if ((accrualRowsHtml == null || accrualRowsHtml.isEmpty())) {
        accrualRowsHtml = "<tr><td colspan='4' class='report-message-row'>No accrual policies defined yet.</td></tr>";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Manage Accrual Policies<% if(inSetupWizardMode_JSP) { %> - Company Setup<% } %></title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%= System.currentTimeMillis() %>"> 
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/accruals.css?v=<%= System.currentTimeMillis() %>"> 
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; }
        .wizard-header h2 { margin-top:0; margin-bottom: 5px; font-weight: 500;}
        .wizard-header p { margin-bottom:0; font-size:0.9em; opacity: 0.9;}
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; }
        .modal.modal-visible { display: flex !important; }
        .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 600px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left; }
        .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee; text-align:center;}
        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; }
        .page-message { padding: 10px 15px; margin: 0 auto 20px auto; border-radius: 4px; text-align: center; }
        .success-message { background-color: #d4edda; color: #155724; border:1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border:1px solid #f5c6cb; }
    </style>
</head>
<body class="reports-page">
    <%-- Conditionally hide the standard navbar and show a wizard-specific header --%>
    <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Accrual Policies for <%= escapeJspHtml(companyNameSignup_Accruals) %></h2>
            <p>Configure time off policies (Vacation, Sick, etc.). You can assign them to employees later.</p>
        </div>
    <% } %>

    <div class="parent-container reports-container">
        <h1>Manage Accrual Policies <% if(inSetupWizardMode_JSP) { %> <span style="font-size: 0.8em; color: #555;">(Setup)</span> <% } %></h1>

        <%-- Display any success or error messages passed from the servlet --%>
        <% if (pageLevelSuccess != null && !pageLevelSuccess.isEmpty()) { %><div class="page-message success-message"><%= escapeJspHtml(pageLevelSuccess) %></div><% } %>
        <% if (pageLevelError != null && !pageLevelError.isEmpty() && !isReopenModalRequest) { %><div class="page-message error-message"><%= escapeJspHtml(pageLevelError) %></div><% } %>
        
        <div id="button-container" class="main-action-buttons">
            <button type="button" id="btnAddPolicy" class="glossy-button text-green"><i class="fas fa-plus"></i> Add Accrual Policy</button>
            <button type="button" id="btnEditPolicy" class="glossy-button text-orange" disabled><i class="fas fa-edit"></i> Edit Accrual Policy</button>
            <button type="button" id="btnDeletePolicy" class="glossy-button text-red" disabled><i class="fas fa-trash-alt"></i> Delete Accrual Policy</button>
        </div>
        <h4 style="text-align: left; color: #6c757d; margin-bottom: 10px; font-size: 0.9em;">To Edit or Delete: First select a row from the table below.</h4>
        
        <div class="report-display-area" style="padding-top: 10px;">
            <div class="table-container report-table-container">
                <table class="report-table" id="accrualsTable">
                    <thead><tr><th>Policy Name</th><th>Annual Vacation Days</th><th>Annual Sick Days</th><th>Annual Personal Days</th></tr></thead>
                    <tbody><%= accrualRowsHtml %></tbody>
                </table>
            </div>
        </div>
        
        <%-- Hidden form used for deleting accrual policies --%>
        <form action="<%= request.getContextPath() %>/AddAndDeleteAccrualPoliciesServlet" method="POST" id="deleteAccrualForm" style="display:none;"><input type="hidden" name="action" value="deleteAndReassignAccrualPolicy"><input type="hidden" name="hiddenAccrualNameToDelete" id="hiddenDeleteAccrualName" value=""><input type="hidden" id="targetAccrualPolicyForReassignment" name="targetAccrualPolicyForReassignment" value=""></form>
    </div>

    <%-- Modals --%>
    
    <!-- **FIX START**: Added the generic wizard modal HTML. -->
    <div id="wizardGenericModal" class="modal">
        <div class="modal-content" style="max-width: 600px;">
            <span class="close" id="closeWizardGenericModal" data-close-modal-id="wizardGenericModal">&times;</span>
            <h2 id="wizardGenericModalTitle"></h2>
            <p id="wizardGenericModalText1" style="padding: 10px 15px; font-size: 1.1em;"></p>
            <p id="wizardGenericModalText2" style="padding: 0px 15px 15px; font-size: 1em; color: #555;"></p>
            <div class="button-row" id="wizardGenericModalButtonRow" style="justify-content: center; padding-top: 15px; border-top: 1px solid #eee;">
                <!-- Buttons will be injected by JS -->
            </div>
        </div>
    </div>
    <!-- **FIX END** -->

    <%-- Include the modals for Add, Edit, and Delete from a separate file --%>
    <%@ include file="/WEB-INF/includes/accruals-modals.jspf" %>
    
    <%-- General purpose notification modal --%>
    <div id="notificationModalGeneral" class="modal" style="z-index: 10005;"><div class="modal-content" style="max-width: 480px;"><span class="close" data-close-modal-id="notificationModalGeneral">&times;</span><h2 id="notificationModalGeneralTitle">Notification</h2><p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center; line-height:1.6;"></p><div class="button-row" style="justify-content: center;"><button type="button" id="okButtonNotificationModalGeneral" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue">OK</button></div></div></div>

    <script type="text/javascript">
        // Pass server-side data to the client-side JavaScript.
        window.appRootPath = "<%= request.getContextPath() %>";
        window.allAvailableAccrualPoliciesForReassign = [ <% if (allAccrualPoliciesForDropdown != null) { boolean firstPolicy = true; for (Map<String, String> policy : allAccrualPoliciesForDropdown) { if (policy != null && policy.get("name") != null) { if (!firstPolicy) { out.print(","); } %> { "name": "<%= escapeForJavaScriptString(policy.get("name")) %>" }<% firstPolicy = false; }}} %> ];
        // **FIX**: Pass wizard-related flags to JavaScript.
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= currentWizardStepForPage_JSP != null ? escapeForJavaScriptString(currentWizardStepForPage_JSP) : "" %>";
        window.COMPANY_NAME_SIGNUP_JS_ACCRUALS = "<%= escapeForJavaScriptString(companyNameSignup_Accruals) %>";
    </script>
    
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="<%= request.getContextPath() %>/js/accruals.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>
