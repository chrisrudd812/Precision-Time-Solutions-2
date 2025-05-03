// --- Global variables ---
let selectedPunchId = null;
let selectedRowElement = null;

// --- Helper Function to get cell text safely ---
function getCellText(row, index) {
    const cells = row.cells;
    if (cells && cells.length > index && cells[index]) {
        // Use textContent for broad compatibility, trim whitespace
        return cells[index].textContent?.trim() || "";
    }
    return "";
}

// --- Helper function to convert various date strings to yyyy-MM-dd ---
function formatDateToYyyyMmDd(dateString) {
    if (!dateString || typeof dateString !== 'string') return '';
    dateString = dateString.trim();
    // console.log("Parsing date string:", dateString); // Uncomment for debugging dates

    // Check if already in correct format
    if (/^\d{4}-\d{2}-\d{2}$/.test(dateString)) {
        // console.log("Date already in yyyy-MM-dd format."); // Uncomment for debugging dates
        return dateString;
    }
    try {
        let year, month, day;
        // Try parsing MM/DD/YYYY or M/D/YY etc.
        if (dateString.includes('/')) {
            const parts = dateString.split('/');
            if (parts.length === 3) {
                month = parts[0].padStart(2, '0');
                day = parts[1].padStart(2, '0');
                // Handle 2-digit or 4-digit year
                year = parts[2].length === 4 ? parts[2] : (parseInt(parts[2]) < 50 ? '20' + parts[2] : '19' + parts[2]);
                // Basic validation of parts
                if (parseInt(year) > 1900 && parseInt(month) >= 1 && parseInt(month) <= 12 && parseInt(day) >= 1 && parseInt(day) <= 31) {
                    const formatted = `${year}-${month}-${day}`;
                    // console.log(`Parsed ${dateString} to ${formatted}`); // Uncomment for debugging dates
                    return formatted;
                } else { console.warn("Invalid date parts detected:", year, month, day); }
            }
        }
        // Fallback: Try letting the Date object parse it (less reliable for specific formats)
        // console.log("Falling back to Date object parsing for:", dateString); // Uncomment for debugging dates
        const dateObj = new Date(dateString);
        // Check if the resulting date is valid and the year makes sense
        if (dateObj && !isNaN(dateObj.getTime())) {
            const parsedYear = dateObj.getFullYear();
             if (parsedYear > 1900) { // Avoid potential issues with year 0 or very old dates
                const parsedMonth = (dateObj.getMonth() + 1).toString().padStart(2, '0'); // Month is 0-indexed
                const parsedDay = dateObj.getDate().toString().padStart(2, '0');
                const formatted = `${parsedYear}-${parsedMonth}-${parsedDay}`;
                // console.log(`Parsed ${dateString} via Date object to ${formatted}`); // Uncomment for debugging dates
                return formatted;
             } else { console.warn("Parsed year seems invalid:", parsedYear); }
        } else { console.warn("Date object creation returned invalid date for:", dateString); }
    } catch (e) { console.error("Error parsing date string:", dateString, e); }

    // If all parsing fails
    console.error("Failed to parse date:", dateString, "- Input field will be empty.");
    return '';
}

// --- Helper to parse time string (HH:MM:SS AM/PM or HH:MM:SS 24hr) to HH:mm:ss (24hr) ---
function parseTimeTo24Hour(timeStr) {
    if (!timeStr || typeof timeStr !== 'string' || timeStr.trim() === '') return '';
    timeStr = timeStr.trim();

    // Check if already in HH:mm:ss (24hr) format
     if (/^\d{2}:\d{2}:\d{2}$/.test(timeStr)) {
          // Optionally validate hours/minutes/seconds range here if needed
          return timeStr;
     }
     // Check for H:mm:ss (24hr) format
     if (/^\d{1}:\d{2}:\d{2}$/.test(timeStr)) {
          // Optionally validate hours/minutes/seconds range here if needed
          return timeStr.padStart(8,'0'); // Pad single digit hour
     }


    // Try parsing HH:MM:SS AM/PM format
    // Regex captures hours, minutes, seconds, and optional AM/PM (case-insensitive)
    const timeParts = timeStr.match(/(\d{1,2}):(\d{2}):(\d{2})\s*(AM|PM)?/i);
    if (timeParts) {
        let hours = parseInt(timeParts[1], 10);
        const minutes = timeParts[2].padStart(2,'0'); // Ensure 2 digits
        const seconds = timeParts[3].padStart(2,'0'); // Ensure 2 digits
        const modifier = timeParts[4]; // AM/PM part

        // Adjust hours for AM/PM if modifier exists
        if (modifier) {
             const upperModifier = modifier.toUpperCase();
            if (upperModifier === 'PM' && hours < 12) {
                hours += 12;
            } else if (upperModifier === 'AM' && hours === 12) { // Handle 12 AM (midnight)
                hours = 0;
            }
            // No change needed for 12 PM or hours 1-11 AM
        }
        // Format hours to two digits (e.g., 00 for midnight, 07 for 7 AM)
        const hoursStr = hours.toString().padStart(2, '0');
        const formattedTime = `${hoursStr}:${minutes}:${seconds}`;
        // console.log(`Parsed time ${timeStr} to ${formattedTime}`); // Uncomment for debugging time
        return formattedTime;
    }

    // If parsing fails
    console.warn("Could not parse time string:", timeStr, "Returning empty string.");
    return ''; // Return empty if format is unrecognized
}

// --- Function to remove query parameters ---
function clearUrlParams() {
    // Check if history API is supported
    if (window.history && window.history.replaceState) {
        // Get the current path without query string or hash
        const cleanUrl = window.location.origin + window.location.pathname;
        try {
            // Replace the current history state without reloading
            window.history.replaceState({}, document.title, cleanUrl);
        } catch (e) {
            console.error("Error clearing URL params:", e);
        }
    }
}

// --- Select Row Function (Globally accessible) ---
// Attaches/removes 'selected' class and updates global state/buttons
window.selectPunchRow = function(rowElement) {
    // console.log("selectPunchRow called for:", rowElement); // Debug log
    const editButton = document.getElementById('btnEditRow');
    const deleteButton = document.getElementById('btnDeleteRow');
    const tableBody = document.getElementById('punchTableBody');

    // Find the currently selected row, if any
    let currentlySelected = tableBody ? tableBody.querySelector('tr.selected') : null;

    // If a different row was already selected, deselect it first
    if (currentlySelected && currentlySelected !== rowElement) {
        currentlySelected.classList.remove('selected');
        // console.log("Deselected previous row:", currentlySelected); // Debug log
    }

    // Toggle the selected state of the clicked row
    if (rowElement) {
        rowElement.classList.toggle('selected');

        // Update global variables and button states based on new selection state
        if (rowElement.classList.contains('selected')) {
            selectedRowElement = rowElement;
            // Ensure data-punch-id exists and is read correctly
            selectedPunchId = rowElement.dataset.punchId || null;
            // console.log("Selected Punch ID:", selectedPunchId); // Debug log
            // Enable buttons only if a valid punch ID was found
            if (editButton) editButton.disabled = !selectedPunchId;
            if (deleteButton) deleteButton.disabled = !selectedPunchId;
        } else {
            // Row was deselected by clicking it again
            selectedRowElement = null;
            selectedPunchId = null;
            // console.log("Row deselected by toggle"); // Debug log
            if (editButton) editButton.disabled = true;
            if (deleteButton) deleteButton.disabled = true;
        }
    } else {
         // Should not happen if called from valid row click, but handle defensively
         selectedRowElement = null;
         selectedPunchId = null;
         if(editButton) editButton.disabled = true;
         if(deleteButton) deleteButton.disabled = true;
    }
};

// --- Helper Function to Hide Edit Modal ---
// Also deselects the row when modal is closed via cancel/close button
function hideEditModal() {
    const editModal = document.getElementById('editPunchModal');
    if(editModal) {
        editModal.classList.remove('modal-visible');
    }
    // Deselect row when hiding modal through explicit cancel/close actions
    deselectRow();
}

// --- Helper Function to Populate Edit Modal Fields ---
function populateEditModal() {
    if (!selectedRowElement || !selectedPunchId) {
        console.error("populateEditModal called without a selected row or punch ID.");
        alert("Error: Cannot populate edit form. Please select a valid punch record again.");
        return;
    }
    // console.log("Populating edit modal for row:", selectedRowElement); // Debug log

    // Get data from the selected table row cells
    const dateStrFromTable = getCellText(selectedRowElement, 0); // Assumes Date is in cell 0
    const inTimeStr = getCellText(selectedRowElement, 1);        // Assumes IN is in cell 1
    const outTimeStr = getCellText(selectedRowElement, 2);       // Assumes OUT is in cell 2
    const typeStrFromTable = getCellText(selectedRowElement, 4); // Assumes Type is in cell 4

    // console.log(`Data from row: ID=${selectedPunchId}, RawDate=${dateStrFromTable}, In=${inTimeStr}, Out=${outTimeStr}, Type=${typeStrFromTable}`); // Debug log

    // Get modal form fields
    const punchIdField = document.getElementById('editPunchIdField');
    const employeeIdField = document.getElementById('editEmployeeId'); // Hidden field for EID
    const dateField = document.getElementById('editDate');
    const inTimeField = document.getElementById('editInTime');
    const outTimeField = document.getElementById('editOutTime');
    const typeField = document.getElementById('editPunchType'); // Select dropdown

    // Populate fields
    if(punchIdField) {
        punchIdField.value = selectedPunchId;
    } else { console.error("Edit Modal: #editPunchIdField not found!"); }

    // Populate hidden EID field - Assuming EID is available globally or can be retrieved
    const employeeDropdown = document.getElementById('employeesDropDown');
    const currentEID = employeeDropdown ? employeeDropdown.value : null;
    if (employeeIdField && currentEID) {
         employeeIdField.value = currentEID;
    } else if (!currentEID) {
         console.error("Edit Modal: Could not determine current Employee ID to populate hidden field.");
         // Optionally disable form submission or show an error
    } else {
        console.error("Edit Modal: #editEmployeeId field not found!");
    }


    if(dateField) {
        const formattedDate = formatDateToYyyyMmDd(dateStrFromTable);
        dateField.value = formattedDate; // Set to yyyy-MM-dd format
        if (!formattedDate && dateStrFromTable) {
            console.error(`Failed to format date '${dateStrFromTable}' for input field.`);
            // Optionally clear the field or show a specific error
            dateField.value = '';
        }
        // else { console.log(`Set date field value to: '${formattedDate}'`); } // Debug log
    } else { console.error("Edit Modal: #editDate field not found!"); }

    if(inTimeField) {
        inTimeField.value = parseTimeTo24Hour(inTimeStr); // Set to HH:mm:ss format or empty
        // console.log(`Set IN time field value to: '${inTimeField.value}' from '${inTimeStr}'`); // Debug log
    } else { console.error("Edit Modal: #editInTime field not found!"); }

    if(outTimeField) {
        outTimeField.value = parseTimeTo24Hour(outTimeStr); // Set to HH:mm:ss format or empty
        // console.log(`Set OUT time field value to: '${outTimeField.value}' from '${outTimeStr}'`); // Debug log
    } else { console.error("Edit Modal: #editOutTime field not found!"); }

    if(typeField) {
        // Try to match the text from the table cell to an option value
        let foundMatch = false;
        for (let i = 0; i < typeField.options.length; i++) {
            if (typeField.options[i].value.toLowerCase() === typeStrFromTable.toLowerCase()) {
                typeField.value = typeField.options[i].value;
                foundMatch = true;
                break;
            }
        }
        // If no exact match, maybe default or log a warning
        if (!foundMatch) {
            console.warn(`Could not find exact match for Punch Type "${typeStrFromTable}" in dropdown. Defaulting or leaving as is.`);
            // Optionally set a default value like 'Supervisor Override' or leave the first option selected
            // typeField.value = 'Supervisor Override'; // Example default
        }
         // console.log(`Set punch type field value to: '${typeField.value}'`); // Debug log
    } else { console.error("Edit Modal: #editPunchType field not found!"); }

    // console.log("Edit modal fields populated."); // Debug log
}

// --- Function to Handle Deleting a Punch Record ---
// Uses fetch to call the servlet with deletePunch action
function deletePunchRecord(punchIdToDelete) {
    // console.log("Attempting to delete punch ID:", punchIdToDelete); // Debug log
    if (!punchIdToDelete) {
        alert("Cannot delete: Punch ID is missing.");
        return;
    }

    // Prepare data for deletion request
    const formData = new URLSearchParams(); // Use URLSearchParams for consistency now
    formData.append('action', 'deletePunch');
    formData.append('deletePunchId', punchIdToDelete);
    // Include EID if available and potentially needed by server for context/logging
    const employeeDropdown = document.getElementById('employeesDropDown');
    const currentEID = employeeDropdown ? employeeDropdown.value : null;
     if (currentEID) {
         formData.append('eid', currentEID); // Send EID for context if available
     }

    console.log("Submitting delete request:", formData.toString()); // Debug log

    // Send delete request to the servlet
    fetch('AddEditAndDeletePunchesServlet', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
        body: formData
    })
    .then(response => {
        // Expecting JSON response from the servlet
        if (response.ok && response.headers.get("Content-Type")?.includes("application/json")) {
            return response.json();
        } else {
            // Handle non-JSON or error responses
            return response.text().then(text => {
                console.error("Delete Response Text (Not JSON or Error):", text);
                throw new Error(`Server error or unexpected response during delete: ${response.status} - ${text || 'No details'}`);
            });
        }
    })
    .then(data => { // Process the parsed JSON data from servlet
        console.log("Delete Response Data:", data);
        const notificationModal = document.getElementById('notificationModal');
        const notificationMessage = document.getElementById('notificationMessage');
        const modalContent = notificationModal ? notificationModal.querySelector('.modal-content') : null;

        if (notificationModal && notificationMessage && modalContent) {
            notificationMessage.textContent = data.message || data.error || 'Deletion processed.';
            if(data.success) {
                 modalContent.classList.remove('error');
            } else {
                 modalContent.classList.add('error');
            }
            notificationModal.classList.add('modal-visible');

             // Add listener to OK button - reload ONLY on success?
             document.getElementById('okButton')?.addEventListener('click', () => {
                 notificationModal.classList.remove('modal-visible');
                 if(data.success) {
                     // Option 1: Remove row visually (more complex if pagination/sorting exists)
                     // if (selectedRowElement && selectedRowElement.dataset.punchId == punchIdToDelete) {
                     //    selectedRowElement.remove();
                     //    deselectRow(); // Clear selection state and disable buttons
                     // } else { window.location.reload(); /* Fallback reload */ }

                     // Option 2: Simple page reload (easier, ensures data consistency)
                     console.log("Reloading page after successful delete.");
                     window.location.reload();
                 }
             }, { once: true }); // Ensure listener added only once per modal display
        } else {
             // Fallback alert
             alert(data.message || data.error || (data.success ? 'Deleted successfully.' : 'Deletion failed.'));
             if(data.success) { window.location.reload(); }
        }
    })
    .catch(error => {
        console.error('Error deleting punch fetch:', error);
        const notificationModal = document.getElementById('notificationModal');
        const notificationMessage = document.getElementById('notificationMessage');
        const modalContent = notificationModal ? notificationModal.querySelector('.modal-content') : null;
         if (notificationModal && notificationMessage && modalContent) {
             notificationMessage.textContent = 'Error deleting: ' + error.message;
             modalContent.classList.add('error');
             notificationModal.classList.add('modal-visible');
              document.getElementById('okButton')?.addEventListener('click', () => {
                  notificationModal.classList.remove('modal-visible');
              }, { once: true });
         } else {
             alert('Error deleting: ' + error.message); // Fallback
         }
    });
}


// --- ** Updated handleEditPunchSubmit to send URL-Encoded Data ** ---
function handleEditPunchSubmit(event) {
    event.preventDefault(); // Stop default form submission
    console.log("Edit Punch form submitted.");

    const form = event.target; // The form element
    const actionUrl = form.getAttribute('action');

    if (!actionUrl) {
        console.error("Form action attribute is missing!");
        alert("Error: Cannot determine where to submit the form.");
        return;
    }

    // --- Create URLSearchParams instead of FormData ---
    // Use FormData temporarily to easily grab all form fields
    const tempFormData = new FormData(form);
    // Create URLSearchParams to hold the data for submission
    const urlEncodedData = new URLSearchParams();
    for (const pair of tempFormData.entries()) {
        urlEncodedData.append(pair[0], pair[1]);
    }
    // Log the data being sent
    console.log("Submitting edit data to URL:", actionUrl);
    // Use .toString() on URLSearchParams to see the encoded string
    console.log("Submitting URL-encoded data:", urlEncodedData.toString());

    fetch(actionUrl, {
        method: form.method, // Should be POST
        // --- Set the correct Content-Type header ---
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8'
        },
        // --- Send the URL-encoded string as the body ---
        body: urlEncodedData // fetch handles URLSearchParams object directly
    })
    .then(response => {
        // Check if response is ok AND is json (Servlet should return JSON now)
        if (response.ok && response.headers.get("Content-Type")?.includes("application/json")) {
            return response.json(); // Parse JSON body
        } else {
            // Handle non-JSON or error responses
            return response.text().then(text => {
                console.error("Server Response Text (Not JSON or Error):", text);
                throw new Error(`Server error or unexpected response: ${response.status} - ${text || 'No details'}`);
            });
        }
    })
    .then(data => { // Process the parsed JSON data from the servlet
        console.log("Server Response Data:", data);
        const notificationModal = document.getElementById('notificationModal');
        const notificationMessage = document.getElementById('notificationMessage');
        const modalContent = notificationModal ? notificationModal.querySelector('.modal-content') : null;

        if (notificationModal && notificationMessage && modalContent) {
             // Use specific keys from servlet's JSON response ("message" or "error")
            notificationMessage.textContent = data.message || data.error || 'Operation completed.';
            // Add/remove error class for styling
            if(data.success) {
                 modalContent.classList.remove('error');
            } else {
                 modalContent.classList.add('error');
            }
            notificationModal.classList.add('modal-visible');

             // Add listener to OK button - reload ONLY on success?
             document.getElementById('okButton')?.addEventListener('click', () => {
                 notificationModal.classList.remove('modal-visible');
                if(data.success) {
                     hideEditModal(); // Close the edit modal first
                     console.log("Reloading page after successful update.");
                     window.location.reload(); // Simple reload to see changes
                }
            }, { once: true }); // Ensure listener added only once per modal display
        } else {
             // Fallback alert
             alert(data.message || data.error || (data.success ? 'Update successful.' : 'Update failed.'));
             if(data.success) {
                 hideEditModal();
                 window.location.reload();
             }
        }
    })
    .catch(error => {
        console.error('Error submitting edit fetch:', error);
        const notificationModal = document.getElementById('notificationModal');
        const notificationMessage = document.getElementById('notificationMessage');
        const modalContent = notificationModal ? notificationModal.querySelector('.modal-content') : null;
         if (notificationModal && notificationMessage && modalContent) {
             notificationMessage.textContent = 'Submission fetch error: ' + error.message;
              modalContent.classList.add('error');
             notificationModal.classList.add('modal-visible');
              document.getElementById('okButton')?.addEventListener('click', () => {
                  notificationModal.classList.remove('modal-visible');
              }, { once: true });
         } else {
            alert('Submission fetch error: ' + error.message); // Fallback
         }
    });
}


// --- Helper to deselect any selected row ---
function deselectRow() {
    const selected = document.querySelector('#punchTableBody tr.selected');
    if (selected) {
        selected.classList.remove('selected');
    }
    // Reset global state
    selectedPunchId = null;
    selectedRowElement = null;
    // Disable action buttons
    const editButton = document.getElementById('btnEditRow');
    const deleteButton = document.getElementById('btnDeleteRow');
    if (editButton) editButton.disabled = true;
    if (deleteButton) deleteButton.disabled = true;
    // console.log("Row deselected programmatically."); // Debug log
}

// --- Run Code After DOM Loaded ---
document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Punches Page DOMContentLoaded START ---");

    // Assign references to frequently used elements
    // console.log("Assigning element references..."); // Debug log
    const employeeIdDropdown = document.getElementById('employeesDropDown');
    const addHoursButton = document.getElementById('btnAddHours');
    const editRowButton = document.getElementById('btnEditRow');
    const deleteRowButton = document.getElementById('btnDeleteRow');
    const addModal = document.getElementById('addHoursModal');
    const editModal = document.getElementById('editPunchModal');
    const closeAddModalButton = document.getElementById('closeAddModal');
    const cancelAddHoursButton = document.getElementById('cancelAddHours');
    const closeEditModalButton = document.getElementById('closeEditModal');
    const cancelEditPunchButton = document.getElementById('cancelEditPunch');
    const addHoursEmployeeIdField = document.getElementById('addHoursEmployeeIdField'); // Hidden field in add modal
    const editEmployeeIdField = document.getElementById('editEmployeeId'); // Hidden field in edit modal
    const notificationBar = document.getElementById('notification-bar'); // Top notification bar (if used)
    const notificationModal = document.getElementById('notificationModal'); // JS Notification modal
    const notificationMessage = document.getElementById('notificationMessage'); // Text inside notification modal
    const notificationOkButton = document.getElementById('okButton'); // OK button in notification modal
    const closeNotificationModalButton = document.getElementById('closeNotificationModal'); // Close button in notification modal
    const addHoursForm = document.getElementById('addHoursForm');
    const addHoursDateInput = document.getElementById('addHoursDate');
    const addHoursPunchTypeDropdown = document.getElementById('addHoursPunchTypeDropDown');
    const tableBody = document.getElementById('punchTableBody');
    const editPunchForm = document.getElementById('editPunchForm');
    // console.log("Finished assigning references."); // Debug log

    // --- Button state handler based on employee selection ---
    function handleControlState() {
        const isEmployeeSelected = employeeIdDropdown && employeeIdDropdown.value !== "";
        // Enable "Add Hours" only if an employee is selected
        if(addHoursButton) {
            addHoursButton.disabled = !isEmployeeSelected;
        }
        // Edit/Delete buttons are handled by row selection (selectPunchRow/deselectRow)
        // console.log(`Control state update: Employee Selected=${isEmployeeSelected}, AddHrs Button Found=${!!addHoursButton}, AddHrs Disabled=${addHoursButton?.disabled}`); // Debug log
    }

    // --- Notification Bar Handling (if using the top bar for redirects) ---
    if (notificationBar && notificationBar.textContent.trim() !== '') {
        // console.log("Notification bar message detected:", notificationBar.textContent.trim()); // Debug log
         notificationBar.style.display = 'block'; // Show it
        // Fade out after 5 seconds
        setTimeout(() => {
            notificationBar.style.transition = 'opacity 0.5s ease-out';
            notificationBar.style.opacity = '0';
             // Hide completely after fade out
             setTimeout(() => { notificationBar.style.display = 'none'; }, 600);
        }, 5000);
        // Clean URL after showing message from redirect
        clearUrlParams();
    } else {
        // Ensure it's hidden if no message
        if(notificationBar) { notificationBar.style.display = 'none'; }
    }

    // --- Attach Event Listeners ---
    // console.log("Attempting to attach event listeners..."); // Debug log

    // Add Hours Button -> Show Add Modal
    if (addHoursButton) {
         // console.log("Attaching click listener to #btnAddHours"); // Debug log
         addHoursButton.addEventListener('click', () => {
             // console.log('Add Hours Button: Click detected!'); // Debug log
             const currentEmployeeId = employeeIdDropdown ? employeeIdDropdown.value : null;
             // console.log('Add Hours Button: Current Employee ID =', currentEmployeeId); // Debug log
             if (currentEmployeeId && addModal) {
                 // console.log('Add Hours Button: Setting fields...'); // Debug log
                 // Set hidden EID field
                 if(addHoursEmployeeIdField) { addHoursEmployeeIdField.value = currentEmployeeId; }
                 else { console.error('Add Hours Button: #addHoursEmployeeIdField not found!'); return; }
                 // Reset form, set defaults
                 if(addHoursForm) { addHoursForm.reset(); } else { console.warn('Add Hours Button: #addHoursForm not found.'); }
                 if(addHoursDateInput) {
                     try { // Set default date to today yyyy-MM-dd
                         const today = new Date();
                         addHoursDateInput.valueAsDate = today; // Easier way to set date input
                     } catch (e) { console.error('Add Hours Button: Error setting default date:', e); addHoursDateInput.value = ''; }
                 } else { console.warn('Add Hours Button: #addHoursDateInput not found.'); }
                 if(addHoursPunchTypeDropdown) { addHoursPunchTypeDropdown.value = 'Supervisor Override'; } // Default type
                 else { console.warn('Add Hours Button: #addHoursPunchTypeDropDown not found.'); }

                 // Show the modal
                 addModal.classList.add('modal-visible');
                 // console.log('Add Hours Button: Modal should be visible.'); // Debug log
             } else if (!currentEmployeeId) {
                 console.warn("Add Hours Button: No employee selected.");
                 alert("Please select an employee first to add hours.");
             } else {
                 console.error('Add Hours Button: #addHoursModal not found!');
             }
         });
    } else { console.error("Could not attach listener: #btnAddHours not found."); }

    // Edit Row Button -> Populate and Show Edit Modal
    if (editRowButton) {
         // console.log("Attaching listener to #btnEditRow"); // Debug log
         editRowButton.addEventListener('click', () => {
             // console.log("Edit Row button clicked. Selected Row Element:", selectedRowElement); // Debug log
             if (!selectedRowElement || !selectedPunchId) {
                 alert('Please select a punch record row from the table first.');
                 return;
             }
             // Populate modal with data from selected row
             populateEditModal();
             // Show the modal
             if(editModal) { editModal.classList.add('modal-visible'); }
             else { console.error("Cannot open Edit Modal: #editPunchModal not found!"); }
         });
    } else { console.error("Could not attach listener: #btnEditRow not found."); }

    // Delete Row Button -> Confirm and Call Delete Function
     if (deleteRowButton) {
         // console.log("Attaching listener to #btnDeleteRow"); // Debug log
         deleteRowButton.addEventListener('click', () => {
             // console.log("Delete Row button clicked. Selected Punch ID:", selectedPunchId); // Debug log
             if (!selectedPunchId) {
                 alert('Please select a punch record row from the table first.');
                 return;
             }
             // Confirm deletion with user
             if (confirm(`Are you sure you want to delete the punch record (ID: ${selectedPunchId})? This action cannot be undone.`)) {
                 deletePunchRecord(selectedPunchId); // Call delete function
             } else {
                 console.log("Deletion cancelled by user.");
             }
         });
     } else { console.error("Could not attach listener: #btnDeleteRow not found."); }

    // Edit Punch Form Submit -> Call Handler
    if (editPunchForm) {
         editPunchForm.addEventListener('submit', handleEditPunchSubmit); // Calls updated function
         // console.log("Attaching submit listener to #editPunchForm"); // Debug log
    } else { console.error("Could not attach submit listener: #editPunchForm not found!"); }

    // Add Hours Form Submit (using standard submit, no JS handler needed unless you want AJAX)

    // Modal Close/Cancel Buttons
    if(closeAddModalButton) { closeAddModalButton.addEventListener('click', () => { if(addModal) addModal.classList.remove('modal-visible'); }); }
    if(cancelAddHoursButton) { cancelAddHoursButton.addEventListener('click', () => { if(addModal) addModal.classList.remove('modal-visible'); }); }
    if(closeEditModalButton) { closeEditModalButton.addEventListener('click', hideEditModal); } // Use helper to also deselect row
    if(cancelEditPunchButton) { cancelEditPunchButton.addEventListener('click', hideEditModal); } // Use helper to also deselect row

    // Table Row Click Listener (using event delegation on tbody)
    if (tableBody) {
        // console.log("Attaching click listener to punch table body"); // Debug log
        tableBody.addEventListener('click', function(event) {
             // Find the closest ancestor TR element that was clicked
             let targetRow = event.target.closest('tr');
             // Ensure the clicked row is actually within this tbody
             if (!targetRow || !tableBody.contains(targetRow)) {
                 // console.log("Click was not on a valid row inside the tbody."); // Debug log
                 return; // Ignore clicks outside of rows or in header/footer if they exist
             }
             // Ensure the row has a punch ID (ignore header/footer/message rows)
              if (!targetRow.dataset.punchId) {
                  // console.log("Clicked row does not have a punch ID:", targetRow); // Debug log
                  return;
              }

             // console.log("Processing click via tbody listener for row:", targetRow); // Debug log
             // Call the global selection function
             window.selectPunchRow(targetRow);
        });
    } else { console.error("Punch table body (#punchTableBody) not found! Cannot attach row listener."); }


    // JS Notification Modal Buttons (OK / Close)
    if(closeNotificationModalButton) {
        closeNotificationModalButton.addEventListener('click', () => {
             if(notificationModal) notificationModal.classList.remove('modal-visible');
         });
     }
    if(notificationOkButton) { // OK button might have specific logic attached in fetch handlers
        notificationOkButton.addEventListener('click', () => {
            if(notificationModal) notificationModal.classList.remove('modal-visible');
        });
     }

    // --- Initial Page Load Logic ---
    // console.log("Running initial page load logic..."); // Debug log
    handleControlState(); // Set initial state of Add Hours button
    deselectRow(); // Ensure no row is selected initially and buttons are disabled
    // Auto-focus employee dropdown if present
    if(employeeIdDropdown) {
        // Use setTimeout to ensure focus works after potential rendering delays
        setTimeout(() => {
            try {
                employeeIdDropdown.focus();
                // console.log("Focused employee dropdown."); // Debug log
            } catch (e) { console.warn("Could not focus employee dropdown:", e)}
        }, 100); // Small delay
    }
    // console.log("Initial state setup complete."); // Debug log
    console.log("--- Punches Page DOMContentLoaded END ---");
}); // --- End DOMContentLoaded ---