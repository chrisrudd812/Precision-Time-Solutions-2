// js/departments.js
document.addEventListener('DOMContentLoaded', function() {
    
    const _showModal = window.showModal;
    const _hideModal = window.hideModal;
    const _decode = window.decodeHtmlEntities;
    const appRoot = window.appRootPath || "";

    // References to page content that needs to be hidden/shown during the wizard
    const wizardHeader = document.querySelector('.wizard-header');
    const parentContainer = document.querySelector('.parent-container');

    const addBtn = document.getElementById('addDepartmentButton');
    const editBtn = document.getElementById('editDepartmentButton');
    const deleteBtn = document.getElementById('deleteDepartmentButton');
    const tableBody = document.getElementById('departmentsTable')?.querySelector('tbody');

    const addModal = document.getElementById('addDepartmentModal');
    const editModal = document.getElementById('editDepartmentModal');
    const deleteModal = document.getElementById('deleteAndReassignDeptModal');
    const addForm = document.getElementById('addDepartmentForm');
    const editForm = document.getElementById('editDepartmentForm');
    const deleteForm = document.getElementById('deleteDepartmentForm');
    
    const wizardModal = document.getElementById('wizardGenericModal');
    const wizardTitle = document.getElementById('wizardGenericModalTitle');
    const wizardText1 = document.getElementById('wizardGenericModalText1');
    const wizardText2 = document.getElementById('wizardGenericModalText2');
    const wizardButtons = document.getElementById('wizardGenericModalButtonRow');

    let selectedRow = null;
    let wizardOpenedAddModal = false;

    function showPageNotification(message, isError = false, callback = null, title = null, options = {}) {
        if (window.showPageNotification) {
            const type = isError ? 'error' : 'success';
            window.showPageNotification(message, type, callback, title, options);
        } else {
            alert((isError ? "Error: " : "Success: ") + message);
        }
    }
    
    // --- Wizard Specific Logic ---

    const wizardStages = {
        "departments_initial": {
            title: "Setup: Departments",
            text1: `Let's set up departments for <strong>${_decode(window.companyName)}</strong>.`,
            text2: "You can add some now, or proceed to the next step. Departments can always be managed later.",
            buttons: [
                { id: "wizardAddDept", text: "<i class='fas fa-plus-circle'></i> Add Departments", class: "text-green", action: "open_add" },
                { id: "wizardNext", text: "Next: Schedules <i class='fas fa-arrow-right'></i>", class: "text-blue", action: "next_step" }
            ]
        },
        "departments_after_add": {
            title: "Setup: Departments",
            text1: "Great! You've added a department.",
            text2: "Would you like to add another, or proceed to schedule setup?",
            buttons: [
                { id: "wizardAddAnother", text: "<i class='fas fa-plus-circle'></i> Add Another", class: "text-green", action: "open_add" },
                { id: "wizardNextAfterAdd", text: "Next: Schedules <i class='fas fa-arrow-right'></i>", class: "text-blue", action: "next_step" }
            ]
        }
    };

    function setMainContentVisibility(visible) {
        if (wizardHeader) wizardHeader.style.display = visible ? 'block' : 'none';
        if (parentContainer) parentContainer.style.display = visible ? 'flex' : 'none';
    }

    function updateWizardView(stageKey) {
        if (!wizardModal || !wizardTitle) return; // defensive check
        const stage = wizardStages[stageKey];
        if (!stage) { 
            _hideModal(wizardModal);
            setMainContentVisibility(true);
            return; 
        }

        setMainContentVisibility(false);

        const titleSpan = wizardTitle.querySelector('span');
        if (titleSpan) titleSpan.textContent = stage.title;

        wizardText1.innerHTML = stage.text1;
        wizardText2.innerHTML = stage.text2;
        wizardButtons.innerHTML = '';
        stage.buttons.forEach(btn => {
            const button = document.createElement('button');
            button.id = btn.id;
            button.type = 'button';
            button.className = `glossy-button ${btn.class}`;
            button.innerHTML = btn.text;
            button.addEventListener('click', () => handleWizardAction(btn.action, button));
            wizardButtons.appendChild(button);
        });
        _showModal(wizardModal);
    }

    function handleWizardAction(action, buttonElement) {
        if (action === 'open_add') {
            _hideModal(wizardModal);
            wizardOpenedAddModal = true;
            setMainContentVisibility(true);
            openAddModal();
        } else if (action === 'next_step') {
            buttonElement.disabled = true;
            buttonElement.innerHTML = "<i class='fas fa-spinner fa-spin'></i> Proceeding...";
            advanceWizardToServerAndRedirect("schedules_prompt", "scheduling.jsp");
        }
    }

    function advanceWizardToServerAndRedirect(serverNextStep, nextPage) {
        fetch(`${appRoot}/WizardStatusServlet`, {
            method: 'POST',
            headers: {'Content-Type': 'application/x-www-form-urlencoded'},
            body: new URLSearchParams({'action': 'setWizardStep', 'nextStep': serverNextStep})
        })
        .then(res => res.ok ? res.json() : Promise.reject('Server error'))
        .then(data => {
            if (data.success) {
                window.location.href = `${appRoot}/${nextPage}?setup_wizard=true&step=${encodeURIComponent(serverNextStep)}`;
            } else {
                showPageNotification(data.error || 'Could not proceed.', true, null, 'Error');
            }
        })
        .catch(err => showPageNotification('Network error. Please try again.', true, null, 'Error'));
    }
    
    function hideAddModalAndHandleWizard() {
        _hideModal(addModal);
        if (window.inWizardMode_Page && wizardOpenedAddModal) {
            wizardOpenedAddModal = false;
            const nonDefaultRows = tableBody ? Array.from(tableBody.querySelectorAll('tr[data-name]')).filter(r => r.dataset.name.toLowerCase() !== 'none').length : 0;
            updateWizardView(nonDefaultRows > 0 ? "departments_after_add" : "departments_initial");
        }
    }
    
    // --- Standard Page Logic ---

    function selectRow(row) {
        if (row && row.dataset.name && row.dataset.name.toLowerCase() === 'none') {
            showPageNotification("'None' is a system default and cannot be edited or deleted.", true, null, "Action Denied");
            if (selectedRow) selectedRow.classList.remove('selected');
            selectedRow = null;
        } else {
            if (selectedRow) selectedRow.classList.remove('selected');
            if (row && row !== selectedRow) {
                row.classList.add('selected');
                selectedRow = row;
            } else {
                selectedRow = null;
            }
        }
        toggleActionButtons();
    }

    function toggleActionButtons() {
        if (!editBtn || !deleteBtn) return;
        const isRowSelected = selectedRow !== null;
        editBtn.disabled = !isRowSelected;
        deleteBtn.disabled = !isRowSelected;
    }

    function openAddModal() {
        if (!addModal || !addForm) return;
        addForm.reset();
        if (window.inWizardMode_Page) {
            let hiddenInput = addForm.querySelector('input[name="setup_wizard"]');
            if (!hiddenInput) {
                hiddenInput = document.createElement('input');
                hiddenInput.type = 'hidden';
                hiddenInput.name = 'setup_wizard';
                addForm.appendChild(hiddenInput);
            }
            hiddenInput.value = 'true';
        }
        _showModal(addModal);
        setTimeout(() => addForm.querySelector('#addDeptName')?.focus(), 150);
    }

    function openEditModal() {
        if (!selectedRow || !editModal || !editForm) return;
        const data = selectedRow.dataset;
        editForm.querySelector('#originalDeptName').value = data.name;
        editForm.querySelector('#editDeptNameDisplay').value = _decode(data.name);
        editForm.querySelector('#editDeptDescription').value = _decode(data.description);
        editForm.querySelector('#editDeptSupervisor').value = _decode(data.supervisor);
        _showModal(editModal);
    }

    function openDeleteModal() {
        if (!selectedRow || !deleteModal) return;
        const deptName = selectedRow.dataset.name;
        deleteModal.querySelector('#deleteReassignModalMessage').innerHTML = `Delete department: <strong>${_decode(deptName)}</strong>.`;
        const select = deleteModal.querySelector('#targetReassignDeptSelect');
        select.innerHTML = '<option value="">-- Select a Department --</option>';
    
        window.allAvailableDepartmentsForReassign.forEach(d => {
            if (d.name !== deptName) {
                select.add(new Option(_decode(d.name), d.name));
            }
        });
        _showModal(deleteModal);
    }
    
    // --- Event Listeners ---

    if (addBtn) addBtn.addEventListener('click', openAddModal);
    if(editBtn) editBtn.addEventListener('click', openEditModal);
    if(deleteBtn) deleteBtn.addEventListener('click', openDeleteModal);
    if(tableBody) tableBody.addEventListener('click', e => selectRow(e.target.closest('tr')));

    addModal?.querySelectorAll('.cancel-btn').forEach(el => el.addEventListener('click', hideAddModalAndHandleWizard));
    editModal?.querySelectorAll('.cancel-btn').forEach(el => el.addEventListener('click', () => _hideModal(editModal)));
    deleteModal?.querySelectorAll('.cancel-btn').forEach(el => el.addEventListener('click', () => _hideModal(deleteModal)));
    
    wizardModal?.querySelector('.close')?.addEventListener('click', () => {
        _hideModal(wizardModal);
        setMainContentVisibility(true);
    });
    
    deleteModal?.querySelector('#confirmDeleteAndReassignBtn')?.addEventListener('click', () => {
        if (!selectedRow || !deleteForm) return;
        const targetDept = deleteModal.querySelector('#targetReassignDeptSelect').value;
        if (!targetDept) {
            showPageNotification('You must select a department to reassign employees to.', true, null, 'Selection Required');
            return;
        }
        deleteForm.querySelector('#hiddenDeleteDepartmentName').value = selectedRow.dataset.name;
        deleteForm.querySelector('#hiddenTargetDepartmentForReassignment').value = targetDept;
        deleteForm.submit();
    });
    
    addForm?.addEventListener('submit', function(event) {
        event.preventDefault(); 
        const newDeptNameInput = addForm.querySelector('#addDeptName');
        const newDeptName = newDeptNameInput.value.trim().toLowerCase();
        const isDuplicate = window.allAvailableDepartmentsForReassign.some(dept => dept.name.trim().toLowerCase() === newDeptName);
        if (isDuplicate) {
            showPageNotification(`A department named "<strong>${newDeptNameInput.value.trim()}</strong>" already exists.`, true, null, "Duplicate Name");
        } else {
            addForm.submit();
        }
    });

    // --- Page Initialization ---
    if (window.inWizardMode_Page) {
        const stage = window.itemJustAdded_Page ? 'departments_after_add' : 'departments_initial';
        updateWizardView(stage);
    }

    // Handle success/error notifications from page load
    const successNotificationDiv = document.getElementById('pageNotificationDiv_Success_Department');
    if (successNotificationDiv && successNotificationDiv.textContent.trim()) {
        const message = successNotificationDiv.innerHTML;
        successNotificationDiv.style.display = 'none';
        showPageNotification(message, false, null, "Success");
    }

    const errorNotificationDiv = document.getElementById('pageNotificationDiv_Error_Department');
    if (errorNotificationDiv && errorNotificationDiv.textContent.trim()) {
        const message = errorNotificationDiv.innerHTML;
        errorNotificationDiv.style.display = 'none';
        showPageNotification(message, true, null, "Error");
    }
});