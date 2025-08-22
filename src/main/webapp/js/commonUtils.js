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

/**
 * [NEW FUNCTION] Applies a default sort to a table if one isn't already set.
 * @param {HTMLElement} table The table element to sort.
 */
function applyDefaultSort(table) {
    if (!table) return;
    // Find the first header that is designated as sortable
    const firstSortableHeader = table.querySelector('th.sortable');
    if (firstSortableHeader) {
        // Check if a sort class is already present on any header (e.g., from server-side rendering)
        const isAlreadySorted = table.querySelector('th.sort-asc') || table.querySelector('th.sort-desc');
        // If no column is already sorted, programmatically click the first one to apply a default sort.
        if (!isAlreadySorted) {
            firstSortableHeader.click();
        }
    }
}

function initializeAllSortableTables() {
    const tables = document.querySelectorAll('.report-table');
    tables.forEach((table, index) => {
        // Only initialize sorting if the table has a body and rows
        if (table.tBodies[0] && table.tBodies[0].rows.length > 1) {
             makeTableSortable(table);
             // [FIX] Apply a default sort to the first sortable table found on the page
             if (index === 0) {
                 applyDefaultSort(table);
             }
        }
    });
}

function makeTableSortable(table) {
    const headers = table.querySelectorAll('th.sortable');
    headers.forEach((header, index) => {
        // A click handler was already here, removing it and re-adding it to avoid duplicates
        // This is a defensive measure in case the script is ever loaded more than once.
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

    // Check if the first data row's column is numeric
    const firstRowCell = rows[0]?.cells[columnIndex];
    if (!firstRowCell) return; // No rows to sort

    const isNumeric = /^[$\-\s]*(\d{1,3}(,\d{3})*|\d+)(\.\d+)?$/.test(firstRowCell.textContent.trim());

    const sortedRows = rows.sort((a, b) => {
        const aCellText = a.cells[columnIndex]?.textContent.trim() || '';
        const bCellText = b.cells[columnIndex]?.textContent.trim() || '';
        
        if (isNumeric) {
            const aVal = parseFloat(aCellText.replace(/[$,\s]/g, '')) || 0;
            const bVal = parseFloat(bCellText.replace(/[$,\s]/g, '')) || 0;
            return direction === 'asc' ? aVal - bVal : bVal - aVal;
        } else {
            // Case-insensitive string comparison
            return direction === 'asc' 
                ? aCellText.localeCompare(bCellText, undefined, {sensitivity: 'base'}) 
                : bCellText.localeCompare(aCellText, undefined, {sensitivity: 'base'});
        }
    });

    // Re-append sorted rows
    tBody.append(...sortedRows);

    // Update header classes
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