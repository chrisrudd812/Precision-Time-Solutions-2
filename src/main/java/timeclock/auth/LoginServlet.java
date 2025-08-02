package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.Configuration;
import timeclock.punches.ShowPunches; 

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.time.ZoneId;
import java.time.LocalDate; 
import java.time.format.DateTimeFormatter; 
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LoginServlet.class.getName());

    private static final int ADMIN_DEFAULT_TIMEOUT_MINUTES = 120;
    private static final int USER_DEFAULT_TIMEOUT_SECONDS = 30 * 60;
    private static final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String companyIdentifier = request.getParameter("companyIdentifier");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        HttpSession session = request.getSession(true);

        boolean isFirstLoginFlow = session.getAttribute("isFirstLoginAfterPinSet") != null;

        if (!ShowPunches.isValid(companyIdentifier) || !ShowPunches.isValid(email) || !ShowPunches.isValid(password)) {
            response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Company ID, email, and PIN are required.", StandardCharsets.UTF_8));
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            String tenantSql = "SELECT TenantID FROM tenants WHERE CompanyIdentifier = ?";
            PreparedStatement psTenant = conn.prepareStatement(tenantSql);
            psTenant.setString(1, companyIdentifier.trim());
            ResultSet rsTenant = psTenant.executeQuery();

            if (!rsTenant.next()) {
                response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid Company ID.", StandardCharsets.UTF_8));
                return;
            }
            int tenantId = rsTenant.getInt("TenantID");

            String userSql = "SELECT EID, PasswordHash, PERMISSIONS, FIRST_NAME, LAST_NAME, ACTIVE, RequiresPasswordChange FROM EMPLOYEE_DATA WHERE LOWER(EMAIL) = LOWER(?) AND TenantID = ?";
            PreparedStatement psUser = conn.prepareStatement(userSql);
            psUser.setString(1, email.trim().toLowerCase());
            psUser.setInt(2, tenantId);
            ResultSet rsUser = psUser.executeQuery();

            if (rsUser.next() && BCrypt.checkpw(password, rsUser.getString("PasswordHash"))) {
                if (!rsUser.getBoolean("ACTIVE")) {
                    response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Account is inactive.", StandardCharsets.UTF_8));
                    return;
                }

                int eid = rsUser.getInt("EID");
                String userPermissions = rsUser.getString("PERMISSIONS");
                session.setAttribute("EID", eid);
                session.setAttribute("UserFirstName", rsUser.getString("FIRST_NAME"));
                session.setAttribute("UserLastName", rsUser.getString("LAST_NAME"));
                session.setAttribute("Permissions", userPermissions);
                session.setAttribute("TenantID", tenantId);
                session.setAttribute("CompanyIdentifier", companyIdentifier.trim());
                session.setAttribute("Email", email.trim().toLowerCase());
                
                // ✅ FIX: This is the fully corrected logic block for the wizard flow.
                if (isFirstLoginFlow && "Administrator".equalsIgnoreCase(userPermissions)) {
                    logger.info("First admin login detected. Continuing setup wizard to admin profile review.");
                    session.removeAttribute("isFirstLoginAfterPinSet"); // Clean up the flag
                    session.setAttribute("startSetupWizard", true);
                    session.setAttribute("wizardStep", "employees_prompt"); // This step name is what employees.jsp uses to initiate the flow
                    session.setAttribute("wizardAdminEid", eid);
                    
                    // Redirect to the employees page with the specific wizard action in the URL.
                    // This is the most reliable way to trigger the correct JavaScript logic.
                    response.sendRedirect("employees.jsp?setup_wizard=true&action=review_admin");
                } else if (rsUser.getBoolean("RequiresPasswordChange")) {
                    response.sendRedirect("change_password.jsp");
                } else {
                    // Normal login for everyone else
                    session.removeAttribute("startSetupWizard");
                    String targetPage = "Administrator".equalsIgnoreCase(userPermissions) ? "employees.jsp" : "timeclock.jsp";
                    response.sendRedirect(targetPage);
                }

            } else {
                response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid credentials.", StandardCharsets.UTF_8));
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during login", e);
            response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Database error.", StandardCharsets.UTF_8));
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException e) { /* ignore */ }
        }
    }
}