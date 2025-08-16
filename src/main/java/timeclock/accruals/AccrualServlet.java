package timeclock.accruals;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder; 
import java.nio.charset.StandardCharsets; 
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/AccrualServlet")
public class AccrualServlet extends HttpServlet {
    private static final long serialVersionUID = 2L; // Version updated
    private static final Logger logger = Logger.getLogger(AccrualServlet.class.getName());

    private boolean isValid(String s) {
        return s != null && !s.trim().isEmpty();
    }
     private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Integer tenantId = null;

        if (session != null) {
            Object tenantIdObj = session.getAttribute("TenantID");
            if (tenantIdObj instanceof Integer) {
                tenantId = (Integer) tenantIdObj;
            }
        }

        if (tenantId == null || tenantId <= 0) {
            sendJsonResponse(response, false, "Session expired or invalid tenant. Please log in.", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String action = request.getParameter("action");
        if (!"adjustAccruedBalanceAction".equals(action)) {
             sendJsonResponse(response, false, "Invalid action specified: " + escapeHtml(action), HttpServletResponse.SC_BAD_REQUEST);
             return;
        }

        try {
            handleAdjustAccruedHours(request, response, tenantId);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unhandled exception in handleAdjustAccruedHours for TenantID: " + tenantId, e);
            sendJsonResponse(response, false, "An unexpected critical server error occurred: " + escapeHtml(e.getMessage()), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void handleAdjustAccruedHours(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        String hoursStr = request.getParameter("accrualHours");
        String accrualColumnName = request.getParameter("accrualTypeColumn");
        boolean isGlobal = "true".equalsIgnoreCase(request.getParameter("isGlobalAdd"));
        String targetEidStr = request.getParameter("targetEmployeeId");
        String adjustmentOperation = request.getParameter("adjustmentOperation");

        Connection con = null;
        try {
            if (!isValid(hoursStr)) throw new IllegalArgumentException("Missing Hours value.");
            if (!isValid(accrualColumnName)) throw new IllegalArgumentException("Missing Accrual Type.");
            if (!isValid(adjustmentOperation)) throw new IllegalArgumentException("Missing adjustment operation type.");

            if (!("VACATION_HOURS".equals(accrualColumnName) || "SICK_HOURS".equals(accrualColumnName) || "PERSONAL_HOURS".equals(accrualColumnName))) {
                throw new IllegalArgumentException("Invalid Accrual Type specified: " + escapeHtml(accrualColumnName));
            }

            double hoursValue;
            try {
                hoursValue = Double.parseDouble(hoursStr.trim());
                if (("add".equals(adjustmentOperation) || "subtract".equals(adjustmentOperation)) && hoursValue <= 0) {
                    throw new NumberFormatException("Hours to add/subtract must be a positive value.");
                } else if ("set".equals(adjustmentOperation) && hoursValue < 0) {
                    throw new NumberFormatException("Balance to set cannot be negative.");
                } else if (!Arrays.asList("add", "subtract", "set").contains(adjustmentOperation)) {
                    throw new IllegalArgumentException("Invalid adjustment operation: " + escapeHtml(adjustmentOperation));
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid value for Hours: '" + escapeHtml(hoursStr) + "'. " + e.getMessage());
            }

            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);

            List<Integer> targetEids = new ArrayList<>();
            if (isGlobal) {
                String sqlGetActive = "SELECT EID FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE AND TenantID = ?";
                try (PreparedStatement ps = con.prepareStatement(sqlGetActive)) {
                    ps.setInt(1, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) targetEids.add(rs.getInt("EID"));
                    }
                }
                if (targetEids.isEmpty()) {
                    sendJsonResponse(response, true, "No active employees found. No hours adjusted.", HttpServletResponse.SC_OK);
                    return;
                }
            } else {
                if (!isValid(targetEidStr)) throw new IllegalArgumentException("Employee must be selected.");
                targetEids.add(Integer.parseInt(targetEidStr.trim()));
            }

            String updateSql;
            if ("set".equals(adjustmentOperation)) {
                updateSql = "UPDATE EMPLOYEE_DATA SET " + accrualColumnName + " = ? WHERE EID = ? AND TenantID = ?";
            } else {
                String sqlOperator = "subtract".equals(adjustmentOperation) ? "-" : "+";
                updateSql = "UPDATE EMPLOYEE_DATA SET " + accrualColumnName + " = COALESCE(" + accrualColumnName + ", 0) " + sqlOperator + " ? WHERE EID = ? AND TenantID = ?";
            }
            
            int successfulUpdates = 0;
            try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                for (int eid : targetEids) {
                    psUpdate.setDouble(1, hoursValue);
                    psUpdate.setInt(2, eid);
                    psUpdate.setInt(3, tenantId);
                    successfulUpdates += psUpdate.executeUpdate();
                }
            }

            if (successfulUpdates > 0) {
                con.commit();
                String successMessage = String.format(Locale.US, "Successfully applied adjustment to %d employee(s).", successfulUpdates);
                sendJsonResponse(response, true, successMessage, HttpServletResponse.SC_OK);
            } else {
                throw new SQLException("The adjustment did not affect any employee records. Please check the selection.");
            }

        } catch (IllegalArgumentException | SecurityException e) {
            rollback(con);
            sendJsonResponse(response, false, "Invalid input: " + escapeHtml(e.getMessage()), HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e) {
            rollback(con);
            sendJsonResponse(response, false, "Database error: " + escapeHtml(e.getMessage()), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (con != null) try { con.close(); } catch (SQLException e) { logger.log(Level.WARNING, "Failed to close connection.", e); }
        }
    }

    private void sendJsonResponse(HttpServletResponse response, boolean success, String message, int statusCode) throws IOException {
        if (response.isCommitted()) { return; }
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String key = success ? "message" : "error";
        String jsonString = String.format("{\"success\": %b, \"%s\": \"%s\"}", success, key, message.replace("\"", "\\\""));
        try (PrintWriter out = response.getWriter()) {
            out.print(jsonString);
        }
    }

    private void rollback(Connection con) {
        if (con != null) {
            try {
                con.rollback();
            } catch (SQLException ex) {
                logger.log(Level.SEVERE, "Transaction rollback failed!", ex);
            }
        }
    }
}
