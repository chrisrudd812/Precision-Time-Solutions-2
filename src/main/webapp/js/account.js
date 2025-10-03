// js/account.js - vFinalWithCorrectUpdate
document.addEventListener('DOMContentLoaded', function() {
    
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
    
    const billingRequiredModal = document.getElementById('billingRequiredModal');
    const billingModalMessageElement = document.getElementById('billingRequiredModalMessage');
    const billingModalManageButton = document.getElementById('billingModalManageButton');
    
    const lifetimeAccessModal = document.getElementById('lifetimeAccessModal');

    function checkSubscriptionStatus() {
        const notificationDiv = document.getElementById('subscriptionStatusMessage');
        if (!notificationDiv) return;

        notificationDiv.textContent = 'Syncing your subscription details...';
        notificationDiv.className = 'page-message info-message';
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
            setTimeout(() => {
                window.location.href = window.location.pathname;
            }, 2500);
        })
        .catch(error => {
            console.error('Error checking subscription status:', error);
            notificationDiv.textContent = 'A network error occurred while syncing.';
            notificationDiv.className = 'page-message error-message';
        });
    }

    if (verifyAdminEmailField && window.primaryCompanyAdminEmail) {
        verifyAdminEmailField.value = window.primaryCompanyAdminEmail;
    }

    if (editCompanyDetailsBtn) {
        editCompanyDetailsBtn.addEventListener('click', function() {
            if (verificationNextAction) verificationNextAction.value = 'editCompanyDetails';
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.value = "";
            showModal(verifyAdminPasswordModal);
            if (verifyAdminCurrentPasswordField) {
                setTimeout(() => verifyAdminCurrentPasswordField.focus(), 150);
            }
        });
    }

    if (manageBillingBtn) {
        manageBillingBtn.addEventListener('click', function() {
            if (typeof currentSubscriptionStatus !== 'undefined' && currentSubscriptionStatus === 'Active - Lifetime Promo') {
                showModal(lifetimeAccessModal);
            } else {
                if (verificationNextAction) verificationNextAction.value = 'redirectToPortal';
                if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.value = "";
                showModal(verifyAdminPasswordModal);
                if (verifyAdminCurrentPasswordField) {
                    setTimeout(() => verifyAdminCurrentPasswordField.focus(), 150);
                }
            }
        });
    }
    
    if (verifyAdminPasswordForm) {
        verifyAdminPasswordForm.addEventListener('submit', function(event) {
            event.preventDefault();
            const password = verifyAdminCurrentPasswordField.value;
            const nextAction = verificationNextAction.value;
            const submitButton = this.querySelector('button[type="submit"]');

            if (!password) {
                showPageNotification("Password is required.", 'error', null, "Error");
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
                        document.getElementById('stripePortalForm').submit();
                    } else if (nextAction === 'editCompanyDetails') {
                        fetchAndPopulateCompanyDetails();
                    }
                } else {
                    showPageNotification(data.error || "Incorrect password.", 'error', null, "Verification Failed");
                }
            })
            .catch(error => {
                console.error("Error during verification fetch:", error);
                showPageNotification("An error occurred during verification.", 'error', null, "Error");
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
                     showPageNotification("Could not load company details for editing.", 'error', null, "Error");
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
                    
                    // MODIFIED: This logic runs AFTER the save is successful
                    const newAddress = document.getElementById('editCompanyAddress').value;
                    const newCity = document.getElementById('editCompanyCity').value;
                    const newState = document.getElementById('editCompanyState').value;
                    const newZip = document.getElementById('editCompanyZip').value;
                    const newPhone = document.getElementById('editCompanyPhone').value;
                    
                    const displayAddress = document.getElementById('displayCompanyAddress');
                    const displayPhone = document.getElementById('displayCompanyPhone');

                    if (displayAddress) {
                        displayAddress.textContent = `${newAddress}, ${newCity}, ${newState} ${newZip}`;
                    }
                    if (displayPhone) {
                        displayPhone.textContent = newPhone;
                    }
                    
                    showPageNotification(data.message || "Company details updated!", 'success', null, "Success");
                } else {
                    showPageNotification(data.error || "Failed to update details.", 'error', null, "Update Failed");
                }
            })
            .catch(error => {
                console.error("Error updating company details:", error);
                showPageNotification("An error occurred during the update.", 'error', null, "Error");
            })
            .finally(() => {
                 if (submitButton) submitButton.disabled = false;
            });
         });
    }
    
    if (billingModalManageButton) {
        billingModalManageButton.addEventListener('click', function() {
            hideModal(billingRequiredModal);
            if (manageBillingBtn) {
                manageBillingBtn.click();
            }
        });
    }
    
    document.querySelectorAll('[data-close-modal]').forEach(button => {
        button.addEventListener('click', () => {
            const modalToClose = button.closest('.modal');
            if (modalToClose) hideModal(modalToClose);
        });
    });

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('from_portal') === 'true') {
        checkSubscriptionStatus();
    }
    
    if (typeof shouldShowBillingModal !== 'undefined' && shouldShowBillingModal) {
        if (billingRequiredModal && billingModalMessageElement) {
            billingModalMessageElement.textContent = typeof billingMessage !== 'undefined' ? billingMessage : 'Your subscription requires attention.';
            showModal(billingRequiredModal);
        }
    }

    const editCompanyPhoneInput = document.getElementById('editCompanyPhone');
    if (editCompanyPhoneInput) {
        editCompanyPhoneInput.addEventListener('input', function(event) {
            const input = event.target;
            const digits = input.value.replace(/\D/g, '');
            let formatted = '';
            if (digits.length > 0) formatted = '(' + digits.substring(0, 3);
            if (digits.length >= 4) formatted += ') ' + digits.substring(3, 6);
            if (digits.length >= 7) formatted += '-' + digits.substring(6, 10);
            input.value = formatted;
        });
    }
});