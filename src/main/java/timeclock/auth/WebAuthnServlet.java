package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.db.DatabaseConnection;
import timeclock.util.Helpers;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;
import java.util.logging.Logger;

@WebServlet("/WebAuthnServlet")
public class WebAuthnServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(WebAuthnServlet.class.getName());

    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String action = request.getParameter("action");
        
        if ("getChallenge".equals(action)) {
            handleGetChallenge(request, response);
        }
    }

    private void handleGetChallenge(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String email = request.getParameter("email");
        String companyId = request.getParameter("companyId");
        
        if (!Helpers.isStringValid(email) || !Helpers.isStringValid(companyId)) {
            response.setStatus(400);
            return;
        }

        try (Connection conn = DatabaseConnection.getConnection()) {
            // Get tenant ID
            String tenantSql = "SELECT TenantID FROM tenants WHERE CompanyIdentifier = ?";
            PreparedStatement psTenant = conn.prepareStatement(tenantSql);
            psTenant.setString(1, companyId.trim());
            ResultSet rsTenant = psTenant.executeQuery();

            if (!rsTenant.next()) {
                response.setStatus(404);
                return;
            }
            int tenantId = rsTenant.getInt("TenantID");

            // Get user EID
            String userSql = "SELECT EID FROM employee_data WHERE LOWER(EMAIL) = LOWER(?) AND TenantID = ?";
            PreparedStatement psUser = conn.prepareStatement(userSql);
            psUser.setString(1, email.trim().toLowerCase());
            psUser.setInt(2, tenantId);
            ResultSet rsUser = psUser.executeQuery();

            if (!rsUser.next()) {
                response.setStatus(404);
                return;
            }
            int eid = rsUser.getInt("EID");

            // Get user's credentials
            String credSql = "SELECT CredentialIdBase64 FROM webauthn_credentials WHERE EID = ? AND TenantID = ? AND IsEnabled = 1";
            PreparedStatement psCred = conn.prepareStatement(credSql);
            psCred.setInt(1, eid);
            psCred.setInt(2, tenantId);
            ResultSet rsCred = psCred.executeQuery();

            StringBuilder credentialsJson = new StringBuilder();
            credentialsJson.append("{\"challenge\":\"");
            
            // Generate random challenge
            byte[] challenge = new byte[32];
            new java.security.SecureRandom().nextBytes(challenge);
            credentialsJson.append(Base64.getEncoder().encodeToString(challenge));
            
            credentialsJson.append("\",\"allowCredentials\":[");
            
            boolean first = true;
            while (rsCred.next()) {
                if (!first) credentialsJson.append(",");
                credentialsJson.append("{\"id\":\"").append(rsCred.getString("CredentialIdBase64")).append("\"}");
                first = false;
            }
            
            credentialsJson.append("]}");

            response.setContentType("application/json");
            response.getWriter().write(credentialsJson.toString());

        } catch (Exception e) {
            logger.severe("Error generating WebAuthn challenge: " + e.getMessage());
            response.setStatus(500);
        }
    }
}