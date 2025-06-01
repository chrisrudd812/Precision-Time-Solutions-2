// js/payroll.js - v31 (Complete, No Placeholders, Diagnostics Included)

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
        // Check if already 24-hour format like HH:mm or HH:mm:ss
        if (/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(timeStr12hr)) {
            return timeStr12hr.length === 5 ? timeStr12hr + ':00' : timeStr12hr; // Add seconds if HH:mm
        }
        // Try to parse 12-hour format (e.g., "09:54:18 AM")
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
            if (ampm === 'AM' && h === 12) h = 0; // Midnight case
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


// --- Exception Report Modal Functions ---
function showExceptionReportModal() {
    console.log("[Payroll.js] showExceptionReportModal CALLED.");
    if(reportModal && typeof showModal === 'function') {
        showModal(reportModal);
    } else if (reportModal) {
        reportModal.classList.add('modal-visible');
    } else {
        console.warn("[Payroll.js] reportModal element not found in showExceptionReportModal.");
    }
}

function hideExceptionReportModal() {
    console.log("[Payroll.js] hideExceptionReportModal CALLED.");
    if(reportModal && typeof hideModal === 'function'){
        console.log("[Payroll.js] Calling commonUtils.hideModal for exceptionReportModal.");
        hideModal(reportModal);
    } else if (reportModal) {
        console.log("[Payroll.js] Fallback hide for exceptionReportModal.");
        reportModal.classList.remove('modal-visible');
    } else {
         console.warn("[Payroll.js] reportModal element not found in hideExceptionReportModal.");
    }
    if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
    if(btnEditReportRow) btnEditReportRow.disabled = true;
    selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
}

function refreshExceptionReport() {
    console.log("[Payroll.js] refreshExceptionReport function CALLED.");
    if (!reportTbody || !reportModal || !btnEditReportRow) {
        const errorMsg = "[Payroll.js] Error: Required page elements missing for Exception Report. reportTbody=" + !!reportTbody + ", reportModal=" + !!reportModal + ", btnEditReportRow=" + !!btnEditReportRow;
        console.error(errorMsg);
        if(typeof showPageNotification === 'function') showPageNotification(errorMsg.replace("[Payroll.js] ", ""), true);
        else alert(errorMsg.replace("[Payroll.js] ", ""));
        return;
    }
    if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
    selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
    if(btnEditReportRow) btnEditReportRow.disabled = true;
    reportTbody.innerHTML='<tr><td colspan="6" style="text-align:center; padding: 20px; font-style: italic;">Loading exceptions... <i class="fas fa-spinner fa-spin"></i></td></tr>';

    mainActionButtons.forEach(b => { if(b && b.id !== 'btnExceptionReport') b.disabled = true; });
    console.log("[Payroll.js] Other main buttons disabled during exception report load.");
    showExceptionReportModal();

    fetch('PayrollServlet', { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, cache:'no-store', body: new URLSearchParams({'action':'exceptionReport'}) })
    .then(response => {
        console.log("[Payroll.js] Exception Report fetch response status:", response.status);
        if (!response.ok) { return response.text().then(text => { throw new Error(text || `Server error loading exceptions: ${response.status}`); }); }
        return response.text();
    })
    .then(htmlResponseOrSignal => {
        console.log("[Payroll.js] Exception Report fetch response text received (first 100 chars):", htmlResponseOrSignal.substring(0,100));
        const responseText = htmlResponseOrSignal.trim().toUpperCase();
        if (responseText === "NO_EXCEPTIONS") {
            console.log("[Payroll.js] NO_EXCEPTIONS received.");
            hideExceptionReportModal();
            const instructionsPara = document.querySelector('p.instructions');
            if(instructionsPara && instructionsPara.textContent.toLowerCase().includes("run the 'exception report'")) instructionsPara.style.display = 'none';
            
            const noExceptionMessage = "Good News! No Exceptions found. Payroll actions are now enabled.";
            if(typeof showPageNotification === 'function') {
                 console.log("[Payroll.js] Calling showPageNotification for 'No Exceptions'.");
                 showPageNotification(noExceptionMessage, false); // false indicates success/info
            } else if (document.getElementById('notificationModal') && document.getElementById('notificationMessage')) {
                console.log("[Payroll.js] Manually showing notificationModal for 'No Exceptions'.");
                document.getElementById('notificationMessage').textContent = noExceptionMessage;
                document.getElementById('notificationModalTitle').textContent = "Information";
                if(typeof showModal === 'function') showModal(document.getElementById('notificationModal'));
                else document.getElementById('notificationModal').classList.add('modal-visible');
            }

            mainActionButtons.forEach(button => { if(button) button.disabled = false; });
            console.log("[Payroll.js] Main action buttons enabled after NO_EXCEPTIONS.");
        } else if (htmlResponseOrSignal.includes("report-error-row") || !htmlResponseOrSignal.includes("<tr")) {
            console.warn("[Payroll.js] Exception Report HTML contains error or no data rows.");
            if(reportTbody) reportTbody.innerHTML='<tr><td colspan="6" class="report-error-row">Error loading exception data or no data found.</td></tr>';
            mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });
        } else {
            console.log("[Payroll.js] Exception Report HTML received and seems valid. Populating table.");
            if(reportTbody) reportTbody.innerHTML = htmlResponseOrSignal;
            mainActionButtons.forEach(button => { if(button) button.disabled = false; }); // Enable other buttons if exceptions are loaded
            console.log("[Payroll.js] Main action buttons (re)enabled after loading exceptions.");
        }
    })
    .catch(error => {
        console.error("[Payroll.js] Error loading exception report:", error);
        hideExceptionReportModal();
        if(typeof showPageNotification === 'function') showPageNotification("Error loading exception report: " + error.message, true);
        mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });
    });
}
// --- End Exception Report Modal Functions ---

// --- Edit Punch Modal Functions (from Exception Report) ---
function hideEditPunchModal() {
    console.log("[Payroll.js] hideEditPunchModal CALLED.");
    if(editPunchModal && typeof hideModal === 'function') {
        console.log("[Payroll.js] Calling commonUtils.hideModal for editPunchModal.");
        hideModal(editPunchModal);
    } else if (editPunchModal) {
        editPunchModal.classList.remove('modal-visible');
    }
    if(selectedReportRowElement){ selectedReportRowElement.classList.remove('selected'); selectedReportRowElement = null; }
    selectedReportPunchId = null; currentExceptionData = {};
    if(btnEditReportRow) btnEditReportRow.disabled = true;
}

function populateEditPunchModal(data) {
    console.log("[Payroll.js] populateEditPunchModal received data:", data ? JSON.parse(JSON.stringify(data)) : "null");
    if(!editPunchModal || !data){ console.error("[Payroll.js] Cannot populate edit punch modal (missing elements or data)."); return; }

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
            else { console.warn("[Payroll.js] Date format for edit modal not MM/DD/YYYY:", data.date); formattedDate = data.date; /* Try to use as is if not parsable */ }
        }
        catch(e){ console.error("[Payroll.js] Error formatting date for input:", data.date, e); formattedDate = data.date; }
    }
    console.log("[Payroll.js] Formatted date for edit modal:", formattedDate);

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
    console.log("[Payroll.js] prepareAndShowEditPunchModal called with:", exceptionData ? JSON.parse(JSON.stringify(exceptionData)) : "null");
    if (!exceptionData || !exceptionData.globalEid){ if(typeof showPageNotification === 'function') showPageNotification("Cannot edit: Missing Employee ID.", true); return; }
    const globalEid = exceptionData.globalEid;
    const contextPath = (typeof appRootPath === 'string' && appRootPath) ? appRootPath : (window.location.pathname.substring(0, window.location.pathname.indexOf("/",1) === -1 ? window.location.pathname.length : window.location.pathname.indexOf("/",1)));
    const url = `${contextPath}/EmployeeInfoServlet?action=getScheduleInfo&eid=${globalEid}`;

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
    console.log("[Payroll.js] handleEditPunchFormSubmit CALLED.");
    if (!editPunchForm) { console.error("[Payroll.js] Edit punch form missing!"); return; }
    const formData = new FormData(editPunchForm);
    // Ensure userTimeZone is part of formData if the hidden field was populated
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
            refreshExceptionReport(); // Refresh the list after successful edit
            if(typeof showPageNotification === 'function') showPageNotification(data.message || "Punch updated successfully!", false);
        } else { if(typeof showPageNotification === 'function') showPageNotification("Error saving punch: " + (data.error || "Unknown error."), true); }
    })
    .catch(error => { if(typeof showPageNotification === 'function') showPageNotification("Error saving punch: " + error.message, true); })
    .finally(() => { if(submitButton) submitButton.disabled = false; });
}
// --- End Edit Punch Modal Functions ---


// --- Print Function ---
function printPayrollSummary() {
    console.log("[Payroll.js] printPayrollSummary function CALLED.");
    const tableToPrint = document.getElementById('payrollTable');
    const payPeriodMsgElement = document.querySelector('.parent-container h2');
    const mainTitleElement = document.querySelector('.parent-container h1');

    if (!tableToPrint) {
        console.error("[Payroll.js] printPayrollSummary: Payroll table not found!");
        if(typeof showPageNotification === 'function') showPageNotification("Payroll table not found. Cannot print.", true);
        else alert("Payroll table not found. Cannot print.");
        return;
    }
    console.log("[Payroll.js] printPayrollSummary: Table and titles found. Proceeding to open print window.");
    const payPeriodText = payPeriodMsgElement ? payPeriodMsgElement.textContent : "Payroll Report";
    const mainTitleText = mainTitleElement ? mainTitleElement.textContent : "Time Clock System";

    let reportsCSSHref = "css/reports.css";
    const reportsCSSLinkElem = document.querySelector('link[href^="css/reports.css"]');
    if (reportsCSSLinkElem) reportsCSSHref = reportsCSSLinkElem.getAttribute('href');

    let payrollCSSHref = "css/payroll.css";
    const payrollCSSLinkElem = document.querySelector('link[href^="css/payroll.css"]');
    if (payrollCSSLinkElem) payrollCSSHref = payrollCSSLinkElem.getAttribute('href');

    const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
    if (!printWindow) {
        if(typeof showPageNotification === 'function') showPageNotification("Could not open print window. Check popup blockers.", true);
        else alert("Could not open print window. Check popup blockers.");
        return;
    }
    printWindow.document.write(`<html><head><title>Print - ${payPeriodText}</title>`);
    printWindow.document.write(`<link rel="stylesheet" href="${reportsCSSHref}">`);
    printWindow.document.write(`<link rel="stylesheet" href="${payrollCSSHref}">`);
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
        @media print {
            @page { size: landscape; margin: 0.5in; }
            body { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
        }
        </style></head><body>`);
    printWindow.document.write(`<h1 style="font-size: 18pt; text-align:center;">${mainTitleText}</h1>`);
    printWindow.document.write(`<h2>${payPeriodText}</h2>`);
    const tableContainerToPrint = tableToPrint.closest('.report-table-container');
    if (tableContainerToPrint) {
        printWindow.document.write(tableContainerToPrint.outerHTML);
    } else {
        printWindow.document.write(tableToPrint.outerHTML);
    }
    printWindow.document.write(`</body></html>`);
    printWindow.document.close();
    setTimeout(() => {
        try { printWindow.focus(); printWindow.print(); }
        catch (e) { console.error("Print failed:", e); alert("Printing failed."); try { printWindow.close(); } catch (e2) {} }
    }, 750);
}
// --- End Print Function ---

// --- Initialize Page ---
document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Payroll Page DOMContentLoaded START (v30 - Notify Modal Diag) ---");

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

    const generalNotificationModal = document.getElementById("notificationModal");
    if (generalNotificationModal) {
        console.log("[Payroll.js] generalNotificationModal found.");
        const gnClose = document.getElementById("closeNotificationModal");
        const gnOk = document.getElementById("okButton");

        if (gnClose) {
            console.log("[Payroll.js] Attaching click listener to notificationModal Close (X) button (#closeNotificationModal).");
            gnClose.addEventListener("click", function(event){
                event.stopPropagation();
                console.log("[Payroll.js] NotificationModal Close (X) button CLICKED.");
                if (typeof hideModal === 'function') {
                    console.log("[Payroll.js] Calling hideModal for generalNotificationModal via X.");
                    hideModal(generalNotificationModal);
                } else {
                    console.warn("[Payroll.js] hideModal function not found for X button! Fallback remove class.");
                    generalNotificationModal.classList.remove('modal-visible');
                }
            });
        } else { console.warn("[Payroll.js] #closeNotificationModal (X button) NOT found."); }

        if (gnOk) {
            console.log("[Payroll.js] Attaching click listener to notificationModal OK button (#okButton).");
            gnOk.addEventListener("click", function(event){
                event.stopPropagation();
                console.log("[Payroll.js] NotificationModal OK button CLICKED.");
                if (typeof hideModal === 'function') {
                    console.log("[Payroll.js] Calling hideModal for generalNotificationModal via OK.");
                    hideModal(generalNotificationModal);
                } else {
                    console.warn("[Payroll.js] hideModal function not found for OK button! Fallback remove class.");
                    generalNotificationModal.classList.remove('modal-visible');
                }
            });
        } else { console.warn("[Payroll.js] #okButton (OK button) NOT found."); }

        window.addEventListener("click", function(event) {
            if (event.target === generalNotificationModal) {
                console.log("[Payroll.js] Clicked on generalNotificationModal backdrop.");
                if (typeof hideModal === 'function') {
                    console.log("[Payroll.js] Calling hideModal for generalNotificationModal via outside click.");
                    hideModal(generalNotificationModal);
                } else {
                     console.warn("[Payroll.js] hideModal function not found for outside click! Fallback remove class.");
                    generalNotificationModal.classList.remove('modal-visible');
                }
            }
        });
        const gnContent = generalNotificationModal.querySelector('.modal-content');
        const gnHeader = generalNotificationModal.querySelector('.modal-content > h2');
        if (gnContent && gnHeader && typeof makeElementDraggable === 'function') { makeElementDraggable(gnContent, gnHeader); }
    } else { console.warn("[Payroll.js] #notificationModal NOT found."); }

    closePeriodConfirmModal = document.getElementById('closePeriodConfirmModal');
    if (closePeriodConfirmModal) {
        confirmModalTitle = closePeriodConfirmModal.querySelector('#confirmModalTitle');
        confirmModalMessage = closePeriodConfirmModal.querySelector('#confirmModalMessage');
        confirmModalOkBtn = closePeriodConfirmModal.querySelector('#confirmModalOkBtn');
        confirmModalCancelBtn = closePeriodConfirmModal.querySelector('#confirmModalCancelBtn');
        closeConfirmModalSpanBtn = closePeriodConfirmModal.querySelector('#closeConfirmModalSpanBtn');

        if(confirmModalOkBtn && closePayPeriodFormElement) {
            confirmModalOkBtn.addEventListener('click', function() {
                 console.log("[Payroll.js] Confirm Modal OK button CLICKED (Submit Close Pay Period Form).");
                closePayPeriodFormElement.submit();
                if(typeof hideModal === 'function') hideModal(closePeriodConfirmModal);
            });
        }
        if(confirmModalCancelBtn && typeof hideModal === 'function') confirmModalCancelBtn.addEventListener('click', () => { console.log("[Payroll.js] Confirm Modal CANCEL clicked."); hideModal(closePeriodConfirmModal);});
        if(closeConfirmModalSpanBtn && typeof hideModal === 'function') closeConfirmModalSpanBtn.addEventListener('click', () => { console.log("[Payroll.js] Confirm Modal X clicked."); hideModal(closePeriodConfirmModal);});
        window.addEventListener('click', (event) => { if (event.target === closePeriodConfirmModal && typeof hideModal === 'function') { console.log("[Payroll.js] Clicked outside closePeriodConfirmModal."); hideModal(closePeriodConfirmModal); }});
    }

    const btnCloseReportModalElem = document.getElementById('closeExceptionReportModal');
    const btnCloseReportButtonElem = document.getElementById('closeExceptionReportButton');
    if (btnCloseReportModalElem) btnCloseReportModalElem.addEventListener('click', () => { console.log("[Payroll.js] Exception Modal X clicked."); hideExceptionReportModal(); });
    if (btnCloseReportButtonElem) btnCloseReportButtonElem.addEventListener('click', () => { console.log("[Payroll.js] Exception Modal Close button clicked."); hideExceptionReportModal(); });

    const closeEditPunchModalButtonElem = document.getElementById('closeEditPunchModal');
    const cancelEditPunchButtonElem = document.getElementById('cancelEditPunch');
    if (closeEditPunchModalButtonElem) closeEditPunchModalButtonElem.addEventListener('click', () => { console.log("[Payroll.js] Edit Punch Modal X clicked."); hideEditPunchModal(); });
    if (cancelEditPunchButtonElem) cancelEditPunchButtonElem.addEventListener('click', () => { console.log("[Payroll.js] Edit Punch Modal Cancel button clicked."); hideEditPunchModal(); });

    const btnShowReport = document.getElementById('btnExceptionReport');
    const notificationBar = document.getElementById('pageNotification');

    if (notificationBar && notificationBar.textContent.trim() !== '' && notificationBar.style.display !== 'none') {
        setTimeout(() => { if(notificationBar){ notificationBar.style.transition = 'opacity 0.5s ease-out'; notificationBar.style.opacity = '0'; setTimeout(() => { notificationBar.style.display = 'none'; }, 500); } }, 7000);
        if(typeof clearUrlParams === 'function') clearUrlParams(['message', 'error']);
    } else if(notificationBar) { notificationBar.style.display = 'none'; }

    if (btnPrintPayroll) {
        console.log("[Payroll.js] Attaching click listener to btnPrintPayroll.");
        btnPrintPayroll.addEventListener('click', printPayrollSummary);
    } else { console.warn("[Payroll.js] #btnPrintPayroll button not found."); }

    if (btnPrintAllTimeCards) {
        console.log("[Payroll.js] Attaching click listener to btnPrintAllTimeCards. currentTenantIdJs:", (typeof currentTenantIdJs !== 'undefined' ? currentTenantIdJs : "UNDEFINED"));
        if (typeof currentTenantIdJs !== 'undefined') { // Ensure currentTenantIdJs is defined (from JSP)
            btnPrintAllTimeCards.addEventListener('click', function() {
                console.log("[Payroll.js] Print All Time Cards button clicked. Tenant ID:", currentTenantIdJs);
                if (typeof currentTenantIdJs !== 'number' || currentTenantIdJs <= 0) {
                    alert("Error: Tenant information is missing. Cannot proceed.");
                    return;
                }
                const servletUrl = `PrintTimecardsServlet?tenantId=${currentTenantIdJs}&filterType=all`;
                console.log("[Payroll.js] Navigating to PrintTimecardsServlet URL:", servletUrl);
                window.open(servletUrl, '_blank');
            });
        } else {
             console.warn("[Payroll.js] btnPrintAllTimeCards found, but currentTenantIdJs is not defined on the page.");
             btnPrintAllTimeCards.disabled = true; // Disable if tenantId isn't available
        }
    } else { console.warn("[Payroll.js] #btnPrintAllTimeCards button not found."); }


    if (btnShowReport) {
        console.log("[Payroll.js] btnShowReport element FOUND. Initial disabled state:", btnShowReport.disabled);
        btnShowReport.addEventListener('click', refreshExceptionReport);
        console.log("[Payroll.js] Event listener for 'click' attached to btnShowReport.");
    } else {
        console.warn("[Payroll.js] btnShowReport element (ID: btnExceptionReport) NOT found.");
    }

    if (btnEditReportRow) {
        btnEditReportRow.addEventListener('click', () => {
            console.log("[Payroll.js] Edit Exception Button clicked. currentExceptionData:", currentExceptionData);
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
        console.log("[Payroll.js] Attaching click listener to actualClosePayPeriodButton.");
        actualClosePayPeriodButton.addEventListener('click', function() {
            console.log("[Payroll.js] Close Pay Period button CLICKED. payPeriodEndDateJs:", (typeof payPeriodEndDateJs !== 'undefined' ? payPeriodEndDateJs : "UNDEFINED"));
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
                message = "The current pay period has not yet ended (Ends: " + payPeriodEndDateJs + ").\n\n" +
                          "Closing the pay period now will finalize all entries up to this moment. This is usually done only if you intend to change your pay period frequency or end dates.\n\n" +
                          "Standard closing procedures (OT calculation, archiving, accruals) will apply.\n\n" +
                          "Are you sure you want to close the pay period early?";
                confirmButtonText = "Proceed to Close Early";
            } else {
                title = "Confirm Close Pay Period";
                message = "Are you sure you want to close the current pay period (Ended: " + payPeriodEndDateJs + ")?\n\n" +
                          "This will:\n" +
                          "- Finalize and UPDATE Overtime calculations for all punches in this period.\n" +
                          "- ARCHIVE all punches for this period.\n" +
                          "- RUN ACCRUALS for eligible employees.\n" +
                          "- Log this payroll run to history.\n" +
                          "- Automatically set the next pay period dates in Settings.\n\n" +
                          "Please ensure all reports (especially Exception Report) and manual adjustments are complete.\n" +
                          "This action CANNOT be easily undone.";
                confirmButtonText = "Confirm Close Period";
            }
            if (confirmModalTitle) confirmModalTitle.textContent = title;
            if (confirmModalMessage) confirmModalMessage.innerHTML = message.replace(/\n/g, '<br>'); // Use innerHTML for <br>
            if (confirmModalOkBtn) {
                 confirmModalOkBtn.textContent = confirmButtonText;
                 confirmModalOkBtn.className = 'glossy-button text-red';
            }
            if(closePeriodConfirmModal && typeof showModal === 'function') {
                console.log("[Payroll.js] Showing Close Period Confirm Modal.");
                showModal(closePeriodConfirmModal);
            } else {
                console.warn("[Payroll.js] closePeriodConfirmModal or showModal function not found.");
            }
        });
    } else { console.warn("[Payroll.js] #btnClosePayPeriodActual button not found."); }

    if (reportTbody) {
        reportTbody.addEventListener('click', (event) => {
            // ... (row click logic as before) ...
        });
    }

    const pageErrorElement = document.getElementById('pageNotification');
    const actionsContainer = document.getElementById('payroll-actions-container');
    const dataIsReady = actionsContainer && (actionsContainer.style.display === '' || actionsContainer.style.display === 'grid' || window.getComputedStyle(actionsContainer).display !== 'none');

    console.log("[Payroll.js] Initial button state check. dataIsReady:", dataIsReady);

    if (dataIsReady) {
        const hasSettingsError = pageErrorElement && pageErrorElement.classList.contains('error-message') &&
                                 pageErrorElement.textContent.toLowerCase().includes("settings");
        console.log("[Payroll.js] hasSettingsError:", hasSettingsError);

        mainActionButtons.forEach(button => {
            if(button) {
                button.disabled = true; // Default to disabled
                console.log(`[Payroll.js] Initially disabling button: ${button.id}`);
            }
        });
        if (btnShowReport) {
            btnShowReport.disabled = hasSettingsError;
            if (!hasSettingsError) { console.log("[Payroll.js] Exception Report button is ENABLED. Other actions (mainActionButtons) disabled pending report."); }
            else { console.log("[Payroll.js] Exception Report button is DISABLED due to settings error."); }
        }
    } else {
        if (btnShowReport) { btnShowReport.disabled = true; console.log("[Payroll.js] Disabling btnShowReport because data is not ready.");}
        mainActionButtons.forEach(button => { if(button) button.disabled = true; });
        console.log("[Payroll.js] Payroll actions container is hidden or data not ready. All payroll action buttons disabled.");
    }

    if (reportModal && reportModal.querySelector('.report-modal-content') && reportModal.querySelector('.report-modal-content > h2')) { makeElementDraggable(reportModal.querySelector('.report-modal-content'), reportModal.querySelector('.report-modal-content > h2'));}
    if (editPunchModal && editPunchModal.querySelector('.modal-content') && editPunchModal.querySelector('.modal-content > h2')) {makeElementDraggable(editPunchModal.querySelector('.modal-content'), editPunchModal.querySelector('.modal-content > h2'));}
    const payrollTableElement = document.getElementById('payrollTable');
    if (payrollTableElement && typeof makeTableSortable === 'function') { makeTableSortable(payrollTableElement, { columnIndex: 2, ascending: true });}
    else {
        if(!payrollTableElement) console.warn("[Payroll.js] Payroll table #payrollTable not found for sorting.");
        if(typeof makeTableSortable !== 'function') console.warn("[Payroll.js] makeTableSortable from commonUtils.js not found. Sorting disabled.");
    }

    console.log("--- Payroll Page DOMContentLoaded END (v30) ---");
});