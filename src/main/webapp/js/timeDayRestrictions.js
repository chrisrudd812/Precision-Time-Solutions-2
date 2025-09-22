// js/timeDayRestrictions.js

document.addEventListener('DOMContentLoaded', function() {
    
    // --- Element Selectors ---
    const allowUnselectedDaysToggle = document.getElementById('allowUnselectedDays');
    const unselectedDayActionText = document.getElementById('unselectedDayActionText');
    const saveTimeDaySettingsBtn = document.getElementById('saveTimeDaySettingsBtn');
    const timeDayRestrictionsForm = document.getElementById('timeDayRestrictionsForm');
    const applyToAllToggle = document.getElementById('applyToAllToggle');
    const masterRow = document.getElementById('masterRow');
    
    // --- Logic for the "Global Rule for Disabled Days" toggle ---
    if (allowUnselectedDaysToggle) {
        allowUnselectedDaysToggle.addEventListener('change', function() {
            const isChecked = this.checked;
            const saveUrl = this.dataset.saveUrl;
            const statusSpan = document.getElementById('allowUnselectedDays-status');

            if (unselectedDayActionText) {
                unselectedDayActionText.textContent = isChecked ? "Allow punches all day" : "Do not allow punches";
            }

            const formData = new URLSearchParams();
            formData.append('settingKey', 'allowUnselectedDays');
            formData.append('settingValue', isChecked);

            if (statusSpan) {
                statusSpan.textContent = 'Saving...';
                statusSpan.className = 'save-status visible info';
            }

            fetch(saveUrl, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: formData
            })
            .then(response => {
                if (!response.ok) { throw new Error('Save failed'); }
                return response.text();
            })
            .then(data => {
                if (statusSpan) {
                    statusSpan.textContent = 'Saved!';
                    statusSpan.className = 'save-status visible';
                    setTimeout(() => {
                        statusSpan.classList.remove('visible');
                    }, 2000);
                }
            })
            .catch(error => {
                if (statusSpan) {
                    statusSpan.textContent = 'Error!';
                    statusSpan.className = 'save-status visible error';
                     setTimeout(() => {
                        statusSpan.classList.remove('visible');
                    }, 3000);
                }
                console.error('Error saving setting:', error);
            });
        });
    }

    // --- Logic for the main "Save Time/Day Settings" button ---
    if (saveTimeDaySettingsBtn && timeDayRestrictionsForm) {
        saveTimeDaySettingsBtn.addEventListener('click', function(event) {
            event.preventDefault();

            const originalButtonHtml = this.innerHTML;
            this.disabled = true;
            this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
            
            const formData = new FormData(timeDayRestrictionsForm);
            
            fetch(timeDayRestrictionsForm.getAttribute('action'), {
                method: 'POST',
                body: new URLSearchParams(formData)
            })
            .then(response => {
                return response.json().then(data => ({ ok: response.ok, data }));
            })
            .then(({ ok, data }) => {
                const message = data.message || (ok ? 'Settings saved successfully!' : 'An unknown error occurred.');
                const type = ok ? 'success' : 'error';
                const title = ok ? 'Success' : 'Error';
                
                if (typeof showPageNotification === 'function') {
                    showPageNotification(message, type, null, title);
                } else {
                    alert(message);
                }
            })
            .catch(error => {
                console.error('Error submitting form:', error);
                if (typeof showPageNotification === 'function') {
                    showPageNotification('A network error occurred while saving. Please check your connection and try again.', 'error', null, 'Network Error');
                } else {
                    alert('A network error occurred. Please try again.');
                }
            })
            .finally(() => {
                this.disabled = false;
                this.innerHTML = originalButtonHtml;
            });
        });
    }

    // --- Logic for "Apply to All" ---
    if (applyToAllToggle && masterRow) {
        const individualDayBlocks = document.querySelectorAll('.day-restriction-block');
        
        applyToAllToggle.addEventListener('change', function() {
            const isEnabled = this.checked;
            masterRow.style.display = isEnabled ? 'flex' : 'none';
            individualDayBlocks.forEach(block => {
                block.style.display = isEnabled ? 'none' : 'block';
            });

            individualDayBlocks.forEach(block => {
                const checkbox = block.querySelector('input[type="checkbox"]');
                if (checkbox) {
                    checkbox.checked = isEnabled;
                    checkbox.dispatchEvent(new Event('change'));
                }
            });
        });
    }
    
    // --- Logic to sync "Apply to All" inputs to individual day inputs ---
    const masterStartTime = document.getElementById('masterStartTime');
    const masterEndTime = document.getElementById('masterEndTime');

    function syncMasterToAll() {
        const startTime = masterStartTime.value;
        const endTime = masterEndTime.value;
        document.querySelectorAll('.day-restriction-block .time-input-field').forEach(input => {
            if (input.id.includes('startTime')) {
                input.value = startTime;
            } else if (input.id.includes('endTime')) {
                input.value = endTime;
            }
        });
    }
    
    if (masterStartTime) masterStartTime.addEventListener('input', syncMasterToAll);
    if (masterEndTime) masterEndTime.addEventListener('input', syncMasterToAll);
    
    // --- MODIFIED: Logic to enable/disable time inputs based on the day's restriction toggle ---
    document.querySelectorAll('.day-restriction-block input[type="checkbox"]').forEach(toggle => {
        const day = toggle.id.split('_')[1];
        const timeInputsGroup = document.getElementById(`timeInputsGroup_${day}`);
        const timeInputs = timeInputsGroup ? timeInputsGroup.querySelectorAll('input[type="time"]') : [];

        function updateTimeInputsState() {
            const isEnabled = toggle.checked;
            timeInputsGroup.style.opacity = isEnabled ? '1' : '0.5';
            timeInputs.forEach(input => {
                input.disabled = !isEnabled;
                // The line that cleared the input value when disabled has been removed.
            });
        }
        
        toggle.addEventListener('change', updateTimeInputsState);
        updateTimeInputsState(); // Run on page load
    });
});