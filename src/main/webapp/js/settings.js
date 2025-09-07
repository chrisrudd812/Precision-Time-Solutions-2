// settings.js

// --- START: State Overtime Rules Data ---
const FLSA_DEFAULTS = { key: "FLSA", dailyOTEnabled: false, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: 12.0, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "Follows Federal FLSA: Overtime at 1.5x pay after 40 hours in a workweek. No daily or 7th day overtime requirement under FLSA." };
const stateOvertimeRules = { "FLSA": FLSA_DEFAULTS, "CA": { key: "CA", dailyOTEnabled: true, dailyOTThreshold: 8.0, doubleTimeEnabled: true, doubleTimeThreshold: 12.0, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: true, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "CA: Daily OT > 8h (1.5x), >12h (2x). 7th Consecutive Day of workweek: First 8h (1.5x), hours > 8 (2x). Weekly OT > 40h (1.5x) (non-duplicative). Many exceptions apply." }, "AK": { key: "AK", dailyOTEnabled: true, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: true, seventhDayOTThreshold: 0, seventhDayDTThreshold: null, notes: "AK: Daily OT > 8h or Weekly > 40h at 1.5x. All hours worked on the 7th consecutive day of the workweek are OT. Check AK DOL." }, "NV": { key: "NV", dailyOTEnabled: true, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "NV: Daily OT > 8h (in 24hr) OR > 40h/wk at 1.5x, IF employee earns < 1.5x state min wage. Check NV DOL." }, "NY": { key: "NY", dailyOTEnabled: false, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "NY: Generally FLSA. Industry-specific wage orders may differ." }, "CO": { key: "CO", dailyOTEnabled: true, dailyOTThreshold: 12.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "CO: OT after 40h/wk, or 12 consecutive hrs." }, "OR": { key: "OR", dailyOTEnabled: true, dailyOTThreshold: 10.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "OR: OT > 40h/wk. Daily OT (>10h) for some manufacturing/canneries." }, "WA": { key: "WA", dailyOTEnabled: false, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "WA: Generally FLSA. High salary thresholds for OT exemption." }, "TX": { ...FLSA_DEFAULTS, key: "TX", notes: FLSA_DEFAULTS.notes.replace("FLSA", "TX follows FLSA") }, "AL": { ...FLSA_DEFAULTS, key: "AL", notes: FLSA_DEFAULTS.notes.replace("FLSA", "AL follows FLSA") }};
const usStates = [ { name: 'Federal (FLSA Default)', code: 'FLSA', ruleKey: 'FLSA' }, { name: 'Alabama', code: 'AL', ruleKey: 'AL' }, { name: 'Alaska', code: 'AK', ruleKey: 'AK' }, { name: 'Arizona', code: 'AZ', ruleKey: 'AZ' }, { name: 'Arkansas', code: 'AR', ruleKey: 'AR' }, { name: 'California', code: 'CA', ruleKey: 'CA' }, { name: 'Colorado', code: 'CO', ruleKey: 'CO' }, { name: 'Connecticut', code: 'CT', ruleKey: 'CT' }, { name: 'Delaware', code: 'DE', ruleKey: 'DE' }, { name: 'District of Columbia', code: 'DC', ruleKey: 'DC' }, { name: 'Florida', code: 'FL', ruleKey: 'FL' }, { name: 'Georgia', code: 'GA', ruleKey: 'GA' }, { name: 'Hawaii', code: 'HI', ruleKey: 'HI' }, { name: 'Idaho', code: 'ID', ruleKey: 'ID' }, { name: 'Illinois', code: 'IL', ruleKey: 'IL' }, { name: 'Indiana', code: 'IN', ruleKey: 'IN' }, { name: 'Iowa', code: 'IA', ruleKey: 'IA' }, { name: 'Kansas', code: 'KS', ruleKey: 'KS' }, { name: 'Kentucky', code: 'KY', ruleKey: 'KY' }, { name: 'Louisiana', code: 'LA', ruleKey: 'LA' }, { name: 'Maine', code: 'ME', ruleKey: 'ME' }, { name: 'Maryland', code: 'MD', ruleKey: 'MD' }, { name: 'Massachusetts', code: 'MA', ruleKey: 'MA' }, { name: 'Michigan', code: 'MI', ruleKey: 'MI' }, { name: 'Minnesota', code: 'MN', ruleKey: 'MN' }, { name: 'Mississippi', code: 'MS', ruleKey: 'MS' }, { name: 'Missouri', code: 'MO', ruleKey: 'MO' }, { name: 'Montana', code: 'MT', ruleKey: 'MT' }, { name: 'Nebraska', code: 'NE', ruleKey: 'NE' }, { name: 'Nevada', code: 'NV', ruleKey: 'NV' }, { name: 'New Hampshire', code: 'NH', ruleKey: 'NH' }, { name: 'New Jersey', code: 'NJ', ruleKey: 'NJ' }, { name: 'New Mexico', code: 'NM', ruleKey: 'NM' }, { name: 'New York', code: 'NY', ruleKey: 'NY' }, { name: 'North Carolina', code: 'NC', ruleKey: 'NC' }, { name: 'North Dakota', code: 'ND', ruleKey: 'ND' }, { name: 'Ohio', code: 'OH', ruleKey: 'OH' }, { name: 'Oklahoma', code: 'OK', ruleKey: 'OK' }, { name: 'Oregon', code: 'OR', ruleKey: 'OR' }, { name: 'Pennsylvania', code: 'PA', ruleKey: 'PA' }, { name: 'Rhode Island', code: 'RI', ruleKey: 'RI' }, { name: 'South Carolina', code: 'SC', ruleKey: 'SC' }, { name: 'South Dakota', code: 'SD', ruleKey: 'SD' }, { name: 'Tennessee', code: 'TN', ruleKey: 'TN' }, { name: 'Texas', code: 'TX', ruleKey: 'TX' }, { name: 'Utah', code: 'UT', ruleKey: 'UT' }, { name: 'Vermont', code: 'VT', ruleKey: 'VT' }, { name: 'Virginia', code: 'VA', ruleKey: 'VA' }, { name: 'Washington', code: 'WA', ruleKey: 'WA' }, { name: 'West Virginia', code: 'WV', ruleKey: 'WV' }, { name: 'Wisconsin', code: 'WI', ruleKey: 'WI' }, { name: 'Wyoming', code: 'WY', ruleKey: 'WY' } ];
// --- END: State Overtime Rules Data ---

function saveSetting(element, valueToSave) {
    let key = element.name;
    let value = valueToSave !== undefined ? valueToSave : element.value; 

    if (!key && element.id) key = element.id; 
    if (!key) { console.error("Save Setting Error: Element missing name/id:", element); return; }

    // --- NEW FIX ---
    // Do not attempt to save empty values for fields that require numbers on the backend.
    if (key.includes("Threshold") || key.endsWith("Rate")) {
        if (value === null || String(value).trim() === '') {
            console.log(`[saveSetting] Skipped saving empty value for numeric key: ${key}`);
            return; // Exit the function early
        }
    }
    // --- END FIX ---

    if (element.type === 'checkbox' && valueToSave === undefined) {
        value = element.checked.toString();
    } else if (element.type === 'radio' && valueToSave === undefined) {
        if (!element.checked && element.name) {
            const checkedRadio = document.querySelector(`input[type="radio"][name="${element.name}"]:checked`);
            if(checkedRadio && checkedRadio !== element) return; 
            else if (!checkedRadio) return; 
        }
        value = element.value;
    }
    
    let statusElement = document.getElementById(key + '-status') || (element.id ? document.getElementById(element.id + '-status') : null);
    if (!statusElement && element.parentElement) {
        statusElement = element.parentElement.querySelector('.save-status');
        if (!statusElement && element.parentElement.parentElement) {
            statusElement = element.parentElement.parentElement.querySelector('.save-status');
        }
    }

    if (statusElement) {
        statusElement.textContent = 'Saving...'; statusElement.className = 'save-status visible';
        let isSpecialGroup = element.name && (element.name.includes('Rate') || element.name.includes('PayPeriodType') || element.name.includes('GracePeriod') || element.name === 'OvertimeRuleMode' || element.name === 'OvertimeState' || element.name === 'FirstDayOfWeek' || element.name === 'PayPeriodStartDate');
        if (element.id && (element.id.includes('Threshold') || element.id.includes('StartDate'))) isSpecialGroup = true;
        if (key === 'PayPeriodEndDate') isSpecialGroup = true;
        if (element.type === 'checkbox') statusElement.classList.add('checkbox-status');
        else if (isSpecialGroup) { statusElement.classList.add('radio-group-status'); }
        statusElement.classList.remove('error', 'info');
    }

    const formData = new URLSearchParams();
    formData.append('settingKey', key);
    formData.append('settingValue', value);
    
    fetch(`${window.appRootPath}/SettingsServlet`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: formData
    })
    .then(response => response.text().then(text => ({ ok: response.ok, status: response.status, body: text })))
    .then(result => {
         if (statusElement) {
             let baseClass = 'save-status visible';
             if (statusElement.classList.contains('checkbox-status')) baseClass += ' checkbox-status';
             if (statusElement.classList.contains('radio-group-status')) baseClass += ' radio-group-status';
             if (result.ok && result.body === 'OK') {
                 statusElement.textContent = 'Saved!'; statusElement.className = baseClass;
             } else {
                 let errorMsg = result.body && result.body.startsWith('Error:') ? result.body.substring(6).trim() : (result.body || `Save failed. Status: ${result.status}`);
                 statusElement.textContent = errorMsg; statusElement.className = baseClass + ' error';
                 console.error("Error saving " + key + ":", errorMsg);
             }
             setTimeout(() => {
                statusElement.className = 'save-status';
                if (baseClass.includes('checkbox-status')) statusElement.classList.add('checkbox-status');
                if (baseClass.includes('radio-group-status')) statusElement.classList.add('radio-group-status');
            }, 3000);
         } else if (!result.ok || result.body !== 'OK') {
             console.error("Error saving " + key + " (no status element):", result.body || result.status);
         }
    })
    .catch(error => {
        console.error('Network Error saving setting:', key, error);
        if (statusElement) {
            let baseClass = 'save-status visible error';
            if (statusElement.classList.contains('checkbox-status')) baseClass += ' checkbox-status';
            if (statusElement.classList.contains('radio-group-status')) baseClass += ' radio-group-status';
            statusElement.textContent = 'Network Error!'; statusElement.className = baseClass;
        }
    });
}

function toggleThresholdInput(enableCheckbox, thresholdInputId, detailsBlockId = null) {
    if (!enableCheckbox) { return; }
    const thresholdInput = document.getElementById(thresholdInputId);
    const detailsBlock = detailsBlockId ? document.getElementById(detailsBlockId) : null; 
    const isEnabledByCheckbox = enableCheckbox.checked;
    const isManualModeActive = document.getElementById('otModeManual') ? document.getElementById('otModeManual').checked : true; 

    if (thresholdInput) {
        thresholdInput.disabled = !(isManualModeActive && isEnabledByCheckbox);
    }
    
    if (detailsBlock) { 
        const shouldDisplay = (document.getElementById('otModeAuto')?.checked && isEnabledByCheckbox) || (isManualModeActive && isEnabledByCheckbox);
        detailsBlock.style.display = shouldDisplay ? 'flex' : 'none';
        detailsBlock.querySelectorAll('input[type="number"], select').forEach(input => {
            input.disabled = !isManualModeActive || !isEnabledByCheckbox;
        });
    }
}

document.addEventListener('DOMContentLoaded', function() {
    console.log("Settings Page DOMContentLoaded");

    // --- Element References ---
    const payPeriodTypeSelect = document.getElementById('payPeriodType'), firstDayOfWeekSelect = document.getElementById('firstDayOfWeek'), payPeriodStartDateInput = document.getElementById('payPeriodStartDate'), payPeriodEndDateDisplaySpan = document.getElementById('payPeriodEndDateDisplay');
    const firstDayOfWeekBlock = document.getElementById('firstDayOfWeekBlock'), payPeriodStartDateBlock = document.getElementById('payPeriodStartDateBlock'), payPeriodEndDateBlock = document.getElementById('payPeriodEndDateBlock'); 
    const firstDayOfWeekNote = document.getElementById('firstDayOfWeekNote');
    const gracePeriodSelect = document.getElementById('gracePeriod');
    const otModeManualRadio = document.getElementById('otModeManual'), otModeAutoRadio = document.getElementById('otModeAuto'), autoStateOvertimeSection = document.getElementById('autoStateOvertimeSection'), overtimeStateSelect = document.getElementById('overtimeStateSelect'), manualOvertimeSettingsDiv = document.getElementById('manualOvertimeSettings'), stateSpecificNotesDisplay = document.getElementById('stateSpecificNotesDisplay');
    const overtimeRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeRate"]'), overtimeDailyCheckbox = document.getElementById('overtimeDaily'), overtimeDailyThresholdInput = document.getElementById('overtimeDailyThreshold'), overtimeDoubleTimeEnabledCheckbox = document.getElementById('overtimeDoubleTimeEnabled'), overtimeDoubleTimeThresholdInput = document.getElementById('overtimeDoubleTimeThreshold');
    const overtimeSeventhDayEnabledCheckbox = document.getElementById('overtimeSeventhDayEnabled'), seventhDayOTDetailsBlock = document.getElementById('seventhDayOTDetailsBlock'), overtimeSeventhDayOTThresholdInput = document.getElementById('overtimeSeventhDayOTThreshold'), overtimeSeventhDayDTThresholdInput = document.getElementById('overtimeSeventhDayDTThreshold');
    
    // --- Populate State Dropdown ---
    if (overtimeStateSelect) {
        usStates.forEach(state => {
            const option = document.createElement('option');
            option.value = state.code; option.textContent = state.name;
            overtimeStateSelect.appendChild(option);
        });
    }

	function calculateAndDisplayPayPeriodEndDate() {
	    if (!payPeriodTypeSelect || !payPeriodStartDateInput || !payPeriodEndDateDisplaySpan || !firstDayOfWeekSelect) return;
	    
	    const periodType = payPeriodTypeSelect.value;
	    const today = new Date();
	    today.setHours(0, 0, 0, 0);
	    let startDate, endDate;
	    let newStartDateSet = false;

        // --- MODIFIED: This function now correctly uses UTC getters to prevent timezone errors ---
	    const toISODateString = (date) => {
            // Using getUTC methods ensures the date is formatted based on its UTC value,
            // which matches how it was created with new Date(Date.UTC(...)). This prevents off-by-one day errors.
	        return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(date.getUTCDate()).padStart(2, '0')}`;
	    };

	    if (periodType === "Daily") {
	        firstDayOfWeekBlock.style.display = 'none';
	        firstDayOfWeekNote.style.display = 'none';
	        payPeriodStartDateBlock.style.display = 'flex';
	        payPeriodEndDateBlock.style.display = 'flex';
	        const todayISO = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
	        payPeriodStartDateInput.value = todayISO;
	        payPeriodEndDateDisplaySpan.textContent = today.toLocaleDateString('en-US', { year: 'numeric', month: '2-digit', day: '2-digit' });
	        saveSetting(payPeriodStartDateInput);
	        saveSetting({ name: "PayPeriodEndDate", type: "hidden" }, todayISO);
	        return; 
	    } else {
	        firstDayOfWeekBlock.style.display = 'flex';
	        firstDayOfWeekNote.style.display = 'block';
	        payPeriodStartDateBlock.style.display = 'flex';
	        payPeriodEndDateBlock.style.display = 'flex';
	    }

	    let startDateStr = payPeriodStartDateInput.value;

	    switch (periodType) {
	        case "Semi-Monthly":
	            const currentDay = today.getDate();
	            if (currentDay <= 15) {
	                startDate = new Date(Date.UTC(today.getFullYear(), today.getMonth(), 1));
	                endDate = new Date(Date.UTC(today.getFullYear(), today.getMonth(), 15));
	            } else {
	                startDate = new Date(Date.UTC(today.getFullYear(), today.getMonth(), 16));
                    // The '0' day of the next month gives the last day of the current month
	                endDate = new Date(Date.UTC(today.getFullYear(), today.getMonth() + 1, 0));
	            }
	            payPeriodStartDateInput.value = toISODateString(startDate);
	            newStartDateSet = true;
	            break;
	        
	        case "Monthly":
	            startDate = new Date(Date.UTC(today.getFullYear(), today.getMonth(), 1));
	            endDate = new Date(Date.UTC(today.getFullYear(), today.getMonth() + 1, 0));
	            payPeriodStartDateInput.value = toISODateString(startDate);
	            newStartDateSet = true;
	            break;

	        default: // Weekly, Bi-Weekly
	            if (!startDateStr) {
	                payPeriodEndDateDisplaySpan.textContent = "Set Start Date";
	                return;
	            }
	            let [year, month, day] = startDateStr.split('-').map(Number);
	            startDate = new Date(Date.UTC(year, month - 1, day));
	            if (isNaN(startDate.getTime())) {
	                payPeriodEndDateDisplaySpan.textContent = "Invalid Start";
	                return;
	            }
	            endDate = new Date(startDate.getTime()); // Use getTime() to clone correctly
	            if (periodType === "Weekly") {
	                endDate.setUTCDate(startDate.getUTCDate() + 6);
	            } else if (periodType === "Bi-Weekly") {
	                endDate.setUTCDate(startDate.getUTCDate() + 13);
	            }
	            break;
	    }

	    if (newStartDateSet) {
	        saveSetting(payPeriodStartDateInput);
	    }

	    payPeriodEndDateDisplaySpan.textContent = endDate.toLocaleDateString('en-US', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'UTC' });
	    const endDateToSave = toISODateString(endDate);
	    saveSetting({ name: "PayPeriodEndDate", type: "hidden" }, endDateToSave);
	}
    
    function updateManualFieldsFromState(stateCode, saveDerivedSettings = false) {
        const stateDetails = usStates.find(s => s.code === stateCode) || usStates.find(s => s.code === 'FLSA');
        const rules = stateOvertimeRules[stateDetails.ruleKey] || stateOvertimeRules["FLSA"];
        
        const applyAndSave = (element, value) => {
            if (element) {
                if(element.type === 'checkbox') element.checked = value;
                else element.value = value;
                if (saveDerivedSettings) saveSetting(element, String(value));
            }
        };

        applyAndSave(overtimeDailyCheckbox, rules.dailyOTEnabled);
        applyAndSave(overtimeDailyThresholdInput, rules.dailyOTEnabled ? rules.dailyOTThreshold.toFixed(1) : '');
        applyAndSave(overtimeDoubleTimeEnabledCheckbox, rules.doubleTimeEnabled);
        applyAndSave(overtimeDoubleTimeThresholdInput, rules.doubleTimeEnabled ? rules.doubleTimeThreshold.toFixed(1) : '');
        applyAndSave(overtimeSeventhDayEnabledCheckbox, rules.seventhDayOTEnabled);
        applyAndSave(overtimeSeventhDayOTThresholdInput, rules.seventhDayOTEnabled ? rules.seventhDayOTThreshold.toFixed(1) : '');
        applyAndSave(overtimeSeventhDayDTThresholdInput, rules.seventhDayOTEnabled ? rules.seventhDayDTThreshold.toFixed(1) : '');
        if (stateSpecificNotesDisplay) stateSpecificNotesDisplay.innerHTML = rules.notes.replace(/\n/g, "<br>");
        
        const rateToSet = String(rules.standardOTRate || "1.5");
        overtimeRateRadios.forEach(radio => {
            radio.checked = (radio.value === rateToSet);
            if (saveDerivedSettings && radio.checked) saveSetting(radio);
        });

        toggleThresholdInput(overtimeDailyCheckbox, 'overtimeDailyThreshold');
        toggleThresholdInput(overtimeDoubleTimeEnabledCheckbox, 'overtimeDoubleTimeThreshold');
        toggleThresholdInput(overtimeSeventhDayEnabledCheckbox, 'overtimeSeventhDayOTThreshold', 'seventhDayOTDetailsBlock');
    }

    function updateOvertimeModeUI() {
        const isManualMode = otModeManualRadio && otModeManualRadio.checked;
        if (autoStateOvertimeSection) autoStateOvertimeSection.style.display = isManualMode ? 'none' : 'block';
        if (manualOvertimeSettingsDiv) manualOvertimeSettingsDiv.style.opacity = isManualMode ? 1 : 0.6;
        
        const allManualInputs = document.querySelectorAll('#manualOvertimeSettings input, #manualOvertimeSettings select');
        allManualInputs.forEach(el => {
            if (el.name !== 'Overtime') el.disabled = !isManualMode;
        });

        if (isManualMode) {
            toggleThresholdInput(overtimeDailyCheckbox, 'overtimeDailyThreshold');
            toggleThresholdInput(overtimeDoubleTimeEnabledCheckbox, 'overtimeDoubleTimeThreshold');
            toggleThresholdInput(overtimeSeventhDayEnabledCheckbox, 'overtimeSeventhDayOTThreshold', 'seventhDayOTDetailsBlock');
        } else {
            updateManualFieldsFromState(overtimeStateSelect.value, false);
        }
    }
    
    function initializePage() {
        if(payPeriodTypeSelect) payPeriodTypeSelect.value = window.settingsConfig.payPeriodType;
        if(firstDayOfWeekSelect) firstDayOfWeekSelect.value = window.settingsConfig.firstDayOfWeek;
        if(payPeriodStartDateInput) payPeriodStartDateInput.value = window.settingsConfig.payPeriodStartDate;
        if(gracePeriodSelect) gracePeriodSelect.value = window.settingsConfig.gracePeriod;
        
        if (window.settingsConfig.overtimeRuleMode === "AutoByState") {
            if(otModeAutoRadio) otModeAutoRadio.checked = true;
        } else { 
            if(otModeManualRadio) otModeManualRadio.checked = true;
        }

        if (overtimeStateSelect) overtimeStateSelect.value = window.settingsConfig.overtimeState || "FLSA";

        updateOvertimeModeUI(); 
        
        if (window.inWizardMode_Page && otModeAutoRadio && otModeAutoRadio.checked) {
            updateManualFieldsFromState(overtimeStateSelect.value, true); 
        }
        
        calculateAndDisplayPayPeriodEndDate();
    }

    // --- Initialize and Attach Listeners ---
    initializePage();

    [payPeriodTypeSelect, firstDayOfWeekSelect, payPeriodStartDateInput].forEach(el => {
        if(el) el.addEventListener('change', () => { saveSetting(el); calculateAndDisplayPayPeriodEndDate(); });
    });
    
    if (gracePeriodSelect) {
        gracePeriodSelect.addEventListener('change', function() {
            saveSetting(this);
        });
    }
    
    [otModeManualRadio, otModeAutoRadio].forEach(el => {
        if(el) el.addEventListener('change', () => { if(el.checked) { saveSetting(el); updateOvertimeModeUI(); }});
    });

    if (overtimeStateSelect) {
        overtimeStateSelect.addEventListener('change', function() {
            saveSetting(this); 
            updateManualFieldsFromState(this.value, true); 
        });
    }

    [overtimeDailyCheckbox, overtimeDoubleTimeEnabledCheckbox, overtimeSeventhDayEnabledCheckbox].forEach(el => {
        if(el) el.addEventListener('change', () => { if (!el.disabled) saveSetting(el); updateOvertimeModeUI(); });
    });
    [overtimeDailyThresholdInput, overtimeDoubleTimeThresholdInput, overtimeSeventhDayOTThresholdInput, overtimeSeventhDayDTThresholdInput].forEach(el => {
        if(el) el.addEventListener('change', () => { if (!el.disabled) saveSetting(el); });
    });
    overtimeRateRadios.forEach(radio => radio.addEventListener('change', () => { if (radio.checked && !radio.disabled) saveSetting(radio); }));

    const restrictionItemsConfig = [
        { checkboxId: 'restrictByTimeDay',  buttonId: 'configureTimeDayBtn',  url: 'TimeDayRestrictionServlet' },
        { checkboxId: 'restrictByLocation', buttonId: 'configureLocationBtn', url: 'LocationRestrictionServlet' },
        { checkboxId: 'restrictByDevice',   buttonId: 'configureDeviceBtn',   url: 'DeviceRestrictionServlet' }
    ];
    
    restrictionItemsConfig.forEach(itemConfig => {
        const checkbox = document.getElementById(itemConfig.checkboxId);
        const button = document.getElementById(itemConfig.buttonId);
        if (checkbox) {
            checkbox.addEventListener('change', function() {
                saveSetting(this);
                if (button) {
                    button.disabled = !this.checked;
                }
            });
        }
        if (button) {
            button.addEventListener('click', function() {
                if (this.disabled) return;
                let destinationUrl = `${window.appRootPath}/${itemConfig.url}`;
                if (window.inWizardMode_Page) {
                    destinationUrl += `?setup_wizard=true&return_step=${window.currentWizardStep_Page}`;
                }
                window.location.href = destinationUrl;
            });
        }
    });
    
    if (window.inWizardMode_Page) {
        const nextButton = document.getElementById('wizardSettingsNextButton');
        if (nextButton) {
            nextButton.addEventListener('click', function() {
                this.disabled = true;
                this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Proceeding...';
                fetch(`${window.appRootPath}/WizardStatusServlet`, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: new URLSearchParams({ 'action': 'setWizardStep', 'nextStep': 'departments_initial' })
                })
                .then(response => response.json())
                .then(data => {
                    if (data.success && data.nextStep) {
                        window.location.href = `${window.appRootPath}/departments.jsp?setup_wizard=true&step=${data.nextStep}`;
                    } else {
                        this.disabled = false;
                        this.innerHTML = 'Next: Departments Setup <i class="fas fa-arrow-right"></i>';
                    }
                })
                .catch(error => {
                    this.disabled = false;
                    this.innerHTML = 'Next: Departments Setup <i class="fas fa-arrow-right"></i>';
                });
            });
        }
    }
});