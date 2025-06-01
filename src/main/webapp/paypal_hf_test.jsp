<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Minimal PayPal Hosted Fields Test</title>
    <style>
        body { font-family: Arial, sans-serif; margin: 20px; }
        .container { padding: 20px; border: 1px solid #ccc; max-width: 500px; margin: auto; }
        .hosted-field-label { display: block; margin-top: 15px; margin-bottom: 5px; font-weight: bold; }
        .form-control-paypal {
            border: 1px solid #ccc; /* Visible border for the div itself */
            padding: 0;             /* PayPal iframe will have its own padding */
            border-radius: 4px;
            height: 40px;           /* Height for the div */
            background-color: #f9f9f9; /* Slight background to see the div */
            margin-bottom: 10px;
        }
        .error-message {
            color: #D8000C; background-color: #FFD2D2; padding: 10px;
            margin-top:15px; border-radius: 4px; border: 1px solid #D8000C;
            display:none; /* Hidden by default */
        }
        button { padding: 10px 15px; margin-top:15px; }
    </style>

    <%-- PayPal JavaScript SDK - Using your Sandbox Client ID --%>
    <script src="https://www.paypal.com/sdk/js?client-id=AVvNXTc0v0M4aRIwgNKSEtQFmTc06PnQLHtQxBeIw0hUAi-jwDO-rSdMJB_KxVp05bvrdfCnnXJR7rTC&components=hosted-fields"></script>
</head>
<body>
    <div class="container">
        <h1>PayPal Hosted Fields Eligibility Test</h1>
        <p>This page attempts to render PayPal Hosted Fields with minimal surrounding code.</p>
        <p>Check the browser console for detailed logs.</p>

        <div id="paypal-error-message" class="error-message"></div>

        <div>
            <label for="cardholder-name-test" class="hosted-field-label">Name on Card (Standard Input):</label>
            <input type="text" id="cardholder-name-test" style="width: calc(100% - 22px); padding:10px; border:1px solid #ccc; border-radius:4px; height:40px; box-sizing: border-box;">
        </div>
        <div>
            <label for="paypal-card-number" class="hosted-field-label">Card Number (PayPal Hosted Field):</label>
            <div id="paypal-card-number" class="form-control-paypal"></div>
        </div>
        <div>
            <label for="paypal-card-expiry" class="hosted-field-label">Expiration Date (MM/YY):</label>
            <div id="paypal-card-expiry" class="form-control-paypal"></div>
        </div>
        <div>
            <label for="paypal-card-cvv" class="hosted-field-label">CVV:</label>
            <div id="paypal-card-cvv" class="form-control-paypal"></div>
        </div>

        <button id="test-submit-button" disabled>Submit Test Payment</button>
    </div>

    <script>
        document.addEventListener('DOMContentLoaded', function () {
            console.log("MINIMAL TEST PAGE: DOMContentLoaded.");

            const submitButton = document.getElementById('test-submit-button');
            const errorMessageDiv = document.getElementById('paypal-error-message');
            let hostedFieldsInstance;

            function displayTestError(message) {
                if (errorMessageDiv) {
                    errorMessageDiv.textContent = message;
                    errorMessageDiv.style.display = 'block';
                    console.error("MINIMAL TEST ERROR DISPLAYED: " + message);
                } else {
                    alert("MINIMAL TEST ERROR: " + message);
                }
                if (submitButton) submitButton.disabled = true;
            }

            function initializeMinimalPayPalHF() {
                console.log("MINIMAL TEST: Attempting to initialize PayPal Hosted Fields. Checking SDK objects...");
                if (window.paypal) {
                    console.log("MINIMAL TEST: window.paypal object IS available.");
                    if (window.paypal.HostedFields) {
                        console.log("MINIMAL TEST: window.paypal.HostedFields object IS available.");
                        if (window.paypal.HostedFields.isEligible()) {
                            console.log("MINIMAL TEST: PayPal Hosted Fields ARE eligible. Calling render()...");
                            
                            paypal.HostedFields.render({
                                styles: { 
                                    'input': { 'font-size': '16px', 'font-family': 'Arial, sans-serif', 'color': '#333', 'padding': '10px 12px' },
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
                                hostedFieldsInstance = instance; 
                                console.log("MINIMAL TEST: PayPal Hosted Fields rendered successfully. Instance created.");
                                if (errorMessageDiv) errorMessageDiv.style.display = 'none'; // Hide error div if successful
                                if (submitButton) submitButton.disabled = false; 
                                alert("MINIMAL TEST: PayPal Hosted Fields rendered! You should be able to type in the card fields.");
                            }).catch(function(err) {
                                console.error("MINIMAL TEST: FATAL ERROR rendering PayPal Hosted Fields:", err);
                                displayTestError('MINIMAL TEST: Could not load payment card fields (Render Fail). ' + (err.message || ''));
                            });

                        } else { 
                            console.error("MINIMAL TEST: PayPal Hosted Fields are NOT eligible (isEligible() returned false).");
                            displayTestError('MINIMAL TEST: Card payment is not available for this environment (Not Eligible).', null);
                        }
                    } else { 
                        console.error("MINIMAL TEST: window.paypal.HostedFields object is NOT available.");
                        displayTestError('MINIMAL TEST: Payment components missing (HF Object Missing).', null);
                    }
                } else { 
                    console.error("MINIMAL TEST: window.paypal object is NOT available.");
                    displayTestError('MINIMAL TEST: PayPal SDK failed to load. Check network and console. (SDK Load Fail)', null);
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
                    displayTestError('MINIMAL TEST: Payment system could not be initialized (SDK Timeout). Please refresh.', null);
                }
            }
            
            if (submitButton && errorMessageDiv) { // Ensure essential elements for the test page are present
                submitButton.disabled = true; 
                attemptMinimalPayPalRender(); 
            } else {
                alert("MINIMAL TEST PAGE ERROR: Core elements like button or error div missing.");
            }

            // Basic submit listener for testing the tokenization if fields render
            if (submitButton) {
                submitButton.addEventListener('click', function() {
                    if (!hostedFieldsInstance) {
                        alert("MINIMAL TEST: Hosted Fields not ready for submission.");
                        return;
                    }
                    const cardholderName = document.getElementById('cardholder-name-test').value;
                    console.log("MINIMAL TEST: Attempting to submit card data via Hosted Fields...");
                    submitButton.disabled = true;
                    hostedFieldsInstance.submit({
                        cardholderName: cardholderName || "Test User"
                    }).then(function(payload) {
                        console.log("MINIMAL TEST: PayPal Submit Payload:", payload);
                        alert("MINIMAL TEST: PayPal tokenization successful! Check console for payload.\nToken/Order ID (e.g., payload.id): " + (payload.id || payload.nonce || "Not Found in expected fields"));
                        submitButton.disabled = false;
                    }).catch(function(err) {
                        console.error("MINIMAL TEST: PayPal Submit Error:", err);
                        let errMsg = "Card submission failed.";
                        if (err && err.message) errMsg = err.message;
                        else if (err && err.details && Array.isArray(err.details) && err.details.length > 0) errMsg = err.details[0].issue + (err.details[0].description ? (": " + err.details[0].description) : "");
                        alert("MINIMAL TEST: " + errMsg);
                        submitButton.disabled = false;
                    });
                });
            }
        });
    </script>
</body>
</html>