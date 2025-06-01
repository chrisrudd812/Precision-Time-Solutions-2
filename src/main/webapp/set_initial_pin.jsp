<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspSetInitialPinLogger = Logger.getLogger("set_initial_pin_jsp_v5_ok_button_fix");

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

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
    HttpSession initialPinSession = request.getSession(false);
    String pageErrorMessage = null; 
    String wizardStep = null;
    Integer wizardAdminEid = null;
    String adminEmailForDisplay = "your email";
    String adminFirstNameForDisplay = "Administrator";
    String companyNameSignup_InitialPin = "Your Company";

    String signupSuccessfulCompanyInfo = null;
    String generatedCompanyID = null;

    if (initialPinSession != null) {
        pageErrorMessage = (String) initialPinSession.getAttribute("errorMessage_initialPin");
        if (pageErrorMessage != null) {
            initialPinSession.removeAttribute("errorMessage_initialPin"); 
        }
        if ((pageErrorMessage == null || pageErrorMessage.isEmpty()) && request.getParameter("error") != null) {
            pageErrorMessage = request.getParameter("error");
        }

        wizardStep = (String) initialPinSession.getAttribute("wizardStep");
        Object eidObj = initialPinSession.getAttribute("wizardAdminEid");
        if (eidObj instanceof Integer) {
            wizardAdminEid = (Integer) eidObj;
        }
        Object emailObj = initialPinSession.getAttribute("wizardAdminEmail");
        if (emailObj instanceof String) {
            adminEmailForDisplay = (String) emailObj;
        }
        Object nameObj = initialPinSession.getAttribute("wizardAdminFirstName");
        if (nameObj instanceof String && !((String)nameObj).isEmpty()) {
            adminFirstNameForDisplay = (String) nameObj;
        }
        Object companyNameObj = initialPinSession.getAttribute("CompanyNameSignup");
         if (companyNameObj instanceof String && !((String)companyNameObj).isEmpty()) {
            companyNameSignup_InitialPin = (String) companyNameObj;
        }

        signupSuccessfulCompanyInfo = (String) initialPinSession.getAttribute("signupSuccessfulCompanyInfo");
        generatedCompanyID = (String) initialPinSession.getAttribute("GeneratedCompanyID");

        // DO NOT remove them here if the modal needs to re-display them on a POST failure of PIN set
        // Instead, they will be naturally cleared when the wizard progresses or session ends.
        // if (signupSuccessfulCompanyInfo != null) initialPinSession.removeAttribute("signupSuccessfulCompanyInfo");
        // if (generatedCompanyID != null) initialPinSession.removeAttribute("GeneratedCompanyID");

        jspSetInitialPinLogger.info("[set_initial_pin.jsp] WizardStep: " + wizardStep + ", AdminEID: " + wizardAdminEid + ", SignupSuccessInfo: " + (signupSuccessfulCompanyInfo != null) + ", CompanyName: " + companyNameSignup_InitialPin);
    }

    if (initialPinSession == null || !"initialPinSetRequired".equals(wizardStep) || wizardAdminEid == null || wizardAdminEid <= 0) {
        jspSetInitialPinLogger.warning("[set_initial_pin.jsp] Access denied or invalid state. Redirecting. WizardStep: " + wizardStep + ", EID: " + wizardAdminEid);
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
    <title>Set Your Login PIN - YourTimeClock</title>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/login.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/css/reports.css?v=<%= System.currentTimeMillis() %>"> 
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .set-pin-container { max-width: 550px; margin-top: 3%; }
        .set-pin-header .logo-link { font-size: 2em; }
        .set-pin-header h1 { font-size: 1.6em; margin-bottom: 10px;}
        .set-pin-header p.welcome-message { font-size: 0.95em; line-height: 1.5; color: #444; margin-bottom:20px; }
        .required { color: #dc3545; margin-left: 2px; }
        .info-box {
            background-color: #e7f3fe; border-left: 6px solid #2196F3; margin-bottom: 20px;
            padding: 15px 20px; border-radius: 4px; font-size: 0.9em;
        }
        .info-box h3 { margin-top: 0; color: #1E88E5; font-size: 1.2em; }
        .info-box p { margin-bottom: 8px; line-height: 1.6; }
        .info-box strong { color: #0D47A1; }
        .page-message.error-message { margin-top:10px; margin-bottom: 10px; }
        .modal { display: none; position: fixed; z-index: 1060; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; }
        .modal.modal-visible { display: flex !important; } /* Ensures visibility when class is added */
        .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 500px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left; }
        .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee; text-align:center;}
        .modal-content .close-modal-button { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; } /* Changed class for clarity */
        .modal-content .button-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
        .signup-success-message-content p { margin-bottom: 10px; line-height: 1.6;}
    </style>
</head>
<body>
    <div class="login-container set-pin-container"> 
        <div class="login-header set-pin-header">
            <a href="<%= request.getContextPath() %>/index.jsp" class="logo-link"><i class="fas fa-clock"></i> YourTimeClock</a>
            <h1>Set Your Login PIN</h1>
            <p class="welcome-message">
                Welcome, <%= escapeHtml(adminFirstNameForDisplay) %>! This is the first step in setting up your user account for 
                <strong><%= escapeHtml(companyNameSignup_InitialPin) %></strong>.
            </p>
        </div>

        <%-- Display Account Creation Success Info Modal --%>
        <% if (signupSuccessfulCompanyInfo != null && generatedCompanyID != null) { %>
            <div id="accountCreationSuccessInfoModal" class="modal modal-visible" style="display:flex;">
                <div class="modal-content">
                    <span class="close-modal-button" id="closeAccountSuccessInfoModal">&times;</span>
                    <h2>Account Creation Successful!</h2>
                    <div class="signup-success-message-content" style="padding: 15px 20px;">
                        <p><%= escapeHtml(signupSuccessfulCompanyInfo) %></p>
                        <p>Your unique Company ID is: <strong id="generatedCompanyIdTextModal"><%= escapeHtml(generatedCompanyID) %></strong></p>
                        <p>The Administrator Password you created is for managing company-level account settings.</p>
                        <p>To secure your user account (for <%= escapeHtml(adminEmailForDisplay) %>), please now set your initial 4-digit PIN. This PIN will be used for clocking in/out and accessing user-specific features.</p>
                        <p>Please make a note of your Company ID. You will need it for future logins.</p>
                    </div>
                    <div class="button-row" style="justify-content: space-around;">
                         <button type="button" id="copyCompanyIdButton_pinPage" class="glossy-button text-blue" style="margin-right:10px;"><i class="fas fa-copy"></i> Copy Company ID</button>
                        <button type="button" id="okAccountSuccessInfoButton" class="glossy-button text-green">OK, Got It!</button>
                    </div>
                </div>
            </div>
        <% } %>

        <% if (pageErrorMessage != null && !pageErrorMessage.isEmpty()) { %>
            <p class="page-message error-message"><%= escapeHtml(pageErrorMessage) %></p>
        <% } %>

        <div class="info-box">
            <h3>Set Your Secure 4-Digit PIN</h3>
            <p>This PIN is for your personal user account (<strong><%= escapeHtml(adminEmailForDisplay) %></strong>) and will be used for clocking in/out and accessing your employee dashboard.</p>
            <p>It is separate from the Company Administrator password you previously created.</p>
        </div>

        <form action="<%= request.getContextPath() %>/SetInitialPinServlet" method="POST" id="setInitialPinForm" class="login-form">
            <input type="hidden" name="eid" value="<%= wizardAdminEid %>"> 
            <input type="hidden" name="wizardStepVerify" value="initialPinSetRequired"> 
            
            <div class="form-group">
                <label for="newPin">New 4-Digit PIN <span class="required">*</span></label>
                <input type="password" id="newPin" name="newPin" required minlength="4" maxlength="4" pattern="\d{4}" title="Must be exactly 4 numerical digits." inputmode="numeric" autocomplete="new-password" <% if (signupSuccessfulCompanyInfo == null && generatedCompanyID == null) { out.print("autofocus"); } %> >
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
            console.log("[DEBUG set_initial_pin.jsp] DOMContentLoaded.");
            const form = document.getElementById('setInitialPinForm');
            const newPinField = document.getElementById('newPin');
            const confirmPinField = document.getElementById('confirmNewPin');
            const successModal = document.getElementById('accountCreationSuccessInfoModal');
            const closeSuccessModalButton = document.getElementById('closeAccountSuccessInfoModal');
            const okSuccessButton = document.getElementById('okAccountSuccessInfoButton');
            const copyButton = document.getElementById('copyCompanyIdButton_pinPage');
            const generatedCompanyIdTextElement = document.getElementById('generatedCompanyIdTextModal');

            console.log("[DEBUG set_initial_pin.jsp] newPinField:", newPinField);
            console.log("[DEBUG set_initial_pin.jsp] successModal:", successModal);
            console.log("[DEBUG set_initial_pin.jsp] okSuccessButton:", okSuccessButton);

            // Function to hide the success modal and focus on PIN input
            function hideSuccessModalAndFocusPin() {
                console.log("[DEBUG set_initial_pin.jsp] Hiding success modal and focusing PIN field.");
                if (successModal) {
                    if (typeof window.hideModal === 'function') {
                        window.hideModal(successModal);
                        console.log("[DEBUG set_initial_pin.jsp] Used global hideModal.");
                    } else {
                        successModal.style.display = 'none'; // Fallback
                        console.log("[DEBUG set_initial_pin.jsp] Used fallback to hide modal.");
                    }
                }
                if (newPinField) {
                    // Use a small timeout to ensure modal is hidden before focus attempt
                    setTimeout(function() {
                        newPinField.focus();
                        console.log("[DEBUG set_initial_pin.jsp] Focus attempted on newPinField. Active element:", document.activeElement ? document.activeElement.id : 'none');
                    }, 50);
                } else {
                    console.warn("[DEBUG set_initial_pin.jsp] newPinField not found for focus after modal close.");
                }
            }

            // Event listener for the "OK, Got It!" button
            if (okSuccessButton) {
                okSuccessButton.addEventListener('click', hideSuccessModalAndFocusPin);
            } else {
                console.warn("[DEBUG set_initial_pin.jsp] okAccountSuccessInfoButton not found!");
            }

            // Event listener for the modal's 'X' close button
            if (closeSuccessModalButton) {
                closeSuccessModalButton.addEventListener('click', hideSuccessModalAndFocusPin);
            } else {
                 console.warn("[DEBUG set_initial_pin.jsp] closeAccountSuccessInfoModal button not found!");
            }

            // Autofocus logic: only if modal isn't already displayed by JSP's inline style
            if (newPinField && !(successModal && (successModal.style.display === 'flex' || (successModal.classList && successModal.classList.contains('modal-visible'))))) {
                console.log("[DEBUG set_initial_pin.jsp] Success modal not initially visible. Focusing newPinField.");
                newPinField.focus();
            } else if (successModal && (successModal.style.display === 'flex' || (successModal.classList && successModal.classList.contains('modal-visible')))) {
                console.log("[DEBUG set_initial_pin.jsp] Success modal is initially visible by JSP style.");
            }


            if (copyButton && generatedCompanyIdTextElement) {
                const companyIdToCopy = generatedCompanyIdTextElement.textContent;
                if (companyIdToCopy) {
                    copyButton.addEventListener('click', function() {
                        navigator.clipboard.writeText(companyIdToCopy).then(function() {
                            alert('Company ID "' + companyIdToCopy + '" copied to clipboard!');
                        }, function(err) {
                            alert('Failed to copy Company ID. Please copy it manually: ' + companyIdToCopy);
                            console.error('Could not copy text: ', err);
                        });
                    });
                } else {
                    copyButton.style.display = 'none';
                }
            } else if (copyButton) {
                 copyButton.style.display = 'none';
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
                if (typeof window.clearUrlParams === 'function') { window.clearUrlParams(['error']); }
                else { try { window.history.replaceState({}, document.title, window.location.pathname); } catch (e) {console.warn("Fallback clearUrlParams failed on set_initial_pin.jsp",e);} }
            }
            console.log("[DEBUG set_initial_pin.jsp] DOMContentLoaded setup complete.");
        });
    </script>
</body>
</html>