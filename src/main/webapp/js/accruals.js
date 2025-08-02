// js/accruals.js - v28_FocusAndDebug
document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] accruals.js (v28) loaded.");

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

    // --- Element Selectors ---
    const addPolicyBtn = document.getElementById('btnAddPolicy');
    const editPolicyBtn = document.getElementById('btnEditPolicy');
    const deletePolicyBtn = document.getElementById('btnDeletePolicy');
    const addAccrualModal = document.getElementById('addAccrualModal');
    const addAccrualForm = document.getElementById('addAccrualForm');
    const closeAddAccrual_X_Btn = document.getElementById('closeAddAccrual_X_Btn');
    const cancelAddAccrualBtn = document.getElementById('cancelAddAccrualBtn');
    const editAccrualModal = document.getElementById('editAccrualModal');
    const accrualsTable = document.getElementById('accrualsTable');
    const tableBody = accrualsTable ? accrualsTable.querySelector('tbody') : null;
    const deleteAccrualForm = document.getElementById('deleteAccrualForm');
    const hiddenDeleteAccrualNameInput = deleteAccrualForm ? deleteAccrualForm.querySelector('#hiddenDeleteAccrualName') : null;
    const deleteReassignAccrualModal = document.getElementById('deleteAndReassignAccrualModal');
    const confirmDeleteAndReassignAccrualBtn = document.getElementById('confirmDeleteAndReassignAccrualBtn');
    const targetReassignAccrualSelect = document.getElementById('targetReassignAccrualSelect');
    const deleteReassignAccrualModalMessage = document.getElementById('deleteReassignAccrualModalMessage');
    const notificationModalGeneral = document.getElementById('notificationModalGeneral');
    const specificWizardIntroModal = document.getElementById('setupWizardModal_Accruals');
    const manageAccrualsButtonWizard = document.getElementById('manageAccrualsButtonWizard');
    const skipAccrualsButtonWizard = document.getElementById('skipAccrualsButtonWizard');
    const closeSetupWizardAccrualsModal_X = document.getElementById('closeSetupWizardAccrualsModal_X');
    const wizardGenericPromptModal = document.getElementById('wizardGenericPromptModal_Accruals');
    const wizardGenericTitleElement = document.getElementById('wizardGenericPromptModalTitle_Accruals');
    const wizardGenericText1Element = document.getElementById('wizardGenericPromptModalText1_Accruals');
    const wizardGenericText2Element = document.getElementById('wizardGenericPromptModalText2_Accruals');
    const wizardGenericButtonRow = document.getElementById('wizardGenericPromptModalButtonRow_Accruals');
    
    let wizardOpenedAddPolicyModal = false;

    const wizardStagesAccruals = {
        "accruals_after_add_prompt": {
            title: "Setup: Accrual Policies",
            text1: "Accrual Policy added successfully!",
            text2: "Would you like to add another policy, or proceed to the next step?",
            buttons: [
                { id: "wizardActionAddAnotherAccrual", text: "Add Another Policy", class: "text-green", actionKey: "openAddAccrualModalViaWizard" },
                { id: "wizardActionNextFromAccrualAddToEmployees", text: "Continue to Next Step", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        }
    };
    
    function _hideAddPolicyModalAndHandleWizard() {
        if (addAccrualModal) _hideModal(addAccrualModal);
        if (window.inWizardMode_Page && wizardOpenedAddPolicyModal) {
            wizardOpenedAddPolicyModal = false;
            updateWizardModalViewAccruals("accruals_after_add_prompt");
        }
    }

    function updateWizardModalViewAccruals(stageKey) {
        if (!wizardGenericPromptModal) return;
        const stageConfig = wizardStagesAccruals[stageKey];
        if (!stageConfig) return;

        wizardGenericTitleElement.textContent = stageConfig.title;
        wizardGenericText1Element.innerHTML = stageConfig.text1;
        wizardGenericText2Element.innerHTML = stageConfig.text2;
        wizardGenericButtonRow.innerHTML = '';

        stageConfig.buttons.forEach(btnConfig => {
            const button = document.createElement('button');
            button.type = 'button';
            button.id = btnConfig.id;
            button.className = `glossy-button ${btnConfig.class}`;
            button.innerHTML = btnConfig.text;

            if (btnConfig.actionKey === "openAddAccrualModalViaWizard") {
                button.addEventListener('click', () => {
                    wizardOpenedAddPolicyModal = true;
                    _hideModal(wizardGenericPromptModal);
                    if (addPolicyBtn) addPolicyBtn.click();
                });
            } else if (btnConfig.actionKey === "advanceToEmployees") {
                button.addEventListener('click', () => {
                    advanceWizardToServerAndRedirectAccruals("editAdminProfile", `${appRoot}/employees.jsp?setup_wizard=true&action=edit_admin_profile`);
                });
            }
            wizardGenericButtonRow.appendChild(button);
        });
        _showModal(wizardGenericPromptModal);
    }

    function advanceWizardToServerAndRedirectAccruals(serverNextStep, redirectUrl) {
         fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': serverNextStep})
        }).then(response => response.json()).then(data => {
            if (data.success) {
                window.location.href = redirectUrl;
            } else {
                _showPageNotification("Could not proceed: " + (data.error || "Server error"), true, notificationModalGeneral, "Setup Error");
            }
        }).catch(error => _showPageNotification("Network error. Please try again.", true, notificationModalGeneral, "Network Error"));
    }

    // --- Main Wizard Logic and Listeners ---
    if (window.inWizardMode_Page) {
        const currentStep = window.currentWizardStep_Page;
        if (currentStep === 'accruals_prompt' && window.showSpecificIntroModal_Accruals_JS) {
            _showModal(specificWizardIntroModal);
        } else if (currentStep === 'accruals_after_add_prompt') {
            updateWizardModalViewAccruals('accruals_after_add_prompt');
        }

        if (manageAccrualsButtonWizard) {
            manageAccrualsButtonWizard.addEventListener('click', () => {
                _hideModal(specificWizardIntroModal);
                wizardOpenedAddPolicyModal = true; 
                if (addPolicyBtn) addPolicyBtn.click();
            });
        }
        if (skipAccrualsButtonWizard) {
            skipAccrualsButtonWizard.addEventListener('click', () => {
                _hideModal(specificWizardIntroModal);
                advanceWizardToServerAndRedirectAccruals("editAdminProfile", `${appRoot}/employees.jsp?setup_wizard=true&action=edit_admin_profile`);
            });
        }
        if (closeSetupWizardAccrualsModal_X) {
            closeSetupWizardAccrualsModal_X.addEventListener('click', () => _hideModal(specificWizardIntroModal));
        }
    }
    
    // --- Standard Page Functionality ---
    if (addPolicyBtn) {
        addPolicyBtn.addEventListener('click', () => {
            if (addAccrualForm) addAccrualForm.reset();
            _showModal(addAccrualModal);
            
            // ** THIS IS THE FIX for the focus issue **
            const nameInput = document.getElementById('addAccrualName');
            if (nameInput) {
                setTimeout(() => nameInput.focus(), 150); // Small delay for stability
            }
        });
    }

    // (The rest of the standard page functions for edit, delete, etc. remain unchanged)
    function selectAccrualRow(row) {
        const currentSelected = accrualsTable.querySelector('tr.selected');
        if (currentSelected) currentSelected.classList.remove('selected');
        if (row) {
            row.classList.add('selected');
            const policyName = row.dataset.name;
            editPolicyBtn.disabled = !policyName || policyName === 'None' || policyName === 'Standard';
            deletePolicyBtn.disabled = !policyName || policyName === 'None' || policyName === 'Standard';
        }
    }
    if (tableBody) tableBody.addEventListener('click', e => selectAccrualRow(e.target.closest('tr')));
    if (closeAddAccrual_X_Btn) closeAddAccrual_X_Btn.addEventListener('click', _hideAddPolicyModalAndHandleWizard);
    if (cancelAddAccrualBtn) cancelAddAccrualBtn.addEventListener('click', _hideAddPolicyModalAndHandleWizard);
    if (editPolicyBtn) {
        editPolicyBtn.addEventListener('click', () => {
            if (editPolicyBtn.disabled) return;
            const selectedRow = accrualsTable.querySelector('tr.selected');
            const data = selectedRow.dataset;
            const form = editAccrualModal.querySelector('form');
            form.originalAccrualName.value = data.name;
            form.editAccrualName.value = _decodeHtmlEntities(data.name);
            form.editVacationDays.value = data.vacation;
            form.editSickDays.value = data.sick;
            form.editPersonalDays.value = data.personal;
            _showModal(editAccrualModal);
        });
    }
    if (deletePolicyBtn) {
        deletePolicyBtn.addEventListener('click', () => {
            if (deletePolicyBtn.disabled) return;
            const selectedRow = accrualsTable.querySelector('tr.selected');
            const policyNameToDelete = selectedRow.dataset.name;
            deleteReassignAccrualModalMessage.innerHTML = `Delete policy: <strong>${_decodeHtmlEntities(policyNameToDelete)}</strong>.<br>Affected employees must be reassigned:`;
            targetReassignAccrualSelect.innerHTML = '';
            (window.allAvailableAccrualPoliciesForReassign || []).forEach(policy => {
                if (policy.name !== policyNameToDelete) {
                    targetReassignAccrualSelect.add(new Option(_decodeHtmlEntities(policy.name), policy.name));
                }
            });
            _showModal(deleteReassignAccrualModal);
        });
    }
    if (confirmDeleteAndReassignAccrualBtn) {
        confirmDeleteAndReassignAccrualBtn.addEventListener('click', () => {
            const selectedRow = accrualsTable.querySelector('tr.selected');
            hiddenDeleteAccrualNameInput.value = selectedRow.dataset.name;
            deleteAccrualForm.targetAccrualPolicyForReassignment.value = targetReassignAccrualSelect.value;
            deleteAccrualForm.submit();
        });
    }
});