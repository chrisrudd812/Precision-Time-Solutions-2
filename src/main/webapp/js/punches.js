// js/punches.js - v.ModalLogicUpdate_Rev2_FullDiag

// --- Global Helper Functions ---
function showToast(message, type = 'info') {
    console.log(`[PUNCHES.JS] showToast called: [${type}] ${message}`);
    let container = document.querySelector('.toast-notification-container');
    if (!container) {
        container = document.createElement('div');
        container.className = 'toast-notification-container';
        document.body.appendChild(container);
    }
    const toast = document.createElement('div');
    toast.className = `toast-notification ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    requestAnimationFrame(() => { toast.classList.add('show'); });
    setTimeout(() => {
        toast.classList.remove('show');
        const transitionEndHandler = () => {
            if (toast.parentNode === container) container.removeChild(toast);
            toast.removeEventListener('transitionend', transitionEndHandler);
        };
        toast.addEventListener('transitionend', transitionEndHandler);
        setTimeout(() => { if (toast.parentNode === container) container.removeChild(toast); }, 500);
    }, 3000);
}

function isHoursOnlyType(punchType) {
    const types = ['vacation', 'sick', 'personal', 'holiday', 'bereavement', 'other'];
    return types.includes(punchType?.toLowerCase().trim() || '');
}

function formatDateToYyyyMmDd(dateString) {
    if (!dateString) { console.warn("[PUNCHES.JS] formatDateToYyyyMmDd: input is null/empty"); return ''; }
    try {
        let date;
        if (dateString.match(/^\d{4}-\d{2}-\d{2}$/)) {
            const [year, month, day] = dateString.split('-').map(Number);
            date = new Date(Date.UTC(year, month - 1, day));
        } else if (dateString.includes('/')) {
            const parts = dateString.split('/');
            if (parts.length === 3) {
                 date = new Date(Date.UTC(parts[2], parseInt(parts[0], 10) - 1, parseInt(parts[1], 10)));
            } else { console.warn("[PUNCHES.JS] formatDateToYyyyMmDd: MM/DD/YYYY split failed for", dateString); return ''; }
        } else if (!isNaN(new Date(dateString).getTime())) {
             date = new Date(dateString);
        } else { console.warn("[PUNCHES.JS] formatDateToYyyyMmDd: Unrecognized date format", dateString); return ''; }
        if (isNaN(date.getTime())) { console.warn("[PUNCHES.JS] formatDateToYyyyMmDd: Invalid date object from", dateString); return '';}
        const year = date.getUTCFullYear();
        const month = String(date.getUTCMonth() + 1).padStart(2, '0');
        const dayNum = String(date.getUTCDate()).padStart(2, '0');
        return `${year}-${month}-${dayNum}`;
    } catch (e) { console.error("[PUNCHES.JS] Error in formatDateToYyyyMmDd for input:", dateString, e); return ''; }
}

function parseTimeTo24Hour(timeStr) {
    if (!timeStr || typeof timeStr !== 'string' || timeStr.trim() === "" || timeStr.toLowerCase() === "n/a" || String(timeStr).toLowerCase().includes('missing')) { return ""; }
    const timeParts = timeStr.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)?/i);
    if (!timeParts) {
        if (timeStr.match(/^\d{2}:\d{2}(:\d{2})?$/)) {
            return timeStr.length === 5 ? timeStr + ":00" : timeStr;
        }
        console.warn("[PUNCHES.JS] Invalid time format for parseTimeTo24Hour:", timeStr); return "";
    }
    let hours = parseInt(timeParts[1], 10);
    const minutes = parseInt(timeParts[2], 10);
    const seconds = timeParts[3] ? parseInt(timeParts[3], 10) : 0;
    const ampm = timeParts[4] ? timeParts[4].toUpperCase() : null;
    if (ampm) {
        if (hours === 12) { hours = (ampm === "AM") ? 0 : 12; }
        else if (ampm === "PM" && hours < 12) { hours += 12; }
    }
    if (isNaN(hours) || isNaN(minutes) || isNaN(seconds) || hours < 0 || hours > 23 || minutes < 0 || minutes > 59 || seconds < 0 || seconds > 59) {
        console.error("[PUNCHES.JS] Parsed time values out of range for input:", timeStr); return "";
    }
    return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function reloadPageWithEid(message, messageType = 'success') {
    console.log("[PUNCHES.JS] reloadPageWithEid called. Message:", message, "Type:", messageType);
    const employeeSelect = document.getElementById('employeeSelectPunches');
    const currentEid = employeeSelect ? employeeSelect.value : (typeof window.SELECTED_EID_ON_LOAD !== 'undefined' ? window.SELECTED_EID_ON_LOAD : null);
    let url = 'punches.jsp';
    const params = new URLSearchParams();
    if (currentEid && currentEid !== "0") { params.append('eid', currentEid); }
    if (message) { params.append(messageType === 'success' ? 'message' : 'error', message); }
    const queryString = params.toString();
    if (queryString) { url += '?' + queryString; }
    console.log("[PUNCHES.JS] reloadPageWithEid - Redirecting to:", url);
    window.location.href = url;
}
window.punchesJsContext = window.punchesJsContext || {};

function submitModalForm(formElement, modalToCloseOnOutcome) {
    console.log("[PUNCHES.JS] submitModalForm called for form:", formElement.id);
    if (!formElement) { console.error("[PUNCHES.JS] submitModalForm ERROR: Form element is null."); return; }

    const formData = new FormData(formElement);
    const params = new URLSearchParams(); // Create new URLSearchParams

    // Iterate over FormData and append to URLSearchParams
    for (const pair of formData.entries()) {
        params.append(pair[0], pair[1]);
    }
    
    console.log("[PUNCHES.JS] submitModalForm - Initial FormData based params:", params.toString());


    if (formElement.id === "addHoursForm") {
        const addHoursPunchTypeContainer = document.getElementById('addHours_punchTypeContainer');
        if (addHoursPunchTypeContainer && addHoursPunchTypeContainer.style.display === 'none') {
            console.log("[PUNCHES.JS] submitModalForm - AddHoursForm is for Timed Punch, ensuring type is Supervisor Override.");
            params.set('punchType', 'Supervisor Override');
        }
    }
    if (formElement.id === "editPunchForm") {
        const editPunch_punchTypeContainer = document.getElementById('editPunch_punchTypeContainer');
        if (editPunch_punchTypeContainer && editPunch_punchTypeContainer.style.display === 'none') {
            console.log("[PUNCHES.JS] submitModalForm - EditPunchForm is for Timed Punch edit, ensuring type is Supervisor Override.");
            params.set('editPunchType', 'Supervisor Override');
        }
    }
     console.log("[PUNCHES.JS] submitModalForm - Params after potential type override:", params.toString());


    if (typeof window.EFFECTIVE_TIME_ZONE_ID === 'string' && window.EFFECTIVE_TIME_ZONE_ID) {
        params.append('userTimeZone', window.EFFECTIVE_TIME_ZONE_ID);
    } else {
        console.warn("[PUNCHES.JS] submitModalForm - EFFECTIVE_TIME_ZONE_ID not found on window.");
    }

    const submitButton = formElement.querySelector('button[type="submit"]');
    if (submitButton) submitButton.disabled = true;
    const servletActionUrl = formElement.getAttribute('action');
    console.log("[PUNCHES.JS] submitModalForm - Submitting to:", servletActionUrl, "with params:", params.toString());


    fetch(servletActionUrl, { method: 'POST', body: params })
    .then(response => {
        console.log("[PUNCHES.JS] submitModalForm - Fetch response status:", response.status);
        if (!response.ok) {
            return response.text().then(text => {
                console.error("[PUNCHES.JS] submitModalForm - Server error response text:", text);
                let serverErrorMessage = "No details from server.";
                try { const errorData = JSON.parse(text); serverErrorMessage = errorData.error || text; }
                catch (e) { serverErrorMessage = text || serverErrorMessage; }
                throw new Error(`Server error ${response.status}: ${serverErrorMessage}`);
            });
        }
        return response.json().catch(async err => {
             const rawText = await response.clone().text().catch(() => "Could not get raw text for JSON parse error.");
             console.error("[PUNCHES.JS] submitModalForm - Error parsing server JSON response:", err, "Raw response text:", rawText);
             throw new Error("Invalid JSON from server. Raw text: " + rawText.substring(0, 500)); // Limit raw text length
        });
    })
    .then(data => {
        console.log("[PUNCHES.JS] submitModalForm - Received data from servlet:", data);
        const messageToDisplay = data.message || (data.success ? "Operation Successful!" : (data.error || "Operation Failed!"));
        console.log("[PUNCHES.JS] submitModalForm - data.success:", data.success, "Message to display:", messageToDisplay);

        if (data.success) {
            console.log("[PUNCHES.JS] submitModalForm - data.success is TRUE. Closing modal and attempting refresh.");
            if (modalToCloseOnOutcome && typeof hideModal === 'function') {
                console.log("[PUNCHES.JS] submitModalForm - Calling hideModal for:", modalToCloseOnOutcome.id);
                hideModal(modalToCloseOnOutcome);
            } else if (modalToCloseOnOutcome) {
                 console.log("[PUNCHES.JS] submitModalForm - Fallback hide for:", modalToCloseOnOutcome.id);
                 modalToCloseOnOutcome.classList.remove('modal-visible');
            }
            if (window.punchesJsContext && typeof window.punchesJsContext.deselectRowAndRefreshGlobally === 'function') {
                 console.log("[PUNCHES.JS] submitModalForm - Calling deselectRowAndRefreshGlobally.");
                 window.punchesJsContext.deselectRowAndRefreshGlobally(messageToDisplay, 'success');
            } else {
                 console.warn("[PUNCHES.JS] submitModalForm - deselectRowAndRefreshGlobally not found, calling reloadPageWithEid directly.");
                 reloadPageWithEid(messageToDisplay, 'success');
            }
        } else {
            console.warn("[PUNCHES.JS] submitModalForm - data.success is FALSE or undefined. Showing toast with error:", messageToDisplay);
            showToast(messageToDisplay, 'error');
        }
    })
    .catch(error => {
        console.error('[PUNCHES.JS] submitModalForm ERROR for form ' + formElement.id + ':', error);
        showToast('Request error: ' + error.message, 'error');
    })
    .finally(() => {
        if (submitButton) {
            console.log("[PUNCHES.JS] submitModalForm - Re-enabling submit button for form:", formElement.id);
            submitButton.disabled = false;
        }
    });
}


document.addEventListener('DOMContentLoaded', function() {
    console.log("Punches.js Loaded (v.ModalLogicUpdate_Rev2_FullDiag)");
    window.punchesJsContext = window.punchesJsContext || {};

    const employeeSelect = document.getElementById('employeeSelectPunches');
    const addHoursBtn = document.getElementById('addHoursBtn');
    const addTimedPunchBtn = document.getElementById('addTimedPunchBtn');
    const editRowBtn = document.getElementById('editRowBtn');
    const deleteRowBtn = document.getElementById('deleteRowBtn');

    const addHoursModal = document.getElementById('addHoursModal');
    const addHoursForm = document.getElementById('addHoursForm');
    const addHours_actionInput = document.getElementById('addHours_actionInput');
    const addHoursFormEIDInput = document.getElementById('addHours_eidInput');
    const addHoursDateInput = document.getElementById('addHours_dateInput');
    const addHoursPunchTypeDropdown = document.getElementById('addHours_typeSelect');
    const addHours_punchTypeContainer = document.getElementById('addHours_punchTypeContainer');
    const addHoursModalTitle = addHoursModal ? addHoursModal.querySelector('h2#addHoursModalTitle') : null;
    const addHoursSubmitBtnText = document.getElementById('addHoursSubmitBtnText');
    const addHours_timeFieldsRowDiv = document.getElementById('addHours_timeFieldsRowDiv');
    const addHours_timeInInput = document.getElementById('addHours_timeInInput');
    const addHours_timeOutInput = document.getElementById('addHours_timeOutInput');
    const addHours_totalHoursDiv = document.getElementById('addHours_totalHoursDiv');
    const addHours_totalHoursInput = document.getElementById('addHours_totalHoursInput');

    const editPunchModal = document.getElementById('editPunchModal');
    const editPunchForm = document.getElementById('editPunchForm');
    const editPunchModalTitle = editPunchModal ? editPunchModal.querySelector('h2#editPunchModalTitleElem') : null;
    const editPunchIdInput = document.getElementById('editPunch_idInput');
    const editPunchEIDInput = document.getElementById('editPunch_eidInput');
    const editPunchDateInput = document.getElementById('editPunch_dateInput');
    const editPunchTimeInInput = document.getElementById('editPunch_timeInInput');
    const editPunchTimeOutInput = document.getElementById('editPunch_timeOutInput');
    const editPunchTypeSelect = document.getElementById('editPunch_typeSelect');
    const editPunch_punchTypeContainer = document.getElementById('editPunch_punchTypeContainer');
    const editPunchTimeFieldsRowDiv = document.getElementById('editPunch_timeFieldsRowDiv');
    const editPunchTotalHoursDiv = document.getElementById('editPunch_totalHoursDiv');
    const editPunchTotalHoursInput = document.getElementById('editPunch_totalHoursInput');

    const punchesTableBody = document.getElementById('punchesTable')?.getElementsByTagName('tbody')[0];
    let selectedRowElement = null;

    window.punchesJsContext.deselectRowAndRefreshGlobally = function(message, messageType) {
        console.log("[PUNCHES.JS] deselectRowAndRefreshGlobally called. Message:", message, "Type:", messageType);
        if (selectedRowElement) {
            selectedRowElement.classList.remove('selected-row');
            selectedRowElement = null;
        }
        if (editRowBtn) editRowBtn.disabled = true;
        if (deleteRowBtn) deleteRowBtn.disabled = true;
        reloadPageWithEid(message, messageType);
    };

    document.querySelectorAll('.modal .close, .modal .close-modal-btn').forEach(button => {
        console.log("[PUNCHES.JS] Attaching modal close listener to button:", button);
        button.addEventListener('click', function (event) {
            console.log("[PUNCHES.JS] Modal close button clicked:", this);
            event.preventDefault();
            const modal = this.closest('.modal');
            console.log("[PUNCHES.JS] Modal found by .closest():", modal);
            if (modal && typeof hideModal === 'function') {
                console.log("[PUNCHES.JS] Calling commonUtils.hideModal for modal:", modal.id);
                hideModal(modal);
            } else if (modal) {
                console.log("[PUNCHES.JS] Fallback: Removing .modal-visible from modal:", modal.id);
                modal.classList.remove('modal-visible');
            } else {
                console.warn("[PUNCHES.JS] Modal not found for close button:", this);
            }
        });
    });

    const ACCRUED_TYPES = ["Vacation", "Sick", "Personal", "Holiday", "Bereavement", "Other"];

    function populateAccruedTypesDropdown(selectElement, selectedValue) {
        // ... (Same as last correct version) ...
    }
    function setupModalForAccruedHours() {
        // ... (Same as last correct version, ensure required/disabled are set appropriately) ...
    }
    function setupModalForTimedPunch() {
        // ... (Same as last correct version, ensure required/disabled are set appropriately) ...
    }
    function configureEditModalFields(punchTypeFromRow) {
        // ... (Same as last correct version, ensure required/disabled are set appropriately) ...
    }

    // --- Full Function Definitions (from Rev2, with slight logging additions) ---
    function populateAccruedTypesDropdown(selectElement, selectedValue) {
        console.log("[PUNCHES.JS] populateAccruedTypesDropdown called for:", selectElement ? selectElement.id : "null element", "with value:", selectedValue);
        if (!selectElement) return;
        selectElement.innerHTML = '';
        ACCRUED_TYPES.forEach(type => {
            const option = new Option(type, type);
            selectElement.add(option);
        });
        if (selectedValue && ACCRUED_TYPES.includes(selectedValue)) {
            selectElement.value = selectedValue;
        } else if (selectedValue) { // If selectedValue is custom but valid for this context (e.g. "Other")
            const option = new Option(selectedValue, selectedValue, true, true);
            selectElement.add(option);
            selectElement.value = selectedValue;
        } else if (ACCRUED_TYPES.length > 0) {
            selectElement.value = ACCRUED_TYPES[0];
        }
    }

    function setupModalForAccruedHours() {
        console.log("[PUNCHES.JS] setupModalForAccruedHours called.");
        if (addHoursModalTitle) addHoursModalTitle.textContent = 'Add Accrued/Manual Hours';
        if (addHoursSubmitBtnText) addHoursSubmitBtnText.textContent = 'Add Hours';

        if (addHoursPunchTypeDropdown) {
            populateAccruedTypesDropdown(addHoursPunchTypeDropdown, 'Vacation');
            addHoursPunchTypeDropdown.required = true;
            addHoursPunchTypeDropdown.disabled = false;
        }
        if (addHours_punchTypeContainer) addHours_punchTypeContainer.style.display = 'block';

        if (addHours_timeFieldsRowDiv) addHours_timeFieldsRowDiv.style.display = 'none';
        if (addHours_timeInInput) { addHours_timeInInput.value = ''; addHours_timeInInput.required = false; }
        if (addHours_timeOutInput) { addHours_timeOutInput.value = ''; addHours_timeOutInput.required = false; }
        if (addHours_totalHoursDiv) addHours_totalHoursDiv.style.display = 'block';
        if (addHours_totalHoursInput) { addHours_totalHoursInput.value = ''; addHours_totalHoursInput.required = true; }
        if (addHours_actionInput) addHours_actionInput.value = 'addHours';
    }

    function setupModalForTimedPunch() {
        console.log("[PUNCHES.JS] setupModalForTimedPunch called.");
        if (addHoursModalTitle) addHoursModalTitle.textContent = 'Add Timed Punch';
        if (addHoursSubmitBtnText) addHoursSubmitBtnText.textContent = 'Add Punch';

        if (addHours_punchTypeContainer) addHours_punchTypeContainer.style.display = 'none';
        if (addHoursPunchTypeDropdown) {
            addHoursPunchTypeDropdown.required = false;
            addHoursPunchTypeDropdown.disabled = false; // Keep enabled; submitModalForm handles type value
        }

        if (addHours_timeFieldsRowDiv) addHours_timeFieldsRowDiv.style.display = 'flex';
        if (addHours_timeInInput) { addHours_timeInInput.value = ''; addHours_timeInInput.required = true; }
        if (addHours_timeOutInput) { addHours_timeOutInput.value = ''; addHours_timeOutInput.required = false; }
        if (addHours_totalHoursDiv) addHours_totalHoursDiv.style.display = 'none';
        if (addHours_totalHoursInput) { addHours_totalHoursInput.value = ''; addHours_totalHoursInput.required = false; }
        if (addHours_actionInput) addHours_actionInput.value = 'addHours';
    }

    function configureEditModalFields(punchTypeFromRow) {
        console.log("[PUNCHES.JS] configureEditModalFields called with punchTypeFromRow:", punchTypeFromRow);
        const isOriginalAccruedType = isHoursOnlyType(punchTypeFromRow);
        console.log("[PUNCHES.JS] configureEditModalFields - isOriginalAccruedType:", isOriginalAccruedType);

        if (isOriginalAccruedType) {
            if (editPunch_punchTypeContainer) editPunch_punchTypeContainer.style.display = 'block';
            if (editPunchTypeSelect) {
                populateAccruedTypesDropdown(editPunchTypeSelect, punchTypeFromRow);
                editPunchTypeSelect.required = true;
                editPunchTypeSelect.disabled = false;
            }
            if (editPunchTimeFieldsRowDiv) editPunchTimeFieldsRowDiv.style.display = 'none';
            if (editPunchTimeInInput) editPunchTimeInInput.required = false;
            if (editPunchTimeOutInput) editPunchTimeOutInput.required = false;
            if (editPunchTotalHoursDiv) editPunchTotalHoursDiv.style.display = 'block';
            if (editPunchTotalHoursInput) editPunchTotalHoursInput.required = true;
        } else { // Editing a timed punch
            if (editPunch_punchTypeContainer) editPunch_punchTypeContainer.style.display = 'none';
            if (editPunchTypeSelect) {
                editPunchTypeSelect.required = false;
                editPunchTypeSelect.disabled = false; // Keep enabled; submitModalForm handles type value
            }
            if (editPunchTimeFieldsRowDiv) editPunchTimeFieldsRowDiv.style.display = 'flex';
            if (editPunchTimeInInput) editPunchTimeInInput.required = true;
            if (editPunchTimeOutInput) editPunchTimeOutInput.required = false;
            if (editPunchTotalHoursDiv) editPunchTotalHoursDiv.style.display = 'none';
            if (editPunchTotalHoursInput) editPunchTotalHoursInput.required = false;
        }
    }

    if (employeeSelect) { employeeSelect.addEventListener('change', function() { document.getElementById('filterPunchesForm').submit(); }); }
    if (addHoursBtn && addHoursModal && addHoursForm) {
        addHoursBtn.addEventListener('click', function() {
            console.log("[PUNCHES.JS] Add Hours button clicked.");
            const currentEid = employeeSelect ? employeeSelect.value : null;
            if (!currentEid || currentEid === "0") { showToast("Please select an employee.", "error"); return; }
            addHoursForm.reset();
            if (addHoursFormEIDInput) addHoursFormEIDInput.value = currentEid;
            setupModalForAccruedHours();
            if (addHoursDateInput) { try { addHoursDateInput.valueAsDate = new Date(); } catch(e) { const today = new Date(); addHoursDateInput.value = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;}}
            if(typeof showModal === 'function') showModal(addHoursModal); else addHoursModal.classList.add('modal-visible');
            if (addHours_totalHoursInput) addHours_totalHoursInput.focus();
        });
    }
    if (addTimedPunchBtn && addHoursModal && addHoursForm) {
        addTimedPunchBtn.addEventListener('click', function() {
            console.log("[PUNCHES.JS] Add Timed Punch button clicked.");
            const currentEid = employeeSelect ? employeeSelect.value : null;
            if (!currentEid || currentEid === "0") { showToast("Please select an employee.", "error"); return; }
            addHoursForm.reset();
            if (addHoursFormEIDInput) addHoursFormEIDInput.value = currentEid;
            setupModalForTimedPunch();
            if (addHoursDateInput) { try { addHoursDateInput.valueAsDate = new Date(); } catch(e) { const today = new Date(); addHoursDateInput.value = `${today.getFullYear()}-${String(today.getMonth()+1).padStart(2,'0')}-${String(today.getDate()).padStart(2,'0')}`;}}
            if(typeof showModal === 'function') showModal(addHoursModal); else addHoursModal.classList.add('modal-visible');
            if (addHours_timeInInput) addHours_timeInInput.focus();
        });
    }

    if (punchesTableBody && editRowBtn && deleteRowBtn) {
        console.log("[PUNCHES.JS] Attaching click listener to punchesTableBody. Element:", punchesTableBody);
        punchesTableBody.addEventListener('click', function(event) {
            console.log("[PUNCHES.JS] punchesTableBody clicked. Event target:", event.target);
            let targetRow = event.target.closest('tr[data-punch-id]');
            console.log("[PUNCHES.JS] targetRow found by .closest('tr[data-punch-id]'):", targetRow);
            if (targetRow && punchesTableBody.contains(targetRow)) {
                console.log("[PUNCHES.JS] Valid targetRow. Current selectedRowElement:", selectedRowElement);
                if (selectedRowElement && selectedRowElement !== targetRow) {
                    selectedRowElement.classList.remove('selected-row');
                }
                targetRow.classList.toggle('selected-row');
                selectedRowElement = targetRow.classList.contains('selected-row') ? targetRow : null;
                console.log("[PUNCHES.JS] Updated selectedRowElement:", selectedRowElement);
                if(editRowBtn) editRowBtn.disabled = !selectedRowElement;
                if(deleteRowBtn) deleteRowBtn.disabled = !selectedRowElement;
            } else {
                console.log("[PUNCHES.JS] Click was not on a valid data row or row not in this table body.");
                if (selectedRowElement) { selectedRowElement.classList.remove('selected-row'); selectedRowElement = null; }
                if(editRowBtn) editRowBtn.disabled = true;
                if(deleteRowBtn) deleteRowBtn.disabled = true;
            }
        });
    } else {
        console.warn("[PUNCHES.JS] Could not attach click listener to punchesTableBody. Check if elements exist:");
        // ... (warnings for missing elements as before)
    }

    if (editRowBtn && editPunchModal && editPunchForm) {
        editRowBtn.addEventListener('click', function() {
            console.log("[PUNCHES.JS] Edit Row button clicked. selectedRowElement:", selectedRowElement);
            if (!selectedRowElement) { showToast("Please select a punch row to edit.", "info"); return; }
            const eidForForm = selectedRowElement.dataset.eid || (employeeSelect ? employeeSelect.value : null);
            if (!eidForForm || eidForForm === "0") { showToast("Employee context for edit is missing.", "error"); return; }

            if (editPunchModalTitle) editPunchModalTitle.textContent = 'Edit Punch Record';
            editPunchForm.reset();
            if(editPunchForm.querySelector('input[name="action"]')) editPunchForm.querySelector('input[name="action"]').value = 'editPunch';
            if(editPunchIdInput) editPunchIdInput.value = selectedRowElement.dataset.punchId;
            if(editPunchEIDInput) editPunchEIDInput.value = eidForForm;
            if(typeof window.EFFECTIVE_TIME_ZONE_ID === 'string' && document.getElementById('editPunch_userTimeZone')) document.getElementById('editPunch_userTimeZone').value = window.EFFECTIVE_TIME_ZONE_ID;


            const punchTypeFromRow = selectedRowElement.dataset.type;
            console.log("[PUNCHES.JS] Edit Row - about to call configureEditModalFields with type:", punchTypeFromRow);
            configureEditModalFields(punchTypeFromRow);

            if(editPunchDateInput) editPunchDateInput.value = formatDateToYyyyMmDd(selectedRowElement.dataset.date);
            const isOriginalAccrued = isHoursOnlyType(punchTypeFromRow);
            if (isOriginalAccrued) {
                if(editPunchTotalHoursInput) editPunchTotalHoursInput.value = parseFloat(selectedRowElement.dataset.totalhours) || '';
                if(editPunchTimeInInput) editPunchTimeInInput.value = '';
                if(editPunchTimeOutInput) editPunchTimeOutInput.value = '';
            } else {
                if(editPunchTimeInInput) editPunchTimeInInput.value = parseTimeTo24Hour(selectedRowElement.dataset.timein);
                if(editPunchTimeOutInput) editPunchTimeOutInput.value = parseTimeTo24Hour(selectedRowElement.dataset.timeout);
                if(editPunchTotalHoursInput) editPunchTotalHoursInput.value = '';
            }
            if(typeof showModal === 'function') {
                 console.log("[PUNCHES.JS] Edit Row - calling commonUtils.showModal for editPunchModal");
                 showModal(editPunchModal);
            } else { editPunchModal.classList.add('modal-visible'); }
            if(editPunchDateInput) editPunchDateInput.focus();
        });
    }

    if (deleteRowBtn) {
        deleteRowBtn.addEventListener('click', function() {
            console.log("[PUNCHES.JS] Delete Row button clicked. selectedRowElement:", selectedRowElement);
            if (!selectedRowElement) { showToast("Please select a row to delete.", "info"); return; }
            if (confirm("Are you sure you want to delete this punch record?")) {
                console.log("[PUNCHES.JS] Delete Row - Confirmed. Proceeding.");
                const punchId = selectedRowElement.dataset.punchId;
                const tempDeleteForm = document.createElement('form');
                tempDeleteForm.method = 'POST'; tempDeleteForm.action = 'AddEditAndDeletePunchesServlet';
                tempDeleteForm.style.display = 'none';
                const actionInput = document.createElement('input'); actionInput.type = 'hidden'; actionInput.name = 'action'; actionInput.value = 'deletePunch'; tempDeleteForm.appendChild(actionInput);
                const punchIdValInput = document.createElement('input'); punchIdValInput.type = 'hidden'; punchIdValInput.name = 'punchId'; punchIdValInput.value = punchId; tempDeleteForm.appendChild(punchIdValInput);
                if (typeof window.EFFECTIVE_TIME_ZONE_ID === 'string' && window.EFFECTIVE_TIME_ZONE_ID) {
                    const tzInput = document.createElement('input'); tzInput.type = 'hidden'; tzInput.name = 'userTimeZone'; tzInput.value = window.EFFECTIVE_TIME_ZONE_ID; tempDeleteForm.appendChild(tzInput);
                }
                document.body.appendChild(tempDeleteForm);
                submitModalForm(tempDeleteForm, null); // No modal to close directly after delete, page reloads
                document.body.removeChild(tempDeleteForm);
            } else {
                console.log("[PUNCHES.JS] Delete Row - Cancelled by user.");
            }
        });
    }

    if (addHoursForm) { addHoursForm.addEventListener('submit', function(event) { event.preventDefault(); submitModalForm(this, addHoursModal); }); }
    if (editPunchForm) { editPunchForm.addEventListener('submit', function(event) { event.preventDefault(); submitModalForm(this, editPunchModal); }); }

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.has('message') || urlParams.has('error')) {
        const message = urlParams.get('message') || urlParams.get('error');
        const type = urlParams.has('message') ? 'success' : 'error';
        showToast(message, type);
        const currentUrl = new URL(window.location);
        currentUrl.searchParams.delete('message');
        currentUrl.searchParams.delete('error');
        if (window.history && window.history.replaceState) {
           window.history.replaceState({}, document.title, currentUrl.toString());
        }
    }
    console.log("Punches.js DOMContentLoaded END (v.ModalLogicUpdate_Rev2_FullDiag)");
});