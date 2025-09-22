// js/timeclock.js

// --- NEW: Inactivity Timeout Logic ---
let inactivityTimer;

function logoutDueToInactivity() {
    // Redirect to the logout servlet with a reason
    const contextPath = (typeof app_contextPath !== 'undefined') ? app_contextPath : '';
    window.location.href = contextPath + '/LogoutServlet?autoLogout=true&reason=' + encodeURIComponent('Session timed out due to inactivity.');
}

function resetInactivityTimer() {
    clearTimeout(inactivityTimer);
    // The session timeout duration is passed from the JSP
    const timeoutMilliseconds = (typeof sessionTimeoutDuration_Js !== 'undefined' && sessionTimeoutDuration_Js > 0) 
                                ? (sessionTimeoutDuration_Js * 1000) 
                                : (60 * 60 * 1000); // Fallback to 1 hour
    inactivityTimer = setTimeout(logoutDueToInactivity, timeoutMilliseconds);
}

function setupInactivityDetection() {
    // This function is called on page load
    // It checks for the permission variable set by timeclock.jsp
    if (typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User') {
        // Attach event listeners that will reset the timer
        ['mousemove', 'mousedown', 'keypress', 'touchmove', 'scroll', 'click', 'keydown'].forEach(activityEvent => {
            window.addEventListener(activityEvent, resetInactivityTimer, true);
        });
        // Start the timer for the first time
        resetInactivityTimer();
    }
}
// --- END: Inactivity Timeout Logic ---


async function getDeviceFingerprint() {
    if (window.FingerprintJS) {
        try {
            const fpPromise = FingerprintJS.load({ monitoring: false });
            const fp = await fpPromise;
            const result = await fp.get();
            return result.visitorId;
        } catch (error) {
            console.error('Error generating fingerprint:', error);
            throw new Error('Fingerprint generation failed: ' + error.message);
        }
    } else {
        console.error('FingerprintJS library not loaded.');
        throw new Error('FingerprintJS library not loaded.');
    }
}

function getClientGeolocation() {
    return new Promise((resolve, reject) => {
        if (!navigator.geolocation) {
            reject({ code: -1, message: "Geolocation is not supported by your browser." });
            return;
        }
        navigator.geolocation.getCurrentPosition(
            (position) => {
                resolve({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                    accuracy: position.coords.accuracy
                });
            },
            (error) => {
                let friendlyMessage = "Geolocation error: ";
                switch(error.code) {
                    case error.PERMISSION_DENIED: friendlyMessage += "Permission denied by browser."; break;
                    case error.POSITION_UNAVAILABLE: friendlyMessage += "Position unavailable."; break;
                    case error.TIMEOUT: friendlyMessage += "Request timed out."; break;
                    default: friendlyMessage += "Unknown error (Code: " + error.code + ").";
                }
                reject({ code: error.code, message: friendlyMessage });
            },
            { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
        );
    });
}

function getDeviceType() {
    const ua = navigator.userAgent;
    if (/(tablet|ipad|playbook|silk)|(android(?!.*mobi))/i.test(ua)) {
        return "Tablet Device";
    }
    if (/Mobile|iP(hone|od)|Android|BlackBerry|IEMobile|Kindle|Silk-Accelerated|(hpw|web)OS|Opera M(obi|ini)/.test(ua)) {
        return "Mobile Device";
    }
    return "Desktop Device";
}

function navigateToEmployee(selectedEid, isCurrentlyReportMode) {
    let baseUrl = 'timeclock.jsp';
    const params = new URLSearchParams();
    if (selectedEid && selectedEid !== "0") {
        params.append('eid', selectedEid);
    }
    if (isCurrentlyReportMode) {
        params.append('reportMode', 'true');
    }
    const queryString = params.toString();
    if (queryString) {
        baseUrl += '?' + queryString;
    }
    window.location.href = baseUrl;
}


function showTimeclockNotificationModal(message, isError) {
    console.log("DEBUG: --- Inside showTimeclockNotificationModal ---");
    console.log("DEBUG: Received message:", message);
    
    const modal = document.getElementById("notificationModal");
    if (!modal) {
        console.error("DEBUG: ABORTING. Modal element NOT FOUND in the DOM.");
        return;
    }

    const title = modal.querySelector('#notificationModalTitle');
    const msg = modal.querySelector('#notificationMessage');
    const okBtn = modal.querySelector('#okButton');
    const closeBtn = modal.querySelector('#closeNotificationModal');
    const modalContent = modal.querySelector('.modal-content');

    // Set title text and add an icon
    if (isError) {
        title.innerHTML = '<i class="fas fa-exclamation-triangle"></i> Error';
        modalContent.classList.add('error-message');
        modalContent.classList.remove('success-message');
    } else {
        title.innerHTML = '<i class="fas fa-check-circle"></i> Notification';
        modalContent.classList.add('success-message');
        modalContent.classList.remove('error-message');
    }
    
    msg.innerHTML = message.replace(/\n/g, '<br>');

    const hide = () => {
        modal.classList.remove('modal-visible');
    };

    okBtn.onclick = hide;
    closeBtn.onclick = hide;

    modal.classList.add('modal-visible');
    okBtn.focus();
}


function printTimecard() {
    const printableArea = document.getElementById('printableTimecardArea');
    if (!printableArea) { alert("Error: Printable area not found."); return; }
    const clonedPrintableArea = printableArea.cloneNode(true);
    const tableContainerInClone = clonedPrintableArea.querySelector('#timecardTableContainer');
    if (tableContainerInClone) { tableContainerInClone.style.maxHeight = 'none'; tableContainerInClone.style.overflowY = 'visible'; }
    let printWindow = window.open('', '_blank', 'width=800,height=600,scrollbars=yes,resizable=yes');
    if (!printWindow) { alert("Could not open print window. Check popup blocker."); return; }
    printWindow.document.write('<html><head><title>Print Timecard</title>');
    const contextPathForCss = (typeof app_contextPath !== 'undefined') ? app_contextPath : '';
    printWindow.document.write('<link rel="stylesheet" href="' + contextPathForCss + '/css/timeclock.css?v=' + new Date().getTime() + '">');
    printWindow.document.write('<link rel="stylesheet" href="' + contextPathForCss + '/css/reports.css?v=' + new Date().getTime() + '">');
    printWindow.document.write('<style>body{margin:20px;background-color:#fff;font-size:10pt;}.timecard{border:none;box-shadow:none;}.punch-buttons,.report-actions-container,.employee-selector-container,#main-clock-container,.main-navbar,#notificationModal{display:none!important;}@media print{@page{size:auto;margin:.75in}body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}</style></head><body>');
    printWindow.document.write(clonedPrintableArea.innerHTML);
    printWindow.document.write('</body></html>');
    printWindow.document.close();
    setTimeout(() => { try {printWindow.focus(); printWindow.print(); } catch(e){alert("Print error: " + e.message);}}, 500);
}

/**
 * Handles the punch-in and punch-out form submissions.
 * This function prevents the default form submission, asynchronously gathers required data
 * (like geolocation and device fingerprint), populates the form's hidden fields,
 * and then programmatically submits the form.
 *
 * @param {Event} event - The form submission event.
 */
async function handlePunchSubmit(event) {
    event.preventDefault(); // Stop the form from submitting immediately
    const form = event.target;
    const submitButton = form.querySelector('button[type="submit"]');
    const isPunchOut = (form.id === "punchOutForm");
    const isPunchIn = !isPunchOut;

    // Disable the button and provide feedback to the user
    if (submitButton) {
        submitButton.disabled = true;
        submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Please Wait...';
    }

    // Function to re-enable the button if something goes wrong
    const restoreButton = () => {
        if (submitButton) {
            submitButton.disabled = false;
            submitButton.innerHTML = isPunchOut ? 'PUNCH OUT' : 'PUNCH IN';
        }
    };

    try {
        // --- NEW UNIVERSAL PRE-CHECK FOR ALL PUNCHES ---
        // As directed, the VERY first step is to check the user's current punch status with the server.
        const response = await fetch(`${app_contextPath}/CheckOpenPunchServlet`);
        if (!response.ok) {
            throw new Error("Server error: Could not verify current punch status.");
        }
        const statusData = await response.json();
        if (statusData.error) {
            throw new Error(statusData.error);
        }

        const hasOpenPunch = statusData.hasOpenPunch;

        // Now, validate the action based on the server's response.
        if (isPunchIn && hasOpenPunch) {
            throw new Error("You are already clocked in. Please clock out before clocking in again.");
        }
        if (isPunchOut && !hasOpenPunch) {
            throw new Error("No open work punch found to clock out against. You are already clocked out.");
        }
        // --- END OF UNIVERSAL PRE-CHECK ---


        // If the pre-check passes, we proceed with the other steps.
        // Step 1: Get data that doesn't cause a delay
        const fingerprint = await getDeviceFingerprint();
        form.querySelector('input[name="deviceFingerprintHash"]').value = fingerprint;
        form.querySelector('input[name="browserTimeZoneId"]').value = Intl.DateTimeFormat().resolvedOptions().timeZone;
        form.querySelector('input[name="deviceType"]').value = getDeviceType();

        // Step 2: Get location ONLY if the company policy requires it.
        if (typeof locationCheckIsTrulyRequired !== 'undefined' && locationCheckIsTrulyRequired) {
            if (submitButton) {
                submitButton.innerHTML = '<i class="fas fa-location-arrow fa-spin"></i> Getting Location...';
            }
            const coords = await getClientGeolocation();
            form.querySelector('input[name="latitude"]').value = coords.latitude;
            form.querySelector('input[name="longitude"]').value = coords.longitude;
        } else {
            form.querySelector('input[name="latitude"]').value = "";
            form.querySelector('input[name="longitude"]').value = "";
        }

        // Step 3: All data is now correctly populated, submit the form to the main servlet.
        form.submit();

    } catch (error) {
        // This will catch any error, including the one from our new pre-check.
        console.error("Punch submission error:", error);
        showTimeclockNotificationModal(error.message || "An unexpected error occurred.", true);
        restoreButton(); // Re-enable the button on failure
    }
}

function applyRowBandingByDay() {
    const tableBody = document.querySelector('#punches tbody');
    if (!tableBody) return;
    const rows = tableBody.querySelectorAll('tr');
    if (rows.length === 0) return;
    let currentDateString = null;
    let currentBandClass = 'band-a';
    rows.forEach(row => {
        const dateCell = row.cells[1];
        if (dateCell) {
            const dateText = dateCell.textContent.trim();
            if (dateText) {
                if (currentDateString !== dateText) {
                    currentBandClass = (currentBandClass === 'band-a') ? 'band-b' : 'band-a';
                    currentDateString = dateText;
                }
                row.classList.remove('band-a', 'band-b');
                row.classList.add(currentBandClass);
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', function() {
    console.log("DEBUG: DOMContentLoaded event fired.");

    const punchInForm = document.getElementById('punchInForm');
    if (punchInForm) punchInForm.addEventListener('submit', handlePunchSubmit);
    
    const punchOutForm = document.getElementById('punchOutForm');
    if (punchOutForm) punchOutForm.addEventListener('submit', handlePunchSubmit);

    const btnPrintEmailSingle = document.getElementById('btnPrintEmailSingleTimecard');
    const employeeSelect = document.getElementById('employeeSelect'); 

    if (btnPrintEmailSingle && employeeSelect) {
        btnPrintEmailSingle.addEventListener('click', function() {
            const eid = employeeSelect.value;
            if (eid && eid !== "0") {
                const printUrl = `${app_contextPath}/PrintTimecardsServlet?filterType=single&filterValue=${eid}`;
                window.open(printUrl, '_blank');
            } else {
                alert("An employee must be selected from the dropdown to print or email their timecard.");
            }
        });
    }

    const btnPrintThisTimecard = document.getElementById('btnPrintThisTimecard');
    if (btnPrintThisTimecard) btnPrintThisTimecard.addEventListener('click', printTimecard);

    setupInactivityDetection();
    applyRowBandingByDay();
    
    const timecardTableContainer = document.getElementById('timecardTableContainer');
    if (timecardTableContainer && timecardTableContainer.scrollHeight > timecardTableContainer.clientHeight) {
        setTimeout(() => {
            timecardTableContainer.scrollTo({ top: timecardTableContainer.scrollHeight, behavior: 'smooth' });
        }, 150);
    }
});