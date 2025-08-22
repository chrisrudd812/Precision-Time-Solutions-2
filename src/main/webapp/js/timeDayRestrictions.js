// js/timeDayRestrictions.js

// Toggles the enabled/disabled state of a day's time inputs based on its checkbox
function toggleTimeInputsState(day, isControlledByMaster) {
    const checkbox = document.getElementById('isRestricted_' + day);
    const startTimeInput = document.getElementById('startTime_' + day);
    const endTimeInput = document.getElementById('endTime_' + day);
    const timeInputsGroupDiv = document.getElementById('timeInputsGroup_' + day);

    if (checkbox && startTimeInput && endTimeInput && timeInputsGroupDiv) {
        const isChecked = checkbox.checked;
        
        // MODIFIED: Do NOT disable the inputs if the master toggle is controlling them.
        // They need to be enabled to submit their values.
        const isDisabled = !isChecked;

        startTimeInput.disabled = isDisabled;
        endTimeInput.disabled = isDisabled;
        
        // Requirement should only be enforced by client-side validation, not the disabled attribute
        startTimeInput.required = isChecked;
        endTimeInput.required = isChecked;
        
        timeInputsGroupDiv.style.opacity = isChecked ? '1' : '0.5';
    }
}

// Main function to run after the page loads
document.addEventListener('DOMContentLoaded', function() {
    const daysOfWeek = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
    
    // --- Master "Apply to All" Toggle Logic ---
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
                // Update the state of the inputs (don't disable if master is on)
                toggleTimeInputsState(day, isMasterEnabled);
            });

            if (isMasterEnabled) {
                // When turning on, sync all days to the master values
                syncAllDaysToMaster();
            }
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
            // We still need to call this to update opacity, etc.
            toggleTimeInputsState(day, true);
        });
    }

    if (masterStartTime) masterStartTime.addEventListener('change', syncAllDaysToMaster);
    if (masterEndTime) masterEndTime.addEventListener('change', syncAllDaysToMaster);


    // --- Individual Day Toggle Initialization & Form Validation ---
    daysOfWeek.forEach(day => {
        const checkbox = document.getElementById('isRestricted_' + day);
        if (checkbox) {
            // Set initial state on page load
            toggleTimeInputsState(day, false);
            // Add event listener to each checkbox
            checkbox.addEventListener('change', () => toggleTimeInputsState(day, false));
        }
    });

    const form = document.getElementById('timeDayRestrictionsForm');
    if (form) {
        form.addEventListener('submit', function(event) {
            let validationError = false;
            let firstErrorField = null;
            const isMasterEnabled = masterToggle && masterToggle.checked;

            // When master is enabled, it syncs to the hidden fields, which are what get submitted.
            // The servlet will validate them. We just need to validate the master fields on the client.
            if (isMasterEnabled) {
                if (!masterStartTime.value || !masterEndTime.value) {
                    alert("When 'Apply to All' is enabled, 'From' and 'To' times are required.");
                    validationError = true;
                    firstErrorField = masterStartTime;
                }
                if (masterStartTime.value && masterEndTime.value && masterStartTime.value >= masterEndTime.value) {
                    alert("'From' time must be before 'To' time.");
                    validationError = true;
                    firstErrorField = masterStartTime;
                }
            } else {
                // If master is not enabled, validate each individual active row
                daysOfWeek.forEach(day => {
                    const restrictCheckbox = document.getElementById('isRestricted_' + day);
                    if (restrictCheckbox && restrictCheckbox.checked) {
                        const startTimeInput = document.getElementById('startTime_' + day);
                        const endTimeInput = document.getElementById('endTime_' + day);
                        if (!startTimeInput.value) { 
                            alert("Error for " + day + ": 'From' time is required when restriction is enabled."); 
                            if (!firstErrorField) firstErrorField = startTimeInput; 
                            validationError = true; 
                        }
                        if (!endTimeInput.value) { 
                            alert("Error for " + day + ": 'To' time is required when restriction is enabled."); 
                            if (!firstErrorField) firstErrorField = endTimeInput; 
                            validationError = true; 
                        }
                        if (startTimeInput.value && endTimeInput.value && startTimeInput.value >= endTimeInput.value) { 
                            alert("Error for " + day + ": 'From' time must be before 'To' time."); 
                            if (!firstErrorField) firstErrorField = startTimeInput; 
                            validationError = true; 
                        }
                    }
                });
            }

            if (validationError) { 
                event.preventDefault(); 
                if (firstErrorField) { setTimeout(() => { firstErrorField.focus(); }, 50); }
            } else {
                const submitButton = document.getElementById('saveTimeDaySettingsBtn');
                if(submitButton) {
                     submitButton.disabled = true;
                     submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
                }
            }
        });
    }
});