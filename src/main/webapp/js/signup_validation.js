document.addEventListener('DOMContentLoaded', function() {
    console.log("signup_validation.js - v22 FINAL UNABRIDGED");

    let isFreePlan = false;
    let stripe, cardNumberElement;
    const appRoot = typeof appRootPath === 'string' ? appRootPath : "";

    // --- HTML Element References ---
    const form = document.getElementById('companySignupForm');
    const clientSideErrorDiv = document.getElementById('card-errors');
    const mainSubmitButton = document.getElementById('mainSubmitButton');
    const originalSubmitButtonHTML = mainSubmitButton.innerHTML;
    const paymentSection = document.getElementById('payment-section');
    const paymentFields = Array.from(paymentSection.querySelectorAll('input, select'));
    const promoCodeInput = document.getElementById('promoCodeInput');
    const validatePromoButton = document.getElementById('validatePromoButton');
    const promoStatusDiv = document.getElementById('promo-status');
    const appliedPromoCodeHiddenInput = document.getElementById('appliedPromoCode');
    const sameAsCompanyAddressCheckbox = document.getElementById('sameAsCompanyAddress');
    const companyPhoneInput = document.getElementById('companyPhone');
    const companyZipInput = document.getElementById('companyZip');
    const billingZipInput = document.getElementById('billingZip');
    const adminEmailInput = document.getElementById('adminEmail');

    // --- Stripe Initialization ---
    function initializeStripe() {
        try {
            stripe = Stripe('pk_test_51RUHnpBtvyYfb2KWE9qJPWYzUdwurEUDf8W1VtxuV16ZJj8eJtCS8ubiNZI1W3XNikJa8XjjbiKp9f3dkzXRabkm009fB33jMV');
            const elements = stripe.elements();
            const style = { base: { fontSize: '16px', color: '#32325d', '::placeholder': { color: '#aab7c4' } } };
            cardNumberElement = elements.create('cardNumber', { style: style });
            cardNumberElement.mount('#card-number');
            const cardExpiryElement = elements.create('cardExpiry', { style: style });
            cardExpiryElement.mount('#card-expiry');
            const cardCvcElement = elements.create('cardCvc', { style: style });
            cardCvcElement.mount('#card-cvc');
        } catch (e) {
            console.error("Stripe.js has not loaded yet.");
        }
    }
    
    // --- Event Listeners ---
    if (form) form.addEventListener('submit', handleFormSubmit);
    if (validatePromoButton) validatePromoButton.addEventListener('click', handlePromoValidation);
    if (sameAsCompanyAddressCheckbox) sameAsCompanyAddressCheckbox.addEventListener('change', handleAddressCheckbox);
    if (promoCodeInput) promoCodeInput.addEventListener('input', handlePromoInput);
    if (companyPhoneInput) companyPhoneInput.addEventListener('input', (e) => formatAndValidate(e.target, formatPhoneNumber, (val) => val.length === 14 || val.length === 0));
    if (companyZipInput) companyZipInput.addEventListener('input', (e) => formatAndValidate(e.target, formatZipCode, (val) => val.length === 5 || val.length === 0));
    if (billingZipInput) billingZipInput.addEventListener('input', (e) => formatAndValidate(e.target, formatZipCode, (val) => val.length === 5 || val.length === 0));
    if (adminEmailInput) adminEmailInput.addEventListener('blur', (e) => formatAndValidate(e.target, null, validateEmail));

    // --- Functions ---
    function handlePromoInput() {
        validatePromoButton.disabled = this.value.trim() === '';
    }

    function handleAddressCheckbox() {
        if (this.checked) {
            document.getElementById('billingAddress').value = document.getElementById('companyAddress').value;
            document.getElementById('billingCity').value = document.getElementById('companyCity').value;
            document.getElementById('billingState').value = document.getElementById('companyState').value;
            document.getElementById('billingZip').value = document.getElementById('companyZip').value;
        }
    }
    
    function formatAndValidate(element, formatter, validator) {
        if (formatter) formatter(element);
        const isValid = validator(element.value);
        element.setCustomValidity(isValid ? '' : 'Invalid format.');
        element.classList.toggle('is-invalid', !isValid && element.value.length > 0);
    }

    function formatPhoneNumber(element) {
        let input = element.value.replace(/\D/g, '').substring(0, 10);
        if (input.length === 0) { element.value = ''; return; }
        let areaCode = input.substring(0, 3);
        let middle = input.substring(3, 6);
        let last = input.substring(6, 10);
        if (input.length > 6) { element.value = `(${areaCode}) ${middle}-${last}`; } 
        else if (input.length > 3) { element.value = `(${areaCode}) ${middle}`; } 
        else { element.value = `(${areaCode}`; }
    }

    function formatZipCode(element) {
        element.value = element.value.replace(/\D/g, '').substring(0, 5);
    }

    function validateEmail(email) {
        if (email.trim() === '') return true;
        const re = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/;
        return re.test(String(email).toLowerCase());
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
                } else {
                    isFreePlan = false;
                    paymentSection.style.display = 'block';
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
    }

    async function handleFormSubmit(event) {
        event.preventDefault();
        setButtonState(true);
        displayClientSideError('');

        if (isFreePlan) {
            paymentFields.forEach(el => el.disabled = true);
        }

        if (!form.checkValidity()) {
            if (isFreePlan) paymentFields.forEach(el => el.disabled = false);
            form.reportValidity();
            setButtonState(false);
            return;
        }
        
        if (isFreePlan) paymentFields.forEach(el => el.disabled = false);

        if (document.getElementById('adminPassword').value !== document.getElementById('adminConfirmPassword').value) {
            displayClientSideError("Passwords do not match.");
            setButtonState(false);
            return;
        }

        const formData = new FormData(form);

        if (isFreePlan) {
            await submitDataToServer(formData);
        } else {
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
            
            const data = await response.json();

            if (data.action_required) {
                const { error: scaError, paymentIntent } = await stripe.handleNextAction({ clientSecret: data.client_secret });
                if (scaError) {
                    displayClientSideError(scaError.message); 
                    setButtonState(false); 
                    return;
                }
                setButtonState(true, 'Finalizing...');
                const finalizeResponse = await fetch(`${appRoot}/SignupServlet`, { 
                    method: 'POST', 
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ action: 'finalizeSubscription', payment_intent_id: paymentIntent.id }) 
                });
                const finalizeResult = await finalizeResponse.json();
                if (finalizeResult.success) {
                    window.location.href = finalizeResult.redirect_url;
                } else {
                    displayClientSideError(finalizeResult.error || 'Failed to finalize payment.'); 
                    setButtonState(false);
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
        if (!clientSideErrorDiv) return;
        clientSideErrorDiv.textContent = message;
        clientSideErrorDiv.style.display = message ? 'block' : 'none';
    }
    
    // --- Initializers ---
    document.getElementById('browserTimeZoneIdField').value = Intl.DateTimeFormat().resolvedOptions().timeZone;
    initializeStripe();
    if (promoCodeInput) {
        handlePromoInput.call(promoCodeInput);
    }
});