<%@page import="timeclock.accruals.ShowAccruals"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<%-- Link to the updated CSS file --%>
<link rel="stylesheet" href="css/accruals.css?v=3"> <%-- Cache-busting version incremented --%>
<title>Accrual Policies</title>
</head>
<body>
	<div class="parent-container">
		<h1>Manage Accrual Policies</h1>
		<h4>To Edit or Delete... First Select a Row</h4>

		<%-- Placeholder for any success/error messages --%>

		<div class="table-container">
			<table class="punches" id="accruals">
				<thead>
					<tr>
						<th>Policy Name</th>
						<th>Annual Vacation Days</th>
						<th>Annual Sick Days</th>
						<th>Annual Personal Days</th>
					</tr>
				</thead>
				<tbody>
					<%= ShowAccruals.showAccruals() %>
				</tbody>
			</table>
		</div>

		<div id="button-container">
			<button type="button" id="btnAddPolicy">Add Accrual Policy</button>
			<button type="button" id="btnEditRow" disabled>Edit Accrual Policy</button>
			<button type="button" id="btnDeleteRow" disabled>Delete Accrual Policy</button>
		</div>

		<%-- Form for DELETING (using JavaScript) --%>
		<form action="AddAndDeleteAccrualPoliciesServlet" method="get" id="deleteForm">
			<input type="hidden" name="delete" id="hiddenAccrualName" value="">
		</form>
	</div> <%-- End of parent-container --%>

	<%-- =================================== --%>
	<%--        ADD ACCRUAL MODAL          --%>
	<%-- =================================== --%>
	<div id="addAccrualModal" class="modal">
		<div class="modal-content">
			<span class="close" id="closeAddModal">&times;</span>
			<h2 style="text-align: center;">Add Accrual Policy</h2>

			<form id="addAccrualForm" action="AddAndDeleteAccrualPoliciesServlet" method="post">
				<div class="form-item">
					<label for="addAccrualName">Accrual Policy Name:</label>
					<input type="text" id="addAccrualName" name="addAccrualName" maxlength="30" required autofocus>
				</div>
				<div class="form-item">
					<label for="addVacationDays">Annual Vacation Days:</label>
					<input type="number" id="addVacationDays" name="addVacationDays" required value="0" min="0">
				</div>
				<div class="form-item">
					<label for="addSickDays">Annual Sick Days:</label>
					<input type="number" id="addSickDays" name="addSickDays" required value="0" min="0">
				</div>
				<div class="form-item">
					<label for="addPersonalDays">Annual Personal Days:</label>
					<input type="number" id="addPersonalDays" name="addPersonalDays" required value="0" min="0">
				</div>
				<input type="hidden" name="addAccrual" value="true">
			</form>

			<div class="button-row">
				<%-- *** Added class="submit-btn" *** --%>
				<button type="submit" form="addAccrualForm" class="submit-btn">Submit</button>
				<%-- *** Added class="cancel-btn" *** --%>
				<button type="button" id="cancelAdd" class="cancel-btn">Cancel</button>
			</div>
		</div> <%-- End of modal-content --%>
	</div> <%-- End of addAccrualModal --%>

	<%-- =================================== --%>
	<%--        EDIT ACCRUAL MODAL         --%>
	<%-- =================================== --%>
	<div id="editAccrualModal" class="modal">
		<div class="modal-content">
			<span class="close" id="closeEditModal">&times;</span>
			<h2>Edit Accrual Policy</h2> <%-- Removed inline style --%>

			<%-- Form contains only inputs --%>
			<form id="editAccrualForm" action="AddAndDeleteAccrualPoliciesServlet" method="post">
				<div class="form-item">
					<label for="editAccrualName">Accrual Policy Name:</label>	
					<input type="text" id="editAccrualName" name="editAccrualName" required>
					<input type="hidden" id="originalAccrualName" name="originalAccrualName">
				</div>
				<div class="form-item">
					<label for="editVacationDays">Annual Vacation Days:</label>
					<input type="number" id="editVacationDays" name="editVacationDays" required min="0">
				</div>
				<div class="form-item">
					<label for="editSickDays">Annual Sick Days:</label>
					<input type="number" id="editSickDays" name="editSickDays" required min="0">
				</div>
				<div class="form-item">
					<label for="editPersonalDays">Annual Personal Days:</label>
					<input type="number" id="editPersonalDays" name="editPersonalDays" required min="0">
				</div>
                <input type="hidden" name="editAccrual" value="true">
                <%-- Button Row is MOVED OUTSIDE the form tag --%>
				<%-- <div class="button-row"> ... BUTTONS WERE HERE ... </div> --%>
			</form> <%-- End of editAccrualForm --%>

            <%-- *** Button Row is NOW HERE, directly inside modal-content *** --%>
            <div class="button-row">
                <button type="submit" form="editAccrualForm" class="submit-btn">Update</button>
                <button type="button" id="cancelEdit" class="cancel-btn">Cancel</button>
            </div>

		</div> <%-- End of modal-content --%>
	</div> <%-- End of editAccrualModal --%>

	<%-- =================================== --%>
	<%--      NOTIFICATION MODAL           --%>
	<%-- =================================== --%>
	<div id="notificationModal" class="modal">
		<div class="modal-content">
			<span class="close" id="closeNotificationModal">&times;</span>
			<h2>Notification</h2>
			<p id="notificationMessage"></p>
			<%-- Removed extra button-container div for consistency --%>
            <div class="button-row" style="justify-content: center;"> <%-- Inline style to center single button --%>
                <%-- Consider adding a specific class like 'ok-btn' if more styling needed --%>
                <button type="button" id="okButton">OK</button>
            </div>
		</div> <%-- End of modal-content --%>
	</div> <%-- End of notificationModal --%>

	<%-- Link to JavaScript file --%>
	<script type="text/javascript" src="js/accruals.js?v=3"></script> <%-- Cache-busting version incremented --%>
</body>
</html>