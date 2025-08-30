document.addEventListener('DOMContentLoaded', function() {
    const contactForm = document.getElementById('contactForm');
    if (!contactForm) return;

    const statusDiv = document.getElementById('form-status-message');
    const submitButton = contactForm.querySelector('button[type="submit"]');
    const originalButtonHTML = submitButton.innerHTML;

    contactForm.addEventListener('submit', function(event) {
        event.preventDefault(); // Stop the default form submission

        const subject = contactForm.querySelector('#contactSubject').value.trim();
        const message = contactForm.querySelector('#contactMessage').value.trim();

        if (!subject || !message) {
            showMessage('Please fill out all required fields.', true);
            return;
        }

        submitButton.disabled = true;
        submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sending...';
        statusDiv.style.display = 'none';

        const formData = new FormData(contactForm);
        const url = contactForm.getAttribute('action');

        fetch(url, {
            method: 'POST',
            body: new URLSearchParams(formData)
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                // [FIX] Use the standard site-wide notification modal for success
                if (typeof window.showPageNotification === 'function') {
                    const modal = document.getElementById('notificationModalGeneral');
                    const okButton = modal.querySelector('#okButtonNotificationModalGeneral');
                    const closeButton = modal.querySelector('.close');

                    // Clone buttons to ensure they have fresh event listeners
                    const newOkButton = okButton.cloneNode(true);
                    okButton.parentNode.replaceChild(newOkButton, okButton);

                    const newCloseButton = closeButton.cloneNode(true);
                    closeButton.parentNode.replaceChild(newCloseButton, closeButton);

                    // Define the action to close the modal
                    const closeModalAction = () => {
                        if (typeof window.hideModal === 'function') {
                            window.hideModal(modal);
                        }
                    };

                    // Attach the new listeners
                    newOkButton.addEventListener('click', closeModalAction, { once: true });
                    newCloseButton.addEventListener('click', closeModalAction, { once: true });
                    
                    // [FIX] Append the new text to the success message
                    const successMessage = data.message + "<br><br>You will receive a response within 48 hours.";

                    window.showPageNotification(successMessage, false, null, "Message Sent");
                } else {
                    alert(data.message); // Fallback
                }
                contactForm.reset();
            } else {
                throw new Error(data.message || 'An unknown error occurred.');
            }
        })
        .catch(error => {
            // Use the local message div for errors
            showMessage(error.message, true);
        })
        .finally(() => {
            submitButton.disabled = false;
            submitButton.innerHTML = originalButtonHTML;
        });
    });

    // This function is now only for client-side errors
    function showMessage(message, isError) {
        if (!statusDiv) return;
        statusDiv.textContent = message;
        statusDiv.className = isError ? 'form-status-message error' : 'form-status-message success';
        statusDiv.style.display = 'block';
    }
});