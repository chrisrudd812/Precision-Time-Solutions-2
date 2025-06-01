<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>

<%!
    // Helper function to escape HTML characters
    private String escapeForHTML(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
%>
<%
    // Retrieve error message from session or request to display at the top
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
    String companyStateValue = request.getParameter("companyState") != null ? escapeForHTML(request.getParameter("companyState")) : ""; // This is for Company State
    String companyZipValue = request.getParameter("companyZip") != null ? escapeForHTML(request.getParameter("companyZip")) : "";
    String adminFirstNameValue = request.getParameter("adminFirstName") != null ? escapeForHTML(request.getParameter("adminFirstName")) : "";
    String adminLastNameValue = request.getParameter("adminLastName") != null ? escapeForHTML(request.getParameter("adminLastName")) : "";
    String adminEmailValue = request.getParameter("adminEmail") != null ? escapeForHTML(request.getParameter("adminEmail")) : "";
    
    String cardholderNameValue = request.getParameter("cardholderName") != null ? escapeForHTML(request.getParameter("cardholderName")) : "";
    String billingAddressValue = request.getParameter("billingAddress") != null ? escapeForHTML(request.getParameter("billingAddress")) : "";
    String billingCityValue = request.getParameter("billingCity") != null ? escapeForHTML(request.getParameter("billingCity")) : "";
    String billingStateValue = request.getParameter("billingState") != null ? escapeForHTML(request.getParameter("billingState")) : ""; // This is for Billing State
    String billingZipValue = request.getParameter("billingZip") != null ? escapeForHTML(request.getParameter("billingZip")) : "";
    boolean termsAcceptedValue = "on".equalsIgnoreCase(request.getParameter("acceptTerms")) || "true".equalsIgnoreCase(request.getParameter("acceptTerms"));
    boolean copyAddressCheckedValue = "on".equalsIgnoreCase(request.getParameter("copyCompanyAddress")) || "true".equalsIgnoreCase(request.getParameter("copyCompanyAddress"));

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
        /* Your existing styles from signup.css will be primary. */
        .disclaimer-box {
            border: 1px solid #e0e0e0; padding: 15px; height: 150px; overflow-y: auto;
            background-color: #f9f9f9; font-size: 0.85em; line-height: 1.5; margin-bottom: 10px;
        }
        .accept-terms-label { display: flex; align-items: center; font-size: 0.9em; }
        .accept-terms-label input[type="checkbox"],
        .billing-address-copy input[type="checkbox"] { /* Combined for consistent styling */
            margin-right: 8px; width: 18px; height: 18px; vertical-align: middle;
        }
        .accept-terms-label input[type="checkbox"]:checked,
        .billing-address-copy input[type="checkbox"]:checked {
            accent-color: #28a745; /* Green checkbox */
        }
        
        .paypal-hosted-field-label { display: block; margin-bottom: 5px; font-weight: 500; color: #495057; font-size: .9em; }
        .form-control-paypal { 
            border: 1px solid #ced4da; padding: 0; 
            border-radius: 4px;
            height: 40px; 
            background-color: white; margin-bottom: 10px; 
        }
        .payment-info-note { font-size: 0.85em; color: #6c757d; margin-top: 5px; margin-bottom: 15px; }
        .error-message { /* This is the general error message style from your CSS */
            color: #721c24; background-color: #f8d7da; padding: 10px 15px; 
            margin-bottom:20px; margin-top:10px; border-radius: 4px; border: 1px solid #f5c6cb; 
            text-align: center;
        }
        .billing-address-copy { margin-bottom: 15px; font-size: 0.9em; display:flex; align-items:center; }
    </style>

    <%-- PayPal JavaScript SDK --%>
    <script src="https://www.paypal.com/sdk/js?client-id=AVvNXTc0v0M4aRIwgNKSEtQFmTc06PnQLHtQxBeIw0hUAi-jwDO-rSdMJB_KxVp05bvrdfCnnXJR7rTC&components=hosted-fields"></script>

</head>
<body>
    <div class="signup-container">
        <div class="signup-header">
            <a href="index.jsp" class="logo-link"><i class="fas fa-clock"></i> YourTimeClock</a>
            <h1>Create Your Company Account</h1>
            <p>Step 1: Company, Admin, Payment & Agreement</p> 
        </div>

        <%-- Display error message from server-side validation or processing (from servlet) --%>
        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
            <div class="error-message" style="display:block !important;"><%= escapeForHTML(errorMessage) %></div>
        <% } %>
        <%-- This div is specifically for CLIENT-SIDE PayPal card errors and other JS validations --%>
        <div id="paypal-card-errors" role="alert" class="error-message" style="display:none;"></div>


        <form action="SignupServlet" method="POST" id="companySignupForm" class="signup-form">
            <input type="hidden" name="action" value="registerCompanyAdmin">
            <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneIdField" value="">

            <fieldset>
                <legend>Company Information</legend>
                <div class="form-row">
                    <div class="form-group half-width">
                        <label for="companyName">Company Name <span class="required">*</span></label>
                        <input type="text" id="companyName" name="companyName" value="<%=companyNameValue%>" required maxlength="100" autofocus>
                    </div>
                    <div class="form-group half-width">
                        <label for="companyPhone">Company Phone</label>
                        <input type="text" id="companyPhone" name="companyPhone" value="<%=companyPhoneValue%>" placeholder="e.g., (555) 123-4567" maxlength="20">
                        <small>Optional. Numbers and symbols like ( ) - + allowed.</small>
                    </div>
                </div>
                <div class="form-group">
                    <label for="companyAddress">Street Address</label>
                    <input type="text" id="companyAddress" name="companyAddress" value="<%=companyAddressValue%>" maxlength="255">
                </div>
                <div class="form-row">
                    <div class="form-group third-width">
                        <label for="companyCity">City</label>
                        <input type="text" id="companyCity" name="companyCity" value="<%=companyCityValue%>" maxlength="100">
                    </div>
                    <div class="form-group third-width">
                        <label for="companyState">State/Province</label>
                        <select id="companyState" name="companyState">
                            <option value="" <%="".equals(companyStateValue) ? "selected" : ""%>>Select State</option>
                            <%@ include file="/WEB-INF/includes/states_options.jspf" %>
                        </select>
                    </div>
                    <div class="form-group third-width">
                        <label for="companyZip">Zip/Postal Code</label>
                        <input type="text" id="companyZip" name="companyZip" value="<%=companyZipValue%>" maxlength="20">
                    </div>
                </div>
            </fieldset>

            <fieldset>
                <legend>Your Administrator Account</legend>
                <p class="fieldset-description">This will be the primary administrator for your company.</p>
                <div class="form-row">
                    <div class="form-group half-width">
                        <label for="adminFirstName">First Name <span class="required">*</span></label>
                        <input type="text" id="adminFirstName" name="adminFirstName" value="<%=adminFirstNameValue%>" required maxlength="50">
                    </div>
                    <div class="form-group half-width">
                        <label for="adminLastName">Last Name <span class="required">*</span></label>
                        <input type="text" id="adminLastName" name="adminLastName" value="<%=adminLastNameValue%>" required maxlength="50">
                    </div>
                </div>
                <div class="form-group">
                    <label for="adminEmail">Email Address (this will be your login) <span class="required">*</span></label>
                    <input type="email" id="adminEmail" name="adminEmail" value="<%=adminEmailValue%>" required maxlength="100">
                </div>
                <div class="form-row">
                    <div class="form-group half-width">
                        <label for="adminPassword">Administrator Password <span class="required">*</span></label>
                        <input type="password" id="adminPassword" name="adminPassword" required minlength="8" autocomplete="new-password">
                        <small>Minimum 8 characters. For company account settings.</small>
                    </div>
                    <div class="form-group half-width">
                        <label for="adminConfirmPassword">Confirm Password <span class="required">*</span></label>
                        <input type="password" id="adminConfirmPassword" name="adminConfirmPassword" required autocomplete="new-password">
                    </div>
                </div>
            </fieldset>

            <fieldset>
                <legend>Payment Information</legend>
                <p class="fieldset-description">Securely enter your payment details for the <strong>$19.99/month</strong> subscription. Processed by PayPal.</p>
                <div class="form-group">
                    <label for="cardholderName">Cardholder Name <span class="required">*</span></label>
                    <input type="text" id="cardholderName" name="cardholderName" value="<%=cardholderNameValue%>" required maxlength="100" autocomplete="cc-name">
                </div>
                
                <label class="paypal-hosted-field-label">Card Number <span class="required">*</span></label>
                <div id="paypal-card-number" class="form-control-paypal"></div>

                <div class="form-row">
                    <div class="form-group half-width">
                        <label class="paypal-hosted-field-label">Expiration Date <span class="required">*</span></label>
                        <div id="paypal-card-expiry" class="form-control-paypal"></div>
                    </div>
                    <div class="form-group half-width">
                        <label class="paypal-hosted-field-label">CVV <span class="required">*</span></label>
                        <div id="paypal-card-cvv" class="form-control-paypal"></div>
                    </div>
                </div>
                <%-- Client-side PayPal card errors will appear in the #paypal-card-errors div at the top of the form --%>

                <p class="payment-info-note">Your card details are securely processed by PayPal. We do not store your full card number.</p>

                <div class="billing-address-copy">
                    <input type="checkbox" id="copyCompanyAddress" name="copyCompanyAddress" <% if(copyAddressCheckedValue) out.print("checked"); %>>
                    <label for="copyCompanyAddress">Billing address is the same as company address.</label>
                </div>

                <div class="form-group">
                    <label for="billingAddress">Billing Street Address <span class="required">*</span></label>
                    <input type="text" id="billingAddress" name="billingAddress" value="<%=billingAddressValue%>" required maxlength="255" autocomplete="street-address">
                </div>
                <div class="form-row">
                    <div class="form-group third-width">
                        <label for="billingCity">Billing City <span class="required">*</span></label>
                        <input type="text" id="billingCity" name="billingCity" value="<%=billingCityValue%>" required maxlength="100" autocomplete="address-level2">
                    </div>
                    <div class="form-group third-width">
                        <label for="billingState">Billing State/Province <span class="required">*</span></label>
                        <select id="billingState" name="billingState" required autocomplete="address-level1">
                            <option value="" <%="".equals(billingStateValue) ? "selected" : ""%>>Select State</option>
                            <%@ include file="/WEB-INF/includes/states_options.jspf" %>
                        </select>
                    </div>
                    <div class="form-group third-width">
                        <label for="billingZip">Billing Zip/Postal Code <span class="required">*</span></label>
                        <input type="text" id="billingZip" name="billingZip" value="<%=billingZipValue%>" required maxlength="20" autocomplete="postal-code">
                    </div>
                </div>
            </fieldset>

            <fieldset>
                <legend>Legal Agreement</legend>
                <div class="disclaimer-box" tabindex="0">
                    <p><strong>Terms of Service & Privacy Policy Summary:</strong></p>
                    <p>Welcome to YourTimeClock! By creating an account, you agree to our full Terms of Service and Privacy Policy (links to be provided). In summary:</p>
                    <ul>
                        <li>You are responsible for the accuracy of the data you and your employees enter.</li>
                        <li>We strive to protect your data using industry-standard security measures.</li>
                        <li>Subscription is $19.99 USD per month, billed automatically. You can cancel anytime, effective at the end of the current billing period.</li>
                        <li>We are not responsible for errors in payroll resulting from incorrect data entry or configuration.</li>
                        <li>Service is provided "as-is" without warranties of any kind.</li>
                        <li>Your data will be processed in accordance with our Privacy Policy. We do not sell your personal data to third parties for their marketing purposes.</li>
                        <li>This is a placeholder. The actual legal text should be provided by a legal professional.</li>
                    </ul>
                    <p><em>Please scroll and read the full (placeholder) disclaimer.</em></p>
                </div>
                <div class="form-group">
                    <label for="acceptTerms" class="accept-terms-label">
                        <input type="checkbox" id="acceptTerms" name="acceptTerms" value="true" <% if(termsAcceptedValue) out.print("checked"); %>>
                        I have read and agree to the Legal Disclaimer, Terms of Service, and Privacy Policy. <span class="required">*</span>
                    </label>
                </div>
            </fieldset>

            <div class="form-actions">
                <button type="submit" class="btn btn-primary btn-large">Create Account & Start Subscription <i class="fas fa-arrow-right"></i></button>
            </div>
        </form>
        <p class="login-redirect">Already have an account? <a href="login.jsp">Log In</a></p>
    </div>
    
    <%-- Ensure common-scripts.jspf is loaded BEFORE signup_validation.js if it defines populateTimeZone --%>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %> 
    <script src="js/signup_validation.js?v=<%= System.currentTimeMillis() %>"></script>

</body>
</html>