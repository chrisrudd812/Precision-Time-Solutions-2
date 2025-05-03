<%@ page import="timeclock.punches.ShowPunches"%>
<%@ page import="timeclock.db.DatabaseConnection" %>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.sql.*" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.time.ZoneId" %>
<%@ page import="java.time.LocalDate"%>
<%@ page import="java.time.format.DateTimeFormatter"%>

<%
    // Scriptlet to get employee list for dropdown
    ArrayList<String> employeeList = new ArrayList<>();
    int eid = 0; // Initialize eid
    String selectedEmployeeIdStr = request.getParameter("employeesDropDown"); // Check dropdown value first
    if (selectedEmployeeIdStr == null || selectedEmployeeIdStr.trim().isEmpty()) { // Check if dropdown was empty or not submitted
        selectedEmployeeIdStr = request.getParameter("eid"); // Check for eid param (e.g., from redirect or initial load)
    }
    String pageError = null;

    // Validate selectedEmployeeId
    if (selectedEmployeeIdStr != null && !selectedEmployeeIdStr.trim().isEmpty()) {
        try {
            eid = Integer.parseInt(selectedEmployeeIdStr.trim());
             if (eid <= 0) { // Don't allow non-positive EIDs
                 eid = 0;
                 pageError = "Invalid Employee ID selected (must be positive).";
             }
        } catch (NumberFormatException e) {
            System.err.println("Invalid EID format received: " + selectedEmployeeIdStr);
            pageError = "Invalid Employee ID selected.";
            eid = 0;
        }
    }

    // Get User's Time Zone ID (use a default if not found in session)
    String userTimeZoneId = (String) session.getAttribute("userTimeZoneId");
    if (userTimeZoneId == null || userTimeZoneId.trim().isEmpty()) {
        userTimeZoneId = "America/Denver"; // Fallback Default
        // Optionally store the default back in session if desired
        // session.setAttribute("userTimeZoneId", userTimeZoneId);
    }

    // Populate employee list for dropdown
    try (Connection con = DatabaseConnection.getConnection();
         PreparedStatement psGetEmployees = con.prepareStatement("SELECT EID, LAST_NAME, FIRST_NAME FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME");
         ResultSet rsEmp = psGetEmployees.executeQuery()) {
        while (rsEmp.next()) {
            // Format as "EID - LastName, FirstName"
            String ln = rsEmp.getString("LAST_NAME");
            String fn = rsEmp.getString("FIRST_NAME");
            String lastName = (ln != null && !ln.trim().isEmpty()) ? ln.trim() : "";
            String firstName = (fn != null && !fn.trim().isEmpty()) ? fn.trim() : "";
            String displayName = lastName + (lastName.isEmpty() || firstName.isEmpty() ? "" : ", ") + firstName;
            employeeList.add(rsEmp.getInt("EID") + " - " + displayName);
        }
    } catch (Exception e) {
        System.err.println("Error loading employee list: " + e.getMessage());
        e.printStackTrace(); // Log full trace to server logs
        pageError = "Error loading employee list from database."; // User-friendly message
    }

    // Check for redirect messages (success/error parameters in URL)
    String successMessage = request.getParameter("message");
    String errorMessage = request.getParameter("error");
    if (errorMessage != null && !errorMessage.isEmpty()) {
         pageError = errorMessage; // Prioritize error message from parameter
         successMessage = null; // Clear success if error exists
    }
%>

<!DOCTYPE html>
<html lang="en">
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Edit Punch Data<%= eid > 0 ? " - EID " + eid : "" %></title>
    <link rel="stylesheet" href="css/navbar.css"> <%-- Assuming navbar CSS exists --%>
    <link rel="stylesheet" href="css/punches.css?v=11"> <%-- Use versioning to help cache busting --%>
</head>
<body>
    <%-- Include Navbar --%>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container">
        <%-- Redundant navbar include removed --%>
        <h1>Edit Punch Data</h1><br><br>

         <%-- Display success/error messages --%>
        <% if (successMessage != null && !successMessage.isEmpty()) { %>
            <div id="notification-bar" class="success-message"><%= successMessage %></div>
        <% } else if (pageError != null && !pageError.isEmpty()) { %>
            <%-- Display page load errors separately or combine --%>
            <div id="notification-bar" class="error-message"><%= pageError %></div>
        <% } %>

        <%-- Employee Selection Dropdown --%>
        <div class="controls-container">
            <div class="form-item-container employee-select">
                <form id="employeeSelectForm" action="punches.jsp" method="GET">
                    <label for="employeesDropDown">Select Employee:</label>
                    <select id="employeesDropDown" name="eid" autofocus="autofocus" onchange="this.form.submit()"> <%-- Changed name to 'eid' --%>
                        <option value="">Select Employee...</option>
                        <% for (String employee : employeeList) {
                            String empId = ""; String empName = employee; // Default
                            try { String[] parts = employee.split(" - ", 2); empId = parts[0]; empName = parts[1];} catch (Exception splitEx) {}
                            // Check if current empId matches the selected eid for the page
                            String selectedAttr = (eid > 0 && empId.equals(String.valueOf(eid))) ? "selected" : "";
                            %>
                            <option value="<%= empId %>" <%= selectedAttr %>><%= empId %> - <%= empName %></option>
                        <% } %>
                    </select>
                     <%-- Add a submit button if JS is disabled, or keep onchange --%>
                    <%-- <noscript><button type="submit">View</button></noscript> --%>
                </form>
            </div>
        </div>


        <%-- Punch Data Table --%>
        <div class="table-container">
            <table class="punches" id="tblPunches">
                <thead>
                    <tr>
                        <th>Date</th><th>IN Punch</th><th>OUT Punch</th>
                        <th style="text-align: right;">Total Hours</th><th>Punch Type</th>
                    </tr>
                </thead>
                <tbody id="punchTableBody">
                    <% if (eid > 0 && pageError == null) { // Only display if valid EID and no page load error
                           try {
                               // Use ShowPunches method to get HTML rows
                               out.print(ShowPunches.getPunchTableRows(eid, userTimeZoneId));
                           }
                           catch (Exception tableEx) {
                               System.err.println("Error generating punch table rows for EID " + eid + ": " + tableEx.getMessage());
                               tableEx.printStackTrace(); // Log full error
                               %> <tr><td colspan="5" class="report-error-row">Error displaying punch data.</td></tr> <%
                           }
                       } else if (eid <= 0 && pageError == null) { // No employee selected, no error
                           %>
                           <tr><td colspan="5" style="text-align: center; font-style: italic; padding: 20px;">Select an employee to view punches.</td></tr>
                           <%
                       } else { // Error occurred during page load (EID validation or employee list)
                           %>
                           <tr><td colspan="5" class="report-error-row"><%= pageError != null ? pageError : "Could not load punch data." %></td></tr>
                           <%
                       } %>
                </tbody>
            </table>
        </div>

        <%-- Modals (Add Hours, Edit Punch, Notification) --%>

        <%-- Add Hours Modal --%>
        <div id="addHoursModal" class="modal">
             <div class="modal-content">
                 <span class="close-button" id="closeAddModal">&times;</span>
                 <h2>Add Non-Clocked Hours</h2><br>
                 <form id="addHoursForm" action="AddEditAndDeletePunchesServlet" method="post">
                     <input type="hidden" id="addHoursEmployeeIdField" name="addHoursEmployeeId" value="">
                     <input type="hidden" name="action" value="addHours">
                     <div class="form-row">
                         <label for="addHoursDate">Date:</label>
                         <input type="date" id="addHoursDate" name="addHoursDate" required>
                     </div>
                     <div class="form-row">
                         <label for="addHoursTotal">Hours:</label>
                         <input type="number" id="addHoursTotal" name="addHoursTotal" step="0.01" min="0.01" max="24" required>
                     </div>
                     <div class="form-row">
                         <label for="addHoursPunchTypeDropDown">Punch Type:</label>
                         <select id="addHoursPunchTypeDropDown" name="addHoursPunchTypeDropDown" required>
                             <option value="Supervisor Override">Supervisor Override</option>
                             <option value="Vacation Time">Vacation Time</option>
                             <option value="Sick Time">Sick Time</option>
                             <option value="Personal Time">Personal Time</option>
                             <option value="Holiday Time">Holiday Time</option>
                             <option value="Bereavement">Bereavement</option>
                             <option value="Other">Other</option>
                         </select>
                     </div>
                     <div class="modal-footer">
                         <button type="submit">Submit</button>
                         <button type="button" id="cancelAddHours" class="cancel-btn">Cancel</button>
                     </div>
                 </form>
             </div>
        </div>

        <%-- Edit Row Modal --%>
        <div id="editPunchModal" class="modal">
             <div class="modal-content">
                 <span class="close-button" id="closeEditModal">&times;</span>
                 <h2>Edit Punch Record</h2><br>
                 <form id="editPunchForm" action="AddEditAndDeletePunchesServlet" method="post">
                     <input type="hidden" id="editPunchIdField" name="editPunchId"> <%-- For punch ID --%>
                     <input type="hidden" name="action" value="editPunch"> <%-- Action for servlet --%>
                     <%-- ** CORRECTED: Added id="editEmployeeId" ** --%>
                     <input type="hidden" id="editEmployeeId" name="editEmployeeId" value="<%= eid > 0 ? eid : "" %>"> <%-- For employee ID --%>

                     <div class="form-row">
                         <label for="editDate">Date:</label>
                         <input type="date" id="editDate" name="editDate" required>
                     </div>
                     <div class="form-row">
                         <label for="editInTime">IN Time (HH:MM:SS):</label>
                         <input type="time" id="editInTime" name="editInTime" step="1"> <%-- step=1 for seconds --%>
                         <small>Leave blank if not applicable.</small>
                     </div>
                     <div class="form-row">
                         <label for="editOutTime">OUT Time (HH:MM:SS):</label>
                         <input type="time" id="editOutTime" name="editOutTime" step="1"> <%-- step=1 for seconds --%>
                         <small>Leave blank if not applicable.</small>
                     </div>
                     <div class="form-row">
                         <label for="editPunchType">Punch Type:</label>
                         <select id="editPunchType" name="editPunchType" required>
                             <option value="User Initiated">User Initiated</option>
                             <option value="Supervisor Override">Supervisor Override</option>
                             <option value="Vacation Time">Vacation Time</option>
                             <option value="Sick Time">Sick Time</option>
                             <option value="Personal Time">Personal Time</option>
                             <option value="Holiday Time">Holiday Time</option>
                             <option value="Bereavement">Bereavement</option>
                             <option value="Other">Other</option>
                         </select>
                     </div>
                     <div class="modal-footer">
                         <button type="submit">Save Changes</button>
                         <button type="button" id="cancelEditPunch" class="cancel-btn">Cancel</button>
                     </div>
                 </form>
             </div>
        </div>

        <%-- Notification Modal (used by JavaScript for AJAX responses) --%>
        <div id="notificationModal" class="modal">
             <div class="modal-content">
                 <span class="close-button" id="closeNotificationModal">&times;</span>
                 <h2>Notification</h2>
                 <p id="notificationMessage"></p> <%-- Message set by JS --%>
                 <div class="modal-footer" style="justify-content: center;">
                     <button type="button" id="okButton" class="modal-ok-button">OK</button>
                 </div>
             </div>
        </div>

        <%-- Action Buttons (Add, Edit, Delete) --%>
        <div class="buttons action-buttons">
           <%-- Buttons are initially disabled, enabled by JS based on selection --%>
           <button type="button" id="btnAddHours" <%= eid <= 0 ? "disabled" : "" %> >Add Hours</button> <%-- Enable if EID selected --%>
           <button type="button" id="btnEditRow" disabled>Edit Row</button>
           <button type="button" id="btnDeleteRow" disabled>Delete Row</button>
        </div>

    </div> <%-- End parent-container --%>

    <%-- Link JavaScript file --%>
    <script type="text/javascript" src="js/punches.js?v=12"></script> <%-- Increment version if JS changed --%>
</body>
</html>