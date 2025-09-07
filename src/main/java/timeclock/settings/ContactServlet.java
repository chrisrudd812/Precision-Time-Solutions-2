package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;
import timeclock.auth.EmailService;
import timeclock.db.DatabaseConnection;

@WebServlet("/ContactServlet")
@MultipartConfig
public class ContactServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ContactServlet.class.getName());

    private static final String SUPPORT_EMAIL = "support@precisiontimesolutions.com";
    private static final String FEEDBACK_EMAIL = "feedback@precisiontimesolutions.com";

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JSONObject jsonResponse = new JSONObject();
        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("TenantID") == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.put("success", false).put("message", "Your session has expired. Please log in again.");
            response.getWriter().write(jsonResponse.toString());
            return;
        }
        
        // Get the logged-in user's email from the session for the auto-reply
        String loggedInUserEmail = (String) session.getAttribute("Email");
        if (loggedInUserEmail == null) {
            loggedInUserEmail = "N/A"; // Fallback, though email should always be in the session
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        String contactSubject = request.getParameter("contactSubject");
        String contactMessage = request.getParameter("contactMessage");
        String requestType = request.getParameter("requestType");
        Part filePart = request.getPart("fileAttachment"); // Get the uploaded file part

        String recipientEmail;
        if ("feedback".equals(requestType)) {
            recipientEmail = FEEDBACK_EMAIL;
        } else {
            recipientEmail = SUPPORT_EMAIL;
        }

        String companyName = "N/A";
        
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT CompanyName FROM tenants WHERE TenantID = ?")) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    companyName = rs.getString("CompanyName");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not retrieve tenant info for TenantID: " + tenantId, e);
        }

        String finalSubject = "New " + requestType + " from " + companyName + ": " + contactSubject;
        String emailBody = "New message received via contact form.\n\n" +
                           "--------------------------------------------------\n" +
                           "Type: " + requestType + "\n" +
                           "Company: " + companyName + " (Tenant ID: " + tenantId + ")\n" +
                           "User's Email: " + loggedInUserEmail + "\n" +
                           "Subject: " + contactSubject + "\n" +
                           "--------------------------------------------------\n\n" +
                           "Message:\n" + contactMessage;

        boolean isSent = EmailService.sendEmail(recipientEmail, finalSubject, emailBody, filePart);

        if (isSent) {
            jsonResponse.put("success", true).put("message", "Your message has been sent successfully!");

            try {
                String userSubject = "Confirmation: We've received your request";
                StringBuilder userBody = new StringBuilder();
                userBody.append("Hello,\n\n");
                userBody.append("Thank you for contacting us. This is an automated confirmation that we have received your message. A member of our team will get back to you within 24-48 hours.\n\n");
                userBody.append("For your records, here is a copy of your message:\n\n");
                userBody.append("--------------------------------------------------\n");
                userBody.append("Subject: ").append(contactSubject).append("\n");
                userBody.append("Message: ").append(contactMessage).append("\n");
                userBody.append("--------------------------------------------------\n\n");
                userBody.append("Sincerely,\nThe Precision Time Solutions Team");

                // Send confirmation to the currently logged-in user
                EmailService.sendEmail(loggedInUserEmail, userSubject, userBody.toString());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Failed to send confirmation email to user: " + loggedInUserEmail, e);
            }

        } else {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("success", false).put("message", "Sorry, there was a server error sending your message.");
        }

        response.getWriter().write(jsonResponse.toString());
    }
}