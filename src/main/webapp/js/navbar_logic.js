// js/navbar_logic.js
document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Navbar Logic JS Loaded ---");

    // --- Navbar Dropdown Handling (Optional: Click Toggle) ---
    // If you prefer click over CSS hover, add the JS toggle logic here.
    // Example:
    /*
    const dropdowns = document.querySelectorAll('.navbar .dropdown');
    if (dropdowns.length > 0) {
        dropdowns.forEach(dropdown => {
            const toggle = dropdown.querySelector('.dropdown-toggle');
            if (toggle) {
                toggle.addEventListener('click', function(event) {
                    event.preventDefault();
                    const currentlyOpen = dropdown.classList.contains('open');
                    dropdowns.forEach(other => { if (other !== dropdown) other.classList.remove('open'); });
                    dropdown.classList.toggle('open'); // Use classList.add/remove if CSS uses .open
                });
            }
        });
        document.addEventListener('click', function(event) {
            if (!event.target.closest('.navbar .dropdown')) {
                dropdowns.forEach(dropdown => dropdown.classList.remove('open'));
            }
        });
        console.log("Navbar dropdown click listeners attached.");
    }
    */

    // --- Add Accrued Hours Modal Opening/Interaction Logic ---
    // These elements are expected to be in navbar.jspf
    const addAccruedHoursModal = document.getElementById('addAccruedHoursModal');
    const openIndividualModalLink = document.getElementById('navAddIndividualHours');
    const openGlobalModalLink = document.getElementById('navAddGlobalHours'); // Ref for future
    const closeAddAccruedHoursBtn = document.getElementById('closeAddAccruedHoursModal');
    const cancelAddAccruedHoursBtn = document.getElementById('cancelAddAccruedHours');
    const addAccruedHoursForm = document.getElementById('addAccruedHoursForm'); // Modal form ref
    const isGlobalCheckbox = document.getElementById('isGlobalAdd'); // In modal
    const employeeDropdown = document.getElementById('targetEmployeeId'); // In modal

    if (addAccruedHoursModal) { // Only proceed if modal exists
        console.log("Modal #addAccruedHoursModal found. Setting up its interaction listeners.");

        function openAddAccruedModal(isGlobalByDefault) {
            const form = addAccruedHoursModal.querySelector('form');
            const dateField = addAccruedHoursModal.querySelector('#accrualDate');
            const globalCheck = addAccruedHoursModal.querySelector('#isGlobalAdd');
            const empDrop = addAccruedHoursModal.querySelector('#targetEmployeeId');
            const hoursField = addAccruedHoursModal.querySelector('#accrualHours');

            if (form) form.reset();
            if (dateField) { try { dateField.valueAsDate = new Date(); } catch(e){} }
            if (globalCheck && empDrop) {
                 globalCheck.checked = isGlobalByDefault;
                 empDrop.disabled = isGlobalByDefault;
                 empDrop.required = !isGlobalByDefault;
                 if (!isGlobalByDefault && empDrop.options.length > 1) { empDrop.value = ""; }
            }
            addAccruedHoursModal.classList.add('modal-visible');
            if (hoursField) hoursField.focus();
        }

        if (openIndividualModalLink) {
            openIndividualModalLink.addEventListener('click', (e) => { e.preventDefault(); openAddAccruedModal(false); });
        } else { console.warn("Link #navAddIndividualHours not found in navbar."); }

        // Activate this when ready for the global link
        // if (openGlobalModalLink) {
        //    openGlobalModalLink.addEventListener('click', (e) => { e.preventDefault(); openAddAccruedModal(true); });
        // } else { console.warn("Link #navAddGlobalHours not found in navbar."); }

        if (isGlobalCheckbox && employeeDropdown) {
            isGlobalCheckbox.addEventListener('change', function() { employeeDropdown.disabled = this.checked; employeeDropdown.required = !this.checked; if(this.checked){ employeeDropdown.value = ""; }});
        } else { console.warn("Checkbox #isGlobalAdd or dropdown #targetEmployeeId not found inside modal."); }

        if (closeAddAccruedHoursBtn) {
            closeAddAccruedHoursBtn.addEventListener('click', () => { addAccruedHoursModal.classList.remove('modal-visible'); });
        }
        if (cancelAddAccruedHoursBtn) {
             cancelAddAccruedHoursBtn.addEventListener('click', () => { addAccruedHoursModal.classList.remove('modal-visible'); });
        }

        // **NOTE:** The SUBMIT handler for the MODAL's form (#addAccruedHoursForm)
        // will also be placed here when its functionality is fully added.
        // For now, only opening/closing/checkbox logic is here.
        if (addAccruedHoursForm) {
             addAccruedHoursForm.addEventListener('submit', function(event) {
                 event.preventDefault(); // Always prevent default
                 // TODO: Add Fetch call logic here later for submitting from the modal
                 alert("Placeholder: Modal form submit intercepted. DB function disabled.");
                 addAccruedHoursModal.classList.remove('modal-visible');
             });
             console.log("Placeholder submit listener attached to #addAccruedHoursForm (modal).");
        }


    } else {
        console.log("Note: Modal #addAccruedHoursModal not found on this page.");
    }

    // --- General Notification Modal Close/OK ---
    // This modal might be used by various scripts, so handle its basic close actions here.
    const notificationModal = document.getElementById('notificationModal');
    if (notificationModal) {
        console.log("Notification Modal found. Attaching close listeners.");
        const okButton = document.getElementById('okButton');
        const closeButton = document.getElementById('closeNotificationModal');
        if (okButton) {
            okButton.addEventListener('click', () => { notificationModal.classList.remove('modal-visible'); });
        }
        if (closeButton) {
             closeButton.addEventListener('click', () => { notificationModal.classList.remove('modal-visible'); });
        }
    } else {
         console.log("Note: General Notification Modal (#notificationModal) not found on this page.");
    }


}); // End DOMContentLoaded