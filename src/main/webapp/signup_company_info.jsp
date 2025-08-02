<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%!
    private String escapeForHTML(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    
    private String escapeForJS(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("/", "\\/");
    }
%>
<%
    String errorMessage = null;
    HttpSession signupSession = request.getSession(false); 
    if (signupSession != null) {
        errorMessage = (String) signupSession.getAttribute("errorMessage");
        if (errorMessage != null) {
            signupSession.removeAttribute("errorMessage");
        }
    }
    if (errorMessage == null) {
        errorMessage = request.getParameter("error");
    }

    String companyNameValue = request.getParameter("companyName") != null ? escapeForHTML(request.getParameter("companyName")) : "";
    String companyPhoneValue = request.getParameter("companyPhone") != null ? escapeForHTML(request.getParameter("companyPhone")) : "";
    String companyAddressValue = request.getParameter("companyAddress") != null ? escapeForHTML(request.getParameter("companyAddress")) : "";
    String companyCityValue = request.getParameter("companyCity") != null ? escapeForHTML(request.getParameter("companyCity")) : "";
    String companyStateValue = request.getParameter("companyState") != null ? escapeForHTML(request.getParameter("companyState")) : "";
    String companyZipValue = request.getParameter("companyZip") != null ? escapeForHTML(request.getParameter("companyZip")) : "";
    String adminFirstNameValue = request.getParameter("adminFirstName") != null ? escapeForHTML(request.getParameter("adminFirstName")) : "";
    String adminLastNameValue = request.getParameter("adminLastName") != null ? escapeForHTML(request.getParameter("adminLastName")) : "";
    String adminEmailValue = request.getParameter("adminEmail") != null ? escapeForHTML(request.getParameter("adminEmail")) : "";
    String cardholderNameValue = request.getParameter("cardholderName") != null ? escapeForHTML(request.getParameter("cardholderName")) : "";
    String billingAddressValue = request.getParameter("billingAddress") != null ? escapeForHTML(request.getParameter("billingAddress")) : "";
    String billingCityValue = request.getParameter("billingCity") != null ? escapeForHTML(request.getParameter("billingCity")) : "";
    String billingStateValue = request.getParameter("billingState") != null ? escapeForHTML(request.getParameter("billingState")) : "";
    String billingZipValue = request.getParameter("billingZip") != null ? escapeForHTML(request.getParameter("billingZip")) : "";
    boolean termsAcceptedValue = "on".equalsIgnoreCase(request.getParameter("acceptTerms")) || "true".equalsIgnoreCase(request.getParameter("acceptTerms"));
    boolean copyAddressCheckedValue = "on".equalsIgnoreCase(request.getParameter("copyCompanyAddress")) || "true".equalsIgnoreCase(request.getParameter("copyCompanyAddress"));
    
    String promoCodeValue_repop = request.getParameter("promoCodeInput") != null ? escapeForHTML(request.getParameter("promoCodeInput")) : "";
    boolean usePromoCodeCheckedValue_repop = "on".equalsIgnoreCase(request.getParameter("usePromoCode")) || 
                                        (request.getParameter("appliedPromoCode") != null && !request.getParameter("appliedPromoCode").isEmpty());
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Create Company Account - YourTimeClock</title>
    <link rel="stylesheet" href="css/signup.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
    <link rel="icon" href="favicon.ico" type="image/x-icon">
    <style>
        body { font-family: 'Roboto', sans-serif; }
        .required, label .required, .stripe-placeholder-label .required { color: #dc3545 !important; margin-left: 2px; font-weight: bold; }
        .disclaimer-box { border: 1px solid #e0e0e0; padding: 15px; height: 150px; overflow-y: auto; background-color: #f9f9f9; font-size: 0.85em; line-height: 1.5; margin-bottom: 10px; }
        .accept-terms-label, .billing-address-copy, #promoCodeSectionContainer .billing-address-copy { display: flex; align-items: center; font-size: 0.9em; }
        .accept-terms-label input[type="checkbox"], .billing-address-copy input[type="checkbox"], #promoCodeSectionContainer .billing-address-copy input[type="checkbox"] { margin-right: 8px; width: 18px; height: 18px; vertical-align: middle; accent-color: #28a745; }
        .stripe-placeholder-label { display: block; margin-bottom: 5px; font-weight: 500; color: #495057; font-size: .9em; }
        #card-number-element, #card-expiry-element, #card-cvc-element { border: 1px solid #ced4da; padding: 10px 12px; border-radius: 4px; height: 40px; background-color: white; margin-bottom: 10px; box-sizing: border-box; }
        .payment-info-note { font-size: 0.85em; color: #6c757d; margin-top: 5px; margin-bottom: 15px; }
        .error-message { color: #721c24; background-color: #f8d7da; padding: 10px 15px; margin-bottom:20px; margin-top:10px; border-radius: 4px; border: 1px solid #f5c6cb; text-align: center; }
        #promoCodeEntryDiv .form-group { margin-bottom: 0 !important; }
        #promoCodeInput[disabled], #applyPromoCodeButton[disabled] { opacity: 0.6; cursor: not-allowed; }
        #creditCardPaymentSection.payment-section-visually-disabled, #billingAddressSection.payment-section-visually-disabled { opacity: 0.6; }
        input:disabled, select:disabled, textarea:disabled { background-color: #e9ecef !important; color: #6c757d; cursor: not-allowed; }
        .payment-section-visually-disabled .form-control-stripe { border-color: #e0e0e0 !important; }
        .glossy-button { padding: 8px 12px; font-size: 0.9em; border-radius: 4px; cursor: pointer; border: 1px solid #007bff; background-color: #007bff; color: white; transition: background-color 0.15s ease-in-out; }
        .glossy-button:hover { background-color: #0056b3; }
        .glossy-button:disabled { background-color: #6c757d; border-color: #6c757d; }
        #mainSubmitButton:disabled { opacity: 0.65; cursor: not-allowed; }
    </style>
    <script src="https://js.stripe.com/v3/"></script>
</head>
<body>
    <div class="signup-container">
        <div class="signup-header">
            <a href="index.jsp" class="logo-link"><i class="fas fa-clock"></i> YourTimeClock</a>
            <h1>Create Your Company Account</h1>
            <p>Step 1: Company, Admin, Payment & Agreement</p> 
        </div>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
            <div class="error-message" style="display:block !important;"><%= escapeForHTML(errorMessage) %></div>
        <% } %>
        <div id="card-errors" role="alert" class="error-message" style="display:none;"></div>

        <form action="SignupServlet" method="POST" id="companySignupForm" class="signup-form">
            <input type="hidden" name="action" value="registerCompanyAdmin">
            <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneIdField" value="">
            <input type="hidden" name="stripePaymentMethodId" id="stripePaymentMethodId">
            <input type="hidden" name="appliedPromoCode" id="appliedPromoCodeInput" value="">

            <fieldset> 
                <legend>Company Information</legend>
                <div class="form-row"><div class="form-group half-width"><label for="companyName">Company Name <span class="required">*</span></label><input type="text" id="companyName" name="companyName" value="<%=companyNameValue%>" required maxlength="100" autofocus></div><div class="form-group half-width"><label for="companyPhone">Company Phone</label><input type="text" id="companyPhone" name="companyPhone" value="<%=companyPhoneValue%>" placeholder="e.g., (555) 123-4567" maxlength="20"></div></div>
                <div class="form-group"><label for="companyAddress">Street Address</label><input type="text" id="companyAddress" name="companyAddress" value="<%=companyAddressValue%>" maxlength="255"></div>
                <div class="form-row"><div class="form-group third-width"><label for="companyCity">City</label><input type="text" id="companyCity" name="companyCity" value="<%=companyCityValue%>" maxlength="100"></div><div class="form-group third-width"><label for="companyState">State/Province</label><select id="companyState" name="companyState"><option value="" <%="".equals(companyStateValue) ? "selected" : ""%>>Select State</option><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div><div class="form-group third-width"><label for="companyZip">Zip/Postal Code</label><input type="text" id="companyZip" name="companyZip" value="<%=companyZipValue%>" maxlength="20"></div></div>
            </fieldset>

            <fieldset> 
                <legend>Your Administrator Account</legend>
                <p class="fieldset-description">This will be the primary administrator for your company.</p>
                <div class="form-row"><div class="form-group half-width"><label for="adminFirstName">First Name <span class="required">*</span></label><input type="text" id="adminFirstName" name="adminFirstName" value="<%=adminFirstNameValue%>" required maxlength="50"></div><div class="form-group half-width"><label for="adminLastName">Last Name <span class="required">*</span></label><input type="text" id="adminLastName" name="adminLastName" value="<%=adminLastNameValue%>" required maxlength="50"></div></div>
                <div class="form-group"><label for="adminEmail">Email Address (this will be your login) <span class="required">*</span></label><input type="email" id="adminEmail" name="adminEmail" value="<%=adminEmailValue%>" required maxlength="100"></div>
                <div class="form-row"><div class="form-group half-width"><label for="adminPassword">Administrator Password <span class="required">*</span></label><input type="password" id="adminPassword" name="adminPassword" required minlength="8" autocomplete="new-password"><small>Minimum 8 characters.</small></div><div class="form-group half-width"><label for="adminConfirmPassword">Confirm Password <span class="required">*</span></label><input type="password" id="adminConfirmPassword" name="adminConfirmPassword" required autocomplete="new-password"></div></div>
            </fieldset>

            <fieldset id="paymentInformationFieldset"> 
                <legend>Payment Information</legend>
                <p class="fieldset-description">Securely enter your payment details for the <strong>$19.99/month</strong> subscription. Processed by Stripe.</p>
                <div class="form-group" id="promoCodeSectionContainer" style="margin-bottom: 20px;"><div class="billing-address-copy"><input type="checkbox" id="usePromoCodeCheckbox" name="usePromoCode" <% if(usePromoCodeCheckedValue_repop) out.print("checked"); %>><label for="usePromoCodeCheckbox">Use Promo Code</label></div><div id="promoCodeEntryDiv" style="margin-top: 10px; padding-left: 25px;"><div class="form-row" style="align-items: flex-end;"><div class="form-group" style="flex-grow: 1; margin-bottom: 0;"><label for="promoCodeInput" class="stripe-placeholder-label" style="font-size: 0.85em;">Enter Promo Code:</label><input type="text" id="promoCodeInput" name="promoCodeInput" value="<%=promoCodeValue_repop%>" placeholder="Promo Code" maxlength="50" style="width: calc(100% - 24px); padding: 9px 12px; height: 38px;" disabled></div><div class="form-group" style="margin-bottom: 0; margin-left: 10px;"><button type="button" id="applyPromoCodeButton" class="glossy-button text-blue" style="padding: 8px 15px; height: 38px;" disabled>Apply</button></div></div><div id="promoCodeStatus" style="font-size: 0.85em; margin-top: 8px; min-height: 1.5em; padding-left: 2px;"></div></div></div>
                
                <div id="creditCardPaymentSection"> 
                    <div class="form-group">
                         <label for="cardholderName">Cardholder Name <span class="required">*</span></label>
                        <input type="text" id="cardholderName" name="cardholderName" value="<%=cardholderNameValue%>" required maxlength="100" autocomplete="off">
                    </div>
                    
                    <label class="stripe-placeholder-label">Card Number <span class="required">*</span></label>
                    <div id="card-number-element" class="form-control-stripe"></div> 
                    <div class="form-row">
                        <div class="form-group half-width">
                             <label class="stripe-placeholder-label">Expiration Date <span class="required">*</span></label>
                             <div id="card-expiry-element" class="form-control-stripe"></div> 
                        </div>
                        <div class="form-group half-width">
                            <label class="stripe-placeholder-label">CVC <span class="required">*</span></label>
                            <div id="card-cvc-element" class="form-control-stripe"></div> 
                        </div>
                    </div>

                    <p class="payment-info-note">Your card details are tokenized and processed securely by Stripe.</p>
                    <div class="billing-address-copy">
                        <input type="checkbox" id="copyCompanyAddress" name="copyCompanyAddress" <% if(copyAddressCheckedValue) out.print("checked"); %>>
                        <label for="copyCompanyAddress">Billing address is the same as company address.</label>
                    </div>
                    <div id="billingAddressSection"> 
                        <div class="form-group"><label for="billingAddress">Billing Street Address <span class="required">*</span></label><input type="text" id="billingAddress" name="billingAddress" value="<%=billingAddressValue%>" required maxlength="255" autocomplete="off"></div>
                        <div class="form-row"><div class="form-group third-width"><label for="billingCity">Billing City <span class="required">*</span></label><input type="text" id="billingCity" name="billingCity" value="<%=billingCityValue%>" required maxlength="100" autocomplete="off"></div><div class="form-group third-width"><label for="billingState">Billing State/Province <span class="required">*</span></label><select id="billingState" name="billingState" required autocomplete="off"><option value="" <%="".equals(billingStateValue) ? "selected" : ""%>>Select State</option><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div><div class="form-group third-width"><label for="billingZip">Billing Zip/Postal Code <span class="required">*</span></label><input type="text" id="billingZip" name="billingZip" value="<%=billingZipValue%>" required maxlength="20" autocomplete="off"></div></div>
                    </div>
                </div> 
            </fieldset>

            <fieldset> 
                <legend>Legal Agreement</legend>
                <div class="disclaimer-box" tabindex="0"><p style="color: red; font-weight: bold;"><strong>IMPORTANT DISCLAIMER: This is a placeholder and NOT a valid legal agreement. Do NOT use this placeholder text in a live application.</strong></p><hr><p><strong>[Placeholder] Terms of Service:</strong></p><p>Welcome to YourTimeClock. By creating an account, you agree to these terms...</p><p><strong>1. Data Responsibility:</strong> You are solely responsible for the accuracy and legality of all data you input...</p><p><strong>[End of Placeholder Text]</strong></p></div>
                <div class="form-group"><label for="acceptTerms" class="accept-terms-label"><input type="checkbox" id="acceptTerms" name="acceptTerms" value="true" <% if(termsAcceptedValue) out.print("checked"); %>>I have read, understand, and agree to the Legal Disclaimer, Terms of Service, and Privacy Policy. <span class="required">*</span></label></div>
            </fieldset>

            <div class="form-actions">
                <button type="submit" id="mainSubmitButton" class="btn btn-primary btn-large">Create Account <i class="fas fa-arrow-right"></i></button>
            </div>
        </form>
        <p class="login-redirect">Already have an account? <a href="login.jsp">Log In</a></p>
    </div>
    
    <script>
        window.stripePublishableKey = "pk_test_51RUHnpBtvyYfb2KWE9qJPWYzUdwurEUDf8W1VtxuV16ZJj8eJtCS8ubiNZI1W3XNikJa8XjjbiKp9f3dkzXRabkm009fB33jMV";
        const appRootPath = "<%= request.getContextPath() %>";
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %> 
    <script src="js/signup_validation.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>