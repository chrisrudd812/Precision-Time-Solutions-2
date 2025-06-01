<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%
    String successMessage = request.getParameter("message");
    String errorMessage = request.getParameter("error");
    String pageError = null;
    if (errorMessage != null && !errorMessage.isEmpty()) {
         pageError = errorMessage;
         successMessage = null;
    }
    String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Add Global Hours</title>
    <link rel="stylesheet" href="css/navbar.css">
    <link rel="stylesheet" href="css/add_global_data.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/punches.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
</head>
<body class="add-global-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container">
        <h1>Add Global Hours</h1>
        <p class="intro-paragraph">Add non-clocked time (like Holiday, Sick Day) for ALL active employees.</p>

        <% if (successMessage != null && !successMessage.isEmpty()) { %>
            <div id="notification-bar" class="success-message"><%= successMessage %></div>
        <% } else if (pageError != null && !pageError.isEmpty()) { %>
            <div id="notification-bar" class="error-message"><%= pageError %></div>
        <% } %>

        <div class="form-container">
            <form id="addGlobalHoursForm" action="AddEditAndDeletePunchesServlet" method="post">
                <input type="hidden" name="action" value="addGlobalHoursSubmit">

                <div class="form-item">
                     <label for="addHoursDate">Date:</label>
                     <input type="date" id="addHoursDate" name="addHoursDate" value="<%= todayDate %>" required>
                 </div>

                 <div class="form-item">
                     <label for="addHoursTotal">Hours:</label>
                     <input type="number" id="addHoursTotal" name="addHoursTotal" step="0.01" min="0.01" max="24" required placeholder="e.g., 8.0"
                            value="8" autofocus>
                 </div>

                <div class="form-item">
                    <label for="addHoursPunchTypeDropDown">Reason / Punch Type:</label>
                    <select id="addHoursPunchTypeDropDown" name="addHoursPunchTypeDropDown" required>
                        <option value="Holiday">Holiday</option>
                        <option value="Vacation">Vacation</option>
                        <option value="Sick">Sick</option>
                        <option value="Personal">Personal</option>
                        <option value="Bereavement">Bereavement</option>
                        <option value="Other">Other</option>
                    </select>
                 </div>

                <div class="button-row">
                    <button type="submit" class="submit-btn">Add Global Entry</button>
                </div>
            </form>
        </div>
    </div>

    <div id="notificationModal" class="modal">
         <div class="modal-content">
             <span class="close-button" id="closeNotificationModal">&times;</span>
             <h2>Notification</h2>
             <p id="notificationMessage"></p>
             <div class="modal-footer" style="justify-content: center;">
                 <button type="button" id="okButton" class="modal-ok-button">OK</button>
             </div>
         </div>
    </div>

    <script type="text/javascript" src="js/punches.js?v=<%= System.currentTimeMillis() %>"></script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>