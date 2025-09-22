// js/employees.js

document.addEventListener('DOMContentLoaded', function() {

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
    const payrollModal = document.getElementById('payrollModal');
    
    const detailsPanel = document.getElementById('employeeDetailsSection');
    const wizardModal = document.getElementById('wizardGenericModal');
    const wizardTitle = document.getElementById('wizardGenericModalTitle');
    const wizardText1 = document.getElementById('wizardGenericModalText1');
    const wizardText2 = document.getElementById('wizardGenericModalText2');
    const wizardButtons = document.getElementById('wizardGenericModalButtonRow');

    let selectedRow = null;
    let selectedEmployeeData = null;
    
    function showPageNotification(message, isError, callback = null, title = null, options = {}) {
        const type = isError ? 'error' : 'success';
        if (window.showPageNotification) {
            window.showPageNotification(message, type, callback, title, options);
        } else {
            alert((isError ? "Error: " : "Success: ") + message);
        }
    }

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
                { id: "wizardGoToEmployees", text: "Manage Employees", class: "text-blue", action: "go_to_employees" },
                { id: "wizardSendWelcome", text: "Email PIN & Welcome Info", class: "text-green", action: "send_welcome" },
                { id: "wizardGoToHelp", text: "Visit Help Center", class: "text-grey", action: "go_to_help" }
            ]
        }
    };

    function updateWizardView(stageKey) {
        console.log("DEBUG: updateWizardView called with stage:", stageKey);
        if (!wizardModal || !wizardStages[stageKey] || !wizardTitle) return;
        const stage = wizardStages[stageKey];
        
        const titleSpan = wizardTitle.querySelector('span');
        if(titleSpan) titleSpan.textContent = stage.title;

        wizardText1.innerHTML = stage.text1;
        wizardText2.innerHTML = stage.text2;
        wizardButtons.innerHTML = '';
        stage.buttons.forEach(btn => {
            const button = document.createElement('button');
            button.id = btn.id;
            button.type = 'button';
            button.className = `glossy-button ${btn.class}`;
            button.innerHTML = btn.text;
            button.addEventListener('click', () => handleWizardAction(btn.action));
            wizardButtons.appendChild(button);
        });
        _showModal(wizardModal);
    }
    
    function endWizard(redirectUrl) {
        fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ 'action': 'endWizard' })
        })
        .then(async response => {
            if (response.ok) {
                if (redirectUrl) {
                    window.location.href = redirectUrl;
                } else {
                    window.location.href = `${appRoot}/employees.jsp`;
                }
            } else {
                alert('Could not finalize setup. The page will now reload.');
                window.location.reload();
            }
        })
        .catch(error => {
            alert('A network error occurred. The page will now reload.');
            window.location.reload();
        });
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
                alert("A network error occurred while fetching your details.");
            }
        } else if (action === 'open_add_employee') {
            openAddModal();
        } else if (action === 'finish_setup') {
            updateWizardView('setup_complete');
        } else if (action === 'go_to_employees') {
            endWizard(); 
        } else if (action === 'go_to_help') {
            endWizard(`${appRoot}/help.jsp`);
        } else if (action === 'send_welcome') {
            // Create and show processing overlay
            const processingOverlay = document.createElement('div');
            processingOverlay.id = 'welcomeEmailProcessingOverlay';
            processingOverlay.innerHTML = `
                <div class="processing-content">
                    <div class="processing-spinner">
                        <i class="fas fa-envelope fa-3x"></i>
                        <i class="fas fa-spinner fa-spin fa-2x"></i>
                    </div>
                    <h3>Processing Welcome Emails</h3>
                    <p>Sending welcome messages and PIN information to all employees...</p>
                    <p class="processing-note">This may take a moment depending on the number of employees.</p>
                </div>
            `;
            processingOverlay.style.cssText = `
                position: fixed;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: rgba(0, 0, 0, 0.8);
                display: flex;
                justify-content: center;
                align-items: center;
                z-index: 10000;
                color: white;
                font-family: Arial, sans-serif;
            `;
            
            const style = document.createElement('style');
            style.textContent = `
                .processing-content {
                    text-align: center;
                    background: rgba(255, 255, 255, 0.1);
                    padding: 40px;
                    border-radius: 10px;
                    backdrop-filter: blur(10px);
                    border: 1px solid rgba(255, 255, 255, 0.2);
                }
                .processing-spinner {
                    position: relative;
                    margin-bottom: 20px;
                }
                .processing-spinner .fa-envelope {
                    color: #4CAF50;
                    margin-right: 15px;
                }
                .processing-spinner .fa-spinner {
                    color: #2196F3;
                }
                .processing-content h3 {
                    margin: 0 0 15px 0;
                    font-size: 24px;
                    color: white;
                }
                .processing-content p {
                    margin: 10px 0;
                    font-size: 16px;
                    color: #e0e0e0;
                }
                .processing-note {
                    font-size: 14px !important;
                    font-style: italic;
                    color: #bbb !important;
                }
            `;
            document.head.appendChild(style);
            document.body.appendChild(processingOverlay);

            const companyName = window.companyNameSignup || 'Your Company';
            const companyId = window.companyIdentifier || '';
            const loginLink = `${window.location.origin}${appRoot}/login.jsp?companyId=${encodeURIComponent(companyId)}&focus=email`;
            const adminHelpLink = `${window.location.origin}${appRoot}/help.jsp`;
            const userHelpLink = `${window.location.origin}${appRoot}/help_user.jsp`;
            
            const subject = `Welcome to ${companyName}!`;
            const baseBody = `Welcome to your new time and attendance system!\n\n` +
                            `To log in, please use the following link. We recommend bookmarking it for easy access:\n` +
                            `${loginLink}\n\n` +
                            `Your login details are:\n` +
                            `• Company ID: ${companyId}\n` +
                            `• Username: Your Email Address\n` +
                            `• Temporary PIN: 1234\n\n` +
                            `You will be required to change this PIN on your first login for security.\n\n`;
            
            const adminContent = `As an administrator, you have full access to manage employees, schedules, and payroll.\n\n` +
                               `For help getting started, visit our administrator help center:\n${adminHelpLink}`;
            
            const userContent = `You can punch in and out using our easy-to-use time clock.\n\n` +
                              `For help using the time clock, visit our user guide:\n${userHelpLink}`;
            
            // Send role-based welcome emails
            fetch(`${appRoot}/MessagingServlet`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: new URLSearchParams({
                    action: 'sendWelcomeEmails',
                    subject: subject,
                    baseBody: baseBody,
                    adminContent: adminContent,
                    userContent: userContent
                })
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showPageNotification(data.message, false, () => endWizard(), 'Success');
                } else {
                    showPageNotification(data.message, true, null, 'Error');
                }
            })
            .catch(error => {
                alert('Network error while sending welcome emails.');
            })
            .finally(() => {
                // Remove processing overlay
                const overlay = document.getElementById('welcomeEmailProcessingOverlay');
                if (overlay) {
                    overlay.remove();
                }
            });
        }
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
        populateDropdown(form.querySelector('select[name="state"]'), states, true);
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
        _showModal(addModal);
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

        _showModal(editModal);

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
                const setDetailContent = (id, label, value) => {
                    const element = detailsPanel.querySelector(`#${id}`);
                    if (element) {
                        element.textContent = value;
                    }
                };
                
                setDetailContent('detailEID', 'Employee ID', _decode(cells[0]?.textContent.trim() || na));
                
                // Consolidated name field
                const fullName = `${_decode(dataAttr.firstname || '')} ${_decode(dataAttr.lastname || '')}`;
                setDetailContent('detailFullName', 'Full Name', fullName.trim() || na);
                
                // Consolidated address field
                const addressParts = [_decode(dataAttr.address), _decode(dataAttr.city), _decode(dataAttr.state), _decode(dataAttr.zip)].filter(part => part && part !== na);
                setDetailContent('detailFullAddress', 'Address', addressParts.length > 0 ? addressParts.join(', ') : na);
                
                setDetailContent('detailPhone', 'Phone', _decode(dataAttr.phone || na));
                setDetailContent('detailEmail', 'Email', _decode(dataAttr.email || na));
                setDetailContent('detailDept', 'Department', _decode(dataAttr.dept || na));
                setDetailContent('detailSchedule', 'Schedule', _decode(dataAttr.schedule || na));
                setDetailContent('detailSupervisor', 'Supervisor', _decode(dataAttr.supervisor || na));
                setDetailContent('detailPermissions', 'Permissions', _decode(dataAttr.permissions || na));
                setDetailContent('detailHireDate', 'Hire Date', formatDateForDisplay(dataAttr.isoDate));
                setDetailContent('detailWorkSched', 'Work Schedule', _decode(dataAttr.workschedule || na));
                
                // Consolidated wage field
                const wageType = _decode(dataAttr.wagetype || na);
                const wage = dataAttr.wage && parseFloat(dataAttr.wage) > 0 ? `$${parseFloat(dataAttr.wage).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2})}` : na;
                setDetailContent('detailWageInfo', 'Wage Info', wage !== na ? `${wage} (${wageType})` : na);
                
                setDetailContent('detailAccrualPolicy', 'Accrual Policy', _decode(dataAttr.accrualpolicy || na));
                setDetailContent('detailVacHours', 'Vacation Hours', _decode(dataAttr.vachours || na));
                setDetailContent('detailSickHours', 'Sick Hours', _decode(dataAttr.sickhours || na));
                setDetailContent('detailPersHours', 'Personal Hours', _decode(dataAttr.pershours || na));
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
        
        fetch(`${appRoot}/AddEditAndDeleteEmployeesServlet`, { 
            method: 'POST', 
            body: new URLSearchParams(form) 
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                const redirect = () => {
                    window.location.href = `${appRoot}/employees.jsp?eid=${data.eid}`;
                };
                showPageNotification(data.message || 'Employee reactivated!', false, redirect, 'Success');
            } else if (data.error === 'user_limit_exceeded') {
                showPageNotification(data.message, true, null, 'User Limit Reached', {
                    customButtonText: 'Upgrade Plan',
                    customButtonAction: () => { window.location.href = `${appRoot}/account.jsp?focus=subscription`; }
                });
            } else {
                showPageNotification(data.error || 'An unknown error occurred.', true, null, 'Error');
            }
        })
        .catch(error => {
            showPageNotification('A network error occurred during reactivation.', true, null, 'Error');
        });
    }

    deleteBtn?.addEventListener('click', () => {
        if (!selectedEmployeeData) return;
        const employeeName = `${_decode(selectedRow.dataset.firstname)} ${_decode(selectedRow.dataset.lastname)}`;
        deactivateModal.querySelector('#deactivateEmployeeName').textContent = employeeName;
        const reasonSelect = deactivateModal.querySelector('#deactivationReasonSelect');
        if (reasonSelect) {
            reasonSelect.value = ''; 
            reasonSelect.classList.remove('is-invalid');
        }
        showModal(deactivateModal);
        const confirmBtn = deactivateModal.querySelector('#confirmDeactivateBtn');
        const cancelBtn = deactivateModal.querySelector('.cancel-btn');
        const confirmHandler = () => {
            const reason = reasonSelect ? reasonSelect.value : '';
            if (!reason) {
                if(reasonSelect) reasonSelect.classList.add('is-invalid');
                showPageNotification("Please select a reason for deactivation.", true);
                return;
            }
            const form = new FormData();
            form.append('action', 'deactivateEmployee');
            form.append('eid', selectedEmployeeData.eid);
            form.append('deactivationReason', reason); 
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
                const submitButton = form.querySelector('button[type="submit"]');
                if (!validateForm(form)) {
                    event.preventDefault(); 
                } else if (form.id === 'editEmployeeForm') {
                     // The JSON submission for edit form is handled separately
                } else {
                    // Remove commas from pay rate before submission for add form
                    const payRateInput = form.querySelector('#addPayRate');
                    if (payRateInput && payRateInput.value) {
                        payRateInput.value = removeCommasFromPayRate(payRateInput);
                    }
                    submitButton.disabled = true;
                    submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
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

        // Remove commas from pay rate before submission
        const payRateInput = this.querySelector('#editPayRate');
        if (payRateInput && payRateInput.value) {
            payRateInput.value = removeCommasFromPayRate(payRateInput);
        }
        
        const formData = new FormData(this);
        const isAdminVerify = formData.get('admin_verify_step') === 'true';
        const formActionUrl = this.getAttribute('action');
        
        fetch(formActionUrl, {
            method: 'POST',
            body: new URLSearchParams(formData)
        })
        .then(response => {
            if (!response.ok) { throw new Error(`HTTP error! Status: ${response.status}`); }
            if (isAdminVerify) { return response.json(); }
            if (response.redirected) { window.location.href = response.url; return null; }
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

    function formatPayRate(event) {
        const input = event.target;
        let value = input.value.replace(/,/g, '');
        if (value && !isNaN(value) && value !== '') {
            const num = parseFloat(value);
            if (num > 0) {
                input.value = num.toLocaleString('en-US');
            }
        }
    }

    function removeCommasFromPayRate(input) {
        return input.value.replace(/,/g, '');
    }

    const addPhoneInput = document.getElementById('addPhone');
    const editPhoneInput = document.getElementById('editPhone');
    const addPayRateInput = document.getElementById('addPayRate');
    const editPayRateInput = document.getElementById('editPayRate');
    
    if(addPhoneInput) addPhoneInput.addEventListener('input', formatPhoneNumber);
    if(editPhoneInput) editPhoneInput.addEventListener('input', formatPhoneNumber);
    if(addPayRateInput) addPayRateInput.addEventListener('blur', formatPayRate);
    if(editPayRateInput) editPayRateInput.addEventListener('blur', formatPayRate);

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
            
            // If in setup wizard mode and canceling from add employee modal, return to appropriate wizard prompt
            if (window.inSetupWizardMode_Page && modal.id === 'addEmployeeModal') {
                setTimeout(() => {
                    // If employees exist (meaning we've added at least one), show after_add_employee_prompt
                    // Otherwise show the initial prompt_add_employees
                    const hasEmployees = tableBody && tableBody.rows.length > 1; // >1 because admin is always there
                    updateWizardView(hasEmployees ? 'after_add_employee_prompt' : 'prompt_add_employees');
                }, 300);
            }
        });
    });
    
    const urlParams = new URLSearchParams(window.location.search);
    const reactivatePrompt = urlParams.get('reactivatePrompt');
    const successDiv = document.getElementById('pageNotificationDiv_Success_Emp');
    const errorDiv = document.getElementById('pageNotificationDiv_Error_Emp');

    const selectAndScrollToEmployee = () => {
        const eid = urlParams.get('eid');
        if (eid) {
            const rowToSelect = tableBody?.querySelector(`tr[data-eid="${eid}"]`);
            if (rowToSelect) {
                selectRow(rowToSelect);
                rowToSelect.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
    };
    
    // DEBUG: Add debugging to see what flags are set
    console.log("DEBUG - Wizard flags:", {
        inWizardMode: window.inSetupWizardMode_Page,
        itemJustAdded: window.itemJustAdded_Page,
        currentStep: window.currentWizardStep_Page,
        urlStep: urlParams.get('step')
    });
    
    // PAYROLL MODAL LOGIC
    if (window.showPayrollModal) {
        const runPayrollBtn = payrollModal?.querySelector('#runPayrollBtn');
        const payrollNoBtn = payrollModal?.querySelector('#payrollNoBtn');
        
        if (runPayrollBtn) {
            runPayrollBtn.addEventListener('click', () => {
                window.location.href = `${appRoot}/payroll.jsp`;
            });
        }
        
        if (payrollNoBtn) {
            payrollNoBtn.addEventListener('click', () => {
                _hideModal(payrollModal);
            });
        }
        
        setTimeout(() => {
            _showModal(payrollModal);
        }, 500);
    }
    
    // WIZARD LOGIC WITH DEBUG
    if (window.inSetupWizardMode_Page) {
        const stepFromUrl = urlParams.get('step');
        
        if (window.itemJustAdded_Page) {
            console.log("DEBUG: Employee was just added, preparing callback");
            const wizardUpdateCallback = () => {
                console.log("DEBUG: Success notification dismissed, showing after_add_employee_prompt");
                updateWizardView('after_add_employee_prompt');
                selectAndScrollToEmployee();
            };

            if (successDiv && successDiv.textContent.trim()) {
                console.log("DEBUG: Found success message, showing notification with callback");
                showPageNotification(successDiv.innerHTML, false, wizardUpdateCallback, 'Success');
                successDiv.style.display = 'none';
            } else {
                console.log("DEBUG: No success message, showing wizard directly");
                updateWizardView('after_add_employee_prompt');
                selectAndScrollToEmployee();
            }
        } else {
            console.log("DEBUG: No employee just added, using step:", stepFromUrl || window.currentWizardStep_Page);
            let stage = stepFromUrl || window.currentWizardStep_Page || 'verify_admin_prompt';
            setTimeout(() => {
                updateWizardView(stage);
            }, 100);
        }
    } else {
        // Normal (non-wizard) page logic
        if (successDiv && successDiv.textContent.trim()) {
            showPageNotification(successDiv.innerHTML, false, selectAndScrollToEmployee, 'Success');
            successDiv.style.display = 'none';
        }
        if (errorDiv && errorDiv.textContent.trim()) {
             if (errorDiv.textContent.includes('user_limit_exceeded')) {
                const errorMessage = errorDiv.textContent.split(':')[1] || "You have reached your user limit.";
                showPageNotification(errorMessage, true, null, 'User Limit Reached', {
                    customButtonText: 'Upgrade Plan',
                    customButtonAction: () => { window.location.href = `${appRoot}/account.jsp?focus=subscription`; }
                });
            } else {
                showPageNotification(errorDiv.innerHTML, true, null, 'Error');
            }
            errorDiv.style.display = 'none';
        }

        const reopenModal = urlParams.get('reopenModal');
        if (reopenModal) {
            const prefillData = {};
            urlParams.forEach((value, key) => { prefillData[key] = value; });
            if (reopenModal === 'add') openAddModal(prefillData);
            window.history.replaceState({}, document.title, window.location.pathname);
        } else if (reactivatePrompt === 'true') {
            const eid = urlParams.get('eid');
            const email = urlParams.get('email');
            const name = urlParams.get('name');
            const reactivateEmailElement = reactivateModal.querySelector('#reactivateEmail');
            const reactivateNameElement = reactivateModal.querySelector('#reactivateName');
            
            if (reactivateEmailElement) reactivateEmailElement.textContent = email;
            if (reactivateNameElement && name) {
                reactivateNameElement.textContent = `(${decodeURIComponent(name)}) `;
                reactivateNameElement.style.display = 'inline';
            } else if (reactivateNameElement) {
                reactivateNameElement.style.display = 'none';
            }
            
            showModal(reactivateModal);
            reactivateModal.querySelector('#confirmReactivateBtn').onclick = () => handleReactivation(eid);
            reactivateModal.querySelector('.cancel-btn').onclick = () => _hideModal(reactivateModal);
        } else {
            const selectedEid = urlParams.get('eid');
            if (selectedEid && !urlParams.has('message')) {
                selectAndScrollToEmployee();
            } else if (!selectedEid && tableBody?.rows.length > 0) {
                selectRow(tableBody.rows[0]);
            }
        }
    }
});