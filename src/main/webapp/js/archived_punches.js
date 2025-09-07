// js/archived_punches.js - v5 (Manual submit via Apply button)

document.addEventListener('DOMContentLoaded', function() {
    console.log("archived_punches.js v5 loaded: Manual submit.");

    const archivedTable = document.getElementById('archivedPunchesTable');
    const printArchivedBtn = document.getElementById('printArchivedPunchesBtn');

    // [REMOVED] All auto-submit event listeners are gone.
    // The new "Apply" button handles submission via standard HTML form behavior.

    // Table Sorting (using makeTableSortable from commonUtils.js)
    if (archivedTable && typeof makeTableSortable === 'function') {
        const headers = Array.from(archivedTable.querySelectorAll('thead th'));
        let initialSortColIndex = -1;

        headers.forEach((th, idx) => {
            if (th.textContent.trim().toUpperCase() === "DATE") {
                 initialSortColIndex = idx;
            }
        });

        makeTableSortable(archivedTable, { columnIndex: initialSortColIndex, ascending: true });
        console.log("Archived punches table sorting initialized.");
    }

    // Print Button
    if (printArchivedBtn) {
        printArchivedBtn.addEventListener('click', function() {
            const tableToPrint = document.getElementById('archivedPunchesTable');
            const reportTitleText = document.querySelector('.report-display-area > h2.report-title')?.textContent || "Archived Punches Report";
            const mainPageTitle = document.querySelector('.parent-container.reports-container > h1');

            if (!tableToPrint || tableToPrint.querySelector('.report-message-row, .report-error-row')) {
                if(typeof showPageNotification === 'function') showPageNotification("No data available to print.", true);
                else alert("No data available to print.");
                return;
            }
            
            let reportsCSSHref = "css/reports.css";
            const reportsCSSLinkElem = document.querySelector('link[href^="css/reports.css"]');
            if (reportsCSSLinkElem) reportsCSSHref = reportsCSSLinkElem.getAttribute('href');

            const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
            if (!printWindow) {
                if(typeof showPageNotification === 'function') showPageNotification("Could not open print window. Check popup blockers.", true);
                return;
            }
            
            printWindow.document.write(`<html><head><title>Print - ${reportTitleText}</title><link rel="stylesheet" href="${reportsCSSHref}"><style>body{margin:20px;background-color:#fff!important;font-size:9pt;}.main-navbar,.archive-controls-form,.report-actions,.modal,script,.parent-container > h1{display:none!important;}.parent-container.reports-container,.report-display-area{width:100%!important;max-width:none!important;margin:0!important;padding:0!important;box-shadow:none;border:none;}h2.report-title{font-size:14pt;text-align:center;margin-bottom:15px;color:#000;}.report-table-container{border:1px solid #999!important;max-height:none!important;overflow:visible!important;}.report-table{width:100%!important;border-collapse:collapse!important;font-size:8pt;}.report-table th,.report-table td{border:1px solid #bbb!important;padding:4px 6px!important;color:#000!important;background-color:#fff!important;white-space:normal!important;word-break:break-word;}.report-table thead th{background-color:#e9ecef!important;font-weight:bold;text-align:center!important;}@media print{@page{size:landscape;margin:0.5in;}body{-webkit-print-color-adjust:exact;print-color-adjust:exact;}}</style></head><body>`);
            if (mainPageTitle) printWindow.document.write(`<h1 style="text-align:center;font-size:18pt;margin-bottom:5px;">${mainPageTitle.textContent}</h1>`);
            printWindow.document.write(`<h2 class="report-title">${reportTitleText}</h2>`);
            const tableContainerToPrint = tableToPrint.closest('.report-table-container');
            if (tableContainerToPrint) printWindow.document.write(tableContainerToPrint.outerHTML); else printWindow.document.write(tableToPrint.outerHTML);
            printWindow.document.write(`</body></html>`);
            printWindow.document.close();
            setTimeout(() => { try { printWindow.focus(); printWindow.print(); } catch (e) { console.error("Print failed:", e); } }, 750);
        });
    }

    // Clear URL parameters
    if (typeof clearUrlParams === 'function') {
        clearUrlParams(['message', 'error', 'employeesDropDown', 'startDate', 'endDate', 'search']);
    }
});