package timeclock.auth;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mindrot.jbcrypt.BCrypt;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/api/mobile/change-pin")
public class ChangePinServlet extends HttpServlet {


	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(ChangePinServlet.class.getName());
    private final Gson gson = new Gson();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Connection conn = null;

        try {
            JsonObject jsonRequest = gson.fromJson(request.getReader(), JsonObject.class);
            int eid = jsonRequest.get("eid").getAsInt();
            int tenantId = jsonRequest.get("tenantId").getAsInt();
            String newPin = jsonRequest.get("newPin").getAsString();

            if (newPin == null || !newPin.matches("\\d{4}")) {
                sendErrorResponse(response, "PIN must be 4 numerical digits.", 400);
                return;
            }
            if ("1234".equals(newPin)) {
                sendErrorResponse(response, "New PIN cannot be the default PIN.", 400);
                return;
            }

            conn = DatabaseConnection.getConnection();
            
            // --- NEW: Check if the new PIN is the same as the old one ---
            String selectSql = "SELECT PasswordHash FROM employee_data WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setInt(1, eid);
                psSelect.setInt(2, tenantId);
                try (ResultSet rs = psSelect.executeQuery()) {
                    if (rs.next()) {
                        String currentHash = rs.getString("PasswordHash");
                        if (currentHash != null && BCrypt.checkpw(newPin, currentHash)) {
                            sendErrorResponse(response, "New PIN cannot be the same as your old PIN.", 400);
                            return;
                        }
                    } else {
                         throw new SQLException("User not found during PIN change validation.");
                    }
                }
            }
            // --- END NEW ---

            String hashedPin = BCrypt.hashpw(newPin, BCrypt.gensalt());
            String updateSql = "UPDATE employee_data SET PasswordHash = ?, RequiresPasswordChange = 0 WHERE EID = ? AND TenantID = ?";
            
            try (PreparedStatement psUpdate = conn.prepareStatement(updateSql)) {
                psUpdate.setString(1, hashedPin);
                psUpdate.setInt(2, eid);
                psUpdate.setInt(3, tenantId);

                int rowsAffected = psUpdate.executeUpdate();
                if (rowsAffected > 0) {
                    sendJsonResponse(response, Map.of("success", true, "message", "PIN updated successfully."));
                } else {
                    throw new SQLException("PIN update failed, user not found.");
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "An error occurred during PIN change.", e);
            sendErrorResponse(response, "An unexpected server error occurred.", 500);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Connection close failed", ex); }
        }
    }
    
    // sendJsonResponse and sendErrorResponse methods remain the same...
    private void sendJsonResponse(HttpServletResponse response, Map<String, Object> data) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.write(gson.toJson(data));
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) throws IOException {
        response.setStatus(statusCode);
        sendJsonResponse(response, Map.of("success", false, "message", message));
    }
}