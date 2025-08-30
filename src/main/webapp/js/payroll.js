// js/payroll.js - v37 (Final Version - Corrected Button Enabling Logic)

// --- Helper Functions ---
function makeElementDraggable(elmnt, handle) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    const dragHandle = handle || elmnt;
    if (dragHandle && typeof dragHandle.onmousedown !== 'undefined') {
        dragHandle.onmousedown = dragMouseDown;
        if(dragHandle.style) dragHandle.style.cursor = 'move';
    } else {
        console.warn("[Payroll.js] MakeElementDraggable: No valid drag handle or element for:", elmnt ? (elmnt.id || 'Unnamed Element') : "unknown element");
        return;
    }
    function dragMouseDown(e) {
        e = e || window.event;
        let currentTarget = e.target;
        while(currentTarget && currentTarget !== dragHandle) {
            if (['INPUT', 'SELECT', 'BUTTON', 'TEXTAREA', 'A'].includes(currentTarget.tagName)) return;
            currentTarget = currentTarget.parentNode;
        }
        e.preventDefault();
        pos3 = e.clientX; pos4 = e.clientY;
        document.onmouseup = closeDragElement; document.onmousemove = elementDrag;
    }
    function elementDrag(e) {
        e = e || window.event; e.preventDefault();
        pos1 = pos3 - e.clientX; pos2 = pos4 - e.clientY;
        pos3 = e.clientX; pos4 = e.clientY;
        if (elmnt && elmnt.style) {
             elmnt.style.top = (elmnt.offsetTop - pos2) + "px";
             elmnt.style.left = (elmnt.offsetLeft - pos1) + "px";
        } else {
            console.warn("[Payroll.js] makeElementDraggable: Attempted to drag an undefined element or element without style property.");
            closeDragElement();
        }
    }
    function closeDragElement() { document.onmouseup = null; document.onmousemove = null; }
}

function parseTimeTo24Hour(timeStr12hr) {
    if (!timeStr12hr || String(timeStr12hr).trim() === '' || String(timeStr12hr).toLowerCase().includes('missing') || String(timeStr12hr).toLowerCase() === "n/a") return '';
    timeStr12hr = String(timeStr12hr).trim();
    try {
        if (/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(timeStr12hr)) {
            return timeStr12hr.length === 5 ? timeStr12hr + ':00' : timeStr12hr;
        }
        const parts = timeStr12hr.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)/i);
        if (!parts) {
            console.warn("Payroll.js (parseTimeTo24Hour): Could not parse time string:", timeStr12hr);
            return '';
        }
        let h = parseInt(parts[1],10);
        const m = parts[2];
        const s = parts[3] ? parts[3] : '00';
        const ampm = parts[4] ? parts[4].toUpperCase() : null;

        if (ampm) {
            if (ampm === 'PM' && h !== 12) h += 12;
            if (ampm === 'AM' && h === 12) h = 0;
        }
        if (h > 23 || h < 0 || isNaN(h) || isNaN(parseInt(m,10)) || isNaN(parseInt(s,10)) || parseInt(m,10) > 59 || parseInt(s,10) > 59 ) {
            console.warn("Payroll.js (parseTimeTo24Hour): Hour, minute, or second out of range after conversion:", timeStr12hr); return '';
        }
        return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
    } catch(e) {
        console.error("Payroll.js (parseTimeTo24Hour): Error parsing time string:", timeStr12hr, e);
        return '';
    }
}
// --- End Helper Functions ---


// --- Global Variables ---
let selectedReportPunchId = null;
let selectedReportRowElement = null;
let currentExceptionData = {};
let reportModal = null;
let editPunchModal = null;
let reportTbody = null;
let btnEditReportRow = null;
let editPunchForm = null;
let closePeriodConfirmModal = null;
let confirmModalTitle = null;
let confirmModalMessage = null;
let confirmModalOkBtn = null;
let confirmModalCancelBtn = null;
let closeConfirmModalSpanBtn = null;
let closePayPeriodFormElement = null;
let btnPrintPayroll = null;
let btnPrintAllTimeCards = null;
let btnExportPayrollElement = null;
let actualClosePayPeriodButton = null;
let mainActionButtons = [];
// --- End Global Variables ---

// --- Initialize Page ---
document.addEventListener('DOMContentLoaded', function() {

    const _showModal = window.showModal;
    const _hideModal = window.hideModal;

    // --- Exception Report Modal Functions ---
    function showExceptionReportModal() {
        if(reportModal && typeof _showModal === 'function') {
            _showModal(reportModal);
        }
    }

    function hideExceptionReportModal() {
        if(reportModal && typeof _hideModal === 'function'){
            _hideModal(reportModal);
        }
        if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
        if(btnEditReportRow) btnEditReportRow.disabled = true;
        selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
    }

    function refreshExceptionReport() {
        if (!reportTbody || !reportModal || !btnEditReportRow) {
            const errorMsg = "Error: Required page elements missing for Exception Report.";
            if(typeof showPageNotification === 'function') showPageNotification(errorMsg, true);
            else alert(errorMsg);
            return;
        }
        if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
        selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
        if(btnEditReportRow) btnEditReportRow.disabled = true;
        reportTbody.innerHTML='<tr><td colspan="6" style="text-align:center; padding: 20px; font-style: italic;">Loading exceptions... <i class="fas fa-spinner fa-spin"></i></td></tr>';
        mainActionButtons.forEach(b => { if(b && b.id !== 'btnExceptionReport') b.disabled = true; });
        
        fetch('PayrollServlet', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, cache:'no-store', body: new URLSearchParams({'action':'exceptionReport'}) })
        .then(response => {
            if (!response.ok) { return response.text().then(text => { throw new Error(text || `Server error loading exceptions: ${response.status}`); }); }
            return response.text();
        })
        .then(htmlResponseOrSignal => {
            const responseText = htmlResponseOrSignal.trim().toUpperCase();
            if (responseText === "NO_EXCEPTIONS") {
                hideExceptionReportModal();
                const instructionsPara = document.querySelector('p.instructions');
                if(instructionsPara && instructionsPara.textContent.toLowerCase().includes("run the 'exception report'")) instructionsPara.style.display = 'none';
                const noExceptionMessage = "Good News! No Exceptions found. Payroll actions are now enabled.";
                if(typeof showPageNotification === 'function') {
                     showPageNotification(noExceptionMessage, false, null, "Exceptions Cleared");
                }
                // This is the correct place to enable the buttons
                mainActionButtons.forEach(button => { if(button) button.disabled = false; });
            } else if (htmlResponseOrSignal.includes("report-error-row") || !htmlResponseOrSignal.includes("<tr")) {
                if(reportTbody) reportTbody.innerHTML='<tr><td colspan="6" class="report-error-row">Error loading exception data or no data found.</td></tr>';
                showExceptionReportModal();
                // Buttons remain disabled on error
                mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });
            } else {
                if(reportTbody) reportTbody.innerHTML = htmlResponseOrSignal;
                showExceptionReportModal();
                // --- FIX: Buttons should REMAIN DISABLED if there are still exceptions ---
                // The "Fix Missing Punches" and "Close Report" buttons inside the modal are the only actions the user should take.
            }
        })
        .catch(error => {
            hideExceptionReportModal();
            if(typeof showPageNotification === 'function') showPageNotification("Error loading exception report: " + error.message, true);
            mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });
        });
    }
    // --- End Exception Report Modal Functions ---

    // --- Edit Punch Modal Functions (from Exception Report) ---
    function hideEditPunchModal() {
        if(editPunchModal && typeof _hideModal === 'function') {
            _hideModal(editPunchModal);
        }
        if(selectedReportRowElement){ selectedReportRowElement.classList.remove('selected'); selectedReportRowElement = null; }
        selectedReportPunchId = null; currentExceptionData = {};
        if(btnEditReportRow) btnEditReportRow.disabled = true;
    }

    function populateEditPunchModal(data) {
        if(!editPunchModal || !data){ return; }
        const nameDisplay = document.getElementById('editPunchEmployeeName');
        const scheduleDisplay = document.getElementById('editPunchScheduleInfo');
        const punchIdField = editPunchModal.querySelector('#editPunchIdField');
        const employeeIdField = editPunchModal.querySelector('#editPunchEmployeeIdField');
        const dateField = editPunchModal.querySelector('#editDate');
        const inTimeField = editPunchModal.querySelector('#editInTime');
        const outTimeField = editPunchModal.querySelector('#editOutTime');
        const userTimeZoneField = editPunchModal.querySelector('#editUserTimeZone');

        if(nameDisplay) nameDisplay.textContent = data.employeeName || `EID: ${data.globalEid || 'N/A'}`;
        if(scheduleDisplay) {
            let scheduleText = `Schedule: ${data.scheduleName || 'N/A'}`;
            if(data.shiftStart && data.shiftEnd && String(data.shiftStart).toLowerCase() !== 'n/a' && String(data.shiftEnd).toLowerCase() !== 'n/a'){
                scheduleText += ` (${data.shiftStart} - ${data.shiftEnd})`;
            }
            scheduleDisplay.textContent = scheduleText;
        }
        let formattedDate = '';
        if(data.date){
            try {
                const parts = String(data.date).split('/'); 
                if(parts.length === 3){ formattedDate = `${parts[2]}-${parts[0].padStart(2,'0')}-${parts[1].padStart(2,'0')}`; }
                else { formattedDate = data.date; }
            }
            catch(e){ formattedDate = data.date; }
        }

        if(punchIdField) punchIdField.value = data.punchId || '';
        if(employeeIdField) employeeIdField.value = data.globalEid || '';
        if(userTimeZoneField && typeof effectiveTimeZoneIdJs === 'string') userTimeZoneField.value = effectiveTimeZoneIdJs;
        if(dateField) dateField.value = formattedDate;
        let parsedInTime = parseTimeTo24Hour(data.inTime);
        if(inTimeField) inTimeField.value = parsedInTime;
        let parsedOutTime = parseTimeTo24Hour(data.outTime);
        if(outTimeField) outTimeField.value = parsedOutTime;

        setTimeout(()=>{
            const fieldToFocus = (outTimeField && (outTimeField.value === '' || (data.outTime && String(data.outTime).toLowerCase().includes('missing')))) ? outTimeField : inTimeField;
            if (fieldToFocus) { try { fieldToFocus.focus(); if (typeof fieldToFocus.select === 'function') fieldToFocus.select(); } catch (e) {} }
        }, 150);
    }

    function prepareAndShowEditPunchModal(exceptionData) {
        if (!exceptionData || !exceptionData.globalEid){ if(typeof showPageNotification === 'function') showPageNotification("Cannot edit: Missing Employee ID.", true); return; }
        const globalEid = exceptionData.globalEid;
        const url = `${window.appRootPath}/EmployeeInfoServlet?action=getScheduleInfo&eid=${globalEid}`;

        fetch(url,{cache:'no-store'})
            .then(response => {
                if (!response.ok) { return response.text().then(text => { throw new Error(`Schedule fetch failed: ${response.status}. ${text}`); }); }
                return response.json();
            })
            .then(scheduleData => {
                if (scheduleData.success) {
                    populateEditPunchModal({ ...exceptionData, ...scheduleData });
                    if(editPunchModal && typeof _showModal === 'function') _showModal(editPunchModal);
                } else { throw new Error(scheduleData.message || 'Failed to get schedule data.'); }
            })
            .catch(error => { if(typeof showPageNotification === 'function') showPageNotification("Could not load details for editing: " + error.message, true); });
    }

    function showSuccessAndRefresh(message) {
        const okButton = document.getElementById('okButtonNotificationModalGeneral');
        
        const refreshAction = () => {
            refreshExceptionReport();
        };

        if (okButton) {
            okButton.addEventListener('click', refreshAction, { once: true });
        }
        
        if (typeof showPageNotification === 'function') {
            showPageNotification(message, false, null, "Success");
        }
    }

    function handleEditPunchFormSubmit(event) {
        event.preventDefault();
        if (!editPunchForm) { return; }

        const inTimeValue = editPunchForm.querySelector('#editInTime').value;
        const outTimeValue = editPunchForm.querySelector('#editOutTime').value;
        if (!inTimeValue || !outTimeValue) {
            if (typeof showPageNotification === 'function') {
                showPageNotification("To fix an exception, both IN and OUT times are required.", true);
            } else {
                alert("To fix an exception, both IN and OUT times are required.");
            }
            return;
        }

        const formData = new FormData(editPunchForm);
        if(typeof effectiveTimeZoneIdJs === 'string' && !formData.has('userTimeZone')) {
            formData.append('userTimeZone', effectiveTimeZoneIdJs);
        }
        const formBody = new URLSearchParams(formData);
        const submitButton = editPunchForm.querySelector('button[type="submit"]');
        if(submitButton) submitButton.disabled = true;

        fetch('AddEditAndDeletePunchesServlet', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body: formBody })
        .then(response => {
            if (!response.ok) { return response.json().catch(()=>response.text()).then(errorData => { const msg = (typeof errorData === 'object' ? errorData.error : errorData); throw new Error(msg || `Save failed: ${response.status}`); }); }
            return response.json();
        })
        .then(data => {
            if (data.success) {
                hideEditPunchModal();
                showSuccessAndRefresh(data.message || "Punch updated successfully!");
            } else { 
                if(typeof showPageNotification === 'function') showPageNotification("Error saving punch: " + (data.error || "Unknown error."), true); 
            }
        })
        .catch(error => { 
            if(typeof showPageNotification === 'function') showPageNotification("Error saving punch: " + error.message, true); 
        })
        .finally(() => { 
            if(submitButton) submitButton.disabled = false; 
        });
    }
    // --- End Edit Punch Modal Functions ---

    // --- Print Function ---
    function printPayrollSummary() {
        const tableToPrint = document.getElementById('payrollTable');
        const payPeriodMsgElement = document.querySelector('.parent-container h2');
        const mainTitleElement = document.querySelector('.parent-container h1');
        if (!tableToPrint) { if(typeof showPageNotification === 'function') showPageNotification("Payroll table not found. Cannot print.", true); else alert("Payroll table not found. Cannot print."); return; }
        const payPeriodText = payPeriodMsgElement ? payPeriodMsgElement.textContent : "Payroll Report";
        const mainTitleText = mainTitleElement ? mainTitleElement.textContent : "Time Clock System";
        let reportsCSSHref = "css/reports.css";
        const reportsCSSLinkElem = document.querySelector('link[href^="css/reports.css"]');
        if (reportsCSSLinkElem) reportsCSSHref = reportsCSSLinkElem.getAttribute('href');
        let payrollCSSHref = "css/payroll.css";
        const payrollCSSLinkElem = document.querySelector('link[href^="css/payroll.css"]');
        if (payrollCSSLinkElem) payrollCSSHref = payrollCSSLinkElem.getAttribute('href');
        const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
        if (!printWindow) { if(typeof showPageNotification === 'function') showPageNotification("Could not open print window. Check popup blockers.", true); else alert("Could not open print window. Check popup blockers."); return; }
        printWindow.document.write(`<html><head><title>Print - ${payPeriodText}</title>`);
        printWindow.document.write(`<link rel="stylesheet" href="${reportsCSSHref}">`);
        printWindow.document.write(`<link rel="stylesheet" href="${payrollCSSHref}">`);
        printWindow.document.write(`<style>
            body { margin: 20px; background-color: #fff !important; font-size: 9pt; } .main-navbar, #payroll-actions-container, .modal, script, .parent-container > h1, p.instructions, .page-message { display: none !important; }
            .parent-container.reports-container, .report-display-area { width: 100% !important; max-width: none !important; margin: 0 !important; padding: 0 !important; box-shadow: none; border: none; }
            .parent-container > h2 { text-align: center; color: #000; font-size: 12pt; margin-bottom: 15px; font-weight: 500; } .report-table-container { border: 1px solid #999 !important; max-height: none !important; overflow: visible !important; }
            .report-table { width: 100% !important; border-collapse: collapse !important; font-size: 8pt; } .report-table th, .report-table td { border: 1px solid #bbb !important; padding: 4px 6px !important; color: #000 !important; background-color: #fff !important; text-align: left; }
            .report-table th { background-color: #e9ecef !important; font-weight: bold; text-align: center !important; } .report-table td[style*='text-align: right'], .report-table th[style*='text-align: right'] { text-align: right !important; }
            .report-table tfoot td { font-weight: bold; } @media print { @page { size: landscape; margin: 0.5in; } body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
            </style></head><body>`);
        printWindow.document.write(`<h1 style="font-size: 18pt; text-align:center;">${mainTitleText}</h1>`);
        printWindow.document.write(`<h2>${payPeriodText}</h2>`);
        const tableContainerToPrint = tableToPrint.closest('.report-table-container');
        if (tableContainerToPrint) { printWindow.document.write(tableContainerToPrint.outerHTML); } else { printWindow.document.write(tableToPrint.outerHTML); }
        printWindow.document.write(`</body></html>`);
        printWindow.document.close();
        setTimeout(() => { try { printWindow.focus(); printWindow.print(); } catch (e) { console.error("Print failed:", e); alert("Printing failed."); try { printWindow.close(); } catch (e2) {} } }, 750);
    }
    // --- End Print Function ---

    reportModal = document.getElementById('exceptionReportModal');
    editPunchModal = document.getElementById('editPunchModal');
    reportTbody = document.getElementById('exceptionReportTbody');
    btnEditReportRow = document.getElementById('editExceptionButton');
    editPunchForm = document.getElementById('editPunchForm');
    actualClosePayPeriodButton = document.getElementById('btnClosePayPeriodActual');
    closePayPeriodFormElement = document.getElementById('closePayPeriodForm');
    btnPrintPayroll = document.getElementById('btnPrintPayroll');
    btnPrintAllTimeCards = document.getElementById('btnPrintAllTimeCards');
    btnExportPayrollElement = document.getElementById('btnExportPayroll');

    mainActionButtons = [];
    if(btnExportPayrollElement) mainActionButtons.push(btnExportPayrollElement);
    if(btnPrintPayroll) mainActionButtons.push(btnPrintPayroll);
    if(btnPrintAllTimeCards) mainActionButtons.push(btnPrintAllTimeCards);
    if(actualClosePayPeriodButton) mainActionButtons.push(actualClosePayPeriodButton);

    const generalNotificationModal = document.getElementById("notificationModalGeneral");
    if (generalNotificationModal) {
        const gnClose = document.getElementById("closeNotificationModalGeneral");
        const gnOk = document.getElementById("okButtonNotificationModalGeneral");
        if (gnClose) gnClose.addEventListener("click", () => _hideModal(generalNotificationModal));
        if (gnOk) gnOk.addEventListener("click", () => _hideModal(generalNotificationModal));
        window.addEventListener("click", (event) => { if (event.target === generalNotificationModal) _hideModal(generalNotificationModal); });
        const gnContent = generalNotificationModal.querySelector('.modal-content');
        const gnHeader = generalNotificationModal.querySelector('.modal-content > h2');
        if (gnContent && gnHeader && typeof makeElementDraggable === 'function') { makeElementDraggable(gnContent, gnHeader); }
    }

    closePeriodConfirmModal = document.getElementById('closePeriodConfirmModal');
    if (closePeriodConfirmModal) {
        confirmModalTitle = closePeriodConfirmModal.querySelector('#confirmModalTitle');
        confirmModalMessage = closePeriodConfirmModal.querySelector('#confirmModalMessage');
        confirmModalOkBtn = closePeriodConfirmModal.querySelector('#confirmModalOkBtn');
        confirmModalCancelBtn = closePeriodConfirmModal.querySelector('#confirmModalCancelBtn');
        closeConfirmModalSpanBtn = closePeriodConfirmModal.querySelector('#closeConfirmModalSpanBtn');

        if(confirmModalOkBtn && closePayPeriodFormElement) {
            confirmModalOkBtn.addEventListener('click', () => closePayPeriodFormElement.submit());
        }
        if(confirmModalCancelBtn) confirmModalCancelBtn.addEventListener('click', () => _hideModal(closePeriodConfirmModal));
        if(closeConfirmModalSpanBtn) closeConfirmModalSpanBtn.addEventListener('click', () => _hideModal(closePeriodConfirmModal));
        window.addEventListener('click', (event) => { if (event.target === closePeriodConfirmModal) _hideModal(closePeriodConfirmModal); });
    }

    const btnCloseReportModalElem = document.getElementById('closeExceptionReportModal');
    const btnCloseReportButtonElem = document.getElementById('closeExceptionReportButton');
    if (btnCloseReportModalElem) btnCloseReportModalElem.addEventListener('click', hideExceptionReportModal);
    if (btnCloseReportButtonElem) btnCloseReportButtonElem.addEventListener('click', hideExceptionReportModal);

    const closeEditPunchModalButtonElem = document.getElementById('closeEditPunchModal');
    const cancelEditPunchButtonElem = document.getElementById('cancelEditPunch');
    if (closeEditPunchModalButtonElem) closeEditPunchModalButtonElem.addEventListener('click', hideEditPunchModal);
    if (cancelEditPunchButtonElem) cancelEditPunchButtonElem.addEventListener('click', hideEditPunchModal);

    const btnShowReport = document.getElementById('btnExceptionReport');
    const notificationBar = document.getElementById('pageNotification');

    if (notificationBar && notificationBar.textContent.trim() !== '' && notificationBar.style.display !== 'none') {
        setTimeout(() => { if(notificationBar){ notificationBar.style.transition = 'opacity 0.5s ease-out'; notificationBar.style.opacity = '0'; setTimeout(() => { notificationBar.style.display = 'none'; }, 500); } }, 7000);
    } else if(notificationBar) { notificationBar.style.display = 'none'; }

    if (btnPrintPayroll) btnPrintPayroll.addEventListener('click', printPayrollSummary);
    if (btnPrintAllTimeCards) {
        if (typeof currentTenantIdJs !== 'undefined') {
            btnPrintAllTimeCards.addEventListener('click', function() {
                if (typeof currentTenantIdJs !== 'number' || currentTenantIdJs <= 0) {
                    alert("Error: Tenant information is missing. Cannot proceed.");
                    return;
                }
                const servletUrl = `PrintTimecardsServlet?tenantId=${currentTenantIdJs}&filterType=all`;
                window.open(servletUrl, '_blank');
            });
        } else {
             btnPrintAllTimeCards.disabled = true;
        }
    }

    if (btnShowReport) btnShowReport.addEventListener('click', refreshExceptionReport);
    if (btnEditReportRow) {
        btnEditReportRow.addEventListener('click', () => {
            if (!selectedReportRowElement || !currentExceptionData?.globalEid) {
                 if(typeof showPageNotification === 'function') showPageNotification("Please select an exception row to edit.", true);
                 else alert("Please select an exception row to edit.");
                 return;
            }
            prepareAndShowEditPunchModal(currentExceptionData);
        });
    }

    if (editPunchForm) editPunchForm.addEventListener('submit', handleEditPunchFormSubmit);
    if (actualClosePayPeriodButton) {
        actualClosePayPeriodButton.addEventListener('click', function() {
            if (typeof payPeriodEndDateJs === 'undefined' || !payPeriodEndDateJs) {
                if(typeof showPageNotification === 'function') showPageNotification("Pay period end date is not available. Cannot proceed.", true);
                else alert("Pay period end date is not available. Cannot proceed.");
                return;
            }
            const today = new Date(); today.setHours(0, 0, 0, 0);
            const parts = payPeriodEndDateJs.split('-');
            const periodEnd = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
            periodEnd.setHours(0,0,0,0);
            let title, message, confirmButtonText;
            if (periodEnd >= today) {
                title = "Close Pay Period Early?";
                message = "The current pay period has not yet ended (Ends: " + payPeriodEndDateJs + ").\n\n" + "Closing the pay period now will finalize all entries up to this moment. This is usually done only if you intend to change your pay period frequency or end dates.\n\n" + "Standard closing procedures (OT calculation, archiving, accruals) will apply.\n\n" + "Are you sure you want to close the pay period early?";
                confirmButtonText = "Proceed to Close Early";
            } else {
                title = "Confirm Close Pay Period";
                message = "Are you sure you want to close the current pay period (Ended: " + payPeriodEndDateJs + ")?\n\n" + "This will:\n" + "- Finalize and UPDATE Overtime calculations for all punches in this period.\n" + "- ARCHIVE all punches for this period.\n" + "- RUN ACCRUALS for eligible employees.\n" + "- Log this payroll run to history.\n" + "- Automatically set the next pay period dates in Settings.\n\n" + "Please ensure all reports (especially Exception Report) and manual adjustments are complete.\n" + "This action CANNOT be easily undone.";
                confirmButtonText = "Confirm Close Period";
            }
            if (confirmModalTitle) confirmModalTitle.textContent = title;
            if (confirmModalMessage) {
                if (periodEnd >= today) { confirmModalMessage.className = 'confirm-message-centered'; } 
                else { confirmModalMessage.className = 'confirm-message-list'; }
                confirmModalMessage.innerHTML = message.replace(/\n/g, '<br>');
            }
            if (confirmModalOkBtn) {
                 confirmModalOkBtn.textContent = confirmButtonText;
                 confirmModalOkBtn.className = 'glossy-button text-red';
            }
            if(closePeriodConfirmModal && typeof _showModal === 'function') {
                _showModal(closePeriodConfirmModal);
            }
        });
    } 
    if (reportTbody) {
        reportTbody.addEventListener('click', (event) => {
            const punchRow = event.target.closest('tr[data-punch-id]');
            if (punchRow) {
                if (selectedReportRowElement && selectedReportRowElement !== punchRow) {
                    selectedReportRowElement.classList.remove('selected');
                }
                punchRow.classList.toggle('selected');
                if (punchRow.classList.contains('selected')) {
                    selectedReportRowElement = punchRow;
                    const cells = punchRow.cells;
                    currentExceptionData = {
                        punchId: punchRow.dataset.punchId,
                        globalEid: punchRow.dataset.eid,
                        employeeName: (cells.length > 2) ? `${decodeHtmlEntities(cells[1].textContent)} ${decodeHtmlEntities(cells[2].textContent)}`.trim() : `EID ${punchRow.dataset.eid}`,
                        date: cells.length > 3 ? cells[3].textContent.trim() : '',
                        inTime: cells.length > 4 ? cells[4].querySelector('span.missing-punch-placeholder') ? '' : cells[4].textContent.trim() : '',
                        outTime: cells.length > 5 ? cells[5].querySelector('span.missing-punch-placeholder') ? '' : cells[5].textContent.trim() : ''
                    };
                    if (btnEditReportRow) btnEditReportRow.disabled = false;
                } else {
                    selectedReportRowElement = null;
                    currentExceptionData = {};
                    if (btnEditReportRow) btnEditReportRow.disabled = true;
                }
            }
        });
    }

    const pageErrorElement = document.getElementById('pageNotification');
    const actionsContainer = document.getElementById('payroll-actions-container');
    const dataIsReady = actionsContainer && (actionsContainer.style.display === '' || actionsContainer.style.display === 'grid' || window.getComputedStyle(actionsContainer).display !== 'none');

    if (dataIsReady) {
        const hasSettingsError = pageErrorElement && pageErrorElement.classList.contains('error-message') && pageErrorElement.textContent.toLowerCase().includes("settings");
        mainActionButtons.forEach(button => { if(button) button.disabled = true; });
        if (btnShowReport) { btnShowReport.disabled = hasSettingsError; }
    } else {
        if (btnShowReport) btnShowReport.disabled = true;
        mainActionButtons.forEach(button => { if(button) button.disabled = true; });
    }

    if (reportModal && reportModal.querySelector('.report-modal-content') && reportModal.querySelector('.report-modal-content > h2')) { makeElementDraggable(reportModal.querySelector('.report-modal-content'), reportModal.querySelector('.report-modal-content > h2'));}
    if (editPunchModal && editPunchModal.querySelector('.modal-content') && editPunchModal.querySelector('.modal-content > h2')) {makeElementDraggable(editPunchModal.querySelector('.modal-content'), editPunchModal.querySelector('.modal-content > h2'));}
    const payrollTableElement = document.getElementById('payrollTable');
    if (payrollTableElement && typeof makeTableSortable === 'function') { makeTableSortable(payrollTableElement, { columnIndex: 2, ascending: true });}
});