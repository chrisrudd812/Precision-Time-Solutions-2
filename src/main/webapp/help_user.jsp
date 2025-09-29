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
            <h2>How Tardies are Calculated</h2>
            <p>Tardies are automatically calculated and tracked by the system to help monitor attendance patterns.</p>
            
            <h3>When Tardies Apply</h3>
            <ul>
                <li><strong>Schedule Required:</strong> Tardies are only calculated for employees assigned to a schedule with defined start and end times</li>
                <li><strong>Scheduled Days Only:</strong> You can only be marked tardy on days you are scheduled to work</li>
                <li><strong>No Tardies on Days Off:</strong> If you punch in on your day off or a day you're not scheduled, you cannot accumulate a tardy</li>
                <li><strong>Open Schedules:</strong> Employees on "Open" schedules (no set start/end times) cannot be marked tardy</li>
            </ul>
            
            <h3>Grace Period</h3>
            <p>Your company may have set a grace period (typically 5-15 minutes) that allows you to punch in late without being marked tardy. This grace period is factored into all tardy calculations.</p>
            
            <h3>Visual Indicators</h3>
            <ul>
                <li><strong>Red Highlighting:</strong> Tardy punches will be highlighted in red on your time card</li>
            </ul>
            
            <p><em>Note: Tardies are used for attendance tracking and reporting. Contact your supervisor if you have questions about your company's attendance policy.</em></p>
        </div>
        

                <div class="help-section">
            <h2>How Overtime is Calculated</h2>
            
            <h3>Overtime Calculation Methods</h3>
            <p>Your overtime calculation depends on your company's subscription plan and configuration settings:</p>
            
            <p><strong>Basic and Business Plans:</strong> Overtime can be calculated using two methods:</p>
            <ul>
                <li><strong>By Company State:</strong> All employees follow the overtime rules of your company's registered state, regardless of where individual employees are located.</li>
                <li><strong>Manual Override:</strong> Your company can set custom overtime rules that override both federal and state calculations.</li>
            </ul>
            
            <p><strong>Pro Plan:</strong> Includes all the above options plus:</p>
            <ul>
                <li><strong>By Employee State:</strong> Each employee's overtime is calculated according to the labor laws of the state listed in their employee profile. This ensures compliance with state-specific overtime rules, which can vary significantly. This feature is particularly useful for companies with remote workers or multiple office locations across different states.</li>
            </ul>
            
            <h3>Work Week Definition</h3>
            <p>The system calculates overtime based on a 7-day work week period. By default, the work week starts on <strong>Sunday at 12:00 AM</strong> and ends on <strong>Saturday at 11:59 PM</strong>. This means:</p>
            <ul>
                <li>Hours are counted from Sunday through Saturday for overtime purposes</li>
                <li>Overtime calculations reset each Sunday</li>
                <li>Daily overtime rules (where applicable by state) are calculated within each 24-hour period</li>
            </ul>
            
            <p><em>Note: While it's optional, changing the first day of the work week in Settings is not recommended as it may affect overtime calculations and payroll consistency.</em></p>
            
            <h3>State-Specific Rules</h3>
            <p>Different states have varying overtime requirements:</p>
            <ul>
                <li>Some states require overtime after 8 hours in a single day (daily overtime)</li>
                <li>Others follow the federal standard of 40 hours per week</li>
                <li>Certain states have different rates for double-time (2x pay) after specific thresholds</li>
                <li>For complete state-by-state overtime rules, visit the <a href="https://www.dol.gov/agencies/whd/minimum-wage/state" target="_blank">Department of Labor's State Minimum Wage and Overtime Laws</a></li>
            </ul>
            
            <h3>Calculation Priority</h3>
            <p>When multiple overtime rules apply, the system uses whichever calculation results in the highest pay for the employee, ensuring compliance while maximizing employee compensation.</p>
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