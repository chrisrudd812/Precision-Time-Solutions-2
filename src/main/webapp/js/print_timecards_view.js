document.addEventListener('DOMContentLoaded', function() {
    const emailBtn = document.getElementById('emailTimecardsBtn');
    // [FIX] Corrected the variable name to match the button's ID
    const closeBtn = document.getElementById('closeTabBtn');

    if (closeBtn) {
        closeBtn.addEventListener('click', function() {
            window.close();
        });
    }

    if (!emailBtn) return;

    const { showPageNotification } = window;
    const appRoot = window.appRootPath || "";

    emailBtn.addEventListener('click', async function() {
        const originalButtonHTML = this.innerHTML;
        this.disabled = true;
        this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Processing...';

        // Create and show processing overlay
        const processingOverlay = document.createElement('div');
        processingOverlay.id = 'emailProcessingOverlay';
        processingOverlay.innerHTML = `
            <div class="processing-content">
                <div class="processing-spinner">
                    <i class="fas fa-envelope fa-3x"></i>
                    <i class="fas fa-spinner fa-spin fa-2x"></i>
                </div>
                <h3>Processing Email Requests</h3>
                <p>Generating and sending timecard emails...</p>
                <p class="processing-note">This may take a moment depending on the number of emails being sent.</p>
            </div>
        `;
        processingOverlay.style.cssText = `
            position: fixed;
            top: 0;
            left: 0;
            width: 100%;
            height: 100%;
            background: rgba(0, 0, 0, 0.8);
            display: flex;
            justify-content: center;
            align-items: center;
            z-index: 10000;
            color: white;
            font-family: Arial, sans-serif;
        `;
        
        const style = document.createElement('style');
        style.textContent = `
            .processing-content {
                text-align: center;
                background: rgba(255, 255, 255, 0.1);
                padding: 40px;
                border-radius: 10px;
                backdrop-filter: blur(10px);
                border: 1px solid rgba(255, 255, 255, 0.2);
            }
            .processing-spinner {
                position: relative;
                margin-bottom: 20px;
            }
            .processing-spinner .fa-envelope {
                color: #4CAF50;
                margin-right: 15px;
            }
            .processing-spinner .fa-spinner {
                color: #2196F3;
            }
            .processing-content h3 {
                margin: 0 0 15px 0;
                font-size: 24px;
                color: white;
            }
            .processing-content p {
                margin: 10px 0;
                font-size: 16px;
                color: #e0e0e0;
            }
            .processing-note {
                font-size: 14px !important;
                font-style: italic;
                color: #bbb !important;
            }
        `;
        document.head.appendChild(style);
        document.body.appendChild(processingOverlay);

        try {
            const payload = {
                timecards: window.timecardDataForEmail,
                payPeriodMessage: window.payPeriodMessage
            };

            const response = await fetch(`${appRoot}/EmailTimecardsServlet`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            const result = await response.json();

            if (result.success) {
                showPageNotification(result.message, false, null, "Success");
            } else {
                throw new Error(result.message || "An unknown error occurred.");
            }

        } catch (error) {
            console.error("Error sending timecard emails:", error);
            showPageNotification(`Failed to send emails: ${error.message}`, true, null, "Error");
        } finally {
            // Remove processing overlay
            const overlay = document.getElementById('emailProcessingOverlay');
            if (overlay) {
                overlay.remove();
            }
            this.disabled = false;
            this.innerHTML = originalButtonHTML;
        }
    });
});