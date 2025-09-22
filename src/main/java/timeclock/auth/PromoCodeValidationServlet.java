package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;

@WebServlet("/PromoCodeValidationServlet")
public class PromoCodeValidationServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(PromoCodeValidationServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String promoCode = request.getParameter("promoCode");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        JSONObject jsonResponse = new JSONObject();

        if (promoCode == null || promoCode.trim().isEmpty()) {
            jsonResponse.put("valid", false).put("error", "Promo code cannot be empty.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write(jsonResponse.toString());
            return;
        }

        String sql = "SELECT DiscountType, ExpirationDate, MaxUses, CurrentUses, IsEnabled FROM promo_codes WHERE Code = ? AND IsEnabled = TRUE";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, promoCode.trim());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Code exists and is enabled, now check other conditions
                LocalDate expirationDate = rs.getDate("ExpirationDate") != null ? rs.getDate("ExpirationDate").toLocalDate() : null;
                Integer maxUses = (Integer) rs.getObject("MaxUses");
                int currentUses = rs.getInt("CurrentUses");
                String discountType = rs.getString("DiscountType");

                if (expirationDate != null && expirationDate.isBefore(LocalDate.now())) {
                    jsonResponse.put("valid", false).put("error", "This promo code has expired.");
                } else if (maxUses != null && currentUses >= maxUses) {
                    jsonResponse.put("valid", false).put("error", "This promo code has reached its usage limit.");
                } else {
                    // The code is valid!
                    jsonResponse.put("valid", true);
                    jsonResponse.put("type", discountType); // e.g., "LIFETIME", "PERCENT"
                }
            } else {
                // Code does not exist or is not enabled
                jsonResponse.put("valid", false).put("error", "Invalid or unrecognized promo code.");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error validating promo code", e);
            jsonResponse.put("valid", false).put("error", "Server error while validating code. Please try again.");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }

        try (PrintWriter out = response.getWriter()) {
            out.print(jsonResponse.toString());
        }
    }
}