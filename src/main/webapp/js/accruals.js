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
    const btnEdit = document.getElementById("btnEditRow");
    const btnDelete = document.getElementById("btnDeleteRow");

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
    const addModal = document.getElementById("addAccrualModal");
    const btnAdd = document.getElementById("btnAddPolicy");
    const spanAdd = document.getElementById("closeAddModal");
    const cancelAddBtn = document.getElementById("cancelAdd");
    const addAccrualForm = document.getElementById("addAccrualForm");
    const addAccrualNameInput = document.getElementById("addAccrualName");

    const editModal = document.getElementById("editAccrualModal");
    const btnEdit = document.getElementById("btnEditRow");
    const spanEdit = document.getElementById("closeEditModal");
    const cancelEditBtn = document.getElementById("cancelEdit");
    const editAccrualForm = document.getElementById("editAccrualForm");
    const editAccrualNameInput = document.getElementById("editAccrualName");

    const btnDelete = document.getElementById("btnDeleteRow");
    const deleteForm = document.getElementById("deleteForm");
    const hiddenAccrualNameInput = document.getElementById("hiddenAccrualName");

    const accrualsTable = document.getElementById("accruals");

    const notificationModal = document.getElementById("notificationModal");
    const notificationMessage = document.getElementById("notificationMessage");
    const closeNotification = document.getElementById("closeNotificationModal");
    const okButton = document.getElementById("okButton");


    // --- Add Accrual Button ---
    if (btnAdd) {
        btnAdd.addEventListener("click", function() {
            showModal(addModal); // Use show function
            if (addAccrualNameInput) {
                 addAccrualNameInput.focus();
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

    // --- Edit Accrual Button ---
    if (btnEdit) {
        btnEdit.addEventListener("click", function() {
            if (selectedRow) {
                const cells = selectedRow.getElementsByTagName("td");
                if (cells.length >= 4) {
                    const accrualName = cells[0].textContent;
                    const vacation = cells[1].textContent;
                    const sick = cells[2].textContent;
                    const personal = cells[3].textContent;

                    if (editModal) {
                        document.getElementById("editAccrualName").value = accrualName;
                        document.getElementById("originalAccrualName").value = accrualName;
                        document.getElementById("editVacationDays").value = vacation;
                        document.getElementById("editSickDays").value = sick;
                        document.getElementById("editPersonalDays").value = personal;
                        showModal(editModal); // Use show function
                        if (editAccrualNameInput) {
                            editAccrualNameInput.focus();
                        }
                    }
                } else {
                     showNotification("Error reading data from selected row."); // Use notification
                }
            } else {
                showNotification("Please select a policy to edit."); // Use notification
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

    // --- Delete Accrual Button ---
    if (btnDelete) {
        btnDelete.addEventListener("click", function() {
            if (selectedRow) {
                const cells = selectedRow.getElementsByTagName("td");
                 if (cells.length > 0 && hiddenAccrualNameInput) {
                    const accrualName = cells[0].textContent;
                    hiddenAccrualNameInput.value = accrualName;
                    if (confirm("Are you sure you want to delete the policy: " + accrualName + "?")) {
                        if(deleteForm) deleteForm.submit();
                    }
                } else {
                     showNotification("Could not get policy name from selected row.");
                }
            } else {
                showNotification("Please select a policy to delete.");
            }
        });
    }

	// Add submit form event listener (optional - allows for validation before submit)
	if(addAccrualForm){
		addAccrualForm.addEventListener("submit", function(event){
			// Add client-side validation if needed here
			// Example: if (!validateForm()) { event.preventDefault(); return; }
			// Allow default submission
		});
	}
    if (editAccrualForm) {
        editAccrualForm.addEventListener("submit", function(event) {
			// Add client-side validation if needed here
            // Allow default submission
        });
    }

    // --- Event Delegation for Row Clicks (on the TABLE) ---
    if (accrualsTable) {
        accrualsTable.addEventListener("click", function(event) {
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
        showNotification("Accrual policy added successfully!");
        notificationShown = true;
    } else if (addSuccess === 'false' && !notificationShown) {
        showNotification("Failed to add accrual policy. " + (errorMessage || "Unknown error."));
        notificationShown = true;
    } else if (editSuccess === 'true' && !notificationShown) {
        showNotification("Accrual policy updated successfully!");
        notificationShown = true;
    } else if (editSuccess === 'false' && !notificationShown) {
        showNotification("Failed to update accrual policy. " + (errorMessage || "Unknown error."));
        notificationShown = true;
    } else if (deleteSuccess === 'true' && !notificationShown) {
        showNotification("Accrual policy deleted successfully!");
        notificationShown = true;
    } else if (deleteSuccess === 'false' && !notificationShown) {
        showNotification("Failed to delete accrual policy. " + (errorMessage || "Unknown error."));
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