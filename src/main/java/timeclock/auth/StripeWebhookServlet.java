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
import com.stripe.model.Customer;

@WebServlet("/StripeWebhookServlet")
public class StripeWebhookServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(StripeWebhookServlet.class.getName());
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Health check endpoint for Stripe webhook
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"status\":\"ok\",\"service\":\"stripe-webhook\"}");
        logger.info("Webhook health check requested from: " + request.getRemoteAddr());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        try {
            // Set response headers for webhook
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            
            // Read the request body
            String payload;
            try (InputStream inputStream = request.getInputStream()) {
                payload = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Webhook error: Could not read request body.", e);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Could not read request body\"}");
                return;
            }

            String sigHeader = request.getHeader("Stripe-Signature");
            if (sigHeader == null || sigHeader.trim().isEmpty()) {
                logger.warning("Webhook error: Missing Stripe-Signature header.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Missing signature header\"}");
                return;
            }
            
            String endpointSecret = (String) getServletContext().getAttribute(StripeInitializer.WEBHOOK_SECRET_KEY);
            if (endpointSecret == null || endpointSecret.trim().isEmpty()) {
                logger.severe("FATAL: Webhook secret not found in Servlet Context.");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Server configuration error\"}");
                return;
            }

            Event event;
            try {
                event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            } catch (SignatureVerificationException e) {
                logger.log(Level.SEVERE, "Stripe signature verification failed", e);
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.getWriter().write("{\"error\":\"Invalid signature\"}");
                return;
            } catch (JsonSyntaxException e) {
                logger.log(Level.WARNING, "Webhook error: Invalid JSON payload", e);
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid JSON\"}");
                return;
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error constructing webhook event", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Server error\"}");
                return;
            }
            
            // Process the event
            boolean processed = false;
            try {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                if (dataObjectDeserializer.getObject().isEmpty()) {
                    logger.warning("Webhook warning: Event data object could not be deserialized for event type: " + event.getType());
                    // Still return 200 - this is not a client error
                    response.setStatus(HttpServletResponse.SC_OK);
                    response.getWriter().write("{\"received\":true,\"warning\":\"Could not deserialize object\"}");
                    return;
                }
                
                StripeObject stripeObject = dataObjectDeserializer.getObject().get();

                // Handle the verified event
                switch (event.getType()) {
                    case "customer.subscription.updated":
                        handleSubscriptionUpdated((Subscription) stripeObject);
                        processed = true;
                        break;
                    case "customer.subscription.deleted":
                        handleSubscriptionDeleted((Subscription) stripeObject);
                        processed = true;
                        break;
                    case "customer.created":
                        handleCustomerCreated(event);
                        processed = true;
                        break;
                    case "customer.subscription.created":
                        handleSubscriptionCreated(event);
                        processed = true;
                        break;
                    default:
                        logger.info("Unhandled webhook event type: " + event.getType());
                        processed = true; // Still successful - we just don't handle this event type
                }
                
                // Success response
                response.setStatus(HttpServletResponse.SC_OK);
                response.getWriter().write("{\"received\":true,\"processed\":" + processed + "}");
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Error processing webhook event: " + event.getType(), e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Processing failed\"}");
            }
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error in webhook servlet", e);
            try {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                response.getWriter().write("{\"error\":\"Unexpected server error\"}");
            } catch (IOException ioException) {
                logger.log(Level.SEVERE, "Could not write error response", ioException);
            }
        }
    }

    private void handleSubscriptionUpdated(Subscription subscription) throws Exception {
        try {
            String newStatus = subscription.getStatus();
            String customerId = subscription.getCustomer();
            Long currentPeriodEnd = subscription.getCurrentPeriodEnd();
            
            if (subscription.getItems() == null || subscription.getItems().getData().isEmpty()) {
                throw new Exception("No subscription items found for customer " + customerId);
            }
            
            String priceId = subscription.getItems().getData().get(0).getPrice().getId();
            
            String sql = "UPDATE tenants SET SubscriptionStatus = ?, CurrentPeriodEnd = FROM_UNIXTIME(?), StripePriceID = ?, " +
                         "MaxUsers = (SELECT maxUsers FROM subscription_plans WHERE stripePriceId = ?) " +
                         "WHERE StripeCustomerID = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, newStatus);
                pstmt.setLong(2, currentPeriodEnd);
                pstmt.setString(3, priceId);
                pstmt.setString(4, priceId);
                pstmt.setString(5, customerId);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    logger.info("Subscription updated for customer " + customerId + ": status=" + newStatus + ", periodEnd=" + currentPeriodEnd + ", priceId=" + priceId);
                } else {
                    logger.warning("DB UPDATE FAILED: Could not find a tenant with StripeCustomerID " + customerId);
                    throw new Exception("No tenant found with customer ID " + customerId);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling subscription update", e);
            throw e; // Re-throw to be handled by caller
        }
    }

    private void handleSubscriptionDeleted(Subscription subscription) throws Exception {
        try {
            String customerId = subscription.getCustomer();
            
            String sql = "UPDATE tenants SET SubscriptionStatus = 'canceled' WHERE StripeCustomerID = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, customerId);
                int rowsAffected = pstmt.executeUpdate();
                if (rowsAffected > 0) {
                    logger.info("Subscription canceled for customer " + customerId);
                } else {
                    logger.warning("DB CANCEL FAILED: Could not find a tenant with StripeCustomerID " + customerId);
                    throw new Exception("No tenant found with customer ID " + customerId);
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling subscription deletion", e);
            throw e; // Re-throw to be handled by caller
        }
    }

    private void handleCustomerCreated(Event event) throws Exception {
        try {
            // Extract data directly from the event JSON
            String eventData = event.toJson();
            logger.info("Customer created event received: " + event.getId());
            
            // Send email notification with basic info
            sendEmailNotification("New Customer Signup", 
                "New customer signed up:\n\n" +
                "Event ID: " + event.getId() + "\n" +
                "Event Type: " + event.getType() + "\n" +
                "Time: " + new java.util.Date());
                
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling customer creation", e);
            throw e;
        }
    }

    private void handleSubscriptionCreated(Event event) throws Exception {
        try {
            logger.info("Subscription created event received: " + event.getId());
            
            // Send email notification
            sendEmailNotification("New Subscription Started", 
                "New subscription started:\n\n" +
                "Event ID: " + event.getId() + "\n" +
                "Event Type: " + event.getType() + "\n" +
                "Time: " + new java.util.Date());
                
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error handling subscription creation", e);
            throw e;
        }
    }

    private void sendEmailNotification(String subject, String message) {
        try {
            String notificationEmail = System.getenv("NOTIFICATION_EMAIL");
            if (notificationEmail == null) {
                notificationEmail = "chrisrudd812@gmail.com"; // fallback
            }
            
            boolean sent = EmailService.sendEmail(notificationEmail, subject, message);
            if (sent) {
                logger.info("Email notification sent: " + subject);
            } else {
                logger.warning("Failed to send email notification: " + subject);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to send email notification", e);
        }
    }
}