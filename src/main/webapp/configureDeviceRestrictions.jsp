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
    jspDeviceRestrictLogger.info("[configureDeviceRestrictions.jsp] Page Load. APP_CONTEXT_PATH_JSP: '" + APP_CONTEXT_PATH_JSP + "'");

    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    String successMessageFromRedirect = request.getParameter("message");
    String errorMessageFromRedirect = request.getParameter("error");

    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP");
    
    String backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp"; 
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        try {
            backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp?setup_wizard=true&step=" +
                                URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            jspDeviceRestrictLogger.log(Level.SEVERE, "Error encoding wizardReturnStepForJSP for backToSettingsUrl", e);
            backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp?error=nav_error";
        }
    }
    
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> employeeDeviceList = (List<Map<String, Object>>) request.getAttribute("employeeDeviceList");
    if (employeeDeviceList == null) employeeDeviceList = new ArrayList<>();
    String currentGlobalMaxDevices = (String) request.getAttribute("currentGlobalMaxDevices");
    if (currentGlobalMaxDevices == null || currentGlobalMaxDevices.trim().isEmpty()) currentGlobalMaxDevices = "2";
    
    HttpSession currentSession_dev_check_header = request.getSession(false);
    String companyNameForHeader_dev = "Your Company";
    if (pageIsInWizardMode && currentSession_dev_check_header != null) {
        Object companyNameObj = currentSession_dev_check_header.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String) companyNameObj).isEmpty()) {
            companyNameForHeader_dev = (String) companyNameObj;
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Device Restrictions<% if(pageIsInWizardMode){ %> - Setup Wizard<% } %></title>
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/settings.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
         
        .config-container { padding: 20px; max-width: 1000px; margin: 20px auto; }
        .page-message { margin-bottom: 15px; padding: 10px; border-radius: 4px; }
        .success-message { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .form-actions { margin-top: 20px; display: flex; justify-content: flex-start; }
         
        /* CSS fix for toast container - if you deleted the whole line, this is a safe version */
        
      
        
        .toast-notification {
            pointer-events: auto; /* Individual toasts are interactive */
            background-color: #333; color: white; padding: 12px 20px; border-radius: 5px; 
            box-shadow: 0 2px 10px rgba(0,0,0,0.2); opacity: 0; visibility: hidden; 
            transition: opacity 0.3s, visibility 0.3s, transform 0.3s; transform: translateY(20px); 
            min-width: 250px; margin-bottom:10px;
        }
        .toast-notification.show { opacity: 1; visibility: visible; transform: translateY(0); }
        .toast-notification.success { background-color: #28a745; }
        .toast-notification.error { background-color: #dc3545; }

        /* Styles from your previous working version or external CSS should handle the rest */
        .global-max-devices-section { margin-bottom: 15px; padding: 15px; border: 1px solid #007bff; border-radius: 5px; background-color: #f0f8ff; display: flex; align-items: center; justify-content: space-between; gap: 15px; flex-wrap: wrap; flex-shrink: 0; }
        .global-max-devices-section .label-input-group { display: flex; align-items: center; gap: 10px; flex-grow: 1; min-width: 300px; } 
        .global-max-devices-section label { font-weight: bold; color: #0056b3; margin-bottom: 0; white-space: nowrap; } 
        .global-max-devices-section input[type="number"] { width: 70px; padding: 8px; height: 36px; text-align: center; border-radius:3px; border:1px solid #ccc; box-sizing: border-box; background-color: #fff; color: #000; cursor: text;}
        .global-max-devices-section .button-status-group { display: flex; align-items: center; gap: 10px; flex-shrink: 0; }
        .global-max-devices-section .save-global-max-btn { padding: 0 15px; height: 36px; font-size:0.9em; line-height: 34px; } 
        .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; border-radius: 0 0 5px 5px; }
         .setting-info-bar { margin-bottom: 20px; padding: 10px 15px; background-color: #e9ecef; border-left: 4px solid #17a2b8; font-size: 0.9em; line-height: 1.5; flex-shrink: 0; }
        .employee-list-scroll-container { flex-grow: 1; max-height: 60vh; overflow-y: auto; border: 1px solid #ddd; border-radius: 4px; padding: 0 10px 10px 10px; margin-bottom: 20px; } .employee-section:first-child { margin-top: 10px; }
        .employee-section { margin-bottom: 20px; border: 1px solid #eee; border-radius: 5px; padding: 15px; background-color: #fff; } .employee-header { font-size: 1.2em; font-weight: bold; color: #0056b3; margin-bottom: 10px; display: flex; justify-content: space-between; align-items: center; }
        .device-table { width: 100%; border-collapse: collapse; } .device-table th, .device-table td { text-align: left; padding: 8px 10px; font-size: 0.9em; vertical-align: middle; border-bottom: 1px solid #eee; } .device-table th { background-color: #f8f9fa; } .device-table td .action-icon-btn { background:none; border:none; padding:0; cursor:pointer; color: #007bff; font-size: 1.1em; line-height:1; } .device-table td .action-icon-btn.delete { color: #dc3545; } .device-table td .action-icon-btn:hover { opacity: 0.7; } .device-table .switch { margin-left:0; vertical-align: middle;} .device-table .description-input { width: 95%; padding: 4px 6px; font-size: 0.9em; border: 1px solid #ccc; border-radius: 3px; } .no-devices { color: #777; font-style: italic; padding: 10px; text-align: center; } .status-indicator { display: inline-block; margin-left: 5px; font-size: 0.8em; font-style: italic; min-width:50px; text-align:left;} .status-saved { color: green; } .status-error { color: red; } .status-typing { color: #666; } .status-saving { color: #007bff; }
    </style>
</head>
<body class="reports-page">
    <% if (!pageIsInWizardMode) { %> <%@ include file="/WEB-INF/includes/navbar.jspf" %> <% } else { %> <div class="wizard-header"><h2>Company Setup: Device Restrictions for <%= escapeHtml(companyNameForHeader_dev) %></h2><p>Set a global limit. Individual devices can be managed later.</p></div> <% } %>
    <div class="parent-container config-container">
        <h1><i class="fas fa-mobile-alt"></i> Employee Device Configuration</h1>
        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>
        <% if (successMessageFromRedirect != null && !successMessageFromRedirect.isEmpty()) { %><script> document.addEventListener('DOMContentLoaded', function() { if(typeof showToast === 'function') showToast("<%= escapeForJS(successMessageFromRedirect) %>", 'success'); }); </script><% } %>
        <% if (errorMessageFromRedirect != null && !errorMessageFromRedirect.isEmpty()) { %><script> document.addEventListener('DOMContentLoaded', function() { if(typeof showToast === 'function') showToast("<%= escapeForJS(errorMessageFromRedirect) %>", 'error'); }); </script><% } %>

        <form id="globalMaxDevicesForm" action="<%= APP_CONTEXT_PATH_JSP %>/DeviceRestrictionServlet" method="POST" style="display:none;">
            <input type="hidden" name="action" value="saveGlobalMaxDevices">
            <input type="hidden" id="hiddenMaxDevicesInput" name="maxDevices">
            <% if (pageIsInWizardMode && wizardReturnStepForJSP != null) { %>
                <input type="hidden" name="wizardModeActive" value="true">
                <input type="hidden" name="wizardReturnStep" value="<%= escapeHtml(wizardReturnStepForJSP) %>">
            <% } %>
        </form>

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
        <p class="setting-info-bar">This limit applies to all employees...</p>
        <div class="employee-list-scroll-container">
            <% if (employeeDeviceList.isEmpty() && pageLoadErrorMessage == null) { %><p class="no-devices" style="padding-top: 20px;">No employees found...</p><% } else { for (Map<String, Object> employee : employeeDeviceList) { %><div class="employee-section" data-employee-eid="<%= employee.get("EID") %>"><div class="employee-header"><span><%= escapeHtml((String)employee.get("LastName")) %>, <%= escapeHtml((String)employee.get("FirstName")) %> (ID: <%= employee.get("TenantEmployeeNumber") != null ? employee.get("TenantEmployeeNumber") : "N/A" %>)</span></div> <% @SuppressWarnings("unchecked") List<Map<String, Object>> devicesForThisEmployee = (List<Map<String, Object>>) employee.get("devices"); if (devicesForThisEmployee == null || devicesForThisEmployee.isEmpty()) { %><p class="no-devices">No devices registered.</p><% } else { %><table class="device-table report-table"><thead><tr><th>Description</th><th>Fingerprint (Start)</th><th>Registered</th><th>Last Used</th><th>Enabled</th><th>Actions</th></tr></thead><tbody><% for (Map<String, Object> device : devicesForThisEmployee) { %> <tr data-device-id="<%= device.get("DeviceID") %>"><td><input type="text" class="description-input" value="<%= device.get("DeviceDescription") != null ? escapeHtml((String)device.get("DeviceDescription")) : "" %>" data-original-value="<%= device.get("DeviceDescription") != null ? escapeHtml((String)device.get("DeviceDescription")) : "" %>" placeholder="Device name/type"><span class="status-indicator desc-status"></span></td><td><% String fullHash = (String) device.get("DeviceFingerprintHash"); String partialHash = fullHash != null && fullHash.length() > 10 ? fullHash.substring(0, 10) + "..." : (fullHash != null ? fullHash : "N/A"); %><span title="<%= escapeHtml(fullHash) %>"><%= partialHash %></span></td><td><%= formatDate((java.sql.Timestamp)device.get("RegisteredDate")) %></td><td><%= formatDate((java.sql.Timestamp)device.get("LastUsedDate")) %></td><td><label class="switch"><input type="checkbox" class="device-enable-toggle" data-device-id="<%= device.get("DeviceID") %>" <%= ((Boolean)device.get("IsEnabled")) ? "checked" : "" %>><span class="slider round"></span></label></td><td><button type="button" class="action-icon-btn delete device-delete-btn" title="Delete Device" data-device-id="<%= device.get("DeviceID") %>"><i class="fas fa-trash-alt"></i></button></td></tr> <% } %></tbody></table> <% } %> </div> <% } %> <% } %>
        </div>
        
        <div class="form-actions">
             <a href="<%= backToSettingsUrl %>" id="backToSettingsLinkDeviceConfig" class="cancel-link glossy-button text-grey"><i class="fas fa-arrow-left" style="margin-right:5px;"></i>Back to Settings</a>
        </div>
    </div>
    <div class="toast-notification-container" id="toast-container"></div>

    <script>
        // These global JS variables are set by the JSP above using APP_CONTEXT_PATH_JSP.
        const APP_CONTEXT_PATH = "<%= APP_CONTEXT_PATH_JSP %>"; 
        const PAGE_IS_IN_WIZARD_MODE = <%= pageIsInWizardMode %>;
        console.log("[configureDeviceRestrictions.jsp] JS GLOBALS -- APP_CONTEXT_PATH: '" + APP_CONTEXT_PATH + "', PAGE_IS_IN_WIZARD_MODE: " + PAGE_IS_IN_WIZARD_MODE);

        function showToast(message, type = 'info') {
            const container = document.getElementById('toast-container');
            if (!container) { console.warn("Toast container not found! Message: " + message); return; }
            const toast = document.createElement('div');
            toast.className = 'toast-notification ' + type;
            toast.appendChild(document.createTextNode(message));
            container.appendChild(toast);
            requestAnimationFrame(() => { toast.classList.add('show'); });
            setTimeout(() => {
                toast.classList.remove('show');
                setTimeout(() => { if(toast.parentNode) {toast.remove();} }, 300);
            }, 3500);
        }

    document.addEventListener('DOMContentLoaded', function() {
        console.log("[configureDeviceRestrictions.js] DOMContentLoaded. Context for AJAX: '" + APP_CONTEXT_PATH + "'");
        
        const backToSettingsLinkElem = document.getElementById('backToSettingsLinkDeviceConfig');
        if(backToSettingsLinkElem) {
            console.log("[configureDeviceRestrictions.js] 'Back to Settings' link found. Href (from DOM on load): " + backToSettingsLinkElem.href);
        } else {
            console.error("[configureDeviceRestrictions.js] CRITICAL: 'Back to Settings' link element ('backToSettingsLinkDeviceConfig') NOT FOUND!");
        }

        if (window.history.replaceState) {
            const currentParams = new URLSearchParams(window.location.search);
            if (currentParams.has('message') || currentParams.has('error')) {
                let cleanPathnameForHistory = window.location.pathname;
                if (APP_CONTEXT_PATH && APP_CONTEXT_PATH !== "/" && !cleanPathnameForHistory.startsWith(APP_CONTEXT_PATH)) {
                     if (cleanPathnameForHistory.startsWith("/")) { cleanPathnameForHistory = APP_CONTEXT_PATH + cleanPathnameForHistory; } 
                     else { cleanPathnameForHistory = APP_CONTEXT_PATH + "/" + cleanPathnameForHistory; }
                } else if (APP_CONTEXT_PATH === "/" && cleanPathnameForHistory.startsWith("//")){ 
                    cleanPathnameForHistory = cleanPathnameForHistory.substring(1);
                }
                const newUrl = window.location.protocol + "//" + window.location.host + cleanPathnameForHistory + currentUrl.search;
                window.history.replaceState({ path: newUrl }, '', newUrl);
            }
        }

        const saveGlobalMaxBtn = document.getElementById('saveGlobalMaxDevicesBtn');
        const globalMaxInput = document.getElementById('globalMaxDevicesInput');
        const globalMaxStatus = document.querySelector('.global-max-status');
        const globalMaxDevicesForm = document.getElementById('globalMaxDevicesForm');
        const hiddenMaxDevicesInputForForm = document.getElementById('hiddenMaxDevicesInput');

        if(globalMaxInput) {
            console.log("[configureDeviceRestrictions.js] globalMaxDevicesInput found. Initial value: '" + globalMaxInput.value + "', Disabled: " + globalMaxInput.disabled + ", ReadOnly: " + globalMaxInput.readOnly);
            globalMaxInput.disabled = false; 
            globalMaxInput.readOnly = false;
            console.log("[configureDeviceRestrictions.js] globalMaxDevicesInput explicitly enabled. New Disabled: " + globalMaxInput.disabled + ", ReadOnly: " + globalMaxInput.readOnly);
            globalMaxInput.addEventListener('input', function(e) { console.log("[configureDeviceRestrictions.js] globalMaxDevicesInput - input event. Value: '" + this.value + "'"); });
            globalMaxInput.addEventListener('focus', function() { console.log("[configureDeviceRestrictions.js] globalMaxDevicesInput FOCUSED."); });
             globalMaxInput.addEventListener('blur', function() { console.log("[configureDeviceRestrictions.js] globalMaxDevicesInput BLURRED."); });
        } else {
            console.error("[configureDeviceRestrictions.js] CRITICAL: globalMaxDevicesInput (id='globalMaxDevicesInput') NOT FOUND! Input will not work.");
        }

        if (saveGlobalMaxBtn && globalMaxInput && globalMaxStatus) {
            console.log("[configureDeviceRestrictions.js] 'Update Global Limit' button (id='saveGlobalMaxDevicesBtn') found. Attaching click listener.");
            saveGlobalMaxBtn.addEventListener('click', function() {
                console.log("[configureDeviceRestrictions.js] 'Update Global Limit' CLICKED. PAGE_IS_IN_WIZARD_MODE (JS Var): " + PAGE_IS_IN_WIZARD_MODE);
                const newValue = globalMaxInput.value;
                console.log("[configureDeviceRestrictions.js] Value from globalMaxInput for save: '" + newValue + "'");
                const numValue = parseInt(newValue, 10);

                if (isNaN(numValue) || numValue < 0 || numValue > 20) {
                    showToast('Max devices must be a whole number between 0 and 20.', 'error');
                    if (globalMaxStatus) { globalMaxStatus.textContent = 'Invalid'; globalMaxStatus.className = 'status-indicator global-max-status status-error'; setTimeout(() => { globalMaxStatus.textContent = ''; globalMaxStatus.className = 'status-indicator global-max-status';}, 3000);}
                    return;
                }
                
                if (PAGE_IS_IN_WIZARD_MODE) {
                    if(globalMaxDevicesForm && hiddenMaxDevicesInputForForm) {
                        const formActionUrl = globalMaxDevicesForm.getAttribute('action');
                        console.log("[configureDeviceRestrictions.js] Wizard mode: Submitting hidden form. Form Action from DOM: '" + formActionUrl + "'");
                        // The form action is already correctly prefixed by the JSP using APP_CONTEXT_PATH_JSP
                        hiddenMaxDevicesInputForForm.value = newValue;
                        globalMaxDevicesForm.submit(); 
                    } else { console.error("[configureDeviceRestrictions.js] Wizard form elements missing for submission!"); alert("Error submitting settings."); }
                } else { 
                    if (globalMaxStatus) {globalMaxStatus.textContent = 'Saving...'; globalMaxStatus.className = 'status-indicator global-max-status status-saving';}
                    const formData = new URLSearchParams(); formData.append('action', 'saveGlobalMaxDevices'); formData.append('maxDevices', newValue);
                    
                    let ajaxFetchUrl = APP_CONTEXT_PATH + "/DeviceRestrictionServlet";
                    if (APP_CONTEXT_PATH === "") ajaxFetchUrl = "/DeviceRestrictionServlet";
                    else if (APP_CONTEXT_PATH === "/" && ajaxFetchUrl.startsWith("//")) ajaxFetchUrl = ajaxFetchUrl.substring(1);
                    
                    console.log("[configureDeviceRestrictions.js] Non-wizard mode: Sending AJAX to '" + ajaxFetchUrl + "'");
                    fetch(ajaxFetchUrl, { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: formData })
                    .then(response => {
                        if (!response.ok) { return response.text().then(text => Promise.reject({status: response.status, statusText: response.statusText, responseText: text})); }
                        return response.json();
                    })
                    .then(data => {
                        if (data.status === 'success') { if(globalMaxStatus){globalMaxStatus.textContent = 'Saved!';} showToast(data.message || "Global limit updated!", 'success'); globalMaxInput.value = data.newMax;
                        } else { if(globalMaxStatus){globalMaxStatus.textContent = 'Error!';} showToast(data.message || "Error saving.", 'error'); }
                        if(globalMaxStatus){ setTimeout(() => { globalMaxStatus.textContent = ''; }, 3000); }
                    }).catch(error => { console.error('[configureDeviceRestrictions.js] Save global max (AJAX) error:', error); if(globalMaxStatus){globalMaxStatus.textContent = 'Network Error!';} showToast('Network error: ' + (error.message || error.statusText || 'Unknown fetch error'), 'error'); });
                }
            });
             if (globalMaxInput) {globalMaxInput.addEventListener('keypress', function(event){ if(event.key === 'Enter'){ event.preventDefault(); if(saveGlobalMaxBtn) saveGlobalMaxBtn.click(); }});}
        } else {
            console.error("[configureDeviceRestrictions.js] CRITICAL: 'Update Global Limit' button or related elements NOT FOUND!");
        }
        
        document.querySelectorAll('.description-input').forEach(input => {
            let debounceTimer; const statusIndicator = input.parentElement.querySelector('.desc-status');
            input.addEventListener('input', function() {
                clearTimeout(debounceTimer); if(statusIndicator) { statusIndicator.textContent = 'Typing...'; statusIndicator.className = 'status-indicator desc-status status-typing';}
                debounceTimer = setTimeout(() => {
                    const deviceId = this.closest('tr').dataset.deviceId; const newDescription = this.value; const originalDescription = this.dataset.originalValue;
                    if (newDescription === originalDescription) { if(statusIndicator) statusIndicator.textContent = ''; return; }
                    if(statusIndicator) { statusIndicator.textContent = 'Saving...'; statusIndicator.className = 'status-indicator desc-status status-saving';}
                    const formData = new URLSearchParams(); formData.append('action', 'updateDeviceDescription'); formData.append('deviceId', deviceId); formData.append('description', newDescription);
                    let fetchUrl = APP_CONTEXT_PATH + '/DeviceRestrictionServlet'; if(APP_CONTEXT_PATH==="") fetchUrl = '/DeviceRestrictionServlet'; else if(APP_CONTEXT_PATH==="/" && fetchUrl.startsWith("//")) fetchUrl = fetchUrl.substring(1);
                    fetch(fetchUrl, { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: formData })
                    .then(response => response.json()).then(data => { /* ... rest of handler ... */ if(data.status === 'success'){if(statusIndicator){statusIndicator.textContent='Saved!'; this.dataset.originalValue=data.newDescription;}showToast(data.message,'success');}else{if(statusIndicator){statusIndicator.textContent='Error!';}showToast(data.message,'error');this.value=originalDescription;}if(statusIndicator){setTimeout(()=>{statusIndicator.textContent='';},3000);}}).catch(e=>{if(statusIndicator){statusIndicator.textContent='Net Error!';}showToast('Net error.','error');this.value=originalDescription;if(statusIndicator){setTimeout(()=>{statusIndicator.textContent='';},3000);}});
                }, 1200);
            });
        });
        document.querySelectorAll('.device-enable-toggle').forEach(toggle => {
            toggle.addEventListener('change', function() {
                const deviceId = this.dataset.deviceId; const isEnabled = this.checked; const formData = new URLSearchParams();
                formData.append('action', 'toggleDeviceStatus'); formData.append('deviceId', deviceId); formData.append('isEnabled', String(isEnabled));
                let fetchUrl = APP_CONTEXT_PATH + '/DeviceRestrictionServlet'; if(APP_CONTEXT_PATH==="") fetchUrl = '/DeviceRestrictionServlet'; else if(APP_CONTEXT_PATH==="/" && fetchUrl.startsWith("//")) fetchUrl = fetchUrl.substring(1);
                fetch(fetchUrl, { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: formData })
                .then(response => response.json()).then(data => { if (data.status === 'success') { showToast(data.message,'success'); } else { showToast(data.message,'error'); this.checked = !isEnabled; }
                }).catch(e=>{showToast('Net error.','error');this.checked = !isEnabled;});
            });
        });
        document.querySelectorAll('.device-delete-btn').forEach(button => {
            button.addEventListener('click', function() {
                const deviceId = this.dataset.deviceId; const row = this.closest('tr'); const descInput = row.querySelector('.description-input');
                const deviceDesc = descInput ? (descInput.value.trim()||"this device") : "this device";
                if (confirm('Delete ' + deviceDesc + '?')) {
                    const formData = new URLSearchParams(); formData.append('action', 'deleteDevice'); formData.append('deviceId', deviceId);
                    let fetchUrl = APP_CONTEXT_PATH + '/DeviceRestrictionServlet'; if(APP_CONTEXT_PATH==="") fetchUrl = '/DeviceRestrictionServlet'; else if(APP_CONTEXT_PATH==="/" && fetchUrl.startsWith("//")) fetchUrl = fetchUrl.substring(1);
                    fetch(fetchUrl, { method: 'POST', headers: {'Content-Type': 'application/x-www-form-urlencoded'}, body: formData })
                    .then(response => response.json()).then(data => { if (data.status === 'success') { showToast(data.message,'success'); row.remove(); /* Update no-devices message if needed */ } else { showToast(data.message,'error'); }
                    }).catch(e=>{showToast('Net error.','error');});
                }
            });
        });
        console.log("[configureDeviceRestrictions.js] All event listeners attached.");
    });
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>