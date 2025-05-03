/**
 * settings.js
 * Handles saving settings via AJAX for settings.jsp
 * Handles enabling/disabling dependent fields.
 * Threshold input retains value when disabled/enabled.
 */

// --- Helper: Function to save settings ---
function saveSetting(element) {
    let key = element.name;
    let value;
    if (!key) { console.error("Element is missing name attribute:", element); return; }

    if (element.type === 'checkbox') { value = element.checked.toString(); console.log(`Checkbox: Key=${key}, Checked=${element.checked}, Val=${value}`); }
    else if (element.type === 'radio') { if (element.checked) { value = element.value; console.log(`Radio: Key=${key}, Val=${value}`); } else { return; } }
    else { value = element.value; console.log(`Input/Select: Key=${key}, Val=${value}`); }

    const settingItem = element.closest('.setting-item');
    let statusElement = settingItem ? settingItem.querySelector('.save-status') : null;
    if (!statusElement && (element.tagName === 'SELECT' || element.type === 'number')) { statusElement = document.getElementById(element.id + '-status'); }
    let showStatus = (element.tagName === 'SELECT' || element.type === 'number'); // Show status for selects and threshold

    if (showStatus && statusElement) { statusElement.textContent = 'Saving...'; statusElement.className = 'save-status visible'; statusElement.classList.remove('error'); }
    const formData = new URLSearchParams();
    formData.append('settingKey', key);
    formData.append('settingValue', value);

    fetch('saveSetting', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded', }, body: formData })
    .then(response => response.text().then(text => ({ ok: response.ok, body: text, statusText: response.statusText })))
    .then(result => {
         if (showStatus && statusElement) {
             if (result.ok && result.body === 'OK') { statusElement.textContent = 'Saved!'; statusElement.className = 'save-status visible'; }
             else { statusElement.textContent = result.body.startsWith('Error:') ? result.body : `Error: ${result.statusText}`; statusElement.className = 'save-status visible error'; }
             setTimeout(() => { statusElement.className = 'save-status'; }, 3000);
         } else if (!result.ok || result.body !== 'OK') { console.error("Error saving setting:", key, result.body || result.statusText); }
    })
    .catch(error => { console.error('Network Error saving setting:', key, error); if (showStatus && statusElement) { statusElement.textContent = 'Network Error!'; statusElement.className = 'save-status visible error'; setTimeout(() => { statusElement.className = 'save-status'; }, 5000); } });
}

// --- Helper: Toggle Enable/Disable State of Rate Radio Buttons ---
function toggleRateRadios(enableCheckbox, rateGroupName) {
     if (!enableCheckbox) return;
    const isEnabled = enableCheckbox.checked;
    console.log(`Toggling ${rateGroupName} radios based on ${enableCheckbox.id}. Enabled: ${isEnabled}`);
    const radios = document.querySelectorAll(`input[type="radio"][name="${rateGroupName}"]`);
    radios.forEach(radio => {
        radio.disabled = !isEnabled;
        const label = document.querySelector(`label[for="${radio.id}"]`); // Find corresponding label
        if(label) {
             label.style.color = isEnabled ? '#333' : '#999';
             label.style.cursor = isEnabled ? 'pointer' : 'not-allowed';
         }
    });
}

// --- Helper: Toggle Enable/Disable/Required State of Threshold Input ---
// *** MODIFIED: Removed line that clears value ***
function toggleThresholdInput(enableCheckbox, thresholdInputId) {
    if (!enableCheckbox) return;
    const thresholdInput = document.getElementById(thresholdInputId);
    if (!thresholdInput) { console.warn("Threshold input not found:", thresholdInputId); return; }

    const isEnabled = enableCheckbox.checked;
    console.log(`Toggling ${thresholdInputId}. Enabled: ${isEnabled}. Required: ${isEnabled}`);

    thresholdInput.disabled = !isEnabled;
    thresholdInput.required = isEnabled; // Still set required attribute dynamically

    // *** REMOVED THIS LINE: if (!isEnabled) { thresholdInput.value = ''; } ***
    // Now the value persists when the field is disabled/re-enabled
}


// --- Main execution after DOM is loaded ---
document.addEventListener('DOMContentLoaded', function() {
    console.log("Settings Page DOMContentLoaded");

    // --- Get Element References ---
    // !!! Replace ALL these IDs with your ACTUAL element IDs if different !!!
    const payPeriodSelect = document.getElementById('payPeriod');
    const firstDaySelect = document.getElementById('firstDay');
    const gracePeriodSelect = document.getElementById('gracePeriod');
    const overtimeCheckbox = document.getElementById('overtime');
    const holidayPayCheckbox = document.getElementById('holidayPay');
    const overtimeDailyCheckbox = document.getElementById('overtimeDaily');
    const overtimeDailyThresholdInput = document.getElementById('overtimeDailyThreshold');
    const overtimeRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeRate"]');
    const holidayPayRateRadios = document.querySelectorAll('input[type="radio"][name="HolidayPayRate"]');
    // Notification Modal elements (assuming IDs exist on page)
    const notificationModal = document.getElementById("notificationModal");
    const notificationMessage = document.getElementById("notificationMessage");
    const closeNotification = document.getElementById("closeNotificationModal");
    const okButton = document.getElementById("okButton");


    // --- Notification Modal Function ---
    function showNotification(message) { /* ... same as before ... */ }
    // --- Helper function to clear URL params ---
    function clearUrlParams() { /* ... same as before ... */ }
    // --- Notification Handling from Server Redirects ---
    const urlParams = new URLSearchParams(window.location.search);
    // ... Add/Edit/Delete success/error handling logic ...


    // --- Attach Listeners using JavaScript ---
    console.log("Attaching listeners for settings...");
    if (payPeriodSelect) payPeriodSelect.addEventListener('change', function() { saveSetting(this); });
    if (firstDaySelect) firstDaySelect.addEventListener('change', function() { saveSetting(this); });
    if (gracePeriodSelect) gracePeriodSelect.addEventListener('change', function() { saveSetting(this); });

    if (overtimeCheckbox) { overtimeCheckbox.addEventListener('change', function() { saveSetting(this); /* No toggle needed for OT Rate */ }); console.log("Listener attached to overtime checkbox."); }
    else { console.warn("Overtime checkbox not found."); }

    overtimeRateRadios.forEach(radio => { radio.addEventListener('change', function() { if (this.checked) saveSetting(this); }); });
    if (overtimeRateRadios.length > 0) console.log("Listeners attached to overtime rate radios.");

    if (overtimeDailyCheckbox) { overtimeDailyCheckbox.addEventListener('change', function() { saveSetting(this); toggleThresholdInput(this, 'overtimeDailyThreshold'); }); console.log("Listener attached to overtimeDaily checkbox."); }
    else { console.warn("OvertimeDaily checkbox not found."); }

    if (overtimeDailyThresholdInput) { overtimeDailyThresholdInput.addEventListener('change', function() { saveSetting(this); }); console.log("Listener attached to overtimeDailyThreshold input."); }
    else { console.warn("OvertimeDailyThreshold input not found."); }

    if (holidayPayCheckbox) { holidayPayCheckbox.addEventListener('change', function() { saveSetting(this); toggleRateRadios(this, 'HolidayPayRate'); }); console.log("Listener attached to holidayPay checkbox."); }
    else { console.warn("HolidayPay checkbox not found."); }

    holidayPayRateRadios.forEach(radio => { radio.addEventListener('change', function() { if (this.checked) saveSetting(this); }); });
    if (holidayPayRateRadios.length > 0) console.log("Listeners attached to holiday pay rate radios.");

     // --- Event Listeners: Notification Modal Closing (Optional) ---
     if (notificationModal && closeNotification) closeNotification.addEventListener("click", function() { if(typeof hideModal === 'function') hideModal(notificationModal); });
     if (okButton) okButton.addEventListener("click", function() { if(notificationModal && typeof hideModal === 'function') hideModal(notificationModal); });

    console.log("Finished attaching listeners.");

    // --- Set Initial Enabled/Disabled/Required States ---
    console.log("Setting initial disabled/required states...");
     if (holidayPayCheckbox) { // Keep initial toggle for Holiday Rate
        toggleRateRadios(holidayPayCheckbox, 'HolidayPayRate');
     } else { console.warn("Holiday Pay checkbox not found for initial state."); }
    if (overtimeDailyCheckbox) { // Keep initial toggle for Daily Threshold
        toggleThresholdInput(overtimeDailyCheckbox, 'overtimeDailyThreshold');
    } else { console.warn("Daily Overtime checkbox not found for initial state."); }
    console.log("Initial disabled/required states set.");

    console.log("Settings Page DOMContentLoaded setup complete.");
}); // End DOMContentLoaded