/**
 * reports.js
 * Handles fetching and displaying reports dynamically on reports.jsp
 */

document.addEventListener('DOMContentLoaded', function() {
    console.log("Reports JS Loaded");

    const reportOutputDiv = document.getElementById('reportOutput');
    const loadingIndicator = document.getElementById('loadingIndicator');
    const reportTitleElement = document.getElementById('reportTitle');
    const reportDescriptionElement = document.getElementById('reportDescription');
    const reportActionsDiv = document.getElementById('reportActions');
    const printReportBtn = document.getElementById('printReportBtn');
    const reportSpecificFiltersDiv = document.getElementById('reportSpecificFilters');
    
    const tardyReportFiltersHTML = `
        <div id="tardyReportFilters" class="report-filters">
            <label><input type="radio" name="tardyDateRange" value="period"> Current Pay Period</label>
            <label><input type="radio" name="tardyDateRange" value="ytd"> Year-to-Date</label>
            <label><input type="radio" name="tardyDateRange" value="all" checked> All Time</label>
        </div>`;

    const archiveReportFilters = document.getElementById('archiveReportFilters');
    const applyArchiveFilterBtn = document.getElementById('applyArchiveFilterBtn');
    const archiveStartDateInput = document.getElementById('archiveStartDate');
    const archiveEndDateInput = document.getElementById('archiveEndDate');
    
    const reactivateEmployeeBtn = document.getElementById('reactivateEmployeeBtn');
    let selectedInactiveRowElement = null;

    const upgradePlanModal = document.getElementById('upgradePlanModal');
    const upgradePlanMessage = document.getElementById('upgradePlanModalMessage');

    const fixMissingPunchesBtnReports = document.getElementById('fixMissingPunchesBtnReports');
    const editPunchModalReports = document.getElementById('editPunchModalReports');
    const editPunchFormReports = document.getElementById('editPunchFormReports');

    let selectedReportExceptionRowElement = null;
    let currentReportExceptionData = {};

    const { hideModal, showModal, showPageNotification, decodeHtmlEntities, parseTimeTo24Hour, applyDefaultSort, makeTableSortable } = window;

    if (editPunchFormReports) {
        editPunchFormReports.addEventListener('submit', handleEditPunchFormSubmitReports);
    }

    // Add cancel button event listener for reports edit modal
    document.querySelector('#editPunchModalReports .cancel-btn')?.addEventListener('click', () => {
        hideEditPunchModalReports();
    });

    if (fixMissingPunchesBtnReports) {
        fixMissingPunchesBtnReports.addEventListener('click', () => {
            if (!selectedReportExceptionRowElement || !currentReportExceptionData.globalEid) {
                showPageNotification("Please select an exception row to edit.", 'error');
                return;
            }
            prepareAndShowEditPunchModalReports(currentReportExceptionData);
        });
    }
    
    const reportDescriptions = {
        exception: "Shows punch records with missing OUT times within the current pay period. To edit, click a row to select, then click 'Fix Missing Punches'.",
        tardy: "Summarizes employees' accumulated tardies (late punches or early outs) based on schedule data for the selected date range.",
        whosin: "Lists employees who are currently clocked IN.",
        activeEmployees: "Lists all currently active employees with key contact and assignment information.",
        inactiveEmployees: "Lists employees marked as inactive. Select a row and click 'Reactivate Employee' to restore their account.",
        archivedPunches: "Search for historical punch records within a specific date range.",
        employeesByDept: "Lists active employees filtered by the selected department.",
        employeesBySched: "Lists active employees filtered by the selected schedule.",
        employeesBySup: "Lists active employees filtered by the selected supervisor.",
        accrualBalance: "Displays current vacation, sick, and personal time balances for all active employees.",
        systemAccess: "Lists all active employees with 'Administrator' permissions for security auditing."
    };

    const employeeReportHeaders = `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="string">Department</th><th class="sortable" data-sort-type="string">Schedule</th><th class="sortable" data-sort-type="string">Supervisor</th><th class="sortable" data-sort-type="string">Email</th></tr></thead>`;
    
    const reportHeaders = {
         exception: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="date">Date</th><th class="sortable" data-sort-type="string">IN Time</th><th class="sortable" data-sort-type="string">OUT Time</th></tr></thead>`,
         tardy: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="number">Late Count</th><th class="sortable" data-sort-type="number">Early Out Count</th></tr></thead>`,
         whosin: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="string">Department</th><th class="sortable" data-sort-type="string">Schedule</th><th class="sortable" data-sort-type="string">Email</th></tr></thead>`,
         activeEmployees: employeeReportHeaders,
         inactiveEmployees: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="string">Department</th><th class="sortable" data-sort-type="string">Schedule</th><th class="sortable" data-sort-type="string">Email</th><th class="sortable" data-sort-type="date">Date Deactivated</th><th class="sortable" data-sort-type="string">Reason</th></tr></thead>`,
         employeesByDept: employeeReportHeaders,
         employeesBySched: employeeReportHeaders,
         employeesBySup: employeeReportHeaders,
         archivedPunches: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">Employee Name</th><th class="sortable" data-sort-type="date">Date</th><th class="sortable" data-sort-type="string">IN</th><th class="sortable" data-sort-type="string">OUT</th><th class="sortable" data-sort-type="number">Total</th><th class="sortable" data-sort-type="string">Type</th></tr></thead>`,
         accrualBalance: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="number">Vacation Hours</th><th class="sortable" data-sort-type="number">Sick Hours</th><th class="sortable" data-sort-type="number">Personal Hours</th></tr></thead>`,
         systemAccess: `<thead><tr><th class="sortable" data-sort-type="number">EID</th><th class="sortable" data-sort-type="string">First Name</th><th class="sortable" data-sort-type="string">Last Name</th><th class="sortable" data-sort-type="string">Email</th></tr></thead>`
    };

    /**
     * Finds cells marked by the server as empty and updates their text content.
     */
    function highlightMissingPunches() {
        const reportTable = reportOutputDiv.querySelector('.report-table');
        if (!reportTable) return;
        const emptyCells = reportTable.querySelectorAll('tbody td.empty-cell');
        emptyCells.forEach(cell => {
            cell.textContent = 'Missing Punch';
        });
    }

    window.loadReport = function(reportType, filterValue = null) {
        if (loadingIndicator) loadingIndicator.style.display = 'flex';
        if (reportActionsDiv) reportActionsDiv.style.display = 'none';
        if (fixMissingPunchesBtnReports) fixMissingPunchesBtnReports.style.display = 'none';
        if (reactivateEmployeeBtn) reactivateEmployeeBtn.style.display = 'none';
        if (reportOutputDiv) reportOutputDiv.innerHTML = '';
        
        if (reportSpecificFiltersDiv) reportSpecificFiltersDiv.innerHTML = '';
        if (archiveReportFilters) archiveReportFilters.style.display = 'none';

        if (reportType === 'tardy') {
            if (reportActionsDiv) reportActionsDiv.style.display = 'flex';
            if (reportSpecificFiltersDiv) {
                reportSpecificFiltersDiv.innerHTML = tardyReportFiltersHTML;
            }
            const tardyRadioButtons = document.querySelectorAll('input[name="tardyDateRange"]');
            tardyRadioButtons.forEach(radio => {
                radio.addEventListener('change', function() { if (this.checked) window.loadReport('tardy', this.value); });
            });
            const radioToCheck = document.querySelector(`input[name="tardyDateRange"][value="${filterValue || 'all'}"]`);
            if (radioToCheck) radioToCheck.checked = true;

        } else if (reportType === 'archivedPunches') {
            if (archiveReportFilters) archiveReportFilters.style.display = 'flex';
            if (reportActionsDiv) reportActionsDiv.style.display = 'flex';
            if (printReportBtn) printReportBtn.style.display = 'none';
            if (loadingIndicator) loadingIndicator.style.display = 'none';
            if (reportOutputDiv) reportOutputDiv.innerHTML = `<p class="report-placeholder">Select a date range and click 'Apply' to view archived punches.</p>`;
            if(reportTitleElement) reportTitleElement.textContent = "Archived Punches Report";
            if(reportDescriptionElement) reportDescriptionElement.textContent = reportDescriptions.archivedPunches;
            return; 
        }

        const reportName = reportType.replace(/([A-Z])/g, ' $1').trim();
        let titleText = `${reportName.charAt(0).toUpperCase() + reportName.slice(1)} Report`;
        
        if (reportType === 'accrualBalance') {
            titleText = 'PTO Balance Report';
        }
        
        if (reportType === 'tardy') {
            const range = filterValue || 'all';
            let rangeText = ' (All Time)';
            if (range === 'ytd') rangeText = ' (Year-to-Date)';
            if (range === 'period') rangeText = ' (Current Pay Period)';
            titleText += rangeText;
        } else if (filterValue) {
             try { filterValue = decodeURIComponent(filterValue); } catch(e) {}
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
        .then(response => response.json())
        .then(data => {
            if (loadingIndicator) loadingIndicator.style.display = 'none';
            if (data.success) {
                if (data.html && data.html !== 'NO_EXCEPTIONS') {
                    const tableHeadersHTML = reportHeaders[reportType];
                    const tableClass = reportType === 'exception' ? 'report-table exception-report-table-interactive' : 'report-table';
                    const tableId = reportType === 'inactiveEmployees' ? 'inactiveEmployeesTable' : 'dynamicReportTable';
                    reportOutputDiv.innerHTML = `<div class="table-container report-table-container"><table class="${tableClass}" id="${tableId}">${tableHeadersHTML}<tbody>${data.html}</tbody></table></div>`;
                    
                    if (reportType === 'exception') {
                        highlightMissingPunches();
                    }

                    const reportTable = document.getElementById(tableId);
                    if (reportTable && typeof makeTableSortable === 'function') {
                        makeTableSortable(reportTable);
                        if (typeof applyDefaultSort === 'function') {
                            applyDefaultSort(reportTable);
                        }
                    }

                    if (reportActionsDiv) reportActionsDiv.style.display = 'flex';
                    if (printReportBtn) printReportBtn.style.display = 'inline-flex';
                    if (reportType === 'exception' && fixMissingPunchesBtnReports) {
                        fixMissingPunchesBtnReports.style.display = 'inline-flex';
                        fixMissingPunchesBtnReports.disabled = true;
                    }
                    if (reportType === 'inactiveEmployees' && reactivateEmployeeBtn) {
                        reactivateEmployeeBtn.style.display = 'inline-flex';
                        reactivateEmployeeBtn.disabled = true;
                    }

                } else {
                    reportOutputDiv.innerHTML = `<p class="report-message-row">${data.message || 'No records found.'}</p>`;
                }
            } else {
                reportOutputDiv.innerHTML = `<p class="report-error-row">Error: ${data.message || 'Unknown server error.'}</p>`;
            }
        })
        .catch(error => {
            if (loadingIndicator) loadingIndicator.style.display = 'none';
            reportOutputDiv.innerHTML = `<p class="report-error-row">Failed to load report: ${error.message}</p>`;
        });
    }

    function loadArchivedPunchesReport() {
        const startDate = archiveStartDateInput.value;
        const endDate = archiveEndDateInput.value;

        if (!startDate || !endDate) { showPageNotification("Please select both a 'From' and 'To' date.", 'error'); return; }
        if (new Date(startDate) > new Date(endDate)) { showPageNotification("'From' date cannot be after 'To' date.", 'error'); return; }

        const params = new URLSearchParams({ reportType: 'archivedPunches', startDate, endDate });
        
        loadingIndicator.style.display = 'flex';
        reportOutputDiv.innerHTML = '';
        if (printReportBtn) printReportBtn.style.display = 'none';
        
        fetch('ReportServlet', { method: 'POST', body: params })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    if (data.html) {
                        const tableHeadersHTML = reportHeaders['archivedPunches'];
                        reportOutputDiv.innerHTML = `<div class="table-container report-table-container"><table class="report-table" id="dynamicReportTable">${tableHeadersHTML}<tbody>${data.html}</tbody></table></div>`;
                        const reportTable = document.getElementById('dynamicReportTable');
                        if (reportTable && typeof makeTableSortable === 'function') {
                            makeTableSortable(reportTable);
                            if (typeof applyDefaultSort === 'function') {
                                applyDefaultSort(reportTable);
                            }
                        }
                        if (printReportBtn) printReportBtn.style.display = 'inline-flex';
                    } else {
                        reportOutputDiv.innerHTML = `<p class="report-message-row">${data.message || 'No records found.'}</p>`;
                    }
                } else {
                    reportOutputDiv.innerHTML = `<p class="report-error-row">Error: ${data.message || 'Unknown error.'}</p>`;
                }
            })
            .catch(error => {
                reportOutputDiv.innerHTML = `<p class="report-error-row">Failed to load report: ${error.message}</p>`;
            })
            .finally(() => {
                loadingIndicator.style.display = 'none';
            });
    }

    if (applyArchiveFilterBtn) {
        applyArchiveFilterBtn.addEventListener('click', loadArchivedPunchesReport);
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
            let scheduleText = `${data.scheduleName || 'N/A'}`;
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
        if(dateField) {
            if (window.PAY_PERIOD_START && window.PAY_PERIOD_END) {
                dateField.min = window.PAY_PERIOD_START;
                dateField.max = window.PAY_PERIOD_END;
            }
            dateField.value = formattedDate;
        }
        if(inTimeField) inTimeField.value = parseTimeTo24Hour(data.inTime);
        if(outTimeField) outTimeField.value = ''; 
        setTimeout(()=> { if (outTimeField) outTimeField.focus(); }, 150);
    }

    function prepareAndShowEditPunchModalReports(exceptionData) {
        if (!exceptionData || !exceptionData.globalEid) {
            showPageNotification("Cannot edit: Missing Employee ID from selected row.", 'error'); return;
        }
        const globalEid = exceptionData.globalEid;
        const contextPath = (typeof appRootPath === 'string' && appRootPath) ? appRootPath : (window.location.pathname.substring(0, window.location.pathname.indexOf("/",2)) || "");
        const url = `${contextPath}/EmployeeInfoServlet?action=getScheduleInfo&eid=${globalEid}`;
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
                showPageNotification("Could not load details for editing punch: " + error.message, 'error');
            });
    }

    function handleEditPunchFormSubmitReports(event) {
        event.preventDefault();
        if (!editPunchFormReports) { return; }
        const formData = new FormData(editPunchFormReports);
        const formBody = new URLSearchParams(formData);
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
            if (data.success) {
                hideEditPunchModalReports();
                showPageNotification(data.message || "Punch updated successfully!", 'success', () => loadReport('exception'));
            } else {
                showPageNotification("Error saving punch: " + (data.error || "Unknown error."), 'error');
            }
        })
        .catch(error => {
            showPageNotification("Error saving punch: " + error.message, 'error');
        })
        .finally(() => { if(submitButton) submitButton.disabled = false; });
    }

    if (reportOutputDiv) {
        reportOutputDiv.addEventListener('click', function(event) {
            const inactiveTable = document.getElementById('inactiveEmployeesTable');
            const interactiveExceptionTable = event.target.closest('table.exception-report-table-interactive');

            if (inactiveTable && inactiveTable.contains(event.target)) {
                const targetRow = event.target.closest('tr[data-eid]');
                if (targetRow) {
                    if (selectedInactiveRowElement && selectedInactiveRowElement !== targetRow) {
                        selectedInactiveRowElement.classList.remove('selected');
                    }
                    targetRow.classList.toggle('selected');
                    if (targetRow.classList.contains('selected')) {
                        selectedInactiveRowElement = targetRow;
                        if (reactivateEmployeeBtn) reactivateEmployeeBtn.disabled = false;
                    } else {
                        selectedInactiveRowElement = null;
                        if (reactivateEmployeeBtn) reactivateEmployeeBtn.disabled = true;
                    }
                }
                return;
            }

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
        });
    }

    function reactivateEmployee(eid) {
        const params = new URLSearchParams({ action: 'reactivateEmployee', eid: eid });
        fetch('AddEditAndDeleteEmployeesServlet', { 
            method: 'POST', 
            headers: { 
                'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
                'X-Requested-With': 'XMLHttpRequest'
            }, 
            body: params 
        })
        .then(response => {
             const contentType = response.headers.get("content-type");
             if (response.ok && contentType && contentType.indexOf("application/json") !== -1) {
                 return response.json();
             }
             if ((response.status === 403 || response.status === 401) && contentType && contentType.indexOf("application/json") !== -1) {
                 return response.json().then(errData => { throw errData; });
             }
             return response.text().then(text => { 
                 throw new Error("Server response was not in the expected format. Your session may have expired.");
             });
        })
        .then(data => {
            if (data.success) {
                showPageNotification(data.message || `Employee ${eid} reactivated successfully.`, 'success', () => loadReport('inactiveEmployees'));
            } else { 
                throw new Error(data.error || 'Unknown server error.'); 
            }
        })
        .catch(error => {
            if (error.error === 'user_limit_exceeded') {
                if (upgradePlanMessage) upgradePlanMessage.textContent = error.message;
                showModal(upgradePlanModal);
            } else if (error.sessionExpired) {
                window.location.href = 'login.jsp?error=Session+Expired';
            }
            else {
                console.error('Reports.js: Error reactivating employee:', error);
                showPageNotification(`Failed to reactivate employee: ${error.message}`, 'error');
            }
        });
    }
    
    if (reactivateEmployeeBtn) {
        reactivateEmployeeBtn.addEventListener('click', function() {
            if (selectedInactiveRowElement && selectedInactiveRowElement.dataset.eid) {
                const eid = selectedInactiveRowElement.dataset.eid;
                reactivateEmployee(eid);
            } else {
                showPageNotification("Please select an employee to reactivate.", 'error');
            }
        });
    }

    function printCurrentReport() {
        const title = reportTitleElement?.textContent || 'Report';
        const description = reportDescriptionElement?.textContent || '';
        const reportTableElement = reportOutputDiv?.querySelector('.report-table');
        if (!reportTableElement) { showPageNotification("No report table content to print.", 'error'); return; }
        
        const commonCSSLink = document.querySelector('link[href^="css/common.css"]');
        const cssPath = commonCSSLink ? commonCSSLink.href : 'css/common.css';
        
        const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
        if (!printWindow) { showPageNotification("Could not open print window. Please check popup blocker settings.", 'error'); return; }

        printWindow.document.write('<html><head><title>Print - ' + decodeHtmlEntities(title) + '</title>');
        printWindow.document.write('<link rel="stylesheet" href="' + cssPath + '">');
        printWindow.document.write(`
            <style>
                body { margin: 20px; background-color: #fff !important; }
                .table-container { max-height: none !important; overflow-y: visible !important; border: 1px solid #ccc !important; }
                table.report-table { font-size: 9pt; }
                table.report-table thead { display: table-header-group; }
                table.report-table tbody tr { page-break-inside: avoid; }
                .main-navbar, .report-actions, .filter-form, #button-container, .report-filters, #archiveReportFilters { display: none !important; }
            </style>
        `);
        printWindow.document.write('</head><body>');
        printWindow.document.write('<h1 style="text-align:center;font-size:16pt;color:#000;">' + decodeHtmlEntities(title) + '</h1>');
        printWindow.document.write('<p style="text-align:center;font-size:10pt;color:#333;">' + decodeHtmlEntities(description) + '</p>');
        printWindow.document.write('<div class="table-container report-table-container">' + reportTableElement.outerHTML + '</div>');
        printWindow.document.write('</body></html>');
        printWindow.document.close();

        setTimeout(() => {
            try {
                printWindow.focus();
                printWindow.print();
            } catch (e) {
                showPageNotification("Printing failed: " + e.message, 'error');
                try { printWindow.close(); } catch (e2) {}
            }
        }, 750);
    }

    if (printReportBtn) printReportBtn.addEventListener('click', printCurrentReport);
    
    const urlParams = new URLSearchParams(window.location.search);
    const initialReport = urlParams.get('report');
    const initialFilterValue = urlParams.get('filterValue');
    
    if (initialReport && reportHeaders.hasOwnProperty(initialReport)) {
        window.loadReport(initialReport, initialFilterValue);
    } else {
         if(reportTitleElement) reportTitleElement.textContent = "Select Report";
         if(reportDescriptionElement) reportDescriptionElement.textContent = "Choose a report from the 'Reports' menu in the navigation bar.";
         if (reportOutputDiv && reportOutputDiv.innerHTML.trim() === '') {
            reportOutputDiv.innerHTML = '<p class="report-placeholder">Please select a report from the menu.</p>';
         }
         if(loadingIndicator) loadingIndicator.style.display = 'none';
         if(reportActionsDiv) reportActionsDiv.style.display = 'none';
    }
});