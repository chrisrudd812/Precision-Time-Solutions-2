// js/manageAccruedHours.js - v2 (Includes makeElementDraggable)

// --- Draggable Modal Functionality ---
function makeElementDraggable(elmnt, handle) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    const dragHandle = handle || elmnt;
    if (dragHandle && typeof dragHandle.onmousedown !== 'undefined') {
        dragHandle.onmousedown = dragMouseDown;
        if(dragHandle.style) dragHandle.style.cursor = 'move';
    } else {
        console.warn("MakeElementDraggable: No valid drag handle or element for:", elmnt ? (elmnt.id || 'Unnamed Element') : "unknown element");
        return;
    }
    function dragMouseDown(e) {
        e = e || window.event;
        let currentTarget = e.target;
        while(currentTarget && currentTarget !== dragHandle) {
            if (['INPUT', 'SELECT', 'BUTTON', 'TEXTAREA', 'A'].includes(currentTarget.tagName)) return;
            currentTarget = currentTarget.parentNode;
        }
        e.preventDefault();
        pos3 = e.clientX; pos4 = e.clientY;
        document.onmouseup = closeDragElement; document.onmousemove = elementDrag;
    }
    function elementDrag(e) {
        e = e || window.event; e.preventDefault();
        pos1 = pos3 - e.clientX; pos2 = pos4 - e.clientY;
        pos3 = e.clientX; pos4 = e.clientY;
        if (elmnt && elmnt.style) {
             elmnt.style.top = (elmnt.offsetTop - pos2) + "px";
             elmnt.style.left = (elmnt.offsetLeft - pos1) + "px";
        } else {
            closeDragElement();
        }
    }
    function closeDragElement() { document.onmouseup = null; document.onmousemove = null; }
}
// --- End Draggable Modal Functionality ---


document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Manage Accrued Hours JS Loaded (v2) ---");

    const openModalBtn = document.getElementById('openManageAccrualModalBtn');
    const manageAccrualModal = document.getElementById('manageAccruedHoursModal');
    const manageAccrualForm = document.getElementById('manageAccruedHoursForm');
    const closeModalSpanBtn = document.getElementById('closeManageAccrualModalBtn');
    const cancelModalBtn = document.getElementById('cancelManageAccrualBtn');

    const employeeSelectContainer = document.getElementById('employeeSelectContainerManage');
    const targetEmployeeIdDropdown = document.getElementById('targetEmployeeIdManage');
    const isGlobalAddInput = document.getElementById('isGlobalAddInput');
    const applyToAllCheckbox = document.getElementById('applyToAllCheckbox');
    const accrualDateInputManage = document.getElementById('accrualDateManage');


    const showPageNotification = window.showPageNotification || function(message, isError) { alert((isError ? "Error: " : "") + message); };
    const showModal = window.showModal || function(modalEl) { if(modalEl) modalEl.classList.add('modal-visible'); };
    const hideModal = window.hideModal || function(modalEl) { if(modalEl) modalEl.classList.remove('modal-visible'); };

    function toggleEmployeeDropdown() {
        if (!applyToAllCheckbox || !employeeSelectContainer || !targetEmployeeIdDropdown || !isGlobalAddInput) {
            console.warn("ManageAccrualsJS: One or more UI elements for scope toggle are missing.");
            return;
        }
        if (applyToAllCheckbox.checked) {
            employeeSelectContainer.style.display = 'none';
            targetEmployeeIdDropdown.required = false;
            targetEmployeeIdDropdown.value = '';
            isGlobalAddInput.value = 'true';
        } else {
            employeeSelectContainer.style.display = 'block';
            targetEmployeeIdDropdown.required = true;
            isGlobalAddInput.value = 'false';
        }
    }

    if (applyToAllCheckbox) {
        applyToAllCheckbox.addEventListener('change', toggleEmployeeDropdown);
        toggleEmployeeDropdown(); // Set initial state
    } else {
        if (employeeSelectContainer) employeeSelectContainer.style.display = 'block';
        if (targetEmployeeIdDropdown) targetEmployeeIdDropdown.required = true;
        if (isGlobalAddInput) isGlobalAddInput.value = 'false';
    }
    
    if (openModalBtn && manageAccrualModal) {
        openModalBtn.addEventListener('click', function() {
            if (manageAccrualForm) manageAccrualForm.reset();
            
            if(accrualDateInputManage && !accrualDateInputManage.value) { // Set date only if not already set (e.g. by JSP default)
                const today = new Date();
                const year = today.getFullYear();
                const month = String(today.getMonth() + 1).padStart(2, '0');
                const day = String(today.getDate()).padStart(2, '0');
                accrualDateInputManage.value = `${year}-${month}-${day}`;
            }

            if (applyToAllCheckbox) applyToAllCheckbox.checked = false;
            toggleEmployeeDropdown(); 
            if (targetEmployeeIdDropdown) targetEmployeeIdDropdown.value = "";
            if(document.getElementById('accrualHoursManage')) document.getElementById('accrualHoursManage').value = "";

            showModal(manageAccrualModal);
            const firstFocusable = document.getElementById('accrualTypeColumnManage');
            if (firstFocusable) firstFocusable.focus();
        });
    }

    if (closeModalSpanBtn && manageAccrualModal) closeModalSpanBtn.addEventListener('click', () => hideModal(manageAccrualModal));
    if (cancelModalBtn && manageAccrualModal) cancelModalBtn.addEventListener('click', () => hideModal(manageAccrualModal));
    
    window.addEventListener('click', (event) => {
        if (event.target === manageAccrualModal) {
            hideModal(manageAccrualModal);
        }
    });

    function handleAdjustAccrualSubmit(event) {
        event.preventDefault();
        const form = event.target;
        const submitButton = form.querySelector('button[type="submit"]');
        const actionUrl = form.getAttribute('action');

        if (applyToAllCheckbox && !applyToAllCheckbox.checked) {
            if (!targetEmployeeIdDropdown || !targetEmployeeIdDropdown.value) {
                showPageNotification("Please select an employee.", true); return;
            }
        }
        const accrualTypeSelect = form.querySelector('#accrualTypeColumnManage');
        if (!accrualTypeSelect || !accrualTypeSelect.value) {
            showPageNotification("Please select an accrual type.", true); return;
        }
        const hoursInput = form.querySelector('#accrualHoursManage');
        if (!hoursInput || !hoursInput.value || parseFloat(hoursInput.value) <= 0) {
             if (parseFloat(hoursInput.value) !== 0) { // Allow 0 for individual adjustments if that's a use case, but generally positive for "add"
                showPageNotification("Please enter hours to add (must be > 0, or 0 if adjusting).", true); return;
             }
        }
        const dateInput = form.querySelector('#accrualDateManage');
        if(!dateInput || !dateInput.value){
            showPageNotification("Please select a date of accrual.", true); return;
        }

        if (submitButton) {
            submitButton.disabled = true;
            submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Adding...';
        }

        const formData = new FormData(form);
         if(applyToAllCheckbox.checked){ // If global, ensure targetEmployeeId is not sent or is ignored by servlet
            formData.delete('targetEmployeeId');
        }
        const urlEncodedData = new URLSearchParams(formData);
        console.log("Submitting Accrual Adjustment to " + actionUrl + ": ", urlEncodedData.toString());

        fetch(actionUrl, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body: urlEncodedData })
            .then(response => {
                if (response.ok && response.headers.get("Content-Type")?.includes("application/json")) {
                    return response.json();
                } else {
                    return response.text().then(text => { throw new Error(`Server error: ${response.status} - ${text||'No details'}`); });
                }
            })
            .then(data => {
                showPageNotification(data.message || data.error || 'Operation completed.', !data.success);
                if (data.success) {
                    hideModal(manageAccrualModal);
                    form.reset();
                    if(applyToAllCheckbox) applyToAllCheckbox.checked = false;
                    toggleEmployeeDropdown();
                }
            })
            .catch(error => {
                 showPageNotification('Submission error: ' + error.message, true);
            })
            .finally(() => {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.innerHTML = '<i class="fas fa-plus-circle"></i> Add Hours';
                }
            });
    }

    if (manageAccrualForm) {
        manageAccrualForm.addEventListener('submit', handleAdjustAccrualSubmit);
    }

    if (manageAccrualModal) {
        const modalContent = manageAccrualModal.querySelector('.modal-content');
        const modalHeader = manageAccrualModal.querySelector('.modal-content > h2');
        if (modalContent && modalHeader) {
            makeElementDraggable(modalContent, modalHeader);
        }
    }

    const urlParamsOnLoad = new URLSearchParams(window.location.search);
    const successMsg = urlParamsOnLoad.get('message');
    const errorMsg = urlParamsOnLoad.get('error');
    if (successMsg) {
        showPageNotification(decodeURIComponent(successMsg), false);
        if (window.history.replaceState) { window.history.replaceState({}, document.title, window.location.pathname); }
    } else if (errorMsg) {
        showPageNotification(decodeURIComponent(errorMsg), true);
        if (window.history.replaceState) { window.history.replaceState({}, document.title, window.location.pathname); }
    }
});