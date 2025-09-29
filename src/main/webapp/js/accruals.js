// js/accruals.js

document.addEventListener('DOMContentLoaded', function() {

    // --- Helper Functions & Global Access ---
    const _showModal = showModal;
    const _hideModal = hideModal;
    const _decode = decodeHtmlEntities;
    const appRoot = window.appRootPath || "";

    // --- Element Selectors ---
    const addPolicyBtn = document.getElementById('btnAddPolicy');
    const editPolicyBtn = document.getElementById('btnEditPolicy');
    const deletePolicyBtn = document.getElementById('btnDeletePolicy');
    const accrualsTable = document.getElementById('accrualsTable');
    const tableBody = accrualsTable ? accrualsTable.querySelector('tbody') : null;
    
    // Modal Selectors
    const addAccrualModal = document.getElementById('addAccrualModal');
    const addAccrualForm = document.getElementById('addAccrualForm');
    const editAccrualModal = document.getElementById('editAccrualModal');
    const editAccrualForm = document.getElementById('editAccrualForm');
    const deleteAccrualForm = document.getElementById('deleteAccrualForm');
    const deleteReassignAccrualModal = document.getElementById('deleteAndReassignAccrualModal');
    const confirmDeleteAndReassignAccrualBtn = document.getElementById('confirmDeleteAndReassignAccrualBtn');
    const targetReassignAccrualSelect = document.getElementById('targetReassignAccrualSelect');
    const deleteReassignAccrualModalMessage = document.getElementById('deleteReassignAccrualModalMessage');
    
    // --- Notification Modal Selectors ---
    const notificationModal = document.getElementById('notificationModalGeneral');
    const notificationTitle = document.getElementById('notificationModalGeneralTitle');
    const notificationMessage = document.getElementById('notificationModalGeneralMessage');
    const okBtnGeneralNotify = document.getElementById('okButtonNotificationModalGeneral');
    const customBtnGeneralNotify = document.getElementById('customButtonNotificationModalGeneral');
    let notificationCallback = null;

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
    const companyNameToDisplayJS_Accruals = (typeof window.COMPANY_NAME_SIGNUP_JS_accruals !== 'undefined' && window.COMPANY_NAME_SIGNUP_JS_accruals) ? window.COMPANY_NAME_SIGNUP_JS_accruals : "your company";

    // --- Modern Notification Function ---
    function showPageNotification(message, isError, callback = null, title = null, options = {}) {
        const modalTitleSpan = notificationTitle.querySelector('span');
        const modalTitleIcon = notificationTitle.querySelector('i');

        notificationModal.classList.remove('modal-state-success', 'modal-state-error');
        notificationTitle.classList.remove('modal-title-success', 'modal-title-error');
        okBtnGeneralNotify.classList.remove('text-green', 'text-red', 'text-grey');
        modalTitleIcon.className = 'fas';

        modalTitleSpan.textContent = title || (isError ? 'Error' : 'Success');

        if (isError) {
            notificationModal.classList.add('modal-state-error');
            notificationTitle.classList.add('modal-title-error');
            modalTitleIcon.classList.add('fa-exclamation-triangle');
            okBtnGeneralNotify.classList.add('text-red');
        } else {
            notificationModal.classList.add('modal-state-success');
            notificationTitle.classList.add('modal-title-success');
            modalTitleIcon.classList.add('fa-check-circle');
            okBtnGeneralNotify.classList.add('text-green');
        }

        notificationMessage.innerHTML = message;
        notificationCallback = callback;

        if (options.customButtonText && options.customButtonAction) {
            customBtnGeneralNotify.textContent = options.customButtonText;
            customBtnGeneralNotify.style.display = 'inline-block';
            okBtnGeneralNotify.textContent = 'Cancel';
            okBtnGeneralNotify.classList.remove('text-green', 'text-red');
            okBtnGeneralNotify.classList.add('text-grey');
            customBtnGeneralNotify.className = 'glossy-button text-blue';
            customBtnGeneralNotify.onclick = () => {
                _hideModal(notificationModal);
                options.customButtonAction();
            };
        } else {
            customBtnGeneralNotify.style.display = 'none';
            customBtnGeneralNotify.onclick = null;
            okBtnGeneralNotify.textContent = 'OK';
        }
        _showModal(notificationModal);
    }

    // --- WIZARD HANDLING LOGIC ---
    const wizardStages = {
        "accruals_prompt": {
            title: "Setup: PTO Policies",
            text1: `Let's configure PTO policies for <strong>${companyNameToDisplayJS_Accruals}</strong>.`,
            text2: "You can create different policies for groups of employees, like 'Full-Time' or 'Part-Time'. A 'Standard' policy is provided.",
            buttons: [
                { id: "wizardActionAddPolicy", text: "Add a Policy", class: "text-green", actionKey: "openAddPolicyModalViaWizard" },
                { id: "wizardActionNextToEmployees", text: "Next: Verify Admin & Add Employees", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        },
        "accruals_after_add_prompt": {
            title: "Setup: PTO Policies",
            text1: "Great! You've defined a new PTO policy.",
            text2: "Add another policy, or proceed to the final step: adding your employees.",
            buttons: [
                { id: "wizardActionAddAnotherPolicy", text: "Add Another Policy", class: "text-green", actionKey: "openAddPolicyModalViaWizard" },
                { id: "wizardActionNextAfterAddToEmployees", text: "Next: Verify Admin & Add Employees", class: "text-blue", actionKey: "advanceToEmployees" }
            ]
        }
    };

    function updateWizardModalView(stageKey) {
        if (!wizardGenericModal) return;
        const stageConfig = wizardStages[stageKey];
        if (!stageConfig) {
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
                showPageNotification("Could not proceed: " + (data.error || "Server error: Invalid response."), true, null, "Setup Error");
            }
        })
        .catch(error => {
            showPageNotification("Network error advancing wizard.", true, null, "Network Error");
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
            const addAccrualNameInput = document.getElementById('addAccrualName');
            if (addAccrualNameInput) {
                setTimeout(() => addAccrualNameInput.focus(), 150);
            }
        }
    }
    
    function openDeleteAccrualModal() {
        const selectedRow = tableBody.querySelector('tr.selected');
        if (!selectedRow) return;
        const policyNameToDelete = selectedRow.dataset.name;
        
        deleteReassignAccrualModalMessage.innerHTML = `Delete policy: <strong>${_decode(policyNameToDelete)}</strong>.`;
        
        targetReassignAccrualSelect.innerHTML = '';
        targetReassignAccrualSelect.add(new Option('-- Select a Policy --', ''));

        (window.allAvailableAccrualPoliciesForReassign || []).forEach(policy => {
            if (policy.name !== policyNameToDelete) {
                targetReassignAccrualSelect.add(new Option(_decode(policy.name), policy.name));
            }
        });
        _showModal(deleteReassignAccrualModal);
    }

    function selectAccrualRow(row) {
        const currentSelected = tableBody.querySelector('tr.selected');
        if (row) {
            const policyName = row.dataset.name;
            const isDefault = policyName === 'None';
            if (isDefault) {
                showPageNotification(`'${policyName}' is a default policy and cannot be edited or deleted.`, true, null, "System Policy");
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
            const singleEmployeeLabel = document.getElementById('singleEmployeeLabel');
            const allEmployeesLabel = document.getElementById('allEmployeesLabel');
            
            allEmployeesToggle.addEventListener('change', function() {
                const singleEmployeeMode = !this.checked;
                employeeSelectContainer.style.display = singleEmployeeMode ? 'block' : 'none';
                employeeSelect.required = singleEmployeeMode;
                
                // Update label highlighting
                if (singleEmployeeLabel && allEmployeesLabel) {
                    if (singleEmployeeMode) {
                        singleEmployeeLabel.classList.add('active');
                        allEmployeesLabel.classList.remove('active');
                    } else {
                        singleEmployeeLabel.classList.remove('active');
                        allEmployeesLabel.classList.add('active');
                    }
                }
            });
            
            // Initialize the form state
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
            if (!isValid) { showPageNotification(errorMessage, true, null, "Validation Error"); return; }
            
            const formData = new FormData(adjustmentForm);
            const formActionUrl = adjustmentForm.getAttribute('action');

            fetch(formActionUrl, { method: 'POST', body: new URLSearchParams(formData) })
            .then(response => { if (!response.ok) { throw new Error(`Server responded with status: ${response.status}`); } return response.json(); })
            .then(data => {
                if (data.success) {
                    showPageNotification(data.message || "Adjustment applied successfully.", false, null, "Success");
                    adjustmentForm.reset();
                    allEmployeesToggle.dispatchEvent(new Event('change'));
                } else {
                    showPageNotification(data.error || "Adjustment failed.", true, null, "Adjustment Failed");
                }
            })
            .catch(error => {
                const displayError = error.message.includes("JSON") ? 'An unexpected response was received from the server.' : 'A network error occurred. Please try again.';
                showPageNotification(displayError, true, null, "Network Error");
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
            form.editAccrualName.value = _decode(data.name);
            form.editVacationDays.value = data.vacation;
            form.editSickDays.value = data.sick;
            form.editPersonalDays.value = data.personal;
            _showModal(editAccrualModal);
        });
    }

    if (deletePolicyBtn) {
        deletePolicyBtn.addEventListener('click', () => {
            if (deletePolicyBtn.disabled) return;
            openDeleteAccrualModal();
        });
    }

    if (confirmDeleteAndReassignAccrualBtn) {
        confirmDeleteAndReassignAccrualBtn.addEventListener('click', () => {
            const selectedRow = tableBody.querySelector('tr.selected');
            if (!selectedRow) return;

            const targetPolicy = targetReassignAccrualSelect.value;
            if (!targetPolicy) {
                showPageNotification('You must select a policy to reassign employees to.', true, null, 'Selection Required');
                return;
            }
            
            const hiddenNameInput = deleteAccrualForm.querySelector('#hiddenDeleteAccrualName');
            if(hiddenNameInput) hiddenNameInput.value = selectedRow.dataset.name;
            
            const targetPolicyInput = deleteAccrualForm.querySelector('#targetAccrualPolicyForReassignment');
            if(targetPolicyInput) targetPolicyInput.value = targetPolicy;
            
            deleteAccrualForm.submit();
        });
    }

    document.querySelectorAll('#addAccrualModal .close, #addAccrualModal .cancel-btn').forEach(btn => btn.addEventListener('click', _hideAddModalAndHandleWizard));
    document.querySelectorAll('#editAccrualModal .close, #editAccrualModal .cancel-btn, #deleteAndReassignAccrualModal .close, #deleteAndReassignAccrualModal .cancel-btn').forEach(btn => btn.addEventListener('click', (e) => _hideModal(e.target.closest('.modal'))));
    
    if (okBtnGeneralNotify) {
        okBtnGeneralNotify.addEventListener('click', () => {
             _hideModal(notificationModal);
            if (typeof notificationCallback === 'function') {
                notificationCallback();
                notificationCallback = null;
            }
        });
    }

    if (closeWizardGenericModalBtn) closeWizardGenericModalBtn.addEventListener('click', () => _hideModal(wizardGenericModal));

    // --- INITIALIZATION & URL PARAMETER HANDLING ---
    handleAdjustmentForm();

    if (window.inWizardMode_Page === true) {
        const urlParamsForWizard = new URLSearchParams(window.location.search);
        const justAdded = urlParamsForWizard.get('accrualAdded') === 'true';
        const currentStep = window.currentWizardStep_Page;
        const stage = justAdded ? "accruals_after_add_prompt" : (currentStep || "accruals_prompt");
        setTimeout(() => { updateWizardModalView(stage); }, 100);
    }

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('reopenModal') === 'addAccrual') {
        const errorMessage = urlParams.get('error');
        const prevPolicyName = urlParams.get('policyName');
        const addAccrualNameInput = document.getElementById('addAccrualName');
        openAddAccrualModal(); 
        if (addAccrualNameInput && prevPolicyName) addAccrualNameInput.value = _decode(prevPolicyName);
        if (errorMessage) {
            showPageNotification(errorMessage, true, null, "Validation Error");
            if (okBtnGeneralNotify) {
                const focusOnClose = () => {
                    _hideModal(notificationModal);
                    if (addAccrualNameInput) { addAccrualNameInput.focus(); addAccrualNameInput.select(); }
                    okBtnGeneralNotify.removeEventListener('click', focusOnClose); 
                };
                okBtnGeneralNotify.addEventListener('click', focusOnClose, { once: true });
            }
        }
        if (window.history.replaceState) {
            const cleanUrl = window.location.protocol + "//" + window.location.host + window.location.pathname + (window.inWizardMode_Page ? '?setup_wizard=true' : '');
            window.history.replaceState({path: cleanUrl}, '', cleanUrl);
        }
    }
    
    const successNotificationDiv = document.getElementById('pageNotificationDiv_Success_Accrual');
    if (successNotificationDiv && successNotificationDiv.textContent.trim()) {
        const message = successNotificationDiv.innerHTML;
        successNotificationDiv.style.display = 'none';
        showPageNotification(message, false, null, "Success");
    }

    const errorNotificationDiv = document.getElementById('pageNotificationDiv_Error_Accrual');
    if (errorNotificationDiv && errorNotificationDiv.textContent.trim()) {
        const message = errorNotificationDiv.innerHTML;
        errorNotificationDiv.style.display = 'none';
        showPageNotification(message, true, null, "Error");
    }
});