// js/login.js - v15 (Corrected Modal Styling)
document.addEventListener('DOMContentLoaded', function() {
    console.log("login.js loaded (v15 - Corrected Modal Styling)");

    const companyIdentifierField = document.getElementById('companyIdentifier');
    const emailField = document.getElementById('email');
    const passwordField = document.getElementById('password');
    const loginForm = document.getElementById('loginForm');
    const notificationModalLogin = document.getElementById('notificationModal');
    const copyCompanyIdButton = document.getElementById('copyCompanyIdButton');
    const pageErrorMessageDiv = document.getElementById('loginErrorMessage');
    const autoLogoutInfoMessageDiv = document.getElementById('autoLogoutInfoMessage');
    const browserTimeZoneInput = document.getElementById('browserTimeZone');

    const urlParams = new URLSearchParams(window.location.search);
    const companyIdFromUrl = urlParams.get('companyIdentifier') || urlParams.get('companyId');
    const adminEmailFromUrl = urlParams.get('adminEmail');
    const successMessageFromUrl = urlParams.get('message');
    const errorFromUrl = urlParams.get('error'); 
    const messageTypeFromUrl = urlParams.get('msgType');
    const autoLogoutMessageReason = urlParams.get('reason');

    if (companyIdentifierField) {
        const savedCompanyId = localStorage.getItem('lastCompanyIdentifier');
        if (companyIdFromUrl) { 
            companyIdentifierField.value = companyIdFromUrl;
            localStorage.setItem('lastCompanyIdentifier', companyIdFromUrl); 
        } else if (savedCompanyId) { 
            companyIdentifierField.value = savedCompanyId;
        }
        companyIdentifierField.addEventListener('blur', function() { 
            if (this.value.trim()) {
                localStorage.setItem('lastCompanyIdentifier', this.value.trim());
            } else {
                localStorage.removeItem('lastCompanyIdentifier');
            }
        });
    }
    if (adminEmailFromUrl && emailField && emailField.value === "") {
        emailField.value = adminEmailFromUrl;
    }

    function setInitialFocus() {
        const isModalVisible = notificationModalLogin && notificationModalLogin.classList.contains('modal-visible');
        if (isModalVisible) {
            const okButtonInModal = notificationModalLogin.querySelector('#okButtonNotificationModal');
            if (okButtonInModal) { okButtonInModal.focus(); return; }
        }
        
        if (urlParams.get('focus') === 'email') {
            if (emailField) { emailField.focus(); return; }
        }
        
        if (messageTypeFromUrl === 'logout' || autoLogoutMessageReason) {
             if (emailField) { emailField.focus(); return; }
        }

        if (companyIdentifierField && companyIdentifierField.value.trim() === '') { companyIdentifierField.focus(); } 
        else if (emailField && emailField.value.trim() === '') { emailField.focus(); } 
        else if (passwordField) { passwordField.focus(); }
    }
    
    const notificationMessageP = notificationModalLogin ? notificationModalLogin.querySelector('#notificationMessage') : null;
    const modalTitleElem = notificationModalLogin ? notificationModalLogin.querySelector('.modal-header h2') : null;
    const okBtnModal = notificationModalLogin ? notificationModalLogin.querySelector('#okButtonNotificationModal') : null;
    
    let displayModal = false;
    let modalMessage = "";
    let modalTitle = "Notification";
    let modalIconClass = "fas fa-info-circle";
    let isModalError = false;

    if (successMessageFromUrl) {
        modalMessage = successMessageFromUrl;
        if (messageTypeFromUrl === 'logout') {
            modalTitle = "Logout Successful";
            modalIconClass = "fas fa-check-circle";
        } else if (messageTypeFromUrl === 'signupSuccess' || successMessageFromUrl.toLowerCase().includes("account created")) {
             modalTitle = "Account Creation Successful!";
             modalIconClass = "fas fa-party-horn"; // Example of a different icon
        } else {
            modalTitle = "Success";
            modalIconClass = "fas fa-check-circle";
        }
        displayModal = true;
    } else if (errorFromUrl) {
        modalMessage = errorFromUrl;
        modalTitle = "Login Error";
        modalIconClass = "fas fa-exclamation-triangle";
        isModalError = true;
        displayModal = true;
    }

    if (autoLogoutMessageReason && pageErrorMessageDiv) {
        // This logic is for the non-modal message bar
        pageErrorMessageDiv.textContent = autoLogoutMessageReason;
        pageErrorMessageDiv.style.display = 'block';
    }

    if (displayModal && notificationModalLogin && notificationMessageP && modalTitleElem && okBtnModal && 
        typeof showModal === 'function' && typeof hideModal === 'function') {
        
        modalTitleElem.querySelector('span').textContent = modalTitle;
        modalTitleElem.querySelector('i').className = modalIconClass;
        notificationMessageP.innerHTML = modalMessage; // Use innerHTML to render any potential tags like in the signup message
        
        const modalContent = notificationModalLogin.querySelector('.modal-content');
        if(modalContent) {
            modalContent.className = 'modal-content'; // Reset classes
            if (isModalError) {
                modalContent.classList.add('modal-state-error');
            } else {
                // MODIFIED: This now correctly applies the success style to all non-error modals, including logout.
                modalContent.classList.add('modal-state-success');
            }
        }
        
        // Handle copy button visibility for signup success
        if (messageTypeFromUrl === 'signupSuccess' && copyCompanyIdButton) {
            copyCompanyIdButton.style.display = 'inline-flex';
            copyCompanyIdButton.addEventListener('click', function() { /* copy logic here */ });
        } else if (copyCompanyIdButton) {
            copyCompanyIdButton.style.display = 'none';
        }

        showModal(notificationModalLogin);
        const closeAndFocus = () => { hideModal(notificationModalLogin); setInitialFocus(); };
        
        const newOkBtnModal = okBtnModal.cloneNode(true);
        okBtnModal.parentNode.replaceChild(newOkBtnModal, okBtnModal);
        newOkBtnModal.addEventListener('click', closeAndFocus, {once: true});

    } else if (displayModal) { 
        alert((isModalError ? "Error: " : (modalTitle + ":\n")) + modalMessage.replace(/<[^>]*>/g, ''));
    }
    
    setInitialFocus();

    if (loginForm) { 
        loginForm.addEventListener('submit', function(event) {
            if (browserTimeZoneInput) {
                try {
                    browserTimeZoneInput.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
                } catch (e) {
                    console.warn("Could not determine browser timezone on submit:", e);
                    browserTimeZoneInput.value = "Unknown"; 
                }
            }
            if (companyIdentifierField && companyIdentifierField.value.trim()) {
                localStorage.setItem('lastCompanyIdentifier', companyIdentifierField.value.trim());
            }
            const submitButton = loginForm.querySelector('button[type="submit"]');
            if(submitButton){ submitButton.disabled = true; submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Logging In...'; }
        });
     }
});