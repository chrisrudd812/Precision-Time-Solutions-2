<%@page import="timeclock.departments.ShowDepartments"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<%-- Link to the updated CSS file --%>
<link rel="stylesheet" href="css/departments.css?v=3"> <%-- Cache-busting version incremented --%>
<link rel="stylesheet" href="css/navbar.css">
<title>Departments</title>
</head>
<body>
	<div class="parent-container">
	<%@ include file="/WEB-INF/includes/navbar.jspf" %>
		<h1>Manage Departments</h1>
		<h4>To Edit or Delete... First Select a Row</h4>

		<%-- Placeholder for any success/error messages --%>

		<div class="table-container">
			<table class="punches" id="departments">
				<thead>
					<tr>
						<th>Department Name</th>
						<th>Description</th>
						<th>Department Supervisor</th>
					</tr>
				</thead>
				<tbody>
					<%= ShowDepartments.showDepartments() %>
				</tbody>
			</table>
		</div>

		<div id="button-container">
			<button type="button" id="btnAddDepartment">Add Department</button>
			<button type="button" id="btnEditDepartment" disabled>Edit Department</button>
			<button type="button" id="btnDeleteDepartment" disabled>Delete Department</button>
		</div>

		<%-- Form for DELETING (using JavaScript) --%>
		<form action="AddAndDeleteDepartmentsServlet" method="get" id="deleteForm">
			<input type="hidden" name="delete" id="hiddenDepartmentName" value="">
		</form>
	</div> <%-- End of parent-container --%>

	<%-- =================================== --%>
	<%--        ADD DEPARTMENT MODAL          --%>
	<%-- =================================== --%>
	<div id="addDepartmentModal" class="modal">
		<div class="modal-content">
			<span class="close" id="closeAddModal">&times;</span>
			<h2 style="text-align: center;">Add Department</h2>

			<form id="addDepartmentForm" action="AddAndDeleteDepartmentsServlet" method="post">
				<div class="form-item">
					<label for="addDepartmentName">Department Name:</label>
					<input type="text" id="addDepartmentName" name="addDepartmentName" maxlength="30" required autofocus placeholder="Required">
				</div>
				<div class="form-item">
					<label for="addDescription">Description:</label>
					<input type="text" id="addDescription" name="addDescription" placeholder="Optional">
				</div>
				<div class="form-item">
					<label for="addSupervisor">Department Supervisor:</label>
					<input type="text" id="addSupervisor" name="addSupervisor" placeholder="Optional">
				</div>
				<input type="hidden" name="addDepartment" value="true">
			</form>

			<div class="button-row">
				<%-- *** Added class="submit-btn" *** --%>
				<button type="submit" form="addDepartmentForm" class="submit-btn">Submit</button>
				<%-- *** Added class="cancel-btn" *** --%>
				<button type="button" id="cancelAdd" class="cancel-btn">Cancel</button>
			</div>
		</div> <%-- End of modal-content --%>
	</div> <%-- End of addDepartmentModal --%>

	<%-- =================================== --%>
	<%--        EDIT DEPARTMENT MODAL         --%>
	<%-- =================================== --%>
	<div id="editDepartmentModal" class="modal">
		<div class="modal-content">
			<span class="close" id="closeEditModal">&times;</span>
			<h2>Edit Department</h2> <%-- Removed inline style --%>

			<%-- Form contains only inputs --%>
			<form id="editDepartmentForm" action="AddAndDeleteDepartmentsServlet" method="post">
				<div class="form-item">
					<label for="editDepartmentName">Department Name:</label>	
					<input type="text" id="editDepartmentName" name="editDepartmentName" required>
					<input type="hidden" id="originalDepartmentName" name="originalDepartmentName">
				</div>
				<div class="form-item">
					<label for="editDescription">Description:</label>
					<input type="text" id="editDescription" name="editDescription">
				</div>
				<div class="form-item">
					<label for="editSupervisor">Department Supervisor:</label>
					<input type="text" id="editSupervisor" name="editSupervisor">
				</div>
				<input type="hidden" name="editDepartment" value="true">
				
                
			</form> <%-- End of editDepartmentForm --%>

            <div class="button-row">
                <button type="submit" form="editDepartmentForm" class="submit-btn">Update</button>
                <button type="button" id="cancelEdit" class="cancel-btn">Cancel</button>
            </div>

		</div> <%-- End of modal-content --%>
	</div> <%-- End of editDepartmentModal --%>

	<%-- =================================== --%>
	<%--      NOTIFICATION MODAL           --%>
	<%-- =================================== --%>
	<div id="notificationModal" class="modal">
		<div class="modal-content">
			<span class="close" id="closeNotificationModal">&times;</span>
			<h2>Notification</h2>
			<p id="notificationMessage"></p>
			
            <div class="button-row" style="justify-content: center;"> <%-- Inline style to center single button --%>
                
                <button type="button" id="okButton">OK</button>
            </div>
		</div> <%-- End of modal-content --%>
	</div> <%-- End of notificationModal --%>

	<%-- Link to JavaScript file --%>
	<script type="text/javascript" src="js/departments.js?v=3"></script> <%-- Cache-busting version incremented --%>
</body>
</html>