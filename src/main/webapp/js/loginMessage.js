document.addEventListener('DOMContentLoaded', function() {
    const loginMessageModal = document.getElementById('loginMessageModal');
    if (loginMessageModal) {
        // Find the message body. If it has no messages, don't show the modal.
        const messageBody = loginMessageModal.querySelector('.login-message-body');
        if (messageBody && messageBody.innerHTML.trim() !== '') {
            if (typeof window.showModal === 'function') {
                window.showModal(loginMessageModal);
            }
        }

        const okButton = document.getElementById('okButtonLoginMessage');
        if (okButton) {
            okButton.addEventListener('click', function() {
                if (typeof window.hideModal === 'function') {
                    window.hideModal(loginMessageModal);
                }
            });
        }
    }
});