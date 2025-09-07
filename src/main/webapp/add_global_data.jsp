<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%
    String successMessage = request.getParameter("message");
    String errorMessage = request.getParameter("error");
    String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
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

    <div class="parent-container reports-container">
        <h1><i class="fas fa-globe-americas"></i> Add Global Hours</h1>
        <p class="intro-paragraph">Add non-clocked time (like Holiday, Sick Day) for ALL active employees.</p>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
            <div class="page-message error-message"><%= errorMessage %></div>
        <% } %>

        <div class="form-container">
            <form id="addGlobalHoursForm" action="AddEditAndDeletePunchesServlet" method="post">
                <input type="hidden" name="action" value="addGlobalHoursSubmit">

                <fieldset class="form-section">
                    <legend>Date <span class="required-asterisk">*</span></legend>
                    <input type="date" id="addHoursDate" name="addHoursDate" value="<%= todayDate %>" required>
                </fieldset>

                <fieldset class="form-section">
                    <legend>Hours <span class="required-asterisk">*</span></legend>
                    <input type="number" id="addHoursTotal" name="addHoursTotal" step="0.01" min="0.01" max="24" required placeholder="e.g., 8.0" value="8">
                </fieldset>

                <fieldset class="form-section">
                    <legend>Reason / Punch Type <span class="required-asterisk">*</span></legend>
                    <select id="addHoursPunchTypeDropDown" name="addHoursPunchTypeDropDown" required>
                        <option value="Holiday">Holiday</option>
                        <option value="Vacation">Vacation</option>
                        <option value="Sick">Sick</option>
                        <option value="Personal">Personal</option>
                        <option value="Other">Other</option>
                    </select>
                </fieldset>

                <div class="button-row">
                     <button type="submit" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add Global Entry</button>
                </div>
            </form>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>

    <%-- MODIFICATION: Removed incorrect punches.js script and added self-contained logic --%>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            // This part shows the modal if a success message is in the URL
            <% if (successMessage != null && !successMessage.isEmpty()) { %>
                showPageNotification('<%= successMessage.replace("'", "\\'") %>', false, null, "Success");
            <% } %>

            // --- Self-Contained Modal Close Logic for THIS PAGE ONLY ---
            const notificationModal = document.getElementById('notificationModalGeneral');
            if (notificationModal) {
                const okButton = document.getElementById('okButtonNotificationModalGeneral');
                const closeXButton = notificationModal.querySelector('.close'); // Standard close button

                function closeAndRefresh() {
                    // This function now handles closing the modal AND refreshing the page
                    if (typeof hideModal === 'function') {
                        hideModal(notificationModal);
                    }
                    // Use location.pathname to reload the page without the URL parameters
                    window.location.href = window.location.pathname;
                }

                if (okButton) {
                    okButton.addEventListener('click', closeAndRefresh);
                }
                if (closeXButton) {
                    closeXButton.addEventListener('click', closeAndRefresh);
                }
            }
        });
    </script>
</body>
</html>