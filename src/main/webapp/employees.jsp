<%@page import="timeclock.employees.ShowEmployees"%>
<%@page import="timeclock.accruals.Accruals"%> <%-- <<< IMPORT RESTORED --%>
<%@page import="timeclock.db.DatabaseConnection"%>
<%@ page import="java.sql.*"%>
<%@ page import="java.util.ArrayList"%>
<%@ page import="java.time.LocalDate"%>
<%@ page import="java.time.format.DateTimeFormatter"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%> <%-- Added language/contentType back --%>


<%
    // Scriptlet to get dropdown data for modals
    ArrayList<String> departmentsList = new ArrayList<>();
    ArrayList<String> schedulesList = new ArrayList<>();
    ArrayList<String> accrualsList = new ArrayList<>();
    try(Connection con = DatabaseConnection.getConnection();
    		PreparedStatement psGetDepartments = con.prepareStatement("SELECT NAME FROM DEPARTMENTS ORDER BY NAME");
		    PreparedStatement psGetSchedules = con.prepareStatement("SELECT NAME FROM SCHEDULES ORDER BY NAME");
		    PreparedStatement psGetAccruals = con.prepareStatement("SELECT NAME FROM ACCRUALS ORDER BY NAME");) {
            try (ResultSet rs = psGetDepartments.executeQuery()) { while (rs.next()) { departmentsList.add(rs.getString("NAME")); } }
            try (ResultSet rs = psGetSchedules.executeQuery()) { while (rs.next()){ schedulesList.add(rs.getString("NAME")); } }
            try (ResultSet rs = psGetAccruals.executeQuery()) { while (rs.next()){ accrualsList.add(rs.getString("NAME")); } }
    } catch (Exception e) {
         System.err.println("Error fetching dropdown data for employees page: " + e.getMessage());
        e.printStackTrace(); // Log the error
    }
    // Get EID if passed (for maintaining selection after add/edit/delete redirect)
    int eid = 0;
    String selectedEIDParam = request.getParameter("eid"); // Assuming servlet redirects with "eid"
    if(selectedEIDParam != null && !selectedEIDParam.trim().isEmpty()) {
        try {
            eid = Integer.parseInt(selectedEIDParam);
        } catch (NumberFormatException nfe) {
            System.err.println("Invalid EID passed in parameter: " + selectedEIDParam);
            eid = 0;
        }
    }
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Employee Management</title>
    <%-- Link BOTH CSS files --%>
    <link rel="stylesheet" href="css/navbar.css"> <%-- Navbar styles first --%>
    <link rel="stylesheet" href="css/employees.css?v=9"> <%-- Page specific styles second (bump version) --%>
</head>
<body>
    <%-- Include Navbar --%>
    

	<div class="parent-container">
	
	<%@ include file="/WEB-INF/includes/navbar.jspf" %>
		<h1>Employee Management</h1>
		

		<div class="table-container">
            <%-- Added id="employees" and removed class="punches" --%>
			<table id="employees" class="employee-table">
				<thead>
					<tr>
						<th>Employee ID</th><th>First Name</th><th>Last Name</th><th>Department</th><th>Schedule</th>
                        <th>Permissions</th><th>Address</th><th>City</th><th>State</th><th>Zip</th>
                        <th>Phone</th><th>E-mail</th><th>Accrual Policy</th>
                        <th>Vac Hrs</th><th>Sick Hrs</th><th>Pers Hrs</th>
                        <th>Hire Date</th><th>Work Sched</th>
                        <th>Wage Type</th><th>Wage</th>
					</tr>
				</thead>
				<tbody>
					<%-- Ensure ShowEmployees adds data-* attributes for JS --%>
                    <%= ShowEmployees.showEmployees() %>
				</tbody>
			</table>
		</div>

		<div id="button-container">
			<button type="button" id="addEmployeeButton">Add Employee</button>
			<button type="button" id="editEmployeeButton" disabled>Edit Employee</button>
			<button type="button" id="deleteEmployeeButton" disabled>Delete Employee</button>
		</div>

        <%-- Employee Details Section --%>
        <div id="employeeDetailsSection" style="display: none;">
            <h2>Employee Details</h2>
            <div class="details-grid">
                <div class="detail-group">
                    <h3>Personal Information</h3>
                    <p><label>Employee ID:</label> <span id="detailEID">--</span></p>
                    <p><label>First Name:</label> <span id="detailFirstName">--</span></p>
                    <p><label>Last Name:</label> <span id="detailLastName">--</span></p>
                    <p><label>Address:</label> <span id="detailAddress">--</span></p>
                    <p><label>City:</label> <span id="detailCity">--</span></p>
                    <p><label>State:</label> <span id="detailState">--</span></p>
                    <p><label>Zip:</label> <span id="detailZip">--</span></p>
                    <p><label>Phone:</label> <span id="detailPhone">--</span></p>
                    <p><label>E-mail:</label> <span id="detailEmail">--</span></p>
                </div>
                <div class="detail-group">
                    <h3 id="companyInfoH3">Company Information</h3>
                    <p><label>Department:</label> <span id="detailDept">--</span></p>
                    <p><label>Schedule:</label> <span id="detailSchedule">--</span></p>
                    <p><label>Permissions:</label> <span id="detailPermissions">--</span></p>
                    <p><label>Hire Date:</label> <span id="detailHireDate">--</span></p>
                    <p><label>Work Schedule:</label> <span id="detailWorkSched">--</span></p>
                    <p><label>Wage Type:</label> <span id="detailWageType">--</span></p>
                    <p><label>Wage:</label> <span id="detailWage">--</span></p>
                </div>
                 <div id="accrualInfoColumn" class="detail-group">
                     <h3>Accrual Information</h3>
                     <p><label>Accrual Policy:</label> <span id="detailAccrualPolicy">--</span></p>
                     <p><label>Vacation Hours:</label> <span id="detailVacHours">--</span></p>
                     <p><label>Sick Hours:</label> <span id="detailSickHours">--</span></p>
                     <p><label>Personal Hours:</label> <span id="detailPersHours">--</span></p>
                 </div>
            </div> <%-- End details-grid --%>
        </div>

        <%-- Hidden form for delete action --%>
		<form action="AddEditAndDeleteEmployeesServlet" method="get" id="deleteForm" style="display: none;">
			<input type="hidden" name="eid" id="hiddenEID" value="">
		</form>

	</div> <%-- End of parent-container --%>

	<%-- Modals (Add, Edit, Notification) --%>
	<div id="addEmployeeModal" class="modal">
        <div class="modal-content"> <span class="close" id="closeAddModal">&times;</span> <h2>Add Employee</h2> <form id="addEmployeeForm" action="AddEditAndDeleteEmployeesServlet" method="post"> <div class="form-item"><label for="addEID">Employee ID:</label><input type="text" id="addEID" name="addEID_display" placeholder="Auto Generated" readonly disabled></div> <div class="form-row"> <div class="form-item"><label for="addFirstName">First Name:</label><input type="text" id="addFirstName" name="addFirstName" required autofocus autocomplete="password"></div> <div class="form-item"><label for="addLastName">Last Name:</label><input type="text" id="addLastName" name="addLastName" required autocomplete="password"></div> </div> <div class="form-row"> <div class="form-item"><label for="addDepartmentsDropDown">Department:</label><select id="addDepartmentsDropDown" name="addDepartmentsDropDown"><% for (String department : departmentsList) { %><option value="<%= department %>"><%= department %></option><% } %></select></div> <div class="form-item"><label for="addSchedulesDropDown">Schedule:</label><select id="addSchedulesDropDown" name="addSchedulesDropDown"><% for (String schedule : schedulesList) { %><option value="<%= schedule %>"><%= schedule %></option><% } %></select></div> </div> <div class="form-row"> <div class="form-item"><label for="addPermissionsDropDown">Permissions:</label><select id="addPermissionsDropDown" name="addPermissionsDropDown"><option value="User">User</option><option value="Administrator">Administrator</option></select></div> <div class="form-item"><label for="addAccrualsDropDown">Accrual Policy:</label><select id="addAccrualsDropDown" name="addAccrualsDropDown"><% for (String accrual : accrualsList) { %><option value="<%= accrual %>"><%= accrual %></option><% } %></select></div> </div> <div class="form-item"><label for="addAddress">Address:</label><input type="text" id="addAddress" name="addAddress" autocomplete="password"></div> <div class="form-row"> <div class="form-item"><label for="addCity">City:</label><input type="text" id="addCity" name="addCity"></div> <div class="form-item"><label for="addState">State:</label><select id="addState" name="addState"><option value="">Select</option><option value="AL">AL</option><option value="AK">AK</option><option value="AZ">AZ</option><option value="AR">AR</option><option value="CA">CA</option><option value="CO">CO</option><option value="CT">CT</option><option value="DE">DE</option><option value="DC">DC</option><option value="FL">FL</option><option value="GA">GA</option><option value="HI">HI</option><option value="ID">ID</option><option value="IL">IL</option><option value="IN">IN</option><option value="IA">IA</option><option value="KS">KS</option><option value="KY">KY</option><option value="LA">LA</option><option value="ME">ME</option><option value="MD">MD</option><option value="MA">MA</option><option value="MI">MI</option><option value="MN">MN</option><option value="MS">MS</option><option value="MO">MO</option><option value="MT">MT</option><option value="NE">NE</option><option value="NV">NV</option><option value="NH">NH</option><option value="NJ">NJ</option><option value="NM">NM</option><option value="NY">NY</option><option value="NC">NC</option><option value="ND">ND</option><option value="OH">OH</option><option value="OK">OK</option><option value="OR">OR</option><option value="PA">PA</option><option value="RI">RI</option><option value="SC">SC</option><option value="SD">SD</option><option value="TN">TN</option><option value="TX">TX</option><option value="UT">UT</option><option value="VT">VT</option><option value="VA">VA</option><option value="WA">WA</option><option value="WV">WV</option><option value="WI">WI</option><option value="WY">WY</option></select></div> <div class="form-item"><label for="addZip">Zip Code:</label><input type="text" id="addZip" name="addZip" pattern="^\d{5}(-\d{4})?$" title="Enter a 5-digit or 9-digit (ZIP+4) code." maxlength="10"></div> </div> <div class="form-row"> <div class="form-item"><label for="addPhone">Phone Number:</label><input type="tel" id="addPhone" name="addPhone" autocomplete="password" pattern="^\+?1?[\s.\-]?\(?(\d{3})\)?[\s.\-]?(\d{3})[\s.\-]?(\d{4})$" title="E.g., 123-456-7890, (123) 456-7890, +1 123 456 7890"></div> <div class="form-item"><label for="addEmail">E-mail:</label><input type="email" id="addEmail" name="addEmail" autocomplete="password"></div> </div> <div class="form-item"><label for="addHireDate">Hire Date:</label><input type="date" id="addHireDate" name="addHireDate" value="<%= LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) %>"></div> <div class="form-row"> <div class="form-item"><label for="addWorkScheduleDropDown">Work Schedule:</label><select id="addWorkScheduleDropDown" name="addWorkScheduleDropDown"><option>Full Time</option><option>Part Time</option><option>Temporary</option></select></div> <div class="form-item"><label for="addWageTypeDropDown">Wage Type:</label><select id="addWageTypeDropDown" name="addWageTypeDropDown"><option>Hourly</option><option>Salary</option></select></div> </div> <div class="form-item"> <label for="addWage">Wage:</label> <div class="input-with-symbol"> <span class="currency-symbol">$</span> <input type="number" id="addWage" name="addWage" step="0.01" required min="0" placeholder="Rate or Salary"> </div> </div> <input type="hidden" name="addEmployee" value="true"> </form> <div class="button-row"> <button type="submit" form="addEmployeeForm" class="submit-btn">Submit</button> <button type="button" id="cancelAdd" class="cancel-btn">Cancel</button> </div> </div>
    </div>
	<div id="editEmployeeModal" class="modal">
        <div class="modal-content"> <span class="close" id="closeEditModal">&times;</span> <h2>Edit Employee</h2> <form id="editEmployeeForm" action="AddEditAndDeleteEmployeesServlet" method="post"> <div class="form-item"> <label for="editEID">Employee ID:</label> <input type="text" id="editEID" readonly disabled> </div> <input type="hidden" id="hiddenEditEID" name="eid" value=""> <div class="form-row"> <div class="form-item"><label for="editFirstName">First Name:</label><input type="text" id="editFirstName" name="firstName" required autocomplete="password"></div> <div class="form-item"><label for="editLastName">Last Name:</label><input type="text" id="editLastName" name="lastName" autocomplete="password"></div> </div> <div class="form-row"> <div class="form-item"><label for="editDepartmentsDropDown">Department:</label><select id="editDepartmentsDropDown" name="departmentsDropDown"><% for (String department : departmentsList) { %><option value="<%= department %>"><%= department %></option><% } %></select></div> <div class="form-item"><label for="editSchedulesDropDown">Schedule:</label><select id="editSchedulesDropDown" name="schedulesDropDown"><% for (String schedule : schedulesList) { %><option value="<%= schedule %>"><%= schedule %></option><% } %></select></div> </div> <div class="form-row"> <div class="form-item"><label for="editPermissionsDropDown">Permissions:</label><select id="editPermissionsDropDown" name="permissionsDropDown"><option value="User">User</option><option value="Administrator">Administrator</option></select></div> <div class="form-item"><label for="editAccrualsDropDown">Accrual Policy:</label><select id="editAccrualsDropDown" name="accrualsDropDown"><% for (String accrual : accrualsList) { %><option value="<%= accrual %>"><%= accrual %></option><% } %></select></div> </div> <div class="form-item"><label for="editAddress">Address:</label><input type="text" id="editAddress" name="address" autocomplete="password"></div> <div class="form-row"> <div class="form-item"><label for="editCity">City:</label><input type="text" id="editCity" name="city"></div> <div class="form-item"><label for="editState">State:</label><select id="editState" name="state"><option value="">Select</option><option value="AL">AL</option><option value="AK">AK</option><option value="AZ">AZ</option><option value="AR">AR</option><option value="CA">CA</option><option value="CO">CO</option><option value="CT">CT</option><option value="DE">DE</option><option value="DC">DC</option><option value="FL">FL</option><option value="GA">GA</option><option value="HI">HI</option><option value="ID">ID</option><option value="IL">IL</option><option value="IN">IN</option><option value="IA">IA</option><option value="KS">KS</option><option value="KY">KY</option><option value="LA">LA</option><option value="ME">ME</option><option value="MD">MD</option><option value="MA">MA</option><option value="MI">MI</option><option value="MN">MN</option><option value="MS">MS</option><option value="MO">MO</option><option value="MT">MT</option><option value="NE">NE</option><option value="NV">NV</option><option value="NH">NH</option><option value="NJ">NJ</option><option value="NM">NM</option><option value="NY">NY</option><option value="NC">NC</option><option value="ND">ND</option><option value="OH">OH</option><option value="OK">OK</option><option value="OR">OR</option><option value="PA">PA</option><option value="RI">RI</option><option value="SC">SC</option><option value="SD">SD</option><option value="TN">TN</option><option value="TX">TX</option><option value="UT">UT</option><option value="VT">VT</option><option value="VA">VA</option><option value="WA">WA</option><option value="WV">WV</option><option value="WI">WI</option><option value="WY">WY</option></select></div> <div class="form-item"><label for="editZip">Zip Code:</label><input type="text" id="editZip" name="zip" pattern="^\d{5}(-\d{4})?$" title="Enter a 5-digit or 9-digit (ZIP+4) code." maxlength="10"></div> </div> <div class="form-row"> <div class="form-item"><label for="editPhone">Phone Number:</label><input type="tel" id="editPhone" name="phone" autocomplete="password" pattern="^\+?1?[\s.\-]?\(?(\d{3})\)?[\s.\-]?(\d{3})[\s.\-]?(\d{4})$" title="E.g., 123-456-7890, (123) 456-7890, +1 123 456 7890"></div> <div class="form-item"><label for="editEmail">E-mail:</label><input type="email" id="editEmail" name="email" autocomplete="password"></div> </div> <div class="form-item"><label for="editHireDate">Hire Date:</label><input type="date" id="editHireDate" name="hireDate"></div> <div class="form-row"> <div class="form-item"><label for="editWorkScheduleDropDown">Work Schedule:</label><select id="editWorkScheduleDropDown" name="workScheduleDropDown"><option>Full Time</option><option>Part Time</option><option>Temporary</option></select></div> <div class="form-item"><label for="editWageTypeDropDown">Wage Type:</label><select id="editWageTypeDropDown" name="wageTypeDropDown"><option>Hourly</option><option>Salary</option></select></div> </div> <div class="form-item"> <label for="editWage">Wage:</label> <div class="input-with-symbol"> <span class="currency-symbol">$</span> <input type="number" id="editWage" name="wage" step="0.01" required min="0"> </div> </div> <input type="hidden" name="editEmployee" value="true"> </form> <div class="button-row"> <button type="submit" form="editEmployeeForm" class="submit-btn">Update</button> <button type="button" id="cancelEdit" class="cancel-btn">Cancel</button> </div> </div>
    </div>
	<div id="notificationModal" class="modal">
         <div class="modal-content"> <span class="close" id="closeNotificationModal">&times;</span> <h2>Notification</h2> <p id="notificationMessage"></p> <div class="button-row" style="justify-content: center;"> <button type="button" id="okButton">OK</button> </div> </div>
    </div>

	<script type="text/javascript" src="js/employees.js?v=8"></script> <%-- Bump JS version --%>
</body>
</html>