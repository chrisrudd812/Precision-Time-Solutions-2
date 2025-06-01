<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Contact Us</title>
    <link rel="stylesheet" href="css/navbar.css">
    <link rel="stylesheet" href="css/main.css"> <%-- Link to a main CSS or create contact.css --%>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <style>
        .content-area { max-width: 800px; margin: 30px auto; padding: 20px; background: #fff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); }
        .content-area h1 { margin-top: 0; text-align: center; }
        .content-area p { line-height: 1.6; }
        /* Basic form styling */
        .contact-form label { display: block; margin: 15px 0 5px 0; font-weight: bold;}
        .contact-form input[type=text],
        .contact-form input[type=email],
        .contact-form textarea { width: 100%; padding: 10px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; }
        .contact-form textarea { height: 150px; resize: vertical; }
        .contact-form button { background-color: #007bff; color: white; padding: 12px 20px; border: none; border-radius: 4px; cursor: pointer; font-size: 1em; margin-top: 15px; }
        .contact-form button:hover { background-color: #0056b3; }
    </style>
</head>
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="content-area parent-container">
        <h1>Contact Us</h1>
        <p>If you need assistance or have questions about the Time Clock application, please fill out the form below or contact support directly.</p>
        <hr style="margin: 20px 0;">

        <%-- Placeholder Contact Form (Does not submit anywhere yet) --%>
        <form action="#" method="post" class="contact-form" onsubmit="alert('Contact form submission is currently disabled.'); return false;">
             <label for="contactName">Your Name:</label>
             <input type="text" id="contactName" name="contactName" required>

             <label for="contactEmail">Your Email:</label>
             <input type="email" id="contactEmail" name="contactEmail" required>

             <label for="contactSubject">Subject:</label>
             <input type="text" id="contactSubject" name="contactSubject" required>

             <label for="contactMessage">Message:</label>
             <textarea id="contactMessage" name="contactMessage" required></textarea>

             <button type="submit">Send Message</button>
        </form>

        <hr style="margin: 20px 0;">
        <p>Alternatively, reach out via [Support Phone Number] or [Support Email Address].</p>

    </div>
<%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>