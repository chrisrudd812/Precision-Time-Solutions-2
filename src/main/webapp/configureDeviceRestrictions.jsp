<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.util.List, java.util.Map, java.util.ArrayList, java.text.SimpleDateFormat, java.util.Date, java.math.BigDecimal, java.net.URLEncoder, java.nio.charset.StandardCharsets, java.util.logging.Logger, java.util.logging.Level" %>

<%!
    private static final Logger jspDeviceRestrictLogger = Logger.getLogger("configureDeviceRestrictions_jsp_fully_complete");

    private String formatDate(java.sql.Timestamp timestamp) {
        if (timestamp == null) return "N/A";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(timestamp.getTime()));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private String escapeForJS(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"").replace("\r", "\\r").replace("\n", "\\n").replace("/", "\\/").replace("<", "\\u003C").replace(">", "\\u003E");
    }
%>
<%
    final String APP_CONTEXT_PATH_JSP = request.getContextPath(); 
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP");
    String backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp";
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp?setup_wizard=true&step=" + URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
    }
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> employeeDeviceList = (List<Map<String, Object>>) request.getAttribute("employeeDeviceList");
    if (employeeDeviceList == null) employeeDeviceList = new ArrayList<>();
    String currentGlobalMaxDevices = (String) request.getAttribute("currentGlobalMaxDevices");
    if (currentGlobalMaxDevices == null || currentGlobalMaxDevices.trim().isEmpty()) currentGlobalMaxDevices = "2";
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Device Restrictions<% if(pageIsInWizardMode){ %> - Setup Wizard<% } %></title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/settings.css?v=<%= System.currentTimeMillis() %>">
    
    <style>
        .config-container { max-width: 1000px; margin: 20px auto; }
        .page-message { margin-bottom: 15px; padding: 10px; border-radius: 4px; }
        .success-message { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .form-actions { margin-top: 20px; display: flex; justify-content: flex-start; }
        .global-max-devices-section { margin-bottom: 15px; padding: 15px; border: 1px solid #007bff; border-radius: 5px; background-color: #f0f8ff; display: flex; align-items: center; justify-content: space-between; gap: 15px; flex-wrap: wrap; flex-shrink: 0; }
        .global-max-devices-section .label-input-group { display: flex; align-items: center; gap: 10px; flex-grow: 1; min-width: 300px; } 
        .global-max-devices-section label { font-weight: bold; color: #0056b3; margin-bottom: 0; white-space: nowrap; } 
        .global-max-devices-section input[type="number"] { width: 70px; padding: 8px; height: 36px; text-align: center; border-radius:3px; border:1px solid #ccc; box-sizing: border-box; background-color: #fff; color: #000; cursor: text;}
        .global-max-devices-section .button-status-group { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
        .global-max-devices-section .save-global-max-btn { padding: 0 15px; height: 36px; font-size:0.9em; line-height: 34px; } 
        .setting-info-bar { margin-bottom: 20px; padding: 10px 15px; background-color: #e9ecef; border-left: 4px solid #17a2b8; font-size: 0.9em; line-height: 1.5; flex-shrink: 0; }
        .employee-list-scroll-container { flex-grow: 1; max-height: 60vh; overflow-y: auto; border: 1px solid #ddd; border-radius: 4px; padding: 0 10px 10px 10px; margin-bottom: 20px; } .employee-section:first-child { margin-top: 10px; }
        .employee-section { margin-bottom: 20px; border: 1px solid #eee; border-radius: 5px; padding: 15px; background-color: #fff; } .employee-header { font-size: 1.2em; font-weight: bold; color: #0056b3; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
        .device-table { width: 100%; border-collapse: collapse; } .device-table th, .device-table td { text-align: left; padding: 8px 10px; font-size: 0.9em; vertical-align: middle; border-bottom: 1px solid #eee; } .device-table th { background-color: #f8f9fa; } .device-table td .action-icon-btn { background:none; border:none; padding:0; cursor:pointer; color: #007bff; font-size: 1.1em; line-height:1; } .device-table td .action-icon-btn.delete { color: #dc3545; } .device-table td .action-icon-btn:hover { opacity: 0.7; } .device-table .switch { margin-left:0; vertical-align: middle;} .device-table .description-input { width: 95%; padding: 4px 6px; font-size: 0.9em; border: 1px solid #ccc; border-radius: 3px; } .no-devices { color: #777; font-style: italic; padding: 10px; text-align: center; } .status-indicator { display: inline-block; margin-left: 5px; font-size: 0.8em; font-style: italic; min-width:50px; text-align:left;} .status-saved { color: green; } .status-error { color: red; } .status-typing { color: #666; } .status-saving { color: #007bff; }
    </style>
</head>
<body class="reports-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <div class="parent-container config-container">
        <h1><i class="fas fa-mobile-alt"></i> Employee Device Configuration</h1>
        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>

        <div class="global-max-devices-section">
            <div class="label-input-group">
                <label for="globalMaxDevicesInput">Max Registered Devices Per Employee:</label>
                <input type="number" id="globalMaxDevicesInput" value="<%= escapeHtml(currentGlobalMaxDevices) %>" min="0" max="20">
            </div>
            <div class="button-status-group">
                <button type="button" id="saveGlobalMaxDevicesBtn" class="glossy-button text-green save-global-max-btn">Update Global Limit</button>
                <span class="status-indicator global-max-status"></span>
            </div>
        </div>
        <p class="setting-info-bar">This limit applies to all employees. You can manage individual devices below by enabling or disabling them, editing their descriptions, or deleting them.<br>
        Devices are Auto Regisered when user punches In or Out for the first time using that device.</p>

        <div class="employee-list-scroll-container">
            <% if (employeeDeviceList.isEmpty() && pageLoadErrorMessage == null) { %>
                <p class="no-devices" style="padding-top: 20px;">No employees found with registered devices.</p>
            <% } else { 
                for (Map<String, Object> employee : employeeDeviceList) { %>
                    <div class="employee-section" data-employee-eid="<%= employee.get("EID") %>">
                        <div class="employee-header">
                            <span><%= escapeHtml((String)employee.get("LastName")) %>, <%= escapeHtml((String)employee.get("FirstName")) %> (ID: <%= employee.get("TenantEmployeeNumber") != null ? employee.get("TenantEmployeeNumber") : "N/A" %>)</span>
                        </div>
                        <% 
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> devicesForThisEmployee = (List<Map<String, Object>>) employee.get("devices");
                        if (devicesForThisEmployee == null || devicesForThisEmployee.isEmpty()) { %>
                            <p class="no-devices">No devices registered.</p>
                        <% } else { %>
                            <table class="device-table report-table">
                                <thead>
                                    <tr><th>Description</th><th>Registered</th><th>Last Used</th><th>Enabled</th><th>Actions</th></tr>
                                </thead>
                                <tbody>
                                    <% for (Map<String, Object> device : devicesForThisEmployee) { %>
                                        <tr data-device-id="<%= device.get("DeviceID") %>">
                                            <td><input type="text" class="description-input" value="<%= device.get("DeviceDescription") != null ? escapeHtml((String)device.get("DeviceDescription")) : "" %>" data-original-value="<%= device.get("DeviceDescription") != null ? escapeHtml((String)device.get("DeviceDescription")) : "" %>" placeholder="Device name/type"><span class="status-indicator desc-status"></span></td>
                                            <td><%= formatDate((java.sql.Timestamp)device.get("RegisteredDate")) %></td>
                                            <td><%= formatDate((java.sql.Timestamp)device.get("LastUsedDate")) %></td>
                                            <td><label class="switch"><input type="checkbox" class="device-enable-toggle" data-device-id="<%= device.get("DeviceID") %>" <%= ((Boolean)device.get("IsEnabled")) ? "checked" : "" %>><span class="slider round"></span></label></td>
                                            <td><button type="button" class="action-icon-btn delete device-delete-btn" title="Delete Device" data-device-id="<%= device.get("DeviceID") %>"><i class="fas fa-trash-alt"></i></button></td>
                                        </tr>
                                    <% } %>
                                </tbody>
                            </table>
                        <% } %> 
                    </div> 
            <%  } 
            } %>
        </div>
        
        <div class="form-actions">
             <a href="<%= backToSettingsUrl %>" id="backToSettingsLinkDeviceConfig" class="cancel-link glossy-button text-grey"><i class="fas fa-arrow-left" style="margin-right:5px;"></i>Back to Settings</a>
        </div>
    </div>
    <div class="toast-notification-container" id="toast-container"></div>
    
    <div id="confirmModal" class="modal">
        <div class="modal-content" style="max-width: 500px;">
            <span class="close" data-close-modal-id="confirmModal">&times;</span>
            <h2 id="confirmModalTitle">Confirm Action</h2>
            <p id="confirmModalMessage" style="padding: 15px 25px; line-height: 1.6;"></p>
            <div class="button-row">
                <%-- MODIFIED: Added an ID to the cancel button for the script to find it --%>
                <button type="button" id="confirmModalCancelBtn" class="glossy-button text-grey" data-close-modal-id="confirmModal">Cancel</button>
                <button type="button" id="confirmModalOkBtn" class="glossy-button text-red">OK</button>
            </div>
        </div>
    </div>

    <script>
        const APP_CONTEXT_PATH = "<%= APP_CONTEXT_PATH_JSP %>";
        const PAGE_IS_IN_WIZARD_MODE = <%= pageIsInWizardMode %>;
    </script>
    
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>
    <script src="<%= APP_CONTEXT_PATH_JSP %>/js/configureDeviceRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>

    <%-- MODIFIED: Added a script to specifically handle the confirm modal's cancel button --%>
    <script>
        document.addEventListener('DOMContentLoaded', function() {
            const confirmModal = document.getElementById('confirmModal');
            const confirmModalCancelBtn = document.getElementById('confirmModalCancelBtn');
            if (confirmModal && confirmModalCancelBtn && typeof hideModal === 'function') {
                confirmModalCancelBtn.addEventListener('click', () => {
                    hideModal(confirmModal);
                });
            }
        });
    </script>
</body>
</html>
