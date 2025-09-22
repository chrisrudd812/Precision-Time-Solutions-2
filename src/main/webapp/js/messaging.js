document.addEventListener('DOMContentLoaded', function() {
    const recipientTypeSelect = document.getElementById('recipientType');
    const targetSelectionContainer = document.getElementById('target-selection-container');
    const recipientTargetSelect = document.getElementById('recipientTarget');
    const recipientTargetLabel = document.getElementById('recipientTargetLabel');
    const messagingForm = document.getElementById('messagingForm');
    const sendMessageBtn = document.getElementById('sendMessageBtn');
    const messageSubject = document.getElementById('messageSubject');
    const messageBody = document.getElementById('messageBody');
    const deliveryTypeInput = document.getElementById('messageDeliveryType');
    const { showPageNotification } = window;

    const optionsCache = {};

    document.querySelectorAll('input[name="deliveryType"]').forEach(radio => {
        radio.addEventListener('change', function() {
            deliveryTypeInput.value = this.value;
        });
    });

    function fetchOptions(type) {
        if (optionsCache[type]) {
            updateTargetSelect(optionsCache[type], type);
            return;
        }

        fetch(`MessagingServlet?action=getOptions&type=${type}`)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    optionsCache[type] = data.options;
                    updateTargetSelect(data.options, type);
                } else {
                    showPageNotification(data.message || 'Error fetching options.', 'error');
                }
            })
            .catch(error => {
                showPageNotification('A network error occurred while fetching options.', 'error');
                console.error('Error:', error);
            });
    }

    /**
     * MODIFIED: This function now adds a placeholder option to the dropdown.
     * @param {Array} options The list of options to populate.
     * @param {string} type The type of recipient (e.g., 'department', 'individual').
     */
    function updateTargetSelect(options, type) {
        recipientTargetSelect.innerHTML = '';
        
        // Add a disabled, selected placeholder option first
        const placeholder = document.createElement('option');
        placeholder.value = ""; // Empty value will fail 'required' validation
        const typeText = type === 'individual' ? 'Employee' : type.charAt(0).toUpperCase() + type.slice(1);
        placeholder.textContent = `-- Select a ${typeText} --`;
        recipientTargetSelect.appendChild(placeholder);

        if (options && options.length > 0) {
            options.forEach(option => {
                const opt = document.createElement('option');
                opt.value = option.value;
                opt.textContent = option.text;
                recipientTargetSelect.appendChild(opt);
            });
        }
    }

    recipientTypeSelect.addEventListener('change', function() {
        const selectedType = this.value;
        if (selectedType === 'all') {
            targetSelectionContainer.style.display = 'none';
            // MODIFIED: Ensure the target select is not required when hidden
            recipientTargetSelect.required = false;
        } else {
            targetSelectionContainer.style.display = 'block';
            const typeText = selectedType === 'individual' ? 'Employee' : selectedType.charAt(0).toUpperCase() + selectedType.slice(1);
            recipientTargetLabel.textContent = `Select ${typeText}:`;
            // MODIFIED: Ensure the target select IS required when visible
            recipientTargetSelect.required = true;
            fetchOptions(selectedType);
        }
    });

    messagingForm.addEventListener('submit', function(event) {
        event.preventDefault();
        const originalButtonText = sendMessageBtn.innerHTML;
        sendMessageBtn.disabled = true;
        sendMessageBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Sending...';

        const formData = new FormData(messagingForm);
        const params = new URLSearchParams(formData);
        params.append('action', 'sendMessage');

        fetch('MessagingServlet', {
            method: 'POST',
            body: params
        })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                showPageNotification(data.message || 'Message sent successfully!', 'success', null, "Message Sent");
                messagingForm.reset();
                recipientTypeSelect.dispatchEvent(new Event('change'));
            } else {
                showPageNotification(data.message || 'Failed to send message.', 'error', null, "Send Error");
            }
        })
        .catch(error => {
            showPageNotification('A network error occurred. Could not send message.', 'error', null, "Network Error");
            console.error('Error:', error);
        })
        .finally(() => {
            sendMessageBtn.disabled = false;
            sendMessageBtn.innerHTML = originalButtonText;
        });
    });
    
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('action') === 'welcome') {
        const subject = urlParams.get('subject');
        const body = urlParams.get('body');

        if (subject) {
            messageSubject.value = decodeURIComponent(subject);
        }
        if (body) {
            messageBody.value = decodeURIComponent(body).replace(/\\n/g, '\n');
        }
        
        document.getElementById('deliveryTypeEmail').checked = true;
        deliveryTypeInput.value = 'email'; 

        recipientTypeSelect.value = 'all';
        recipientTypeSelect.dispatchEvent(new Event('change'));
        
        window.history.replaceState({}, document.title, window.location.pathname);
    }
});