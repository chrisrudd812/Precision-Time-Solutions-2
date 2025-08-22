// js/payroll.js - v36 (Forces default sort to ascending)

// --- Helper Functions ---
function makeElementDraggable(elmnt, handle) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    const dragHandle = handle || elmnt;
    if (dragHandle && typeof dragHandle.onmousedown !== 'undefined') {
        dragHandle.onmousedown = dragMouseDown;
        if(dragHandle.style) dragHandle.style.cursor = 'move';
    } else {
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
        if (!parts) return '';
        let h = parseInt(parts[1],10);
        const m = parts[2];
        const s = parts[3] ? parts[3] : '00';
        const ampm = parts[4] ? parts[4].toUpperCase() : null;

        if (ampm) {
            if (ampm === 'PM' && h !== 12) h += 12;
            if (ampm === 'AM' && h === 12) h = 0;
        }
        if (h > 23 || h < 0 || isNaN(h) || isNaN(parseInt(m,10)) || isNaN(parseInt(s,10)) || parseInt(m,10) > 59 || parseInt(s,10) > 59 ) return '';
        return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
    } catch(e) {
        return '';
    }
}

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

// --- Exception Report Modal Functions ---
function showExceptionReportModal() {
    if(reportModal && typeof showModal === 'function') {
        showModal(reportModal);
    }
}

function hideExceptionReportModal() {
    if(reportModal && typeof hideModal === 'function'){
        hideModal(reportModal);
    }
    if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
    if(btnEditReportRow) btnEditReportRow.disabled = true;
    selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
}

function refreshExceptionReport() {
    if (!reportTbody || !reportModal || !btnEditReportRow) return;

    if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
    selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
    if(btnEditReportRow) btnEditReportRow.disabled = true;
    reportTbody.innerHTML='<tr><td colspan="6" style="text-align:center; padding: 20px; font-style: italic;">Loading exceptions... <i class="fas fa-spinner fa-spin"></i></td></tr>';

    mainActionButtons.forEach(b => { if(b && b.id !== 'btnExceptionReport') b.disabled = true; });
    showExceptionReportModal();

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
            if(instructionsPara) instructionsPara.style.display = 'none';
            
            const noExceptionMessage = "Good News! No Exceptions found. Payroll actions are now enabled.";
            if(typeof showPageNotification === 'function') {
                 showPageNotification(noExceptionMessage, false, document.getElementById('notificationModalGeneral'), 'Good News!');
            }

            mainActionButtons.forEach(button => { if(button) button.disabled = false; });
        } else if (htmlResponseOrSignal.includes("report-error-row") || !htmlResponseOrSignal.includes("<tr")) {
            if(reportTbody) reportTbody.innerHTML='<tr><td colspan="6" class="report-error-row">Error loading exception data or no data found.</td></tr>';
            mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });
        } else {
            if(reportTbody) reportTbody.innerHTML = htmlResponseOrSignal;
            mainActionButtons.forEach(button => { if(button) button.disabled = false; });
        }
    })
    .catch(error => {
        hideExceptionReportModal();
        if(typeof showPageNotification === 'function') showPageNotification("Error loading exception report: " + error.message, true);
        mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });
    });
}

// --- Edit Punch Modal Functions ---
function hideEditPunchModal() {
    if(editPunchModal && typeof hideModal === 'function') {
        hideModal(editPunchModal);
    }
    if(selectedReportRowElement){ selectedReportRowElement.classList.remove('selected'); selectedReportRowElement = null; }
    selectedReportPunchId = null; currentExceptionData = {};
    if(btnEditReportRow) btnEditReportRow.disabled = true;
}

function populateEditPunchModal(data) {
    if(!editPunchModal || !data) return;
    document.getElementById('editPunchEmployeeName').textContent = data.employeeName || `EID: ${data.globalEid || 'N/A'}`;
    let scheduleText = `Schedule: ${data.scheduleName || 'N/A'}`;
    if(data.shiftStart && data.shiftEnd && String(data.shiftStart).toLowerCase() !== 'n/a' && String(data.shiftEnd).toLowerCase() !== 'n/a'){
        scheduleText += ` (${data.shiftStart} - ${data.shiftEnd})`;
    }
    document.getElementById('editPunchScheduleInfo').textContent = scheduleText;
    
    let formattedDate = '';
    if(data.date){
        const parts = String(data.date).split('/'); 
        if(parts.length === 3){ formattedDate = `${parts[2]}-${parts[0].padStart(2,'0')}-${parts[1].padStart(2,'0')}`; }
    }
    
    editPunchModal.querySelector('#editPunchIdField').value = data.punchId || '';
    editPunchModal.querySelector('#editPunchEmployeeIdField').value = data.globalEid || '';
    if(typeof effectiveTimeZoneIdJs === 'string') editPunchModal.querySelector('#editUserTimeZone').value = effectiveTimeZoneIdJs;
    editPunchModal.querySelector('#editDate').value = formattedDate;
    editPunchModal.querySelector('#editInTime').value = parseTimeTo24Hour(data.inTime);
    editPunchModal.querySelector('#editOutTime').value = parseTimeTo24Hour(data.outTime);

    setTimeout(()=>{
        const outTimeField = editPunchModal.querySelector('#editOutTime');
        const fieldToFocus = (outTimeField && (outTimeField.value === '' || (data.outTime && String(data.outTime).toLowerCase().includes('missing')))) ? outTimeField : editPunchModal.querySelector('#editInTime');
        if (fieldToFocus) { try { fieldToFocus.focus(); if (typeof fieldToFocus.select === 'function') fieldToFocus.select(); } catch (e) {} }
    }, 150);
}

function prepareAndShowEditPunchModal(exceptionData) {
    if (!exceptionData || !exceptionData.globalEid){ if(typeof showPageNotification === 'function') showPageNotification("Cannot edit: Missing Employee ID.", true); return; }
    const url = `${appRootPath}/EmployeeInfoServlet?action=getScheduleInfo&eid=${exceptionData.globalEid}`;

    fetch(url,{cache:'no-store'})
        .then(response => {
            if (!response.ok) { return response.text().then(text => { throw new Error(`Schedule fetch failed: ${response.status}. ${text}`); }); }
            return response.json();
        })
        .then(scheduleData => {
            if (scheduleData.success) {
                populateEditPunchModal({ ...exceptionData, ...scheduleData });
                if(editPunchModal && typeof showModal === 'function') showModal(editPunchModal);
            } else { throw new Error(scheduleData.message || 'Failed to get schedule data.'); }
        })
        .catch(error => { if(typeof showPageNotification === 'function') showPageNotification("Could not load details for editing: " + error.message, true); });
}

function handleEditPunchFormSubmit(event) {
    event.preventDefault();
    if (!editPunchForm) return;
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
            refreshExceptionReport();
            if(typeof showPageNotification === 'function') showPageNotification(data.message || "Punch updated successfully!", false);
        } else { if(typeof showPageNotification === 'function') showPageNotification("Error saving punch: " + (data.error || "Unknown error."), true); }
    })
    .catch(error => { if(typeof showPageNotification === 'function') showPageNotification("Error saving punch: " + error.message, true); })
    .finally(() => { if(submitButton) submitButton.disabled = false; });
}

// --- Print Function ---
function printPayrollSummary() {
    const tableToPrint = document.getElementById('payrollTable');
    const payPeriodMsgElement = document.querySelector('.parent-container h2');
    const mainTitleElement = document.querySelector('.parent-container h1');
    if (!tableToPrint) {
        if(typeof showPageNotification === 'function') showPageNotification("Payroll table not found. Cannot print.", true);
        return;
    }
    
    const payPeriodText = payPeriodMsgElement ? payPeriodMsgElement.textContent : "Payroll Report";
    const mainTitleText = mainTitleElement ? mainTitleElement.textContent : "Time Clock System";
    let reportsCSSHref = document.querySelector('link[href^="css/reports.css"]')?.getAttribute('href') || "css/reports.css";
    let payrollCSSHref = document.querySelector('link[href^="css/payroll.css"]')?.getAttribute('href') || "css/payroll.css";

    const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
    if (!printWindow) {
        if(typeof showPageNotification === 'function') showPageNotification("Could not open print window. Check popup blockers.", true);
        return;
    }
    printWindow.document.write(`<html><head><title>Print - ${payPeriodText}</title>`);
    printWindow.document.write(`<link rel="stylesheet" href="${reportsCSSHref}"><link rel="stylesheet" href="${payrollCSSHref}">`);
    printWindow.document.write(`<style>
        body { margin: 20px; background-color: #fff !important; font-size: 9pt; }
        .main-navbar, #payroll-actions-container, .modal, script, .parent-container > h1, p.instructions, .page-message { display: none !important; }
        .parent-container.reports-container, .report-display-area { width: 100% !important; max-width: none !important; margin: 0 !important; padding: 0 !important; box-shadow: none; border: none; }
        .parent-container > h2 { text-align: center; color: #000; font-size: 12pt; margin-bottom: 15px; font-weight: 500; }
        .report-table-container { border: 1px solid #999 !important; max-height: none !important; overflow: visible !important; }
        .report-table { width: 100% !important; border-collapse: collapse !important; font-size: 8pt; }
        .report-table th, .report-table td { border: 1px solid #bbb !important; padding: 4px 6px !important; color: #000 !important; background-color: #fff !important; text-align: left; }
        .report-table th { background-color: #e9ecef !important; font-weight: bold; text-align: center !important; }
        .report-table td[style*='text-align: right'], .report-table th[style*='text-align: right'] { text-align: right !important; }
        .report-table tfoot td { font-weight: bold; }
        @media print { @page { size: landscape; margin: 0.5in; } body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
        </style></head><body>`);
    printWindow.document.write(`<h1 style="font-size: 18pt; text-align:center;">${mainTitleText}</h1><h2>${payPeriodText}</h2>`);
    const tableContainerToPrint = tableToPrint.closest('.report-table-container');
    printWindow.document.write(tableContainerToPrint ? tableContainerToPrint.outerHTML : tableToPrint.outerHTML);
    printWindow.document.write(`</body></html>`);
    printWindow.document.close();
    setTimeout(() => {
        try { printWindow.focus(); printWindow.print(); }
        catch (e) { alert("Printing failed."); try { printWindow.close(); } catch (e2) {} }
    }, 750);
}

// --- Initialize Page ---
document.addEventListener('DOMContentLoaded', function() {
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
    mainActionButtons = [btnExportPayrollElement, btnPrintPayroll, btnPrintAllTimeCards, actualClosePayPeriodButton];
    
    const generalNotificationModal = document.getElementById("notificationModalGeneral");
    if (generalNotificationModal) {
        const gnClose = generalNotificationModal.querySelector('.close');
        const gnOk = generalNotificationModal.querySelector('.glossy-button');
        const closeHandler = () => {
            if (typeof hideModal === 'function') {
                hideModal(generalNotificationModal);
            }
        };
        if (gnClose) gnClose.addEventListener("click", closeHandler);
        if (gnOk) gnOk.addEventListener("click", closeHandler);
        window.addEventListener("click", (event) => {
            if (event.target === generalNotificationModal) {
                closeHandler();
            }
        });
    }

    closePeriodConfirmModal = document.getElementById('closePeriodConfirmModal');
    if (closePeriodConfirmModal) {
        confirmModalTitle = closePeriodConfirmModal.querySelector('#confirmModalTitle');
        confirmModalMessage = closePeriodConfirmModal.querySelector('#confirmModalMessage');
        confirmModalOkBtn = closePeriodConfirmModal.querySelector('#confirmModalOkBtn');
        confirmModalCancelBtn = closePeriodConfirmModal.querySelector('#confirmModalCancelBtn');
        closeConfirmModalSpanBtn = closePeriodConfirmModal.querySelector('#closeConfirmModalSpanBtn');

        if(confirmModalOkBtn && closePayPeriodFormElement) {
            confirmModalOkBtn.addEventListener('click', function() {
                closePayPeriodFormElement.submit();
                if(typeof hideModal === 'function') hideModal(closePeriodConfirmModal);
            });
        }
        if(confirmModalCancelBtn && typeof hideModal === 'function') confirmModalCancelBtn.addEventListener('click', () => hideModal(closePeriodConfirmModal));
        if(closeConfirmModalSpanBtn && typeof hideModal === 'function') closeConfirmModalSpanBtn.addEventListener('click', () => hideModal(closePeriodConfirmModal));
        window.addEventListener('click', (event) => { if (event.target === closePeriodConfirmModal && typeof hideModal === 'function') hideModal(closePeriodConfirmModal); });
    }

    const btnCloseReportModalElem = document.getElementById('closeExceptionReportModal');
    if (btnCloseReportModalElem) btnCloseReportModalElem.addEventListener('click', () => hideExceptionReportModal());
    const btnCloseReportButtonElem = document.getElementById('closeExceptionReportButton');
    if (btnCloseReportButtonElem) btnCloseReportButtonElem.addEventListener('click', () => hideExceptionReportModal());

    const closeEditPunchModalButtonElem = document.getElementById('closeEditPunchModal');
    if (closeEditPunchModalButtonElem) closeEditPunchModalButtonElem.addEventListener('click', () => hideEditPunchModal());
    const cancelEditPunchButtonElem = document.getElementById('cancelEditPunch');
    if (cancelEditPunchButtonElem) cancelEditPunchButtonElem.addEventListener('click', () => hideEditPunchModal());
    
    const btnShowReport = document.getElementById('btnExceptionReport');
    if (btnShowReport) {
        btnShowReport.addEventListener('click', refreshExceptionReport);
    }
    
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

    if (editPunchForm) { editPunchForm.addEventListener('submit', handleEditPunchFormSubmit); }

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
            let title, message;
            if (periodEnd >= today) {
                title = "Close Pay Period Early?";
                message = "The current pay period has not yet ended. Closing the pay period now will finalize all entries. Are you sure you want to close the pay period early?";
            } else {
                title = "Confirm Close Pay Period";
                message = "Are you sure you want to close the current pay period? This action cannot be undone.";
            }
            if (confirmModalTitle) confirmModalTitle.textContent = title;
            if (confirmModalMessage) confirmModalMessage.innerHTML = message.replace(/\n/g, '<br>');
            if(closePeriodConfirmModal && typeof showModal === 'function') {
                showModal(closePeriodConfirmModal);
            }
        });
    }

    if (reportTbody) {
        reportTbody.addEventListener('click', (event) => {
            const row = event.target.closest('tr');
            if(!row || !row.dataset.punchId || !reportTbody.contains(row)) return;
            if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
            row.classList.add('selected');
            selectedReportRowElement = row;
            
            currentExceptionData.punchId = row.dataset.punchId;
            currentExceptionData.globalEid = row.dataset.eid;
            currentExceptionData.employeeName = `${row.cells[1].textContent} ${row.cells[2].textContent}`;
            currentExceptionData.date = row.cells[3].textContent;
            currentExceptionData.inTime = row.cells[4].textContent;
            currentExceptionData.outTime = row.cells[5].textContent;
            
            if(btnEditReportRow) btnEditReportRow.disabled = false;
        });
    }
    
    if (btnPrintPayroll) {
        btnPrintPayroll.addEventListener('click', printPayrollSummary);
    }

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

    const actionsContainer = document.getElementById('payroll-actions-container');
    const dataIsReady = actionsContainer && window.getComputedStyle(actionsContainer).display !== 'none';
    const pageErrorElement = document.getElementById('pageNotification');

    if (dataIsReady) {
        const hasSettingsError = pageErrorElement && pageErrorElement.classList.contains('error-message');
        
        mainActionButtons.forEach(button => {
            if(button) button.disabled = true;
        });

        if (btnShowReport) {
            btnShowReport.disabled = hasSettingsError;
        }
    } else {
        if (btnShowReport) btnShowReport.disabled = true;
        mainActionButtons.forEach(button => { if(button) button.disabled = true; });
    }

    const payrollTableElement = document.getElementById('payrollTable');
    if (payrollTableElement) {
        const firstHeader = payrollTableElement.querySelector('th.sortable');
        if (firstHeader) {
            setTimeout(() => {
                // Set the header to a 'desc' state first, so the click toggles it to 'asc'
                firstHeader.classList.add('sort-desc');
                firstHeader.classList.remove('sort-asc');
                firstHeader.click();
            }, 100);
        }
    }
});