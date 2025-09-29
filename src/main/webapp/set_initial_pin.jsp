<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspSetInitialPinLogger_SERVER = Logger.getLogger("set_initial_pin_jsp_server_side_v7_button_style");

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
    HttpSession initialPinSession = request.getSession(false);
    String pageErrorMessage = null; 
    String wizardStep = null;
    Integer wizardAdminEid = null;
    String adminEmailForDisplay = "your email";
    String adminFirstNameForDisplay = "Administrator";
    String companyNameSignup_InitialPin = "Your Company";
    String signupSuccessfulCompanyInfo = null; 

    if (initialPinSession != null) {
        pageErrorMessage = (String) initialPinSession.getAttribute("errorMessage_initialPin");
        if (pageErrorMessage != null) initialPinSession.removeAttribute("errorMessage_initialPin"); 
        if ((pageErrorMessage == null || pageErrorMessage.isEmpty()) && request.getParameter("error") != null) pageErrorMessage = request.getParameter("error");

        wizardStep = (String) initialPinSession.getAttribute("wizardStep");
        Object eidObj = initialPinSession.getAttribute("wizardAdminEid");
        if (eidObj instanceof Integer) wizardAdminEid = (Integer) eidObj;
        Object emailObj = initialPinSession.getAttribute("wizardAdminEmail");
        if (emailObj instanceof String) adminEmailForDisplay = (String) emailObj;
        Object nameObj = initialPinSession.getAttribute("wizardAdminFirstName");
        if (nameObj instanceof String && !((String)nameObj).isEmpty()) adminFirstNameForDisplay = (String) nameObj;
        Object companyNameObj = initialPinSession.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) companyNameSignup_InitialPin = (String) companyNameObj;

        signupSuccessfulCompanyInfo = (String) initialPinSession.getAttribute("signupSuccessfulCompanyInfo");
        jspSetInitialPinLogger_SERVER.info("[set_initial_pin.jsp SERVER] WizardStep: " + wizardStep + ", AdminEID: " + wizardAdminEid + 
                                     ", SignupSuccessInfo present: " + (signupSuccessfulCompanyInfo != null) + 
                                     ", CompanyName: " + companyNameSignup_InitialPin);
    }

    if (initialPinSession == null || !"initialPinSetRequired".equals(wizardStep) || wizardAdminEid == null || wizardAdminEid <= 0) {
        jspSetInitialPinLogger_SERVER.warning("[set_initial_pin.jsp SERVER] Access denied or invalid state. Redirecting. WizardStep: " + wizardStep + ", EID: " + wizardAdminEid);
        if (initialPinSession != null) initialPinSession.invalidate(); 
        response.sendRedirect(request.getContextPath() + "/signup_company_info.jsp?error=" + URLEncoder.encode("Invalid setup step or session expired. Please start over.", StandardCharsets.UTF_8.name()));
        return;
    }
    initialPinSession.setMaxInactiveInterval(600); 
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Set Your Login PIN - <%= escapeHtml(companyNameSignup_InitialPin) %></title>
    <link rel="icon" href="<%= request.getContextPath() %>/favicon.ico" type="image/x-icon">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/login.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
    <style>
        body { font-family: 'Roboto', sans-serif; } 
        .set-pin-container { max-width: 550px; margin-top: 3%; }
        .login-header h1 { font-size: 1.6em; margin-bottom: 10px;}
        .login-header p.welcome-message { font-size: 0.95em; line-height: 1.5; color: #444; margin-bottom:20px; }
        .required { color: #dc3545; margin-left: 2px; }
        .page-message.error-message { margin-top:10px; margin-bottom: 10px; }
        .modal { display: none; position: fixed; z-index: 1060; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; }
        .modal.modal-visible { display: flex !important; } 
        .modal-content { background-color: #fefefe; margin: auto; padding: 25px 30px; border: 1px solid #bbb; border-radius: 8px; width: 90%; max-width: 520px; box-shadow: 0 5px 15px rgba(0,0,0,0.3); text-align:left; position: relative; }
        .modal-content h2 { margin-top: 0; font-size: 1.75em; color: #0056b3; padding-bottom: 15px; border-bottom: 1px solid #e9ecef; text-align:center; margin-bottom: 20px; font-weight: 500;}
        .modal-content .close-modal-button { color: #888; float: right; font-size: 30px; font-weight: bold; cursor:pointer; position: absolute; top: 10px; right: 15px; line-height: 1; }
        .modal-content .close-modal-button:hover, .modal-content .close-modal-button:focus { color: #000; text-decoration: none; }
        .modal-content .button-row { display: flex; justify-content: space-evenly; gap: 15px; margin-top: 25px; padding-top:15px; border-top: 1px solid #e9ecef;}
        .signup-success-message-content p { margin-bottom: 12px; line-height: 1.65; font-size: 0.95em;}
        .signup-success-message-content p strong { font-weight: 500; }
        
        .set-pin-header .logo-image {
            height: 250px; /* [FIX] Made the logo bigger */
            width: auto;
            margin-bottom: 15px;
        }

        .glossy-button {
            display: inline-flex; 
            align-items: center; 
            justify-content: center;
            font-weight: 500;
            text-align: center;
            vertical-align: middle;
            cursor: pointer;
            user-select: none;
            border: 1px solid transparent;
            padding: 0.65rem 1.2rem;
            font-size: 1rem;
            line-height: 1.5;
            border-radius: 0.3rem;
            text-decoration: none;
            transition: color .15s ease-in-out, background-color .15s ease-in-out, border-color .15s ease-in-out, box-shadow .15s ease-in-out;
            box-shadow: 0 2px 5px rgba(0,0,0,0.1);
        }
        .glossy-button:hover { filter: brightness(95%); box-shadow: 0 4px 8px rgba(0,0,0,0.15); }
        .glossy-button:active { filter: brightness(90%); box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
        .glossy-button:disabled { opacity: 0.65; cursor: not-allowed; box-shadow: none; }
        .glossy-button i.fas { margin-right: 0.5em; }
        .text-green { color: #fff; background-color: #28a745; border-color: #28a745; }
        .text-green:hover { background-color: #218838; border-color: #1e7e34; }
        .text-blue { color: #fff; background-color: #007bff; border-color: #007bff; }
        .text-blue:hover { background-color: #0069d9; border-color: #0062cc; }
    </style>
</head>
<body>
    <div class="login-container set-pin-container"> 
        <div class="login-header set-pin-header">
            <a href="<%= request.getContextPath() %>/index.jsp" class="logo-link">
                <img src="<%= request.getContextPath() %>/Images/logo.webp" alt="<%= escapeHtml(companyNameSignup_InitialPin) %> Logo" class="logo-image">
            </a>
            <h1>Set Your Login PIN</h1>
            <p class="welcome-message">
                Welcome, <%= escapeHtml(adminFirstNameForDisplay) %>! This is the first step in setting up your user account for 
                <strong><%= escapeHtml(companyNameSignup_InitialPin) %></strong>.
            </p>
        </div>

        <div id="accountCreationSuccessInfoModal" class="modal <% if (signupSuccessfulCompanyInfo != null) { out.print("modal-visible"); } %>">
            <div class="modal-content">
                <span class="close-modal-button" id="closeAccountSuccessInfoModal">&times;</span>
                <h2>Account Creation Successful!</h2>
                <div class="signup-success-message-content">
                     <p><%= signupSuccessfulCompanyInfo %></p> 
                    <p style="margin-top:15px;">The Administrator Password you created is for managing company-level account settings (billing, etc).</p>
                    <p>To secure your <em>User</em> account (for <strong><%= escapeHtml(adminEmailForDisplay) %></strong>), please now set your initial 4-digit PIN. This PIN will be used for accessing the Dashboard features and clocking in/out.</p>
                    <p>Please make a note of your Company ID (shown above). You will need to provide it to employees to login.</p>
                </div>
                <div class="button-row">
                    <button type="button" id="copyCompanyIdButton_pinPage" class="glossy-button text-blue"><i class="fas fa-copy"></i> Copy Company ID</button>
                    <button type="button" id="okAccountSuccessInfoButton" class="glossy-button text-green">OK, Got It!</button>
                </div>
            </div>
        </div>

        <% if (pageErrorMessage != null && !pageErrorMessage.isEmpty()) { %>
            <p class="page-message error-message"><%= escapeHtml(pageErrorMessage) %></p>
        <% } %>

        <form action="<%= request.getContextPath() %>/SetInitialPinServlet" method="POST" id="setInitialPinForm" class="login-form">
            <input type="hidden" name="eid" value="<%= wizardAdminEid %>"> 
            <input type="hidden" name="wizardStepVerify" value="initialPinSetRequired"> 
            
            <div class="form-group">
                <label for="newPin">New 4-Digit PIN <span class="required">*</span></label>
                <input type="password" id="newPin" name="newPin" required minlength="4" maxlength="4" pattern="\d{4}" title="Must be exactly 4 numerical digits." inputmode="numeric" autocomplete="new-password" <% if (signupSuccessfulCompanyInfo == null) { out.print("autofocus"); } %> >
            </div>
            <div class="form-group">
                <label for="confirmNewPin">Confirm New PIN <span class="required">*</span></label>
                <input type="password" id="confirmNewPin" name="confirmNewPin" required minlength="4" maxlength="4" pattern="\d{4}" title="Must be exactly 4 numerical digits." inputmode="numeric" autocomplete="new-password">
            </div>
            <div class="form-actions">
                <button type="submit" class="glossy-button text-green login-submit-button">
                    <i class="fas fa-save"></i> Set PIN & Continue Setup
                </button>
            </div>
        </form>
    </div>
    
    <script type="text/javascript">
        const appRootPath = "<%= request.getContextPath() %>";
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript">
        document.addEventListener('DOMContentLoaded', function() {
            console.log("[set_initial_pin.jsp CLIENT] DOMContentLoaded.");
            const form = document.getElementById('setInitialPinForm');
            const newPinField = document.getElementById('newPin');
            const confirmPinField = document.getElementById('confirmNewPin');
            const successModal = document.getElementById('accountCreationSuccessInfoModal');
            const closeSuccessModalButton = document.getElementById('closeAccountSuccessInfoModal');
            const okSuccessButton = document.getElementById('okAccountSuccessInfoButton');
            const copyButton = document.getElementById('copyCompanyIdButton_pinPage');
            const companyIdElementForCopy = document.getElementById('copyCompanyIdValue'); 

            function hideSuccessModalAndFocusPin() {
                console.log("[set_initial_pin.jsp CLIENT] Hiding success modal and focusing PIN field.");
                if (successModal) {
                    successModal.classList.remove('modal-visible');
                    successModal.style.display = 'none'; 
                }
                if (newPinField) {
                    setTimeout(function() { 
                        newPinField.focus();
                        console.log("[set_initial_pin.jsp CLIENT] Focus attempted on newPinField.");
                    }, 50);
                }
            }

            if (okSuccessButton) {
                okSuccessButton.addEventListener('click', hideSuccessModalAndFocusPin);
            }
            if (closeSuccessModalButton) {
                closeSuccessModalButton.addEventListener('click', hideSuccessModalAndFocusPin);
            }

            if (newPinField && (!successModal || !successModal.classList.contains('modal-visible'))) {
                console.log("[set_initial_pin.jsp CLIENT] Success modal not initially visible. Focusing newPinField.");
                newPinField.focus();
            } else if (successModal && successModal.classList.contains('modal-visible')) {
                 console.log("[set_initial_pin.jsp CLIENT] Success modal IS initially visible.");
            }

            if (copyButton && companyIdElementForCopy) {
                const companyIdToCopy = companyIdElementForCopy.textContent;
                if (companyIdToCopy && companyIdToCopy.trim() !== "") {
                    copyButton.addEventListener('click', function() {
                        navigator.clipboard.writeText(companyIdToCopy.trim()).then(function() {
                            alert('Company ID "' + companyIdToCopy.trim() + '" copied to clipboard!');
                        }, function(err) {
                            prompt("Failed to auto-copy. Please copy manually:", companyIdToCopy.trim());
                            console.warn("[set_initial_pin.jsp CLIENT] Could not copy text automatically: ", err);
                        });
                    });
                } else {
                    console.warn("[set_initial_pin.jsp CLIENT] 'copyCompanyIdValue' element found, but its textContent is empty. Hiding copy button.");
                    copyButton.style.display = 'none';
                }
            } else if (copyButton) {
                 console.warn("[set_initial_pin.jsp CLIENT] 'copyCompanyIdButton_pinPage' found, but 'copyCompanyIdValue' element (target for copy) not found. Hiding copy button.");
                 copyButton.style.display = 'none';
            } else {
                 console.log("[set_initial_pin.jsp CLIENT] Copy Company ID button not found.");
            }

            if (form) {
                form.addEventListener('submit', function(event) {
                    let isValid = true;
                    const newPin = newPinField.value;
                    const confirmPin = confirmPinField.value;

                    if (!newPin.match(/^\d{4}$/)) {
                        alert("New PIN must be exactly 4 numerical digits.");
                        newPinField.focus(); isValid = false;
                    } else if (newPin === "1234" && newPin.length === 4) { 
                        alert("Your new PIN cannot be the default '1234'. Please choose a different PIN.");
                        newPinField.value = ""; confirmPinField.value = "";
                        newPinField.focus(); isValid = false;
                    } else if (newPin !== confirmPin) {
                        alert("New PINs do not match.");
                        confirmPinField.value = ""; 
                        confirmPinField.focus(); isValid = false;
                    }
                    if (!isValid) { 
                        event.preventDefault();
                    } else {
                        const submitBtnEl = form.querySelector('button[type="submit"]');
                        if(submitBtnEl){
                            submitBtnEl.disabled = true;
                            submitBtnEl.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
                        }
                    }
                });
            }
            
            const urlParams = new URLSearchParams(window.location.search);
            if (urlParams.has('error') || urlParams.has('message')) { 
                if (typeof window.clearUrlParams === 'function') { 
                    window.clearUrlParams(['error', 'message']);
                } else { 
                    try { 
                        const newUrl = window.location.pathname + (window.location.hash || '');
                        window.history.replaceState({}, document.title, newUrl); 
                    } catch (e) {
                        console.warn("[set_initial_pin.jsp CLIENT] Fallback clearUrlParams via history API failed.",e);
                    }
                }
            }
            console.log("[set_initial_pin.jsp CLIENT] DOMContentLoaded setup complete.");
        });
    </script>
</body>
</html>