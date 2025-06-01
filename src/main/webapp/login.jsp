<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%!
    private String escapeForJavaScriptString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("/", "\\/"); 
    }
%>
<%
    HttpSession loginSession = request.getSession(false); 
    String errorMessage = null;
    if (loginSession != null) {
        errorMessage = (String) loginSession.getAttribute("errorMessage");
        if (errorMessage != null) {
            loginSession.removeAttribute("errorMessage"); 
        }
    }
    if (errorMessage == null) {
        errorMessage = request.getParameter("error");
    }

    String successMessageFromRequest = request.getParameter("message");
    String companyIdentifierRepop = request.getParameter("companyIdentifier");
    String adminEmailRepop = request.getParameter("adminEmail");
    String messageType = request.getParameter("msgType"); 
    String autoLogoutMessage = request.getParameter("autoLogoutMessage"); // For auto-logout specific messages
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - YourTimeClock</title>
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/login.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
</head>
<body>
    <div class="login-container">
        <div class="login-header">
            <a href="index.jsp" class="logo-link"><i class="fas fa-clock"></i> YourTimeClock</a>
        </div>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
            <p class="error-message login-page-message" id="loginErrorMessage"><%= errorMessage.replace("<", "&lt;").replace(">", "&gt;") %></p>
        <% } %>
        <% if (autoLogoutMessage != null && !autoLogoutMessage.isEmpty()) { %>
            <p class="info-message login-page-message" id="autoLogoutInfoMessage"><%= autoLogoutMessage.replace("<", "&lt;").replace(">", "&gt;") %></p>
        <% } %>


        <form action="LoginServlet" method="POST" id="loginForm" class="login-form">
            <input type="hidden" id="browserTimeZone" name="browserTimeZone" value="">
            <div class="form-group">
                <label for="companyIdentifier">Company ID <span class="required">*</span></label>
                <input type="text" id="companyIdentifier" name="companyIdentifier"
                       value="<%= (companyIdentifierRepop != null ? companyIdentifierRepop.replace("\"", "&quot;") : "") %>"
                       autocomplete="organization"
                       required>
            </div>
            <div class="form-group">
                <label for="email">Email Address <span class="required">*</span></label>
                <input type="email" id="email" name="email"
                       value="<%= (adminEmailRepop != null ? adminEmailRepop.replace("\"", "&quot;") : "") %>"
                       autocomplete="email" required>
            </div>
            <div class="form-group">
                <label for="password">PIN <span class="required">*</span></label>
                <input type="password" id="password" name="password" autocomplete="current-password" required>
            </div>
            <div class="form-actions">
                <button type="submit" class="glossy-button text-blue login-submit-button">
                    <i class="fas fa-sign-in-alt"></i> Log In
                </button>
            </div>
        </form>
        <p class="signup-redirect">Don't have a company account? <a href="signup_company_info.jsp">Sign Up Here</a></p>
    </div>

    <div id="notificationModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeNotificationModal">&times;</span>
            <h2 id="notificationModalTitle">Notification</h2>
            <div id="notificationMessage" class="signup-success-message" style="text-align: left; padding: 15px 20px; line-height:1.6;">
                <%-- JS will populate this --%>
            </div>
            <div class="button-row notification-button-row" style="justify-content: space-around; padding-top: 15px; padding-bottom: 20px;">
                <button type="button" id="copyCompanyIdButton" class="glossy-button text-blue" style="display:none; flex-grow:1; margin: 0 5px;">
                    <i class="fas fa-copy"></i> Copy Company ID
                </button>
                <button type="button" id="okButtonNotificationModal" class="glossy-button text-green" style="flex-grow:1; margin: 0 5px;">OK</button>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="js/login.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>