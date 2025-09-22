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
    HttpSession currentSession = request.getSession(); // Use the existing session

    // MODIFIED: Get messages from the session and then remove them
    String saveSuccessMessageFromServlet = (String) currentSession.getAttribute("saveSuccessMessage");
    if (saveSuccessMessageFromServlet != null) {
        currentSession.removeAttribute("saveSuccessMessage");
    }
    String errorMessageFromPost = (String) currentSession.getAttribute("errorMessageJSP");
    if (errorMessageFromPost != null) {
        currentSession.removeAttribute("errorMessageJSP");
    }
    
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
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
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="<%= app_context_path %>/css/common.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= app_context_path %>/css/modals.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= app_context_path %>/css/configureLocationRestrictions.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
</head>
<body>
    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="fas fa-map-marked-alt"></i> Configure Location Punch Restrictions (Geofences)</h1>
        
        <div class="content-display-area">
            <p class="setting-info">
                Define geographical areas where employees are allowed to punch. If "Restrict by Location" is enabled on the main settings page, employees must be within the radius of at least one **active** geofence to punch.
            </p>
            
            <% if (isGloballyEnabled && !anyLocationEnabled) { %>
                <div class="page-message error-message">
                    <i class="fas fa-exclamation-triangle"></i>
                    <strong>Warning:</strong> "Restrict by Location" is enabled, but you have no active geofence locations. This will currently allow punches from any location. Please add and enable at least one location to enforce the restriction.
                </div>
            <% } %>
    
            <% if (pageLoadErrorMessage != null) { %><div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>
            
            <div class="action-buttons main-action-buttons">
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
        </div>
        
        <div class="form-actions" style="justify-content: flex-start; margin-top:25px;">
             <a href="<%= backToSettingsUrl %>" class="glossy-button text-red"><i class="fas fa-arrow-left"></i> Back to Settings</a>
        </div>
    </div>

    <form id="deleteLocationForm" action="<%= app_context_path %>/LocationRestrictionServlet" method="POST" style="display:none;">
        <input type="hidden" name="action" value="deleteLocation">
        <input type="hidden" name="locationID" id="deleteLocationIDInput">
    </form>
    
    <div class="toast-notification-container" id="toast-container"></div>
    
    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    <%@ include file="/WEB-INF/includes/configureLocationRestrictions-modals.jspf" %>

    <script>
        window.appConfig = {
            contextPath: "<%= escapeForJS(app_context_path) %>"
        };
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="js/configureLocationRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>
    
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            // This script block now correctly shows the message after a redirect
            const showNotification = window.showPageNotification || function(msg, type) { alert( (type === 'error' ? 'Error: ' : 'Success: ') + msg); };
            
            <% if (errorMessageFromPost != null && !errorMessageFromPost.isEmpty()) { %>
                showNotification('<%= escapeForJS(errorMessageFromPost) %>', 'error', null, 'Error');
            <% } else if (saveSuccessMessageFromServlet != null && !saveSuccessMessageFromServlet.isEmpty()) { %>
                showNotification('<%= escapeForJS(saveSuccessMessageFromServlet) %>', 'success', null, 'Success');
            <% } %>
        });
    </script>
</body>
</html>