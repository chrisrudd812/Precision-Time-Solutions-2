// js/timeclock.js - v22 (Final Robust Modal Fix)

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
    const modal = document.getElementById("notificationModal");
    if (!modal) return;

    const title = modal.querySelector('#notificationModalTitle');
    const msg = modal.querySelector('#notificationMessage');
    const okBtn = modal.querySelector('#okButton');
    const closeBtn = modal.querySelector('#closeNotificationModal');

    title.textContent = isError ? "Error" : "Notification";
    const modalContent = modal.querySelector('.modal-content');
    if (modalContent) {
        modalContent.classList.toggle('error-message', isError);
        modalContent.classList.toggle('success-message', !isError);
    }

    msg.innerHTML = message.replace(/\n/g, '<br>');

    const hide = () => {
        modal.style.display = 'none';
        modal.classList.remove('modal-visible');
    };

    // 🔑 Attach listeners once (no cloning)
    okBtn.onclick = hide;
    closeBtn.onclick = hide;

    modal.style.display = 'flex';
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

let inactivityTimer;
let quickLogoutTimer;
function performLogoutRedirectWithMessage(messageKey, isAutoLogoutFromPunch = false) {
    const contextPath = (typeof app_contextPath !== 'undefined') ? app_contextPath : '';
    let logoutReasonParam = "Auto-logout initiated.";
    if (messageKey === 'punchSuccess') logoutReasonParam = 'Punch successful. Auto-logout initiated.';
    else if (messageKey === 'sessionTimeout') logoutReasonParam = 'Session timed out due to inactivity.';
    window.location.href = contextPath + '/LogoutServlet?autoLogout=true&reason=' + encodeURIComponent(logoutReasonParam);
}
function resetInactivityTimer() {
    if (typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User') {
        clearTimeout(inactivityTimer);
        const timeoutSeconds = (typeof sessionTimeoutDuration_Js !== 'undefined' && sessionTimeoutDuration_Js > 0) ? sessionTimeoutDuration_Js : (30 * 60);
        if (quickLogoutTimer == null || typeof quickLogoutTimer === 'undefined') {
            inactivityTimer = setTimeout(() => { performLogoutRedirectWithMessage('sessionTimeout'); }, timeoutSeconds * 1000);
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

async function handlePunchSubmit(event) {
    event.preventDefault();
    const form = event.target;
    const submitButton = form.querySelector('button[type="submit"]');
    
    if (submitButton) {
        submitButton.disabled = true;
        submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Submitting...';
    }
    
    try {
        const fingerprint = await getDeviceFingerprint();
        document.getElementById(form.id === "punchInForm" ? 'deviceFingerprintHash_IN' : 'deviceFingerprintHash_OUT').value = fingerprint;
    } catch (fpError) {
        console.error("FP Err:", fpError);
    }
    
    try {
        const locationData = await getClientGeolocation();
        if (locationData) {
            document.getElementById(form.id === "punchInForm" ? 'clientLatitude_IN' : 'clientLatitude_OUT').value = locationData.latitude;
            document.getElementById(form.id === "punchInForm" ? 'clientLongitude_IN' : 'clientLongitude_OUT').value = locationData.longitude;
            document.getElementById(form.id === "punchInForm" ? 'clientLocationAccuracy_IN' : 'clientLocationAccuracy_OUT').value = locationData.accuracy;
        }
    } catch (locError) {
        console.error("Geo Err:", locError);
    }
    
    try {
        const tzInput = document.getElementById(form.id === "punchInForm" ? 'browserTimeZoneId_IN' : 'browserTimeZoneId_OUT');
        if (tzInput) tzInput.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
    } catch (e) { console.warn("Could not get browser timezone.", e); }
    
    form.submit();
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
    const punchInForm = document.getElementById('punchInForm');
    if (punchInForm) punchInForm.addEventListener('submit', handlePunchSubmit);
    
    const punchOutForm = document.getElementById('punchOutForm');
    if (punchOutForm) punchOutForm.addEventListener('submit', handlePunchSubmit);

    const urlParams = new URLSearchParams(window.location.search);
    const messageParam = urlParams.get('message');
    const errorParam = urlParams.get('error');
    const punchStatus = urlParams.get('punchStatus');

    let currentUrlForRedirect = window.location.pathname;
    const eidFromUrl = urlParams.get('eid');
    if (eidFromUrl) { currentUrlForRedirect += '?eid=' + eidFromUrl; }

    if (messageParam) {
        showTimeclockNotificationModal(decodeURIComponent(messageParam), false);
        if (window.history.replaceState) { window.history.replaceState(null, '', currentUrlForRedirect); }
        if (punchStatus === 'success' && typeof currentUserPermissions_tc !== 'undefined' && currentUserPermissions_tc === 'User') {
            clearTimeout(inactivityTimer);
            quickLogoutTimer = setTimeout(() => { performLogoutRedirectWithMessage('punchSuccess', true); }, 1500);
        }
    } else if (errorParam) {
        showTimeclockNotificationModal(decodeURIComponent(errorParam), true);
        if (window.history.replaceState) { window.history.replaceState(null, '', currentUrlForRedirect); }
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