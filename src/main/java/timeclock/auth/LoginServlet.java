package timeclock.auth;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LoginServlet.class.getName());

    private static final Map<String, Integer> PRICE_ID_TO_MAX_USERS = Map.of(
        "price_1RttGyBtvyYfb2KWNRui8ev1", 50,  // Example: Business Plan
        "price_1RttIyBtvyYfb2KW86IvsAvX", 100, // Example: Pro Plan
        "price_1RWNdXBtvyYfb2KWWt6p9F4X", 25   // Example: Starter Plan
    );

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String companyIdentifier = request.getParameter("companyIdentifier");
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        HttpSession session = request.getSession(true);

        if (!ShowPunches.isValid(companyIdentifier) || !ShowPunches.isValid(email) || !ShowPunches.isValid(password)) {
            response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Company ID, email, and PIN are required.", StandardCharsets.UTF_8));
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
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
                // --- AUTHENTICATION SUCCESSFUL ---
                
                if (!rsUser.getBoolean("ACTIVE")) {
                    response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Account is inactive.", StandardCharsets.UTF_8));
                    return;
                }

                // Set session attributes immediately after authentication.
                int eid = rsUser.getInt("EID");
                String userPermissions = rsUser.getString("PERMISSIONS");
                session.setAttribute("EID", eid);
                session.setAttribute("UserFirstName", rsUser.getString("FIRST_NAME"));
                session.setAttribute("UserLastName", rsUser.getString("LAST_NAME"));
                session.setAttribute("Permissions", userPermissions);
                session.setAttribute("TenantID", tenantId);
                session.setAttribute("CompanyIdentifier", companyIdentifier.trim());
                session.setAttribute("Email", email.trim().toLowerCase());

                // Set session timeout based on role
                if ("Administrator".equalsIgnoreCase(userPermissions)) {
                    session.setMaxInactiveInterval(8 * 60 * 60); // 8 hours for admins
                    logger.info("Admin session timeout set to 8 hours for user EID: " + eid);
                } else {
                    session.setMaxInactiveInterval(1 * 60); // 1 minute for users
                    logger.info("User session timeout set to 1 minute for user EID: " + eid);
                }
                
                String subscriptionStatus = syncSubscriptionStatus(conn, tenantId);
                session.setAttribute("SubscriptionStatus", subscriptionStatus);

                if ("canceled".equalsIgnoreCase(subscriptionStatus) || "unpaid".equalsIgnoreCase(subscriptionStatus) || "past_due".equalsIgnoreCase(subscriptionStatus)) {
                    logger.warning("Subscription for TenantID " + tenantId + " is inactive. Redirecting to account page.");
                    session.setAttribute("errorMessage", "Your subscription is inactive. Please update your billing information to restore access.");
                    response.sendRedirect("account.jsp");
                    return;
                }
                
                if (rsUser.getBoolean("RequiresPasswordChange")) {
                    session.setAttribute("pinChangeRequired", true);
                    response.sendRedirect("change_password.jsp");
                } else {
                    // This is a normal login, so ensure wizard mode is off.
                    session.removeAttribute("startSetupWizard");
                    String targetPage = "Administrator".equalsIgnoreCase(userPermissions) ? "employees.jsp" : "timeclock.jsp";
                    response.sendRedirect(targetPage);
                }

            } else {
                response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Invalid credentials.", StandardCharsets.UTF_8));
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during login process", e);
            response.sendRedirect("login.jsp?error=" + URLEncoder.encode("A server error occurred. Please try again.", StandardCharsets.UTF_8));
        }
    }

    private String syncSubscriptionStatus(Connection conn, int tenantId) {
        String sqlInfo = "SELECT StripeSubscriptionID, SubscriptionStatus, StripePriceID, MaxUsers FROM tenants WHERE TenantID = ?";
        try (PreparedStatement pstmtInfo = conn.prepareStatement(sqlInfo)) {
            pstmtInfo.setInt(1, tenantId);
            ResultSet rsTenant = pstmtInfo.executeQuery();

            if (!rsTenant.next()) {
                logger.warning("Could not find tenant info for TenantID " + tenantId + " during sync.");
                return "error";
            }
            
            String stripeSubscriptionId = rsTenant.getString("StripeSubscriptionID");
            String currentDbStatus = rsTenant.getString("SubscriptionStatus");
            String currentDbPriceId = rsTenant.getString("StripePriceID");
            int currentDbMaxUsers = rsTenant.getInt("MaxUsers");

            if (stripeSubscriptionId == null || stripeSubscriptionId.trim().isEmpty()) {
                return currentDbStatus;
            }

            Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);
            String newStripeStatusFromApi = stripeSubscription.getStatus();
            Boolean isPendingCancellation = stripeSubscription.getCancelAtPeriodEnd();

            String effectiveStatus = newStripeStatusFromApi;
            if ("active".equalsIgnoreCase(newStripeStatusFromApi) && Boolean.TRUE.equals(isPendingCancellation)) {
                effectiveStatus = "canceled";
            }

            String newPriceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();
            Integer newMaxUsers = PRICE_ID_TO_MAX_USERS.getOrDefault(newPriceId, 25);

            boolean needsUpdate = !effectiveStatus.equalsIgnoreCase(currentDbStatus) ||
                                  (currentDbPriceId != null && !newPriceId.equals(currentDbPriceId)) ||
                                  newMaxUsers != currentDbMaxUsers;

            if (needsUpdate) {
                String sqlUpdate = "UPDATE tenants SET SubscriptionStatus = ?, StripePriceID = ?, MaxUsers = ? WHERE TenantID = ?";
                try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {
                    pstmtUpdate.setString(1, effectiveStatus);
                    pstmtUpdate.setString(2, newPriceId);
                    pstmtUpdate.setInt(3, newMaxUsers);
                    pstmtUpdate.setInt(4, tenantId);
                    pstmtUpdate.executeUpdate();
                }
                return effectiveStatus;
            }
            return currentDbStatus;

        } catch (StripeException | SQLException e) {
            logger.log(Level.SEVERE, "Could not sync subscription status for TenantID " + tenantId, e);
            try (PreparedStatement pstmt = conn.prepareStatement("SELECT SubscriptionStatus FROM tenants WHERE TenantID = ?")) {
                 pstmt.setInt(1, tenantId);
                 ResultSet rs = pstmt.executeQuery();
                 return rs.next() ? rs.getString("SubscriptionStatus") : "error";
            } catch (SQLException ex) {
                return "error";
            }
        }
    }
}