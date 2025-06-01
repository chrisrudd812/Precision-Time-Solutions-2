document.addEventListener('DOMContentLoaded', function() {
    console.log("signup_validation.js loaded (v7.4 - MINIMAL PayPal HF Eligibility Test)");

    const form = document.getElementById('companySignupForm');
    const submitButton = form ? form.querySelector('button[type="submit"]') : null;
    const clientSideErrorDiv = document.getElementById('paypal-card-errors'); 

    // --- Minimal populateTimeZone ---
    function populateTimeZone() {
        try {
            const browserTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone;
            const field = document.getElementById('browserTimeZoneIdField');
            if (field) {
                field.value = browserTimeZone;
                console.log("Timezone populated: " + browserTimeZone);
            } else { console.warn("Timezone field not found."); }
        } catch (e) { console.warn("Timezone detection failed:", e); }
    }
    populateTimeZone();
    // --- End Minimal populateTimeZone ---

    function displayMinimalError(message) {
        if (clientSideErrorDiv) {
            clientSideErrorDiv.textContent = message;
            clientSideErrorDiv.style.display = 'block';
            clientSideErrorDiv.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } else {
            alert(message); 
        }
        if (submitButton) submitButton.disabled = true;
    }


    // --- MINIMAL PayPal Hosted Fields Logic ---
    let hostedFieldsInstanceMinimal; 

    function initializeMinimalPayPalHF() {
        console.log("MINIMAL TEST: Checking PayPal SDK availability...");
        if (window.paypal) {
            console.log("MINIMAL TEST: window.paypal IS available.");
            if (window.paypal.HostedFields) {
                console.log("MINIMAL TEST: window.paypal.HostedFields IS available.");
                if (window.paypal.HostedFields.isEligible()) {
                    console.log("MINIMAL TEST: PayPal Hosted Fields ARE eligible. Calling render()...");
                    
                    paypal.HostedFields.render({
                        styles: { 
                            'input': { 'font-size': '16px', 'font-family': 'inherit', 'color': '#333', 'padding': '10px 12px' },
                            ':focus': { 'color': 'black' },
                            '.valid': { 'color': '#28a745' },
                            '.invalid': { 'color': '#dc3545' }
                        },
                        fields: { 
                            number: { selector: '#paypal-card-number', placeholder: 'Card Number' },
                            cvv:    { selector: '#paypal-card-cvv',    placeholder: 'CVV' },
                            expirationDate: { selector: '#paypal-card-expiry', placeholder: 'MM/YY' }
                        }
                    }).then(function (instance) {
                        hostedFieldsInstanceMinimal = instance; 
                        console.log("MINIMAL TEST: PayPal Hosted Fields rendered successfully. Instance created.");
                        if (clientSideErrorDiv) clientSideErrorDiv.style.display = 'none';
                        if (submitButton) submitButton.disabled = false; // Enable submit button
                    }).catch(function(err) {
                        console.error("MINIMAL TEST: FATAL ERROR rendering PayPal Hosted Fields:", err);
                        displayMinimalError('MINIMAL TEST: Could not load payment card fields (Render Fail). Refresh.');
                    });

                } else { 
                    console.error("MINIMAL TEST: PayPal Hosted Fields are NOT eligible (isEligible() returned false).");
                    displayMinimalError('MINIMAL TEST: Card payment is not available (Not Eligible). Try refreshing.', null);
                }
            } else { 
                console.error("MINIMAL TEST: window.paypal.HostedFields object is NOT available.");
                displayMinimalError('MINIMAL TEST: Payment components missing (HF Object Missing). Refresh.', null);
            }
        } else { 
            console.error("MINIMAL TEST: window.paypal object is NOT available.");
            displayMinimalError('MINIMAL TEST: PayPal SDK failed to load. Check connection & refresh. (SDK Load Fail)', null);
        }
    }

    // Attempt to initialize PayPal Hosted Fields with retries
    let paypalMinimalRenderAttempts = 0;
    const maxPaypalMinimalRenderAttempts = 7; 
    const paypalMinimalRetryDelay = 700;    

    function attemptMinimalPayPalRender() {
        if (window.paypal && window.paypal.HostedFields) { 
            initializeMinimalPayPalHF(); 
        } else if (paypalMinimalRenderAttempts < maxPaypalMinimalRenderAttempts) {
            paypalMinimalRenderAttempts++;
            console.log(`MINIMAL TEST: PayPal SDK core objects not ready yet, attempt ${paypalMinimalRenderAttempts}/${maxPaypalMinimalRenderAttempts}. Retrying in ${paypalMinimalRetryDelay}ms...`);
            setTimeout(attemptMinimalPayPalRender, paypalMinimalRetryDelay);
        } else {
            console.error("MINIMAL TEST: PayPal SDK did not become available after several attempts.");
            displayMinimalError('MINIMAL TEST: Payment system could not be initialized (SDK Timeout). Please refresh.', null);
        }
    }
    
    if (!form || !submitButton || !clientSideErrorDiv) {
        console.error("MINIMAL TEST: Essential form elements not found. Aborting.");
        if(document.getElementById('paypal-card-errors')) {
             document.getElementById('paypal-card-errors').textContent = "Critical page error. Contact support. (Code: JS_MIN_FORM_FAIL)";
             document.getElementById('paypal-card-errors').style.display = 'block';
        }
    } else {
        // Disable button initially until fields are ready or fail
        submitButton.disabled = true; 
        attemptMinimalPayPalRender(); // Start the process to initialize PayPal
    }

    // --- Form Submit Listener (VERY Basic for this test, assuming fields rendered) ---
    // We are temporarily bypassing your full validation and copy address logic to isolate the PayPal rendering issue.
    if (form) {
        form.addEventListener('submit', function (event) {
            event.preventDefault(); 
            
            if (!hostedFieldsInstanceMinimal) {
                displayMinimalError('MINIMAL TEST: Payment fields not ready. Refresh. (HF Instance Missing)');
                return;
            }
            
            // For this minimal test, we'll just try to submit to PayPal and log the token.
            // Your full validation and data gathering would go here in the full version.
            const cardholderNameInput = document.getElementById('cardholderName');
            const cardholderNameVal = cardholderNameInput ? cardholderNameInput.value.trim() : "Test Cardholder";

            console.log("MINIMAL TEST: Submitting card details to PayPal...");
            if (submitButton) submitButton.disabled = true;

            hostedFieldsInstanceMinimal.submit({
                cardholderName: cardholderNameVal
            }).then(function (payload) {
                console.log("MINIMAL TEST: PayPal Hosted Fields Submit RESPONSE Payload:", payload); 
                const paymentTokenForServer = payload.id; // ** VERIFY THIS FIELD **

                if (!paymentTokenForServer) {
                    displayMinimalError('MINIMAL TEST: No payment token from PayPal. Payload: ' + JSON.stringify(payload));
                    if (submitButton) submitButton.disabled = false;
                    return;
                }
                alert("MINIMAL TEST: Got PayPal Token (see console). Form NOT submitted to server in this test.\nToken: " + paymentTokenForServer);
                // In full version, you would add to form and form.submit();
                if (submitButton) submitButton.disabled = false; // Re-enable for further testing

            }).catch(function (err) {
                console.error('MINIMAL TEST: PayPal Hosted Fields submit() error:', err);
                displayMinimalError('MINIMAL TEST: Card processing error: ' + (err.message || "Unknown PayPal error."));
                if (submitButton) submitButton.disabled = false;
            });
        });
    }
    // --- End Minimal Form Submit Listener ---

    console.log("signup_validation.js (v7.4 - MINIMAL PayPal HF Eligibility Test) setup complete.");
});