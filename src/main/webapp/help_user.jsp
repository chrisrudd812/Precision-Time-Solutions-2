<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quick Help - Time Clock</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <style>
    
    code {color:red;}
    
        .help-container {
            max-height: 90vh;
            overflow-y: auto;
            padding: 20px;
        }
        .help-section {
            margin-bottom: 25px;
            padding: 20px;
            background: #f8f9fa;
            border-radius: 8px;
            border-left: 4px solid #14b8a6;
        }
        .help-section h2 {
            color: #0f766e;
            margin-top: 0;
        }
        .punch-buttons {
            display: flex;
            gap: 15px;
            margin: 15px 0;

        .mobile-notice {
            background: #dbeafe;
            border: 1px solid #3b82f6;
            padding: 15px;
            border-radius: 6px;
            margin: 15px 0;
        }
    </style>
</head>
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    
    <div class="parent-container help-container">
        <h1><i class="fas fa-clock"></i> Quick Time Clock Help</h1>
        
        <div class="help-section">
            <h2>How to Punch In/Out</h2>
            <p><strong>Simple:</strong> Click the buttons on your Time Clock page when you start and end work. There is a running clock at the top to ensure correct time. The Time Card shows you useful information:</p>
            
            <ol><li>Employee Information - ID, Name, Department and Schedule</li><li>Tardy punches are highlighted in red</li><li>Punch Type - User Initiated, Holiday, Supervisor Override, etc.</li><li>Accrued PTO</li></ol>
            
            <ol><li>Your hours are automatically calculated and saved</li>
            <li>Successful punch subject to restrictions if enabled</li>
            <li>Time / Day Restrictions</li>
            <li>Device Restrictions</li>
            <li>Location Restrictions</li>
        </div>
        <img src="Images/timecards_individual.webp" alt="Time Clock">
        <br>
        <div class="help-section">
            <h2>Mobile Access</h2>
            <p>Use any web browser on your phone or tablet - just bookmark this site for quick access.</p>
            <div class="mobile-notice">
                <strong>📱 New:</strong> Try our dedicated mobile app.
            </div>
        </div>
        
        <div class="help-section">
            <h2>Forgot to Punch?</h2>
            <p>Contact your supervisor - they can add or fix your punch times.</p>
        </div>
        
        <div class="help-section">
            <h2>Login Issues?</h2>
            <p>Make sure you have:</p>
            <ul>
                <li>Correct company ID</li>
                <li>Your email address</li>
                <li>Your PIN (default for new users or reset PINs is <code>1234</code>)</li>
            </ul>
        </div>
        
        <div class="help-section">
            <h2>Need Help?</h2>
            <p>Contact your supervisor or administrator for schedule, time off, or payroll questions.</p>
        </div>
    </div>
    
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>