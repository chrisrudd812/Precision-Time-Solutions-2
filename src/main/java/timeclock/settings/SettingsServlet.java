package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.Configuration; 

/**
 * Servlet to handle saving settings from the settings page via AJAX.
 * Sends plain text response ("OK" or "Error: ...").
 */
@WebServlet("/saveSetting")
public class SettingsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SettingsServlet.class.getName());
    // *** REMOVED: private static final Gson gson = new Gson(); ***

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String key = request.getParameter("settingKey");
        String value = request.getParameter("settingValue");

        logger.info("Received setting save request: Key=" + key + ", Value=" + value);

        response.setContentType("text/plain"); // Set content type to plain text
        response.setCharacterEncoding("UTF-8");

        // Basic validation
        if (key == null || key.trim().isEmpty() || value == null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); // 400 Bad Request
            response.getWriter().write("Error: Missing setting key or value."); // Send plain text error
            logger.warning("Save setting failed: Missing key or value.");
            return;
        }

        try {
            // Call your configuration class to save the property
            Configuration.saveProperty(key.trim(), value);

            // Send plain text success response
            response.getWriter().write("OK");
            logger.info("Setting saved successfully: Key=" + key);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error saving setting: Key=" + key, e);
            // Send plain text error response
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); // 500 Internal Server Error
            response.getWriter().write("Error: Failed to save setting due to server error.");
        }
    }

}