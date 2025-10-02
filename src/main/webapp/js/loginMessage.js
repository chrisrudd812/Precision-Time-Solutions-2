document.addEventListener('DOMContentLoaded', function() {
    const loginMessageModal = document.getElementById('loginMessageModal');
    if (!loginMessageModal) return;

    const messageBody = loginMessageModal.querySelector('.login-message-body');
    const okButton = document.getElementById('okButtonLoginMessage');
    let messageIds = [];

    fetch('LoginMessageServlet?action=getMessages')
        .then(response => response.json())
        .then(data => {
            if (data.success && data.messages && data.messages.length > 0) {
                messageIds = data.messages.map(msg => msg.messageId);
                
                let html = '';
                data.messages.forEach(msg => {
                    html += '<div class="message-item">';
                    if (msg.subject) {
                        html += '<h3 style="margin-top: 0; margin-bottom: 10px; color: #1e40af;">' + escapeHtml(msg.subject) + '</h3>';
                    }
                    html += '<p style="margin: 0; line-height: 1.6;">' + escapeHtml(msg.body).replace(/\n/g, '<br>') + '</p>';
                    html += '</div>';
                });
                
                messageBody.innerHTML = html;
                
                if (typeof window.showModal === 'function') {
                    window.showModal(loginMessageModal);
                } else {
                    loginMessageModal.style.display = 'flex';
                    loginMessageModal.classList.add('modal-visible');
                }
            }
        })
        .catch(err => console.error('Error fetching login messages:', err));

    if (okButton) {
        okButton.addEventListener('click', function() {
            messageIds.forEach(id => {
                fetch('LoginMessageServlet?action=deleteMessage&messageId=' + id)
                    .catch(err => console.error('Error deleting message:', err));
            });
            
            if (typeof window.hideModal === 'function') {
                window.hideModal(loginMessageModal);
            } else {
                loginMessageModal.style.display = 'none';
                loginMessageModal.classList.remove('modal-visible');
            }
        });
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
});
