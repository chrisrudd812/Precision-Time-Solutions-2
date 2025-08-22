// js/scheduling.js

/**
 * FIX: Copied utility functions from commonUtils.js to resolve script loading order issues.
 * This ensures these functions are available when this script runs, without modifying shared files.
 */
function decodeHtmlEntities(encodedString) {
    if (encodedString === null || typeof encodedString === 'undefined' || String(encodedString).toLowerCase() === 'null') { return ''; }
    try {
        const textarea = document.createElement('textarea');
        textarea.innerHTML = String(encodedString); 
        return textarea.value;
    } catch (e) {
        return String(encodedString); 
    }
}

function showModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.style.display = 'flex';
        modalElement.classList.add('modal-visible');
    }
}

function hideModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.style.display = 'none';
        modalElement.classList.remove('modal-visible');
    }
}

function showPageNotification(message, isError = false, modalInstance = null, titleText = "Notification") { 
    const modalToUse = modalInstance || document.getElementById("notificationModalGeneral");
    const msgElem = modalToUse ? modalToUse.querySelector('#notificationModalGeneralMessage') : null;
    const modalTitleElem = modalToUse ? modalToUse.querySelector('#notificationModalGeneralTitle') : null;

    if (!modalToUse || !msgElem) {
        alert((isError ? "Error: " : "Notification: ") + message);
        return;
    }
    if(modalTitleElem) modalTitleElem.textContent = titleText;
    msgElem.innerHTML = message;
    showModal(modalToUse); 
    
    // FIX: Automatically focus the OK button for better accessibility.
    const okButton = modalToUse.querySelector('#okButtonNotificationModalGeneral');
    if (okButton) {
        setTimeout(() => okButton.focus(), 150);
    }
}


document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] scheduling.js Loaded. Ready to work.");

    // --- Element Selectors ---
    const addScheduleButton = document.getElementById('addScheduleButton');
    const editScheduleButton = document.getElementById('editScheduleButton');
    const deleteScheduleButton = document.getElementById('deleteScheduleButton');
    const schedulesTable = document.getElementById('schedulesTable');
    const tableBody = schedulesTable ? schedulesTable.querySelector('tbody') : null;

    // Modal Specific Selectors
    const addScheduleModal = document.getElementById('addScheduleModal');
    const addScheduleForm = document.getElementById('addScheduleForm');
    const addScheduleNameInput = document.getElementById('addScheduleName');
    
    const editScheduleModal = document.getElementById('editScheduleModal');
    const editScheduleForm = document.getElementById('editScheduleForm');

    const deleteReassignModal = document.getElementById('deleteAndReassignSchedModal');
    const deleteReassignSelect = document.getElementById('targetReassignSchedSelect');
    const deleteReassignMessage = document.getElementById('deleteReassignModalMessage');
    const confirmDeleteBtn = document.getElementById('confirmDeleteAndReassignSchedBtn');

    const notificationModal = document.getElementById('notificationModalGeneral');
    
    // Auto-Lunch related inputs
    const addAutoLunchCheckbox = document.getElementById('addAutoLunch');
    const addHoursRequiredInput = document.getElementById('addHoursRequired');
    const addLunchLengthInput = document.getElementById('addLunchLength');
    const editAutoLunchCheckbox = document.getElementById('editAutoLunch');
    const editHoursRequiredInput = document.getElementById('editHoursRequired');
    const editLunchLengthInput = document.getElementById('editLunchLength');
    
    // --- WIZARD-SPECIFIC SELECTORS ---
    const wizardGenericModal = document.getElementById('wizardGenericModal');
    const wizardTitleElement = document.getElementById('wizardGenericModalTitle');
    const wizardText1Element = document.getElementById('wizardGenericModalText1');
    const wizardText2Element = document.getElementById('wizardGenericModalText2');
    const wizardButtonRow = document.getElementById('wizardGenericModalButtonRow');

    // --- State Variables ---
    let selectedSchedRow = null;
    let selectedSchedData = null;
    let wizardOpenedAddModal = false; 
    const appRoot = typeof window.appRootPath === 'string' ? window.appRootPath : "";
    const companyNameToDisplayJS_Sched = (typeof window.COMPANY_NAME_SIGNUP_JS_SCHED !== 'undefined' && window.COMPANY_NAME_SIGNUP_JS_SCHED) ? window.COMPANY_NAME_SIGNUP_JS_SCHED : "your company";

    // --- Helper Functions ---
    const _showModal = showModal;
    const _hideModal = hideModal;
    const _decodeLocal = decodeHtmlEntities;

    // --- WIZARD HANDLING LOGIC ---
    const wizardStages = {
        "schedules_prompt": {
            title: "Setup: Schedules",
            text1: `Now, let's define some standard work schedules for <strong>${companyNameToDisplayJS_Sched}</strong>.`,
            text2: "Examples: 'Day Shift 8-5', 'Night Shift', 'Open Shift', etc. You can assign these to employees later.",
            buttons: [
                { id: "wizardActionAddSchedule", text: "Add a Schedule", class: "text-green", actionKey: "openAddScheduleModalViaWizard" },
                { id: "wizardActionNextToAccruals", text: "Next: Accrual Policy Setup", class: "text-blue", actionKey: "advanceToAccruals" }
            ]
        },
        "schedules_after_add_prompt": {
            title: "Setup: Schedules",
            text1: "Excellent! You've created a schedule.",
            text2: "You can add more schedules now, or proceed to set up time off policies.",
            buttons: [
                { id: "wizardActionAddAnotherSchedule", text: "Add Another Schedule", class: "text-green", actionKey: "openAddScheduleModalViaWizard" },
                { id: "wizardActionNextAfterAddToAccruals", text: "Next: Accrual Policy Setup", class: "text-blue", actionKey: "advanceToAccruals" }
            ]
        }
    };

    function updateWizardModalView(stageKey) {
        if (!wizardGenericModal) return;
        const stageConfig = wizardStages[stageKey];
        if (!stageConfig) { _hideModal(wizardGenericModal); return; }

        if(wizardTitleElement) wizardTitleElement.textContent = stageConfig.title;
        if(wizardText1Element) wizardText1Element.innerHTML = stageConfig.text1;
        if(wizardText2Element) wizardText2Element.innerHTML = stageConfig.text2;
        if(wizardButtonRow) wizardButtonRow.innerHTML = '';

        stageConfig.buttons.forEach(btnConfig => {
            const button = document.createElement('button');
            button.type = 'button';
            button.id = btnConfig.id;
            button.className = `glossy-button ${btnConfig.class}`;
            button.innerHTML = btnConfig.text;
            button.addEventListener('click', () => handleWizardAction(btnConfig.actionKey, btnConfig.nextPage));
            if(wizardButtonRow) wizardButtonRow.appendChild(button);
        });

        _showModal(wizardGenericModal);
    }

    function handleWizardAction(actionKey) {
        if (actionKey === "openAddScheduleModalViaWizard") {
            wizardOpenedAddModal = true;
            _hideModal(wizardGenericModal);
            openAddScheduleModal();
        } else if (actionKey === "advanceToAccruals") {
            advanceWizardToServerAndRedirect("accruals_prompt", "accruals.jsp");
        }
    }

    function advanceWizardToServerAndRedirect(serverNextStep, nextPage) {
         fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': serverNextStep})
        })
        .then(response => response.ok ? response.json() : Promise.reject('Failed to update wizard step on server.'))
        .then(data => {
            if (data.success && data.nextStep) {
                const redirectUrl = `${appRoot}/${nextPage}?setup_wizard=true&step=${encodeURIComponent(data.nextStep)}`;
                window.location.href = redirectUrl;
            }
            else { 
                showPageNotification("Could not proceed: " + (data.error || "Server error: Invalid response from server."), true, notificationModal, "Setup Error"); 
            }
        })
        .catch(error => { 
            console.error("Wizard advancement error:", error); 
            showPageNotification("Network error advancing wizard. Please try again.", true, notificationModal, "Network Error"); 
        });
    }

    function _hideAddModalAndHandleWizard() {
        _hideModal(addScheduleModal);
        if (window.inWizardMode_Page && wizardOpenedAddModal) {
            wizardOpenedAddModal = false;
            const nonDefaultRows = tableBody ? Array.from(tableBody.querySelectorAll('tr[data-name]')).filter(r => r.dataset.name && !r.dataset.name.toLowerCase().startsWith('open')).length : 0;
            updateWizardModalView(nonDefaultRows > 0 ? "schedules_after_add_prompt" : "schedules_prompt");
        }
    }

    function openAddScheduleModal() {
        console.log("[DEBUG] openAddScheduleModal called.");
        if (addScheduleModal) {
            if (addScheduleForm) addScheduleForm.reset();
            if (addAutoLunchCheckbox) addAutoLunchCheckbox.dispatchEvent(new Event('change'));
            _showModal(addScheduleModal);
            if (addScheduleNameInput) setTimeout(() => { addScheduleNameInput.focus(); }, 150);
        }
    }

    function toggleActionButtons() {
        const isRowSelected = selectedSchedRow !== null;
        let disableEdit = !isRowSelected;
        let disableDelete = !isRowSelected;
        
        if (isRowSelected && selectedSchedData) {
            const schedNameLower = selectedSchedData.name.trim().toLowerCase();
            if (schedNameLower === 'open') {
                disableEdit = true;
                disableDelete = true;
            } else if (schedNameLower === 'open w/ auto lunch') {
                disableEdit = false; 
                disableDelete = true;
            }
        }
        
        if (editScheduleButton) editScheduleButton.disabled = disableEdit;
        if (deleteScheduleButton) deleteScheduleButton.disabled = disableDelete;
    }

    function selectRow(row) {
        if (selectedSchedRow) selectedSchedRow.classList.remove('selected');
        
        if (row && row !== selectedSchedRow) {
            row.classList.add('selected');
            selectedSchedRow = row;
            selectedSchedData = {
                id: row.dataset.id || null,
                name: _decodeLocal(row.dataset.name || ''),
                shiftStart: row.dataset.shiftStart || '',
                lunchStart: row.dataset.lunchStart || '',
                lunchEnd: row.dataset.lunchEnd || '',
                shiftEnd: row.dataset.shiftEnd || '',
                daysWorked: row.dataset.daysWorked || '-------',
                autoLunch: (row.dataset.autoLunch === 'true'),
                hoursRequired: row.dataset.hoursRequired || '',
                lunchLength: row.dataset.lunchLength || ''
            };
            
            const schedNameLower = selectedSchedData.name.trim().toLowerCase();
            // FIX: Improved notification messages for clarity.
            if (schedNameLower === 'open') {
                showPageNotification("The default 'Open' schedule cannot be edited or deleted. It serves as a system fallback.", false, notificationModal, "System Schedule");
            } else if (schedNameLower === 'open w/ auto lunch') {
                showPageNotification("This is a system schedule. Only the Auto Lunch settings (Hours Required & Lunch Length) can be changed.", false, notificationModal, "System Schedule Information");
            }

        } else {
            selectedSchedRow = null;
            selectedSchedData = null;
        }
        toggleActionButtons();
    }
    
    function populateEditModal() {
        if (!editScheduleModal || !selectedSchedData) return;
        const form = editScheduleModal.querySelector('form');
        form.reset();
        
        form.querySelector('#hiddenEditOriginalName').value = selectedSchedData.name;
        form.querySelector('#editScheduleName').value = selectedSchedData.name;
        form.querySelector('#editShiftStart').value = selectedSchedData.shiftStart;
        form.querySelector('#editLunchStart').value = selectedSchedData.lunchStart;
        form.querySelector('#editLunchEnd').value = selectedSchedData.lunchEnd;
        form.querySelector('#editShiftEnd').value = selectedSchedData.shiftEnd;
        const days = selectedSchedData.daysWorked;
        form.querySelector('#editDaySun').checked = days.charAt(0) === 'S';
        form.querySelector('#editDayMon').checked = days.charAt(1) === 'M';
        form.querySelector('#editDayTue').checked = days.charAt(2) === 'T';
        form.querySelector('#editDayWed').checked = days.charAt(3) === 'W';
        form.querySelector('#editDayThu').checked = days.charAt(4) === 'H';
        form.querySelector('#editDayFri').checked = days.charAt(5) === 'F';
        form.querySelector('#editDaySat').checked = days.charAt(6) === 'A';
        form.querySelector('#editAutoLunch').checked = selectedSchedData.autoLunch;
        form.querySelector('#editHoursRequired').value = selectedSchedData.hoursRequired;
        form.querySelector('#editLunchLength').value = selectedSchedData.lunchLength;
        
        const isAutoLunchSchedule = selectedSchedData.name.trim().toLowerCase() === 'open w/ auto lunch';
        
        const fieldsToToggle = [
            'editScheduleName', 'editShiftStart', 'editLunchStart', 
            'editLunchEnd', 'editShiftEnd', 'editDaySun', 'editDayMon', 
            'editDayTue', 'editDayWed', 'editDayThu', 'editDayFri', 'editDaySat',
            'editAutoLunch'
        ];
        
        fieldsToToggle.forEach(id => {
            const field = form.querySelector(`#${id}`);
            if (field) field.disabled = isAutoLunchSchedule;
        });

        if(editHoursRequiredInput) editHoursRequiredInput.disabled = !editAutoLunchCheckbox.checked;
        if(editLunchLengthInput) editLunchLengthInput.disabled = !editAutoLunchCheckbox.checked;

        if (isAutoLunchSchedule) {
             if(editHoursRequiredInput) editHoursRequiredInput.disabled = false;
             if(editLunchLengthInput) editLunchLengthInput.disabled = false;
        }

        if(editAutoLunchCheckbox) editAutoLunchCheckbox.dispatchEvent(new Event('change'));
        _showModal(editScheduleModal);
    }
    
    function populateDeleteModal() {
        if (!deleteReassignModal || !selectedSchedData || !deleteReassignSelect) return;
        if (deleteReassignMessage) {
            deleteReassignMessage.innerHTML = `You are about to delete schedule: <strong>${selectedSchedData.name}</strong>. All employees assigned to this schedule must be reassigned.`;
        }
        deleteReassignSelect.innerHTML = '';
        const schedules = window.allAvailableSchedulesForReassign || [];
        schedules.forEach(sched => {
            if (sched.name !== selectedSchedData.name) {
                const option = new Option(_decodeLocal(sched.name), sched.name);
                deleteReassignSelect.add(option);
            }
        });
        const deleteForm = document.getElementById('deleteScheduleForm');
        if (deleteForm) {
            deleteForm.querySelector('#hiddenDeleteScheduleName').value = selectedSchedData.name;
        }
        _showModal(deleteReassignModal);
    }

    function setupAutoLunchToggle(checkbox, hoursInput, lengthInput, setDefaults) {
        if (!checkbox || !hoursInput || !lengthInput) return;
        const toggleInputs = () => {
            if (!checkbox.disabled) {
                const isEnabled = checkbox.checked;
                hoursInput.disabled = !isEnabled;
                lengthInput.disabled = !isEnabled;
                hoursInput.required = isEnabled;
                lengthInput.required = isEnabled;
                if (isEnabled && setDefaults) {
                    if (!hoursInput.value) hoursInput.value = '6';
                    if (!lengthInput.value) lengthInput.value = '30';
                } else if (!isEnabled) {
                    hoursInput.value = '';
                    lengthInput.value = '';
                }
            }
        };
        checkbox.addEventListener('change', toggleInputs);
        toggleInputs(); 
    }

    function setupTimePairValidation(startInput, endInput) {
        if (!startInput || !endInput) return;
        const validate = () => {
            const startValue = startInput.value;
            const endValue = endInput.value;
            if (startValue && !endValue) endInput.required = true; else endInput.required = false;
            if (endValue && !startValue) startInput.required = true; else startInput.required = false;
        };
        startInput.addEventListener('input', validate);
        endInput.addEventListener('input', validate);
    }
    
    // --- EVENT LISTENERS ---
    if (tableBody) tableBody.addEventListener('click', (event) => { selectRow(event.target.closest('tr')); });
    if (addScheduleButton) addScheduleButton.addEventListener('click', () => { if (window.inWizardMode_Page) { updateWizardModalView('schedules_prompt'); } else { openAddScheduleModal(); } });
    if (editScheduleButton) editScheduleButton.addEventListener('click', () => { if (!editScheduleButton.disabled) populateEditModal(); });
    if (deleteScheduleButton) deleteScheduleButton.addEventListener('click', () => { if (!deleteScheduleButton.disabled) populateDeleteModal(); });
    document.querySelectorAll('.modal .close, .modal .cancel-btn').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const modalToClose = e.target.closest('.modal');
            if (modalToClose) { if (modalToClose.id === 'addScheduleModal') { _hideAddModalAndHandleWizard(); } else { _hideModal(modalToClose); } }
        });
    });
    if (notificationModal) notificationModal.querySelector('#okButtonNotificationModalGeneral').addEventListener('click', () => _hideModal(notificationModal));
    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener('click', () => {
            const deleteForm = document.getElementById('deleteScheduleForm');
            if (deleteForm && deleteReassignSelect) {
                deleteForm.querySelector('#hiddenTargetScheduleForReassignment').value = deleteReassignSelect.value;
                deleteForm.submit();
            }
        });
    }

    // --- INITIALIZATION ---
    setupAutoLunchToggle(addAutoLunchCheckbox, addHoursRequiredInput, addLunchLengthInput, true);
    setupAutoLunchToggle(editAutoLunchCheckbox, editHoursRequiredInput, editLunchLengthInput, false);
    if(addScheduleModal) {
        setupTimePairValidation(document.getElementById('addShiftStart'), document.getElementById('addShiftEnd'));
        setupTimePairValidation(document.getElementById('addLunchStart'), document.getElementById('addLunchEnd'));
    }
    if(editScheduleModal) {
        setupTimePairValidation(document.getElementById('editShiftStart'), document.getElementById('editShiftEnd'));
        setupTimePairValidation(document.getElementById('editLunchStart'), document.getElementById('editLunchEnd'));
    }
    toggleActionButtons();
    if (window.inWizardMode_Page === true) {
        const urlParamsForWizard = new URLSearchParams(window.location.search);
        const justAdded = urlParamsForWizard.get('scheduleAdded') === 'true';
        const stage = justAdded ? "schedules_after_add_prompt" : (window.currentWizardStep_Page || "schedules_prompt");
        updateWizardModalView(stage);
    }
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('reopenModal') === 'addSchedule') {
        const errorMessage = urlParams.get('error');
        const prevScheduleName = urlParams.get('scheduleName');
        openAddScheduleModal(); 
        if (addScheduleNameInput && prevScheduleName) addScheduleNameInput.value = _decodeLocal(prevScheduleName);
        if (errorMessage) {
            showPageNotification(errorMessage, true, notificationModal, "Validation Error");
            const okBtn = notificationModal.querySelector('#okButtonNotificationModalGeneral');
            if (okBtn) {
                const focusOnClose = (e) => {
                    e.stopImmediatePropagation(); 
                    _hideModal(notificationModal);
                    if (addScheduleNameInput) { addScheduleNameInput.focus(); addScheduleNameInput.select(); }
                    okBtn.removeEventListener('click', focusOnClose); 
                };
                okBtn.addEventListener('click', focusOnClose);
            }
        }
        if (window.history.replaceState) {
            const cleanUrl = window.location.protocol + "//" + window.location.host + window.location.pathname + (window.inWizardMode_Page ? '?setup_wizard=true' : '');
            window.history.replaceState({path: cleanUrl}, '', cleanUrl);
        }
    }
});
