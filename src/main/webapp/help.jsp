<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Help Topics</title>
    <link rel="stylesheet" href="css/navbar.css">
    <link rel="stylesheet" href="css/help.css"> <%-- Link to the new help CSS --%>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
</head>
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="help-container parent-container">
        <h1>Help Topics</h1>

        <div class="help-layout">
            <nav class="help-nav">
                <h2>Navigation</h2>
                <ul>
                    <li><a href="#getting-started">Getting Started</a></li>
                    <li><a href="#time-clock">Using the Time Clock</a></li>
                    <li><a href="#employee-management">Employee Management</a></li>
                    <li><a href="#scheduling">Scheduling</a></li>
                    <li><a href="#accruals">Accruals</a></li>
                    <li><a href="#payroll">Payroll Procedure</a></li>
                    <li><a href="#reports">Reports</a></li>
                    <li><a href="#settings">Settings</a></li>
                    <li><a href="#account">Account Management</a></li>
                    <li><a href="contact.jsp">Contact Us</a></li>
                </ul>
            </nav>

            <main class="help-content">
                <section id="getting-started">
                    <h2>Getting Started</h2>
                    <p>Welcome to the Time Clock application! This section provides a brief overview...</p>
                    <p>First steps involve configuring your company settings and adding departments, schedules, and accrual policies before adding employees.</p>
                    </section>

                <section id="time-clock">
                    <h2>Using the Time Clock</h2>
                    <p>The main Time Clock page allows employees to punch IN and OUT. Ensure the correct employee is selected (if applicable based on permissions).</p>
                    <p>Click "PUNCH IN" when starting work and "PUNCH OUT" when finishing work or starting a break (if breaks aren't automatic).</p>
                    <p>Your current time card for the active pay period is displayed below the clock.</p>
                    </section>

                <section id="employee-management">
                    <h2>Employee Management</h2>
                    <p>Administrators can add, edit, and deactivate employees from the "Employees" page.</p>
                    <ul>
                        <li><strong>Add Employee:</strong> Click the "Add Employee" button and fill in the required details in the modal. Email is mandatory and used for login. New employees get a default PIN of "1234" which they must change.</li>
                        <li><strong>Edit Employee:</strong> Select an employee row in the table, then click "Edit Employee". Modify details in the modal and click "Update".</li>
                        <li><strong>Deactivate Employee:</strong> Select an employee row, then click "Deactivate Employee". This prevents login and payroll inclusion but retains their data.</li>
                        <li><strong>Reset Password:</strong> Select an employee row to view details, then click the "Reset Password" button in the details section.</li>
                    </ul>
                    </section>

                 <section id="scheduling">
                    <h2>Scheduling</h2>
                    <p>Define work schedules (shifts, days, auto-lunch rules) on the "Scheduling" page. Assign employees to these schedules via the "Employees" page.</p>
                     </section>

                 <section id="accruals">
                     <h2>Accruals</h2>
                     <p>Set up different accrual policies (e.g., Standard, Executive) defining annual vacation, sick, and personal days on the "Accruals" page. Assign policies to employees via the "Employees" page.</p>
                     <p>Accrued hours are calculated and added automatically when a pay period is closed in the Payroll section. You can also add individual or global hours manually via the "Accruals" dropdown.</p>
                     </section>

                <section id="payroll">
                    <h2>Payroll Procedure</h2>
                    <p>The "Payroll" page calculates pay based on punches and settings for the current pay period.</p>
                    <ol>
                        <li>Review the calculated payroll data.</li>
                        <li>Run the "Exception Report" (from Payroll or Reports menu) to check for missed punches. Correct any exceptions using "Edit Employee Punches".</li>
                        <li>(Optional) Export the payroll data to Excel/CSV.</li>
                        <li>(Optional) Print the payroll summary.</li>
                        <li>Click "Close Pay Period". This finalizes OT, archives punches, updates accruals, logs history, and sets the next pay period dates. **This cannot be easily undone.**</li>
                    </ol>
                    </section>

                 <section id="reports">
                     <h2>Reports</h2>
                     <p>Various reports are available under the "Reports" menu:</p>
                     <ul>
                         <li><strong>Exception Report:</strong> Shows punches needing correction (typically missed punches).</li>
                         <li><strong>Tardy Report:</strong> Summarizes late arrivals or early departures.</li>
                         <li><strong>Who's In Report:</strong> Lists employees currently clocked in.</li>
                         <li><strong>Time Card Reports:</strong> View and print time cards individually, for all employees, or filtered by department, schedule, or supervisor.</li>
                         <li><strong>Archived Punches:</strong> View historical punch data from closed pay periods.</li>
                     </ul>
                     </section>

                 <section id="settings">
                     <h2>Settings</h2>
                     <p>Configure application-wide settings like pay period type, first day of the work week, grace periods, and overtime rules on the "Settings" page.</p>
                     </section>

                 <section id="account">
                      <h2>Account Management</h2>
                      <p>The "Account" page allows you to manage your own user details, such as changing your password.</p>
                      </section>

            </main>
        </div>
    </div>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>