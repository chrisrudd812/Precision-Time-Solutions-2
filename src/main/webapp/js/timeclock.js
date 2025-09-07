// js/timeclock.js

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
function performLogoutRedirectWithMessage(messageKey) {
    const contextPath = (typeof app_contextPath !== 'undefined') ? app_contextPath : '';
    let logoutReasonParam = "Auto-logout initiated.";
    if (messageKey === 'punchSuccess') logoutReasonParam = 'Punch successful. Logging out...';
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

    const restoreButton = () => {
        if (submitButton) {
            submitButton.disabled = false;
            submitButton.innerHTML = form.id === "punchInForm" ? 'PUNCH IN' : 'PUNCH OUT';
        }
    };

    const executePunch = async (latitude, longitude) => {
        try {
            const fingerprint = await getDeviceFingerprint();
            document.getElementById(form.id === "punchInForm" ? 'deviceFingerprintHash_IN' : 'deviceFingerprintHash_OUT').value = fingerprint;
            document.getElementById(form.id === "punchInForm" ? 'latitude_IN' : 'latitude_OUT').value = latitude;
            document.getElementById(form.id === "punchInForm" ? 'longitude_IN' : 'longitude_OUT').value = longitude;
            const tzInput = document.getElementById(form.id === "punchInForm" ? 'browserTimeZoneId_IN' : 'browserTimeZoneId_OUT');
            if (tzInput) tzInput.value = Intl.DateTimeFormat().resolvedOptions().timeZone;
            const deviceTypeInput = document.getElementById(form.id === "punchInForm" ? 'deviceType_IN' : 'deviceType_OUT');
            if (deviceTypeInput) deviceTypeInput.value = getDeviceType();
            form.submit();
        } catch (error) {
            console.error("Data gathering error:", error);
            showTimeclockNotificationModal("Could not gather all required data before punching: " + error.message, true);
            restoreButton();
        }
    };

    if (typeof IS_LOCATION_RESTRICTION_ENABLED !== 'undefined' && IS_LOCATION_RESTRICTION_ENABLED) {
        try {
            const locationData = await getClientGeolocation();
            executePunch(locationData.latitude, locationData.longitude);
        } catch (locError) {
            console.error("Geo Err:", locError);
            showTimeclockNotificationModal(locError.message, true);
            restoreButton();
        }
    } else {
        executePunch("", "");
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
    const punchInForm = document.getElementById('punchInForm');
    if (punchInForm) punchInForm.addEventListener('submit', handlePunchSubmit);
    
    const punchOutForm = document.getElementById('punchOutForm');
    if (punchOutForm) punchOutForm.addEventListener('submit', handlePunchSubmit);

    const btnPrintEmailSingle = document.getElementById('btnPrintEmailSingleTimecard');
    const employeeSelect = document.getElementById('employeeSelect'); 

    if (btnPrintEmailSingle && employeeSelect) {
        btnPrintEmailSingle.addEventListener('click', function() {
            // [DEBUG LOG]
            console.log("[TimecardDebug] 'Print/Email' button clicked.");
            
            const eid = employeeSelect.value;
            
            // [DEBUG LOG]
            console.log(`[TimecardDebug] EID read from dropdown is: '${eid}'`);
            
            if (eid && eid !== "0") {
                const printUrl = `${app_contextPath}/PrintTimecardsServlet?filterType=single&filterValue=${eid}`;
                // [DEBUG LOG]
                console.log(`[TimecardDebug] Opening new tab with URL: ${printUrl}`);
                window.open(printUrl, '_blank');
            } else {
                console.log("[TimecardDebug] No employee selected. Alerting user.");
                alert("An employee must be selected from the dropdown to print or email their timecard.");
            }
        });
    }

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
            quickLogoutTimer = setTimeout(() => { performLogoutRedirectWithMessage('punchSuccess'); }, 12000);
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