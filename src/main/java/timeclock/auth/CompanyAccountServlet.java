package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/CompanyAccountServlet")
public class CompanyAccountServlet extends HttpServlet {
    private static final long serialVersionUID = 6L; // Version update
    private static final Logger logger = Logger.getLogger(CompanyAccountServlet.class.getName());

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("TenantID") != null) {
            return (Integer) session.getAttribute("TenantID");
        }
        return null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();
        Integer tenantId = getTenantId(request);

        if (tenantId == null) {
            jsonResponse.put("success", false).put("error", "Session expired.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(jsonResponse.toString());
            return;
        }

        String action = request.getParameter("action");
        if ("getBillingDetails".equals(action)) {
            getBillingDetails(request, tenantId, jsonResponse);
        } else if ("getCompanyDetails".equals(action)) {
            getCompanyDetails(request, tenantId, jsonResponse);
        } else {
            jsonResponse.put("success", false).put("error", "Invalid GET action.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.print(jsonResponse.toString());
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();
        Integer tenantId = getTenantId(request);

        if (tenantId == null) {
            jsonResponse.put("success", false).put("error", "Session expired.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            out.print(jsonResponse.toString());
            return;
        }

        String action = request.getParameter("action");
        if ("verifyAdminPassword".equals(action)) {
            verifyCompanyAdminPassword(request, tenantId, jsonResponse);
        } else if ("updateCompanyDetails".equals(action)) {
            updateCompanyDetails(request, tenantId, jsonResponse);
        }
        else {
            jsonResponse.put("success", false).put("error", "Invalid POST action.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }
        out.print(jsonResponse.toString());
    }

    private void getBillingDetails(HttpServletRequest request, int tenantId, JSONObject jsonResponse) {
        JSONObject details = new JSONObject();
        details.put("cardholderName", "");
        details.put("cardLastFour", "");
        details.put("cardExpiry", "");
        jsonResponse.put("success", true).put("details", details);
    }

    private void getCompanyDetails(HttpServletRequest request, int tenantId, JSONObject jsonResponse) {
        String sql = "SELECT CompanyName, CompanyIdentifier, PhoneNumber, Address, City, State, ZipCode FROM tenants WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    JSONObject details = new JSONObject();
                    details.put("companyName", rs.getString("CompanyName"));
                    details.put("companyIdentifier", rs.getString("CompanyIdentifier"));
                    details.put("companyPhone", rs.getString("PhoneNumber"));
                    details.put("companyAddress", rs.getString("Address"));
                    details.put("companyCity", rs.getString("City"));
                    details.put("companyState", rs.getString("State"));
                    details.put("companyZip", rs.getString("ZipCode"));
                    jsonResponse.put("success", true).put("details", details);
                } else {
                    jsonResponse.put("success", false).put("error", "No company details found.");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error fetching company details for T:" + tenantId, e);
            jsonResponse.put("success", false).put("error", "Database error fetching company details.");
        }
    }
    
    private void updateCompanyDetails(HttpServletRequest request, int tenantId, JSONObject jsonResponse) {
        String companyPhone = request.getParameter("companyPhone");
        String companyAddress = request.getParameter("companyAddress");
        String companyCity = request.getParameter("companyCity");
        String companyState = request.getParameter("companyState");
        String companyZip = request.getParameter("companyZip");

        String sql = "UPDATE tenants SET PhoneNumber = ?, Address = ?, City = ?, State = ?, ZipCode = ? WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, companyPhone);
            pstmt.setString(2, companyAddress);
            pstmt.setString(3, companyCity);
            pstmt.setString(4, companyState);
            pstmt.setString(5, companyZip);
            pstmt.setInt(6, tenantId);

            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                jsonResponse.put("success", true).put("message", "Company details updated successfully.");
            } else {
                jsonResponse.put("success", false).put("error", "Update failed. Company not found or no changes made.");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error updating company details for T:" + tenantId, e);
            jsonResponse.put("success", false).put("error", "Database error occurred during update.");
        }
    }

    private void verifyCompanyAdminPassword(HttpServletRequest request, int tenantId, JSONObject jsonResponse) {
        String password = request.getParameter("password");
        if (password == null || password.trim().isEmpty()) {
            jsonResponse.put("success", false).put("error", "Password is required.");
            return;
        }
        
        String sql = "SELECT AdminPasswordHash FROM tenants WHERE TenantID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String storedHash = rs.getString("AdminPasswordHash");
                    if (storedHash != null && BCrypt.checkpw(password, storedHash)) {
                        jsonResponse.put("success", true);
                    } else {
                        jsonResponse.put("success", false).put("error", "Incorrect password.");
                    }
                } else {
                    jsonResponse.put("success", false).put("error", "Administrator account not found.");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error verifying admin password for T:" + tenantId, e);
            jsonResponse.put("success", false).put("error", "Database error during verification.");
        }
    }
}