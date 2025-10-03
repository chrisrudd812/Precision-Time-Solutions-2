// js/reassign.js
document.addEventListener('DOMContentLoaded', function() {
    const reassignTypeSelect = document.getElementById('reassignType');
    const fromValueSelect = document.getElementById('fromValue');
    const toValueSelect = document.getElementById('toValue');
    const applyButton = document.getElementById('applyButton');
    const validationMessageDiv = document.getElementById('validationMessage');
    const reassignForm = document.getElementById('reassignForm');

    // 'allData' and 'initialReassignTypeJS' are expected to be defined globally
    // in a <script> tag in reassign.jsp before this script runs.
    
    // Check if critical global variables are defined
    if (typeof allData === 'undefined' || typeof initialReassignTypeJS === 'undefined') {
        console.error("reassign.js: 'allData' or 'initialReassignTypeJS' is not defined. Check reassign.jsp script block.");
        if (validationMessageDiv) {
            validationMessageDiv.textContent = "Page configuration error. Cannot load reassignment options.";
            validationMessageDiv.style.display = 'block';
        }
        if (applyButton) applyButton.disabled = true;
        return; // Stop further execution if core data is missing
    }

    function populateFromToDropdowns() {
        const selectedType = reassignTypeSelect.value;
        let dataList = [];

        if (allData && allData[selectedType]) {
            dataList = allData[selectedType];
        } else {
            console.warn("reassign.js: No data found for type:", selectedType, "in allData object. Dropdowns will be empty.");
        }
        
        fromValueSelect.innerHTML = '<option value="">-- Select Current --</option>';
        toValueSelect.innerHTML = '<option value="">-- Select New --</option>';

        if (Array.isArray(dataList)) {
            dataList.forEach(item => {
                // Ensure item and item.name exist. All data from servlet should be {name: "value"}
                const itemName = item && item.name ? item.name : null; 
                
                if (itemName !== null && typeof itemName !== 'undefined') { 
                    const optionFrom = document.createElement('option');
                    optionFrom.value = itemName;
                    optionFrom.textContent = itemName;
                    fromValueSelect.appendChild(optionFrom);

                    const optionTo = document.createElement('option');
                    optionTo.value = itemName;
                    optionTo.textContent = itemName;
                    toValueSelect.appendChild(optionTo);
                } else {
                    console.warn("reassign.js: Invalid item found in dataList for type", selectedType, ":", item);
                }
            });
        } else {
            console.error("reassign.js: Data for type", selectedType, "is not an array:", dataList);
        }
        validateSelections();
    }

    function validateSelections() {
        const fromVal = fromValueSelect.value;
        const toVal = toValueSelect.value;
        let isValid = true;
        let message = "";

        if (!fromVal || !toVal) { // Check if either is empty (i.e., "-- Select --")
             message = "Please make a selection for both 'From' and 'To'.";
             isValid = false;
        } else if (fromVal === toVal) {
            message = "'From' and 'To' selections cannot be the same.";
            isValid = false;
        }

        if (!isValid) {
            validationMessageDiv.textContent = message;
            validationMessageDiv.style.display = message ? 'block' : 'none';
            if (applyButton) applyButton.disabled = true;
        } else {
            if (validationMessageDiv) validationMessageDiv.style.display = 'none';
            if (applyButton) applyButton.disabled = false;
        }
    }

    if (reassignTypeSelect) {
        // Set initial type from URL parameter (passed via initialReassignTypeJS)
        if (initialReassignTypeJS && typeof allData[initialReassignTypeJS] !== 'undefined') {
            reassignTypeSelect.value = initialReassignTypeJS;
        } else {
            console.warn("reassign.js: Initial reassign type '" + initialReassignTypeJS + "' not found in data keys or invalid. Defaulting to current dropdown value (likely 'department').");
             // If initialReassignTypeJS is bad, it will just use the first option in the HTML select
        }
        populateFromToDropdowns(); // Populate based on the (possibly defaulted) selection
        reassignTypeSelect.addEventListener('change', populateFromToDropdowns);
    } else {
        console.error("reassign.js: #reassignType select element not found.");
    }

    if (fromValueSelect) {
        fromValueSelect.addEventListener('change', validateSelections);
    } else {
        console.error("reassign.js: #fromValue select element not found.");
    }

    if (toValueSelect) {
        toValueSelect.addEventListener('change', validateSelections);
    } else {
        console.error("reassign.js: #toValue select element not found.");
    }

    if (reassignForm) {
        reassignForm.addEventListener('submit', function(event) {
            const fromVal = fromValueSelect.value;
            const toVal = toValueSelect.value;

            if (!fromVal || !toVal) {
                if (validationMessageDiv) {
                    validationMessageDiv.textContent = "Please select both 'From' and 'To' values.";
                    validationMessageDiv.style.display = 'block';
                }
                if (applyButton) applyButton.disabled = true;
                event.preventDefault();
                return;
            }
            if (fromVal === toVal) {
                 if (validationMessageDiv) {
                    validationMessageDiv.textContent = "'From' and 'To' selections cannot be the same.";
                    validationMessageDiv.style.display = 'block';
                }
                if (applyButton) applyButton.disabled = true;
                event.preventDefault();
                return;
            }
            if (validationMessageDiv) validationMessageDiv.style.display = 'none';
            if (applyButton) applyButton.disabled = false;
            
        });
    } else {
        console.error("reassign.js: #reassignForm not found.");
    }

    // Initial validation call to set button state
    if(applyButton && fromValueSelect && toValueSelect && validationMessageDiv){
      validateSelections();
    } else {
      console.warn("reassign.js: One or more elements for initial validation (applyButton, fromValueSelect, toValueSelect, validationMessageDiv) not found.");
    }
});