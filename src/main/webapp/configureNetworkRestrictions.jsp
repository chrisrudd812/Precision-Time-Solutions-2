<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.util.List, java.util.Map, java.util.ArrayList, java.net.URLEncoder, java.nio.charset.StandardCharsets" %>
<%@ page import="timeclock.util.IPAddressUtil" %>

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
    final String APP_CONTEXT_PATH_JSP = request.getContextPath(); 
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    String successMessageFromRedirect = request.getParameter("message");
    String errorMessageFromRedirect = request.getParameter("error");

    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP");
    
    String backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp"; 
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp?setup_wizard=true&step=" + URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
    }
    
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> allowedNetworks = (List<Map<String, Object>>) request.getAttribute("allowedNetworks");
    if (allowedNetworks == null) allowedNetworks = new ArrayList<>();

    String clientIpAddress = IPAddressUtil.getClientIpAddr(request);
    boolean isActuallyLoopback = "127.0.0.1".equals(clientIpAddress) || "0:0:0:0:0:0:0:1".equals(clientIpAddress);
    String displayIpMessage; 
    String ipToPrePopulate = "";

    if (isActuallyLoopback) {
        displayIpMessage = "You appear to be accessing this page locally (<code>" + escapeHtml(clientIpAddress) + "</code>). Add public network ranges for your users.";
    } else {
        displayIpMessage = "Your current public IP for testing appears to be: <strong>" + escapeHtml(clientIpAddress) + "</strong>. You can add this as a /32 CIDR if needed.";
        ipToPrePopulate = escapeHtml(clientIpAddress) + "/32";
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Allowed Networks</title>
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/reports.css?v=<%= System.currentTimeMillis() %>"> 
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/configureNetworkRestrictions.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="reports-page">

    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="fas fa-network-wired"></i> Configure Allowed Networks</h1>
        <p class="setting-info" style="margin-bottom:20px;"><%= displayIpMessage %></p>

        <% if (pageLoadErrorMessage != null) { %><div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>
        
        <div class="form-section">
            <h2><i class="fas fa-plus-square"></i> Add New Allowed Network</h2>
            <form id="addNetworkForm">
                <input type="hidden" name="action" value="addNetwork">
                <div class="form-item">
                     <label for="addNetworkName">Name / Label <span class="required-asterisk">*</span></label>
                    <input type="text" id="addNetworkName" name="networkName" required maxlength="100" placeholder="e.g., Main Office Network">
                </div>
                <div class="form-item">
                    <label for="addCIDR">CIDR / IP Address <span class="required-asterisk">*</span></label>
                    <input type="text" id="addCIDR" name="cidr" required maxlength="45" placeholder="e.g., 203.0.113.45/32 or 192.168.1.0/24">
                </div>
                <div class="form-item">
                     <label for="addDescription">Description</label>
                    <textarea id="addDescription" name="description" maxlength="500" placeholder="Optional notes, e.g., purpose or location details"></textarea>
                </div>
                <div class="form-item switch-container">
                    <label for="addIsEnabled">Enabled:</label>
                     <label class="switch">
                        <input type="checkbox" id="addIsEnabled" name="isEnabled" value="true" checked>
                        <span class="slider round"></span>
                    </label>
                 </div>
                <div class="button-row">
                    <button type="submit" class="glossy-button text-green"><i class="fas fa-plus-circle"></i> Add Network</button>
                </div>
            </form>
        </div>

        <h2><i class="fas fa-list-ul"></i> Existing Allowed Networks</h2>
        <div class="table-container report-table-container">
            <table class="report-table network-table">
                <thead><tr><th>Name</th><th>CIDR / IP</th><th>Description</th><th>Enabled</th><th>Actions</th></tr></thead>
                <tbody id="networksTableBody">
                    <% if (allowedNetworks.isEmpty()) { %>
                        <tr><td colspan="5" class="no-networks">No networks configured yet.</td></tr>
                    <% } else { for (Map<String, Object> network : allowedNetworks) { %>
                        <tr data-network-id="<%= network.get("NetworkID") %>" data-name="<%= escapeHtml((String)network.get("NetworkName")) %>" data-cidr="<%= escapeHtml((String)network.get("CIDR")) %>" data-description="<%= escapeHtml((String)network.get("Description")) %>" data-enabled="<%= network.get("IsEnabled") %>">
                            <td><%= escapeHtml((String)network.get("NetworkName")) %></td>
                            <td><%= escapeHtml((String)network.get("CIDR")) %></td>
                            <td><%= escapeHtml((String)network.get("Description")) %></td>
                            <td>
                                <label class="switch">
                                    <input type="checkbox" class="network-enable-toggle" data-network-id="<%= network.get("NetworkID") %>" <%= ((Boolean)network.get("IsEnabled")) ? "checked" : "" %>>
                                    <span class="slider round"></span>
                                </label>
                             </td>
                            <td>
                                <button type="button" class="action-icon-btn edit edit-network-btn" title="Edit Network"><i class="fas fa-edit"></i></button>
                                 <button type="button" class="action-icon-btn delete delete-network-btn" title="Delete Network"><i class="fas fa-trash-alt"></i></button>
                            </td>
                        </tr>
                     <% } } %>
                </tbody>
            </table>
        </div>

        <div id="networkModal" class="modal">
            <div class="modal-content">
                <span class="close" id="closeNetworkModalBtn" title="Close">&times;</span>
                <h2 id="networkModalTitle">Edit Network</h2>
                <form id="editNetworkForm">
                    <input type="hidden" name="action" value="editNetwork">
                     <input type="hidden" name="networkID" id="editNetworkID">
                    <div class="form-item"><label for="editNetworkName">Name / Label <span class="required-asterisk">*</span></label><input type="text" id="editNetworkName" name="networkName" required maxlength="100"></div>
                    <div class="form-item"><label for="editCIDR">CIDR / IP Address <span class="required-asterisk">*</span></label><input type="text" id="editCIDR" name="cidr" required maxlength="45"></div>
                    <div class="form-item"><label for="editDescription">Description</label><textarea id="editDescription" name="description" maxlength="500"></textarea></div>
                    <div class="form-item switch-container">
                        <label for="editIsEnabled">Enabled:</label>
                        <label class="switch"><input type="checkbox" id="editIsEnabled" name="isEnabled" value="true"><span class="slider round"></span></label>
                    </div>
                     <div class="button-row">
                        <button type="button" id="cancelNetworkModalBtn" class="glossy-button text-grey">Cancel</button>
                        <button type="submit" id="saveNetworkChangesBtn" class="glossy-button text-green">Save Changes</button>
                    </div>
                </form>
             </div>
        </div>
        
        <div id="confirmModal" class="modal">
            <div class="modal-content" style="max-width: 500px;">
                <span class="close">&times;</span>
                <h2 id="confirmModalTitle">Confirm Deletion</h2>
                <p id="confirmModalMessage"></p>
                <div class="button-row">
                    <button type="button" id="confirmModalCancelBtn" class="glossy-button text-grey">Cancel</button>
                    <button type="button" id="confirmModalOkBtn" class="glossy-button text-red">Delete</button>
                </div>
            </div>
        </div>
        
        <div class="form-actions">
             <a href="<%= escapeHtml(backToSettingsUrl) %>" id="backToSettingsLinkNetworkConfig" class="cancel-link glossy-button text-grey">
                <i class="fas fa-arrow-left"></i>Back to Settings
             </a>
        </div>
    </div>

    <div class="toast-notification-container" id="toast-container"></div>

    <script>
        window.appConfig = {
            contextPath: "<%= escapeForJS(APP_CONTEXT_PATH_JSP) %>",
            ipToPrepopulate: "<%= escapeForJS(ipToPrePopulate) %>"
        };
    </script>
    
    <script src="<%= APP_CONTEXT_PATH_JSP %>/js/configureNetworkRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>