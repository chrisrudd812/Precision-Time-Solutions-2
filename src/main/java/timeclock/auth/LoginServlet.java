package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.Configuration;
import timeclock.punches.ShowPunches; // For ShowPunches.isValid
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
import java.time.ZoneId;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LoginServlet.class.getName());

    private static final int ADMIN_DEFAULT_TIMEOUT_MINUTES = 120;
    private static final int USER_DEFAULT_TIMEOUT_SECONDS = 30 * 60;
    private static final String PACIFIC_TIME_FALLBACK = "America/Los_Angeles";
    private static final String DEFAULT_TENANT_FALLBACK_TIMEZONE = "America/Denver";
    private static final String UTC_ZONE_ID_SERVLET = "UTC";

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String companyIdentifier = request.getParameter("companyIdentifier");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String browserTimeZoneIdStr = request.getParameter("browserTimeZone");
        HttpSession session = request.getSession(true);

        logger.info("[LoginServlet] Login attempt for CoID: " + companyIdentifier + ", email: " + email + ", BrowserTZ: " + browserTimeZoneIdStr);

        if (!ShowPunches.isValid(companyIdentifier) || !ShowPunches.isValid(email) || !ShowPunches.isValid(password)) {
            session.setAttribute("errorMessage", "Company ID, email, and PIN are required.");
            response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Company ID, email, and PIN are required.", StandardCharsets.UTF_8.name()));
            return;
        }

        Connection conn = null;
        PreparedStatement psTenant = null; ResultSet rsTenant = null;
        PreparedStatement psUser = null; ResultSet rsUser = null;
        
        Boolean isWizardMode = (Boolean) session.getAttribute("startSetupWizard"); // Get as Boolean
        String wizardStepFromSignup = (String) session.getAttribute("wizardStep");
        Integer wizardAdminEid = (Integer) session.getAttribute("wizardAdminEid");

        try {
            conn = DatabaseConnection.getConnection();
            String tenantSql = "SELECT TenantID, AdminEmail FROM tenants WHERE CompanyIdentifier = ?";
            psTenant = conn.prepareStatement(tenantSql);
            psTenant.setString(1, companyIdentifier.trim());
            rsTenant = psTenant.executeQuery();

            int tenantId = -1;
            String tenantPrimaryAdminEmail = null;

            if (rsTenant.next()) {
                tenantId = rsTenant.getInt("TenantID");
                tenantPrimaryAdminEmail = rsTenant.getString("AdminEmail");
            } else {
                logger.warning("[LoginServlet] Tenant not found for CoID: " + companyIdentifier);
                session.setAttribute("errorMessage", "Invalid Company ID or credentials.");
                response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid Company ID or credentials.", StandardCharsets.UTF_8.name()) + "&companyIdentifier=" + URLEncoder.encode(companyIdentifier, StandardCharsets.UTF_8.name()));
                return;
            }
            
            String userSql = "SELECT EID, PasswordHash, PERMISSIONS, FIRST_NAME, LAST_NAME, RequiresPasswordChange, ACTIVE " +
                             "FROM EMPLOYEE_DATA WHERE LOWER(EMAIL) = LOWER(?) AND TenantID = ?";
            psUser = conn.prepareStatement(userSql);
            psUser.setString(1, email.trim().toLowerCase());
            psUser.setInt(2, tenantId);
            rsUser = psUser.executeQuery();

            if (rsUser.next()) {
                String storedHash = rsUser.getString("PasswordHash");
                boolean isActive = rsUser.getBoolean("ACTIVE");
                int eid = rsUser.getInt("EID");
                String userPermissionsFromDB = rsUser.getString("PERMISSIONS");
                String userFirstName = rsUser.getString("FIRST_NAME");
                String userLastName = rsUser.getString("LAST_NAME");
                boolean dbRequiresPasswordChange = rsUser.getBoolean("RequiresPasswordChange");

                logger.info("[LoginServlet] User EID " + eid + " DB data: Perms='" + userPermissionsFromDB +
                            "', ReqPassChange=" + dbRequiresPasswordChange + ", Active=" + isActive);

                if (!isActive) {
                    logger.warning("[LoginServlet] Inactive user EID: " + eid);
                    session.setAttribute("errorMessage", "This user account is inactive.");
                    response.sendRedirect("login.jsp?error=" + URLEncoder.encode("This user account is inactive.", StandardCharsets.UTF_8.name()) + "&companyIdentifier=" + URLEncoder.encode(companyIdentifier, StandardCharsets.UTF_8.name()) + "&adminEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8.name()));
                    return;
                }

                if (BCrypt.checkpw(password, storedHash)) {
                    logger.info("[LoginServlet] Password verified for EID: " + eid);
                    
                    session.setAttribute("EID", eid);
                    session.setAttribute("UserFirstName", userFirstName);
                    session.setAttribute("UserLastName", userLastName);
                    session.setAttribute("Permissions", userPermissionsFromDB);
                    session.setAttribute("TenantID", tenantId);
                    session.setAttribute("CompanyIdentifier", companyIdentifier.trim());
                    session.setAttribute("Email", email.trim().toLowerCase());
                    session.setAttribute("RequiresPasswordChange", Boolean.valueOf(dbRequiresPasswordChange)); // Store the flag from DB
                    
                    if (ShowPunches.isValid(tenantPrimaryAdminEmail)) {
                        session.setAttribute("TenantPrimaryAdminEmail", tenantPrimaryAdminEmail.trim().toLowerCase());
                    }

                    String effectiveUserTimeZoneId = null;
                    if (ShowPunches.isValid(browserTimeZoneIdStr) && !"Unknown".equalsIgnoreCase(browserTimeZoneIdStr)) {
                        try {
                            ZoneId.of(browserTimeZoneIdStr);
                            effectiveUserTimeZoneId = browserTimeZoneIdStr;
                            logger.info("[LoginServlet][TZ] Using Browser Detected TimeZone: " + effectiveUserTimeZoneId);
                        } catch (Exception e) {
                            logger.warning("[LoginServlet][TZ] Invalid Browser TimeZone received: '" + browserTimeZoneIdStr + "'. Proceeding to next fallback.");
                        }
                    }
                    if (!ShowPunches.isValid(effectiveUserTimeZoneId)) {
                        String tenantDefaultTz = Configuration.getProperty(tenantId, "DefaultTimeZone");
                        if (ShowPunches.isValid(tenantDefaultTz)) {
                            effectiveUserTimeZoneId = tenantDefaultTz;
                            logger.info("[LoginServlet][TZ] Using Tenant DefaultTimeZone from SETTINGS: " + effectiveUserTimeZoneId);
                        } else {
                            effectiveUserTimeZoneId = DEFAULT_TENANT_FALLBACK_TIMEZONE;
                            logger.info("[LoginServlet][TZ] Tenant DefaultTimeZone not set or invalid in DB. Using application default for tenant: " + effectiveUserTimeZoneId);
                        }
                    }
                    if (!ShowPunches.isValid(effectiveUserTimeZoneId)) {
                        effectiveUserTimeZoneId = PACIFIC_TIME_FALLBACK;
                        logger.warning("[LoginServlet][TZ] All other timezone sources failed. Using System Fallback (Pacific Time): " + effectiveUserTimeZoneId);
                    }
                    try {
                        ZoneId.of(effectiveUserTimeZoneId);
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, "[LoginServlet][TZ] CRITICAL: Final effectiveUserTimeZoneId '" + effectiveUserTimeZoneId + "' is invalid. Defaulting to UTC for session safety.", e);
                        effectiveUserTimeZoneId = UTC_ZONE_ID_SERVLET;
                    }
                    session.setAttribute("userTimeZoneId", effectiveUserTimeZoneId);
                    logger.info("[LoginServlet][TZ] Final userTimeZoneId set in session: " + effectiveUserTimeZoneId + " for EID: " + eid);
                    
                    // --- Wizard Logic Re-check ---
                    logger.info("[LoginServlet] Before wizard continuation check: isWizardMode=" + isWizardMode +
                                ", wizardStepFromSignup='" + wizardStepFromSignup + "'" +
                                ", wizardAdminEid=" + wizardAdminEid + ", loggedInEid=" + eid +
                                ", dbRequiresPasswordChange=" + dbRequiresPasswordChange);

                    if (Boolean.TRUE.equals(isWizardMode) && "loginForPinChange".equals(wizardStepFromSignup) && wizardAdminEid != null && wizardAdminEid.equals(eid)) {
                        logger.info("[LoginServlet] Wizard mode conditions met for PIN change.");
                        session.setAttribute("startSetupWizard", Boolean.TRUE); 
                        session.setAttribute("wizardAdminEid", wizardAdminEid); 
                        session.setAttribute("wizardStep", "pinChangePending"); 
                    } else {
                        logger.info("[LoginServlet] Not in specific wizard pin change flow, or EID mismatch. Clearing general wizard flags.");
                        session.removeAttribute("startSetupWizard");
                        session.removeAttribute("wizardStep");
                        session.removeAttribute("wizardAdminEid");
                        session.removeAttribute("CompanyNameSignup");
                        session.removeAttribute("AdminFirstNameSignup");
                    }
                    
                    int timeoutInSeconds; // Determine session timeout
                    // ... (session timeout logic as before) ...
                    if ("Administrator".equalsIgnoreCase(userPermissionsFromDB)) {
                        String adminTimeoutMinutesStr = Configuration.getProperty(tenantId, "AdminSessionTimeoutMinutes", String.valueOf(ADMIN_DEFAULT_TIMEOUT_MINUTES));
                        try {
                            timeoutInSeconds = Integer.parseInt(adminTimeoutMinutesStr) * 60;
                            if (timeoutInSeconds <= 0) timeoutInSeconds = ADMIN_DEFAULT_TIMEOUT_MINUTES * 60;
                        } catch (NumberFormatException e) { timeoutInSeconds = ADMIN_DEFAULT_TIMEOUT_MINUTES * 60; }
                        logger.info("[LoginServlet] Setting Admin session timeout to " + (timeoutInSeconds/60) + " minutes.");
                    } else {
                        String userTimeoutSecondsStr = Configuration.getProperty(tenantId, "UserSessionTimeoutSeconds", String.valueOf(USER_DEFAULT_TIMEOUT_SECONDS));
                        try {
                            timeoutInSeconds = Integer.parseInt(userTimeoutSecondsStr);
                            if (timeoutInSeconds <= 0) timeoutInSeconds = USER_DEFAULT_TIMEOUT_SECONDS;
                        } catch (NumberFormatException e) { timeoutInSeconds = USER_DEFAULT_TIMEOUT_SECONDS; }
                        logger.info("[LoginServlet] Setting User session timeout to " + timeoutInSeconds + " seconds.");
                    }
                    session.setMaxInactiveInterval(timeoutInSeconds);


                    if (dbRequiresPasswordChange) { // Use the flag read from DB
                        logger.info("[LoginServlet] Redirecting to change_password.jsp for EID: " + eid + " as dbRequiresPasswordChange is true.");
                        response.sendRedirect("change_password.jsp");
                    } else {
                        String targetPage = "User".equalsIgnoreCase(userPermissionsFromDB) ? "timeclock.jsp" : "employees.jsp";
                        logger.info("[LoginServlet] Login successful. Redirecting to " + targetPage + " for EID: " + eid);
                        response.sendRedirect(targetPage);
                    }
                } else { // Password mismatch
                    logger.warning("[LoginServlet] Password mismatch for EID: " + eid + ", TenantID: " + tenantId);
                    session.setAttribute("errorMessage", "Invalid Company ID or credentials.");
                    response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid Company ID or credentials.", StandardCharsets.UTF_8.name()) + "&companyIdentifier=" + URLEncoder.encode(companyIdentifier, StandardCharsets.UTF_8.name()) + "&adminEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8.name()));
                }
            } else { // User not found
                logger.warning("[LoginServlet] User not found for email: " + email + " within TenantID: " + tenantId + " (CoID: " + companyIdentifier + ")");
                session.setAttribute("errorMessage", "Invalid Company ID or credentials.");
                response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid Company ID or credentials.", StandardCharsets.UTF_8.name()) + "&companyIdentifier=" + URLEncoder.encode(companyIdentifier, StandardCharsets.UTF_8.name()) + "&adminEmail=" + URLEncoder.encode(email, StandardCharsets.UTF_8.name()));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[LoginServlet] Database error during login", e);
            session.setAttribute("errorMessage", "Database error during login. Please try again later.");
            if (!response.isCommitted()) response.sendRedirect("login.jsp");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[LoginServlet] Unexpected error during login", e);
            session.setAttribute("errorMessage", "An unexpected error occurred. Please try again.");
            if (!response.isCommitted()) response.sendRedirect("login.jsp");
        } finally {
            try { if (rsUser != null) rsUser.close(); } catch (SQLException e) { logger.log(Level.FINER, "Error closing rsUser", e); }
            try { if (psUser != null) psUser.close(); } catch (SQLException e) { logger.log(Level.FINER, "Error closing psUser", e); }
            try { if (rsTenant != null) rsTenant.close(); } catch (SQLException e) { logger.log(Level.FINER, "Error closing rsTenant", e); }
            try { if (psTenant != null) psTenant.close(); } catch (SQLException e) { logger.log(Level.FINER, "Error closing psTenant", e); }
            if (conn != null) { try { conn.close(); } catch (SQLException e) { logger.log(Level.WARNING, "[LoginServlet] Failed to close DB conn", e); }}
        }
        
        if (!response.isCommitted()) {
            logger.severe("[LoginServlet] doPost completed WITHOUT an explicit redirect or response. Forcing redirect to login.");
            session.setAttribute("errorMessage", "Login processing error. Please try again.");
            response.sendRedirect("login.jsp");
        }
    }
}