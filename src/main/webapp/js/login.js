document.addEventListener('DOMContentLoaded', function() {

    // Get elements specific to the login page
    const btnAddSampleData = document.getElementById("addSampleDataBtn");
    const notificationModal = document.getElementById("notificationModal");
    const notificationMessage = document.getElementById("notificationMessage");
    const closeNotification = document.getElementById("closeNotificationModal");
    const okButton = document.getElementById("okButton");

    // --- Function to SHOW the notification modal ---
    function showNotification(message) {
        if (notificationMessage && notificationModal) {
            notificationMessage.textContent = message;
            notificationModal.classList.add('modal-visible'); // Use class to show
        } else {
            console.error("Notification modal elements not found! Message:", message);
            alert(message); // Fallback if modal elements are missing
        }
    }

    // --- Function to HIDE the notification modal ---
    function hideNotification() {
        if (notificationModal) {
            notificationModal.classList.remove('modal-visible'); // Use class to hide
        }
    }

    // --- Add Sample Data Button Click ---
    if (btnAddSampleData) {
        btnAddSampleData.addEventListener("click", function() {
            // Provide visual feedback and prevent multiple clicks
            btnAddSampleData.disabled = true;
            btnAddSampleData.textContent = "Adding Data...";

            // Send asynchronous request to the servlet
            fetch('AddSampleDataServlet', { // Make sure this URL matches your servlet mapping
                method: 'POST' // Use POST for actions that modify data
            })
            .then(response => {
                if (!response.ok) {
                    // Try to get error details from response text
                    return response.text().then(text => {
                        throw new Error(text || `Server responded with status: ${response.status}`);
                    });
                }
                return response.json(); // Expect a JSON response on success
            })
            .then(data => {
                if (data.success) {
                    showNotification("Sample data added successfully!");
                } else {
                    // Use error message from JSON if available
                    showNotification("Error adding sample data: " + (data.error || "Unknown error from server."));
                }
            })
            .catch(error => {
                console.error('Error fetching or processing:', error);
                showNotification("Failed to add sample data. Network or server error: " + error.message);
            })
            .finally(() => {
                 // Re-enable button and restore text after fetch completes
                 btnAddSampleData.disabled = false;
                 btnAddSampleData.textContent = "Add Sample Data";
            });
        });
    } else {
        console.error("Button with ID 'addSampleDataBtn' not found!");
    }

    // --- Close Notification Modal ---
	if (closeNotification) {
       closeNotification.addEventListener("click", hideNotification);
    }
    if (okButton) {
        okButton.addEventListener("click", hideNotification);
    }

    // Close modal if clicking outside (optional)
    window.addEventListener('click', function(event) {
      if (event.target == notificationModal) { // Check if click is on the background
        hideNotification();
      }
    });

}); // End DOMContentLoaded