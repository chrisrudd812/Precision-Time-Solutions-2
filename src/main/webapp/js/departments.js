let selectedRow = null; // Global: Store the currently selected row

// --- GLOBAL Function: Select a Row (for highlighting) ---
function selectRow(row) {
    if (selectedRow) {
        selectedRow.classList.remove("selected");
    }
    row.classList.add("selected");
    selectedRow = row;
    toggleButtonState(); // Enable/disable buttons
}

// --- GLOBAL Function: Enable/Disable Edit and Delete Buttons ---
function toggleButtonState() {
    const btnEdit = document.getElementById("btnEditDepartment");
    const btnDelete = document.getElementById("btnDeleteDepartment");

    if (btnEdit) {
        btnEdit.disabled = !selectedRow;
    }
    if (btnDelete) {
        btnDelete.disabled = !selectedRow;
    }
}

// --- Function: Remove query parameters from URL ---
function clearUrlParams() {
    const currentUrl = window.location.pathname;
    window.history.replaceState({}, document.title, currentUrl);
}

// --- Function to SHOW a modal (using class) ---
function showModal(modalElement) {
    if (modalElement) {
        modalElement.classList.add('modal-visible'); // <<< CORRECT WAY
    }
}

// --- Function to HIDE a modal (using class) ---
function hideModal(modalElement) {
    if (modalElement) {
        modalElement.classList.remove('modal-visible'); // <<< CORRECT WAY
    }
}


document.addEventListener('DOMContentLoaded', function() {

    // --- Get DOM elements ---
    const addModal = document.getElementById("addDepartmentModal");
    const btnAdd = document.getElementById("btnAddDepartment");
    const spanAdd = document.getElementById("closeAddModal");
    const cancelAddBtn = document.getElementById("cancelAdd");
    const addDepartmentForm = document.getElementById("addDepartmentForm");
    const addDepartmentNameInput = document.getElementById("addDepartmentName");

    const editModal = document.getElementById("editDepartmentModal");
    const btnEdit = document.getElementById("btnEditDepartment");
    const spanEdit = document.getElementById("closeEditModal");
    const cancelEditBtn = document.getElementById("cancelEdit");
    const editDepartmentForm = document.getElementById("editDepartmentForm");
    const editDepartmentNameInput = document.getElementById("editDepartmentName");

    const btnDelete = document.getElementById("btnDeleteDepartment");
    const deleteForm = document.getElementById("deleteForm");
    const hiddenDepartmentNameInput = document.getElementById("hiddenDepartmentName");

    const departmentsTable = document.getElementById("departments");

    const notificationModal = document.getElementById("notificationModal");
    const notificationMessage = document.getElementById("notificationMessage");
    const closeNotification = document.getElementById("closeNotificationModal");
    const okButton = document.getElementById("okButton");


    // --- Add Department Button ---
    if (btnAdd) {
        btnAdd.addEventListener("click", function() {
            showModal(addModal); // Use show function
            if (addDepartmentNameInput) {
                 addDepartmentNameInput.focus();
            }
        });
    }

    // --- Close Add Modal ---
    if (spanAdd) {
        spanAdd.addEventListener("click", function() {
            hideModal(addModal); // Use hide function
        });
    }
      if (cancelAddBtn) {
        cancelAddBtn.addEventListener("click", function() {
            hideModal(addModal); // Use hide function
        });
    }

    // --- Edit Department Button ---
    if (btnEdit) {
        btnEdit.addEventListener("click", function() {
            if (selectedRow) {
                const cells = selectedRow.getElementsByTagName("td");
                if (cells.length >= 3) {
                    const departmentName = cells[0].textContent;
                    const description = cells[1].textContent;
                    const supervisor = cells[2].textContent;

                    if (editModal) {
                        document.getElementById("editDepartmentName").value = departmentName;
                        document.getElementById("originalDepartmentName").value = departmentName;
                        document.getElementById("editDescription").value = description;
                        document.getElementById("editSupervisor").value = supervisor;
                        showModal(editModal); // Use show function
                        if (editDepartmentNameInput) {
                            editDepartmentNameInput.focus();
                        }
                    }
                } else {
                     showNotification("Error reading data from selected row."); // Use notification
                }
            } else {
                showNotification("Please select a department to edit."); // Use notification
            }
        });
    }

    // --- Close Edit Modal ---
    if (spanEdit) {
        spanEdit.addEventListener("click", function() {
             hideModal(editModal); // Use hide function
        });
    }
     if (cancelEditBtn) {
        cancelEditBtn.addEventListener("click", function() {
            hideModal(editModal); // Use hide function
        });
    }

    // --- Delete Department Button ---
    if (btnDelete) {
        btnDelete.addEventListener("click", function() {
            if (selectedRow) {
                const cells = selectedRow.getElementsByTagName("td");
                 if (cells.length > 0 && hiddenDepartmentNameInput) {
                    const departmentName = cells[0].textContent;
                    hiddenDepartmentNameInput.value = departmentName;
                    if (confirm("Are you sure you want to delete the department: " + departmentName + "?")) {
                        if(deleteForm) deleteForm.submit();
                    }
                } else {
                     showNotification("Could not get department name from selected row.");
                }
            } else {
                showNotification("Please select a department to delete.");
            }
        });
    }

	// Add submit form event listener (optional - allows for validation before submit)
	if(addDepartmentForm){
		addDepartmentForm.addEventListener("submit", function(event){
			// Add client-side validation if needed here
			// Example: if (!validateForm()) { event.preventDefault(); return; }
			// Allow default submission
		});
	}
    if (editDepartmentForm) {
        editDepartmentForm.addEventListener("submit", function(event) {
			// Add client-side validation if needed here
            // Allow default submission
        });
    }

    // --- Event Delegation for Row Clicks (on the TABLE) ---
    if (departmentsTable) {
        departmentsTable.addEventListener("click", function(event) {
            let target = event.target;
            while (target && target.tagName !== "TR") {
                target = target.parentNode;
            }
            if (target && target.parentNode.tagName === "TBODY") {
                selectRow(target);
            }
        });
    }

    // --- Check for URL parameters (success/error) and show the modal ---
    const urlParams = new URLSearchParams(window.location.search);
    const addSuccess = urlParams.get('addSuccess');
    const editSuccess = urlParams.get('editSuccess');
    const deleteSuccess = urlParams.get('deleteSuccess');
    const errorMessage = urlParams.get('error');
    let notificationShown = false;

    if (addSuccess === 'true' && !notificationShown) {
        showNotification("Department added successfully!");
        notificationShown = true;
    } else if (addSuccess === 'false' && !notificationShown) {
        showNotification("Failed to add department. " + (errorMessage || "Unknown error."));
        notificationShown = true;
    } else if (editSuccess === 'true' && !notificationShown) {
        showNotification("Department updated successfully!");
        notificationShown = true;
    } else if (editSuccess === 'false' && !notificationShown) {
        showNotification("Failed to update department. " + (errorMessage || "Unknown error."));
        notificationShown = true;
    } else if (deleteSuccess === 'true' && !notificationShown) {
        showNotification("Department deleted successfully!");
        notificationShown = true;
    } else if (deleteSuccess === 'false' && !notificationShown) {
        showNotification("Failed to delete department. " + (errorMessage || "Unknown error."));
        notificationShown = true;
    } else if (errorMessage && !notificationShown) {
         showNotification("An error occurred: " + errorMessage);
         notificationShown = true;
    }

    // --- Function to show the notification modal ---
    function showNotification(message) {
        if (notificationMessage && notificationModal) {
            notificationMessage.textContent = message;
            showModal(notificationModal); // Use general show function
            clearUrlParams(); // Clean URL after showing
        } else {
            console.error("Notification modal elements not found! Message:", message);
            alert(message); // Fallback
        }
    }

	// Close Notification Modal ('X' button)
	if (closeNotification) {
       closeNotification.addEventListener("click", function() {
           hideModal(notificationModal); // Use hide function
        });
    }
     // Close Notification Modal ('OK' button)
    if (okButton) {
        okButton.addEventListener("click", function() {
           hideModal(notificationModal); // Use hide function
        });
    }

    // Set initial button states (Edit/Delete disabled)
    toggleButtonState();

}); // End DOMContentLoaded