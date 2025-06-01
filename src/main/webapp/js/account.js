// js/account.js
document.addEventListener('DOMContentLoaded', function() {
    console.log("account.js loaded with verify-first flow.");

    // --- Elements for Edit Company Details ---
    const editCompanyDetailsBtn = document.getElementById('editCompanyDetailsBtn');
    const verifyAdminPasswordModal = document.getElementById('verifyAdminPasswordModal'); // For company details edit
    const verifyAdminPasswordForm = document.getElementById('verifyAdminPasswordForm');   // For company details edit
    const verifyAdminEmailField = document.getElementById('verifyAdminEmail');             // For company details edit (pre-fill)
    const verifyAdminCurrentPasswordField = document.getElementById('verifyAdminCurrentPassword'); // For company details edit

    const editCompanyDetailsModal = document.getElementById('editCompanyDetailsModal');
    const updateCompanyDetailsForm = document.getElementById('updateCompanyDetailsForm');
    const editCompanyNameDisplay = document.getElementById('editCompanyNameDisplay');
    const editCompanyIdDisplay = document.getElementById('editCompanyIdDisplay');
    const editCompanyPhoneField = document.getElementById('editCompanyPhone');
    const editCompanyAddressField = document.getElementById('editCompanyAddress');
    const editCompanyCityField = document.getElementById('editCompanyCity');
    const editCompanyStateField = document.getElementById('editCompanyState');
    const editCompanyZipField = document.getElementById('editCompanyZip');
    
    // --- General Notification Modal ---
    const notificationModal = document.getElementById('notificationModalGeneral');

    // --- Elements for Edit Account Login Details (New Flow) ---
    const editAccountLoginBtn = document.getElementById('editAccountLoginBtn');
    const editAccountLoginModal = document.getElementById('editAccountLoginModal');
    
    const verifyPasswordSectionForLoginChange = document.getElementById('verifyPasswordSectionForLoginChange');
    const verifyCurrentAdminPasswordForLoginChangeField = document.getElementById('verifyCurrentAdminPasswordForLoginChange');
    const triggerVerifyAdminPasswordForLoginChangeBtn = document.getElementById('triggerVerifyAdminPasswordForLoginChange');
    
    const updateAccountLoginForm = document.getElementById('updateAccountLoginForm');
    const editAccountLoginSeparator = document.getElementById('editAccountLoginSeparator');
    const verifiedCurrentPasswordField = document.getElementById('verifiedCurrentPassword'); // Hidden input

    const currentAdminLoginEmailField = document.getElementById('currentAdminLoginEmail'); // Display field in new modal
    const newAdminLoginEmailField = document.getElementById('newAdminLoginEmail');
    const confirmNewAdminLoginEmailField = document.getElementById('confirmNewAdminLoginEmail');
    const newAdminPasswordField = document.getElementById('newAdminPassword');
    const confirmNewAdminPasswordField = document.getElementById('confirmNewAdminPassword');
    const saveAccountChangesBtn = document.getElementById('saveAccountChangesBtn');

    // --- Helper: Show/Hide Modal (Assuming from common-scripts.jspf) ---
    // These should be globally available if common-scripts.jspf is included before this script
    // function showModal(modalElement) { /* ... */ }
    // function hideModal(modalElement) { /* ... */ }
    // function showPageNotification(message, isError, modalElement, title) { /* ... */ }


    // --- Logic for Edit Company Details (Original Flow - should still work) ---
    if (editCompanyDetailsBtn && verifyAdminEmailField && window.primaryCompanyAdminEmail) {
        verifyAdminEmailField.value = window.primaryCompanyAdminEmail; // Pre-fill for company details verification modal
    }

    if (editCompanyDetailsBtn && verifyAdminPasswordModal && typeof showModal === 'function') {
        editCompanyDetailsBtn.addEventListener('click', function() {
            console.log("Edit Company Details button clicked (original flow).");
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.value = "";
            showModal(verifyAdminPasswordModal);
            if (verifyAdminCurrentPasswordField) verifyAdminCurrentPasswordField.focus();
        });
    }

    if (verifyAdminPasswordForm && typeof hideModal === 'function' && typeof showModal === 'function') {
        verifyAdminPasswordForm.addEventListener('submit', function(event) {
            event.preventDefault();
            const password = verifyAdminCurrentPasswordField.value;
            const submitButton = this.querySelector('button[type="submit"]');
            if (submitButton) submitButton.disabled = true;
            console.log("Verifying company admin password for company details edit...");

            fetch('CompanyAccountServlet?action=verifyAdminPassword', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: new URLSearchParams({
                    'adminEmail': verifyAdminEmailField.value, // Primary admin email
                    'password': password
                })
            })
            .then(response => {
                if (!response.ok) { 
                    return response.json().then(err => Promise.reject(err)).catch(() => Promise.reject({error: "Password verification failed. Status: " + response.status}));
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    console.log("Admin password verified for company details edit.");
                    hideModal(verifyAdminPasswordModal);
                    fetchCompanyDetailsForEditModal();
                } else {
                    console.error("Admin password verification failed for company details edit:", data.error);
                    if (typeof showPageNotification === 'function' && notificationModal) {
                        showPageNotification(data.error || "Incorrect password or verification error.", true, notificationModal, "Verification Failed");
                    } else {
                        alert(data.error || "Incorrect password or verification error.");
                    }
                }
            })
            .catch(error => {
                console.error("Error during admin password verification for company details edit:", error);
                if (typeof showPageNotification === 'function' && notificationModal) {
                    showPageNotification(error.error || error.message || "An error occurred during verification.", true, notificationModal, "Verification Error");
                } else {
                    alert(error.error || error.message || "An error occurred during verification.");
                }
            })
            .finally(() => {
                if (submitButton) submitButton.disabled = false;
            });
        });
    }

    function fetchCompanyDetailsForEditModal() {
        console.log("Fetching current company details for edit modal...");
        fetch('CompanyAccountServlet?action=getCompanyDetails')
            .then(response => {
                if (!response.ok) { return response.json().then(err => Promise.reject(err)).catch(() => Promise.reject({error: "Failed to fetch details. Status: " + response.status}));}
                return response.json();
            })
            .then(data => {
                if (data.success && data.details) {
                    console.log("Company details fetched:", data.details);
                    populateEditCompanyDetailsModal(data.details);
                    if (typeof showModal === 'function') showModal(editCompanyDetailsModal);
                } else {
                    throw new Error(data.error || "Could not retrieve company details.");
                }
            })
            .catch(error => {
                console.error("Error fetching company details:", error);
                if (typeof showPageNotification === 'function' && notificationModal) {
                    showPageNotification(error.error || error.message || "Could not load company details for editing.", true, notificationModal, "Error");
                } else {
                    alert(error.error || error.message || "Could not load company details for editing.");
                }
            });
    }

    function populateEditCompanyDetailsModal(details) {
        if (editCompanyNameDisplay) editCompanyNameDisplay.value = details.companyName || "N/A";
        if (editCompanyIdDisplay) editCompanyIdDisplay.value = details.companyIdentifier || "N/A";
        if (editCompanyPhoneField) editCompanyPhoneField.value = details.companyPhone === "N/A" ? "" : (details.companyPhone || "");
        if (editCompanyAddressField) editCompanyAddressField.value = details.companyAddress === "N/A" ? "" : (details.companyAddress || "");
        if (editCompanyCityField) editCompanyCityField.value = details.companyCity === "N/A" ? "" : (details.companyCity || "");
        if (editCompanyStateField) editCompanyStateField.value = details.companyState === "N/A" ? "" : (details.companyState || "");
        if (editCompanyZipField) editCompanyZipField.value = details.companyZip === "N/A" ? "" : (details.companyZip || "");
    }

    if (updateCompanyDetailsForm && typeof hideModal === 'function') {
        updateCompanyDetailsForm.addEventListener('submit', function(event) {
            event.preventDefault();
            const submitButton = this.querySelector('button[type="submit"]');
            if (submitButton) submitButton.disabled = true;
            const formData = new FormData(this);
            console.log("Updating company details...");

            fetch('CompanyAccountServlet?action=updateCompanyDetails', {
                method: 'POST',
                body: new URLSearchParams(formData)
            })
            .then(response => {
                if (!response.ok) { return response.json().then(err => Promise.reject(err)).catch(() => Promise.reject({error: "Update failed. Status: " + response.status}));}
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    console.log("Company details updated successfully.");
                    hideModal(editCompanyDetailsModal);
                    if (typeof showPageNotification === 'function' && notificationModal) {
                        showPageNotification(data.message || "Company details updated successfully!", false, notificationModal, "Update Successful");
                        setTimeout(() => { window.location.reload(); }, 2500);
                    } else {
                        alert(data.message || "Company details updated successfully!");
                        window.location.reload();
                    }
                } else {
                    throw new Error(data.error || "Failed to update company details.");
                }
            })
            .catch(error => {
                console.error("Error updating company details:", error);
                 if (typeof showPageNotification === 'function' && notificationModal) {
                    showPageNotification(error.error || error.message || "An error occurred while updating.", true, notificationModal, "Update Error");
                } else {
                    alert(error.error || error.message || "An error occurred while updating.");
                }
            })
            .finally(() => {
                if (submitButton) submitButton.disabled = false;
            });
        });
    }

    // --- NEW Logic for Edit Account Login Details (Verify-First Flow) ---

    function resetAndPrepareEditLoginModal() {
        if (!editAccountLoginModal || !currentAdminLoginEmailField || !verifyPasswordSectionForLoginChange || 
            !updateAccountLoginForm || !saveAccountChangesBtn || !verifyCurrentAdminPasswordForLoginChangeField ||
            !triggerVerifyAdminPasswordForLoginChangeBtn || !editAccountLoginSeparator || !newAdminLoginEmailField ||
            !confirmNewAdminLoginEmailField || !newAdminPasswordField || !confirmNewAdminPasswordField || !verifiedCurrentPasswordField) {
            console.error("One or more elements for Edit Account Login modal are missing.");
            return false;
        }

        // Pre-fill current admin email display in the update form section
        currentAdminLoginEmailField.value = window.primaryCompanyAdminEmail || "N/A";

        // Reset and show verification section
        verifyCurrentAdminPasswordForLoginChangeField.value = "";
        verifyCurrentAdminPasswordForLoginChangeField.disabled = false;
        triggerVerifyAdminPasswordForLoginChangeBtn.disabled = false;
        verifyPasswordSectionForLoginChange.style.display = 'block';
        
        // Hide and disable update section
        updateAccountLoginForm.style.display = 'none';
        editAccountLoginSeparator.style.display = 'none';
        
        newAdminLoginEmailField.value = ""; newAdminLoginEmailField.disabled = true;
        confirmNewAdminLoginEmailField.value = ""; confirmNewAdminLoginEmailField.disabled = true;
        newAdminPasswordField.value = ""; newAdminPasswordField.disabled = true;
        confirmNewAdminPasswordField.value = ""; confirmNewAdminPasswordField.disabled = true;
        
        verifiedCurrentPasswordField.value = ""; // Clear the hidden field that stores verified password
        saveAccountChangesBtn.disabled = true;
        return true;
    }

    if (editAccountLoginBtn && typeof showModal === 'function') {
        editAccountLoginBtn.addEventListener('click', function() {
            console.log("Edit Account Login Details button clicked.");
            if (resetAndPrepareEditLoginModal()) {
                showModal(editAccountLoginModal);
                verifyCurrentAdminPasswordForLoginChangeField.focus();
            }
        });
    }

    if (triggerVerifyAdminPasswordForLoginChangeBtn && typeof showPageNotification === 'function') {
        triggerVerifyAdminPasswordForLoginChangeBtn.addEventListener('click', function() {
            const currentPasswordInModal = verifyCurrentAdminPasswordForLoginChangeField.value;
            if (!currentPasswordInModal) {
                showPageNotification("Please enter your current company admin password to verify.", true, notificationModal, "Verification Required");
                verifyCurrentAdminPasswordForLoginChangeField.focus();
                return;
            }

            this.disabled = true; // Disable verify button during request
            console.log("Verifying current admin password via modal button for login changes...");

            fetch('CompanyAccountServlet?action=verifyAdminPassword', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: new URLSearchParams({
                    'adminEmail': window.primaryCompanyAdminEmail, // Current primary admin email
                    'password': currentPasswordInModal
                })
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().then(err => Promise.reject(err)).catch(() => Promise.reject({error: "Password verification failed. Status: " + response.status }));
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    console.log("Password verified successfully. Enabling edit fields.");
                    showPageNotification("Password verified. You can now make changes.", false, notificationModal, "Verification Successful");

                    verifiedCurrentPasswordField.value = currentPasswordInModal; // Store verified password
                    
                    verifyCurrentAdminPasswordForLoginChangeField.disabled = true; // Keep field disabled
                    triggerVerifyAdminPasswordForLoginChangeBtn.disabled = true; // Keep button disabled or hide

                    updateAccountLoginForm.style.display = 'block';
                    editAccountLoginSeparator.style.display = 'block';
                    newAdminLoginEmailField.disabled = false;
                    confirmNewAdminLoginEmailField.disabled = false;
                    newAdminPasswordField.disabled = false;
                    confirmNewAdminPasswordField.disabled = false;
                    saveAccountChangesBtn.disabled = false;
                    
                    newAdminPasswordField.focus(); // Focus on the first editable field for changes

                } else {
                    console.error("Password verification failed (modal button):", data.error);
                    showPageNotification(data.error || "Incorrect password. Please try again.", true, notificationModal, "Verification Failed");
                    verifyCurrentAdminPasswordForLoginChangeField.focus();
                    verifyCurrentAdminPasswordForLoginChangeField.select();
                    this.disabled = false; // Re-enable verify button on failure
                }
            })
            .catch(error => {
                console.error("Error during password verification (modal button):", error);
                showPageNotification(error.error || error.message || "An error occurred during verification.", true, notificationModal, "Verification Error");
                this.disabled = false; // Re-enable verify button on error
            });
        });
    }

    if (updateAccountLoginForm && typeof hideModal === 'function' && typeof showPageNotification === 'function') {
        updateAccountLoginForm.addEventListener('submit', function(event) {
            event.preventDefault();
            const submitButton = saveAccountChangesBtn; // Specific button
            
            const currentPasswordForSubmit = verifiedCurrentPasswordField.value; 
            const newEmail = newAdminLoginEmailField.value.trim();
            const confirmNewEmail = confirmNewAdminLoginEmailField.value.trim();
            const newPassword = newAdminPasswordField.value;
            const confirmNewPassword = confirmNewAdminPasswordField.value;

            if (!currentPasswordForSubmit) {
                showPageNotification("Password was not verified. Please verify your current password first.", true, notificationModal, "Error");
                resetAndPrepareEditLoginModal(); // Reset modal to verification step
                if(editAccountLoginModal.style.display !== 'block' && typeof showModal === 'function'){
                    showModal(editAccountLoginModal); // Ensure modal is visible if reset
                }
                verifyCurrentAdminPasswordForLoginChangeField.focus();
                return;
            }

            let changesRequested = false;
            if (newEmail) {
                if (newEmail === (window.primaryCompanyAdminEmail || "N/A")) {
                    // Not a change if it's the same as the original email loaded on the page
                } else {
                    changesRequested = true;
                    if (newEmail !== confirmNewEmail) {
                        showPageNotification("New admin emails do not match.", true, notificationModal, "Validation Error");
                        return;
                    }
                    if (!/^\S+@\S+\.\S+$/.test(newEmail)) {
                        showPageNotification("New admin email format is invalid.", true, notificationModal, "Validation Error");
                        return;
                    }
                }
            }

            if (newPassword) {
                changesRequested = true;
                if (newPassword.length < 8) { 
                     showPageNotification("New password must be at least 8 characters long.", true, notificationModal, "Validation Error");
                    return;
                }
                if (newPassword !== confirmNewPassword) {
                    showPageNotification("New admin passwords do not match.", true, notificationModal, "Validation Error");
                    return;
                }
            }

            if (!changesRequested) {
                showPageNotification("No actual changes specified for email or password.", false, notificationModal, "Information");
                return;
            }

            if (submitButton) submitButton.disabled = true;
            console.log("Submitting account login details update...");

            const payload = {
                currentPassword: currentPasswordForSubmit,
                newAdminLoginEmail: newEmail, // Send even if same, servlet will check if different from DB
                newAdminPassword: newPassword  // Send even if empty, servlet will check if empty
            };
            
            fetch('CompanyAccountServlet?action=updateAdminLoginDetails', {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                body: new URLSearchParams(payload) 
            })
            .then(response => {
                if (!response.ok) { 
                    return response.json().then(err => Promise.reject(err)).catch(() => Promise.reject({error: "Update failed. Status: " + response.status}));
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    console.log("Account login details updated successfully:", data);
                    hideModal(editAccountLoginModal);
                    let successMessage = data.message || "Account login details updated successfully!";
                    
                    showPageNotification(successMessage, false, notificationModal, "Update Successful");
                    // Update window.primaryCompanyAdminEmail if it changed, for subsequent modal openings before reload
                    if (data.emailChanged && data.newAdminEmail) {
                        window.primaryCompanyAdminEmail = data.newAdminEmail;
                    }
                    setTimeout(() => { window.location.reload(); }, 3000);
                } else {
                    showPageNotification(data.error || "Failed to update account login details.", true, notificationModal, "Update Error");
                    if (data.error && data.error.toLowerCase().includes("password")) {
                         // If error is password related after successful client verification, implies something unexpected or session issue.
                         // Resetting to verification step is a safe bet.
                         setTimeout(() => {
                            if(resetAndPrepareEditLoginModal()){
                                if(editAccountLoginModal.style.display !== 'block' && typeof showModal === 'function'){
                                     showModal(editAccountLoginModal);
                                }
                                verifyCurrentAdminPasswordForLoginChangeField.focus();
                            }
                         }, 500);
                    }
                }
            })
            .catch(error => {
                console.error("Error updating account login details:", error);
                showPageNotification(error.error || error.message || "An error occurred.", true, notificationModal, "Update Error");
            })
            .finally(() => {
                if (submitButton) submitButton.disabled = false;
            });
        });
    }

    // Common Close listeners for all modals
    document.querySelectorAll('[data-close-modal]').forEach(button => {
        button.addEventListener('click', () => {
            const modalToClose = button.closest('.modal');
            if (modalToClose && typeof hideModal === 'function') {
                hideModal(modalToClose);
                if (modalToClose.id === 'editAccountLoginModal') {
                    console.log("Edit Account Login modal closed, ensuring clean state for next open.");
                    resetAndPrepareEditLoginModal(); // Reset its state but don't show it
                }
            } else if (modalToClose) { // Fallback
                modalToClose.style.display = 'none';
            }
        });
    });
});