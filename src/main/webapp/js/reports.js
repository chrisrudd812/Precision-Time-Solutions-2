/**
 * reports.js
 * Handles fetching and displaying reports dynamically on reports.jsp
 * Uses commonUtils.js for shared functionality.
 * v13: Removed default sort text from descriptions.
 */

// --- Draggable Modal Functionality ---
function makeElementDraggable(elmnt, handle) {
    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    const dragHandle = handle || elmnt; 

    if (dragHandle && typeof dragHandle.onmousedown !== 'undefined') {
        dragHandle.onmousedown = dragMouseDown;
        if (dragHandle.style) dragHandle.style.cursor = 'move';
    } else {
        console.warn("MakeElementDraggable: No valid drag handle provided or found for:", elmnt ? elmnt.id : "unknown element");
        return;
    }

    function dragMouseDown(e) {
        e = e || window.event;
        let currentTarget = e.target;
        while(currentTarget && currentTarget !== dragHandle) {
            if (['INPUT', 'SELECT', 'BUTTON', 'TEXTAREA', 'A'].includes(currentTarget.tagName)) {
                return;
            }
            currentTarget = currentTarget.parentNode;
        }
        e.preventDefault();
        pos3 = e.clientX;
        pos4 = e.clientY;
        document.onmouseup = closeDragElement;
        document.onmousemove = elementDrag;
    }

    function elementDrag(e) {
        e = e || window.event;
        e.preventDefault();
        pos1 = pos3 - e.clientX;
        pos2 = pos4 - e.clientY;
        pos3 = e.clientX;
        pos4 = e.clientY;
        elmnt.style.top = (elmnt.offsetTop - pos2) + "px";
        elmnt.style.left = (elmnt.offsetLeft - pos1) + "px";
    }

    function closeDragElement() {
        document.onmouseup = null;
        document.onmousemove = null;
    }
}
// --- End Draggable Modal Functionality ---


document.addEventListener('DOMContentLoaded', function() {
    console.log("Reports JS Loaded - v13 (Removed default sort text)");

    const reportOutputDiv = document.getElementById('reportOutput');
    const loadingIndicator = document.getElementById('loadingIndicator');
    const reportTitleElement = document.getElementById('reportTitle');
    const reportDescriptionElement = document.getElementById('reportDescription');
    const reportActionsDiv = document.getElementById('reportActions');
    const printReportBtn = document.getElementById('printReportBtn');

    const fixMissingPunchesBtnReports = document.getElementById('fixMissingPunchesBtnReports');
    const editPunchModalReports = document.getElementById('editPunchModalReports');
    const editPunchFormReports = document.getElementById('editPunchFormReports');
    const closeEditPunchModalReportsBtn = document.getElementById('closeEditPunchModalReports');
    const cancelEditPunchReportsBtn = document.getElementById('reports_cancelEditPunch');

    let selectedReportExceptionRowElement = null;
    let currentReportExceptionData = {};

    const notificationModal = document.getElementById("notificationModal");
    const notificationCloseButton = document.getElementById("closeNotificationModal");
    const notificationOkButton = document.getElementById("okButton");

    if (notificationModal && notificationModal.querySelector('.modal-content') && notificationModal.querySelector('.modal-content > h2')) {
        if (notificationCloseButton) notificationCloseButton.addEventListener("click", function() { if (typeof hideModal === 'function') hideModal(notificationModal); });
        if (notificationOkButton) notificationOkButton.addEventListener("click", function() { if (typeof hideModal === 'function') hideModal(notificationModal); });
        window.addEventListener("click", function(event) { if (event.target === notificationModal) { if (typeof hideModal === 'function') hideModal(notificationModal); }});
        makeElementDraggable(notificationModal.querySelector('.modal-content'), notificationModal.querySelector('.modal-content > h2'));
    }

    if (editPunchModalReports && editPunchModalReports.querySelector('.modal-content') && editPunchModalReports.querySelector('.modal-content > h2')) {
        if (closeEditPunchModalReportsBtn) closeEditPunchModalReportsBtn.addEventListener('click', hideEditPunchModalReports);
        if (cancelEditPunchReportsBtn) cancelEditPunchReportsBtn.addEventListener('click', hideEditPunchModalReports);
        window.addEventListener('click', (event) => { if (event.target === editPunchModalReports) hideEditPunchModalReports(); });
        makeElementDraggable(editPunchModalReports.querySelector('.modal-content'), editPunchModalReports.querySelector('.modal-content > h2'));
    }

    if (editPunchFormReports) {
        editPunchFormReports.addEventListener('submit', handleEditPunchFormSubmitReports);
    }

    if (fixMissingPunchesBtnReports) {
        fixMissingPunchesBtnReports.addEventListener('click', () => {
            if (!selectedReportExceptionRowElement || !currentReportExceptionData.globalEid) {
                if (typeof showPageNotification === 'function') showPageNotification("Please select an exception row to edit.", true);
                else alert("Please select an exception row to edit.");
                return;
            }
            prepareAndShowEditPunchModalReports(currentReportExceptionData);
        });
    }
    
    // --- MODIFIED: Removed "Default sort" text ---
    const reportDescriptions = {
        exception: "Shows punch records with missing OUT times for work-type punches within the current pay period. Click a row to select, then 'Fix Missing Punches'.",
        tardy: "Summarizes employees marked as tardy or leaving early based on punch data.",
        whosin: "Lists employees who are currently clocked IN based on the latest punch data for today.",
        activeEmployees: "Lists all currently active employees with key contact and assignment information.",
        inactiveEmployees: "Lists employees marked as inactive. Click a row to reactivate.",
        employeesByDept: "Lists active employees filtered by the selected department.",
        employeesBySched: "Lists active employees filtered by the selected schedule.",
        employeesBySup: "Lists active employees filtered by the selected supervisor."
    };
    const employeeReportHeaders = `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="string">Department</th><th class="sortable" data-sort-type="string">Schedule</th><th class="sortable" data-sort-type="string">Supervisor</th><th class="sortable" data-sort-type="string">Email</th><th class="sortable" data-sort-type="string">Phone</th></tr></thead>`;
    const reportHeaders = {
         exception: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="date">Date</th><th class="sortable" data-sort-type="string">IN Time</th><th class="sortable" data-sort-type="string">OUT Time</th></tr></thead>`,
         tardy: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="number">Late Count</th><th class="sortable" data-sort-type="number">Early Out Count</th></tr></thead>`,
         whosin: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="string">Department</th><th class="sortable" data-sort-type="string">Schedule</th></tr></thead>`,
         activeEmployees: employeeReportHeaders,
         inactiveEmployees: employeeReportHeaders,
         employeesByDept: employeeReportHeaders,
         employeesBySched: employeeReportHeaders,
         employeesBySup: employeeReportHeaders
    };

    const hideModal = window.hideModal || function(modalElement) { if(modalElement) modalElement.classList.remove('modal-visible'); console.warn("hideModal from commonUtils not found, using fallback."); };
    const showModal = window.showModal || function(modalElement) { if(modalElement) modalElement.classList.add('modal-visible'); console.warn("showModal from commonUtils not found, using fallback."); };
    const showPageNotification = window.showPageNotification || function(message, isError) { alert((isError ? "Error: " : "Info: ") + message); console.warn("showPageNotification from commonUtils not found, using alert fallback."); };
    const decodeHtmlEntities = window.decodeHtmlEntities || function(text) {
        const textArea = document.createElement('textarea');
        textArea.innerHTML = text;
        return textArea.value;
    };
    const parseTimeTo24Hour = window.parseTimeTo24Hour || function(timeStr12hr) {
        if (!timeStr12hr || typeof timeStr12hr !== 'string' || String(timeStr12hr).trim() === '' || String(timeStr12hr).toLowerCase().includes('missing')) return '';
        timeStr12hr = String(timeStr12hr).trim();
        try {
            if (/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(timeStr12hr)) { if (timeStr12hr.length === 5) return timeStr12hr + ':00'; return timeStr12hr; }
            const parts = timeStr12hr.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)/i);
            if (!parts) { console.warn("Reports.js: Could not parse 12hr time string:", timeStr12hr); return ''; }
            let h = parseInt(parts[1], 10); const m = parts[2]; const s = parts[3] ? parts[3] : '00'; const ampm = parts[4].toUpperCase();
            if (isNaN(h) || h < 1 || h > 12) { console.warn("Reports.js: Invalid hour in time:", timeStr12hr); return '';}
            if (ampm === 'PM' && h !== 12) h += 12;
            if (ampm === 'AM' && h === 12) h = 0;
            return `${String(h).padStart(2, '0')}:${m}:${s}`;
        } catch (e) { console.error("Reports.js: Error parsing time string:", timeStr12hr, e); return ''; }
    };


    function loadReport(reportType, filterValue = null) {
        console.log(`Reports.js: Attempting to load report: ${reportType}, Filter: ${filterValue}`);
        if (loadingIndicator) loadingIndicator.style.display = 'flex';
        if (reportActionsDiv) reportActionsDiv.style.display = 'none';
        if (fixMissingPunchesBtnReports) fixMissingPunchesBtnReports.style.display = 'none';
        if (reportOutputDiv) reportOutputDiv.innerHTML = '';

        const reportName = reportType.replace(/([A-Z])/g, ' $1').trim();
        let titleText = `${reportName.charAt(0).toUpperCase() + reportName.slice(1)} Report`;
        if (filterValue) {
             try { filterValue = decodeURIComponent(filterValue); } catch(e) { console.warn("Reports.js: Error decoding filterValue:", e); }
             if(reportType === 'employeesByDept') titleText = `Department: ${filterValue} - Employees`;
             else if(reportType === 'employeesBySched') titleText = `Schedule: ${filterValue} - Employees`;
             else if(reportType === 'employeesBySup') titleText = `Supervisor: ${filterValue} - Employees`;
        }
        if(reportTitleElement) reportTitleElement.textContent = titleText;
        if(reportDescriptionElement) reportDescriptionElement.textContent = reportDescriptions[reportType] || 'Report results shown below.';

        const params = new URLSearchParams();
        params.append('reportType', reportType);
        if (filterValue) params.append('filterValue', filterValue);

        fetch('ReportServlet', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body: params })
        .then(response => {
            if (!response.ok) return response.text().then(text => { throw new Error(`Server Error: ${response.status} - ${text || response.statusText}`); });
            const contentType = response.headers.get("content-type");
            if (contentType && contentType.includes("application/json")) return response.json();
            throw new Error("Received non-JSON response from server.");
        })
        .then(data => {
            console.log("Reports.js: Report data received:", data);
            if (loadingIndicator) loadingIndicator.style.display = 'none';

            if (data.success) {
                if (data.html && data.html !== 'NO_EXCEPTIONS') {
                    const tableHeadersHTML = reportHeaders[reportType] || `<thead><tr><th>Report Data</th></tr></thead>`;
                    let tableClass = 'sortable'; 
                    if (reportType === 'inactiveEmployees') tableClass += ' inactive-employees-table';
                    if (reportType === 'exception') tableClass += ' exception-report-table-interactive';

                    if(reportOutputDiv) {
                        reportOutputDiv.innerHTML = `
                            <div class="table-container report-table-container">
                                <table class="report-table ${tableClass}" id="dynamicReportTable">
                                    ${tableHeadersHTML}
                                    <tbody>
                                        ${data.html}
                                    </tbody>
                                </table>
                            </div>`;
                        const reportTable = reportOutputDiv.querySelector('#dynamicReportTable');
                        if (reportTable) {
                            const initialSort = { columnIndex: 0, ascending: true }; 
                            
                            if (typeof makeTableSortable === 'function') {
                                makeTableSortable(reportTable, initialSort);
                            } else {
                                console.warn("makeTableSortable not found (expected in commonUtils.js)");
                            }

                            if (reportActionsDiv && printReportBtn) {
                                printReportBtn.style.display = 'inline-flex';
                                reportActionsDiv.style.display = 'flex';
                            }
                            if (reportType === 'exception' && fixMissingPunchesBtnReports) {
                                fixMissingPunchesBtnReports.style.display = 'inline-flex';
                                fixMissingPunchesBtnReports.disabled = true;
                            }
                        } else {
                            if (reportActionsDiv) reportActionsDiv.style.display = 'none';
                        }
                    }
                } else {
                    if(reportOutputDiv) reportOutputDiv.innerHTML = `<p class="report-message-row">${data.message || 'No records found.'}</p>`;
                    if (reportActionsDiv && printReportBtn) { reportActionsDiv.style.display = 'flex'; printReportBtn.style.display = 'none';}
                    if (fixMissingPunchesBtnReports) fixMissingPunchesBtnReports.style.display = 'none';
                }
            } else {
                if(reportOutputDiv) reportOutputDiv.innerHTML = `<p class="report-error-row">Error: ${data.message || 'Unknown server error.'}</p>`;
                if (reportActionsDiv) reportActionsDiv.style.display = 'none';
            }
        })
        .catch(error => {
            console.error('Reports.js: Error fetching or processing report:', error);
            if (loadingIndicator) loadingIndicator.style.display = 'none';
            if(reportTitleElement) reportTitleElement.textContent = "Report Error";
            if(reportDescriptionElement) reportDescriptionElement.textContent = "Could not load the requested report.";
            if (reportActionsDiv) reportActionsDiv.style.display = 'none';
            if (reportOutputDiv) reportOutputDiv.innerHTML = `<p class="report-error-row">Failed to load report: ${error.message}</p>`;
        });
    }

    function hideEditPunchModalReports() {
        if (editPunchModalReports) hideModal(editPunchModalReports);
        if (selectedReportExceptionRowElement) {
            selectedReportExceptionRowElement.classList.remove('selected');
            selectedReportExceptionRowElement = null;
        }
        currentReportExceptionData = {};
        if (fixMissingPunchesBtnReports) fixMissingPunchesBtnReports.disabled = true;
    }

    function populateEditPunchModalReports(data) {
        if (!editPunchModalReports || !data) { console.error("Reports.js: Cannot populate reports edit punch modal: missing elements or data."); return; }

        const nameDisplay = document.getElementById('reports_editPunchEmployeeName');
        const scheduleDisplay = document.getElementById('reports_editPunchScheduleInfo');
        const punchIdField = document.getElementById('reports_editPunchIdField');
        const employeeIdField = document.getElementById('reports_editEmployeeIdField');
        const dateField = document.getElementById('reports_editDate');
        const inTimeField = document.getElementById('reports_editInTime');
        const outTimeField = document.getElementById('reports_editOutTime');

        if(nameDisplay) nameDisplay.textContent = data.employeeName || `EID: ${data.globalEid}`;
        if(scheduleDisplay) {
            let scheduleText = `Schedule: ${data.scheduleName || 'N/A'}`;
            if(data.shiftStart && data.shiftEnd && data.shiftStart.toLowerCase() !== 'n/a' && data.shiftEnd.toLowerCase() !== 'n/a'){
                scheduleText += ` (${data.shiftStart} - ${data.shiftEnd})`;
            }
            scheduleDisplay.textContent = scheduleText;
        }
        let formattedDate = '';
        if(data.date){
            try {
                const parts = data.date.split('/');
                if(parts.length === 3){ formattedDate = `${parts[2]}-${parts[0].padStart(2,'0')}-${parts[1].padStart(2,'0')}`; }
            } catch(e){ console.error("Reports.js: Error formatting date for input:", data.date, e); }
        }
        if(punchIdField) punchIdField.value = data.punchId || '';
        if(employeeIdField) employeeIdField.value = data.globalEid || '';
        if(dateField) dateField.value = formattedDate;
        if(inTimeField) inTimeField.value = parseTimeTo24Hour(data.inTime);
        if(outTimeField) outTimeField.value = ''; 

        setTimeout(()=> { if (outTimeField) outTimeField.focus(); }, 150);
    }

    function prepareAndShowEditPunchModalReports(exceptionData) {
        console.log("Reports.js: Preparing Edit Punch Modal for EID:", exceptionData?.globalEid);
        if (!exceptionData || !exceptionData.globalEid) {
            showPageNotification("Cannot edit: Missing Employee ID from selected row.", true); return;
        }
        const globalEid = exceptionData.globalEid;
        const contextPath = (typeof appRootPath === 'string' && appRootPath) ? appRootPath : (window.location.pathname.substring(0, window.location.pathname.indexOf("/",2)) || "");
        const url = `${contextPath}/EmployeeInfoServlet?action=getScheduleInfo&eid=${globalEid}`;

        console.log("Reports.js: Fetching schedule info:", url);
        fetch(url, {cache: 'no-store'})
            .then(response => {
                if (!response.ok) return response.text().then(text => { throw new Error(`Schedule fetch failed: ${response.status}. ${text}`); });
                return response.json();
            })
            .then(scheduleData => {
                if (scheduleData.success) {
                    const finalData = { ...exceptionData, ...scheduleData };
                    populateEditPunchModalReports(finalData);
                    if(editPunchModalReports) showModal(editPunchModalReports);
                } else { throw new Error(scheduleData.message || 'Failed to get schedule data.'); }
            })
            .catch(error => {
                console.error("Reports.js: Error preparing edit punch modal (reports):", error);
                showPageNotification("Could not load details for editing punch: " + error.message, true);
            });
    }

    function handleEditPunchFormSubmitReports(event) {
        event.preventDefault();
        if (!editPunchFormReports) { console.error("Reports.js: editPunchFormReports missing!"); return; }

        const formData = new FormData(editPunchFormReports);
        const formBody = new URLSearchParams(formData);
        console.log("Reports.js: Submitting Edited Punch (Reports):", formBody.toString());

        const submitButton = editPunchFormReports.querySelector('button[type="submit"]');
        if(submitButton) submitButton.disabled = true;

        fetch('AddEditAndDeletePunchesServlet', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body: formBody })
        .then(response => {
            if (!response.ok) {
                return response.json().catch(() => response.text()).then(errorData => {
                    const errorMsg = (typeof errorData === 'object' && errorData.error) ? errorData.error : (typeof errorData === 'string' ? errorData : `Save failed: ${response.status}`);
                    throw new Error(errorMsg);
                });
            }
            return response.json();
        })
        .then(data => {
            console.log("Reports.js: Edit Punch Save Response (Reports):", data);
            if (data.success) {
                hideEditPunchModalReports();
                showPageNotification(data.message || "Punch updated successfully!", false);
                loadReport('exception'); 
            } else {
                showPageNotification("Error saving punch: " + (data.error || "Unknown error."), true);
            }
        })
        .catch(error => {
            console.error("Reports.js: Error saving punch (fetch catch reports):", error);
            showPageNotification("Error saving punch: " + error.message, true);
        })
        .finally(() => { if(submitButton) submitButton.disabled = false; });
    }

    if (reportOutputDiv) {
        reportOutputDiv.addEventListener('click', function(event) {
            const interactiveExceptionTable = event.target.closest('table.exception-report-table-interactive');
            const inactiveTable = event.target.closest('table.inactive-employees-table');

            if (interactiveExceptionTable) {
                const punchRow = event.target.closest('tr[data-punch-id]');
                if (punchRow) {
                    if (selectedReportExceptionRowElement && selectedReportExceptionRowElement !== punchRow) {
                        selectedReportExceptionRowElement.classList.remove('selected');
                    }
                    punchRow.classList.toggle('selected');
                    if (punchRow.classList.contains('selected')) {
                        selectedReportExceptionRowElement = punchRow;
                        const cells = punchRow.cells;
                        currentReportExceptionData = {
                            punchId: punchRow.dataset.punchId,
                            globalEid: punchRow.dataset.eid,
                            employeeName: (cells.length > 2) ? `${decodeHtmlEntities(cells[1].textContent)} ${decodeHtmlEntities(cells[2].textContent)}`.trim() : `EID ${punchRow.dataset.eid}`,
                            date: cells.length > 3 ? cells[3].textContent.trim() : '',
                            inTime: cells.length > 4 ? cells[4].querySelector('span.missing-punch-placeholder') ? '' : cells[4].textContent.trim() : '',
                        };
                        if (fixMissingPunchesBtnReports) fixMissingPunchesBtnReports.disabled = false;
                    } else {
                        selectedReportExceptionRowElement = null;
                        currentReportExceptionData = {};
                        if (fixMissingPunchesBtnReports) fixMissingPunchesBtnReports.disabled = true;
                    }
                }
                return;
            }

            if (inactiveTable) {
                const targetRow = event.target.closest('tr[data-eid]');
                if (targetRow) {
                    const eid = targetRow.dataset.eid;
                    const cells = targetRow.cells;
                    const employeeName = (cells.length > 2) ? `${decodeHtmlEntities(cells[1].textContent)} ${decodeHtmlEntities(cells[2].textContent)}`.trim() : `EID ${eid}`;
                    if (confirm(`Are you sure you want to reactivate employee ${employeeName} (EID: ${eid})?`)) {
                        reactivateEmployee(eid);
                    }
                }
                return;
            }
        });
    } else { console.error("Reports.js: #reportOutputDiv not found for event listeners."); }

    function reactivateEmployee(eid) {
        console.log(`Reports.js: Attempting to reactivate employee EID: ${eid}`);
        const params = new URLSearchParams();
        params.append('action', 'reactivateEmployee');
        params.append('eid', eid);
        showPageNotification("Reactivating employee " + eid + "...", false);

        fetch('AddEditAndDeleteEmployeesServlet', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body: params })
        .then(response => {
             if (response.ok && response.headers.get("Content-Type")?.includes("application/json")) { return response.json(); }
             return response.text().then(text => { throw new Error(`Server error reactivating: ${response.status} - ${text||'No details'}`); });
        })
        .then(data => {
            console.log("Reports.js: Reactivate response:", data);
            if (data.success) {
                showPageNotification(data.message || `Employee ${eid} reactivated successfully.`, false);
                loadReport('inactiveEmployees');
            } else { showPageNotification(`Error reactivating employee: ${data.error || 'Unknown server error.'}`, true); }
        })
        .catch(error => {
            console.error('Reports.js: Error reactivating employee:', error);
            showPageNotification(`Failed to reactivate employee: ${error.message}`, true);
        });
    }

    function printCurrentReport() {
        console.log("Reports.js: printCurrentReport function called.");
        const title = reportTitleElement?.textContent || 'Report';
        const description = reportDescriptionElement?.textContent || '';
        const reportTableElement = reportOutputDiv?.querySelector('#dynamicReportTable');
        if (!reportTableElement) { showPageNotification("No report table content to print.", true); return; }
        const reportTableContainerClone = reportTableElement.closest('.report-table-container')?.cloneNode(true);
        if (!reportTableContainerClone) { showPageNotification("Could not prepare report content for printing.", true); return; }
        const reportsCSSLink = document.querySelector('link[href^="css/reports.css"]');
        const cssVersion = reportsCSSLink ? reportsCSSLink.getAttribute('href').split('v=')[1] || '1' : '1';
        const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
        if (!printWindow) { showPageNotification("Could not open print window. Please check popup blocker settings.", true); return; }
        printWindow.document.write(`<html><head><title>Print - ${decodeHtmlEntities(title)}</title><link rel="stylesheet" href="css/reports.css?v=${cssVersion}"><style>body{margin:20px;background-color:#fff!important;}</style></head><body><h1 style="text-align:center;font-size:16pt;color:#000;">${decodeHtmlEntities(title)}</h1><p style="text-align:center;font-size:10pt;color:#333;">${decodeHtmlEntities(description)}</p>${reportTableContainerClone.outerHTML}</body></html>`);
        printWindow.document.close();
        setTimeout(() => { try { printWindow.focus(); printWindow.print(); } catch (e) { showPageNotification("Printing failed: " + e.message, true); try {printWindow.close();}catch(e2){} } }, 750);
    }


    if (printReportBtn) printReportBtn.addEventListener('click', printCurrentReport);

    const urlParams = new URLSearchParams(window.location.search);
    const initialReport = urlParams.get('report');
    const initialFilterValue = urlParams.get('filterValue');

    if (initialReport && reportHeaders.hasOwnProperty(initialReport)) {
        loadReport(initialReport, initialFilterValue);
    } else {
         if(reportTitleElement) reportTitleElement.textContent = "Select Report";
         if(reportDescriptionElement) reportDescriptionElement.textContent = "Choose a report from the menu.";
         if (reportOutputDiv && reportOutputDiv.innerHTML.trim() === '') {
            reportOutputDiv.innerHTML = '<p class="report-placeholder">Please select a report from the menu.</p>';
         }
         if(loadingIndicator) loadingIndicator.style.display = 'none';
         if(reportActionsDiv) reportActionsDiv.style.display = 'none';
    }
});