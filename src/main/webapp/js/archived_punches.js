// js/archived_punches.js - v4 (Auto-submit, notification modal refs removed)

document.addEventListener('DOMContentLoaded', function() {
    console.log("archived_punches.js v4 loaded: Auto-submit, no direct notification modal refs.");

    const archiveViewForm = document.getElementById('archiveViewForm');
    const employeeDropdown = document.getElementById('employeesDropDown');
    const startDateInput = document.getElementById('startDate');
    const endDateInput = document.getElementById('endDate');

    const archivedTable = document.getElementById('archivedPunchesTable');
    const printArchivedBtn = document.getElementById('printArchivedPunchesBtn');

    // Function to submit the filter form
    function submitArchiveFilters() {
        if (archiveViewForm) {
            console.log("Submitting archive filters via JavaScript...");
            // Optionally, add a visual loading indicator before submission
            const loadButtonPlaceholder = archiveViewForm.querySelector('.form-item-container.button-item');
            if (loadButtonPlaceholder && loadButtonPlaceholder.firstElementChild && loadButtonPlaceholder.firstElementChild.tagName === 'BUTTON') {
                // If a button was there and is now hidden, can use its space or just submit.
            }
            archiveViewForm.submit();
        } else {
            console.error("#archiveViewForm not found for submission.");
        }
    }

    // Add event listeners to filter controls to auto-submit
    if (employeeDropdown) {
        employeeDropdown.addEventListener('change', submitArchiveFilters);
    } else { console.warn("#employeesDropDown not found."); }

    if (startDateInput) {
        startDateInput.addEventListener('change', submitArchiveFilters);
    } else { console.warn("#startDate input not found."); }

    if (endDateInput) {
        endDateInput.addEventListener('change', submitArchiveFilters);
    } else { console.warn("#endDate input not found."); }


    // Table Sorting (using makeTableSortable from commonUtils.js)
    if (archivedTable && typeof makeTableSortable === 'function') {
        let initialSortColIndex = -1; // Default to no specific sort
        const headers = Array.from(archivedTable.querySelectorAll('thead th'));

        // Determine the index of the "Date" column for default sorting
        // This needs to account for the conditional EID and Employee Name columns
        let dateColumnActualIndex = 2; // Default if EID/Name are not present
        if (headers.length > 5 && headers[0].textContent.trim().toUpperCase() === "EMP ID" && headers[1].textContent.trim().toUpperCase() === "EMPLOYEE NAME") {
            dateColumnActualIndex = 2; // Date is the 3rd column (index 2)
        } else if (headers.length > 0 && headers[0].textContent.trim().toUpperCase() === "DATE") {
            dateColumnActualIndex = 0; // Date is the 1st column
        }
        // Find the "Date" th element to pass to makeTableSortable's initial state
        headers.forEach((th, idx) => {
            if (th.textContent.trim().toUpperCase() === "DATE") {
                 initialSortColIndex = idx;
            }
            // data-sort-type attributes are set in the JSP
        });


        makeTableSortable(archivedTable, { columnIndex: initialSortColIndex, ascending: true }); // Default sort by determined Date column
        console.log("Archived punches table sorting initialized. Default sort on column index: " + initialSortColIndex);
    } else {
        if(!archivedTable) console.warn("Table #archivedPunchesTable not found for sorting.");
        if(typeof makeTableSortable !== 'function') console.warn("makeTableSortable from commonUtils.js not found. Sorting disabled.");
    }

    // Print Button
    if (printArchivedBtn) {
        printArchivedBtn.addEventListener('click', function() {
            console.log("Print Archived Punches button clicked.");
            const tableToPrint = document.getElementById('archivedPunchesTable');
            const reportTitleTextElement = document.querySelector('.report-display-area > h2.report-title');
            const reportTitleText = reportTitleTextElement ? reportTitleTextElement.textContent : "Archived Punches Report";
            const mainPageTitle = document.querySelector('.parent-container.reports-container > h1');

            if (!tableToPrint) {
                if(typeof showPageNotification === 'function') showPageNotification("No data table found to print.", true);
                else alert("No data table found to print.");
                return;
            }
            let reportsCSSHref = "css/reports.css";
            const reportsCSSLinkElem = document.querySelector('link[href^="css/reports.css"]');
            if (reportsCSSLinkElem) reportsCSSHref = reportsCSSLinkElem.getAttribute('href');

            const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
            if (!printWindow) {
                if(typeof showPageNotification === 'function') showPageNotification("Could not open print window. Check popup blockers.", true);
                else alert("Could not open print window. Check popup blockers.");
                return;
            }
            printWindow.document.write(`<html><head><title>Print - ${reportTitleText}</title><link rel="stylesheet" href="${reportsCSSHref}"><style>body{margin:20px;background-color:#fff!important;font-size:9pt;}.main-navbar,.archive-controls-form,.report-actions,.modal,script,.parent-container > h1{display:none!important;}.parent-container.reports-container,.report-display-area{width:100%!important;max-width:none!important;margin:0!important;padding:0!important;box-shadow:none;border:none;}h2.report-title{font-size:14pt;text-align:center;margin-bottom:15px;color:#000;}.report-table-container{border:1px solid #999!important;max-height:none!important;overflow:visible!important;}.report-table{width:100%!important;border-collapse:collapse!important;font-size:8pt;}.report-table th,.report-table td{border:1px solid #bbb!important;padding:4px 6px!important;color:#000!important;background-color:#fff!important;white-space:normal!important;word-break:break-word;}.report-table thead th{background-color:#e9ecef!important;font-weight:bold;text-align:center!important;}@media print{@page{size:landscape;margin:0.5in;}body{-webkit-print-color-adjust:exact;print-color-adjust:exact;}}</style></head><body>`);
            if (mainPageTitle) printWindow.document.write(`<h1 style="text-align:center;font-size:18pt;margin-bottom:5px;">${mainPageTitle.textContent}</h1>`);
            printWindow.document.write(`<h2 class="report-title">${reportTitleText}</h2>`);
            const tableContainerToPrint = tableToPrint.closest('.report-table-container');
            if (tableContainerToPrint) printWindow.document.write(tableContainerToPrint.outerHTML); else printWindow.document.write(tableToPrint.outerHTML);
            printWindow.document.write(`</body></html>`);
            printWindow.document.close();
            setTimeout(() => { try { printWindow.focus(); printWindow.print(); } catch (e) { console.error("Print failed:", e); if(typeof showPageNotification==='function')showPageNotification("Printing failed.", true); else alert("Printing failed."); try{printWindow.close();}catch(e2){} } }, 750);
        });
    } else {
        console.warn("#printArchivedPunchesBtn not found.");
    }

    // Clear URL parameters if they were from messages (like from payroll_history.jsp)
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('message') || urlParams.has('error') || urlParams.has('startDate')) { // Clear all relevant params
        if (typeof clearUrlParams === 'function') {
            clearUrlParams(['message', 'error', 'employeesDropDown', 'startDate', 'endDate']);
        }
    }
});