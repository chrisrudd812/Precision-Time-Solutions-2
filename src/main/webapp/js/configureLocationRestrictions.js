// js/configureLocationRestrictions.js

const APP_CONTEXT_PATH = window.appConfig.contextPath;

/**
 * Shows a toast notification. Used for quick, non-blocking messages like a setting save.
 * @param {string} message The message to display.
 * @param {string} [type='info'] The type of toast ('info', 'success', 'error').
 */
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    if (!container) {
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

/**
 * Shows a general-purpose modal notification. Used for important confirmations or errors.
 * @param {string} title The title for the modal.
 * @param {string} message The message body for the modal.
 */
function showNotificationModal(title, message) {
    const modal = document.getElementById('notificationModalGeneral');
    if (modal) {
        const modalTitle = modal.querySelector('#notificationModalGeneralTitle');
        const modalMessage = modal.querySelector('#notificationModalGeneralMessage');
        if(modalTitle) modalTitle.textContent = title;
        if(modalMessage) modalMessage.textContent = message;
        if (typeof showModal === 'function') {
            showModal(modal);
        } else {
            console.warn('Global showModal function not found. Using basic display block.');
            modal.style.display = 'block';
        }
    } else {
        alert(title + ":\n" + message);
    }
}


document.addEventListener('DOMContentLoaded', function() {
    const addLocationBtn = document.getElementById('addLocationBtn');
    const locationModal = document.getElementById('locationModal');
    const locationForm = document.getElementById('locationForm');
    const getCurrentLocationBtn = document.getElementById('getCurrentLocationBtn');
    const confirmModal = document.getElementById('confirmModal');
    const findAddressBtn = document.getElementById('findAddressBtn');
    const addressSearchInput = document.getElementById('addressSearchInput');

    let map = null; 
    let marker = null;
    const defaultViewLat = 40.0150; // Boulder, CO
    const defaultViewLon = -105.2705; 
    const defaultViewZoom = 10; 
    const pointSelectZoom = 16;
    const latitudeInputEl = document.getElementById('latitudeInput'); 
    const longitudeInputEl = document.getElementById('longitudeInput');
    const locationModalTitle = document.getElementById('locationModalTitle');

    function updateLatLngFields(lat, lng) {
        if(latitudeInputEl) latitudeInputEl.value = parseFloat(lat).toFixed(8);
        if(longitudeInputEl) longitudeInputEl.value = parseFloat(lng).toFixed(8);
    }
    
    function initOrUpdateMap(lat, lon, zoom, placeMarker = true) {
        if (!L) return; 
        try { 
            if (!map) { 
                map = L.map('locationMap'); 
                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap contributors', maxZoom: 19 }).addTo(map); 
                map.on('click', function(e) { 
                    if (marker) { marker.setLatLng(e.latlng); } 
                    else { 
                        marker = L.marker(e.latlng, { draggable: true }).addTo(map); 
                        marker.on('dragend', function(event) { updateLatLngFields(marker.getLatLng().lat, marker.getLatLng().lng); }); 
                    } 
                    updateLatLngFields(e.latlng.lat, e.latlng.lng); 
                    map.setView(e.latlng, pointSelectZoom); 
                }); 
            } 
            map.setView([lat, lon], zoom); 
            if (placeMarker) { 
                if (marker) { marker.setLatLng([lat, lon]); } 
                else { 
                    marker = L.marker([lat, lon], { draggable: true }).addTo(map); 
                    marker.on('dragend', function(event) { updateLatLngFields(marker.getLatLng().lat, marker.getLatLng().lng); }); 
                } 
                updateLatLngFields(lat, lon); 
            } else if (marker) { 
                map.removeLayer(marker); 
                marker = null; 
                if(latitudeInputEl) latitudeInputEl.value = ''; 
                if(longitudeInputEl) longitudeInputEl.value = '';
            } 
        } catch (e) { console.error("Map error:", e); } 
    }
    
    function showLocationModalInternal() { 
        if (locationModal && typeof showModal === 'function') { 
            showModal(locationModal); 
            setTimeout(() => { if (map) map.invalidateSize(); }, 50); 
        }
    }
    
    addLocationBtn?.addEventListener('click', function() { 
        locationForm.reset();
        locationForm.querySelector('#locationActionInput').value = 'addLocation';
        locationForm.querySelector('#locationIDInput').value = '0';
        if (locationModalTitle) locationModalTitle.textContent = 'Add New Location';
        locationForm.querySelector('#locationIsEnabledInput').checked = true;
        locationForm.querySelector('#radiusMetersInput').value = '100';
        initOrUpdateMap(defaultViewLat, defaultViewLon, defaultViewZoom, false); 
        showLocationModalInternal(); 
        document.getElementById('locationNameInput').focus(); 
    });
    
    getCurrentLocationBtn?.addEventListener('click', function() {
        this.disabled = true;
        this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Fetching...';
        navigator.geolocation.getCurrentPosition(
            (position) => {
                updateLatLngFields(position.coords.latitude, position.coords.longitude);
                initOrUpdateMap(position.coords.latitude, position.coords.longitude, pointSelectZoom, true);
                this.disabled = false;
                this.innerHTML = '<i class="fas fa-location-arrow"></i> Use My Current Location';
            },
            (error) => {
                showNotificationModal("Location Error", "Error getting location: " + error.message);
                this.disabled = false;
                this.innerHTML = '<i class="fas fa-location-arrow"></i> Use My Current Location';
            },
            { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
        );
    });

    findAddressBtn?.addEventListener('click', function() {
        const address = addressSearchInput.value.trim();
        if (!address) {
            showNotificationModal('Input Required', 'Please enter an address to search for.');
            return;
        }

        this.disabled = true;
        this.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Finding...';

        const url = `https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address)}`;

        fetch(url)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`Network response was not ok: ${response.statusText}`);
                }
                return response.json();
            })
            .then(data => {
                if (data && data.length > 0) {
                    const firstResult = data[0];
                    const lat = parseFloat(firstResult.lat);
                    const lon = parseFloat(firstResult.lon);
                    updateLatLngFields(lat, lon);
                    initOrUpdateMap(lat, lon, pointSelectZoom, true);
                } else {
                    showNotificationModal('Not Found', 'The address could not be found. Please try again with a different format.');
                }
            })
            .catch(error => {
                console.error('Address search failed:', error);
                showNotificationModal('Error', 'An error occurred while searching for the address. Please check your connection and try again.');
            })
            .finally(() => {
                this.disabled = false;
                this.innerHTML = '<i class="fas fa-search"></i> Find';
            });
    });
    
    addressSearchInput?.addEventListener('keydown', function(event) {
        if (event.key === 'Enter') {
            event.preventDefault(); 
            findAddressBtn.click();
        }
    });

    document.querySelectorAll('.edit-location').forEach(button => { 
        button.addEventListener('click', function() {
            const row = this.closest('tr');
            locationForm.reset();
            locationForm.querySelector('#locationActionInput').value = 'editLocation';
            locationForm.querySelector('#locationIDInput').value = row.dataset.locationId;
            if (locationModalTitle) locationModalTitle.textContent = 'Edit Location';
            locationForm.querySelector('#locationNameInput').value = row.dataset.name;
            const lat = parseFloat(row.dataset.lat);
            const lon = parseFloat(row.dataset.lon);
            latitudeInputEl.value = !isNaN(lat) ? lat : '';
            longitudeInputEl.value = !isNaN(lon) ? lon : '';
            locationForm.querySelector('#radiusMetersInput').value = row.dataset.radius;
            locationForm.querySelector('#locationIsEnabledInput').checked = (row.dataset.enabled === 'true');
            if (!isNaN(lat) && !isNaN(lon)) {
                initOrUpdateMap(lat, lon, pointSelectZoom, true);
            } else {
                initOrUpdateMap(defaultViewLat, defaultViewLon, defaultViewZoom, false);
            }
            showLocationModalInternal();
        });
    });
    
    document.querySelectorAll('.delete-location').forEach(button => {
        button.addEventListener('click', function() {
            const row = this.closest('tr');
            const locationName = row.dataset.name;
            const locationId = row.dataset.locationId;
            
            if (confirmModal) {
                confirmModal.querySelector('#confirmModalMessage').innerHTML = `Are you sure you want to delete the location "<strong>${locationName}</strong>"? This cannot be undone.`;
                if(typeof showModal === 'function') showModal(confirmModal);
                
                const confirmBtn = confirmModal.querySelector('#confirmModalOkBtn');
                const newConfirmBtn = confirmBtn.cloneNode(true);
                confirmBtn.parentNode.replaceChild(newConfirmBtn, confirmBtn);
                
                newConfirmBtn.addEventListener('click', () => {
                    document.getElementById('deleteLocationIDInput').value = locationId;
                    document.getElementById('deleteLocationForm').submit();
                }, { once: true });
            }
        });
    });
    
    document.querySelectorAll('.location-enable-toggle').forEach(toggle => {
        toggle.addEventListener('change', function() {
            const locationID = this.dataset.locationId;
            const isEnabled = this.checked;
            const formData = new URLSearchParams({
                action: 'updateLocationStatus',
                locationID: locationID,
                isEnabled: isEnabled
            });

            fetch(`${APP_CONTEXT_PATH}/LocationRestrictionServlet`, { method: 'POST', body: formData })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Network response was not ok.');
                }
                return response.json();
            })
            .then(data => {
                if (data.success) {
                    showToast(data.message || 'Status updated!', 'success');
                } else {
                    throw new Error(data.error || 'Failed to update status.');
                }
            })
            .catch(error => {
                showNotificationModal('Update Failed', error.message);
                this.checked = !isEnabled; // Revert toggle on error
            });
        });
    });
    
    document.querySelectorAll('[data-close-modal-id]').forEach(btn => {
        btn.addEventListener('click', () => {
            const modalId = btn.dataset.closeModalId;
            const modal = document.getElementById(modalId);
            if (modal && typeof hideModal === 'function') {
                hideModal(modal);
            }
        });
    });
});
