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
        this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Preparing Emails...';

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
            this.disabled = false;
            this.innerHTML = originalButtonHTML;
        }
    });
});