<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.punches.ShowPunches" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspLogger = Logger.getLogger("add_global_data_jsp");
    
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
%>

<%
    // Check if navbar should be hidden
    String hideNavParam = request.getParameter("hideNav");
    boolean hideNavbar = "true".equalsIgnoreCase(hideNavParam);
    
    // Session validation
    HttpSession globalSession = request.getSession(false);
    Integer tenantId = null;
    String userPermissions = null;
    Integer adminEidForLog = null;

    if (globalSession != null) {
        Object tenantIdObj = globalSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        Object permObj = globalSession.getAttribute("Permissions");
        if (permObj instanceof String) { userPermissions = (String) permObj; }
        Object eidObj = globalSession.getAttribute("EID");
        if (eidObj instanceof Integer) { adminEidForLog = (Integer) eidObj; }
    }

    if (tenantId == null || tenantId <= 0 || !"Administrator".equalsIgnoreCase(userPermissions)) {
        if(globalSession != null) globalSession.invalidate();
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Access Denied. Administrator privileges required.", StandardCharsets.UTF_8.name()));
        return;
    }

    String successMessage = request.getParameter("message");
    String errorMessage = request.getParameter("error");
    
    // Get current pay period information
    LocalDate currentPayPeriodStartDate = null;
    LocalDate currentPayPeriodEndDate = null;
    String payPeriodDisplay = "Pay Period Not Configured";
    DateTimeFormatter friendlyDateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    
    try {
        Map<String, LocalDate> currentPeriod = ShowPunches.getCurrentPayPeriodInfo(tenantId);
        if (currentPeriod != null && currentPeriod.get("startDate") != null && currentPeriod.get("endDate") != null) {
            currentPayPeriodStartDate = currentPeriod.get("startDate");
            currentPayPeriodEndDate = currentPeriod.get("endDate");
            payPeriodDisplay = currentPayPeriodStartDate.format(friendlyDateFormatter) + " - " + currentPayPeriodEndDate.format(friendlyDateFormatter);
        } else {
            errorMessage = "Current pay period is not configured. Please set it in Company Settings.";
        }
    } catch (Exception e) {
        errorMessage = "Error loading current pay period information.";
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Add Global Hours</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/add_global_data.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="reports-page<%= hideNavbar ? " no-navbar" : "" %>">
    <% if (!hideNavbar) { %>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="parent-container reports-container">
        <h1><i class="fas fa-globe-americas"></i> Add Global Hours</h1>
        
        <h2 id="payPeriodHeader">Pay Period: <%= escapeHtml(payPeriodDisplay) %></h2>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
            <div class="page-message error-message"><%= escapeHtml(errorMessage) %></div>
        <% } %>

        <div class="content-display-area">
            <div class="form-container">
                <p class="form-instructions">
                    Add non-clocked time (like Holiday, Sick Day) for ALL active employees in the current pay period.
                </p>

                <form id="addGlobalHoursForm" action="AddEditAndDeletePunchesServlet" method="post">
                    <input type="hidden" name="action" value="addGlobalHoursSubmit">
                    <% if (hideNavbar) { %><input type="hidden" name="hideNav" value="true"><% } %>

                    <div class="form-item">
                        <label for="addHoursDate">Date <span class="required-asterisk">*</span></label>
                        <input type="date" id="addHoursDate" name="addHoursDate" required
                               <% if (currentPayPeriodStartDate != null && currentPayPeriodEndDate != null) { %>
                               min="<%= currentPayPeriodStartDate.toString() %>" 
                               max="<%= currentPayPeriodEndDate.toString() %>"
                               <% } %>>
                    </div>

                    <div class="form-item">
                        <label for="addHoursTotal">Hours <span class="required-asterisk">*</span></label>
                        <input type="number" id="addHoursTotal" name="addHoursTotal" step="0.01" min="0.01" max="160" required placeholder="e.g., 8.0" value="8">
                    </div>

                    <div class="form-item">
                        <label for="addHoursPunchTypeDropDown">Reason / Punch Type <span class="required-asterisk">*</span></label>
                        <select id="addHoursPunchTypeDropDown" name="addHoursPunchTypeDropDown" required>
                            <option value="Holiday">Holiday</option>
                            <option value="Vacation">Vacation</option>
                            <option value="Sick">Sick</option>
                            <option value="Personal">Personal</option>
                            <option value="Other">Other</option>
                        </select>
                    </div>

                    <button type="submit" class="glossy-button text-green submit-button">
                        <i class="fas fa-plus-circle"></i> Add Global Entry
                    </button>
                </form>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>

    <script>
        document.addEventListener('DOMContentLoaded', function() {
            const dateInput = document.getElementById('addHoursDate');
            
            // Focus the date field on page load
            if (dateInput) dateInput.focus();
            
            <% if (successMessage != null && !successMessage.isEmpty()) { %>
                showPageNotification('<%= escapeHtml(successMessage).replace("'", "\\'") %>', false, function() {
                    const params = new URLSearchParams();
                    <% if (hideNavbar) { %>params.append('hideNav', 'true');<% } %>
                    window.location.href = window.location.pathname + (params.toString() ? '?' + params.toString() : '');
                }, "Success");
            <% } %>
        });
    </script>
</body>
</html>