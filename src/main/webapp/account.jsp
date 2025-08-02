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
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String escapeForJavaScriptString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t").replace("/", "\\/");
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
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmtTenant = conn.prepareStatement(sqlTenant)) {
            pstmtTenant.setInt(1, tenantId);
            try(ResultSet rsTenant = pstmtTenant.executeQuery()) {
                if (rsTenant.next()) {
                    info.companyName = rsTenant.getString("CompanyName");
                    info.companyIdentifier = rsTenant.getString("CompanyIdentifier");
                    info.adminEmail = rsTenant.getString("AdminEmail");
                    info.companyPhone = rsTenant.getString("PhoneNumber");
                    info.companyAddress = rsTenant.getString("Address");
                    info.companyCity = rsTenant.getString("City");
                    info.companyState = rsTenant.getString("State");
                    info.companyZip = rsTenant.getString("ZipCode");
                    
                    if (info.adminEmail != null && !info.adminEmail.trim().isEmpty() && !"N/A".equals(info.adminEmail)) {
                        try (PreparedStatement pstmtAdminName = conn.prepareStatement(sqlAdminName)) {
                            pstmtAdminName.setInt(1, tenantId);
                            pstmtAdminName.setString(2, info.adminEmail.trim());
                            try(ResultSet rsAdminName = pstmtAdminName.executeQuery()) {
                                if (rsAdminName.next()) {
                                    String firstName = rsAdminName.getString("FIRST_NAME");
                                    String lastName = rsAdminName.getString("LAST_NAME"); 
                                    info.primaryAdminFullName = ((firstName != null ? firstName.trim() : "") + " " + (lastName != null ? lastName.trim() : "")).trim();
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) { jspAccountLogger.log(Level.SEVERE, "SQLException in getCompanyDisplayDetails for TenantID " + tenantId, e); } 
        return info;
    }
%>
<%
    HttpSession accountSession = request.getSession(false);
    String userFirstName = "User"; 
    String userLastName = "";
    String loggedInUserEmail = null; 
    Integer sessionTenantId = null;
    String pageErrorMessage = (String) accountSession.getAttribute("errorMessage");
    String pageSuccessMessage = (String) accountSession.getAttribute("successMessage");
    if(pageErrorMessage != null) accountSession.removeAttribute("errorMessage");
    if(pageSuccessMessage != null) accountSession.removeAttribute("successMessage");

    if (accountSession != null) {
        userFirstName = (String) accountSession.getAttribute("UserFirstName");
        userLastName = (String) accountSession.getAttribute("UserLastName");
        loggedInUserEmail = (String) accountSession.getAttribute("Email");
        sessionTenantId = (Integer) accountSession.getAttribute("TenantID");
    }

    if (sessionTenantId == null) { 
        response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid session.", StandardCharsets.UTF_8.name()));
        return;
    }

    CompanyDisplayInfo companyInfo = getCompanyDisplayDetails(sessionTenantId);
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Account Settings - Precision Time Solutions</title>
    <link rel="stylesheet" href="css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="css/reports.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        body { background-image: url("Images/background2.png"); }
        .content-area.account-page-container { max-width: 800px; margin: 3% auto; padding: 25px 30px; background: #fff; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.08); opacity: 0.93; }
        .content-area h1 { text-align: center; color: #333; font-weight: 400; font-size: 2.2em; border-bottom: 1px solid #e0e0e0; padding-bottom: 15px; margin-top: 0; margin-bottom: 30px; }
        .account-section { margin-bottom: 30px; }
        .account-section h2 { font-size: 1.45em; color: #005A9C; margin-bottom: 18px; font-weight: 500; padding-bottom: 8px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
        .account-section ul { list-style: none; padding-left: 5px; margin-top: 0; }
        .account-section ul li { margin-bottom: 12px; font-size: 1.0em; color: #444; display: flex; align-items: flex-start; }
        .account-section ul li i.fas { margin-right: 12px; color: #007bff; width: 20px; text-align: center; font-size: 1.1em; margin-top: 3px; }
        .info-label { font-weight: 500; color: #333; min-width: 180px; display: inline-block; }
        .info-value { color: #555; }
        .edit-details-btn { font-size: 0.7em; padding: 5px 10px; }
        .page-message { padding: 10px 15px; margin: -10px auto 20px auto; border-radius: 4px; text-align: center; }
        .success-message { background-color: #d4edda; color: #155724; border:1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border:1px solid #f5c6cb; }
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); }
        .modal-content { background-color: #fefefe; margin: 10% auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 80%; max-width: 500px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.19); }
        .modal-content h2 { margin-top: 0; }
        .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor: pointer; }
        .form-item { margin-bottom: 15px; }
        .form-item label { display: block; margin-bottom: 5px; font-weight: 500; }
        .form-item input, .form-item select { width: 100%; padding: 10px; border: 1px solid #ccc; border-radius: 4px; box-sizing: border-box; }
        .button-row { display: flex; justify-content: flex-end; gap: 10px; margin-top: 20px; }
        .form-row { display: flex; gap: 20px; }
        .form-row .form-item { flex: 1; }
        .card-icons { display: flex; align-items: center; gap: 8px; }
        .card-icons img { height: 24px; width: auto; border-radius: 3px; }
        .gpay-button { background-color: #000; color: #fff; border: none; border-radius: 28px; padding: 10px 24px; cursor: pointer; display: inline-flex; align-items: center; justify-content: center; min-width: 200px; transition: background-color 0.3s; }
        .gpay-button:hover { background-color: #333; }
        .gpay-button img { height: 24px; }
        .separator { display: flex; align-items: center; text-align: center; color: #aaa; margin: 25px 0; }
        .separator hr { flex-grow: 1; border: none; border-top: 1px solid #eee; }
        .separator span { padding: 0 10px; font-size: 0.9em; }
    </style>
</head>
<body>
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <div class="parent-container">
        <div class="content-area account-page-container"> 
            <h1>Account Settings</h1>
            <% if (pageSuccessMessage != null) { %><div class="page-message success-message"><%= escapeJspHtml(pageSuccessMessage) %></div><% } %>
            <% if (pageErrorMessage != null) { %><div class="page-message error-message"><%= escapeJspHtml(pageErrorMessage) %></div><% } %>
            
            <div class="account-section">
                <h2>Company Details <button type="button" id="editCompanyDetailsBtn" class="glossy-button text-orange edit-details-btn"><i class="fas fa-edit"></i> Edit</button></h2>
                <ul>
                    <li><i class="fas fa-briefcase"></i><span class="info-label">Company Name:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyName) %></span></li>
                    <li><i class="fas fa-hashtag"></i><span class="info-label">Company ID:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyIdentifier) %></span></li>
                    <li><i class="fas fa-at"></i><span class="info-label">Primary Admin Email:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.adminEmail) %></span></li>
                    <li><i class="fas fa-user-tie"></i><span class="info-label">Primary Admin Name:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.primaryAdminFullName) %></span></li>
                    <li><i class="fas fa-phone-alt"></i><span class="info-label">Company Phone:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyPhone) %></span></li>
                </ul>
            </div>
            <div class="account-section">
                <h2>Account Management <button type="button" id="editAccountLoginBtn" class="glossy-button text-orange edit-details-btn"><i class="fas fa-user-cog"></i> Edit</button></h2>
                <ul>
                    <li><i class="fas fa-user-circle"></i><span class="info-label">Your Login Email:</span> <span class="info-value"><%= escapeJspHtml(loggedInUserEmail) %></span></li>
                    <li><i class="fas fa-envelope-open-text"></i><span class="info-label">Company Admin Login:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.adminEmail) %></span></li>
                </ul>
            </div>

            <div class="account-section">
                <h2>
                    Billing & Subscription
                    <button type="button" id="manageBillingBtn" class="glossy-button text-orange edit-details-btn">
                        <i class="fas fa-credit-card"></i> Edit
                    </button>
                </h2>
                <p style="padding-left: 5px; margin-top: -10px; color: #444;">Manage your subscription plan and update your payment method.</p>
            </div>
        </div>
    </div>

    <!-- MODALS -->
    <div id="verifyAdminPasswordModal" class="modal">
        <div class="modal-content">
            <span class="close" data-close-modal>&times;</span>
            <h2>Verify Administrator Credentials</h2>
            <p style="font-size:0.9em; color:#555; margin-bottom:15px;">For security, please re-enter the Company Admin password.</p>
            <form id="verifyAdminPasswordForm">
                <input type="hidden" id="verificationNextAction" value="">
                <div class="form-item">
                    <label for="verifyAdminEmail">Admin Email:</label>
                    <input type="email" id="verifyAdminEmail" readonly>
                </div>
                <div class="form-item">
                    <label for="verifyAdminCurrentPassword">Password:<span style="color:red;">*</span></label>
                    <input type="password" id="verifyAdminCurrentPassword" required autofocus>
                </div>
                <div class="button-row">
                    <button type="submit" class="glossy-button text-green">Verify</button>
                    <button type="button" data-close-modal class="cancel-btn glossy-button text-red">Cancel</button>
                </div>
            </form>
        </div>
    </div>
    
    <div id="billingModal" class="modal">
        <div class="modal-content" style="max-width: 500px;">
            <span class="close" data-close-modal>&times;</span>
            <h2>Billing Information</h2>
            
            <div class="gpay-section" style="margin-top: 10px; margin-bottom: 25px; text-align: center;">
                 <button type="button" id="gpay-button" class="gpay-button">
                     <img src="https://upload.wikimedia.org/wikipedia/commons/f/f2/Google_Pay_Logo.svg" alt="Google Pay" onerror="this.style.display='none'">
                 </button>
            </div>

            <div class="separator"><hr><span>OR</span><hr></div>
            
            <p style="text-align:center; color: #555; margin-top: -5px; margin-bottom: 20px;">Enter card details manually below.</p>
            
            <form id="billingForm" action="BillingServlet" method="POST">
                <div class="form-item">
                    <label for="cardholderName">Name on Card</label>
                    <input type="text" id="cardholderName" name="cardholderName" required autocomplete="cc-name">
                </div>
                <div class="form-item">
                    <label for="cardNumber">Card Number</label>
                    <input type="text" id="cardNumber" name="cardNumber" required autocomplete="cc-number" placeholder="•••• •••• •••• 1234" title="Enter a valid card number">
                </div>
                <div class="form-row">
                    <div class="form-item">
                        <label for="expiryDate">Expiry Date (MM/YY)</label>
                        <input type="text" id="expiryDate" name="expiryDate" required autocomplete="cc-exp" placeholder="MM/YY" pattern="(0[1-9]|1[0-2])\/?([0-9]{2})">
                    </div>
                    <div class="form-item">
                        <label for="cvc">CVC</label>
                        <input type="text" id="cvc" name="cvc" required autocomplete="cc-csc" placeholder="•••" pattern="[0-9]{3,4}">
                    </div>
                </div>
                <div class="button-row" style="justify-content: space-between; align-items: center;">
                    <div class="card-icons">
                        <img src="https://js.stripe.com/v3/fingerprinted/img/visa-729c05c240c4e15718a3293e10e3d6c1.svg" alt="Visa" onerror="this.style.display='none'"/>
                        <img src="https://js.stripe.com/v3/fingerprinted/img/mastercard-4d8844094130711885b5e41b28c9848f.svg" alt="Mastercard" onerror="this.style.display='none'"/>
                        <img src="https://js.stripe.com/v3/fingerprinted/img/amex-a49b82f46c5cd6a96a57925b46378a5a.svg" alt="American Express" onerror="this.style.display='none'"/>
                    </div>
                    <button type="submit" class="glossy-button text-green">
                        <i class="fas fa-save"></i> Save Payment Method
                    </button>
                </div>
            </form>
        </div>
    </div>

    <div id="editCompanyDetailsModal" class="modal">
        <div class="modal-content" style="max-width: 650px;"> 
            <span class="close" data-close-modal>&times;</span> 
            <h2>Edit Company Details</h2> 
            <form id="updateCompanyDetailsForm"> 
                <div class="form-item"> <label for="editCompanyNameDisplay">Company Name:</label> <input type="text" id="editCompanyNameDisplay" name="companyNameDisplay" readonly disabled> </div> 
                <div class="form-item"> <label for="editCompanyIdDisplay">Company ID:</label> <input type="text" id="editCompanyIdDisplay" name="companyIdDisplay" readonly disabled> </div> 
                <hr> 
                <div class="form-item"> <label for="editCompanyPhone">Company Phone:</label> <input type="tel" id="editCompanyPhone" name="companyPhone" maxlength="20"> </div> 
                <div class="form-item"> <label for="editCompanyAddress">Street Address:</label> <input type="text" id="editCompanyAddress" name="companyAddress" maxlength="255"> </div> 
                <div class="form-row"> 
                    <div class="form-item"> <label for="editCompanyCity">City:</label> <input type="text" id="editCompanyCity" name="companyCity" maxlength="100"> </div> 
                    <div class="form-item"> <label for="editCompanyState">State:</label> <select id="editCompanyState" name="companyState"> <option value="">Select State</option> <%@ include file="/WEB-INF/includes/states_options.jspf" %> </select> </div> 
                </div> 
                <div class="form-item"> 
                    <label for="editCompanyZip">Zip Code:</label> 
                    <input type="text" id="editCompanyZip" name="companyZip" maxlength="10"> 
                </div> 
                <div class="button-row"> 
                    <button type="submit" class="glossy-button text-green"><i class="fas fa-save"></i> Update Details</button> 
                    <button type="button" data-close-modal class="cancel-btn glossy-button text-red"><i class="fas fa-times"></i> Cancel</button> 
                </div> 
            </form> 
        </div> 
    </div>
    
    <div id="editAccountLoginModal" class="modal">
        <div class="modal-content" style="max-width: 550px;">
            <span class="close" data-close-modal>&times;</span>
            <h2>Edit Account Login Details</h2>
            <p>Forms for editing account login will be populated here.</p>
        </div>
    </div>
    
    <div id="notificationModalGeneral" class="modal">
        <div class="modal-content" style="max-width: 480px;">
            <span class="close" data-close-modal>&times;</span>
            <h2 id="notificationModalGeneralTitle">Notification</h2>
            <p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center; line-height:1.6;"></p>
            <div class="button-row" style="justify-content: center;">
                <button type="button" data-close-modal class="glossy-button text-blue">OK</button>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript">
        window.primaryCompanyAdminEmail = "<%= companyInfo.adminEmail != null ? escapeForJavaScriptString(companyInfo.adminEmail.trim()) : "" %>";
    </script>
    <script src="${pageContext.request.contextPath}/js/account.js?v=<%= System.currentTimeMillis() %>"></script> 
</body>
</html>
