// js/scheduling.js

document.addEventListener('DOMContentLoaded', function() {

    const _showModal = showModal;
    const _hideModal = hideModal;
    const _decode = decodeHtmlEntities;
    const appRoot = window.appRootPath || "";

    const addScheduleButton = document.getElementById('addScheduleButton');
    const editScheduleButton = document.getElementById('editScheduleButton');
    const deleteScheduleButton = document.getElementById('deleteScheduleButton');
    const schedulesTable = document.getElementById('schedulesTable');
    const tableBody = schedulesTable ? schedulesTable.querySelector('tbody') : null;

    const addScheduleModal = document.getElementById('addScheduleModal');
    const addScheduleForm = document.getElementById('addScheduleForm');
    const editScheduleModal = document.getElementById('editScheduleModal');
    const deleteReassignModal = document.getElementById('deleteAndReassignSchedModal');
    const deleteReassignSelect = document.getElementById('targetReassignSchedSelect');
    const deleteReassignMessage = document.getElementById('deleteReassignModalMessage');
    const confirmDeleteBtn = document.getElementById('confirmDeleteAndReassignSchedBtn');
    
    const addAutoLunchCheckbox = document.getElementById('addAutoLunch');
    const addHoursRequiredInput = document.getElementById('addHoursRequired');
    const addLunchLengthInput = document.getElementById('addLunchLength');
    const editAutoLunchCheckbox = document.getElementById('editAutoLunch');
    const editHoursRequiredInput = document.getElementById('editHoursRequired');
    const editLunchLengthInput = document.getElementById('editLunchLength');
    
    const wizardGenericModal = document.getElementById('wizardGenericModal');
    const wizardTitleElement = document.getElementById('wizardGenericModalTitle');
    const wizardText1Element = document.getElementById('wizardGenericModalText1');
    const wizardText2Element = document.getElementById('wizardGenericModalText2');
    const wizardButtonRow = document.getElementById('wizardGenericModalButtonRow');

    let selectedSchedRow = null;
    let selectedSchedData = null;
    let wizardOpenedAddModal = false; 
    const companyNameToDisplayJS_Sched = (typeof window.COMPANY_NAME_SIGNUP_JS_SCHED !== 'undefined' && window.COMPANY_NAME_SIGNUP_JS_SCHED) ? window.COMPANY_NAME_SIGNUP_JS_SCHED : "your company";
    
    function selectRow(row) {
        if (selectedSchedRow) selectedSchedRow.classList.remove('selected');
        
        if (row && row !== selectedSchedRow) {
            row.classList.add('selected');
            selectedSchedRow = row;
            selectedSchedData = {
                id: row.dataset.id || null,
                name: _decode(row.dataset.name || ''),
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
            if (schedNameLower === 'open') {
                window.showPageNotification(
                    "The default 'Open' schedule cannot be edited or deleted. It serves as a system fallback.", 
                    'error',
                    null, 
                    "Action Denied"
                );
            } else if (schedNameLower === 'open w/ auto lunch') {
                window.showPageNotification(
                    "This is a system schedule. Only the Auto Lunch settings (Hours Required & Lunch Length) can be changed.", 
                    'warning',
                    null, 
                    "System Schedule Information"
                );
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
            'editShiftStart', 'editLunchStart', 
            'editLunchEnd', 'editShiftEnd', 'editDaySun', 'editDayMon', 
            'editDayTue', 'editDayWed', 'editDayThu', 'editDayFri', 'editDaySat'
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
    
    const wizardStages = {
        "schedules_prompt": {
            title: "Setup: Schedules",
            text1: `Now, let's define some standard work schedules for <strong>${companyNameToDisplayJS_Sched}</strong>.`,
            text2: "Examples: 'Day Shift 8-5', 'Night Shift', 'Open Shift', etc. You can assign these to employees later.",
            buttons: [
                { id: "wizardActionAddSchedule", text: "Add a Schedule", class: "text-green", actionKey: "openAddScheduleModalViaWizard" },
                { id: "wizardActionNextToAccruals", text: "Next: PTO Policy Setup", class: "text-blue", actionKey: "advanceToAccruals" }
            ]
        },
        "schedules_after_add_prompt": {
            title: "Setup: Schedules",
            text1: "Excellent! You've created a schedule.",
            text2: "You can add more schedules now, or proceed to set up time off policies.",
            buttons: [
                { id: "wizardActionAddAnotherSchedule", text: "Add Another Schedule", class: "text-green", actionKey: "openAddScheduleModalViaWizard" },
                { id: "wizardActionNextAfterAddToAccruals", text: "Next: PTO Policy Setup", class: "text-blue", actionKey: "advanceToAccruals" }
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
                window.showPageNotification("Could not proceed: " + (data.error || "Server error: Invalid response from server."), 'error', null, "Setup Error"); 
            }
        })
        .catch(error => { 
            console.error("Wizard advancement error:", error); 
            window.showPageNotification("Network error advancing wizard. Please try again.", 'error', null, "Network Error"); 
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
        if (addScheduleModal) {
            if (addScheduleForm) addScheduleForm.reset();
            if (addAutoLunchCheckbox) addAutoLunchCheckbox.dispatchEvent(new Event('change'));
            _showModal(addScheduleModal);
            const addScheduleNameInput = document.getElementById('addScheduleName');
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
    
    function populateDeleteModal() {
        if (!deleteReassignModal || !selectedSchedData || !deleteReassignSelect) return;
        
        if (deleteReassignMessage) {
            deleteReassignMessage.innerHTML = `You are about to delete schedule: <strong>${_decode(selectedSchedData.name)}</strong>.`;
        }
        deleteReassignSelect.innerHTML = '';
        deleteReassignSelect.add(new Option('-- Select a Schedule --', ''));

        const schedules = window.allAvailableSchedulesForReassign || [];
        schedules.forEach(sched => {
            if (sched.name !== selectedSchedData.name) {
                const option = new Option(_decode(sched.name), sched.name);
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

    if (confirmDeleteBtn) {
        confirmDeleteBtn.addEventListener('click', () => {
            const deleteForm = document.getElementById('deleteScheduleForm');
            const targetSched = deleteReassignSelect.value;
            
            if (!targetSched) {
                window.showPageNotification('You must select a schedule to reassign employees to.', 'error', null, 'Selection Required');
                return;
            }

            if (deleteForm) {
                deleteForm.querySelector('#hiddenTargetScheduleForReassignment').value = targetSched;
                deleteForm.submit();
            }
        });
    }

    setupAutoLunchToggle(addAutoLunchCheckbox, addHoursRequiredInput, addLunchLengthInput, true);
    setupAutoLunchToggle(editAutoLunchCheckbox, editHoursRequiredInput, editLunchLengthInput, false);
    
    // Auto-scroll when tabbing from schedule name to time fields
    const addScheduleNameInput = document.getElementById('addScheduleName');
    const editScheduleNameInput = document.getElementById('editScheduleName');
    
    if (addScheduleNameInput) {
        addScheduleNameInput.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                setTimeout(() => {
                    const timeSection = addScheduleModal.querySelector('.form-body-container:nth-child(2)');
                    if (timeSection) timeSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }, 50);
            }
        });
    }
    
    if (editScheduleNameInput) {
        editScheduleNameInput.addEventListener('keydown', (e) => {
            if (e.key === 'Tab') {
                setTimeout(() => {
                    const timeSection = editScheduleModal.querySelector('.form-body-container:nth-child(2)');
                    if (timeSection) timeSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
                }, 50);
            }
        });
    }
    
    // Auto-scroll for edit modal time fields on focus
    const editTimeFields = ['editShiftStart', 'editShiftEnd', 'editLunchStart', 'editLunchEnd'];
    editTimeFields.forEach(fieldId => {
        const field = document.getElementById(fieldId);
        if (field) {
            field.addEventListener('focus', () => {
                const timeSection = editScheduleModal.querySelector('.form-body-container:nth-child(2)');
                if (timeSection) timeSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
            });
        }
    });
    
    // Scroll to bottom when day checkboxes are clicked or focused
    const dayCheckboxes = document.querySelectorAll('#addScheduleModal input[name="days"], #editScheduleModal input[name="days"]');
    dayCheckboxes.forEach(checkbox => {
        const scrollToBottom = () => {
            const modal = checkbox.closest('.modal');
            const modalBody = modal.querySelector('.modal-body');
            if (modalBody) {
                setTimeout(() => {
                    modalBody.scrollTo({ top: modalBody.scrollHeight, behavior: 'smooth' });
                }, 50);
            }
        };
        checkbox.addEventListener('click', scrollToBottom);
        checkbox.addEventListener('focus', scrollToBottom);
    });
    if(addScheduleModal) {
        setupTimePairValidation(document.getElementById('addShiftStart'), document.getElementById('addShiftEnd'));
        setupTimePairValidation(document.getElementById('addLunchStart'), document.getElementById('addLunchEnd'));
    }
    if(editScheduleModal) {
        setupTimePairValidation(document.getElementById('editShiftStart'), document.getElementById('editShiftEnd'));
        setupTimePairValidation(document.getElementById('editLunchEnd'), document.getElementById('editLunchEnd'));
    }

    toggleActionButtons();
    
    // *** THIS IS THE CORRECTED LOGIC ***
    if (window.inWizardMode_Page === true) {
        // Rely on the 'itemJustAdded_Page' flag set by the JSP for consistency with other wizard pages.
        const stage = window.itemJustAdded_Page ? "schedules_after_add_prompt" : (window.currentWizardStep_Page || "schedules_prompt");
        updateWizardModalView(stage);
    }

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('reopenModal') === 'addSchedule') {
        const errorMessage = urlParams.get('error');
        const prevScheduleName = urlParams.get('scheduleName');
        openAddScheduleModal(); 
        const addScheduleNameInput = document.getElementById('addScheduleName');
        if (addScheduleNameInput && prevScheduleName) addScheduleNameInput.value = _decode(prevScheduleName);
        if (errorMessage) {
            window.showPageNotification(errorMessage, 'error', null, "Validation Error");
        }
        if (window.history.replaceState) {
            const cleanUrl = window.location.protocol + "//" + window.location.host + window.location.pathname + (window.inWizardMode_Page ? '?setup_wizard=true' : '');
            window.history.replaceState({path: cleanUrl}, '', cleanUrl);
        }
    }

    const successNotificationDiv = document.getElementById('pageNotificationDiv_Success_Sched');
    if (successNotificationDiv && successNotificationDiv.textContent.trim()) {
        const message = successNotificationDiv.innerHTML;
        successNotificationDiv.style.display = 'none';
        window.showPageNotification(message, 'success', null, "Success");
    }

    const errorNotificationDiv = document.getElementById('pageNotificationDiv_Error_Sched');
    if (errorNotificationDiv && errorNotificationDiv.textContent.trim()) {
        const message = errorNotificationDiv.innerHTML;
        errorNotificationDiv.style.display = 'none';
        window.showPageNotification(message, 'error', null, "Error");
    }
});