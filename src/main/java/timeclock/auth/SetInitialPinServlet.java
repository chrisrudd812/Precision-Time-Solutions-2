package timeclock.auth; // Ensure this matches your package structure

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
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/SetInitialPinServlet")
public class SetInitialPinServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SetInitialPinServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);

        String eidStr = request.getParameter("eid");
        String newPin = request.getParameter("newPin");
        String confirmPin = request.getParameter("confirmNewPin");
        String wizardStepVerificationFromForm = request.getParameter("wizardStepVerify");

        logger.info("[SetInitialPinServlet] Received POST request.");
        logger.info("[SetInitialPinServlet] Parameters - EID: " + eidStr + ", newPin provided: " + (newPin != null && !newPin.isEmpty()) +
                ", wizardStepVerify: " + wizardStepVerificationFromForm);

        String redirectToSetInitialPinPage = request.getContextPath() + "/set_initial_pin.jsp";
        String redirectToSignupPage = request.getContextPath() + "/signup_company_info.jsp";
        String errorMessage = null;

        // 1. Session and Wizard State Validation
        if (session == null) {
            errorMessage = "Your session has expired. Please start the signup process again.";
            logger.warning("[SetInitialPinServlet] No active session. Redirecting to signup.");
            response.sendRedirect(redirectToSignupPage + "?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
            return;
        }
        logger.info("[SetInitialPinServlet] Session ID: " + session.getId());

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        Integer sessionEid = (Integer) session.getAttribute("wizardAdminEid");
        String sessionWizardStep = (String) session.getAttribute("wizardStep");

        logger.info("[SetInitialPinServlet] Session Attributes - TenantID: " + tenantId +
                ", wizardAdminEid: " + sessionEid + ", wizardStep: " + sessionWizardStep);

        if (tenantId == null || sessionEid == null ||
            !"initialPinSetRequired".equals(sessionWizardStep) ||
            !"initialPinSetRequired".equals(wizardStepVerificationFromForm)) {

            errorMessage = "Invalid session or incorrect wizard step for setting initial PIN. Please restart the signup process.";
            logger.warning("[SetInitialPinServlet] Invalid session state for PIN set. " +
                           "TenantID null? " + (tenantId == null) +
                           ", SessionEID null? " + (sessionEid == null) +
                           ", SessionWizardStep correct? " + "initialPinSetRequired".equals(sessionWizardStep) +
                           ", FormWizardStepVerify correct? " + "initialPinSetRequired".equals(wizardStepVerificationFromForm) +
                           ". Redirecting to signup.");
            session.invalidate(); // Invalidate potentially corrupt session
            response.sendRedirect(redirectToSignupPage + "?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
            return;
        }

        int eid;
        try {
            eid = Integer.parseInt(eidStr);
            if (eid != sessionEid.intValue()) {
                throw new NumberFormatException("EID from form (" + eid + ") does not match session EID (" + sessionEid.intValue() + ").");
            }
        } catch (NumberFormatException | NullPointerException e) {
            errorMessage = "Invalid employee identifier provided. Please contact support or restart signup.";
            logger.log(Level.WARNING, "[SetInitialPinServlet] Invalid EID format or mismatch for TenantID: " + tenantId + ". Form EID: " + eidStr + ", Session EID: " + sessionEid, e);
            session.setAttribute("errorMessage_initialPin", errorMessage);
            response.sendRedirect(redirectToSetInitialPinPage + "?error=" + URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
            return;
        }
        logger.info("[SetInitialPinServlet] EID verification passed for EID: " + eid);

        // 2. PIN Input Validation
        if (newPin == null || !newPin.matches("\\d{4}")) {
            errorMessage = "New PIN must be exactly 4 numerical digits.";
        } else if (confirmPin == null || !confirmPin.equals(newPin)) {
            errorMessage = "PINs do not match. Please re-enter.";
        } else if ("1234".equals(newPin)) {
            errorMessage = "Your new PIN cannot be the default '1234'. Please choose a different PIN.";
        }

        if (errorMessage != null) {
            logger.warning("[SetInitialPinServlet] PIN validation failed for EID: " + eid + ". Error: " + errorMessage);
            session.setAttribute("errorMessage_initialPin", errorMessage);
            response.sendRedirect(redirectToSetInitialPinPage); // Error message shown by JSP
            return;
        }
        logger.info("[SetInitialPinServlet] PIN validation passed for EID: " + eid);

        // 3. Database Update
        String hashedPin = BCrypt.hashpw(newPin, BCrypt.gensalt(12));
        String sql = "UPDATE EMPLOYEE_DATA SET PasswordHash = ?, RequiresPasswordChange = FALSE WHERE EID = ? AND TenantID = ?";

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, hashedPin);
                ps.setInt(2, eid);
                ps.setInt(3, tenantId);

                int rowsAffected = ps.executeUpdate();
                if (rowsAffected > 0) {
                    con.commit();
                    logger.info("[SetInitialPinServlet] PIN successfully set and RequiresPasswordChange updated for EID: " + eid + ", TenantID: " + tenantId);

                    session.setAttribute("wizardStep", "settings_setup");
                    session.removeAttribute("errorMessage_initialPin");

                    String adminFirstName = (String) session.getAttribute("wizardAdminFirstName");
                    String companyName = (String) session.getAttribute("CompanyNameSignup");
                    if (adminFirstName != null && companyName != null) {
                         session.setAttribute("wizardWelcomeMessage", "Welcome, " + adminFirstName + "! Your PIN is set. Now, let's configure your company settings for " + companyName + ".");
                    }

                    // --- ADDED LOGGING FOR CONTEXT PATH BEFORE REDIRECT ---
                    String contextPathForRedirect = request.getContextPath();
                    logger.info("[SetInitialPinServlet] ContextPath for redirect is: '" + contextPathForRedirect + "'");
                    // --- END ADDED LOGGING ---

                    String nextWizardPage = contextPathForRedirect + "/settings.jsp?setup_wizard=true&step=settings_setup";
                    logger.info("[SetInitialPinServlet] Redirecting to next wizard step: " + nextWizardPage);
                    response.sendRedirect(nextWizardPage);

                } else {
                    con.rollback();
                    errorMessage = "Failed to update PIN in the database. Your employee record might not have been found or no change was made. Please try again or contact support.";
                    logger.warning("[SetInitialPinServlet] PIN update failed (0 rows affected) for EID: " + eid + ", TenantID: " + tenantId);
                    session.setAttribute("errorMessage_initialPin", errorMessage);
                    response.sendRedirect(redirectToSetInitialPinPage);
                }
            }
        } catch (SQLException e) {
            if (con != null) {
                try { con.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "[SetInitialPinServlet] Rollback failed for EID: " + eid, ex); }
            }
            errorMessage = "A database error occurred while updating your PIN. Please try again.";
            logger.log(Level.SEVERE, "[SetInitialPinServlet] SQLException for EID: " + eid + ", TenantID: " + tenantId, e);
            session.setAttribute("errorMessage_initialPin", errorMessage);
            response.sendRedirect(redirectToSetInitialPinPage);
        } finally {
            if (con != null) {
                try {
                    if (!con.getAutoCommit()) con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "[SetInitialPinServlet] Failed to close connection for EID: " + eid, e);
                }
            }
        }
    }
}