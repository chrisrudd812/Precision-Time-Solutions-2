<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="java.util.List, java.util.Map, java.util.ArrayList, java.text.SimpleDateFormat, java.util.Date, java.net.URLEncoder, java.nio.charset.StandardCharsets, java.util.logging.Logger, java.util.logging.Level" %>
<%@ page import="timeclock.util.IPAddressUtil" %>

<%!
    // Logger for this JSP
    private static final Logger jspNetworkRestrictLogger = Logger.getLogger("configureNetworkRestrictions_jsp_dedup_final_v2_external_js_css");

    // Utility function to escape HTML characters for display in HTML content
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    // Utility function to escape characters for safe embedding in JavaScript strings
    private String escapeForJS(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\"", "\\\"")
                    .replace("\r", "\\r")
                    .replace("\n", "\\n")
                    .replace("/", "\\/")
                    .replace("<", "\\u003C") // Unicode escape for <
                    .replace(">", "\\u003E"); // Unicode escape for >
    }
%>
<%
    // Define APP_CONTEXT_PATH_JSP ONCE AND ONLY ONCE at the beginning of the scriptlet block.
    // This will be passed to the external JavaScript file.
    final String APP_CONTEXT_PATH_JSP = request.getContextPath(); 
    jspNetworkRestrictLogger.info("[configureNetworkRestrictions.jsp] Page Load. APP_CONTEXT_PATH_JSP defined as: '" + APP_CONTEXT_PATH_JSP + "'");

    // Retrieve messages and page mode details
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    String successMessageFromRedirect = request.getParameter("message"); // Displayed via JS toast on client-side
    String errorMessageFromRedirect = request.getParameter("error");     // Displayed via JS toast on client-side

    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP");
    
    // Determine the URL for the "Back to Settings" link
    String backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp"; 
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        try {
            backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp?setup_wizard=true&step=" +
                                URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) { 
            jspNetworkRestrictLogger.log(Level.SEVERE, "Error encoding wizardReturnStepForJSP for backToSettingsUrl", e);
            // Fallback URL in case of encoding error
            backToSettingsUrl = APP_CONTEXT_PATH_JSP + "/settings.jsp?error=" + URLEncoder.encode("Navigation encoding error", StandardCharsets.UTF_8.name());
        }
    }
    
    // Fetch allowed networks list from request attribute (set by NetworkRestrictionServlet)
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> allowedNetworks = (List<Map<String, Object>>) request.getAttribute("allowedNetworks");
    if (allowedNetworks == null) {
        allowedNetworks = new ArrayList<>(); // Ensure it's never null to prevent NPE in loops
    }

    // Determine client's IP address for display and pre-population
    String clientIpAddress = IPAddressUtil.getClientIpAddr(request); 
    boolean isActuallyLoopback = "127.0.0.1".equals(clientIpAddress) || 
                                 "0:0:0:0:0:0:0:1".equals(clientIpAddress) || 
                                 "::1".equals(clientIpAddress);
    String displayIpMessage; 
    String ipToPrePopulate = ""; // This will be passed to JavaScript

    if (isActuallyLoopback) {
        displayIpMessage = "You appear to be accessing this page locally (<code>" + escapeHtml(clientIpAddress) + "</code>). Add public network ranges for your users.";
    } else {
        displayIpMessage = "Your current public IP for testing appears to be: <strong>" + escapeHtml(clientIpAddress) + "</strong>. You can add this as a /32 CIDR if needed.";
        ipToPrePopulate = escapeHtml(clientIpAddress) + "/32"; // Pre-populate with client's public IP/32
    }
    
    // Company Name for Wizard Header
    HttpSession currentSession_net_check_header = request.getSession(false);
    String companyNameForHeader_net = "Your Company"; // Default
    if (pageIsInWizardMode && currentSession_net_check_header != null) {
        Object companyNameObj = currentSession_net_check_header.getAttribute("CompanyNameSignup");
        if (companyNameObj instanceof String && !((String) companyNameObj).isEmpty()) {
            companyNameForHeader_net = (String) companyNameObj;
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Allowed Networks<% if(pageIsInWizardMode){ %> - Setup Wizard<% } %></title>
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <%-- <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/settings.css?v=<%= System.currentTimeMillis() %>"> --%>
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/reports.css?v=<%= System.currentTimeMillis() %>"> 
    <link rel="stylesheet" href="<%= APP_CONTEXT_PATH_JSP %>/css/configureNetworkRestrictions.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body class="reports-page"> <%-- Using reports-page class for consistent body styling if applicable --%>

    <%-- Conditional Navbar or Wizard Header --%>
    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Network Restrictions for <%= escapeHtml(companyNameForHeader_net) %></h2>
            <p>Define allowed IP addresses or network ranges from which users can clock in/out. This step is optional but recommended for security.</p>
        </div>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="fas fa-network-wired"></i> Configure Allowed Networks</h1>
        <p class="setting-info" style="margin-bottom:20px;"><%= displayIpMessage %></p>

        <%-- Display page load errors (e.g., if servlet had issues fetching initial data) --%>
        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %>
            <div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div>
        <% } %>

        <%-- Success/Error messages from redirects (e.g., after form submission if not handled by AJAX toast) --%>
        <%-- These will be picked up by JavaScript to show as toasts --%>
        <% if (successMessageFromRedirect != null && !successMessageFromRedirect.isEmpty()) { %>
            <script>
                document.addEventListener('DOMContentLoaded', function() {
                    if(typeof showToast === 'function') showToast("<%= escapeForJS(successMessageFromRedirect) %>", 'success');
                });
            </script>
        <% } %>
        <% if (errorMessageFromRedirect != null && !errorMessageFromRedirect.isEmpty()) { %>
            <script>
                document.addEventListener('DOMContentLoaded', function() {
                    if(typeof showToast === 'function') showToast("<%= escapeForJS(errorMessageFromRedirect) %>", 'error');
                });
            </script>
        <% } %>

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
                    <%-- Value for addCIDR will be set by JavaScript using ipToPrePopulate --%>
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
                <thead>
                    <tr>
                        <th>Name</th>
                        <th>CIDR / IP</th>
                        <th>Description</th>
                        <th>Enabled</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody id="networksTableBody">
                    <% if (allowedNetworks.isEmpty() && (pageLoadErrorMessage == null || pageLoadErrorMessage.isEmpty())) { %>
                        <tr><td colspan="5" class="no-networks">No networks configured yet. Add one above to get started.</td></tr>
                    <% } else { %>
                        <% for (Map<String, Object> network : allowedNetworks) { %>
                            <tr data-network-id="<%= network.get("NetworkID") %>"
                                data-name="<%= escapeHtml((String)network.get("NetworkName")) %>"
                                data-cidr="<%= escapeHtml((String)network.get("CIDR")) %>"
                                data-description="<%= network.get("Description") != null ? escapeHtml((String)network.get("Description")) : "" %>"
                                data-enabled="<%= ((Boolean)network.get("IsEnabled")).toString() %>">
                                <td><%= escapeHtml((String)network.get("NetworkName")) %></td>
                                <td><%= escapeHtml((String)network.get("CIDR")) %></td>
                                <td><%= network.get("Description") != null ? escapeHtml((String)network.get("Description")) : "" %></td>
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
                        <% } %>
                    <% } %>
                </tbody>
            </table>
        </div>

        <%-- Modal for Editing Networks --%>
        <div id="networkModal" class="modal">
            <div class="modal-content">
                <span class="close" id="closeNetworkModalBtn" title="Close">&times;</span>
                <h2 id="networkModalTitle">Edit Network</h2>
                <form id="editNetworkForm">
                    <input type="hidden" name="action" value="editNetwork">
                    <input type="hidden" name="networkID" id="editNetworkID">
                    <div class="form-item">
                        <label for="editNetworkName">Name / Label <span class="required-asterisk">*</span></label>
                        <input type="text" id="editNetworkName" name="networkName" required maxlength="100">
                    </div>
                    <div class="form-item">
                        <label for="editCIDR">CIDR / IP Address <span class="required-asterisk">*</span></label>
                        <input type="text" id="editCIDR" name="cidr" required maxlength="45">
                    </div>
                    <div class="form-item">
                        <label for="editDescription">Description</label>
                        <textarea id="editDescription" name="description" maxlength="500"></textarea>
                    </div>
                    <div class="form-item switch-container">
                        <label for="editIsEnabled">Enabled:</label>
                        <label class="switch">
                            <input type="checkbox" id="editIsEnabled" name="isEnabled" value="true">
                            <span class="slider round"></span>
                        </label>
                    </div>
                    <div class="button-row">
                        <button type="button" id="cancelNetworkModalBtn" class="glossy-button text-grey">Cancel</button>
                        <button type="submit" id="saveNetworkChangesBtn" class="glossy-button text-green">Save Changes</button>
                    </div>
                </form>
            </div>
        </div>
        <br>
        
        <div class="form-actions">
             <a href="<%= escapeHtml(backToSettingsUrl) %>" id="backToSettingsLinkNetworkConfig" class="cancel-link glossy-button text-grey">
                <i class="fas fa-arrow-left"></i>Back to Settings
             </a>
        </div>
    </div>

    <%-- Container for Toast Notifications --%>
    <div class="toast-notification-container" id="toast-container"></div>

    <%-- Script to pass JSP variables to the external JavaScript file --%>
    <script>
        window.appConfig = {
            contextPath: "<%= escapeForJS(APP_CONTEXT_PATH_JSP) %>",
            pageIsInWizardMode: <%= pageIsInWizardMode %>,
            ipToPrepopulate: "<%= escapeForJS(ipToPrePopulate) %>",
            wizardReturnStep: "<%= wizardReturnStepForJSP != null ? escapeForJS(wizardReturnStepForJSP) : "" %>"
        };
    </script>

    <%-- Link to the external JavaScript file for this page --%>
    <%-- It's often good practice to place JS links at the end of the body for faster perceived page load --%>
    <script src="<%= APP_CONTEXT_PATH_JSP %>/js/configureNetworkRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>
    
    <%-- Include common scripts if any (e.g., global utility functions) --%>
    <%-- Ensure that if showToast is defined in common-scripts.jspf, it doesn't conflict with the one in configureNetworkRestrictions.js --%>
    <%-- If showToast is in common-scripts.jspf, you can remove its definition from configureNetworkRestrictions.js --%>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>

</body>
</html>