package timeclock.auth;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.billingportal.Session;
import com.stripe.param.billingportal.SessionCreateParams;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/StripePortalServlet")
public class StripePortalServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(StripePortalServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("TenantID") == null) {
            response.sendRedirect("login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8));
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        String stripeCustomerId = getStripeCustomerId(tenantId);

        if (stripeCustomerId == null || stripeCustomerId.trim().isEmpty()) {
            session.setAttribute("errorMessage", "Could not manage billing: Customer ID not found.");
            response.sendRedirect("account.jsp");
            return;
        }

        // FIX: Add a parameter to the return URL so the page knows to check for updates.
        String returnUrl = request.getScheme() + "://" + request.getServerName();
        if (request.getServerPort() != 80 && request.getServerPort() != 443) {
            returnUrl += ":" + request.getServerPort();
        }
        returnUrl += request.getContextPath() + "/account.jsp?from_portal=true";


        try {
            SessionCreateParams params = SessionCreateParams.builder()
                .setCustomer(stripeCustomerId)
                .setReturnUrl(returnUrl)
                .build();

            Session portalSession = Session.create(params);
            
            response.sendRedirect(portalSession.getUrl());

        } catch (StripeException e) {
            logger.log(Level.SEVERE, "StripeException creating billing portal session for TenantID: " + tenantId, e);
            session.setAttribute("errorMessage", "Error accessing billing portal. Please contact support.");
            response.sendRedirect("account.jsp");
        }
    }

    private String getStripeCustomerId(Integer tenantId) {
        if (tenantId == null) return null;
        
        String customerId = null;
        String sql = "SELECT StripeCustomerID FROM tenants WHERE TenantID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
             
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    customerId = rs.getString("StripeCustomerID");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching StripeCustomerID for TenantID: " + tenantId, e);
        }
        return customerId;
    }
}