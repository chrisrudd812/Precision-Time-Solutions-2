// js/departments.js
document.addEventListener('DOMContentLoaded', function() {
    
    // --- Helper Functions & Global Access ---
    const _showModal = window.showModal;
    const _hideModal = window.hideModal;
    const _decode = window.decodeHtmlEntities;
    const appRoot = window.appRootPath || "";

    // --- Element Selectors ---
    const addBtn = document.getElementById('addDepartmentButton');
    const editBtn = document.getElementById('editDepartmentButton');
    const deleteBtn = document.getElementById('deleteDepartmentButton');
    const tableBody = document.getElementById('departmentsTable')?.querySelector('tbody');

    // Modal Selectors
    const addModal = document.getElementById('addDepartmentModal');
    const editModal = document.getElementById('editDepartmentModal');
    const deleteModal = document.getElementById('deleteAndReassignDeptModal');
    const addForm = document.getElementById('addDepartmentForm');
    const editForm = document.getElementById('editDepartmentForm');
    const deleteForm = document.getElementById('deleteDepartmentForm');
    const notificationModal = document.getElementById('notificationModalGeneral');

    // --- WIZARD-SPECIFIC SELECTORS ---
    const wizardModal = document.getElementById('wizardGenericModal');
    const wizardTitle = document.getElementById('wizardGenericModalTitle');
    const wizardText1 = document.getElementById('wizardGenericModalText1');
    const wizardText2 = document.getElementById('wizardGenericModalText2');
    const wizardButtons = document.getElementById('wizardGenericModalButtonRow');

    // --- State ---
    let selectedRow = null;
    let wizardOpenedAddModal = false;

    // --- WIZARD LOGIC ---
    const wizardStages = {
        "departments_initial": {
            title: "Setup: Departments",
            text1: `Let's set up departments for <strong>${_decode(window.companyName)}</strong>.`,
            text2: "You can add some now, or proceed to the next step. Departments can always be managed later.",
            buttons: [
                { id: "wizardAddDept", text: "Add Departments", class: "text-green", action: "open_add" },
                { id: "wizardNext", text: "Next: Schedules", class: "text-blue", action: "next_step" }
            ]
        },
        "departments_after_add": {
            title: "Setup: Departments",
            text1: "Great! You've added a department.",
            text2: "Would you like to add another, or proceed to schedule setup?",
            buttons: [
                { id: "wizardAddAnother", text: "Add Another", class: "text-green", action: "open_add" },
                { id: "wizardNextAfterAdd", text: "Next: Schedules", class: "text-blue", action: "next_step" }
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
        if (action === 'open_add') {
            wizardOpenedAddModal = true;
            openAddModal();
        } else if (action === 'next_step') {
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
                window.showPageNotification(data.error || 'Could not proceed.', true, notificationModal);
            }
        })
        .catch(err => window.showPageNotification('Network error. Please try again.', true, notificationModal));
    }
    
    function hideAddModalAndHandleWizard() {
        _hideModal(addModal);
        if (window.inWizardMode_Page && wizardOpenedAddModal) {
            wizardOpenedAddModal = false;
            const nonDefaultRows = tableBody ? Array.from(tableBody.querySelectorAll('tr[data-name]')).filter(r => r.dataset.name.toLowerCase() !== 'none').length : 0;
            updateWizardView(nonDefaultRows > 0 ? "departments_after_add" : "departments_initial");
        }
    }

    // --- Core Page Logic ---
    function selectRow(row) {
        if (row && row.dataset.name.toLowerCase() === 'none') {
            window.showPageNotification("'None' is a system default and cannot be edited or deleted.", false, notificationModal);
            if (selectedRow) selectedRow.classList.remove('selected');
            selectedRow = null;
        } else {
            if (selectedRow) selectedRow.classList.remove('selected');
            if (row && row !== selectedRow) {
                row.classList.add('selected');
                selectedRow = row;
            } else {
                selectedRow = null; // Deselect
            }
        }
        toggleActionButtons();
    }

    function toggleActionButtons() {
        const isRowSelected = selectedRow !== null;
        editBtn.disabled = !isRowSelected;
        deleteBtn.disabled = !isRowSelected;
    }

    function openAddModal() {
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
        
        setTimeout(() => {
            const deptNameInput = addForm.querySelector('#addDeptName');
            if (deptNameInput) {
                deptNameInput.focus();
            }
        }, 150);
    }

    function openEditModal() {
        if (!selectedRow) return;
        const data = selectedRow.dataset;
        editForm.querySelector('#originalDeptName').value = data.name;
        editForm.querySelector('#editDeptNameDisplay').value = _decode(data.name);
        editForm.querySelector('#editDeptDescription').value = _decode(data.description);
        editForm.querySelector('#editDeptSupervisor').value = _decode(data.supervisor);
        _showModal(editModal);
    }

    function openDeleteModal() {
        if (!selectedRow) return;
        const deptName = selectedRow.dataset.name;
        deleteModal.querySelector('#deleteReassignModalMessage').innerHTML = `Delete department: <strong>${_decode(deptName)}</strong>. Reassign employees to:`;
        const select = deleteModal.querySelector('#targetReassignDeptSelect');
        select.innerHTML = '';
        window.allAvailableDepartmentsForReassign.forEach(d => {
            if (d.name !== deptName) {
                select.add(new Option(_decode(d.name), d.name));
            }
        });
        _showModal(deleteModal);
    }

    // --- Event Listeners ---
    addBtn.addEventListener('click', () => {
        if (window.inWizardMode_Page) {
            updateWizardView('departments_initial');
        } else {
            openAddModal();
        }
    });

    editBtn.addEventListener('click', openEditModal);
    deleteBtn.addEventListener('click', openDeleteModal);
    tableBody?.addEventListener('click', e => selectRow(e.target.closest('tr')));

    // Modal close/cancel listeners
    addModal.querySelectorAll('.close, .cancel-btn').forEach(el => el.addEventListener('click', hideAddModalAndHandleWizard));
    editModal.querySelectorAll('.close, .cancel-btn').forEach(el => el.addEventListener('click', () => _hideModal(editModal)));
    deleteModal.querySelectorAll('.close, .cancel-btn').forEach(el => el.addEventListener('click', () => _hideModal(deleteModal)));
    notificationModal.querySelectorAll('.close, #okButtonNotificationModalGeneral').forEach(el => el.addEventListener('click', () => _hideModal(notificationModal)));
    wizardModal?.querySelector('.close')?.addEventListener('click', () => _hideModal(wizardModal));

    deleteModal.querySelector('#confirmDeleteAndReassignBtn')?.addEventListener('click', () => {
        if (!selectedRow) return;
        deleteForm.querySelector('#hiddenDeleteDepartmentName').value = selectedRow.dataset.name;
        deleteForm.querySelector('#hiddenTargetDepartmentForReassignment').value = deleteModal.querySelector('#targetReassignDeptSelect').value;
        deleteForm.submit();
    });
    
    // ADDED: Event listener to check for duplicate department names before submitting.
    addForm.addEventListener('submit', function(event) {
        event.preventDefault(); // Stop the form from submitting immediately.

        const newDeptNameInput = addForm.querySelector('#addDeptName');
        if (!newDeptNameInput) {
            addForm.submit(); // Submit if the input isn't found for some reason.
            return;
        }
        
        const newDeptName = newDeptNameInput.value.trim().toLowerCase();
        
        // Check if a department with the same name already exists.
        const isDuplicate = window.allAvailableDepartmentsForReassign.some(dept => dept.name.trim().toLowerCase() === newDeptName);

        if (isDuplicate) {
            // If it's a duplicate, show a notification and don't submit.
            window.showPageNotification(`A department named "${newDeptNameInput.value.trim()}" already exists. Please choose a different name.`, true, notificationModal);
        } else {
            // If it's not a duplicate, submit the form.
            addForm.submit();
        }
    });

    // --- INITIALIZATION ---
    if (window.inWizardMode_Page) {
        const stage = window.itemJustAdded_Page ? 'departments_after_add' : 'departments_initial';
        updateWizardView(stage);
    }
});
