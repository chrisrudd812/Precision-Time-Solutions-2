// js/settings.js  
// --- START: US Federal Holidays Data ---  
const US_HOLIDAYS = [  
    { id: 'new_years', name: "New Year's Day" },  
    { id: 'mlk_day', name: 'Martin Luther King Jr. Day' },  
    { id: 'presidents_day', name: "Presidents' Day" },  
    { id: 'memorial_day', name: 'Memorial Day' },  
    { id: 'juneteenth', name: 'Juneteenth' },  
    { id: 'independence_day', name: 'Independence Day' },  
    { id: 'labor_day', name: 'Labor Day' },  
    { id: 'columbus_day', name: 'Columbus Day' },  
    { id: 'veterans_day', name: 'Veterans Day' },  
    { id: 'thanksgiving', name: 'Thanksgiving' },  
    { id: 'christmas', name: 'Christmas' }  
];  
// --- END: US Federal Holidays Data ---  
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
    if (key.includes("Threshold") || key.endsWith("Rate")) {  
        if (value === null || String(value).trim() === '') {  
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
ECHO is on.
    let statusElement = document.getElementById(key + '-status') || (element.id ? document.getElementById(element.id + '-status') : null);  
    if (!statusElement && element.parentElement) {  
        statusElement = element.parentElement.querySelector('.save-status');  
        if (!statusElement && element.parentElement.parentElement) {  
            statusElement = element.parentElement.parentElement.querySelector('.save-status');  
        }  
    }  
    if (statusElement) {  
        statusElement.textContent = 'Saving...';  
        statusElement.className = 'save-status visible saving';  
    }  
    const formData = new URLSearchParams();  
    formData.append('settingKey', key);  
    formData.append('settingValue', value);  
ECHO is on.
    fetch(`${window.appRootPath}/SettingsServlet`, {  
        method: 'POST',  
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },  
        body: formData  
    })  
    .then(response => response.text().then(text => ({ ok: response.ok, status: response.status, body: text })))  
    .then(result => {  
         if (statusElement) {  
             let baseClass = 'save-status visible';  
             if (result.ok && result.body === 'OK') {  
                 statusElement.textContent = 'Saved!';  
                 statusElement.className = `${baseClass} success`;  
             } else {  
                 let errorMsg = result.body && result.body.startsWith('Error:') ? result.body.substring(6).trim() : (result.body || `Save failed.`);  
                 statusElement.textContent = errorMsg;  
                 statusElement.className = `${baseClass} error`;  
                 console.error("Error saving " + key + ":", errorMsg);  
             }  
             setTimeout(() => {  
                statusElement.className = 'save-status';  
            }, 3000);  
         } else if (!result.ok || result.body !== 'OK') {  
             console.error("Error saving " + key + " (no status element):", result.body || result.status);  
         }  
    })  
    .catch(error => {  
        console.error('Network Error saving setting:', key, error);  
        if (statusElement) {  
            statusElement.textContent = 'Network Error!';  
            statusElement.className = 'save-status visible error';  
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
ECHO is on.
    if (detailsBlock) {   
        const shouldDisplay = (document.getElementById('otModeAuto')?.checked && isEnabledByCheckbox) || (isManualModeActive && isEnabledByCheckbox);  
        detailsBlock.style.display = shouldDisplay ? 'flex' : 'none';  
        detailsBlock.querySelectorAll('input[type="number"], select').forEach(input => {  
            input.disabled = !isManualModeActive || !isEnabledByCheckbox;  
        });  
    }  
}  
document.addEventListener('DOMContentLoaded', function() {  
    const payPeriodTypeSelect = document.getElementById('payPeriodType'), firstDayOfWeekSelect = document.getElementById('firstDayOfWeek'), payPeriodStartDateInput = document.getElementById('payPeriodStartDate'), payPeriodEndDateDisplaySpan = document.getElementById('payPeriodEndDateDisplay');  
    const firstDayOfWeekBlock = document.getElementById('firstDayOfWeekBlock'), payPeriodStartDateBlock = document.getElementById('payPeriodStartDateBlock'), payPeriodEndDateBlock = document.getElementById('payPeriodEndDateBlock');   
    const firstDayOfWeekNote = document.getElementById('firstDayOfWeekNote');  
    const gracePeriodSelect = document.getElementById('gracePeriod');  
    const otTypeManualRadio = document.getElementById('otTypeManual'), otTypeCompanyStateRadio = document.getElementById('otTypeCompanyState'), otTypeEmployeeStateRadio = document.getElementById('otTypeEmployeeState'), autoStateOvertimeSection = document.getElementById('autoStateOvertimeSection'), overtimeStateSelect = document.getElementById('overtimeStateSelect'), stateSpecificNotesDisplay = document.getElementById('stateSpecificNotesDisplay');  
    const overtimeRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeRate"]'), overtimeDailyCheckbox = document.getElementById('overtimeDaily'), overtimeDailyThresholdInput = document.getElementById('overtimeDailyThreshold'), overtimeDoubleTimeEnabledCheckbox = document.getElementById('overtimeDoubleTimeEnabled'), overtimeDoubleTimeThresholdInput = document.getElementById('overtimeDoubleTimeThreshold');  
    const overtimeSeventhDayEnabledCheckbox = document.getElementById('overtimeSeventhDayEnabled'), seventhDayOTDetailsBlock = document.getElementById('seventhDayOTDetailsBlock'), overtimeSeventhDayOTThresholdInput = document.getElementById('overtimeSeventhDayOTThreshold'), overtimeSeventhDayDTThresholdInput = document.getElementById('overtimeSeventhDayDTThreshold');  
ECHO is on.
    if (overtimeStateSelect) {  
        usStates.forEach(state => {  
            const option = document.createElement('option');  
            option.value = state.code; option.textContent = state.name;  
            overtimeStateSelect.appendChild(option);  
        });  
    }  
	function calculateAndDisplayPayPeriodEndDate(shouldSave = false) {  
	    if (!payPeriodTypeSelect || !payPeriodStartDateInput || !payPeriodEndDateDisplaySpan || !firstDayOfWeekSelect) return;  
ECHO is on.
	    const periodType = payPeriodTypeSelect.value;  
	    const today = new Date();  
	    today.setHours(0, 0, 0, 0);  
	    let startDate, endDate;  
	    let newStartDateSet = false;  
	    const toISODateString = (date) => {  
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
	        if (shouldSave) {  
                saveSetting(payPeriodStartDateInput);  
                saveSetting({ name: "PayPeriodEndDate", type: "hidden" }, todayISO);  
            }  
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
	                endDate = new Date(Date.UTC(today.getFullYear(), today.getMonth() + 1, 0));  
	            }  
	            payPeriodStartDateInput.value = toISODateString(startDate);  
	            newStartDateSet = true;  
	            break;  
ECHO is on.
	        case "Monthly":  
	            startDate = new Date(Date.UTC(today.getFullYear(), today.getMonth(), 1));  
	            endDate = new Date(Date.UTC(today.getFullYear(), today.getMonth() + 1, 0));  
	            payPeriodStartDateInput.value = toISODateString(startDate);  
	            newStartDateSet = true;  
	            break;  
	        default:  
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
	            endDate = new Date(startDate.getTime());  
	            if (periodType === "Weekly") {  
	                endDate.setUTCDate(startDate.getUTCDate() + 6);  
	            } else if (periodType === "Bi-Weekly") {  
	                endDate.setUTCDate(startDate.getUTCDate() + 13);  
	            }  
	            break;  
	    }  
	    if (newStartDateSet && shouldSave) {  
	        saveSetting(payPeriodStartDateInput);  
	    }  
	    payPeriodEndDateDisplaySpan.textContent = endDate.toLocaleDateString('en-US', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: 'UTC' });  
	    if (shouldSave) {  
            const endDateToSave = toISODateString(endDate);  
            saveSetting({ name: "PayPeriodEndDate", type: "hidden" }, endDateToSave);  
        }  
	}  
ECHO is on.
    function updateManualFieldsFromState(stateCode, saveDerivedSettings = false) {  
        const stateDetails = usStates.find(s => s.code === stateCode) || usStates.find(s => s.code === 'FLSA');  
        const rules = stateOvertimeRules[stateDetails.ruleKey] || stateOvertimeRules["FLSA"];  
ECHO is on.
        const applyAndSave = (element, value) => {  
            if (element) {  
                if(element.type === 'checkbox') element.checked = value;  
                else element.value = value;  
                if (saveDerivedSettings) saveSetting(element, String(value));  
            }  
        };  
        // *** THIS IS THE FIX: Added a helper function to safely format numbers ***  
        const formatThreshold = (value) => (value !== null && typeof value !== 'undefined') ? value.toFixed(1) : '';  
        applyAndSave(overtimeDailyCheckbox, rules.dailyOTEnabled);  
        applyAndSave(overtimeDailyThresholdInput, rules.dailyOTEnabled ? formatThreshold(rules.dailyOTThreshold) : '');  
ECHO is on.
        applyAndSave(overtimeDoubleTimeEnabledCheckbox, rules.doubleTimeEnabled);  
        applyAndSave(overtimeDoubleTimeThresholdInput, rules.doubleTimeEnabled ? formatThreshold(rules.doubleTimeThreshold) : '');  
ECHO is on.
        applyAndSave(overtimeSeventhDayEnabledCheckbox, rules.seventhDayOTEnabled);  
        applyAndSave(overtimeSeventhDayOTThresholdInput, rules.seventhDayOTEnabled ? formatThreshold(rules.seventhDayOTThreshold) : '');  
        applyAndSave(overtimeSeventhDayDTThresholdInput, rules.seventhDayOTEnabled ? formatThreshold(rules.seventhDayDTThreshold) : '');  
ECHO is on.
        if (seventhDayOTDetailsBlock) {  
            seventhDayOTDetailsBlock.style.display = rules.seventhDayOTEnabled ? 'flex' : 'none';  
        }  
ECHO is on.
        if (stateSpecificNotesDisplay) stateSpecificNotesDisplay.innerHTML = rules.notes.replace(/\n/g, "<br>");  
ECHO is on.
        const rateToSet = String(rules.standardOTRate || "1.5");  
        overtimeRateRadios.forEach(radio => {  
            radio.checked = (radio.value === rateToSet);  
            if (saveDerivedSettings && radio.checked) saveSetting(radio);  
        });  
    }  
    function initializeHolidayCheckboxes() {  
        const holidayCheckboxList = document.getElementById('holidayCheckboxList');  
        if (!holidayCheckboxList) return;  
ECHO is on.
        const selectedHolidays = window.settingsConfig.overtimeHolidays ? window.settingsConfig.overtimeHolidays.split(',').filter(h => h) : [];  
ECHO is on.
        holidayCheckboxList.innerHTML = '';  
        US_HOLIDAYS.forEach(holiday => {  
            const wrapper = document.createElement('div');  
            wrapper.style.cssText = 'display: flex; align-items: center; gap: 8px;';  
ECHO is on.
            const checkbox = document.createElement('input');  
            checkbox.type = 'checkbox';  
            checkbox.id = `holiday_${holiday.id}`;  
            checkbox.value = holiday.id;  
            checkbox.checked = selectedHolidays.includes(holiday.id);  
            checkbox.className = 'holiday-checkbox';  
            checkbox.style.cssText = 'width: 20px; height: 20px; cursor: pointer; flex-shrink: 0; opacity: 1;';  
            checkbox.disabled = false;  
            checkbox.addEventListener('change', saveHolidaySelection);  
ECHO is on.
            const label = document.createElement('label');  
            label.htmlFor = `holiday_${holiday.id}`;  
            label.textContent = holiday.name;  
            label.style.cssText = 'cursor: pointer; user-select: none; margin: 0;';  
ECHO is on.
            wrapper.appendChild(checkbox);  
            wrapper.appendChild(label);  
            holidayCheckboxList.appendChild(wrapper);  
        });  
    }  
ECHO is on.
    function saveHolidaySelection() {  
        const checkboxes = document.querySelectorAll('#holidayCheckboxList input[type="checkbox"]');  
        const selected = Array.from(checkboxes).filter(cb => cb.checked).map(cb => cb.value);  
        const holidayString = selected.join(',');  
        saveSetting({ name: 'OvertimeHolidays', type: 'hidden' }, holidayString);  
    }  
ECHO is on.
    function updateOvertimeModeUI() {  
        const isManualMode = otTypeManualRadio && otTypeManualRadio.checked;  
        const isCompanyStateMode = otTypeCompanyStateRadio && otTypeCompanyStateRadio.checked;  
        const isEmployeeStateMode = otTypeEmployeeStateRadio && otTypeEmployeeStateRadio.checked;  
        const isStateMode = isCompanyStateMode || isEmployeeStateMode;  
ECHO is on.
        // Hide everything for employee state mode except holiday section  
        const allManualDivs = document.querySelectorAll('#manualOvertimeSettings');  
ECHO is on.
        const employeeStateNote = document.getElementById('employeeStateNote');  
        const holidaySection = document.getElementById('holidayOvertimeSection');  
ECHO is on.
        if (isEmployeeStateMode) {  
            if (autoStateOvertimeSection) autoStateOvertimeSection.style.display = 'none';  
            if (employeeStateNote) employeeStateNote.style.display = 'block';  
            allManualDivs.forEach(div => div.style.display = 'none');  
            if (holidaySection) {  
                const holidayHr = holidaySection.previousElementSibling;  
                if (holidayHr && holidayHr.tagName === 'HR') holidayHr.style.display = 'none';  
            }  
        } else {  
            if (autoStateOvertimeSection) autoStateOvertimeSection.style.display = isStateMode ? 'block' : 'none';  
            if (employeeStateNote) employeeStateNote.style.display = 'none';  
            allManualDivs.forEach(div => {  
                div.style.display = 'block';  
                div.style.opacity = isManualMode ? 1 : 0.6;  
            });  
            if (holidaySection) {  
                const holidayHr = holidaySection.previousElementSibling;  
                if (holidayHr && holidayHr.tagName === 'HR') holidayHr.style.display = 'block';  
            }  
        }  
ECHO is on.
        if (overtimeStateSelect) {  
            overtimeStateSelect.disabled = isEmployeeStateMode;  
        }  
ECHO is on.
        const allManualInputs = document.querySelectorAll('#manualOvertimeSettings input:not([name="Overtime"]), #manualOvertimeSettings select');  
        allManualInputs.forEach(el => el.disabled = !isManualMode);  
ECHO is on.
        // Always enable holiday overtime controls  
        const holidayOTCheckbox = document.getElementById('overtimeHolidayEnabled');  
        if (holidayOTCheckbox) {  
            holidayOTCheckbox.disabled = false;  
        }  
ECHO is on.
        const holidayRateRadios = document.querySelectorAll('input[name="OvertimeHolidayRate"]');  
        holidayRateRadios.forEach(radio => {  
            if (holidayOTCheckbox && holidayOTCheckbox.checked) {  
                radio.disabled = false;  
            }  
        });  
        if (isManualMode) {  
            toggleThresholdInput(overtimeDailyCheckbox, 'overtimeDailyThreshold');  
            toggleThresholdInput(overtimeDoubleTimeEnabledCheckbox, 'overtimeDoubleTimeThreshold');  
            toggleThresholdInput(overtimeSeventhDayEnabledCheckbox, 'overtimeSeventhDayOTThreshold', 'seventhDayOTDetailsBlock');  
        } else {  
            if (overtimeDailyThresholdInput) overtimeDailyThresholdInput.disabled = true;  
            if (overtimeDoubleTimeThresholdInput) overtimeDoubleTimeThresholdInput.disabled = true;  
            if (overtimeSeventhDayOTThresholdInput) overtimeSeventhDayOTThresholdInput.disabled = true;  
            if (overtimeSeventhDayDTThresholdInput) overtimeSeventhDayDTThresholdInput.disabled = true;  
            updateManualFieldsFromState(overtimeStateSelect.value, false);  
        }  
    }  
ECHO is on.
    function initializePage() {  
        if(payPeriodTypeSelect) payPeriodTypeSelect.value = window.settingsConfig.payPeriodType;  
        if(firstDayOfWeekSelect) firstDayOfWeekSelect.value = window.settingsConfig.firstDayOfWeek;  
        if(payPeriodStartDateInput) payPeriodStartDateInput.value = window.settingsConfig.payPeriodStartDate;  
        if(gracePeriodSelect) gracePeriodSelect.value = window.settingsConfig.gracePeriod;  
ECHO is on.
        if (window.settingsConfig.overtimeType === "company_state") {  
            if(otTypeCompanyStateRadio) otTypeCompanyStateRadio.checked = true;  
        } else if (window.settingsConfig.overtimeType === "employee_state") {  
            if(otTypeEmployeeStateRadio) otTypeEmployeeStateRadio.checked = true;  
        } else {   
            if(otTypeManualRadio) otTypeManualRadio.checked = true;  
        }  
        if (overtimeStateSelect) overtimeStateSelect.value = window.settingsConfig.overtimeState || "FLSA";  
        const holidayCheckbox = document.getElementById('overtimeHolidayEnabled');  
        const holidayRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeHolidayRate"]');  
        const holidayRateBlock = document.getElementById('overtimeHolidayRateBlock');  
        const holidaySelectionBlock = document.getElementById('holidaySelectionBlock');  
ECHO is on.
        if (holidayCheckbox) {  
            holidayCheckbox.checked = window.settingsConfig.overtimeHolidayEnabled;  
            const isEnabled = holidayCheckbox.checked;  
            holidayRateRadios.forEach(radio => {  
                radio.disabled = !isEnabled;  
                if (radio.value === window.settingsConfig.overtimeHolidayRate) radio.checked = true;  
            });  
            if (holidayRateBlock) {  
                holidayRateBlock.style.display = isEnabled ? 'flex' : 'none';  
            }  
            if (holidaySelectionBlock) holidaySelectionBlock.style.display = isEnabled ? 'block' : 'none';  
        }  
ECHO is on.
        initializeHolidayCheckboxes();  
ECHO is on.
        const daysOffCheckbox = document.getElementById('overtimeDaysOffEnabled');  
        const daysOffRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeDaysOffRate"]');  
        const daysOffRateBlock = document.getElementById('overtimeDaysOffRateBlock');  
ECHO is on.
        if (daysOffCheckbox) {  
            daysOffCheckbox.checked = window.settingsConfig.overtimeDaysOffEnabled;  
            const isEnabled = daysOffCheckbox.checked;  
            daysOffRateRadios.forEach(radio => {  
                radio.disabled = !isEnabled;  
                if (radio.value === window.settingsConfig.overtimeDaysOffRate) radio.checked = true;  
            });  
            if (daysOffRateBlock) daysOffRateBlock.style.display = isEnabled ? 'flex' : 'none';  
        }  
        updateOvertimeModeUI();   
ECHO is on.
        calculateAndDisplayPayPeriodEndDate(window.inWizardMode_Page); // Save during wizard mode  
    }  
    initializePage();  
    [payPeriodTypeSelect, firstDayOfWeekSelect, payPeriodStartDateInput].forEach(el => {  
        if(el) el.addEventListener('change', () => {   
            saveSetting(el);   
            calculateAndDisplayPayPeriodEndDate(true);   
        });  
    });  
ECHO is on.
    if (gracePeriodSelect) gracePeriodSelect.addEventListener('change', () => saveSetting(gracePeriodSelect));  
ECHO is on.
    [otTypeManualRadio, otTypeCompanyStateRadio, otTypeEmployeeStateRadio].forEach(el => {  
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
ECHO is on.
    // Holiday Overtime handling  
    const overtimeHolidayEnabledCheckbox = document.getElementById('overtimeHolidayEnabled');  
    const overtimeHolidayRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeHolidayRate"]');  
    const overtimeHolidayRateBlock = document.getElementById('overtimeHolidayRateBlock');  
ECHO is on.
    if (overtimeHolidayEnabledCheckbox) {  
        overtimeHolidayEnabledCheckbox.addEventListener('change', function() {  
            saveSetting(this);  
            const isEnabled = this.checked;  
            const holidaySelectionBlock = document.getElementById('holidaySelectionBlock');  
            overtimeHolidayRateRadios.forEach(radio => radio.disabled = !isEnabled);  
            if (overtimeHolidayRateBlock) {  
                overtimeHolidayRateBlock.style.display = isEnabled ? 'flex' : 'none';  
            }  
            if (holidaySelectionBlock) {  
                holidaySelectionBlock.style.display = isEnabled ? 'block' : 'none';  
            }  
        });  
    }  
ECHO is on.
    const selectAllHolidaysBtn = document.getElementById('selectAllHolidays');  
    const deselectAllHolidaysBtn = document.getElementById('deselectAllHolidays');  
ECHO is on.
    if (selectAllHolidaysBtn) {  
        selectAllHolidaysBtn.addEventListener('click', function() {  
            const checkboxes = document.querySelectorAll('#holidayCheckboxList input[type="checkbox"]');  
            checkboxes.forEach(cb => cb.checked = true);  
            saveHolidaySelection();  
        });  
    }  
ECHO is on.
    if (deselectAllHolidaysBtn) {  
        deselectAllHolidaysBtn.addEventListener('click', function() {  
            const checkboxes = document.querySelectorAll('#holidayCheckboxList input[type="checkbox"]');  
            checkboxes.forEach(cb => cb.checked = false);  
            saveHolidaySelection();  
        });  
    }  
ECHO is on.
    overtimeHolidayRateRadios.forEach(radio => {  
        radio.addEventListener('change', () => {  
            if (radio.checked && !radio.disabled) saveSetting(radio);  
        });  
    });  
ECHO is on.
    // Days Off Overtime handling  
    const overtimeDaysOffEnabledCheckbox = document.getElementById('overtimeDaysOffEnabled');  
    const overtimeDaysOffRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeDaysOffRate"]');  
    const overtimeDaysOffRateBlock = document.getElementById('overtimeDaysOffRateBlock');  
ECHO is on.
    if (overtimeDaysOffEnabledCheckbox) {  
        overtimeDaysOffEnabledCheckbox.checked = window.settingsConfig.overtimeDaysOffEnabled;  
        const isEnabled = overtimeDaysOffEnabledCheckbox.checked;  
        overtimeDaysOffRateRadios.forEach(radio => {  
            radio.disabled = !isEnabled;  
            if (radio.value === window.settingsConfig.overtimeDaysOffRate) radio.checked = true;  
        });  
        if (overtimeDaysOffRateBlock) overtimeDaysOffRateBlock.style.display = isEnabled ? 'flex' : 'none';  
ECHO is on.
        overtimeDaysOffEnabledCheckbox.addEventListener('change', function() {  
            saveSetting(this);  
            const isEnabled = this.checked;  
            overtimeDaysOffRateRadios.forEach(radio => radio.disabled = !isEnabled);  
            if (overtimeDaysOffRateBlock) {  
                overtimeDaysOffRateBlock.style.display = isEnabled ? 'flex' : 'none';  
            }  
        });  
    }  
ECHO is on.
    overtimeDaysOffRateRadios.forEach(radio => {  
        radio.addEventListener('change', () => {  
            if (radio.checked && !radio.disabled) saveSetting(radio);  
        });  
    });  
    const restrictionItemsConfig = [  
        { checkboxId: 'restrictByTimeDay',  buttonId: 'configureTimeDayBtn',  url: 'TimeDayRestrictionServlet' },  
        { checkboxId: 'restrictByLocation', buttonId: 'configureLocationBtn', url: 'LocationRestrictionServlet' },  
        { checkboxId: 'restrictByDevice',   buttonId: 'configureDeviceBtn',   url: 'DeviceRestrictionServlet' }  
    ];  
ECHO is on.
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
ECHO is on.
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
                .catch(() => {  
                    this.disabled = false;  
                    this.innerHTML = 'Next: Departments Setup <i class="fas fa-arrow-right"></i>';  
                });  
            });  
        }  
    }  
});  
// Days Off Overtime handling  
const overtimeDaysOffEnabledCheckbox = document.getElementById('overtimeDaysOffEnabled');  
const overtimeDaysOffRateRadios = document.querySelectorAll('input[type="radio"][name="OvertimeDaysOffRate"]');  
const overtimeDaysOffRateBlock = document.getElementById('overtimeDaysOffRateBlock');  
if (overtimeDaysOffEnabledCheckbox) {  
    overtimeDaysOffEnabledCheckbox.checked = window.settingsConfig.overtimeDaysOffEnabled;  
    const isEnabled = overtimeDaysOffEnabledCheckbox.checked;  
    overtimeDaysOffRateRadios.forEach(radio => {  
        radio.disabled = !isEnabled;  
        if (radio.value === window.settingsConfig.overtimeDaysOffRate) radio.checked = true;  
    });  
    if (overtimeDaysOffRateBlock) overtimeDaysOffRateBlock.style.display = isEnabled ? 'flex' : 'none';  
ECHO is on.
    overtimeDaysOffEnabledCheckbox.addEventListener('change', function() {  
        saveSetting(this);  
        const isEnabled = this.checked;  
        overtimeDaysOffRateRadios.forEach(radio => radio.disabled = !isEnabled);  
        if (overtimeDaysOffRateBlock) overtimeDaysOffRateBlock.style.display = isEnabled ? 'flex' : 'none';  
    });  
}  
overtimeDaysOffRateRadios.forEach(radio => {  
    radio.addEventListener('change', () => {  
        if (radio.checked && !radio.disabled) saveSetting(radio);  
    });  
});  
// Custom Holiday handling  
const customHolidayCheckbox = document.getElementById('customHolidayCheckbox');  
const customHolidayDate = document.getElementById('customHolidayDate');  
const customHolidayName = document.getElementById('customHolidayName');  
if (customHolidayCheckbox && customHolidayDate && customHolidayName) {  
    customHolidayCheckbox.checked = window.settingsConfig.customHolidayDate && window.settingsConfig.customHolidayName;  
ECHO is on.
    customHolidayCheckbox.addEventListener('change', function() {  
        if (this.checked) {  
            if (customHolidayDate.value && customHolidayName.value) {  
                saveSetting(customHolidayDate);  
                saveSetting(customHolidayName);  
                saveHolidaySelection();  
            }  
        } else {  
            saveSetting({ name: 'CustomHolidayDate', type: 'hidden' }, '');  
            saveSetting({ name: 'CustomHolidayName', type: 'hidden' }, '');  
            saveHolidaySelection();  
        }  
    });  
ECHO is on.
    customHolidayDate.addEventListener('change', function() {  
        if (customHolidayCheckbox.checked && this.value) {  
            saveSetting(this);  
            saveHolidaySelection();  
        }  
    });  
ECHO is on.
    customHolidayName.addEventListener('change', function() {  
        if (customHolidayCheckbox.checked && this.value) {  
            saveSetting(this);  
            saveHolidaySelection();  
        }  
    });  
}  
