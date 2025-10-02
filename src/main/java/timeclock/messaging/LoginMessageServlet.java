package timeclock.messaging;

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

@WebServlet("/LoginMessageServlet")
public class LoginMessageServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(LoginMessageServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("EID") == null) {
            writeJsonResponse(response, false, "Session expired", null);
            return;
        }

        int eid = (Integer) session.getAttribute("EID");
        String action = request.getParameter("action");

        if ("getMessages".equals(action)) {
            getLoginMessages(eid, response);
        } else if ("deleteMessage".equals(action)) {
            String messageIdStr = request.getParameter("messageId");
            if (messageIdStr != null) {
                try {
                    int messageId = Integer.parseInt(messageIdStr);
                    deleteLoginMessage(messageId, response);
                } catch (NumberFormatException e) {
                    writeJsonResponse(response, false, "Invalid message ID", null);
                }
            } else {
                writeJsonResponse(response, false, "Message ID required", null);
            }
        }
    }

    private void getLoginMessages(int eid, HttpServletResponse response) throws IOException {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"success\":true,\"messages\":[");
        
        String sql = "SELECT MessageID, Subject, Body FROM login_messages WHERE RecipientEID = ? ORDER BY CreatedAt ASC";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, eid);
            try (ResultSet rs = ps.executeQuery()) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) jsonBuilder.append(",");
                    jsonBuilder.append("{")
                        .append("\"messageId\":").append(rs.getInt("MessageID")).append(",")
                        .append("\"subject\":\"").append(escapeJson(rs.getString("Subject"))).append("\",")
                        .append("\"body\":\"").append(escapeJson(rs.getString("Body"))).append("\"")
                        .append("}");
                    first = false;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving login messages for EID " + eid, e);
            writeJsonResponse(response, false, "Database error", null);
            return;
        }
        
        jsonBuilder.append("]}");
        response.getWriter().write(jsonBuilder.toString());
    }

    private void deleteLoginMessage(int messageId, HttpServletResponse response) throws IOException {
        String sql = "DELETE FROM login_messages WHERE MessageID = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setInt(1, messageId);
            int deleted = ps.executeUpdate();
            writeJsonResponse(response, deleted > 0, deleted > 0 ? "Message deleted" : "Message not found", null);
            
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting login message ID " + messageId, e);
            writeJsonResponse(response, false, "Database error", null);
        }
    }

    private void writeJsonResponse(HttpServletResponse response, boolean success, String message, Object data) throws IOException {
        PrintWriter out = response.getWriter();
        if (data != null) {
            out.write("{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\",\"data\":" + data + "}");
        } else {
            out.write("{\"success\":" + success + ",\"message\":\"" + escapeJson(message) + "\"}");
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}