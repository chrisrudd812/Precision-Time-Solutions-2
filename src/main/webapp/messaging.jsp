<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%
    String pageName = "messaging.jsp";
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Messaging Center</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    
    <link rel="stylesheet" href="css/messaging.css?v=<%= System.currentTimeMillis() %>">
</head>
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <%-- MODIFIED: Updated container classes for consistency --%>
    <div class="parent-container">
        <h1><i class="fas fa-comments"></i> Messaging Center</h1>

        <div class="content-display-area">
            <p class="messaging-description">Send a message to employees via email or as a pop-up that will appear the next time they log in. Select a delivery method, recipient group, compose your message, and send.</p>

            <div class="messaging-form-container">
                <form id="messagingForm">
                    <input type="hidden" id="messageDeliveryType" name="messageDeliveryType" value="login">

                    <div class="toggle-switch-container">
                        <% boolean isWelcomeAction = "welcome".equals(request.getParameter("action")); %>
                        <input type="radio" id="deliveryTypeEmail" name="deliveryType" value="email" <%= isWelcomeAction ? "checked" : "" %>>
                        <label for="deliveryTypeEmail">Email Message</label>
                        <input type="radio" id="deliveryTypeLogin" name="deliveryType" value="login" <%= !isWelcomeAction ? "checked" : "" %>>
                        <label for="deliveryTypeLogin">Login Message</label>
                        <div class="toggle-glider"></div>
                    </div>

                    <div class="form-row">
                        <div class="form-item">
                            <label for="recipientType">Send To:</label>
                            <select id="recipientType" name="recipientType">
                                <option value="all">All Active Employees</option>
                                <option value="department">A Specific Department</option>
                                <option value="schedule">A Specific Schedule</option>
                                <option value="supervisor">A Specific Supervisor</option>
                                <option value="individual">An Individual Employee</option>
                            </select>
                        </div>

                        <div id="target-selection-container" class="form-item" style="display:none;">
                            <label for="recipientTarget" id="recipientTargetLabel">Select Target:</label>
                            <select id="recipientTarget" name="recipientTarget"></select>
                        </div>
                    </div>

                    <div class="form-item">
                        <label for="messageSubject">Subject:</label>
                        <input type="text" id="messageSubject" name="messageSubject" required placeholder="e.g., Welcome to the Company!">
                    </div>

                    <div class="form-item">
                        <label for="messageBody">Message Body:</label>
                        <textarea id="messageBody" name="messageBody" rows="10" required placeholder="Your initial password is '1234'. Please change it upon your first login."></textarea>
                    </div>

                    <div class="button-row">
                        <button type="submit" id="sendMessageBtn" class="glossy-button text-green">
                            <i class="fas fa-paper-plane"></i> Send Message
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <%-- MODIFIED: Changed include to the consolidated modals.jspf --%>
    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/messaging.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>