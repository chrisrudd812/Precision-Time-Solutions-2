<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.List, java.util.Map, java.util.ArrayList, java.text.SimpleDateFormat, java.util.Date, java.util.TimeZone" %>
<%@ page import="timeclock.util.Helpers" %>
<%
    // This logic retrieves the data passed from the servlet's doGet method
    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStep = Helpers.escapeHtml((String) request.getAttribute("wizardReturnStepForJSP"));
    String pageLoadErrorMessage = Helpers.escapeHtml((String) request.getAttribute("pageLoadErrorMessage"));

    String currentGlobalMaxDevices = Helpers.escapeHtml((String) request.getAttribute("currentGlobalMaxDevices"));
    if (currentGlobalMaxDevices == null || currentGlobalMaxDevices.isEmpty()) {
        currentGlobalMaxDevices = "2"; // Default
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> employeeDeviceList = (List<Map<String, Object>>) request.getAttribute("employeeDeviceList");
    if (employeeDeviceList == null) employeeDeviceList = new ArrayList<>();

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd 'at' hh:mm a");
    sdf.setTimeZone(TimeZone.getDefault()); // Adjust timezone as needed
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Device Restrictions</title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/modals.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configureDeviceRestrictions.css?v=<%= System.currentTimeMillis() %>">
</head>
<body>
    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="fas fa-mobile-alt"></i> Configure Device Restrictions</h1>
        
        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %>
            <div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i> <%= pageLoadErrorMessage %></div>
        <% } else { %>
            <div class="content-display-area device-restrictions">
                <div class="global-max-devices-section">
                     <label for="globalMaxDevicesInput">Maximum allowed devices per user:</label>
                    <input type="number" id="globalMaxDevicesInput" name="maxDevices" value="<%= currentGlobalMaxDevices %>" min="0" max="20">
                    <button id="saveGlobalMaxDevicesBtn" class="glossy-button text-green"><i class="fas fa-save"></i> Save Global Limit</button>
                    <span class="status-indicator global-max-status"></span>
                </div>
                
                <div class="how-it-works-section">
                    <h3><i class="fas fa-info-circle"></i> How Device Restrictions Work</h3>
                    <div class="explanation-content">
                        <p><strong>1. Set Device Limit:</strong> First, configure the maximum number of devices allowed per user above.</p>
                        <p><strong>2. Automatic Registration:</strong> When an employee punches in or out for the first time using a new device (phone, tablet, desktop, etc.), that device is automatically registered and listed below.</p>
                        <p><strong>3. Device Limit Enforcement:</strong> Employees can continue using different devices to punch in until they reach the maximum limit. Once the limit is reached, any attempt to punch from a new device will be denied with an explanation.</p>
                        <p><strong>4. Device Management:</strong> You can disable devices to block their usage, or delete devices to free up slots for new devices.</p>
                    </div>
                </div>

                <div class="employee-list-scroll-container">
                <% if (employeeDeviceList.isEmpty()) { %>
                    <p class="no-devices">No employees with registered devices found.</p>
                <% } else { %>
                    <% for (Map<String, Object> employee : employeeDeviceList) { %>
                    <div class="employee-section">
                        <div class="employee-header">
                           <span><i class="fas fa-user"></i> <%= Helpers.escapeHtml((String) employee.get("LastName")) %>, <%= Helpers.escapeHtml((String) employee.get("FirstName")) %></span>
                        </div>
                        <%
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> devices = (List<Map<String, Object>>) employee.get("devices");
                        if (devices == null || devices.isEmpty()) { %>
                            <p class="no-devices">No devices registered for this employee.</p>
                        <% } else { %>
                        <table class="device-table">
                            <thead>
                                <tr>
                                    <th>Description</th>
                                    <th>Registered</th>
                                    <th>Last Used</th>
                                    <th>Enabled</th>
                                    <th>Actions</th>
                                </tr>
                            </thead>
                            <tbody>
                            <% for (Map<String, Object> device : devices) { 
                                String deviceDesc = Helpers.escapeHtml((String) device.get("DeviceDescription"));
                            %>
                                <tr data-device-id="<%= device.get("DeviceID") %>">
                                    <td>
                                        <input type="text" class="description-input" value="<%= deviceDesc %>" data-original-value="<%= deviceDesc %>" maxlength="255">
                                        <span class="status-indicator desc-status"></span>
                                    </td>
                                    <td><%= device.get("RegisteredDate") != null ? sdf.format((Date)device.get("RegisteredDate")) : "N/A" %></td>
                                    <td><%= device.get("LastUsedDate") != null ? sdf.format((Date)device.get("LastUsedDate")) : "Never" %></td>
                                    <td>
                                        <label class="switch">
                                            <input type="checkbox" class="device-enable-toggle" data-device-id="<%= device.get("DeviceID") %>" <%= Boolean.TRUE.equals(device.get("IsEnabled")) ? "checked" : "" %>>
                                            <span class="slider round"></span>
                                        </label>
                                    </td>
                                    <td>
                                        <button class="action-icon-btn delete device-delete-btn" data-device-id="<%= device.get("DeviceID") %>" title="Delete Device"><i class="fas fa-trash-alt"></i></button>
                                    </td>
                                </tr>
                            <% } %>
                            </tbody>
                        </table>
                        <% } %>
                    </div>
                    <% } %>
                <% } %>
                </div>
                <div class="form-actions">
                    <a href="${pageContext.request.contextPath}/settings.jsp<% if(pageIsInWizardMode){ %>?setup_wizard=true&step=<%=wizardReturnStep%><% } %>" class="glossy-button text-grey"><i class="fas fa-arrow-left"></i> Back to Settings</a>
                </div>
            </div>
        <% } %>
    </div>
    
    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    <script>
        window.APP_CONTEXT_PATH = "${pageContext.request.contextPath}";
    </script>
    
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>

    
    <script src="${pageContext.request.contextPath}/js/configureDeviceRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>
    
</body>
</html>