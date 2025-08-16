// js/account.js - vFinalFixes_Complete
document.addEventListener('DOMContentLoaded', function() {
    console.log("account.js loaded (vFinalFixes_Complete).");

    // --- Element Selectors ---
    const editCompanyDetailsBtn = document.getElementById('editCompanyDetailsBtn');
    const manageBillingBtn = document.getElementById('manageBillingBtn');

    const verifyAdminPasswordModal = document.getElementById('verifyAdminPasswordModal');
    const verifyAdminPasswordForm = document.getElementById('verifyAdminPasswordForm');
    const verifyAdminEmailField = document.getElementById('verifyAdminEmail');
    const verifyAdminCurrentPasswordField = document.getElementById('verifyAdminCurrentPassword');
    const verificationNextAction = document.getElementById('verificationNextAction');

    const editCompanyDetailsModal = document.getElementById('editCompanyDetailsModal');
    const updateCompanyDetailsForm = document.getElementById('updateCompanyDetailsForm');
    
    const notificationModal = document.getElementById('notificationModalGeneral');

    // Function to handle checking subscription status after returning from portal
    function checkSubscriptionStatus() {
        const notificationDiv = document.getElementById('subscriptionStatusMessage');
        if (!notificationDiv) return;

        notificationDiv.textContent = 'Syncing your subscription details...';
        notificationDiv.className = 'page-message info-message'; // A new style for 'info'
        notificationDiv.style.display = 'block';

        fetch('SubscriptionStatusServlet', {
            method: 'POST'
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                notificationDiv.textContent = data.message;
                notificationDiv.className = 'page-message success-message';
            } else {
                notificationDiv.textContent = data.error || 'Failed to sync subscription.';
                notificationDiv.className = 'page-message error-message';
            }
            // Reload the page to show updated details from the server
            setTimeout(() => {
                // Clear the 'from_portal' param from URL and reload
                window.location.href = window.location.pathname;
            }, 2500);
        })
        .catch(error => {
            console.error('Error checking subscription status:', error);
            notificationDiv.textContent = 'A network error occurred while syncing.';
            notificationDiv.className = 'page-message error-message';
        });
    }


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

    if (manageBillingBtn) {
        manageBillingBtn.addEventListener('click', function() {
             if (verificationNextAction) verificationNextAction.value = 'redirectToPortal';
             if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.value = "";
             showModal(verifyAdminPasswordModal);
             if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.focus();
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
                    if (nextAction === 'redirectToPortal') {
                        // Find the form and submit it
                        document.getElementById('stripePortalForm').submit();
                    } else if (nextAction === 'editCompanyDetails') {
                        fetchAndPopulateCompanyDetails();
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
    
    document.querySelectorAll('[data-close-modal]').forEach(button => {
        button.addEventListener('click', () => {
            const modalToClose = button.closest('.modal');
            if (modalToClose) hideModal(modalToClose);
        });
    });

    // --- Page Load Logic ---
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('from_portal') === 'true') {
        checkSubscriptionStatus();
    }
});