<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.punches.ShowPunches" %> <%-- For getActiveEmployeesForDropdown --%>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>

<%! private static final Logger jspLogger = Logger.getLogger("manageAccruedHours_jsp"); %>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    if (currentSession != null) {
        Object tenantIdObj = currentSession.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) { tenantId = (Integer) tenantIdObj; }
        // Potentially fetch UserPermissions if admin-only page
    }

    if (tenantId == null || tenantId <= 0) {
        // Consider adding an admin check here if this page is admin-only
        response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Session expired or invalid tenant. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    List<Map<String, Object>> activeEmployeeList = new ArrayList<>();
    String employeeListError = null;
    try {
        activeEmployeeList = ShowPunches.getActiveEmployeesForDropdown(tenantId);
        if (activeEmployeeList == null) activeEmployeeList = new ArrayList<>();
    } catch (Exception e) {
        employeeListError = "Error loading employee list: " + e.getMessage();
        jspLogger.severe(employeeListError + " for TenantID: " + tenantId);
    }
    String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Manage Accrued Hours</title>
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>"> {/* Base styles, modals, buttons */}
    <link rel="stylesheet" href="css/manageAccruedHours.css?v=<%= System.currentTimeMillis() %>"> {/* Page-specific styles */}
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
</head>
<body class="reports-page"> 
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reports-container">
        <h1>Manage Accrued Hours</h1>
        <p style="text-align:center; margin-bottom:20px;">Use the button below to directly adjust accrual balances for employees.</p>
        
        <div style="text-align: center; margin-bottom: 30px;">
            <button type="button" id="openManageAccrualModalBtn" class="glossy-button text-blue" style="padding: 10px 20px; font-size: 1.1em;">
                <i class="fas fa-plus-circle"></i> Adjust Accrual Balance
            </button>
        </div>
        
        <div id="pageNotificationArea" style="text-align:center;">
            <%-- Messages from redirects or JS can be placed here or use modal --%>
             <% String successMessage = request.getParameter("message");
                String errorMessage = request.getParameter("error");
                if (successMessage != null && !successMessage.isEmpty()) { %>
                <div class="page-message success-message"><%= successMessage.replace("<","&lt;") %></div>
             <% } else if (errorMessage != null && !errorMessage.isEmpty()) { %>
                <div class="page-message error-message"><%= errorMessage.replace("<","&lt;") %></div>
             <% } %>
        </div>

    </div>

    <%-- Modal for Adding/Adjusting Accrued Hours --%>
    <div id="manageAccruedHoursModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeManageAccrualModalBtn">&times;</span>
            <h2 id="manageAccrualModalTitle">Add Accrued Hours</h2>
            <p class="modal-description">
                This action directly adds to an employee's (or all employees') current accrued
                hours balance. It does not change or interact with any existing accrual policies.
            </p>

            <form id="manageAccruedHoursForm" action="AccrualServlet" method="post">
                <input type="hidden" name="action" value="addAccruedHours">
                <input type="hidden" id="isGlobalAddInput" name="isGlobalAdd" value="false"> {/* JS will manage this */}

                <div class="form-item" id="employeeSelectContainerManage">
                    <label for="targetEmployeeIdManage">Employee:</label>
                    <select id="targetEmployeeIdManage" name="targetEmployeeId" <%= (activeEmployeeList.isEmpty() ? "disabled" : "") %>>
                        <option value="">-- Select Employee --</option>
                        <% for (Map<String, Object> emp : activeEmployeeList) {
                            Integer empGlobalEid = (Integer) emp.get("eid");
                            String displayName = (String) emp.get("displayName");
                        %>
                            <option value="<%= empGlobalEid %>"><%= displayName %></option>
                        <% } %>
                        <% if (activeEmployeeList.isEmpty() && employeeListError == null) { %>
                            <option value="" disabled>No active employees found</option>
                        <% } else if (employeeListError != null) { %>
                             <option value="" disabled>Error loading employees</option>
                        <% } %>
                    </select>
                </div>
                
                <div class="form-item">
                    <label for="accrualTypeColumnManage">Accrual Type: <span class="required-asterisk">*</span></label>
                    <select id="accrualTypeColumnManage" name="accrualTypeColumn" required>
                        <option value="">-- Select Type --</option>
                        <option value="VACATION_HOURS">Vacation Time</option>
                        <option value="SICK_HOURS">Sick Time</option>
                        <option value="PERSONAL_HOURS">Personal Time</option>
                    </select>
                 </div>

                 <div class="form-item">
                     <label for="accrualHoursManage">Hours to Add: <span class="required-asterisk">*</span></label>
                     <input type="number" id="accrualHoursManage" name="accrualHours" step="0.01" min="0.01" required placeholder="e.g., 8.00">
                 </div>

                 <div class="form-item">
                    <label for="accrualDateManage">Date of Accrual: <span class="required-asterisk">*</span></label>
                    <input type="date" id="accrualDateManage" name="accrualDate" value="<%= todayDate %>" required>
                </div>

                <div class="form-item apply-global-checkbox">
                    <input type="checkbox" id="applyToAllCheckbox" name="applyToAll" value="true">
                    <label for="applyToAllCheckbox">Apply to All Active Employees</label>
                </div>

                <div class="button-row">
                    <button type="button" id="cancelManageAccrualBtn" class="glossy-button text-red">
                         <i class="fas fa-times"></i> Cancel
                    </button>
                    <button type="submit" class="glossy-button text-green">
                        <i class="fas fa-plus-circle"></i> Add Hours
                    </button>
                </div>
            </form>
        </div>
    </div>

    <%-- Standard Notification Modal (if not already in common-scripts or navbar) --%>
    <div id="notificationModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeNotificationModal">&times;</span>
            <h2 id="notificationModalTitleArea">Notification</h2>
            <p id="notificationMessageArea"></p>
            <div class="button-row" style="justify-content: center;">
                <button type="button" id="okNotificationButton" class="glossy-button text-blue">OK</button>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="js/manageAccruedHours.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>