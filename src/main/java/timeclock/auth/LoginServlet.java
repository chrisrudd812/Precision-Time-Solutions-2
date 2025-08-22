package timeclock.auth;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LoginServlet.class.getName());

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

                int eid = rsUser.getInt("EID");
                String userPermissions = rsUser.getString("PERMISSIONS");
                session.setAttribute("EID", eid);
                session.setAttribute("UserFirstName", rsUser.getString("FIRST_NAME"));
                session.setAttribute("UserLastName", rsUser.getString("LAST_NAME"));
                session.setAttribute("Permissions", userPermissions);
                session.setAttribute("TenantID", tenantId);
                session.setAttribute("CompanyIdentifier", companyIdentifier.trim());
                session.setAttribute("Email", email.trim().toLowerCase());

                if ("Administrator".equalsIgnoreCase(userPermissions)) {
                    session.setMaxInactiveInterval(4 * 60 * 60); // 4 hours for admins
                } else {
                    session.setMaxInactiveInterval(1 * 60); // 1 minute for users
                }
                
                String subscriptionStatus = syncSubscriptionStatus(conn, tenantId);
                session.setAttribute("SubscriptionStatus", subscriptionStatus);

                if ("Administrator".equalsIgnoreCase(userPermissions) && 
                   ("canceled".equalsIgnoreCase(subscriptionStatus) || "unpaid".equalsIgnoreCase(subscriptionStatus) || "past_due".equalsIgnoreCase(subscriptionStatus))) {
                    
                    session.setAttribute("errorMessage", "Your subscription is inactive. Please update your billing information to restore access.");
                    response.sendRedirect("account.jsp");
                    return;
                }
                
                // *** FIX IS HERE: Changed getBoolean to getInt for reliability ***
                if (rsUser.getInt("RequiresPasswordChange") == 1) {
                    session.setAttribute("pinChangeRequired", true);
                    response.sendRedirect("change_password.jsp");
                } else {
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
        String currentDbStatus = "error";
        try {
            String sqlInfo = "SELECT StripeSubscriptionID, SubscriptionStatus, StripePriceID FROM tenants WHERE TenantID = ?";
            String stripeSubscriptionId = null;
            String currentDbPriceId = null;
            
            try (PreparedStatement pstmtInfo = conn.prepareStatement(sqlInfo)) {
                pstmtInfo.setInt(1, tenantId);
                ResultSet rsTenant = pstmtInfo.executeQuery();
                if (rsTenant.next()) {
                    stripeSubscriptionId = rsTenant.getString("StripeSubscriptionID");
                    currentDbStatus = rsTenant.getString("SubscriptionStatus");
                    currentDbPriceId = rsTenant.getString("StripePriceID");
                } else {
                     throw new SQLException("Tenant not found for ID: " + tenantId);
                }
            }

            if (stripeSubscriptionId == null || stripeSubscriptionId.trim().isEmpty()) {
                return currentDbStatus;
            }

            Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);
            String newStripeStatus = stripeSubscription.getStatus();
            String newPriceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();

            boolean needsUpdate = !newStripeStatus.equalsIgnoreCase(currentDbStatus) || (currentDbPriceId != null && !newPriceId.equals(currentDbPriceId));

            if (needsUpdate) {
                Timestamp newPeriodEnd = new Timestamp(stripeSubscription.getCurrentPeriodEnd() * 1000L);
                int newMaxUsers = getMaxUsersForPriceId(conn, newPriceId);

                String sqlUpdate = "UPDATE tenants SET SubscriptionStatus = ?, StripePriceID = ?, CurrentPeriodEnd = ?, MaxUsers = ? WHERE TenantID = ?";
                try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {
                    pstmtUpdate.setString(1, newStripeStatus);
                    pstmtUpdate.setString(2, newPriceId);
                    pstmtUpdate.setTimestamp(3, newPeriodEnd);
                    pstmtUpdate.setInt(4, newMaxUsers);
                    pstmtUpdate.setInt(5, tenantId);
                    pstmtUpdate.executeUpdate();
                }
                return newStripeStatus;
            }
            return currentDbStatus;

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not sync subscription status for TenantID " + tenantId, e);
            return currentDbStatus;
        }
    }
    
    private int getMaxUsersForPriceId(Connection conn, String priceId) throws SQLException {
        String sql = "SELECT maxUsers FROM subscription_plans WHERE stripePriceId = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, priceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("maxUsers");
                }
            }
        }
        return 25; // Fallback default
    }
}