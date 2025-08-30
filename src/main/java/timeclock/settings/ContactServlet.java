package timeclock.settings;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;
import timeclock.db.DatabaseConnection;

@WebServlet("/ContactServlet")
public class ContactServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ContactServlet.class.getName());

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

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        String contactSubject = request.getParameter("contactSubject");
        String contactMessage = request.getParameter("contactMessage");

        if (contactSubject == null || contactSubject.trim().isEmpty() ||
            contactMessage == null || contactMessage.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("success", false).put("message", "Subject and Message are required.");
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        String companyName = "N/A";
        String adminEmail = "N/A";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT CompanyName, AdminEmail FROM tenants WHERE TenantID = ?")) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    companyName = rs.getString("CompanyName");
                    adminEmail = rs.getString("AdminEmail");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not retrieve tenant info for TenantID: " + tenantId, e);
        }

        try {
            // [FIX] Hardcoded SMTP settings.
            // IMPORTANT: Replace the password placeholder with your 16-digit Google App Password.
            final String smtpHost = "smtp.gmail.com";
            final String smtpPort = "587";
            final String username = "chrisrudd812@gmail.com";
            final String password = "qflv azzg npjx qnyt"; // <-- REPLACE THIS
            final String mailToAddress = "chrisrudd812@gmail.com";
            
            Properties props = new Properties();
            props.put("mail.smtp.host", smtpHost);
            props.put("mail.smtp.port", smtpPort);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");

            Session mailSession = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, password);
                }
            });

            Message mimeMessage = new MimeMessage(mailSession);
            mimeMessage.setFrom(new InternetAddress(username));
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(mailToAddress));
            mimeMessage.setReplyTo(new InternetAddress[]{new InternetAddress(adminEmail)});
            mimeMessage.setSubject("Support Request from " + companyName + ": " + contactSubject);
            
            String emailBody = "Support request from a logged-in user.\n\n" +
                               "--------------------------------------------------\n" +
                               "Company: " + companyName + " (Tenant ID: " + tenantId + ")\n" +
                               "Admin Email: " + adminEmail + "\n" +
                               "Subject: " + contactSubject + "\n" +
                               "--------------------------------------------------\n\n" +
                               "Message:\n" + contactMessage;
            
            mimeMessage.setText(emailBody);
            Transport.send(mimeMessage);
            
            jsonResponse.put("success", true).put("message", "Your support request has been sent successfully!");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to send contact form email for TenantID: " + tenantId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("success", false).put("message", "Sorry, there was a server error sending your message.");
        }

        response.getWriter().write(jsonResponse.toString());
    }
}