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
import java.util.logging.Logger;

@WebServlet("/WizardStatusServlet")
public class WizardStatusServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(WizardStatusServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("TenantID") == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeUrlParam("Session expired."));
            return;
        }

        String action = request.getParameter("action");
        String currentStep = request.getParameter("currentStep");
        String nextStep = request.getParameter("nextStep");
        logger.info("WizardStatusServlet received action: " + action + " from step: " + currentStep + " nextStep: " + nextStep);

        // Handles AJAX calls to set the wizard step in the session
        if ("setWizardStep".equals(action) && nextStep != null) {
            session.setAttribute("wizardStep", nextStep);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":true,\"nextStep\":\"" + nextStep + "\"}");
            return;
        }

        // ** FIX: Added handler for marking the intro as shown **
        if ("markAdminIntroAsShown".equals(action)) {
            session.setAttribute("admin_profile_intro_shown_employees_wizard", true);
            logger.info("Session attribute 'admin_profile_intro_shown_employees_wizard' set to true.");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":true}");
            return;
        }
        
        // Finalizes the wizard and removes session flags
        if ("completeWizard".equals(action)) {
            session.removeAttribute("startSetupWizard");
            session.removeAttribute("wizardStep");
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"success\":true}");
            return;
        }

        String nextStepUrl = "";
        
        // Handles form submissions from wizard pages to advance to the next step
        if ("nextStep".equals(action) || "skipStep".equals(action)) {
            String step = (currentStep != null) ? currentStep : (String) session.getAttribute("wizardStep");
            switch (step) {
                case "settings_setup":
                    session.setAttribute("wizardStep", "departments");
                    nextStepUrl = "/departments.jsp?setup_wizard=true";
                    break;
                case "departments":
                    session.setAttribute("wizardStep", "schedules");
                    nextStepUrl = "/schedules.jsp?setup_wizard=true";
                    break;
                case "schedules":
                    session.setAttribute("wizardStep", "accruals");
                    nextStepUrl = "/accruals.jsp?setup_wizard=true";
                    break;
                case "accruals":
                    session.setAttribute("wizardStep", "editAdminProfile");
                    nextStepUrl = "/employees.jsp?setup_wizard=true&action=edit_admin_profile";
                    break;
                default:
                    // ** FIX: Changed fallback to a page that exists **
                    logger.warning("Unknown wizard step for 'nextStep' action: " + step);
                    nextStepUrl = "/employees.jsp?error=" + encodeUrlParam("Unknown wizard step.");
                    session.removeAttribute("startSetupWizard");
                    break;
            }
        } else {
             // ** FIX: Changed fallback to a page that exists **
            logger.warning("Invalid wizard action received: " + action);
            nextStepUrl = "/employees.jsp?error=" + encodeUrlParam("Invalid wizard action.");
        }
        
        response.sendRedirect(request.getContextPath() + nextStepUrl);
    }

    private String encodeUrlParam(String value) {
        if (value == null) return "";
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return "";
        }
    }
}