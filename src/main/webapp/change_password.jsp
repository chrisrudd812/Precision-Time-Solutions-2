<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %> 
<%@ page import="java.util.logging.Level" %>  

<%!
    private static final Logger jspChangePasswordLogger = Logger.getLogger("change_password_jsp_v3");

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
    HttpSession cpSession = request.getSession(false); 
    String pageErrorMessage = null; 
    
    if (cpSession != null) {
        pageErrorMessage = (String) cpSession.getAttribute("errorMessage");
        if (pageErrorMessage != null) {
            cpSession.removeAttribute("errorMessage"); 
        }
    }
    if (pageErrorMessage == null || pageErrorMessage.isEmpty()) {
        pageErrorMessage = request.getParameter("error");
    }

    String userFirstNameForDisplay = "User"; 
    Boolean requiresChangeSessionFlag = null;
    Integer userEidForPinChange = null;
    final int PIN_CHANGE_TIMEOUT_SECONDS = 300; // 5 minutes for PIN change process

    if (cpSession != null) {
        Object eidObj = cpSession.getAttribute("EID");
        if (eidObj instanceof Integer) {
            userEidForPinChange = (Integer) eidObj;
        }

        Object nameObj = cpSession.getAttribute("UserFirstName");
        if (nameObj instanceof String && !((String)nameObj).isEmpty()) {
            userFirstNameForDisplay = (String) nameObj;
        }

        // FIX: Changed this to look for the correct session attribute set by LoginServlet.
        Object rpcObj = cpSession.getAttribute("pinChangeRequired");
        if (rpcObj instanceof Boolean) {
            requiresChangeSessionFlag = (Boolean) rpcObj;
        }
    }

    // This security check now works correctly.
    if (cpSession == null || userEidForPinChange == null || userEidForPinChange <= 0 || 
        requiresChangeSessionFlag == null || !requiresChangeSessionFlag.booleanValue()) { 
        String redirectErrorMsg = "PIN change not currently required or session invalid.";
        jspChangePasswordLogger.warning("[change_password.jsp] Redirecting to login. Conditions not met for PIN change page.");
        if(cpSession != null) {
             cpSession.setAttribute("errorMessage", redirectErrorMsg);
        }
        response.sendRedirect("login.jsp?error=" + URLEncoder.encode(redirectErrorMsg, StandardCharsets.UTF_8.name()));
        return;
    } else {
        // PIN change IS required, extend session timeout for this page
        cpSession.setMaxInactiveInterval(PIN_CHANGE_TIMEOUT_SECONDS);
        jspChangePasswordLogger.info("[change_password.jsp] PIN change required. Extended session timeout to " + PIN_CHANGE_TIMEOUT_SECONDS + " seconds for EID: " + userEidForPinChange);
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Set New PIN - YourTimeClock</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/login.css?v=<%= System.currentTimeMillis() %>">   
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <style>
        .change-password-container { max-width: 520px; margin-top: 5%; }
        .change-password-header .logo-link { font-size: 2em; }
        .change-password-header h1 { font-size: 1.6em; margin-bottom: 10px;}
        .change-password-header p { font-size: 0.95em; line-height: 1.5; color: #444;}
        .required { color: #dc3545; margin-left: 2px; }
    </style>
</head>
<body>
    <div class="change-password-container login-container"> 
        <div class="change-password-header login-header">
            <a href="index.jsp" class="logo-link">
    <img src="<%= request.getContextPath() %>/images/logo.png" alt="Company Logo" style="height: 32px; vertical-align: middle; margin-right: 8px;"> 
    Precision Time Solutions
</a>
            <h1>Set Your New Login PIN</h1>
            <p>Hi <%= escapeHtml(userFirstNameForDisplay) %>, your temporary PIN for your <strong>user account login</strong> needs to be changed.
            <br>This PIN is used for clocking in/out and accessing your user-specific features.
            It is separate from your Company Administrator password (if applicable, which is used for company-wide settings).
            <br>Please set your new 4-digit numeric PIN below.</p>
        </div>

        <% if (pageErrorMessage != null && !pageErrorMessage.isEmpty()) { %>
            <p class="error-message login-page-message"><%= escapeHtml(pageErrorMessage) %></p>
        <% } %>

        <form action="ChangePasswordServlet" method="POST" id="changePasswordForm" class="login-form">
            <div class="form-group">
                <label for="newPassword">New 4-Digit PIN <span class="required">*</span></label>
                <input type="password" id="newPassword" name="newPassword" required minlength="4" maxlength="4" pattern="\d{4}" title="Must be exactly 4 digits." inputmode="numeric" autocomplete="new-password">
            </div>
            <div class="form-group">
                <label for="confirmPassword">Confirm New PIN <span class="required">*</span></label>
                <input type="password" id="confirmPassword" name="confirmPassword" required minlength="4" maxlength="4" pattern="\d{4}" title="Must be exactly 4 digits." inputmode="numeric" autocomplete="new-password">
            </div>
            <div class="form-actions">
                <button type="submit" class="glossy-button text-green login-submit-button">
                    <i class="fas fa-save"></i> Set New PIN
                </button>
            </div>
        </form>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript">
        document.addEventListener('DOMContentLoaded', function() {
            const form = document.getElementById('changePasswordForm');
            const newPasswordField = document.getElementById('newPassword');
            const confirmPasswordField = document.getElementById('confirmPassword');
            
            if (newPasswordField) newPasswordField.focus();

            if (form) {
                form.addEventListener('submit', function(event) {
                    let isValid = true;
                    const newPin = newPasswordField.value;
                    const confirmPin = confirmPasswordField.value;

                    if (!newPin.match(/^\d{4}$/)) {
                        alert("New PIN must be exactly 4 numerical digits.");
                        newPasswordField.focus(); isValid = false;
                    } else if (newPin === "1234") { 
                        alert("Your new PIN cannot be the default PIN '1234'. Please choose a different PIN.");
                        newPasswordField.value = ""; confirmPasswordField.value = "";
                        newPasswordField.focus(); isValid = false;
                    } else if (newPin !== confirmPin) {
                        alert("New PINs do not match.");
                        confirmPasswordField.value = ""; 
                        confirmPasswordField.focus(); isValid = false;
                    }
                    if (!isValid) { 
                        event.preventDefault();
                    } else {
                        const submitButton = form.querySelector('button[type="submit"]');
                        if(submitButton){
                            submitButton.disabled = true;
                            submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
                        }
                    }
                });
            }
            const urlParams = new URLSearchParams(window.location.search);
            if (urlParams.has('error')) { 
                if (typeof clearUrlParams === 'function') { clearUrlParams(['error']); }
                else { try { window.history.replaceState({}, document.title, window.location.pathname); } catch (e) {} }
            }
        });
    </script>
</body>
</html>