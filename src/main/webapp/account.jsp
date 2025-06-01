<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.sql.Connection" %>
<%@ page import="java.sql.PreparedStatement" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="timeclock.db.DatabaseConnection" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspAccountLogger = Logger.getLogger("account_jsp");

    private String escapeJspHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    private String escapeForJavaScriptString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("/", "\\/");
    }

    private static class CompanyDisplayInfo {
        String companyName = "N/A"; String companyIdentifier = "N/A";
        String adminEmail = "N/A";
        String companyPhone = "N/A"; 
        String companyAddress = "N/A"; String companyCity = "N/A";    
        String companyState = "N/A"; String companyZip = "N/A";
        String primaryAdminFullName = "N/A";
    }

    private CompanyDisplayInfo getCompanyDisplayDetails(Integer tenantId) {
        CompanyDisplayInfo info = new CompanyDisplayInfo();
        if (tenantId == null || tenantId <= 0) { return info; }
        String sqlTenant = "SELECT CompanyName, CompanyIdentifier, AdminEmail, PhoneNumber, Address, City, State, ZipCode FROM tenants WHERE TenantID = ?";
        String sqlAdminName = "SELECT FIRST_NAME, LAST_NAME FROM employee_data WHERE TenantID = ? AND LOWER(EMAIL) = LOWER(?) AND ACTIVE = TRUE LIMIT 1";
        Connection conn = null; PreparedStatement pstmtTenant = null; ResultSet rsTenant = null; PreparedStatement pstmtAdminName = null; ResultSet rsAdminName = null;
        try {
            conn = DatabaseConnection.getConnection(); 
            if (conn == null) { 
                jspAccountLogger.warning("DB connection is null in getCompanyDisplayDetails for T:" + tenantId); 
                return info; 
            }
            pstmtTenant = conn.prepareStatement(sqlTenant); pstmtTenant.setInt(1, tenantId); rsTenant = pstmtTenant.executeQuery();
            if (rsTenant.next()) {
                info.companyName = rsTenant.getString("CompanyName"); info.companyIdentifier = rsTenant.getString("CompanyIdentifier"); info.adminEmail = rsTenant.getString("AdminEmail"); 
                info.companyPhone = rsTenant.getString("PhoneNumber"); info.companyAddress = rsTenant.getString("Address"); info.companyCity = rsTenant.getString("City"); 
                info.companyState = rsTenant.getString("State"); info.companyZip = rsTenant.getString("ZipCode");
                
                if (info.companyName == null || info.companyName.trim().isEmpty()) info.companyName = "N/A";
                if (info.companyIdentifier == null || info.companyIdentifier.trim().isEmpty()) info.companyIdentifier = "N/A";
                if (info.adminEmail == null || info.adminEmail.trim().isEmpty()) info.adminEmail = "N/A";
                if (info.companyPhone == null || info.companyPhone.trim().isEmpty()) info.companyPhone = "N/A";
                if (info.companyAddress == null || info.companyAddress.trim().isEmpty()) info.companyAddress = "N/A";
                if (info.companyCity == null || info.companyCity.trim().isEmpty()) info.companyCity = "N/A";
                if (info.companyState == null || info.companyState.trim().isEmpty()) info.companyState = "N/A";
                if (info.companyZip == null || info.companyZip.trim().isEmpty()) info.companyZip = "N/A";

                if (!"N/A".equals(info.adminEmail)) {
                    pstmtAdminName = conn.prepareStatement(sqlAdminName); pstmtAdminName.setInt(1, tenantId); pstmtAdminName.setString(2, info.adminEmail.trim()); rsAdminName = pstmtAdminName.executeQuery();
                    if (rsAdminName.next()) {
                        String firstName = rsAdminName.getString("FIRST_NAME"); String lastName = rsAdminName.getString("LAST_NAME"); 
                        StringBuilder fullNameBuilder = new StringBuilder();
                        if (firstName != null && !firstName.trim().isEmpty()) { fullNameBuilder.append(firstName.trim()); }
                        if (lastName != null && !lastName.trim().isEmpty()) { if (fullNameBuilder.length() > 0) fullNameBuilder.append(" "); fullNameBuilder.append(lastName.trim()); }
                        if (fullNameBuilder.length() > 0) { info.primaryAdminFullName = fullNameBuilder.toString(); } else { info.primaryAdminFullName = info.adminEmail; }
                    } else { 
                        info.primaryAdminFullName = info.adminEmail; 
                        jspAccountLogger.info("getCompanyDisplayDetails: No employee_data record found for AdminEmail " + info.adminEmail + " in T:" + tenantId + ". Using email as full name."); 
                    }
                } else {
                     info.primaryAdminFullName = "N/A"; 
                }
            } else { jspAccountLogger.warning("getCompanyDisplayDetails: No tenant details found for TenantID " + tenantId); }
        } catch (SQLException e) { jspAccountLogger.log(Level.SEVERE, "SQLException in getCompanyDisplayDetails for TenantID " + tenantId, e); } 
        finally { 
            try { if (rsAdminName != null) rsAdminName.close(); } catch (SQLException e) { jspAccountLogger.log(Level.FINER, "Error closing rsAdminName", e); } 
            try { if (pstmtAdminName != null) pstmtAdminName.close(); } catch (SQLException e) { jspAccountLogger.log(Level.FINER, "Error closing pstmtAdminName", e); } 
            try { if (rsTenant != null) rsTenant.close(); } catch (SQLException e) { jspAccountLogger.log(Level.FINER, "Error closing rsTenant", e); } 
            try { if (pstmtTenant != null) pstmtTenant.close(); } catch (SQLException e) { jspAccountLogger.log(Level.FINER, "Error closing pstmtTenant", e); } 
            try { if (conn != null) conn.close(); } catch (SQLException e) { jspAccountLogger.log(Level.FINER, "Error closing DB connection", e); } 
        }
        return info;
    }
%>
<%
    HttpSession accountSession = request.getSession(false);
    String userFirstName = "User"; 
    String userLastName = "";
    String loggedInUserEmail = null; 
    String userPermissions = null;
    Integer sessionTenantId = null;

    if (accountSession != null) {
        userFirstName = (String) accountSession.getAttribute("UserFirstName");
        userLastName = (String) accountSession.getAttribute("UserLastName");
        loggedInUserEmail = (String) accountSession.getAttribute("Email");
        userPermissions = (String) accountSession.getAttribute("Permissions");
        sessionTenantId = (Integer) accountSession.getAttribute("TenantID");
    }

    if (sessionTenantId == null) { 
        if (accountSession != null) accountSession.invalidate();
        response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid session. Please log in again.", StandardCharsets.UTF_8.name()));
        return;
    }

    CompanyDisplayInfo companyInfo = getCompanyDisplayDetails(sessionTenantId);
    boolean isThePrimaryCompanyAdmin = false; // This variable is still useful for messages, even if not for button visibility
    if (loggedInUserEmail != null && 
        companyInfo.adminEmail != null && 
        !"N/A".equals(companyInfo.adminEmail) && 
        loggedInUserEmail.trim().equalsIgnoreCase(companyInfo.adminEmail.trim())) {
        isThePrimaryCompanyAdmin = true;
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Account Settings - YourTimeClock</title>
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <style>
        body { background-color: #f4f7f6; }
        .content-area.account-page-container { max-width: 800px; margin: 20px auto; padding: 25px 30px; background: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.08); }
        .content-area h1 { text-align: center; color: #333; font-weight: 400; font-size: 2.2em; border-bottom: 1px solid #e0e0e0; padding-bottom: 15px; margin-top: 0; margin-bottom: 30px; }
        .welcome-message { font-size: 1.0em; color: #555; margin-bottom: 25px; padding-bottom: 20px; border-bottom: 1px dashed #eee; line-height: 1.6; }
        .account-section { margin-bottom: 30px; }
        .account-section:last-child { margin-bottom: 0; }
        .account-section h2 { 
            font-size: 1.45em; color: #005A9C; margin-bottom: 18px; 
            font-weight: 500; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0;
            display: flex; justify-content: space-between; align-items: center; 
        }
        .account-section ul { list-style: none; padding-left: 5px; }
        .account-section ul li { margin-bottom: 12px; font-size: 1.0em; color: #444; display: flex; align-items: flex-start; }
        .account-section ul li i.fas { margin-right: 12px; color: #007bff; width: 20px; text-align: center; font-size: 1.1em; margin-top: 3px; }
        .account-section a { color: #007bff; text-decoration: none; font-weight: 500; }
        .account-section a:hover { text-decoration: underline; color: #0056b3; }
        .info-label { font-weight: 500; color: #333; min-width: 180px; display: inline-block; }
        .info-value { color: #555; }
        .placeholder-text { color: #777; font-style: italic; }
        .edit-details-btn { font-size: 0.7em; padding: 5px 10px; line-height: 1.5; margin-left: 10px; } 
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); }
        .modal-content { background-color: #fefefe; margin: 10% auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 80%; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); }
        .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee;}
        .modal-content h3 { font-size: 1.1em; color: #333; margin-top:15px; margin-bottom:10px; font-weight:500; }
        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; }
        .close:hover, .close:focus { color: black; text-decoration: none; cursor: pointer; }
        .form-item { margin-bottom: 15px; }
        .form-item label { display: block; margin-bottom: 5px; font-weight: 500; color: #333; }
        .form-item input[type="text"], .form-item input[type="email"], .form-item input[type="password"], .form-item input[type="tel"], .form-item select { width: calc(100% - 22px); padding: 10px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; }
        .form-item input[readonly] { background-color: #e9ecef; cursor: not-allowed; }
        .form-item input:disabled { background-color: #f0f0f0; color: #888; cursor: not-allowed; }
        .form-row { display: flex; justify-content: space-between; }
        .form-row .form-item { flex-basis: 48%; }
        .button-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
        .required-asterisk { color: red; margin-left: 3px; }
        hr { border: 0; border-top: 1px solid #eee; margin: 20px 0; }
    </style>
</head>
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>

    <div class="parent-container">
        <div class="content-area account-page-container">
            <h1>Account Settings</h1>

            <p class="welcome-message">
                Welcome, <strong><%= escapeJspHtml(userFirstName) %> <%= escapeJspHtml(userLastName) %></strong>!
                Manage your user settings and view company information below.<br>
                <% 
                   // Message can still depend on whether they are the primary admin or just a regular admin
                   if (isThePrimaryCompanyAdmin) {
                       out.print("As the <strong>Primary Company Administrator (" + escapeJspHtml(companyInfo.primaryAdminFullName) + ")</strong>, you can also manage company-level account settings critical for your organization.");
                   } else if ("Administrator".equalsIgnoreCase(userPermissions)) {
                       out.print("You have administrator privileges. Some company-level account settings can only be modified by the Primary Company Administrator: <strong>" + escapeJspHtml(companyInfo.primaryAdminFullName) + "</strong> (" + escapeJspHtml(companyInfo.adminEmail) + ").");
                   } else {
                       out.print("For critical account changes, please contact your company administrator.");
                   }
                %>
            </p>

            <div class="account-section">
                <h2>
                    Company Details
                    <%-- Button is now always visible to logged-in users --%>
                    <button type="button" id="editCompanyDetailsBtn" class="glossy-button text-orange edit-details-btn">
                        <i class="fas fa-edit"></i> Edit Details
                    </button>
                </h2>
                <ul>
                    <li><i class="fas fa-briefcase"></i><span class="info-label">Company Name:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyName) %></span></li>
                    <li><i class="fas fa-hashtag"></i><span class="info-label">Company ID:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyIdentifier) %></span></li>
                    <li><i class="fas fa-at"></i><span class="info-label">Primary Admin Email:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.adminEmail) %></span></li>
                    <li><i class="fas fa-user-tie"></i><span class="info-label">Primary Admin Name:</span> <span class="info-value" id="displayPrimaryAdminName"><%= escapeJspHtml(companyInfo.primaryAdminFullName) %></span></li>
                    <li><i class="fas fa-phone-alt"></i><span class="info-label">Company Phone:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyPhone) %></span></li>
                    <li>
                        <i class="fas fa-map-marker-alt"></i><span class="info-label">Company Address:</span>
                        <span class="info-value">
                            <% 
                               boolean hasAddressPart = false; StringBuilder fullAddress = new StringBuilder();
                               if (companyInfo.companyAddress != null && !"N/A".equals(companyInfo.companyAddress) && !companyInfo.companyAddress.trim().isEmpty()) { fullAddress.append(escapeJspHtml(companyInfo.companyAddress)); hasAddressPart = true; }
                               String cityStateZip = "";
                               if (companyInfo.companyCity != null && !"N/A".equals(companyInfo.companyCity) && !companyInfo.companyCity.trim().isEmpty()) cityStateZip += escapeJspHtml(companyInfo.companyCity);
                               if (companyInfo.companyState != null && !"N/A".equals(companyInfo.companyState) && !companyInfo.companyState.trim().isEmpty()) cityStateZip += (cityStateZip.isEmpty() ? "" : ", ") + escapeJspHtml(companyInfo.companyState);
                               if (companyInfo.companyZip != null && !"N/A".equals(companyInfo.companyZip) && !companyInfo.companyZip.trim().isEmpty()) cityStateZip += (cityStateZip.isEmpty() ? "" : " ") + escapeJspHtml(companyInfo.companyZip);
                               if (!cityStateZip.isEmpty()) { if (hasAddressPart) fullAddress.append("<br>"); fullAddress.append(cityStateZip); hasAddressPart = true; }
                               if (hasAddressPart) { out.print(fullAddress.toString()); } else { out.print("<span class='placeholder-text'>Not Provided</span>"); }
                            %>
                        </span>
                    </li>
                </ul>
            </div>

            <div class="account-section">
                <h2>
                    Account Management
                    <%-- Button is now always visible to logged-in users --%>
                    <button type="button" id="editAccountLoginBtn" class="glossy-button text-orange edit-details-btn">
                        <i class="fas fa-user-cog"></i> Edit Login Details
                    </button>
                </h2>
                <ul>
                    <li><i class="fas fa-user-circle"></i><span class="info-label">Your Login Email:</span> <span class="info-value"><%= (loggedInUserEmail != null ? escapeJspHtml(loggedInUserEmail) : "N/A") %></span></li>
                     <%-- Display Company Admin login info for clarity --%>
                     <li><i class="fas fa-envelope-open-text"></i><span class="info-label">Company Admin Login:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.adminEmail) %></span></li>
                </ul>
            </div>

            <div class="account-section">
                <h2>Billing & Subscription</h2>
                <ul><li><i class="fas fa-credit-card"></i><span class="placeholder-text">Payment method and subscription management (Coming Soon).</span></li></ul>
            </div>
        </div>
    </div>

    <%-- Modals --%>
    <div id="verifyAdminPasswordModal" class="modal"> <div class="modal-content" style="max-width: 450px;"> <span class="close" data-close-modal>&times;</span> <h2>Verify Administrator Credentials</h2> <form id="verifyAdminPasswordForm"> <div class="form-item"> <label for="verifyAdminEmail">Admin Email:</label> <input type="email" id="verifyAdminEmail" name="verifyAdminEmail" readonly> </div> <div class="form-item"> <label for="verifyAdminCurrentPassword">Current Company Admin Password:<span class="required-asterisk">*</span></label> <input type="password" id="verifyAdminCurrentPassword" name="currentPassword" required autofocus> </div> </form> <div class="button-row"> <button type="submit" form="verifyAdminPasswordForm" class="glossy-button text-green"><i class="fas fa-check-circle"></i> Verify</button> <button type="button" data-close-modal class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button> </div> </div> </div>
    
    <div id="editCompanyDetailsModal" class="modal"> 
        <div class="modal-content" style="max-width: 650px;"> 
            <span class="close" data-close-modal>&times;</span> 
            <h2>Edit Company Details</h2> 
            <form id="updateCompanyDetailsForm"> 
                <div class="form-item"> <label for="editCompanyNameDisplay">Company Name:</label> <input type="text" id="editCompanyNameDisplay" name="companyNameDisplay" readonly disabled> </div> 
                <div class="form-item"> <label for="editCompanyIdDisplay">Company ID:</label> <input type="text" id="editCompanyIdDisplay" name="companyIdDisplay" readonly disabled> </div> 
                <hr> 
                <div class="form-item"> <label for="editCompanyPhone">Company Phone:</label> <input type="tel" id="editCompanyPhone" name="companyPhone" maxlength="20" pattern="^[0-9\\s()+\\-]*$" title="Digits and ()-+"> </div> 
                <div class="form-item"> <label for="editCompanyAddress">Street Address:</label> <input type="text" id="editCompanyAddress" name="companyAddress" maxlength="255"> </div> 
                <div class="form-row"> 
                    <div class="form-item"> <label for="editCompanyCity">City:</label> <input type="text" id="editCompanyCity" name="companyCity" maxlength="100"> </div> 
                    <div class="form-item"> <label for="editCompanyState">State:</label> <select id="editCompanyState" name="companyState"> <option value="">Select State</option> <%@ include file="/WEB-INF/includes/states_options.jspf" %> </select> </div> 
                </div> 
                <div class="form-item"> 
                    <label for="editCompanyZip">Zip Code:</label> 
                    <%-- CORRECTED ZIP CODE PATTERN --%>
                    <input type="text" id="editCompanyZip" name="companyZip" pattern="^\d{5}(?:[-\s]\d{4})?$" title="Enter 5 digits, or 5 digits, a hyphen or space, and then 4 digits (e.g., 12345 or 12345-6789 or 12345 6789)" maxlength="10"> 
                </div> 
            </form> 
            <div class="button-row"> 
                <button type="submit" form="updateCompanyDetailsForm" class="glossy-button text-green"><i class="fas fa-save"></i> Update Details</button> 
                <button type="button" data-close-modal class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button> 
            </div> 
        </div> 
    </div>
    
    <%-- Modal for Editing Account Login Details (Restructured) --%>
    <div id="editAccountLoginModal" class="modal">
        <div class="modal-content" style="max-width: 550px;">
            <span class="close" data-close-modal>&times;</span>
            <h2>Edit Account Login Details</h2>
            
            <div id="verifyPasswordSectionForLoginChange">
                <p style="font-size: 0.9em; margin-bottom: 15px; color: #555;">
                    To make changes to the primary company administrator's login email or password, please first verify the current company administrator password.
                </p>
                <div class="form-item">
                    <label for="verifyCurrentAdminPasswordForLoginChange">Current Company Admin Password:<span class="required-asterisk">*</span></label>
                    <input type="password" id="verifyCurrentAdminPasswordForLoginChange" name="currentPasswordModalVerify" required>
                </div>
                <div class="button-row" style="margin-top:15px; margin-bottom: 20px;">
                    <button type="button" id="triggerVerifyAdminPasswordForLoginChange" class="glossy-button text-blue"><i class="fas fa-shield-alt"></i> Verify Password</button>
                </div>
            </div>
            <hr id="editAccountLoginSeparator" style="display:none; margin: 20px 0;">

            <form id="updateAccountLoginForm" style="display:none;">
                 <input type="hidden" id="verifiedCurrentPassword" name="currentPassword">

                <div class="form-item">
                    <label for="currentAdminLoginEmail">Current Primary Admin Email:</label>
                    <input type="email" id="currentAdminLoginEmail" name="currentAdminLoginEmailDisplay" readonly>
                </div>
                
                <h3>Change Login Email</h3>
                <div class="form-item">
                    <label for="newAdminLoginEmail">New Primary Admin Email:</label>
                    <input type="email" id="newAdminLoginEmail" name="newAdminLoginEmail" placeholder="Leave blank if no change" disabled>
                </div>
                <div class="form-item">
                    <label for="confirmNewAdminLoginEmail">Confirm New Primary Admin Email:</label>
                    <input type="email" id="confirmNewAdminLoginEmail" name="confirmNewAdminLoginEmail" placeholder="Confirm if changing email" disabled>
                </div>
                
                <h3>Change Password</h3>
                 <div class="form-item">
                    <label for="newAdminPassword">New Company Admin Password:</label>
                    <input type="password" id="newAdminPassword" name="newAdminPassword" placeholder="Leave blank if no change" disabled>
                </div>
                <div class="form-item">
                    <label for="confirmNewAdminPassword">Confirm New Company Admin Password:</label>
                    <input type="password" id="confirmNewAdminPassword" name="confirmNewAdminPassword" placeholder="Confirm if changing password" disabled>
                </div>
                
                <div class="button-row" style="margin-top:25px;">
                    <button type="submit" id="saveAccountChangesBtn" class="glossy-button text-green" disabled><i class="fas fa-save"></i> Save Account Changes</button>
                    <button type="button" data-close-modal class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button>
                </div>
            </form>
        </div>
    </div>

    <div id="notificationModalGeneral" class="modal"><div class="modal-content" style="max-width: 480px;"><span class="close" data-close-modal>&times;</span><h2 id="notificationModalGeneralTitle">Notification</h2><p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center; line-height:1.6;"></p><div class="button-row" style="justify-content: center;"><button type="button" data-close-modal id="okButtonNotificationModalGeneral" class="glossy-button text-blue"><i class="fas fa-thumbs-up"></i> OK</button></div></div></div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript">
        window.primaryCompanyAdminEmail = "<%= companyInfo.adminEmail != null && !"N/A".equals(companyInfo.adminEmail) ? escapeForJavaScriptString(companyInfo.adminEmail.trim()) : "" %>";
        // isUserThePrimaryCompanyAdmin is still useful for controlling messages, even if not button visibility
        window.isUserThePrimaryCompanyAdmin = <%= isThePrimaryCompanyAdmin %>; 
    </script>
    <script src="${pageContext.request.contextPath}/js/account.js?v=<%= System.currentTimeMillis() %>"></script> 
</body>
</html>