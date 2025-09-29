package timeclock.punches;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/CheckOpenPunchServlet")
public class CheckOpenPunchServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(CheckOpenPunchServlet.class.getName());
    private final Gson gson = new Gson();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Map<String, Object> jsonResponse = new HashMap<>();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.put("error", "Session expired.");
            response.getWriter().write(gson.toJson(jsonResponse));
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        Integer eid = (Integer) session.getAttribute("EID");

        if (tenantId == null || eid == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            jsonResponse.put("error", "Invalid session data.");
            response.getWriter().write(gson.toJson(jsonResponse));
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            boolean hasOpenPunch = hasOpenPunch(con, tenantId, eid);
            jsonResponse.put("hasOpenPunch", hasOpenPunch);
            response.getWriter().write(gson.toJson(jsonResponse));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error checking for open punch for EID: " + eid, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("error", "Database error during check.");
            response.getWriter().write(gson.toJson(jsonResponse));
        }
    }

    private boolean hasOpenPunch(Connection con, int tenantId, int eid) throws SQLException {
        String sql = "SELECT IN_1 FROM punches WHERE TenantID = ? AND EID = ? AND OUT_1 IS NULL AND PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Regular') ORDER BY IN_1 DESC LIMIT 1";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setInt(2, eid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    java.sql.Timestamp lastInTime = rs.getTimestamp("IN_1");
                    if (lastInTime != null) {
                        long hoursSinceLastPunch = (System.currentTimeMillis() - lastInTime.getTime()) / (1000 * 60 * 60);
                        // Only consider it an open punch if it's within 20 hours
                        return hoursSinceLastPunch <= 20;
                    }
                }
                return false;
            }
        }
    }
}