package timeclock.punches;

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

@WebServlet("/QuickPunchCheckServlet")
public class QuickPunchCheckServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

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
                        return hoursSinceLastPunch <= 20;
                    }
                }
                return false;
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String punchAction = request.getParameter("punchAction");
        String eidStr = request.getParameter("punchEID");
        
        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":\"Session expired.\"}");
            return;
        }

        Integer tenantId = (Integer) session.getAttribute("TenantID");
        Integer sessionEid = (Integer) session.getAttribute("EID");
        int punchEID = (eidStr != null && !eidStr.isEmpty()) ? Integer.parseInt(eidStr) : sessionEid;

        try (Connection con = DatabaseConnection.getConnection()) {
            if ("IN".equals(punchAction)) {
                if (hasOpenPunch(con, tenantId, punchEID)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"You are already clocked in. Please clock out before clocking in again.\"}");
                    return;
                }
            } else if ("OUT".equals(punchAction)) {
                if (!hasOpenPunch(con, tenantId, punchEID)) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.getWriter().write("{\"error\":\"No open work punch found to clock out against. You are already clocked out.\"}");
                    return;
                }
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.getWriter().write("{\"error\":\"Invalid punch action specified.\"}");
                return;
            }
            
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\":true}");
            
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }
}