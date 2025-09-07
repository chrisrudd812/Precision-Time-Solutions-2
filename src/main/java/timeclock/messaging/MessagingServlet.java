package timeclock.messaging;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.auth.EmailService;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@WebServlet("/MessagingServlet")
public class MessagingServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(MessagingServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("TenantID") == null) {
            writeJsonResponse(response, false, "Session expired. Please log in.", null);
            return;
        }
        int tenantId = (Integer) session.getAttribute("TenantID");
        String action = request.getParameter("action");

        if ("getOptions".equals(action)) {
            String type = request.getParameter("type");
            List<SelectOption> options = getOptionsForType(tenantId, type);
            writeJsonResponse(response, true, "Options loaded", options);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("TenantID") == null) {
            writeJsonResponse(response, false, "Session expired. Please log in.", null);
            return;
        }
        int tenantId = (Integer) session.getAttribute("TenantID");
        String action = request.getParameter("action");

        if ("sendMessage".equals(action)) {
            handleSendMessage(tenantId, request, response);
        }
    }

    private void handleSendMessage(int tenantId, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String deliveryType = request.getParameter("messageDeliveryType");
        String recipientType = request.getParameter("recipientType");
        String recipientTarget = request.getParameter("recipientTarget");
        String subject = request.getParameter("messageSubject");
        String body = request.getParameter("messageBody");
        Integer senderEid = (Integer) request.getSession(false).getAttribute("EID");

        try {
            if ("login".equals(deliveryType)) {
                List<Integer> recipientEIDs = MessagingDataService.getRecipientEIDs(tenantId, recipientType, recipientTarget);
                if (recipientEIDs.isEmpty()) {
                    writeJsonResponse(response, false, "No recipients found for the selected criteria.", null);
                    return;
                }
                saveLoginMessages(tenantId, senderEid, recipientEIDs, subject, body);
                String successMessage = "Login message has been scheduled for " + recipientEIDs.size() + " recipient(s).";
                writeJsonResponse(response, true, successMessage, null);
            } else {
                List<String> recipientEmails = getRecipientEmails(tenantId, recipientType, recipientTarget);
                if (recipientEmails.isEmpty()) {
                    writeJsonResponse(response, false, "No recipients found with a valid email address for the selected criteria.", null);
                    return;
                }
                EmailService.send(recipientEmails, subject, body);
                String successMessage = "Email successfully sent to " + recipientEmails.size() + " recipient(s).";
                writeJsonResponse(response, true, successMessage, null);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in handleSendMessage for TenantID " + tenantId, e);
            writeJsonResponse(response, false, "An unexpected error occurred: " + e.getMessage(), null);
        }
    }
    
    private void saveLoginMessages(int tenantId, Integer senderEid, List<Integer> recipientEIDs, String subject, String body) throws SQLException {
        String sql = "INSERT INTO login_messages (TenantID, SenderEID, RecipientEID, Subject, Body) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (Integer recipientEid : recipientEIDs) {
                ps.setInt(1, tenantId);
                if (senderEid != null) {
                    ps.setInt(2, senderEid);
                } else {
                    ps.setNull(2, java.sql.Types.INTEGER);
                }
                ps.setInt(3, recipientEid);
                ps.setString(4, subject);
                ps.setString(5, body);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private List<SelectOption> getOptionsForType(int tenantId, String type) {
        if (type == null) return new ArrayList<>();
        switch (type) {
            case "department":
                return MessagingDataService.getDepartmentsForTenant(tenantId).stream()
                    .map(dept -> new SelectOption(dept.get("name"), dept.get("name")))
                    .collect(Collectors.toList());
            case "schedule":
                return MessagingDataService.getSchedulesForTenant(tenantId).stream()
                    .map(sched -> new SelectOption(sched.get("name"), sched.get("name")))
                    .collect(Collectors.toList());
            case "supervisor":
                return MessagingDataService.getSupervisorsForTenant(tenantId).stream()
                    .map(sup -> new SelectOption(sup, sup))
                    .collect(Collectors.toList());
            case "individual":
                return MessagingDataService.getActiveEmployeesSimple(tenantId).stream()
                    .map(emp -> new SelectOption(emp.get("EID"), emp.get("name")))
                    .collect(Collectors.toList());
            default:
                return new ArrayList<>();
        }
    }

    private List<String> getRecipientEmails(int tenantId, String type, String target) throws SQLException {
        List<String> emails = new ArrayList<>();
        String sql = "SELECT EMAIL FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE AND EMAIL IS NOT NULL AND EMAIL <> ''";

        if (type == null) return emails;
        
        String additionalClause = "";
        switch (type) {
            case "all":
                break;
            case "department":
                additionalClause = " AND DEPT = ?";
                break;
            case "schedule":
                additionalClause = " AND SCHEDULE = ?";
                break;
            case "supervisor":
                additionalClause = " AND SUPERVISOR = ?";
                break;
            case "individual":
                additionalClause = " AND EID = ?";
                break;
            default:
                return emails;
        }
        sql += additionalClause;

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            if (!"all".equals(type)) {
                ps.setString(2, target);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    emails.add(rs.getString("EMAIL"));
                }
            }
        }
        return emails;
    }

    private void writeJsonResponse(HttpServletResponse response, boolean success, String message, Object data) throws IOException {
        try (PrintWriter out = response.getWriter()) {
            out.print("{\"success\":" + success + ",");
            out.print("\"message\":\"" + (message == null ? "" : message.replace("\"", "\\\"")) + "\",");
            out.print("\"options\":" + objectToJson(data));
            out.print("}");
            out.flush();
        }
    }
    
    private String objectToJson(Object data) {
        if (data == null) return "[]";
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            return "[" + list.stream().map(this::objectToJson).collect(Collectors.joining(",")) + "]";
        }
        if (data instanceof SelectOption) {
            SelectOption option = (SelectOption) data;
            String value = option.getValue().replace("\\", "\\\\").replace("\"", "\\\"");
            String text = option.getText().replace("\\", "\\\\").replace("\"", "\\\"");
            return String.format("{\"value\":\"%s\",\"text\":\"%s\"}", value, text);
        }
        return "[]";
    }

    private static class SelectOption {
        private String value;
        private String text;
        public SelectOption(String value, String text) { this.value = value; this.text = text; }
        public String getValue() { return value; }
        public String getText() { return text; }
    }
}