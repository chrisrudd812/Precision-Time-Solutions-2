<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>


<%!
    private static final Logger jspLocationRestrictLogger = Logger.getLogger("configureLocationRestrictions_jsp_wizard_v1");

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
%>
<%
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    String saveSuccessMessageFromServlet = (String) request.getAttribute("saveSuccessMessage");
    String errorMessageFromPost = (String) request.getAttribute("errorMessageJSP");

    // Determine if in wizard mode based on attributes set by the servlet
    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP"); // e.g., "settings_setup"
    String app_context_path = request.getContextPath(); // Get context path once

    String backToSettingsUrl = app_context_path + "/settings.jsp"; // Default back URL
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        backToSettingsUrl = app_context_path + "/settings.jsp?setup_wizard=true&step=" +
                            URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
        jspLocationRestrictLogger.info("Page in wizard mode. Back to Settings URL set to: " + backToSettingsUrl);
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> locations = (List<Map<String, Object>>) request.getAttribute("locations");
    if (locations == null) {
        locations = new ArrayList<>();
        if (pageLoadErrorMessage == null && errorMessageFromPost == null && saveSuccessMessageFromServlet == null ) {
            Object tenantIdAttr = request.getSession(false) != null ? request.getSession(false).getAttribute("TenantID") : null;
            if (tenantIdAttr != null) {
                 pageLoadErrorMessage = "Could not load existing locations list. Please try adding one.";
            }
        }
    }
    
    HttpSession currentSession_loc_check = request.getSession(false);
    String companyNameForHeader = "Your Company";
    if (pageIsInWizardMode && currentSession_loc_check != null) {
        Object companyNameObj = currentSession_loc_check.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String) companyNameObj).isEmpty()) {
            companyNameForHeader = (String) companyNameObj;
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Location Punch Restrictions<% if(pageIsInWizardMode){ %> - Setup Wizard<% } %></title>
    <link rel="stylesheet" href="<%= app_context_path %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= app_context_path %>/css/settings.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= app_context_path %>/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        /* Your existing styles from configureLocationRestrictions.jsp (Turn 37) */
        .config-container { padding: 20px; max-width: 900px; margin: 20px auto; background-color:#fff; border-radius:8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .page-message { padding: 10px 15px; margin-bottom: 20px !important; border-radius: 4px; display: flex; align-items: center;}
        .page-message .fas { margin-right: 10px; font-size: 1.2em;}
        .success-message { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        h1 .fas, h1 .far { margin-right: 10px; }
        .action-buttons { margin-bottom: 20px; }
        .location-table th, .location-table td { text-align: left; padding: 8px 10px;}
        .location-table td .action-icon { margin-right: 10px; color: #007bff; cursor:pointer; font-size: 1.1em; } .location-table td .action-icon.delete { color: #dc3545; } .location-table td .action-icon:hover { opacity: 0.7; } .location-table .switch { margin-left:0; }
        .modal#locationModal { display: none; } .modal#locationModal.modal-visible { display: flex !important; align-items: center; justify-content: center; } #locationModal .modal-content { max-width: 600px; width: 90%; background-color: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.2); } .modal-content h2 { margin-top: 0; font-size: 1.4em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee;} .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; }
        #locationModal .form-item { margin-bottom: 12px; display: flex; flex-direction: column; align-items: flex-start; } #locationModal .form-item label { display: block; margin-bottom: 4px; font-weight: bold; width:100%; color: #333; } #locationModal .form-item input[type="text"], #locationModal .form-item input[type="number"] { width: 100%; padding: 8px 10px; border: 1px solid #ccc; border-radius: 4px; font-size: 1em; box-sizing: border-box; }
        #locationModal .form-item.toggle-row { display: flex; flex-direction: row; align-items: center; width: 100%; margin-top: 15px; margin-bottom: 15px; box-sizing: border-box; }
        #locationModal .form-item.toggle-row label.slider-label { font-weight: bold; color: #333; margin-bottom: 0; white-space: nowrap; box-sizing: border-box; flex-basis: 135px; flex-shrink: 0; padding-right: 10px; }
        #locationModal .form-item.toggle-row .switch { position: relative; display: inline-block; width: 50px; min-width: 50px; height: 24px; margin-left: 0; } #locationModal .form-item.toggle-row .switch input[type="checkbox"] { opacity: 0; width: 0; height: 0; } #locationModal .form-item.toggle-row .switch .slider { position: absolute; cursor: pointer; top: 0; left: 0; right: 0; bottom: 0; background-color: #ccc; transition: .4s; border-radius: 24px; } #locationModal .form-item.toggle-row .switch .slider:before { position: absolute; content: ""; height: 16px; width: 16px; left: 4px; bottom: 4px; background-color: white; transition: .4s; border-radius: 50%; } #locationModal .form-item.toggle-row .switch input[type="checkbox"]:checked + .slider { background-color: #006400; } #locationModal .form-item.toggle-row .switch input[type="checkbox"]:focus + .slider { box-shadow: 0 0 2px #006400; } #locationModal .form-item.toggle-row .switch input[type="checkbox"]:checked + .slider:before { transform: translateX(26px); }
        #locationModal .button-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; } .required-asterisk { color: red; margin-left: 2px;} #locationMap { height: 280px; width: 100%; margin-bottom: 10px; border: 1px solid #ccc; border-radius: 4px; background-color: #f0f0f0; } .map-instructions { font-size: 0.85em; text-align: center; margin-top: -5px; margin-bottom: 15px; padding: 8px 10px; background-color: #f0f5fa; border: 1px solid #d6e0f0; border-radius: 4px; color: #334e68; } .lat-lon-group { display: flex; gap: 10px; margin-bottom: 10px; } .lat-lon-group .form-item { flex: 1; } .get-current-location-btn { padding: 8px 12px; font-size: 0.9em; width: 100%; margin-bottom:15px; display: flex; align-items: center; justify-content: center; box-sizing: border-box; } .get-current-location-btn .fas { margin-right: 8px;} .get-current-location-btn:disabled { cursor: wait; opacity: 0.7; }
        #configSaveSuccessModal { display: none; } #configSaveSuccessModal.modal-visible { display: flex !important; align-items: center; justify-content: center; } #configSaveSuccessModal .modal-content { max-width: 420px; text-align: center; padding: 25px 30px; } #configSaveSuccessModal h2 { font-size: 1.6em; color: #28a745; border-bottom: none; margin-top: 0; margin-bottom: 15px; } #configSaveSuccessModal h2 .fas { margin-right: 10px; } #configSaveSuccessModal p { font-size: 1em; color: #333; margin-bottom: 25px; line-height: 1.6; } #configSaveSuccessModal .button-row { justify-content: center; }
        .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; border-radius: 0 0 5px 5px; }
        .wizard-header h2 { margin-top:0; margin-bottom: 5px; font-weight: 500;}
        .wizard-header p { margin-bottom:0; font-size:0.9em; opacity: 0.9;}
    </style>
</head>
<body class="reports-page">
    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
         <div class="wizard-header">
            <h2>Company Setup: Location Restrictions for <%= escapeHtml(companyNameForHeader) %></h2>
             <p>Define geofences where employees can punch in/out. This is optional.</p>
        </div>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="fas fa-map-marked-alt"></i> Configure Location Punch Restrictions (Geofences)</h1>
        <p class="setting-info" style="margin-bottom:25px;">
            Define geographical areas (geofences) where employees are allowed to punch.
            If "Restrict by Location" is enabled on the main settings page and one or more locations here are active,
            employees must be within the radius of an active geofence to punch.
        </p>
        <%-- This modal is for non-wizard mode save success messages that keep user on this page --%>
        <div id="configSaveSuccessModal" class="modal">
            <div class="modal-content">
                <h2 id="configSaveSuccessTitle"><i class="fas fa-check-circle"></i> Location Saved!</h2>
                <p id="configSaveSuccessMessage"></p>
                <div class="button-row"> <button type="button" id="configSaveOkButton" class="glossy-button text-blue">OK</button> </div>
            </div>
        </div>

        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>
        <% if (errorMessageFromPost != null && !errorMessageFromPost.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i> <%= escapeHtml(errorMessageFromPost) %></div><% } %>
        <% if (saveSuccessMessageFromServlet != null && !saveSuccessMessageFromServlet.isEmpty() && !pageIsInWizardMode) { /* Only show for non-wizard success that stays here */ %>
            <div class="page-message success-message"><i class="fas fa-check-circle"></i> <%= escapeHtml(saveSuccessMessageFromServlet) %></div>
        <% } %>
        
        <div class="action-buttons">
            <button type="button" id="addLocationBtn" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add New Location</button>
        </div>

        <%
        boolean canDisplayTable = true;
        if (pageLoadErrorMessage != null && !pageLoadErrorMessage.toLowerCase().contains("defaults are shown") && !pageLoadErrorMessage.toLowerCase().contains("could not load existing locations")) {
             canDisplayTable = false;
        }
        Object tenantIdForCheck_loc = request.getSession(false) != null ? request.getSession(false).getAttribute("TenantID") : null;
        if (tenantIdForCheck_loc == null) {
            canDisplayTable = false; 
        }
        %>

        <% if (canDisplayTable) { %>
            <div class="table-container report-table-container">
                <table class="report-table location-table">
                    <thead><tr><th>Name</th><th>Latitude</th><th>Longitude</th><th>Radius (m)</th><th>Enabled</th><th>Actions</th></tr></thead>
                    <tbody>
                        <% if (locations != null && !locations.isEmpty()) { for (Map<String, Object> loc : locations) { %>
                                <tr data-location-id="<%= loc.get("locationID") %>" data-name="<%= escapeHtml((String)loc.get("locationName")) %>" data-lat="<%= loc.get("latitude") != null ? ((BigDecimal)loc.get("latitude")).toPlainString() : "" %>" data-lon="<%= loc.get("longitude") != null ? ((BigDecimal)loc.get("longitude")).toPlainString() : "" %>" data-radius="<%= loc.get("radiusMeters") %>" data-enabled="<%= loc.get("isEnabled") %>">
                                    <td><%= escapeHtml((String)loc.get("locationName")) %></td><td><%= loc.get("latitude") != null ? ((BigDecimal)loc.get("latitude")).toPlainString() : "N/A" %></td><td><%= loc.get("longitude") != null ? ((BigDecimal)loc.get("longitude")).toPlainString() : "N/A" %></td><td><%= loc.get("radiusMeters") %>m</td>
                                    <td><label class="switch"><input type="checkbox" class="location-enable-toggle" data-location-id="<%= loc.get("locationID") %>" <%= (Boolean)loc.get("isEnabled") ? "checked" : "" %>><span class="slider round"></span></label></td>
                                    <td><i class="fas fa-edit action-icon edit-location" title="Edit"></i> <i class="fas fa-trash-alt action-icon delete-location" title="Delete"></i></td>
                                </tr>
                            <% } %>
                        <% } else { %><tr><td colspan="6" style="text-align:center; padding: 15px;">No geofence locations configured.</td></tr> <% } %>
                    </tbody>
                </table>
            </div>
        <% } else if (pageLoadErrorMessage == null) { %><div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i>Cannot load page. Invalid session or tenant.</div> <% } %>
        
        <div class="form-actions" style="justify-content: flex-start; margin-top:25px;">
             <a href="<%= backToSettingsUrl %>" class="cancel-link glossy-button text-grey"><i class="fas fa-arrow-left" style="margin-right:5px;"></i>Back to Settings</a>
        </div>
    </div>

    <div id="locationModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeLocationModalBtn">&times;</span>
            <h2 id="locationModalTitle">Add New Location</h2>
            <div id="locationMap"></div>
            <p class="map-instructions">Click map to set center, or use "My Current Location". Drag marker to adjust.</p>
            <form id="locationForm" action="<%= app_context_path %>/LocationRestrictionServlet" method="POST" onsubmit="return validateLocationForm();">
                <input type="hidden" name="action" id="locationActionInput" value="addLocation">
                <input type="hidden" name="locationID" id="locationIDInput" value="0">
                <%-- Add hidden fields if in wizard mode for the form submission --%>
                <% if (pageIsInWizardMode && wizardReturnStepForJSP != null) { %>
                    <input type="hidden" name="wizardModeActive" value="true">
                    <input type="hidden" name="wizardReturnStep" value="<%= escapeHtml(wizardReturnStepForJSP) %>">
                <% } %>
                <div class="form-item"><label for="locationNameInput">Location Name:<span class="required-asterisk">*</span></label><input type="text" id="locationNameInput" name="locationName" required maxlength="100"></div>
                <button type="button" id="getCurrentLocationBtn" class="get-current-location-btn glossy-button text-blue"><i class="fas fa-location-arrow"></i> Use My Current Location</button>
                <div class="lat-lon-group">
                    <div class="form-item"><label for="latitudeInput">Latitude:<span class="required-asterisk">*</span></label><input type="number" id="latitudeInput" name="latitude" required step="0.00000001" min="-90" max="90" readonly title="Set by map or current location"></div>
                    <div class="form-item"><label for="longitudeInput">Longitude:<span class="required-asterisk">*</span></label><input type="number" id="longitudeInput" name="longitude" required step="0.00000001" min="-180" max="180" readonly title="Set by map or current location"></div>
                </div>
                <div class="form-item"><label for="radiusMetersInput">Radius (meters):<span class="required-asterisk">*</span></label><input type="number" id="radiusMetersInput" name="radiusMeters" required min="10" max="10000" step="1" value="50"></div>
                <div class="form-item toggle-row">
                    <label for="locationIsEnabledInput" class="slider-label">Enabled:</label>
                    <label class="switch"><input type="checkbox" id="locationIsEnabledInput" name="isEnabled" value="true" checked><span class="slider round"></span></label>
                </div>
                <div class="button-row">
                    <button type="button" id="cancelLocationModalBtn" class="glossy-button text-red">Cancel</button>
                    <button type="submit" id="saveLocationBtn" class="glossy-button text-green">Save Location</button>
                </div>
            </form>
        </div>
    </div>
    <form id="deleteLocationForm" action="<%= app_context_path %>/LocationRestrictionServlet" method="POST" style="display:none;">
        <input type="hidden" name="action" value="deleteLocation">
        <input type="hidden" name="locationID" id="deleteLocationIDInput">
         <%-- Add hidden fields if in wizard mode for delete form too if needed, though delete usually just reloads current page via GET after POST --%>
    </form>

    <script>
        // Make app_context_path available globally for external JS if needed
        const appRootPath = "<%= app_context_path %>"; 
        console.log("[configureLocationRestrictions.jsp] Global appRootPath for JS: '" + appRootPath + "'");
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script>
        // All your existing JavaScript for map, modals, validation (from Turn 37) goes here
        // Ensure any fetch calls within it use appRootPath for URL construction
        function validateLocationForm() { /* ... */ return true; }
        document.addEventListener('DOMContentLoaded', function() {
            console.log("configureLocationRestrictions.jsp DOMContentLoaded - v14 (Wizard Path Fix)");
            const addLocationBtn = document.getElementById('addLocationBtn');
            const locationModal = document.getElementById('locationModal');
            const closeLocationModalBtn = document.getElementById('closeLocationModalBtn');
            const cancelLocationModalBtn = document.getElementById('cancelLocationModalBtn');
            const locationForm = document.getElementById('locationForm'); // Ensure this is used if submitting via JS
            const getCurrentLocationBtn = document.getElementById('getCurrentLocationBtn');

            // Simplified map and modal JS from your provided file
            let map = null; let marker = null;
            const defaultViewLat = 40.0150; const defaultViewLon = -105.2705; const defaultViewZoom = 10; const pointSelectZoom = 16;
            const latitudeInputEl = document.getElementById('latitudeInput'); const longitudeInputEl = document.getElementById('longitudeInput');

            function updateLatLngFields(lat, lng) { if(latitudeInputEl) latitudeInputEl.value = parseFloat(lat).toFixed(8); if(longitudeInputEl) longitudeInputEl.value = parseFloat(lng).toFixed(8); }
            function initOrUpdateMap(lat, lon, zoom, placeMarker = true) { if (!L) return false; try { if (!map) { map = L.map('locationMap'); L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '&copy; OpenStreetMap contributors', maxZoom: 19 }).addTo(map); map.on('click', function(e) { if (marker) { marker.setLatLng(e.latlng); } else { marker = L.marker(e.latlng, { draggable: true }).addTo(map); marker.on('dragend', function(event) { updateLatLngFields(marker.getLatLng().lat, marker.getLatLng().lng); }); } updateLatLngFields(e.latlng.lat, e.latlng.lng); map.setView(e.latlng, pointSelectZoom); }); } map.setView([lat, lon], zoom); if (placeMarker) { if (marker) { marker.setLatLng([lat, lon]); } else { marker = L.marker([lat, lon], { draggable: true }).addTo(map); marker.on('dragend', function(event) { updateLatLngFields(marker.getLatLng().lat, marker.getLatLng().lng); }); } updateLatLngFields(lat, lon); } else if (marker) { map.removeLayer(marker); marker = null; if(latitudeInputEl) latitudeInputEl.value = ''; if(longitudeInputEl) longitudeInputEl.value = '';} return true; } catch (e) { console.error("Map error:", e); return false;} }
            function showLocationModalInternal() { if (locationModal) { if(typeof window.showModal === 'function') window.showModal(locationModal); else locationModal.style.display = 'flex'; setTimeout(function() { if (map) { map.invalidateSize(); } }, 50); }}
            function hideLocationModalInternal() { if (locationModal) { if(typeof window.hideModal === 'function') window.hideModal(locationModal); else locationModal.style.display = 'none'; }}

            if(addLocationBtn) addLocationBtn.addEventListener('click', function() { /* ... as provided ... */ document.getElementById('locationActionInput').value = 'addLocation'; document.getElementById('locationIDInput').value = '0'; document.getElementById('locationModalTitle').textContent = 'Add New Location'; locationForm.reset(); document.getElementById('locationIsEnabledInput').checked = true; document.getElementById('radiusMetersInput').value = '50'; initOrUpdateMap(defaultViewLat, defaultViewLon, defaultViewZoom, false); showLocationModalInternal(); document.getElementById('locationNameInput').focus(); });
            if(getCurrentLocationBtn) { /* ... as provided, no changes needed for context path ... */ getCurrentLocationBtn.addEventListener('click', function() { /* ... */ });}
            if(closeLocationModalBtn) closeLocationModalBtn.addEventListener('click', hideLocationModalInternal);
            if(cancelLocationModalBtn) cancelLocationModalBtn.addEventListener('click', function() { hideLocationModalInternal(); if (locationForm) locationForm.reset(); });

            document.querySelectorAll('.edit-location').forEach(button => { button.addEventListener('click', function() { /* ... as provided, but ensure form submit is standard if no JS submit ... */ const row = this.closest('tr'); document.getElementById('locationActionInput').value = 'editLocation'; document.getElementById('locationIDInput').value = row.dataset.locationId; document.getElementById('locationNameInput').value = row.dataset.name; const lat = parseFloat(row.dataset.lat); const lon = parseFloat(row.dataset.lon); latitudeInputEl.value = !isNaN(lat) ? lat.toFixed(8) : ''; longitudeInputEl.value = !isNaN(lon) ? lon.toFixed(8) : ''; document.getElementById('radiusMetersInput').value = row.dataset.radius; document.getElementById('locationIsEnabledInput').checked = (row.dataset.enabled === 'true'); if (!isNaN(lat) && !isNaN(lon)) { initOrUpdateMap(lat, lon, pointSelectZoom, true); } else { initOrUpdateMap(defaultViewLat, defaultViewLon, defaultViewZoom, false); } document.getElementById('locationModalTitle').textContent = 'Edit Location'; showLocationModalInternal(); document.getElementById('locationNameInput').focus(); });});
            document.querySelectorAll('.delete-location').forEach(button => { button.addEventListener('click', function() { /* ... as provided ... */ const row = this.closest('tr'); if (confirm("Delete '" + row.dataset.name + "'?")) { document.getElementById('deleteLocationIDInput').value = row.dataset.locationId; document.getElementById('deleteLocationForm').submit(); } });});
            
            document.querySelectorAll('.location-enable-toggle').forEach(toggle => {
                toggle.addEventListener('change', function() {
                    const locationID = this.dataset.locationId; const isEnabled = this.checked; const formData = new URLSearchParams();
                    formData.append('action', 'updateLocationStatus'); formData.append('locationID', locationID); formData.append('isEnabled', String(isEnabled));
                    // Ensure context path for this fetch too
                    fetch(`${appRootPath}/LocationRestrictionServlet`, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: formData })
                    .then(response => { if (!response.ok) { throw new Error('Network response error ' + response.statusText); } return response.text(); })
                    .then(text => { console.log("Location status update submitted, page will reload/re-render."); window.location.reload(); /* Simpler to reload if servlet forwards back */ })
                    .catch(error => { console.error('Error updating status:', error); alert('Failed to update status.'); this.checked = !isEnabled; });
                });
            });
            
            // Non-wizard success modal display
            const jsSaveSuccessMessage = "<%= saveSuccessMessageFromServlet != null ? escapeHtml(saveSuccessMessageFromServlet) : "" %>";
            const pageIsInWizardModeJS = <%= pageIsInWizardMode %>;
            const successModalEl = document.getElementById('configSaveSuccessModal');
            const successModalMsgEl = document.getElementById('configSaveSuccessMessage');
            const successModalOkBtnEl = document.getElementById('configSaveOkButton');

            if (!pageIsInWizardModeJS && jsSaveSuccessMessage && successModalEl && successModalMsgEl && successModalOkBtnEl) {
                successModalMsgEl.innerHTML = jsSaveSuccessMessage;
                if(typeof window.showModal === 'function') window.showModal(successModalEl); else successModalEl.style.display = 'flex';
                const newOkButton = successModalOkBtnEl.cloneNode(true); successModalOkBtnEl.parentNode.replaceChild(newOkButton, successModalOkBtnEl);
                newOkButton.addEventListener('click', function() { if(typeof window.hideModal === 'function') window.hideModal(successModalEl); else successModalEl.style.display = 'none'; if (window.history.replaceState && (new URLSearchParams(window.location.search).has('message'))) { window.history.replaceState({}, document.title, window.location.pathname);}}, { once: true });
            }
            if (window.history.replaceState && (new URLSearchParams(window.location.search).has('message') || new URLSearchParams(window.location.search).has('error'))) {
                 if(!jsSaveSuccessMessage && !pageIsInWizardModeJS) { // Only clear if not handled by modal
                    window.history.replaceState({}, document.title, window.location.pathname);
                 }
            }
        });
    </script>
    <%-- Ensure common-scripts.jspf is included AFTER appRootPath is defined --%>
</body>
</html>