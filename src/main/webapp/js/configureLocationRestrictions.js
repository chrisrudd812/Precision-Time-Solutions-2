// js/configureLocationRestrictions.js

const APP_CONTEXT_PATH = window.appConfig.contextPath;

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

document.addEventListener('DOMContentLoaded', function() {
    const addLocationBtn = document.getElementById('addLocationBtn');
    const locationModal = document.getElementById('locationModal');
    const closeLocationModalBtn = document.getElementById('closeLocationModalBtn');
    const cancelLocationModalBtn = document.getElementById('cancelLocationModalBtn');
    const locationForm = document.getElementById('locationForm');
    const getCurrentLocationBtn = document.getElementById('getCurrentLocationBtn');
    const confirmModal = document.getElementById('confirmModal');

    let map = null; 
    let marker = null;
    const defaultViewLat = 40.0150; 
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
    
    function hideLocationModalInternal() { 
        if (locationModal && typeof hideModal === 'function') { 
            hideModal(locationModal);
        }
    }

    addLocationBtn?.addEventListener('click', function() { 
        locationForm.reset();
        locationForm.querySelector('#locationActionInput').value = 'addLocation';
        locationForm.querySelector('#locationIDInput').value = '0';
        if (locationModalTitle) locationModalTitle.textContent = 'Add New Location';
        locationForm.querySelector('#locationIsEnabledInput').checked = true;
        locationForm.querySelector('#radiusMetersInput').value = '50';
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
                alert("Error getting location: " + error.message);
                this.disabled = false;
                this.innerHTML = '<i class="fas fa-location-arrow"></i> Use My Current Location';
            },
            { enableHighAccuracy: true, timeout: 10000, maximumAge: 0 }
        );
    });

    closeLocationModalBtn?.addEventListener('click', hideLocationModalInternal);
    cancelLocationModalBtn?.addEventListener('click', hideLocationModalInternal);

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
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    showToast(data.message || 'Status updated!', 'success');
                    // MODIFIED: Only reload the page on success
                    setTimeout(() => window.location.reload(), 1500);
                } else {
                    throw new Error(data.error || 'Failed to update status.');
                }
            })
            .catch(error => {
                showToast(error.message, 'error');
                this.checked = !isEnabled; // Revert toggle on error
            });
        });
    });
    
    document.querySelectorAll('.modal .close, #confirmModalCancelBtn').forEach(btn => {
        btn.addEventListener('click', () => {
            const modal = btn.closest('.modal');
            if (typeof hideModal === 'function') hideModal(modal);
        });
    });
});