package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/BillingServlet")
public class BillingServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(BillingServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("TenantID") == null) {
            response.sendRedirect("login.jsp?error=" + encode("Session expired. Please log in."));
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        String cardholderName = request.getParameter("cardholderName");
        String cardNumber = request.getParameter("cardNumber");

        // Basic validation
        if (cardholderName == null || cardholderName.trim().isEmpty() || cardNumber == null || cardNumber.trim().isEmpty()) {
            session.setAttribute("errorMessage", "Cardholder Name and Card Number are required.");
            response.sendRedirect("account.jsp");
            return;
        }

        // --- Placeholder for Real Payment Gateway Integration (e.g., Stripe) ---
        try {
            logger.info("Simulating payment information processing for TenantID: " + tenantId);
            
            // In a real application, you would send data to Stripe and get a token.
            // You would then save the Stripe Customer ID and Payment Method ID to your database.
            // UPDATE tenants SET StripeCustomerID = 'cus_xxxx', StripePaymentMethodID = 'pm_xxxx' WHERE TenantID = ?
            
            Thread.sleep(1500); // Simulate network latency

            session.setAttribute("successMessage", "Billing information has been updated successfully!");
            response.sendRedirect("account.jsp");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during billing processing simulation for TenantID: " + tenantId, e);
            session.setAttribute("errorMessage", "An unexpected error occurred while saving billing info.");
            response.sendRedirect("account.jsp");
        }
    }

    private String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }
}
