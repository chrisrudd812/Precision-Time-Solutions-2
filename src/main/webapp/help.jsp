<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Precision Time Solutions - Help & User Guide</title>
    <style>
        /* Smooth scrolling for TOC links */
        html {
            scroll-behavior: smooth;
        }

        /* General Body Styling */
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.6;
            margin: 0;
            padding: 0;
            background-color: #f8f9fa;
            color: #343a40;
            padding-top: 60px; /* Adjust for fixed navbar */
        }
        
        .layout-container {
            display: flex;
        }

        nav#table-of-contents { /* More specific selector */
            width: 260px;
            position: fixed;
            top: 60px; /* Position below the main navbar */
            left: 0;
            height: calc(100vh - 60px); /* Adjust height for main navbar */
            background-color: #e9ecef;
            padding: 20px;
            overflow-y: auto;
            box-sizing: border-box;
        }
        
        nav#table-of-contents h2 {
            margin-top: 0;
            margin-bottom: 20px;
            text-align: center;
            font-size: 1.5em;
            color: #0056b3;
        }

        nav#table-of-contents ul {
            list-style: none;
            padding: 0;
            margin: 0;
            display: flex;
            flex-direction: column;
        }

        nav#table-of-contents ul li a {
            text-decoration: none;
            color: #0056b3;
            font-weight: 500;
            padding: 10px 15px;
            border-radius: 5px;
            transition: background-color 0.3s, color 0.3s;
            display: block;
            margin-bottom: 5px;
        }

        nav#table-of-contents ul li a:hover {
            background-color: #0056b3;
            color: #ffffff;
        }

        main {
            margin-left: 260px;
            padding: 20px 40px;
            width: calc(100% - 260px);
            box-sizing: border-box;
        }

        header {
            text-align: center;
            border-bottom: 2px solid #e9ecef;
            padding-bottom: 20px;
            margin-bottom: 20px;
            background-color: #fff;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.05);
        }

        h1 {
            color: #0056b3;
            font-size: 2.5em;
            margin: 0;
        }
        
        section {
            background-color: #ffffff;
            padding: 30px;
            margin-bottom: 20px;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.05);
        }

        h2 {
            color: #0056b3;
            font-size: 2em;
            border-bottom: 1px solid #dee2e6;
            padding-bottom: 10px;
            margin-top: 0;
        }

        h3 {
            font-size: 1.5em;
            color: #17a2b8;
            margin-top: 30px;
        }

        img {
            max-width: 100%;
            height: auto;
            border: 1px solid #dee2e6;
            border-radius: 5px;
            margin-top: 15px;
            margin-bottom: 15px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
        }

        blockquote {
            background-color: #eef7ff;
            border-left: 5px solid #007bff;
            margin: 20px 0;
            padding: 15px 20px;
            font-style: italic;
        }

        code {
            color: #dc3545;
            font-weight: bold;
            font-family: "Courier New", Courier, monospace;
        }

    </style>
</head>
<body>
    
    <%-- [FIX] This JSP include directive is now uncommented and will be processed by the server --%>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="layout-container">
        <nav id="table-of-contents">
            <h2>Table of Contents</h2>
            <ul>
                <li><a href="#login">Login</a></li>
                <li><a href="#time-clock">Using the Time Clock</a></li>
                <li><a href="#navigation-bar">Navigation Bar</a></li>
                <li><a href="#employee-management">Employee Management</a></li>
                <li><a href="#departments">Departments</a></li>
                <li><a href="#schedules">Schedules</a></li>
                <li><a href="#accruals">Accrual Policies</a></li>
                <li><a href="#settings">Global Settings</a></li>
                <li><a href="#payroll">Payroll Processing</a></li>
                <li><a href="#reports">Reports</a></li>
                <li><a href="#account">Account Settings</a></li>
            </ul>
        </nav>

        <main>
            <header>
                <h1>Precision Time Solutions - Help & User Guide</h1>
                <p>Welcome! This guide will walk you through the features and settings to manage your company's time and attendance effectively.</p>
            </header>

            <section id="login">
            
                <h2>Login</h2>
                <p>This is how you log into the system.<br><br>
                 The first time you log in, use the email address you registered with and the default PIN <code>1234</code>. You will be prompted to change your PIN immediately after logging in for security purposes. PIN must be 4 digits.<br><br> 
                If you are an administrator, you will be taken to the Employee Management page after logging in. Regular users will be directed to the Time Clock page.  - This is the only functionality that non-Administrators have access to. 
                </p>
                
                <img src="Images/login.png"   style="max-width:600px" alt="Login screen">
                
            </section>
            
            <section id="time-clock">
                 <h2>Using the Time Clock</h2>
                <p>The main Time Clock page allows employees to punch IN and OUT. On the time card, you can also view employee details and available accrued time. <br>To use, simply click on IN or OUT to initiate a punch. You will then be automatically logged out in 10 seconds, unless you have Administrator permissions.</p> 
                <img src="Images/timecards_individual.png" style="max-width:1000px" alt="Individual time card view">
            </section>

			<section id="navigation-bar">
                <h2>Navigating the Application</h2>
                <p>The Navigation Bar pictured below is located at the top of every page (Admin users only) and is used to access the various management functions. Some of the options have sub-menus with additional options. <br> 
                You can jump to any section, form anywhere, easily using the navigation bar.</p> 
                <img src="Images/navbar.png" alt="Navigation bar screenshot">
            </section>

            <section id="employee-management">
                <h2>Employee Management</h2>
                 <p>The Employee Management page is where you can view all active employees, add new employees, edit their information, reset PINs, and deactivate employees.</p>
                
                <h3>Viewing Employee Details</h3>
                <p>To view an employee's details, click on their row in the table. Their information, including accrued time balances, will appear in the "Selected Employee Details" panel. <br>This panel also contains the PIN Reset button, which resets their PIN to "1234".</p> 
                <img src="Images/employees_main.png" style="max-width:1000px" alt="Employee page with a row selected">

                <h3>Adding a New Employee</h3>
                <ol>
                    <li>Click the green <strong>"Add Employee"</strong> button.</li>
                     <li>Fill in the employee's information in the modal that appears. Fields with a red asterisk <code>*</code> are required. The email address serves as the username.</li>
                    <li>Set the permissions: "User" can only access the Time Clock, while "Administrators" can access all functions.</li>
                    <li>Complete the sections for personal info, company assignments, and wage information.</li>
                    <li>Click <strong>"Submit"</strong> to save the new employee.</li>
                </ol>
                <img src="Images/add_new_employee.png" style="max-width:600px" alt="Add new employee form">

                <h3>Editing an Existing Employee</h3>
                <ol>
                    <li>Select the employee's row in the table.</li>
                    <li>Click the now-active <strong>"Edit Employee"</strong> button.</li>
                    <li>In the "Edit Employee" modal, make the necessary changes.</li>
                    <li>Click <strong>"Save Changes"</strong> to update the record.</li>
                </ol>
                 <img src="Images/employees_edit.png"  style="max-width:600px" alt="Edit employee form">

                <h3>Deactivating an Employee</h3>
                <p>Deactivating an employee removes them from active lists but preserves their history for reporting.</p>
                <ol>
                     <li>Select the employee's row in the table.</li>
                    <li>Click the now-active <strong>"Deactivate Employee"</strong> button.</li>
                    <li>In the confirmation pop-up, select a reason for deactivation from the dropdown list.</li>
                    <li>Click the red <strong>"Deactivate"</strong> button to confirm.</li>
                 </ol>
                <img src="Images/deactivate_employee.png"  style="max-width:600px" alt="Deactivate employee confirmation modal">
                <blockquote><strong>Note:</strong> Inactive employees can be viewed and reactivated from the <strong>Reports -> Employee Reports</strong> section.</blockquote>
            </section>
            
            <section id="departments">
                 <h2>Managing Departments</h2>
                <p>Departments help you organize employees for reporting and management. Each employee can be assigned to one department.</p>
                <img src="Images/departments_main.png"  style="max-width:1000px" alt="Departments main page">

                <h3>Adding a New Department</h3>
                <p>Click "Add Department", enter a unique name and optional details, and click "Submit".</p>
                <img src="Images/departments_add.png"  style="max-width:600px" alt="Add new department form">

                 <h3>Editing a Department</h3>
                <p>Select a department, click "Edit Department", make your changes, and click "Save Changes".<br>
                Note: Name cannot be changed. Instead, create new department, delete old department while re-assigning employees (if any) to new department.</p>
                <img src="Images/departments_edit.png"  style="max-width:600px" alt="Edit department form">

                <h3>Deleting a Department</h3>
                <p>Select a department and click "Delete Department". You must re-assign any employees from the deleted department to a new one before confirming the deletion.</p>
                <img src="Images/departments_delete.png"  style="max-width:600px" alt="Delete department confirmation modal">
            </section>

            <section id="schedules">
                <h2>Managing Schedules</h2>
                <p>Schedules define standard shift times and automatic lunch break rules, which helps flag attendance exceptions.</p>
                <img src="Images/scheduling_main.png"  style="max-width:1000px" alt="Schedules main page">

                <h3>Adding a New Schedule</h3>
                <p>Click "Add Schedule", enter a unique name, set shift times, and configure auto-lunch rules if needed. <br>The auto-lunch feature requires you to specify the hours an employee must work (Hrs Req) and the break duration in minutes (Lunch (min)).</p> 
                <img src="Images/scheduling_add.png"  style="max-width:600px" alt="Add new schedule form">

                <h3>Editing a Schedule</h3>
                <p>Select a schedule, click "Edit Schedule", and update its details.  Note: The schedule name cannot be edited. <br>To rename a schedule, you must create a new one and reassign employees while deleting the old one.</p> 
                <img src="Images/scheduling_edit.png"  style="max-width:600px" alt="Edit schedule form">

                <h3>Deleting a Schedule</h3>
                <p>Select a schedule and click "Delete Schedule". You must move any employees on that schedule to a different one before confirming the deletion.</p> 
                <img src="Images/scheduling_delete.png"  style="max-width:600px" alt="Delete schedule confirmation modal">
            </section>

            <section id="accruals">
                <h2>Managing Accrual Policies</h2>
                <p>Accrual policies define how employees earn paid time off (PTO). The system automatically adds prorated hours when a pay period is closed.</p>
                <img src="Images/accruals_main.png"  style="max-width:1000px" alt="Accrual policies main page">
                
                <h3>Adding a New Accrual Policy</h3>
                <p>Click "Add Policy", give it a unique name, and enter the annual hours for Vacation, Sick, and Personal time.</p>
                <img src="Images/accruals_add.png"  style="max-width:600px" alt="Add new accrual policy form">

                <h3>Editing an Accrual Policy</h3>
                <p>Select a policy, click "Edit Policy", and adjust the time-off hours. Note: The policy name cannot be changed. To rename, you must create a new policy and reassign employees before deleting the old one.</p> 
                <img src="Images/accruals_edit.png"  style="max-width:600px" alt="Edit accrual policy form">

                <h3>Deleting an Accrual Policy</h3>
                <p>Select a policy and click "Delete Policy". You will be required to re-assign employees to a different policy before confirming the deletion.</p> 
                <img src="Images/accruals_delete.png"  style="max-width:600px" alt="Delete accrual policy confirmation modal">
            </section>
            
            <section id="settings">
                <h2>Global Settings</h2>
                 <p>The Global Settings page controls the core time keeping rules for your entire company.</p> 

                <h3>Pay Period & Tardy Rules</h3>
                <ul>
                    <li><strong>Pay Period Type:</strong> How often you pay employees (Weekly, Bi-Weekly, etc.).</li> 
                     <li><strong>First Day of Work Week:</strong> Critical for calculating weekly overtime.</li> 
                    <li><strong>Pay Period Start Date:</strong> A calendar date that one of your pay periods begins on.</li>
                    <li><strong>Grace Period (Minutes):</strong> How many minutes an employee can punch in late before being marked as "Tardy".</li>
                 </ul>
                <img src="Images/settings_pay_period.png"  style="max-width:600px" alt="Pay period settings">

                <h3>Overtime Rules</h3>
                <p>Configure how overtime is calculated, either automatically by state (recommended to ensure compliance) or with manual rules for daily, weekly, and 7th-day overtime (OT) and double-time (DT).</p>
                <img src="Images/settings_overtime.png"  style="max-width:600px" alt="Overtime settings">

                <h3>Punch Restrictions</h3>
                <p>Control when, where and how employees can punch the clock. Any combination of restrictions (if any) is allowed.</p>
                <ul>
                    <li><strong>Restrict by Time/Day:</strong> Set specific time windows for each day of the week.</li>
                    <li><strong>Restrict by Location:</strong> Set up geofences so employees can only punch in/out from specific locations.</li>
                     <li><strong>Restrict by Device:</strong> Ensure employees can only punch from registered devices.</li>
                </ul>
                <img src="Images/settings_punch_restrictions.png" style="max-width:600px" alt="Punch restriction settings">
                
                <h3>Configuring Time/Day Restrictions</h3>
                 
                <p>For each day of the week, you can specify allowed punch-in and punch-out time ranges. If no times are set for a day, punches are allowed at any time on that day.</p>
                
                <img src="Images/time_restrictions.png" style="max-width:600px" alt="Time restriction settings configuration">
                
                <h3>Configuring Location Restrictions</h3>
                 <p>To restrict punches by location, first enable the feature.  Then, add locations by specifying a name, address, and radius in feet.  Employees must be within this radius to punch in/out. Or you can click "Use My Location".<br> Location restrictions are only recommended if employees are using GPS enabled devices.  <br>
                Punch radius may have to be adjusted if only using wi-fi.</p> 
                
                
                <img src="Images/location_restrictions.png" style="max-width:700px" alt="Location restriction settings configuration">
                
                 <h3>Adding/Editing a Location</h3>
                <p>Click "Add Location", enter a unique name, address, and radius in feet, then click "Submit". You can also use the "Use My Location" button to auto-fill your current location coordinates.
                Click Save. <br>Make sure individual locations are enabled here and with the main toggle in the settings page.</p> 
                <img src="Images/location_restrictions_add.png" style="max-width:600px" alt="Add location form">
                
                
                <h3>Configuring Device Restrictions</h3>
                 <p>To restrict punches by device, first enable the feature. Then, set the maximum allowed registered devices per employee. <br>When an employee punches IN or OUT, the device's "fingerprint" is checked against the employee's registered devices list.  If the device id registered, then the punch is allowed, otherwise it will attempt to register the device. <br>If the maximum registered devices has been reached, the punch is denied. Employees can only punch in/out from these registered devices when the feature is enabled. Administrators can view and manage all registered devices from the Settings > Restrict by device > Configure page.</p>                 
                <img src="Images/device_restrictions.png" style="max-width:700px" alt="Device restriction settings configuration">
            </section>
            
            
             <section id="payroll">
                <h2>Payroll Processing</h2>
                <p>The Payroll Processing page is where you review and finalize employee time cards for payroll.</p> 
                <img src="Images/payroll_main.png"  style="max-width:800px" alt="Payroll processing main page">

                <h3>Reviewing Time Cards</h3>
                 <p>Step 1 is to run the Exception Report. This will reveal any missing punches for the current pay period and display them in a table. To edit, click on a row and click Fix Missing Punches. <br>Manually enter the missing punch time and click Save Changes. 
                 Employee's total hours will be adjusted and the entry will be removed from the exception list. Continue fixing punches until list is empty. <br>Other Payroll options will not be available until ALL Exceptions are resolved. After handling exceptions you can optionally perform other tasks like adding Vacation/Holiday hours, Exporting to an Excel file and Printing time cards. 
                </p>
                
                <img src="Images/payroll_exceptions.png" style="max-width:600px" alt="Payroll exceptions report with missing punches">
                
                <p>Next you can optionally add global hours (Vacation, Holiday etc.), Export to Excel or Print Time Cards </p>
                 
                <h3>Finalizing Payroll</h3>
                <p>After reviewing, click "Close Pay Period" to end the pay period.  Finalized periods cannot be edited. 
                Upon closing, new pay period date range will be applied, current punches will be archived and accrued balances will be updated (Vac., Sick, etc.). 
                </p>
                <img src="Images/payroll_close_confirm.png" style="max-width:600px" alt="Finalize payroll confirmation modal">
                
                </section>
            
            <section id="reports">
                <h2>Reports</h2>
                <p>The Reports section provides detailed insights into your employee time data. You can view individual time cards, get payroll summaries, find attendance exceptions, and manage your employee roster. Most reports can be printed or exported.</p>
                
                <h3>Time Card Reports</h3>
                <p>This report allows you to view and print the detailed time card for a single employee or all employees for the current pay period. It includes a daily breakdown of punches, total hours, and overtime.</p>
                <ul>
                    <li><strong>To Print All Time Cards:</strong> Navigate to the <strong>Reports > Time Card Reports > </strong> page and click the <code>Print All Time Cards</code> button to generate a printable document for every active employee.</li>
                    <li><strong>To Print an Individual Time Card:</strong> Navigate to <strong>Reports > Time Card Reports > Individual Time Card</strong> and select an employee.</li>
                </ul>
                <img src="Images/timecards_all.png" style="max-width:1000px" alt="Printable view of all employee time cards">

                <h3>Exception Report</h3>
                <p>The Exception Report is a critical tool for ensuring payroll accuracy. It automatically finds and lists all punches that are missing an <strong>OUT</strong> time. All exceptions must be corrected before you can close the pay period.</p> 
                <ul>
                    <li><strong>Accessing:</strong> From the <strong>Payroll Processing</strong> page, click the <code>Exception Report</code> button.</li> 
                    <li><strong>Usage:</strong> Select a row and click <code>Fix Missing Punches</code> to resolve the error by entering the correct time.</li>
                </ul>
                <img src="Images/payroll_exceptions.png" style="max-width:700px" alt="Exception Report modal showing a missing punch">
                
                <h3>Employee Reports</h3>
                <p>The Employee Report provides a comprehensive list of all employees, both active and inactive. This is where you can manage employee statuses and view contact information.</p>
                <ul>
                    <li><strong>Accessing:</strong> Navigate to <code>Reports > Employee Reports</code> from the main navigation bar.</li>
                    <li><strong>Features:</strong> You can filter the list to see employees by department, schedule or supervisor, only active or inactive employees, then use the <strong>Reactivate Employee</strong> button to return them to the active roster.</li>
                </ul>
                <img src="Images/reports_all_active_employees.png" style="max-width:1000px" alt="Employee Report showing active and active employees">
                
                 <h3>Tardy Report</h3>
                <p>The Tardy Report provides a comprehensive sortable list of all employees with their number of late punches or early outs.</p>
                <ul>
                    <li><strong>Accessing:</strong> Navigate to <code>Reports > Tardy Report</code> from the main navigation bar.</li>
                    </ul>
                <img src="Images/reports_tardy.png" style="max-width:1000px" alt="Report showing employees with tardies">
                
                <h3>Who's IN Report</h3>
                <p>The Who's IN Report provides a list of all employees who are currently punched IN.</p>
                <ul>
                    <li><strong>Accessing:</strong> Navigate to <code>Reports > Who's IN Report</code> from the main navigation bar.</li>
                    </ul>
                <img src="Images/reports_whosin.png" style="max-width:1000px" alt="Report showing employees currently punched IN">
                
                <h3>Accrual Balance Report</h3>
                <p>The Accrual Balance Report provides a comprehensive list of all employees with their current accrued time off balances.</p>
				<ul>
					<li><strong>Accessing:</strong> Navigate to <code>Reports
							> Accrual Balance Report</code> from the main navigation bar.</li>
				</ul>
                <img src="Images/reports_accrual_balance.png" style="max-width:1000px" alt="Report showing employees with their current accrued time off balances">

				<h3>System Access Report</h3>
				<p>The System Access Report provides a list of all users with Administrative Permissions, for auditing purposes.</p>
				<ul>
					<li><strong>Accessing:</strong> Navigate to <code>Reports
							> System Access Report</code> from the main navigation bar.</li>
				</ul>
				<img src="Images/reports_system_access_report.png"
					style="max-width: 1000px" alt="Report showing users  with Administrative Permissions">

				<Archived Punches Report>
				<h3>Archived Punches Report</h3>
				<p>The Archived Punches Report provides a detailed list of all
					punches that have been archived after closing a pay period for auditing purposes.</p>
				<ul>
					<li><strong>Accessing:</strong> Navigate to <code>Reports
							> Archived Punches</code> from the main navigation bar.</li>
				</ul>
				<img src="Images/reports_archived_punches.png"
					style="max-width: 1000px"
					alt="Report showing archived punches after closing a pay period">
			</section>
            
            <section id="account">
                <h2>Account Settings</h2>
                <p>The Account page is for company-level administration, like managing your plan subscription and updating company info. Only the designated company admin can access these settings.</p>
                <img src="Images/account.png"  style="max-width:600px" alt="Account settings page">
                <p>Click "Edit" to change company info.</p>
                <img src="Images/account_company_info.png"  style="max-width:600px" alt="Editing company information">
                <p>Click "Manage Subscription and Billing" to change or cancel your subscription.</p>
                <img src="Images/account_subscription.png"  style="max-width:1000px" alt="Subscription management page">
            </section>
        </main>
    </div>

</body>
</html>