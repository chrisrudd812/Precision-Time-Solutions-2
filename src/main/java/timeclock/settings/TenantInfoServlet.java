package timeclock.settings;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import timeclock.db.DatabaseConnection;

@WebServlet("/api/mobile/tenant-info")
public class TenantInfoServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(TenantInfoServlet.class.getName());
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        String tenantIdParam = request.getParameter("tenantId");
        
        if (tenantIdParam == null || tenantIdParam.trim().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("message", "Missing tenantId parameter");
            response.getWriter().write(gson.toJson(errorResponse));
            return;
        }
        
        try {
            int tenantId = Integer.parseInt(tenantIdParam);
            JsonObject tenantInfo = getTenantInfo(tenantId);
            
            if (tenantInfo != null) {
                tenantInfo.addProperty("success", true);
                response.getWriter().write(gson.toJson(tenantInfo));
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("success", false);
                errorResponse.addProperty("message", "Tenant not found");
                response.getWriter().write(gson.toJson(errorResponse));
            }
            
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            JsonObject errorResponse = new JsonObject();
            errorResponse.addProperty("success", false);
            errorResponse.addProperty("message", "Invalid tenantId format");
            response.getWriter().write(gson.toJson(errorResponse));
        }
    }
    
    private JsonObject getTenantInfo(int tenantId) {
        String sql = "SELECT MaxUsers FROM tenants WHERE TenantID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, tenantId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    JsonObject tenantInfo = new JsonObject();
                    tenantInfo.addProperty("tenantId", tenantId);
                    tenantInfo.addProperty("maxUsers", rs.getInt("MaxUsers"));
                    return tenantInfo;
                }
            }
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving tenant info for TenantID: " + tenantId, e);
        }
        
        return null;
    }
}