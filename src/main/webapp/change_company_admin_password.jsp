<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.sql.Connection" %>
<%@ page import="java.sql.PreparedStatement" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.sql.SQLException" %>
<%@ page import="timeclock.db.DatabaseConnection" %>

<%!
    // Helper to fetch AdminEmail for pre-population
    private String getAdminEmailForTenant(Integer tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return "N/A";
        }
        String adminEmail = "N/A";
        String sql = "SELECT AdminEmail FROM tenants WHERE TenantID = ?";
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) {
                System.err.println("getAdminEmailForTenant: Failed to get DB connection.");
                return adminEmail;
            }
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, tenantId);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                adminEmail = rs.getString("AdminEmail");
            }
        } catch (SQLException e) {
            System.err.println("SQLException in getAdminEmailForTenant for TenantID " + tenantId + ": " + e.getMessage());
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) { /* ignore */ }
            try { if (pstmt != null) pstmt.close(); } catch (SQLException e) { /* ignore */ }
            try { if (conn != null) conn.close(); } catch (SQLException e) { /* ignore */ }
        }
        return adminEmail;
    }
%>
<%
    HttpSession currentSession = request.getSession(false);
    Integer tenantId = null;
    String adminEmailForForm = "N/A";
    if (currentSession != null && currentSession.getAttribute("TenantID") != null) {
        tenantId = (Integer) currentSession.getAttribute("TenantID");
        adminEmailForForm = getAdminEmailForTenant(tenantId); // Fetch admin email for display
    } else {
        // If no valid session or tenantId, redirect to login
        if (currentSession != null) currentSession.invalidate();
        response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Session expired or invalid. Please log in.", StandardCharsets.UTF_8.name()));
        return;
    }

    String errorMessage = (String) session.getAttribute("errorMessage");
    if (errorMessage != null) {
        session.removeAttribute("errorMessage");
    }
    String successMessage = (String) session.getAttribute("successMessage");
    if (successMessage != null) {
        session.removeAttribute("successMessage");
    }
%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Change Company Admin Password - YourTimeClock</title>
    
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="css/login.css?v=7"> 
    
    <style>
        .change-password-container { max-width: 500px; }
        .login-header h1 { font-size: 1.5em; margin-bottom: 10px;}
        .login-form .form-group input[readonly] { background-color: #e9ecef; cursor: not-allowed; }
        .page-message { padding: 10px 15px; margin: 0 auto 20px auto; border-radius: 4px; text-align: center; max-width: calc(100% - 30px); font-size: 0.9em; }
        .error-message { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .success-message { background-color: #d1e7dd; color: #0f5132; border: 1px solid #badbcc; }
    </style>
</head>
<body>
    <div class="login-container change-password-container">
        <div class="login-header">
            <a href="index.jsp" class="logo-link"><i class="fas fa-clock"></i> YourTimeClock</a>
            <h1>Change Company Admin Password</h1>
            <p>Update the main administrator password for your company account.</p>
        </div>

        <% if (errorMessage != null && !errorMessage.isEmpty()) { %>
             <p class="error-message login-page-message"><%= errorMessage.replace("<", "&lt;").replace(">", "&gt;") %></p>
        <% } %>
        <% if (successMessage != null && !successMessage.isEmpty()) { %>
            <p class="success-message login-page-message"><%= successMessage.replace("<", "&lt;").replace(">", "&gt;") %></p>
        <% } %>

        <form action="ChangeCompanyAdminPasswordServlet" method="POST" id="changeCompanyAdminPasswordForm" class="login-form">
            <div class="form-group">
                 <label for="adminUsername">Admin Username (Email)</label>
                <input type="email" id="adminUsername" name="adminUsername" value="<%= adminEmailForForm %>" readonly>
            </div>
            <div class="form-group">
                <label for="currentPassword">Current Admin Password <span class="required">*</span></label>
                <input type="password" id="currentPassword" name="currentPassword" required autocomplete="current-password" autofocus>
            </div>
            <div class="form-group">
                <label for="newPassword">New Admin Password <span class="required">*</span></label>
                <input type="password" id="newPassword" name="newPassword" required data-minlength="8" autocomplete="new-password">
                <small>Minimum 8 characters.</small>
            </div>
            <div class="form-group">
                <label for="confirmNewPassword">Confirm New Admin Password <span class="required">*</span></label>
                <input type="password" id="confirmNewPassword" name="confirmNewPassword" required data-minlength="8" autocomplete="new-password">
            </div>
            <div class="form-actions">
                 <%-- FIX: Simplified the button class to ensure consistent styling. --%>
                 <button type="submit" class="glossy-button text-green">
                    <i class="fas fa-save"></i> Update Password
                </button>
            </div>
             <p class="signup-redirect" style="margin-top: 25px;">
                <a href="account.jsp"><i class="fas fa-arrow-left"></i> Back to Account Settings</a>
             </p>
        </form>
    </div>

    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script type="text/javascript">
        document.addEventListener('DOMContentLoaded', function() {
            const form = document.getElementById('changeCompanyAdminPasswordForm');
            const newPasswordField = document.getElementById('newPassword');
            const confirmPasswordField = document.getElementById('confirmNewPassword');
            const currentPasswordField = document.getElementById('currentPassword');

            if (currentPasswordField) currentPasswordField.focus();
            if (form) {
                form.addEventListener('submit', function(event) {
                    let isValid = true;
                    const newPass = newPasswordField.value;
                    const confirmPass = confirmPasswordField.value;

                    if (newPass.length < 8) {
                        alert("New password must be at least 8 characters long.");
                        newPasswordField.focus();
                        isValid = false;
                    } else if (newPass !== confirmPass) {
                        alert("New passwords do not match.");
                        confirmPasswordField.focus();
                        isValid = false;
                    }

                    if (!isValid) {
                        event.preventDefault();
                    } else {
                        const submitButton = form.querySelector('button[type="submit"]');
                        if (submitButton) {
                            submitButton.disabled = true;
                            submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Updating...';
                        }
                    }
                });
            }
             const urlParams = new URLSearchParams(window.location.search);
            if (urlParams.has('error') || urlParams.has('message')) {
                if (typeof clearUrlParams === 'function') { clearUrlParams(['error', 'message']); }
                else {
                    try { window.history.replaceState({}, document.title, window.location.pathname); }
                    catch (e) { console.warn("Could not clear URL params via history API."); }
                }
            }
        });
    </script>
</body>
</html>