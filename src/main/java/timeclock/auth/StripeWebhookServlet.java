package timeclock.auth;

import com.google.gson.JsonSyntaxException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.net.Webhook;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/StripeWebhookServlet")
public class StripeWebhookServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(StripeWebhookServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {

        // FIX: Read the request body as raw bytes and then convert to a UTF-8 string.
        // This is the most reliable method and avoids any line ending modification.
        String payload;
        try (InputStream inputStream = request.getInputStream()) {
            payload = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Webhook error: Could not read request body.", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        String sigHeader = request.getHeader("Stripe-Signature");
        String endpointSecret = (String) getServletContext().getAttribute(StripeInitializer.WEBHOOK_SECRET_KEY);
        
        if (endpointSecret == null || endpointSecret.trim().isEmpty()) {
            logger.severe("FATAL: Webhook secret not found in Servlet Context.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }

        Event event;
        try {
            // The Stripe library will now use the raw, unaltered payload for verification.
            event = Webhook.constructEvent(payload, sigHeader, endpointSecret);

        } catch (SignatureVerificationException e) {
            logger.log(Level.SEVERE, "--> STRIPE SIGNATURE VERIFICATION FAILED! <--", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        } catch (JsonSyntaxException e) {
            logger.log(Level.WARNING, "Webhook error: Invalid JSON payload.", e);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        // If we get here, the signature was valid.
        logger.info("SUCCESS: Stripe Webhook signature verified. Event Type: " + event.getType());

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        if (dataObjectDeserializer.getObject().isEmpty()) {
            logger.warning("Webhook error: Event data object could not be deserialized.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        
        StripeObject stripeObject = dataObjectDeserializer.getObject().get();

        // Handle the verified event
        switch (event.getType()) {
            case "customer.subscription.updated":
                handleSubscriptionUpdated((Subscription) stripeObject);
                break;
            case "customer.subscription.deleted":
                handleSubscriptionDeleted((Subscription) stripeObject);
                break;
            default:
                logger.info("Received unhandled Stripe event type: " + event.getType());
        }

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void handleSubscriptionUpdated(Subscription subscription) {
        String newStatus = subscription.getStatus();
        String customerId = subscription.getCustomer();
        logger.info("Processing 'customer.subscription.updated' for customer " + customerId + ". New status: " + newStatus);
        
        String sql = "UPDATE tenants SET SubscriptionStatus = ? WHERE StripeCustomerID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, newStatus);
            pstmt.setString(2, customerId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("DB UPDATE SUCCESS for customer " + customerId);
            } else {
                logger.warning("DB UPDATE FAILED: Could not find a tenant with StripeCustomerID " + customerId);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error during subscription update for customer " + customerId, e);
        }
    }

    private void handleSubscriptionDeleted(Subscription subscription) {
        String customerId = subscription.getCustomer();
        logger.info("Processing 'customer.subscription.deleted' for customer " + customerId);
        
        String sql = "UPDATE tenants SET SubscriptionStatus = 'canceled' WHERE StripeCustomerID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, customerId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                logger.info("DB CANCEL SUCCESS for customer " + customerId);
            } else {
                logger.warning("DB CANCEL FAILED: Could not find a tenant with StripeCustomerID " + customerId);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error during subscription cancellation for customer " + customerId, e);
        }
    }
}