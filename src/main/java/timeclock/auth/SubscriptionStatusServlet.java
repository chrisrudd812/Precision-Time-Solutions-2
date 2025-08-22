package timeclock.auth;

import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/SubscriptionStatusServlet")
public class SubscriptionStatusServlet extends HttpServlet {
    private static final long serialVersionUID = 2L; // Version update
    private static final Logger logger = Logger.getLogger(SubscriptionStatusServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject jsonResponse = new JSONObject();
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("TenantID") == null) {
            jsonResponse.put("success", false).put("error", "Session expired. Please log in again.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            try (PrintWriter out = response.getWriter()) {
                out.print(jsonResponse.toString());
            }
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        
        try (Connection conn = DatabaseConnection.getConnection()) {
            String stripeSubscriptionId = getStripeSubscriptionId(conn, tenantId);

            if (stripeSubscriptionId == null) {
                throw new Exception("No Subscription ID found for this account.");
            }

            Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);

            String newStatus = stripeSubscription.getStatus();
            String newPriceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();
            Timestamp newPeriodEnd = new Timestamp(stripeSubscription.getCurrentPeriodEnd() * 1000L);

            // IMPROVEMENT: Get MaxUsers dynamically from your subscription_plans table
            int newMaxUsers = getMaxUsersForPriceId(conn, newPriceId);

            updateTenantSubscription(conn, tenantId, newStatus, newPriceId, newPeriodEnd, newMaxUsers);
            jsonResponse.put("success", true).put("message", "Subscription details successfully synced!");

        } catch (StripeException e) {
            logger.log(Level.SEVERE, "Stripe API error syncing subscription for TenantID: " + tenantId, e);
            jsonResponse.put("success", false).put("error", "Could not connect to the billing service to sync your plan.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error syncing subscription for TenantID: " + tenantId, e);
            jsonResponse.put("success", false).put("error", "A database error occurred while syncing your plan.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "General error syncing subscription for TenantID: " + tenantId, e);
            jsonResponse.put("success", false).put("error", e.getMessage());
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        try (PrintWriter out = response.getWriter()) {
            out.print(jsonResponse.toString());
        }
    }

    private String getStripeSubscriptionId(Connection conn, int tenantId) throws SQLException {
        String sql = "SELECT StripeSubscriptionID FROM tenants WHERE TenantID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("StripeSubscriptionID");
                }
            }
        }
        return null;
    }

    private int getMaxUsersForPriceId(Connection conn, String priceId) throws SQLException, Exception {
        String sql = "SELECT maxUsers FROM subscription_plans WHERE stripePriceId = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, priceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("maxUsers");
                } else {
                    // This is a safeguard in case a plan exists in Stripe but not your DB
                    logger.log(Level.WARNING, "Plan details for Price ID {0} not found in local DB. Defaulting to 25 users.", priceId);
                    return 25; 
                }
            }
        }
    }

    private void updateTenantSubscription(Connection conn, int tenantId, String status, String priceId, Timestamp periodEnd, int maxUsers) throws SQLException {
        String sql = "UPDATE tenants SET SubscriptionStatus = ?, StripePriceID = ?, CurrentPeriodEnd = ?, MaxUsers = ? WHERE TenantID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status);
            pstmt.setString(2, priceId);
            pstmt.setTimestamp(3, periodEnd);
            pstmt.setInt(4, maxUsers);
            pstmt.setInt(5, tenantId);
            
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.log(Level.INFO, "Tenant {0} subscription updated. Status: {1}, PriceID: {2}, MaxUsers: {3}", new Object[]{tenantId, status, priceId, maxUsers});
            } else {
                 logger.log(Level.WARNING, "Failed to update subscription for TenantID {0}, tenant not found.", tenantId);
            }
        }
    }
}