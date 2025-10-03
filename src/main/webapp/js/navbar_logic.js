// js/navbar_logic.js
document.addEventListener('DOMContentLoaded', function() {

    // These elements are expected to be in navbar.jspf
    const addAccruedHoursModal = document.getElementById('addAccruedHoursModal');
    const openIndividualModalLink = document.getElementById('navAddIndividualHours');
    const openGlobalModalLink = document.getElementById('navAddGlobalHours'); // Ref for future
    const closeAddAccruedHoursBtn = document.getElementById('closeAddAccruedHoursModal');
    const cancelAddAccruedHoursBtn = document.getElementById('cancelAddAccruedHours');
    const addAccruedHoursForm = document.getElementById('addAccruedHoursForm'); // Modal form ref
    const isGlobalCheckbox = document.getElementById('isGlobalAdd'); // In modal
    const employeeDropdown = document.getElementById('targetEmployeeId'); // In modal

    if (addAccruedHoursModal) { 
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
        } 

        if (isGlobalCheckbox && employeeDropdown) {
            isGlobalCheckbox.addEventListener('change', function() { employeeDropdown.disabled = this.checked; employeeDropdown.required = !this.checked; if(this.checked){ employeeDropdown.value = ""; }});
        } 

        if (closeAddAccruedHoursBtn) {
            closeAddAccruedHoursBtn.addEventListener('click', () => { addAccruedHoursModal.classList.remove('modal-visible'); });
        }
        if (cancelAddAccruedHoursBtn) {
             cancelAddAccruedHoursBtn.addEventListener('click', () => { addAccruedHoursModal.classList.remove('modal-visible'); });
        }

        if (addAccruedHoursForm) {
             addAccruedHoursForm.addEventListener('submit', function(event) {
                 event.preventDefault(); // Always prevent default
                 // TODO: Add Fetch call logic here later for submitting from the modal
                 alert("Placeholder: Modal form submit intercepted. DB function disabled.");
                 addAccruedHoursModal.classList.remove('modal-visible');
             });
        }

    } 
	
    const notificationModal = document.getElementById('notificationModal');
    if (notificationModal) {
        const okButton = document.getElementById('okButton');
        const closeButton = document.getElementById('closeNotificationModal');
        if (okButton) {
            okButton.addEventListener('click', () => { notificationModal.classList.remove('modal-visible'); });
        }
        if (closeButton) {
             closeButton.addEventListener('click', () => { notificationModal.classList.remove('modal-visible'); });
        }
    } 

}); // End DOMContentLoaded