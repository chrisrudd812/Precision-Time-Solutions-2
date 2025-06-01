// js/timeclock.js - v19 (Smooth scroll, Day Banding, Width consistency handled in CSS)

// --- FingerprintJS Function ---
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

// --- Get Geolocation Function ---
function getClientGeolocation() {
    return new Promise((resolve, reject) => {
        if (!navigator.geolocation) {
            reject({ code: -1, message: "Geolocation is not supported by your browser." });
            return;
        }
        navigator.geolocation.getCurrentPosition(
            (position) => {
                console.log("CLIENT GEOLOCATION OBTAINED: Lat:", position.coords.latitude,
                            "Lon:", position.coords.longitude,
                            "Accuracy (m):", position.coords.accuracy);
                resolve({
                    latitude: position.coords.latitude,
                    longitude: position.coords.longitude,
                    accuracy: position.coords.accuracy
                });
            },
            (error) => {
                console.error("CLIENT GEOLOCATION ERROR: Code:", error.code, "Message:", error.message, "Raw Object:", error);
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

// --- Utilities ---
function navigateToEmployee(selectedEid, isCurrentlyReportMode) {
    let baseUrl = 'timeclock.jsp';
    let params = [];
    if (selectedEid && selectedEid !== "" && parseInt(selectedEid, 10) > 0) {
        params.push('eid=' + selectedEid);
    }
    if (isCurrentlyReportMode === true) {
        params.push('reportMode=true');
    }
    if (params.length > 0) {
        baseUrl += '?' + params.join('&');
    }
    window.location.href = baseUrl;
}

// --- Notification Modal Logic ---
let notificationModal_tc, notificationModalContent_tc, notificationMessage_tc, notificationCloseButton_tc, notificationOkButton_tc;

function showTimeclockNotificationModal(message, isError) {
    if (!notificationModal_tc) notificationModal_tc = document.getElementById("notificationModal");
    if (notificationModal_tc && !notificationModalContent_tc) notificationModalContent_tc = notificationModal_tc.querySelector('.modal-content');
    if (notificationModal_tc && !notificationMessage_tc) notificationMessage_tc = notificationModal_tc.querySelector('#notificationMessage');

    if (notificationModal_tc && notificationMessage_tc && notificationModalContent_tc) {
        const tempDiv = document.createElement('div');
        tempDiv.textContent = message; // Use textContent for security, then replace for display
        notificationMessage_tc.innerHTML = tempDiv.innerHTML.replace(/\n/g, '<br>');
        
        notificationModalContent_tc.classList.remove('error-message', 'success-message');
        // Assuming your modal content has these classes for styling error/success
        const messageContainer = notificationModal_tc.querySelector('p#notificationMessage') || notificationModalContent_tc;
        messageContainer.classList.remove('error-text', 'success-text'); // Example custom classes
        
        if (isError) {
            notificationModalContent_tc.classList.add('error-message'); // General modal error style
            if (messageContainer) messageContainer.classList.add('error-text');
        } else {
            notificationModalContent_tc.classList.add('success-message'); // General modal success style
            if (messageContainer) messageContainer.classList.add('success-text');
        }

        const nBar = document.getElementById('notification-bar');
        if (nBar) nBar.style.display = 'none'; // Hide inline notification bar if modal is shown

        if (typeof showModal === 'function') { showModal(notificationModal_tc); }
        else { notificationModal_tc.classList.add('modal-visible'); }
    } else {
        alert((isError ? "Error: " : "Notification: ") + message.replace(/<br\s*\/?>/gi, '\n'));
    }
}

function hideTimeclockNotificationModal() {
    if (notificationModal_tc) {
        if (typeof hideModal === 'function') { hideModal(notificationModal_tc); }
        else { notificationModal_tc.classList.remove('modal-visible'); }
    }
}

// --- Print Logic ---
function printTimecard() {
    const printableArea = document.getElementById('printableTimecardArea');
    if (!printableArea) { alert("Error: Printable area not found."); return; }
    const clonedPrintableArea = printableArea.cloneNode(true);
    const tableContainerInClone = clonedPrintableArea.querySelector('#timecardTableContainer');
    if (tableContainerInClone) { tableContainerInClone.style.maxHeight = 'none'; tableContainerInClone.style.overflowY = 'visible'; }
    const tableInClone = clonedPrintableArea.querySelector('.timecard-table');
    if (tableInClone) { tableInClone.style.maxHeight = 'none'; tableInClone.style.overflowY = 'visible'; }
    let printWindow = window.open('', '_blank', 'width=800,height=600,scrollbars=yes,resizable=yes');
    if (!printWindow) { alert("Could not open print window. Check popup blocker."); return; }
    printWindow.document.write('<html><head><title>Print Timecard</title>');
    const contextPathForCss = (typeof app_contextPath !== 'undefined') ? app_contextPath : (window.location.pathname.substring(0, window.location.pathname.indexOf('/',1) === -1 ? window.location.pathname.length : window.location.pathname.indexOf('/',1)));
    printWindow.document.write('<link rel="stylesheet" href="' + contextPathForCss + '/css/timeclock.css?v=' + new Date().getTime() + '">');
    printWindow.document.write('<link rel="stylesheet" href="' + contextPathForCss + '/css/reports.css?v=' + new Date().getTime() + '">'); // If reports.css has relevant base table styles
    printWindow.document.write('<style>body{margin:20px;background-color:#fff;font-size:10pt;}.timecard{border:1px solid #999;box-shadow:none;margin:0;max-width:100%;width:auto!important;}.timecard-table-container{border:none;}.punch-buttons,.report-actions-container,.employee-selector-container,#main-clock-container,.main-navbar,#notificationModal{display:none!important;}@media print{@page{size:auto;margin:.75in}body{-webkit-print-color-adjust:exact;print-color-adjust:exact}}</style></head><body>');
    printWindow.document.write(clonedPrintableArea.innerHTML);
    printWindow.document.write('</body></html>');
    printWindow.document.close();
    setTimeout(() => { try {printWindow.focus(); printWindow.print(); } catch(e){alert("Print error: " + e.message);}}, 500);
}

// --- Inactivity Auto-Logout & Quick Logout Logic ---
let inactivityTimer;
let quickLogoutTimer;
function performLogoutRedirectWithMessage(messageKey, isAutoLogoutFromPunch = false) {
    console.log(`[timeclock.js] Initiating ${isAutoLogoutFromPunch ? "quick post-punch" : "inactivity"} logout. Reason key: ${messageKey}`);
    hideTimeclockNotificationModal();
    const redirectDelay = isAutoLogoutFromPunch ? 1500 : 2000; // Reduced delay
    setTimeout(() => {
        const contextPath = (typeof app_contextPath !== 'undefined') ? app_contextPath : (window.location.pathname.substring(0, window.location.pathname.indexOf('/',1) === -1 ? window.location.pathname.length : window.location.pathname.indexOf('/',1)));
        let logoutReasonParam = "Auto-logout initiated.";
        if (messageKey === 'punchSuccess') logoutReasonParam = 'Punch successful. Auto-logout initiated.';
        else if (messageKey === 'sessionTimeout') logoutReasonParam = 'Session timed out due to inactivity.';
        window.location.href = contextPath + '/LogoutServlet?autoLogout=true&reason=' + encodeURIComponent(logoutReasonParam);
    }, redirectDelay);
}
function resetInactivityTimer() {
    if (typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User') {
        clearTimeout(inactivityTimer);
        const timeoutSeconds = (typeof sessionTimeoutDuration_Js !== 'undefined' && sessionTimeoutDuration_Js > 0) ? sessionTimeoutDuration_Js : (30 * 60); // Default 30 mins
        const timeoutMilliseconds = timeoutSeconds * 1000;
        const redirectTimeout = timeoutMilliseconds; // Give a small buffer if desired
        if (quickLogoutTimer == null || typeof quickLogoutTimer === 'undefined') { // Only reset if not in quick logout
            inactivityTimer = setTimeout(() => { performLogoutRedirectWithMessage('sessionTimeout'); }, redirectTimeout);
        }
    }
}
function setupInactivityDetection() {
    if (typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User') {
        resetInactivityTimer();
        ['mousemove', 'mousedown', 'keypress', 'touchmove', 'scroll', 'click', 'keydown'].forEach(activityEvent => {
            window.addEventListener(activityEvent, resetInactivityTimer, { passive: true });
        });
    }
}

// --- Event Listener for Punch Forms ---
async function handlePunchSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const submitButton = form.querySelector('button[type="submit"]');
    const originalButtonText = submitButton ? submitButton.innerHTML : "";

    if (submitButton) {
        submitButton.disabled = true;
        submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Submitting...';
    }

    let fingerprint = null;
    let locationData = null;
    let clientSideErrorMessages = [];

    try { fingerprint = await getDeviceFingerprint(); }
    catch (fpError) { clientSideErrorMessages.push(fpError.message || "Could not identify device."); console.error("FP Err:", fpError); }

    try { locationData = await getClientGeolocation(); }
    catch (locError) { clientSideErrorMessages.push(locError.message || "Could not retrieve location."); console.error("Geo Err:", locError); }
    
    if (fingerprint === 'fingerprintjs_not_loaded' || fingerprint === null) {
        if(!clientSideErrorMessages.some(msg => msg.toLowerCase().includes("fingerprint") || msg.toLowerCase().includes("device id"))) {
             clientSideErrorMessages.unshift("Device identification module failed critically.");
        }
    }

    // If FingerprintJS itself failed to load or produce a hash, abort.
    if (fingerprint === 'fingerprintjs_not_loaded' || fingerprint === null) {
        showTimeclockNotificationModal((clientSideErrorMessages.join("<br/>") || "Device ID module failed.") + "<br/>Punch submission aborted.", true);
        if (submitButton) { submitButton.disabled = false; submitButton.innerHTML = originalButtonText; }
        return; // Abort submission
    }
    
    // Set fingerprint and location data to hidden form fields
    document.getElementById(form.id === "punchInForm" ? 'deviceFingerprintHash_IN' : 'deviceFingerprintHash_OUT').value = fingerprint || "";
    const latInput = document.getElementById(form.id === "punchInForm" ? 'clientLatitude_IN' : 'clientLatitude_OUT');
    const lonInput = document.getElementById(form.id === "punchInForm" ? 'clientLongitude_IN' : 'clientLongitude_OUT');
    const accInput = document.getElementById(form.id === "punchInForm" ? 'clientLocationAccuracy_IN' : 'clientLocationAccuracy_OUT');
    const tzInput = document.getElementById(form.id === "punchInForm" ? 'browserTimeZoneId_IN' : 'browserTimeZoneId_OUT');

    if (locationData) {
        if(latInput) latInput.value = locationData.latitude;
        if(lonInput) lonInput.value = locationData.longitude;
        if(accInput) accInput.value = locationData.accuracy;
    } else { // Clear them if no data
        if(latInput) latInput.value = "";
        if(lonInput) lonInput.value = "";
        if(accInput) accInput.value = "";
        console.warn("Submitting punch without location data due to client-side issue. Server will validate.");
    }
    if(tzInput) { // Always try to set timezone
        try { tzInput.value = Intl.DateTimeFormat().resolvedOptions().timeZone; }
        catch(e) { console.warn("Could not get browser timezone.", e); tzInput.value = "Unknown"; }
    }

    if (clientSideErrorMessages.length > 0) {
        // Log these errors, but proceed with submission. Server will ultimately decide.
        console.warn("Client-side issues occurred (punch still submitted for server validation):", clientSideErrorMessages.join("; "));
    }
    form.submit(); // Submit the form
}

// --- New function for row banding ---
function applyRowBandingByDay() {
    const tableBody = document.querySelector('#punches tbody'); // Target the specific table
    if (!tableBody) {
        console.warn("Timeclock.js: Table body for punches not found for banding.");
        return;
    }

    const rows = tableBody.querySelectorAll('tr');
    if (rows.length === 0) return;

    let currentDateString = null;
    let currentBandClass = 'band-a'; // Start with band-a

    rows.forEach(row => {
        // Assuming the date is in the second cell (index 1) which contains "MM/DD/YYYY"
        const dateCell = row.cells[1];
        if (dateCell) {
            const dateText = dateCell.textContent.trim();
            if (dateText) { // Ensure cell has text
                if (currentDateString !== dateText) {
                    // Day has changed, toggle the band class
                    currentBandClass = (currentBandClass === 'band-a') ? 'band-b' : 'band-a';
                    currentDateString = dateText;
                }
                // Remove old band classes and add the new one
                row.classList.remove('band-a', 'band-b');
                row.classList.add(currentBandClass);
            }
        }
    });
    console.log("Timeclock.js: Applied row banding by day.");
}


document.addEventListener('DOMContentLoaded', function() {
    console.log("--- Timeclock Page DOMContentLoaded START (v19 - Scroll, Banding) ---");
    notificationModal_tc = document.getElementById("notificationModal");
    // ... (rest of your existing DOMContentLoaded, including notification modal setup)
    if (notificationModal_tc) {
        notificationModalContent_tc = notificationModal_tc.querySelector('.modal-content');
        notificationMessage_tc = notificationModal_tc.querySelector('#notificationMessage');
        notificationCloseButton_tc = notificationModal_tc.querySelector('#closeNotificationModal'); // Corrected ID
        notificationOkButton_tc = notificationModal_tc.querySelector('#okButton'); // Corrected ID
        if (notificationOkButton_tc) {
            notificationOkButton_tc.addEventListener('click', () => {
                hideTimeclockNotificationModal();
                const urlParamsForBtn = new URLSearchParams(window.location.search);
                const punchStatusForBtn = urlParamsForBtn.get('punchStatus');
                // Only reset inactivity if not a successful user punch (which triggers quick logout)
                if (!(punchStatusForBtn === 'success' && typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User')) {
                     resetInactivityTimer(); // If user clicks OK, reset inactivity
                }
            });
        }
        if (notificationCloseButton_tc) {
            notificationCloseButton_tc.addEventListener('click', () => {
                 hideTimeclockNotificationModal();
                 const urlParamsForBtn = new URLSearchParams(window.location.search);
                 if (!(urlParamsForBtn.get('punchStatus') === 'success' && typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User')) {
                     resetInactivityTimer();
                 }
            });
        }
    } else { console.error("Modal #notificationModal not found."); }


    const punchInForm = document.getElementById('punchInForm');
    if (punchInForm) punchInForm.addEventListener('submit', handlePunchSubmit);
    const punchOutForm = document.getElementById('punchOutForm');
    if (punchOutForm) punchOutForm.addEventListener('submit', handlePunchSubmit);

    // ... (existing URL param message handling) ...
    const urlParams = new URLSearchParams(window.location.search);
    const messageParam = urlParams.get('message');
    const errorParam = urlParams.get('error');
    const punchStatus = urlParams.get('punchStatus');
    const accuracyOverrideFlag = urlParams.get('accuracyOverride');

    let currentUrlForRedirect = window.location.pathname;
    let paramsForCurrentUrl = [];
    const eidFromUrl = urlParams.get('eid');
    const reportModeFromUrl = urlParams.get('reportMode');
    if (eidFromUrl && typeof displayedEid_tc !== 'undefined' && parseInt(eidFromUrl, 10) === displayedEid_tc) { paramsForCurrentUrl.push('eid=' + displayedEid_tc); }
    if (reportModeFromUrl && typeof isReportMode_tc !== 'undefined' && isReportMode_tc.toString() === reportModeFromUrl) { paramsForCurrentUrl.push('reportMode=true'); }
    if (paramsForCurrentUrl.length > 0) { currentUrlForRedirect += '?' + paramsForCurrentUrl.join('&'); }

    if (messageParam) {
        showTimeclockNotificationModal(decodeURIComponent(messageParam), false);
        if (accuracyOverrideFlag === 'true') {
            console.warn("SERVER INDICATED ACCURACY OVERRIDE: Punch was allowed due to server-side location accuracy adjustment. Raw client accuracy was logged by 'CLIENT GEOLOCATION OBTAINED' if successful.");
        }
        if (window.history.replaceState) { window.history.replaceState(null, '', currentUrlForRedirect); }
        if (punchStatus === 'success' && typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User') {
            clearTimeout(inactivityTimer); clearTimeout(quickLogoutTimer); // Clear both timers
            quickLogoutTimer = setTimeout(() => { performLogoutRedirectWithMessage('punchSuccess', true); }, 1500);
        }
    } else if (errorParam) {
        // Do not show modal for these specific auto-logout messages that are informational.
        if (errorParam !== 'Session timed out due to inactivity.' && errorParam !== 'Punch successful. Auto-logout initiated.') {
            showTimeclockNotificationModal(decodeURIComponent(errorParam), true);
        }
        if (window.history.replaceState) { window.history.replaceState(null, '', currentUrlForRedirect); }
    } else {
        // Only auto-hide #notification-bar if it's NOT showing an error from JSP pageError
        const nBar = document.getElementById('notification-bar');
        if (nBar && nBar.textContent.trim() !== '' && nBar.style.display !== 'none' && !nBar.classList.contains('error-message')) {
             setTimeout(() => {
                if (nBar && (!notificationModal_tc || !notificationModal_tc.classList.contains('modal-visible'))) { // Only hide if modal isn't also up
                    nBar.style.transition='opacity 0.5s ease-out'; nBar.style.opacity='0';
                    setTimeout(() => {nBar.style.display='none';}, 500);
                }
             }, 5000); // Autohide success messages after 5 seconds
        }
    }


    const btnPrintThisTimecard = document.getElementById('btnPrintThisTimecard');
    if (btnPrintThisTimecard) btnPrintThisTimecard.addEventListener('click', printTimecard);

    setupInactivityDetection(); // Setup user inactivity auto-logout

    // --- New: Auto-scroll and Row Banding ---
    applyRowBandingByDay(); // Apply banding after table is populated

    const timecardTableContainer = document.getElementById('timecardTableContainer');
    if (timecardTableContainer && timecardTableContainer.scrollHeight > timecardTableContainer.clientHeight) {
        // Small delay to ensure the browser has rendered and calculated scrollHeight correctly
        setTimeout(() => {
            timecardTableContainer.scrollTo({
                top: timecardTableContainer.scrollHeight,
                behavior: 'smooth'
            });
            console.log("Timeclock.js: Scrolled to bottom of timecard table.");
        }, 150); // Increased delay slightly
    }
    // --- End New ---

    console.log("--- Timeclock Page DOMContentLoaded END (v19) ---");
});