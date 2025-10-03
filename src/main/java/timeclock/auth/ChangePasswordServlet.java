package timeclock.auth; // Your package

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/ChangePasswordServlet")
public class ChangePasswordServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ChangePasswordServlet.class.getName());

    // *** ADDED escapeHtml method ***
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Integer tenantId = null;
        Integer eid = null;
        String userPermissions = null;
        Boolean isWizardMode = null;
        String wizardStep = null;
        Integer wizardAdminEid = null;

        if (session != null) {
            tenantId = (Integer) session.getAttribute("TenantID");
            eid = (Integer) session.getAttribute("EID");
            userPermissions = (String) session.getAttribute("Permissions");
            isWizardMode = (Boolean) session.getAttribute("startSetupWizard");
            wizardStep = (String) session.getAttribute("wizardStep");
            wizardAdminEid = (Integer) session.getAttribute("wizardAdminEid");

        } else {
            response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
            return;
        }

        if (tenantId == null || eid == null || eid <= 0) {
            if (session != null) {
                session.setAttribute("errorMessage", "Invalid session data. Please log in again.");
            }
            response.sendRedirect("login.jsp");
            return;
        }

        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        String errorRedirectPage = "change_password.jsp";
        String successRedirectPage = "User".equalsIgnoreCase(userPermissions) ? "timeclock.jsp" : "employees.jsp";

        if (newPassword == null || !newPassword.matches("^\\d{4}$") || "1234".equals(newPassword)) {
            String error = "New PIN must be exactly 4 numerical digits and cannot be '1234'.";
            logger.warning("[ChangePasswordServlet] Invalid new PIN for EID: " + eid + " - " + error);
            session.setAttribute("errorMessage", error);
            response.sendRedirect(errorRedirectPage);
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            String error = "New PINs do not match.";
            logger.warning("[ChangePasswordServlet] PINs do not match for EID: " + eid);
            session.setAttribute("errorMessage", error);
            response.sendRedirect(errorRedirectPage);
            return;
        }

        Connection conn = null;
        PreparedStatement psCheck = null;
        ResultSet rsCheck = null;
        PreparedStatement psUpdate = null;

        try {
            conn = DatabaseConnection.getConnection();
            if (conn == null) throw new SQLException("Failed to get database connection.");
            conn.setAutoCommit(false);

            String checkSql = "SELECT RequiresPasswordChange FROM employee_data WHERE EID = ? AND TenantID = ?";
            boolean dbRequiresChange = false;

            psCheck = conn.prepareStatement(checkSql);
            psCheck.setInt(1, eid);
            psCheck.setInt(2, tenantId);
            rsCheck = psCheck.executeQuery();

            if (rsCheck.next()) {
                dbRequiresChange = rsCheck.getBoolean("RequiresPasswordChange");
            } else {
                conn.rollback();
                logger.warning("[ChangePasswordServlet] User record not found for EID: " + eid + ", TenantID: " + tenantId + " during PIN change verification.");
                session.setAttribute("errorMessage", "User record not found. Please contact support.");
                response.sendRedirect(errorRedirectPage);
                return;
            }

            if (!dbRequiresChange && Boolean.TRUE.equals(isWizardMode) && "pinChangePending".equals(wizardStep) && wizardAdminEid != null && wizardAdminEid.equals(eid)) {
            } else if (!dbRequiresChange) {
                logger.warning("[ChangePasswordServlet] User EID " + eid + " (Tenant: " + tenantId + ") PIN change attempted, but DB flag 'RequiresPasswordChange' is already FALSE. Not in expected wizard flow.");
                session.setAttribute("successMessage", "Your PIN was already updated or does not require a change.");
                 if (Boolean.TRUE.equals(isWizardMode)) {
                    session.removeAttribute("startSetupWizard");
                    session.removeAttribute("wizardStep");
                    session.removeAttribute("wizardAdminEid");
                }
                response.sendRedirect(successRedirectPage);
                conn.rollback();
                return;
            }

            String hashedNewPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt(12));
            String updateSql = "UPDATE employee_data SET PasswordHash = ?, RequiresPasswordChange = ? WHERE EID = ? AND TenantID = ?";
            psUpdate = conn.prepareStatement(updateSql);

            psUpdate.setString(1, hashedNewPassword);
            psUpdate.setBoolean(2, false);
            psUpdate.setInt(3, eid);
            psUpdate.setInt(4, tenantId);

            int rowsAffected = psUpdate.executeUpdate();
            if (rowsAffected > 0) {
                conn.commit();
                session.setAttribute("RequiresPasswordChange", Boolean.FALSE);
                session.removeAttribute("errorMessage");

                if (Boolean.TRUE.equals(isWizardMode) && "pinChangePending".equals(wizardStep) && wizardAdminEid != null && wizardAdminEid.equals(eid)) {
                    session.setAttribute("wizardStep", "departments");
                    String companyNameFromSession = (String) session.getAttribute("CompanyNameSignup");
                    String adminFirstNameFromSession = (String) session.getAttribute("AdminFirstNameSignup");
                    
                    // Use the escapeHtml method here
                    String welcomeMsg = "Welcome, " + (adminFirstNameFromSession != null ? escapeHtml(adminFirstNameFromSession) : "Administrator") + "!";
                    if (companyNameFromSession != null) {
                        welcomeMsg += " Let's set up your company: " + escapeHtml(companyNameFromSession) + ".";
                    }
                    session.setAttribute("wizardWelcomeMessage", welcomeMsg);
                    
                    successRedirectPage = "departments.jsp?setup_wizard=true";
                    session.setAttribute("successMessage", "PIN updated! Let's set up your company details.");
                } else {
                    session.setAttribute("successMessage", "Your PIN has been successfully updated.");
                    if (Boolean.TRUE.equals(isWizardMode)) {
                        logger.warning("[ChangePasswordServlet] Wizard mode was active but conditions for 'pinChangePending' not fully met. Clearing wizard flags. EID=" + eid + ", WizAdminEID=" + wizardAdminEid + ", Step=" + wizardStep);
                        session.removeAttribute("startSetupWizard");
                        session.removeAttribute("wizardStep");
                        session.removeAttribute("wizardAdminEid");
                        session.removeAttribute("CompanyNameSignup");
                        session.removeAttribute("AdminFirstNameSignup");
                    }
                }
                response.sendRedirect(successRedirectPage);
            } else {
                conn.rollback();
                logger.warning("[ChangePasswordServlet] PIN change failed (0 rows updated) for EID: " + eid + ", TenantID: " + tenantId);
                session.setAttribute("errorMessage", "Failed to update PIN. Please try again.");
                response.sendRedirect(errorRedirectPage);
            }

        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException se) { logger.log(Level.SEVERE, "[ChangePasswordServlet] Rollback failed (SQLException)", se); } }
            logger.log(Level.SEVERE, "[ChangePasswordServlet] Database error for EID: " + eid, e);
            session.setAttribute("errorMessage", "Database error: " + e.getMessage());
            response.sendRedirect(errorRedirectPage);
        } catch (Exception e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException se) { logger.log(Level.SEVERE, "[ChangePasswordServlet] Rollback failed (Exception)", se); } }
            logger.log(Level.SEVERE, "[ChangePasswordServlet] Unexpected error for EID: " + eid, e);
            session.setAttribute("errorMessage", "An unexpected error occurred: " + e.getMessage());
            response.sendRedirect(errorRedirectPage);
        }
        finally {
            try { if (rsCheck != null) rsCheck.close(); } catch (SQLException e) { /* ignored */ }
            try { if (psCheck != null) psCheck.close(); } catch (SQLException e) { /* ignored */ }
            try { if (psUpdate != null) psUpdate.close(); } catch (SQLException e) { /* ignored */ }
            if (conn != null) {
                try {
                    if (!conn.getAutoCommit()) conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) { logger.log(Level.WARNING, "[ChangePasswordServlet] Error closing connection", e); }
            }
        }
    }
}