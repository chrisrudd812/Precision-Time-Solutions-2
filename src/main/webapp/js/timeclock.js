// js/timeclock.js

// --- Utilities ---
function navigateToEmployee(selectedEid) {
    const baseUrl = 'timeclock.jsp';
    if (selectedEid) {
        window.location.href = baseUrl + '?eid=' + selectedEid;
    } else {
        // Go back to base page if no employee selected in dropdown
        window.location.href = baseUrl;
    }
}

// --- Notification Modal Logic ---
// Get modal elements ONCE, assuming they exist in the DOM when script runs
const notificationModal = document.getElementById("notificationModal");
const notificationModalContent = notificationModal ? notificationModal.querySelector('.modal-content') : null;
const notificationMessage = document.getElementById("notificationMessage");
const notificationCloseButton = document.getElementById("closeNotificationModal");
const notificationOkButton = document.getElementById("okButton");

function showNotificationModal(message, isError) {
    // Check if elements exist before using them
    if (notificationModal && notificationMessage && notificationModalContent) {
        console.log("Showing modal:", message, "Error:", isError);
        notificationMessage.innerHTML = message; // Use innerHTML for potential <br> tags from server messages
        if (isError) {
            notificationModalContent.classList.add('error');
        } else {
            notificationModalContent.classList.remove('error');
        }
        // Hide the top notification bar if the modal is shown for punch success/error
        const nBar = document.getElementById('notification-bar');
        if (nBar) nBar.style.display = 'none';

        notificationModal.classList.add('modal-visible'); // Make modal visible using CSS class
    } else {
        console.error("Notification modal elements not found! Cannot show notification.");
        alert(message); // Fallback alert if modal isn't set up correctly
    }
}

function hideNotificationModal() {
    if (notificationModal) {
        notificationModal.classList.remove('modal-visible');
        console.log("Notification modal hidden.");
    }
}

// --- Page Load Logic & Event Listeners ---
// Use DOMContentLoaded to ensure elements are ready before attaching listeners
document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Timeclock Page DOMContentLoaded START ---");

    // Verify modal elements exist on load
    if (!notificationModal || !notificationMessage || !notificationModalContent || !notificationCloseButton || !notificationOkButton) {
         console.error("One or more notification modal elements were not found on page load. Modal functionality may be broken.");
         // Display a persistent error or log prominently if critical
    }

    // Read URL parameters to check for punch results
    const urlParams = new URLSearchParams(window.location.search);
    const messageParam = urlParams.get('message');
    const errorParam = urlParams.get('error');
    // const eidParam = urlParams.get('eid'); // EID is mainly handled by JSP for initial load

    // Show Modal if message/error exists from Punch In/Out redirect
    if (messageParam) {
        console.log("Message param found:", messageParam);
        showNotificationModal(messageParam, false);
    } else if (errorParam) {
        console.log("Error param found:", errorParam);
        showNotificationModal(errorParam, true);
    } else {
        // If no message/error param from punch, handle page load errors shown in the notification bar
        const nBar = document.getElementById('notification-bar');
        if (nBar && nBar.textContent.trim() !== '') {
             console.log("Displaying page load error from notification bar.");
             // Make sure bar is visible initially if it has content
             nBar.style.display = 'block';
             nBar.style.opacity = '1';
             // Keep top bar visible for a bit for these non-modal errors
             setTimeout(() => {
                // Check again in case modal appeared and hid it
                if (nBar && (!notificationModal || !notificationModal.classList.contains('modal-visible'))) {
                    nBar.style.transition='opacity 0.5s ease-out';
                    nBar.style.opacity='0';
                    setTimeout(() => {nBar.style.display='none';}, 500);
                }
             }, 5000); // Standard 5 sec display
        }
    }

    // Attach Modal Button Listeners only if buttons exist
    if (notificationOkButton) {
        notificationOkButton.addEventListener('click', () => {
            console.log("Modal OK button clicked.");
            hideNotificationModal();
            // Redirect to base page without parameters to clear message/error/eid
            window.location.href = 'timeclock.jsp';
        });
    } else { console.error("Modal OK button (#okButton) not found, cannot attach listener."); }

    if (notificationCloseButton) {
        notificationCloseButton.addEventListener('click', () => {
             console.log("Modal Close button clicked.");
             hideNotificationModal();
             // Redirect to base page without parameters
             window.location.href = 'timeclock.jsp';
        });
    } else { console.error("Modal Close button (#closeNotificationModal) not found, cannot attach listener."); }

    console.log("--- Timeclock Page DOMContentLoaded END ---");
}); // --- End DOMContentLoaded ---