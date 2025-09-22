<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>User Help - How to Punch In and Out</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/help.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="help-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    
    <div class="parent-container help-container">
        <h1>How to Use Your Time Clock</h1>
        
        <div class="help-content">
            <div class="help-section">
                <h2><i class="fas fa-clock"></i> Punching In and Out</h2>
                <p>Your time clock system is simple to use. Follow these steps to track your work hours:</p>
                
                <div class="step-guide">
                    <div class="step">
                        <div class="step-number">1</div>
                        <div class="step-content">
                            <h3>Log In</h3>
                            <p>Go to the login page and enter:</p>
                            <ul>
                                <li>Your company ID (provided by your administrator)</li>
                                <li>Your email address</li>
                                <li>Your PIN (initially 1234, you'll change this on first login)</li>
                            </ul>
                        </div>
                    </div>
                    
                    <div class="step">
                        <div class="step-number">2</div>
                        <div class="step-content">
                            <h3>Punch In</h3>
                            <p>When you arrive at work, click the <strong>"Punch In"</strong> button on your time clock page. This records your start time.</p>
                        </div>
                    </div>
                    
                    <div class="step">
                        <div class="step-number">3</div>
                        <div class="step-content">
                            <h3>Punch Out</h3>
                            <p>When you're done working, click the <strong>"Punch Out"</strong> button. This records your end time and calculates your hours worked.</p>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="help-section">
                <h2><i class="fas fa-mobile-alt"></i> Using on Mobile</h2>
                <p>You can access your time clock from any device with internet access:</p>
                <ul>
                    <li>Bookmark the login page on your phone for quick access</li>
                    <li>The system works on all smartphones and tablets</li>
                    <li>No app download required - just use your web browser</li>
                </ul>
            </div>
            
            <div class="help-section">
                <h2><i class="fas fa-question-circle"></i> Common Questions</h2>
                
                <div class="faq-item">
                    <h3>What if I forget to punch in or out?</h3>
                    <p>Contact your supervisor or administrator. They can add or edit your punch times as needed.</p>
                </div>
                
                <div class="faq-item">
                    <h3>Can I see my hours worked?</h3>
                    <p>Yes! Your time clock page shows your recent punches and total hours for the current pay period.</p>
                </div>
                
                <div class="faq-item">
                    <h3>What if I'm having trouble logging in?</h3>
                    <p>Make sure you're using the correct company ID and email address. If you've forgotten your PIN, contact your administrator to reset it.</p>
                </div>
            </div>
            
            <div class="help-section">
                <h2><i class="fas fa-phone"></i> Need More Help?</h2>
                <p>If you have questions about your schedule, time off, or payroll, please contact your supervisor or HR department.</p>
            </div>
        </div>
    </div>
    
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>