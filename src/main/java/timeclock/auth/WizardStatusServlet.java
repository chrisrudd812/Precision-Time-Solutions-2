package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Logger;

@WebServlet("/WizardStatusServlet")
public class WizardStatusServlet extends HttpServlet {
    private static final long serialVersionUID = 2L; // Version updated
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
                    // **FIX**: Return the nextStep in the JSON response so the JS knows where to go.
                    out.print("{\"success\": true, \"nextStep\": \"" + nextStep + "\"}");
                } else {
                    out.print("{\"success\": false, \"error\": \"Next step not provided.\"}");
                }
            } else if ("endWizard".equals(action)) {
                session.removeAttribute("startSetupWizard");
                session.removeAttribute("wizardStep");
                session.removeAttribute("CompanyNameSignup");
                logger.info("Setup wizard session attributes cleared. Wizard ended.");
                out.print("{\"success\": true}");
            } else {
                out.print("{\"success\": false, \"error\": \"Invalid action.\"}");
            }
        } catch (Exception e) {
            logger.severe("Error in WizardStatusServlet: " + e.getMessage());
            out.print("{\"success\": false, \"error\": \"Server error processing wizard status.\"}");
        } finally {
            out.flush();
        }
    }
}
