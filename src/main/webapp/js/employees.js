// js/employees.js (v28 - Auto-Select and Final Polish)
function initializeEmployeesPage() {
    const _hideModal = (modalElement) => { if (modalElement) { modalElement.style.display = 'none'; modalElement.classList.remove('modal-visible'); } };
    const _showModal = (modalElement) => { if (modalElement) { modalElement.style.display = 'flex'; modalElement.classList.add('modal-visible'); } };
    const _decodeHtml = (text) => {
        if (typeof text !== 'string') return text;
        const textarea = document.createElement('textarea'); textarea.innerHTML = text; return textarea.value;
    };
    function setFieldValue(form, selector, value) {
        const field = form.querySelector(selector);
        if (field) {
            let valToSet = (value === null || typeof value === 'undefined' || String(value).toLowerCase() === 'null') ? "" : _decodeHtml(String(value));
            if (field.type === 'date' && valToSet && !/^\d{4}-\d{2}-\d{2}$/.test(valToSet)) {
                try {
                    const d = new Date(valToSet.replace(/-/g, '/').replace(/T.*/, ''));
                    if (!isNaN(d.getTime())) valToSet = d.toISOString().split('T')[0]; else valToSet = "";
                } catch(e) { valToSet = ""; }
            }
            field.value = valToSet;
        }
    }
    function equalizeDetailPanelHeights() {
        const detailPanels = document.querySelectorAll('.details-grid .detail-group');
        if (!detailPanels || detailPanels.length === 0) return;
        detailPanels.forEach(panel => { panel.style.minHeight = 'auto'; });
        let maxHeight = 0;
        detailPanels.forEach(panel => { if (panel.offsetHeight > maxHeight) maxHeight = panel.offsetHeight; });
        if (maxHeight > 0) { detailPanels.forEach(panel => { panel.style.minHeight = maxHeight + 'px'; }); }
    }

    const inWizardMode = window.inSetupWizardMode_Js;
    const currentWizardStep = window.currentWizardStep_Js;
    const showAdminIntro = window.initialWizardModalShouldShow_AdminProfileIntro_Js;
    const adminEidForEdit = window.adminEidForWizardEdit_Js;
    const employeeJustAddedName = window.employeeJustAddedName_Js;
    const tableBody = document.querySelector('#employeesTable tbody');
    const detailsSection = document.getElementById('employeeDetailsSection');
    const addEmployeeModal = document.getElementById('addEmployeeModal');
    const editEmployeeModal = document.getElementById('editEmployeeModal');
    const addEmployeeForm = document.getElementById('addEmployeeForm');
    const editEmployeeForm = document.getElementById('editEmployeeForm');
    const addBtn = document.getElementById('addEmployeeButton');
    const editBtn = document.getElementById('editEmployeeButton');
    const deactivateBtn = document.getElementById('deactivateEmployeeButton');
    const cancelAddEmployeeBtn = document.getElementById('cancelAddEmployee');
    const cancelEditEmployeeBtn = document.getElementById('cancelEditEmployee');
    const closeAddEmployeeModalBtn = document.getElementById('closeAddEmployeeModal');
    const closeEditEmployeeModalBtn = document.getElementById('closeEditEmployeeModal');
    const deactivateConfirmModal = document.getElementById('deactivateConfirmModal');
    const confirmDeactivateBtn = document.getElementById('confirmDeactivateBtn');
    const cancelDeactivateBtn = document.getElementById('cancelDeactivateBtn');
    const closeDeactivateModalBtn = document.getElementById('closeDeactivateModal');
    const deactivateEmployeeName = document.getElementById('deactivateEmployeeName');
    const deactivateEmployeeForm = document.getElementById('deactivateEmployeeForm');
    const wizardAdminProfileIntroModal = document.getElementById('wizardAdminProfileIntroModal');
    const okAdminProfileIntroButton = document.getElementById('okAdminProfileIntroButton');
    const wizardPromptAddEmployeesModal = document.getElementById('wizardPromptAddEmployeesModal');
    const wizardPromptMsgDiv = document.getElementById('wizardPromptAddEmployeesMessage');
    const wizardSetupCompleteModal = document.getElementById('wizardSetupCompleteModal');
    const wizardTriggerAddEmployeeModalButton = document.getElementById('wizardTriggerAddEmployeeModalButton');
    const wizardDoneAddingEmployeesButton = document.getElementById('wizardDoneAddingEmployeesButton');
    const wizardOkFinalButton = document.getElementById('wizardOkFinalButton');
    const wizardHelpButton = document.getElementById('wizardHelpButton');
    
    let selectedRow = null;
    let selectedGlobalEID = null;

    function toggleActionButtonsState() {
        const enable = selectedRow !== null;
        if (editBtn) editBtn.disabled = !enable;
        if (deactivateBtn) deactivateBtn.disabled = !enable;
    }
    
    function populateEmployeeDetails(row) {
        if (!detailsSection || !row) return;
        const data = row.dataset;
        const setText = (id, value) => {
            const el = detailsSection.querySelector('#' + id);
            if (el) el.textContent = (value && value !== 'null' && value.trim() !== '') ? _decodeHtml(value) : '--';
        };
        setText('detailEID', data.tenantemployeenumber);
        setText('detailFirstName', data.firstname);
        setText('detailLastName', data.lastname);
        setText('detailAddress', data.address);
        setText('detailCity', data.city);
        setText('detailState', data.state);
        setText('detailZip', data.zip);
        setText('detailPhone', data.phone);
        setText('detailEmail', data.email);
        setText('detailDept', data.dept);
        setText('detailSchedule', data.schedule);
        setText('detailSupervisor', data.supervisor);
        setText('detailPermissions', data.permissions);
        setText('detailHireDate', data.hiredate);
        setText('detailWorkSched', data.worksched);
        setText('detailWageType', data.wagetype);
        setText('detailAccrualPolicy', data.accrualpolicy);
        setText('detailVacHours', data.vachours);
        setText('detailSickHours', data.sickhours);
        setText('detailPersHours', data.pershours);
        const wageSpan = detailsSection.querySelector('#detailWage');
        if(wageSpan) {
            const wage = parseFloat(data.wage);
            wageSpan.textContent = (!isNaN(wage) && wage > 0) ? new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(wage) : '--';
        }
        if(document.getElementById('resetFormEid')) document.getElementById('resetFormEid').value = data.eid;
        if(document.getElementById('btnResetPassword')) document.getElementById('btnResetPassword').disabled = false;
        detailsSection.style.display = 'grid';
        setTimeout(equalizeDetailPanelHeights, 10);
    }

    function hideEmployeeDetails() {
        if (detailsSection) detailsSection.style.display = 'none';
        if(document.getElementById('btnResetPassword')) document.getElementById('btnResetPassword').disabled = true;
    }

    function selectEmployeeRow(rowElement) {
        const currentSelected = tableBody.querySelector('tr.selected');
        if (currentSelected) currentSelected.classList.remove('selected');
        if (rowElement && rowElement !== currentSelected) {
            rowElement.classList.add('selected');
            selectedRow = rowElement;
            selectedGlobalEID = rowElement.dataset.eid;
            populateEmployeeDetails(rowElement);
        } else {
            selectedRow = null;
            selectedGlobalEID = null;
            hideEmployeeDetails();
        }
        toggleActionButtonsState();
    }
    
    function populateAndShowEditModal(employeeData, isWizardStep = false) {
        if (!editEmployeeModal || !editEmployeeForm || !employeeData) return;
        editEmployeeForm.reset();
        setFieldValue(editEmployeeForm, '#hiddenEditEID', employeeData.eid);
        setFieldValue(editEmployeeForm, '#editEIDDisplay', employeeData.tenantemployeenumber);
        setFieldValue(editEmployeeForm, '#editFirstName', employeeData.firstname);
        setFieldValue(editEmployeeForm, '#editLastName', employeeData.lastname);
        setFieldValue(editEmployeeForm, '#editDepartmentsDropDown', employeeData.dept);
        setFieldValue(editEmployeeForm, '#editSchedulesDropDown', employeeData.schedule);
        setFieldValue(editEmployeeForm, '#editSupervisor', employeeData.supervisor);
        setFieldValue(editEmployeeForm, '#editAccrualsDropDown', employeeData.accrualpolicy);
        setFieldValue(editEmployeeForm, '#editAddress', employeeData.address);
        setFieldValue(editEmployeeForm, '#editCity', employeeData.city);
        setFieldValue(editEmployeeForm, '#editState', employeeData.state);
        setFieldValue(editEmployeeForm, '#editZip', employeeData.zip);
        setFieldValue(editEmployeeForm, '#editPhone', employeeData.phone);
        setFieldValue(editEmployeeForm, '#editEmail', employeeData.email);
        setFieldValue(editEmployeeForm, '#editHireDate', employeeData.hiredate);
        setFieldValue(editEmployeeForm, '#editWage', employeeData.wage);
        setFieldValue(editEmployeeForm, '#editWorkScheduleDropDown', employeeData.worksched || 'Full Time');
        setFieldValue(editEmployeeForm, '#editWageTypeDropDown', employeeData.wagetype || 'Hourly');
        _showModal(editEmployeeModal);

        if (isWizardStep) {
            setFieldValue(editEmployeeForm, '#editPermissionsDropDown', 'Administrator');
            const deptDropdown = editEmployeeForm.querySelector('#editDepartmentsDropDown');
            if (deptDropdown) setTimeout(() => deptDropdown.focus(), 150);
        } else {
             setFieldValue(editEmployeeForm, '#editPermissionsDropDown', employeeData.permissions);
        }
    }
    
    function fetchAndEditEmployee(eid, isWizardStep = false) {
        if (!eid) return;
        fetch(`EmployeeInfoServlet?action=getEmployeeDetails&eid=${eid}`)
            .then(response => response.ok ? response.json() : response.text().then(text => { throw new Error(`Network error: ${response.status}. ${text}`); }))
            .then(data => {
                if (data.success && data.employee) {
                    populateAndShowEditModal(data.employee, isWizardStep);
                } else {
                    throw new Error(data.error || 'Failed to retrieve employee profile.');
                }
            })
            .catch(error => alert(`Could not load employee profile. Error: ${error.message}`));
    }

    function runWizard() {
        if (!inWizardMode) return;
        if (currentWizardStep === "editAdminProfile") {
            if (showAdminIntro) { _showModal(wizardAdminProfileIntroModal); } 
            else { fetchAndEditEmployee(adminEidForEdit, true); }
        } else if (currentWizardStep === "addMoreEmployees") {
            if (wizardPromptMsgDiv) {
                if (employeeJustAddedName) { wizardPromptMsgDiv.innerHTML = `<p>Employee '<strong>${_decodeHtml(employeeJustAddedName)}</strong>' added!</p>`; } 
                else { wizardPromptMsgDiv.innerHTML = `<p>Your profile is up to date!</p>`; }
                wizardPromptMsgDiv.innerHTML += `<p>Now, you can add your employees. When you're done, click "Finish Setup".</p>`;
                _showModal(wizardPromptAddEmployeesModal);
            }
        } else if (currentWizardStep === "setupComplete") {
            _showModal(wizardSetupCompleteModal);
        }
    }

    function autoSelectRowOnLoad() {
        const urlParams = new URLSearchParams(window.location.search);
        const eidFromUrl = urlParams.get('eid');
        let rowToSelect = null;

        if (eidFromUrl) {
            rowToSelect = tableBody.querySelector(`tr[data-eid="${eidFromUrl}"]`);
        } else if (!inWizardMode) {
            rowToSelect = tableBody.querySelector('tr:not(.report-error-row):not(.report-message-row)');
        }

        if (rowToSelect) {
            selectEmployeeRow(rowToSelect);
            rowToSelect.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    if (tableBody) tableBody.addEventListener('click', (e) => selectEmployeeRow(e.target.closest('tr')));
    if (addBtn) {
        addBtn.addEventListener('click', () => {
            if (addEmployeeForm) {
                addEmployeeForm.reset();
                setFieldValue(addEmployeeForm, '#addWorkScheduleDropDown', 'Full Time');
                setFieldValue(addEmployeeForm, '#addWageTypeDropDown', 'Hourly');
                const firstNameInput = addEmployeeForm.querySelector('#addFirstName');
                if (firstNameInput) setTimeout(() => firstNameInput.focus(), 150);
            }
            _showModal(addEmployeeModal);
        });
    }
    if (editBtn) editBtn.addEventListener('click', () => { if (selectedGlobalEID) fetchAndEditEmployee(selectedGlobalEID, false); });
    if (closeAddEmployeeModalBtn) closeAddEmployeeModalBtn.addEventListener('click', () => _hideModal(addEmployeeModal));
    if (cancelAddEmployeeBtn) cancelAddEmployeeBtn.addEventListener('click', () => _hideModal(addEmployeeModal));
    if (closeEditEmployeeModalBtn) closeEditEmployeeModalBtn.addEventListener('click', () => _hideModal(editEmployeeModal));
    if (cancelEditEmployeeBtn) cancelEditEmployeeBtn.addEventListener('click', () => _hideModal(editEmployeeModal));
    
    if (deactivateBtn) {
        deactivateBtn.addEventListener('click', () => {
            if (!selectedRow) return;
            deactivateEmployeeName.textContent = `${_decodeHtml(selectedRow.dataset.firstname)} ${_decodeHtml(selectedRow.dataset.lastname)}`;
            _showModal(deactivateConfirmModal);
        });
    }
    if (confirmDeactivateBtn) {
        confirmDeactivateBtn.addEventListener('click', () => {
            if (selectedGlobalEID) {
                deactivateEmployeeForm.querySelector('#deactivateEidInput').value = selectedGlobalEID;
                deactivateEmployeeForm.submit();
            }
        });
    }
    if(cancelDeactivateBtn) cancelDeactivateBtn.addEventListener('click', () => _hideModal(deactivateConfirmModal));
    if(closeDeactivateModalBtn) closeDeactivateModalBtn.addEventListener('click', () => _hideModal(deactivateConfirmModal));

    if (okAdminProfileIntroButton) {
        okAdminProfileIntroButton.onclick = function() {
            _hideModal(wizardAdminProfileIntroModal);
            fetchAndEditEmployee(adminEidForEdit, true);
            fetch(`WizardStatusServlet`, { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: 'action=markAdminIntroAsShown'});
        };
    }
    if (wizardDoneAddingEmployeesButton) wizardDoneAddingEmployeesButton.addEventListener('click', () => { _hideModal(wizardPromptAddEmployeesModal); _showModal(wizardSetupCompleteModal); });
    if (wizardTriggerAddEmployeeModalButton) {
        wizardTriggerAddEmployeeModalButton.addEventListener('click', () => {
            _hideModal(wizardPromptAddEmployeesModal);
            if (addBtn) addBtn.click();
        });
    }
    if (wizardOkFinalButton) {
        wizardOkFinalButton.addEventListener('click', function() {
            this.disabled = true;
            fetch(`WizardStatusServlet`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: 'action=completeWizard'
            }).finally(() => { window.location.href = 'employees.jsp'; });
        });
    }
    if (wizardHelpButton) wizardHelpButton.addEventListener('click', () => { window.open('https://www.example.com/help', '_blank'); });
    
    toggleActionButtonsState();
    runWizard();
    autoSelectRowOnLoad();
    window.addEventListener('resize', equalizeDetailPanelHeights);
}
window.addEventListener('load', initializeEmployeesPage);