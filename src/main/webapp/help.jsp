<%
    String publicParam = request.getParameter("public");
    boolean isPublicView = "true".equalsIgnoreCase(publicParam);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Precision Time Solutions - Help and User Guide</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <style>
        /* Smooth scrolling for TOC links with offset for sticky header */
        html {
            scroll-behavior: smooth;
            scroll-padding-top: 300px;
        }

        /* General Body Styling */
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            line-height: 1.6;
            margin: 0;
            padding: 0;
            background: linear-gradient(to right, #2c3e50, #3498db);
            color: #343a40;
            min-height: 100vh;
        }
        
        .layout-container {
            display: flex;
        }

        nav#table-of-contents { /* More specific selector */
            width: 240px;
            position: fixed;
            top: 6.5vh;
            left: 1.5%;
            height: calc(92vh - 20px);
            background-color: #e9ecef;
            padding: 20px;
            overflow-y: auto;
            box-sizing: border-box;
            border-radius: 8px;
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
            margin-left: 280px;
            padding: 20px 40px;
            width: calc(100% - 280px);
            box-sizing: border-box;
        }



        /* Responsive Design */
        @media (max-width: 992px) {
            nav#table-of-contents {
                position: static;
                width: auto;
                height: auto;
                left: auto;
                top: auto;
                margin: 20px 40px;
                border-radius: 8px;
            }
            header {
                margin: 20px 40px;
                border-radius: 8px;
            }
            main {
                margin-left: 0;
                width: 100%;
                padding: 20px 40px;
                padding-top: 20px;
            }
            .layout-container {
                flex-direction: column;
            }
            img {
                max-width: 100%;
                width: auto;
            }
        }

        @media (max-width: 480px) {
            body {
                padding: 0 !important;
            }
            
            .layout-container {
                margin-top: 80px !important;
                padding: 0 !important;
                flex-direction: column !important;
            }
            
            .parent-container,
            nav#table-of-contents,
            header,
            main,
            section {
                width: calc(100vw - 20px) !important;
                max-width: none !important;
                margin: 10px !important;
                left: auto !important;
                right: auto !important;
                position: static !important;
                box-sizing: border-box !important;
            }
            
            main {
                padding: 0 !important;
                margin-left: 0 !important;
                width: calc(100vw - 20px) !important;
            }
            
            section {
                padding: 15px !important;
                width: calc(100vw - 20px) !important;
                margin: 10px !important;
            }
            
            nav#table-of-contents {
                padding: 15px !important;
                width: calc(100vw - 20px) !important;
                height: auto !important;
                margin: 10px !important;
                order: -1;
            }
            
            header {
                padding: 15px !important;
                width: calc(100vw - 20px) !important;
                margin: 10px !important;
            }
            
            h1 {
                font-size: 1.3em !important;
                text-align: center !important;
            }
            
            h2 {
                font-size: 1.2em !important;
            }
        }

        header {
            text-align: center;
            border-bottom: 2px solid #e9ecef;
            padding-bottom: 20px;
            margin-bottom: 18px;
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
            margin-top: 18px;
            border-radius: 8px;
            box-shadow: 0 4px 8px rgba(0,0,0,0.05);
        }
        
        section#login {
            top: 15vh;
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
            width: 100%;
            height: auto;
            border: 1px solid #dee2e6;
            border-radius: 5px;
            margin-top: 15px;
            margin-bottom: 15px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.05);
            display: block;
            margin-left: auto;
            margin-right: auto;
            object-fit: contain;
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
<body class="reports-page<% if (isPublicView) { %> no-navbar<% } %>">
    
    <%-- Include the standard navigation bar only if not public view --%>
    <% if (!isPublicView) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="layout-container">
        <nav id="table-of-contents">
            <h2>Table of Contents</h2>
            <div style="display: flex; gap: 5px; margin-bottom: 15px;">
                <input type="text" id="search-field" placeholder="Search page..." style="flex: 1; padding: 8px; border: 1px solid #ccc; border-radius: 4px; font-size: 14px;">
                <button onclick="searchPage()" style="padding: 8px 12px; background: #0056b3; color: white; border: none; border-radius: 4px; cursor: pointer;">Search</button>
            </div>
            <ul>
                <li><a href="#login">Login</a></li>
                <li><a href="#time-clock">Using the Time Clock</a></li>
                <li><a href="#navigation-bar">Navigation Bar</a></li>
                <li><a href="#employee-management">Employee Management</a></li>
                <li><a href="#punch-management">Punch Management</a></li>
                <li><a href="#departments">Departments</a></li>
                <li><a href="#schedules">Schedules</a></li>
                <li><a href="#accruals">Accrual Policies</a></li>
                <li><a href="#settings">Global Settings</a></li>
                <li><a href="#payroll">Payroll Processing</a></li>
                <li><a href="#overtime-calculation">Overtime Calculations</a></li>
                <li><a href="#reports">Reports</a></li>
                <li><a href="#account">Account Settings</a></li>
                <li><a href="contact.jsp<% if (isPublicView) { %>?public=true<% } %>">Contact Support</a></li>
            </ul>
        </nav>

        <main>
            <header>
                <h1>Precision Time Solutions - Help and User Guide</h1>
                <p>Welcome! This guide will walk you through the features and settings to manage your company's time and attendance effectively.</p>
            </header>

            <section id="login">
            
                <h2>Login</h2>
                <p>This is how you log into the system.<br><br>
                 The first time you log in, use the email address you registered with and the default PIN <code>1234</code>. You will be prompted to change your PIN immediately after logging in for security purposes. PIN must be 4 digits.<br><br> 
                If you are an administrator, you will be taken to the Employee Management page after logging in. Regular users will be directed to the Time Clock page and logged out automatically after 30 seconds of inactivity to keep terminal secured.  - This is the only functionality that non-Administrators have access to. 
                <br>If you have any messages, they will be displayed upon successful login.</p><br>
                
                Login Checklist: 
                
                <ol><li>Company ID - Sent in welcome email or provided by supervisor</li>
                <li>Your e-mail address (username)</li>
                <li>PIN - default for new employees or PIN resets is <code>1234</code></li>
                <li>For mobile devices - bio-metric authentication or device lock PIN</li></ol>
                
                <img src="Images/login.webp" style="max-width:600px" alt="Login screen">
                
            </section>
            
            <section id="time-clock">
                 <h2>Using the Time Clock</h2>
                <p>The main Time Clock page allows employees to punch IN and OUT. On the time card, you can also view employee details and available accrued PTO. <br>To use, simply click on IN or OUT to initiate a punch. 
                You will then be automatically logged out in 30 seconds, unless you have Administrator permissions. <br>Tardy punches will be shown in red<br>Accrued PTO is visible at the bottom of the "time card"". </p> <br>
                <span style="color: red;">Note: Successful punch is subject to restriction policies (Device, Time or Location Restrictions)</span>
                <img src="Images/timecards_individual.webp" style="max-width: 1200px" alt="Individual time card view">
            </section>

			<section id="navigation-bar">
                <h2>Navigating the Application</h2>
                <p>The Navigation Bar pictured below is located at the top of every page (Admin users only) and is used to access the various management functions. Some of the options have sub-menus with additional options. <br> 
                You can jump to any section, form anywhere, easily using the navigation bar.</p> 
                <img src="Images/navbar.webp" alt="Navigation bar screenshot">
            </section>

            <section id="employee-management">
                <h2>Employee Management</h2>
                 <p>The Employee Management page is where you can view all active employees, add new employees, edit their information, reset PINs, and deactivate employees.</p>
                
                <h3>Viewing Employee Details</h3>
                <p>To view an employee's details, click on their row in the table. Their information, including accrued time balances, will appear in the "Selected Employee Details" panel. 
                <br>This panel also contains the PIN Reset button, which resets their PIN to "1234" and require change at next login.</p> 
                <img src="Images/employees_main.webp" style="max-width: 1200px" alt="Employee page with a row selected">

                <h3>Adding a New Employee</h3>
                <ol>
                    <li>Click the green <strong>"Add Employee"</strong> button.</li>
                     <li>Fill in the employee's information in the window that appears. Fields with a red asterisk <code>*</code> are required. The email address serves as the user name.</li>
                    <li>Set the permissions: "User" can only access the Time Clock, while "Administrators" can access all functions.</li>
                    <li>Complete the sections for personal info, company assignments, and wage information.</li>
                    <li>Click <strong>"Submit"</strong> to save the new employee.</li>
                    <li>Employee will be created with default temporary PIN (1234).</li>
                </ol>
                <img src="Images/add_new_employee.webp" style="max-width:600px" alt="Add new employee form">

                <h3>Editing an Existing Employee</h3>
                <ol>
                    <li>Select the employee's row in the table.</li>
                    <li>Click the now-active <strong>"Edit Employee"</strong> button.</li>
                    <li>In the "Edit Employee" modal, make the necessary changes.</li>
                    <li>Click <strong>"Save Changes"</strong> to update the record.</li>
                </ol>
                 <img src="Images/employees_edit.webp"  style="max-width:600px" alt="Edit employee form">

                <h3>Deactivating an Employee</h3>
                <p>Deactivating an employee marks them as inactive. They will not be included in any calculations or reports except the Inactive Employees Report and can be reactivated from there (temp workers, seasonal, contractors, etc.).</p>
                <ol>
                     <li>Select the employee's row in the table.</li>
                    <li>Click the now-active <strong>"Deactivate Employee"</strong> button.</li>
                    <li>In the confirmation pop-up, select a reason for deactivation from the dropdown list.</li>
                    <li>Click the red <strong>"Deactivate"</strong> button to confirm.</li>
                 </ol>
                <img src="Images/deactivate_employee.webp"  style="max-width:600px" alt="Deactivate employee confirmation modal">
                <blockquote><strong>Note:</strong> Inactive employees can be viewed and reactivated from the <strong>Reports -> Employee Reports -> Inactive Employees</strong> section.<br>Inactive employees do not count toward the plan's employee limit.</blockquote>
                
                <h3>Re-Assigning Employees</h3>
                <p>While editing an individual employee is done in the edit form, you can also re-assign multiple employees to a different department, schedule, or accrual (PTO) policy in bulk.</p>
				<ol>
					<li>On the Navbar go to Employees > Re-assign Employees by ></li>
					
					<li>In the "Re-Assign Employees" modal, choose the new
						department, schedule, or PTO policy from the dropdowns.</li>
					<li>Click <strong>"Apply Reassignment"</strong> to apply the changes to
						all selected employees.
					</li>
					</ol>
					                <img src="Images/employees_reassign.webp"  style="max-width:1200px" alt="Re-assign employees modal">
			</section>
            
            <section id="punch-management">
                <h2>Punch Management</h2>
                <p>The Punch Management section allows administrators to view, edit, and manage employee time punches. This is where you can correct punch times, add missing punches and apply PTO or holiday pay.</p>
                
                <h3>Viewing Punch Records</h3>
                <p>Access punch records through the main navigation bar under <strong>Punch Management -> Edit Employee Punches</strong></p>
                
                <img src="Images/punches_main.webp" style="max-width: 1200px" alt="Punch Management">
                
                <h3>Adding PTO</h3>
                <p>To add PTO for an INDIVIDUAL employee,  navigate to Punch Management -> Edit Employee Punches<strong>"Add PTO"</strong><br>
                To add PTO globally, navigate to Punch Management -> Add Global Hours (see next section)</p>
                
                <ol>
					<li>Fill out form</li>
					
					<li>Date must be in current pay period</li>
					<li>Select type (Vacation, Holiday, etc.)<li>
					<li>Apply number of hours to add</li>
					<li>PTO balances are automatically updated</li>
					</ol>
                
                <img src="Images/punches_add_PTO.webp" style="max-width: 600px" alt="Add PTO">
                
                <h3>Adding Missing Punches</h3>
                <p>If an employee forgot to punch in or out, you can add the missing punch manually., then save.</p>
                <ol>
					<li>Click <strong>"Add Timed Punch"</strong></li>
					<li>Date must be in current pay period</li>
					<li>Enter the punch time</li>
					<li>Edited punch times are always defined as "Supervisor Override" for the punch type.<li>
					<li>Total and OT are recalculated automatically</li>
					</ol>
                
                              
                <h3>Editing Punch Times</h3>
                <p>To edit a punch time, select the punch record and click <strong>"Edit Punch"</strong>. You can modify the punch time, add notes, and save the changes. All edits are logged for audit purposes.</p>
                <ol>
                	<li>Select a row to edit in the table</li>
					<li>Click <strong>"Edit Row"</strong></li>
					<li>Date must be in current pay period</li>
					<li>Edited punch times are always defined as "Supervisor Override" for the punch type.<li>
					<li>Adjust values and click save</li>
					</ol>
					
					<img src="Images/punches_edit.webp" style="max-width: 600px" alt="Edit Punches">
                
                <h3>Deleting Incorrect Punches</h3>
                <p>Incorrect punches can be deleted by selecting the punch record and clicking <strong>"Delete Row"</strong>. A confirmation dialog will appear before the punch is permanently removed.</p>
                
                <h3>Adding Global PTO</h3>
                <p>To apply PTO to ALL employees, navigate to Punch Management -> Add Global PTO</p>
                <ol>
                	<li>Date must be in current pay period</li>
					<li>Select type (Vacation, Holiday, etc.)<li>
					<li>Apply number of hours to add</li>
					<li>PTO balances are automatically updated</li>
					</ol>
            
            </section>
            
            <section id="departments">
                 <h2>Managing Departments</h2>
                <p>Departments help you organize employees for reporting and management. Each employee can be assigned to one department.</p>
                <img src="Images/departments_main.webp"  style="max-width:1000px" alt="Departments main page">

                <h3>Adding a New Department</h3>
                <p>Click "Add Department", enter a unique name and optional details, and click "Submit".</p>
                <img src="Images/departments_add.webp"  style="max-width:600px" alt="Add new department form">

                 <h3>Editing a Department</h3>
                <p>Select a department, click "Edit Department", make your changes, and click "Save Changes".<br>
                <span class="red">Note: Name cannot be changed. Instead, create new department, delete old department while re-assigning employees (if any) to new department.</span>
                </p>
                <img src="Images/departments_edit.webp"  style="max-width:600px" alt="Edit department form">

                <h3>Deleting a Department</h3>
                <p>Select a department and click "Delete Department". You must re-assign any employees from the deleted department to a new one before confirming the deletion.<br>
                <span class="red">Note: Deleting or Re-naming department "None" is not allowed as it is a system default.</span>
                </p>
                <img src="Images/departments_delete.webp"  style="max-width:600px" alt="Delete department confirmation modal">
            </section>

            <section id="schedules">
                <h2>Managing Schedules</h2>
                <p>Schedules define standard shift times and apply automatic lunch deductions and flags attendance exceptions (tardies). 
                <br>If Auto Lunch is enabled, time will be deducted from the daily total as per the auto lunch threshold and lunch length defined.
                <span class="red">Tardy punches will only be observed and recorded if the employee is assigned to a schedule with defined start and end times and days. (only marked tardy on scheduled days). <br>"Open" schedules do not record tardies.<br>
                Tardies will show on time card in RED.</span>
                
                </p>
                <img src="Images/scheduling_main.webp"  style="max-width:1000px" alt="Schedules main page">

                <h3>Adding a New Schedule</h3>
                <p>Click "Add Schedule", enter a unique name, set shift times, and configure auto-lunch rules if needed. <br>The auto-lunch feature requires you to specify the hours an employee must work (Hrs Req) and the break duration in minutes (Lunch (min)).</p> 
                <img src="Images/scheduling_add.webp"  style="max-width:600px" alt="Add new schedule form">

                <h3>Editing a Schedule</h3>
                <p>Select a schedule, click "Edit Schedule", and update its details.  Note: The schedule name cannot be edited. <br>To rename a schedule, you must create a new one and reassign employees while deleting the old one.
                <span class="red">Note: Editing "Open" or "Open w/ auto lunch" is not allowed as they serve as a system default. "Open w/ auto lunch" is editable (threshold and lunch length only)</span>
                </p> 
                <img src="Images/scheduling_edit.webp"  style="max-width:600px" alt="Edit schedule form">

                <h3>Deleting a Schedule</h3>
                <p>Select a schedule and click "Delete Schedule". You must select another schedule to move any employees (if any) on that schedule, to a different schedule before confirming the deletion.<br>
                <span class="red">Note: Deleting "Open" or "Open w/ auto lunch" is not allowed as is serves as a system default.</span>
                </p> 
                <img src="Images/scheduling_delete.webp"  style="max-width:600px" alt="Delete schedule confirmation modal">
            </section>

            <section id="accruals">
                <h2>Managing PTO Policies</h2>
                <p>Accrual policies define how employees earn paid time off (PTO). The system automatically adds prorated hours to each employee's PTO balance when each pay period is closed.
                <br>You can also add to, subtract from, or set the PTO balance for individuals or all employees in the Adjust Employee PTO Balance section. (Especially useful during initial setup)</p>
                <img src="Images/accruals_main.webp" style="max-width: 1200px" alt="PTO policies main page">
                
                <h3>Adding a New PTO Policy</h3>
                <p>Click "Add Policy", give it a unique name, and enter the annual hours for Vacation, Sick, and Personal time.</p>
                <img src="Images/accruals_add.webp"  style="max-width:600px" alt="Add new PTO policy form">

                <h3>Editing an PTO Policy</h3>
                <p>Select a policy, click "Edit Policy", and adjust the time-off hours. Note: The policy name cannot be changed. To rename, you must create a new policy and reassign employees before deleting the old one.</p> 
                <img src="Images/accruals_edit.webp"  style="max-width:600px" alt="Edit PTO policy form">

                <h3>Deleting an PTO Policy</h3>
                <p>Select a policy and click "Delete Policy". You will be required to re-assign employees to a different policy before confirming the deletion.
                <br><span class="red">Note: Deleting "None" policy is not allowed as is serves as a system default.</span></p> 
                <img src="Images/accruals_delete.webp"  style="max-width:600px" alt="Delete PTO policy confirmation modal">
                
                <h3>Adjusting PTO</h3>
                <ol><li>Select an individual or all employees</li><li>Select PTO type</li><li>Select action</li><li>Define hours</li><li>Click Apply Adjustment</ol>
                <img src="Images/accruals_adjust.webp"  style="max-width:1200px" alt="Adjust PTO">
                
            </section>
            
            <section id="settings">
                <h2>Global Settings</h2>
                 <p>The Global Settings page controls the core time keeping rules for your entire company.</p> 

                <h3>Pay Period and Tardy Rules</h3>
                <ul>
                    <li><strong>Pay Period Type:</strong> How often you pay employees (Weekly, Bi-Weekly, etc.).</li> 
                     <li><strong>First Day of Work Week:</strong> Critical for calculating weekly overtime. <li>(Sunday is standard in US)</li><li>Only relevant for pay periods over one week.</li>
                    <li><strong>Pay Period Start Date:</strong> A calendar date that one of your pay periods begins on.<li>Pay period End Date is calculated automatically</li>
                    <li><strong>Grace Period (Minutes):</strong> How many minutes an employee can punch in late (or leave early) before being marked as "Tardy". Tardies are recorded for reporting purposes and displayed in red on time card.</li>
                 </ul>
                <img src="Images/settings_pay_period.webp" style="max-width:1200px" alt="Pay period settings">

                <h3>Overtime Rules</h3>
                <p>Configure how overtime is calculated for daily, weekly, and 7th-day overtime (OT) and double-time (DT) rules.</p>
                <ol><li>Either with manual rules</li>
                <li>Automatically by state (recommended to ensure compliance)</li>
                <li>Pro Plan subscribers have option for remote workers or locations, which calculates each employee's overtime according to the state in each employee's profile.</li></ol>
                
                <img src="Images/settings_overtime.webp" style="max-width:1200px" alt="Overtime settings">

                <h3>Punch Restrictions</h3>
                <p>Control when, where and how employees can punch the clock. Any combination of restrictions (if any) is allowed.</p>
                <ul>
                    <li><strong>Restrict by Time/Day:</strong> Set specific time windows for each day of the week.</li>
                    <li><strong>Restrict by Location:</strong> Set up geofences so employees can only punch in/out from specific locations.</li>
                    <li><strong>Restrict by Device:</strong> Ensure employees can only punch from registered devices.</li>
                </ul>
                <img src="Images/settings_punch_restrictions.webp" style="max-width:800px" alt="Punch restriction settings">
                
                <h3>Configuring Time/Day Restrictions</h3>
                 
                <p>For each day of the week, you can specify allowed punch-in and punch-out time ranges.</p>
                <ol><li>If no times are set for a day, you can choose whether to restrict all day or allow all day.</li>
                <li>Quickly set all days to the same time windows using the Apply to All Days toggle.</li>
                </ol>
                
                <img src="Images/time_restrictions.webp" style="max-width:800px" alt="Time restriction settings configuration">
                <img src="Images/time_restrictions_2.webp" style="max-width:800px" alt="Time restrictions details">
                
                                
                <h3>Configuring Location Restrictions</h3>
                 <p>To restrict punches by location, first enable the feature. Then, add locations by specifying a name, address, and radius in meters. 
                  </p> 
                
                 <ol>
                 <li>Employees must be within this radius to punch in/out.</li>
                 <li>Location restrictions are only recommended if employees are using GPS enabled devices.</li>
                 <li>Punch radius may have to be adjusted if only using wi-fi.</li>
                 </ol>
                  
                <img src="Images/location_restrictions.webp" style="max-width:700px" alt="Location restriction settings configuration">
                
                 <h3>Adding/Editing a Location</h3>
                <p>Click "Add Location", enter a unique name, address, and radius in feet, then click "Submit". You can also use the "Use My Location" button to auto-fill your current location coordinates.
                Click Save. <br>Make sure individual locations are enabled here and with the main toggle in the settings page.</p> 
                <img src="Images/location_restrictions_add.webp" style="max-width:600px" alt="Add location form">
                
                
                <h3>Configuring Device Restrictions</h3>
                 <p>To restrict punches by device, first enable the feature. Then, set the maximum allowed registered devices per employee. When an employee punches IN or OUT, the device's "fingerprint" is checked against the employee's registered devices list.</p> 
                 <img src="Images/device_restrictions.webp" style="max-width:700px" alt="Device restriction settings configuration">
                 
                 <ol><li>If the device id registered, then the punch is allowed, otherwise it will attempt to register the device.</li>
                 <li>If the maximum registered devices has been reached, the punch is denied until an administrator allows more devices per user, or a previous device is deleted.</li>
                 <li>Devices can be blocked from punching with the disable toggle</li>
                 <li>VPNs, private browsing (incognito mode), proxy servers, etc. will cause the system to detect a new device.</li>
                 <li>Administrators can view and manage all registered devices from the Settings > Restrict by device > Configure page.</li>
                 
                 </ol>
                                 
            </section>
            
            
             <section id="payroll">
                <h2>Payroll Processing</h2>
                <p>The Payroll Processing page is where you review and finalize employee time cards for payroll.</p> 
                <img src="Images/payroll_main.webp" style="max-width: 1200px" alt="Payroll processing main page">

                <h3>Reviewing Time Cards</h3>
                
                <ol><li>Step 1 is to run the Exception Report. This will reveal any missing punches for the current pay period and display them in a table. >
                 <li>To edit, click on a row and click Fix Missing Punches.</li>
                 <li>Manually enter the missing punch time and click Save Changes.</li>
                 <li>Employee's total hours will be adjusted and the entry will be removed from the exception list.</li>
                 <li>Continue fixing punches until list is empty. Other Payroll options will not be available until ALL Exceptions are resolved</li>
                 <li>After handling exceptions you can optionally perform other tasks like adding Vacation/Holiday hours, Exporting to an Excel file and Printing / Emailing time cards.</li>
                 <li>After reviewing, click "Close Pay Period" to end the pay period.  Finalized periods cannot be edited.</li>
                 <li>Upon closing, new pay period date range will be applied, current punches will be archived and accrued balances will be updated (Vac., Sick, etc.).</li>
                 </ol>
                
                <img src="Images/payroll_exceptions.webp" style="max-width:600px" alt="Payroll exceptions report with missing punches">
               
                <img src="Images/payroll_close_confirm.webp" style="max-width:600px" alt="Finalize payroll confirmation modal">
                
                <h3 id="overtime-calculation">How Overtime is Calculated</h3>
                
                <h4>Overtime Calculation Methods</h4>
                
                <p>Your overtime calculation depends on your subscription plan and configuration settings:</p>
                
                <p><strong>Basic and Business Plans:</strong> Overtime can be calculated using two methods:</p>
                <ul>
                    <li><strong>By Company State:</strong> All employees follow the overtime rules of your company's registered state, regardless of where individual employees are located.</li>
                    <li><strong>Manual Override:</strong> You can set custom overtime rules that override both federal and state calculations.</li>
                </ul>
                
                <p><strong>Pro Plan:</strong> Includes all the above options plus:</p>
                <ul>
                    <li><strong>By Employee State:</strong> Each employee's overtime is calculated according to the labor laws of the state listed in their employee profile. This ensures compliance with state-specific overtime rules, which can vary significantly. This feature is particularly useful for companies with remote workers or multiple office locations across different states.</li>
                </ul>
                
                <h4>Work Week Definition</h4>
                
                <p>The system calculates overtime based on a 7-day work week period. By default, the work week starts on <strong>Sunday at 12:00 AM</strong> and ends on <strong>Saturday at 11:59 PM</strong>. This means:</p>
                <ul>
                    <li>Hours are counted from Sunday through Saturday for overtime purposes</li>
                    <li>Overtime calculations reset each Sunday</li>
                    <li>Daily overtime rules (where applicable by state) are calculated within each 24-hour period</li>
                </ul>
                
                <p><em>Note: While it's optional, changing the first day of the work week in Settings is not recommended as it may affect overtime calculations and payroll consistency.</em></p>
                
                <h4>State-Specific Rules</h4>
                
                <p>Different states have varying overtime requirements:</p>
                <ul>
                    <li>Some states require overtime after 8 hours in a single day (daily overtime)</li>
                    <li>Others follow the federal standard of 40 hours per week</li>
                    <li>Certain states have different rates for double-time (2x pay) after specific thresholds</li>
                    <li>For complete state-by-state overtime rules, visit the <a href="https://www.dol.gov/agencies/whd/minimum-wage/state" target="_blank">Department of Labor's State Minimum Wage and Overtime Laws</a></li>
                </ul>
                
                <h4>Calculation Priority</h4>
                
                <p>When multiple overtime rules apply, the system uses whichever calculation results in the highest pay for the employee, ensuring compliance while maximizing employee compensation.<br>
                Punch types other than "User Initiated" or "Supervisor Override" (PTO), and Salaried employees are not included in Overtime calculations.</p>
                
                </section>
            
            <section id="reports">
                <h2>Reports</h2>
                <p>The Reports section provides detailed insights into your employee time data. You can view individual time cards, get payroll summaries, find attendance exceptions, and manage your employee roster. Most reports can be printed or exported.</p>
                
                <h3>Time Card Reports</h3>
                <p>This report allows you to view and print the detailed time card for a single employee or all employees for the current pay period. It includes a daily breakdown of punches, total hours, and overtime.</p>
                
                <ul>
                    <li><strong>To Print All Time Cards:</strong> Navigate to the <strong>Reports > Time Card Reports -> All Time Cards</strong> page and click the <code>Print All</code> button to generate a printable document for every active employee.</li>
                    <li>Each Time Card will print on a separate page</li>
                    <li>Time Cards can also be E-mailed to all employees using the <code>Email All</code> button</li>
                    <li><strong>To Print an Individual Time Card:</strong> Navigate to <strong>Reports > Time Card Reports > Individual Time Card</strong> and select an employee.</li>
                </ul>
                <img src="Images/timecards_all.webp" style="max-width:800px" alt="Printable view of all employee time cards">

                <h3>Exception Report</h3>
                <p>The Exception Report is a critical tool for ensuring payroll accuracy. It automatically finds and lists all punches that are missing an <strong>OUT</strong> time. <strong>All exceptions must be corrected before you can close the pay period.</strong></p> 
                <ul>
                    <li><strong>Accessing:</strong> Navigate to Reports select <code>Exception Report</code></li> 
                    <li><strong>Usage:</strong> Select a row and click <code>Fix Missing Punches</code> to resolve the error by entering the correct time.</li>
                </ul>
                <img src="Images/reports_exception.webp" style="max-width:800px" alt="Exception Report modal showing a missing punch">
                
                <h3>Employee Reports</h3>
                <p>The Employee Report provides a comprehensive list of all employees, both active and inactive. This is where you can manage employee statuses and view contact information.</p>
                <ul>
                    <li><strong>Accessing:</strong> Navigate to <code>Reports > Employee Reports</code> from the main navigation bar.</li>
                    <li><strong>Features:</strong> You can filter the list to see employees by department, schedule or supervisor, only active or inactive employees, then use the <strong>Reactivate Employee</strong> button to return them to the active roster.</li>
                </ul>
                <img src="Images/reports_all_active_employees.webp" style="max-width:800px" alt="Employee Report showing active and active employees">
                
                 <h3>Tardy Report</h3>
                <p>The Tardy Report provides a comprehensive sortable list of all employees with their number of late punches or early outs. You can filter tardies by current pay period, YTD or All Time</p>
                <ul>
                    <li><strong>Accessing:</strong> Navigate to <code>Reports > Tardy Report</code> from the main navigation bar.</li>
                    </ul>
                <img src="Images/reports_tardy.webp" style="max-width:800px" alt="Report showing employees with tardies">
                
                <h2>Other Reports</h2>
                
                <h3>Who's IN Report</h3>
                <p>The Who's IN Report provides a list of all employees who are currently punched IN.</p>
                <ul>
                    <li><strong>Accessing:</strong> Navigate to <code>Reports > Who's IN Report</code> from the main navigation bar.</li>
                    </ul>
                
                
                <h3>PTO Balance Report</h3>
                <p>The PTO Balance Report provides a comprehensive list of all employees with their current accrued PTO balances.</p>
				<ul>
					<li><strong>Accessing:</strong> Navigate to <code>Reports
							> PTO Balance Report</code> from the main navigation bar.</li>
				</ul>
                
				<h3>System Access Report</h3>
				<p>The System Access Report provides a list of all users with Administrative Permissions, for auditing purposes.</p>
				<ul>
					<li><strong>Accessing:</strong> Navigate to <code>Reports
							> System Access Report</code> from the main navigation bar.</li>
				</ul>
				
				<h3>Archived Punches Report</h3>
				<p>The Archived Punches Report provides a detailed list of all
					punches that have been archived after closing a pay period for auditing purposes.</p>
				<ul>
					<li><strong>Accessing:</strong> Navigate to <code>Reports
							> Archived Punches</code> from the main navigation bar.</li>
							<li>You can filter by All Employees, Individual Employee, Start and End Dates</li>
				</ul>
				
			</section>
            
            <section id="account">
                <h2>Account Settings</h2>
                <p>The Account page is for company-level administration, like managing your plan subscription and updating company info. Only the designated company admin can access these settings.</p>
                <img src="Images/account.webp"  style="max-width:600px" alt="Account settings page">
                <p>Click "Edit" to change company info. You will be required to enter the company admin user name and pasword.</p>
                <img src="Images/account_company_info.webp"  style="max-width:600px" alt="Editing company information">
                <p>Click "Manage Subscription and Billing" to change or cancel your subscription. Password credentials will be required.</p>
                <img src="Images/account_subscription.webp"  style="max-width:1000px" alt="Subscription management page">
            </section>
        </main>
    </div>
<%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
<script>
function searchPage() {
    const searchTerm = document.getElementById('search-field').value.toLowerCase().trim();
    if (!searchTerm) return;
    
    const mainContent = document.querySelector('main');
    const elements = mainContent.querySelectorAll('p, h1, h2, h3, li, td, th, span, div');
    
    for (let element of elements) {
        if (element.textContent.toLowerCase().includes(searchTerm)) {
            element.scrollIntoView({ behavior: 'smooth', block: 'center' });
            element.style.backgroundColor = '#ffff99';
            setTimeout(() => { element.style.backgroundColor = ''; }, 3000);
            return;
        }
    }
    
    alert('No results found. Please revise your search.');
}

document.addEventListener('DOMContentLoaded', function() {
    document.getElementById('search-field').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            searchPage();
        }
    });
});
</script>
</body>
</html>