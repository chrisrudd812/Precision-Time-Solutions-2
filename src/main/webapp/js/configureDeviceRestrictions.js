// js/configureDeviceRestrictions.js

// ✅ This function now uses the modal from notification-modals.jspf
function showConfirmModal(message, onConfirmCallback) {
    const modal = document.getElementById('confirmModal');
    const messageEl = document.getElementById('confirmModalMessage');
    const okBtn = document.getElementById('confirmModalOkBtn');
    
    if (!modal || !messageEl || !okBtn) {
        console.error("Confirm modal elements not found!");
        if(confirm(message)) { onConfirmCallback(); } // Fallback to ugly confirm
        return;
    }

    messageEl.textContent = message;

    // Clone and replace the OK button to ensure no old event listeners are attached
    const newOkBtn = okBtn.cloneNode(true);
    okBtn.parentNode.replaceChild(newOkBtn, okBtn);

    newOkBtn.addEventListener('click', function() {
        if (typeof hideModal === 'function') {
            hideModal(modal);
        } else {
            modal.classList.remove('modal-visible');
        }
        onConfirmCallback();
    }, { once: true }); // Important: the callback should only ever fire once per click

    if (typeof showModal === 'function') {
        showModal(modal);
    } else {
        modal.classList.add('modal-visible');
    }
}

document.addEventListener('DOMContentLoaded', function() {
    console.log("[configureDeviceRestrictions.js] DOMContentLoaded. Context for AJAX: '" + APP_CONTEXT_PATH + "'");

    const saveGlobalMaxBtn = document.getElementById('saveGlobalMaxDevicesBtn');
    const globalMaxInput = document.getElementById('globalMaxDevicesInput');
    const globalMaxStatus = document.querySelector('.global-max-status');
    
    // --- Event Listener for Saving Global Max Devices ---
    if (saveGlobalMaxBtn && globalMaxInput && globalMaxStatus) {
        saveGlobalMaxBtn.addEventListener('click', function() {
            const newValue = globalMaxInput.value;
            const numValue = parseInt(newValue, 10);

            if (isNaN(numValue) || numValue < 0 || numValue > 20) {
                showToast('Max devices must be a whole number between 0 and 20.', 'error');
                return;
            }
            
            globalMaxStatus.textContent = 'Saving...';
            globalMaxStatus.className = 'status-indicator global-max-status status-saving';
            
            const formData = new URLSearchParams();
            formData.append('action', 'saveGlobalMaxDevices');
            formData.append('maxDevices', newValue);
            
            fetch(APP_CONTEXT_PATH + "/DeviceRestrictionServlet", {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: formData
            })
            .then(response => {
                if (!response.ok) { return response.json().then(err => Promise.reject(err)); }
                return response.json();
            })
            .then(data => {
                if (data.status === 'success') {
                    globalMaxStatus.textContent = 'Saved!';
                    globalMaxStatus.className = 'status-indicator global-max-status status-saved';
                    showToast(data.message || "Global limit updated!", 'success');
                    globalMaxInput.value = data.newMax;
                } else {
                    globalMaxStatus.textContent = 'Error!';
                    globalMaxStatus.className = 'status-indicator global-max-status status-error';
                    showToast(data.message || "Error saving.", 'error');
                }
                setTimeout(() => { globalMaxStatus.textContent = ''; globalMaxStatus.className = 'status-indicator global-max-status'; }, 3000);
            })
            .catch(error => {
                console.error('[configureDeviceRestrictions.js] Save global max AJAX error:', error);
                globalMaxStatus.textContent = 'Network Error!';
                globalMaxStatus.className = 'status-indicator global-max-status status-error';
                showToast('Network error: ' + (error.message || 'Unknown fetch error'), 'error');
            });
        });
        globalMaxInput.addEventListener('keypress', function(event){
            if(event.key === 'Enter'){
                event.preventDefault();
                saveGlobalMaxBtn.click();
            }
        });
    }

    // --- Event Listener for Description Input Fields ---
    document.querySelectorAll('.description-input').forEach(input => {
        let debounceTimer;
        const statusIndicator = input.parentElement.querySelector('.desc-status');
        input.addEventListener('input', function() {
            clearTimeout(debounceTimer);
            if(statusIndicator) {
                statusIndicator.textContent = 'Typing...';
                statusIndicator.className = 'status-indicator desc-status status-typing';
            }
            debounceTimer = setTimeout(() => {
                const deviceId = this.closest('tr').dataset.deviceId;
                const newDescription = this.value;
                const originalDescription = this.dataset.originalValue;

                if (newDescription === originalDescription) {
                    if(statusIndicator) statusIndicator.textContent = '';
                    return;
                }
                
                if(statusIndicator) {
                    statusIndicator.textContent = 'Saving...';
                    statusIndicator.className = 'status-indicator desc-status status-saving';
                }
                
                const formData = new URLSearchParams();
                formData.append('action', 'updateDeviceDescription');
                formData.append('deviceId', deviceId);
                formData.append('description', newDescription);

                fetch(APP_CONTEXT_PATH + '/DeviceRestrictionServlet', { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: formData })
                .then(response => response.json())
                .then(data => {
                    if(data.status === 'success'){
                        if(statusIndicator) {
                            statusIndicator.textContent = 'Saved!';
                            statusIndicator.className = 'status-indicator desc-status status-saved';
                        }
                        this.dataset.originalValue = data.newDescription;
                        showToast(data.message,'success');
                    } else {
                        if(statusIndicator) {
                            statusIndicator.textContent = 'Error!';
                            statusIndicator.className = 'status-indicator desc-status status-error';
                        }
                        showToast(data.message, 'error');
                        this.value = originalDescription; // Revert on failure
                    }
                    setTimeout(() => { if(statusIndicator) statusIndicator.textContent = ''; }, 3000);
                })
                .catch(e => {
                    if(statusIndicator) {
                        statusIndicator.textContent = 'Net Error!';
                        statusIndicator.className = 'status-indicator desc-status status-error';
                    }
                    showToast('Network error while saving description.','error');
                    this.value = originalDescription;
                });
            }, 1200);
        });
    });

    // --- Event Listener for Enable/Disable Toggles ---
    document.querySelectorAll('.device-enable-toggle').forEach(toggle => {
        toggle.addEventListener('change', function() {
            const deviceId = this.dataset.deviceId;
            const isEnabled = this.checked;
            const formData = new URLSearchParams();
            formData.append('action', 'toggleDeviceStatus');
            formData.append('deviceId', deviceId);
            formData.append('isEnabled', String(isEnabled));
            
            fetch(APP_CONTEXT_PATH + '/DeviceRestrictionServlet', { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: formData })
            .then(response => response.json())
            .then(data => {
                if (data.status === 'success') {
                    showToast(data.message, 'success');
                } else {
                    showToast(data.message, 'error');
                    this.checked = !isEnabled; // Revert toggle on failure
                }
            })
            .catch(e => {
                showToast('Network error while toggling status.','error');
                this.checked = !isEnabled;
            });
        });
    });

    // --- Event Listener for Delete Buttons ---
    document.querySelectorAll('.device-delete-btn').forEach(button => {
        button.addEventListener('click', function() {
            const deviceId = this.dataset.deviceId;
            const row = this.closest('tr');
            const descInput = row.querySelector('.description-input');
            const deviceDesc = descInput ? (descInput.value.trim() || "this device") : "this device";
            
            showConfirmModal('Are you sure you want to delete "' + deviceDesc + '"?', () => {
                const formData = new URLSearchParams();
                formData.append('action', 'deleteDevice');
                formData.append('deviceId', deviceId);
                
                fetch(APP_CONTEXT_PATH + '/DeviceRestrictionServlet', { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: formData })
                .then(response => response.json())
                .then(data => {
                    if (data.status === 'success') {
                        showToast(data.message, 'success');
                        row.style.transition = 'opacity 0.5s ease-out';
                        row.style.opacity = '0';
                        setTimeout(() => row.remove(), 500);
                    } else {
                        showToast(data.message, 'error');
                    }
                })
                .catch(e => {
                    showToast('Network error while trying to delete.', 'error');
                });
            });
        });
    });
});