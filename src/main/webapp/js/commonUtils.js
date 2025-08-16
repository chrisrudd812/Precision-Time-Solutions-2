// js/commonUtils.js

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
        modalElement.style.display = 'flex';
        modalElement.classList.add('modal-visible');
    }
}

function hideModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.style.display = 'none';
        modalElement.classList.remove('modal-visible');
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
}

/**
 * FIX: This function has been completely replaced with a modern version using CSS transforms to prevent the "jump" on drag start.
 * @param {HTMLElement} modalElement The modal element to make draggable.
 */
function makeModalDraggable(modalElement) {
    const content = modalElement.querySelector('.modal-content');
    const header = content ? content.querySelector('h2') : null;
    if (!header || !content) return;

    header.style.cursor = 'move';
    header.addEventListener('mousedown', function(e) {
        e.preventDefault();

        // Get the initial mouse position
        let initialMouseX = e.clientX;
        let initialMouseY = e.clientY;

        // Get the initial position of the modal content
        const rect = content.getBoundingClientRect();
        const initialContentX = rect.left;
        const initialContentY = rect.top;

        // Switch to absolute positioning if not already
        if (window.getComputedStyle(content).position !== 'absolute') {
            content.style.position = 'absolute';
            content.style.left = `${initialContentX}px`;
            content.style.top = `${initialContentY}px`;
        }

        function elementDrag(e) {
            e.preventDefault();
            // Calculate the new cursor position
            const dx = e.clientX - initialMouseX;
            const dy = e.clientY - initialMouseY;

            // Set the element's new position
            content.style.left = `${initialContentX + dx}px`;
            content.style.top = `${initialContentY + dy}px`;
        }

        function closeDragElement() {
            // Stop moving when mouse button is released
            document.removeEventListener('mousemove', elementDrag);
            document.removeEventListener('mouseup', closeDragElement);
        }

        document.addEventListener('mousemove', elementDrag);
        document.addEventListener('mouseup', closeDragElement);
    });
}


// --- Table Sorting Logic ---
function initializeAllSortableTables() {
    const tables = document.querySelectorAll('.report-table');
    tables.forEach(table => {
        if (table.tBodies[0] && table.tBodies[0].rows.length > 1) {
             makeTableSortable(table);
        }
    });
}

function makeTableSortable(table) {
    const headers = table.querySelectorAll('th.sortable');
    headers.forEach((header, index) => {
        header.addEventListener('click', () => {
            sortTableByColumn(table, index);
        });
    });
}

function sortTableByColumn(table, columnIndex) {
    const tBody = table.tBodies[0];
    const rows = Array.from(tBody.querySelectorAll("tr"));
    const header = table.querySelectorAll('th.sortable')[columnIndex];
    
    const currentIsAsc = header.classList.contains('sort-asc');
    const direction = currentIsAsc ? 'desc' : 'asc';

    const isNumeric = !isNaN(rows[0]?.cells[columnIndex]?.textContent.trim().replace(/[$,]/g, ''));

    const sortedRows = rows.sort((a, b) => {
        const aCellText = a.cells[columnIndex]?.textContent.trim() || '';
        const bCellText = b.cells[columnIndex]?.textContent.trim() || '';
        
        if (isNumeric) {
            const aVal = parseFloat(aCellText.replace(/[$,]/g, '')) || 0;
            const bVal = parseFloat(bCellText.replace(/[$,]/g, '')) || 0;
            return direction === 'asc' ? aVal - bVal : bVal - aVal;
        } else {
            return direction === 'asc' 
                ? aCellText.localeCompare(bCellText) 
                : bCellText.localeCompare(aCellText);
        }
    });

    while (tBody.firstChild) {
        tBody.removeChild(tBody.firstChild);
    }
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