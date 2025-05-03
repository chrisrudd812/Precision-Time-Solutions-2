<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="timeclock.reports.ShowReports" %>
<%@ page import="timeclock.db.DatabaseConnection" %>
<%@ page import="java.sql.*" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.time.LocalDate"%>
<%@ page import="java.time.format.DateTimeFormatter"%>
<%@ page import="java.time.format.DateTimeParseException"%> <%-- Added import --%>
<%@ page import="java.time.ZoneId" %> <%-- Added import --%>

<%
    // --- Parameter Reading ---
    String eidStr = request.getParameter("employeesDropDown");
    String startDateStr = request.getParameter("startDate");
    String endDateStr = request.getParameter("endDate");

    int eid = 0;
    LocalDate startDate = null;
    LocalDate endDate = null;
    String paramError = null; // Specific errors from parameter parsing

    // --- Get User Time Zone ---
    String userTimeZoneId = (String) session.getAttribute("userTimeZoneId");
    if (userTimeZoneId == null || userTimeZoneId.trim().isEmpty()) {
        userTimeZoneId = "America/Denver"; // Default
    }

    // --- Parse Employee ID ---
    if (eidStr != null && !eidStr.trim().isEmpty()) {
        try {
            eid = Integer.parseInt(eidStr.trim());
            if (eid <= 0) {
                paramError = "Invalid Employee ID selected.";
                eid = 0; // Reset eid if invalid
            }
        } catch (NumberFormatException e) {
            paramError = "Employee ID is not a valid number.";
            eid = 0; // Reset eid on format error
        }
    }

    // --- Parse Dates (only if EID is potentially valid) ---
    // Set defaults first
    LocalDate defaultEndDate = LocalDate.now();
    LocalDate defaultStartDate = defaultEndDate.minusMonths(1); // Default to last month

    try {
        if (startDateStr != null && !startDateStr.isEmpty()) {
            startDate = LocalDate.parse(startDateStr);
        } else {
            startDate = defaultStartDate; // Use default if parameter missing/empty
        }
        if (endDateStr != null && !endDateStr.isEmpty()) {
            endDate = LocalDate.parse(endDateStr);
        } else {
            endDate = defaultEndDate; // Use default if parameter missing/empty
        }
        // Validate range logic
        if (startDate.isAfter(endDate)) {
            paramError = (paramError == null ? "" : paramError + " "); // Append error
            paramError += "Start date cannot be after end date.";
            // Reset to defaults on error
            startDate = defaultStartDate;
            endDate = defaultEndDate;
        }
    } catch (DateTimeParseException e) {
        paramError = (paramError == null ? "" : paramError + " "); // Append error
        paramError += "Invalid date format. Please use YYYY-MM-DD.";
        // Reset to defaults on error
        startDate = defaultStartDate;
        endDate = defaultEndDate;
    }

    // --- Fetch Employee List ---
    ArrayList<String> employeeList = new ArrayList<>();
    try (Connection con = DatabaseConnection.getConnection();
         PreparedStatement psGetEmployees = con.prepareStatement("SELECT EID, LAST_NAME, FIRST_NAME FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME");
         ResultSet rsEmp = psGetEmployees.executeQuery()) {
        while (rsEmp.next()) {
            employeeList.add(rsEmp.getInt("EID") + " - " + rsEmp.getString("LAST_NAME") + ", " + rsEmp.getString("FIRST_NAME"));
        }
    } catch (Exception e) {
        paramError = (paramError == null ? "" : paramError + " ");
        paramError += "Error loading employee list.";
        System.err.println("Error loading employee list: " + e.getMessage());
    }


    // --- Fetch Archived Data ---
    String tableRowsHtml = "";
    // Determine initial message or fetch data
    if (eid <= 0 && paramError == null) { // No employee selected yet, no other errors
         tableRowsHtml = "<tr><td colspan='5' class='report-message-row'>Select an employee and date range, then click 'Load Data'.</td></tr>";
    } else if (paramError != null) { // Parameter parsing error occurred
         tableRowsHtml = "<tr><td colspan='5' class='report-error-row'>" + paramError + "</td></tr>";
    } else { // Valid EID and Dates parsed (or defaulted correctly), attempt fetch
        try {
            tableRowsHtml = ShowReports.showArchivedPunchesReport(eid, userTimeZoneId, startDate, endDate);
        } catch (Exception e) {
            tableRowsHtml = "<tr><td colspan='5' class='report-error-row'>An unexpected error occurred while fetching archived data.</td></tr>";
            System.err.println("Error calling showArchivedPunchesReport: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Format dates for input field values, ensuring they are never null
    String startDateValue = (startDate != null) ? startDate.toString() : defaultStartDate.toString();
    String endDateValue = (endDate != null) ? endDate.toString() : defaultEndDate.toString();
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Archived Punches</title>
    <link rel="stylesheet" href="css/navbar.css">
    <%-- Reusing reports CSS for table styling --%>
    <link rel="stylesheet" href="css/reports.css?v=3"> <%-- Use latest reports CSS --%>
    <style>
        /* Specific styles for this page's controls */
        .archive-controls {
            display: flex;
            flex-wrap: wrap;
            gap: 15px 20px; /* Gap between items */
            align-items: flex-end; /* Align bottoms of labels/inputs/button */
            margin-bottom: 25px;
            padding: 20px;
            background-color: #f8f9fa;
            border: 1px solid #dee2e6;
            border-radius: 5px;
        }
        .archive-controls .form-item-container {
             /* flex: 1 1 auto; /* Allow items to grow and shrink */
             min-width: 150px; /* Minimum width for items */
        }
        .archive-controls .form-item-container label {
            margin-bottom: 5px;
            font-weight: bold;
            display: block;
            font-size: 0.9em;
            color: #495057;
        }
        .archive-controls .form-item-container select,
        .archive-controls .form-item-container input[type="date"] {
            padding: 8px 10px;
            border-radius: 4px;
            border: 1px solid #ced4da; /* Bootstrap-like border color */
            width: 100%; /* Make inputs fill container */
            box-sizing: border-box; /* Include padding in width */
            height: 38px; /* Consistent height */
        }
         .archive-controls .form-item-container select {
             min-width: 200px; /* Give dropdown more space */
         }
        .archive-controls .load-button { /* Style for the button */
            padding: 8px 20px;
            height: 38px; /* Match input height */
            /* Using report button style by adding class="report-trigger-button" */
            margin-left: 10px; /* Space before button */
        }
    </style>
</head>
<body class="reports-page"> <%-- Reusing class for similar background/layout --%>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reports-container">
        <h1>View Archived Punches</h1>

        <%-- Display Parameter Errors Here (Optional) --%>
        <% if (paramError != null) { %>
            <div id="notification-bar" class="error-message" style="display:block; max-width: 90%; margin: 0 auto 20px auto;"><%= paramError %></div>
        <% } %>

        <%-- Controls Section Form --%>
        <form id="archiveViewForm" action="archived_punches.jsp" method="GET">
             <div class="archive-controls">
                 <%-- Employee Selection --%>
                 <div class="form-item-container employee-select">
                     <label for="employeesDropDown">Employee:</label>
                     <select id="employeesDropDown" name="employeesDropDown" required>
                         <option value="">Select Employee...</option>
                         <% for (String employee : employeeList) {
                             String empIdForm = ""; String empName = employee;
                             try { String[] parts = employee.split(" - ", 2); empIdForm = parts[0]; empName = parts[1];} catch (Exception splitEx) {}
                             // Re-select based on the successfully parsed 'eid' variable
                             String selectedAttr = (eid > 0 && empIdForm.equals(String.valueOf(eid))) ? "selected" : ""; %>
                             <option value="<%= empIdForm %>" <%= selectedAttr %>><%= empIdForm %> - <%= empName %></option>
                         <% } %>
                     </select>
                 </div>
                 <%-- Date Range Selection --%>
                 <div class="form-item-container date-range-item">
                     <label for="startDate">Start Date:</label>
                     <input type="date" id="startDate" name="startDate" value="<%= startDateValue %>" required>
                 </div>
                  <div class="form-item-container date-range-item">
                      <label for="endDate">End Date:</label>
                      <input type="date" id="endDate" name="endDate" value="<%= endDateValue %>" required>
                  </div>
                  <%-- Added report-trigger-button class for styling reuse --%>
                  <button type="submit" id="loadArchived" class="load-button report-trigger-button">Load Data</button>
             </div>
         </form>

        <%-- Results Section --%>
        <div class="report-display-area">
             <h2 class="report-title">Archived Punches Results</h2>
             <div id="reportOutput" class="report-output">
                <div class="table-container report-table-container">
                    <%-- Reusing report-table for consistent styling --%>
                    <table class="report-table archive-table">
                        <thead>
                            <tr>
                                <th>Date</th>
                                <th>IN Punch</th>
                                <th>OUT Punch</th>
                                <th>Total Hours</th>
                                <th>Punch Type</th>
                            </tr>
                        </thead>
                        <tbody>
                            <%-- Output the generated rows --%>
                            <%= tableRowsHtml %>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

    </div> <%-- End parent-container --%>

    <%-- Optional JS for date validation or other enhancements --%>
    <%-- <script type="text/javascript" src="js/archived_punches.js?v=1"></script> --%>
</body>
</html>