package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection; // Your existing DB connection utility

@WebServlet("/api/mobile/settings")
public class MobileSettingsServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(MobileSettingsServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        logger.info("[MobileSettingsServlet] GET request received.");

        String tenantIdParam = request.getParameter("tenantId");
        logger.info("[MobileSettingsServlet] tenantId param: " + tenantIdParam);

        if (tenantIdParam == null) {
            logger.warning("[MobileSettingsServlet] tenantId is missing.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Error: tenantId parameter is required.");
            return;
        }

        int tenantId;
        try {
            tenantId = Integer.parseInt(tenantIdParam);
        } catch (NumberFormatException e) {
            logger.warning("[MobileSettingsServlet] Invalid tenantId: " + tenantIdParam);
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Error: tenantId must be a number.");
            return;
        }

        // --- Query the settings table ---
        String sql = "SELECT setting_key, setting_value FROM settings WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, tenantId);
            logger.info("[MobileSettingsServlet] Executing query: " + ps.toString());

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder();
                json.append("{");

                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    String key = rs.getString("setting_key");
                    String value = rs.getString("setting_value");
                    json.append("\"").append(key).append("\":\"").append(value).append("\"");
                    first = false;
                }

                json.append("}");
                logger.info("[MobileSettingsServlet] Query result JSON: " + json.toString());

                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                PrintWriter out = response.getWriter();
                out.write(json.toString());
                out.flush();
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[MobileSettingsServlet] SQLException retrieving settings for TenantID: " + tenantId, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error: Database error. Check server logs.");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[MobileSettingsServlet] Unexpected exception", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error: Unexpected server error. Check logs.");
        }
    }
}
