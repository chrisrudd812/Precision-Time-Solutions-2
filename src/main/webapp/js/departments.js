// js/departments.js - vCompleteFix_TableVisible_Debug_Full
document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] departments.js (vCompleteFix_TableVisible_Debug_Full) loaded.");
    
    // --- Element Selectors ---
    const addDepartmentButton = document.getElementById('addDepartmentButton');
    const editDepartmentButton = document.getElementById('editDepartmentButton');
    const deleteDepartmentButton = document.getElementById('deleteDepartmentButton');

    const addDepartmentModal = document.getElementById('addDepartmentModal');
    console.log("[DEBUG] addDepartmentModal element:", addDepartmentModal); 

    const addDepartmentForm = document.getElementById('addDepartmentForm');
    console.log("[DEBUG] addDepartmentForm element:", addDepartmentForm); 

    const addDeptNameInput = document.getElementById('addDeptName');
    console.log("[DEBUG] addDeptNameInput element:", addDeptNameInput); 

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
    // Corrected selector based on the JSP for the general notification modal's close X button
    const generalModalCloseX = notificationModalGeneral ? notificationModalGeneral.querySelector('#closeNotificationModalGeneralXBtn_Dept') || notificationModalGeneral.querySelector('.close[data-close-modal-id="notificationModalGeneral"]') : null;


    const deleteReassignModal = document.getElementById('deleteAndReassignDeptModal');
    const closeDeleteReassignModalBtn = document.getElementById('closeDeleteReassignModalBtn');
    const cancelDeleteReassignBtn = document.getElementById('cancelDeleteReassignBtn');
    const confirmDeleteAndReassignBtn = document.getElementById('confirmDeleteAndReassignBtn');
    const targetReassignDeptSelect = document.getElementById('targetReassignDeptSelect');
    const deleteReassignModalMessage = document.getElementById('deleteReassignModalMessage');
    const deleteReassignModalError = document.getElementById('deleteReassignModalError');

    const wizardGenericModal = document.getElementById('wizardGenericModal');
    console.log("[DEBUG] wizardGenericModal element:", wizardGenericModal);

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

    console.log("[DEBUG] Initial JS Vars from JSP - IS_WIZARD_MODE_DEPARTMENTS:",
        (typeof IS_WIZARD_MODE_DEPARTMENTS !== 'undefined' ? IS_WIZARD_MODE_DEPARTMENTS : "N/A"),
        "| CURRENT_WIZARD_STEP_DEPARTMENTS:", currentGlobalWizardStep,
        "| DEPT_JUST_ADDED_WIZARD:", (typeof DEPARTMENT_JUST_ADDED_WIZARD !== 'undefined' ? DEPARTMENT_JUST_ADDED_WIZARD : "N/A"),
        "| COMPANY_NAME_SIGNUP_JS:", (typeof COMPANY_NAME_SIGNUP_JS !== 'undefined' ? COMPANY_NAME_SIGNUP_JS : "N/A"),
        "| appRoot:", appRoot
    );

    // --- Helper Functions ---
    function _decodeLocal(text) {
        if (typeof window.decodeHtmlEntities === 'function') { return window.decodeHtmlEntities(text); }
        if (typeof text !== 'string') return text === null || typeof text === 'undefined' ? "" : String(text);
        const ta = document.createElement('textarea'); ta.innerHTML = text; return ta.value;
    }

    function _showModal(modalEl) {
        console.log("[DEBUG] _showModal attempting to show:", modalEl ? modalEl.id : "null element");
        if (modalEl) {
            if (typeof window.showModal === 'function') {
                console.log("[DEBUG] _showModal using global window.showModal()");
                window.showModal(modalEl); 
            } else { 
                console.log("[DEBUG] _showModal using fallback (style.display='flex', classList.add)");
                modalEl.style.display = 'flex'; 
                modalEl.classList.add('modal-visible'); 
            }
        } else { console.warn("[DEBUG] _showModal: modalEl is null or undefined."); }
    }

    function _hideModal(modalEl) {
        console.log("[DEBUG] _hideModal attempting to hide:", modalEl ? modalEl.id : "null element");
        if (modalEl) {
            if (typeof window.hideModal === 'function') {
                console.log("[DEBUG] _hideModal using global window.hideModal()");
                window.hideModal(modalEl); 
            } else { 
                console.log("[DEBUG] _hideModal using fallback (style.display='none', classList.remove)");
                modalEl.style.display = 'none'; 
                modalEl.classList.remove('modal-visible'); 
            }
        } else { console.warn("[DEBUG] _hideModal: modalEl is null or undefined."); }
    }
    
    function _hideAddModalAndHandleWizard() {
        console.log("[DEBUG] _hideAddModalAndHandleWizard called. Wizard Mode:", IS_WIZARD_MODE_DEPARTMENTS, "wizardOpenedAddModal:", wizardOpenedAddModal);
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
        console.log(`[DEBUG] _showPageNotification: Message='${message}', isError=${isError}, Title='${title}'`);
        if (typeof window.showPageNotification === 'function') {
             console.log("[DEBUG] Using global showPageNotification.");
             window.showPageNotification(message, isError, notificationModalGeneral, title);
             return;
        }
        console.log("[DEBUG] Using local fallback _showPageNotification.");
        const titleElem = notificationModalGeneral ? notificationModalGeneral.querySelector("#notificationModalGeneralTitle") : null;
        const msgElem = notificationModalGeneral ? notificationModalGeneral.querySelector("#notificationModalGeneralMessage") : null;
        const modalContent = notificationModalGeneral ? notificationModalGeneral.querySelector(".modal-content") : null;

        if (titleElem) titleElem.textContent = title;
        if (msgElem) msgElem.innerHTML = message;
        if (modalContent) {
            modalContent.classList.toggle('error-message', isError);
            modalContent.classList.toggle('success-message', !isError);
        }
        if (notificationModalGeneral) _showModal(notificationModalGeneral);
        else alert((isError ? "Error: " : "Info: ") + title + "\n" + message.replace(/<br\s*\/?>/gi, '\n'));
    }
    
    function escapeHtml(unsafe) {
        if (typeof unsafe !== 'string') {
             console.warn("[DEBUG] escapeHtml called with non-string:", unsafe);
             return unsafe === null || typeof unsafe === 'undefined' ? "" : String(unsafe);
        }
        return unsafe
             .replace(/&/g, "&amp;")
             .replace(/</g, "&lt;")
             .replace(/>/g, "&gt;")
             .replace(/"/g, "&quot;")
             .replace(/'/g, "&#039;");
    }

    const companyNameToDisplayJS = (typeof COMPANY_NAME_SIGNUP_JS !== 'undefined' && COMPANY_NAME_SIGNUP_JS && COMPANY_NAME_SIGNUP_JS.trim() !== "" && COMPANY_NAME_SIGNUP_JS !== "Your Company") ? COMPANY_NAME_SIGNUP_JS : "your new company";
    console.log("[DEBUG] companyNameToDisplayJS set to:", companyNameToDisplayJS);

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
        },
        "schedules_prompt": {
            title: "Setup: Schedules",
            text1: `Great! Now let's set up work schedules for <strong>${escapeHtml(companyNameToDisplayJS)}</strong>.`,
            text2: "Schedules help with tardiness tracking and shift management. You can manage these later via Navbar > Scheduling.",
            buttons: [
                { id: "wizardActionGoToSchedules", text: "Setup Schedules Now", class: "text-green", actionKey: "goToSchedulingPage" },
                { id: "wizardActionNextToAccruals", text: "Next: Accruals", class: "text-blue", actionKey: "advanceToAccruals" }
            ]
        },
        "accruals_prompt": {
            title: "Setup: Accrual Policies",
            text1: `Next, configure accrual policies (e.g., vacation, sick time) for <strong>${escapeHtml(companyNameToDisplayJS)}</strong>.`,
            text2: "These define how employees earn time off. Manage them later via Navbar > Accruals.",
            buttons: [
                { id: "wizardActionGoToAccruals", text: "Setup Accruals Now", class: "text-green", actionKey: "goToAccrualsPage" },
                { id: "wizardActionNextToEmployees", text: "Next: Employees", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        },
        "employees_prompt": {
            title: "Setup: Add Employees",
            text1: `Almost done! The final setup step is to add your employees to <strong>${escapeHtml(companyNameToDisplayJS)}</strong>.`,
            text2: "You'll need to add your administrator profile first, then other employees. Manage later via Navbar > Employees.",
             buttons: [
                { id: "wizardActionGoToEmployees", text: "Add Employees Now", class: "text-green", actionKey: "goToEmployeesPage" },
                { id: "wizardActionFinishSetup", text: "Finish Setup", class: "text-orange", actionKey: "completeWizard" }
            ]
        }
    };
    console.log("[DEBUG] wizardStages object initialized:", wizardStages);


    function showMainPageContent(show = true) {
        const displayStyle = show ? '' : 'none'; 
        if(departmentTableContainer) departmentTableContainer.style.display = show ? '' : 'none';
        if(mainActionButtonsContainer) mainActionButtonsContainer.style.display = show ? 'flex' : 'none';
        if(addDepartmentButton) addDepartmentButton.style.display = show ? 'inline-flex' : 'none';
        if(editDepartmentButton) editDepartmentButton.style.display = show ? 'inline-flex' : 'none';
        if(deleteDepartmentButton) deleteDepartmentButton.style.display = show ? 'inline-flex' : 'none';
        if(instructionTextH4) instructionTextH4.style.display = show ? 'block' : 'none';
        console.log(`[DEBUG] Main page content visibility set to: ${show}`);
    }

    function toggleActionButtons() {
        const isRowSelected = selectedDeptRow !== null;
        let titleMessage = isRowSelected ? "" : "Select a department first.";
        let disableActions = !isRowSelected;
        if (isRowSelected && selectedDeptData && selectedDeptData.name && selectedDeptData.name.toLowerCase() === 'none') {
            titleMessage = "The default 'None' department cannot be edited or deleted.";
            disableActions = true;
        }
        if (editDepartmentButton) { editDepartmentButton.disabled = disableActions; editDepartmentButton.title = titleMessage || "Edit selected department"; }
        if (deleteDepartmentButton) { deleteDepartmentButton.disabled = disableActions; deleteDepartmentButton.title = titleMessage || "Delete selected department"; }
    }

    function selectDeptRow(row) {
        if (selectedDeptRow && selectedDeptRow !== row) { selectedDeptRow.classList.remove('selected'); }
        if (row && row.dataset && typeof row.dataset.name !== 'undefined') {
            if (row === selectedDeptRow) { 
                row.classList.remove('selected'); selectedDeptRow = null; selectedDeptData = null;
            } else {
                row.classList.add('selected'); selectedDeptRow = row;
                selectedDeptData = {
                    name: _decodeLocal(row.dataset.name),
                    description: _decodeLocal(row.dataset.description || ""),
                    supervisor: _decodeLocal(row.dataset.supervisor || "")
                };
                if (selectedDeptData.name && selectedDeptData.name.toLowerCase() === 'none') {
                    if(typeof _showPageNotification === 'function') _showPageNotification("The default 'None' department is built-in and cannot be edited or deleted.", false, "Information");
                }
            }
        } else { 
            if(selectedDeptRow) selectedDeptRow.classList.remove('selected');
            selectedDeptRow = null; selectedDeptData = null;
        }
        toggleActionButtons();
    }

    function openAddDepartmentModal() {
        console.log("[DEBUG] openAddDepartmentModal called. wizardOpenedAddModal:", wizardOpenedAddModal);
        console.log("[DEBUG] addDepartmentModal exists?", (addDepartmentModal ? "Yes" : "No"), "addDepartmentForm exists?", (addDepartmentForm ? "Yes" : "No"));
        if (addDepartmentModal && addDepartmentForm) {
            addDepartmentForm.reset();
            const supervisorInput = addDepartmentModal.querySelector('#addDeptSupervisor');
            if (supervisorInput) supervisorInput.value = ""; 
            
            console.log("[DEBUG] Calling _showModal for addDepartmentModal");
            _showModal(addDepartmentModal);
            
            if (addDeptNameInput) {
                console.log("[DEBUG] Attempting to focus addDeptNameInput in 150ms");
                setTimeout(() => { 
                    addDeptNameInput.focus(); 
                    console.log("[DEBUG] Focus attempted on #addDeptName. document.activeElement:", document.activeElement ? document.activeElement.id : "none"); 
                }, 150);
            } else {
                console.warn("[DEBUG] addDeptNameInput is null, cannot set focus.");
            }
        } else { 
            console.error("[DEBUG] Add Department Modal (#addDepartmentModal) or Form (#addDepartmentForm) not found by getElementById. Check JSP IDs."); 
        }
    }

    function populateAndShowEditModalSimple() {
        if (!editDepartmentModal || !editDepartmentForm || !selectedDeptData) { 
            console.warn("[DEBUG] populateAndShowEditModalSimple: Missing elements or selectedDeptData.");
            return; 
        }
        editDepartmentForm.reset();
        if (originalDeptNameInput) originalDeptNameInput.value = selectedDeptData.name || "";
        const editDescInput = editDepartmentModal.querySelector('#editDeptDescription');
        const editSuperInput = editDepartmentModal.querySelector('#editDeptSupervisor');
        if (editDeptNameInput) { editDeptNameInput.value = selectedDeptData.name || ""; editDeptNameInput.disabled = true; }
        if(editDescInput) editDescInput.value = selectedDeptData.description || "";
        if(editSuperInput) editSuperInput.value = selectedDeptData.supervisor || "";
        _showModal(editDepartmentModal);
        if (editDescInput && editDescInput.focus) { setTimeout(() => editDescInput.focus(), 150); }
        else if (editSuperInput && editSuperInput.focus) { setTimeout(() => editSuperInput.focus(), 150); }
    }

    function updateWizardModalView(stageKey) {
        console.log("[DEBUG] updateWizardModalView called for stage:", stageKey);
        if (!wizardGenericModal || !wizardTitleElement || !wizardText1Element || !wizardText2Element || !wizardButtonRow) {
            console.error("[DEBUG] Wizard modal core elements not found. Cannot update view."); 
            showMainPageContent(true); return;
        }
        const stageConfig = wizardStages[stageKey];
        if (!stageConfig) {
            console.error(`[DEBUG] Unknown wizard stage key: '${stageKey}'. Check wizardStages definition.`);
            _hideModal(wizardGenericModal); showMainPageContent(true); return;
        }
        currentGlobalWizardStep = stageKey; 
        wizardTitleElement.textContent = stageConfig.title;
        wizardText1Element.innerHTML = stageConfig.text1; 
        wizardText2Element.innerHTML = stageConfig.text2;
        wizardButtonRow.innerHTML = ''; 

        stageConfig.buttons.forEach(btnConfig => {
            const button = document.createElement('button');
            button.type = 'button'; button.id = btnConfig.id;
            button.className = `glossy-button ${btnConfig.class}`;
            button.innerHTML = btnConfig.text;
            switch (btnConfig.actionKey) {
                case "openAddDeptModalViaWizard": button.addEventListener('click', handleWizardAddDeptClick); break;
                case "advanceToSchedules": button.addEventListener('click', () => advanceWizardToServerAndRedirect("schedules_prompt", `${appRoot}/scheduling.jsp?setup_wizard=true`)); break;
                case "goToSchedulingPage": button.addEventListener('click', () => advanceWizardToServerAndRedirect("schedules_prompt", `${appRoot}/scheduling.jsp?setup_wizard=true`)); break;
                case "advanceToAccruals": button.addEventListener('click', () => advanceWizardToServerAndRedirect("accruals_prompt", `${appRoot}/accruals.jsp?setup_wizard=true`)); break;
                case "goToAccrualsPage": button.addEventListener('click', () => advanceWizardToServerAndRedirect("accruals_prompt", `${appRoot}/accruals.jsp?setup_wizard=true`)); break;
                case "advanceToEmployees": button.addEventListener('click', () => advanceWizardToServerAndRedirect("employees_prompt", `${appRoot}/employees.jsp?setup_wizard=true&action=review_admin`)); break;
                case "goToEmployeesPage": button.addEventListener('click', () => advanceWizardToServerAndRedirect("employees_prompt", `${appRoot}/employees.jsp?setup_wizard=true&action=review_admin`)); break;
                case "completeWizard": button.addEventListener('click', () => { _hideModal(wizardGenericModal); completeAndExitWizard(true); }); break;
                default: console.warn("[DEBUG] Unknown wizard button actionKey:", btnConfig.actionKey);
            }
            wizardButtonRow.appendChild(button);
        });

        if (stageKey === "departments_after_add") {
            showMainPageContent(true); 
        } else {
            showMainPageContent(false); 
        }
        _showModal(wizardGenericModal);
        console.log("[DEBUG] Wizard modal updated and shown for stage:", stageKey);
    }

    const handleWizardAddDeptClick = () => {
        console.log("[DEBUG] handleWizardAddDeptClick: Action triggered.");
        wizardOpenedAddModal = true; 
        console.log("[DEBUG] handleWizardAddDeptClick: Hiding wizardGenericModal.");
        _hideModal(wizardGenericModal);
        console.log("[DEBUG] handleWizardAddDeptClick: Showing main page content.");
        showMainPageContent(true); 
        console.log("[DEBUG] handleWizardAddDeptClick: Calling openAddDepartmentModal().");
        openAddDepartmentModal(); 
    };
    
    function advanceWizardToServerAndRedirect(serverNextStep, redirectUrl) {
        console.log(`[DEBUG] advanceWizardToServerAndRedirect: Advancing server to step ${serverNextStep}, then redirecting to ${redirectUrl}`);
         fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': serverNextStep})
        })
        .then(response => {
            if (!response.ok) { throw new Error(`HTTP error ${response.status}`); }
            return response.json();
        })
        .then(data => {
            if (data.success) {
                console.log(`[DEBUG] advanceWizardToServerAndRedirect: Server step set to ${serverNextStep}. Redirecting now to ${redirectUrl}.`);
                window.location.href = redirectUrl;
            } else { 
                console.error("[DEBUG] advanceWizardToServerAndRedirect: Error setting wizard step on server:", data.error);
                if(typeof _showPageNotification === 'function') _showPageNotification("Could not proceed: " + (data.error || "Server error"), true, "Setup Error");
            }
        })
        .catch(error => { 
            console.error("[DEBUG] advanceWizardToServerAndRedirect: Network error or JSON parsing error:", error);
            if(typeof _showPageNotification === 'function') _showPageNotification("Network error advancing wizard. Please try again. Details: " + error.message, true, "Network Error"); 
        });
    }
    
    function completeAndExitWizard(showNotification = true) {
        console.log("[DEBUG] completeAndExitWizard called. Show Notification:", showNotification);
        if(wizardGenericModal) _hideModal(wizardGenericModal); 
        showMainPageContent(true); 
        
        fetch(`${appRoot}/WizardStatusServlet`, { 
            method: 'POST', 
            headers: {'Content-Type': 'application/x-www-form-urlencoded'}, 
            body: new URLSearchParams({'action': 'completeWizard'}) 
        })
        .then(response => {
            if (!response.ok) { throw new Error(`HTTP error ${response.status}`); }
            return response.json();
        })
        .then(data => {
            if (data.success) {
                console.log("[DEBUG] Wizard completed on server.");
                if (showNotification && typeof _showPageNotification === 'function') { 
                    _showPageNotification("Setup flow complete! You can now explore and manage your company settings.", false, "Setup Finished");
                }
                if(typeof _clearUrlParams === 'function') _clearUrlParams(['setup_wizard', 'message', 'error', 'wizardStep', 'deptAdded']);
                const wizardWelcomeMsgDiv = document.getElementById('wizardPageWelcomeMsg');
                if (wizardWelcomeMsgDiv) wizardWelcomeMsgDiv.style.display = 'none';
                document.title = "Department Management";
                const mainHeading = document.querySelector('.parent-container.reports-container > h1');
                if(mainHeading && mainHeading.innerHTML.includes("(Setup)")) {
                     mainHeading.innerHTML = "Manage Departments";
                }
            } else { 
                 console.error("[DEBUG] Error completing wizard on server:", data.error);
                 if(typeof _showPageNotification === 'function') _showPageNotification("Could not finalize setup on server: " + (data.error || "Unknown error"), true, "Setup Error");
            }
        })
        .catch(error => { 
             console.error("[DEBUG] Network error or JSON parsing error completing wizard:", error);
             if(typeof _showPageNotification === 'function') _showPageNotification("Network error finalizing setup. Details: " + error.message, true, "Network Error");
        });
    }

    // --- Initial Page Load Logic & Event Listener Attachments ---
    if (typeof IS_WIZARD_MODE_DEPARTMENTS !== 'undefined' && IS_WIZARD_MODE_DEPARTMENTS === true) {
        console.log("[DEBUG] Page loaded in WIZARD MODE. Current Step from JSP:", currentGlobalWizardStep);
        if (currentGlobalWizardStep === "departments" || currentGlobalWizardStep === "departments_initial") {
            if (typeof DEPARTMENT_JUST_ADDED_WIZARD !== 'undefined' && DEPARTMENT_JUST_ADDED_WIZARD === true) {
                updateWizardModalView("departments_after_add");
            } else {
                updateWizardModalView("departments_initial");
            }
        } else if (currentGlobalWizardStep === "departments_after_add") {
             updateWizardModalView("departments_after_add");
        } else if (wizardStages[currentGlobalWizardStep]) { 
            updateWizardModalView(currentGlobalWizardStep);
        } else {
             console.warn("[DEBUG] Wizard mode ON, but step '", currentGlobalWizardStep, "' is not an initial department prompt. Showing main content.");
             showMainPageContent(true); 
        }
    } else {
        console.log("[DEBUG] Not in wizard mode. Standard page view.");
        showMainPageContent(true);
        if(typeof _clearUrlParams === 'function') _clearUrlParams(['setup_wizard', 'wizardStep', 'deptAdded']);
    }
    
    // Event Listeners
    if (addDepartmentButton) { addDepartmentButton.addEventListener('click', openAddDepartmentModal); }
    else { console.warn("[DEBUG] addDepartmentButton not found."); }

    if (editDepartmentButton) { 
        editDepartmentButton.addEventListener('click', () => {
            if (editDepartmentButton.disabled || !selectedDeptData) {
                if(typeof _showPageNotification === 'function') _showPageNotification("Select a department to edit.", true, "Selection Required"); 
                return;
            }
            populateAndShowEditModalSimple();
        });
    } else { console.warn("[DEBUG] editDepartmentButton not found."); }
    
    if (closeAddDeptModalX && addDepartmentModal) { closeAddDeptModalX.addEventListener('click', _hideAddModalAndHandleWizard); }
    else { console.warn("[DEBUG] closeAddDeptModalX or addDepartmentModal not found."); }

    if (cancelAddDeptButton && addDepartmentModal) { cancelAddDeptButton.addEventListener('click', _hideAddModalAndHandleWizard); }
    else { console.warn("[DEBUG] cancelAddDeptButton or addDepartmentModal not found."); }

    if (closeEditDeptModalX && editDepartmentModal) { closeEditDeptModalX.addEventListener('click', () => { _hideModal(editDepartmentModal); selectDeptRow(null); }); }
    else { console.warn("[DEBUG] closeEditDeptModalX or editDepartmentModal not found."); }

    if (cancelEditDeptButton && editDepartmentModal) { cancelEditDeptButton.addEventListener('click', () => { _hideModal(editDepartmentModal); selectDeptRow(null); }); }
    else { console.warn("[DEBUG] cancelEditDeptButton or editDepartmentModal not found."); }
    
    if (tableBody) { tableBody.addEventListener('click', (event) => { const row = event.target.closest('tr'); if (!row || !tableBody.contains(row) || row.classList.contains('report-message-row') || row.classList.contains('report-error-row')) { selectDeptRow(null); return; } selectDeptRow(row); }); }
    else { console.warn("[DEBUG] tableBody not found."); }

    if (deleteDepartmentButton && deleteReassignModal && targetReassignDeptSelect && deleteReassignModalMessage && hiddenDeleteDepartmentName) {
        deleteDepartmentButton.addEventListener('click', () => {
             if (deleteDepartmentButton.disabled || !selectedDeptData) { if(typeof _showPageNotification === 'function') _showPageNotification("Select a department to delete.", true, "Selection Required"); return; }
             currentDeptNameToDeleteForModal = selectedDeptData.name;
             const decodedName = _decodeLocal(currentDeptNameToDeleteForModal);
              if (currentDeptNameToDeleteForModal && currentDeptNameToDeleteForModal.toLowerCase() === 'none'){ if(typeof _showPageNotification === 'function') _showPageNotification("The default 'None' department cannot be deleted.", true, "Action Not Allowed"); return; }

             if (deleteReassignModalMessage) deleteReassignModalMessage.innerHTML = `You are about to delete department: <strong>${escapeHtml(decodedName)}</strong>.<br>If any employees are assigned, they will be moved to the department selected below:`;
             if (targetReassignDeptSelect) {
                targetReassignDeptSelect.innerHTML = ''; let defaultTarget = null; let hasAlternatives = false;
                (window.allAvailableDepartmentsForReassign || []).forEach(dept => {
                    if (dept.name !== currentDeptNameToDeleteForModal) { hasAlternatives = true; const opt = new Option(_decodeLocal(dept.name), dept.name); targetReassignDeptSelect.add(opt); if (dept.name && dept.name.toLowerCase() === 'none') defaultTarget = opt.value; }
                });
                if (!hasAlternatives) { if(typeof _showPageNotification === 'function') _showPageNotification("Cannot delete: No alternative departments (like 'None') exist for reassignment.", true, "Reassignment Error"); return; }
                if (defaultTarget) targetReassignDeptSelect.value = defaultTarget;
                else if (targetReassignDeptSelect.options.length > 0) targetReassignDeptSelect.value = targetReassignDeptSelect.options[0].value;
             }
             if (deleteReassignModalError) deleteReassignModalError.style.display = 'none';
             _showModal(deleteReassignModal);
        });
    } else { console.warn("[DEBUG] Some elements for deleteDepartmentButton functionality are missing."); }

    if(confirmDeleteAndReassignBtn && deleteDepartmentForm && targetReassignDeptSelect && hiddenDeleteDepartmentName) {
        confirmDeleteAndReassignBtn.addEventListener('click', () => {
            const targetDeptValue = targetReassignDeptSelect.value;
            if (!targetDeptValue) { if (deleteReassignModalError) { deleteReassignModalError.textContent = "Please select a target department."; deleteReassignModalError.style.display = 'block'; } return; }
            if (deleteReassignModalError) deleteReassignModalError.style.display = 'none';
            hiddenDeleteDepartmentName.value = currentDeptNameToDeleteForModal;
            let targetInput = deleteDepartmentForm.querySelector('input[name="targetDepartmentForReassignment"]');
            if(!targetInput) { targetInput = document.createElement('input'); targetInput.type = 'hidden'; targetInput.name='targetDepartmentForReassignment'; deleteDepartmentForm.appendChild(targetInput); }
            targetInput.value = targetDeptValue;
            deleteDepartmentForm.submit();
        });
    } else { console.warn("[DEBUG] Some elements for confirmDeleteAndReassignBtn functionality are missing."); }

    if (closeDeleteReassignModalBtn) closeDeleteReassignModalBtn.addEventListener('click', () => _hideModal(deleteReassignModal));
    if (cancelDeleteReassignBtn) cancelDeleteReassignBtn.addEventListener('click', () => _hideModal(deleteReassignModal));

    if (closeWizardGenericModalBtn && wizardGenericModal) {
         closeWizardGenericModalBtn.addEventListener('click', () => {
            console.log("[DEBUG] Wizard Generic Modal X clicked - exiting wizard flow for this page.");
            completeAndExitWizard(false); 
         });
    } else { console.warn("[DEBUG] closeWizardGenericModalBtn or wizardGenericModal not found.");}
    
    if (okBtnGeneralNotify && notificationModalGeneral) {okBtnGeneralNotify.addEventListener('click', () => { if(typeof _hideModal === 'function') _hideModal(notificationModalGeneral);});}
    else { console.warn("[DEBUG] okBtnGeneralNotify or notificationModalGeneral not found."); }

    if (generalModalCloseX && notificationModalGeneral) {generalModalCloseX.addEventListener('click', () => { if(typeof _hideModal === 'function') _hideModal(notificationModalGeneral);});}
    else { console.warn("[DEBUG] generalModalCloseX or notificationModalGeneral not found."); }
    
    function autoHideMessage(msgDivId) { 
        const msgDiv = document.getElementById(msgDivId);
        if (msgDiv && msgDiv.textContent.trim() !== '' && (msgDiv.style.display !== 'none' && msgDiv.offsetHeight > 0) ) {
            setTimeout(() => {
                if(msgDiv) {
                    msgDiv.style.transition = 'opacity 0.5s ease-out'; msgDiv.style.opacity = '0';
                    setTimeout(() => { if(msgDiv) { msgDiv.style.display = 'none'; msgDiv.style.opacity = '1'; }}, 500);
                }
            }, 7000);
        }
    }
    autoHideMessage('pageSuccessMessage_Dept'); 
    autoHideMessage('pageErrorMessage_Dept');
    toggleActionButtons(); 

    if (departmentsTable && typeof _makeTableSortable === 'function') {
        _makeTableSortable(departmentsTable, {columnIndex: 0, ascending: true});
    } else if (departmentsTable) {
        console.warn("[DEBUG] _makeTableSortable function not available (likely from commonUtils.js). Table sorting disabled.");
    }

    console.log("[DEBUG] departments.js (vCompleteFix_TableVisible_Debug_Full) full setup complete.");
});