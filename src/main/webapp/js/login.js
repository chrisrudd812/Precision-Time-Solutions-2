// js/login.js - v14 (Refined focus handling for logout)
document.addEventListener('DOMContentLoaded', function() {
    console.log("login.js loaded (v14 - Refined Focus)");

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
    const companyIdFromUrl = urlParams.get('companyIdentifier') || urlParams.get('companyId'); // Also check for 'companyId'
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
        
        // [NEW] Check for the 'focus' parameter first for the welcome email link.
        if (urlParams.get('focus') === 'email') {
            if (emailField) {
                emailField.focus();
                return; // Prioritize this focus action
            }
        }
        
        // If the user was just logged out, focus the email field.
        if (messageTypeFromUrl === 'logout' || autoLogoutMessageReason) {
             if (emailField) { emailField.focus(); return; }
        }

        // Otherwise, use the original focus logic.
        if (companyIdentifierField && companyIdentifierField.value.trim() === '') { companyIdentifierField.focus(); } 
        else if (emailField && emailField.value.trim() === '') { emailField.focus(); } 
        else if (passwordField) { passwordField.focus(); }
    }
    
    const notificationMessageDiv = notificationModalLogin ? notificationModalLogin.querySelector('#notificationMessage') : null;
    const modalTitleElem = notificationModalLogin ? notificationModalLogin.querySelector('h2#notificationModalTitle') : null;
    const okBtnModal = notificationModalLogin ? notificationModalLogin.querySelector('#okButtonNotificationModal') : null;
    const closeXModal = notificationModalLogin ? notificationModalLogin.querySelector('span.close#closeNotificationModal') : null;

    let displayModal = false;
    let modalMessage = "";
    let modalTitle = "Notification";
    let isModalError = false;

    if (successMessageFromUrl) {
        modalMessage = successMessageFromUrl;
        if (messageTypeFromUrl === 'logout') {
            modalTitle = "Logout Successful";
        } else if (messageTypeFromUrl === 'signupSuccess' || successMessageFromUrl.toLowerCase().includes("account created")) {
             modalTitle = "Account Creation Successful!";
            let companyIdForCopy = "";
            const tempDiv = document.createElement('div'); tempDiv.innerHTML = successMessageFromUrl;
            const strongIdElement = tempDiv.querySelector('#copyCompanyIdValue');
            if (strongIdElement && strongIdElement.textContent) companyIdForCopy = strongIdElement.textContent.trim();
            
            if (companyIdForCopy && copyCompanyIdButton) { 
                copyCompanyIdButton.dataset.companyIdToCopy = companyIdForCopy;
                copyCompanyIdButton.style.display = 'inline-flex';
                const newCopyBtn = copyCompanyIdButton.cloneNode(true);
                copyCompanyIdButton.parentNode.replaceChild(newCopyBtn, copyCompanyIdButton);
                newCopyBtn.addEventListener('click', function() { 
                    const idToCopy = this.dataset.companyIdToCopy;
                    if (idToCopy && navigator.clipboard) {
                        navigator.clipboard.writeText(idToCopy).then(() => {
                            const originalHTML = newCopyBtn.innerHTML; 
                            newCopyBtn.innerHTML = '<i class="fas fa-check"></i> Copied!'; newCopyBtn.disabled = true; 
                            setTimeout(() => { newCopyBtn.innerHTML = originalHTML; newCopyBtn.disabled = false; }, 2500);
                        });
                    }
                });
            } else if (copyCompanyIdButton) { copyCompanyIdButton.style.display = 'none'; }
        } else {
            modalTitle = "Success";
             if(copyCompanyIdButton) copyCompanyIdButton.style.display = 'none';
        }
        displayModal = true;
    } else if (errorFromUrl) {
        modalMessage = errorFromUrl;
        modalTitle = "Login Error";
        isModalError = true;
        displayModal = true;
        if(copyCompanyIdButton) copyCompanyIdButton.style.display = 'none';
    }

    if (autoLogoutMessageReason && pageErrorMessageDiv) {
        pageErrorMessageDiv.textContent = autoLogoutMessageReason;
        pageErrorMessageDiv.className = 'page-message info-message login-page-message'; 
        pageErrorMessageDiv.style.display = 'block';
    }


    if (displayModal && notificationModalLogin && notificationMessageDiv && modalTitleElem && okBtnModal && closeXModal && 
        typeof showModal === 'function' && typeof hideModal === 'function') {
        
        modalTitleElem.textContent = modalTitle;
        if (messageTypeFromUrl === 'signupSuccess' && successMessageFromUrl.includes("<")) { 
            notificationMessageDiv.innerHTML = modalMessage;
        } else {
            notificationMessageDiv.textContent = modalMessage;
        }
        
        const modalContent = notificationModalLogin.querySelector('.modal-content');
        if(modalContent) {
            modalContent.classList.remove('error-message-modal', 'success-message-modal');
            if(isModalError) modalContent.classList.add('error-message-modal');
            else if (messageTypeFromUrl !== 'logout') modalContent.classList.add('success-message-modal');
        }

        showModal(notificationModalLogin);
        const closeAndFocus = () => { hideModal(notificationModalLogin); setInitialFocus(); };
        
        const newOkBtnModal = okBtnModal.cloneNode(true);
        okBtnModal.parentNode.replaceChild(newOkBtnModal, okBtnModal);
        newOkBtnModal.addEventListener('click', closeAndFocus, {once: true});

        const newCloseXModal = closeXModal.cloneNode(true);
        closeXModal.parentNode.replaceChild(newCloseXModal, closeXModal);
        newCloseXModal.addEventListener('click', closeAndFocus, {once: true});

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

    setTimeout(() => {
        if (typeof clearUrlParams === 'function') {
            clearUrlParams(['error', 'message', 'companyIdentifier', 'adminEmail', 'msgType', 'reason']);
        }
    }, 300);

});