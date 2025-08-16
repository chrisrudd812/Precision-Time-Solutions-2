package timeclock.auth;

import com.stripe.Stripe;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Logger;

@WebListener
public class StripeInitializer implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(StripeInitializer.class.getName());
    public static final String WEBHOOK_SECRET_KEY = "stripeWebhookSecret";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try (InputStream input = StripeInitializer.class.getClassLoader().getResourceAsStream("stripe.properties")) {
            
            Properties props = new Properties();
            if (input == null) {
                logger.severe("FATAL: Cannot find stripe.properties file in the classpath.");
                return;
            }
            props.load(input);

            String stripeApiKey = props.getProperty("stripe.apiKey");
            String webhookSecret = props.getProperty("stripe.webhookSecret");

            if (stripeApiKey == null || stripeApiKey.trim().isEmpty()) {
                logger.severe("FATAL: stripe.apiKey is not set in stripe.properties!");
            } else {
                Stripe.apiKey = stripeApiKey;
                logger.info("Stripe API Key initialized successfully from properties file.");
            }

            if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
                logger.severe("FATAL: stripe.webhookSecret is not set in stripe.properties!");
            } else {
                ServletContext context = sce.getServletContext();
                context.setAttribute(WEBHOOK_SECRET_KEY, webhookSecret);
                // --- DEBUG LOGGING: Print the full secret to the console at startup ---
                logger.info("--- STRIPE DEBUG ---");
                logger.info("Full Webhook Secret loaded: " + webhookSecret);
                logger.info("--- END STRIPE DEBUG ---");
            }

        } catch (Exception e) {
            logger.severe("FATAL: Could not initialize Stripe keys from stripe.properties file. Error: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No action needed on shutdown.
    }
}