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
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/SubscriptionStatusServlet")
public class SubscriptionStatusServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SubscriptionStatusServlet.class.getName());

    // In a real app, this mapping might come from a database or config file.
    private static final Map<String, Integer> PRICE_ID_TO_MAX_USERS = Map.of(
        "price_1RttGyBtvyYfb2KWNRui8ev1", 50,  // Example: Business Plan
        "price_1RttIyBtvyYfb2KW86IvsAvX", 100, // Example: Pro Plan
        "price_1RWNdXBtvyYfb2KWWt6p9F4X", 25   // Example: Starter Plan
    );

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject jsonResponse = new JSONObject();
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("TenantID") == null) {
            jsonResponse.put("success", false).put("error", "Session expired.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().print(jsonResponse.toString());
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        String[] dbInfo = getSubscriptionInfoFromDb(tenantId);
        String stripeSubscriptionId = dbInfo[0];
        String currentDbStatus = dbInfo[1];

        if (stripeSubscriptionId == null) {
            jsonResponse.put("success", false).put("error", "No subscription ID found for this account.");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().print(jsonResponse.toString());
            return;
        }

        try {
            Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);
            String newStripeStatus = stripeSubscription.getStatus(); // "active", "trialing", "canceled", etc.
            String newPriceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();
            
            // Determine new max users based on the price ID
            Integer newMaxUsers = PRICE_ID_TO_MAX_USERS.getOrDefault(newPriceId, 25); // Default to 25 if price ID is unknown

            // Only update if something has actually changed
            if (!newStripeStatus.equalsIgnoreCase(currentDbStatus) || !newPriceId.equals(dbInfo[2]) || !newMaxUsers.equals(Integer.valueOf(dbInfo[3]))) {
                updateTenantSubscription(tenantId, newStripeStatus, newPriceId, newMaxUsers);
                jsonResponse.put("success", true).put("message", "Your subscription has been updated successfully!");
                logger.info("Subscription for TenantID " + tenantId + " was updated. New status: " + newStripeStatus);
            } else {
                jsonResponse.put("success", true).put("message", "Your subscription is already up to date.");
                logger.info("Subscription for TenantID " + tenantId + " checked, no changes found.");
            }

        } catch (StripeException | SQLException e) {
            logger.log(Level.SEVERE, "Error checking subscription status for TenantID: " + tenantId, e);
            jsonResponse.put("success", false).put("error", "An error occurred while syncing your subscription.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        response.getWriter().print(jsonResponse.toString());
    }

    private String[] getSubscriptionInfoFromDb(int tenantId) {
        String[] info = new String[4]; // [SubscriptionID, Status, PriceID, MaxUsers]
        String sql = "SELECT StripeSubscriptionID, SubscriptionStatus, StripePriceID, MaxUsers FROM tenants WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    info[0] = rs.getString("StripeSubscriptionID");
                    info[1] = rs.getString("SubscriptionStatus");
                    info[2] = rs.getString("StripePriceID");
                    info[3] = String.valueOf(rs.getInt("MaxUsers"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching subscription info from DB for TenantID: " + tenantId, e);
        }
        return info;
    }

    private void updateTenantSubscription(int tenantId, String newStatus, String newPriceId, int newMaxUsers) throws SQLException {
        String sql = "UPDATE tenants SET SubscriptionStatus = ?, StripePriceID = ?, MaxUsers = ? WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setString(2, newPriceId);
            pstmt.setInt(3, newMaxUsers);
            pstmt.setInt(4, tenantId);
            pstmt.executeUpdate();
        }
    }
}