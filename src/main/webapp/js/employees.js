// js/employees.js

// --- Global variables ---
let selectedRow = null;
let selectedEID = null;
let currentSortColumn = -1;
let currentSortAsc = true;

// --- Notification Modal Globals ---
let notificationModal = null;
let notificationModalContent = null;
let notificationMessage = null;
let notificationCloseButton = null;
let notificationOkButton = null;

// --- Helper Function to get cell text safely ---
function getCellText(row, index) {
    if (row && row.cells && row.cells[index]) {
        return row.cells[index].textContent.trim();
    }
    // console.warn(`Could not get text from row ${row ? row.rowIndex : 'N/A'}, cell index ${index}`);
    return ''; // Return empty string for consistency
}

// --- Function to Populate Details Section ---
function populateDetails(row) {
    const detailsSection = document.getElementById('employeeDetailsSection');
    if (!detailsSection || !row) { console.error("Details section or row not found for population."); return; }
    // console.log("Populating details for row:", row.rowIndex);
    const setText = (id, value) => { const el = document.getElementById(id); if (el) { el.textContent = (value && value !== '--' && value.trim() !== '') ? value : '--'; } else { console.warn("Detail element not found:", id); } };
    setText('detailEID', getCellText(row, 0)); setText('detailFirstName', getCellText(row, 1)); setText('detailLastName', getCellText(row, 2)); setText('detailDept', getCellText(row, 3)); setText('detailSchedule', getCellText(row, 4)); setText('detailPermissions', getCellText(row, 5)); setText('detailAddress', getCellText(row, 6)); setText('detailCity', getCellText(row, 7)); setText('detailState', getCellText(row, 8)); setText('detailZip', getCellText(row, 9)); setText('detailPhone', getCellText(row, 10)); setText('detailEmail', getCellText(row, 11)); setText('detailAccrualPolicy', getCellText(row, 12)); setText('detailVacHours', getCellText(row, 13)); setText('detailSickHours', getCellText(row, 14)); setText('detailPersHours', getCellText(row, 15)); setText('detailHireDate', getCellText(row, 16)); setText('detailWorkSched', getCellText(row, 17)); setText('detailWageType', getCellText(row, 18));
    const wageValue = getCellText(row, 19); const wageSpan = document.getElementById('detailWage'); if (wageSpan) { const wageNum = parseFloat(wageValue.replace(/[^0-9.-]+/g,"")); if (!isNaN(wageNum)) { try { wageSpan.textContent = wageNum.toLocaleString('en-US', { style: 'currency', currency: 'USD' }); } catch(e) { wageSpan.textContent = wageValue || '--'; } } else { wageSpan.textContent = wageValue || '--'; } } else { console.warn("Detail element not found: detailWage"); }
    detailsSection.style.display = 'block';
}

// --- Function to Hide Details Section ---
function hideDetails() {
    const detailsSection = document.getElementById('employeeDetailsSection');
    if (detailsSection) { detailsSection.style.display = 'none'; const spans = detailsSection.querySelectorAll('span[id^="detail"]'); spans.forEach(span => { span.textContent = '--'; }); }
}

// --- Function to Deselect Table Row ---
function deselectRow() {
    if (selectedRow) { selectedRow.classList.remove('selected'); }
    selectedRow = null; selectedEID = null;
    const editBtn = document.getElementById('editEmployeeButton'); const deleteBtn = document.getElementById('deleteEmployeeButton');
    if(editBtn) editBtn.disabled = true; if(deleteBtn) deleteBtn.disabled = true;
    hideDetails();
}

// --- Table Sorting Function ---
function sortTable(th, columnIndex) {
    const table = document.getElementById('employees'); const tbody = table?.getElementsByTagName('tbody')[0]; const headerRows = table?.querySelectorAll('thead th');
    if (!tbody || !headerRows) { console.error("Cannot sort table - tbody or headerRows not found."); return; }
    const newAsc = (currentSortColumn === columnIndex) ? !currentSortAsc : true; console.log(`Sorting column ${columnIndex}, Ascending: ${newAsc}`);
    const rows = Array.from(tbody.rows); let dataType = 'string'; const firstCellContent = rows.length > 0 ? getCellText(rows[0], columnIndex) : '';
    if (columnIndex === 0 || (columnIndex >= 13 && columnIndex <= 15)) { dataType = 'number'; } else if (columnIndex === 19) { dataType = 'currency'; } else if (columnIndex === 16 && firstCellContent && firstCellContent.match(/^\d{1,2}\/\d{1,2}\/\d{4}$/)) { dataType = 'date'; }
    // console.log("Detected sort data type:", dataType);
    rows.sort((rowA, rowB) => { let cellA = getCellText(rowA, columnIndex); let cellB = getCellText(rowB, columnIndex); let valA, valB; try { switch (dataType) { case 'number': valA = parseFloat(cellA) || 0; valB = parseFloat(cellB) || 0; break; case 'currency': valA = parseFloat(cellA.replace(/[^0-9.-]+/g,"")) || 0; valB = parseFloat(cellB.replace(/[^0-9.-]+/g,"")) || 0; break; case 'date': const partsA = cellA.split('/'); const partsB = cellB.split('/'); valA = partsA.length === 3 ? `${partsA[2]}${partsA[0].padStart(2,'0')}${partsA[1].padStart(2,'0')}` : ''; valB = partsB.length === 3 ? `${partsB[2]}${partsB[0].padStart(2,'0')}${partsB[1].padStart(2,'0')}` : ''; break; default: valA = cellA.toLowerCase(); valB = cellB.toLowerCase(); break; } if (valA < valB) return newAsc ? -1 : 1; if (valA > valB) return newAsc ? 1 : -1; return 0; } catch (e) { console.error("Error comparing values:", cellA, cellB, e); return 0; } });
    tbody.innerHTML = ''; tbody.append(...rows);
    headerRows.forEach(header => header.classList.remove('sort-asc', 'sort-desc')); th.classList.add(newAsc ? 'sort-asc' : 'sort-desc');
    currentSortColumn = columnIndex; currentSortAsc = newAsc; console.log("Table sorted.");
}

// --- Notification Modal Functions ---
function showNotification(message, isError) {
    if (!notificationModal || !notificationMessage || !notificationModalContent) { console.error("Notification modal elements not found! Cannot show notification."); alert(message); return; }
    notificationMessage.innerHTML = message; // Use innerHTML
    notificationModalContent.classList.toggle('error', isError); // Add/remove error class
    notificationModal.classList.add('modal-visible');
    console.log("Notification modal shown.");
    // Hide the top bar if modal shown for add/edit/delete result
    const nBar = document.getElementById('notification-bar');
    if(nBar) nBar.style.display = 'none';
}

function hideNotification() {
    if(notificationModal){
        notificationModal.classList.remove('modal-visible');
        console.log("Notification modal hidden.");
        // Clear URL parameters when modal is hidden
        clearUrlParams();
    }
}

// --- Function to Populate Edit Modal ---
function populateEditModal() {
    if (!selectedRow || !selectedEID) { console.error("Cannot populate edit modal - no row selected."); alert("Please select an employee row first."); return; }
    console.log("Populating edit modal for EID:", selectedEID);
    const form = document.getElementById('editEmployeeForm'); const modal = document.getElementById('editEmployeeModal'); if (!form || !modal) { console.error("Edit form or modal not found!"); return; }
    // Get form elements
    const eidDisplay = form.querySelector('#editEID'); const hiddenEidInput = form.querySelector('#hiddenEditEID'); const firstNameInput = form.querySelector('#editFirstName'); const lastNameInput = form.querySelector('#editLastName'); const deptSelect = form.querySelector('#editDepartmentsDropDown'); const scheduleSelect = form.querySelector('#editSchedulesDropDown'); const permissionsSelect = form.querySelector('#editPermissionsDropDown'); const accrualSelect = form.querySelector('#editAccrualsDropDown'); const addressInput = form.querySelector('#editAddress'); const cityInput = form.querySelector('#editCity'); const stateSelect = form.querySelector('#editState'); const zipInput = form.querySelector('#editZip'); const phoneInput = form.querySelector('#editPhone'); const emailInput = form.querySelector('#editEmail'); const hireDateInput = form.querySelector('#editHireDate'); const workSchedSelect = form.querySelector('#editWorkScheduleDropDown'); const wageTypeSelect = form.querySelector('#editWageTypeDropDown'); const wageInput = form.querySelector('#editWage');
    // Get data from selected row
    const eid = getCellText(selectedRow, 0); const firstName = getCellText(selectedRow, 1); const lastName = getCellText(selectedRow, 2); const dept = getCellText(selectedRow, 3); const schedule = getCellText(selectedRow, 4); const permissions = getCellText(selectedRow, 5); const address = getCellText(selectedRow, 6); const city = getCellText(selectedRow, 7); const state = getCellText(selectedRow, 8); const zip = getCellText(selectedRow, 9); const phone = getCellText(selectedRow, 10); const email = getCellText(selectedRow, 11); const accrualPolicy = getCellText(selectedRow, 12); const hireDate = getCellText(selectedRow, 16); const workSched = getCellText(selectedRow, 17); const wageType = getCellText(selectedRow, 18); const wage = getCellText(selectedRow, 19).replace(/[^0-9.-]+/g,"");
    // Populate form fields
    if(eidDisplay) eidDisplay.value = eid; if(hiddenEidInput) hiddenEidInput.value = eid; if(firstNameInput) firstNameInput.value = firstName; if(lastNameInput) lastNameInput.value = lastName; if(deptSelect) deptSelect.value = dept; if(scheduleSelect) scheduleSelect.value = schedule; if(permissionsSelect) permissionsSelect.value = permissions; if(accrualSelect) accrualSelect.value = accrualPolicy; if(addressInput) addressInput.value = address; if(cityInput) cityInput.value = city; if(stateSelect) stateSelect.value = state; if(zipInput) zipInput.value = zip; if(phoneInput) phoneInput.value = phone; if(emailInput) emailInput.value = email;
    if(hireDateInput) { let formattedHireDate = ''; if (hireDate && hireDate.includes('/')) { try { const parts = hireDate.split('/'); if (parts.length === 3) { formattedHireDate = `${parts[2]}-${parts[0].padStart(2,'0')}-${parts[1].padStart(2,'0')}`; } } catch(e) {} } hireDateInput.value = formattedHireDate; }
    if(workSchedSelect) workSchedSelect.value = workSched; if(wageTypeSelect) wageTypeSelect.value = wageType; if(wageInput) wageInput.value = wage;
    console.log("Edit modal populated.");
}
// --- End Edit Modal Population ---

// --- Global Utility: Remove extra spaces ---
function removeExtraSpaces(str) { return str ? str.replace(/\s\s+/g, ' ').trim() : ''; }

// --- Run Code After DOM Loaded ---
document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Employees Page DOMContentLoaded START ---");

    // Assign global references for modal elements
    notificationModal = document.getElementById("notificationModal");
    notificationModalContent = notificationModal ? notificationModal.querySelector('.modal-content') : null;
    notificationMessage = document.getElementById("notificationMessage");
    notificationOkButton = document.getElementById("okButton");
    notificationCloseButton = document.getElementById("closeNotificationModal");
    if (!notificationModal || !notificationOkButton || !notificationCloseButton || !notificationMessage || !notificationModalContent) {
        console.error("CRITICAL: One or more notification modal elements are missing from the JSP! Notifications will fail.");
    }

    // Assign other references
    const employeesTable = document.getElementById('employees');
    const tableHead = employeesTable?.getElementsByTagName('thead')[0];
    const tableBody = employeesTable?.getElementsByTagName('tbody')[0];
    const addBtn = document.getElementById('addEmployeeButton');
    const editBtn = document.getElementById('editEmployeeButton');
    const deleteBtn = document.getElementById('deleteEmployeeButton');
    const addModal = document.getElementById('addEmployeeModal');
    const editModal = document.getElementById('editEmployeeModal');
    const closeAddModal = document.getElementById('closeAddModal');
    const closeEditModal = document.getElementById('closeEditModal');
    const cancelAddBtn = document.getElementById('cancelAdd');
    const cancelEditBtn = document.getElementById('cancelEdit');
    const notificationBar = document.getElementById('notification-bar');

    // --- Table Row Click Listener ---
    if (tableBody) {
        console.log("Attaching click listener to table body");
        tableBody.addEventListener('click', function(event) {
            let targetRow = event.target.closest('tr');
            if (!targetRow || !tableBody.contains(targetRow) || !targetRow.cells || targetRow.cells.length === 0) return; // Ignore clicks outside rows or on header/empty rows
            if (targetRow.cells[0].tagName === 'TH') return; // Ignore clicks on header row if inside tbody somehow

            console.log("Row clicked:", targetRow.rowIndex);
            if (selectedRow && selectedRow === targetRow) {
                deselectRow();
            } else {
                if (selectedRow) { selectedRow.classList.remove('selected'); }
                targetRow.classList.add('selected'); selectedRow = targetRow;
                selectedEID = getCellText(targetRow, 0);
                const eidValid = selectedEID && selectedEID !== '--' && !isNaN(parseInt(selectedEID));
                if(editBtn) editBtn.disabled = !eidValid;
                if(deleteBtn) deleteBtn.disabled = !eidValid;
                if (eidValid) { populateDetails(selectedRow); console.log("Selected EID:", selectedEID); }
                else { console.warn("Clicked row has invalid or missing EID in cell 0."); hideDetails(); }
            }
        });
    } else { console.error("Employee table body not found!"); }

    // --- Table Header Click Listener ---
    if (tableHead) {
         console.log("Attaching click listener to table headers for sorting");
         const headers = tableHead.querySelectorAll('th');
         headers.forEach((header, index) => {
             if (header.textContent.trim() !== '') { header.style.cursor = 'pointer'; header.title = `Sort by ${header.textContent.trim()}`; header.addEventListener('click', () => { sortTable(header, index); }); }
             else { header.style.cursor = 'default'; }
         });
    } else { console.error("Employee table head not found! Sorting disabled."); }

    // --- Button Listeners ---
    if (addBtn && addModal) { addBtn.addEventListener('click', function() { console.log("Add button clicked"); const addForm = document.getElementById('addEmployeeForm'); if(addForm) addForm.reset(); try { const hireDateInput = document.getElementById('addHireDate'); if(hireDateInput) hireDateInput.valueAsDate = new Date(); } catch(e) {} addModal.classList.add('modal-visible'); const firstNameInput = document.getElementById('addFirstName'); if(firstNameInput) setTimeout(() => firstNameInput.focus(), 50); }); } else { console.error("Add button or modal not found."); }
    if (editBtn && editModal) { editBtn.addEventListener('click', function() { if (!selectedRow || !selectedEID) { alert('Please select an employee row to edit.'); return; } console.log("Edit button clicked for EID:", selectedEID); populateEditModal(); editModal.classList.add('modal-visible'); }); } else { console.error("Edit button or modal not found."); }
    if (deleteBtn) { deleteBtn.addEventListener('click', function() { if (!selectedRow || !selectedEID) { alert('Please select an employee row to delete.'); return; } console.log("Delete button clicked for EID:", selectedEID); if (confirm('Are you sure you want to delete employee ' + selectedEID + '? This action cannot be undone.')) { const hiddenField = document.getElementById('hiddenEID'); const deleteForm = document.getElementById('deleteForm'); if(hiddenField && deleteForm) { hiddenField.value = selectedEID; deleteForm.submit(); } else { console.error("Delete form or hidden field not found!"); } } }); } else { console.error("Delete button not found."); }

    // --- Modal Close Listeners ---
    if(closeAddModal && addModal) closeAddModal.addEventListener('click', function() { addModal.classList.remove('modal-visible'); }); else { console.error("Add modal close button not found.");}
    if(closeEditModal && editModal) closeEditModal.addEventListener('click', function() { editModal.classList.remove('modal-visible'); deselectRow(); }); else { console.error("Edit modal close button not found.");}
    if(cancelAddBtn && addModal) cancelAddBtn.addEventListener('click', function() { addModal.classList.remove('modal-visible'); }); else { console.error("Add modal cancel button not found.");}
    if(cancelEditBtn && editModal) cancelEditBtn.addEventListener('click', function() { editModal.classList.remove('modal-visible'); deselectRow(); }); else { console.error("Edit modal cancel button not found.");}

    // --- Attach Listeners to Notification Modal Buttons ---
    if (notificationCloseButton) { notificationCloseButton.addEventListener('click', hideNotification); } else { console.error("Notification modal close button not found!"); }
    if (notificationOkButton) { notificationOkButton.addEventListener('click', hideNotification); } else { console.error("Notification modal OK button not found!"); }


    // --- Check URL Params on Load & Show Modal ---
    const urlParams = new URLSearchParams(window.location.search);
    const initialMessage = urlParams.get('message');
    const initialError = urlParams.get('error');
    const eidFromUrl = urlParams.get('eid'); // EID from redirect after add/edit/delete

    let modalShown = false; // Flag to track if modal was triggered
    if (initialMessage && initialMessage.trim() !== "") { // Check if not empty
        console.log("Message param found:", initialMessage);
        showNotification(initialMessage, false); // Show success modal
        modalShown = true;
    } else if (initialError && initialError.trim() !== "") { // Check if not empty
        console.log("Error param found:", initialError);
        showNotification(initialError, true); // Show error modal
        modalShown = true;
    }

    // Handle notification bar only if modal wasn't shown and bar exists/has content
    if (!modalShown && notificationBar && notificationBar.textContent.trim() !== '') {
         console.log("Page load error found in bar:", notificationBar.textContent.trim());
         notificationBar.style.display = 'block'; // Ensure visible
         notificationBar.style.opacity = '1';
         setTimeout(() => { if(notificationBar){ notificationBar.style.transition = 'opacity 0.5s ease-out'; notificationBar.style.opacity = '0'; setTimeout(() => { notificationBar.style.display = 'none'; }, 500); } }, 5000);
         clearUrlParams(); // Also clear params if top bar was shown
    } else if (notificationBar) {
         notificationBar.style.display = 'none'; // Hide bar if empty or modal shown
    }


    // --- Auto-select row if EID present in URL (after potential add/edit/delete) ---
    if(eidFromUrl && tableBody){
        console.log("EID found in URL, attempting to select row:", eidFromUrl);
        const rows = tableBody.getElementsByTagName('tr');
        let rowFound = false;
        for(let i=0; i<rows.length; i++){
            if(getCellText(rows[i], 0) === eidFromUrl){
                console.log("Found matching row for EID", eidFromUrl);
                // Use setTimeout to ensure selection happens after potential rendering updates
                setTimeout(() => {
                     if (rows[i]) { // Check if row still exists
                         rows[i].click(); // Simulate click to select and show details
                         rows[i].scrollIntoView({ behavior: 'smooth', block: 'center' });
                     }
                }, 50); // Small delay
                rowFound = true;
                break;
            }
        }
        if (!rowFound) { console.warn("Row for EID", eidFromUrl, "not found in table."); deselectRow(); }
    } else if (!eidFromUrl) {
         // Auto-select first valid data row only if no EID is specified in URL
         console.log("No EID in URL, attempting to auto-select first data row...");
         if (tableBody) { const firstDataRow = tableBody.querySelector('tr:has(td)'); if (firstDataRow) { console.log("First data row found, simulating click..."); setTimeout(() => firstDataRow.click(), 50); } else { console.log("No data rows found."); deselectRow();} }
         else { console.log("Table body not found."); deselectRow(); }
    }

    console.log("--- Employees Page DOMContentLoaded END ---");
}); // --- End DOMContentLoaded ---