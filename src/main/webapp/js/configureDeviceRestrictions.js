// js/configureDeviceRestrictions.js

document.addEventListener('DOMContentLoaded', function() {
    const contextPath = window.APP_CONTEXT_PATH || '';

    // --- Element Selectors ---
    const saveGlobalMaxBtn = document.getElementById('saveGlobalMaxDevicesBtn');
    const globalMaxInput = document.getElementById('globalMaxDevicesInput');
    const globalStatusIndicator = document.querySelector('.status-indicator.global-max-status');
    const employeeListContainer = document.querySelector('.employee-list-scroll-container');
    const employeeSelect = document.getElementById('employeeSelect');
    
    // --- Employee Filter ---
    if (employeeSelect) {
        employeeSelect.addEventListener('change', function() {
            const selectedEid = this.value;
            const employeeSections = document.querySelectorAll('.employee-section');
            
            employeeSections.forEach(section => {
                if (selectedEid === '' || section.dataset.eid === selectedEid) {
                    section.style.display = 'block';
                } else {
                    section.style.display = 'none';
                }
            });
        });
    }
    
    async function sendRequest(action, params) {
        const formData = new URLSearchParams();
        formData.append('action', action);
        if (params) {
            for (const key in params) {
                if (Object.prototype.hasOwnProperty.call(params, key)) {
                    formData.append(key, params[key]);
                }
            }
        }

        const response = await fetch(`${contextPath}/DeviceRestrictionServlet`, {
            method: 'POST',
            headers: {
                // --- FIX: Removed the typo (extra space) in the Content-Type header ---
                'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
            },
            body: formData
        });
        
        const contentType = response.headers.get("content-type");
        if (contentType && contentType.includes("application/json")) {
            const data = await response.json();
            if (!response.ok || data.status !== 'success') {
                throw new Error(data.message || 'An unknown server error occurred.');
            }
            return data;
        } else {
            const errorText = await response.text();
            console.error('Server returned a non-JSON response:', errorText);
            throw new Error('An unexpected server error occurred. Please check the console for details.'); 
        }
    }
    
    // --- Event Listener for Save Global Limit Button ---
    if (saveGlobalMaxBtn) {
        saveGlobalMaxBtn.addEventListener('click', function() {
            const maxDevices = parseInt(globalMaxInput.value, 10);
            if (isNaN(maxDevices) || maxDevices < 1 || maxDevices > 20) {
                showPageNotification('Please enter a number between 1 and 20.', 'error', null, 'Invalid Input');
                return;
            }

            if (globalStatusIndicator) {
                globalStatusIndicator.textContent = 'Saving...';
                globalStatusIndicator.className = 'status-indicator global-max-status status-saving';
            }

            sendRequest('saveGlobalMaxDevices', { maxDevices: globalMaxInput.value })
            .then(data => {
                if (globalStatusIndicator) globalStatusIndicator.textContent = '';
                showPageNotification(data.message || 'Global limit updated!', 'success');
            })
            .catch(error => {
                if (globalStatusIndicator) globalStatusIndicator.textContent = '';
                showPageNotification(error.message, 'error');
            });
        });
    }

    // --- Real-time validation for the number input ---
    if (globalMaxInput) {
        globalMaxInput.addEventListener('input', function() {
            this.value = this.value.replace(/[^0-9]/g, '');
            let value = parseInt(this.value, 10);
            if (isNaN(value)) { return; }
            if (value < 1) { this.value = 1; }
            if (value > 20) { this.value = 20; }
        });
        globalMaxInput.addEventListener('blur', function() {
            if (this.value === '') { this.value = 1; }
        });
    }

    // --- Event Delegation for Device Actions ---
    if (employeeListContainer) {
        employeeListContainer.addEventListener('change', function(event) {
            const target = event.target;
            // Handle description saving
            if (target.classList.contains('description-input')) {
                const originalValue = target.dataset.originalValue;
                const newValue = target.value.trim();
                const statusIndicator = target.nextElementSibling;
                if (originalValue === newValue) return;
                if (statusIndicator) {
                    statusIndicator.textContent = 'Saving...';
                    statusIndicator.className = 'status-indicator desc-status status-saving';
                }
                
                sendRequest('updateDeviceDescription', { deviceId: target.closest('tr').dataset.deviceId, description: newValue })
                    .then(data => {
                        target.dataset.originalValue = data.newDescription;
                        target.value = data.newDescription;
                        if (statusIndicator) statusIndicator.textContent = '';
                        showToast('Description updated.');
                    })
                    .catch(err => {
                        if (statusIndicator) statusIndicator.textContent = '';
                        showPageNotification(err.message, 'error');
                        target.value = originalValue;
                    });
            }
            // Handle Enable/Disable Toggle
            if (target.classList.contains('device-enable-toggle')) {
                const isEnabled = target.checked;

                sendRequest('toggleDeviceStatus', { deviceId: target.dataset.deviceId, isEnabled: isEnabled })
                .then(data => {
                    showToast('Status updated.');
                })
                .catch(err => {
                    showPageNotification(err.message, 'error');
                    target.checked = !isEnabled;
                });
            }
        });

        // Handle clicking the delete button
        employeeListContainer.addEventListener('click', function(event) {
            const deleteButton = event.target.closest('.device-delete-btn');
            if (deleteButton) {
                const row = deleteButton.closest('tr');
                const description = row.querySelector('.description-input').value || 'Unnamed Device';
                
                showConfirmModal(`Are you sure you want to delete "<strong>${description}</strong>"?`, (confirmed) => {
                    if (confirmed) {
                        sendRequest('deleteDevice', { deviceId: row.dataset.deviceId })
                        .then(data => {
                            row.style.transition = 'opacity 0.4s ease';
                            row.style.opacity = '0';
                            setTimeout(() => { row.remove(); }, 400);
                            showToast(data.message || 'Device deleted.');
                        })
                        .catch(err => {
                            showPageNotification(err.message, 'error');
                        });
                    }
                }, { type: 'delete', title: 'Confirm Deletion' });
            }
        });
    }
});