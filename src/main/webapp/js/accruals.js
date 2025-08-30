// js/accruals.js

document.addEventListener('DOMContentLoaded', function() {
    console.log("[ACCRUALS.JS] DOMContentLoaded: Script loaded.");

    // --- Helper Functions & Global Access ---
    const _showModal = showModal;
    const _hideModal = hideModal;
    const _decodeHtmlEntities = decodeHtmlEntities;

    // --- Element Selectors ---
    const addPolicyBtn = document.getElementById('btnAddPolicy');
    const editPolicyBtn = document.getElementById('btnEditPolicy');
    const deletePolicyBtn = document.getElementById('btnDeletePolicy');
    const accrualsTable = document.getElementById('accrualsTable');
    const tableBody = accrualsTable ? accrualsTable.querySelector('tbody') : null;
    
    // Modal Selectors
    const addAccrualModal = document.getElementById('addAccrualModal');
    const addAccrualForm = document.getElementById('addAccrualForm');
    const addAccrualNameInput = document.getElementById('addAccrualName');
    const editAccrualModal = document.getElementById('editAccrualModal');
    const deleteAccrualForm = document.getElementById('deleteAccrualForm');
    const hiddenDeleteAccrualNameInput = document.getElementById('hiddenDeleteAccrualName');
    const deleteReassignAccrualModal = document.getElementById('deleteAndReassignAccrualModal');
    const confirmDeleteAndReassignAccrualBtn = document.getElementById('confirmDeleteAndReassignAccrualBtn');
    const targetReassignAccrualSelect = document.getElementById('targetReassignAccrualSelect');
    const deleteReassignAccrualModalMessage = document.getElementById('deleteReassignAccrualModalMessage');
    const notificationModal = document.getElementById('notificationModalGeneral');
    const okBtnGeneralNotify = document.getElementById('okButtonNotificationModalGeneral');

    // WIZARD-SPECIFIC SELECTORS
    const wizardGenericModal = document.getElementById('wizardGenericModal');
    const wizardTitleElement = document.getElementById('wizardGenericModalTitle');
    const wizardText1Element = document.getElementById('wizardGenericModalText1');
    const wizardText2Element = document.getElementById('wizardGenericModalText2');
    const wizardButtonRow = document.getElementById('wizardGenericModalButtonRow');
    const closeWizardGenericModalBtn = document.getElementById('closeWizardGenericModal');

    // Adjustment Form Selectors
    const adjustmentForm = document.getElementById('adjustAccrualBalanceForm');
    const allEmployeesToggle = document.getElementById('allEmployeesToggle');
    const employeeSelectContainer = document.getElementById('employeeSelectContainer');
    const employeeSelect = document.getElementById('employeeSelect');
    const adjustmentHoursInput = document.getElementById('adjustmentHours');

    // --- State Variables ---
    let wizardOpenedAddModal = false;
    const appRoot = typeof window.appRootPath === 'string' ? window.appRootPath : "";
    const companyNameToDisplayJS_Accruals = (typeof window.COMPANY_NAME_SIGNUP_JS_ACCRUALS !== 'undefined' && window.COMPANY_NAME_SIGNUP_JS_ACCRUALS) ? window.COMPANY_NAME_SIGNUP_JS_ACCRUALS : "your company";

    // --- WIZARD HANDLING LOGIC ---
    const wizardStages = {
        "accruals_prompt": {
            title: "Setup: Accrual Policies",
            text1: `Let's configure time off policies for <strong>${companyNameToDisplayJS_Accruals}</strong>.`,
            text2: "You can create different policies for groups of employees, like 'Full-Time' or 'Part-Time'. A 'Standard' policy is provided.",
            buttons: [
                { id: "wizardActionAddPolicy", text: "Add a Policy", class: "text-green", actionKey: "openAddPolicyModalViaWizard" },
                { id: "wizardActionNextToEmployees", text: "Next: Verify Admin & Add Employees", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        },
        "accruals_after_add_prompt": {
            title: "Setup: Accrual Policies",
            text1: "Great! You've defined a new accrual policy.",
            text2: "Add another policy, or proceed to the final step: adding your employees.",
            buttons: [
                { id: "wizardActionAddAnotherPolicy", text: "Add Another Policy", class: "text-green", actionKey: "openAddPolicyModalViaWizard" },
                { id: "wizardActionNextAfterAddToEmployees", text: "Next: Verify Admin & Add Employees", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        }
    };

    function updateWizardModalView(stageKey) {
        console.log(`[ACCRUALS.JS] updateWizardModalView called with stage: '${stageKey}'`);
        if (!wizardGenericModal) {
            console.error("[ACCRUALS.JS] Wizard modal element not found. Cannot display wizard.");
            return;
        }
        const stageConfig = wizardStages[stageKey];
        if (!stageConfig) {
            console.warn(`[ACCRUALS.JS] No configuration found for wizard stage: '${stageKey}'. Hiding modal.`);
            _hideModal(wizardGenericModal);
            return;
        }

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
            button.addEventListener('click', () => handleWizardAction(btnConfig.actionKey));
            if(wizardButtonRow) wizardButtonRow.appendChild(button);
        });
        console.log("[ACCRUALS.JS] Wizard modal populated. Showing now.");
        _showModal(wizardGenericModal);
    }

    function handleWizardAction(actionKey) {
        if (actionKey === "openAddPolicyModalViaWizard") {
            wizardOpenedAddModal = true;
            _hideModal(wizardGenericModal);
            openAddAccrualModal();
        } else if (actionKey === "advanceToEmployees") {
            advanceWizardToServerAndRedirect("verify_admin_prompt", "employees.jsp");
        }
    }

    function advanceWizardToServerAndRedirect(serverNextStep, nextPage) {
         fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': serverNextStep})
        })
        .then(response => response.ok ? response.json() : Promise.reject('Failed to update wizard step.'))
        .then(data => {
            if (data.success && data.nextStep) {
                const redirectUrl = `${appRoot}/${nextPage}?setup_wizard=true&step=${encodeURIComponent(data.nextStep)}`;
                window.location.href = redirectUrl;
            } else {
                showPageNotification("Could not proceed: " + (data.error || "Server error: Invalid response."), true, notificationModal, "Setup Error");
            }
        })
        .catch(error => {
            console.error("Wizard advancement error:", error);
            showPageNotification("Network error advancing wizard.", true, notificationModal, "Network Error");
        });
    }
    
    function _hideAddModalAndHandleWizard() {
        _hideModal(addAccrualModal);
        if (window.inWizardMode_Page && wizardOpenedAddModal) {
            wizardOpenedAddModal = false;
            const nonDefaultRows = tableBody ? Array.from(tableBody.querySelectorAll('tr[data-name]')).filter(r => r.dataset.name && r.dataset.name !== 'None' && r.dataset.name !== 'Standard').length : 0;
            updateWizardModalView(nonDefaultRows > 0 ? "accruals_after_add_prompt" : "accruals_prompt");
        }
    }
    
    // --- Core Page Logic ---
    function openAddAccrualModal() {
        if (addAccrualModal) {
            if (addAccrualForm) addAccrualForm.reset();
            _showModal(addAccrualModal);
            if (addAccrualNameInput) {
                setTimeout(() => addAccrualNameInput.focus(), 150);
            }
        }
    }

    function selectAccrualRow(row) {
        const currentSelected = tableBody.querySelector('tr.selected');
        if (row) {
            const policyName = row.dataset.name;
            const isDefault = policyName === 'None';
            if (isDefault) {
                showPageNotification(`'${policyName}' is a default policy and cannot be edited or deleted.`, false, notificationModal, "System Policy");
                if (currentSelected) currentSelected.classList.remove('selected');
                if(editPolicyBtn) editPolicyBtn.disabled = true;
                if(deletePolicyBtn) deletePolicyBtn.disabled = true;
                return;
            }
            if (row === currentSelected) {
                row.classList.remove('selected');
                if(editPolicyBtn) editPolicyBtn.disabled = true;
                if(deletePolicyBtn) deletePolicyBtn.disabled = true;
            } else {
                if (currentSelected) currentSelected.classList.remove('selected');
                row.classList.add('selected');
                if(editPolicyBtn) editPolicyBtn.disabled = false;
                if(deletePolicyBtn) deletePolicyBtn.disabled = false;
            }
        } else {
            if (currentSelected) currentSelected.classList.remove('selected');
            if(editPolicyBtn) editPolicyBtn.disabled = true;
            if(deletePolicyBtn) deletePolicyBtn.disabled = true;
        }
    }

    // --- Adjustment Form Logic ---
    function handleAdjustmentForm() {
        if (!adjustmentForm) return;

        if (allEmployeesToggle) {
            allEmployeesToggle.addEventListener('change', function() {
                const singleEmployeeMode = !this.checked;
                employeeSelectContainer.style.display = singleEmployeeMode ? 'block' : 'none';
                employeeSelect.required = singleEmployeeMode;
            });
            allEmployeesToggle.dispatchEvent(new Event('change'));
        }

        adjustmentForm.addEventListener('submit', function(e) {
            e.preventDefault(); 
            let isValid = true;
            let errorMessage = '';
            const selectedOperation = adjustmentForm.querySelector('input[name="adjustmentOperation"]:checked').value;
            if (!allEmployeesToggle.checked && !employeeSelect.value) { isValid = false; errorMessage = 'Please select an employee.'; } 
            else if (adjustmentHoursInput.value === '') { isValid = false; errorMessage = 'Please enter a value for hours.'; } 
            else {
                const hours = parseFloat(adjustmentHoursInput.value);
                if (selectedOperation === 'set' && hours < 0) { isValid = false; errorMessage = 'Balance to set cannot be negative.'; } 
                else if (selectedOperation !== 'set' && hours <= 0) { isValid = false; errorMessage = 'Hours to add or subtract must be a positive number.'; }
            }
            if (!isValid) { showPageNotification(errorMessage, true, notificationModal, "Validation Error"); return; }
            
            const formData = new FormData(adjustmentForm);
            const formActionUrl = adjustmentForm.getAttribute('action');

            fetch(formActionUrl, { method: 'POST', body: new URLSearchParams(formData) })
            .then(response => { if (!response.ok) { throw new Error(`Server responded with status: ${response.status}`); } return response.json(); })
            .then(data => {
                // MODIFIED: Always use the modal for notifications
                if (data.success) {
                    showPageNotification(data.message || "Adjustment applied successfully.", false, notificationModal, "Success");
                    adjustmentForm.reset();
                    allEmployeesToggle.dispatchEvent(new Event('change'));
                } else {
                    showPageNotification(data.error || "Adjustment failed.", true, notificationModal, "Adjustment Failed");
                }
            })
            .catch(error => {
                console.error('Error submitting adjustment:', error);
                const displayError = error.message.includes("JSON") ? 'An unexpected response was received from the server.' : 'A network error occurred. Please try again.';
                showPageNotification(displayError, true, notificationModal, "Network Error");
            });
        });
    }

    // --- EVENT LISTENERS ---
    if (addPolicyBtn) addPolicyBtn.addEventListener('click', () => { if (window.inWizardMode_Page) { updateWizardModalView('accruals_prompt'); } else { openAddAccrualModal(); } });
    if (tableBody) tableBody.addEventListener('click', e => selectAccrualRow(e.target.closest('tr')));
    if (editPolicyBtn) {
        editPolicyBtn.addEventListener('click', () => {
            if (editPolicyBtn.disabled) return;
            const selectedRow = tableBody.querySelector('tr.selected');
            if (!selectedRow) return;
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
            const selectedRow = tableBody.querySelector('tr.selected');
            if (!selectedRow) return;
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
            const selectedRow = tableBody.querySelector('tr.selected');
            if (!selectedRow) return;
            if(hiddenDeleteAccrualNameInput) hiddenDeleteAccrualNameInput.value = selectedRow.dataset.name;
            const targetPolicyInput = deleteAccrualForm.querySelector('#targetAccrualPolicyForReassignment');
            if(targetPolicyInput) targetPolicyInput.value = targetReassignAccrualSelect.value;
            deleteAccrualForm.submit();
        });
    }
    document.querySelectorAll('#addAccrualModal .close, #addAccrualModal .cancel-btn').forEach(btn => btn.addEventListener('click', _hideAddModalAndHandleWizard));
    document.querySelectorAll('#editAccrualModal .close, #editAccrualModal .cancel-btn, #deleteAndReassignAccrualModal .close, #deleteAndReassignAccrualModal .cancel-btn').forEach(btn => btn.addEventListener('click', (e) => _hideModal(e.target.closest('.modal'))));
    if (okBtnGeneralNotify) okBtnGeneralNotify.addEventListener('click', () => _hideModal(notificationModal));
    if (closeWizardGenericModalBtn) closeWizardGenericModalBtn.addEventListener('click', () => _hideModal(wizardGenericModal));

    // --- INITIALIZATION & URL PARAMETER HANDLING ---
    handleAdjustmentForm();

    console.log(`[ACCRUALS.JS] Initializing Wizard check. Is in wizard mode? ${window.inWizardMode_Page}`);
    if (window.inWizardMode_Page === true) {
        const urlParamsForWizard = new URLSearchParams(window.location.search);
        const justAdded = urlParamsForWizard.get('accrualAdded') === 'true';
        const currentStep = window.currentWizardStep_Page;
        
        console.log(`[ACCRUALS.JS] Wizard context: justAdded=${justAdded}, currentStep='${currentStep}'`);
        
        const stage = justAdded ? "accruals_after_add_prompt" : (currentStep || "accruals_prompt");
        
        setTimeout(() => {
             updateWizardModalView(stage);
        }, 100);
    }

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('reopenModal') === 'addAccrual') {
        const errorMessage = urlParams.get('error');
        const prevPolicyName = urlParams.get('policyName');
        openAddAccrualModal(); 
        if (addAccrualNameInput && prevPolicyName) addAccrualNameInput.value = _decodeHtmlEntities(prevPolicyName);
        if (errorMessage) {
            showPageNotification(errorMessage, true, notificationModal, "Validation Error");
            if (okBtnGeneralNotify) {
                const focusOnClose = (e) => {
                    e.stopImmediatePropagation(); 
                    _hideModal(notificationModal);
                    if (addAccrualNameInput) { addAccrualNameInput.focus(); addAccrualNameInput.select(); }
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
    
    // ADDED: Check for and display the page-level success notification in a modal
    const successNotificationDiv = document.getElementById('pageNotificationDiv_Success_Accrual');
    if (successNotificationDiv && successNotificationDiv.textContent.trim()) {
        const message = successNotificationDiv.innerHTML;
        successNotificationDiv.style.display = 'none'; // Hide the original div
        showPageNotification(message, false, notificationModal, "Success");
    }
});