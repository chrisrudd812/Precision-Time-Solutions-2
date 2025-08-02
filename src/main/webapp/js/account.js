// js/account.js - vFinalFixes_Complete
document.addEventListener('DOMContentLoaded', function() {
    console.log("account.js loaded (vFinalFixes_Complete).");

    // --- Element Selectors ---
    const editCompanyDetailsBtn = document.getElementById('editCompanyDetailsBtn');
    const editAccountLoginBtn = document.getElementById('editAccountLoginBtn');
    const manageBillingBtn = document.getElementById('manageBillingBtn');
    const gpayButton = document.getElementById('gpay-button');

    const verifyAdminPasswordModal = document.getElementById('verifyAdminPasswordModal');
    const verifyAdminPasswordForm = document.getElementById('verifyAdminPasswordForm');
    const verifyAdminEmailField = document.getElementById('verifyAdminEmail');
    const verifyAdminCurrentPasswordField = document.getElementById('verifyAdminCurrentPassword');
    const verificationNextAction = document.getElementById('verificationNextAction');

    const billingModal = document.getElementById('billingModal');
    const billingForm = document.getElementById('billingForm');
    
    const editCompanyDetailsModal = document.getElementById('editCompanyDetailsModal');
    const updateCompanyDetailsForm = document.getElementById('updateCompanyDetailsForm');
    
    const editAccountLoginModal = document.getElementById('editAccountLoginModal');
    
    const notificationModal = document.getElementById('notificationModalGeneral');

    // --- Event Listener Setup ---
    if (verifyAdminEmailField && window.primaryCompanyAdminEmail) {
        verifyAdminEmailField.value = window.primaryCompanyAdminEmail;
    }

    if (editCompanyDetailsBtn) {
        editCompanyDetailsBtn.addEventListener('click', function() {
            if (verificationNextAction) verificationNextAction.value = 'editCompanyDetails';
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.value = "";
            showModal(verifyAdminPasswordModal);
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.focus();
        });
    }

    if (editAccountLoginBtn) {
        editAccountLoginBtn.addEventListener('click', function() {
            if (verificationNextAction) verificationNextAction.value = 'editAccountLogin';
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.value = "";
            showModal(verifyAdminPasswordModal);
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.focus();
        });
    }

    if (manageBillingBtn) {
        manageBillingBtn.addEventListener('click', function() {
            if (verificationNextAction) verificationNextAction.value = 'openBillingModal';
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.value = "";
            showModal(verifyAdminPasswordModal);
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.focus();
        });
    }
    
    if (gpayButton) {
        gpayButton.addEventListener('click', function() {
            showPageNotification("Google Pay integration is coming soon! For now, please enter card details manually.", false, notificationModal, "Feature Update");
        });
    }

    if (verifyAdminPasswordForm) {
        verifyAdminPasswordForm.addEventListener('submit', function(event) {
            event.preventDefault();
            const password = verifyAdminCurrentPasswordField.value;
            const nextAction = verificationNextAction.value;
            const submitButton = this.querySelector('button[type="submit"]');

            if (!password) {
                showPageNotification("Password is required.", true, notificationModal, "Error");
                return;
            }
            if (submitButton) submitButton.disabled = true;
            
            fetch('CompanyAccountServlet?action=verifyAdminPassword', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: new URLSearchParams({ 'password': password })
            })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    hideModal(verifyAdminPasswordModal);
                    if (nextAction === 'openBillingModal') {
                        fetchAndPopulateBillingInfo();
                    } else if (nextAction === 'editCompanyDetails') {
                        fetchAndPopulateCompanyDetails();
                    } else if (nextAction === 'editAccountLogin') {
                        showModal(editAccountLoginModal);
                    }
                } else {
                    showPageNotification(data.error || "Incorrect password.", true, notificationModal, "Verification Failed");
                }
            })
            .catch(error => {
                console.error("Error during verification fetch:", error);
                showPageNotification("An error occurred during verification.", true, notificationModal, "Error");
            })
            .finally(() => {
                if (submitButton) submitButton.disabled = false;
            });
        });
    }

    function fetchAndPopulateBillingInfo() {
        // Since we don't store billing info, we just clear and show the modal.
        if (billingForm) billingForm.reset();
        document.getElementById('cardNumber').placeholder = '•••• •••• •••• ••••';
        showModal(billingModal);
    }

    function fetchAndPopulateCompanyDetails() {
        fetch('CompanyAccountServlet?action=getCompanyDetails')
            .then(response => response.json())
            .then(data => {
                if (data.success && data.details) {
                    const details = data.details;
                    document.getElementById('editCompanyNameDisplay').value = details.companyName || '';
                    document.getElementById('editCompanyIdDisplay').value = details.companyIdentifier || '';
                    document.getElementById('editCompanyPhone').value = details.companyPhone || '';
                    document.getElementById('editCompanyAddress').value = details.companyAddress || '';
                    document.getElementById('editCompanyCity').value = details.companyCity || '';
                    document.getElementById('editCompanyState').value = details.companyState || '';
                    document.getElementById('editCompanyZip').value = details.companyZip || '';
                    showModal(editCompanyDetailsModal);
                } else {
                     showPageNotification("Could not load company details for editing.", true, notificationModal, "Error");
                }
            });
    }
    
    if (updateCompanyDetailsForm) {
         updateCompanyDetailsForm.addEventListener('submit', function(e) {
            e.preventDefault();
            const submitButton = this.querySelector('button[type="submit"]');
            if (submitButton) submitButton.disabled = true;

            const formData = new FormData(this);
            fetch('CompanyAccountServlet?action=updateCompanyDetails', {
                method: 'POST',
                body: new URLSearchParams(formData)
            })
            .then(response => response.json())
            .then(data => {
                if(data.success) {
                    hideModal(editCompanyDetailsModal);
                    showPageNotification(data.message || "Company details updated!", false, notificationModal, "Success");
                    setTimeout(() => window.location.reload(), 2000);
                } else {
                    showPageNotification(data.error || "Failed to update details.", true, notificationModal, "Update Failed");
                }
            })
            .catch(error => {
                console.error("Error updating company details:", error);
                showPageNotification("An error occurred during the update.", true, notificationModal, "Error");
            })
            .finally(() => {
                 if (submitButton) submitButton.disabled = false;
            });
         });
    }

    // Add a simple submit handler for the billing form to show processing state
    if (billingForm) {
        billingForm.addEventListener('submit', function(e) {
            const submitBtn = this.querySelector('button[type="submit"]');
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';
            }
        });
    }
    
    // The draggable functionality is handled globally by your commonUtils.js,
    // so no specific calls are needed here.

    document.querySelectorAll('[data-close-modal]').forEach(button => {
        button.addEventListener('click', () => {
            const modalToClose = button.closest('.modal');
            if (modalToClose) hideModal(modalToClose);
        });
    });
});
