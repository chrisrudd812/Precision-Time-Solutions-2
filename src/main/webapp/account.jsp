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
        String companyName = ""; String companyIdentifier = "";
        String adminEmail = ""; String companyPhone = ""; 
        String companyAddress = ""; String companyCity = "";    
        String companyState = ""; String companyZip = "";
        String primaryAdminFullName = "";
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
                    
                    if (info.adminEmail != null && !info.adminEmail.trim().isEmpty()) {
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
        } catch (SQLException e) {
            // Database error occurred
        } 
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
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/account.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="account-page">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <div class="parent-container">
        <h1><i class="fas fa-user-cog"></i> Account Settings</h1>
        
        <div id="subscriptionStatusMessage" style="display: none;"></div>
        
        <% if (pageSuccessMessage != null) { %><div class="page-message success-message"><%= escapeJspHtml(pageSuccessMessage) %></div><% } %>
        <% if (pageErrorMessage != null) { %><div class="page-message error-message"><%= escapeJspHtml(pageErrorMessage) %></div><% } %>
        
        <div class="details-section">
            <h2>Company Details <button type="button" id="editCompanyDetailsBtn" class="glossy-button text-orange"><i class="fas fa-edit"></i> Edit</button></h2>
            <ul>
                <li><i class="fas fa-briefcase"></i><span class="info-label">Company Name:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyName) %></span></li>
                <li><i class="fas fa-hashtag"></i><span class="info-label">Company ID:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.companyIdentifier) %></span></li>
                <%-- MODIFIED: Added full address display and IDs for JS updates --%>
                <li><i class="fas fa-map-marker-alt"></i><span class="info-label">Address:</span> <span id="displayCompanyAddress" class="info-value"><%= escapeJspHtml(companyInfo.companyAddress) %>, <%= escapeJspHtml(companyInfo.companyCity) %>, <%= escapeJspHtml(companyInfo.companyState) %> <%= escapeJspHtml(companyInfo.companyZip) %></span></li>
                <li><i class="fas fa-phone-alt"></i><span class="info-label">Company Phone:</span> <span id="displayCompanyPhone" class="info-value"><%= escapeJspHtml(companyInfo.companyPhone) %></span></li>
            </ul>
        </div>
        
        <div class="details-section">
            <h2>Account Management</h2>
            <ul>
                <li><i class="fas fa-user-circle"></i><span class="info-label">Your Login Email:</span> <span class="info-value"><%= escapeJspHtml((loggedInUserEmail != null && !loggedInUserEmail.isEmpty()) ? loggedInUserEmail : companyInfo.adminEmail) %></span></li>
                <li><i class="fas fa-envelope-open-text"></i><span class="info-label">Company Admin Email:</span> <span class="info-value"><%= escapeJspHtml(companyInfo.adminEmail) %></span></li>
            </ul>
        </div>
        
        <div class="details-section">
            <h2>Subscription</h2>
            <p class="section-description">Manage your subscription plan, view invoices, and update your payment method via our secure billing portal.</p>
            <p class="section-description note"><strong>Note:</strong> Subscription and billing can only be managed by the primary company administrator.</p>
            <div style="padding-left: 5px; margin-top: 15px;">
                <button type="button" id="manageBillingBtn" class="glossy-button text-blue" autofocus><i class="fas fa-credit-card"></i> Manage Subscription and Billing</button>
                <form id="stripePortalForm" action="StripePortalServlet" method="POST" style="display:none;"></form>
            </div>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/account-modals.jspf" %>
    <%@ include file="/WEB-INF/includes/modals.jspf" %>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    
    <script type="text/javascript">
        <%
            boolean showBillingModal = (accountSession != null && accountSession.getAttribute("showBillingModal") != null && (Boolean) accountSession.getAttribute("showBillingModal"));
            String billingModalMessage = (accountSession != null) ? (String) accountSession.getAttribute("billingModalMessage") : "Your subscription requires attention.";
            if (billingModalMessage == null) billingModalMessage = "Your subscription requires attention.";
            String subscriptionStatus = (accountSession != null) ? (String) accountSession.getAttribute("SubscriptionStatus") : "";

            if (accountSession != null) {
                accountSession.removeAttribute("showBillingModal");
                accountSession.removeAttribute("billingModalMessage");
            }
        %>
        var shouldShowBillingModal = <%= showBillingModal %>;
        var billingMessage = "<%= escapeForJavaScriptString(billingModalMessage) %>";
        var currentSubscriptionStatus = "<%= escapeForJavaScriptString(subscriptionStatus) %>";
        window.primaryCompanyAdminEmail = "<%= companyInfo.adminEmail != null ? escapeForJavaScriptString(companyInfo.adminEmail.trim()) : "" %>";
    </script>
    
    <script src="${pageContext.request.contextPath}/js/account.js?v=<%= System.currentTimeMillis() %>"></script> 
</body>
</html>