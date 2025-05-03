/**
 * scheduling.js
 * Handles interactivity for the Schedule Management page (scheduling.jsp).
 * Includes client-side check for duplicate schedule names on Add.
 * Includes notification handling from URL parameters.
 * Added more logging and error handling for debugging listener attachment.
 */

document.addEventListener('DOMContentLoaded', function() {
    console.log("Scheduling Page DOMContentLoaded");

    // --- Function Definitions (Moved to Top) ---

    // --- Modal Handling ---
    function showModal(modalElement) {
        if (modalElement) { modalElement.classList.add('modal-visible'); console.log("Showing modal:", modalElement.id); }
        else { console.error("Attempted to show a null modal element."); }
    }
    function hideModal(modalElement) {
        if (modalElement) { modalElement.classList.remove('modal-visible'); console.log("Hiding modal:", modalElement.id); }
        else { console.error("Attempted to hide a null modal element."); }
    }

    // --- Notification Modal Function ---
    function showNotification(message) {
        // Get refs inside function to ensure modal exists when called
        const notificationModal_func = document.getElementById("notificationModal"); // Use distinct name or ensure no conflict
        const notificationMessage_func = document.getElementById("notificationMessage");
        if (notificationMessage_func && notificationModal_func) {
            notificationMessage_func.textContent = message;
            showModal(notificationModal_func); // Use local showModal definition
        } else { console.error("Notification modal elements not found inside showNotification! Message:", message); alert(message); }
    }

    // --- Helper function to clear URL params ---
    function clearUrlParams() {
        try {
            const currentUrl = window.location.pathname;
            window.history.replaceState({}, document.title, currentUrl);
        } catch (e) { console.error("Error clearing URL params:", e); }
    }

    // --- Global Variable ---
    let selectedRow = null; // Holds the currently selected <tr> element

    // --- Row Selection Function --- (Define before use)
    function selectRow(rowElement) {
        console.log("selectRow called on:", rowElement);
        if (selectedRow) {
            selectedRow.classList.remove("selected");
        }
        rowElement.classList.add("selected");
        selectedRow = rowElement;
        // Check if toggleButtonState exists before calling
        if (typeof toggleButtonState === 'function') {
             toggleButtonState();
        } else { console.error("toggleButtonState function not defined when selectRow called."); }
    }

    // --- Button State Function --- (Define before use)
    function toggleButtonState() {
        try {
            const editBtn = document.getElementById("editScheduleButton");   // !!! Use your ACTUAL Edit button ID !!!
            const deleteBtn = document.getElementById("deleteScheduleButton"); // !!! Use your ACTUAL Delete button ID !!!
            let disableButtons = !selectedRow;
            let buttonTitle = '';
            const defaultScheduleTitle = "Cannot edit/delete default schedules (Open / Open with Auto Lunch)";
            const noSelectionTitle = "Select a schedule first";

            if (!selectedRow) {
                disableButtons = true;
                buttonTitle = noSelectionTitle;
            } else if (selectedRow.dataset && selectedRow.dataset.name) {
                const selectedNameLower = selectedRow.dataset.name.toLowerCase();
                if (selectedNameLower === 'open' || selectedNameLower === 'open with auto lunch') {
                    disableButtons = true;
                    buttonTitle = defaultScheduleTitle;
                }
            } else {
                disableButtons = true;
                buttonTitle = "Cannot read data from selected row";
            }

            if (editBtn) { editBtn.disabled = disableButtons; editBtn.title = buttonTitle; }
            else { console.warn("Edit button not found in toggleButtonState"); }
            if (deleteBtn) { deleteBtn.disabled = disableButtons; deleteBtn.title = buttonTitle; }
            else { console.warn("Delete button not found in toggleButtonState"); }

            console.log("Buttons updated. Edit disabled:", editBtn?.disabled, "Delete disabled:", deleteBtn?.disabled, "Title:", buttonTitle);
        } catch(e) { console.error("ERROR in toggleButtonState:", e); }
    }

     // --- Auto Lunch Toggle Function --- (Define before use)
     function toggleAutoLunchFields(checkboxElem, hoursInput, lengthInput) {
         try {
             if (!checkboxElem) return;
             const isChecked = checkboxElem.checked;
             console.log("Auto Lunch toggle state:", isChecked);
             if (hoursInput) { hoursInput.disabled = !isChecked; if (!isChecked) hoursInput.value = ''; }
             else { console.warn("Hours input not found for toggle"); }
             if (lengthInput) { lengthInput.disabled = !isChecked; if (!isChecked) lengthInput.value = ''; }
             else { console.warn("Length input not found for toggle"); }
         } catch(e) { console.error("ERROR in toggleAutoLunchFields:", e); }
     }


    // --- Get Element References (Wrap in try/catch) ---
    console.log("Getting element references...");
    let scheduleTable, addScheduleButton, editScheduleButton, deleteScheduleButton,
        addScheduleModal, editScheduleModal, addScheduleForm, editScheduleForm,
        deleteScheduleForm, closeAddBtn, cancelAddBtn, closeEditBtn, cancelEditBtn,
        notificationModal, notificationMessage, closeNotification, okButton,
        addAutoLunchCheckbox, addHoursRequiredInput, addLunchLengthInput,
        editAutoLunchCheckbox, hiddenDeleteInput; // Declare vars

    try {
        // !!! Replace ALL these IDs with your ACTUAL element IDs !!!
        scheduleTable = document.getElementById('schedulesTable');
        addScheduleButton = document.getElementById('addScheduleButton');
        editScheduleButton = document.getElementById('editScheduleButton');
        deleteScheduleButton = document.getElementById('deleteScheduleButton');
        addScheduleModal = document.getElementById('addScheduleModal');
        editScheduleModal = document.getElementById('editScheduleModal');
        addScheduleForm = document.getElementById('addScheduleForm');
        editScheduleForm = document.getElementById('editScheduleForm');
        deleteScheduleForm = document.getElementById('deleteScheduleForm');
        closeAddBtn = document.getElementById('closeAddModal');
        cancelAddBtn = document.getElementById('cancelAdd');
        closeEditBtn = document.getElementById('closeEditModal');
        cancelEditBtn = document.getElementById('cancelEdit');
        notificationModal = document.getElementById("notificationModal");
        notificationMessage = document.getElementById("notificationMessage");
        closeNotification = document.getElementById("closeNotificationModal");
        okButton = document.getElementById("okButton");
        addAutoLunchCheckbox = document.getElementById('addAutoLunch');
        addHoursRequiredInput = document.getElementById('addHoursRequired');
        addLunchLengthInput = document.getElementById('addLunchLength');
        editAutoLunchCheckbox = document.getElementById('editAutoLunch'); // Might be null if Edit Modal HTML missing
        hiddenDeleteInput = document.getElementById("hiddenDeleteScheduleName");
        console.log("Finished getting element references.");
    } catch(e) {
        console.error("ERROR occurred while getting element references:", e);
        // Stop further execution if critical elements are missing?
        // Or let individual listeners fail gracefully? Let listeners handle null checks.
    }


    // --- Notification Handling (Wrapped in try/catch) ---
    console.log("Checking URL parameters for notifications...");
    try {
        const urlParams = new URLSearchParams(window.location.search);
        const addSuccess = urlParams.get('addSuccess');
        const editSuccess = urlParams.get('editSuccess');
        const deleteSuccess = urlParams.get('deleteSuccess');
        const errorMessage = urlParams.get('error'); // Declared now
        let notificationShown = false;

        if ((addSuccess !== null || editSuccess !== null || deleteSuccess !== null || errorMessage !== null) && !notificationShown) {
            let message = "";
             // Build message logic (same as before)
            if (addSuccess === 'true') message = "Schedule added successfully!";
            else if (editSuccess === 'true') message = "Schedule updated successfully!";
            else if (deleteSuccess === 'true') message = "Schedule deleted successfully!";
            else if (addSuccess === 'false') message = "Failed to add schedule. " + (errorMessage ? decodeURIComponent(errorMessage.replace(/\+/g, ' ')) : "Please check details.");
            else if (editSuccess === 'false') message = "Failed to update schedule. " + (errorMessage ? decodeURIComponent(errorMessage.replace(/\+/g, ' ')) : "Please check details.");
            else if (deleteSuccess === 'false') message = "Failed to delete schedule. " + (errorMessage ? decodeURIComponent(errorMessage.replace(/\+/g, ' ')) : "Please check details.");
            else if (errorMessage) message = "An error occurred: " + decodeURIComponent(errorMessage.replace(/\+/g, ' '));

            if (message) {
                console.log("Attempting to show notification:", message);
                showNotification(message); // Call function defined above
                clearUrlParams(); // Call function defined above
                notificationShown = true;
            } else { console.log("No notification message to show from URL params."); }
        } else { console.log("No relevant URL parameters found for notification."); }
    } catch (e) { console.error("ERROR during notification handling block:", e); }
    console.log("Finished notification handling.");
    // --- End Notification Handling ---


    // --- Event Listener: Row Selection (Wrap attachment in try/catch) ---
    console.log("Attempting to attach row click listener...");
    try {
        if (scheduleTable && scheduleTable.querySelector('tbody')) {
             const tbody = scheduleTable.querySelector('tbody');
             tbody.addEventListener("click", function(event) {
                 console.log("DEBUG: Click detected inside tbody.");
                 let target = event.target;
                 while (target && target.tagName !== "TR") {
                     if (target === tbody) { target = null; break; }
                     target = target.parentNode;
                 }
                 if (target && target.parentNode === tbody) {
                     console.log("DEBUG: Clicked on TR element:", target);
                     selectRow(target); // Assumes selectRow is defined
                 } else { console.log("DEBUG: Click was not on a direct TR child of tbody."); }
             });
             console.log("Row click listener attached successfully to tbody.");
        } else { console.warn("Schedule table or tbody not found. Cannot attach row click listener."); }
    } catch(e) { console.error("ERROR attaching row click listener:", e); }
     // --- End Row Selection ---


    // --- Attach other listeners (Wrap each block in try/catch) ---
     console.log("Attaching other listeners...");
     try { if (addScheduleButton && addScheduleModal && addScheduleForm) { addScheduleButton.addEventListener('click', function() { /* Add modal logic */ console.log("Add Schedule button clicked"); addScheduleForm.reset(); if (addAutoLunchCheckbox) { addAutoLunchCheckbox.checked = false; toggleAutoLunchFields(addAutoLunchCheckbox, addHoursRequiredInput, addLunchLengthInput); } showModal(addScheduleModal); const firstInput = addScheduleForm.querySelector('input[type="text"]'); if (firstInput) firstInput.focus(); }); console.log("Add btn listener attached."); } else { console.warn("Add btn/modal/form not found."); } } catch(e) { console.error("Err attaching Add btn listener", e); }
     try { if (editScheduleButton && editScheduleModal && editScheduleForm) { editScheduleButton.addEventListener('click', function() { /* Edit modal logic from previous correct version */ console.log("Edit Schedule button clicked. Selected row:", selectedRow); const currentSelectedRow = selectedRow; if (!currentSelectedRow) { showNotification('Please select...'); return; } const scheduleData = currentSelectedRow.dataset; if (!scheduleData || typeof scheduleData.name === 'undefined') { showNotification('Error reading data...'); return; } const scheduleName = scheduleData.name; if (scheduleName && (scheduleName.toLowerCase() === 'open' || scheduleName.toLowerCase() === 'open with auto lunch')) { showNotification('Cannot edit default...'); return; } const nameInput = document.getElementById('editScheduleName'); const shiftStartInput = document.getElementById('editShiftStart'); const lunchStartInput = document.getElementById('editLunchStart'); const lunchEndInput = document.getElementById('editLunchEnd'); const shiftEndInput = document.getElementById('editShiftEnd'); const autoLunchCheckbox_edit = document.getElementById('editAutoLunch'); const hoursRequiredInput_edit = document.getElementById('editHoursRequired'); const lunchLengthInput_edit = document.getElementById('editLunchLength'); const workScheduleSelect = document.getElementById('editWorkSchedule'); const originalNameInput = document.getElementById('hiddenEditOriginalName'); const daysCheckboxes = { Sun: document.getElementById('editDaySun'), Mon: document.getElementById('editDayMon'), Tue: document.getElementById('editDayTue'), Wed: document.getElementById('editDayWed'), Thu: document.getElementById('editDayThu'), Fri: document.getElementById('editDayFri'), Sat: document.getElementById('editDaySat')}; if (!nameInput || !autoLunchCheckbox_edit || !hoursRequiredInput_edit || !lunchLengthInput_edit) { console.error("Edit Modal Error: Essential elements missing."); showNotification("Error initializing edit form."); return; } nameInput.value = scheduleData.name || ''; if (originalNameInput) originalNameInput.value = scheduleData.name || ''; if (shiftStartInput) shiftStartInput.value = scheduleData.shiftStart || null; if (lunchStartInput) lunchStartInput.value = scheduleData.lunchStart || null; if (lunchEndInput) lunchEndInput.value = scheduleData.lunchEnd || null; if (shiftEndInput) shiftEndInput.value = scheduleData.shiftEnd || null; const daysWorkedString = scheduleData.daysWorked || ""; const workedDaysSet = new Set(daysWorkedString.split(',').map(day => day.trim()).filter(day => day)); for (const dayKey in daysCheckboxes) { const checkbox = daysCheckboxes[dayKey]; if (checkbox) { checkbox.checked = workedDaysSet.has(checkbox.value); } } autoLunchCheckbox_edit.checked = (scheduleData.autoLunch === 'true'); hoursRequiredInput_edit.value = scheduleData.hoursRequired || '0'; lunchLengthInput_edit.value = scheduleData.lunchLength || '0'; if (workScheduleSelect) workScheduleSelect.value = scheduleData.workSchedule || ''; console.log("Populated edit form."); toggleAutoLunchFields(autoLunchCheckbox_edit, hoursRequiredInput_edit, lunchLengthInput_edit); const isNameOpen = (scheduleData.name === 'Open'); if (shiftStartInput) shiftStartInput.disabled = isNameOpen; if (lunchStartInput) lunchStartInput.disabled = isNameOpen; if (lunchEndInput) lunchEndInput.disabled = isNameOpen; if (shiftEndInput) shiftEndInput.disabled = isNameOpen; for (const dayKey in daysCheckboxes) { if (daysCheckboxes[dayKey]) { daysCheckboxes[dayKey].disabled = isNameOpen; } } nameInput.readOnly = isNameOpen; console.log("Applied disabled states."); showModal(editScheduleModal); if (nameInput && !nameInput.readOnly) { nameInput.focus(); }}); console.log("Edit btn listener attached."); } else { console.warn("Edit btn/modal/form not found."); } } catch(e) { console.error("Err attaching Edit btn listener", e); }
     try { if (deleteScheduleButton && deleteScheduleForm && hiddenDeleteInput) { deleteScheduleButton.addEventListener('click', function() { /* Delete logic */ const currentSelectedRow = selectedRow; if (currentSelectedRow) { const scheduleData = currentSelectedRow.dataset; const scheduleName = scheduleData ? scheduleData.name : null; if (scheduleName) { const scheduleNameLower = scheduleName.toLowerCase(); if (scheduleNameLower === 'open' || scheduleNameLower === 'open with auto lunch') { showNotification("Cannot delete default..."); return; } if (confirm("Delete schedule: " + scheduleName + "?")) { hiddenDeleteInput.value = scheduleName; deleteScheduleForm.submit(); } } else { showNotification("Cannot get name..."); } } else { showNotification("Select schedule..."); } }); console.log("Del btn listener attached."); } else { console.warn("Del btn/form/input not found."); } } catch(e) { console.error("Err attaching Del btn listener", e); }
     try { if (addAutoLunchCheckbox) { addAutoLunchCheckbox.addEventListener('change', function() { const addHoursInput = document.getElementById('addHoursRequired'); const addLengthInput = document.getElementById('addLunchLength'); toggleAutoLunchFields(this, addHoursInput, addLengthInput); }); console.log("Add toggle listener attached."); } else { console.warn("Add toggle cb not found."); } } catch(e) { console.error("Err attaching Add toggle listener", e); }
     try { if (editAutoLunchCheckbox) { editAutoLunchCheckbox.addEventListener('change', function() { const hoursInput = document.getElementById('editHoursRequired'); const lengthInput = document.getElementById('editLunchLength'); toggleAutoLunchFields(this, hoursInput, lengthInput); }); console.log("Edit toggle listener attached."); } else { console.warn("Edit toggle cb not found."); } } catch(e) { console.error("Err attaching Edit toggle listener", e); }
     // Modal Closing Listeners
     try { if (addScheduleModal && closeAddBtn) closeAddBtn.addEventListener('click', function() { hideModal(addScheduleModal); }); } catch(e) { console.error("Err attaching closeAdd listener", e); }
     try { if (addScheduleModal && cancelAddBtn) cancelAddBtn.addEventListener('click', function() { hideModal(addScheduleModal); }); } catch(e) { console.error("Err attaching cancelAdd listener", e); }
     try { if (editScheduleModal && closeEditBtn) closeEditBtn.addEventListener('click', function() { hideModal(editScheduleModal); }); } catch(e) { console.error("Err attaching closeEdit listener", e); }
     try { if (editScheduleModal && cancelEditBtn) cancelEditBtn.addEventListener('click', function() { hideModal(editScheduleModal); }); } catch(e) { console.error("Err attaching cancelEdit listener", e); }
     try { if (notificationModal && closeNotification) closeNotification.addEventListener("click", function() { hideModal(notificationModal); }); } catch(e) { console.error("Err attaching closeNotif listener", e); }
     try { if (okButton) okButton.addEventListener("click", function() { if(notificationModal) hideModal(notificationModal); }); } catch(e) { console.error("Err attaching okNotif listener", e); }

    console.log("Finished attaching other listeners.");


    // --- Form Submit Listeners (Wrap in try/catch) ---
     try { if(addScheduleForm) { addScheduleForm.addEventListener("submit", function(event){ /* Add form submit logic with duplicate check */ console.log("Add form submit event triggered."); const nameInput_add = document.getElementById('addScheduleName'); const currentScheduleTable = document.getElementById('schedulesTable'); if (!nameInput_add || !currentScheduleTable || !currentScheduleTable.querySelector('tbody')) { console.error("Add valid cannot proceed: Missing elements."); showNotification("Error validating form."); event.preventDefault(); return; } const enteredName = nameInput_add.value.trim(); const enteredNameLower = enteredName.toLowerCase(); let nameExists = false; if (enteredName === "") { showNotification("Name empty."); event.preventDefault(); return; } if (enteredNameLower === 'open' || enteredNameLower === 'open with auto lunch') { showNotification("Cannot use default name."); event.preventDefault(); return; } const tableRows = currentScheduleTable.querySelectorAll('tbody tr'); for (let i = 0; i < tableRows.length; i++) { const row = tableRows[i]; const rowName = row.dataset.name ? row.dataset.name.toLowerCase() : ''; if (rowName === enteredNameLower) { nameExists = true; break; } } if (nameExists) { event.preventDefault(); showNotification("Name exists."); console.log("Client valid failed: Duplicate name."); } else { console.log("Client valid passed: Allowing submission."); } }); console.log("Add form submit listener attached."); } else { console.warn("Add form not found for submit listener."); } } catch(e) { console.error("Err attaching Add form submit", e); }
     try { if (editScheduleForm) { editScheduleForm.addEventListener("submit", function(e){ console.log("Edit form submit"); /* Add validation? */ }); console.log("Edit form submit listener attached."); } else { console.warn("Edit form not found for submit listener."); } } catch(e) { console.error("Err attaching Edit form submit", e); }


    // --- Initial State ---
    console.log("Setting initial state...");
    try {
         toggleButtonState();
         if(addAutoLunchCheckbox) {
            const addHoursInput = document.getElementById('addHoursRequired');
            const addLengthInput = document.getElementById('addLunchLength');
            toggleAutoLunchFields(addAutoLunchCheckbox, addHoursInput, addLengthInput);
         } else { console.warn("Add modal cb not found for initial toggle."); }
         console.log("Initial button states set.");
    } catch(e) { console.error("ERROR setting initial state:", e); }

    console.log("Scheduling Page DOMContentLoaded setup complete.");
}); // End DOMContentLoaded