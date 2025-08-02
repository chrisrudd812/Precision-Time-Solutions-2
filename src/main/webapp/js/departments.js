// js/departments.js - vFix_NoneSelection
document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] departments.js (vFix_NoneSelection) loaded.");
    
    // --- Element Selectors ---
    const addDepartmentButton = document.getElementById('addDepartmentButton');
    const editDepartmentButton = document.getElementById('editDepartmentButton');
    const deleteDepartmentButton = document.getElementById('deleteDepartmentButton');
    const addDepartmentModal = document.getElementById('addDepartmentModal');
    const addDepartmentForm = document.getElementById('addDepartmentForm');
    const addDeptNameInput = document.getElementById('addDeptName');
    const closeAddDeptModalX = document.getElementById('closeAddDeptModalX');
    const cancelAddDeptButton = document.getElementById('cancelAddDeptButton');
    const editDepartmentModal = document.getElementById('editDepartmentModal');
    const editDepartmentForm = document.getElementById('editDepartmentForm');
    const editDeptNameInput = document.getElementById('editDeptNameDisplay'); 
    const originalDeptNameInput = document.getElementById('originalDeptName');
    const closeEditDeptModalX = document.getElementById('closeEditDeptModalX');
    const cancelEditDeptButton = document.getElementById('cancelEditDeptButton');
    const departmentsTable = document.getElementById('departmentsTable');
    const tableBody = departmentsTable ? departmentsTable.querySelector('tbody') : null;
    const deleteDepartmentForm = document.getElementById('deleteDepartmentForm');
    const hiddenDeleteDepartmentName = deleteDepartmentForm ? deleteDepartmentForm.querySelector('#hiddenDeleteDepartmentName') : null;
    const notificationModalGeneral = document.getElementById("notificationModalGeneral");
    const okBtnGeneralNotify = document.getElementById('okButtonNotificationModalGeneral');
    const generalModalCloseX = notificationModalGeneral ? notificationModalGeneral.querySelector('#closeNotificationModalGeneralXBtn_Dept') || notificationModalGeneral.querySelector('.close[data-close-modal-id="notificationModalGeneral"]') : null;
    const deleteReassignModal = document.getElementById('deleteAndReassignDeptModal');
    const closeDeleteReassignModalBtn = document.getElementById('closeDeleteReassignModalBtn');
    const cancelDeleteReassignBtn = document.getElementById('cancelDeleteReassignBtn');
    const confirmDeleteAndReassignBtn = document.getElementById('confirmDeleteAndReassignBtn');
    const targetReassignDeptSelect = document.getElementById('targetReassignDeptSelect');
    const deleteReassignModalMessage = document.getElementById('deleteReassignModalMessage');
    const deleteReassignModalError = document.getElementById('deleteReassignModalError');
    const wizardGenericModal = document.getElementById('wizardGenericModal');
    const wizardTitleElement = document.getElementById('wizardGenericModalTitle');
    const wizardText1Element = document.getElementById('wizardGenericModalText1');
    const wizardText2Element = document.getElementById('wizardGenericModalText2');
    const wizardButtonRow = document.getElementById('wizardGenericModalButtonRow');
    const closeWizardGenericModalBtn = document.getElementById('closeWizardGenericModal');
    const departmentTableContainer = document.getElementById('departmentTableContainer');
    const mainActionButtonsContainer = document.getElementById('button-container');
    const instructionTextH4 = document.getElementById('instructionTextH4');

    let selectedDeptRow = null;
    let selectedDeptData = null;
    let currentDeptNameToDeleteForModal = null;
    let wizardOpenedAddModal = false;
    let currentGlobalWizardStep = typeof CURRENT_WIZARD_STEP_DEPARTMENTS !== 'undefined' ? CURRENT_WIZARD_STEP_DEPARTMENTS : null;
    const appRoot = typeof appRootPath === 'string' ? appRootPath : "";

    // --- Helper Functions ---
    function _decodeLocal(text) {
        if (typeof window.decodeHtmlEntities === 'function') { return window.decodeHtmlEntities(text); }
        if (typeof text !== 'string') return text === null || typeof text === 'undefined' ? "" : String(text);
        const ta = document.createElement('textarea'); ta.innerHTML = text; return ta.value;
    }
    function _showModal(modalEl) {
        if (modalEl) {
            if (typeof window.showModal === 'function') { window.showModal(modalEl); } 
            else { modalEl.style.display = 'flex'; modalEl.classList.add('modal-visible'); }
        }
    }
    function _hideModal(modalEl) {
        if (modalEl) {
            if (typeof window.hideModal === 'function') { window.hideModal(modalEl); } 
            else { modalEl.style.display = 'none'; modalEl.classList.remove('modal-visible'); }
        }
    }    
    function _hideAddModalAndHandleWizard() {
        _hideModal(addDepartmentModal);
        if (IS_WIZARD_MODE_DEPARTMENTS && wizardOpenedAddModal) {
            wizardOpenedAddModal = false; 
            const nonNoneRows = tableBody ? Array.from(tableBody.querySelectorAll('tr[data-name]')).filter(r => r.dataset.name && r.dataset.name.toLowerCase() !== 'none').length : 0;
            if (typeof DEPARTMENT_JUST_ADDED_WIZARD !== 'undefined' && DEPARTMENT_JUST_ADDED_WIZARD === true || nonNoneRows > 0) {
                 updateWizardModalView("departments_after_add");
            } else {
                 updateWizardModalView("departments_initial");
            }
        } else {
            selectDeptRow(null); 
        }
    }
    function _showPageNotification(message, isError = false, title = "Notification") {
        if (typeof window.showPageNotification === 'function') {
             window.showPageNotification(message, isError, notificationModalGeneral, title);
             return;
        }
        const titleElem = notificationModalGeneral ? notificationModalGeneral.querySelector("#notificationModalGeneralTitle") : null;
        const msgElem = notificationModalGeneral ? notificationModalGeneral.querySelector("#notificationModalGeneralMessage") : null;
        if (titleElem) titleElem.textContent = title;
        if (msgElem) msgElem.innerHTML = message;
        if (notificationModalGeneral) _showModal(notificationModalGeneral);
        else alert((isError ? "Error: " : "Info: ") + title + "\n" + message.replace(/<br\s*\/?>/gi, '\n'));
    }    
    function escapeHtml(unsafe) {
        if (typeof unsafe !== 'string') return unsafe === null || typeof unsafe === 'undefined' ? "" : String(unsafe);
        return unsafe.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }

    const companyNameToDisplayJS = (typeof COMPANY_NAME_SIGNUP_JS !== 'undefined' && COMPANY_NAME_SIGNUP_JS && COMPANY_NAME_SIGNUP_JS.trim() !== "" && COMPANY_NAME_SIGNUP_JS !== "Your Company") ? COMPANY_NAME_SIGNUP_JS : "your new company";
    const wizardStages = {
        "departments_initial": {
            title: "Setup: Departments",
            text1: `Let's set up departments for <strong>${escapeHtml(companyNameToDisplayJS)}</strong>.`,
            text2: "You can add some now, or proceed to the next step. Departments can always be managed later via Navbar > Departments.",
            buttons: [
                { id: "wizardActionAddDept", text: "Add Departments Now", class: "text-green", actionKey: "openAddDeptModalViaWizard" },
                { id: "wizardActionNextToSchedules", text: "Next: Schedules", class: "text-blue", actionKey: "advanceToSchedules" }
            ]
        },
        "departments_after_add": {
            title: "Setup: Departments",
            text1: "Department added successfully!",
            text2: "Would you like to add another department, or proceed to schedule setup?",
            buttons: [
                { id: "wizardActionAddAnotherDept", text: "Add Another Department", class: "text-green", actionKey: "openAddDeptModalViaWizard" },
                { id: "wizardActionNextAfterAddToSchedules", text: "Next: Schedules", class: "text-blue", actionKey: "advanceToSchedules" }
            ]
        }
    };

    function showMainPageContent(show = true) {
        const displayStyle = show ? '' : 'none'; 
        if(departmentTableContainer) departmentTableContainer.style.display = show ? '' : 'none';
        if(mainActionButtonsContainer) mainActionButtonsContainer.style.display = show ? 'flex' : 'none';
        if(instructionTextH4) instructionTextH4.style.display = show ? 'block' : 'none';
    }

    function toggleActionButtons() {
        const isRowSelected = selectedDeptRow !== null;
        let titleMessage = isRowSelected ? "" : "Select a department first.";
        let disableActions = !isRowSelected;
        // This check is now mostly redundant due to the new logic in selectDeptRow, but it's good for defense-in-depth.
        if (isRowSelected && selectedDeptData && selectedDeptData.name && selectedDeptData.name.toLowerCase() === 'none') {
            titleMessage = "The default 'None' department cannot be edited or deleted.";
            disableActions = true;
        }
        if (editDepartmentButton) { editDepartmentButton.disabled = disableActions; editDepartmentButton.title = titleMessage || "Edit selected department"; }
        if (deleteDepartmentButton) { deleteDepartmentButton.disabled = disableActions; deleteDepartmentButton.title = titleMessage || "Delete selected department"; }
    }

    /**
     * Handles selecting or deselecting a table row.
     * FIX: Prevents selection of the 'None' department and shows a notification instead.
     * @param {HTMLElement | null} row The table row element that was clicked.
     */
    function selectDeptRow(row) {
        // If a row is clicked and it's the 'None' department, show a notification and stop.
        if (row && row.dataset && typeof row.dataset.name !== 'undefined' && row.dataset.name.toLowerCase() === 'none') {
            _showPageNotification("'None' is a system default and cannot be edited or deleted.", false, "System Default");
            
            // If another row was previously selected, unselect it visually.
            if (selectedDeptRow) {
                selectedDeptRow.classList.remove('selected');
            }
            // Reset selection state and disable action buttons.
            selectedDeptRow = null;
            selectedDeptData = null;
            toggleActionButtons();
            return; // Exit the function to prevent the 'None' row from being selected.
        }

        // --- Original logic for all other rows ---
        if (selectedDeptRow && selectedDeptRow !== row) {
            selectedDeptRow.classList.remove('selected');
        }

        if (row && row.dataset && typeof row.dataset.name !== 'undefined') {
            // If the clicked row is the currently selected one, deselect it.
            if (row === selectedDeptRow) { 
                row.classList.remove('selected');
                selectedDeptRow = null;
                selectedDeptData = null;
            } else { // Otherwise, select the new row.
                row.classList.add('selected');
                selectedDeptRow = row;
                selectedDeptData = {
                    name: _decodeLocal(row.dataset.name),
                    description: _decodeLocal(row.dataset.description || ""),
                    supervisor: _decodeLocal(row.dataset.supervisor || "")
                };
            }
        } else { // If click was outside a valid row, deselect everything.
            if(selectedDeptRow) selectedDeptRow.classList.remove('selected');
            selectedDeptRow = null;
            selectedDeptData = null;
        }
        toggleActionButtons();
    }

    function openAddDepartmentModal() {
        if (addDepartmentModal && addDepartmentForm) {
            addDepartmentForm.reset();
            _showModal(addDepartmentModal);
            if (addDeptNameInput) {
                setTimeout(() => { addDeptNameInput.focus(); }, 150);
            }
        }
    }

    function populateAndShowEditModalSimple() {
        if (!editDepartmentModal || !editDepartmentForm || !selectedDeptData) return; 
        editDepartmentForm.reset();
        if (originalDeptNameInput) originalDeptNameInput.value = selectedDeptData.name || "";
        const editDescInput = editDepartmentModal.querySelector('#editDeptDescription');
        const editSuperInput = editDepartmentModal.querySelector('#editDeptSupervisor');
        if (editDeptNameInput) { editDeptNameInput.value = selectedDeptData.name || ""; editDeptNameInput.disabled = true; }
        if(editDescInput) editDescInput.value = selectedDeptData.description || "";
        if(editSuperInput) editSuperInput.value = selectedDeptData.supervisor || "";
        _showModal(editDepartmentModal);
        if (editDescInput) { setTimeout(() => editDescInput.focus(), 150); }
    }

    function updateWizardModalView(stageKey) {
        if (!wizardGenericModal) { showMainPageContent(true); return; }
        const stageConfig = wizardStages[stageKey];
        if (!stageConfig) { _hideModal(wizardGenericModal); showMainPageContent(true); return; }
        currentGlobalWizardStep = stageKey; 
        if(wizardTitleElement) wizardTitleElement.textContent = stageConfig.title;
        if(wizardText1Element) wizardText1Element.innerHTML = stageConfig.text1; 
        if(wizardText2Element) wizardText2Element.innerHTML = stageConfig.text2;
        if(wizardButtonRow) wizardButtonRow.innerHTML = ''; 

        stageConfig.buttons.forEach(btnConfig => {
            const button = document.createElement('button');
            button.type = 'button'; button.id = btnConfig.id;
            button.className = `glossy-button ${btnConfig.class}`;
            button.innerHTML = btnConfig.text;
            switch (btnConfig.actionKey) {
                case "openAddDeptModalViaWizard": button.addEventListener('click', handleWizardAddDeptClick); break;
                case "advanceToSchedules": button.addEventListener('click', () => advanceWizardToServerAndRedirect("schedules_prompt", `scheduling.jsp?setup_wizard=true`)); break;
                default: console.warn("Unknown wizard button actionKey:", btnConfig.actionKey);
            }
            if(wizardButtonRow) wizardButtonRow.appendChild(button);
        });
        showMainPageContent(stageKey === "departments_after_add"); 
        _showModal(wizardGenericModal);
    }

    const handleWizardAddDeptClick = () => {
        wizardOpenedAddModal = true; 
        _hideModal(wizardGenericModal);
        showMainPageContent(true); 
        openAddDepartmentModal(); 
    };
    
    function advanceWizardToServerAndRedirect(serverNextStep, redirectUrl) {
         fetch(`WizardStatusServlet`, {
            method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': serverNextStep})
        })
        .then(response => response.ok ? response.json() : Promise.reject('Failed to set wizard step'))
        .then(data => {
            if (data.success) { window.location.href = redirectUrl; } 
            else { _showPageNotification("Could not proceed: " + (data.error || "Server error"), true, "Setup Error"); }
        })
        .catch(error => { _showPageNotification("Network error advancing wizard. Please try again.", true, "Network Error"); });
    }
    
    if (typeof IS_WIZARD_MODE_DEPARTMENTS !== 'undefined' && IS_WIZARD_MODE_DEPARTMENTS === true) {
        if (currentGlobalWizardStep === "departments" || currentGlobalWizardStep === "departments_initial") {
            updateWizardModalView( (typeof DEPARTMENT_JUST_ADDED_WIZARD !== 'undefined' && DEPARTMENT_JUST_ADDED_WIZARD === true) ? "departments_after_add" : "departments_initial" );
        } else if (currentGlobalWizardStep === "departments_after_add") {
             updateWizardModalView("departments_after_add");
        } else {
             showMainPageContent(true); 
        }
    } else {
        showMainPageContent(true);
    }
    
    // --- Event Listeners ---
    if (addDepartmentButton) addDepartmentButton.addEventListener('click', openAddDepartmentModal);
    if (editDepartmentButton) editDepartmentButton.addEventListener('click', () => { if (!editDepartmentButton.disabled && selectedDeptData) { populateAndShowEditModalSimple(); }});
    if (closeAddDeptModalX) closeAddDeptModalX.addEventListener('click', _hideAddModalAndHandleWizard);
    if (cancelAddDeptButton) cancelAddDeptButton.addEventListener('click', _hideAddModalAndHandleWizard);
    if (closeEditDeptModalX) closeEditDeptModalX.addEventListener('click', () => { _hideModal(editDepartmentModal); selectDeptRow(null); });
    if (cancelEditDeptButton) cancelEditDeptButton.addEventListener('click', () => { _hideModal(editDepartmentModal); selectDeptRow(null); });
    if (tableBody) tableBody.addEventListener('click', (event) => selectDeptRow(event.target.closest('tr')));
    if (deleteDepartmentButton) {
        deleteDepartmentButton.addEventListener('click', () => {
             if (deleteDepartmentButton.disabled || !selectedDeptData || (selectedDeptData.name && selectedDeptData.name.toLowerCase() === 'none')) return;
             currentDeptNameToDeleteForModal = selectedDeptData.name;
             if (deleteReassignModalMessage) deleteReassignModalMessage.innerHTML = `You are about to delete department: <strong>${escapeHtml(_decodeLocal(currentDeptNameToDeleteForModal))}</strong>.`;
             if (targetReassignDeptSelect) {
                targetReassignDeptSelect.innerHTML = '';
                (window.allAvailableDepartmentsForReassign || []).forEach(dept => {
                    if (dept.name !== currentDeptNameToDeleteForModal) {
                        targetReassignDeptSelect.add(new Option(_decodeLocal(dept.name), dept.name));
                    }
                });
             }
             _showModal(deleteReassignModal);
        });
    }
    if(confirmDeleteAndReassignBtn) {
        confirmDeleteAndReassignBtn.addEventListener('click', () => {
            if (hiddenDeleteDepartmentName) hiddenDeleteDepartmentName.value = currentDeptNameToDeleteForModal;
            let targetInput = deleteDepartmentForm.querySelector('input[name="targetDepartmentForReassignment"]');
            if(!targetInput) { targetInput = document.createElement('input'); targetInput.type = 'hidden'; targetInput.name='targetDepartmentForReassignment'; deleteDepartmentForm.appendChild(targetInput); }
            targetInput.value = targetReassignDeptSelect.value;
            deleteDepartmentForm.submit();
        });
    }
    if (closeDeleteReassignModalBtn) closeDeleteReassignModalBtn.addEventListener('click', () => _hideModal(deleteReassignModal));
    if (cancelDeleteReassignBtn) cancelDeleteReassignBtn.addEventListener('click', () => _hideModal(deleteReassignModal));
    if (closeWizardGenericModalBtn) closeWizardGenericModalBtn.addEventListener('click', () => _hideModal(wizardGenericModal));
    if (okBtnGeneralNotify) okBtnGeneralNotify.addEventListener('click', () => _hideModal(notificationModalGeneral));
    if (generalModalCloseX) generalModalCloseX.addEventListener('click', () => _hideModal(notificationModalGeneral));
    toggleActionButtons(); 
    if (departmentsTable && typeof makeTableSortable === 'function') {
        makeTableSortable(departmentsTable);
    }
    
    if (typeof makeModalDraggable === 'function') {
        if (wizardGenericModal) makeModalDraggable(wizardGenericModal);
        if (addDepartmentModal) makeModalDraggable(addDepartmentModal);
        if (editDepartmentModal) makeModalDraggable(editDepartmentModal);
        if (deleteReassignModal) makeModalDraggable(deleteReassignModal);
    }
});
