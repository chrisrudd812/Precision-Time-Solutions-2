<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.net.URLEncoder, java.nio.charset.StandardCharsets, jakarta.servlet.http.HttpSession, java.sql.*, org.json.*, timeclock.db.DatabaseConnection" %>

<%!
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    String errorMessage = request.getParameter("error");
    String priceId = request.getParameter("priceId");
    JSONObject plan = new JSONObject();

    if (priceId == null || priceId.trim().isEmpty()) {
        priceId = "price_1RWNdXBtvyYfb2KWWt6p9F4X"; 
    }

    // Quick hardcoded plan data to avoid database query delay
    if ("price_1RWNdXBtvyYfb2KWWt6p9F4X".equals(priceId)) {
        plan.put("name", "Starter");
        plan.put("price", 19.99);
        plan.put("users", 25);
    } else if ("price_1RttGyBtvyYfb2KWNRui8ev1".equals(priceId)) {
        plan.put("name", "Business");
        plan.put("price", 29.99);
        plan.put("users", 50);
    } else if ("price_1RttIyBtvyYfb2KW86IvsAvX".equals(priceId)) {
        plan.put("name", "Pro");
        plan.put("price", 39.99);
        plan.put("users", 100);
    } else {
        response.sendRedirect("index.jsp?error=invalid_plan");
        return;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Create Company Account - Precision Time Solutions</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/signup.css?v=<%= System.currentTimeMillis() %>">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
</head>
<body>
    <div class="parent-container signup-container">
        <h1><i class="fas fa-user-plus"></i> Create Your Company Account</h1>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
             <div class="page-message error-message"><%= escapeHtml(errorMessage) %></div>
        <% } %>

        <form action="SignupServlet" method="POST" id="companySignupForm" class="signup-form" novalidate>
            <input type="hidden" name="action" value="registerCompanyAdmin">
            <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneIdField" value="">
            <input type="hidden" name="stripePaymentMethodId" id="stripePaymentMethodId">
            <input type="hidden" name="appliedPromoCode" id="appliedPromoCode">
            <input type="hidden" name="stripePriceId" value="<%= escapeHtml(priceId) %>">

            <h4 class="section-heading"><i class="fas fa-check-circle"></i> Your Selected Plan</h4>
            <div class="form-body-container">
                <div class="plan-confirmation-box">
                    <div class="plan-header">
                        <div class="plan-badge">
                            <i class="fas fa-star"></i>
                            <span class="plan-name"><%= plan.getString("name").equals("Starter") ? "Basic" : plan.getString("name") %></span>
                        </div>
                        <span class="plan-price">$<%= String.format("%.2f", plan.getDouble("price")) %><span class="price-period">/month</span></span>
                    </div>
                    <div class="plan-details">
                        <span class="plan-features"><%
                            String displayName = plan.getString("name").equals("Starter") ? "Basic" : plan.getString("name");
                            String features = "";
                            if (displayName.equals("Basic")) {
                                features = "Up to 25 Active Users, Basic Reporting, Email Support, Payroll Processing and PTO Tracking";
                            } else if (displayName.equals("Business")) {
                                features = "Everything from Basic plus: Up to 50 Active Employees, Messaging Module, Additional Reports";
                            } else if (displayName.equals("Pro")) {
                                features = "Everything from Business plus: Up to 100 Active Employees, Multi-State Operations with Location-Based Overtime, Priority Support";
                            } else {
                                features = plan.getString("features");
                            }
                        %><%= features %></span>
                    </div>
                </div>
            </div>

            <h4 class="section-heading"><i class="fas fa-building"></i> Company Information</h4>
            <div class="form-body-container">
                <div class="form-row">
                    <div class="form-group half-width"><label for="companyName">Company Name <span class="required">*</span></label><input type="text" id="companyName" name="companyName" required autofocus></div>
                    <div class="form-group half-width">
                        <label for="companyPhone">Company Phone</label>
                        <input type="tel" id="companyPhone" name="companyPhone" placeholder="(555) 555-5555" pattern="^\(\d{3}\) \d{3}-\d{4}$" title="Phone number must be in the format (555) 555-5555.">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group address-field"><label for="companyAddress">Street Address</label><input type="text" id="companyAddress" name="companyAddress" autocomplete="off"></div>
                    <div class="form-group city-field"><label for="companyCity">City</label><input type="text" id="companyCity" name="companyCity" autocomplete="off"></div>
                    <div class="form-group state-field"><label for="companyState">State <span class="required">*</span></label><select id="companyState" name="companyState" autocomplete="off" required><option value="" selected disabled>Select a State</option><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div>
                    <div class="form-group zip-field"><label for="companyZip">Zip Code</label><input type="text" id="companyZip" name="companyZip" autocomplete="off" pattern="\d{5}|\d{5}-\d{4}" title="Zip code must be 5 or 9 digits (e.g., 12345 or 12345-6789)."></div>
                </div>
            </div>

            <h4 class="section-heading"><i class="fas fa-user-shield"></i> Your Administrator Account</h4>
            <div class="form-body-container">
                <div class="form-row">
                    <div class="form-group half-width"><label for="adminFirstName">First Name <span class="required">*</span></label><input type="text" id="adminFirstName" name="adminFirstName" required></div>
                    <div class="form-group half-width"><label for="adminLastName">Last Name <span class="required">*</span></label><input type="text" id="adminLastName" name="adminLastName" required></div>
                </div>
                <div class="form-group"><label for="adminEmail">Email Address (this will be your login) <span class="required">*</span></label><input type="email" id="adminEmail" name="adminEmail" required></div>
                <div class="form-row">
                    <div class="form-group half-width"><label for="adminPassword">Create Company Admin Password <span class="required">*</span></label><input type="password" id="adminPassword" name="adminPassword" required></div>
                    <div class="form-group half-width"><label for="adminConfirmPassword">Confirm Password <span class="required">*</span></label><input type="password" id="adminConfirmPassword" name="adminConfirmPassword" required></div>
                </div>
            </div>

            <h4 class="section-heading"><i class="fas fa-tag"></i> Have a Promo Code?</h4>
            <div class="form-body-container">
                <div class="promo-container">
                    <div class="form-group">
                        <label for="promoCodeInput" class="sr-only">Promo Code</label>
                        <input type="text" id="promoCodeInput" placeholder="Enter code">
                    </div>
                    <button type="button" id="validatePromoButton" class="glossy-button text-blue" disabled>Apply</button>
                </div>
                <div id="promo-status" class="promo-status"></div>
            </div>
            
            <%@ include file="/WEB-INF/includes/modals.jspf" %>
            
            <script>
                document.getElementById('promoCodeInput').addEventListener('input', function() {
                    const button = document.getElementById('validatePromoButton');
                    button.disabled = this.value.trim() === '';
                });
                
                document.getElementById('validatePromoButton').addEventListener('click', function() {
                    const promoCode = document.getElementById('promoCodeInput').value.trim();
                    const status = document.getElementById('promo-status');
                    
                    if (promoCode.toLowerCase() === 'altman55') {
                        // Upgrade to Pro plan
                        document.querySelector('input[name="stripePriceId"]').value = 'price_1RttIyBtvyYfb2KW86IvsAvX';
                        document.querySelector('input[name="appliedPromoCode"]').value = promoCode;
                        
                        // Update display to show FREE
                        document.querySelector('.plan-name').textContent = 'Pro';
                        document.querySelector('.plan-price').innerHTML = 'FREE<span class="price-period"> (Promo)</span>';
                        document.querySelector('.plan-features').textContent = 'Everything from Business plus: Up to 100 Active Employees, Multi-State Operations with Location-Based Overtime, Priority Support';
                        
                        // Hide payment section and remove required attributes
                        const paymentSection = document.getElementById('payment-section');
                        paymentSection.style.display = 'none';
                        
                        // Remove required attributes from payment fields
                        const requiredFields = paymentSection.querySelectorAll('[required]');
                        requiredFields.forEach(field => field.removeAttribute('required'));
                        
                        status.innerHTML = '<span style="color: green;"><i class="fas fa-check"></i> Promo code applied! Free Pro plan access.</span>';
                        this.disabled = true;
                        document.getElementById('promoCodeInput').disabled = true;
                    } else {
                        status.innerHTML = '<span style="color: red;"><i class="fas fa-times"></i> Invalid promo code.</span>';
                    }
                });
            </script>

            <div id="payment-section">
                <h4 class="section-heading"><i class="fas fa-credit-card"></i> Payment Information</h4>
                <div class="form-body-container">
                    <div class="form-row">
                        <div class="form-group third-width"><label for="cardholderName">Name on Card <span class="required">*</span></label><input type="text" id="cardholderName" name="cardholderName" required></div>
                        <div class="form-group third-width">
                            <label for="card-number">Card Number <span class="required">*</span></label>
                            <div id="card-number" class="stripe-element"></div>
                        </div>
                        <div class="form-group third-width">
                            <label>Expiry & CVC <span class="required">*</span></label>
                            <div class="stripe-elements-row">
                                <div id="card-expiry" class="stripe-element"></div>
                                <div id="card-cvc" class="stripe-element"></div>
                            </div>
                        </div>
                    </div>
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" id="sameAsCompanyAddress">
                        <label class="form-check-label" for="sameAsCompanyAddress">Billing address is the same as company address</label>
                    </div>
                    <div class="form-row">
                        <div class="form-group address-field"><label for="billingAddress">Billing Address <span class="required">*</span></label><input type="text" id="billingAddress" name="billingAddress" required></div>
                        <div class="form-group city-field"><label for="billingCity">City <span class="required">*</span></label><input type="text" id="billingCity" name="billingCity" required></div>
                        <div class="form-group state-field"><label for="billingState">State <span class="required">*</span></label><select id="billingState" name="billingState" required><option value="" selected disabled>Select a State</option><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div>
                        <div class="form-group zip-field"><label for="billingZip">Zip <span class="required">*</span></label><input type="text" id="billingZip" name="billingZip" required pattern="\d{5}|\d{5}-\d{4}" title="Zip code must be 5 or 9 digits (e.g., 12345 or 12345-6789)."></div>
                    </div>

                </div>
            </div>
            
            <div id="card-errors" role="alert" class="page-message error-message" style="display:none; margin-top: 20px;"></div>
            
             <div class="form-actions">
                <div class="terms-agreement">
                     <input class="form-check-input" type="checkbox" id="termsAgreement" name="termsAgreement" required>
                     <label for="termsAgreement" class="terms-notice">
                        I agree to the <a href="terms.jsp" tabindex="-1" target="_blank">Terms of Service</a> and <a href="privacy.jsp" tabindex="-1" target="_blank">Privacy Policy</a>.
                     </label>
                </div>
                <button type="submit" id="mainSubmitButton" class="glossy-button text-green" style="width:100%; height: 45px; font-size: 1.1em;">Create Account <i class="fas fa-arrow-right"></i></button>
            </div>
         </form>
    </div>
    
    <script src="https://js.stripe.com/v3/"></script>
    
    <script>
        const STRIPE_PUBLISHABLE_KEY = "<%= System.getenv("STRIPE_PUBLISHABLE_KEY") %>";
        const appRootPath = "<%= request.getContextPath() %>";
    </script>
    <script src="js/signup_validation.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>