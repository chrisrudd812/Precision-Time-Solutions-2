// js/accruals.js - v22_WizardFlowTableVisibilityComplete
document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] accruals.js (v22_WizardFlowTableVisibilityComplete) loaded.");
    console.log("[DEBUG Accruals.js] Initial JS Vars - WizardMode:",
        (typeof window.inWizardMode_Page !== 'undefined' ? window.inWizardMode_Page : "N/A"),
        "| CurrentStep:", (typeof window.currentWizardStep_Page !== 'undefined' ? window.currentWizardStep_Page : "N/A"),
        "| PolicyJustAdded:", (typeof window.ACCRUAL_POLICY_JUST_ADDED_WIZARD !== 'undefined' ? window.ACCRUAL_POLICY_JUST_ADDED_WIZARD : "N/A"),
        "| CompanyName:", (typeof window.COMPANY_NAME_SIGNUP_JS !== 'undefined' ? window.COMPANY_NAME_SIGNUP_JS : "N/A"),
        "| ShowSpecificIntro (from JSP):", (typeof window.showSpecificIntroModal_Accruals_JS !== 'undefined' ? window.showSpecificIntroModal_Accruals_JS : "N/A")
    );

    // --- Global/Common Utility Function Access ---
    const _showModal = window.showModal || function(modalEl) { if(modalEl) { console.log("[DEBUG Accruals] Fallback showModal for:", modalEl.id); modalEl.style.display = 'flex'; modalEl.classList.add('modal-visible'); } else { console.warn("[DEBUG Accruals] Fallback showModal: modalEl is null");} };
    const _hideModal = window.hideModal || function(modalEl) { if(modalEl) { console.log("[DEBUG Accruals] Fallback hideModal for:", modalEl.id); modalEl.style.display = 'none'; modalEl.classList.remove('modal-visible');} else { console.warn("[DEBUG Accruals] Fallback hideModal: modalEl is null");} };
    const _showPageNotification = window.showPageNotification || function(message, isError = false, modalInst = null, title = "Notification") { 
        const modalToUse = modalInst || document.getElementById("notificationModalGeneral");
        console.log("[DEBUG Accruals] Fallback _showPageNotification. Message:", message, "isError:", isError);
        if(modalToUse && modalToUse.querySelector('#notificationModalGeneralMessage')) {
            modalToUse.querySelector('#notificationModalGeneralMessage').innerHTML = message;
            if(modalToUse.querySelector('#notificationModalGeneralTitle')) modalToUse.querySelector('#notificationModalGeneralTitle').textContent = title;
            const modalContent = modalToUse.querySelector('.modal-content');
            if (modalContent) {
                modalContent.classList.toggle('error-message', isError);
                modalContent.classList.toggle('success-message', !isError);
            }
            _showModal(modalToUse);
        } else {
            alert(title + ": " + message); 
        }
    };
    const _decodeHtmlEntities = window.decodeHtmlEntities || function(text) { 
        if (typeof text !== 'string') return text;
        const ta = document.createElement('textarea'); ta.innerHTML = text; return ta.value;
    };
    const _clearUrlParams = window.clearUrlParams || function(params = ['setup_wizard', 'message', 'error', 'wizardStep', 'accrualAdded']) { 
        console.log("[DEBUG Accruals] Fallback _clearUrlParams called with:", params);
        try {
            const url = new URL(window.location.href); let changed = false;
            params.forEach(p => { if (url.searchParams.has(p)) { url.searchParams.delete(p); changed = true; }});
            if (changed) window.history.replaceState({}, document.title, url.pathname + url.search);
        } catch(e) { console.warn("[DEBUG Accruals] Local clearUrlParams failed.", e); }
    };
    const _makeTableSortable = window.makeTableSortable || function(table, options) { console.warn("[DEBUG Accruals] makeTableSortable not globally available."); };
    const appRoot = typeof appRootPath === 'string' ? appRootPath : "";

    // --- Element Selectors (from your v18 and new wizard elements) ---
    const addPolicyBtn = document.getElementById('btnAddPolicy');
    const editPolicyBtn = document.getElementById('btnEditPolicy');
    const deletePolicyBtn = document.getElementById('btnDeletePolicy');
    const addAccrualModal = document.getElementById('addAccrualModal');
    const addAccrualForm = document.getElementById('addAccrualForm');
    const closeAddAccrual_X_Btn = document.getElementById('closeAddAccrual_X_Btn');
    const cancelAddAccrualBtn = document.getElementById('cancelAddAccrualBtn');
    const editAccrualModal = document.getElementById('editAccrualModal');
    const editAccrualForm = document.getElementById('editAccrualForm');
    const closeEditAccrual_X_Btn = document.getElementById('closeEditAccrual_X_Btn');
    const cancelEditAccrualBtn = document.getElementById('cancelEditAccrualBtn');
    const accrualsTable = document.getElementById('accrualsTable');
    const tableBody = accrualsTable ? accrualsTable.querySelector('tbody') : null;
    const deleteAccrualForm = document.getElementById('deleteAccrualForm'); 
    const hiddenDeleteAccrualNameInput = deleteAccrualForm ? deleteAccrualForm.querySelector('#hiddenDeleteAccrualName') : null;
    const deleteReassignAccrualModal = document.getElementById('deleteAndReassignAccrualModal');
    const closeDeleteReassignAccrualModalBtn = document.getElementById('closeDeleteReassignAccrualModalBtn');
    const cancelDeleteReassignAccrualBtn = document.getElementById('cancelDeleteReassignAccrualBtn');
    const confirmDeleteAndReassignAccrualBtn = document.getElementById('confirmDeleteAndReassignAccrualBtn');
    const targetReassignAccrualSelect = document.getElementById('targetReassignAccrualSelect');
    const deleteReassignAccrualModalMessage = document.getElementById('deleteReassignAccrualModalMessage');
    const deleteReassignAccrualModalError = document.getElementById('deleteReassignAccrualModalError');
    const notificationModalGeneral = document.getElementById('notificationModalGeneral');
    const adjustmentFormFieldset = document.getElementById('adjustmentFormFieldset'); 
    const adjustAccrualBalanceForm = document.getElementById('adjustAccrualBalanceForm');
    // ... other adjustment form selectors from your v18 should be here if used below ...

    // Wizard-specific Element Selectors
    const specificWizardIntroModal = document.getElementById('setupWizardModal_Accruals'); 
    const manageAccrualsButtonWizardSpecific = document.getElementById('manageAccrualsButtonWizard');
    const skipAccrualsButtonWizardSpecific = document.getElementById('skipAccrualsButtonWizard');
    const closeSetupWizardAccrualsModal_X_Specific = document.getElementById('closeSetupWizardAccrualsModal_X');

    const wizardGenericPromptModal = document.getElementById('wizardGenericPromptModal_Accruals'); 
    const wizardGenericTitleElement = document.getElementById('wizardGenericPromptModalTitle_Accruals');
    const wizardGenericText1Element = document.getElementById('wizardGenericPromptModalText1_Accruals');
    const wizardGenericText2Element = document.getElementById('wizardGenericPromptModalText2_Accruals');
    const wizardGenericButtonRow = document.getElementById('wizardGenericPromptModalButtonRow_Accruals');
    const closeWizardGenericPromptModalBtn = document.getElementById('closeWizardGenericPromptModal_Accruals');
    
    const policyTableContainer = document.getElementById('reportOutput_accruals'); 
    const mainActionButtonsContainer = document.getElementById('button-container');
    const instructionTextH4_Accruals = document.getElementById('instructionTextH4_Accruals');
    const adjustmentSection = document.getElementById('adjustAccrualBalanceSection');

    let selectedAccrualRow = null; 
    let selectedAccrualDataName = null;
    let currentPolicyNameToDeleteForModal = null;
    let wizardOpenedAddPolicyModal = false; 
    let currentGlobalWizardStep = typeof window.currentWizardStep_Page !== 'undefined' ? window.currentWizardStep_Page : null;

    function escapeHtmlAccruals(unsafe) {
        if (typeof unsafe !== 'string') {
             return unsafe === null || typeof unsafe === 'undefined' ? "" : String(unsafe);
        }
        return unsafe.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;").replace(/'/g, "&#039;");
    }
    
    function showMainPageContentAccruals(show = true) {
        const displayStyle = show ? '' : 'none';
        if(policyTableContainer) policyTableContainer.style.display = displayStyle;
        if(mainActionButtonsContainer) mainActionButtonsContainer.style.display = show ? 'flex' : 'none';
        if(instructionTextH4_Accruals) instructionTextH4_Accruals.style.display = show ? 'block' : 'none';
        if(adjustmentSection) adjustmentSection.style.display = displayStyle;
        console.log(`[DEBUG Accruals.js] Main page content accruals set to: ${show}`);
    }

    const companyNameToDisplayJS_Accruals_Local = (typeof window.COMPANY_NAME_SIGNUP_JS !== 'undefined' && window.COMPANY_NAME_SIGNUP_JS && window.COMPANY_NAME_SIGNUP_JS.trim() !== "" && window.COMPANY_NAME_SIGNUP_JS !== "Your Company") ? window.COMPANY_NAME_SIGNUP_JS : "your company";

    const wizardStagesAccruals = {
        "accruals_prompt": {
            title: "Setup: Accrual Policies",
            text1: `Next, configure Accrual Policies for <strong>${escapeHtmlAccruals(companyNameToDisplayJS_Accruals_Local)}</strong> (e.g., for Vacation, Sick time). Default "None" and "Standard" policies already exist.`,
            text2: "You can add specific policies now, or proceed to the next step (adding employees). Manage policies later via Navbar > Accruals.",
            buttons: [
                { id: "wizardActionAddAccrualNow", text: "Add Policies Now", class: "text-green", actionKey: "openAddAccrualModalViaWizard" },
                { id: "wizardActionNextToEmployeesFromAccruals", text: "Next: Add Employees", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        },
        "accruals_after_add_prompt": {
            title: "Setup: Accrual Policies",
            text1: "Accrual Policy added successfully!",
            text2: "Would you like to add another policy, or proceed to adding employees?",
            buttons: [
                { id: "wizardActionAddAnotherAccrual", text: "Add Another Policy", class: "text-green", actionKey: "openAddAccrualModalViaWizard" },
                { id: "wizardActionNextFromAccrualAddToEmployees", text: "Next: Add Employees", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        }
    };
    console.log("[DEBUG Accruals.js] wizardStagesAccruals initialized:", wizardStagesAccruals);
    
    function _hideAddPolicyModalAndHandleWizard() {
        console.log("[DEBUG Accruals.js] _hideAddPolicyModalAndHandleWizard called. Wizard Mode:", window.inWizardMode_Page, "wizardOpenedAddPolicyModal:", wizardOpenedAddPolicyModal);
        if (addAccrualModal && typeof _hideModal === 'function') _hideModal(addAccrualModal);
        
        if (window.inWizardMode_Page && wizardOpenedAddPolicyModal) {
            wizardOpenedAddPolicyModal = false; 
            const nonDefaultRows = tableBody ? Array.from(tableBody.querySelectorAll('tr[data-name]')).filter(r => {
                const name = r.dataset.name ? r.dataset.name.toLowerCase() : "";
                return name !== 'none' && name !== 'standard';
            }).length : 0;

            if (window.ACCRUAL_POLICY_JUST_ADDED_WIZARD === true || nonDefaultRows > 0) { 
                 updateWizardModalViewAccruals("accruals_after_add_prompt");
            } else {
                 updateWizardModalViewAccruals("accruals_prompt");
            }
        } else {
            selectAccrualRow(null); 
        }
    }
    
    function updateWizardModalViewAccruals(stageKey) {
        console.log("[DEBUG Accruals.js] updateWizardModalViewAccruals called for stage:", stageKey);
        if (!wizardGenericPromptModal || !wizardGenericTitleElement || !wizardGenericText1Element || !wizardGenericText2Element || !wizardGenericButtonRow) {
            console.error("[DEBUG Accruals.js] Generic wizard prompt modal elements for accruals not found! Cannot update view.");
            showMainPageContentAccruals(true); return;
        }
        const stageConfig = wizardStagesAccruals[stageKey];
        if (!stageConfig) {
            console.error(`[DEBUG Accruals.js] Unknown wizard stage key for accruals: '${stageKey}'. Check wizardStagesAccruals definition.`);
            if (typeof _hideModal === 'function' && wizardGenericPromptModal) _hideModal(wizardGenericPromptModal); 
            showMainPageContentAccruals(true); return;
        }
        currentGlobalWizardStep = stageKey; 
        wizardGenericTitleElement.textContent = stageConfig.title;
        wizardGenericText1Element.innerHTML = stageConfig.text1;
        wizardGenericText2Element.innerHTML = stageConfig.text2;
        wizardGenericButtonRow.innerHTML = '';

        stageConfig.buttons.forEach(btnConfig => {
            const button = document.createElement('button');
            button.type = 'button'; button.id = btnConfig.id;
            button.className = `glossy-button ${btnConfig.class}`;
            button.innerHTML = btnConfig.text;
            switch (btnConfig.actionKey) {
                case "openAddAccrualModalViaWizard": button.addEventListener('click', handleWizardAddAccrualClick); break;
                case "advanceToEmployees":
                    button.addEventListener('click', () => {
                        advanceWizardToServerAndRedirectAccruals("employees_prompt", `${appRoot}/employees.jsp?setup_wizard=true&action=review_admin`);
                    });
                    break;
                default: console.warn("[DEBUG Accruals.js] Unknown wizard button actionKey:", btnConfig.actionKey);
            }
            wizardGenericButtonRow.appendChild(button);
        });

        if (stageKey === "accruals_after_add_prompt") { // Keep table visible for "Add Another"
            showMainPageContentAccruals(true);
        } else {
            showMainPageContentAccruals(false); 
        }
        if (typeof _showModal === 'function') _showModal(wizardGenericPromptModal);
        console.log("[DEBUG Accruals.js] Wizard generic prompt modal shown/updated for stage:", stageKey);
    }

    const handleWizardAddAccrualClick = () => {
        console.log("[DEBUG Accruals.js] WIZARD: 'Add Policies Now' button clicked from generic prompt.");
        wizardOpenedAddPolicyModal = true; 
        if (typeof _hideModal === 'function' && wizardGenericPromptModal) _hideModal(wizardGenericPromptModal);
        
        showMainPageContentAccruals(true); // Make main content visible

        if (addPolicyBtn) {
            console.log("[DEBUG Accruals.js] Clicking main btnAddPolicy programmatically.");
            addPolicyBtn.click(); 
        } else {
            console.warn("[DEBUG Accruals.js] Main 'btnAddPolicy' button not found, attempting direct modal open for addAccrualModal.");
            if (addAccrualModal && addAccrualForm) {
                addAccrualForm.reset();
                const addNameInput = addAccrualModal.querySelector('#addAccrualName');
                if(addNameInput) { addNameInput.disabled = false; setTimeout(() => addNameInput.focus(), 150); }
                const addVacInput = addAccrualModal.querySelector('#addVacationDays'); if (addVacInput) addVacInput.value = "0";
                const addSickInput = addAccrualModal.querySelector('#addSickDays'); if (addSickInput) addSickInput.value = "0";
                const addPersInput = addAccrualModal.querySelector('#addPersonalDays'); if (addPersInput) addPersInput.value = "0";
                if (typeof _showModal === 'function') _showModal(addAccrualModal);
            } else {
                console.error("[DEBUG Accruals.js] Cannot open Add Accrual Policy modal directly: key elements missing.");
                if (typeof _showPageNotification === 'function') _showPageNotification("Error: Could not open the Add Accrual Policy form.", true, notificationModalGeneral, "Error");
                if (typeof _showModal === 'function' && wizardGenericPromptModal) _showModal(wizardGenericPromptModal);
            }
        }
    };

    function advanceWizardToServerAndRedirectAccruals(serverNextStep, redirectUrl) {
        console.log(`[DEBUG Accruals.js] Advancing server to step ${serverNextStep}, then redirecting to ${redirectUrl}`);
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
                console.log(`[DEBUG Accruals.js] Server step set to ${serverNextStep}. Redirecting now to ${redirectUrl}.`);
                sessionStorage.setItem('accruals_intro_shown_wizard', 'true'); 
                window.location.href = redirectUrl; 
            } else { 
                console.error("[DEBUG Accruals.js] Error setting wizard step before redirect:", data.error);
                if (typeof _showPageNotification === 'function') _showPageNotification("Could not proceed: " + (data.error || "Server error"), true, notificationModalGeneral, "Setup Error");
            }
        })
        .catch(error => { 
            console.error("[DEBUG Accruals.js] Network error or JSON parsing error advancing wizard step:", error);
            if (typeof _showPageNotification === 'function') _showPageNotification("Network error. Please try again. Details: " + error.message, true, notificationModalGeneral, "Network Error"); 
        });
    }
    
    function completeWizardFlowAccruals(showNotification = true) {
        console.log("[DEBUG Accruals.js] Calling completeWizardFlowAccruals.");
        if (typeof _hideModal === 'function') {
            if(wizardGenericPromptModal) _hideModal(wizardGenericPromptModal); 
            if(specificWizardIntroModal) _hideModal(specificWizardIntroModal); 
        }
        showMainPageContentAccruals(true); 
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
                if (showNotification && typeof _showPageNotification === 'function') _showPageNotification("Setup flow complete!", false, notificationModalGeneral, "Setup Finished");
                if (typeof _clearUrlParams === 'function') _clearUrlParams(['setup_wizard', 'message', 'error', 'wizardStep', 'accrualAdded']);
                sessionStorage.removeItem('accruals_intro_shown_wizard');
                 document.title = "Manage Accrual Policies"; // Remove (Setup)
                 const mainHeading = document.querySelector('.parent-container.reports-container > h1 span');
                 if(mainHeading) mainHeading.style.display = 'none';
            } else {  
                if (typeof _showPageNotification === 'function') _showPageNotification("Could not finalize setup: " + (data.error || "Unknown error"), true, notificationModalGeneral, "Setup Error"); 
            }
        })
        .catch(error => { 
            if (typeof _showPageNotification === 'function') _showPageNotification("Network error finalizing setup. Details: " + error.message, true, notificationModalGeneral, "Network Error"); 
        });
    }

    // --- Specific Wizard Intro Modal Logic ---
    if (window.inWizardMode_Page && window.showSpecificIntroModal_Accruals_JS === true && specificWizardIntroModal) {
        if (sessionStorage.getItem('accruals_intro_shown_wizard') !== 'true') {
            console.log("[DEBUG Accruals.js] Showing specific wizard intro modal (setupWizardModal_Accruals).");
            if (typeof _showModal === 'function') _showModal(specificWizardIntroModal);
        } else {
            console.log("[DEBUG Accruals.js] Specific intro modal (setupWizardModal_Accruals) already handled per sessionStorage. Proceeding to generic prompt check.");
        }
    }

    if(closeSetupWizardAccrualsModal_X_Specific && specificWizardIntroModal) {
        closeSetupWizardAccrualsModal_X_Specific.addEventListener('click', () => { 
            console.log("[DEBUG Accruals.js] Specific intro modal 'X' clicked.");
            if (typeof _hideModal === 'function') _hideModal(specificWizardIntroModal); 
            sessionStorage.setItem('accruals_intro_shown_wizard', 'true');
            currentGlobalWizardStep = "accruals_prompt"; 
            updateWizardModalViewAccruals("accruals_prompt"); 
        });
    }
    if(manageAccrualsButtonWizardSpecific && specificWizardIntroModal) {
        manageAccrualsButtonWizardSpecific.addEventListener('click', () => {
            console.log("[DEBUG Accruals.js] 'Manage Accruals' from specific intro clicked.");
            if (typeof _hideModal === 'function') _hideModal(specificWizardIntroModal); 
            sessionStorage.setItem('accruals_intro_shown_wizard', 'true');
            currentGlobalWizardStep = "accruals_prompt";
            updateWizardModalViewAccruals("accruals_prompt");
        });
    }
    if(skipAccrualsButtonWizardSpecific && specificWizardIntroModal) {
        skipAccrualsButtonWizardSpecific.addEventListener('click', () => {
            console.log("[DEBUG Accruals.js] 'Skip Accruals' from specific intro clicked.");
            if (typeof _hideModal === 'function') _hideModal(specificWizardIntroModal);
            sessionStorage.setItem('accruals_intro_shown_wizard', 'true'); 
            advanceWizardToServerAndRedirectAccruals("employees_prompt", 
                `${appRoot}/employees.jsp?setup_wizard=true&action=review_admin`);
        });
    }

    // --- INITIAL PAGE LOAD FOR GENERIC WIZARD PROMPT ---
    // This runs after the specific intro modal logic might have set sessionStorage flag.
    if (window.inWizardMode_Page === true) {
        const specificIntroAlreadyHandled = sessionStorage.getItem('accruals_intro_shown_wizard') === 'true';
        const neverShowSpecificIntro = !window.showSpecificIntroModal_Accruals_JS; // If JSP determined it shouldn't show
        const specificIntroIsCurrentlyVisible = specificWizardIntroModal && (specificWizardIntroModal.style.display === 'flex' || specificWizardIntroModal.classList.contains('modal-visible'));

        if ((specificIntroAlreadyHandled || neverShowSpecificIntro) && !specificIntroIsCurrentlyVisible) {
            console.log("[DEBUG Accruals.js] Conditions met to check for generic prompt. CurrentStep:", currentGlobalWizardStep);
            if (currentGlobalWizardStep === "accruals_prompt") {
                if (window.ACCRUAL_POLICY_JUST_ADDED_WIZARD === true) {
                    updateWizardModalViewAccruals("accruals_after_add_prompt");
                } else {
                    updateWizardModalViewAccruals("accruals_prompt");
                }
            } else if (currentGlobalWizardStep === "accruals_after_add_prompt") {
                 updateWizardModalViewAccruals("accruals_after_add_prompt");
            } else if (wizardStagesAccruals[currentGlobalWizardStep]) {
                updateWizardModalViewAccruals(currentGlobalWizardStep);
            } else if (currentGlobalWizardStep === "accruals" && wizardStagesAccruals["accruals_prompt"]){ // Handle generic "accruals" step
                 updateWizardModalViewAccruals("accruals_prompt");
            }
             else {
                 console.log("[DEBUG Accruals.js] No specific wizard step matched for generic prompt; or specific intro should be visible. Main content may show.");
                  if (!specificIntroIsCurrentlyVisible && (!wizardGenericPromptModal || wizardGenericPromptModal.style.display === 'none')) {
                    showMainPageContentAccruals(true);
                 }
            }
        } else if (specificIntroIsCurrentlyVisible) {
             console.log("[DEBUG Accruals.js] Specific intro modal is currently visible. Generic prompt deferred.");
        }
         else {
            console.log("[DEBUG Accruals.js] Specific intro modal is due to be shown by JSP/initial JS logic, or other condition not met for generic prompt.");
        }
    } else { 
        console.log("[DEBUG Accruals.js] Not in wizard mode. Standard page view.");
        showMainPageContentAccruals(true);
        if (typeof _clearUrlParams === 'function') _clearUrlParams();
    }


    // --- Your Existing v18 Policy Management & Balance Adjustment JS ---
    // (Ensure all your existing functions and listeners from v18 are here,
    // and they use the _prefixed helper functions like _showModal, _hideModal, etc.)

    // Example of adapting your existing addPolicyBtn listener:
    if (addPolicyBtn && addAccrualModal) {
        addPolicyBtn.addEventListener('click', () => {
            console.log("[DEBUG Accruals.js] Standard Add Policy button clicked.");
            wizardOpenedAddPolicyModal = false; // This is a standard click, not from wizard prompt
            if (addAccrualForm) addAccrualForm.reset();
            const addNameInput = addAccrualModal.querySelector('#addAccrualName');
            if(addNameInput) { addNameInput.disabled = false; setTimeout(() => addNameInput.focus(),150); }
            const addVacInput = addAccrualModal.querySelector('#addVacationDays'); if (addVacInput) addVacInput.value = "0";
            const addSickInput = addAccrualModal.querySelector('#addSickDays'); if (addSickInput) addSickInput.value = "0";
            const addPersInput = addAccrualModal.querySelector('#addPersonalDays'); if (addPersInput) addPersInput.value = "0";
            if (typeof _showModal === 'function') _showModal(addAccrualModal);
        });
    }
    // Ensure Add Modal Close/Cancel use the wizard-aware handler
    if(closeAddAccrual_X_Btn) closeAddAccrual_X_Btn.addEventListener('click', _hideAddPolicyModalAndHandleWizard);
    if(cancelAddAccrualBtn) cancelAddAccrualBtn.addEventListener('click', _hideAddPolicyModalAndHandleWizard);
    if(addAccrualForm && typeof addAccrualForm.addEventListener === 'function') {
        addAccrualForm.addEventListener('submit', function(e) { 
            // Standard form submission will occur. If you need to preventDefault and handle via JS:
            // e.preventDefault(); 
            // console.log("[DEBUG Accruals.js] Add Accrual Form submitted.");
            // this.submit(); // or handle with fetch
        });
    }

    // ... (The rest of your v18 code: togglePolicyActionButtons, selectAccrualRow, populateEditAccrualModal, 
    //      listeners for edit/delete buttons and their modals, adjustment form logic, 
    //      page load message handling, autoHide, table sorting init)
    //      IMPORTANT: Make sure all calls to showModal, hideModal, showPageNotification etc.
    //      in your existing code are replaced with _showModal, _hideModal, _showPageNotification
    //      to use the globally defined versions from commonUtils.js reliably.

    // Fallback for critical UI element listeners if they were missed in the copy-paste of v18 logic
    function ensureBasicListeners_v18_fallback() {
        if (!editPolicyBtn || !editAccrualModal) console.warn("Edit policy button or modal missing");
        else editPolicyBtn.addEventListener('click', () => { if (editPolicyBtn.disabled || !selectedAccrualDataName) { if(typeof _showPageNotification === 'function') _showPageNotification("Please select an accrual policy to edit.", true, notificationModalGeneral,"Error"); return; } populateEditAccrualModal(); });
        
        if (!closeEditAccrual_X_Btn || !editAccrualModal) console.warn("Edit modal close button missing");
        else closeEditAccrual_X_Btn.addEventListener('click', () => { if (typeof _hideModal === 'function') _hideModal(editAccrualModal); selectAccrualRow(null); });
        
        if (!cancelEditAccrualBtn || !editAccrualModal) console.warn("Edit modal cancel button missing");
        else cancelEditAccrualBtn.addEventListener('click', () => { if (typeof _hideModal === 'function') _hideModal(editAccrualModal); selectAccrualRow(null); });

        if(deletePolicyBtn && deleteReassignAccrualModal && hiddenDeleteAccrualNameInput && targetReassignAccrualSelect && deleteReassignAccrualModalMessage) {
             deletePolicyBtn.addEventListener('click', () => {
                if (deletePolicyBtn.disabled || !selectedAccrualDataName) { if(typeof _showPageNotification === 'function') _showPageNotification("Please select a policy to delete.", true, notificationModalGeneral,"Error"); return;}
                currentPolicyNameToDeleteForModal = selectedAccrualDataName;
                const decodedName = typeof _decodeHtmlEntities === 'function' ? _decodeHtmlEntities(currentPolicyNameToDeleteForModal) : currentPolicyNameToDeleteForModal;
                deleteReassignAccrualModalMessage.innerHTML = `You are about to delete policy: <strong>${escapeHtmlAccruals(decodedName)}</strong>.<br>Affected employees must be reassigned:`;
                targetReassignAccrualSelect.innerHTML = ''; let defaultTarget = null;
                (window.allAvailableAccrualPoliciesForReassign || []).forEach(policy => {
                    if (policy.name !== currentPolicyNameToDeleteForModal) {
                        const opt = new Option(_decodeHtmlEntities(policy.name), policy.name); targetReassignAccrualSelect.add(opt);
                        if (policy.name.toLowerCase() === 'none') defaultTarget = opt.value;
                        else if (!defaultTarget && policy.name.toLowerCase() === 'standard') defaultTarget = opt.value;
                    }
                });
                if (defaultTarget) targetReassignAccrualSelect.value = defaultTarget;
                else if (targetReassignAccrualSelect.options.length > 0) targetReassignAccrualSelect.value = targetReassignAccrualSelect.options[0].value;
                else { if(typeof _showPageNotification === 'function') _showPageNotification("No alternative policies for reassignment. Please ensure 'None' or 'Standard' policies exist.", true, notificationModalGeneral,"Error"); return;}
                if (deleteReassignAccrualModalError) deleteReassignAccrualModalError.style.display = 'none';
                if(typeof _showModal === 'function') _showModal(deleteReassignAccrualModal);
            });
        } else { console.warn("Delete policy button or related modal elements missing"); }

        if(confirmDeleteAndReassignAccrualBtn && deleteAccrualForm && hiddenDeleteAccrualNameInput && targetReassignAccrualSelect) {
            confirmDeleteAndReassignAccrualBtn.addEventListener('click', () => {
                hiddenDeleteAccrualNameInput.value = currentPolicyNameToDeleteForModal;
                let targetInput = deleteAccrualForm.querySelector('input[name="targetAccrualPolicyForReassignment"]');
                if (!targetInput) { targetInput = document.createElement('input'); targetInput.type = 'hidden'; targetInput.name = 'targetAccrualPolicyForReassignment'; targetInput.id="hiddenTargetAccrualPolicyForReassignment"; deleteAccrualForm.appendChild(targetInput); }
                targetInput.value = targetReassignAccrualSelect.value;
                let actionInput = deleteAccrualForm.querySelector('input[name="action"]');
                if (actionInput) actionInput.value = 'deleteAndReassignAccrualPolicy'; 
                else { actionInput = document.createElement('input'); actionInput.type = 'hidden'; actionInput.name = 'action'; actionInput.value = 'deleteAndReassignAccrualPolicy'; deleteAccrualForm.appendChild(actionInput); }
                if (typeof _hideModal === 'function') _hideModal(deleteReassignAccrualModal); deleteAccrualForm.submit();
            });
        } else { console.warn("Confirm delete/reassign button or related form elements missing"); }
    }
    ensureBasicListeners_v18_fallback(); // Call to ensure the basic listeners from v18 are attached.

    if (closeWizardGenericPromptModalBtn && wizardGenericPromptModal) {
        closeWizardGenericPromptModalBtn.addEventListener('click', () => {
            console.log("[DEBUG Accruals.js] Wizard Generic Prompt Modal (Accruals) X clicked.");
            advanceWizardToServerAndRedirectAccruals("employees_prompt", `${appRoot}/employees.jsp?setup_wizard=true&action=review_admin`);
        });
    }
    
    togglePolicyActionButtons(); // Initialize button states

    console.log("accruals.js (v21_WizardCompleteIntegration) full setup complete.");
});