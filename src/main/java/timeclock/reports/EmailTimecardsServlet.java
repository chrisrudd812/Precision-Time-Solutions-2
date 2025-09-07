package timeclock.reports;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.auth.EmailService;
import timeclock.util.PdfGenerator;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/EmailTimecardsServlet")
public class EmailTimecardsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(EmailTimecardsServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Integer tenantId = (session != null) ? (Integer) session.getAttribute("TenantID") : null;
        if (tenantId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"success\": false, \"message\": \"Session expired.\"}");
            return;
        }

        StringBuilder requestBody = new StringBuilder();
        try (var reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                requestBody.append(line);
            }
        }
        
        logger.info("[EmailTimecardsServlet] Received JSON payload: " + requestBody.toString());

        int successCount = 0;
        try {
            Gson gson = new Gson();
            Type payloadType = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> payload = gson.fromJson(requestBody.toString(), payloadType);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> timecards = (List<Map<String, Object>>) payload.get("timecards");
            String payPeriodMessage = (String) payload.get("payPeriodMessage");

            if (timecards == null || payPeriodMessage == null) {
                throw new Exception("Invalid data received from client. Timecards or Pay Period Message is missing.");
            }
            
            logger.info("[EmailTimecardsServlet] Deserialized " + timecards.size() + " timecards to process.");

            for (Map<String, Object> cardData : timecards) {
                String recipientEmail = (String) cardData.get("email");
                
                logger.info("[EmailTimecardsServlet] Processing timecard for: " + cardData.get("employeeName") + ". Email: " + recipientEmail);

                if (recipientEmail == null || recipientEmail.trim().isEmpty()) {
                    logger.warning("Skipping timecard email for " + cardData.get("employeeName") + " due to missing email address.");
                    continue;
                }

                String subject = "Your Timecard for " + payPeriodMessage;
                String body = "Hello " + cardData.get("employeeName") + ",\n\nPlease find your timecard for the recent pay period attached.\n\nThank you.";

                byte[] pdfBytes = PdfGenerator.createTimecardPdf(cardData, payPeriodMessage);

                EmailService.send(recipientEmail, subject, body, pdfBytes, "Timecard.pdf");
                successCount++;
            }

            response.getWriter().write("{\"success\": true, \"message\": \"Successfully sent " + successCount + " timecard emails.\"}");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in EmailTimecardsServlet for TenantID: " + tenantId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"success\": false, \"message\": \"A server error occurred while sending emails.\"}");
        }
    }
}