(function() {
    const customHolidayCheckbox = document.getElementById('customHolidayCheckbox');
    const customHolidayDate = document.getElementById('customHolidayDate');
    const customHolidayName = document.getElementById('customHolidayName');

    if (customHolidayCheckbox && customHolidayDate && customHolidayName) {
        const hasCustomHoliday = window.settingsConfig.customHolidayDate && window.settingsConfig.customHolidayName;
        customHolidayCheckbox.checked = hasCustomHoliday;
        customHolidayDate.disabled = !hasCustomHoliday;
        customHolidayName.disabled = !hasCustomHoliday;
        
        customHolidayCheckbox.addEventListener('change', function() {
            const isChecked = this.checked;
            customHolidayDate.disabled = !isChecked;
            customHolidayName.disabled = !isChecked;
            saveSetting({ name: 'CustomHolidayEnabled', type: 'checkbox', checked: isChecked }, isChecked.toString());
        });
        
        customHolidayDate.addEventListener('change', function() {
            if (customHolidayCheckbox.checked && this.value) saveSetting(this);
        });
        
        customHolidayName.addEventListener('change', function() {
            if (customHolidayCheckbox.checked && this.value) saveSetting(this);
        });
    }
})();
