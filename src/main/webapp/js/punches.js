// js/punches.js - v.ModalLogicUpdate_Rev11_DEBUG

// --- Global Constants & Helpers ---
const ACCRUED_HOUR_TYPES = ["Vacation", "Sick", "Personal", "Holiday", "Bereavement", "Other"];
const TIMED_PUNCH_TYPE = "Supervisor Override";
const MANUAL_ENTRY_TYPES = [TIMED_PUNCH_TYPE, ...ACCRUED_HOUR_TYPES];

window.handleNotificationOk = function() {
    const modal = document.getElementById("notificationModalGeneral");
    if (modal && typeof hideModal === 'function') {
        hideModal(modal);
    }
};

function isAccruedHourType(punchType) {
    if (!punchType) return false;
    return ACCRUED_HOUR_TYPES.includes(punchType);
}

function showToast(message, type = 'info') {
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
        toast.addEventListener('transitionend', () => toast.remove(), { once: true });
    }, 3000);
}

function formatDateToYyyyMmDd(dateString) {
    if (!dateString || typeof dateString !== 'string') { return ''; }
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        return dateString;
    }
    try {
        const parts = dateString.split(/[-/]/);
        if (parts.length === 3) {
            const year = parts[2].length === 4 ? parts[2] : null;
            const month = parts[0];
            const day = parts[1];
            if(year) {
                return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            }
        }
    } catch (e) {
        console.error("Could not parse date:", dateString, e);
        return '';
    }
    return '';
}

function parseTimeTo24Hour(timeStr) {
    if (!timeStr || typeof timeStr !== 'string' || timeStr.trim() === "" || timeStr.toLowerCase().includes("n/a")) {
        return "";
    }
    timeStr = timeStr.trim();
    
    const ampmMatch = timeStr.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)/i);
    if (ampmMatch) {
        let hours = parseInt(ampmMatch[1], 10);
        const minutes = ampmMatch[2];
        const seconds = ampmMatch[3] ? ampmMatch[3].padStart(2, '0') : '00';
        const ampm = ampmMatch[4].toUpperCase();

        if (ampm === 'PM' && hours < 12) {
            hours += 12;
        }
        if (ampm === 'AM' && hours === 12) {
            hours = 0;
        }
        return `${String(hours).padStart(2, '0')}:${minutes}:${seconds}`;
    }

    const twentyFourHourMatch = timeStr.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?/);
    if (twentyFourHourMatch) {
        const hours = twentyFourHourMatch[1].padStart(2, '0');
        const minutes = twentyFourHourMatch[2];
        const seconds = twentyFourHourMatch[3] ? twentyFourHourMatch[3].padStart(2, '0') : '00';
        return `${hours}:${minutes}:${seconds}`;
    }

    return "";
}


function reloadPageWithEid() {
    const employeeSelect = document.getElementById('employeeSelectPunches');
    const currentEid = employeeSelect ? employeeSelect.value : (typeof window.SELECTED_EID_ON_LOAD !== 'undefined' ? window.SELECTED_EID_ON_LOAD : null);
    const params = new URLSearchParams();
    if (currentEid && currentEid !== "0") { params.append('eid', currentEid); }
    window.location.href = `punches.jsp?${params.toString()}`;
}

function submitModalForm(formElement, modalToCloseOnOutcome) {
    if (!formElement) { return; }
    const formData = new FormData(formElement);
    const params = new URLSearchParams(formData);

    if (typeof window.EFFECTIVE_TIME_ZONE_ID === 'string' && window.EFFECTIVE_TIME_ZONE_ID) {
        params.append('userTimeZone', window.EFFECTIVE_TIME_ZONE_ID);
    }
    const submitButton = formElement.querySelector('button[type="submit"]');
    if (submitButton) submitButton.disabled = true;

    fetch(formElement.getAttribute('action'), { method: 'POST', body: params })
    .then(response => response.ok ? response.json() : response.text().then(text => { throw new Error(text) }))
    .then(data => {
        const messageToDisplay = data.message || (data.success ? "Operation Successful!" : (data.error || "Operation Failed!"));
        if (modalToCloseOnOutcome) hideModal(modalToCloseOnOutcome);

        if (data.success) {
            window.handleNotificationOk = function() {
                const modal = document.getElementById("notificationModalGeneral");
                if (modal) hideModal(modal);
                reloadPageWithEid();
            };
        } else {
            window.handleNotificationOk = function() {
                const modal = document.getElementById("notificationModalGeneral");
                if (modal) hideModal(modal);
            };
        }
        
        setTimeout(() => {
            const modalTitle = data.success ? "Success" : "Error";
            showPageNotification(messageToDisplay, !data.success, null, modalTitle);
        }, 150);
    })
    .catch(error => {
        showPageNotification('Request error: ' + error.message, true, null, "Error");
    })
    .finally(() => {
        if (submitButton) submitButton.disabled = false;
    });
}


document.addEventListener('DOMContentLoaded', function() {
    document.body.addEventListener('click', function(event) {
        if (event.target && event.target.id === 'okButtonNotificationModalGeneral') {
            if (window.handleNotificationOk && typeof window.handleNotificationOk === 'function') {
                window.handleNotificationOk();
            }
        }
    });

    const employeeSelect = document.getElementById('employeeSelectPunches');
    const addHoursBtn = document.getElementById('addHoursBtn');
    const addTimedPunchBtn = document.getElementById('addTimedPunchBtn');
    const editRowBtn = document.getElementById('editRowBtn');
    const deleteRowBtn = document.getElementById('deleteRowBtn');
    
    const addHoursModal = document.getElementById('addHoursModal');
    const addHoursForm = document.getElementById('addHoursForm');
    const addHoursModalTitle = addHoursModal ? addHoursModal.querySelector('h2#addHoursModalTitle') : null;
    const addHoursDateInput = document.getElementById('addHours_dateInput');
    const addHoursPunchTypeDropdown = document.getElementById('addHours_typeSelect');
    const addHours_timeFieldsRowDiv = document.getElementById('addHours_timeFieldsRowDiv');
    const addHours_timeInInput = document.getElementById('addHours_timeInInput');
    const addHours_timeOutInput = document.getElementById('addHours_timeOutInput');
    const addHours_totalHoursDiv = document.getElementById('addHours_totalHoursDiv');
    const addHours_totalHoursInput = document.getElementById('addHours_totalHoursInput');

    const editPunchModal = document.getElementById('editPunchModal');
    const editPunchForm = document.getElementById('editPunchForm');
    const editPunchIdInput = document.getElementById('editPunch_idInput');
    const editPunchEIDInput = document.getElementById('editPunch_eidInput');
    const editPunchDateInput = document.getElementById('editPunch_dateInput');
    const editPunchTimeInInput = document.getElementById('editPunch_timeInInput');
    const editPunchTimeOutInput = document.getElementById('editPunch_timeOutInput');
    const editPunchTypeSelect = document.getElementById('editPunch_typeSelect');
    const editPunchTimeFieldsRowDiv = document.getElementById('editPunch_timeFieldsRowDiv');
    const editPunchTotalHoursDiv = document.getElementById('editPunch_totalHoursDiv');
    const editPunchTotalHoursInput = document.getElementById('editPunch_totalHoursInput');

    const punchesTableBody = document.getElementById('punchesTable')?.tBodies[0];
    let selectedRowElement = null;
    
    document.querySelectorAll('.modal .close, .modal .close-modal-btn').forEach(button => {
        button.addEventListener('click', (event) => {
            event.preventDefault();
            hideModal(button.closest('.modal'));
        });
    });

    const cancelDeleteBtn = document.getElementById('cancelDeleteBtn');
    if (cancelDeleteBtn) {
        cancelDeleteBtn.addEventListener('click', () => {
            hideModal(document.getElementById('confirmationModal'));
        });
    }

    function populateDropdown(selectElement, optionsArray, selectedValue) {
        if (!selectElement) return;
        selectElement.innerHTML = '';
        optionsArray.forEach(type => selectElement.add(new Option(type, type)));
        if (selectedValue && optionsArray.includes(selectedValue)) {
            selectElement.value = selectedValue;
        } else if (optionsArray.length > 0) {
            selectElement.value = optionsArray[0];
        }
    }

    function updateAddModalFieldsVisibility(selectedValue) {
        const isAccrued = isAccruedHourType(selectedValue);
        if (addHours_timeFieldsRowDiv) addHours_timeFieldsRowDiv.style.display = isAccrued ? 'none' : 'flex';
        if (addHours_timeInInput) addHours_timeInInput.required = !isAccrued;
        if (addHours_totalHoursDiv) addHours_totalHoursDiv.style.display = isAccrued ? 'block' : 'none';
        if (addHours_totalHoursInput) addHours_totalHoursInput.required = isAccrued;
    }

    function setupModalForAccruedHours() {
        if (addHoursModalTitle) addHoursModalTitle.textContent = 'Add Manual Hours';
        populateDropdown(addHoursPunchTypeDropdown, ACCRUED_HOUR_TYPES, 'Vacation');
        updateAddModalFieldsVisibility('Vacation');
    }

    function setupModalForTimedPunch() {
        if (addHoursModalTitle) addHoursModalTitle.textContent = 'Add Timed Punch';
        populateDropdown(addHoursPunchTypeDropdown, [TIMED_PUNCH_TYPE], TIMED_PUNCH_TYPE);
        updateAddModalFieldsVisibility(TIMED_PUNCH_TYPE);
    }

    function configureEditModalFields(punchTypeFromRow) {
        const isAccrued = isAccruedHourType(punchTypeFromRow);
        populateDropdown(editPunchTypeSelect, isAccrued ? ACCRUED_HOUR_TYPES : [TIMED_PUNCH_TYPE], punchTypeFromRow);
        if (editPunchTimeFieldsRowDiv) editPunchTimeFieldsRowDiv.style.display = isAccrued ? 'none' : 'flex';
        if (editPunchTimeInInput) editPunchTimeInInput.required = !isAccrued;
        if (editPunchTotalHoursDiv) editPunchTotalHoursDiv.style.display = isAccrued ? 'block' : 'none';
        if (editPunchTotalHoursInput) editPunchTotalHoursInput.required = isAccrued;
    }
    
    function openAddModal(setupFunction) {
        const currentEid = employeeSelect ? employeeSelect.value : null;
        if (!currentEid || currentEid === "0") {
            showToast("Please select an employee.", "error");
            return;
        }
        if (addHoursForm) {
            addHoursForm.reset();
            addHoursForm.querySelector('input[name="eid"]').value = currentEid;
        }
        setupFunction();
        if (addHoursDateInput) {
            // Set date restrictions to current pay period
            if (window.PAY_PERIOD_START && window.PAY_PERIOD_END) {
                addHoursDateInput.min = window.PAY_PERIOD_START;
                addHoursDateInput.max = window.PAY_PERIOD_END;
            }
            // --- FIX: Use local date components instead of UTC-based toISOString() ---
            const today = new Date();
            const year = today.getFullYear();
            const month = String(today.getMonth() + 1).padStart(2, '0'); // getMonth() is 0-indexed
            const day = String(today.getDate()).padStart(2, '0');
            addHoursDateInput.value = `${year}-${month}-${day}`;
        }
        showModal(addHoursModal);
        (setupFunction === setupModalForAccruedHours ? addHours_totalHoursInput : addHours_timeInInput)?.focus();
    }
    
    if (addHoursPunchTypeDropdown) {
        addHoursPunchTypeDropdown.addEventListener('change', (e) => updateAddModalFieldsVisibility(e.target.value));
    }
    if (addHoursBtn) addHoursBtn.addEventListener('click', () => openAddModal(setupModalForAccruedHours));
    if (addTimedPunchBtn) addTimedPunchBtn.addEventListener('click', () => openAddModal(setupModalForTimedPunch));

    if (punchesTableBody) {
        punchesTableBody.addEventListener('click', (event) => {
            let targetRow = event.target.closest('tr[data-punch-id]');
            if (!targetRow) return;
            if (selectedRowElement && selectedRowElement !== targetRow) {
                selectedRowElement.classList.remove('selected');
            }
            targetRow.classList.toggle('selected');
            selectedRowElement = targetRow.classList.contains('selected') ? targetRow : null;
            if (editRowBtn) editRowBtn.disabled = !selectedRowElement;
            if (deleteRowBtn) deleteRowBtn.disabled = !selectedRowElement;
            
            // Scroll to bottom when row is selected to show buttons
            if (selectedRowElement) {
                window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
            }
        });
    }

    if (editRowBtn) {
        editRowBtn.addEventListener('click', () => {
            if (!selectedRowElement) return;
            
            const data = selectedRowElement.dataset;

            if (editPunchForm) editPunchForm.reset();
            
            if (editPunchIdInput) editPunchIdInput.value = data.punchId || '';
            if (editPunchEIDInput) editPunchEIDInput.value = data.eid || '';

            const formattedDate = formatDateToYyyyMmDd(data.date);
            if (editPunchDateInput) {
                // Set date restrictions to current pay period
                if (window.PAY_PERIOD_START && window.PAY_PERIOD_END) {
                    editPunchDateInput.min = window.PAY_PERIOD_START;
                    editPunchDateInput.max = window.PAY_PERIOD_END;
                }
                editPunchDateInput.value = formattedDate;
            }
            
            configureEditModalFields(data.type);

            if (isAccruedHourType(data.type)) {
                if (editPunchTotalHoursInput) editPunchTotalHoursInput.value = parseFloat(data.totalhours) || '';
            } else {
                const formattedInTime = parseTimeTo24Hour(data.timein);
                const formattedOutTime = parseTimeTo24Hour(data.timeout);
                if (editPunchTimeInInput) editPunchTimeInInput.value = formattedInTime;
                if (editPunchTimeOutInput) editPunchTimeOutInput.value = formattedOutTime;
            }
            
            showModal(editPunchModal);
            editPunchDateInput?.focus();
        });
    }

    if (deleteRowBtn) {
        deleteRowBtn.addEventListener('click', () => {
            if (!selectedRowElement) return;
            const confirmationModal = document.getElementById('confirmationModal');
            if (!confirmationModal) return;

            const confirmBtn = confirmationModal.querySelector('#confirmDeleteBtn');
            const modalMessage = confirmationModal.querySelector('#confirmationModalMessage');

            modalMessage.textContent = "Are you sure you want to delete this punch record? This action cannot be undone.";
            showModal(confirmationModal);

            const confirmAction = () => {
                const punchId = selectedRowElement.dataset.punchId;
                const tempDeleteForm = document.createElement('form');
                tempDeleteForm.method = 'POST';
                tempDeleteForm.action = 'AddEditAndDeletePunchesServlet';
                tempDeleteForm.innerHTML = `<input type="hidden" name="action" value="deletePunch"><input type="hidden" name="punchId" value="${punchId}">`;
                document.body.appendChild(tempDeleteForm);
                submitModalForm(tempDeleteForm, confirmationModal);
                tempDeleteForm.remove();
            };
            
            const newConfirmBtn = confirmBtn.cloneNode(true);
            confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);
            newConfirmBtn.addEventListener('click', confirmAction, { once: true });
        });
    }

    if (addHoursForm) addHoursForm.addEventListener('submit', (e) => { e.preventDefault(); submitModalForm(e.target, addHoursModal); });
    if (editPunchForm) editPunchForm.addEventListener('submit', (e) => { e.preventDefault(); submitModalForm(e.target, editPunchModal); });

    const urlParams = new URLSearchParams(window.location.search);
    const message = urlParams.get('message') || urlParams.get('error');
    if (message) {
        showToast(message, urlParams.has('message') ? 'success' : 'error');
        const cleanUrl = `${window.location.pathname}?eid=${employeeSelect ? employeeSelect.value : ''}`;
        window.history.replaceState({}, document.title, cleanUrl);
    }
});