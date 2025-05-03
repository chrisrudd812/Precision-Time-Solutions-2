<!DOCTYPE html>
<html>
<head>
<title>Login</title>
<%-- Make sure this CSS contains the .modal, .modal-visible, .modal-content styles --%>
<link rel="stylesheet" href="css/login.css?v=2"> <%-- Incremented version --%>

</head>
<body>
	<div class="parent-container">
		<h1>Welcome</h1>
		<h3>Enter Your Login Credentials</h3>
        <div class="button-container"> <%-- Assuming this container is for styling --%>
            <form action="employees.jsp"> <%-- Assuming this is your actual login form target --%>
                Username: <input type="text" name="username" autocomplete="off" autofocus>
                Password: <input type="password" name="password">
                <input type="submit" name="login" value="Login">
            </form>
            <br> <br>

            <%-- Other navigation buttons (keeping structure as provided) --%>
            <form action="employees.jsp"> <input type="submit" value="Show Employees"> </form>
            <form action="departments.jsp"> <input type="submit" value="Show Departments"> </form>
            <form action="scheduling.jsp"> <input type="submit" value="Show Schedules"> </form>
            <form action="accruals.jsp"> <input type="submit" value="Show Accrual Policies"> </form>

            <%-- *** MODIFIED: Changed from form/submit to button type="button" *** --%>
            <button type="button" id="addSampleDataBtn">Add Sample Data</button>

            <form action="settings.jsp"> <input type="submit" value="Settings"> </form>
            <form action="payroll.jsp"> <input type="submit" value="Payroll"> </form>
            <form action="timeclock.jsp"> <input type="submit" value="Time Clock"> </form>
            <form action="punches.jsp"> <input type="submit" value="Add Edit Punches"> </form>
        </div>
	</div>

    <%-- =================================== --%>
	<%--      NOTIFICATION MODAL           --%>
    <%-- (Make sure styles in style.css target this) --%>
	<%-- =================================== --%>
	<div id="notificationModal" class="modal">
		<div class="modal-content">
			<span class="close" id="closeNotificationModal">&times;</span>
			<h2>Notification</h2>
			<p id="notificationMessage"></p>
            <div class="button-row" style="justify-content: center;">
                <button type="button" id="okButton">OK</button>
            </div>
		</div>
	</div>

	<%-- Link to a NEW JavaScript file for this page's specific logic --%>
	<script type="text/javascript" src="js/login.js?v=2"></script> <%-- Incremented version --%>
</body>
</html>