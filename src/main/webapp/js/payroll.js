// js/payroll.js

document.addEventListener('DOMContentLoaded', function() {

    const _showModal = window.showModal;
    const _hideModal = window.hideModal;
    const _decode = window.decodeHtmlEntities;
    const appRoot = window.appRootPath;

    // --- Page Element Selectors ---
    const reportModal = document.getElementById('exceptionReportModal');
    const editPunchModal = document.getElementById('editPunchModal');
    const reportTbody = document.getElementById('exceptionReportTbody');
    const btnEditReportRow = document.getElementById('editExceptionButton');
    const editPunchForm = document.getElementById('editPunchForm');
    const actualClosePayPeriodButton = document.getElementById('btnClosePayPeriodActual');
    const closePayPeriodFormElement = document.getElementById('closePayPeriodForm');
    const btnPrintPayroll = document.getElementById('btnPrintPayroll');
    const btnPrintAllTimeCards = document.getElementById('btnPrintAllTimeCards');
    const btnExportPayrollElement = document.getElementById('btnExportPayroll');
    const btnShowReport = document.getElementById('btnExceptionReport');
    const closePeriodConfirmModal = document.getElementById('closePeriodConfirmModal');
    const confirmModalTitle = document.getElementById('confirmModalTitle');
    const confirmModalMessage = document.getElementById('confirmModalMessage');
    const confirmModalOkBtn = document.getElementById('confirmModalOkBtn');
    const confirmModalCancelBtn = document.getElementById('confirmModalCancelBtn');

    // --- State ---
    let selectedReportRowElement = null;
    let currentExceptionData = {};
    const mainActionButtons = [btnExportPayrollElement, btnPrintPayroll, btnPrintAllTimeCards, actualClosePayPeriodButton];
    let notificationCallback = null;

    // --- Core Functions ---
    function hideExceptionReportModal() {
        if(reportModal) _hideModal(reportModal);
        if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
        if(btnEditReportRow) btnEditReportRow.disabled = true;
        selectedReportRowElement = null; currentExceptionData = {};
    }

    function refreshExceptionReport() {
        if (!reportTbody || !reportModal || !btnEditReportRow) {
            window.showPageNotification("Error: Required page elements missing for Exception Report.", 'error', null, "Error");
            return;
        }
        if(selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
        selectedReportRowElement = null; currentExceptionData = {};
        btnEditReportRow.disabled = true;
        reportTbody.innerHTML='<tr><td colspan="6" style="text-align:center; padding: 20px;">Loading... <i class="fas fa-spinner fa-spin"></i></td></tr>';
        mainActionButtons.forEach(b => { if(b && b.id !== 'btnExceptionReport') b.disabled = true; });
        
        fetch(`${appRoot}/PayrollServlet`, { method:'POST', headers:{'Content-Type':'application/x-www-form-urlencoded'}, body: new URLSearchParams({'action':'exceptionReport'}) })
        .then(response => response.ok ? response.text() : response.text().then(text => Promise.reject(new Error(text))))
        .then(htmlResponse => {
            if (htmlResponse.trim().toUpperCase() === "NO_EXCEPTIONS") {
                hideExceptionReportModal();
                const instructionsPara = document.querySelector('p.instructions');
                if(instructionsPara) instructionsPara.innerHTML = "<strong>Good News!</strong> No exceptions were found. All payroll actions are now enabled.";
                window.showPageNotification("No Exceptions found. Payroll actions are now enabled.", 'success', null, "Exceptions Cleared");
                mainActionButtons.forEach(button => { if(button) button.disabled = false; });
            } else {
                reportTbody.innerHTML = htmlResponse;
                _showModal(reportModal);
            }
        })
        .catch(error => {
            hideExceptionReportModal();
            window.showPageNotification("Error loading exception report: " + error.message, 'error', null, "Error");
            mainActionButtons.forEach(button => { if(button && button.id !== 'btnExceptionReport') button.disabled = true; });
        });
    }
    
    function hideEditPunchModal() {
        if(editPunchModal) _hideModal(editPunchModal);
        if(selectedReportRowElement){ selectedReportRowElement.classList.remove('selected'); selectedReportRowElement = null; }
        currentExceptionData = {};
        if(btnEditReportRow) btnEditReportRow.disabled = true;
    }

    function populateEditPunchModal(data) {
        if(!editPunchModal || !data) return;
        document.getElementById('editPunchEmployeeName').textContent = data.employeeName || `EID: ${data.globalEid || 'N/A'}`;
        let scheduleText = `${data.scheduleName || 'N/A'}`;
        if(data.shiftStart && data.shiftEnd) scheduleText += ` (${data.shiftStart} - ${data.shiftEnd})`;
        document.getElementById('editPunchScheduleInfo').textContent = scheduleText;
        
        let formattedDate = '';
        if(data.date) {
            const parts = String(data.date).split('/'); 
            if(parts.length === 3) formattedDate = `${parts[2]}-${parts[0].padStart(2,'0')}-${parts[1].padStart(2,'0')}`;
        }

        editPunchForm.querySelector('#editPunchIdField').value = data.punchId || '';
        editPunchForm.querySelector('#editPunchEmployeeIdField').value = data.globalEid || '';
        editPunchForm.querySelector('#editUserTimeZone').value = window.effectiveTimeZoneIdJs;
        const dateField = editPunchForm.querySelector('#editDate');
        if (dateField) {
            if (window.PAY_PERIOD_START && window.PAY_PERIOD_END) {
                dateField.min = window.PAY_PERIOD_START;
                dateField.max = window.PAY_PERIOD_END;
            }
            dateField.value = formattedDate;
        }
        editPunchForm.querySelector('#editInTime').value = data.inTime ? window.parseTimeTo24Hour(data.inTime) : '';
        editPunchForm.querySelector('#editOutTime').value = data.outTime ? window.parseTimeTo24Hour(data.outTime) : '';
    }

    function prepareAndShowEditPunchModal(exceptionData) {
        if (!exceptionData || !exceptionData.globalEid){ window.showPageNotification("Cannot edit: Missing Employee ID.", 'error'); return; }
        fetch(`${appRoot}/EmployeeInfoServlet?action=getScheduleInfo&eid=${exceptionData.globalEid}`)
            .then(response => response.json())
            .then(scheduleData => {
                if (scheduleData.success) {
                    populateEditPunchModal({ ...exceptionData, ...scheduleData });
                    _showModal(editPunchModal);
                } else { throw new Error(scheduleData.error || 'Failed to get schedule data.'); }
            })
            .catch(error => { window.showPageNotification("Could not load details for editing: " + error.message, 'error'); });
    }

    function handleEditPunchFormSubmit(event) {
        event.preventDefault();
        if (!editPunchForm.querySelector('#editInTime').value || !editPunchForm.querySelector('#editOutTime').value) {
            window.showPageNotification("To fix an exception, both IN and OUT times are required.", 'error', null, "Validation Error");
            return;
        }
        const formData = new FormData(editPunchForm);
        fetch(`${appRoot}/AddEditAndDeletePunchesServlet`, { method: 'POST', body: new URLSearchParams(formData) })
        .then(response => response.json())
        .then(data => {
            if (data.success) {
                hideEditPunchModal();
                window.showPageNotification(data.message || "Punch updated successfully!", 'success', refreshExceptionReport, "Success");
            } else { window.showPageNotification("Error saving punch: " + (data.error || "Unknown error."), 'error'); }
        })
        .catch(error => { window.showPageNotification("Network error saving punch: " + error.message, 'error'); });
    }
    
    // --- EVENT LISTENERS ---
    btnShowReport?.addEventListener('click', refreshExceptionReport);
    btnEditReportRow?.addEventListener('click', () => {
        if(btnEditReportRow.disabled) return;
        prepareAndShowEditPunchModal(currentExceptionData);
    });
    editPunchForm?.addEventListener('submit', handleEditPunchFormSubmit);
    
    document.getElementById('closeExceptionReportButton')?.addEventListener('click', hideExceptionReportModal);
    document.getElementById('cancelEditPunch')?.addEventListener('click', hideEditPunchModal);

    reportTbody?.addEventListener('click', (event) => {
        const punchRow = event.target.closest('tr[data-punch-id]');
        if (punchRow) {
            if (selectedReportRowElement) selectedReportRowElement.classList.remove('selected');
            punchRow.classList.add('selected');
            selectedReportRowElement = punchRow;
            currentExceptionData = {
                punchId: punchRow.dataset.punchId, globalEid: punchRow.dataset.eid,
                employeeName: `${_decode(punchRow.cells[1].textContent)} ${_decode(punchRow.cells[2].textContent)}`.trim(),
                date: punchRow.cells[3].textContent.trim(), inTime: punchRow.cells[4].textContent.trim(), outTime: punchRow.cells[5].textContent.trim()
            };
            btnEditReportRow.disabled = false;
        }
    });

    actualClosePayPeriodButton?.addEventListener('click', function() {
        const today = new Date(); today.setHours(0, 0, 0, 0);
        const parts = window.payPeriodEndDateJs.split('-');
        const periodEnd = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
        
        let title, message, buttonText;
        if (periodEnd >= today) {
            title = "Close Pay Period Early?";
            message = `The pay period has not ended. Closing now will finalize all entries. This is usually done only for pay cycle changes.<br><br>Are you sure?`;
            buttonText = "Close Early";
        } else {
            title = "Confirm Close Pay Period";
            message = `This will finalize overtime, archive punches, and run accruals. This action cannot be easily undone.<br><br>Are you sure you want to close the period?`;
            buttonText = "Confirm Close";
        }
        confirmModalTitle.querySelector('span').textContent = title;
        confirmModalMessage.innerHTML = message;
        confirmModalOkBtn.textContent = buttonText;
        _showModal(closePeriodConfirmModal);
    });

    confirmModalOkBtn?.addEventListener('click', () => closePayPeriodFormElement.submit());
    confirmModalCancelBtn?.addEventListener('click', () => _hideModal(closePeriodConfirmModal));

    btnPrintPayroll?.addEventListener('click', () => {
        const tableToPrint = document.getElementById('payrollTable');
        const pageTitle = 'Payroll Summary';
        const periodHeader = document.getElementById('payPeriodHeader');
        
        if (!tableToPrint) {
            window.showPageNotification('No payroll data to print.', 'error');
            return;
        }
        
        const printWindow = window.open('', '_blank', 'width=1100,height=850,scrollbars=yes,resizable=yes');
        if (!printWindow) {
            window.showPageNotification('Could not open print window. Check popup blockers.', 'error');
            return;
        }
        
        printWindow.document.write(`<html><head><title>Print - ${pageTitle}</title><style>
            body { margin: 20px; background-color: #fff !important; font-size: 9pt; color: #000; }
            h1 { font-size: 16pt; text-align: center; margin-bottom: 10px; color: #000; }
            h2 { font-size: 12pt; text-align: center; margin-bottom: 20px; color: #000; }
            .table-container { border: 1px solid #999 !important; max-height: none !important; overflow: visible !important; }
            table { width: 100% !important; border-collapse: collapse !important; font-size: 8pt; }
            th, td { border: 1px solid #bbb !important; padding: 4px 6px !important; color: #000 !important; background-color: #fff !important; }
            thead th { background-color: #e9ecef !important; font-weight: bold; text-align: center !important; }
            tfoot td { background-color: #f8f9fa !important; font-weight: bold; }
            @media print { @page { size: landscape; margin: 0.5in; } body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } }
        </style></head><body>`);
        
        printWindow.document.write(`<h1>${pageTitle}</h1>`);
        if (periodHeader) printWindow.document.write(`<h2>${periodHeader.textContent}</h2>`);
        printWindow.document.write(`<div class="table-container">${tableToPrint.outerHTML}</div>`);
        printWindow.document.write(`</body></html>`);
        printWindow.document.close();
        
        setTimeout(() => {
            try {
                printWindow.focus();
                printWindow.print();
            } catch (e) {
                window.showPageNotification('Printing failed: ' + e.message, 'error');
                try { printWindow.close(); } catch (e2) {}
            }
        }, 750);
    });
    btnPrintAllTimeCards?.addEventListener('click', () => window.open(`${appRoot}/PrintTimecardsServlet?filterType=all`, '_blank'));
    
    document.getElementById('btnAddHolidayPTO')?.addEventListener('click', () => {
        let url = `${appRoot}/add_global_data.jsp`;
        if (window.PAY_PERIOD_START && window.PAY_PERIOD_END) {
            url += `?startDate=${window.PAY_PERIOD_START}&endDate=${window.PAY_PERIOD_END}`;
        }
        window.location.href = url;
    });

    // Add click listener to payroll table rows
    document.querySelector('#payrollTable tbody')?.addEventListener('click', (event) => {
        const row = event.target.closest('tr');
        if (!row) return;
        
        const cells = row.cells;
        if (!cells || cells.length < 3) return;
        
        const empId = cells[0].textContent.trim();
        const firstName = cells[1].textContent.trim();
        const lastName = cells[2].textContent.trim();
        
        if (empId && firstName && lastName) {
            const url = `${appRoot}/punches.jsp?eid=${empId}&hideNav=true`;
            const modal = document.createElement('div');
            modal.style.cssText = 'position:fixed;top:0;left:0;width:100%;height:100%;background:rgba(0,0,0,0.8);z-index:10000;display:flex;align-items:center;justify-content:center;';
            modal.innerHTML = `<div style="width:1200px;max-width:90%;height:800px;max-height:85vh;background:#fff;border-radius:8px;position:relative;display:flex;flex-direction:column;box-shadow:0 4px 20px rgba(0,0,0,0.3);"><button onclick="this.closest('div[style*=fixed]').remove();window.location.reload();" style="position:absolute;top:10px;right:10px;z-index:1;background:#dc3545;color:#fff;border:none;border-radius:4px;padding:8px 16px;cursor:pointer;font-size:16px;font-weight:bold;">✕ Close</button><iframe src="${url}" style="width:100%;height:100%;border:none;border-radius:8px;"></iframe></div>`;
            document.body.appendChild(modal);
            return;
            
            const width = 1200, height = 800;
            const left = (screen.width - width) / 2;
            const top = (screen.height - height) / 2;
            const windowFeatures = `width=${width},height=${height},left=${left},top=${top},scrollbars=yes,resizable=yes,toolbar=no,menubar=no,location=no,status=no`;
            const popup = window.open(url, '_blank', windowFeatures);
            
            // Add auto-close timer and refresh on close
            if (popup) {
                let inactivityTimer;
                const INACTIVITY_TIMEOUT = 60000; // 1 minute
                
                const resetTimer = () => {
                    clearTimeout(inactivityTimer);
                    inactivityTimer = setTimeout(() => {
                        if (popup && !popup.closed) {
                            popup.close();
                        }
                    }, INACTIVITY_TIMEOUT);
                };
                
                // Start the timer
                resetTimer();
                
                // Check for popup close and refresh payroll
                const checkClosed = setInterval(() => {
                    if (popup.closed) {
                        clearInterval(checkClosed);
                        clearTimeout(inactivityTimer);
                        // Refresh the payroll page
                        window.location.reload();
                    }
                }, 1000);
                
                // Reset timer on popup activity
                const setupActivityListeners = () => {
                    try {
                        const popupDoc = popup.document;
                        const events = ['mousedown', 'mousemove', 'keypress', 'scroll', 'touchstart', 'click', 'keydown', 'input', 'change', 'focus', 'blur'];
                        events.forEach(event => {
                            popupDoc.addEventListener(event, resetTimer, { passive: true, capture: true });
                        });
                        // Also listen on popup window
                        popup.addEventListener('focus', resetTimer);
                        popup.addEventListener('blur', resetTimer);
                        
                        // Additional listeners for form interactions
                        const forms = popupDoc.querySelectorAll('form');
                        forms.forEach(form => {
                            form.addEventListener('submit', resetTimer);
                        });
                        
                        // Listen for modal show/hide events
                        const modals = popupDoc.querySelectorAll('.modal');
                        modals.forEach(modal => {
                            const observer = new MutationObserver(resetTimer);
                            observer.observe(modal, { attributes: true, attributeFilter: ['style', 'class'] });
                        });
                        
                    } catch (e) {
                        // Cross-origin restrictions - fallback to focus events only
                        popup.addEventListener('focus', resetTimer);
                        popup.addEventListener('blur', resetTimer);
                    }
                };
                
                // Set up listeners when popup loads
                popup.addEventListener('load', () => {
                    setupActivityListeners();
                    resetTimer(); // Reset timer when page loads
                });
                
                // Try to set up listeners immediately and periodically
                const trySetup = () => {
                    try {
                        if (popup.document && popup.document.readyState === 'complete') {
                            setupActivityListeners();
                            resetTimer();
                        } else {
                            setTimeout(trySetup, 100);
                        }
                    } catch (e) {
                        // Ignore cross-origin errors
                    }
                };
                trySetup();
            }
        }
    });
    
    // --- INITIALIZATION ---
    mainActionButtons.forEach(button => { if(button) button.disabled = true; });
    if(btnShowReport) btnShowReport.disabled = false;
    
    const successDiv = document.getElementById('pageNotificationDiv_Success_Payroll');
    const errorDiv = document.getElementById('pageNotificationDiv_Error_Payroll');

    if (successDiv && successDiv.textContent.trim()) {
        window.showPageNotification(successDiv.innerHTML, 'success', null, "Success");
    } else if (errorDiv && errorDiv.textContent.trim()) {
        window.showPageNotification(errorDiv.innerHTML, 'error', null, "Error");
    }
});