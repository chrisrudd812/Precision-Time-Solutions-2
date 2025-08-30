<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Contact Us</title>
    <link rel="stylesheet" href="css/navbar.css">
    <link rel="stylesheet" href="css/reports.css">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <style>
        .content-area { max-width: 800px; margin: 30px auto; padding: 20px 40px; background: #fff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
        .content-area h1 { margin-top: 0; text-align: center; border-bottom: 1px solid #eee; padding-bottom: 15px; }
        .content-area p { line-height: 1.6; }
        
        /* [FIX] New, more robust styles for a clean form layout */
        .contact-form { margin-top: 20px; }
        .form-group { margin-bottom: 20px; }
        .form-group label {
            display: block;
            margin-bottom: 8px;
            font-weight: 500;
            font-size: .95em;
            color: #475569;
        }
        .form-group input[type=text],
        .form-group input[type=email],
        .form-group textarea {
            width: 100%;
            padding: 10px 12px;
            border: 1px solid #cbd5e0;
            border-radius: 4px;
            box-sizing: border-box;
            font-size: 1em;
            font-family: inherit;
        }
        .form-group input:focus, .form-group textarea:focus {
            outline: none;
            border-color: #3b82f6;
            box-shadow: 0 0 0 0.2rem rgba(59, 130, 246, 0.25);
        }
        .form-group textarea {
            height: 180px;
            resize: vertical;
        }
        .form-group button {
            width: 100%;
            min-width: 0;
        }
        
        /* Styles for the submission status message div */
        .form-status-message {
            padding: 10px 15px; border-radius: 4px; margin-bottom: 20px; text-align: center;
            border: 1px solid transparent; display: none;
        }
        .form-status-message.success { background-color: #d4edda; color: #155724; border-color: #c3e6cb; }
        .form-status-message.error { background-color: #f8d7da; color: #721c24; border-color: #f5c6cb; }
    </style>
</head>
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="content-area parent-container">
        <h1>Contact Support</h1>
        <p>If you need assistance or have questions about the Time Clock application, please fill out the form below.</p>
        <hr style="margin: 20px 0;">
        
        <div id="form-status-message"></div>
        
        <%-- [FIX] Restructured the form with div.form-group for proper alignment --%>
        <form id="contactForm" class="contact-form" action="ContactServlet" method="post">
            <div class="form-group">
                <label for="contactSubject">Subject:</label>
                <input type="text" id="contactSubject" name="contactSubject" required>
            </div>

            <div class="form-group">
                <label for="contactMessage">Message:</label>
                <textarea id="contactMessage" name="contactMessage" required></textarea>
            </div>

            <div class="form-group">
                <button type="submit" class="glossy-button text-blue" style="margin-top: 10px;">
                    <i class="fas fa-paper-plane"></i> Send Message
                </button>
            </div>
            <p>Alternatively, you can email us directly at <a href="mailto:chrisrudd812@gmail.com">chrisrudd812@gmail.com</a></p>
        </form>
    </div>

    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/contact.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>