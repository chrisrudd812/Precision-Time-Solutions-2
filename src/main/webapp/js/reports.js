/**
 * reports.js
 * Handles fetching and displaying reports dynamically on reports.jsp
 * based ONLY on the 'report' URL parameter.
 */
document.addEventListener('DOMContentLoaded', function() {
    console.log("Reports JS Loaded - URL Parameter Mode");

    // Get references to elements
    const reportOutputDiv = document.getElementById('reportOutput');
    const loadingIndicator = document.getElementById('loadingIndicator');
    const reportTitle = document.getElementById('reportTitle');
    const reportDescription = document.getElementById('reportDescription');

    // --- Report Descriptions ---
    const reportDescriptions = {
        exception: "Shows punch records where the calculated total hours is zero, potentially indicating missed punches.",
        tardy: "Summarizes employees marked as tardy or leaving early based on punch data.",
        whosin: "Lists employees who are currently clocked IN based on the latest punch data for today.",
        // Add descriptions for other reports
    };

    // --- Table Header Definitions ---
    const reportHeaders = {
         exception: `<thead><tr><th>EID</th><th>First Name</th><th>Last Name</th><th>Date</th><th>IN Punch</th><th>OUT Punch</th></tr></thead>`,
         tardy: `<thead><tr><th>EID</th><th>First Name</th><th>Last Name</th><th>Late Count</th><th>Early Out Count</th></tr></thead>`,
         whosin: `<thead><tr><th>EID</th><th>First Name</th><th>Last Name</th><th>Department</th><th>Schedule</th></tr></thead>`,
        // Add headers for other reports
    };

    // --- Function to fetch and display report ---
    function loadReport(reportType) {
        if (!reportType) {
            console.error("loadReport called without a report type.");
            // Display message if needed, although initial JSP handles this now
            if (reportOutputDiv) reportOutputDiv.innerHTML = '<p class="report-placeholder">Invalid report type specified.</p>';
            if (loadingIndicator) loadingIndicator.style.display = 'none';
             if (reportTitle) reportTitle.textContent = "Error";
             if (reportDescription) reportDescription.textContent = "Could not load report due to missing type.";
            return;
        }
        console.log("Loading report:", reportType);

        // Ensure loading indicator is visible (might already be from JSP)
        if (loadingIndicator) loadingIndicator.style.display = 'flex';
        // Update Title and Description
        if(reportTitle) reportTitle.textContent = `${reportType.charAt(0).toUpperCase() + reportType.slice(1)} Report`;
        if(reportDescription) reportDescription.textContent = reportDescriptions[reportType] || 'Report results shown below.'; // Set description

        // Clear previous output *before* fetch starts
        if (reportOutputDiv) reportOutputDiv.innerHTML = '';

        const params = new URLSearchParams();
        params.append('reportType', reportType);

        fetch('ReportServlet', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded', },
            body: params
        })
        .then(response => {
            if (!response.ok) {
                return response.text().then(text => { throw new Error(`Server Error: ${response.status} - ${text || response.statusText}`); });
            }
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) {
                return response.json();
            } else {
                throw new Error("Received non-JSON response from server.");
            }
        })
        .then(data => {
            console.log("Received data:", data);
            if (loadingIndicator) loadingIndicator.style.display = 'none';

            if (data.success) {
                if (data.html) {
                    const tableHeaders = reportHeaders[reportType] || `<thead><tr><th>Report Data</th></tr></thead>`;
                    reportOutputDiv.innerHTML = `
                        <div class="table-container report-table-container">
                            <table class="report-table ${reportType}-report-table">
                                ${tableHeaders}
                                <tbody>
                                    ${data.html}
                                </tbody>
                            </table>
                        </div>`;
                } else if (data.message) {
                    reportOutputDiv.innerHTML = `<p class="report-message-row">${data.message}</p>`;
                } else {
                     reportOutputDiv.innerHTML = `<p class="report-message-row">Report generated successfully, but no data to display.</p>`;
                }
            } else {
                reportOutputDiv.innerHTML = `<p class="report-error-row">Error: ${data.message || 'Unknown error from server.'}</p>`;
            }
        })
        .catch(error => {
            console.error('Error fetching report:', error);
            if (loadingIndicator) loadingIndicator.style.display = 'none';
            // Update title/description on error too
            if(reportTitle) reportTitle.textContent = "Report Error";
            if(reportDescription) reportDescription.textContent = "Could not load the requested report.";
            reportOutputDiv.innerHTML = `<p class="report-error-row">Failed to load report. Network or server error: ${error.message}</p>`;
        });
    }

    // --- Initialize page based on URL parameter ---
    const urlParams = new URLSearchParams(window.location.search);
    const initialReport = urlParams.get('report');

    if (initialReport && reportHeaders[initialReport]) { // Check if it's a known report type
        console.log("Loading initial report from URL parameter:", initialReport);
        // Title/description/loading set by JSP, just call loadReport
        loadReport(initialReport);
    } else {
         console.log("No valid report type found in URL parameter.");
         // The JSP already displays a "Select Report" message and hides loading.
         // No further action needed here unless you want to display a specific error.
         // reportOutputDiv.innerHTML = '<p class="report-placeholder">Please select a valid report from the menu.</p>'; // Optional alternative message
    }

}); // End DOMContentLoaded