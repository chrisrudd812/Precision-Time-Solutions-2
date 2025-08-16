// signup_validation.js (v15.1 - Final Fix)
document.addEventListener('DOMContentLoaded', function() {
    console.log("signup_validation.js (v15.1) - DOMContentLoaded Initializing...");

    // --- Global Flags and State Variables ---
    let isLifetimePromoApplied = false;
    let stripe, cardNumberElement, cardExpiryElement, cardCvcElement;
    const appRoot = typeof appRootPath === 'string' ? appRootPath : "";

    // --- HTML Element References ---
    const form = document.getElementById('companySignupForm');
    const clientSideErrorDiv = document.getElementById('card-errors');
    const mainSubmitButton = document.getElementById('mainSubmitButton');
    const originalSubmitButtonHTML = mainSubmitButton ? mainSubmitButton.innerHTML : 'Create Account <i class="fas fa-arrow-right"></i>';

    const companyNameInput = document.getElementById('companyName');
    const companyAddressInput = document.getElementById('companyAddress');
    const companyCityInput = document.getElementById('companyCity');
    const companyStateInput = document.getElementById('companyState');
    const companyZipInput = document.getElementById('companyZip');
    const adminFirstNameInput = document.getElementById('adminFirstName');
    const adminLastNameInput = document.getElementById('adminLastName');
    const adminEmailInput = document.getElementById('adminEmail');
    const passwordInput = document.getElementById('adminPassword');
    const confirmPasswordInput = document.getElementById('adminConfirmPassword');
    const cardholderNameInput = document.getElementById('cardholderName');
    const billingAddressInput = document.getElementById('billingAddress');
    const billingCityInput = document.getElementById('billingCity');
    const billingStateSelect = document.getElementById('billingState');
    const billingZipInput = document.getElementById('billingZip');
    const acceptTermsCheckbox = document.getElementById('acceptTerms');
    const copyAddressCheckbox = document.getElementById('copyCompanyAddress');
    
    const usePromoCodeCheckbox = document.getElementById('usePromoCodeCheckbox');
    const promoCodeEntryDiv = document.getElementById('promoCodeEntry');
    const promoCodeInput = document.getElementById('promoCodeInput');
    const applyPromoCodeButton = document.getElementById('applyPromoCodeButton');
    const promoCodeStatusDiv = document.getElementById('promoCodeStatus');
    const appliedPromoCodeHiddenInput = document.getElementById('appliedPromoCodeInput');
    const creditCardPaymentSection = document.getElementById('creditCardPaymentSection');
    const billingAddressSection = document.getElementById('billingAddressSection');

    // --- Utility Functions ---
    function populateTimeZone() {
        try {
            const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
            const field = document.getElementById('browserTimeZoneIdField');
            if (field) field.value = browserTimeZone;
        } catch (e) { console.warn("Timezone detection failed:", e); }
    }

    function displayClientSideError(message, fieldToFocus = null) {
        if(clientSideErrorDiv) {
            clientSideErrorDiv.textContent = message;
            clientSideErrorDiv.style.display = 'block';
        } else {
            alert(message);
        }
        if(fieldToFocus) {
            try {
                fieldToFocus.focus();
                if (fieldToFocus.classList) fieldToFocus.classList.add('is-invalid');
            } catch(e){}
        }
    }
    
    function validateSignupFormBasic() {
        document.querySelectorAll('.signup-form .is-invalid').forEach(el => {
            el.classList.remove('is-invalid');
        });

        const requiredFields = [
            { el: companyNameInput, name: "Company Name" },
            { el: adminFirstNameInput, name: "Admin First Name" },
            { el: adminLastNameInput, name: "Admin Last Name" },
            { el: adminEmailInput, name: "Admin Email" },
            { el: passwordInput, name: "Password" },
            { el: confirmPasswordInput, name: "Confirm Password" }
        ];
        if (!isLifetimePromoApplied) {
            requiredFields.push(
                { el: cardholderNameInput, name: "Cardholder Name" },
                { el: billingAddressInput, name: "Billing Address" },
                { el: billingCityInput, name: "Billing City" },
                { el: billingStateSelect, name: "Billing State" },
                { el: billingZipInput, name: "Billing Zip" }
            );
        }
        for (const field of requiredFields) {
            if (!field.el || !field.el.value.trim()) {
                displayClientSideError(`${field.name} is a required field.`, field.el);
                return false;
            }
        }
        if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(adminEmailInput.value.trim())) {
            displayClientSideError("Please enter a valid email address.", adminEmailInput);
            return false;
        }
        if (passwordInput.value.length < 8) {
            displayClientSideError("Password must be at least 8 characters long.", passwordInput);
            return false;
        }
        if (passwordInput.value !== confirmPasswordInput.value) {
            displayClientSideError("Passwords do not match.", confirmPasswordInput);
            return false;
        }
        if (!acceptTermsCheckbox.checked) {
            displayClientSideError("You must accept the terms of service to continue.", acceptTermsCheckbox);
            return false;
        }
        return true;
    }

    function setButtonState(disabled, htmlContent = null) {
         if (mainSubmitButton) {
            mainSubmitButton.disabled = disabled;
            mainSubmitButton.innerHTML = disabled ? (htmlContent || '<i class="fas fa-spinner fa-spin"></i> Processing...') : originalSubmitButtonHTML;
        }
    }

    function disablePaymentFields() {
        isLifetimePromoApplied = true;
        if (creditCardPaymentSection) creditCardPaymentSection.classList.add('payment-section-visually-disabled');
        if (billingAddressSection) billingAddressSection.classList.add('payment-section-visually-disabled');
        document.querySelectorAll('#creditCardPaymentSection input, #creditCardPaymentSection select, #billingAddressSection input, #billingAddressSection select').forEach(el => {
            el.disabled = true;
            el.required = false;
        });
    }

    function enablePaymentFields() {
        isLifetimePromoApplied = false;
        if (creditCardPaymentSection) creditCardPaymentSection.classList.remove('payment-section-visually-disabled');
        if (billingAddressSection) billingAddressSection.classList.remove('payment-section-visually-disabled');
        document.querySelectorAll('#creditCardPaymentSection input, #creditCardPaymentSection select, #billingAddressSection input, #billingAddressSection select').forEach(el => {
            el.disabled = false;
        });
        if(cardholderNameInput) cardholderNameInput.required = true;
        if(billingAddressInput) billingAddressInput.required = true;
        if(billingCityInput) billingCityInput.required = true;
        if(billingStateSelect) billingStateSelect.required = true;
        if(billingZipInput) billingZipInput.required = true;
    }

    function togglePromoUI(enabled) {
        if (promoCodeEntryDiv) promoCodeEntryDiv.style.display = enabled ? 'block' : 'none';
        if (promoCodeInput) promoCodeInput.disabled = !enabled;
        if (applyPromoCodeButton) applyPromoCodeButton.disabled = !enabled;
        if (!enabled) {
            if (promoCodeInput) promoCodeInput.value = '';
            if (promoCodeStatusDiv) promoCodeStatusDiv.textContent = '';
            if (appliedPromoCodeHiddenInput) appliedPromoCodeHiddenInput.value = '';
            if (isLifetimePromoApplied) {
                enablePaymentFields();
            }
        } else {
             if(promoCodeInput) promoCodeInput.focus();
        }
    }
    
    if (copyAddressCheckbox) {
        const syncBillingAddress = () => {
            if (copyAddressCheckbox.checked) {
                billingAddressInput.value = companyAddressInput.value;
                billingCityInput.value = companyCityInput.value;
                billingStateSelect.value = companyStateInput.value;
                billingZipInput.value = companyZipInput.value;
            }
        };
        copyAddressCheckbox.addEventListener('change', syncBillingAddress);
        [companyAddressInput, companyCityInput, companyStateInput, companyZipInput].forEach(input => {
            input.addEventListener('input', syncBillingAddress);
        });
        syncBillingAddress();
    }
    
    if (usePromoCodeCheckbox) {
        togglePromoUI(usePromoCodeCheckbox.checked);
        usePromoCodeCheckbox.addEventListener('change', function() {
            togglePromoUI(this.checked);
        });
    }

    if (applyPromoCodeButton) {
        applyPromoCodeButton.addEventListener('click', async function() {
            const code = promoCodeInput.value.trim();
            promoCodeStatusDiv.textContent = '';
            appliedPromoCodeHiddenInput.value = '';
            enablePaymentFields();
            if (!code) { promoCodeStatusDiv.textContent = 'Please enter a promo code.'; promoCodeStatusDiv.style.color = 'red'; return; }
            applyPromoCodeButton.disabled = true;
            applyPromoCodeButton.textContent = 'Verifying...';
            try {
                const response = await fetch(`${appRoot}/PromoCodeValidationServlet`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    body: new URLSearchParams({ promoCode: code })
                });
                const result = await response.json();
                if (result.valid) {
                    promoCodeStatusDiv.style.color = 'green';
                    appliedPromoCodeHiddenInput.value = code;
                    if (result.type === 'LIFETIME') {
                        promoCodeStatusDiv.textContent = 'Lifetime access promo applied!';
                        disablePaymentFields();
                    } else if (result.type === 'TRIAL') {
                        promoCodeStatusDiv.textContent = `Valid trial code applied!`;
                    } else {
                         promoCodeStatusDiv.textContent = `Valid discount code applied!`;
                    }
                } else {
                    promoCodeStatusDiv.style.color = 'red';
                    promoCodeStatusDiv.textContent = result.error || 'Invalid promo code.';
                }
            } catch (error) {
                console.error('Error validating promo code:', error);
                promoCodeStatusDiv.style.color = 'red';
                promoCodeStatusDiv.textContent = 'Could not verify code. Please try again.';
            } finally {
                applyPromoCodeButton.disabled = false;
                applyPromoCodeButton.textContent = 'Apply';
            }
        });
    }

    if(window.stripePublishableKey) {
        stripe = Stripe(window.stripePublishableKey);
        const elements = stripe.elements();
        const style = { base: { fontSize: '16px' }};
        cardNumberElement = elements.create('cardNumber', {style});
        cardNumberElement.mount('#card-number-element');
        cardExpiryElement = elements.create('cardExpiry', {style});
        cardExpiryElement.mount('#card-expiry-element');
        cardCvcElement = elements.create('cardCvc', {style});
        cardCvcElement.mount('#card-cvc-element');
    }

    if (form) {
        form.addEventListener('submit', async function (event) {
            event.preventDefault();
            setButtonState(true, '<i class="fas fa-spinner fa-spin"></i> Validating...');
            clientSideErrorDiv.style.display = 'none';

            if (!validateSignupFormBasic()) {
                setButtonState(false);
                return;
            }

            const currentFormData = new FormData(form);
            
            if (isLifetimePromoApplied) {
                submitDataToServer(currentFormData);
            } else {
                if (!stripe || !cardNumberElement) { displayClientSideError("Payment fields not ready."); setButtonState(false); return; }
                
                setButtonState(true, '<i class="fas fa-spinner fa-spin"></i> Processing Payment...');
                
                const { paymentMethod, error: pmError } = await stripe.createPaymentMethod({
                    type: 'card',
                    card: cardNumberElement,
                    billing_details: { 
                        name: cardholderNameInput.value.trim(),
                        email: adminEmailInput.value.trim(),
                        address: {
                            line1: billingAddressInput.value.trim(),
                            city: billingCityInput.value.trim(),
                            state: billingStateSelect.value,
                            postal_code: billingZipInput.value.trim()
                        }
                    }
                });

                if (pmError) {
                    displayClientSideError(pmError.message);
                    setButtonState(false);
                    return;
                }
                
                currentFormData.set('stripePaymentMethodId', paymentMethod.id);
                submitDataToServer(currentFormData, paymentMethod); 
            }
        });
    }

    async function submitDataToServer(formData, paymentMethodForSca = null) {
        setButtonState(true);
        try {
            const servletUrl = `${appRoot}/${form.getAttribute('action')}`;
            const response = await fetch(servletUrl, { 
                method: 'POST', 
                body: new URLSearchParams(formData) 
            });

            const data = await response.json();

            if (!response.ok) {
                displayClientSideError(data.error || 'An unknown server error occurred.');
                setButtonState(false);
                return;
            }

            if (data.action_required) {
                setButtonState(true, '<i class="fas fa-spinner fa-spin"></i> Authenticating...');
                const { paymentIntent, error: confirmError } = await stripe.confirmCardPayment(data.client_secret, {
                    payment_method: paymentMethodForSca.id
                });
                if (confirmError) { displayClientSideError(confirmError.message); setButtonState(false); return; }
                
                setButtonState(true, '<i class="fas fa-spinner fa-spin"></i> Finalizing...');
                
                const finalizeResponse = await fetch(servletUrl, { 
                    method: 'POST', 
                    body: new URLSearchParams({
                        action: 'finalizeSubscription',
                        payment_intent_id: paymentIntent.id,
                        stripe_subscription_id: data.subscription_id || ''
                    }) 
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
                displayClientSideError(data.error || 'An unknown error occurred.');
                setButtonState(false);
            }
        } catch (e) {
            console.error("Error in submitDataToServer:", e);
            displayClientSideError('A network error occurred. Please try again.');
            setButtonState(false);
        }
    }
    
    populateTimeZone();
});