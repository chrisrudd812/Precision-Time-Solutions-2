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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@WebServlet("/api/mobile/login")
public class MobileLoginServlet extends HttpServlet {

    private static final Logger logger = Logger.getLogger(MobileLoginServlet.class.getName());
    private final Gson gson = new Gson();

    private record TenantInfo(int id, String timeZone, int maxUsers) {}

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Connection conn = null;

        try {
            JsonObject jsonRequest = gson.fromJson(request.getReader(), JsonObject.class);
            String companyIdentifier = jsonRequest.get("companyIdentifier").getAsString().trim();
            String email = jsonRequest.get("email").getAsString().trim();
            String password = jsonRequest.get("password").getAsString();

            conn = DatabaseConnection.getConnection();
            
            TenantInfo tenantInfo = findTenantInfo(conn, companyIdentifier);
            if (tenantInfo == null) {
                sendJsonResponse(response, Map.of("success", false, "message", "Invalid Company ID"));
                return;
            }
            
            Map<String, Object> validationResult = validateUser(conn, tenantInfo, email, password);
            
            if ((boolean) validationResult.get("success")) {
                // --- UPDATED: Always check for messages on a successful login/lookup ---
                int eid = (int) ((Map<String, Object>) validationResult.get("user")).get("eid");
                List<Map<String, String>> loginMessages = checkForAndClearLoginMessages(conn, eid);
                validationResult.put("loginMessages", loginMessages);
                sendJsonResponse(response, validationResult);
            } else {
                 sendJsonResponse(response, validationResult);
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, "An unexpected error occurred during login.", e);
            sendErrorResponse(response, "An unexpected server error occurred.", 500);
        } finally {
            if (conn != null) try { conn.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Connection close failed", ex); }
        }
    }

    private TenantInfo findTenantInfo(Connection conn, String companyIdentifier) throws SQLException {
        String tenantSql = "SELECT TenantID, DefaultTimeZone, MaxUsers FROM tenants WHERE CompanyIdentifier = ?";
        try (PreparedStatement ps = conn.prepareStatement(tenantSql)) {
            ps.setString(1, companyIdentifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new TenantInfo(rs.getInt("TenantID"), rs.getString("DefaultTimeZone"), rs.getInt("MaxUsers"));
                }
                return null;
            }
        }
    }
    
    private Map<String, Object> validateUser(Connection conn, TenantInfo tenantInfo, String email, String password) throws SQLException {
        String userSql = "SELECT EID, PasswordHash, PERMISSIONS, FIRST_NAME, LAST_NAME, ACTIVE, DEPT, SUPERVISOR, SCHEDULE, RequiresPasswordChange " +
                         "FROM employee_data WHERE LOWER(EMAIL) = LOWER(?) AND TenantID = ?";
        
        try (PreparedStatement ps = conn.prepareStatement(userSql)) {
            ps.setString(1, email.toLowerCase());
            ps.setInt(2, tenantInfo.id());
            
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean("ACTIVE")) {
                    return Map.of("success", false, "message", "Invalid credentials");
                }

                boolean requiresChange = rs.getBoolean("RequiresPasswordChange");
                
                if (requiresChange) {
                     return buildSuccessResponse(rs, tenantInfo, true);
                }

                String passwordHash = rs.getString("PasswordHash");
                if (passwordHash != null && BCrypt.checkpw(password, passwordHash)) {
                    return buildSuccessResponse(rs, tenantInfo, false);
                }
            }
        }
        return Map.of("success", false, "message", "Invalid credentials");
    }

    private Map<String, Object> buildSuccessResponse(ResultSet rs, TenantInfo tenantInfo, boolean requiresChange) throws SQLException {
        Map<String, Object> userData = new HashMap<>();
        userData.put("tenantId", tenantInfo.id());
        userData.put("eid", rs.getInt("EID"));
        userData.put("firstName", rs.getString("FIRST_NAME"));
        userData.put("lastName", rs.getString("LAST_NAME"));
        userData.put("permissions", rs.getString("PERMISSIONS"));
        userData.put("timeZoneId", tenantInfo.timeZone());
        userData.put("department", rs.getString("DEPT"));
        userData.put("supervisor", rs.getString("SUPERVISOR"));
        userData.put("schedule", rs.getString("SCHEDULE"));
        userData.put("requiresPasswordChange", requiresChange);
        userData.put("maxUsers", tenantInfo.maxUsers());
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("user", userData);
        return response;
    }

    private List<Map<String, String>> checkForAndClearLoginMessages(Connection conn, int eid) throws SQLException {
        List<Map<String, String>> messages = new ArrayList<>();
        String selectSql = "SELECT MessageID, Subject, Body FROM login_messages WHERE RecipientEID = ?";
        try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
            psSelect.setInt(1, eid);
            try (ResultSet rs = psSelect.executeQuery()) {
                if (rs.next()) {
                    Map<String, String> message = new HashMap<>();
                    message.put("subject", rs.getString("Subject"));
                    message.put("body", rs.getString("Body"));
                    messages.add(message);
                    int messageId = rs.getInt("MessageID");

                    String deleteSql = "DELETE FROM login_messages WHERE MessageID = ?";
                    try (PreparedStatement psDelete = conn.prepareStatement(deleteSql)) {
                        psDelete.setInt(1, messageId);
                        psDelete.executeUpdate();
                    }
                }
            }
        }
        return messages;
    }

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