<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%
    String publicParam = request.getParameter("public");
    boolean isPublicView = "true".equalsIgnoreCase(publicParam);
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Contact Us</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>

    <style>
        /* --- Page Specific Styles --- */
        .contact-section {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 20px;
            margin-top: 20px;
            background-color: #ffffff;
            padding: 25px;
            border-radius: 8px;
        }
        .contact-info {
            text-align: center;
        }
        .contact-info h2 {
            font-size: 1.4em;
            color: #0f766e;
            margin-top: 0;
            margin-bottom: 15px;
            font-weight: 600;
        }
        .contact-info p {
            font-size: 0.95em;
            color: #475569;
            line-height: 1.7;
            max-width: 650px;
            margin-left: auto;
            margin-right: auto;
        }
        .contact-form-container {
            width: 100%;
            max-width: 600px;
        }
        #contactMessage {
            height: 250px;
            min-height: 150px;
            width: 100%;
        }
        textarea {
            font-family: inherit;
            font-size: .95em;
            line-height: 1.5;
        }
        /* File Input Styles */
        .file-input-wrapper { 
            border: 2px dashed #cbd5e0; 
            border-radius: 6px; 
            padding: 20px; 
            text-align: center; 
            cursor: pointer; 
            background-color: #f8fafc; 
            transition: background-color 0.2s, border-color 0.2s;
        }
        .file-input-wrapper:hover {
            background-color: #f1f5f9;
            border-color: #94a3b8;
        }
        #fileAttachment { display: none; }
        #fileNameDisplay { margin-top: 10px; font-style: italic; color: #475569; }
        /* Toggle Switch Styles */
        .toggle-switch-container {
            display: flex;
            background-color: #e2e8f0;
            border-radius: 20px;
            padding: 4px;
            margin-bottom: 25px;
            position: relative;
            user-select: none;
        }
        .toggle-switch-container input[type="radio"] { display: none; }
        .toggle-switch-container label {
            flex: 1;
            text-align: center;
            padding: 8px 12px;
            font-weight: 500;
            color: #475569;
            cursor: pointer;
            z-index: 10;
            transition: color 0.3s ease;
        }
        .toggle-switch-container input[type="radio"]:checked + label { color: #ffffff; }
        .toggle-glider {
            position: absolute;
            top: 4px;
            left: 4px;
            height: calc(100% - 8px);
            width: calc(50% - 4px);
            background-color: #14b8a6;
            border-radius: 16px;
            box-shadow: 0 1px 3px rgba(0,0,0,0.1);
            transition: transform 0.3s ease;
            z-index: 5;
        }
        #requestTypeFeedback:checked ~ .toggle-glider { transform: translateX(100%); }
        
        /* Mobile Styles */
        @media (max-width: 480px) {
            body {
                padding: 0 !important;
            }
            
            .parent-container {
                width: calc(100vw - 20px) !important;
                max-width: none !important;
                margin-top: 80px !important;
                margin-right: 10px !important;
                margin-bottom: 10px !important;
                margin-left: 10px !important;
                padding: 0 !important;
                box-sizing: border-box !important;
                position: relative !important;
                left: 0 !important;
                right: 0 !important;
            }
            
            .contact-section {
                width: 100% !important;
                max-width: none !important;
                margin: 10px 0 !important;
                padding: 15px !important;
                box-sizing: border-box !important;
            }
            
            .contact-form-container {
                width: 100% !important;
                max-width: none !important;
                padding: 0 15px !important;
                box-sizing: border-box !important;
            }
            
            h1 {
                font-size: 1.3em !important;
                text-align: center !important;
                margin: 10px 0 !important;
                padding: 10px !important;
            }
            
            .contact-info h2 {
                font-size: 1.2em !important;
            }
        }
    </style>
</head>
<body class="reports-page<% if (isPublicView) { %> no-navbar<% } %>">
    <% if (!isPublicView) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="parent-container">
        <h1>Get In Touch</h1>
        
        <div class="contact-section">
            <div class="contact-info">
                <h2>How can we help?</h2>
                <p>Please select the nature of your inquiry, fill out the form, and attach a screenshot if applicable. We aim to respond to all support requests within 24-48 hours.</p>
            </div>
            
            <div class="contact-form-container">
                <form id="contactForm" class="contact-form" action="ContactServlet" method="post" enctype="multipart/form-data">
                    <div class="toggle-switch-container">
                        <input type="radio" id="requestTypeSupport" name="requestType" value="support" checked>
                        <label for="requestTypeSupport">Create Support Request</label>
                        <input type="radio" id="requestTypeFeedback" name="requestType" value="feedback">
                        <label for="requestTypeFeedback">Questions / Feedback</label>
                        <div class="toggle-glider"></div>
                    </div>
                    <div class="form-group">
                        <label for="contactSubject">Subject</label>
                        <input type="text" id="contactSubject" name="contactSubject" required>
                    </div>
                    <div class="form-group">
                        <label for="contactMessage" style="padding-top: 25px">Message</label>
                        <textarea id="contactMessage" name="contactMessage" required></textarea>
                    </div>

                    <div class="form-group">
                        <label style="padding-top: 25px">Attach a Screenshot (Optional)</label>
                        <label for="fileAttachment" class="file-input-wrapper"">
                            <div><i class="fas fa-cloud-upload-alt"></i> Click to browse or drag & drop a file</div>
                            <div id="fileNameDisplay">No file selected</div>
                        </label>
                        <input type="file" id="fileAttachment" name="fileAttachment" accept="image/*,.pdf,.doc,.docx,.txt">
                    </div>
                    
                    <div class="form-group">
                        <button type="submit" class="glossy-button text-blue" style="width: 100%; margin-top: 15px;">
                            <i class="fas fa-paper-plane"></i> Send Message
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/contact.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>