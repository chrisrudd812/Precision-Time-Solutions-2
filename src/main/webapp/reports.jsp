<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%
    // Check if a report was specified in the URL immediately
    String initialReportType = request.getParameter("report");
    boolean reportSelected = (initialReportType != null && !initialReportType.trim().isEmpty());
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <title>Reports</title>
    <link rel="stylesheet" href="css/navbar.css">
    <link rel="stylesheet" href="css/reports.css?v=3"> <%-- Updated CSS version --%>
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container reports-container">
        <h1>Reports</h1>

        <%-- Removed the .report-controls div and buttons --%>

        <%-- Area where the selected report content will be displayed --%>
        <div class="report-display-area">
            <%-- Title and Description updated dynamically by JS --%>
            <h2 id="reportTitle" class="report-title">
                <%= reportSelected ? "Loading Report..." : "Select Report" %>
            </h2>
            <p id="reportDescription" class="report-description">
                 <%= reportSelected ? "" : "Please choose a report from the 'Reports' menu in the navigation bar." %>
            </p>

            <%-- Loading Indicator (Initially Hidden unless report selected) --%>
            <div id="loadingIndicator" class="loading-indicator" style="<%= reportSelected ? "display: flex;" : "display: none;" %>">
                <div class="spinner"></div>
                Loading report...
            </div>

            <%-- Report Output Container --%>
            <div id="reportOutput" class="report-output">
                <%-- Report table/message will be injected here by JavaScript --%>
                <% if (!reportSelected) { %>
                    <%-- Initial placeholder if no report selected via URL --%>
                     <p class="report-placeholder">Report results will appear here once selected from the menu.</p>
                <% } %>
            </div>
        </div>

    </div> <%-- End parent-container --%>

    <%-- Link the JavaScript file --%>
    <script type="text/javascript" src="js/reports.js?v=2"></script> <%-- Updated JS version --%>
</body>
</html>