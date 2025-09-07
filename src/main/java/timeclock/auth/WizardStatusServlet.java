package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/WizardStatusServlet")
public class WizardStatusServlet extends HttpServlet {
    private static final long serialVersionUID = 2L;
    private static final Logger logger = Logger.getLogger(WizardStatusServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        HttpSession session = request.getSession(false);

        String action = request.getParameter("action");

        if (session == null) {
            out.print("{\"success\": false, \"error\": \"Session expired.\"}");
            out.flush();
            return;
        }

        try {
            if ("setWizardStep".equals(action)) {
                String nextStep = request.getParameter("nextStep");
                if (nextStep != null && !nextStep.trim().isEmpty()) {
                    session.setAttribute("wizardStep", nextStep);
                    logger.info("Wizard step updated to: " + nextStep);
                    out.print("{\"success\": true, \"nextStep\": \"" + nextStep + "\"}");
                } else {
                    out.print("{\"success\": false, \"error\": \"Next step not provided.\"}");
                }
            } else if ("endWizard".equals(action)) {
                // ## DEBUG START: Added logging to trace session attribute removal ##
                logger.info("--- WIZARD DEBUG: Received action 'endWizard'. ---");
                Integer tenantId = (Integer) session.getAttribute("TenantID");
                if (tenantId != null) {
                    String status = getSubscriptionStatus(tenantId);
                    session.setAttribute("SubscriptionStatus", status);
                    logger.info("[Wizard Debug] Set SubscriptionStatus='" + status + "' for TenantID " + tenantId + " at end of wizard.");
                }

                logger.info("[Wizard Debug] Session ID before removal: " + session.getId());
                logger.info("[Wizard Debug] 'startSetupWizard' attribute BEFORE removal: " + session.getAttribute("startSetupWizard"));
                
                session.removeAttribute("startSetupWizard");
                session.removeAttribute("wizardStep");
                session.removeAttribute("CompanyNameSignup");

                logger.info("[Wizard Debug] 'startSetupWizard' attribute AFTER removal: " + session.getAttribute("startSetupWizard"));
                logger.info("--- WIZARD DEBUG: Setup wizard session attributes cleared. Responding with success. ---");
                // ## DEBUG END ##

                out.print("{\"success\": true}");
            } else {
                out.print("{\"success\": false, \"error\": \"Invalid action.\"}");
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in WizardStatusServlet", e);
            out.print("{\"success\": false, \"error\": \"Server error processing wizard status.\"}");
        } finally {
            out.flush();
        }
    }
    
    private String getSubscriptionStatus(Integer tenantId) {
        if (tenantId == null) return null;
        String status = null;
        String sql = "SELECT SubscriptionStatus FROM tenants WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    status = rs.getString("SubscriptionStatus");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching subscription status for TenantID: " + tenantId, e);
        }
        return status;
    }
}