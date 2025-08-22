// js/employees.js

/**
 * FIX: Copied utility functions from commonUtils.js to resolve script loading order issues.
 * This ensures these functions are available when this script runs, without modifying shared files.
 */
function decodeHtmlEntities(encodedString) {
    if (encodedString === null || typeof encodedString === 'undefined' || String(encodedString).toLowerCase() === 'null') { return ''; }
    try {
        const textarea = document.createElement('textarea');
        textarea.innerHTML = String(encodedString); 
        return textarea.value;
    } catch (e) {
        return String(encodedString); 
    }
}

function showModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.style.display = 'flex';
        modalElement.classList.add('modal-visible');
    }
}

function showPageNotification(message, isError = false, modalInstance = null, titleText = "Notification") { 
    const modalToUse = modalInstance || document.getElementById("notificationModalGeneral");
    const msgElem = modalToUse ? modalToUse.querySelector('#notificationModalGeneralMessage') : null;
    const modalTitleElem = modalToUse ? modalToUse.querySelector('#notificationModalGeneralTitle') : null;

    if (!modalToUse || !msgElem) {
        alert((isError ? "Error: " : "Notification: ") + message);
        return;
    }
    if(modalTitleElem) modalTitleElem.textContent = titleText;
    msgElem.innerHTML = message;
    showModal(modalToUse); 
}


document.addEventListener('DOMContentLoaded', function() {

    // --- Global variables and references to DOM elements ---
    const _showModal = window.showModal;
    const _hideModal = window.hideModal;
    const _decode = decodeHtmlEntities;
    const appRoot = window.appRootPath || "";

    const addBtn = document.getElementById('addEmployeeButton');
    const editBtn = document.getElementById('editEmployeeButton');
    const deleteBtn = document.getElementById('deleteEmployeeButton');
    const employeesTable = document.getElementById('employeesTable');
    const tableBody = employeesTable?.querySelector('tbody');

    const addModal = document.getElementById('addEmployeeModal');
    const editModal = document.getElementById('editEmployeeModal');
    const addForm = document.getElementById('addEmployeeForm');
    const editForm = document.getElementById('editEmployeeForm');
    const deactivateModal = document.getElementById('deactivateConfirmModal');
    const reactivateModal = document.getElementById('reactivateEmployeeModal');
    const notificationModal = document.getElementById('notificationModalGeneral');
    
    const okBtnGeneralNotify = document.getElementById('okButtonNotificationModalGeneral');

    const detailsPanel = document.getElementById('employeeDetailsSection');
    const wizardModal = document.getElementById('wizardGenericModal');
    const wizardTitle = document.getElementById('wizardGenericModalTitle');
    const wizardText1 = document.getElementById('wizardGenericModalText1');
    const wizardText2 = document.getElementById('wizardGenericModalText2');
    const wizardButtons = document.getElementById('wizardGenericModalButtonRow');

    let selectedRow = null;
    let selectedEmployeeData = null;

    // --- WIZARD LOGIC ---
    const wizardStages = {
        "verify_admin_prompt": {
            title: "Final Step: Confirm Your Info",
            text1: "Let's quickly confirm your administrator account details.",
            text2: "Please verify your information and assign yourself to the correct policies.",
            buttons: [{ id: "wizardConfirmAdmin", text: "Confirm My Info", class: "text-blue", action: "confirm_admin" }]
        },
        "prompt_add_employees": {
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
        },
        "setup_complete": {
            title: "Congratulations! Setup Complete",
            text1: "You have successfully configured your company and added your employees.",
            text2: "You can now manage your employees or visit our help center to learn more.",
            buttons: [
                { id: "wizardGoToEmployees", text: "Go to Employee Management", class: "text-blue", action: "go_to_employees" },
                { id: "wizardGoToHelp", text: "Visit Help Center", class: "text-grey", action: "go_to_help" }
            ]
        }
    };

    function updateWizardView(stageKey) {
        if (!wizardModal || !wizardStages[stageKey]) return;
        const stage = wizardStages[stageKey];
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
        showModal(wizardModal);
    }

    async function handleWizardAction(action) {
        _hideModal(wizardModal);
        if (action === 'confirm_admin') {
            const adminEid = window.wizardAdminEid;
            if (!adminEid) {
                alert("Error: Admin EID not found for wizard flow.");
                return;
            }
            try {
                const response = await fetch(`${appRoot}/EmployeeInfoServlet?action=getEmployeeDetails&eid=${adminEid}`);
                const data = await response.json();
                if (data.success) {
                    populateAndShowEditModal(data.employee, true);
                } else {
                    alert('Error fetching admin details: ' + data.error);
                }
            } catch (err) {
                console.error("Failed to fetch admin details:", err);
                alert("A network error occurred while fetching your details.");
            }
        } else if (action === 'open_add_employee') {
            openAddModal();
        } else if (action === 'finish_setup') {
            updateWizardView('setup_complete');
        } else if (action === 'go_to_employees') {
            endWizard();
        } else if (action === 'go_to_help') {
            window.location.href = `${appRoot}/help.jsp`;
        }
    }

    function endWizard() {
        fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ 'action': 'endWizard' })
        })
        .then(() => window.location.href = `${appRoot}/employees.jsp`);
    }
    
    function formatDateForInput(dateString) {
        if (!dateString) return '';
        const dateObj = new Date(dateString);
        if (isNaN(dateObj.getTime())) return '';
        dateObj.setMinutes(dateObj.getMinutes() + dateObj.getTimezoneOffset());
        return dateObj.toISOString().slice(0, 10);
    }

    function formatDateForDisplay(dateString) {
        if (!dateString) return '--';
        const dateObj = new Date(dateString);
        if (isNaN(dateObj.getTime())) return '--';
        dateObj.setMinutes(dateObj.getMinutes() + dateObj.getTimezoneOffset());
        return dateObj.toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' });
    }

    function populateDropdown(selectElement, optionsArray, addBlankOption = false) {
        if (!selectElement) return;
        const currentValue = selectElement.value;
        selectElement.innerHTML = '';
        if (addBlankOption) {
            selectElement.add(new Option('Select...', ''));
        }
        optionsArray.forEach(item => {
            const text = typeof item === 'object' ? _decode(item.name) : item;
            const value = typeof item === 'object' ? _decode(item.name) : item;
            selectElement.add(new Option(text, value));
        });
        selectElement.value = currentValue;
    }
    
    function populateAllDropdowns(form) {
        const departments = [{name: "None"}, ...window.departmentsData.filter(d => d.name !== "None")];
        const schedules = [{name: "Open"}, ...window.schedulesData.filter(s => s.name !== "Open")];
        const accruals = [{name: "None"}, ...window.accrualPoliciesData.filter(p => p.name !== "None")];
        populateDropdown(form.querySelector('select[name="departmentsDropDown"]'), departments);
        populateDropdown(form.querySelector('select[name="schedulesDropDown"]'), schedules);
        populateDropdown(form.querySelector('select[name="accrualsDropDown"]'), accruals);
        
        const states = ["","AL","AK","AZ","AR","CA","CO","CT","DE","DC","FL","GA","HI","ID","IL","IN","IA","KS","KY","LA","ME","MD","MA","MI","MN","MS","MO","MT","NE","NV","NH","NJ","NM","NY","NC","ND","OH","OK","OR","PA","RI","SC","SD","TN","TX","UT","VT","VA","WA","WV","WI","WY"];
        populateDropdown(form.querySelector('select[name="state"]'), states, false);
        populateDropdown(form.querySelector('select[name="permissionsDropDown"]'), ["User", "Administrator"]);
        populateDropdown(form.querySelector('select[name="workScheduleDropDown"]'), ["Full Time", "Part Time", "Seasonal", "Temporary", "Contractor"]);
        populateDropdown(form.querySelector('select[name="wageTypeDropDown"]'), ["Hourly", "Salary"]);
    }

    function openAddModal(prefillData = {}) {
        addForm.reset();
        clearValidation(addForm);
        populateAllDropdowns(addForm);
        
        const today = new Date();
        const yyyy = today.getFullYear();
        const mm = String(today.getMonth() + 1).padStart(2, '0');
        const dd = String(today.getDate()).padStart(2, '0');
        addForm.querySelector('#addHireDate').value = `${yyyy}-${mm}-${dd}`;
        
        addForm.querySelector('#addWorkSchedule').value = 'Full Time';
        addForm.querySelector('#addWageType').value = 'Hourly';
        addForm.querySelector('#addDepartment').value = 'None';
        addForm.querySelector('#addSchedule').value = 'Open';
        addForm.querySelector('#addAccrualPolicy').value = 'None';
        addForm.querySelector('#addPermissions').value = 'User';

        for (const key in prefillData) {
            const field = addForm.querySelector(`[name="${key}"]`);
            if (field) {
                field.value = prefillData[key];
            }
        }

        showModal(addModal);
        
        setTimeout(() => {
            addForm.querySelector('#addFirstName').focus();
        }, 150);
    }

    function populateAndShowEditModal(data, isAdminVerification = false) {
        if (!data) return;
        
        editForm.reset();
        clearValidation(editForm);
        populateAllDropdowns(editForm);
        
        editForm.querySelector('#editEmployeeId').value = data.eid || '';
        editForm.querySelector('#editFirstName').value = _decode(data.firstname);
        editForm.querySelector('#editLastName').value = _decode(data.lastname);
        editForm.querySelector('#editEmail').value = _decode(data.email);
        editForm.querySelector('#editAddress').value = _decode(data.address);
        editForm.querySelector('#editCity').value = _decode(data.city);
        editForm.querySelector('#editState').value = _decode(data.state);
        editForm.querySelector('#editZip').value = _decode(data.zip);
        editForm.querySelector('#editPhone').value = _decode(data.phone);
        editForm.querySelector('#editHireDate').value = formatDateForInput(data.hiredate);
        editForm.querySelector('#editPermissions').value = _decode(data.permissions);
        editForm.querySelector('#editSupervisor').value = _decode(data.supervisor);
        
        editForm.querySelector('#editDepartment').value = _decode(data.dept || 'None');
        
        editForm.querySelector('#editPayRate').value = (data.wage && parseFloat(data.wage) > 0) ? data.wage : '';
        
        editForm.querySelector('#editSchedule').value = data.schedule || 'Open';
        editForm.querySelector('#editWorkSchedule').value = data.worksched || 'Full Time';
        editForm.querySelector('#editWageType').value = data.wagetype || 'Hourly';
        editForm.querySelector('#editAccrualPolicy').value = data.accrualpolicy || 'None';

        let adminVerifyStepInput = editForm.querySelector('input[name="admin_verify_step"]');
        if (!adminVerifyStepInput) {
            adminVerifyStepInput = document.createElement('input');
            adminVerifyStepInput.type = 'hidden';
            adminVerifyStepInput.name = 'admin_verify_step';
            adminVerifyStepInput.id = 'adminVerifyStep';
            editForm.appendChild(adminVerifyStepInput);
        }
        adminVerifyStepInput.value = isAdminVerification ? 'true' : 'false';

        showModal(editModal);

        if (isAdminVerification) {
            setTimeout(() => {
                editForm.querySelector('#editAddress').focus();
            }, 150);
        }
    }

    function selectRow(row) {
        if (selectedRow) selectedRow.classList.remove('selected');
        
        if (row) {
            row.classList.add('selected');
            selectedRow = row;
            editBtn.disabled = false;
            deleteBtn.disabled = false;

            const dataAttr = row.dataset;
            selectedEmployeeData = { eid: dataAttr.eid || '' };
            
            if (detailsPanel) {
                const na = '--';
                const cells = row.cells;
                detailsPanel.querySelector('#detailEID').textContent = _decode(cells[0]?.textContent.trim() || na);
                detailsPanel.querySelector('#detailFirstName').textContent = _decode(dataAttr.firstname || na);
                detailsPanel.querySelector('#detailLastName').textContent = _decode(dataAttr.lastname || na);
                detailsPanel.querySelector('#detailAddress').textContent = _decode(dataAttr.address || na);
                detailsPanel.querySelector('#detailCity').textContent = _decode(dataAttr.city || na);
                detailsPanel.querySelector('#detailState').textContent = _decode(dataAttr.state || na);
                detailsPanel.querySelector('#detailZip').textContent = _decode(dataAttr.zip || na);
                detailsPanel.querySelector('#detailPhone').textContent = _decode(dataAttr.phone || na);
                detailsPanel.querySelector('#detailEmail').textContent = _decode(dataAttr.email || na);
                detailsPanel.querySelector('#detailDept').textContent = _decode(dataAttr.dept || na);
                detailsPanel.querySelector('#detailSchedule').textContent = _decode(dataAttr.schedule || na);
                detailsPanel.querySelector('#detailSupervisor').textContent = _decode(dataAttr.supervisor || na);
                detailsPanel.querySelector('#detailPermissions').textContent = _decode(dataAttr.permissions || na);
                detailsPanel.querySelector('#detailHireDate').textContent = formatDateForDisplay(dataAttr.isoDate);
                detailsPanel.querySelector('#detailWorkSched').textContent = _decode(dataAttr.workschedule || na);
                detailsPanel.querySelector('#detailWageType').textContent = _decode(dataAttr.wagetype || na);
                detailsPanel.querySelector('#detailWage').textContent = dataAttr.wage && parseFloat(dataAttr.wage) > 0 ? `$${parseFloat(dataAttr.wage).toFixed(2)}` : na;
                detailsPanel.querySelector('#detailAccrualPolicy').textContent = _decode(dataAttr.accrualpolicy || na);
                detailsPanel.querySelector('#detailVacHours').textContent = _decode(dataAttr.vachours || na);
                detailsPanel.querySelector('#detailSickHours').textContent = _decode(dataAttr.sickhours || na);
                detailsPanel.querySelector('#detailPersHours').textContent = _decode(dataAttr.pershours || na);
                detailsPanel.querySelector('#resetFormEid').value = selectedEmployeeData.eid;
                detailsPanel.querySelector('#btnResetPassword').disabled = false;
                detailsPanel.style.display = 'block';
            }
        } else {
            selectedRow = null;
            selectedEmployeeData = null;
            editBtn.disabled = true;
            deleteBtn.disabled = true;
            if (detailsPanel) detailsPanel.style.display = 'none';
        }
    }

    function handleReactivation(eid) {
        const form = new FormData();
        form.append('action', 'reactivateEmployee');
        form.append('eid', eid);
        _hideModal(reactivateModal);
        fetch(`${appRoot}/AddEditAndDeleteEmployeesServlet`, { method: 'POST', body: new URLSearchParams(form) })
            .then(response => { if (response.redirected) window.location.href = response.url; else window.location.reload(); });
    }

    deleteBtn?.addEventListener('click', () => {
        if (!selectedEmployeeData) return;
        const employeeName = `${_decode(selectedRow.dataset.firstname)} ${_decode(selectedRow.dataset.lastname)}`;
        deactivateModal.querySelector('#deactivateEmployeeName').textContent = employeeName;
        showModal(deactivateModal);
        const confirmBtn = deactivateModal.querySelector('#confirmDeactivateBtn');
        const cancelBtn = deactivateModal.querySelector('.cancel-btn');
        const confirmHandler = () => {
            const form = new FormData();
            form.append('action', 'deactivateEmployee');
            form.append('eid', selectedEmployeeData.eid);
            fetch(`${appRoot}/AddEditAndDeleteEmployeesServlet`, { method: 'POST', body: new URLSearchParams(form) })
            .then(response => { if(response.redirected) { window.location.href = response.url; } else { window.location.reload(); } });
            _hideModal(deactivateModal);
        };
        confirmBtn.onclick = confirmHandler;
        cancelBtn.onclick = () => _hideModal(deactivateModal);
    });

    function validateField(field) {
        const isValid = field.checkValidity();
        if (isValid) {
            field.classList.add('is-valid');
            field.classList.remove('is-invalid');
        } else {
            field.classList.add('is-invalid');
            field.classList.remove('is-valid');
        }
        return isValid;
    }
    
    function validateForm(form) {
        let firstInvalidField = null;
        let isFormValid = true;
        form.querySelectorAll('[required], [pattern]').forEach(field => {
            if (!validateField(field)) {
                if (isFormValid) { 
                    firstInvalidField = field;
                }
                isFormValid = false;
            }
        });
        if (firstInvalidField) {
            firstInvalidField.focus();
        }
        return isFormValid;
    }
    
    function clearValidation(form) {
        form.querySelectorAll('.is-valid, .is-invalid').forEach(field => {
            field.classList.remove('is-valid', 'is-invalid');
        });
    }

    [addForm, editForm].forEach(form => {
        if(form) {
            form.setAttribute('novalidate', 'true'); 
            form.addEventListener('submit', event => {
                if (!validateForm(form)) {
                    event.preventDefault(); 
                }
            });
            form.querySelectorAll('[required], [pattern]').forEach(field => {
                field.addEventListener('input', () => validateField(field));
                if (field.tagName === 'SELECT') {
                    field.addEventListener('change', () => validateField(field));
                }
            });
        }
    });
    
    editForm?.addEventListener('submit', function (event) {
        event.preventDefault();
        if (!validateForm(this)) return;

        const submitButton = this.querySelector('button[type="submit"]');
        const originalButtonHtml = submitButton.innerHTML;
        submitButton.disabled = true;
        submitButton.innerHTML = `<i class="fas fa-spinner fa-spin"></i> Saving...`;

        const formData = new FormData(this);
        const isAdminVerify = formData.get('admin_verify_step') === 'true';
        const formActionUrl = this.getAttribute('action');
        
        console.log("--- DEBUGGING EMPLOYEE SAVE ---");
        console.log("Submitting to URL:", formActionUrl);
        console.log("Is Admin Verification:", isAdminVerify);
        for (let [key, value] of formData.entries()) {
            console.log(`Form Data -> ${key}: ${value}`);
        }
        console.log("---------------------------------");
        
        fetch(formActionUrl, {
            method: 'POST',
            body: new URLSearchParams(formData)
        })
        .then(response => {
            if (!response.ok) {
                console.error("Server responded with an error status:", response.status, response.statusText);
                throw new Error(`HTTP error! Status: ${response.status}`);
            }
        
            if (isAdminVerify) {
                 return response.json();
            }
            if (response.redirected) {
                window.location.href = response.url;
                return null; 
            }
            return response.text();
        })
        .then(data => {
            if (!data) return; 

            if (typeof data === 'object' && data.success) {
                _hideModal(editModal);
                if (isAdminVerify) {
                    const adminRow = tableBody?.querySelector(`tr[data-eid="${formData.get('eid')}"]`);
                    if(adminRow) selectRow(adminRow);
                    updateWizardView('prompt_add_employees');
                }
            } else if (typeof data === 'object' && !data.success) {
                 alert('Error: ' + data.error);
            }
        })
        .catch(error => {
            console.error('Submit Error:', error);
            alert('An error occurred during submission. Check the console for details.');
        })
        .finally(() => {
            submitButton.disabled = false;
            submitButton.innerHTML = originalButtonHtml;
        });
    });
    
    function formatPhoneNumber(event) {
        const input = event.target;
        const digits = input.value.replace(/\D/g, '');
        let formatted = '';
        if (digits.length > 0) formatted = '(' + digits.substring(0, 3);
        if (digits.length >= 4) formatted += ') ' + digits.substring(3, 6);
        if (digits.length >= 7) formatted += '-' + digits.substring(6, 10);
        input.value = formatted;
        validateField(input);
    }

    const addPhoneInput = document.getElementById('addPhone');
    const editPhoneInput = document.getElementById('editPhone');
    if(addPhoneInput) addPhoneInput.addEventListener('input', formatPhoneNumber);
    if(editPhoneInput) editPhoneInput.addEventListener('input', formatPhoneNumber);

    function makeModalDraggable(modal) {
        const header = modal.querySelector('h2');
        const content = modal.querySelector('.modal-content');
        if (!header || !content) return;
        header.style.cursor = 'move';
        let isDragging = false, initialMouseX, initialMouseY, initialContentX, initialContentY;
        header.addEventListener('mousedown', (e) => {
            if (e.target.closest('button, input, select, textarea, .close')) return;
            isDragging = true;
            initialMouseX = e.clientX;
            initialMouseY = e.clientY;
            const rect = content.getBoundingClientRect();
            initialContentX = rect.left;
            initialContentY = rect.top;
            document.body.classList.add('is-dragging');
        });
        document.addEventListener('mousemove', (e) => {
            if (!isDragging) return;
            e.preventDefault();
            const dx = e.clientX - initialMouseX;
            const dy = e.clientY - initialMouseY;
            content.style.position = 'absolute';
            content.style.left = `${initialContentX + dx}px`;
            content.style.top = `${initialContentY + dy}px`;
        });
        document.addEventListener('mouseup', () => {
            if (isDragging) {
                isDragging = false;
                document.body.classList.remove('is-dragging');
            }
        });
    }
    const style = document.createElement('style');
    style.textContent = '.is-dragging { user-select: none; }';
    document.head.appendChild(style);
    document.querySelectorAll('.modal').forEach(makeModalDraggable);

    // --- EVENT LISTENERS ---
    addBtn?.addEventListener('click', () => openAddModal());
    editBtn?.addEventListener('click', () => {
        if (!selectedEmployeeData || !selectedEmployeeData.eid) return;
        fetch(`${appRoot}/EmployeeInfoServlet?action=getEmployeeDetails&eid=${selectedEmployeeData.eid}`)
            .then(res => res.json())
            .then(data => {
                if(data.success) {
                    populateAndShowEditModal(data.employee, false);
                } else {
                    alert('Could not load employee details.');
                }
            });
    });
    tableBody?.addEventListener('click', e => selectRow(e.target.closest('tr')));
    document.querySelectorAll('.modal .close, .modal .cancel-btn').forEach(btn => {
        btn.addEventListener('click', e => {
            const modal = e.target.closest('.modal');
            const form = modal.querySelector('form');
            if(form) clearValidation(form);
            _hideModal(modal);
        });
    });
    
    if (okBtnGeneralNotify) {
        okBtnGeneralNotify.addEventListener('click', () => _hideModal(notificationModal));
    }


    // --- Page Load Logic ---
    const urlParams = new URLSearchParams(window.location.search);
    const reactivatePrompt = urlParams.get('reactivatePrompt');

    if (window.inSetupWizardMode_Page) {
        let stage = urlParams.get('step') || window.currentWizardStep_Page;
        updateWizardView(stage || 'verify_admin_prompt');
    } else {
        const reopenModal = urlParams.get('reopenModal');
        if (reopenModal) {
            const prefillData = {};
            urlParams.forEach((value, key) => { prefillData[key] = value; });
            if (reopenModal === 'add') openAddModal(prefillData);
            window.history.replaceState({}, document.title, window.location.pathname);
        } else {
            const successMsg = urlParams.get('message');
            const errorMsg = urlParams.get('error');
            
            // FIX: Changed window.showPageNotification to the local showPageNotification function
            if (errorMsg) {
                showPageNotification(errorMsg, true, notificationModal, 'Error');
            }
            if (successMsg) {
                showPageNotification(successMsg, false, notificationModal, 'Success');
                if (okBtnGeneralNotify) {
                    setTimeout(() => okBtnGeneralNotify.focus(), 150); 
                }
            }
            if (reactivatePrompt === 'true') {
                const eid = urlParams.get('eid');
                const email = urlParams.get('email');
                reactivateModal.querySelector('#reactivateEmail').textContent = email;
                showModal(reactivateModal);
                reactivateModal.querySelector('#confirmReactivateBtn').onclick = () => handleReactivation(eid);
                reactivateModal.querySelector('.cancel-btn').onclick = () => _hideModal(reactivateModal);
            }
        }
        
        const selectedEid = urlParams.get('eid');
        if (selectedEid && !reactivatePrompt) {
            const rowToSelect = tableBody?.querySelector(`tr[data-eid="${selectedEid}"]`);
            if (rowToSelect) {
                selectRow(rowToSelect);
                rowToSelect.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        } else if (tableBody?.rows.length > 0) {
            selectRow(tableBody.rows[0]);
        }
    }
    
});
