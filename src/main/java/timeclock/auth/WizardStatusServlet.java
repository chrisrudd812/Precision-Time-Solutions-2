package timeclock.auth; // Ensure this is your actual package

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;
import org.json.JSONObject; // Ensure this library is available

@WebServlet("/WizardStatusServlet") // Mapped relative to the context path
public class WizardStatusServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(WizardStatusServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();

        String action = request.getParameter("action");
        HttpSession session = request.getSession(false);

        if (session == null) {
            logger.warning("[WizardStatusServlet] No active session found for action: " + action + ". User needs to log in.");
            jsonResponse.put("success", false).put("error", "No active session. Please log in again.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // Unauthorized
            out.print(jsonResponse.toString());
            out.flush();
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID"); // For logging context
        String logPrefix = "[WizardStatusServlet TenantID: " + (tenantId != null ? tenantId : "N/A") + "] ";
        logger.info(logPrefix + "Action '" + action + "' received.");

        if ("completeWizard".equals(action)) {
            try {
                // Clear all wizard-specific session attributes
                session.removeAttribute("startSetupWizard");
                session.removeAttribute("wizardStep");
                session.removeAttribute("wizardAdminEid");
                session.removeAttribute("wizardAdminEmail"); // From SignupServlet
                session.removeAttribute("wizardAdminFirstName"); // From SignupServlet
                session.removeAttribute("CompanyNameSignup");
                session.removeAttribute("GeneratedCompanyID"); // From SignupServlet
                session.removeAttribute("SignupCompanyState"); // From SignupServlet
                session.removeAttribute("signupSuccessfulCompanyInfo"); // From SignupServlet
                session.removeAttribute("wizardWelcomeMessage"); // Set by SetInitialPinServlet or others

                // Clear any other wizard-specific session attributes you might have set on other pages
                // e.g., session.removeAttribute("departments_intro_shown_wizard");

                logger.info(logPrefix + "Wizard session attributes cleared for wizard completion.");
                jsonResponse.put("success", true).put("message", "Wizard status cleared successfully. Setup complete!");
                response.setStatus(HttpServletResponse.SC_OK);
            } catch (Exception e) {
                logger.severe(logPrefix + "Error clearing wizard status: " + e.getMessage());
                jsonResponse.put("success", false).put("error", "Server error while clearing wizard status: " + e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } else if ("setWizardStep".equals(action)) {
            String nextStep = request.getParameter("nextStep");
            if (nextStep != null && !nextStep.trim().isEmpty()) {
                try {
                    String previousStep = (String) session.getAttribute("wizardStep");
                    session.setAttribute("wizardStep", nextStep.trim());

                    // Clear any general success/error messages that might linger from a previous step's page
                    session.removeAttribute("successMessage");
                    session.removeAttribute("errorMessage");
                    // Also clear page-specific messages if you use them, e.g.:
                    // session.removeAttribute("errorMessage_initialPin");


                    logger.info(logPrefix + "Wizard step updated from '" + (previousStep != null ? previousStep : "N/A") + "' to '" + nextStep.trim() + "'.");
                    jsonResponse.put("success", true).put("message", "Wizard step updated to " + nextStep.trim() + ".");
                    jsonResponse.put("nextStep", nextStep.trim()); // Return the step that was set
                    response.setStatus(HttpServletResponse.SC_OK);
                } catch (Exception e) {
                    logger.severe(logPrefix + "Error setting wizard step to '" + nextStep + "': " + e.getMessage());
                    jsonResponse.put("success", false).put("error", "Server error while setting next wizard step: " + e.getMessage());
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } else {
                logger.warning(logPrefix + "'setWizardStep' action called without 'nextStep' parameter.");
                jsonResponse.put("success", false).put("error", "Next step parameter not specified.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
        } else {
            logger.warning(logPrefix + "Invalid action received: '" + action + "'.");
            jsonResponse.put("success", false).put("error", "Invalid action specified for wizard.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        out.print(jsonResponse.toString());
        out.flush();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Generally, wizard status changes should be POST to avoid issues with idempotency
        logger.warning("[WizardStatusServlet] GET method called, which is not supported. Action: " + request.getParameter("action"));
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "GET method is not supported for this servlet. Please use POST.");
    }
}