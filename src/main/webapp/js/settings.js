// settings.js - vFINAL_WIZARD_FIX
// This version includes robust logging and moves all wizard logic from the JSP to this file.

// --- START: State Overtime Rules Data ---
const FLSA_DEFAULTS = { key: "FLSA", dailyOTEnabled: false, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: 12.0, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "Follows Federal FLSA: Overtime at 1.5x pay after 40 hours in a workweek. No daily or 7th day overtime requirement under FLSA." };
const stateOvertimeRules = { "FLSA": FLSA_DEFAULTS, "CA": { key: "CA", dailyOTEnabled: true, dailyOTThreshold: 8.0, doubleTimeEnabled: true, doubleTimeThreshold: 12.0, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: true, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "CA: Daily OT > 8h (1.5x), >12h (2x). 7th Consecutive Day of workweek: First 8h (1.5x), hours > 8 (2x). Weekly OT > 40h (1.5x) (non-duplicative). Many exceptions apply." }, "AK": { key: "AK", dailyOTEnabled: true, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: true, seventhDayOTThreshold: 0, seventhDayDTThreshold: null, notes: "AK: Daily OT > 8h or Weekly > 40h at 1.5x. All hours worked on the 7th consecutive day of the workweek are OT. Check AK DOL." }, "NV": { key: "NV", dailyOTEnabled: true, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "NV: Daily OT > 8h (in 24hr) OR > 40h/wk at 1.5x, IF employee earns < 1.5x state min wage. Check NV DOL." }, "NY": { key: "NY", dailyOTEnabled: false, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "NY: Generally FLSA. Industry-specific wage orders may differ." }, "CO": { key: "CO", dailyOTEnabled: true, dailyOTThreshold: 12.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "CO: OT after 12h/day, 40h/wk, or 12 consecutive hrs." }, "OR": { key: "OR", dailyOTEnabled: true, dailyOTThreshold: 10.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "OR: OT > 40h/wk. Daily OT (>10h) for some manufacturing/canneries." }, "WA": { key: "WA", dailyOTEnabled: false, dailyOTThreshold: 8.0, doubleTimeEnabled: false, doubleTimeThreshold: null, standardOTRate: "1.5", weeklyOTThreshold: 40, seventhDayOTEnabled: false, seventhDayOTThreshold: 8.0, seventhDayDTThreshold: 8.0, notes: "WA: Generally FLSA. High salary thresholds for OT exemption." }, "TX": { ...FLSA_DEFAULTS, key: "TX", notes: FLSA_DEFAULTS.notes.replace("FLSA", "TX follows FLSA") }, "AL": { ...FLSA_DEFAULTS, key: "AL", notes: FLSA_DEFAULTS.notes.replace("FLSA", "AL follows FLSA") }};
const usStates = [ { name: 'Federal (FLSA Default)', code: 'FLSA', ruleKey: 'FLSA' }, { name: 'Alabama', code: 'AL', ruleKey: 'AL' }, { name: 'Alaska', code: 'AK', ruleKey: 'AK' }, { name: 'Arizona', code: 'AZ', ruleKey: 'AZ' }, { name: 'Arkansas', code: 'AR', ruleKey: 'AR' }, { name: 'California', code: 'CA', ruleKey: 'CA' }, { name: 'Colorado', code: 'CO', ruleKey: 'CO' }, { name: 'Connecticut', code: 'CT', ruleKey: 'CT' }, { name: 'Delaware', code: 'DE', ruleKey: 'DE' }, { name: 'District of Columbia', code: 'DC', ruleKey: 'DC' }, { name: 'Florida', code: 'FL', ruleKey: 'FL' }, { name: 'Georgia', code: 'GA', ruleKey: 'GA' }, { name: 'Hawaii', code: 'HI', ruleKey: 'HI' }, { name: 'Idaho', code: 'ID', ruleKey: 'ID' }, { name: 'Illinois', code: 'IL', ruleKey: 'IL' }, { name: 'Indiana', code: 'IN', ruleKey: 'IN' }, { name: 'Iowa', code: 'IA', ruleKey: 'IA' }, { name: 'Kansas', code: 'KS', ruleKey: 'KS' }, { name: 'Kentucky', code: 'KY', ruleKey: 'KY' }, { name: 'Louisiana', code: 'LA', ruleKey: 'LA' }, { name: 'Maine', code: 'ME', ruleKey: 'ME' }, { name: 'Maryland', code: 'MD', ruleKey: 'MD' }, { name: 'Massachusetts', code: 'MA', ruleKey: 'MA' }, { name: 'Michigan', code: 'MI', ruleKey: 'MI' }, { name: 'Minnesota', code: 'MN', ruleKey: 'MN' }, { name: 'Mississippi', code: 'MS', ruleKey: 'MS' }, { name: 'Missouri', code: 'MO', ruleKey: 'MO' }, { name: 'Montana', code: 'MT', ruleKey: 'MT' }, { name: 'Nebraska', code: 'NE', ruleKey: 'NE' }, { name: 'Nevada', code: 'NV', ruleKey: 'NV' }, { name: 'New Hampshire', code: 'NH', ruleKey: 'NH' }, { name: 'New Jersey', code: 'NJ', ruleKey: 'NJ' }, { name: 'New Mexico', code: 'NM', ruleKey: 'NM' }, { name: 'New York', code: 'NY', ruleKey: 'NY' }, { name: 'North Carolina', code: 'NC', ruleKey: 'NC' }, { name: 'North Dakota', code: 'ND', ruleKey: 'ND' }, { name: 'Ohio', code: 'OH', ruleKey: 'OH' }, { name: 'Oklahoma', code: 'OK', ruleKey: 'OK' }, { name: 'Oregon', code: 'OR', ruleKey: 'OR' }, { name: 'Pennsylvania', code: 'PA', ruleKey: 'PA' }, { name: 'Rhode Island', code: 'RI', ruleKey: 'RI' }, { name: 'South Carolina', code: 'SC', ruleKey: 'SC' }, { name: 'South Dakota', code: 'SD', ruleKey: 'SD' }, { name: 'Tennessee', code: 'TN', ruleKey: 'TN' }, { name: 'Texas', code: 'TX', ruleKey: 'TX' }, { name: 'Utah', code: 'UT', ruleKey: 'UT' }, { name: 'Vermont', code: 'VT', ruleKey: 'VT' }, { name: 'Virginia', code: 'VA', ruleKey: 'VA' }, { name: 'Washington', code: 'WA', ruleKey: 'WA' }, { name: 'West Virginia', code: 'WV', ruleKey: 'WV' }, { name: 'Wisconsin', code: 'WI', ruleKey: 'WI' }, { name: 'Wyoming', code: 'WY', ruleKey: 'WY' } ];
// --- END: State Overtime Rules Data ---

function saveSetting(element, valueToSave) {
    let key = element.name;
    let value = valueToSave !== undefined ? valueToSave : element.value; 

    if (!key && element.id) key = element.id; 
    if (!key) { console.error("Save Setting Error: Element missing name/id:", element); return; }

    if (element.disabled && 
        (key === "Overtime" || key === "FirstDayOfWeek") && 
        !(document.getElementById('otModeAuto')?.checked)
       ) {
        if (key === "Overtime" || key === "FirstDayOfWeek") {
            let statusElement = document.getElementById(key + '-status') || (element.id ? document.getElementById(element.id + '-status') : null);
            if (statusElement) {
                statusElement.textContent = 'Fixed'; statusElement.className = 'save-status visible info';
                if (element.type === 'checkbox') statusElement.classList.add('checkbox-status');
                else if (element.type === 'radio' || element.tagName === 'SELECT') statusElement.classList.add('radio-group-status');
                setTimeout(() => { 
                    statusElement.className = 'save-status'; 
                    if (statusElement.classList.contains('checkbox-status')) statusElement.classList.add('checkbox-status');
                    if (statusElement.classList.contains('radio-group-status')) statusElement.classList.add('radio-group-status');
                }, 2000);
            }
            return;
        }
    }

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
        let isSpecialGroup = element.name && 
                             (element.name.includes('Rate') || 
                              element.name.includes('PayPeriodType') || 
                              element.name.includes('GracePeriod') || 
                              element.name === 'OvertimeRuleMode' || 
                              element.name === 'OvertimeState' || 
                              element.name === 'FirstDayOfWeek' || 
                              element.name === 'PayPeriodStartDate');
        if (element.id && (element.id.includes('Threshold') || element.id.includes('StartDate'))) isSpecialGroup = true;
        if (key === 'PayPeriodEndDate') isSpecialGroup = true;

        if (element.type === 'checkbox') statusElement.classList.add('checkbox-status');
        else if (isSpecialGroup) {
            statusElement.classList.add('radio-group-status');
        }
        statusElement.classList.remove('error', 'info');
    }

    const formData = new URLSearchParams();
    formData.append('settingKey', key);
    formData.append('settingValue', value);

    console.log("Saving Setting - Key:", key, "Value:", value);

    fetch('saveSetting', {
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
                 if (result.status === 403) errorMsg = "Change denied by server.";
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
             alert("Error saving " + key + ": " + (result.body || "Server error " + result.status));
         }
    })
    .catch(error => {
        console.error('Network Error saving setting:', key, error);
        if (statusElement) {
            let baseClass = 'save-status visible error';
            if (statusElement.classList.contains('checkbox-status')) baseClass += ' checkbox-status';
            if (statusElement.classList.contains('radio-group-status')) baseClass += ' radio-group-status';
            statusElement.textContent = 'Network Error!'; statusElement.className = baseClass;
            setTimeout(() => {
                statusElement.className = 'save-status';
                if (baseClass.includes('checkbox-status')) statusElement.classList.add('checkbox-status');
                if (baseClass.includes('radio-group-status')) statusElement.classList.add('radio-group-status');
            }, 5000);
        }  else {
            alert("Network error saving " + key + ". Please try again.");
        }
    });
}

function toggleThresholdInput(enableCheckbox, thresholdInputId, detailsBlockId = null) {
    if (!enableCheckbox) { console.warn("toggleThresholdInput: enableCheckbox is null for:", thresholdInputId); return; }
    const thresholdInput = document.getElementById(thresholdInputId);
    const detailsBlock = detailsBlockId ? document.getElementById(detailsBlockId) : null; 
    const individualBlock = thresholdInput ? (document.getElementById(thresholdInputId + 'Block') || thresholdInput.closest('.setting-block')) : null;

    const isEnabledByCheckbox = enableCheckbox.checked;
    const otModeManualRadio = document.getElementById('otModeManual');
    const isManualModeActive = otModeManualRadio ? otModeManualRadio.checked : true; 

    if (thresholdInput) {
        thresholdInput.disabled = !(isManualModeActive && isEnabledByCheckbox);
        thresholdInput.required = (isManualModeActive && isEnabledByCheckbox);
    }
    
    if (detailsBlock) { 
        const autoModeActive = document.getElementById('otModeAuto')?.checked;
        const shouldDisplay = (isManualModeActive && isEnabledByCheckbox) || (autoModeActive && isEnabledByCheckbox);
        detailsBlock.style.display = shouldDisplay ? 'flex' : 'none'; 
        detailsBlock.style.opacity = shouldDisplay ? 1 : 0.5;
        
        const inputsInBlockDisabled = !isManualModeActive || (isManualModeActive && !isEnabledByCheckbox);
        detailsBlock.querySelectorAll('input[type="number"], select').forEach(input => {
            input.disabled = inputsInBlockDisabled;
        });
    } else if (individualBlock) {
        individualBlock.style.opacity = (isManualModeActive && isEnabledByCheckbox) || !isManualModeActive ? 1 : 0.5;
    }
}

function toggleSpecificPunchRestrictions(mainToggleIsOn) {
    console.log("toggleSpecificPunchRestrictions called. Main toggle is on:", mainToggleIsOn);
    const restrictionItems = [
        { checkboxId: 'restrictByTimeDay', buttonId: 'configureTimeDayBtn' },
        { checkboxId: 'restrictByLocation', buttonId: 'configureLocationBtn' },
        { checkboxId: 'restrictByNetwork', buttonId: 'configureNetworkBtn' },
        { checkboxId: 'restrictByDevice', buttonId: 'configureDeviceBtn' }
    ];
    const groupDiv = document.getElementById('specificPunchRestrictionsGroup');

    restrictionItems.forEach(item => {
        const checkboxElement = document.getElementById(item.checkboxId);
        const buttonElement = document.getElementById(item.buttonId);
        const labelElement = document.querySelector(`label[for="${item.checkboxId}"].slider-label`);
        
        if (checkboxElement) checkboxElement.disabled = !mainToggleIsOn;
        if (labelElement) labelElement.classList.toggle('disabled-text', !mainToggleIsOn);
        if (buttonElement) buttonElement.disabled = !(mainToggleIsOn && checkboxElement && checkboxElement.checked);
    });
    if (groupDiv) groupDiv.style.opacity = mainToggleIsOn ? 1 : 0.6;
}


document.addEventListener('DOMContentLoaded', function() {
    console.log("Settings Page DOMContentLoaded (Pay Period Enhancements, 7th Day OT, Punch Restrict Toggle)");

    const payPeriodTypeSelect = document.getElementById('payPeriodType');
    const firstDayOfWeekSelect = document.getElementById('firstDayOfWeek');
    const payPeriodStartDateInput = document.getElementById('payPeriodStartDate');
    const payPeriodEndDateDisplaySpan = document.getElementById('payPeriodEndDateDisplay');
    const firstDayOfWeekBlock = document.getElementById('firstDayOfWeekBlock'); 
    const payPeriodStartDateBlock = document.getElementById('payPeriodStartDateBlock');
    const payPeriodEndDateBlock = document.getElementById('payPeriodEndDateBlock'); 
    
    const gracePeriodSelect = document.getElementById('gracePeriod');
    // REMOVED: holidayPayRateRadios constant is no longer needed.
    const punchRestrictionsEnabledCheckbox = document.getElementById('punchRestrictionsEnabled');

    const otModeManualRadio = document.getElementById('otModeManual');
    const otModeAutoRadio = document.getElementById('otModeAuto');
    const autoStateOvertimeSection = document.getElementById('autoStateOvertimeSection');
    const overtimeStateSelect = document.getElementById('overtimeStateSelect');
    const manualOvertimeSettingsDiv = document.getElementById('manualOvertimeSettings');
    const stateSpecificNotesDisplay = document.getElementById('stateSpecificNotesDisplay');

    const overtimeRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeRate"]');
    const overtimeDailyCheckbox = document.getElementById('overtimeDaily');
    const overtimeDailyThresholdInput = document.getElementById('overtimeDailyThreshold');
    const overtimeDailyThresholdBlock = document.getElementById('overtimeDailyThresholdBlock');
    const overtimeDoubleTimeEnabledCheckbox = document.getElementById('overtimeDoubleTimeEnabled');
    const overtimeDoubleTimeThresholdInput = document.getElementById('overtimeDoubleTimeThreshold');
    const overtimeDoubleTimeThresholdBlock = document.getElementById('overtimeDoubleTimeThresholdBlock');
    
    const overtimeSeventhDayEnabledCheckbox = document.getElementById('overtimeSeventhDayEnabled');
    const seventhDayOTDetailsBlock = document.getElementById('seventhDayOTDetailsBlock');
    const overtimeSeventhDayOTThresholdInput = document.getElementById('overtimeSeventhDayOTThreshold');
    const overtimeSeventhDayDTThresholdInput = document.getElementById('overtimeSeventhDayDTThreshold');

    if (overtimeStateSelect) {
        if (!overtimeStateSelect.querySelector('option[value=""]')) {
            const defaultOption = document.createElement('option');
            defaultOption.value = ""; defaultOption.textContent = "-- Select a State --";
            overtimeStateSelect.insertBefore(defaultOption, overtimeStateSelect.firstChild);
        }
        usStates.forEach(state => {
            if (state.code === 'FLSA' && overtimeStateSelect.querySelector('option[value="FLSA"]')) return;
            const option = document.createElement('option');
            option.value = state.code; option.textContent = state.name;
            overtimeStateSelect.appendChild(option);
        });
    }

    function calculateAndDisplayPayPeriodEndDate() {
        let needsSaveStartDate = false; 
        if (!payPeriodTypeSelect || !payPeriodStartDateInput || !payPeriodEndDateDisplaySpan || !firstDayOfWeekSelect) {
            if(payPeriodEndDateDisplaySpan) payPeriodEndDateDisplaySpan.textContent = "Error";
            return;
        }
        const periodType = payPeriodTypeSelect.value;
        let startDateStr = payPeriodStartDateInput.value;
        const today = new Date(); today.setHours(0,0,0,0);

        if (periodType === "Daily") {
            if(firstDayOfWeekBlock) firstDayOfWeekBlock.style.display = 'none';
            if(payPeriodStartDateBlock) payPeriodStartDateBlock.style.display = 'none';
            if(payPeriodEndDateBlock) payPeriodEndDateBlock.style.display = 'none';
            
            const todayISO = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`;
            if (payPeriodStartDateInput.value !== todayISO) {
                payPeriodStartDateInput.value = todayISO;
                saveSetting(payPeriodStartDateInput); 
            }
            saveSetting({name: "PayPeriodEndDate", type:"hidden"}, todayISO); 

            if (payPeriodEndDateDisplaySpan) payPeriodEndDateDisplaySpan.textContent = `${String(today.getMonth() + 1).padStart(2, '0')}/${String(today.getDate()).padStart(2, '0')}/${today.getFullYear()}`;
            return; 
        } else {
            if(firstDayOfWeekBlock) firstDayOfWeekBlock.style.display = 'flex';
            if(payPeriodStartDateBlock) payPeriodStartDateBlock.style.display = 'flex';
            if(payPeriodEndDateBlock) payPeriodEndDateBlock.style.display = 'flex';
        }

        if (!startDateStr) { payPeriodEndDateDisplaySpan.textContent = "Set Start Date"; return; }

        try {
            let [year, month, day] = startDateStr.split('-').map(Number);
            let startDate = new Date(year, month - 1, day); 
            if (isNaN(startDate.getTime())) { payPeriodEndDateDisplaySpan.textContent = "Invalid Start"; return; }

            if (periodType === "Monthly") {
                let currentStartDateForMonthly = new Date(startDate.getFullYear(), startDate.getMonth(), 1);
                if (startDate.getDate() !== 1 || payPeriodStartDateInput.value === "" || startDate > today) {
                    if (payPeriodStartDateInput.value === "" || new Date(payPeriodStartDateInput.value.replace(/-/g, '/')+"T00:00:00") > today) {
                        currentStartDateForMonthly = new Date(today.getFullYear(), today.getMonth(), 1);
                    }
                }
                const updatedStartDateStr = `${currentStartDateForMonthly.getFullYear()}-${String(currentStartDateForMonthly.getMonth() + 1).padStart(2, '0')}-01`;
                if (payPeriodStartDateInput.value !== updatedStartDateStr) {
                    payPeriodStartDateInput.value = updatedStartDateStr;
                    needsSaveStartDate = true;
                }
                startDateStr = updatedStartDateStr;
            } else if (periodType === "Semi-Monthly") {
                const currentDayOfMonth = startDate.getDate();
                if (currentDayOfMonth !== 1 && currentDayOfMonth !== 16) {
                    let newStartDay;
                    if (startDate > today) { 
                        newStartDay = (today.getDate() < 16 && today.getDate() >=1) ? 1 : 16;
                        if (newStartDay === 16 && today.getDate() < 16) { 
                           startDate = new Date(today.getFullYear(), today.getMonth() -1, 16);
                        } else {
                           startDate = new Date(today.getFullYear(), today.getMonth(), newStartDay);
                        }
                    } else { 
                        newStartDay = (currentDayOfMonth < 16) ? 1 : 16;
                        startDate.setDate(newStartDay);
                    }
                    needsSaveStartDate = true;
                }
                if (startDate > today) {
                    if (startDate.getDate() === 16) { startDate = new Date(today.getFullYear(), today.getMonth(), 1); }
                    if (startDate > today) { startDate.setMonth(startDate.getMonth() -1); startDate.setDate(16); }
                    needsSaveStartDate = true;
                }

                if (needsSaveStartDate) {
                    const updatedStartDateStr = `${startDate.getFullYear()}-${String(startDate.getMonth() + 1).padStart(2, '0')}-${String(startDate.getDate()).padStart(2, '0')}`;
                    payPeriodStartDateInput.value = updatedStartDateStr;
                }
                startDateStr = payPeriodStartDateInput.value; 
            }

            if(needsSaveStartDate) saveSetting(payPeriodStartDateInput); 
            
            [year, month, day] = startDateStr.split('-').map(Number);
            startDate = new Date(year, month - 1, day);
            let endDate = new Date(startDate);

            switch (periodType) {
                case "Weekly": endDate.setDate(startDate.getDate() + 6); break;
                case "Bi-Weekly": endDate.setDate(startDate.getDate() + 13); break;
                case "Semi-Monthly":
                    if (startDate.getDate() === 1) endDate.setDate(15);
                    else if (startDate.getDate() === 16) endDate = new Date(startDate.getFullYear(), startDate.getMonth() + 1, 0);
                    else { payPeriodEndDateDisplaySpan.textContent = "Start 1st/16th"; return; }
                    break;
                case "Monthly": endDate = new Date(startDate.getFullYear(), startDate.getMonth() + 1, 0); break;
                default: payPeriodEndDateDisplaySpan.textContent = "N/A"; return;
            }
            
            const options = { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'UTC' };
            payPeriodEndDateDisplaySpan.textContent = endDate.toLocaleDateString('en-US', options);
            
            const endDateToSave = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2,'0')}-${String(endDate.getDate()).padStart(2,'0')}`;
            saveSetting({name: "PayPeriodEndDate", type:"hidden"}, endDateToSave); 

        } catch (e) {
            console.error("Error calculating pay period end date:", e);
            payPeriodEndDateDisplaySpan.textContent = "Calc Error";
        }
    }
    
    function updateManualFieldsFromState(stateCode, saveDerivedSettings = false) {
        const stateDetails = usStates.find(s => s.code === stateCode);
        const ruleKeyToUse = stateDetails ? stateDetails.ruleKey : "FLSA";
        const rules = stateOvertimeRules[ruleKeyToUse] || stateOvertimeRules["FLSA"];
        console.log("Applying rules for state:", stateCode, "using ruleKey:", ruleKeyToUse, "Rules:", JSON.stringify(rules));

        if (overtimeDailyCheckbox) overtimeDailyCheckbox.checked = rules.dailyOTEnabled;
        if (overtimeDailyThresholdInput) overtimeDailyThresholdInput.value = rules.dailyOTEnabled && rules.dailyOTThreshold !== null ? rules.dailyOTThreshold.toFixed(1) : '';
        if (overtimeDoubleTimeEnabledCheckbox) overtimeDoubleTimeEnabledCheckbox.checked = rules.doubleTimeEnabled;
        if (overtimeDoubleTimeThresholdInput) overtimeDoubleTimeThresholdInput.value = rules.doubleTimeEnabled && rules.doubleTimeThreshold !== null ? rules.doubleTimeThreshold.toFixed(1) : '';
        if (overtimeRateRadios) {
            const rateToSet = String(rules.standardOTRate || "1.5");
            overtimeRateRadios.forEach(radio => { radio.checked = (radio.value === rateToSet); });
        }
        if (overtimeSeventhDayEnabledCheckbox) overtimeSeventhDayEnabledCheckbox.checked = rules.seventhDayOTEnabled;
        if (seventhDayOTDetailsBlock) seventhDayOTDetailsBlock.style.display = rules.seventhDayOTEnabled ? 'flex' : 'none';
        if (overtimeSeventhDayOTThresholdInput) overtimeSeventhDayOTThresholdInput.value = rules.seventhDayOTEnabled && rules.seventhDayOTThreshold !== null ? rules.seventhDayOTThreshold.toFixed(1) : '';
        if (overtimeSeventhDayDTThresholdInput) overtimeSeventhDayDTThresholdInput.value = rules.seventhDayOTEnabled && rules.seventhDayDTThreshold !== null ? rules.seventhDayDTThreshold.toFixed(1) : '';

        if (stateSpecificNotesDisplay) { stateSpecificNotesDisplay.innerHTML = rules.notes ? rules.notes.replace(/\n/g, "<br>") : "No specific notes or using FLSA defaults."; }
    
        if(overtimeDailyThresholdBlock) overtimeDailyThresholdBlock.style.opacity = rules.dailyOTEnabled ? 1 : 0.5;
        if(overtimeDoubleTimeThresholdBlock) overtimeDoubleTimeThresholdBlock.style.opacity = rules.doubleTimeEnabled ? 1 : 0.5;
        if(seventhDayOTDetailsBlock) seventhDayOTDetailsBlock.style.opacity = rules.seventhDayOTEnabled ? 1 : 0.5;

        if (saveDerivedSettings) {
            console.log("Auto-saving derived settings for state:", stateCode);
            if(overtimeDailyCheckbox) saveSetting(overtimeDailyCheckbox, rules.dailyOTEnabled.toString());
            if(overtimeDailyThresholdInput && rules.dailyOTEnabled) { 
                saveSetting(overtimeDailyThresholdInput, (rules.dailyOTThreshold !== null) ? rules.dailyOTThreshold.toFixed(1) : "8.0"); 
            } else if (overtimeDailyThresholdInput) {
                 console.log("Daily OT is OFF for state " + stateCode + ", not saving OvertimeDailyThreshold.");
            }
            
            if(overtimeDoubleTimeEnabledCheckbox) saveSetting(overtimeDoubleTimeEnabledCheckbox, rules.doubleTimeEnabled.toString());
            if(overtimeDoubleTimeThresholdInput && rules.doubleTimeEnabled) { 
                saveSetting(overtimeDoubleTimeThresholdInput, (rules.doubleTimeThreshold !== null) ? rules.doubleTimeThreshold.toFixed(1) : "12.0");
            } else if (overtimeDoubleTimeThresholdInput) {
                 console.log("Daily DT is OFF for state " + stateCode + ", not saving OvertimeDoubleTimeThreshold.");
            }
            
            const rateToSet = String(rules.standardOTRate || "1.5");
            const rateRadioToSave = Array.from(overtimeRateRadios).find(r => r.value === rateToSet) || document.getElementById('overtimeRate1.5');
            if(rateRadioToSave) { rateRadioToSave.checked = true; saveSetting(rateRadioToSave); }

            if(overtimeSeventhDayEnabledCheckbox) saveSetting(overtimeSeventhDayEnabledCheckbox, rules.seventhDayOTEnabled.toString());
            if(overtimeSeventhDayOTThresholdInput && rules.seventhDayOTEnabled) { 
                 saveSetting(overtimeSeventhDayOTThresholdInput, (rules.seventhDayOTThreshold !== null) ? rules.seventhDayOTThreshold.toFixed(1) : "8.0");
            } else if (overtimeSeventhDayOTThresholdInput) {
                console.log("7th Day OT is OFF for state " + stateCode + ", not saving OvertimeSeventhDayOTThreshold.");
            }
            if(overtimeSeventhDayDTThresholdInput && rules.seventhDayOTEnabled) { 
                saveSetting(overtimeSeventhDayDTThresholdInput, (rules.seventhDayDTThreshold !== null) ? rules.seventhDayDTThreshold.toFixed(1) : "8.0");
            } else if (overtimeSeventhDayDTThresholdInput) {
                console.log("7th Day DT is OFF for state " + stateCode + ", not saving OvertimeSeventhDayDTThreshold.");
            }
        }
    }

    function updateOvertimeModeUI() {
        const isManualMode = otModeManualRadio && otModeManualRadio.checked;
        console.log("Updating OT Mode UI. Manual Mode Selected:", isManualMode);

        if (autoStateOvertimeSection) autoStateOvertimeSection.style.display = isManualMode ? 'none' : 'block';
        if (manualOvertimeSettingsDiv) {
            manualOvertimeSettingsDiv.style.opacity = isManualMode ? 1 : 0.7;
            manualOvertimeSettingsDiv.classList.toggle('auto-mode-fields-disabled', !isManualMode);
        }

        const manualTextInputs = [overtimeDailyThresholdInput, overtimeDoubleTimeThresholdInput, overtimeSeventhDayOTThresholdInput, overtimeSeventhDayDTThresholdInput];
        const manualToggles = [overtimeDailyCheckbox, overtimeDoubleTimeEnabledCheckbox, overtimeSeventhDayEnabledCheckbox];
        
        manualTextInputs.forEach(el => { if (el) el.disabled = !isManualMode; });
        manualToggles.forEach(el => { if (el) el.disabled = !isManualMode; });
        overtimeRateRadios.forEach(radio => radio.disabled = !isManualMode);

        if (isManualMode) {
            if(overtimeDailyCheckbox) toggleThresholdInput(overtimeDailyCheckbox, 'overtimeDailyThreshold');
            if(overtimeDoubleTimeEnabledCheckbox) toggleThresholdInput(overtimeDoubleTimeEnabledCheckbox, 'overtimeDoubleTimeThreshold');
            if(overtimeSeventhDayEnabledCheckbox) toggleThresholdInput(overtimeSeventhDayEnabledCheckbox, 'overtimeSeventhDayOTThreshold', 'seventhDayOTDetailsBlock');
            if(stateSpecificNotesDisplay) stateSpecificNotesDisplay.innerHTML = "Overtime rules are configured manually. Weekly OT is fixed (FLSA).";
        } else { 
            manualTextInputs.forEach(el => { if (el) el.disabled = true; });
            manualToggles.forEach(el => { if(el) el.disabled = true; });
            overtimeRateRadios.forEach(radio => radio.disabled = true);
            if (overtimeStateSelect && overtimeStateSelect.value) {
                updateManualFieldsFromState(overtimeStateSelect.value, false); 
            } else {
                updateManualFieldsFromState("FLSA", false); 
            }
            if (seventhDayOTDetailsBlock && overtimeSeventhDayEnabledCheckbox) {
                seventhDayOTDetailsBlock.style.display = overtimeSeventhDayEnabledCheckbox.checked ? 'flex' : 'none';
            }
        }
    }

    if (window.settingsConfig) {
        console.log("Initializing UI from window.settingsConfig", window.settingsConfig);
        if(payPeriodTypeSelect && window.settingsConfig.payPeriodType) payPeriodTypeSelect.value = window.settingsConfig.payPeriodType;
        if(firstDayOfWeekSelect && window.settingsConfig.firstDayOfWeek) firstDayOfWeekSelect.value = window.settingsConfig.firstDayOfWeek;
        if(payPeriodStartDateInput && window.settingsConfig.payPeriodStartDate) payPeriodStartDateInput.value = window.settingsConfig.payPeriodStartDate;
        
        if (window.settingsConfig.overtimeRuleMode === "AutoByState" && otModeAutoRadio) {
            otModeAutoRadio.checked = true;
        } else if (otModeManualRadio) { 
            otModeManualRadio.checked = true;
        }
        if (window.settingsConfig.overtimeState && overtimeStateSelect) {
            overtimeStateSelect.value = window.settingsConfig.overtimeState;
        }
        if(overtimeDailyCheckbox) overtimeDailyCheckbox.checked = String(window.settingsConfig.overtimeDailyEnabled).toLowerCase() === 'true';
        if(overtimeDailyThresholdInput) overtimeDailyThresholdInput.value = window.settingsConfig.overtimeDailyThreshold || '8.0';
        if(overtimeDoubleTimeEnabledCheckbox) overtimeDoubleTimeEnabledCheckbox.checked = String(window.settingsConfig.overtimeDoubleTimeEnabled).toLowerCase() === 'true';
        if(overtimeDoubleTimeThresholdInput) overtimeDoubleTimeThresholdInput.value = window.settingsConfig.overtimeDoubleTimeThreshold || '12.0';
        if(overtimeRateRadios) {
            const rateToSet = String(window.settingsConfig.overtimeRate || "1.5");
            overtimeRateRadios.forEach(radio => { radio.checked = (radio.value === rateToSet); });
        }
        if(overtimeSeventhDayEnabledCheckbox) overtimeSeventhDayEnabledCheckbox.checked = String(window.settingsConfig.overtimeSeventhDayEnabled).toLowerCase() === 'true';
        if(overtimeSeventhDayOTThresholdInput) overtimeSeventhDayOTThresholdInput.value = window.settingsConfig.overtimeSeventhDayOTThreshold || '8.0';
        if(overtimeSeventhDayDTThresholdInput) overtimeSeventhDayDTThresholdInput.value = window.settingsConfig.overtimeSeventhDayDTThreshold || '8.0';
    }
    calculateAndDisplayPayPeriodEndDate(); 
    updateOvertimeModeUI(); 

    if (payPeriodTypeSelect) payPeriodTypeSelect.addEventListener('change', function() { saveSetting(this); calculateAndDisplayPayPeriodEndDate(); });
    if (firstDayOfWeekSelect) firstDayOfWeekSelect.addEventListener('change', function() { saveSetting(this); calculateAndDisplayPayPeriodEndDate(); });
    if (payPeriodStartDateInput) payPeriodStartDateInput.addEventListener('change', function() { saveSetting(this); calculateAndDisplayPayPeriodEndDate(); });
    
    if (gracePeriodSelect) gracePeriodSelect.addEventListener('change', function() { saveSetting(this); });
    // REMOVED: Event listener for holidayPayRateRadios is no longer needed.
    if (otModeManualRadio) otModeManualRadio.addEventListener('change', function() { if(this.checked) { saveSetting(this); updateOvertimeModeUI(); }});
    if (otModeAutoRadio) otModeAutoRadio.addEventListener('change', function() { if(this.checked) { saveSetting(this); updateOvertimeModeUI(); }});
    if (overtimeStateSelect) {
        overtimeStateSelect.addEventListener('change', function() {
            const selectedStateCode = this.value;
            console.log("State selected via dropdown:", selectedStateCode);
            saveSetting(this); 
            if (selectedStateCode) { updateManualFieldsFromState(selectedStateCode, true); } 
            else { updateManualFieldsFromState("FLSA", true); }
        });
    }
    if (overtimeDailyCheckbox) { overtimeDailyCheckbox.addEventListener('change', function() { if (!this.disabled) saveSetting(this); toggleThresholdInput(this, 'overtimeDailyThreshold'); }); }
    if (overtimeDailyThresholdInput) {
        overtimeDailyThresholdInput.addEventListener('change', function() { if (!this.disabled) saveSetting(this); });
        overtimeDailyThresholdInput.addEventListener('keyup', function(event) { if (event.key === 'Enter' && !this.disabled) { saveSetting(this); this.blur(); }});
    }
    if (overtimeDoubleTimeEnabledCheckbox) {
        overtimeDoubleTimeEnabledCheckbox.addEventListener('change', function() { if (!this.disabled) saveSetting(this); toggleThresholdInput(this, 'overtimeDoubleTimeThreshold'); });
    }
    if (overtimeDoubleTimeThresholdInput) {
        overtimeDoubleTimeThresholdInput.addEventListener('change', function() { if (!this.disabled) saveSetting(this); });
        overtimeDoubleTimeThresholdInput.addEventListener('keyup', function(event) { if (event.key === 'Enter' && !this.disabled) { saveSetting(this); this.blur(); }});
    }
    overtimeRateRadios.forEach(radio => { radio.addEventListener('change', function() { if (this.checked && !this.disabled) saveSetting(this); }); });

    if (overtimeSeventhDayEnabledCheckbox) {
        overtimeSeventhDayEnabledCheckbox.addEventListener('change', function() {
            if (!this.disabled) saveSetting(this);
            toggleThresholdInput(this, 'overtimeSeventhDayOTThreshold', 'seventhDayOTDetailsBlock'); 
            const dtInput = document.getElementById('overtimeSeventhDayDTThreshold');
            const manualMode = document.getElementById('otModeManual')?.checked;
            if (dtInput && manualMode) { dtInput.disabled = !this.checked; }
        });
    }
    if (overtimeSeventhDayOTThresholdInput) {
        overtimeSeventhDayOTThresholdInput.addEventListener('change', function() { if (!this.disabled) saveSetting(this); });
        overtimeSeventhDayOTThresholdInput.addEventListener('keyup', function(event) { if (event.key === 'Enter' && !this.disabled) { saveSetting(this); this.blur(); }});
    }
    if (overtimeSeventhDayDTThresholdInput) {
        overtimeSeventhDayDTThresholdInput.addEventListener('change', function() { if (!this.disabled) saveSetting(this); });
        overtimeSeventhDayDTThresholdInput.addEventListener('keyup', function(event) { if (event.key === 'Enter' && !this.disabled) { saveSetting(this); this.blur(); }});
    }
    if (otModeManualRadio && otModeManualRadio.checked && overtimeSeventhDayEnabledCheckbox) {
        toggleThresholdInput(overtimeSeventhDayEnabledCheckbox, 'overtimeSeventhDayOTThreshold', 'seventhDayOTDetailsBlock');
        const dtInput = document.getElementById('overtimeSeventhDayDTThreshold');
        if(dtInput) dtInput.disabled = !overtimeSeventhDayEnabledCheckbox.checked;
    }


    const restrictionItemsConfig = [
        { checkboxId: 'restrictByTimeDay',  buttonId: 'configureTimeDayBtn',  url: 'TimeDayRestrictionServlet' },
        { checkboxId: 'restrictByLocation', buttonId: 'configureLocationBtn', url: 'LocationRestrictionServlet' },
        { checkboxId: 'restrictByNetwork',  buttonId: 'configureNetworkBtn',  url: 'NetworkRestrictionServlet' },
        { checkboxId: 'restrictByDevice',   buttonId: 'configureDeviceBtn',   url: 'DeviceRestrictionServlet' }
    ];
    if (punchRestrictionsEnabledCheckbox) {
        punchRestrictionsEnabledCheckbox.addEventListener('change', function() { 
            saveSetting(this); 
            toggleSpecificPunchRestrictions(this.checked);
        });
        toggleSpecificPunchRestrictions(punchRestrictionsEnabledCheckbox.checked);
    } else {
        toggleSpecificPunchRestrictions(false); 
    }
    restrictionItemsConfig.forEach(itemConfig => {
        const checkbox = document.getElementById(itemConfig.checkboxId);
        const button = document.getElementById(itemConfig.buttonId);
        if (checkbox && button) {
            checkbox.addEventListener('change', function() {
                if(punchRestrictionsEnabledCheckbox && punchRestrictionsEnabledCheckbox.checked) saveSetting(this);
                button.disabled = !(this.checked && punchRestrictionsEnabledCheckbox && punchRestrictionsEnabledCheckbox.checked);
            });
            button.addEventListener('click', function() {
                if (this.disabled) return;
                if (itemConfig.url && itemConfig.url !== '#') { window.location.href = itemConfig.url; }
                else { alert('Configuration for this item is not yet implemented.'); }
            });
            if (punchRestrictionsEnabledCheckbox && checkbox) {
                 button.disabled = !(checkbox.checked && punchRestrictionsEnabledCheckbox.checked);
            } else if (button) { 
                button.disabled = true;
            }
        }
    });
    
    const urlParamsOnLoad = new URLSearchParams(window.location.search);
    if (urlParamsOnLoad.has('message') || urlParamsOnLoad.has('error')) {
        const message = urlParamsOnLoad.get('message') || urlParamsOnLoad.get('error');
        const type = urlParamsOnLoad.has('message') ? 'success' : 'error';
        console.log("Page load detected message in URL:", message);
        if(typeof showToast === 'function') showToast(message, type);
        else console.error("showToast function not defined when trying to display URL message.");

        const currentPath = window.location.pathname;
        const newSearchParams = new URLSearchParams();
        urlParamsOnLoad.forEach((value, key) => {
            if (key !== 'message' && key !== 'error') {
                newSearchParams.append(key, value);
            }
        });
        const newSearch = newSearchParams.toString();
        if (window.history && window.history.replaceState) {
            window.history.replaceState({}, document.title, currentPath + (newSearch ? '?' + newSearch : ''));
        }
    }
    
    // --- **FIX START**: Wizard Navigation Logic with Debugging ---
    if (window.inWizardMode_Page) {
        const nextButton = document.getElementById('wizardSettingsNextButton');
        
        if (!nextButton) {
            console.error("FATAL WIZARD ERROR: The 'Next' button (id=wizardSettingsNextButton) was not found in the DOM.");
            return;
        }
        if (typeof window.appRootPath === 'undefined') {
            console.error("FATAL WIZARD ERROR: The global variable 'window.appRootPath' is not defined. Check the settings.jsp file.");
            return;
        }

        console.log(`[WIZARD DEBUG] Initializing 'Next' button. Context path is: "${window.appRootPath}"`);

        nextButton.addEventListener('click', function() {
            this.disabled = true;
            this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Proceeding...';

            const servletUrl = `${window.appRootPath}/WizardStatusServlet`;
            
            console.log(`[WIZARD DEBUG] Calling servlet at: "${servletUrl}"`);

            fetch(servletUrl, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: new URLSearchParams({
                    'action': 'setWizardStep',
                    'nextStep': 'departments_initial'
                })
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error(`Server responded with status: ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                console.log("[WIZARD DEBUG] Received data from servlet:", data);

                if (data.success && data.nextStep) {
                    const redirectUrl = `${window.appRootPath}/departments.jsp?setup_wizard=true&step=${data.nextStep}`;
                    
                    console.log(`[WIZARD DEBUG] SUCCESS! Redirecting to: "${redirectUrl}"`);
                    window.location.href = redirectUrl;
                } else {
                    const errorMessage = data.error || "The server did not confirm the next step.";
                    console.error("[WIZARD DEBUG] Server responded with an error:", errorMessage);
                    showPageNotification(`Error proceeding: ${errorMessage}`, true, null, "Wizard Error");
                    this.disabled = false;
                    this.innerHTML = 'Next: Departments Setup <i class="fas fa-arrow-right"></i>';
                }
            })
            .catch(error => {
                console.error("[WIZARD DEBUG] A network or fetch error occurred:", error);
                showPageNotification("A network error occurred. Please check the console and try again.", true, null, "Network Error");
                this.disabled = false;
                this.innerHTML = 'Next: Departments Setup <i class="fas fa-arrow-right"></i>';
            });
        });
    }
    // --- **FIX END** ---

    console.log("Settings Page DOMContentLoaded setup complete.");
});