<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Privacy Policy - Precision Time Solutions</title>
    
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
            background-color: #f8d7da; /* Red for higher importance */
            border-left: 4px solid #dc3545;
            padding: 15px 20px;
            margin-bottom: 30px;
            border-radius: 4px;
        }
        .disclaimer-box p {
            margin: 0;
            font-weight: 500;
            color: #721c24;
        }
    </style>
</head>
<body class="reports-page">

    <%-- Include the standard navigation bar --%>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="content-container">
        <h1>Privacy Policy</h1>

        <p><strong>Last Updated:</strong> August 28, 2025</p>

        <p>This Privacy Policy describes how Precision Time Solutions ("we," "us," or "our") collects, uses, and shares information in connection with your use of our Precision Time Solutions application (the "Service").</p>

        <h2>1. Information We Collect</h2>
        <p>We collect information in the following ways:</p>
        <ul>
            <li><strong>Information You Provide:</strong> This includes information you provide when you sign up, create an account, or add employees, such as company name, administrator name and email, employee names and details, and payment information.</li>
            <li><strong>Information Collected Automatically:</strong> When you use our Service, we automatically collect certain information, including IP addresses, device "finger-prints" for security and restriction purposes, and browser time-zone information. If location restrictions are enabled, we also collect geo-location data at the time of a punch.</li>
        </ul>

        <h2>2. How We Use Your Information</h2>
        <p>We use the information we collect to:</p>
        <ul>
            <li>Provide, maintain, and improve our Service.</li>
            <li>Process transactions and send you related information, including subscription confirmations and invoices.</li>
            <li>Communicate with you, including responding to your comments, questions, and support requests.</li>
            <li>Monitor and analyze trends, usage, and activities in connection with our Service.</li>
            <li>Enforce our terms and policies and protect our Service from fraud or abuse.</li>
        </ul>

        <h2>3. How We Share Your Information</h2>
        <p>We do not sell your personal information. We may share information as follows:</p>
        <ul>
            <li>With third-party vendors and service providers that perform services for us, such as payment processing (e.g., Stripe).</li>
            <li>In response to a request for information if we believe disclosure is in accordance with, or required by, any applicable law, regulation, or legal process.</li>
            <li>If we believe your actions are inconsistent with our user agreements or policies, or to protect the rights, property, and safety of us or others.</li>
        </ul>

        <h2>4. Data Security</h2>
        <p>We implement reasonable security measures to help protect your information from loss, theft, misuse, and unauthorized access, disclosure, alteration, and destruction.</p>

        <h2>5. Data Retention</h2>
        <p>We store the information we collect for as long as it is necessary for the purpose(s) for which we originally collected it, or for other legitimate business purposes, including to meet our legal, regulatory, or other compliance obligations.</p>
        
        <h2>6. Your Rights</h2>
        <p>Depending on your location, you may have certain rights regarding your personal information, such as the right to access, correct, or delete your data. Please contact us to make such a request.</p>

        <h2>7. Changes to This Policy</h2>
        <p>We may change this Privacy Policy from time to time. If we make changes, we will notify you by revising the "Last Updated" date at the top of the policy and, in some cases, we may provide you with additional notice.</p>
        
        <h2>8. Contact Us</h2>
        <p>If you have any questions about this Privacy Policy, please contact us <a href="/contact.jsp">here</a>
        </p>

    </div>
</body>
</html>