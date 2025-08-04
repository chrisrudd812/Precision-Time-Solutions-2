// js/scheduling.js - vFINAL_WIZARD_FIX_2
document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] scheduling.js (vFINAL_WIZARD_FIX_2) loaded.");

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
    const closeAddModalBtn = document.getElementById('closeAddModal');
    const cancelAddScheduleBtn = document.getElementById('cancelAddSchedule');

    const editScheduleModal = document.getElementById('editScheduleModal');
    const closeEditModalBtn = document.getElementById('closeEditModal');
    const cancelEditScheduleBtn = document.getElementById('cancelEditSchedule');

    const deleteReassignModal = document.getElementById('deleteAndReassignSchedModal');
    const deleteReassignSelect = document.getElementById('targetReassignSchedSelect');
    const deleteReassignMessage = document.getElementById('deleteReassignModalMessage');
    const closeDeleteReassignBtn = document.getElementById('closeDeleteReassignModalBtn');
    const cancelDeleteReassignBtn = document.getElementById('cancelDeleteReassignBtn');
    const confirmDeleteBtn = document.getElementById('confirmDeleteAndReassignSchedBtn');

    const notificationModal = document.getElementById('notificationModalGeneral');
    const okBtnGeneralNotify = document.getElementById('okButtonNotificationModalGeneral');
    const closeNotificationModalX = notificationModal ? notificationModal.querySelector('.close') : null;
    
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
    const closeWizardGenericModalBtn = document.getElementById('closeWizardGenericModal');

    // --- State Variables ---
    let selectedSchedRow = null;
    let selectedSchedData = null;
    let wizardOpenedAddModal = false; 
    const appRoot = typeof appRootPath === 'string' ? appRootPath : "";
    const companyNameToDisplayJS_Sched = (typeof COMPANY_NAME_SIGNUP_JS_SCHED !== 'undefined' && COMPANY_NAME_SIGNUP_JS_SCHED) ? COMPANY_NAME_SIGNUP_JS_SCHED : "your company";

    // --- Helper Functions ---
    const _showModal = window.showModal || function(modalEl) { if (modalEl) modalEl.style.display = 'flex'; };
    const _hideModal = window.hideModal || function(modalEl) { if (modalEl) modalEl.style.display = 'none'; };
    const _decodeLocal = window.decodeHtmlEntities || function(text) { const ta = document.createElement('textarea'); ta.innerHTML = text; return ta.value; };

    // --- WIZARD HANDLING LOGIC ---

    const wizardStages = {
        "schedules_prompt": {
            title: "Setup: Schedules",
            text1: `Now, let's define some standard work schedules for <strong>${companyNameToDisplayJS_Sched}</strong>.`,
            text2: "Examples: 'Day Shift 8-5', 'Night Shift', 'Open Shift', etc. You can assign these to employees later.",
            buttons: [
                { id: "wizardActionAddSchedule", text: "Add a Schedule", class: "text-green", actionKey: "openAddScheduleModalViaWizard" },
                // **FIX**: Updated button text and action key to point to Accruals.
                { id: "wizardActionNextToAccruals", text: "Next: Accrual Policy Setup", class: "text-blue", actionKey: "advanceToAccruals" }
            ]
        },
        "schedules_after_add_prompt": {
            title: "Setup: Schedules",
            text1: "Excellent! You've created a schedule.",
            text2: "You can add more schedules now, or proceed to set up time off policies.",
            buttons: [
                { id: "wizardActionAddAnotherSchedule", text: "Add Another Schedule", class: "text-green", actionKey: "openAddScheduleModalViaWizard" },
                // **FIX**: Updated button text and action key to point to Accruals.
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
            // **FIX**: This now correctly advances to the accruals page.
            advanceWizardToServerAndRedirect("accruals_prompt", "accruals.jsp");
        }
    }

    // **FIX**: Made this function more generic to handle redirection to any page.
    function advanceWizardToServerAndRedirect(serverNextStep, nextPage) {
         fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': serverNextStep})
        })
        .then(response => response.ok ? response.json() : Promise.reject('Failed to update wizard step on server.'))
        .then(data => {
            if (data.success) {
                const redirectUrl = `${appRoot}/${nextPage}?setup_wizard=true&step=${encodeURIComponent(serverNextStep)}`;
                window.location.href = redirectUrl;
            }
            else { window.showPageNotification("Could not proceed: " + (data.error || "Server error"), true, notificationModal, "Setup Error"); }
        })
        .catch(error => { console.error("Wizard advancement error:", error); window.showPageNotification("Network error advancing wizard. Please try again.", true, notificationModal, "Network Error"); });
    }

    function _hideAddModalAndHandleWizard() {
        _hideModal(addScheduleModal);
        if (window.inWizardMode_Page && wizardOpenedAddModal) {
            wizardOpenedAddModal = false;
            const nonDefaultRows = tableBody ? Array.from(tableBody.querySelectorAll('tr[data-name]')).filter(r => r.dataset.name && !r.dataset.name.toLowerCase().startsWith('open')).length : 0;
            updateWizardModalView(nonDefaultRows > 0 ? "schedules_after_add_prompt" : "schedules_prompt");
        }
    }

    // --- Core Page Logic (unchanged) ---
    function openAddScheduleModal() {
        if (addScheduleModal) {
            if (addScheduleForm) addScheduleForm.reset();
            if (addAutoLunchCheckbox) addAutoLunchCheckbox.dispatchEvent(new Event('change'));
            _showModal(addScheduleModal);
            if (addScheduleNameInput) setTimeout(() => { addScheduleNameInput.focus(); }, 150);
        }
    }

    function toggleActionButtons() {
        const isRowSelected = selectedSchedRow !== null;
        let disableActions = !isRowSelected;
        let titleMessage = isRowSelected ? "" : "Select a schedule to edit or delete.";
        if (isRowSelected && selectedSchedData && selectedSchedData.name.toLowerCase().startsWith('open')) {
            disableActions = true;
            titleMessage = "Default 'Open' schedules cannot be edited or deleted.";
        }
        if (editScheduleButton) { editScheduleButton.disabled = disableActions; editScheduleButton.title = titleMessage; }
        if (deleteScheduleButton) { deleteScheduleButton.disabled = disableActions; deleteScheduleButton.title = titleMessage; }
    }

    function selectRow(row) {
        if (row && row.dataset.name && row.dataset.name.toLowerCase().startsWith('open')) {
            window.showPageNotification("The default 'Open' schedules cannot be edited or deleted.", false, notificationModal, "System Schedule");
            if (selectedSchedRow) selectedSchedRow.classList.remove('selected');
            selectedSchedRow = null;
            selectedSchedData = null;
            toggleActionButtons();
            return;
        }

        if (selectedSchedRow) selectedSchedRow.classList.remove('selected');
        
        if (row && row !== selectedSchedRow) {
            row.classList.add('selected');
            selectedSchedRow = row;
            selectedSchedData = {
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
        };
        checkbox.addEventListener('change', toggleInputs);
        toggleInputs();
    }
    
    // --- EVENT LISTENERS (unchanged) ---
    if (tableBody) tableBody.addEventListener('click', (event) => { selectRow(event.target.closest('tr')); });
    if (addScheduleButton) {
        addScheduleButton.addEventListener('click', () => {
            if (window.inWizardMode_Page) {
                updateWizardModalView('schedules_prompt');
            } else {
                openAddScheduleModal();
            }
        });
    }
    if (editScheduleButton) editScheduleButton.addEventListener('click', () => { if (!editScheduleButton.disabled) populateEditModal(); });
    if (deleteScheduleButton) deleteScheduleButton.addEventListener('click', () => { if (!deleteScheduleButton.disabled) populateDeleteModal(); });
    if (closeAddModalBtn) closeAddModalBtn.addEventListener('click', _hideAddModalAndHandleWizard);
    if (cancelAddScheduleBtn) cancelAddScheduleBtn.addEventListener('click', _hideAddModalAndHandleWizard);
    if (closeEditModalBtn) closeEditModalBtn.addEventListener('click', () => _hideModal(editScheduleModal));
    if (cancelEditScheduleBtn) cancelEditScheduleBtn.addEventListener('click', () => _hideModal(editScheduleModal));
    if (closeDeleteReassignBtn) closeDeleteReassignBtn.addEventListener('click', () => _hideModal(deleteReassignModal));
    if (cancelDeleteReassignBtn) cancelDeleteReassignBtn.addEventListener('click', () => _hideModal(deleteReassignModal));
    if (okBtnGeneralNotify) okBtnGeneralNotify.addEventListener('click', () => _hideModal(notificationModal));
    if (closeNotificationModalX) closeNotificationModalX.addEventListener('click', () => _hideModal(notificationModal));
    if (closeWizardGenericModalBtn) closeWizardGenericModalBtn.addEventListener('click', () => _hideModal(wizardGenericModal));
    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener('click', () => {
            const deleteForm = document.getElementById('deleteScheduleForm');
            if (deleteForm && deleteReassignSelect) {
                deleteForm.querySelector('#hiddenTargetScheduleForReassignment').value = deleteReassignSelect.value;
                deleteForm.submit();
            }
        });
    }
    setupAutoLunchToggle(addAutoLunchCheckbox, addHoursRequiredInput, addLunchLengthInput, true);
    setupAutoLunchToggle(editAutoLunchCheckbox, editHoursRequiredInput, editLunchLengthInput, false);

    // --- INITIALIZATION (unchanged) ---
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
        if (addScheduleNameInput && prevScheduleName) {
            addScheduleNameInput.value = _decodeLocal(prevScheduleName);
        }
        if (errorMessage) {
            window.showPageNotification(errorMessage, true, notificationModal, "Validation Error");
            if (okBtnGeneralNotify) {
                const focusOnClose = (e) => {
                    e.stopImmediatePropagation(); 
                    _hideModal(notificationModal);
                    if (addScheduleNameInput) {
                        addScheduleNameInput.focus();
                        addScheduleNameInput.select();
                    }
                    okBtnGeneralNotify.removeEventListener('click', focusOnClose); 
                };
                okBtnGeneralNotify.addEventListener('click', focusOnClose);
            }
        }
        if (window.history.replaceState) {
            const cleanUrl = window.location.protocol + "//" + window.location.host + window.location.pathname + (window.inWizardMode_Page ? '?setup_wizard=true' : '');
            window.history.replaceState({path: cleanUrl}, '', cleanUrl);
        }
    }
});
