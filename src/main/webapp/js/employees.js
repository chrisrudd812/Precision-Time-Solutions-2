// js/employees.js - v32_EditModalCloseRefresh_Full
document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Employees Page DOMContentLoaded START (v32_EditModalCloseRefresh_Full) ---");

    // --- Helper Functions (Prefer commonUtils.js if available) ---
    const _decodeHtmlEntitiesLocalEmp = window.decodeHtmlEntities || function(text) {
        if (typeof text !== 'string') return text === null || typeof text === 'undefined' ? "" : String(text);
        const textarea = document.createElement('textarea');
        textarea.innerHTML = text;
        return textarea.value;
    };
    const _robustHideModalLocalEmp = window.hideModal || function(modalElement) {
        if (modalElement) { modalElement.style.display = 'none'; if(modalElement.classList) modalElement.classList.remove('modal-visible');} else { console.warn("hideModal: modalElement is null");}
    };
    const _robustShowModalLocalEmp = window.showModal || function(modalElement) {
        if (modalElement) { modalElement.style.display = 'flex'; if(modalElement.classList) modalElement.classList.add('modal-visible');} else { console.warn("showModal: modalElement is null");}
    };
    const _showPageNotificationLocalEmp = window.showPageNotification || function(message, isError = false, modalInstance = null, titleText = "Notification") {
        const modalToUse = modalInstance || document.getElementById("notificationModalGeneral");
        const msgElem = modalToUse ? (modalToUse.querySelector('#notificationModalGeneralMessage') || modalToUse.querySelector('#notificationMessage')) : null;
        const modalContent = modalToUse ? modalToUse.querySelector('.modal-content') : null;
        const modalTitleElem = modalToUse ? (modalToUse.querySelector('#notificationModalGeneralTitle') || modalToUse.querySelector('#notificationModalTitle')) : null;
        if (!modalToUse || !msgElem || !modalContent) { console.error("Local _showPageNotificationLocalEmp: Modal core elements missing. Fallback alert. Message:", message); alert((isError ? "Error: " : "Info: ") + titleText + "\n" + message); return; }
        if(modalTitleElem) modalTitleElem.textContent = titleText; msgElem.innerHTML = message; 
        modalContent.classList.toggle('error-message', isError);
        modalContent.classList.toggle('success-message', !isError);
        _robustShowModalLocalEmp(modalToUse);
    };
    const _clearUrlParamsEmployeesLocal = window.clearUrlParams || function(paramsToClear = ['setup_wizard', 'action', 'eid', 'message', 'error']) {
        try {
            const currentUrl = new URL(window.location.href); let paramsChanged = false;
            paramsToClear.forEach(param => { if (currentUrl.searchParams.has(param)) { currentUrl.searchParams.delete(param); paramsChanged = true; }});
            if (paramsChanged) window.history.replaceState({}, document.title, currentUrl.pathname + (currentUrl.search.length > 0 ? currentUrl.search : ''));
        } catch (e) { console.warn("URL param cleaning failed in employees.js (local fallback)."); }
    };
    const _makeTableSortableLocalEmp = window.makeTableSortable || function(table, options) { console.warn("makeTableSortable not globally available for employees.js, table sorting disabled."); };

    function _showPersistentWizardNavLocal(navElement) { if (navElement) navElement.style.display = 'block'; }
    function _hidePersistentWizardNavLocal(navElement) { if (navElement) navElement.style.display = 'none'; }

    // --- Window Globals (set by JSP) ---
    const inWizardMode = window.inSetupWizardMode_Js || false;
    const currentWizardStepFromJSP = window.currentWizardStep_Js || "";
    const initialActionFromJSP = window.initialWizardAction_Js || ""; 
    const showAdminIntro = window.initialWizardModalShouldShow_AdminProfileIntro_Js || false;
    const adminEidForEdit = window.adminEidForWizardEdit_Js || "";
    const loggedInAdminEid = window.loggedInAdminEid_Js || "";
    const showPromptAdd = window.initialWizardModalShouldShow_PromptAddEmployees_Js || false;
    const showSetupComplete = window.initialWizardModalShouldShow_SetupComplete_Js || false;
    const eidToSelectOnLoad = window.globalEidToSelectOnLoad_Js || 0; 
    const employeeJustAddedName = window.employeeJustAddedName_Js || "";
    const companyNameForWizard = window.COMPANY_NAME_SIGNUP_JS || "Your Company";
    const appRoot = typeof appRootPath === 'string' ? appRootPath : ""; 

    console.log("[employees.js] Parsed Globals (v32):", {
        inWizardMode, currentWizardStepFromJSP, initialActionFromJSP, adminEidForEdit, loggedInAdminEid,
        eidToSelectOnLoad, employeeJustAddedName, companyNameForWizard, appRoot
    });

    // --- Element Selectors ---
    const employeesTable = document.getElementById('employeesTable');
    const tableBody = employeesTable ? employeesTable.querySelector('tbody') : null;
    const tableHead = employeesTable ? employeesTable.querySelector('thead') : null;
    const detailsSection = document.getElementById('employeeDetailsSection');
    const addEmployeeModal = document.getElementById('addEmployeeModal');
    const editEmployeeModal = document.getElementById('editEmployeeModal');
    const notificationModalGeneral = document.getElementById("notificationModalGeneral");
    const wizardAdminProfileIntroModal = document.getElementById('wizardAdminProfileIntroModal');
    const wizardPromptAddEmployeesModal = document.getElementById('wizardPromptAddEmployeesModal');
    const wizardSetupCompleteModal = document.getElementById('wizardSetupCompleteModal');
    const wizardNavigationControls = document.getElementById('wizardNavigationControls_Employees');
    const wizardPromptMsgDiv = document.getElementById('wizardPromptAddEmployeesMessage');
    const addEmployeeForm = document.getElementById('addEmployeeForm');
    const editEmployeeForm = document.getElementById('editEmployeeForm');
    const addBtn = document.getElementById('addEmployeeButton');
    const editBtn = document.getElementById('editEmployeeButton');
    const deactivateBtn = document.getElementById('deactivateEmployeeButton');
    const okAdminProfileIntroButton = document.getElementById('okAdminProfileIntroButton');
    const wizardTriggerAddEmployeeModalButton = document.getElementById('wizardTriggerAddEmployeeModalButton');
    const wizardDoneAddingEmployeesButton = document.getElementById('wizardDoneAddingEmployeesButton');
    const wizardOkFinalButton = document.getElementById('wizardOkFinalButton');
    const wizardHelpButton = document.getElementById('wizardHelpButton');
    const closeWizardSetupCompleteModalXBtn = document.getElementById('closeWizardSetupCompleteModalXBtn');
    const persistentProceedAfterAdminEditButton = document.getElementById('persistentProceedAfterAdminEditButton');
    const persistentDoneAddingEmployeesButton = document.getElementById('persistentDoneAddingEmployeesButton');
    const closeAddEmployeeModalBtn = addEmployeeModal ? addEmployeeModal.querySelector('.close#closeAddEmployeeModal') : null;
    const cancelAddEmployeeBtn = document.getElementById('cancelAddEmployee');
    const closeEditEmployeeModalBtn = editEmployeeModal ? editEmployeeModal.querySelector('.close#closeEditEmployeeModal') : null;
    const cancelEditEmployeeBtn = document.getElementById('cancelEditEmployee');

    let selectedRow = null;
    let selectedGlobalEID = null;
    let selectedEmployeeDataForEdit = null;
    let wizardOpenedAddModal = false; 

    // --- Core Page Functions ---
    function toggleActionButtonsState() { 
        const enable = selectedRow !== null && selectedGlobalEID !== null;
        if (editBtn) editBtn.disabled = !enable;
        if (deactivateBtn) deactivateBtn.disabled = !enable;
        const resetPasswordButton = detailsSection ? detailsSection.querySelector('#btnResetPassword') : null;
        if(resetPasswordButton) resetPasswordButton.disabled = !enable;
    }
    function populateEmployeeDetails(row) { 
        if (!detailsSection) { hideEmployeeDetails(); return; }
        if (!row || !row.dataset || !row.dataset.eid) { hideEmployeeDetails(); return; }
        const setText = (id, value) => { const el = detailsSection.querySelector('#' + id); if (el) { let displayValue = (value && String(value).trim() !== '' && String(value).toLowerCase() !== 'null' && String(value).toLowerCase() !== 'n/a' ) ? _decodeHtmlEntitiesLocalEmp(value) : '--'; el.textContent = displayValue; }};
        setText('detailEID', row.dataset.tenantemployeenumber || row.dataset.eid); setText('detailFirstName', row.dataset.firstname); setText('detailLastName', row.dataset.lastname); setText('detailDept', row.dataset.dept); setText('detailSchedule', row.dataset.schedule); setText('detailSupervisor', row.dataset.supervisor); setText('detailPermissions', row.dataset.permissions); setText('detailAddress', row.dataset.address); setText('detailCity', row.dataset.city); setText('detailState', row.dataset.state); setText('detailZip', row.dataset.zip); setText('detailPhone', row.dataset.phone); setText('detailEmail', row.dataset.email); setText('detailAccrualPolicy', row.dataset.accrualpolicy); setText('detailVacHours', row.dataset.vachours); setText('detailSickHours', row.dataset.sickhours); setText('detailPersHours', row.dataset.pershours); setText('detailHireDate', row.dataset.hiredate); setText('detailWorkSched', row.dataset.worksched); setText('detailWageType', row.dataset.wagetype);
        const wageSpan = detailsSection.querySelector('#detailWage'); const wageRaw = row.dataset.wage || '0'; if (wageSpan) { try { const wageNum = parseFloat(wageRaw); wageSpan.textContent = !isNaN(wageNum) && wageNum > 0 ? wageNum.toLocaleString('en-US', { style: 'currency', currency: 'USD' }) : '--'; } catch (e) { wageSpan.textContent = '--'; } }
        const resetFormEidField = detailsSection.querySelector('#resetFormEid'); if (resetFormEidField) { resetFormEidField.value = row.dataset.eid; }
        detailsSection.style.display = 'block';
    }
    function hideEmployeeDetails() { if (detailsSection) detailsSection.style.display = 'none'; }
    function selectEmployeeRow(rowElement) { 
        const currentSelected = tableBody ? tableBody.querySelector('tr.selected') : null;
        if (currentSelected && currentSelected !== rowElement) currentSelected.classList.remove('selected');
        if (rowElement && rowElement.classList && typeof rowElement.dataset !== 'undefined' && rowElement.dataset.eid) {
            if (rowElement.classList.contains('selected') && currentSelected === rowElement) { 
                 rowElement.classList.remove("selected"); selectedRow = null; selectedGlobalEID = null; selectedEmployeeDataForEdit = null; hideEmployeeDetails();
            } else {
                rowElement.classList.add("selected"); selectedRow = rowElement; selectedGlobalEID = rowElement.dataset.eid; selectedEmployeeDataForEdit = { ...rowElement.dataset }; populateEmployeeDetails(rowElement); 
            }
        } else { if(currentSelected) currentSelected.classList.remove('selected'); selectedRow = null; selectedGlobalEID = null; selectedEmployeeDataForEdit = null; hideEmployeeDetails(); }
        toggleActionButtonsState();
    }
    function setFieldValue(form, selector, value, isDate = false) { 
        const field = form.querySelector(selector);
        if (field) {
            let valToSet = (value === null || typeof value === 'undefined' || String(value).toLowerCase() === 'null' || String(value).toLowerCase() === 'n/a') ? "" : _decodeHtmlEntitiesLocalEmp(String(value));
            if (isDate) { if (valToSet && /^\d{4}-\d{2}-\d{2}$/.test(valToSet)) { /* Correct */ } else if (valToSet) { try { const d = new Date(valToSet.replace(/-/g, '/').replace(/T.*/, '')); if (!isNaN(d.getTime())) { const year = d.getFullYear(); const month = ('0' + (d.getMonth() + 1)).slice(-2); const day = ('0' + d.getDate()).slice(-2); valToSet = `${year}-${month}-${day}`; } else { valToSet = ""; } } catch (e) { valToSet = ""; } }}
            field.value = valToSet;
        }
    }
    function populateAndShowEditModal(dataToEdit, isWizardAdminEdit = false) { 
        if (!editEmployeeModal || !editEmployeeForm || !dataToEdit || Object.keys(dataToEdit).length === 0) { console.error("populateAndShowEditModal: Missing elements or data."); return; }
        editEmployeeForm.reset(); 
        setFieldValue(editEmployeeForm, '#editEIDDisplay', dataToEdit.tenantemployeenumber || dataToEdit.eid);
        setFieldValue(editEmployeeForm, '#hiddenEditEID', dataToEdit.eid);
        setFieldValue(editEmployeeForm, '#editFirstName', dataToEdit.firstname); setFieldValue(editEmployeeForm, '#editLastName', dataToEdit.lastname);
        setFieldValue(editEmployeeForm, '#editDepartmentsDropDown', dataToEdit.dept || "None"); setFieldValue(editEmployeeForm, '#editSchedulesDropDown', dataToEdit.schedule || "");
        setFieldValue(editEmployeeForm, '#editSupervisor', dataToEdit.supervisor || ''); setFieldValue(editEmployeeForm, '#editPermissionsDropDown', dataToEdit.permissions || "User");
        setFieldValue(editEmployeeForm, '#editAddress', dataToEdit.address); setFieldValue(editEmployeeForm, '#editCity', dataToEdit.city);
        setFieldValue(editEmployeeForm, '#editState', dataToEdit.state || ""); setFieldValue(editEmployeeForm, '#editZip', dataToEdit.zip);
        setFieldValue(editEmployeeForm, '#editPhone', dataToEdit.phone); setFieldValue(editEmployeeForm, '#editEmail', dataToEdit.email);
        setFieldValue(editEmployeeForm, '#editAccrualsDropDown', dataToEdit.accrualpolicy || "None");
        setFieldValue(editEmployeeForm, '#editWorkScheduleDropDown', dataToEdit.worksched || "Full Time"); setFieldValue(editEmployeeForm, '#editWageTypeDropDown', dataToEdit.wagetype || "Hourly");
        const isoDateToUse = dataToEdit.isoDate || (dataToEdit.hiredate ? dataToEdit.hiredate : ""); 
        setFieldValue(editEmployeeForm, '#editHireDate', isoDateToUse, true); 
        const wageFieldEdit = document.getElementById('editWage'); if (wageFieldEdit) { wageFieldEdit.value = (dataToEdit.wage && parseFloat(dataToEdit.wage) > 0) ? parseFloat(dataToEdit.wage).toFixed(2) : ""; }
        _robustShowModalLocalEmp(editEmployeeModal);
        const focusFieldId = isWizardAdminEdit ? '#editDepartmentsDropDown' : '#editFirstName';
        const focusField = editEmployeeModal.querySelector(focusFieldId);
        if (focusField) { setTimeout(() => { focusField.focus();}, 250); }
    }
    function handleAdminProfileEditStep() { 
        let targetAdminEid = adminEidForEdit || loggedInAdminEid; 
        if (!targetAdminEid || String(targetAdminEid).trim() === "") { console.error("Wizard: Admin EID missing for profile edit."); _showPageNotificationLocalEmp("Cannot load administrator profile: ID missing.", true, notificationModalGeneral, "Error"); return; }
        if (!tableBody) { console.error("Wizard: tableBody not found."); _showPageNotificationLocalEmp("Cannot load administrator profile: Table data missing.", true, notificationModalGeneral, "Error");return; }
        const adminRow = tableBody.querySelector(`tr[data-eid="${targetAdminEid}"]`);
        if (adminRow) {
            selectEmployeeRow(adminRow); 
            if (selectedEmployeeDataForEdit && selectedEmployeeDataForEdit.eid === String(targetAdminEid)) { populateAndShowEditModal(selectedEmployeeDataForEdit, true); }
            else { console.error("Wizard: Admin data selection issue."); _showPageNotificationLocalEmp("Could not load administrator data for editing.", true, notificationModalGeneral, "Error"); }
        } else { console.warn("Wizard: Admin row EID '" + targetAdminEid + "' NOT found in table."); _showPageNotificationLocalEmp("Administrator profile not found. Please ensure the admin user exists and is active. You might need to add them manually if this is unexpected.", true, notificationModalGeneral, "Error");}
    }
    
    [addEmployeeForm, editEmployeeForm].forEach(form => { 
        if (form) {
            form.addEventListener('submit', function(event) {
                const wageInputId = form.id === 'addEmployeeForm' ? 'addWage' : 'editWage';
                const wageField = document.getElementById(wageInputId); let isValidForm = true;
                if (wageField) { 
                    if (wageField.value.trim() === '' && wageField.hasAttribute('required')) { _showPageNotificationLocalEmp("Wage is required.", true, notificationModalGeneral, "Validation Error"); wageField.focus(); isValidForm = false;}
                    else if (wageField.value.trim() !== "") { const wageVal = parseFloat(wageField.value); if (isNaN(wageVal) || wageVal <= 0) { _showPageNotificationLocalEmp("Wage must be a positive number greater than 0.00.", true, notificationModalGeneral, "Validation Error"); wageField.focus(); isValidForm = false; }}
                }
                const emailFieldId = form.id === 'addEmployeeForm' ? 'addEmail' : 'editEmail';
                const emailField = document.getElementById(emailFieldId);
                if (emailField && emailField.value.trim() !== "" && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailField.value.trim())) {
                     _showPageNotificationLocalEmp("Please enter a valid email address.", true, notificationModalGeneral, "Validation Error"); emailField.focus(); isValidForm = false;
                }

                if (!isValidForm) { event.preventDefault(); const submitButton = form.querySelector('button[type="submit"]'); if(submitButton) { submitButton.disabled = false; const icon = submitButton.querySelector('i'); if (form.id === 'addEmployeeForm' && icon) { submitButton.innerHTML = '<i class="fas fa-check"></i> Submit'; } else if (form.id === 'editEmployeeForm' && icon) { submitButton.innerHTML = '<i class="fas fa-save"></i> Update'; } }
                } else { const submitButton = form.querySelector('button[type="submit"]'); if(submitButton) { submitButton.disabled = true; submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';}}
            });
        }
    });

    // --- Wizard Logic ---
    if (inWizardMode) {
        console.log("[Employees.js] Wizard Mode Active. Effective Step for Logic:", currentWizardStepFromJSP);
        let effectiveWizardStep = currentWizardStepFromJSP;

        if (effectiveWizardStep === "editAdminProfile") {
            if (wizardNavigationControls) _showPersistentWizardNavLocal(wizardNavigationControls);
            if (showAdminIntro && wizardAdminProfileIntroModal) {
                _robustShowModalLocalEmp(wizardAdminProfileIntroModal);
                if (okAdminProfileIntroButton) {
                    okAdminProfileIntroButton.onclick = function() {
                        _robustHideModalLocalEmp(wizardAdminProfileIntroModal);
                        sessionStorage.setItem('admin_profile_intro_shown_employees_wizard', 'true');
                        handleAdminProfileEditStep();
                    };
                }
            } else { 
                handleAdminProfileEditStep();
            }
        } else if (effectiveWizardStep === "addMoreEmployees") {
            if (wizardNavigationControls) _showPersistentWizardNavLocal(wizardNavigationControls);
            if (wizardPromptAddEmployeesModal && wizardPromptMsgDiv) {
                if (employeeJustAddedName && employeeJustAddedName !== "") {
                    wizardPromptMsgDiv.innerHTML = `<p>Employee '<strong>${_decodeHtmlEntitiesLocalEmp(employeeJustAddedName)}</strong>' was added successfully!</p><p>Would you like to add another employee, or are you ready to finish the setup?</p>`;
                    if (eidToSelectOnLoad && eidToSelectOnLoad > 0 && tableBody) {
                        const newRow = tableBody.querySelector(`tr[data-eid="${eidToSelectOnLoad}"]`);
                        if (newRow) {
                            console.log("[Employees.js] Highlighting new employee row:", eidToSelectOnLoad);
                            newRow.classList.add('newly-added-highlight');
                            selectEmployeeRow(newRow); 
                            setTimeout(() => { newRow.classList.remove('newly-added-highlight'); }, 7000);
                        }
                    }
                } else {
                    wizardPromptMsgDiv.innerHTML = `<p>Your administrator profile is up to date for <strong>${companyNameForWizard}</strong>!</p><p>Now, you can add your other employees. Click "Add Another Employee" to begin.</p><p>When you're done adding employees, click "Finish Setup".</p>`;
                }
                if (showPromptAdd || (employeeJustAddedName && employeeJustAddedName !== "")) {
                     _robustShowModalLocalEmp(wizardPromptAddEmployeesModal);
                     if(wizardNavigationControls) _hidePersistentWizardNavLocal(wizardNavigationControls);
                }
            }
        } else if (effectiveWizardStep === "setupComplete") {
            if (wizardNavigationControls) _hidePersistentWizardNavLocal(wizardNavigationControls);
            if (showSetupComplete && wizardSetupCompleteModal) { 
                _robustShowModalLocalEmp(wizardSetupCompleteModal);
            } else {
                if(wizardSetupCompleteModal) _robustShowModalLocalEmp(wizardSetupCompleteModal);
            }
        } else {
            console.warn("[Employees.js] In wizard mode but no specific employee step matched:", effectiveWizardStep, ". Defaulting to admin profile.");
            if (adminEidForEdit || loggedInAdminEid) {
                 if (wizardNavigationControls) _showPersistentWizardNavLocal(wizardNavigationControls);
                 if (showAdminIntro && wizardAdminProfileIntroModal) _robustShowModalLocalEmp(wizardAdminProfileIntroModal);
                 else { handleAdminProfileEditStep(); }
            } else { 
                 showMainPageContent(true); 
                 console.error("[Employees.js] Cannot determine wizard action for admin profile, EID missing.");
            }
        }
    } else { 
        _clearUrlParamsEmployeesLocal(); 
        let rowSelectedOnLoad = false;
        if (eidToSelectOnLoad && eidToSelectOnLoad > 0 && tableBody) { const rowToSelectById = tableBody.querySelector(`tr[data-eid="${eidToSelectOnLoad}"]`); if (rowToSelectById) { selectEmployeeRow(rowToSelectById); rowSelectedOnLoad = true; }}
        function attemptFirstRowSelectionAfterModal() { if (rowSelectedOnLoad || document.querySelector('.modal.modal-visible')) return; if (tableBody && tableBody.rows.length > 0) { const firstDataRow = tableBody.querySelector('tr[data-eid]:not(.report-message-row):not(.report-error-row)'); if (firstDataRow) { selectEmployeeRow(firstDataRow); rowSelectedOnLoad = true; } else { selectEmployeeRow(null); }} else {selectEmployeeRow(null);}}
        if (notificationModalGeneral) { const isNotificationInitiallyVisible = (notificationModalGeneral.style.display !== 'none' && notificationModalGeneral.classList.contains('modal-visible')); if (!isNotificationInitiallyVisible && !rowSelectedOnLoad) { attemptFirstRowSelectionAfterModal(); } const obs = new MutationObserver((mutationsList, o) => { for(const m of mutationsList) { if ((m.attributeName === 'style' && notificationModalGeneral.style.display === 'none' && !rowSelectedOnLoad) || (m.attributeName === 'class' && !notificationModalGeneral.classList.contains('modal-visible') && !rowSelectedOnLoad)) { attemptFirstRowSelectionAfterModal(); o.disconnect(); return; } } }); obs.observe(notificationModalGeneral, { attributes: true });} else { attemptFirstRowSelectionAfterModal(); }
        if (!rowSelectedOnLoad && !document.querySelector('.modal.modal-visible')) { selectEmployeeRow(null); }
    }
    
    // --- Standard Button Listeners ---
    if (addBtn && addEmployeeModal && addEmployeeForm) {
        addBtn.addEventListener('click', function() { 
            wizardOpenedAddModal = false; // Standard click, not from wizard
            if (!addEmployeeModal || !addEmployeeForm) return; addEmployeeForm.reset(); 
            if (document.getElementById('addEIDDisplay')) document.getElementById('addEIDDisplay').placeholder = "System Assigned";
            const hireDateField = document.getElementById('addHireDate'); if (hireDateField) { try { const today = new Date(); const offset = today.getTimezoneOffset(); const todayLocal = new Date(today.getTime() - (offset*60*1000)); hireDateField.value = todayLocal.toISOString().split('T')[0]; } catch(e){} }
            const addWageField = document.getElementById('addWage'); if(addWageField) addWageField.value = ""; 
            ['addDepartmentsDropDown', 'addAccrualsDropDown'].forEach(id => { const el = document.getElementById(id); if(el && el.options && el.options.length > 0 && el.querySelector('option[value="None"]')) el.value = "None"; else if(el) el.value = "";});
            const addSupervisor = document.getElementById('addSupervisor'); if(addSupervisor) addSupervisor.value = "None";
            const addSched = document.getElementById('addSchedulesDropDown'); if(addSched) addSched.value="";
            ['addPermissionsDropDown', 'addWorkScheduleDropDown', 'addWageTypeDropDown'].forEach(id => { const el = document.getElementById(id); if(el && el.options && el.options.length > 0) el.value = el.options[0].value; });
            _robustShowModalLocalEmp(addEmployeeModal); 
            const firstNameInput = addEmployeeModal.querySelector('#addFirstName'); if (firstNameInput) setTimeout(() => firstNameInput.focus(), 150);
        });
    }
    if (editBtn && editEmployeeModal && editEmployeeForm) { 
        editBtn.addEventListener('click', function() { 
            if (editBtn.disabled || !selectedEmployeeDataForEdit) { _showPageNotificationLocalEmp("Please select an employee from the table to edit.", true, notificationModalGeneral, "Selection Required"); return; } 
            populateAndShowEditModal(selectedEmployeeDataForEdit, false); 
        });
    }
    
    if (closeAddEmployeeModalBtn) {
        closeAddEmployeeModalBtn.addEventListener("click", () => {
            _robustHideModalLocalEmp(addEmployeeModal);
            if (inWizardMode && wizardOpenedAddModal) {
                wizardOpenedAddModal = false; 
                if(wizardPromptAddEmployeesModal) _robustShowModalLocalEmp(wizardPromptAddEmployeesModal);
                if(wizardNavigationControls && currentWizardStepFromJSP === "addMoreEmployees") _showPersistentWizardNavLocal(wizardNavigationControls);
            }
        });
    }
    if (cancelAddEmployeeBtn) {
        cancelAddEmployeeBtn.addEventListener("click", () => {
            _robustHideModalLocalEmp(addEmployeeModal);
            if (inWizardMode && wizardOpenedAddModal) {
                wizardOpenedAddModal = false;
                if(wizardPromptAddEmployeesModal) _robustShowModalLocalEmp(wizardPromptAddEmployeesModal);
                if(wizardNavigationControls && currentWizardStepFromJSP === "addMoreEmployees") _showPersistentWizardNavLocal(wizardNavigationControls);
            }
        });
    }
    // CORRECTED: Add/Edit Modal Close/Cancel - Wizard Aware and Non-Wizard Refresh
    if (closeEditEmployeeModalBtn) {
        closeEditEmployeeModalBtn.addEventListener("click", () => { 
            console.log("[Employees.js] Edit modal X clicked. In wizard mode:", inWizardMode);
            _robustHideModalLocalEmp(editEmployeeModal); 
            if(!inWizardMode) {
                console.log("[Employees.js] Not in wizard mode. Reloading page for clean state.");
                window.location.href = `${appRoot}/employees.jsp`; 
            } else if (currentWizardStepFromJSP === "editAdminProfile") {
                if (wizardAdminProfileIntroModal && !sessionStorage.getItem('admin_profile_intro_shown_employees_wizard')) {
                     _robustShowModalLocalEmp(wizardAdminProfileIntroModal);
                } else if (wizardNavigationControls) {
                     _showPersistentWizardNavLocal(wizardNavigationControls);
                }
            }
        });
    }
    if (cancelEditEmployeeBtn) {
        cancelEditEmployeeBtn.addEventListener("click", () => { 
            console.log("[Employees.js] Edit modal Cancel button clicked. In wizard mode:", inWizardMode);
            _robustHideModalLocalEmp(editEmployeeModal); 
            if(!inWizardMode) {
                console.log("[Employees.js] Not in wizard mode. Reloading page for clean state.");
                window.location.href = `${appRoot}/employees.jsp`; 
            } else if (currentWizardStepFromJSP === "editAdminProfile") {
                 if (wizardAdminProfileIntroModal && !sessionStorage.getItem('admin_profile_intro_shown_employees_wizard')) {
                    _robustShowModalLocalEmp(wizardAdminProfileIntroModal);
                 } else if (wizardNavigationControls) {
                     _showPersistentWizardNavLocal(wizardNavigationControls);
                 }
            }
        });
    }
    
    if (deactivateBtn) { deactivateBtn.addEventListener('click', function() { 
         if (!selectedRow || !selectedGlobalEID) { _showPageNotificationLocalEmp('Please select an employee to deactivate.', true, notificationModalGeneral, "Selection Required"); return; } 
         const empName = `${_decodeHtmlEntitiesLocalEmp(selectedRow.dataset.firstname) || ''} ${_decodeHtmlEntitiesLocalEmp(selectedRow.dataset.lastname) || ''}`.trim(); 
         const displayEid = selectedRow.dataset.tenantemployeenumber || selectedGlobalEID; 
         if (confirm(`Are you sure you want to DEACTIVATE employee ${empName} (ID: ${displayEid})?`)) { window.location.href = `${appRoot}/AddEditAndDeleteEmployeesServlet?action=deactivateEmployee&eid=${selectedGlobalEID}`; }
    });}

    // --- Wizard Step Transition Button Listeners ---
    if (wizardTriggerAddEmployeeModalButton) { 
        wizardTriggerAddEmployeeModalButton.onclick = function() { 
            _robustHideModalLocalEmp(wizardPromptAddEmployeesModal); 
            wizardOpenedAddModal = true; 
            if (addBtn) addBtn.click(); else console.error("Main Add Employee button not found!"); 
            if (wizardNavigationControls) _hidePersistentWizardNavLocal(wizardNavigationControls);
        };
    }
    if (wizardDoneAddingEmployeesButton) {  
        wizardDoneAddingEmployeesButton.innerHTML = '<i class="fas fa-flag-checkered"></i> Finish Setup'; 
        wizardDoneAddingEmployeesButton.onclick = function() { 
            _robustHideModalLocalEmp(wizardPromptAddEmployeesModal); 
            console.log("[Employees.js] 'Finish Setup' clicked. Redirecting to setup_complete action.");
            window.location.href = `${appRoot}/employees.jsp?setup_wizard=true&action=setup_complete`; 
        };
    }
    
    function finalizeWizardClientSide(serverSuccess, serverMessage) { 
        window.inSetupWizardMode_Js = false; 
        window.currentWizardStep_Js = "setupComplete_clientFinalized"; 
        _clearUrlParamsEmployeesLocal(['setup_wizard', 'action', 'eid']); 
        
        if(wizardNavigationControls) _hidePersistentWizardNavLocal(wizardNavigationControls);
        
        document.title = "Employee Management";
        const mainHeading = document.querySelector('.parent-container.reports-container > h1');
        if(mainHeading && mainHeading.innerHTML.includes("(Setup)")) {
            mainHeading.innerHTML = "Employee Management";
        }

        if (!serverSuccess) { 
            _showPageNotificationLocalEmp(serverMessage || "Could not fully finalize setup on server.", true, notificationModalGeneral, "Setup Finalization Issue");
        }
    }

    if (wizardOkFinalButton) {  
        wizardOkFinalButton.innerHTML = '<i class="fas fa-users"></i> Go to Employees Page'; 
        wizardOkFinalButton.onclick = function() { 
            _robustHideModalLocalEmp(wizardSetupCompleteModal); 
            fetch(`${appRoot}/WizardStatusServlet?action=completeWizard`, { method: 'POST' })
            .then(r => {
                if (!r.ok) { return r.text().then(text => Promise.reject({error: `Server error ${r.status}: ${text || 'No details'}`})).catch(() => Promise.reject({error: `Server error ${r.status}`})); }
                return r.json();
            })
            .then(d => { 
                if(d.success) { 
                    finalizeWizardClientSide(true, "Setup complete!");
                    window.location.href = `${appRoot}/employees.jsp`; // Redirect to clean employees page
                } else { 
                    finalizeWizardClientSide(false, d.error || 'Unknown error completing wizard on server.');
                     _showPageNotificationLocalEmp(d.error || "Failed to finalize setup.", true, notificationModalGeneral, "Error");
                }
            })
            .catch(e => { 
                finalizeWizardClientSide(false, e.error || e.message || "Network error completing wizard."); 
                console.error("Fetch error for completeWizard:", e);
                _showPageNotificationLocalEmp("Error communicating with server to finalize setup. " + (e.error || e.message || "Please try again."), true, notificationModalGeneral, "Communication Error");
            }); 
        }; 
    }
    if (wizardHelpButton) { 
         wizardHelpButton.addEventListener('click', function() { 
            _robustHideModalLocalEmp(wizardSetupCompleteModal); 
            fetch(`${appRoot}/WizardStatusServlet?action=completeWizard`, { method: 'POST' }) 
            .then(r => r.ok ? r.json() : r.json().then(e => Promise.reject(e)).catch(() => Promise.reject({error: "Invalid JSON."})))
            .then(d => { if(d.success) console.log("Wizard flags cleared before help."); else console.error("Server failed to clear flags.", d.error);})
            .catch(e => console.error('Error completing wizard before help:', e.error || e.message))
            .finally(() => { finalizeWizardClientSide(true, null); window.location.href = `${appRoot}/help.jsp`; });
        });
    }
    if(closeWizardSetupCompleteModalXBtn) { 
        closeWizardSetupCompleteModalXBtn.onclick = function() {
             _robustHideModalLocalEmp(wizardSetupCompleteModal);
            fetch(`${appRoot}/WizardStatusServlet?action=completeWizard`, { method: 'POST' })
            .then(r => r.ok ? r.json() : r.json().then(eData => Promise.reject(eData)).catch(() => Promise.reject({error: `HTTP error ${r.status}`})))
            .then(d => { if(d.success) { finalizeWizardClientSide(true, null); window.location.href = `${appRoot}/employees.jsp`;} else { finalizeWizardClientSide(false, d.error);}})
            .catch(e => { finalizeWizardClientSide(false, e.message || "Error finalizing wizard."); });
        };
    }

    if (persistentProceedAfterAdminEditButton) { persistentProceedAfterAdminEditButton.addEventListener('click', function() { 
        if (editEmployeeModal && (editEmployeeModal.style.display === 'flex' || editEmployeeModal.classList.contains('modal-visible'))) {
            _showPageNotificationLocalEmp("Please save or cancel your profile changes in the 'Edit Employee' window first.", true, notificationModalGeneral, "Action Required");
        } else {
             window.location.href = `${appRoot}/employees.jsp?setup_wizard=true&action=prompt_add_employees&eid=${loggedInAdminEid}`; 
        }
    });}
    if (persistentDoneAddingEmployeesButton){ 
        persistentDoneAddingEmployeesButton.innerHTML = '<i class="fas fa-flag-checkered"></i> Finish Setup'; 
        persistentDoneAddingEmployeesButton.addEventListener('click', function(){ 
            window.location.href = `${appRoot}/employees.jsp?setup_wizard=true&action=setup_complete`; 
    });}

    // Initial UI state
    toggleActionButtonsState(); 
    if (tableHead && employeesTable && typeof _makeTableSortableLocalEmp === 'function') { 
        _makeTableSortableLocalEmp(employeesTable, { columnIndex: 0, ascending: true }); 
    }
    
    const pageNotificationSuccessEmp = document.getElementById('pageNotification_Success_Emp');
    const pageNotificationErrorEmp = document.getElementById('pageNotification_Error_Emp');
    function autoHidePageNotification(notificationElement) {
        if (notificationElement && notificationElement.textContent.trim() !== '' && notificationElement.style.display !== 'none') { 
            setTimeout(() => { 
                if(notificationElement) {
                    notificationElement.style.transition = 'opacity 0.5s ease-out';
                    notificationElement.style.opacity = '0'; 
                    setTimeout(() => { 
                        if(notificationElement) {
                            notificationElement.style.display = 'none'; 
                            notificationElement.style.opacity = '1'; // Reset for next time
                        }
                    }, 500);
                }
            }, 7000); 
        }
    }
    autoHidePageNotification(pageNotificationSuccessEmp);
    autoHidePageNotification(pageNotificationErrorEmp);

     if (notificationModalGeneral) {
        const okBtn = notificationModalGeneral.querySelector('#okButtonNotificationModalGeneral');
        // Ensure the close button selector is specific to the general notification modal in employees.jsp
        const closeBtn = notificationModalGeneral.querySelector('#closeNotificationModalGeneral_Emp') || notificationModalGeneral.querySelector('.close[data-close-modal-id="notificationModalGeneral"]');
        if (okBtn) okBtn.addEventListener('click', () => _robustHideModalLocalEmp(notificationModalGeneral));
        if (closeBtn) closeBtn.addEventListener('click', () => _robustHideModalLocalEmp(notificationModalGeneral));
    }
    if (!tableBody && employeesTable) { console.error("FATAL: Table body (tbody) not found within employeesTable after all initializations.");}
    else if (!employeesTable) { console.error("FATAL: employeesTable itself not found.");}


    console.log("--- Employees Page DOMContentLoaded END (v32_EditModalCloseRefresh_Full) ---");
});