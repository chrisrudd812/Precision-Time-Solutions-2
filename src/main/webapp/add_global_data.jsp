<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%
    // Check for redirect messages
    String successMessage = request.getParameter("message");
    String errorMessage = request.getParameter("error");
    String pageError = null; // Use this if errors can originate directly from this JSP's logic
    if (errorMessage != null && !errorMessage.isEmpty()) {
         pageError = errorMessage; // Assign error message from parameter
         successMessage = null; // Ensure success message isn't shown if there's an error
    }
    // Format today's date for the input field default value
    String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Add Global Hours</title>
    <%-- Link shared CSS (navbar) --%>
    <link rel="stylesheet" href="css/navbar.css">
    <%-- Link page-specific styles --%>
    <link rel="stylesheet" href="css/add_global_data.css?v=2"> <%-- Updated version --%>
    <%-- Removed inline <style> block --%>
</head>
<%-- Added class="add-global-page" --%>
<body class="add-global-page">
    <%-- Include Navbar --%>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %> <%-- Verify this path is correct --%>

    <div class="parent-container">
        <h1>Add Global Hours</h1>
        <%-- Added class="intro-paragraph" --%>
        <p class="intro-paragraph">Add non-clocked time (like Holiday, Sick Day) for ALL active employees.</p>

        <%-- Display Success/Error Bar --%>
        <%-- Ensure these classes match CSS (.success-message, .error-message) --%>
        <% if (successMessage != null && !successMessage.isEmpty()) { %>
            <div id="notification-bar" class="success-message"><%= successMessage %></div>
        <% } else if (pageError != null && !pageError.isEmpty()) { %>
            <div id="notification-bar" class="error-message"><%= pageError %></div>
        <% } %>

        <div class="form-container"> <%-- Wrapper for centering & styling --%>
            <form id="addGlobalHoursForm" action="AddEditAndDeletePunchesServlet" method="post">
                <input type="hidden" name="action" value="addGlobalHoursSubmit">

                <div class="form-item">
                     <label for="addHoursDate">Date:</label>
                     <input type="date" id="addHoursDate" name="addHoursDate" value="<%= todayDate %>" required>
                 </div>

                 <div class="form-item">
                     <label for="addHoursTotal">Hours:</label>
                     <%-- Added value="8" and autofocus --%>
                     <input type="number" id="addHoursTotal" name="addHoursTotal" step="0.01" min="0.01" max="24" required placeholder="e.g., 8.0"
                            value="8" autofocus>
                 </div>

                <div class="form-item">
                    <label for="addHoursPunchTypeDropDown">Reason / Punch Type:</label>
                    <%-- Limited options typically used for global adds --%>
                    <select id="addHoursPunchTypeDropDown" name="addHoursPunchTypeDropDown" required>
                        <option value="Holiday Time">Holiday Time</option>
                        <option value="Vacation Time">Vacation Time (Global)</option>
                        <option value="Sick Time">Sick Time (Global)</option>
                        <option value="Personal Time">Personal Time (Global)</option>
                        <option value="Bereavement">Bereavement</option>
                        <option value="Supervisor Override">Supervisor Override</option>
                        <option value="Other">Other</option>
                    </select>
                 </div>

                <div class="button-row">
                    <button type="submit" class="submit-btn">Add Global Entry</button>
                </div>
            </form>
        </div>

    </div> <%-- End parent-container --%>

    <%-- Include JS file here if/when needed --%>
    <%-- <script type="text/javascript" src="js/add_global_data.js?v=1"></script> --%>
</body>
</html>