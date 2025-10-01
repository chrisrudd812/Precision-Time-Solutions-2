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
    String autoLogoutMessage = request.getParameter("reason"); // Corrected to match servlet and JS
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Login - Precision Time Solutions</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/login.css?v=<%= System.currentTimeMillis() %>">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
</head>
<body>
    <div class="login-container">
        <div class="login-header">
            <a href="index.jsp" class="logo-link">
                 <img src="<%= request.getContextPath() %>/Images/logo.webp" alt="Precision Time Solutions Logo" class="logo-image">
            </a>
            <h1>Admin and Employee Login</h1>
        </div>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
            <p class="error-message login-page-message" id="loginErrorMessage"><%= errorMessage.replace("<", "&lt;").replace(">", "&gt;") %></p>
        <% } %>
        <% if (autoLogoutMessage != null && !autoLogoutMessage.isEmpty()) { %>
             <p class="page-message info-message login-page-message" id="autoLogoutInfoMessage"><%= autoLogoutMessage.replace("<", "&lt;").replace(">", "&gt;") %></p>
        <% } %>

        <form action="LoginServlet" method="POST" id="loginForm" class="login-form">
             <input type="hidden" id="browserTimeZone" name="browserTimeZone" value="">
            <div class="form-body-container">
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
                    <input type="password" id="password" name="password" autocomplete="off" required>
                </div>
            </div>
            <div class="form-actions">
                <button type="submit" class="glossy-button text-blue login-submit-button">
                    <i class="fas fa-sign-in-alt"></i> Log In
                </button>
            </div>
        </form>
        <p class="signup-redirect">Don't have a company account? <a href="signup_company_info.jsp">Sign Up Here</a></p>
    </div>

    <%-- MODIFIED: Modal structure updated to match the site's standard --%>
    <div id="notificationModal" class="modal">
        <div class="modal-content">
            <div class="modal-header">
                <h2 id="notificationModalTitle">
                    <i class="fas fa-info-circle"></i> <span>Notification</span>
                </h2>
            </div>
            <div class="modal-body">
                <p id="notificationMessage"></p>
            </div>
            <div class="button-row notification-button-row">
                <button type="button" id="copyCompanyIdButton" class="glossy-button text-blue" style="display:none;">
                    <i class="fas fa-copy"></i> Copy Company ID
                 </button>
                <button type="button" id="okButtonNotificationModal" class="glossy-button text-green">OK</button>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript" src="js/login.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>