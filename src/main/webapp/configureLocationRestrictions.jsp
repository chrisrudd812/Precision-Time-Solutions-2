<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.math.BigDecimal" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.ArrayList" %>

<%!
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private String escapeForJS(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n");
    }
%>
<%
    String app_context_path = request.getContextPath();
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    String saveSuccessMessageFromServlet = (String) request.getAttribute("saveSuccessMessage");
    String errorMessageFromPost = (String) request.getAttribute("errorMessageJSP");
    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP");

    String backToSettingsUrl = app_context_path + "/settings.jsp";
    if (pageIsInWizardMode && wizardReturnStepForJSP != null) {
        backToSettingsUrl = app_context_path + "/settings.jsp?setup_wizard=true&step=" +
                             URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> locations = (List<Map<String, Object>>) request.getAttribute("locations");
    if (locations == null) locations = new ArrayList<>();
    
    boolean isGloballyEnabled = Boolean.TRUE.equals(request.getAttribute("isGloballyEnabled"));
    boolean anyLocationEnabled = locations.stream().anyMatch(loc -> Boolean.TRUE.equals(loc.get("isEnabled")));
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Location Punch Restrictions</title>
    <link rel="stylesheet" href="<%= app_context_path %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= app_context_path %>/css/settings.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= app_context_path %>/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
.config-container {
	max-width: 900px;
}

.location-table th, .location-table td {
	text-align: left;
	padding: 8px 10px;
}

.location-table td .action-icon {
	margin-right: 10px;
	color: #007bff;
	cursor: pointer;
	font-size: 1.1em;
}

.location-table td .action-icon.delete {
	color: #dc3545;
}

.location-table .switch {
	margin-left: 0;
}

#locationModal .modal-content {
	max-width: 600px;
}

.required-asterisk {
	color: red;
	margin-left: 2px;
}

#locationMap {
	height: 280px;
	margin-bottom: 10px;
	border: 1px solid #ccc;
	border-radius: 4px;
}

.map-instructions {
	font-size: 0.85em;
	text-align: center;
	margin-top: -5px;
	margin-bottom: 15px;
	padding: 8px 10px;
	background-color: #f0f5fa;
	border: 1px solid #d6e0f0;
	border-radius: 4px;
	color: #334e68;
}

.lat-lon-group {
	display: flex;
	gap: 10px;
}

.lat-lon-group .form-item {
	flex: 1;
}

#confirmModal .modal-content p {
	padding: 20px 10px;
	text-align: center;
	line-height: 1.6;
}

#locationModal .form-item, #locationModal .get-current-location-btn {
	margin-bottom: 18px;
}

#locationModal .button-row {
	display: flex;
	gap: 12px;
	margin-top: 25px;
}

#locationModal .button-row button {
	flex-grow: 1;
	width: 100%;
}

#locationModal .get-current-location-btn {
	width: 100%;
}

.action-buttons {
	margin-bottom: 20px;
}

.address-search-group {
	display: flex;
	gap: 10px;
	/* MODIFIED: Changed alignment to center to vertically align the items */
	align-items: center;
	margin-bottom: 18px;
}

.address-search-group .form-item {
	flex-grow: 1;
	margin-bottom: 0;
}

.address-search-group button {
	flex-shrink: 0;
	white-space: nowrap;
}

h4 {
	margin: 10px 0;
	text-align: center;
	color: #555;
}
</style>
</head>
<body class="reports-page">
    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="fas fa-map-marked-alt"></i> Configure Location Punch Restrictions (Geofences)</h1>
        <p class="setting-info">
            Define geographical areas where employees are allowed to punch. If "Restrict by Location" is enabled on the main settings page, employees must be within the radius of at least one **active** geofence to punch.
        </p>
        
        <% if (isGloballyEnabled && !anyLocationEnabled) { %>
            <div class="page-message error-message">
                <i class="fas fa-exclamation-triangle"></i>
                <strong>Warning:</strong> "Restrict by Location" is enabled, but you have no active geofence locations. This will allow punches from any location. Please add and enable at least one location to enforce the restriction.
            </div>
        <% } %>

        <% if (pageLoadErrorMessage != null) { %><div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>
        
        <div class="action-buttons">
            <button type="button" id="addLocationBtn" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add New Location</button>
        </div>

        <div class="table-container report-table-container">
            <table class="report-table location-table">
                <thead><tr><th>Name</th><th>Latitude</th><th>Longitude</th><th>Radius (m)</th><th>Enabled</th><th>Actions</th></tr></thead>
                <tbody>
                     <% for (Map<String, Object> loc : locations) { %>
                        <tr data-location-id="<%= loc.get("locationID") %>" data-name="<%= escapeHtml((String)loc.get("locationName")) %>" data-lat="<%= loc.get("latitude") %>" data-lon="<%= loc.get("longitude") %>" data-radius="<%= loc.get("radiusMeters") %>" data-enabled="<%= loc.get("isEnabled") %>">
                           <td><%= escapeHtml((String)loc.get("locationName")) %></td><td><%= loc.get("latitude") %></td><td><%= loc.get("longitude") %></td><td><%= loc.get("radiusMeters") %>m</td>
                            <td><label class="switch"><input type="checkbox" class="location-enable-toggle" data-location-id="<%= loc.get("locationID") %>" <%= (Boolean)loc.get("isEnabled") ? "checked" : "" %>><span class="slider round"></span></label></td>
                            <td><i class="fas fa-edit action-icon edit-location" title="Edit"></i> <i class="fas fa-trash-alt action-icon delete-location" title="Delete"></i></td>
                        </tr>
                     <% } %>
                     <% if (locations.isEmpty()) { %>
                        <tr><td colspan="6" style="text-align:center; padding: 15px;">No geofence locations configured.</td></tr>
                     <% } %>
                </tbody>
            </table>
        </div>
        
        <div class="form-actions" style="justify-content: flex-start; margin-top:25px;">
             <a href="<%= backToSettingsUrl %>" class="glossy-button text-grey"><i class="fas fa-arrow-left"></i> Back to Settings</a>
        </div>
    </div>

    <div id="confirmModal" class="modal">
        <div class="modal-content" style="max-width: 500px;">
            <span class="close" data-close-modal-id="confirmModal">&times;</span>
            <h2 id="confirmModalTitle">Confirm Deletion</h2>
            <p id="confirmModalMessage"></p>
            <div class="button-row">
                <button type="button" id="confirmModalCancelBtn" class="glossy-button text-grey" data-close-modal-id="confirmModal">Cancel</button>
                <button type="button" id="confirmModalOkBtn" class="glossy-button text-red">Delete</button>
            </div>
        </div>
    </div>

    <div id="locationModal" class="modal">
        <div class="modal-content">
            <span class="close" id="closeLocationModalBtn" data-close-modal-id="locationModal">&times;</span>
             <h2 id="locationModalTitle">Add New Location</h2>
            <div id="locationMap"></div>
            <p class="map-instructions">Click map to set center, or use "My Current Location". Drag marker to adjust.</p>
            <form id="locationForm" action="<%= app_context_path %>/LocationRestrictionServlet" method="POST">
                <input type="hidden" name="action" id="locationActionInput" value="addLocation">
                <input type="hidden" name="locationID" id="locationIDInput" value="0">
                <div class="form-item"><label for="locationNameInput">Location Name:<span class="required-asterisk">*</span></label><input type="text" id="locationNameInput" name="locationName" required maxlength="100"></div>
                
                <div class="address-search-group">
                    <div class="form-item">
                        <label for="addressSearchInput">Find by Address:</label>
                        <input type="text" id="addressSearchInput" placeholder="e.g., 1600 Pennsylvania Ave NW, Washington, DC">
                    </div>
                    <button type="button" id="findAddressBtn" class="glossy-button text-blue"><i class="fas fa-search"></i> Find</button>
                </div>
                
                <h4>-or-</h4>

                <button type="button" id="getCurrentLocationBtn" class="get-current-location-btn glossy-button text-blue"><i class="fas fa-location-arrow"></i> Use My Current Location</button>
                <div class="lat-lon-group">
                    <div class="form-item"><label for="latitudeInput">Latitude:<span class="required-asterisk">*</span></label><input type="number" id="latitudeInput" name="latitude" required step="0.00000001" min="-90" max="90" readonly></div>
                     <div class="form-item"><label for="longitudeInput">Longitude:<span class="required-asterisk">*</span></label><input type="number" id="longitudeInput" name="longitude" required step="0.00000001" min="-180" max="180" readonly></div>
                </div>
                <div class="form-item"><label for="radiusMetersInput">Radius (meters):<span class="required-asterisk">*</span></label><input type="number" id="radiusMetersInput" name="radiusMeters" required min="10" max="10000" step="1" value="100"></div>
                <div class="form-item toggle-row">
                     <label for="locationIsEnabledInput" class="slider-label">Enabled:</label>
                    <label class="switch"><input type="checkbox" id="locationIsEnabledInput" name="isEnabled" value="true" checked><span class="slider round"></span></label>
                </div>
                <div class="button-row">
                    <button type="button" id="cancelLocationModalBtn" class="glossy-button text-red" data-close-modal-id="locationModal">Cancel</button>
                     <button type="submit" id="saveLocationBtn" class="glossy-button text-green">Save Location</button>
                </div>
            </form>
        </div>
    </div>
    <form id="deleteLocationForm" action="<%= app_context_path %>/LocationRestrictionServlet" method="POST" style="display:none;">
        <input type="hidden" name="action" value="deleteLocation">
        <input type="hidden" name="locationID" id="deleteLocationIDInput">
    </form>
    
    <div class="toast-notification-container" id="toast-container"></div>
    
    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>

    <script>
        window.appConfig = {
            contextPath: "<%= escapeForJS(app_context_path) %>"
        };
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/configureLocationRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>
    
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            <% if (errorMessageFromPost != null && !errorMessageFromPost.isEmpty()) { %>
                showNotificationModal('Error', '<%= escapeForJS(errorMessageFromPost) %>');
            <% } else if (saveSuccessMessageFromServlet != null && !saveSuccessMessageFromServlet.isEmpty()) { %>
                showNotificationModal('Success', '<%= escapeForJS(saveSuccessMessageFromServlet) %>');
            <% } %>
        });
    </script>
</body>
</html>
