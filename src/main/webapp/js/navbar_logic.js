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

// Global function to open time card modal from navbar
function openTimeCardModal(url) {
    const modal = document.createElement('div');
    modal.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.8);z-index:10000;display:flex;align-items:center;justify-content:center;';
    modal.innerHTML = `<div style="width:1200px;max-width:90%;height:800px;max-height:85vh;background:#fff;border-radius:8px;position:relative;display:flex;flex-direction:column;box-shadow:0 4px 20px rgba(0,0,0,0.3);"><button onclick="this.closest('div[style*=fixed]').remove();" style="position:absolute;top:10px;right:10px;z-index:1;background:#dc3545;color:#fff;border:none;border-radius:4px;padding:8px 16px;cursor:pointer;font-size:16px;font-weight:bold;">✕ Close</button><iframe src="${url}" style="width:100%;height:100%;border:none;border-radius:8px;"></iframe></div>`;
    document.body.appendChild(modal);
}