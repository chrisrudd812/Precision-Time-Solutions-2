// js/login.js - v16 (Added WebAuthn Fingerprint Support)
document.addEventListener('DOMContentLoaded', function() {

    function isBiometricSupported() {
        return window.PublicKeyCredential && 'credentials' in navigator;
    }

    async function authenticateWithBiometric() {
        try {
            // Create a temporary credential to trigger biometric
            const credential = await navigator.credentials.create({
                publicKey: {
                    challenge: new Uint8Array(32),
                    rp: { name: "Time Clock" },
                    user: {
                        id: new Uint8Array(16),
                        name: "temp",
                        displayName: "Temp User"
                    },
                    pubKeyCredParams: [{ alg: -7, type: "public-key" }],
                    authenticatorSelection: {
                        authenticatorAttachment: "platform",
                        userVerification: "required"
                    },
                    timeout: 60000
                }
            });
            return credential;
        } catch (error) {
            throw new Error('Biometric authentication failed');
        }
    }

    const companyIdentifierField = document.getElementById('companyIdentifier');
    const emailField = document.getElementById('email');
    const passwordField = document.getElementById('password');
    const loginForm = document.getElementById('loginForm');
    const notificationModalLogin = document.getElementById('notificationModal');
    const copyCompanyIdButton = document.getElementById('copyCompanyIdButton');
    const pageErrorMessageDiv = document.getElementById('loginErrorMessage');
    const autoLogoutInfoMessageDiv = document.getElementById('autoLogoutInfoMessage');
    const browserTimeZoneInput = document.getElementById('browserTimeZone');
    const passwordGroup = document.getElementById('passwordGroup');
    const isMobile = /Mobi|Android/i.test(navigator.userAgent);

    // PIN field is always visible and required
    passwordGroup.style.display = 'block';
    passwordField.required = true;

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
        // Skip auto-focus on mobile to prevent keyboard popup
        if (isMobile) return;
        
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
        loginForm.addEventListener('submit', async function(event) {
            event.preventDefault();
            
            const email = emailField.value.trim();
            const companyId = companyIdentifierField.value.trim();
            const submitButton = loginForm.querySelector('button[type="submit"]');
            
            if (!email || !companyId) {
                alert('Please enter Company ID and Email');
                return;
            }
            
            // Regular PIN submission
            if (!passwordField.value.trim()) {
                alert('Please enter your PIN');
                return;
            }
            
            // Regular login
            if (browserTimeZoneInput) {
                try {
                    browserTimeZoneInput.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
                } catch (e) {
                    browserTimeZoneInput.value = "Unknown"; 
                }
            }
            
            if (companyIdentifierField && companyIdentifierField.value.trim()) {
                localStorage.setItem('lastCompanyIdentifier', companyIdentifierField.value.trim());
            }
            
            submitButton.disabled = true;
            submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Logging In...';
            
            const originalPassword = passwordField.value;
            
            const hiddenPassword = document.createElement('input');
            hiddenPassword.type = 'hidden';
            hiddenPassword.name = 'password';
            hiddenPassword.value = originalPassword;
            loginForm.appendChild(hiddenPassword);
            
            loginForm.submit();
        });
     }
});