// js/login.js - v13 (Timezone capture and refined message/focus handling)
document.addEventListener('DOMContentLoaded', function() {
    console.log("login.js loaded (v13 - Timezone Capture)");

    const companyIdentifierField = document.getElementById('companyIdentifier');
    const emailField = document.getElementById('email');
    const passwordField = document.getElementById('password');
    const loginForm = document.getElementById('loginForm');
    const notificationModalLogin = document.getElementById('notificationModal');
    const copyCompanyIdButton = document.getElementById('copyCompanyIdButton');
    const pageErrorMessageDiv = document.getElementById('loginErrorMessage');
    const autoLogoutInfoMessageDiv = document.getElementById('autoLogoutInfoMessage'); // For auto-logout messages
    const browserTimeZoneInput = document.getElementById('browserTimeZone'); // Hidden input for timezone

    const urlParams = new URLSearchParams(window.location.search);
    const companyIdFromUrl = urlParams.get('companyIdentifier');
    const adminEmailFromUrl = urlParams.get('adminEmail'); // Used if redirected from signup with adminEmail
    const successMessageFromUrl = urlParams.get('message');
    const errorFromUrl = urlParams.get('error'); 
    const messageTypeFromUrl = urlParams.get('msgType'); // 'logout', 'signupSuccess'
    const autoLogoutMessageReason = urlParams.get('reason'); // For auto-logout specific messages

    // --- Pre-fill Company Identifier ---
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
    // Pre-fill admin email if coming from signup
    if (adminEmailFromUrl && emailField && emailField.value === "") {
        emailField.value = adminEmailFromUrl;
    }


    function setInitialFocus() {
        const isModalVisible = notificationModalLogin && notificationModalLogin.classList.contains('modal-visible');
        if (isModalVisible) {
            const okButtonInModal = notificationModalLogin.querySelector('#okButtonNotificationModal');
            if (okButtonInModal) { okButtonInModal.focus(); return; }
        }
        // Focus priority: empty CompanyID -> empty Email -> PIN
        if (companyIdentifierField && companyIdentifierField.value.trim() === '') { companyIdentifierField.focus(); } 
        else if (emailField && emailField.value.trim() === '') { emailField.focus(); } 
        else if (passwordField) { passwordField.focus(); }
    }
    
    // --- Message Handling (Notification Modal or Inline) ---
    const notificationMessageDiv = notificationModalLogin ? notificationModalLogin.querySelector('#notificationMessage') : null;
    const modalTitleElem = notificationModalLogin ? notificationModalLogin.querySelector('h2#notificationModalTitle') : null;
    const okBtnModal = notificationModalLogin ? notificationModalLogin.querySelector('#okButtonNotificationModal') : null;
    const closeXModal = notificationModalLogin ? notificationModalLogin.querySelector('span.close#closeNotificationModal') : null;

    let displayModal = false;
    let modalMessage = "";
    let modalTitle = "Notification";
    let isModalError = false; // Differentiates between success/info and error styling for modal

    if (successMessageFromUrl) {
        modalMessage = successMessageFromUrl;
        if (messageTypeFromUrl === 'logout') {
            modalTitle = "Logout Successful";
        } else if (messageTypeFromUrl === 'signupSuccess' || successMessageFromUrl.toLowerCase().includes("account created")) {
             modalTitle = "Account Creation Successful!";
             // Logic for 'Copy Company ID' button
            let companyIdForCopy = "";
            const tempDiv = document.createElement('div'); tempDiv.innerHTML = successMessageFromUrl; // Parse HTML from message
            const strongIdElement = tempDiv.querySelector('#copyCompanyIdValue'); // ID from servlet message
            if (strongIdElement && strongIdElement.textContent) companyIdForCopy = strongIdElement.textContent.trim();
            
            if (companyIdForCopy && copyCompanyIdButton) { 
                copyCompanyIdButton.dataset.companyIdToCopy = companyIdForCopy;
                copyCompanyIdButton.style.display = 'inline-flex'; // Show button
                const newCopyBtn = copyCompanyIdButton.cloneNode(true); // Re-attach listener
                copyCompanyIdButton.parentNode.replaceChild(newCopyBtn, copyCompanyIdButton);
                newCopyBtn.addEventListener('click', function() { 
                    const idToCopy = this.dataset.companyIdToCopy;
                    if (idToCopy && navigator.clipboard) {
                        navigator.clipboard.writeText(idToCopy).then(() => {
                            const originalHTML = newCopyBtn.innerHTML; 
                            newCopyBtn.innerHTML = '<i class="fas fa-check"></i> Copied!'; newCopyBtn.disabled = true; 
                            setTimeout(() => { newCopyBtn.innerHTML = originalHTML; newCopyBtn.disabled = false; }, 2500);
                        }).catch(err => { console.error('Copy failed:', err); alert('Failed to copy Company ID.'); });
                    } else if (!navigator.clipboard) { alert('Clipboard API not available in this browser.'); }
                });
            } else if (copyCompanyIdButton) { copyCompanyIdButton.style.display = 'none'; }
        } else { // Generic success message
            modalTitle = "Success";
             if(copyCompanyIdButton) copyCompanyIdButton.style.display = 'none';
        }
        displayModal = true;
    } else if (errorFromUrl) { // Error messages from redirects
        modalMessage = errorFromUrl;
        modalTitle = "Login Error";
        isModalError = true;
        displayModal = true;
        if(copyCompanyIdButton) copyCompanyIdButton.style.display = 'none';
    }

    if (autoLogoutMessageReason && pageErrorMessageDiv) { // For auto-logout messages shown inline
        pageErrorMessageDiv.textContent = decodeHtmlEntities(autoLogoutMessageReason);
        pageErrorMessageDiv.className = 'page-message info-message login-page-message'; 
        pageErrorMessageDiv.style.display = 'block';
    }


    if (displayModal && notificationModalLogin && notificationMessageDiv && modalTitleElem && okBtnModal && closeXModal && 
        typeof showModal === 'function' && typeof hideModal === 'function') {
        
        modalTitleElem.textContent = modalTitle;
        // Use innerHTML if message might contain HTML (like the signup success with strong tag)
        if (messageTypeFromUrl === 'signupSuccess' && successMessageFromUrl.includes("<")) { 
            notificationMessageDiv.innerHTML = modalMessage; // Allows HTML from servlet message
        } else {
            notificationMessageDiv.textContent = modalMessage; // Plain text for others
        }
        
        const modalContent = notificationModalLogin.querySelector('.modal-content');
        if(modalContent) { // Apply error/success styling to modal content
            modalContent.classList.remove('error-message-modal', 'success-message-modal'); // Use distinct classes
            if(isModalError) modalContent.classList.add('error-message-modal');
            else if (messageTypeFromUrl !== 'logout') modalContent.classList.add('success-message-modal');
        }

        showModal(notificationModalLogin);
        const closeAndFocus = () => { hideModal(notificationModalLogin); setInitialFocus(); };
        
        // Re-attach listeners to new buttons if common-scripts doesn't handle data-close-modal globally
        const newOkBtnModal = okBtnModal.cloneNode(true);
        okBtnModal.parentNode.replaceChild(newOkBtnModal, okBtnModal);
        newOkBtnModal.addEventListener('click', closeAndFocus, {once: true});

        const newCloseXModal = closeXModal.cloneNode(true);
        closeXModal.parentNode.replaceChild(newCloseXModal, closeXModal);
        newCloseXModal.addEventListener('click', closeAndFocus, {once: true});

    } else if (displayModal) { 
        // Fallback alert if modal elements are missing (should not happen ideally)
        alert((isModalError ? "Error: " : (modalTitle + ":\n")) + modalMessage.replace(/<[^>]*>/g, ''));
    }
    
    setInitialFocus(); // Call once after potential modal display

    if (loginForm) { 
        loginForm.addEventListener('submit', function(event) {
            // Capture and set browser timezone
            if (browserTimeZoneInput) {
                try {
                    browserTimeZoneInput.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
                } catch (e) {
                    console.warn("Could not determine browser timezone on submit:", e);
                    browserTimeZoneInput.value = "Unknown"; 
                }
            }
            // Save company ID on successful submission attempt
            if (companyIdentifierField && companyIdentifierField.value.trim()) {
                localStorage.setItem('lastCompanyIdentifier', companyIdentifierField.value.trim());
            }
            const submitButton = loginForm.querySelector('button[type="submit"]');
            if(submitButton){ submitButton.disabled = true; submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Logging In...'; }
            // Allow form to submit naturally
        });
     }

    // Clean URL parameters after a short delay to allow any other scripts to read them if needed
    setTimeout(() => {
        if (typeof clearUrlParams === 'function') {
            clearUrlParams(['error', 'message', 'companyIdentifier', 'adminEmail', 'msgType', 'autoLogoutMessage', 'reason']);
        }
    }, 300); // Increased delay slightly

    console.log("login.js loaded END (v13)");
});