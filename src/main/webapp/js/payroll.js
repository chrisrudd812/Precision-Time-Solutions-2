// js/payroll.js

// --- Global variables for selected report row state ---
let selectedReportPunchId = null;
let selectedReportRowElement = null;
let currentExceptionData = {}; // Store data of the selected exception row

// --- Global variables for Notification Modal ---
let notificationModal = null;
let notificationModalContent = null;
let notificationMessage = null;
let notificationCloseButton = null;
let notificationOkButton = null;
let focusElementIdOnClose = null; // Track element to focus or action on close

// --- Global references for elements needed by global functions ---
let reportModal, reportTbody, btnEditReportRow, editPunchModal, editPunchForm, btnClosePayPeriod, btnPrintPayroll;
let mainActionButtons = []; // Array to hold main action buttons

// --- Global Utility Functions ---
function clearUrlParams() {
    try {
        const cleanUrl = window.location.origin + window.location.pathname;
        window.history.replaceState({}, document.title, cleanUrl);
    } catch(e) {
        console.error("Error clearing URL params:", e);
    }
}

// Parses common time formats (including 12hr AM/PM) to HH:mm:ss (24hr)
function parseTimeTo24Hour(timeStr12hr) {
    // Return empty string if input is invalid, empty, or placeholder
    if (!timeStr12hr || typeof timeStr12hr !== 'string' || timeStr12hr.trim() === '' || timeStr12hr.includes('Missing Punch')) return '';
    timeStr12hr = timeStr12hr.trim();
    try {
        // Check if already potentially 24hr HH:mm or HH:mm:ss
        if (/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(timeStr12hr)) {
             // If HH:mm, add :00 - assumes seconds are desired
             if (timeStr12hr.length === 5) return timeStr12hr + ':00';
             return timeStr12hr; // Assume HH:mm:ss is correct
        }
        // Try parsing HH:MM:SS AM/PM format
        const parts = timeStr12hr.match(/(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?\s*(AM|PM)/i);
        if (!parts) {
            console.warn("Could not parse 12hr time string:", timeStr12hr);
            return ''; // Return empty if format is unrecognized
        }
        let h = parseInt(parts[1], 10);
        const m = parts[2].padStart(2,'0');
        const s = parts[3] ? parts[3].padStart(2,'0') : '00'; // Default seconds to 00
        const a = parts[4].toUpperCase();
        if (isNaN(h) || h < 1 || h > 12) return ''; // Invalid hour
        if (a === 'PM' && h !== 12) h += 12;
        else if (a === 'AM' && h === 12) h = 0; // Handle midnight
        const hs = String(h).padStart(2, '0');
        return `${hs}:${m}:${s}`;
    } catch (e) {
        console.error("Error parsing time string:", timeStr12hr, e);
        return ''; // Return empty on error
    }
}
// --- End Utility Functions ---


// --- Notification Modal Functions ---
function showNotification(message, isError, focusId = null) {
    if (!notificationModal || !notificationMessage || !notificationModalContent) { console.error("Notification modal elements not initialized!"); alert(message); return; }
    notificationMessage.innerHTML = message; // Use innerHTML for potential <br>
    if(isError){ notificationModalContent.classList.add('error'); } else { notificationModalContent.classList.remove('error'); }
    notificationModal.classList.add('modal-visible');
    focusElementIdOnClose = focusId; // Remember next action/focus
    if(notificationOkButton){ notificationOkButton.style.display='block'; notificationOkButton.style.margin='0 auto'; }
    console.log("showNotification: Attempted visible.");
}

function hideNotification() {
    if(notificationModal){
        notificationModal.classList.remove('modal-visible');
        const focusTargetId = focusElementIdOnClose;
        focusElementIdOnClose = null;
        if(focusTargetId && focusTargetId !== "REFRESH_REPORT"){
            const elementToFocus = document.getElementById(focusTargetId);
            if(elementToFocus){ setTimeout(()=> { try { elementToFocus.focus(); } catch(e){} }, 100); }
            else{ console.warn("Focus target not found:", focusTargetId); }
        }
        console.log("Notification modal hidden.");
    }
}

// Handles OK click on the notification modal
function handleNotificationOk() {
    const shouldRefreshReport = (focusElementIdOnClose === "REFRESH_REPORT"); // Check if refresh needed
    hideNotification(); // Hide modal first
    if(shouldRefreshReport){
        console.log("Refreshing exception report after notification OK.");
        refreshExceptionReport(); // Trigger refresh if requested
    }
    // Clear the flag AFTER checking it
    if (focusElementIdOnClose === "REFRESH_REPORT") {
         focusElementIdOnClose = null;
    }
}
// --- End Notification Modal Functions ---


// --- Report Modal Functions ---
function showReportModal() {
    if(reportModal) reportModal.classList.add('modal-visible');
    else console.error("Report modal element (#exceptionReportModal) not found");
}

function hideReportModal() {
    if(reportModal){
        reportModal.classList.remove('modal-visible');
        if(selectedReportRowElement){selectedReportRowElement.classList.remove('selected');}
        if(btnEditReportRow)btnEditReportRow.disabled=true;
        selectedReportRowElement=null; selectedReportPunchId=null; currentExceptionData={};
    } else console.error("Report modal element (#exceptionReportModal) not found");
}

// Fetches and displays the exception report
function refreshExceptionReport() {
    console.log("Refreshing Exception Report...");
    if (!reportTbody || !reportModal || !mainActionButtons || !btnClosePayPeriod || !btnEditReportRow) {
        console.error("Cannot refresh report, required elements refs missing.");
        alert("Error: Cannot initialize report refresh. Required page elements missing.");
        return;
    }
    // Clear selection, disable edit button, show loading
    if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
    selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
    btnEditReportRow.disabled = true;

    reportTbody.innerHTML='<tr><td colspan="6" style="text-align:center; padding: 20px; font-style: italic;">Loading exceptions...</td></tr>';
    mainActionButtons.forEach(b=>{if(b)b.disabled=true;}); // Disable main actions
    showReportModal(); // Show modal while loading

    fetch('PayrollServlet', {
        method:'POST',
        headers:{'Content-Type':'application/x-www-form-urlencoded'},
        cache:'no-store',
        body: new URLSearchParams({'action':'exceptionReport'})
    })
    .then(response => { // Check response status first
        if (!response.ok) {
            return response.text().then(text => { // Try to get error text from server
                throw new Error(text || `Server error fetching exceptions: ${response.status}`);
            });
        }
        return response.text(); // Expecting HTML snippet or "NO_EXCEPTIONS"
    })
    .then(htmlResponseOrSignal => {
        const responseText = htmlResponseOrSignal.trim().toUpperCase();
        if (responseText === "NO_EXCEPTIONS") {
            console.log("No exceptions found.");
            hideReportModal(); // Close the report modal
            const instructionsPara = document.querySelector('p.instructions');
            if(instructionsPara) instructionsPara.style.display = 'none'; // Hide instructions if no exceptions
            // Show success message in the *notification* modal
            showNotification("Good News! - No Exceptions found.<br>You can now Export, Print, or Close the Pay Period.", false, 'btnClosePayPeriod'); // Focus close button after OK
            // Re-enable main payroll actions
            mainActionButtons.forEach(button => { if(button) button.disabled = false; });
            console.log("Main payroll buttons enabled.");
        } else if (htmlResponseOrSignal.includes("report-error-row") || !htmlResponseOrSignal.includes("<tr")) {
             // Check if servlet sent back an error row OR if response is not valid table rows
            console.error("Server returned an error row or invalid data for exception report.");
            if(reportTbody) reportTbody.innerHTML='<tr><td colspan="6" class="report-error-row">Error loading exception data from server.</td></tr>';
            // Keep main buttons disabled
            mainActionButtons.forEach(button => { if(button) button.disabled = true; });
        } else {
            console.log("Exceptions found and loaded.");
            if(reportTbody){
                reportTbody.innerHTML = htmlResponseOrSignal; // Populate table
                highlightEmptyCellsAndPlaceholders(); // Apply styling to missing data
            }
            // Keep main buttons disabled, Edit button disabled until row selected
            mainActionButtons.forEach(button => { if(button) button.disabled = true; });
            if(btnEditReportRow) btnEditReportRow.disabled = true;
        }
     })
     .catch(error => {
        console.error('Error fetching exception report:', error);
        hideReportModal(); // Hide modal on error
        // Keep main buttons disabled
        mainActionButtons.forEach(button => { if(button) button.disabled = true; });
        // Show error in notification modal
        showNotification("Error loading exception report: " + error.message, true);
    });
 }

 // Styles empty/placeholder cells in exception report
function highlightEmptyCellsAndPlaceholders() {
    const tbody = document.getElementById('exceptionReportTbody'); if(!tbody)return;
    const rows = tbody.getElementsByTagName('tr');
    for(let r=0; r<rows.length; r++){ const cells = rows[r].cells; if(cells.length > 5){ for(let i=3; i<=5; i++){ const cell = cells[i]; if(cell){ cell.classList.remove('empty-cell'); const text = cell.textContent?.trim() ?? ''; if(text === '' || text.includes('Missing Punch')){ cell.classList.add('empty-cell'); } } } } }
}
// --- End Report Modal Functions ---


// --- Edit Punch Modal Functions ---
function hideEditPunchModal() {
    if(editPunchModal) editPunchModal.classList.remove('modal-visible');
    // Deselect report row when closing edit modal
    if(selectedReportRowElement){ selectedReportRowElement.classList.remove('selected'); selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {}; if(btnEditReportRow)btnEditReportRow.disabled = true; }
}

// Populates the edit modal - Includes FOCUS FIX
function populateEditModal(data) {
    if(!editPunchModal || !data){ console.error("Cannot populate edit modal: Missing modal reference or data object."); return; }

    // Get elements within the modal
    const nameDisplay = document.getElementById('editPunchEmployeeName');
    const scheduleDisplay = document.getElementById('editPunchScheduleInfo');
    const punchIdField = editPunchModal.querySelector('#editPunchIdField');
    const employeeIdField = editPunchModal.querySelector('#editPunchEmployeeIdField');
    const dateField = editPunchModal.querySelector('#editDate');
    const inTimeField = editPunchModal.querySelector('#editInTime');
    const outTimeField = editPunchModal.querySelector('#editOutTime');
    const typeField = editPunchModal.querySelector('#editPunchType');
    const userTimeZoneField = editPunchModal.querySelector('#editUserTimeZone');

    // Populate Header Info
    if(nameDisplay){ nameDisplay.textContent = data.employeeName || `EID: ${data.eid}`; }
    if(scheduleDisplay){ let scheduleText = `Schedule: ${data.scheduleName || 'N/A'}`; if(data.shiftStart && data.shiftEnd && data.shiftStart !== 'null' && data.shiftEnd !== 'null'){ scheduleText += ` (${data.shiftStart} - ${data.shiftEnd})`; } scheduleDisplay.textContent = scheduleText; }

    // Format and Populate Form Fields
    let formattedDate = '';
    if(data.date){ // Expecting MM/DD/YYYY from table cell
        try { const parts = data.date.split('/'); if(parts.length === 3){ formattedDate = `${parts[2]}-${parts[0].padStart(2,'0')}-${parts[1].padStart(2,'0')}`; } } catch(e){ console.error("Error formatting date from report:", data.date, e); }
    }
    let formattedInTime = parseTimeTo24Hour(data.inTime);
    let formattedOutTime = parseTimeTo24Hour(data.outTime);

    if(punchIdField) punchIdField.value = data.punchId || ''; else console.error("Edit Modal: #editPunchIdField missing!");
    if(employeeIdField) employeeIdField.value = data.eid || ''; else console.error("Edit Modal: #editEmployeeIdField missing!");
    if(dateField) dateField.value = formattedDate; else console.error("Edit Modal: #editDate missing!");
    if(inTimeField) inTimeField.value = formattedInTime; else console.error("Edit Modal: #editInTime missing!");
    if(outTimeField) outTimeField.value = formattedOutTime; else console.error("Edit Modal: #editOutTime missing!");
    if(typeField) typeField.value = 'Supervisor Override'; else console.error("Edit Modal: #editPunchType missing!"); // Default type
    if(userTimeZoneField) { try { userTimeZoneField.value = Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC'; } catch(e) { userTimeZoneField.value = 'UTC';} }

    // --- UPDATED FOCUS LOGIC (Targets OUT field only) ---
    setTimeout(()=>{
        // Find the OUT time field by its ID
        const outTimeFieldElement = document.getElementById('editOutTime');

        // If found, try to focus and select it
        if (outTimeFieldElement) {
            try {
                outTimeFieldElement.focus();
                // Attempt to select the content for easier editing
                if (typeof outTimeFieldElement.select === 'function') {
                    outTimeFieldElement.select();
                }
                console.log("Focused OUT time field (#editOutTime).");
            } catch (e) {
                // Log error if focusing fails
                console.error("Error focusing/selecting OUT time field:", e);
            }
        } else {
            // Log a warning if the field couldn't be found
            console.warn("OUT time field (#editOutTime) not found in modal for focusing.");
            // Optionally add fallback focus here if needed, e.g., to date field
            // const dateField = document.getElementById('editDate');
            // if (dateField) try { dateField.focus(); } catch(e) {}
        }
    }, 150); // Delay slightly to ensure modal is visible
}

// Fetches schedule info, then populates and shows the edit modal
function prepareAndShowEditModal(exceptionData) {
    console.log("Prepare Edit Modal EID:", exceptionData?.eid);
    if (!exceptionData || !exceptionData.eid){ alert("Cannot edit: Missing EID."); return; }
    const eid = exceptionData.eid;
    // Construct URL safely, ensure context path is correct if app not at root
    const contextPath = window.location.pathname.substring(0, window.location.pathname.indexOf("/",2)); // Basic context path detection
    const url = `${contextPath}/EmployeeInfoServlet?action=getScheduleInfo&eid=${eid}`;
    console.log("Fetching schedule info:", url);

    fetch(url,{cache:'no-store'})
        .then(response => {
            console.log("Fetch schedule status:", response.status);
            if (!response.ok) { return response.text().then(text => { throw new Error(`Failed to fetch schedule info. Status: ${response.status}. ${text}`); }); }
            return response.json();
        })
        .then(scheduleData => {
            console.log("Schedule Data received:", scheduleData);
            if (scheduleData.success) {
                const finalData = { ...exceptionData, ...scheduleData }; // Merge exception data with fetched schedule data
                populateEditModal(finalData); // Populate the modal
                if(editPunchModal){ editPunchModal.classList.add('modal-visible'); } // Show the modal
                else { console.error("Cannot show edit modal - reference missing."); }
            } else { throw new Error(scheduleData.message || 'Failed to retrieve valid schedule data from server.'); }
        })
        .catch(error => {
            console.error("Error preparing edit modal:", error);
            showNotification("Could not load details required for editing:<br>" + error.message, true);
        });
}

/**
 * Handles Edit Form Submit using fetch.
 * On success, hides modal and REFRESHES exception report via notification callback.
 */
function handleEditFormSubmit(event) {
    console.log("handleEditFormSubmit triggered.");
    event.preventDefault();
    if (!editPunchForm) { console.error("Edit form reference missing!"); return; }

    const formData = new FormData(editPunchForm);
    const formBody = new URLSearchParams(formData);
    // Add user timezone if possible
    try { const userDetectedTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone; if(userDetectedTimeZone) formBody.append('userTimeZone', userDetectedTimeZone); } catch (e) { console.error("Error detecting timezone:", e); }

    console.log("Submitting Edited Punch Data:", formBody.toString());

    fetch('AddEditAndDeletePunchesServlet', { // Ensure servlet mapping is correct
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
        body: formBody
    })
    .then(response => {
        if (!response.ok) { return response.json().catch(()=>null).then(eD => { throw new Error(eD?.error || `Save failed. Server responded with status: ${response.status}`); }); }
        return response.json(); // Expecting JSON back
    })
    .then(data => {
        console.log("Edit Save Response Data:", data);
        if (data.success) {
            hideEditPunchModal(); // Close edit modal
            // Show success notification; the OK handler will trigger refresh
            showNotification(data.message || "Punch updated successfully!", false, "REFRESH_REPORT");
        } else {
            console.error("Save failed:", data.error);
            alert("Error saving punch: " + (data.error || "Unknown server error"));
        }
    })
    .catch(error => {
        console.error("Error saving punch fetch:", error);
        alert("Error saving punch: " + error.message);
    });
 }
// --- End Edit Punch Modal Functions ---


// --- Run Code After DOM Loaded ---
document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Payroll Page DOMContentLoaded START ---");

    // Assign Global References
    console.log("Assigning global element references...");
    reportModal=document.getElementById('exceptionReportModal'); editPunchModal=document.getElementById('editPunchModal'); reportTbody=document.getElementById('exceptionReportTbody'); btnEditReportRow=document.getElementById('editExceptionButton'); editPunchForm=document.getElementById('editPunchForm'); btnClosePayPeriod=document.getElementById('btnClosePayPeriod'); btnPrintPayroll=document.getElementById('btnPrintPayroll'); const btnExportPayroll = document.getElementById('btnExportPayroll');
    // Add buttons to array only if they exist
    if(btnExportPayroll) mainActionButtons.push(btnExportPayroll); if(btnPrintPayroll) mainActionButtons.push(btnPrintPayroll); if(btnClosePayPeriod) mainActionButtons.push(btnClosePayPeriod);
    notificationModal=document.getElementById("notificationModal"); notificationModalContent=notificationModal?notificationModal.querySelector('.modal-content'):null; notificationMessage=document.getElementById("notificationMessage"); notificationOkButton=document.getElementById("okButton"); notificationCloseButton = document.getElementById("closeNotificationModal");
    console.log("Finished assigning global references.");

    // Local References for attaching listeners
    const btnShowReport = document.getElementById('btnExceptionReport');
    const notificationBar = document.getElementById('notification-bar');
    const btnCloseReportModal = document.getElementById('closeExceptionReportModal');
    const btnCloseReportButton = document.getElementById('closeExceptionReportButton');
    const closeEditPunchModalButton = document.getElementById('closeEditPunchModal');
    const cancelEditPunchButton = document.getElementById('cancelEditPunch');

    // Notification Bar Handling
    if (notificationBar && notificationBar.textContent.trim() !== '') { setTimeout(() => { if(notificationBar){ notificationBar.style.transition = 'opacity 0.5s ease-out'; notificationBar.style.opacity = '0'; setTimeout(() => { notificationBar.style.display = 'none'; }, 500); } }, 5000); clearUrlParams(); } else if(notificationBar) { notificationBar.style.display = 'none'; }

    // Attach Event Listeners
    console.log("Attempting to attach event listeners...");

    // Print button
    if (btnPrintPayroll) { console.log("Attaching click listener to #btnPrintPayroll"); btnPrintPayroll.addEventListener('click', () => { const t=document.getElementById('payrollTable'); const h = document.querySelector('.parent-container > h2'); if (!t) { alert("Error: Payroll table not found."); return; } let pH = `<!DOCTYPE html><html><head><title>Payroll Print</title><link rel="stylesheet" href="css/print-payroll.css"></head><body><h1>Payroll Report</h1>`; if(h) pH += h.outerHTML; pH += t.outerHTML; pH += `</body></html>`; const wW=800; const wH=600; const l=(window.screen.width/2)-(wW/2); const top=(window.screen.height/2)-(wH/2); const pS=`width=${wW},height=${wH},top=${top},left=${l},scrollbars=yes,resizable=yes`; const pW=window.open('', '_blank', pS); if (!pW) { alert("Could not open print window."); return; } pW.document.write(pH); pW.document.close(); pW.focus(); setTimeout(() => { try { pW.print(); } catch(e) { console.error("Print failed", e); pW.close();} }, 500); }); } else { console.error("#btnPrintPayroll not found!"); }

    // Exception Report button
    if (btnShowReport) { console.log("Attaching click listener to #btnExceptionReport"); btnShowReport.addEventListener('click', refreshExceptionReport); } else { console.error("#btnExceptionReport could not be found!"); }

    // "Fix Missing Punches" button in Exception Report Modal
    if (btnEditReportRow) { console.log("Attaching click listener to #editExceptionButton"); btnEditReportRow.addEventListener('click', () => { console.log("Fix Missing Punches CLICKED"); if (!selectedReportRowElement || !selectedReportPunchId || !currentExceptionData?.eid) { alert("Please select a row with valid data from the report first."); return; } prepareAndShowEditModal(currentExceptionData); }); } else { console.error("#editExceptionButton not found!"); }

    // Edit Punch Form submission (modal)
    if (editPunchForm) { console.log("Attaching submit listener to #editPunchForm"); editPunchForm.addEventListener('submit', handleEditFormSubmit); } else { console.error("#editPunchForm not found!"); }

    // Close Pay Period form uses onsubmit in HTML

    // Modal Close Buttons
    if (notificationCloseButton) notificationCloseButton.addEventListener('click', hideNotification); else console.error("#closeNotificationModal not found!");
    if (notificationOkButton) notificationOkButton.addEventListener('click', handleNotificationOk); else console.error("#okButton not found!");
    if (btnCloseReportModal) btnCloseReportModal.addEventListener('click', hideReportModal); else console.error("#closeExceptionReportModal not found!");
    if (btnCloseReportButton) btnCloseReportButton.addEventListener('click', hideReportModal); else console.error("#closeExceptionReportButton not found!");
    if (closeEditPunchModalButton) closeEditPunchModalButton.addEventListener('click', hideEditPunchModal); else console.error("#closeEditPunchModal not found!");
    if (cancelEditPunchButton) cancelEditPunchButton.addEventListener('click', hideEditPunchModal); else console.error("#cancelEditPunch not found!");

    // Exception Report Row Click Delegation
    if (reportTbody) {
        console.log("Attaching click listener to #exceptionReportTbody");
        reportTbody.addEventListener('click', (event) => {
            let clickedRow = event.target.closest('tr');
            if (!clickedRow || !reportTbody.contains(clickedRow) || !clickedRow.dataset.punchId) return;
            let currentlySelected = reportTbody.querySelector('tr.selected');
            if (currentlySelected && currentlySelected !== clickedRow) { currentlySelected.classList.remove('selected'); }
            clickedRow.classList.toggle('selected');
            if (clickedRow.classList.contains('selected')) {
                selectedReportRowElement = clickedRow; selectedReportPunchId = clickedRow.dataset.punchId; const cells = clickedRow.cells;
                // Extract data needed for populating edit form AND displaying info in modal header
                currentExceptionData = { punchId: selectedReportPunchId, eid: cells[0]?.textContent ?? '', employeeName: `${cells[1]?.textContent ?? ''} ${cells[2]?.textContent ?? ''}`.trim(), date: cells[3]?.textContent ?? '', inTime: cells[4]?.textContent ?? '', outTime: cells[5]?.textContent ?? '' };
                if (btnEditReportRow) btnEditReportRow.disabled = false; // Enable edit button
                console.log("Selected exception row. Punch ID:", selectedReportPunchId, "Data:", currentExceptionData);
            } else {
                selectedReportRowElement = null; selectedReportPunchId = null; currentExceptionData = {};
                if (btnEditReportRow) btnEditReportRow.disabled = true; // Disable edit button
                console.log("Exception row deselected.");
            }
        });
    } else { console.error("#exceptionReportTbody not found!");}

    // Handle Initial URL parameters for notifications (e.g., after closing period)
    const urlParams = new URLSearchParams(window.location.search); const initialError = urlParams.get('error'); const initialMessage = urlParams.get('message');
    if (notificationModal && initialMessage) { showNotification(initialMessage, false); clearUrlParams(); }
    else if (notificationModal && initialError){ showNotification(initialError, true); clearUrlParams(); }

    // Initial state: Assume payroll can't be processed until exceptions checked
    console.log("Setting initial state: Disabling main payroll action buttons until exceptions checked.");
    mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });

    console.log("--- Payroll Page DOMContentLoaded END ---");
}); // --- End DOMContentLoaded ---