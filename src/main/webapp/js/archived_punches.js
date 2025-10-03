// js/archived_punches.js

document.addEventListener('DOMContentLoaded', function() {
    
    const printArchivedBtn = document.getElementById('printArchivedPunchesBtn');

    // MODIFIED: Removed local table sorting logic.
    // The global initializeAllSortableTables() in commonUtils.js will handle this table now.

    // Print Button
    if (printArchivedBtn) {
        printArchivedBtn.addEventListener('click', function() {
            const tableToPrint = document.getElementById('archivedPunchesTable');
            const reportTitleText = document.querySelector('.content-display-area > h2.report-title')?.textContent || "Archived Punches Report";
            const mainPageTitle = document.querySelector('.parent-container > h1');

            if (!tableToPrint || tableToPrint.querySelector('.report-message-row, .report-error-row')) {
                if(typeof window.showPageNotification === 'function') window.showPageNotification("No data available to print.", 'error');
                else alert("No data available to print.");
                return;
            }
            
            let commonCSSHref = "css/common.css";
            const commonCSSLinkElem = document.querySelector('link[href^="css/common.css"]');
            if (commonCSSLinkElem) commonCSSHref = commonCSSLinkElem.getAttribute('href');

            const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
            if (!printWindow) {
                if(typeof window.showPageNotification === 'function') window.showPageNotification("Could not open print window. Check popup blockers.", 'error');
                return;
            }
            
            printWindow.document.write(`<html><head><title>Print - ${reportTitleText}</title><link rel="stylesheet" href="${commonCSSHref}"><style>body{margin:20px;background-color:#fff!important;font-size:9pt;}.main-navbar,.archive-controls-form,.report-actions,.modal,script,.parent-container > h1{display:none!important;}.parent-container,.content-display-area{width:100%!important;max-width:none!important;margin:0!important;padding:0!important;box-shadow:none;border:none;background:none!important;}h2.report-title{font-size:14pt;text-align:center;margin-bottom:15px;color:#000;}.table-container{border:1px solid #999!important;max-height:none!important;overflow:visible!important;}.report-table{width:100%!important;border-collapse:collapse!important;font-size:8pt;}.report-table th,.report-table td{border:1px solid #bbb!important;padding:4px 6px!important;color:#000!important;background-color:#fff!important;white-space:normal!important;word-break:break-word;}.report-table thead th{background-color:#e9ecef!important;font-weight:bold;text-align:center!important;}@media print{@page{size:landscape;margin:0.5in;}body{-webkit-print-color-adjust:exact;print-color-adjust:exact;}}</style></head><body>`);
            if (mainPageTitle) printWindow.document.write(`<h1 style="text-align:center;font-size:18pt;margin-bottom:5px;">${mainPageTitle.textContent}</h1>`);
            printWindow.document.write(`<h2 class="report-title">${reportTitleText}</h2>`);
            const tableContainerToPrint = tableToPrint.closest('.table-container');
            if (tableContainerToPrint) printWindow.document.write(tableContainerToPrint.outerHTML); else printWindow.document.write(tableToPrint.outerHTML);
            printWindow.document.write(`</body></html>`);
            printWindow.document.close();
            setTimeout(() => { try { printWindow.focus(); printWindow.print(); } catch (e) { console.error("Print failed:", e); } }, 750);
        });
    }

    // Focus the Apply button on redirect from payroll history
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('startDate') && urlParams.has('endDate') && !urlParams.has('search')) {
        const applyBtn = document.getElementById('applyFiltersBtn');
        if (applyBtn) {
            applyBtn.focus();
        }
    }
});