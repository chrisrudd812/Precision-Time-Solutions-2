document.addEventListener('DOMContentLoaded', function() {
    const contactForm = document.getElementById('contactForm');
    if (!contactForm) return;
    
    const fileInput = document.getElementById('fileAttachment');
    const fileNameDisplay = document.getElementById('fileNameDisplay');
    fileInput.addEventListener('change', () => {
        if (fileInput.files.length > 0) {
            fileNameDisplay.textContent = fileInput.files[0].name;
        } else {
            fileNameDisplay.textContent = 'No file selected';
        }
    });

    contactForm.addEventListener('submit', function(event) {
        event.preventDefault(); 
        const submitButton = contactForm.querySelector('button[type="submit"]');
        const originalButtonHTML = submitButton.innerHTML;
        submitButton.disabled = true;
        submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sending...';

        const formData = new FormData(contactForm);
        const url = contactForm.getAttribute('action');

        fetch(url, {
            method: 'POST',
            body: formData 
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                if (typeof window.showPageNotification === 'function') {
                    const successMessage = data.message + "<br><br>We will be in touch shortly.";
                    window.showPageNotification(successMessage, false, null, "Message Sent");
                } else {
                    alert(data.message);
                }
                contactForm.reset();
                fileNameDisplay.textContent = 'No file selected';
            } else {
                throw new Error(data.message || 'An unknown error occurred.');
            }
        })
        .catch(error => {
            if (typeof window.showPageNotification === 'function') {
                window.showPageNotification(error.message, true, null, "Error");
            } else {
                alert("Error: " + error.message);
            }
        })
        .finally(() => {
            submitButton.disabled = false;
            submitButton.innerHTML = originalButtonHTML;
        });
    });
});