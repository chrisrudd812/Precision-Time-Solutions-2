// js/configureLocationRestrictions.js

document.addEventListener('DOMContentLoaded', function() {
    // These functions should be globally available from your common scripts
    const { showModal, hideModal, showPageNotification, showToast, decodeHtmlEntities } = window;
    const contextPath = window.appConfig.contextPath || '';

    // --- Element Selectors ---
    const addLocationBtn = document.getElementById('addLocationBtn');
    const locationTableBody = document.querySelector('.location-table tbody');
    
    // Modal Selectors
    const locationModal = document.getElementById('locationModal');
    const locationModalTitle = document.getElementById('locationModalTitle');
    const locationForm = document.getElementById('locationForm');
    const locationActionInput = document.getElementById('locationActionInput');
    const locationIDInput = document.getElementById('locationIDInput');
    const locationNameInput = document.getElementById('locationNameInput');
    const latitudeInput = document.getElementById('latitudeInput');
    const longitudeInput = document.getElementById('longitudeInput');
    const radiusMetersInput = document.getElementById('radiusMetersInput');
    const locationIsEnabledInput = document.getElementById('locationIsEnabledInput');
    const cancelLocationModalBtn = document.getElementById('cancelLocationModalBtn');

    // Map & Geocoding Selectors
    const findAddressBtn = document.getElementById('findAddressBtn');
    const addressSearchInput = document.getElementById('addressSearchInput');
    const getCurrentLocationBtn = document.getElementById('getCurrentLocationBtn');
    
    // Delete Confirmation Modal Selectors
    const confirmModal = document.getElementById('confirmModal');
    const confirmModalMessage = document.getElementById('confirmModalMessage');
    const confirmModalOkBtn = document.getElementById('confirmModalOkBtn');
    const confirmModalCancelBtn = document.getElementById('confirmModalCancelBtn');
    const deleteLocationForm = document.getElementById('deleteLocationForm');

    // --- Leaflet Map Initialization ---
    let map, marker, circle;
    const boulderCoords = [40.0150, -105.2705]; // Default coordinates

    function initializeMap() {
        if (document.getElementById('locationMap') && !map) {
            map = L.map('locationMap').setView(boulderCoords, 13);
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
            }).addTo(map);
            
            map.on('click', function(e) {
                updateMapAndInputs(e.latlng.lat, e.latlng.lng, radiusMetersInput.value);
            });
        }
    }

    function updateMapAndInputs(lat, lng, radius = 250) {
        if (!map) initializeMap();
        
        latitudeInput.value = lat.toFixed(8);
        longitudeInput.value = lng.toFixed(8);
        radiusMetersInput.value = radius;

        const latLng = [lat, lng];

        if (!marker) {
            marker = L.marker(latLng, { draggable: true }).addTo(map);
            marker.on('dragend', function(event) {
                const position = marker.getLatLng();
                updateMapAndInputs(position.lat, position.lng, radiusMetersInput.value);
            });
        } else {
            marker.setLatLng(latLng);
        }

        if (!circle) {
            circle = L.circle(latLng, { radius: radius }).addTo(map);
        } else {
            circle.setLatLng(latLng).setRadius(radius);
        }
        map.setView(latLng, 16);
    }
    
    radiusMetersInput.addEventListener('input', function() {
        if (circle) {
            circle.setRadius(this.value);
        }
    });

    // --- Modal Logic ---
    function openAddModal() {
        locationForm.reset();
        locationIsEnabledInput.checked = true;
        locationActionInput.value = 'addLocation';
        locationIDInput.value = '0';
        locationModalTitle.innerHTML = '<i class="fas fa-plus-circle"></i> <span>Add New Location</span>';
        
        showModal(locationModal);
        setTimeout(() => {
            initializeMap();
            map.invalidateSize();
            updateMapAndInputs(boulderCoords[0], boulderCoords[1], 250);
        }, 100);
    }

    function openEditModal(row) {
        locationForm.reset();
        const data = row.dataset;
        locationActionInput.value = 'editLocation';
        locationIDInput.value = data.locationId;
        locationModalTitle.innerHTML = '<i class="fas fa-edit"></i> <span>Edit Location</span>';

        locationNameInput.value = decodeHtmlEntities(data.name);
        locationIsEnabledInput.checked = (data.enabled === 'true');

        showModal(locationModal);
        setTimeout(() => {
            initializeMap();
            map.invalidateSize();
            updateMapAndInputs(parseFloat(data.lat), parseFloat(data.lon), parseInt(data.radius));
        }, 100);
    }
    
    function openDeleteModal(row) {
        const data = row.dataset;
        confirmModalMessage.innerHTML = `Are you sure you want to delete the location: <strong>${decodeHtmlEntities(data.name)}</strong>? This action cannot be undone.`;
        
        confirmModalOkBtn.onclick = function() {
            deleteLocationForm.querySelector('#deleteLocationIDInput').value = data.locationId;
            deleteLocationForm.submit();
        };

        showModal(confirmModal);
    }

    // --- Event Listeners ---
    if (addLocationBtn) addLocationBtn.addEventListener('click', openAddModal);

    if (cancelLocationModalBtn) cancelLocationModalBtn.addEventListener('click', () => hideModal(locationModal));
    if (confirmModalCancelBtn) confirmModalCancelBtn.addEventListener('click', () => hideModal(confirmModal));

    if (locationTableBody) {
        locationTableBody.addEventListener('click', (event) => {
            const target = event.target;
            const row = target.closest('tr');
            if (!row) return;

            if (target.matches('.edit-location, .edit-location *')) {
                openEditModal(row);
            } else if (target.matches('.delete-location, .delete-location *')) {
                openDeleteModal(row);
            }

            if (target.classList.contains('location-enable-toggle')) {
                const locationId = target.dataset.locationId;
                const isEnabled = target.checked;
                
                const formData = new URLSearchParams();
                formData.append('action', 'toggleLocationStatus');
                formData.append('locationID', locationId);
                formData.append('isEnabled', isEnabled);

                fetch(`${contextPath}/LocationRestrictionServlet`, {
                    method: 'POST',
                    body: formData
                })
                .then(response => response.json())
                .then(data => {
                    if (data.success) {
                        showToast('Status updated!', 'success');
                        // MODIFIED: Sync the UI state with the saved state
                        if (row) {
                            row.dataset.enabled = isEnabled;
                        }
                    } else {
                        showToast(data.message || 'Failed to update status.', 'error');
                        target.checked = !isEnabled;
                    }
                })
                .catch(error => {
                    console.error("Toggle save error:", error);
                    showToast('A network error occurred.', 'error');
                    target.checked = !isEnabled;
                });
            }
        });
    }

    if (getCurrentLocationBtn) {
        getCurrentLocationBtn.addEventListener('click', () => {
            if ("geolocation" in navigator) {
                getCurrentLocationBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Getting Location...';
                getCurrentLocationBtn.disabled = true;
                
                const options = {
                    enableHighAccuracy: true,
                    timeout: 10000,
                    maximumAge: 300000
                };
                
                navigator.geolocation.getCurrentPosition(
                    position => {
                        const accuracy = position.coords.accuracy;
                        updateMapAndInputs(position.coords.latitude, position.coords.longitude);
                        getCurrentLocationBtn.innerHTML = '<i class="fas fa-location-arrow"></i> Use My Current Location';
                        getCurrentLocationBtn.disabled = false;
                        showToast(`Location found! (±${Math.round(accuracy)}m accuracy)`, 'success');
                    },
                    error => {
                        getCurrentLocationBtn.innerHTML = '<i class="fas fa-location-arrow"></i> Use My Current Location';
                        getCurrentLocationBtn.disabled = false;
                        
                        let errorMessage = 'Location access failed. ';
                        
                        switch(error.code) {
                            case error.PERMISSION_DENIED:
                                if (location.protocol !== 'https:' && location.hostname !== 'localhost') {
                                    errorMessage += 'HTTPS connection required for location services. Please use HTTPS or enable location permissions.';
                                } else {
                                    errorMessage += 'Location permission denied. Please enable location access in your browser settings.';
                                }
                                break;
                            case error.POSITION_UNAVAILABLE:
                                errorMessage += 'Location information unavailable. Please try again.';
                                break;
                            case error.TIMEOUT:
                                errorMessage += 'Location request timed out. Try using the address search instead.';
                                break;
                            default:
                                errorMessage += 'Unknown error occurred. Please try again.';
                                break;
                        }
                        
                        showToast(errorMessage, 'error');
                    },
                    options
                );
            } else {
                showToast('Geolocation not supported by this browser', 'error');
            }
        });
    }

    if (findAddressBtn) {
        findAddressBtn.addEventListener('click', () => {
            // ... (this function is unchanged)
            const address = addressSearchInput.value.trim();
            if (!address) {
                showPageNotification('Please enter an address to search for.', 'error', null, 'Input Required');
                return;
            }
            
            findAddressBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Finding...';
            findAddressBtn.disabled = true;
            fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(address)}`)
                .then(response => response.json())
                .then(data => {
                    if (data && data.length > 0) {
                        const bestMatch = data[0];
                        updateMapAndInputs(parseFloat(bestMatch.lat), parseFloat(bestMatch.lon));
                    } else {
                        showPageNotification('Could not find a location for the address provided.', 'error');
                    }
                })
                .catch(error => {
                    console.error("Geocoding error:", error);
                    showPageNotification('An error occurred while searching for the address.', 'error');
                })
                .finally(() => {
                    findAddressBtn.innerHTML = '<i class="fas fa-search"></i> Find';
                    findAddressBtn.disabled = false;
                });
        });
    }
});