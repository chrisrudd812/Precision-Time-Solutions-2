// js/commonUtils.js

// ADDED: Global variable to dynamically manage modal z-index
let currentMaxZIndex = 10080; // Start at the base z-index from your CSS

/**
 * Shows a toast notification.
 * @param {string} message The message to display.
 * @param {string} [type='info'] The type of toast ('info', 'success', 'error').
 */
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

// MODIFIED: Added detailed logging
function showModal(modalElement) {
    console.log(`%c[showModal] CALLED for: #${modalElement ? modalElement.id : 'UNKNOWN'}`, 'color: #28a745; font-weight: bold;');
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        console.log(`[showModal] PREVIOUS z-index: ${currentMaxZIndex}`);
        currentMaxZIndex++; // Increment z-index for the new modal
        modalElement.style.zIndex = currentMaxZIndex;
        console.log(`[showModal] NEW z-index: ${currentMaxZIndex} applied to #${modalElement.id}`);

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

// MODIFIED: Added detailed logging
function hideModal(modalElement) {
    console.log(`%c[hideModal] CALLED for: #${modalElement ? modalElement.id : 'UNKNOWN'}`, 'color: #dc3545; font-weight: bold;');
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.classList.remove('modal-visible');
        // Let the CSS transition finish before hiding the element
        setTimeout(() => {
            modalElement.style.display = 'none';
        }, 300); // Match this duration to your CSS transition time
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

    header.style.cursor = 'move';
    header.addEventListener('mousedown', function(e) {
        e.preventDefault();

        let initialMouseX = e.clientX;
        let initialMouseY = e.clientY;

        const rect = content.getBoundingClientRect();
        const initialContentX = rect.left;
        const initialContentY = rect.top;

        if (window.getComputedStyle(content).position !== 'absolute') {
            content.style.position = 'absolute';
            content.style.left = `${initialContentX}px`;
            content.style.top = `${initialContentY}px`;
        }

        function elementDrag(e) {
            e.preventDefault();
            const dx = e.clientX - initialMouseX;
            const dy = e.clientY - initialMouseY;
            content.style.left = `${initialContentX + dx}px`;
            content.style.top = `${initialContentY + dy}px`;
        }

        function closeDragElement() {
            document.removeEventListener('mousemove', elementDrag);
            document.removeEventListener('mouseup', closeDragElement);
        }

        document.addEventListener('mousemove', elementDrag);
        document.addEventListener('mouseup', closeDragElement);
    });
}


// --- Table Sorting Logic ---

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
    if (typeof makeModalDraggable === 'function') {
        const allModalsOnPage = document.querySelectorAll('.modal');
        allModalsOnPage.forEach(modal => {
            makeModalDraggable(modal);
        });
    }
    
    if (typeof initializeAllSortableTables === 'function') {
        initializeAllSortableTables();
    }
});