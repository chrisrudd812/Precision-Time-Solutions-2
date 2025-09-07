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
            updateTargetSelect(optionsCache[type]);
            return;
        }

        fetch(`MessagingServlet?action=getOptions&type=${type}`)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    optionsCache[type] = data.options;
                    updateTargetSelect(data.options);
                } else {
                    showPageNotification(data.message || 'Error fetching options.', true);
                }
            })
            .catch(error => {
                showPageNotification('A network error occurred while fetching options.', true);
                console.error('Error:', error);
            });
    }

    function updateTargetSelect(options) {
        recipientTargetSelect.innerHTML = '';
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
        } else {
            targetSelectionContainer.style.display = 'block';
            recipientTargetLabel.textContent = `Select ${selectedType.charAt(0).toUpperCase() + selectedType.slice(1)}:`;
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
                showPageNotification(data.message || 'Message sent successfully!', false, null, "Message Sent");
                messagingForm.reset();
                targetSelectionContainer.style.display = 'none';
            } else {
                showPageNotification(data.message || 'Failed to send message.', true, null, "Send Error");
            }
        })
        .catch(error => {
            showPageNotification('A network error occurred. Could not send message.', true, null, "Network Error");
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
            messageSubject.value = subject;
        }
        if (body) {
            messageBody.value = body;
        }
        
        recipientTypeSelect.value = 'all';
        recipientTypeSelect.dispatchEvent(new Event('change'));
        window.history.replaceState({}, document.title, window.location.pathname);
    }
});