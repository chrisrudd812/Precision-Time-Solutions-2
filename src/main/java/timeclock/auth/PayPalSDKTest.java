package timeclock.auth;

// Attempt to import some core PayPal SDK classes
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.subscriptions.Plan; // Just to test a class from subscriptions package

public class PayPalSDKTest {

    public static void main(String[] args) {
        System.out.println("Attempting to reference PayPal SDK classes...");

        try {
            // Try to reference a class directly.
            // We are not trying to make API calls, just see if the class can be found.
            Class<?> environmentClass = PayPalEnvironment.class;
            System.out.println("Successfully referenced: " + environmentClass.getName());

            Class<?> clientClass = PayPalHttpClient.class;
            System.out.println("Successfully referenced: " + clientClass.getName());

            Class<?> planClass = Plan.class;
            System.out.println("Successfully referenced: " + planClass.getName());

            System.out.println("PayPal SDK Test: Basic class references seem OKAY if you see this without compilation errors BEFORE running.");

        } catch (NoClassDefFoundError e) {
            System.err.println("RUNTIME ERROR: NoClassDefFoundError. This means a class was available at compile time but not at runtime.");
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("An unexpected error occurred during PayPal SDK Test:");
            e.printStackTrace();
        }
    }
}