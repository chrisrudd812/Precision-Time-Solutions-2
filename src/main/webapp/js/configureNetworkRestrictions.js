// js/configureNetworkRestrictions.js

// Read configuration passed from JSP
const APP_CONTEXT_PATH = window.appConfig.contextPath;
const PAGE_IS_IN_WIZARD_MODE = window.appConfig.pageIsInWizardMode;
const IP_TO_PREPOPULATE = window.appConfig.ipToPrepopulate;
const WIZARD_RETURN_STEP = window.appConfig.wizardReturnStep;

console.log("[configureNetworkRestrictions.js] SCRIPT LOADED. APP_CONTEXT_PATH: '" + APP_CONTEXT_PATH + "', PAGE_IS_IN_WIZARD_MODE: " + PAGE_IS_IN_WIZARD_MODE);

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) {
        console.warn("[configureNetworkRestrictions.js] Toast container ('toast-container') not found! Message: " + message);
        alert((type === 'error' ? "Error: " : (type === 'success' ? "Success: " : "Info: ")) + message);
        return;
    }
    const toast = document.createElement('div');
    toast.className = 'toast-notification ' + type;
    toast.appendChild(document.createTextNode(message));
    container.appendChild(toast);

    requestAnimationFrame(() => {
        toast.classList.add('show');
    });

    // Toast visibility duration: 3500ms (3.5 seconds)
    setTimeout(() => {
        toast.classList.remove('show');
        // Remove the toast from DOM after fade-out transition (300ms)
        setTimeout(() => {
            if (toast.parentNode) {
                toast.remove();
            }
        }, 300);
    }, 3500);
}

function reloadNetworkList() {
    let reloadUrl = (APP_CONTEXT_PATH === "" || APP_CONTEXT_PATH === "/" ? "" : APP_CONTEXT_PATH) + "/NetworkRestrictionServlet";
    const params = new URLSearchParams();

    if (PAGE_IS_IN_WIZARD_MODE) {
        params.append('setup_wizard', 'true');
        if (WIZARD_RETURN_STEP && WIZARD_RETURN_STEP !== "") {
             params.append('step', WIZARD_RETURN_STEP);
        } else {
            params.append('step', 'settings_setup'); // Default fallback
        }
    }
    const queryString = params.toString();
    if (queryString) {
        reloadUrl += "?" + queryString;
    }
    console.log("[configureNetworkRestrictions.js] Reloading network list by navigating to: " + reloadUrl);
    window.location.href = reloadUrl;
}

function escapeHtmlForJs(unsafe) {
    if (unsafe === null || typeof unsafe === 'undefined') return '';
    return unsafe
         .replace(/&/g, "&amp;")
         .replace(/</g, "&lt;")
         .replace(/>/g, "&gt;")
         .replace(/"/g, "&quot;")
         .replace(/'/g, "&#039;");
}

document.addEventListener('DOMContentLoaded', function() {
    console.log("[configureNetworkRestrictions.js] DOMContentLoaded. Base APP_CONTEXT_PATH for all AJAX: '" + APP_CONTEXT_PATH + "'");

    // Pre-populate CIDR field if IP_TO_PREPOPULATE is available
    const cidrInputForAdd = document.getElementById('addCIDR');
    if (cidrInputForAdd && IP_TO_PREPOPULATE) {
        cidrInputForAdd.value = IP_TO_PREPOPULATE;
    }
    
    // Clean URL: Remove 'message' and 'error' query parameters
    const urlParams = new URLSearchParams(window.location.search);
    let paramsToRetainForUrlClean = new URLSearchParams();
    let retainUrlParamsFlag = false;

    urlParams.forEach((value, key) => {
        if (key.toLowerCase() === 'setup_wizard' || key.toLowerCase() === 'step') {
             paramsToRetainForUrlClean.append(key, value);
             retainUrlParamsFlag = true;
        }
    });
    
    if (urlParams.has('message') || urlParams.has('error')) {
        let newPath = window.location.pathname;
        if (retainUrlParamsFlag) {
            newPath += '?' + paramsToRetainForUrlClean.toString();
        }
        if (window.history.replaceState) {
            window.history.replaceState({ path: newPath }, '', newPath);
            console.log("[configureNetworkRestrictions.js] Cleaned message/error params from URL.");
        }
    }

    const addNetworkForm = document.getElementById('addNetworkForm');
    if (addNetworkForm) {
        addNetworkForm.addEventListener('submit', function(event) {
            event.preventDefault();
            const submitButton = this.querySelector('button[type="submit"]');
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Adding...';
            }

            const formData = new FormData(this);
            const params = new URLSearchParams();
            for (const pair of formData) {
                params.append(pair[0], pair[1]);
            }
            if (!formData.has("isEnabled")) {
                params.append("isEnabled", "false");
            }

            const fetchUrl = `${APP_CONTEXT_PATH}/NetworkRestrictionServlet`;
            console.log("[configureNetworkRestrictions.js] AddNetwork - Fetching: '" + fetchUrl + "' with params: " + params.toString());

            fetch(fetchUrl, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: params
            })
            .then(response => response.json())
            .then(data => {
                showToast(data.message, data.status === 'success' ? 'success' : 'error');
                if (data.status === 'success') {
                    addNetworkForm.reset();
                    if (cidrInputForAdd) { // Re-populate CIDR after reset
                        cidrInputForAdd.value = IP_TO_PREPOPULATE;
                    }
                    // Delay reload to let user see the toast
                    setTimeout(() => {
                        reloadNetworkList();
                    }, 2500); // Reload after 2.5 seconds
                } else {
                    if (submitButton) {
                        submitButton.disabled = false;
                        submitButton.innerHTML = '<i class="fas fa-plus-circle"></i> Add Network';
                    }
                }
            })
            .catch(error => {
                console.error('[configureNetworkRestrictions.js] Error adding network:', error);
                showToast('Network error while adding. Please see console for details.', 'error');
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.innerHTML = '<i class="fas fa-plus-circle"></i> Add Network';
                }
            });
        });
    } else {
        console.error("[configureNetworkRestrictions.js] Add Network Form ('addNetworkForm') NOT FOUND!");
    }

    // Modal handling
    const networkModal = document.getElementById('networkModal');
    const editNetworkForm = document.getElementById('editNetworkForm');
    const closeNetworkModalBtn = document.getElementById('closeNetworkModalBtn');
    const cancelNetworkModalBtn = document.getElementById('cancelNetworkModalBtn');
    const networkModalTitle = document.getElementById('networkModalTitle');
    const editNetworkIdField = document.getElementById('editNetworkID');
    const editNetworkNameField = document.getElementById('editNetworkName');
    const editCidrField = document.getElementById('editCIDR');
    const editDescriptionField = document.getElementById('editDescription');
    const editIsEnabledCheckbox = document.getElementById('editIsEnabled');

    document.querySelectorAll('.edit-network-btn').forEach(button => {
        button.addEventListener('click', function() {
            const row = this.closest('tr');
            if (!row) return;

            if (editNetworkIdField) editNetworkIdField.value = row.dataset.networkId || "";
            if (networkModalTitle) networkModalTitle.textContent = 'Edit Network: ' + (row.dataset.name || 'N/A');
            if (editNetworkNameField) editNetworkNameField.value = row.dataset.name || "";
            if (editCidrField) editCidrField.value = row.dataset.cidr || "";
            if (editDescriptionField) editDescriptionField.value = row.dataset.description || "";
            if (editIsEnabledCheckbox) editIsEnabledCheckbox.checked = (row.dataset.enabled === 'true');

            if (networkModal) networkModal.classList.add('modal-visible');
            if (editNetworkNameField) editNetworkNameField.focus();
        });
    });
    
    if (closeNetworkModalBtn && networkModal) {
        closeNetworkModalBtn.addEventListener('click', () => networkModal.classList.remove('modal-visible'));
    }
    if (cancelNetworkModalBtn && networkModal) {
        cancelNetworkModalBtn.addEventListener('click', () => networkModal.classList.remove('modal-visible'));
    }
    if (networkModal) {
        networkModal.addEventListener('click', (event) => {
            if (event.target === networkModal) {
                networkModal.classList.remove('modal-visible');
            }
        });
    }

    if (editNetworkForm) {
        editNetworkForm.addEventListener('submit', function(event) {
            event.preventDefault();
            const submitButton = this.querySelector('button[type="submit"]#saveNetworkChangesBtn');
            if (submitButton) {
                submitButton.disabled = true;
                submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
            }

            const params = new URLSearchParams(new FormData(this));
            if (!params.has("isEnabled")) {
                params.append("isEnabled", "false");
            }
            
            const fetchUrl = `${APP_CONTEXT_PATH}/NetworkRestrictionServlet`;
            console.log("[configureNetworkRestrictions.js] EditNetwork - Fetching: '" + fetchUrl + "' with params: " + params.toString());

            fetch(fetchUrl, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: params
            })
            .then(response => response.json())
            .then(data => {
                showToast(data.message, data.status === 'success' ? 'success' : 'error');
                if (data.status === 'success') {
                    if (networkModal) networkModal.classList.remove('modal-visible');
                    setTimeout(() => {
                        reloadNetworkList();
                    }, 2500); // Delay reload
                } else {
                    if (submitButton) {
                        submitButton.disabled = false;
                        submitButton.innerHTML = 'Save Changes';
                    }
                }
            })
            .catch(error => {
                console.error('[configureNetworkRestrictions.js] Error editing network:', error);
                showToast('Network error while editing. See console.', 'error');
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.innerHTML = 'Save Changes';
                }
            });
        });
    } else {
        console.error("[configureNetworkRestrictions.js] Edit Network Form ('editNetworkForm') NOT FOUND!");
    }

    document.querySelectorAll('.network-enable-toggle').forEach(toggle => {
        toggle.addEventListener('change', function() {
            const networkId = this.dataset.networkId;
            const isEnabled = this.checked;
            const originalToggle = this;

            console.log(`[configureNetworkRestrictions.js] Toggle changed for NetworkID: ${networkId}, New IsEnabled: ${isEnabled}`);

            const params = new URLSearchParams();
            params.append('action', 'toggleNetworkStatus');
            params.append('networkID', networkId);
            params.append('isEnabled', String(isEnabled));

            const fetchUrl = `${APP_CONTEXT_PATH}/NetworkRestrictionServlet`;
            fetch(fetchUrl, {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: params
            })
            .then(response => {
                 if (!response.ok) {
                    console.error("[configureNetworkRestrictions.js] ToggleNetwork - Fetch FAILED. Status: " + response.status);
                    return response.text().then(text => {
                        let errorMsg = `Server error ${response.status}`;
                        try { const jsonError = JSON.parse(text); if (jsonError && jsonError.message) errorMsg = jsonError.message; } catch (e) { /* Ignore */ }
                        throw new Error(errorMsg);
                    });
                }
                return response.json();
            })
            .then(data => {
                showToast(data.message, data.status === 'success' ? 'success' : 'error');
                if (data.status !== 'success') {
                    originalToggle.checked = !isEnabled; // Revert on failure
                }
            })
            .catch(error => {
                console.error('[configureNetworkRestrictions.js] Error toggling network status:', error);
                showToast(`Error toggling status: ${error.message || 'See console.'}`, 'error');
                originalToggle.checked = !isEnabled; // Revert on any error
            });
        });
    });

    document.querySelectorAll('.delete-network-btn').forEach(button => {
        const originalButtonHTML = button.innerHTML; // Store original HTML (usually just the icon)
        button.addEventListener('click', function() {
            const row = this.closest('tr');
            if (!row) {
                showToast('Error: Could not identify network to delete.', 'error');
                return;
            }
            const networkId = row.dataset.networkId;
            const networkName = row.dataset.name || 'this network';

            if (!networkId) {
                 showToast('Error: Network ID missing, cannot delete.', 'error');
                 return;
            }
            if (confirm(`Are you sure you want to delete the network "${escapeHtmlForJs(networkName)}"? This action cannot be undone.`)) {
                this.disabled = true;
                this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Deleting...';
                
                const params = new URLSearchParams();
                params.append('action', 'deleteNetwork');
                params.append('networkID', networkId);

                const fetchUrl = `${APP_CONTEXT_PATH}/NetworkRestrictionServlet`;
                fetch(fetchUrl, {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: params
                })
                .then(response => {
                    return response.json().then(data => {
                        if (!response.ok) throw new Error(data.message || `Server error: ${response.status}`);
                        return data;
                    });
                })
                .then(data => {
                    showToast(data.message, data.status === 'success' ? 'success' : 'error');
                    if (data.status === 'success') {
                        setTimeout(() => {
                            reloadNetworkList();
                        }, 2500); // Delay reload
                    } else {
                        this.disabled = false;
                        this.innerHTML = originalButtonHTML;
                    }
                })
                .catch(error => {
                    console.error('[configureNetworkRestrictions.js] Error deleting network:', error);
                    showToast(`Error deleting network: ${error.message || 'See console for details.'}`, 'error');
                    this.disabled = false;
                    this.innerHTML = originalButtonHTML;
                });
            }
        });
    });

    console.log("[configureNetworkRestrictions.js] All event listeners setup complete.");
});