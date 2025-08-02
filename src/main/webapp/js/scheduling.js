// js/scheduling.js - vDeleteFix
document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] scheduling.js (vDeleteFix) loaded.");
    
    // --- Global/Common Utility Function Access ---
    const _showModal = window.showModal || function(modalEl) { if(modalEl) { modalEl.style.display = 'flex'; modalEl.classList.add('modal-visible'); } };
    const _hideModal = window.hideModal || function(modalEl) { if(modalEl) { modalEl.style.display = 'none'; modalEl.classList.remove('modal-visible');} };
    const _showPageNotification = window.showPageNotification || function(message, isError = false, modalInst = null, title = "Notification") { 
        const modalToUse = modalInst || document.getElementById("notificationModalGeneral");
        if(modalToUse && modalToUse.querySelector('#notificationModalGeneralMessage')) {
            modalToUse.querySelector('#notificationModalGeneralMessage').innerHTML = message;
            if(modalToUse.querySelector('#notificationModalGeneralTitle')) modalToUse.querySelector('#notificationModalGeneralTitle').textContent = title;
            _showModal(modalToUse);
        } else {
            alert(title + ": " + message); 
        }
    };
    const _decodeHtmlEntities = window.decodeHtmlEntities || function(text) { 
        if (typeof text !== 'string') return text;
        const ta = document.createElement('textarea'); ta.innerHTML = text; return ta.value;
    };
    const appRoot = typeof appRootPath === 'string' ? appRootPath : "";

    // --- Standard Element Selectors ---
    const addScheduleBtn = document.getElementById('addScheduleButton');      
    const editScheduleBtn = document.getElementById('editScheduleButton');    
    const deleteScheduleBtn = document.getElementById('deleteScheduleButton');  
    
    const addScheduleModalEl = document.getElementById('addScheduleModal');
    const addScheduleForm = document.getElementById('addScheduleForm');
    const addScheduleNameInput = document.getElementById('addScheduleName');
    const addAutoLunchCheckbox = document.getElementById('addAutoLunch');
    const addHoursRequiredInput = document.getElementById('addHoursRequired');
    const addLunchLengthInput = document.getElementById('addLunchLength');
    const closeAddModal_X_Sched = document.getElementById('closeAddModal');
    const cancelAddScheduleActualBtn = document.getElementById('cancelAddSchedule');

    const editScheduleModalEl = document.getElementById('editScheduleModal');
    const editScheduleForm = document.getElementById('editScheduleForm');
    const hiddenEditOriginalName = document.getElementById('hiddenEditOriginalName');
    const closeEditModal_X_Sched = document.getElementById('closeEditModal');
    const cancelEditScheduleActualBtn = document.getElementById('cancelEditSchedule');

    const schedulesTable = document.getElementById('schedulesTable');
    const schedulesTableBody = schedulesTable ? schedulesTable.querySelector("tbody") : null;
    
    const deleteForm = document.getElementById("deleteScheduleForm"); 
    const hiddenDeleteScheduleNameInput = deleteForm ? deleteForm.querySelector("#hiddenDeleteScheduleName") : null;
    const hiddenTargetScheduleForReassignmentInput = deleteForm ? deleteForm.querySelector("#hiddenTargetScheduleForReassignment") : null;

    const notificationModalGeneral = document.getElementById("notificationModalGeneral");

    const deleteReassignSchedModal = document.getElementById('deleteAndReassignSchedModal');
    const closeDeleteReassignSchedModalBtn = document.getElementById('closeDeleteReassignSchedModalBtn');
    const cancelDeleteReassignSchedBtn = document.getElementById('cancelDeleteReassignSchedBtn');
    const confirmDeleteAndReassignSchedBtn = document.getElementById('confirmDeleteAndReassignSchedBtn');
    const targetReassignSchedSelect = document.getElementById('targetReassignSchedSelect');
    const deleteReassignSchedModalMessage = document.getElementById('deleteReassignSchedModalMessage');
    
    let selectedRow = null; 
    let selectedScheduleData = null;
    let currentSchedNameToDeleteForModal = null;

    function escapeHtmlSched(unsafe) {
        if (typeof unsafe !== 'string') {
             return unsafe === null || typeof unsafe === 'undefined' ? "" : String(unsafe);
        }
        return unsafe.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }
    
    function toggleScheduleButtonState() { 
        const enable = selectedRow !== null && selectedScheduleData !== null;
        let disableDueToDefault = false;
        let title = enable ? "" : "Select a schedule first";
        if (enable && selectedScheduleData && selectedScheduleData.name) {
            const selectedNameLower = selectedScheduleData.name.toLowerCase();
            if (selectedNameLower.startsWith('open')) {
                disableDueToDefault = true; title = "Default 'Open' schedules cannot be deleted.";
            }
        }
        if (editScheduleBtn) { 
            // Allow editing of "Open with Auto Lunch"
            const isStrictlyOpen = enable && selectedScheduleData.name.toLowerCase() === 'open';
            editScheduleBtn.disabled = !enable || isStrictlyOpen; 
            editScheduleBtn.title = isStrictlyOpen ? "The 'Open' schedule cannot be edited." : (title || "Edit selected schedule");
        }
        if (deleteScheduleBtn) { 
            deleteScheduleBtn.disabled = !enable || disableDueToDefault; 
            deleteScheduleBtn.title = title || "Delete selected schedule"; 
        }
    }

    function selectScheduleRow(rowToSelect) {
        if (selectedRow && selectedRow !== rowToSelect) {
            selectedRow.classList.remove("selected");
        }
        if (rowToSelect) {
            if (selectedRow === rowToSelect) { 
                rowToSelect.classList.remove("selected"); selectedRow = null; selectedScheduleData = null;
            } else {
                rowToSelect.classList.add("selected"); selectedRow = rowToSelect; 
                // Deep copy dataset to avoid live reference issues
                selectedScheduleData = JSON.parse(JSON.stringify(rowToSelect.dataset)); 
            }
        } else { 
            if (selectedRow) selectedRow.classList.remove("selected");
            selectedRow = null; selectedScheduleData = null;
        }
        toggleScheduleButtonState();
    }
    
    function toggleAutoLunchFieldsInModal(checkbox, hoursInput, lengthInput) {
        if (!checkbox || !hoursInput || !lengthInput) { return; }
        const isChecked = checkbox.checked;
        hoursInput.disabled = !isChecked; lengthInput.disabled = !isChecked;
        hoursInput.required = isChecked; lengthInput.required = isChecked;
        if (!isChecked) { hoursInput.value = ''; lengthInput.value = ''; }
    }

    if (schedulesTableBody) { 
        schedulesTableBody.addEventListener("click", function(event) {
            let targetRow = event.target.closest('tr');
            if (targetRow && targetRow.parentNode === schedulesTableBody && 
                targetRow.dataset && typeof targetRow.dataset.name !== 'undefined' && 
                !targetRow.classList.contains('report-message-row') && 
                !targetRow.classList.contains('report-error-row')) {
                selectScheduleRow(targetRow); 
            } else if (targetRow && targetRow.parentNode === schedulesTableBody) { 
                selectScheduleRow(null); 
            }
        });
    }

    if (addScheduleBtn && addScheduleModalEl && addScheduleForm) { 
        addScheduleBtn.addEventListener("click", function() {
            addScheduleForm.reset();
            const nameInput = addScheduleForm.querySelector('#addScheduleName');
            if(nameInput) { nameInput.disabled = false; nameInput.readOnly = false; }
            const autoLunchCheck = addScheduleForm.querySelector('#addAutoLunch');
            if(autoLunchCheck) {
                autoLunchCheck.checked = false; 
                toggleAutoLunchFieldsInModal(autoLunchCheck, addScheduleForm.querySelector('#addHoursRequired'), addScheduleForm.querySelector('#addLunchLength')); 
            }
            if(typeof _showModal === 'function') _showModal(addScheduleModalEl);
            if(nameInput) setTimeout(() => nameInput.focus(), 150);
        });
        const addAutoLunchCheck = addScheduleForm.querySelector('#addAutoLunch');
        if(addAutoLunchCheck) { addAutoLunchCheck.addEventListener('change', function() { toggleAutoLunchFieldsInModal(this, addScheduleForm.querySelector('#addHoursRequired'), addScheduleForm.querySelector('#addLunchLength')); }); }
    }

    if (closeAddModal_X_Sched) { closeAddModal_X_Sched.addEventListener("click", () => _hideModal(addScheduleModalEl)); }
    if (cancelAddScheduleActualBtn) { cancelAddScheduleActualBtn.addEventListener("click", () => _hideModal(addScheduleModalEl)); }
    
    if (editScheduleBtn && editScheduleModalEl && editScheduleForm) { 
         editScheduleBtn.addEventListener("click", function() {
            if (editScheduleBtn.disabled || !selectedRow || !selectedScheduleData) return;
            
            editScheduleForm.reset(); 
            const data = selectedScheduleData; 
            const scheduleNameToEdit = _decodeHtmlEntities(data.name || "");

            if(hiddenEditOriginalName) hiddenEditOriginalName.value = data.name || ""; 
            
            const isOpenType = scheduleNameToEdit.toLowerCase().startsWith('open');
            const isStrictlyOpen = scheduleNameToEdit.toLowerCase() === 'open';

            const setField = (id, value) => { 
                const el = editScheduleForm.querySelector(`#${id}`); 
                if (el) { el.value = value || ""; }
            };
            
            const setAndDisableField = (id, value, disabled) => {
                const el = editScheduleForm.querySelector(`#${id}`);
                if (el) {
                    el.value = value || "";
                    el.disabled = disabled;
                }
            };

            setAndDisableField('editScheduleName', scheduleNameToEdit, true);
            setAndDisableField('editShiftStart', data.shiftStart, isOpenType);
            setAndDisableField('editLunchStart', data.lunchStart, isOpenType);
            setAndDisableField('editLunchEnd', data.lunchEnd, isOpenType);
            setAndDisableField('editShiftEnd', data.shiftEnd, isOpenType);
            
            setField('editHoursRequired', data.hoursRequired === "N/A" ? "" : data.hoursRequired);
            setField('editLunchLength', data.lunchLength === "N/A" ? "" : data.lunchLength);

            const autoLunchCheckbox = editScheduleForm.querySelector('#editAutoLunch');
            if(autoLunchCheckbox) {
                autoLunchCheckbox.checked = (String(data.autoLunch).toLowerCase() === 'true');
                autoLunchCheckbox.disabled = isStrictlyOpen;
                toggleAutoLunchFieldsInModal(autoLunchCheckbox, editScheduleForm.querySelector('#editHoursRequired'), editScheduleForm.querySelector('#editLunchLength'));
            }

            editScheduleForm.querySelectorAll('.checkbox-group input[name="days"]').forEach(cb => {
                const dayChar = cb.dataset.dayChar;
                cb.checked = data.daysWorked ? data.daysWorked.includes(dayChar) : false;
                cb.disabled = isOpenType;
            });

            _showModal(editScheduleModalEl); 
        });
        const editAutoLunchCheck = editScheduleForm.querySelector('#editAutoLunch');
        if(editAutoLunchCheck) { editAutoLunchCheck.addEventListener('change', function() { toggleAutoLunchFieldsInModal(this, editScheduleForm.querySelector('#editHoursRequired'), editScheduleForm.querySelector('#editLunchLength')); }); }
    }
    if (closeEditModal_X_Sched) { closeEditModal_X_Sched.addEventListener("click", () => { _hideModal(editScheduleModalEl); selectScheduleRow(null); }); }
    if (cancelEditScheduleActualBtn) { cancelEditScheduleActualBtn.addEventListener("click", () => { _hideModal(editScheduleModalEl); selectScheduleRow(null); }); }

    /**
     * FIX: Implemented logic to open the delete/reassign modal.
     */
    if (deleteScheduleBtn) {
        deleteScheduleBtn.addEventListener("click", function() {
            if (deleteScheduleBtn.disabled || !selectedScheduleData) {
                _showPageNotification("Please select a schedule to delete.", true, null, "Action Required");
                return;
            }

            currentSchedNameToDeleteForModal = selectedScheduleData.name;
            const decodedSchedNameForDisplay = _decodeHtmlEntities(currentSchedNameToDeleteForModal);
            
            if (deleteReassignSchedModalMessage) {
                deleteReassignSchedModalMessage.innerHTML = `You are about to delete schedule: <strong>${escapeHtmlSched(decodedSchedNameForDisplay)}</strong>.<br>If any employees are assigned, they must be moved to another schedule.`;
            }
            
            if (targetReassignSchedSelect) {
                targetReassignSchedSelect.innerHTML = ''; // Clear previous options
                const availableSchedules = window.allAvailableSchedulesForReassign || [];
                let optionsAdded = 0;
                availableSchedules.forEach(sched => {
                    if (sched.name !== currentSchedNameToDeleteForModal) {
                        const option = new Option(_decodeHtmlEntities(sched.name), sched.name);
                        targetReassignSchedSelect.add(option);
                        optionsAdded++;
                    }
                });
                
                if (optionsAdded === 0) {
                     _showPageNotification("You cannot delete the last schedule. Please add another schedule first to reassign employees to.", true, null, "Deletion Blocked");
                     return;
                }
            }
            
            _showModal(deleteReassignSchedModal);
        });
    }

    /**
     * FIX: Implemented logic to handle the final deletion confirmation and form submission.
     */
    if (confirmDeleteAndReassignSchedBtn) {
        confirmDeleteAndReassignSchedBtn.addEventListener('click', () => {
            if (!deleteForm || !hiddenDeleteScheduleNameInput || !hiddenTargetScheduleForReassignmentInput || !targetReassignSchedSelect) {
                console.error("Delete form elements are missing.");
                _showPageNotification("A critical error occurred. Cannot submit deletion request.", true);
                return;
            }
            
            const targetSchedule = targetReassignSchedSelect.value;
            if (!targetSchedule) {
                _showPageNotification("Please select a schedule to reassign employees to.", true, null, "Selection Required");
                return;
            }

            hiddenDeleteScheduleNameInput.value = currentSchedNameToDeleteForModal;
            hiddenTargetScheduleForReassignmentInput.value = targetSchedule;
            
            deleteForm.submit();
        });
    }

    if (closeDeleteReassignSchedModalBtn) { closeDeleteReassignSchedModalBtn.addEventListener('click', () => _hideModal(deleteReassignSchedModal)); }
    if (cancelDeleteReassignSchedBtn) { cancelDeleteReassignSchedBtn.addEventListener('click', () => _hideModal(deleteReassignSchedModal)); }
    
    if (notificationModalGeneral) {
        const okBtn = notificationModalGeneral.querySelector('#okButtonNotificationModalGeneral');
        const closeXBtn = notificationModalGeneral.querySelector('.close');
        if(okBtn) okBtn.addEventListener('click', () => _hideModal(notificationModalGeneral));
        if(closeXBtn) closeXBtn.addEventListener('click', () => _hideModal(notificationModalGeneral));
    }

    toggleScheduleButtonState();
});
