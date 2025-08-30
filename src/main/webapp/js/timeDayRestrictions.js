// js/timeDayRestrictions.js

/**
 * [UPDATED] Saves a single setting and shows a toast. It now gets the URL directly
 * from the element's data-save-url attribute for reliability.
 * @param {HTMLInputElement} element The input element to save.
 */
function saveSingleSettingWithToast(element) {
    const key = element.name;
    const value = element.checked.toString();
    const url = element.dataset.saveUrl; // <-- Read the URL from the data attribute

    if (!key) {
        console.error('Cannot save setting: Element is missing a name attribute.');
        return;
    }
    // Add a check to make sure the URL was found
    if (!url) {
        console.error('Cannot save setting: The data-save-url attribute is missing on the element.');
        if (typeof window.showToast === 'function') {
            window.showToast('Configuration Error: Save URL not found.', 'error');
        }
        return;
    }

    const formData = new URLSearchParams();
    formData.append('settingKey', key);
    formData.append('settingValue', value);

    // Use the URL from the element's data attribute
    fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    })
    .then(response => response.text().then(text => ({ ok: response.ok, body: text })))
    .then(result => {
        if (result.ok && result.body === 'OK') {
            if (typeof window.showToast === 'function') {
                window.showToast('Rule updated successfully!', 'success');
            }
        } else {
            // If the body contains HTML (like a 404 page), just show a generic error.
            const errorMessage = result.body.includes('<html') ? 'Save failed. Please check server logs.' : result.body;
            throw new Error(errorMessage);
        }
    })
    .catch(error => {
        console.error('Failed to save setting:', error);
        if (typeof window.showToast === 'function') {
            window.showToast(`Error: ${error.message}`, 'error');
        }
    });
}


/**
 * Toggles the enabled/disabled state of a day's time inputs based on its checkbox.
 * @param {string} day - The name of the day (e.g., "Sunday").
 */
function toggleTimeInputsState(day) {
    const checkbox = document.getElementById('isRestricted_' + day);
    const startTimeInput = document.getElementById('startTime_' + day);
    const endTimeInput = document.getElementById('endTime_' + day);
    const timeInputsGroupDiv = document.getElementById('timeInputsGroup_' + day);

    if (checkbox && startTimeInput && endTimeInput && timeInputsGroupDiv) {
        const isChecked = checkbox.checked;
        const isDisabled = !isChecked;

        startTimeInput.disabled = isDisabled;
        endTimeInput.disabled = isDisabled;
        timeInputsGroupDiv.style.opacity = isChecked ? '1' : '0.5';
    }
}

// ... The rest of this file (from DOMContentLoaded downward) remains exactly the same ...
document.addEventListener('DOMContentLoaded', function() {
    if (typeof window.showPageNotification !== 'function') {
        console.error("FATAL: commonUtils.js with showPageNotification() is not loaded. Modals will not work.");
        window.showPageNotification = (message, isError, cb, title) => alert((title || (isError ? 'Error' : 'Success')) + '\n\n' + message);
    }

    const daysOfWeek = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
    
    const allowUnselectedToggle = document.getElementById('allowUnselectedDays');
    const unselectedDayActionText = document.getElementById('unselectedDayActionText');

    if (allowUnselectedToggle && unselectedDayActionText) {
        allowUnselectedToggle.addEventListener('change', function() {
            unselectedDayActionText.textContent = this.checked 
                ? "Allow punches all day" 
                : "Do not allow punches";
            
            saveSingleSettingWithToast(this);
        });
    }

    const masterToggle = document.getElementById('applyToAllToggle');
    const masterRow = document.getElementById('masterRow');
    const masterStartTime = document.getElementById('masterStartTime');
    const masterEndTime = document.getElementById('masterEndTime');

    if (masterToggle) {
        masterToggle.addEventListener('change', function() {
            const isMasterEnabled = this.checked;
            masterRow.style.display = isMasterEnabled ? 'flex' : 'none';
            daysOfWeek.forEach(day => {
                const dayBlock = document.getElementById('isRestricted_' + day).closest('.day-restriction-block');
                dayBlock.style.display = isMasterEnabled ? 'none' : 'block';
                toggleTimeInputsState(day);
            });
            if (isMasterEnabled) syncAllDaysToMaster();
        });
    }

    function syncAllDaysToMaster() {
        if (!masterToggle || !masterToggle.checked) return;
        const masterStartValue = masterStartTime.value;
        const masterEndValue = masterEndTime.value;
        daysOfWeek.forEach(day => {
            document.getElementById('isRestricted_' + day).checked = true;
            document.getElementById('startTime_' + day).value = masterStartValue;
            document.getElementById('endTime_' + day).value = masterEndValue;
            toggleTimeInputsState(day);
        });
    }

    if (masterStartTime) masterStartTime.addEventListener('change', syncAllDaysToMaster);
    if (masterEndTime) masterEndTime.addEventListener('change', syncAllDaysToMaster);

    daysOfWeek.forEach(day => {
        const checkbox = document.getElementById('isRestricted_' + day);
        if (checkbox) {
            toggleTimeInputsState(day);
            checkbox.addEventListener('change', () => toggleTimeInputsState(day));
        }
    });

    const form = document.getElementById('timeDayRestrictionsForm');
    const saveButton = document.getElementById('saveTimeDaySettingsBtn');

    if (form && saveButton) {
        saveButton.addEventListener('click', function() {
            let validationError = false;
            let firstErrorField = null;
            let errorMessage = '';
            const isMasterEnabled = masterToggle && masterToggle.checked;

            if (isMasterEnabled) {
                if (!masterStartTime.value || !masterEndTime.value) {
                    errorMessage = "When 'Apply to All' is enabled, 'From' and 'To' times are required.";
                    firstErrorField = masterStartTime;
                    validationError = true;
                } else if (masterStartTime.value >= masterEndTime.value) {
                    errorMessage = "'From' time must be before 'To' time.";
                    firstErrorField = masterStartTime;
                    validationError = true;
                }
            } else {
                for (const day of daysOfWeek) {
                    const restrictCheckbox = document.getElementById('isRestricted_' + day);
                    if (restrictCheckbox && restrictCheckbox.checked) {
                        const startTimeInput = document.getElementById('startTime_' + day);
                        const endTimeInput = document.getElementById('endTime_' + day);
                        if (!startTimeInput.value) { 
                            errorMessage = `Error for ${day}: 'From' time is required when restriction is enabled.`;
                            firstErrorField = startTimeInput; 
                            validationError = true; 
                        } else if (!endTimeInput.value) { 
                            errorMessage = `Error for ${day}: 'To' time is required when restriction is enabled.`;
                            firstErrorField = endTimeInput; 
                            validationError = true; 
                        } else if (startTimeInput.value >= endTimeInput.value) { 
                            errorMessage = `Error for ${day}: 'From' time must be before 'To' time.`;
                            firstErrorField = startTimeInput; 
                            validationError = true; 
                        }
                    }
                    if (validationError) break;
                }
            }

            if (validationError) { 
                const modal = document.getElementById('notificationModalGeneral');
                const okButton = document.getElementById('okButtonNotificationModalGeneral');
                const closeButton = modal ? modal.querySelector('.close') : null;

                const newOkButton = okButton.cloneNode(true);
                okButton.parentNode.replaceChild(newOkButton, okButton);
                
                let newCloseButton;
                if (closeButton) {
                    newCloseButton = closeButton.cloneNode(true);
                    closeButton.parentNode.replaceChild(newCloseButton, closeButton);
                }

                const closeModalAction = () => {
                    if (typeof window.hideModal === 'function') {
                        window.hideModal(modal);
                    }
                    if (firstErrorField) {
                        firstErrorField.focus();
                    }
                };

                newOkButton.addEventListener('click', closeModalAction, { once: true });
                if (newCloseButton) {
                    newCloseButton.addEventListener('click', closeModalAction, { once: true });
                }

                window.showPageNotification(errorMessage, true, null, 'Validation Error');
                return;
            }

            saveButton.disabled = true;
            saveButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
            
            const formData = new FormData(form);
            const url = form.getAttribute('action');

            fetch(url, {
                method: 'POST',
                body: new URLSearchParams(formData)
            })
            .then(response => {
                if (!response.ok) {
                    return response.json().catch(() => response.text()).then(errorBody => {
                        const message = errorBody.message || errorBody || `Server responded with status ${response.status}`;
                        throw new Error(message);
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    const redirectUrl = form.dataset.cancelUrl || 'settings.jsp';
                    const okButton = document.getElementById('okButtonNotificationModalGeneral');
                    
                    const newOkButton = okButton.cloneNode(true);
                    okButton.parentNode.replaceChild(newOkButton, okButton);
                    
                    newOkButton.addEventListener('click', function redirectCallback() {
                        window.location.href = redirectUrl;
                    }, { once: true });
                    
                    window.showPageNotification(data.message, false, null, 'Success');
                } else {
                    throw new Error(data.message || 'An unknown server error occurred.');
                }
            })
            .catch(error => {
                window.showPageNotification(error.message, true, null, 'Save Failed');
                saveButton.disabled = false;
                saveButton.innerHTML = '<i class="fas fa-save"></i> Save Time/Day Settings';
            });
        });
    }
});