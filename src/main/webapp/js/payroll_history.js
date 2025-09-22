// js/payroll_history.js - v3 (Sorting is now handled by commonUtils.js)

document.addEventListener('DOMContentLoaded', function() {
    console.log("payroll_history.js v3 loaded");

    const tableBody = document.getElementById('historyTableBody');
    const btnViewDetails = document.getElementById('btnViewDetails');
    const btnPrintHistory = document.getElementById('btnPrintHistory');

    let selectedRow = null;
    let selectedStartDate = null;
    let selectedEndDate = null;

    // Initialize Notification Modal Elements
    const notificationModal = document.getElementById("notificationModal");
    const notificationCloseButton = document.getElementById("closeNotificationModal");
    const notificationOkButton = document.getElementById("okButton");

    if (notificationModal) {
        if (notificationCloseButton) notificationCloseButton.addEventListener("click", function() { hideModal(notificationModal); });
        if (notificationOkButton) notificationOkButton.addEventListener("click", function() { hideModal(notificationModal); });
        window.addEventListener("click", function(event) { if (event.target === notificationModal) { hideModal(notificationModal); }});
    } else {
        console.warn("Payroll_history.js: #notificationModal not found.");
    }

    // --- Row Click Listener ---
    if (tableBody) {
        tableBody.addEventListener('click', function(event) {
            const clickedRow = event.target.closest('tr');
            if (!clickedRow || !clickedRow.hasAttribute('data-start-date') || !tableBody.contains(clickedRow)) return;

            if (selectedRow && selectedRow !== clickedRow) {
                selectedRow.classList.remove('selected');
            }
            clickedRow.classList.toggle('selected');

            if (clickedRow.classList.contains('selected')) {
                selectedRow = clickedRow;
                selectedStartDate = selectedRow.getAttribute('data-start-date');
                selectedEndDate = selectedRow.getAttribute('data-end-date');
                if (btnViewDetails) btnViewDetails.disabled = !(selectedStartDate && selectedEndDate);
            } else {
                selectedRow = null;
                selectedStartDate = null;
                selectedEndDate = null;
                if (btnViewDetails) btnViewDetails.disabled = true;
            }
        });
    } else { console.error('History table body (#historyTableBody) not found.'); }

    // --- View Details Button Listener ---
    if (btnViewDetails) {
        btnViewDetails.addEventListener('click', function() {
            if (!selectedRow || !selectedStartDate || !selectedEndDate) {
                if(typeof showPageNotification === 'function') showPageNotification("Please select a payroll period row first.", true);
                else alert('Please select a payroll period row first.');
                return;
            }
            const contextPath = (typeof appRootPath !== 'undefined' && appRootPath !== null) ? appRootPath : (window.location.pathname.substring(0, window.location.pathname.indexOf("/",2)) || "");
            const url = `${contextPath}/archived_punches.jsp?startDate=${encodeURIComponent(selectedStartDate)}&endDate=${encodeURIComponent(selectedEndDate)}`;
            window.location.href = url;
        });
    } else { console.error('View Details button (#btnViewDetails) not found.'); }

    // --- Print History Button Listener ---
     if (btnPrintHistory) {
         btnPrintHistory.addEventListener('click', () => {
             const tableToPrint = document.getElementById('historyTable');
             const pageTitle = document.title || "Payroll History";
             const mainHeader = document.querySelector('.parent-container.reports-container > h1');

             if (!tableToPrint) {
                 if(typeof showPageNotification === 'function') showPageNotification("Error: History table element not found.", true);
                 else alert("Error: History table element not found.");
                 return;
             }

             let reportsCSSHref = "css/reports.css";
             const reportsCSSLinkElem = document.querySelector('link[href^="css/reports.css"]');
             if (reportsCSSLinkElem) reportsCSSHref = reportsCSSLinkElem.getAttribute('href');

             let printHTML = `<!DOCTYPE html><html lang="en"><head><title>Print - ${pageTitle}</title>`;
             printHTML += `<link rel="stylesheet" href="${reportsCSSHref}">`;
             printHTML += `<style>
                             body { margin: 20px; background-color: #fff !important; font-size: 9pt; }
                             .main-navbar, #button-container, .modal, .page-message, .reports-page .parent-container.reports-container > h4 { display: none !important; }
                             .parent-container.reports-container { width: 100% !important; max-width: none !important; margin:0 !important; padding:0 !important; box-shadow: none; border: none;}
                             h1 { font-size: 16pt; text-align:center; margin-bottom:20px; color:#000; }
                             .report-table-container { border: 1px solid #999 !important; max-height: none !important; overflow: visible !important; }
                             table.report-table { width: 100% !important; border-collapse: collapse !important; font-size: 8pt; }
                             .report-table th, .report-table td { border: 1px solid #bbb !important; padding: 4px 6px !important; color: #000 !important; background-color: #fff !important; }
                             .report-table thead th { background-color: #e9ecef !important; font-weight: bold; text-align: center !important; }
                             @media print { @page { size: portrait; margin: 0.5in; } body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
                           </style></head><body>`;
             if (mainHeader) printHTML += `<h1>${mainHeader.textContent}</h1>`;

             const tableContainerClone = tableToPrint.closest('.report-table-container');
             if (tableContainerClone) {
                 printHTML += tableContainerClone.outerHTML;
             } else {
                 printHTML += tableToPrint.outerHTML;
             }
             printHTML += `</body></html>`;

             const printWindow = window.open('', '_blank', 'width=800,height=600,scrollbars=yes,resizable=yes');
             if (!printWindow) {
                 if(typeof showPageNotification === 'function') showPageNotification("Could not open print window. Please check popup blockers.", true);
                 else alert("Could not open print window. Please check your browser's popup blocker settings.");
                 return;
             }
             printWindow.document.write(printHTML);
             printWindow.document.close();
             setTimeout(() => {
                 try { printWindow.focus(); printWindow.print(); }
                 catch (e) { console.error("Printing failed:", e); if(typeof showPageNotification === 'function') showPageNotification("Printing failed.", true); else alert("Printing failed."); try{printWindow.close();}catch(e2){} }
             }, 750);
         });
     } else { console.error('Print History button (#btnPrintHistory) not found.'); }

    // Initial button state
    if (btnViewDetails) btnViewDetails.disabled = true;

});