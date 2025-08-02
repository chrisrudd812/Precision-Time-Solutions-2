// js/commonUtils.js (v4 - Corrected Draggable Activation)

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
 * Makes a modal draggable by its h2 title bar.
 * @param {HTMLElement} modalElement The modal element to make draggable.
 */
function makeModalDraggable(modalElement) {
    const content = modalElement.querySelector('.modal-content');
    const header = content ? content.querySelector('h2') : null;
    if (!header || !content) return;

    let pos1 = 0, pos2 = 0, pos3 = 0, pos4 = 0;
    header.style.cursor = 'move';
    header.onmousedown = dragMouseDown;

    function dragMouseDown(e) {
        e = e || window.event;
        e.preventDefault();
        pos3 = e.clientX;
        pos4 = e.clientY;
        document.onmouseup = closeDragElement;
        document.onmousemove = elementDrag;
    }

    function elementDrag(e) {
        e = e || window.event;
        e.preventDefault();
        if (window.getComputedStyle(content).position !== 'absolute') {
            const rect = content.getBoundingClientRect();
            content.style.position = 'absolute';
            content.style.left = rect.left + 'px';
            content.style.top = rect.top + 'px';
        }
        pos1 = pos3 - e.clientX;
        pos2 = pos4 - e.clientY;
        pos3 = e.clientX;
        pos4 = e.clientY;
        content.style.top = (content.offsetTop - pos2) + "px";
        content.style.left = (content.offsetLeft - pos1) + "px";
    }

    function closeDragElement() {
        document.onmouseup = null;
        document.onmousemove = null;
    }
}

// ** FIX: This listener now correctly activates the draggable function for all modals on any page **
document.addEventListener('DOMContentLoaded', function() {
    if (typeof makeModalDraggable === 'function') {
        const allModalsOnPage = document.querySelectorAll('.modal');
        allModalsOnPage.forEach(modal => {
            makeModalDraggable(modal);
        });
    }
});