<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%!
    private String escapeForHTML(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    String errorMessage = request.getParameter("error");
    String selectedPriceId = request.getParameter("priceId") != null ? escapeForHTML(request.getParameter("priceId")) : "price_123abc_starter"; // Default to starter plan if none selected
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Create Company Account - Precision Time Solutions</title>
    <link rel="stylesheet" href="css/signup.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
    <style>
        .form-row { display: flex; flex-wrap: wrap; gap: 20px; }
        .form-row .form-group { flex: 1 1 100%; margin-bottom: 0; }
        .form-row .half-width { flex-basis: calc(50% - 10px); }
        .form-row .third-width { flex-basis: calc(33.333% - 14px); }
        .required { color: #dc3545; margin-left: 2px; }
        .disclaimer-box { border: 1px solid #e0e0e0; padding: 15px; height: 150px; overflow-y: auto; background-color: #f9f9f9; font-size: 0.85em; margin-bottom: 10px; }
        .accept-terms-label, .billing-address-copy { display: flex; align-items: center; font-size: 0.9em; cursor: pointer; }
        input[type="checkbox"] { margin-right: 8px; width: 18px; height: 18px; accent-color: #28a745; }
        .stripe-placeholder-label { display: block; margin-bottom: 5px; font-weight: 500; color: #495057; }
        .form-control-stripe {
            border: 1px solid #ced4da;
            padding: 10px 12px;
            border-radius: 4px;
            background-color: white;
        }
        .error-message { color: #721c24; background-color: #f8d7da; padding: 10px 15px; margin-bottom:20px; border-radius: 4px; border: 1px solid #f5c6cb; text-align: center; }
        #promoCodeStatus { font-size: 0.9em; margin-top: 8px; min-height: 1.5em; }
        
        .is-invalid {
            border: 1px solid #dc3545 !important;
        }
    </style>
    <script src="https://js.stripe.com/v3/"></script>
</head>
<body>
    <div class="signup-container">
        <div class="signup-header">
            <a href="index.jsp" class="logo-link"><i class="fas fa-clock"></i> Precision Time Solutions</a>
            <h1>Create Your Company Account</h1>
            <p>Step 1 of 3: Company, Admin, & Payment</p> 
        </div>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
             <div class="error-message"><%= escapeForHTML(errorMessage) %></div>
        <% } %>
        <div id="card-errors" role="alert" class="error-message" style="display:none;"></div>

        <form action="SignupServlet" method="POST" id="companySignupForm" class="signup-form">
            <input type="hidden" name="action" value="registerCompanyAdmin">
            <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneIdField" value="">
            <input type="hidden" name="stripePaymentMethodId" id="stripePaymentMethodId">
            <input type="hidden" name="selectedPriceId" value="<%= selectedPriceId %>">
            <input type="hidden" name="appliedPromoCode" id="appliedPromoCodeInput" value="">

            <fieldset> 
                <legend>Company Information</legend>
                <div class="form-row">
                    <div class="form-group half-width"><label for="companyName">Company Name <span class="required">*</span></label><input type="text" id="companyName" name="companyName" required autofocus></div>
                    <div class="form-group half-width"><label for="companyPhone">Company Phone</label><input type="tel" id="companyPhone" name="companyPhone" placeholder="(555) 555-5555" pattern="\(\d{3}\) \d{3}-\d{4}" title="Phone number must be in the format (555) 555-5555."></div>
                </div>
                <div class="form-group"><label for="companyAddress">Street Address</label><input type="text" id="companyAddress" name="companyAddress"></div>
                <div class="form-row">
                    <div class="form-group third-width"><label for="companyCity">City</label><input type="text" id="companyCity" name="companyCity"></div>
                    <div class="form-group third-width"><label for="companyState">State/Province</label><select id="companyState" name="companyState"><option value="">Select State</option><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div>
                    <div class="form-group third-width"><label for="companyZip">Zip/Postal Code</label><input type="text" id="companyZip" name="companyZip" pattern="\d{5}(-\d{4})?" title="Zip code must be 5 or 9 digits (e.g., 12345 or 12345-6789)."></div>
                </div>
            </fieldset>

            <fieldset> 
                <legend>Your Administrator Account</legend>
                <div class="form-row">
                    <div class="form-group half-width"><label for="adminFirstName">First Name <span class="required">*</span></label><input type="text" id="adminFirstName" name="adminFirstName" required></div>
                    <div class="form-group half-width"><label for="adminLastName">Last Name <span class="required">*</span></label><input type="text" id="adminLastName" name="adminLastName" required></div>
                </div>
                <div class="form-group"><label for="adminEmail">Email Address (this will be your login) <span class="required">*</span></label><input type="email" id="adminEmail" name="adminEmail" required></div>
                <div class="form-row">
                    <div class="form-group half-width"><label for="adminPassword">Administrator Password <span class="required">*</span></label><input type="password" id="adminPassword" name="adminPassword" required minlength="8" autocomplete="new-password"></div>
                    <div class="form-group half-width"><label for="adminConfirmPassword">Confirm Password <span class="required">*</span></label><input type="password" id="adminConfirmPassword" name="adminConfirmPassword" required autocomplete="new-password"></div>
                </div>
            </fieldset>

            <fieldset id="paymentInformationFieldset"> 
                <legend>Payment Information</legend>
                
                <div class="form-group" id="promoCodeSectionContainer">
                    <div class="billing-address-copy">
                        <input type="checkbox" id="usePromoCodeCheckbox" name="usePromoCode">
                        <label for="usePromoCodeCheckbox">I have a promo code</label>
                    </div>
                    <div id="promoCodeEntry" style="display: none; margin-top: 10px; padding-left: 28px;">
                        <div class="form-row" style="align-items: flex-end;">
                            <div class="form-group" style="flex-grow: 1;"><label for="promoCodeInput">Promo Code:</label><input type="text" id="promoCodeInput" name="promoCodeInput" disabled></div>
                            <div class="form-group"><button type="button" id="applyPromoCodeButton" class="glossy-button text-blue" disabled>Apply</button></div>
                        </div>
                        <div id="promoCodeStatus"></div>
                    </div>
                </div>
                
                <div id="creditCardPaymentSection"> 
                    <div class="form-group"><label for="cardholderName">Cardholder Name <span class="required">*</span></label><input type="text" id="cardholderName" name="cardholderName" required></div>
                    
                    <label class="stripe-placeholder-label">Card Number <span class="required">*</span></label>
                    <div id="card-number-element" class="form-control-stripe" style="margin-bottom: 20px;"></div>
                    
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

                    <div class="billing-address-copy" style="margin-top: 15px;">
                        <input type="checkbox" id="copyCompanyAddress" name="copyCompanyAddress">
                        <label for="copyCompanyAddress">Billing address is the same as company address.</label>
                    </div>
                    
                    <div id="billingAddressSection" style="margin-top: 10px;"> 
                        <div class="form-group"><label for="billingAddress">Billing Street Address <span class="required">*</span></label><input type="text" id="billingAddress" name="billingAddress" required></div>
                        <div class="form-row">
                            <div class="form-group third-width"><label for="billingCity">Billing City <span class="required">*</span></label><input type="text" id="billingCity" name="billingCity" required></div>
                            <div class="form-group third-width"><label for="billingState">Billing State <span class="required">*</span></label><select id="billingState" name="billingState" required><option value="">Select State</option><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div>
                            <div class="form-group third-width"><label for="billingZip">Billing Zip Code <span class="required">*</span></label><input type="text" id="billingZip" name="billingZip" required></div>
                        </div>
                    </div>
                </div> 
            </fieldset>

            <fieldset> 
                <legend>Legal Agreement</legend>
                <div class="disclaimer-box" tabindex="0">
                    <p><strong>Terms of Service & Privacy Policy</strong></p>
                    <p><strong>Effective Date:</strong> August 6, 2025</p>
                    <p>Welcome to Precision Time Solutions ("Service"). These Terms of Service ("Terms") govern your use of our time and attendance tracking services. By creating an account, you agree to these Terms.</p>
                    <p><strong>1. Service Description:</strong> The Service provides tools for employee time tracking, schedule management, and reporting for payroll purposes. You are responsible for the accuracy of all data entered into the Service.</p>
                    <p><strong>2. Subscription and Payment:</strong> The Service is billed on a subscription basis. You agree to pay all fees associated with your chosen plan. Payments are processed by our third-party payment processor, Stripe, and are subject to their terms. Subscriptions auto-renew unless canceled.</p>
                    <p><strong>3. Your Responsibilities:</strong> You are responsible for maintaining the confidentiality of your administrator password and employee PINs. You agree to comply with all applicable labor laws regarding employee time tracking and payment.</p>
                    <p><strong>4. Data Privacy:</strong> We collect and store company and employee data necessary to provide the Service. This includes names, email addresses, and punch times. We do not sell your data. Our collection and use of personal information is governed by our Privacy Policy.</p>
                    <p><strong>5. Limitation of Liability:</strong> The Service is provided "as is." We are not liable for any indirect, incidental, or consequential damages arising from your use of the Service, including payroll errors or legal compliance issues.</p>
                    <p><strong>Disclaimer:</strong> This is a template and not legal advice. Consult with a legal professional to create a Terms of Service agreement that is appropriate for your specific business and jurisdiction.</p>
                </div>
                <div class="form-group">
                    <label for="acceptTerms" class="accept-terms-label">
                        <input type="checkbox" id="acceptTerms" name="acceptTerms" value="true">
                        I have read, understand, and agree to the Terms of Service. <span class="required">*</span>
                    </label>
                </div>
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
    
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            function formatPhoneNumber(event) {
                const input = event.target;
                if(event.inputType === 'deleteContentBackward' || event.inputType === 'deleteContentForward') {
                    return;
                }
                const digits = input.value.replace(/\D/g, '');
                let formatted = '';
                if (digits.length > 0) {
                    formatted = '(' + digits.substring(0, 3);
                }
                if (digits.length >= 4) {
                    formatted += ') ' + digits.substring(3, 6);
                }
                if (digits.length >= 7) {
                    formatted += '-' + digits.substring(6, 10);
                }
                input.value = formatted;
            }
            const companyPhoneInput = document.getElementById('companyPhone');
            if(companyPhoneInput) {
                companyPhoneInput.addEventListener('input', formatPhoneNumber);
            }
            const adminPasswordField = document.getElementById('adminPassword');
            const adminConfirmPasswordField = document.getElementById('adminConfirmPassword');

            if (adminConfirmPasswordField && adminPasswordField) {
                adminConfirmPasswordField.addEventListener('blur', function() {
                    if (adminPasswordField.value.length > 0 && adminConfirmPasswordField.value.length > 0) {
                        window.scrollTo({
                            top: document.body.scrollHeight,
                            behavior: 'smooth'
                        });
                    }
                });
            }
        });
    </script>
</body>
</html>