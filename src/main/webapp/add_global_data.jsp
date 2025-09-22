<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    String successMessage = request.getParameter("message");
    String errorMessage = request.getParameter("error");
    String payPeriodStart = request.getParameter("startDate");
    String payPeriodEnd = request.getParameter("endDate");
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
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container">
        <h1><i class="fas fa-globe-americas"></i> Add Global Hours</h1>

        <div class="content-display-area">
            <div class="form-container">
                <p class="form-instructions">
                    Add non-clocked time (like Holiday, Sick Day) for ALL active employees.
                </p>

                <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
                    <div class="page-message error-message" style="margin-bottom: 1.5rem;"><%= errorMessage %></div>
                <% } %>

                <form id="addGlobalHoursForm" action="AddEditAndDeletePunchesServlet" method="post">
                    <input type="hidden" name="action" value="addGlobalHoursSubmit">

                    <div class="form-item">
                        <label for="addHoursDate">Date <span class="required-asterisk">*</span></label>
                        <%-- MODIFIED: Removed the value attribute to leave the field empty --%>
                        <input type="date" id="addHoursDate" name="addHoursDate" required>
                    </div>

                    <div class="form-item">
                        <label for="addHoursTotal">Hours <span class="required-asterisk">*</span></label>
                        <input type="number" id="addHoursTotal" name="addHoursTotal" step="0.01" min="0.01" max="24" required placeholder="e.g., 8.0" value="8">
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
            
            // Set pay period date restrictions if available
            <% if (payPeriodStart != null && payPeriodEnd != null) { %>
            if (dateInput) {
                dateInput.min = '<%= payPeriodStart %>';
                dateInput.max = '<%= payPeriodEnd %>';
            }
            <% } %>
            
            // Focus the date field on page load
            if (dateInput) dateInput.focus();
            
            <% if (successMessage != null && !successMessage.isEmpty()) { %>
                showPageNotification('<%= successMessage.replace("'", "\\'") %>', false, function() {
                    // This is a callback function that runs after the modal is closed
                    window.location.href = window.location.pathname;
                }, "Success");
            <% } %>
        });
    </script>
</body>
</html>