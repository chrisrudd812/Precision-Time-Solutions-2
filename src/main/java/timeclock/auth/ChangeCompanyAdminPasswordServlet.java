package timeclock.auth; // Or your preferred package for servlets, ensure consistency

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import timeclock.db.DatabaseConnection; // Your database connection class
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.net.URLEncoder;
// import java.net.URLEncoder; // Not directly needed if using session for messages
// import java.nio.charset.StandardCharsets; // Not directly needed
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/ChangeCompanyAdminPasswordServlet")
public class ChangeCompanyAdminPasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ChangeCompanyAdminPasswordServlet.class.getName());
    private static final int MIN_PASSWORD_LENGTH = 8;


    private Integer getTenantIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object tenantIdObj = session.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) {
            Integer id = (Integer) tenantIdObj;
            return (id > 0) ? id : null;
        }
        return null;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8"); // Ensure request encoding
        response.setCharacterEncoding("UTF-8");
        
        HttpSession session = request.getSession(false);
        String redirectPage = "change_company_admin_password.jsp";
        String loginPage = "login.jsp";

        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            logger.warning("ChangeCompanyAdminPasswordServlet: No TenantID in session or session is null.");
            // Set error message for login page if redirecting there
            if (session != null) session.invalidate(); // Invalidate potentially corrupt session
            response.sendRedirect(loginPage + "?error=" + URLEncoder.encode("Your session has expired. Please log in again.", "UTF-8"));
            return;
        }

        String currentPassword = request.getParameter("currentPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmNewPassword = request.getParameter("confirmNewPassword");

        // 1. Input Validation
        if (currentPassword == null || currentPassword.isEmpty() ||
            newPassword == null || newPassword.isEmpty() ||
            confirmNewPassword == null || confirmNewPassword.isEmpty()) {
            session.setAttribute("errorMessage", "All password fields are required.");
            response.sendRedirect(redirectPage);
            return;
        }

        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            session.setAttribute("errorMessage", "New password must be at least " + MIN_PASSWORD_LENGTH + " characters long.");
            response.sendRedirect(redirectPage);
            return;
        }

        if (!newPassword.equals(confirmNewPassword)) {
            session.setAttribute("errorMessage", "New passwords do not match.");
            response.sendRedirect(redirectPage);
            return;
        }

        if (currentPassword.equals(newPassword)) {
            session.setAttribute("errorMessage", "New password cannot be the same as the current password.");
            response.sendRedirect(redirectPage);
            return;
        }

        Connection con = null;
        PreparedStatement psFetchHash = null;
        PreparedStatement psUpdateHash = null;
        ResultSet rs = null;

        try {
            con = DatabaseConnection.getConnection();
            // Transaction control can be useful if multiple DB operations were needed,
            // but for a single update, auto-commit is often fine.
            // For consistency, let's manage it if you prefer.
            // con.setAutoCommit(false); 

            // 2. Fetch current AdminPasswordHash from tenants table
            String sqlFetchHash = "SELECT AdminPasswordHash FROM tenants WHERE TenantID = ?";
            psFetchHash = con.prepareStatement(sqlFetchHash);
            psFetchHash.setInt(1, tenantId);
            rs = psFetchHash.executeQuery();

            String storedHash = null;
            if (rs.next()) {
                storedHash = rs.getString("AdminPasswordHash");
            }

            if (storedHash == null) {
                logger.severe("Could not find AdminPasswordHash for TenantID: " + tenantId);
                session.setAttribute("errorMessage", "Could not verify current password. Account configuration issue.");
                // if (!con.getAutoCommit()) con.rollback();
                response.sendRedirect(redirectPage);
                return;
            }

            // 3. Verify Current Password
            if (!BCrypt.checkpw(currentPassword, storedHash)) {
                logger.warning("Current company admin password verification failed for TenantID: " + tenantId);
                session.setAttribute("errorMessage", "Incorrect current admin password.");
                // if (!con.getAutoCommit()) con.rollback();
                response.sendRedirect(redirectPage);
                return;
            }

            // 4. Hash New Password
            String newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));

            // 5. Update Database
            String sqlUpdateHash = "UPDATE tenants SET AdminPasswordHash = ? WHERE TenantID = ?";
            psUpdateHash = con.prepareStatement(sqlUpdateHash);
            psUpdateHash.setString(1, newPasswordHash);
            psUpdateHash.setInt(2, tenantId);

            int rowsAffected = psUpdateHash.executeUpdate();

            if (rowsAffected > 0) {
                // if (!con.getAutoCommit()) con.commit();
                session.setAttribute("successMessage", "Company admin password updated successfully.");
            } else {
                logger.warning("Company admin password update failed (no rows affected) for TenantID: " + tenantId);
                // if (!con.getAutoCommit()) con.rollback();
                session.setAttribute("errorMessage", "Could not update password. Please try again.");
            }
            response.sendRedirect(redirectPage);

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during company admin password change for TenantID: " + tenantId, e);
            // try { if (con != null && !con.getAutoCommit()) con.rollback(); } catch (SQLException se) { logger.log(Level.SEVERE, "Rollback failed", se); }
            session.setAttribute("errorMessage", "Database error: " + e.getMessage());
            response.sendRedirect(redirectPage);
        } catch (Exception e) { // Catch any other unexpected errors
            logger.log(Level.SEVERE, "Unexpected error during company admin password change for TenantID: " + tenantId, e);
            // try { if (con != null && !con.getAutoCommit()) con.rollback(); } catch (SQLException se) { logger.log(Level.SEVERE, "Rollback failed", se); }
            session.setAttribute("errorMessage", "An unexpected server error occurred: " + e.getMessage());
            response.sendRedirect(redirectPage);
        } finally {
            try { if (rs != null) rs.close(); } catch (SQLException e) { /* ignored */ }
            try { if (psFetchHash != null) psFetchHash.close(); } catch (SQLException e) { /* ignored */ }
            try { if (psUpdateHash != null) psUpdateHash.close(); } catch (SQLException e) { /* ignored */ }
            try { 
                // if (con != null && !con.getAutoCommit()) con.setAutoCommit(true); // Reset auto-commit
                if (con != null) con.close(); 
            } catch (SQLException e) { logger.log(Level.WARNING, "Error closing connection", e); }
        }
    }
}