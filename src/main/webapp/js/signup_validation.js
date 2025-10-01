document.addEventListener('DOMContentLoaded', function() {

    let isFreePlan = false;
    let stripe, cardNumberElement;
    const appRoot = typeof appRootPath === 'string' ? appRootPath : "";
    
    const stripeValidationState = {
        cardNumber: false,
        cardExpiry: false,
        cardCvc: false
    };

    // --- HTML Element References ---
    const form = document.getElementById('companySignupForm');
    const clientSideErrorDiv = document.getElementById('card-errors');
    const mainSubmitButton = document.getElementById('mainSubmitButton');
    const originalSubmitButtonHTML = mainSubmitButton ? mainSubmitButton.innerHTML : '';
    const paymentSection = document.getElementById('payment-section');
    const promoCodeInput = document.getElementById('promoCodeInput');
    const validatePromoButton = document.getElementById('validatePromoButton');
    const promoStatusDiv = document.getElementById('promo-status');
    const appliedPromoCodeHiddenInput = document.getElementById('appliedPromoCode');
    const sameAsCompanyAddressCheckbox = document.getElementById('sameAsCompanyAddress');
    const companyPhoneInput = document.getElementById('companyPhone');
    const termsAgreementCheckbox = document.getElementById('termsAgreement');


    // --- Stripe Initialization ---
    function initializeStripe() {
        try {
            // *** MODIFICATION START ***
            // Check if the publishable key was passed from the JSP file.
            // If not, log an error and disable the payment form to prevent errors.
            if (typeof STRIPE_PUBLISHABLE_KEY === 'undefined' || STRIPE_PUBLISHABLE_KEY === 'null' || STRIPE_PUBLISHABLE_KEY === '') {
                displayClientSideError("Payment processing is currently unavailable. Please contact support.");
                return;
            }

            // Use the key provided by the server via the JSP file
            stripe = Stripe(STRIPE_PUBLISHABLE_KEY);
            // *** MODIFICATION END ***
            
            const elements = stripe.elements();
            const style = { base: { fontSize: '16px', color: '#32325d', '::placeholder': { color: '#aab7c4' } } };
            
            cardNumberElement = elements.create('cardNumber', { style: style });
            cardNumberElement.mount('#card-number');
            addStripeValidationListener(cardNumberElement, 'card-number', 'cardNumber');

            const cardExpiryElement = elements.create('cardExpiry', { style: style });
            cardExpiryElement.mount('#card-expiry');
            addStripeValidationListener(cardExpiryElement, 'card-expiry', 'cardExpiry');
            
            const cardCvcElement = elements.create('cardCvc', { style: style });
            cardCvcElement.mount('#card-cvc');
            addStripeValidationListener(cardCvcElement, 'card-cvc', 'cardCvc');

        } catch (e) {
            displayClientSideError("Payment processing is currently unavailable. Please contact support.");
        }
    }

    function addStripeValidationListener(element, containerId, fieldType) {
        const container = document.getElementById(containerId);
        if (!container) return;
        
        element.on('change', function(event) {
            const isValid = event.complete && !event.error;
            stripeValidationState[fieldType] = isValid;
            
            if (isValid) {
                container.classList.add('is-valid');
                container.classList.remove('is-invalid');
            } else if (event.error || (!event.empty && !isValid)) {
                container.classList.add('is-invalid');
                container.classList.remove('is-valid');
            } else {
                 container.classList.remove('is-valid', 'is-invalid');
            }
            checkFormCompletion();
        });
    }
    
    // --- Event Listeners ---
    if (form) form.addEventListener('submit', handleFormSubmit);
    if (validatePromoButton) validatePromoButton.addEventListener('click', handlePromoValidation);
    if (sameAsCompanyAddressCheckbox) sameAsCompanyAddressCheckbox.addEventListener('change', handleAddressCheckbox);
    if (promoCodeInput) promoCodeInput.addEventListener('input', () => { validatePromoButton.disabled = promoCodeInput.value.trim() === ''; });
    if (companyPhoneInput) {
        companyPhoneInput.addEventListener('input', (e) => formatPhoneNumber(e.target));
    }
    if (termsAgreementCheckbox) {
        termsAgreementCheckbox.addEventListener('change', checkFormCompletion);
    }
    if (form) {
        form.querySelectorAll('input[required], input[pattern], select[required]').forEach(field => {
            const handleValidation = () => {
                validateField(field);
                checkFormCompletion();
            };
            field.addEventListener('input', handleValidation);
            field.addEventListener('blur', handleValidation);
            if (field.tagName === 'SELECT') {
                field.addEventListener('change', handleValidation);
            }
        });
    }
    
    // --- Functions ---
    function checkFormCompletion() {
        if (!form || !mainSubmitButton || !termsAgreementCheckbox) return;

        let allRequiredFieldsValid = true;
        form.querySelectorAll('[required]').forEach(field => {
            if (!isFreePlan && paymentSection.contains(field)) {
                if (!field.checkValidity()) allRequiredFieldsValid = false;
            } else if (!paymentSection.contains(field)) {
                 if (!field.checkValidity()) allRequiredFieldsValid = false;
            }
        });

        let allStripeFieldsValid = true;
        if (!isFreePlan) {
            allStripeFieldsValid = Object.values(stripeValidationState).every(isValid => isValid);
        }

        const canSubmit = allRequiredFieldsValid && allStripeFieldsValid && termsAgreementCheckbox.checked;
        mainSubmitButton.disabled = !canSubmit;
    }

    function validateField(field) {
        if (!field) return true;
        if (!field.required && field.value.trim() === '') {
            field.classList.remove('is-valid', 'is-invalid');
            return true;
        }
        const isValid = field.checkValidity();
        if (isValid) {
            field.classList.add('is-valid');
            field.classList.remove('is-invalid');
        } else {
            field.classList.add('is-invalid');
            field.classList.remove('is-valid');
        }
        return isValid;
    }

    function handleAddressCheckbox() {
        const billingAddress = document.getElementById('billingAddress');
        const billingCity = document.getElementById('billingCity');
        const billingState = document.getElementById('billingState');
        const billingZip = document.getElementById('billingZip');

        if (this.checked) {
            billingAddress.value = document.getElementById('companyAddress').value;
            billingCity.value = document.getElementById('companyCity').value;
            billingState.value = document.getElementById('companyState').value;
            billingZip.value = document.getElementById('companyZip').value;

            validateField(billingAddress);
            validateField(billingCity);
            validateField(billingState);
            validateField(billingZip);
            
            // Check if all required billing fields are valid, then focus on card details
            if (billingAddress.checkValidity() && billingCity.checkValidity() && 
                billingState.checkValidity() && billingZip.checkValidity()) {
                if (cardNumberElement) {
                    cardNumberElement.focus();
                }
            }
        } else {
            billingAddress.value = '';
            billingCity.value = '';
            billingState.value = '';
            billingZip.value = '';
            validateField(billingAddress);
            validateField(billingCity);
            validateField(billingState);
            validateField(billingZip);
        }
        checkFormCompletion();
    }

    function formatPhoneNumber(element) {
        const digits = element.value.replace(/\D/g, '');
        let formatted = '';
        if (digits.length > 0) formatted = '(' + digits.substring(0, 3);
        if (digits.length >= 4) formatted += ') ' + digits.substring(3, 6);
        if (digits.length >= 7) formatted += '-' + digits.substring(6, 10);
        element.value = formatted;
    }

    async function handlePromoValidation() {
        const promoCode = promoCodeInput.value.trim();
        if (!promoCode) return;
        promoStatusDiv.textContent = 'Validating...';
        promoStatusDiv.className = 'promo-status validating';
        try {
            const response = await fetch(`${appRoot}/PromoCodeValidationServlet`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: `promoCode=${encodeURIComponent(promoCode)}`
            });
            const result = await response.json();
            if (result.valid) {
                promoStatusDiv.textContent = `Success! Code "${promoCode}" applied.`;
                promoStatusDiv.className = 'promo-status success';
                appliedPromoCodeHiddenInput.value = promoCode;
                if (result.type === 'LIFETIME' || promoCode.toLowerCase() === 'altman55') {
                    isFreePlan = true;
                    paymentSection.style.display = 'none';
                }
            } else {
                promoStatusDiv.textContent = result.error || 'Invalid promo code.';
                promoStatusDiv.className = 'promo-status error';
                appliedPromoCodeHiddenInput.value = '';
                isFreePlan = false;
                paymentSection.style.display = 'block';
            }
        } catch (e) {
            promoStatusDiv.textContent = 'Could not validate code. Check connection.';
            promoStatusDiv.className = 'promo-status error';
        }
        checkFormCompletion();
    }

    async function handleFormSubmit(event) {
        event.preventDefault();
        setButtonState(true);
        displayClientSideError('');

        let isFormValid = true;
        form.querySelectorAll('input[required], input[pattern], select[required]').forEach(field => {
            if (!validateField(field)) { isFormValid = false; }
        });

        if (!isFormValid) {
            form.reportValidity();
            setButtonState(false);
            return;
        }

        if (document.getElementById('adminPassword').value !== document.getElementById('adminConfirmPassword').value) {
            displayClientSideError("Passwords do not match.");
            setButtonState(false);
            return;
        }

        const formData = new FormData(form);

        if (isFreePlan) {
            const paymentFields = Array.from(paymentSection.querySelectorAll('input, select'));
            paymentFields.forEach(el => el.disabled = true);
            const formDataFree = new FormData(form);
            paymentFields.forEach(el => el.disabled = false);
            await submitDataToServer(formDataFree);
        } else {
            // Check if stripe object is initialized before using it
            if (!stripe) {
                displayClientSideError("Payment system is not initialized. Please check your connection or contact support.");
                setButtonState(false);
                return;
            }
            const { paymentMethod, error } = await stripe.createPaymentMethod({
                type: 'card',
                card: cardNumberElement,
                billing_details: { name: formData.get('cardholderName') }
            });

            if (error) {
                displayClientSideError(error.message);
                setButtonState(false);
            } else {
                formData.set('stripePaymentMethodId', paymentMethod.id);
                await submitDataToServer(formData);
            }
        }
    }

    async function submitDataToServer(formData) {
        try {
            const response = await fetch(`${appRoot}/SignupServlet`, {
                method: 'POST',
                body: new URLSearchParams(formData)
            });
            
            let data;
            try {
                data = await response.json();
            } catch (jsonError) {
                displayClientSideError('Server returned an invalid response. Please try again.');
                setButtonState(false);
                return;
            }

            if (data.action_required) {
                const { error: scaError } = await stripe.handleNextAction({ clientSecret: data.client_secret });
                if (scaError) {
                    displayClientSideError(scaError.message); 
                    setButtonState(false);
                } else {
                    window.location.href = data.redirect_url_after_sca;
                }
            } else if (data.success) {
                window.location.href = data.redirect_url;
            } else {
                displayClientSideError(data.error || 'An unknown server error occurred.');
                setButtonState(false);
            }
        } catch (e) {
            displayClientSideError('A network error occurred. Please try again.');
            setButtonState(false);
        }
    }
    
    function setButtonState(isLoading, text) {
        if (!mainSubmitButton) return;
        mainSubmitButton.disabled = isLoading;
        mainSubmitButton.innerHTML = isLoading ? (text || '<i class="fas fa-spinner fa-spin"></i> Processing...') : originalSubmitButtonHTML;
    }

    function displayClientSideError(message) {
        if (!message) return;
        
        // Use the confirmation modal with error styling
        const modal = document.getElementById('confirmModalGeneral');
        const messageElement = document.getElementById('confirmModalGeneralMessage');
        const okBtn = document.getElementById('confirmModalGeneralOkBtn');
        const cancelBtn = document.getElementById('confirmModalGeneralCancelBtn');
        const modalContent = modal ? modal.querySelector('.modal-content') : null;
        const titleElement = modal ? modal.querySelector('.modal-header h2') : null;
        
        if (modal && messageElement && okBtn && modalContent) {
            // Add error state styling
            modalContent.classList.remove('modal-state-warning');
            modalContent.classList.add('modal-state-error');
            
            // Change title to Error
            if (titleElement) {
                titleElement.innerHTML = '<i class="fas fa-exclamation-circle"></i> <span>Error</span>';
            }
            
            // Set error message and hide cancel button
            messageElement.textContent = message;
            cancelBtn.style.display = 'none';
            okBtn.textContent = 'OK';
            okBtn.className = 'glossy-button text-red';
            
            modal.classList.add('modal-visible');
            
            okBtn.onclick = function() {
                modal.classList.remove('modal-visible');
                cancelBtn.style.display = 'inline-block';
                modalContent.classList.remove('modal-state-error');
                modalContent.classList.add('modal-state-warning');
            };
            
            modal.onclick = function(event) {
                if (event.target === modal) {
                    modal.classList.remove('modal-visible');
                    cancelBtn.style.display = 'inline-block';
                    modalContent.classList.remove('modal-state-error');
                    modalContent.classList.add('modal-state-warning');
                }
            };
        } else {
            alert('Payment Error: ' + message);
        }
    }
    
    // --- Initializers ---
    document.getElementById('browserTimeZoneIdField').value = Intl.DateTimeFormat().resolvedOptions().timeZone;
    if (paymentSection) initializeStripe();
    if (promoCodeInput) {
        validatePromoButton.disabled = promoCodeInput.value.trim() === '';
    }
    if (form) {
        form.querySelectorAll('[required]').forEach(field => {
            validateField(field);
        });
        
        if (!isFreePlan) {
            document.getElementById('card-number').classList.add('is-invalid');
            document.getElementById('card-expiry').classList.add('is-invalid');
            document.getElementById('card-cvc').classList.add('is-invalid');
        }

        checkFormCompletion();
    }
});