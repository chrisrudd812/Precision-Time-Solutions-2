// js/configureNetworkRestrictions.js

const APP_CONTEXT_PATH = window.appConfig.contextPath;
const IP_TO_PREPOPULATE = window.appConfig.ipToPrepopulate;

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) {
        alert((type === 'error' ? "Error: " : "Success: ") + message);
        return;
    }
    const toast = document.createElement('div');
    toast.className = `toast-notification ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    requestAnimationFrame(() => { toast.classList.add('show'); });
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => { if (toast.parentNode) toast.remove(); }, 300);
    }, 3500);
}

// A standardized function for all fetch requests on this page
async function sendRequest(action, formData) {
    const params = new URLSearchParams(formData);
    params.set('action', action); // Ensure action is set

    const response = await fetch(`${APP_CONTEXT_PATH}/NetworkRestrictionServlet`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: params
    });

    const data = await response.json();
    if (!response.ok) {
        throw new Error(data.message || `Server error: ${response.status}`);
    }
    return data;
}

document.addEventListener('DOMContentLoaded', function() {
    const addNetworkForm = document.getElementById('addNetworkForm');
    const cidrInputForAdd = document.getElementById('addCIDR');
    const networkModal = document.getElementById('networkModal');
    const editNetworkForm = document.getElementById('editNetworkForm');
    const confirmModal = document.getElementById('confirmModal');

    if (cidrInputForAdd && IP_TO_PREPOPULATE) {
        cidrInputForAdd.value = IP_TO_PREPOPULATE;
    }

    // --- Add Form ---
    addNetworkForm?.addEventListener('submit', function(event) {
        event.preventDefault();
        const btn = this.querySelector('button[type="submit"]');
        btn.disabled = true;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Adding...';

        sendRequest('addNetwork', new FormData(this))
            .then(data => {
                showToast(data.message, 'success');
                setTimeout(() => window.location.reload(), 1500);
            })
            .catch(error => {
                showToast(error.message, 'error');
                btn.disabled = false;
                btn.innerHTML = '<i class="fas fa-plus-circle"></i> Add Network';
            });
    });

    // --- Edit Modal ---
    document.querySelectorAll('.edit-network-btn').forEach(button => {
        button.addEventListener('click', function() {
            const row = this.closest('tr');
            editNetworkForm.querySelector('#editNetworkID').value = row.dataset.networkId;
            editNetworkForm.querySelector('#networkModalTitle').textContent = 'Edit Network: ' + row.dataset.name;
            editNetworkForm.querySelector('#editNetworkName').value = row.dataset.name;
            editNetworkForm.querySelector('#editCIDR').value = row.dataset.cidr;
            editNetworkForm.querySelector('#editDescription').value = row.dataset.description;
            editNetworkForm.querySelector('#editIsEnabled').checked = (row.dataset.enabled === 'true');
            if(typeof showModal === 'function') showModal(networkModal);
        });
    });

    editNetworkForm?.addEventListener('submit', function(event) {
        event.preventDefault();
        const btn = this.querySelector('button[type="submit"]');
        btn.disabled = true;
        btn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
        
        const formData = new FormData(this);
        if (!formData.has("isEnabled")) formData.append("isEnabled", "false");

        sendRequest('editNetwork', formData)
            .then(data => {
                showToast(data.message, 'success');
                if(typeof hideModal === 'function') hideModal(networkModal);
                setTimeout(() => window.location.reload(), 1500);
            })
            .catch(error => {
                showToast(error.message, 'error');
                btn.disabled = false;
                btn.innerHTML = 'Save Changes';
            });
    });
    
    // --- Delete Modal ---
    document.querySelectorAll('.delete-network-btn').forEach(button => {
        button.addEventListener('click', function() {
            const row = this.closest('tr');
            const networkName = row.dataset.name;
            const networkId = row.dataset.networkId;

            confirmModal.querySelector('#confirmModalMessage').innerHTML = `Are you sure you want to delete the network "<strong>${networkName}</strong>"? This cannot be undone.`;
            if(typeof showModal === 'function') showModal(confirmModal);

            const confirmBtn = confirmModal.querySelector('#confirmModalOkBtn');
            const newConfirmBtn = confirmBtn.cloneNode(true);
            confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);

            newConfirmBtn.addEventListener('click', () => {
                const formData = new FormData();
                formData.append('networkID', networkId);
                sendRequest('deleteNetwork', formData)
                    .then(data => {
                        showToast(data.message, 'success');
                        setTimeout(() => window.location.reload(), 1500);
                    })
                    .catch(error => showToast(error.message, 'error'));
                if(typeof hideModal === 'function') hideModal(confirmModal);
            }, { once: true });
        });
    });

    // --- Toggle Switch ---
    document.querySelectorAll('.network-enable-toggle').forEach(toggle => {
        toggle.addEventListener('change', function() {
            const formData = new FormData();
            formData.append('networkID', this.dataset.networkId);
            formData.append('isEnabled', this.checked);

            sendRequest('updateLocationStatus', formData) // Note: Reusing the same action name, ensure servlet handles it
                .then(data => showToast(data.message, 'success'))
                .catch(error => {
                    showToast(error.message, 'error');
                    this.checked = !this.checked; // Revert on error
                });
        });
    });
    
    // --- Modal Close Buttons ---
    document.querySelectorAll('.modal .close, .modal .glossy-button.text-grey').forEach(btn => {
        btn.addEventListener('click', () => {
            const modal = btn.closest('.modal');
            if(typeof hideModal === 'function') hideModal(modal);
        });
    });
});