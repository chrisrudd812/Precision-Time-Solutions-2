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
    String planName = "";

    if (priceId == null || priceId.trim().isEmpty()) {
        // Default to a starter plan if no priceId is provided in the URL
        priceId = "price_1RWNdXBtvyYfb2KWWt6p9F4X"; 
    }

    String sql = "SELECT planName, price, maxUsers, features FROM subscription_plans WHERE stripePriceId = ?";
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql)) {
        pstmt.setString(1, priceId);
        try (ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                planName = rs.getString("planName");
                plan.put("name", planName);
                plan.put("price", rs.getDouble("price"));
                plan.put("users", rs.getInt("maxUsers"));
                plan.put("features", rs.getString("features"));
            } else {
                response.sendRedirect("index.jsp?error=invalid_plan");
                return;
            }
        }
    } catch (Exception e) {
        errorMessage = "Could not load subscription plan details.";
        e.printStackTrace();
    }
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
        .stripe-element { box-sizing: border-box; height: 40px; padding: 10px 12px; border: 1px solid #ccc; border-radius: 4px; background-color: white; }
        .stripe-elements-row { display: flex; gap: 10px; margin-top: 10px; }
        .stripe-elements-row > div { flex: 1; }
        .promo-container { display: flex; gap: 10px; align-items: flex-start; }
        .promo-container .form-group { flex-grow: 1; margin-bottom: 0; }
        .form-check { margin-bottom: 15px; display: flex; align-items: center; }
        #sameAsCompanyAddress { width: 20px; height: 20px; margin-right: 10px; }
        #sameAsCompanyAddress:checked { accent-color: #28a745; }
        .is-invalid { border-color: #dc3545 !important; }
    </style>
</head>
<body>
    <div class="signup-container">
        <div class="signup-header"><h1>Create Your Company Account</h1></div>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
             <div class="error-message"><%= escapeHtml(errorMessage) %></div>
        <% } %>

        <form action="SignupServlet" method="POST" id="companySignupForm" class="signup-form" novalidate>
            <input type="hidden" name="action" value="registerCompanyAdmin">
            <input type="hidden" name="browserTimeZoneId" id="browserTimeZoneIdField" value="">
            <input type="hidden" name="stripePaymentMethodId" id="stripePaymentMethodId">
            <input type="hidden" name="appliedPromoCode" id="appliedPromoCode">
            <input type="hidden" name="selectedPlan" value="<%= escapeHtml(planName) %>">

            <fieldset><legend>1. Your Selected Plan</legend>
                <div class="plan-confirmation-box">
                    <span class="plan-name"><%= plan.getString("name") %></span>
                    <span class="plan-price">$<%= String.format("%.2f", plan.getDouble("price")) %>/mo</span>
                    <span class="plan-features"><%= plan.getString("features") %></span>
                    <a href="index.jsp#pricing" class="change-plan-link">Change Plan</a>
                </div>
            </fieldset>

            <fieldset><legend>2. Company Information</legend>
                <div class="form-group"><label for="companyName">Company Name <span class="required">*</span></label><input type="text" id="companyName" name="companyName" required autofocus></div>
                <div class="form-row">
                    <div class="form-group half-width"><label for="companyAddress">Street Address</label><input type="text" id="companyAddress" name="companyAddress"></div>
                    <div class="form-group half-width"><label for="companyPhone">Company Phone</label><input type="tel" id="companyPhone" name="companyPhone" placeholder="(555) 555-5555"></div>
                </div>
                <div class="form-row">
                    <div class="form-group third-width"><label for="companyCity">City</label><input type="text" id="companyCity" name="companyCity"></div>
                    <div class="form-group third-width"><label for="companyState">State <span class="required">*</span></label><select id="companyState" name="companyState" required><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div>
                    <div class="form-group third-width"><label for="companyZip">Zip Code</label><input type="text" id="companyZip" name="companyZip" placeholder="12345" maxlength="5"></div>
                </div>
            </fieldset>

            <fieldset><legend>3. Your Administrator Account</legend>
                <div class="form-row">
                    <div class="form-group half-width"><label for="adminFirstName">First Name <span class="required">*</span></label><input type="text" id="adminFirstName" name="adminFirstName" required></div>
                    <div class="form-group half-width"><label for="adminLastName">Last Name <span class="required">*</span></label><input type="text" id="adminLastName" name="adminLastName" required></div>
                </div>
                <div class="form-group"><label for="adminEmail">Email Address (this will be your login) <span class="required">*</span></label><input type="email" id="adminEmail" name="adminEmail" required></div>
                <div class="form-row">
                    <div class="form-group half-width"><label for="adminPassword">Password (min 8 characters) <span class="required">*</span></label><input type="password" id="adminPassword" name="adminPassword" required minlength="8"></div>
                    <div class="form-group half-width"><label for="adminConfirmPassword">Confirm Password <span class="required">*</span></label><input type="password" id="adminConfirmPassword" name="adminConfirmPassword" required></div>
                </div>
            </fieldset>

            <fieldset><legend>4. Have a Promo Code?</legend>
                <div class="promo-container">
                    <div class="form-group">
                        <label for="promoCodeInput" class="sr-only">Promo Code</label>
                        <input type="text" id="promoCodeInput" placeholder="Enter code">
                    </div>
                    <button type="button" id="validatePromoButton" class="btn btn-primary" disabled>Apply</button>
                </div>
                <div id="promo-status" class="promo-status"></div>
            </fieldset>

            <div id="payment-section">
                <fieldset><legend>5. Payment Information</legend>
                    <div class="form-group"><label for="cardholderName">Name on Card <span class="required">*</span></label><input type="text" id="cardholderName" name="cardholderName" required></div>
                    <div class="form-check">
                        <input class="form-check-input" type="checkbox" id="sameAsCompanyAddress">
                        <label class="form-check-label" for="sameAsCompanyAddress">Billing address is the same as company address</label>
                    </div>
                    <div class="form-group"><label for="billingAddress">Billing Address <span class="required">*</span></label><input type="text" id="billingAddress" name="billingAddress" required></div>
                     <div class="form-row">
                        <div class="form-group third-width"><label for="billingCity">City <span class="required">*</span></label><input type="text" id="billingCity" name="billingCity" required></div>
                        <div class="form-group third-width"><label for="billingState">State <span class="required">*</span></label><select id="billingState" name="billingState" required><%@ include file="/WEB-INF/includes/states_options.jspf" %></select></div>
                        <div class="form-group third-width"><label for="billingZip">Zip <span class="required">*</span></label><input type="text" id="billingZip" name="billingZip" required placeholder="12345" maxlength="5"></div>
                    </div>
                    <div class="form-group">
                        <label for="card-number">Card Details <span class="required">*</span></label>
                        <div id="card-number" class="stripe-element"></div>
                        <div class="stripe-elements-row">
                            <div id="card-expiry" class="stripe-element"></div>
                            <div id="card-cvc" class="stripe-element"></div>
                        </div>
                    </div>
                </fieldset>
            </div>
            
            <div id="card-errors" role="alert" class="error-message" style="display:none;"></div>
            <div class="form-actions"><button type="submit" id="mainSubmitButton" class="btn btn-primary btn-large">Create Account <i class="fas fa-arrow-right"></i></button></div>
        </form>
    </div>
    
    <script src="https://js.stripe.com/v3/"></script>
    <script> const appRootPath = "<%= request.getContextPath() %>"; </script>
    <script src="js/signup_validation.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>