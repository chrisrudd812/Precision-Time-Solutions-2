// js/employees.js - vFINAL_WIZARD_FIX (Updated)
document.addEventListener('DOMContentLoaded', function() {
    console.log("[DEBUG] employees.js (vFINAL_WIZARD_FIX) loaded.");

    const _showModal = window.showModal;
    const _hideModal = window.hideModal;
    const _decode = window.decodeHtmlEntities;
    const appRoot = window.appRootPath || "";

    const addBtn = document.getElementById('addEmployeeButton');
    const editBtn = document.getElementById('editEmployeeButton');
    const deleteBtn = document.getElementById('deleteEmployeeButton');
    const tableBody = document.getElementById('employeesTable')?.querySelector('tbody');

    const addModal = document.getElementById('addEmployeeModal');
    const editModal = document.getElementById('editEmployeeModal');
    const addForm = document.getElementById('addEmployeeForm');
    const editForm = document.getElementById('editEmployeeForm');
    const notificationModal = document.getElementById('notificationModalGeneral');

    const wizardModal = document.getElementById('wizardGenericModal');
    const wizardTitle = document.getElementById('wizardGenericModalTitle');
    const wizardText1 = document.getElementById('wizardGenericModalText1');
    const wizardText2 = document.getElementById('wizardGenericModalText2');
    const wizardButtons = document.getElementById('wizardGenericModalButtonRow');

    let selectedRow = null;
    let wizardOpenedAddModal = false;

    const wizardStages = {
        "verify_admin_prompt": {
            title: "Final Step: Confirm Your Info",
            text1: "Let's quickly confirm your administrator account details.",
            text2: "Your user account has been created with the information you provided. Please verify it and assign yourself to a department, schedule, and accrual policy.",
            buttons: [{ id: "wizardConfirmAdmin", text: "Confirm My Info", class: "text-blue", action: "confirm_admin" }]
        },
        "add_employees_prompt": {
            title: "Setup: Add Employees",
            text1: "Administrator account confirmed!",
            text2: "Now you can add the rest of your employees to the system.",
            buttons: [
                { id: "wizardAddEmployee", text: "Add an Employee", class: "text-green", action: "open_add_employee" },
                { id: "wizardFinishSetup", text: "Finish Setup", class: "text-blue", action: "finish_setup" }
            ]
        },
        "after_add_employee_prompt": {
            title: "Setup: Employee Added",
            text1: "Great, the employee has been added.",
            text2: "You can add another employee or complete the setup process.",
            buttons: [
                { id: "wizardAddAnother", text: "Add Another Employee", class: "text-green", action: "open_add_employee" },
                { id: "wizardFinishAfterAdd", text: "Finish Setup", class: "text-blue", action: "finish_setup" }
            ]
        }
    };

    function updateWizardView(stageKey) {
        if (!wizardModal) return;
        const stage = wizardStages[stageKey];
        if (!stage) { _hideModal(wizardModal); return; }

        wizardTitle.textContent = stage.title;
        wizardText1.innerHTML = stage.text1;
        wizardText2.innerHTML = stage.text2;
        wizardButtons.innerHTML = '';
        stage.buttons.forEach(btn => {
            const button = document.createElement('button');
            button.id = btn.id;
            button.className = `glossy-button ${btn.class}`;
            button.innerHTML = btn.text;
            button.addEventListener('click', () => handleWizardAction(btn.action));
            wizardButtons.appendChild(button);
        });
        _showModal(wizardModal);
    }

    function handleWizardAction(action) {
        _hideModal(wizardModal);
        if (action === 'confirm_admin') {
            const adminRow = tableBody.querySelector('tr');
            if (adminRow) {
                selectRow(adminRow);
                populateAndShowEditModal(true); 
            }
        } else if (action === 'open_add_employee') {
            openAddModal();
        } else if (action === 'finish_setup') {
            endWizard();
        }
    }
    
    function endWizard() {
        fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({ 'action': 'endWizard' })
        })
        .then(res => res.json())
        .then(data => {
            if(data.success) {
                window.location.href = `${appRoot}/employees.jsp?message=Setup%20Complete!`;
            } else {
                showPageNotification(data.error || 'Could not finalize setup.', true, null, "Wizard Error");
            }
        })
        .catch(() => showPageNotification('Network error ending setup.', true, null, "Network Error"));
    }

    function populateDropdowns(form) {
        // This function populates dropdowns for Departments, Schedules, and Accruals
        // The data comes from the window object, which is populated in employees.jsp
        const deptSelect = form.querySelector('select[name="departmentsDropDown"]');
        const schedSelect = form.querySelector('select[name="schedulesDropDown"]');
        const accrualSelect = form.querySelector('select[name="accrualsDropDown"]');
        
        if(deptSelect) {
            deptSelect.innerHTML = '';
            window.departmentsData.forEach(d => deptSelect.add(new Option(_decode(d.name), d.name)));
        }

        if(schedSelect) {
            schedSelect.innerHTML = '';
            window.schedulesData.forEach(s => schedSelect.add(new Option(_decode(s.name), s.name)));
        }
        
        if(accrualSelect) {
            accrualSelect.innerHTML = '';
            window.accrualPoliciesData.forEach(p => accrualSelect.add(new Option(_decode(p.name), p.name)));
        }
    }

    function openAddModal() {
        addForm.reset();
        _showModal(addModal);
    }

    // **FIX START**: Replaced with the fully updated function
    function populateAndShowEditModal(isAdminVerification = false) {
        if (!selectedRow) return;
        editForm.reset();
        
        // This populates dropdowns like Department, Schedule, etc.
        populateDropdowns(editForm);
    
        const data = selectedRow.dataset;
        
        // --- Populate all fields from the selected row's data-* attributes ---
        editForm.querySelector('#editEmployeeId').value = data.eid;
        editForm.querySelector('#editFirstName').value = _decode(data.firstname);
        editForm.querySelector('#editLastName').value = _decode(data.lastname);
        editForm.querySelector('#editEmail').value = _decode(data.email);
        editForm.querySelector('#editAddress').value = _decode(data.address);
        editForm.querySelector('#editCity').value = _decode(data.city);
        editForm.querySelector('#editState').value = _decode(data.state);
        editForm.querySelector('#editZip').value = _decode(data.zip);
        editForm.querySelector('#editPhone').value = _decode(data.phone);
        editForm.querySelector('#editHireDate').value = data.isoDate;
        editForm.querySelector('#editPayRate').value = data.wage;
        
        // **FIX**: Added population for the new fields
        editForm.querySelector('#editWageType').value = _decode(data.wagetype);
        editForm.querySelector('#editWorkSchedule').value = _decode(data.workschedule);
        editForm.querySelector('#editPermissions').value = _decode(data.permissions);
        editForm.querySelector('#editSupervisor').value = _decode(data.supervisor);
    
        // Populate existing dropdowns
        editForm.querySelector('#editDepartment').value = _decode(data.dept);
        editForm.querySelector('#editSchedule').value = _decode(data.schedule);
        editForm.querySelector('#editAccrualPolicy').value = _decode(data.accrualpolicy);
        
        // --- Wizard-specific logic ---
        const emailInput = editForm.querySelector('#editEmail');
        emailInput.readOnly = false;
        emailInput.style.backgroundColor = '';
    
        if (isAdminVerification) {
            let hiddenInput = editForm.querySelector('input[name="admin_verify_step"]');
            if (!hiddenInput) {
                hiddenInput = document.createElement('input');
                hiddenInput.type = 'hidden';
                hiddenInput.name = 'admin_verify_step';
                editForm.appendChild(hiddenInput);
            }
            hiddenInput.value = 'true';
        }
    
        _showModal(editModal);
    }
    // **FIX END**

    function selectRow(row) {
        if (selectedRow) selectedRow.classList.remove('selected');
        if (row) {
            row.classList.add('selected');
            selectedRow = row;
            editBtn.disabled = false;
            deleteBtn.disabled = false;
        } else {
            selectedRow = null;
            editBtn.disabled = true;
            deleteBtn.disabled = true;
        }
    }

    if (addBtn) {
        addBtn.addEventListener('click', () => {
            if(window.inWizardMode_Page) {
                updateWizardView('add_employees_prompt');
            } else {
                openAddModal();
            }
        });
    }
    if (editBtn) editBtn.addEventListener('click', () => populateAndShowEditModal(false));
    if (tableBody) tableBody.addEventListener('click', e => selectRow(e.target.closest('tr')));
    
    document.querySelectorAll('.modal .close, .modal .cancel-btn').forEach(btn => {
        btn.addEventListener('click', e => _hideModal(e.target.closest('.modal')));
    });
    document.getElementById('okButtonNotificationModalGeneral')?.addEventListener('click', () => _hideModal(notificationModal));
    document.getElementById('closeWizardGenericModal')?.addEventListener('click', () => _hideModal(wizardModal));
    
    if (window.inWizardMode_Page) {
        const params = new URLSearchParams(window.location.search);
        let stage = window.currentWizardStep_Page;
        
        if (params.get('adminVerified') === 'true') {
            stage = 'add_employees_prompt';
        } else if (params.get('employeeAdded') === 'true') {
            stage = 'after_add_employee_prompt';
        }
        
        updateWizardView(stage || 'verify_admin_prompt');
    }
});