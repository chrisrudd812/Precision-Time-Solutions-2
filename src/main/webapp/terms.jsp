<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    String publicParam = request.getParameter("public");
    boolean isPublicView = "true".equalsIgnoreCase(publicParam);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Terms of Service - Precision Time Solutions</title>
    
    <%-- Include main CSS files for consistent styling --%>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>

    <style>
        /* Styles for a clean, readable content page */
        .content-container {
            padding: 20px 40px;
            max-width: 900px;
            margin: 20px auto;
            background-color: #fff;
            border-radius: 8px;
            box-shadow: 0 2px 10px rgba(0,0,0,0.05);
        }
        .content-container h1 {
            text-align: center;
            border-bottom: 1px solid #e2e8f0;
            padding-bottom: 15px;
            margin-bottom: 25px;
            color: #1e293b;
        }
        .content-container h2 {
            font-size: 1.4em;
            color: #0f766e;
            margin-top: 30px;
            margin-bottom: 15px;
            border-bottom: 1px solid #f1f5f9;
            padding-bottom: 5px;
        }
        .content-container p, .content-container li {
            line-height: 1.7;
            color: #334155;
            text-align: justify;
        }
        .content-container ul {
            padding-left: 25px;
        }
        .disclaimer-box {
            background-color: #fff3cd;
            border-left: 4px solid #ffc107;
            padding: 15px 20px;
            margin-bottom: 30px;
            border-radius: 4px;
        }
        .disclaimer-box p {
            margin: 0;
            font-weight: 500;
            color: #856404;
        }
    </style>
</head>
<body class="reports-page<% if (isPublicView) { %> no-navbar<% } %>">

    <% if (!isPublicView) { %>
        <%-- Include the standard navigation bar --%>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <%-- Include common JavaScript files for navbar functionality --%>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>

    <div class="content-container">
        <h1>Terms of Service</h1>

        <p><strong>Last Updated:</strong> August 28, 2024</p>

        <p>Welcome to Precision Time Solutions! These Terms of Service ("Terms") govern your use of our time tracking, employee management, and payroll services (collectively, the "Service"), provided by Precision Time Solutions ("we," "us," or "our").</p>

        <p>By signing up for or using the Service, you agree to be bound by these Terms. If you do not agree to these Terms, do not use the Service.</p>

        <h2>1. Description of Service</h2>
        <p>Precision Time Solutions is a cloud-based software-as-a-service application designed to help businesses manage employee time tracking, scheduling, accruals, and payroll processing. The features available to you depend on your selected subscription plan.</p>

        <h2>2. User Accounts</h2>
        <p>To use the Service, you must register for a company account and create an administrator account. You are responsible for all activities that occur under your account. You agree to:</p>
        <ul>
            <li>Provide accurate and complete information during registration.</li>
            <li>Maintain the security of your password and administrator credentials.</li>
            <li>Notify us immediately of any unauthorized use of your account.</li>
        </ul>

        <h2>3. Subscriptions and Payment</h2>
        <p>The Service is billed on a subscription basis. You will be billed in advance on a recurring, periodic basis (typically monthly). By providing a payment method, you authorize us to charge you the subscription fees for your selected plan.</p>
        <p>Subscription fees are non-refundable except as required by law. Subscription will remain active until next billing period. We reserve the right to change our prices at any time, and we will provide you with reasonable notice of any price changes.</p>

        <h2>4. Acceptable Use</h2>
        <p>You agree not to use the Service for any unlawful purpose or in any way that could harm, disable, overburden, or impair the Service. Prohibited actions include, but are not limited to:</p>
        <ul>
            <li>Uploading or transmitting viruses or any other malicious code.</li>
            <li>Attempting to gain unauthorized access to our systems or other user accounts.</li>
            <li>Using the service to store or transmit any material that is fraudulent, libelous, or otherwise objectionable.</li>
        </ul>

        <h2>5. Termination</h2>
        <p>You may terminate your account at any time by contacting us. We may also suspend or terminate your account if you breach these Terms. Upon termination, your right to use the Service will immediately cease, but you will remain responsible for any outstanding fees.</p>

        <h2>6. Disclaimers and Limitation of Liability</h2>
        <p>The Service is provided on an "AS IS" and "AS AVAILABLE" basis. We do not warrant that the Service will be uninterrupted, error-free, or completely secure. To the maximum extent permitted by law, we shall not be liable for any indirect, incidental, special, consequential, or punitive damages resulting from your use of the Service.</p>

        <h2>7. Governing Law</h2>
        <p>These Terms shall be governed by the laws of the State of [Your State], United States, without regard to its conflict of law provisions.</p>

        <h2>8. Changes to Terms</h2>
        <p>We reserve the right to modify these Terms at any time. We will provide notice of any significant changes. Your continued use of the Service after such changes constitutes your acceptance of the new Terms.</p>

        <h2>9. Contact Us</h2>
        <p>If you have any questions about these Terms, please contact us <a href="<%= request.getContextPath() %>/contact.jsp?public=true">here</a>.</p>

    </div>
</body>
</html>