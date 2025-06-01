package timeclock.auth; // Make sure this matches your actual package structure

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection; // Your DB connection class
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern; // For email validation

import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt; // For password hashing

@WebServlet("/CompanyAccountServlet")
public class CompanyAccountServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CompanyAccountServlet.class.getName());
    private static final int MIN_ADMIN_PASSWORD_LENGTH = 8; // Define for new admin password

    // Basic email validation pattern
    private static final Pattern VALID_EMAIL_ADDRESS_REGEX =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE);

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("TenantID") != null) {
            return (Integer) session.getAttribute("TenantID");
        }
        return null;
    }

    private String getLoggedInUserEmailFromSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("Email") != null) {
            return (String) session.getAttribute("Email");
        }
        return null;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();
        Integer tenantId = getTenantId(request);
        String loggedInUserEmail = getLoggedInUserEmailFromSession(request);

        if (tenantId == null || loggedInUserEmail == null) {
            jsonResponse.put("success", false).put("error", "Session expired or invalid. Please log in again.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        String action = request.getParameter("action");
        logger.info("[CompanyAccountServlet] POST action: " + action + " for T:" + tenantId + " by User: " + loggedInUserEmail);

        try {
            // Centralized security check for actions requiring primary admin privileges
            if ("updateCompanyDetails".equals(action) || 
                "updateAdminLoginDetails".equals(action) || 
                "verifyAdminPassword".equals(action)) { // verifyAdminPassword is also a sensitive precursor
                if (!isUserPrimaryAdmin(tenantId, loggedInUserEmail)) {
                    logger.warning("Unauthorized attempt for action '" + action + "' by non-primary admin: " + loggedInUserEmail + " for T:" + tenantId);
                    jsonResponse.put("success", false).put("error", "Access Denied: This action can only be performed by the primary company administrator.");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    out.print(jsonResponse.toString());
                    out.flush();
                    return;
                }
            }

            if ("verifyAdminPassword".equals(action)) {
                verifyCompanyAdminPassword(request, tenantId, loggedInUserEmail, jsonResponse);
            } else if ("updateCompanyDetails".equals(action)) {
                updateCompanyDetails(request, tenantId, jsonResponse);
            } else if ("updateAdminLoginDetails".equals(action)) {
                updateAdminLoginDetails(request, tenantId, loggedInUserEmail, jsonResponse);
            } else {
                jsonResponse.put("success", false).put("error", "Invalid action specified.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error in CompanyAccountServlet action " + action + " for T:" + tenantId, e);
            jsonResponse.put("success", false).put("error", "Database error: " + e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "General Error in CompanyAccountServlet action " + action + " for T:" + tenantId, e);
            jsonResponse.put("success", false).put("error", "An unexpected server error occurred. Please try again later."); // More generic server error
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        if (!response.isCommitted()) {
            out.print(jsonResponse.toString());
            out.flush();
        } else {
            logger.warning("[CompanyAccountServlet] Response already committed for action: " + action + " for T:" + tenantId);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();
        Integer tenantId = getTenantId(request);
        String loggedInUserEmail = getLoggedInUserEmailFromSession(request);

        if (tenantId == null || loggedInUserEmail == null) {
            jsonResponse.put("success", false).put("error", "Session expired or invalid.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        String action = request.getParameter("action");
        logger.info("[CompanyAccountServlet] GET action: " + action + " for T:" + tenantId);

        if ("getCompanyDetails".equals(action)) {
            // This action can be called by any authenticated user of the tenant to populate their view.
            try (Connection conn = DatabaseConnection.getConnection()) {
                if (conn == null) {
                    logger.severe("Failed to get database connection for getCompanyDetails.");
                    jsonResponse.put("success", false).put("error", "Database connection error.");
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    out.print(jsonResponse.toString());
                    out.flush();
                    return;
                }
                String sql = "SELECT CompanyName, CompanyIdentifier, AdminEmail, PhoneNumber, Address, City, State, ZipCode FROM tenants WHERE TenantID = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, tenantId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            JSONObject details = new JSONObject();
                            details.put("companyName", rs.getString("CompanyName"));
                            details.put("companyIdentifier", rs.getString("CompanyIdentifier"));
                            details.put("adminEmail", rs.getString("AdminEmail"));
                            details.put("companyPhone", rs.getString("PhoneNumber"));
                            details.put("companyAddress", rs.getString("Address"));
                            details.put("companyCity", rs.getString("City"));
                            details.put("companyState", rs.getString("State"));
                            details.put("companyZip", rs.getString("ZipCode"));
                            jsonResponse.put("success", true).put("details", details);
                            response.setStatus(HttpServletResponse.SC_OK);
                        } else {
                            jsonResponse.put("success", false).put("error", "Company details not found.");
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        }
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "SQL Error fetching company details for T:" + tenantId, e);
                jsonResponse.put("success", false).put("error", "Database error fetching details.");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } else {
            jsonResponse.put("success", false).put("error", "Invalid GET action.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        if (!response.isCommitted()) {
            out.print(jsonResponse.toString());
            out.flush();
        } else {
             logger.warning("[CompanyAccountServlet] Response already committed for GET action: " + action + " for T:" + tenantId);
        }
    }

    private boolean isUserPrimaryAdmin(int tenantId, String loggedInUserEmail) throws SQLException {
        if (loggedInUserEmail == null) return false;
        String primaryAdminEmailFromDB = null;
        String sql = "SELECT AdminEmail FROM tenants WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) {
                 logger.severe("Failed to get database connection for isUserPrimaryAdmin check.");
                 throw new SQLException("Cannot connect to database for admin verification.");
            }
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    primaryAdminEmailFromDB = rs.getString("AdminEmail");
                }
            }
        }
        return primaryAdminEmailFromDB != null && primaryAdminEmailFromDB.trim().equalsIgnoreCase(loggedInUserEmail.trim());
    }

    private void verifyCompanyAdminPassword(HttpServletRequest request, int tenantId, String loggedInUserEmail, JSONObject jsonResponse) throws SQLException {
        String password = request.getParameter("password");
        // The primary admin check is now done in doPost before this method is called.
        // loggedInUserEmail is the primary admin's email from the session.

        if (isEmpty(password)) {
            jsonResponse.put("success", false).put("error", "Password is required for verification.");
            return;
        }

        String storedHash = null;
        String dbAdminEmail = null; 
        String sql = "SELECT AdminEmail, AdminPasswordHash FROM tenants WHERE TenantID = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) throw new SQLException("Database connection failed for password verification.");
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    dbAdminEmail = rs.getString("AdminEmail");
                    storedHash = rs.getString("AdminPasswordHash");
                }
            }
        }

        if (storedHash == null || dbAdminEmail == null) {
            jsonResponse.put("success", false).put("error", "Tenant administrator credentials could not be retrieved.");
            return;
        }
        // This is a redundant check if isUserPrimaryAdmin was called, but good for defense in depth
        if (!loggedInUserEmail.trim().equalsIgnoreCase(dbAdminEmail.trim())) {
            logger.warning("Security Alert: verifyCompanyAdminPassword called by " + loggedInUserEmail + " but DB primary admin is " + dbAdminEmail + " for T:" + tenantId);
            jsonResponse.put("success", false).put("error", "Authorization mismatch during password verification.");
            return;
        }

        if (BCrypt.checkpw(password, storedHash)) {
            jsonResponse.put("success", true).put("message", "Password verified.");
        } else {
            jsonResponse.put("success", false).put("error", "Incorrect company administrator password.");
        }
    }

    private void updateCompanyDetails(HttpServletRequest request, int tenantId, JSONObject jsonResponse) throws SQLException {
        // Primary admin check is done in doPost.
        String companyPhone = request.getParameter("companyPhone");
        String companyAddress = request.getParameter("companyAddress");
        String companyCity = request.getParameter("companyCity");
        String companyState = request.getParameter("companyState");
        String companyZip = request.getParameter("companyZip");

        String sql = "UPDATE tenants SET PhoneNumber = ?, Address = ?, City = ?, State = ?, ZipCode = ? WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (conn == null) throw new SQLException("Database connection failed for updating company details.");
            
            pstmt.setString(1, isEmpty(companyPhone) ? null : companyPhone.trim());
            pstmt.setString(2, isEmpty(companyAddress) ? null : companyAddress.trim());
            pstmt.setString(3, isEmpty(companyCity) ? null : companyCity.trim());
            pstmt.setString(4, isEmpty(companyState) ? "" : companyState.trim());
            pstmt.setString(5, isEmpty(companyZip) ? null : companyZip.trim());
            pstmt.setInt(6, tenantId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                jsonResponse.put("success", true).put("message", "Company details updated successfully.");
            } else {
                jsonResponse.put("success", false).put("error", "Failed to update details or no changes were made.");
            }
        }
    }

    private void updateAdminLoginDetails(HttpServletRequest request, int tenantId, String loggedInUserEmail, JSONObject jsonResponse) throws SQLException {
        // Primary admin check is done in doPost.
        String currentPassword = request.getParameter("currentPassword");
        String newAdminLoginEmailInput = request.getParameter("newAdminLoginEmail");
        String newAdminPasswordInput = request.getParameter("newAdminPassword");

        if (isEmpty(currentPassword)) {
            jsonResponse.put("success", false).put("error", "Current password is required to make changes.");
            return;
        }

        String storedHash = null;
        String currentPrimaryAdminEmailFromDB = null; // This is the email currently in the DB for the tenant
        String sqlFetch = "SELECT AdminEmail, AdminPasswordHash FROM tenants WHERE TenantID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmtFetch = conn.prepareStatement(sqlFetch)) {
            if (conn == null) throw new SQLException("Database connection failed for fetching admin details.");
            pstmtFetch.setInt(1, tenantId);
            try (ResultSet rs = pstmtFetch.executeQuery()) {
                if (rs.next()) {
                    currentPrimaryAdminEmailFromDB = rs.getString("AdminEmail");
                    storedHash = rs.getString("AdminPasswordHash");
                }
            }
        }

        if (storedHash == null || currentPrimaryAdminEmailFromDB == null) {
            jsonResponse.put("success", false).put("error", "Could not retrieve current admin credentials for verification.");
            return;
        }
        // Ensure the loggedInUserEmail from session (who is performing the action) IS the currentPrimaryAdminEmailFromDB
        if (!loggedInUserEmail.trim().equalsIgnoreCase(currentPrimaryAdminEmailFromDB.trim())) {
             logger.severe("CRITICAL SECURITY ALERT: Attempt to change admin login details for T:" + tenantId +
                           " by user " + loggedInUserEmail + ", but the DB primary admin is " + currentPrimaryAdminEmailFromDB + ".");
             jsonResponse.put("success", false).put("error", "Security validation failed. Your session may not match the primary administrator account. Please re-login.");
             return;
        }
        if (!BCrypt.checkpw(currentPassword, storedHash)) {
            jsonResponse.put("success", false).put("error", "Incorrect current company administrator password.");
            return;
        }

        boolean emailChanged = false;
        boolean passwordChanged = false;
        StringBuilder updateSqlBuilder = new StringBuilder("UPDATE tenants SET ");
        boolean needsComma = false;
        String newCleanEmail = null;

        if (!isEmpty(newAdminLoginEmailInput)) {
            newCleanEmail = newAdminLoginEmailInput.trim();
            if (!newCleanEmail.equalsIgnoreCase(currentPrimaryAdminEmailFromDB.trim())) { // Check if it's actually a new email
                if (!VALID_EMAIL_ADDRESS_REGEX.matcher(newCleanEmail).matches()) {
                     jsonResponse.put("success", false).put("error", "The new admin email format is invalid.");
                     return;
                }
                // Check for email uniqueness across other tenants
                String checkEmailSql = "SELECT COUNT(*) FROM tenants WHERE LOWER(AdminEmail) = LOWER(?) AND TenantID != ?";
                try (Connection connCheck = DatabaseConnection.getConnection();
                     PreparedStatement pstmtCheckEmail = connCheck.prepareStatement(checkEmailSql)) {
                    if (connCheck == null) throw new SQLException("Database connection failed for email uniqueness check.");
                    pstmtCheckEmail.setString(1, newCleanEmail);
                    pstmtCheckEmail.setInt(2, tenantId); 
                    try (ResultSet rsCheck = pstmtCheckEmail.executeQuery()) {
                        if (rsCheck.next() && rsCheck.getInt(1) > 0) {
                            jsonResponse.put("success", false).put("error", "This email address is already in use by another company account.");
                            return;
                        }
                    }
                }
                updateSqlBuilder.append("AdminEmail = ?");
                needsComma = true;
                emailChanged = true;
            } else {
                newCleanEmail = null; // Email provided is same as current, so no change
            }
        }

        String newHashedPassword = null;
        if (!isEmpty(newAdminPasswordInput)) {
            if (newAdminPasswordInput.length() < MIN_ADMIN_PASSWORD_LENGTH) {
                jsonResponse.put("success", false).put("error", "New password must be at least " + MIN_ADMIN_PASSWORD_LENGTH + " characters.");
                return;
            }
            if (BCrypt.checkpw(newAdminPasswordInput, storedHash)) { // Check if new password is same as current
                jsonResponse.put("success", false).put("error", "New password cannot be the same as the current password.");
                return;
            }
            newHashedPassword = BCrypt.hashpw(newAdminPasswordInput, BCrypt.gensalt(12));
            if (needsComma) {
                updateSqlBuilder.append(", ");
            }
            updateSqlBuilder.append("AdminPasswordHash = ?");
            passwordChanged = true;
        }

        if (!emailChanged && !passwordChanged) {
            // If newCleanEmail was set but was same as old, and no password change, it comes here.
            jsonResponse.put("success", true).put("message", "No changes were made to email or password.");
            jsonResponse.put("emailChanged", false); // Explicitly false
            jsonResponse.put("passwordChanged", false); // Explicitly false
            return;
        }

        updateSqlBuilder.append(" WHERE TenantID = ?");

        try (Connection connUpdate = DatabaseConnection.getConnection();
             PreparedStatement pstmtUpdate = connUpdate.prepareStatement(updateSqlBuilder.toString())) {
            if (connUpdate == null) throw new SQLException("Database connection failed for updating admin login details.");
            int paramIndex = 1;
            if (emailChanged && newCleanEmail != null) { // newCleanEmail would be set if email actually changed
                pstmtUpdate.setString(paramIndex++, newCleanEmail);
            }
            if (passwordChanged) {
                pstmtUpdate.setString(paramIndex++, newHashedPassword);
            }
            pstmtUpdate.setInt(paramIndex, tenantId);

            int rowsAffected = pstmtUpdate.executeUpdate();

            if (rowsAffected > 0) {
                jsonResponse.put("success", true);
                String successMsg = "";
                if (emailChanged && passwordChanged) successMsg = "Admin email and password updated successfully.";
                else if (emailChanged) successMsg = "Admin email updated successfully.";
                else if (passwordChanged) successMsg = "Admin password updated successfully.";
                else successMsg = "Account login details updated."; // Should not happen due to check above
                
                jsonResponse.put("message", successMsg);
                jsonResponse.put("emailChanged", emailChanged);
                jsonResponse.put("passwordChanged", passwordChanged);
                if (emailChanged && newCleanEmail != null) {
                    jsonResponse.put("newAdminEmail", newCleanEmail);
                }

                HttpSession session = request.getSession(false);
                if (session != null && emailChanged && newCleanEmail != null) {
                    // Check if the logged-in user IS the primary admin whose email just changed
                    if (loggedInUserEmail.equalsIgnoreCase(currentPrimaryAdminEmailFromDB)) { 
                        session.setAttribute("Email", newCleanEmail);
                        logger.info("Updated session email for primary admin T:" + tenantId + " from " + currentPrimaryAdminEmailFromDB + " to " + newCleanEmail);
                    }
                }
            } else {
                jsonResponse.put("success", false).put("error", "Failed to update account login details. No rows affected.");
            }
        }
    }
}