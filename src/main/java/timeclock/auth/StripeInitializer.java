package timeclock.auth;

import com.stripe.Stripe;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Level;
import java.util.logging.Logger;

@WebListener
public class StripeInitializer implements ServletContextListener {

    private static final Logger logger = Logger.getLogger(StripeInitializer.class.getName());

    // The key for storing the webhook secret in the ServletContext
    public static final String WEBHOOK_SECRET_KEY = "stripeWebhookSecret";

    // The names of the environment variables to read from
    private static final String STRIPE_API_KEY_ENV = "STRIPE_API_KEY";
    private static final String STRIPE_WEBHOOK_SECRET_ENV = "STRIPE_WEBHOOK_SECRET";

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            // Read the Stripe API Key from an environment variable
            String stripeApiKey = getEnvVariable(STRIPE_API_KEY_ENV);
            Stripe.apiKey = stripeApiKey;

            // Read the Stripe Webhook Secret from an environment variable
            String webhookSecret = getEnvVariable(STRIPE_WEBHOOK_SECRET_ENV);
            ServletContext context = sce.getServletContext();
            context.setAttribute(WEBHOOK_SECRET_KEY, webhookSecret);

        } catch (Exception e) {
            // If any variable is missing, the getEnvVariable method will throw an exception.
            // We log it here to ensure visibility during server startup.
            logger.log(Level.SEVERE, "FATAL: Could not initialize Stripe. " + e.getMessage(), e);
            // It's often good practice to rethrow to prevent the app from starting in a bad state.
            throw new RuntimeException("Failed to initialize Stripe due to missing configuration.", e);
        }
    }

    /**
     * Helper method to get an environment variable and throw an error if it's missing.
     * @param key The name of the environment variable.
     * @return The value of the environment variable.
     * @throws IllegalStateException if the environment variable is not set.
     */
    private static String getEnvVariable(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Required environment variable '" + key + "' is not set.");
        }
        return value;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // No action needed on shutdown.
    }
}