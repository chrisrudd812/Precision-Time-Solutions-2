package timeclock.employees;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.db.DatabaseConnection;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebServlet("/ReassignServlet")
public class ReassignServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ReassignServlet.class.getName());
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private void loadDropdownData(HttpServletRequest request, Integer tenantId) {
        String departmentsJson = "[]";
        String schedulesJson = "[]";
        String accrualPoliciesJson = "[]";
        String supervisorsJson = "[]";
        String pageLoadError = null;

        if (tenantId == null || tenantId <= 0) {
            logger.warning("loadDropdownData: Invalid tenantId (" + tenantId + "). Not fetching data.");
            pageLoadError = "Invalid session or tenant context. Cannot load reassignment data.";
        } else {
            try {
                // Fetch Departments
                List<Map<String, String>> departments = new ArrayList<>();
                String deptSql = "SELECT NAME FROM departments WHERE TenantID = ? ORDER BY NAME ASC"; // Corrected TenantID
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(deptSql)) {
                    pstmt.setInt(1, tenantId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> item = new HashMap<>();
                            item.put("name", rs.getString("NAME"));
                            departments.add(item);
                        }
                    }
                }
                departmentsJson = objectMapper.writeValueAsString(departments);

                // Fetch Schedules
                List<Map<String, String>> schedules = new ArrayList<>();
                String schedSql = "SELECT NAME FROM schedules WHERE TenantID = ? ORDER BY NAME ASC"; // Corrected TenantID
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(schedSql)) {
                    pstmt.setInt(1, tenantId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> item = new HashMap<>();
                            item.put("name", rs.getString("NAME"));
                            schedules.add(item);
                        }
                    }
                }
                schedulesJson = objectMapper.writeValueAsString(schedules);

                // Fetch Accrual Policies
                List<Map<String, String>> accrualPolicies = new ArrayList<>();
                String accrualSql = "SELECT NAME FROM accruals WHERE TenantID = ? ORDER BY NAME ASC"; // Corrected TenantID
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(accrualSql)) {
                    pstmt.setInt(1, tenantId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> item = new HashMap<>();
                            item.put("name", rs.getString("NAME"));
                            accrualPolicies.add(item);
                        }
                    }
                }
                accrualPoliciesJson = objectMapper.writeValueAsString(accrualPolicies);

                // Fetch Supervisors
                List<Map<String, String>> supervisors = new ArrayList<>();
                String superSql = "SELECT DISTINCT SUPERVISOR FROM employee_data WHERE TenantID = ? AND SUPERVISOR IS NOT NULL AND TRIM(SUPERVISOR) <> '' ORDER BY SUPERVISOR ASC"; // Corrected TenantID
                try (Connection conn = DatabaseConnection.getConnection();
                     PreparedStatement pstmt = conn.prepareStatement(superSql)) {
                    pstmt.setInt(1, tenantId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        while (rs.next()) {
                            Map<String, String> item = new HashMap<>();
                            item.put("name", rs.getString("SUPERVISOR"));
                            supervisors.add(item);
                        }
                    }
                }
                supervisorsJson = objectMapper.writeValueAsString(supervisors);

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "SQL Error loading dropdown data for tenant " + tenantId + ": " + e.getMessage(), e);
                pageLoadError = "Error loading data for dropdowns from database. Column name casing might be an issue (expected TenantID). Details: " + e.getMessage();
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logger.log(Level.SEVERE, "JSON Processing Error loading dropdown data for tenant " + tenantId, e);
                pageLoadError = "Error processing data for display. Please check server logs.";
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Unexpected error loading dropdown data for tenant " + tenantId, e);
                pageLoadError = "An unexpected error occurred while loading data.";
            }
        }

        request.setAttribute("departmentsJson", departmentsJson);
        request.setAttribute("schedulesJson", schedulesJson);
        request.setAttribute("accrualPoliciesJson", accrualPoliciesJson);
        request.setAttribute("supervisorsJson", supervisorsJson);
        if (pageLoadError != null) {
            request.setAttribute("pageLoadErrorMessage", pageLoadError);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Integer tenantId = null;
        String pageLoadErrorMessage = null;

        if (session == null || session.getAttribute("TenantID") == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
            return;
        }
        
        Object tenantIdObj = session.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) {
            tenantId = (Integer) tenantIdObj;
        }
        String userPermissions = (String) session.getAttribute("Permissions");

        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            pageLoadErrorMessage = "Access Denied. Administrator privileges required.";
            tenantId = 0; 
        }
        
        loadDropdownData(request, tenantId); 
        
        if (pageLoadErrorMessage != null && request.getAttribute("pageLoadErrorMessage") == null) {
             request.setAttribute("pageLoadErrorMessage", pageLoadErrorMessage);
        }
        
        String initialReassignType = request.getParameter("type");
        if (initialReassignType == null || initialReassignType.trim().isEmpty()) {
            initialReassignType = "department"; 
        }
        request.setAttribute("type", initialReassignType);

        request.getRequestDispatcher("/reassign.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        String successMessage = null;
        String errorMessage = null;
        String reassignType = request.getParameter("reassignType");

        if (session == null || session.getAttribute("TenantID") == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired. Please log in.", StandardCharsets.UTF_8.name()));
            return;
        }
        
        Integer tenantId = (Integer) session.getAttribute("TenantID");
        String userPermissions = (String) session.getAttribute("Permissions");

        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            errorMessage = "Access Denied. Administrator privileges required.";
        } else {
            String fromValue = request.getParameter("fromValue");
            String toValue = request.getParameter("toValue");

            if (reassignType == null || fromValue == null || toValue == null ||
                fromValue.trim().isEmpty() || toValue.trim().isEmpty()) {
                errorMessage = "Missing required fields (type, from, or to).";
            } else if (fromValue.trim().equals(toValue.trim())) {
                errorMessage = "'From' and 'To' values cannot be the same.";
            } else {
                String columnNameInEmployeeData = null;
                String typeFriendlyName = "";

                switch (reassignType) {
                    case "department":
                        columnNameInEmployeeData = "DEPT"; 
                        typeFriendlyName = "Department";
                        break;
                    case "schedule":
                        columnNameInEmployeeData = "SCHEDULE"; 
                        typeFriendlyName = "Schedule";
                        break;
                    case "accrual_policy":
                        columnNameInEmployeeData = "ACCRUAL_POLICY"; 
                        typeFriendlyName = "Accrual Policy";
                        break;
                    case "supervisor":
                        columnNameInEmployeeData = "SUPERVISOR"; 
                        typeFriendlyName = "Supervisor";
                        break;
                    default:
                        errorMessage = "Invalid reassignment type selected: " + reassignType;
                }

                if (columnNameInEmployeeData != null && errorMessage == null) {
                    // Corrected SQL with 'TenantID' (camel case)
                    String sql = "UPDATE employee_data SET " + columnNameInEmployeeData + " = ? WHERE " + columnNameInEmployeeData + " = ? AND TenantID = ?";
                    int affectedRows = 0;
                    

                    try (Connection conn = DatabaseConnection.getConnection();
                         PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        
                        pstmt.setString(1, toValue.trim());
                        pstmt.setString(2, fromValue.trim());
                        pstmt.setInt(3, tenantId);
                        
                        affectedRows = pstmt.executeUpdate();

                        if (affectedRows > 0) {
                           successMessage = affectedRows + " employee(s) successfully reassigned from " + typeFriendlyName + " '" + fromValue + "' to '" + toValue + "'.";
                        } else {
                           successMessage = "No employees were found matching the 'From' criteria (" + typeFriendlyName + ": '" + fromValue + "'). No changes were made.";
                        }
                    } catch (SQLException e) {
                        errorMessage = "Database error during reassignment. Please check server logs for details. Error: " + e.getMessage();
                        logger.log(Level.SEVERE, "Database error during reassignment of " + typeFriendlyName + " from '" + fromValue + "' to '" + toValue + "' (Tenant: " + tenantId + ")", e);
                    }
                }
            }
        }
        
        loadDropdownData(request, tenantId); 
        request.setAttribute("successMessage", successMessage);
        request.setAttribute("errorMessage", errorMessage);
        request.setAttribute("type", reassignType); 
        
        request.getRequestDispatcher("/reassign.jsp").forward(request, response);
    }
}