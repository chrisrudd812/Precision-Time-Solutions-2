package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mindrot.jbcrypt.BCrypt;

@WebServlet("/SetInitialPinServlet")
public class SetInitialPinServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SetInitialPinServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        HttpSession session = request.getSession(false);
        String eidStr = request.getParameter("eid");
        String newPin = request.getParameter("newPin");
        String confirmNewPin = request.getParameter("confirmNewPin");
        String wizardStepVerify = request.getParameter("wizardStepVerify");

        if (session == null || !"initialPinSetRequired".equals(session.getAttribute("wizardStep")) || eidStr == null) {
            response.sendRedirect("signup_company_info.jsp?error=" + URLEncoder.encode("Invalid setup step or session expired.", StandardCharsets.UTF_8));
            return;
        }

        Integer wizardAdminEid = (Integer) session.getAttribute("wizardAdminEid");
        int eid = 0;
        try {
            eid = Integer.parseInt(eidStr);
        } catch (NumberFormatException e) {
            session.setAttribute("errorMessage_initialPin", "Invalid employee ID format.");
            response.sendRedirect("set_initial_pin.jsp");
            return;
        }

        if (wizardAdminEid == null || wizardAdminEid != eid || !"initialPinSetRequired".equals(wizardStepVerify)) {
            session.setAttribute("errorMessage_initialPin", "Security validation failed. Please try again.");
            response.sendRedirect("set_initial_pin.jsp");
            return;
        }

        if (newPin == null || !newPin.matches("\\d{4}") || !newPin.equals(confirmNewPin)) {
            session.setAttribute("errorMessage_initialPin", "PINs must be 4 digits and must match.");
            response.sendRedirect("set_initial_pin.jsp");
            return;
        }

        String hashedPin = BCrypt.hashpw(newPin, BCrypt.gensalt(12));
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            String sql = "UPDATE employee_data SET PasswordHash = ?, RequiresPasswordChange = FALSE WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement pstmt = con.prepareStatement(sql)) {
                pstmt.setString(1, hashedPin);
                pstmt.setInt(2, eid);
                pstmt.setInt(3, (Integer) session.getAttribute("TenantID"));
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected > 0) {
                    logger.info("Successfully set initial PIN for EID: " + eid);
                    
                    // [FIX] Set the correct 4-hour session timeout for the administrator
                    session.setMaxInactiveInterval(4 * 60 * 60); // 4 hours in seconds
                    logger.info("Administrator session timeout set to 4 hours for EID: " + eid);

                    session.setAttribute("wizardStep", "settings_setup");
                    response.sendRedirect("settings.jsp?setup_wizard=true&step=settings_setup");
                    return;
                } else {
                    throw new SQLException("Failed to update PIN, no rows affected.");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error setting initial PIN for EID: " + eid, e);
            session.setAttribute("errorMessage_initialPin", "A database error occurred.");
            response.sendRedirect("set_initial_pin.jsp");
        } finally {
            if (con != null) {
                try { con.close(); } catch (SQLException e) { /* ignore */ }
            }
        }
    }
}