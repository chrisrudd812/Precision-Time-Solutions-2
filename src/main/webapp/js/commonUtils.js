// js/commonUtils.js - v2 (Updated showModal and hideModal)

// --- Helper to decode HTML entities ---
function decodeHtmlEntities(encodedString) {
    if (encodedString === null || typeof encodedString === 'undefined' || String(encodedString).toLowerCase() === 'null') {
        return ''; 
    }
    try {
        const textarea = document.createElement('textarea');
        textarea.innerHTML = String(encodedString); 
        return textarea.value;
    } catch (e) {
        console.error("Error decoding entities for string:", encodedString, e);
        return String(encodedString); 
    }
}

// --- Modal Helper Functions ---
function showModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.style.display = 'flex'; // Or 'block' if your modals are designed for block display
        modalElement.classList.add('modal-visible');
        console.log("[commonUtils.js v2] Modal shown and display style set:", modalElement.id);
    } else {
        console.warn("[commonUtils.js v2] Attempted to show a null or invalid modal element:", modalElement);
    }
}

function hideModal(modalElement) {
    if (modalElement && typeof modalElement.classList !== 'undefined') {
        modalElement.style.display = 'none'; // Explicitly hide
        modalElement.classList.remove('modal-visible');
        console.log("[commonUtils.js v2] Modal hidden:", modalElement.id);
    } else {
        console.warn("[commonUtils.js v2] Attempted to hide a null or invalid modal element:", modalElement);
    }
}

// --- Function: Remove specific query parameters from URL ---
function clearUrlParams(paramsToClear = ['message', 'error', 'addSuccess', 'editSuccess', 'deleteSuccess', 'companyIdentifier']) {
    if (typeof window.URLSearchParams === 'undefined' || typeof window.history.replaceState === 'undefined') {
        console.warn("URLSearchParams or history.replaceState not supported. Cannot clear URL params.");
        return;
    }
    const currentUrl = new URL(window.location.href);
    let paramsChanged = false;
    paramsToClear.forEach(param => {
        if (currentUrl.searchParams.has(param)) {
            currentUrl.searchParams.delete(param);
            paramsChanged = true;
        }
    });

    if (paramsChanged) {
        try {
            const newUrl = currentUrl.pathname + (currentUrl.search.length > 0 ? currentUrl.search : '');
            window.history.replaceState({}, document.title, newUrl);
            // console.log("URL params cleared by commonUtils. New relative URL: " + newUrl);
        } catch (e) {
            console.warn("Could not clean URL params (commonUtils):", e);
        }
    }
}

// --- General Notification Function ---
function showPageNotification(message, isError = false, modalInstance = null, titleText = "Notification") { 
    const modalToUse = modalInstance || document.getElementById("notificationModalGeneral") || document.getElementById("notificationModal"); // Try specific then general
    const msgElem = modalToUse ? (modalToUse.querySelector('#notificationModalGeneralMessage') || modalToUse.querySelector('#notificationMessage')) : null;
    const modalContent = modalToUse ? modalToUse.querySelector('.modal-content') : null;
    const modalTitleElem = modalToUse ? (modalToUse.querySelector('#notificationModalGeneralTitle') || modalToUse.querySelector('#notificationModalTitle')) : null;

    if (!modalToUse || !msgElem || !modalContent) {
        console.error("showPageNotification: Modal core elements not found for IDs (notificationModalGeneral or notificationModal, and their inner message/title elements)! Fallback alert for message:", message);
        alert((isError ? "Error: " : "Notification: ") + message);
        return;
    }
    if(modalTitleElem) modalTitleElem.textContent = titleText;
    msgElem.innerHTML = message; // Use innerHTML if message might contain HTML (like from servlet)
    
    if(isError) {
        modalContent.classList.add('error-message'); // General error class for modal content
        modalContent.classList.remove('success-message');
    } else {
        modalContent.classList.remove('error-message');
        modalContent.classList.add('success-message'); // General success class
    }
    
    showModal(modalToUse); 
}

/**
 * Makes a given HTML table sortable by its headers.
 */
function makeTableSortable(table, initialSortState = { columnIndex: -1, ascending: true }) {
    if (!table || typeof table.querySelectorAll !== 'function') {
        console.error("makeTableSortable: Invalid table element provided.", table);
        return;
    }
    const tableIdForLog = table.id || "anonymous_table";
    // console.log(`Initializing sorting for table: ${tableIdForLog}`); // Less verbose

    const headers = Array.from(table.querySelectorAll('thead th'));
    const tbody = table.querySelector('tbody');

    if (!tbody) { console.error(`tbody not found for sorting in table: ${tableIdForLog}`); return; }
    if (headers.length === 0) { console.warn(`No headers found in thead for table: ${tableIdForLog}. Sorting disabled.`); return; }

    let currentSort = { ...initialSortState };

    headers.forEach((header, actualIndexInHeaderRow) => {
        if (header.textContent.trim() && !header.querySelector('input, button, select')) {
            header.classList.add('sortable');
            if (!header.dataset.sortType) { header.dataset.sortType = 'string'; }

            header.addEventListener('click', () => {
                const rows = Array.from(tbody.rows).filter(row => !row.classList.contains('report-message-row') && !row.classList.contains('report-error-row')); // Exclude message/error rows
                if (rows.length === 0) { return; }

                const isAscending = (currentSort.columnIndex === actualIndexInHeaderRow) ? !currentSort.ascending : true;
                const dataType = header.dataset.sortType;

                const compareRows = (rowA, rowB) => {
                    let valA, valB;
                    try {
                        const cellA = rowA.cells[actualIndexInHeaderRow];
                        const cellB = rowB.cells[actualIndexInHeaderRow];
                        if (!cellA || !cellB) return 0;

                        valA = cellA.dataset.sortValue || cellA.textContent.trim();
                        valB = cellB.dataset.sortValue || cellB.textContent.trim();

                        if (dataType === 'number' || dataType === 'currency') {
                            valA = parseFloat(String(valA).replace(/[^0-9.-]+/g, "")) || 0;
                            valB = parseFloat(String(valB).replace(/[^0-9.-]+/g, "")) || 0;
                        } else if (dataType === 'date') { // Assumes YYYY-MM-DD for dates in data-sort-value or text
                            valA = valA.toLowerCase(); 
                            valB = valB.toLowerCase();
                        } else { // string
                            valA = valA.toLowerCase();
                            valB = valB.toLowerCase();
                        }
                        if (valA < valB) return isAscending ? -1 : 1;
                        if (valA > valB) return isAscending ? 1 : -1;
                        return 0;
                    } catch (e) { return 0; }
                };
                rows.sort(compareRows);
                headers.forEach(th => th.classList.remove('sort-asc', 'sort-desc'));
                header.classList.add(isAscending ? 'sort-asc' : 'sort-desc');
                currentSort = { columnIndex: actualIndexInHeaderRow, ascending: isAscending };
                rows.forEach(row => tbody.appendChild(row));
            });
        }
    });
    if (currentSort.columnIndex !== -1 && headers.length > currentSort.columnIndex && headers[currentSort.columnIndex].classList.contains('sortable')) {
        headers.forEach(th => th.classList.remove('sort-asc', 'sort-desc'));
        headers[currentSort.columnIndex].classList.add(currentSort.ascending ? 'sort-asc' : 'sort-desc');
    } else if (currentSort.columnIndex !== -1) {
        currentSort = { columnIndex: -1, ascending: true };
    }
}
console.log("commonUtils.js loaded (v2)");