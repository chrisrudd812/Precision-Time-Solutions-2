// js/commonUtils.js

let currentMaxZIndex = 10080;

/**
 * Parses a 12-hour time string (e.g., "05:30:00 PM") into a 24-hour format ("17:30:00").
 * @param {string} timeStr12hr The 12-hour format time string.
 * @returns {string} The time in 24-hour format, or an empty string if parsing fails.
 */
function parseTimeTo24Hour(timeStr12hr) {
    if (!timeStr12hr || String(timeStr12hr).trim() === '' || String(timeStr12hr).toLowerCase().includes('missing') || String(timeStr12hr).toLowerCase() === "n/a") return '';
    timeStr12hr = String(timeStr12hr).trim();
    try {
        if (/^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/.test(timeStr12hr)) {
            return timeStr12hr.length === 5 ? timeStr12hr + ':00' : timeStr12hr;
        }
        const parts = timeStr12hr.match(/(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)/i);
        if (!parts) {
            console.warn("commonUtils.js (parseTimeTo24Hour): Could not parse time string:", timeStr12hr);
            return '';
        }
        let h = parseInt(parts[1],10);
        const m = parts[2];
        const s = parts[3] ? parts[3] : '00';
        const ampm = parts[4] ? parts[4].toUpperCase() : null;

        if (ampm) {
            if (ampm === 'PM' && h !== 12) h += 12;
            if (ampm === 'AM' && h === 12) h = 0;
        }
        if (h > 23 || h < 0 || isNaN(h) || isNaN(parseInt(m,10)) || isNaN(parseInt(s,10)) || parseInt(m,10) > 59 || parseInt(s,10) > 59 ) {
            console.warn("commonUtils.js (parseTimeTo24Hour): Hour, minute, or second out of range after conversion:", timeStr12hr); return '';
        }
        return `${String(h).padStart(2,'0')}:${String(m).padStart(2,'0')}:${String(s).padStart(2,'0')}`;
    } catch(e) {
        console.error("commonUtils.js (parseTimeTo24Hour): Error parsing time string:", timeStr12hr, e);
        return '';
    }
}
// Expose the function globally
window.parseTimeTo24Hour = parseTimeTo24Hour;


function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) {
        console.error('Toast container not found!');
        alert((type === 'error' ? "Error: " : "Success: ") + message);
        return;
    }
    const toast = document.createElement('div');
    toast.className = `toast-notification ${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    requestAnimationFrame(() => { toast.classList.add('show'); });
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => { if (toast.parentNode) toast.remove(); }, 300);
    }, 3500);
}

function decodeHtmlEntities(encodedString) {
    if (encodedString === null || typeof encodedString === 'undefined' || String(encodedString).toLowerCase() === 'null') { return ''; }
    try {
        const textarea = document.createElement('textarea');
        textarea.innerHTML = String(encodedString); 
        return textarea.value;
    } catch (e) {
        return String(encodedString); 
    }
}

function showModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        currentMaxZIndex++;
        modalElement.style.zIndex = currentMaxZIndex;
        modalElement.style.display = 'flex';
        requestAnimationFrame(() => {
             modalElement.classList.add('modal-visible');
        });
        const content = modalElement.querySelector('.modal-content');
        if (!content) {
            console.error("[DEBUG] FATAL: .modal-content div not found inside", modalElement.id);
        }
    } else {
        console.error("[DEBUG] FATAL: showModal received an invalid or null element.");
    }
}

function hideModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.classList.remove('modal-visible');
        setTimeout(() => {
            modalElement.style.display = 'none';
            const content = modalElement.querySelector('.modal-content');
            if (content && content.style.position === 'absolute') {
                 content.style.position = '';
                 content.style.left = '';
                 content.style.top = '';
                 content.style.margin = '';
                 content.style.transform = '';
            }
        }, 300);
    }
}

function showPageNotification(message, isError = false, modalInstance = null, titleText = "Notification") { 
    const modalToUse = modalInstance || document.getElementById("notificationModalGeneral");
    const msgElem = modalToUse ? modalToUse.querySelector('#notificationModalGeneralMessage') : null;
    const modalTitleElem = modalToUse ? modalToUse.querySelector('#notificationModalGeneralTitle') : null;

    if (!modalToUse || !msgElem) {
        alert((isError ? "Error: " : "Notification: ") + message);
        return;
    }
    if(modalTitleElem) modalTitleElem.textContent = titleText;
    msgElem.innerHTML = message;
    showModal(modalToUse); 
	modalToUse.querySelector('#okButtonNotificationModalGeneral')?.focus();
}

function makeModalDraggable(modalElement) {
    const content = modalElement.querySelector('.modal-content');
    const header = content ? content.querySelector('h2') : null;
    if (!header || !content) return;

    header.style.cursor = 'grab';
    
    let initialMouseX, initialMouseY;
    let initialContentX, initialContentY;

    function dragStart(e) {
        e.preventDefault();
        if (window.getComputedStyle(content).position !== 'absolute') {
            const rect = content.getBoundingClientRect();
            content.style.position = 'absolute';
            content.style.left = `${rect.left}px`;
            content.style.top = `${rect.top}px`;
            content.style.margin = '0';
        }
        header.style.cursor = 'grabbing';
        initialMouseX = e.clientX;
        initialMouseY = e.clientY;
        initialContentX = content.offsetLeft;
        initialContentY = content.offsetTop;
        document.addEventListener('mousemove', elementDrag);
        document.addEventListener('mouseup', dragEnd);
    }

    function elementDrag(e) {
        const dx = e.clientX - initialMouseX;
        const dy = e.clientY - initialMouseY;
        content.style.left = `${initialContentX + dx}px`;
        content.style.top = `${initialContentY + dy}px`;
    }

    function dragEnd() {
        header.style.cursor = 'grab';
        document.removeEventListener('mousemove', elementDrag);
        document.removeEventListener('mouseup', dragEnd);
    }
    header.addEventListener('mousedown', dragStart);
}

function applyDefaultSort(table) {
    if (!table) return;
    const firstSortableHeader = table.querySelector('th.sortable');
    if (firstSortableHeader) {
        const isAlreadySorted = table.querySelector('th.sort-asc') || table.querySelector('th.sort-desc');
        if (!isAlreadySorted) {
            firstSortableHeader.click();
        }
    }
}

function initializeAllSortableTables() {
    const tables = document.querySelectorAll('.report-table');
    tables.forEach((table, index) => {
        if (table.tBodies[0] && table.tBodies[0].rows.length > 1) {
             makeTableSortable(table);
             if (index === 0) {
                 applyDefaultSort(table);
             }
        }
    });
}

function makeTableSortable(table) {
    const headers = table.querySelectorAll('th.sortable');
    headers.forEach((header, index) => {
        const newHeader = header.cloneNode(true);
        header.parentNode.replaceChild(newHeader, header);
        newHeader.addEventListener('click', () => {
            sortTableByColumn(table, index);
        });
    });
}

function sortTableByColumn(table, columnIndex) {
    const tBody = table.tBodies[0];
    if (!tBody) return;
    const rows = Array.from(tBody.querySelectorAll("tr"));
    const header = table.querySelectorAll('th.sortable')[columnIndex];
    const currentIsAsc = header.classList.contains('sort-asc');
    const direction = currentIsAsc ? 'desc' : 'asc';
    const firstRowCell = rows[0]?.cells[columnIndex];
    if (!firstRowCell) return;
    const isNumeric = /^[$\-\s]*(\d{1,3}(,\d{3})*|\d+)(\.\d+)?$/.test(firstRowCell.textContent.trim());
    const sortedRows = rows.sort((a, b) => {
        const aCellText = a.cells[columnIndex]?.textContent.trim() || '';
        const bCellText = b.cells[columnIndex]?.textContent.trim() || '';
        if (isNumeric) {
            const aVal = parseFloat(aCellText.replace(/[$,\s]/g, '')) || 0;
            const bVal = parseFloat(bCellText.replace(/[$,\s]/g, '')) || 0;
            return direction === 'asc' ? aVal - bVal : bVal - aVal;
        } else {
            return direction === 'asc' 
                ? aCellText.localeCompare(bCellText, undefined, {sensitivity: 'base'}) 
                : bCellText.localeCompare(aCellText, undefined, {sensitivity: 'base'});
        }
    });
    tBody.append(...sortedRows);
    table.querySelectorAll('th.sortable').forEach(th => {
        th.classList.remove('sort-asc', 'sort-desc');
    });
    header.classList.add(direction === 'asc' ? 'sort-asc' : 'sort-desc');
}

document.addEventListener('DOMContentLoaded', function() {
    const allModalsOnPage = document.querySelectorAll('.modal');
    allModalsOnPage.forEach(modal => {
        makeModalDraggable(modal);
    });
    
    if (typeof initializeAllSortableTables === 'function') {
        initializeAllSortableTables();
    }

    const allCloseButtons = document.querySelectorAll('.modal .close, .modal .cancel-btn, .modal [data-close-modal-id], .modal .close-modal-btn');
    allCloseButtons.forEach(button => {
        button.addEventListener('click', function(event) {
            const modalToClose = event.target.closest('.modal');
            if (modalToClose && typeof hideModal === 'function') {
                hideModal(modalToClose);
            }
        });
    });

    const allOkButtons = document.querySelectorAll('#okButtonNotificationModalGeneral, #okButton');
     allOkButtons.forEach(button => {
        button.addEventListener('click', function(event) {
            const modalToClose = event.target.closest('.modal');
            if (modalToClose && typeof hideModal === 'function') {
                hideModal(modalToClose);
            }
        });
    });

    // [NEW] Logic to find and display the login message modal
    const loginMessageModal = document.getElementById('loginMessageModal');
    if (loginMessageModal) {
        const messageBody = loginMessageModal.querySelector('.login-message-body');
        // Only show modal if the server actually populated messages into it
        if (messageBody && messageBody.innerHTML.trim() !== '') {
            showModal(loginMessageModal);
        }

        const okButton = document.getElementById('okButtonLoginMessage');
        if (okButton) {
            okButton.addEventListener('click', function() {
                hideModal(loginMessageModal);
            });
        }
    }
});