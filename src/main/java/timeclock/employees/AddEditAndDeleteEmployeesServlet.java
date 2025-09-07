package timeclock.employees;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/AddEditAndDeleteEmployeesServlet")
public class AddEditAndDeleteEmployeesServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddEditAndDeleteEmployeesServlet.class.getName());

    private Integer getTenantIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object tenantIdObj = session.getAttribute("TenantID");
        return (tenantIdObj instanceof Integer) ? (Integer) tenantIdObj : null;
    }

    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }

    private void ensureDefaultLookupsExist(Connection con, int tenantId) throws SQLException {
        logger.info("Ensuring default lookups exist for TenantID: " + tenantId);
        String[] sqlCommands = {
            "INSERT IGNORE INTO departments (TenantID, NAME, DESCRIPTION) VALUES (?, 'None', 'Default for unassigned employees')",
            "INSERT IGNORE INTO schedules (TenantID, NAME) VALUES (?, 'Open')",
            "INSERT IGNORE INTO accruals (TenantID, NAME) VALUES (?, 'None')"
        };

        for (String sql : sqlCommands) {
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, tenantId);
                ps.executeUpdate();
            }
        }
        logger.info("Default lookup check complete for TenantID: " + tenantId);
    }
    
    private String buildRedirectUrl(HttpServletRequest request, String page, int eid, String message, String error, String wizardAction) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page);
        StringBuilder params = new StringBuilder();

        if (isValid(wizardAction)) {
            params.append(params.length() > 0 ? "&" : "").append("setup_wizard=true&step=").append(wizardAction);
        }
        if (eid > 0) {
            params.append(params.length() > 0 ? "&" : "").append("eid=").append(eid);
        }
        if (message != null) {
            params.append(params.length() > 0 ? "&" : "").append("message=").append(URLEncoder.encode(message, StandardCharsets.UTF_8.name()));
        }
        if (error != null) {
            params.append(params.length() > 0 ? "&" : "").append("error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8.name()));
        }

        if (params.length() > 0) {
            url.append("?").append(params);
        }
        return url.toString();
    }

    private void rollback(Connection con) {
        if (con != null) {
            try { 
                if (!con.getAutoCommit()) { 
                    logger.warning("Transaction is being rolled back.");
                    con.rollback(); 
                } 
            }
            catch (SQLException e) { logger.log(Level.SEVERE, "Transaction rollback failed!", e); }
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            String requestedWithHeader = request.getHeader("X-Requested-With");
            boolean isAjax = "XMLHttpRequest".equals(requestedWithHeader);
            
            if (isAjax) {
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
                response.getWriter().print("{\"success\": false, \"error\": \"Session expired. Please log in again.\", \"sessionExpired\": true}");
            } else {
                response.sendRedirect(request.getContextPath() + "/login.jsp?error=Session+expired.");
            }
            return;
        }

        String action = request.getParameter("action");
        logger.info("AddEditAndDeleteEmployeesServlet received POST action: " + action);
        
        if ("editEmployee".equals(action) && "true".equalsIgnoreCase(request.getParameter("admin_verify_step"))) {
            editEmployeeAsJson(request, response, tenantId, session);
            return;
        }
        
        try {
            switch (action != null ? action.trim() : "") {
                case "addEmployee":
                    addEmployee(request, response, tenantId, session);
                    break;
                case "editEmployee":
                    editEmployeeAsRedirect(request, response, tenantId, session);
                    break;
                case "deactivateEmployee":
                    deactivateEmployee(request, response, tenantId);
                    break;
                case "reactivateEmployee":
                    reactivateEmployee(request, response, tenantId);
                    break;
                default:
                    logger.warning("Invalid action specified: " + action);
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid action specified.", null));
                    break;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Top-level error processing employee POST action '" + action + "'", e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "A critical server error occurred.", null));
        }
    }

    private void addEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session) throws IOException {
        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String email = request.getParameter("email").trim().toLowerCase();
        String hireDateStr = request.getParameter("hireDate");
        
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection(); 
            con.setAutoCommit(false);
            
            // --- NEW USER LIMIT CHECK ---
            int maxUsers = 25; // Default value
            try (PreparedStatement ps = con.prepareStatement("SELECT MaxUsers FROM tenants WHERE TenantID = ?")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maxUsers = rs.getInt("MaxUsers");
                    }
                }
            }

            int activeUserCount = 0;
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        activeUserCount = rs.getInt(1);
                    }
                }
            }
            
            if (activeUserCount >= maxUsers) {
                logger.warning("Add Employee denied for TenantID " + tenantId + ". Limit of " + maxUsers + " reached.");
                String errorMsg = "user_limit_exceeded:You have reached your maximum of " + maxUsers + " active users. Please upgrade your plan to add more employees.";
                response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, errorMsg, null));
                return; // Stop execution
            }
            // --- END USER LIMIT CHECK ---
            
            ensureDefaultLookupsExist(con, tenantId);
            
            String sqlIns = "INSERT INTO employee_data (TenantID,TenantEmployeeNumber,FIRST_NAME,LAST_NAME,DEPT,SCHEDULE,SUPERVISOR,PERMISSIONS,ADDRESS,CITY,STATE,ZIP,PHONE,EMAIL,ACCRUAL_POLICY,HIRE_DATE,WORK_SCHEDULE,WAGE_TYPE,WAGE,PasswordHash,RequiresPasswordChange,ACTIVE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE)";
            try (PreparedStatement psAdd = con.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
                int tenEmpNo = getNextTenantEmployeeNumber(con, tenantId);
                psAdd.setInt(1, tenantId);
                psAdd.setInt(2, tenEmpNo);
                psAdd.setString(3, firstName.trim());
                psAdd.setString(4, lastName.trim());
                psAdd.setString(5, request.getParameter("departmentsDropDown"));
                psAdd.setString(6, request.getParameter("schedulesDropDown"));
                psAdd.setString(7, request.getParameter("supervisor"));
                psAdd.setString(8, request.getParameter("permissionsDropDown"));
                psAdd.setString(9, request.getParameter("address"));
                psAdd.setString(10, request.getParameter("city"));
                psAdd.setString(11, request.getParameter("state"));
                psAdd.setString(12, request.getParameter("zip"));
                psAdd.setString(13, request.getParameter("addEmployeePhone"));
                psAdd.setString(14, email);
                psAdd.setString(15, request.getParameter("accrualsDropDown"));
                psAdd.setDate(16, Date.valueOf(LocalDate.parse(hireDateStr)));
                psAdd.setString(17, request.getParameter("workScheduleDropDown"));
                psAdd.setString(18, request.getParameter("wageTypeDropDown"));
                
                String wageStr = request.getParameter("wage");
                if (isValid(wageStr)) {
                    psAdd.setDouble(19, Double.parseDouble(wageStr.replace(',', '.')));
                } else {
                    psAdd.setDouble(19, 0.0);
                }
                
                psAdd.setString(20, BCrypt.hashpw("1234", BCrypt.gensalt(12)));
                psAdd.setBoolean(21, true);

                if (psAdd.executeUpdate() > 0) {
                    int newGlobalEid = -1;
                    try (ResultSet gK = psAdd.getGeneratedKeys()) { if (gK.next()) newGlobalEid = gK.getInt(1); }
                    con.commit();
                    logger.info("Successfully added and committed new employee.");
                    String successMessage = "Employee " + firstName.trim() + " " + lastName.trim() + " added.";
                    boolean inWizard = "true".equals(request.getParameter("setup_wizard"));
                    String wizardRedirectAction = null;
                    if (inWizard) {
                        session.setAttribute("wizardStep", "after_add_employee_prompt");
                        wizardRedirectAction = "after_add_employee_prompt";
                    }
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", newGlobalEid, successMessage, null, wizardRedirectAction));
                } else { 
                    throw new SQLException("Add employee failed, no rows affected."); 
                }
            }
        } catch (SQLException e) { 
            rollback(con); 
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate entry") && e.getMessage().contains("uq_employee_email_tenant")) {
                String checkInactiveSql = "SELECT EID, ACTIVE FROM employee_data WHERE TenantID = ? AND EMAIL = ?";
                try (Connection checkCon = DatabaseConnection.getConnection();
                     PreparedStatement psCheck = checkCon.prepareStatement(checkInactiveSql)) {
                    psCheck.setInt(1, tenantId);
                    psCheck.setString(2, email);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next() && !rs.getBoolean("ACTIVE")) {
                            int inactiveEid = rs.getInt("EID");
                            String redirectUrl = buildRedirectUrl(request, "employees.jsp", 0, null, null, null);
                            redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "reactivatePrompt=true&eid=" + inactiveEid + "&email=" + URLEncoder.encode(email, StandardCharsets.UTF_8.name());
                            response.sendRedirect(redirectUrl);
                            return;
                        }
                    }
                } catch (SQLException checkEx) {
                    logger.log(Level.WARNING, "Failed to check for inactive employee", checkEx);
                }
            }
            logger.log(Level.WARNING, "Error adding employee for TenantID " + tenantId, e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Error: Could not add employee. An active employee with this email may already exist, or a database error occurred.", null));
        } catch (Exception e) {
            rollback(con);
            logger.log(Level.SEVERE, "Unexpected error adding employee for TenantID " + tenantId, e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "An unexpected error occurred.", null));
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); } }
        }
    }
    
    private void editEmployeeAsRedirect(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session) throws IOException {
        String eidStr = request.getParameter("eid");
        int globalEID = -1;
        try {
            globalEID = Integer.parseInt(eidStr);
        } catch (NumberFormatException e) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid Employee ID.", null));
            return;
        }

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);

            ensureDefaultLookupsExist(con, tenantId);

            String sqlUpd = "UPDATE employee_data SET FIRST_NAME=?,LAST_NAME=?,DEPT=?,SCHEDULE=?,SUPERVISOR=?,PERMISSIONS=?,ADDRESS=?,CITY=?,STATE=?,ZIP=?,PHONE=?,EMAIL=?,ACCRUAL_POLICY=?,HIRE_DATE=?,WORK_SCHEDULE=?,WAGE_TYPE=?,WAGE=? WHERE EID=? AND TenantID=?";
            try(PreparedStatement psU = con.prepareStatement(sqlUpd)) {
                psU.setString(1, request.getParameter("firstName").trim());
                psU.setString(2, request.getParameter("lastName").trim());
                psU.setString(3, request.getParameter("departmentsDropDown"));
                psU.setString(4, request.getParameter("schedulesDropDown"));
                psU.setString(5, request.getParameter("supervisor"));
                psU.setString(6, request.getParameter("permissionsDropDown"));
                psU.setString(7, request.getParameter("address"));
                psU.setString(8, request.getParameter("city"));
                psU.setString(9, request.getParameter("state"));
                psU.setString(10, request.getParameter("zip"));
                psU.setString(11, request.getParameter("editEmployeePhone"));
                psU.setString(12, request.getParameter("email").trim().toLowerCase());
                psU.setString(13, request.getParameter("accrualsDropDown"));
                psU.setDate(14, Date.valueOf(LocalDate.parse(request.getParameter("hireDate"))));
                psU.setString(15, request.getParameter("workScheduleDropDown"));
                psU.setString(16, request.getParameter("wageTypeDropDown"));
                
                String wageStr = request.getParameter("wage");
                if (isValid(wageStr)) {
                    psU.setDouble(17, Double.parseDouble(wageStr.replace(',', '.')));
                } else {
                    psU.setDouble(17, 0.0);
                }
                
                psU.setInt(18, globalEID);
                psU.setInt(19, tenantId);
                
                int rowsAffected = psU.executeUpdate();
                if (rowsAffected > 0) {
                    con.commit();
                    logger.info("Successfully updated and committed employee EID: " + globalEID);
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, "Employee updated successfully.", null, null));
                } else {
                    con.rollback();
                    logger.warning("Update for employee EID " + globalEID + " affected 0 rows. Transaction rolled back.");
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "Update failed. Employee not found or no changes were made.", null));
                }
            }
        } catch (Exception e) {
            rollback(con);
            logger.log(Level.SEVERE, "Error editing employee EID: " + globalEID + ". Transaction rolled back.", e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "An error occurred while saving.", null));
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); } }
        }
    }

    private void editEmployeeAsJson(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session) throws IOException {
        Connection con = null;
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);
            
            ensureDefaultLookupsExist(con, tenantId);
            
            int globalEID = Integer.parseInt(request.getParameter("eid"));
            String sqlUpd = "UPDATE employee_data SET FIRST_NAME=?,LAST_NAME=?,DEPT=?,SCHEDULE=?,SUPERVISOR=?,PERMISSIONS=?,ADDRESS=?,CITY=?,STATE=?,ZIP=?,PHONE=?,EMAIL=?,ACCRUAL_POLICY=?,HIRE_DATE=?,WORK_SCHEDULE=?,WAGE_TYPE=?,WAGE=? WHERE EID=? AND TenantID=?";

            try (PreparedStatement psU = con.prepareStatement(sqlUpd)) {
                psU.setString(1, request.getParameter("firstName").trim());
                psU.setString(2, request.getParameter("lastName").trim());
                psU.setString(3, request.getParameter("departmentsDropDown"));
                psU.setString(4, request.getParameter("schedulesDropDown"));
                psU.setString(5, request.getParameter("supervisor"));
                psU.setString(6, request.getParameter("permissionsDropDown"));
                psU.setString(7, request.getParameter("address"));
                psU.setString(8, request.getParameter("city"));
                psU.setString(9, request.getParameter("state"));
                psU.setString(10, request.getParameter("zip"));
                psU.setString(11, request.getParameter("editEmployeePhone"));
                psU.setString(12, request.getParameter("email").trim().toLowerCase());
                psU.setString(13, request.getParameter("accrualsDropDown"));
                psU.setDate(14, Date.valueOf(LocalDate.parse(request.getParameter("hireDate"))));
                psU.setString(15, request.getParameter("workScheduleDropDown"));
                psU.setString(16, request.getParameter("wageTypeDropDown"));
                
                String wageStr = request.getParameter("wage");
                if (isValid(wageStr)) {
                    psU.setDouble(17, Double.parseDouble(wageStr.replace(',', '.')));
                } else {
                    psU.setDouble(17, 0.0);
                }
                
                psU.setInt(18, globalEID);
                psU.setInt(19, tenantId);

                if (psU.executeUpdate() > 0) {
                    con.commit();
                    logger.info("Successfully updated and committed ADMIN employee EID: " + globalEID);
                    session.setAttribute("wizardStep", "prompt_add_employees");
                    jsonResponse.put("success", true).put("message", "Administrator details verified!");
                } else {
                    con.rollback();
                    logger.warning("Update for ADMIN employee EID " + globalEID + " affected 0 rows. Transaction rolled back.");
                    jsonResponse.put("success", false).put("error", "Update failed. Admin record not found.");
                }
            }
        } catch (Exception e) {
            rollback(con);
            logger.log(Level.SEVERE, "Error in editEmployeeAsJson. Transaction rolled back.", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("success", false).put("error", "Server Error: " + e.getMessage());
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); } }
        }
        out.print(jsonResponse.toString());
    }
    
    private void deactivateEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        String eidStr = request.getParameter("eid");
        String deactivationReason = request.getParameter("deactivationReason");
        int eidToDeactivate = -1;
        try {
            eidToDeactivate = Integer.parseInt(eidStr);
        } catch (NumberFormatException e) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid Employee ID.", null));
            return;
        }

        if (!isValid(deactivationReason)) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "A reason for deactivation is required.", null));
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT Permissions FROM employee_data WHERE EID = ? AND TenantID = ? AND ACTIVE = TRUE";
            try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                psCheck.setInt(1, eidToDeactivate);
                psCheck.setInt(2, tenantId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next() && "Administrator".equalsIgnoreCase(rs.getString("Permissions"))) {
                        String countSql = "SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE AND Permissions = 'Administrator'";
                        try (PreparedStatement psCount = con.prepareStatement(countSql)) {
                            psCount.setInt(1, tenantId);
                            try (ResultSet rsCount = psCount.executeQuery()) {
                                if (rsCount.next() && rsCount.getInt(1) <= 1) {
                                    String errorMsg = "Action denied: Cannot deactivate the only remaining administrator account.";
                                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, errorMsg, null));
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            String deactivateSql = "UPDATE employee_data SET ACTIVE = FALSE, DeactivationReason = ?, DeactivationDate = CURDATE() WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement psDeactivate = con.prepareStatement(deactivateSql)) {
                psDeactivate.setString(1, deactivationReason);
                psDeactivate.setInt(2, eidToDeactivate);
                psDeactivate.setInt(3, tenantId);
                if (psDeactivate.executeUpdate() > 0) {
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, "Employee successfully deactivated.", null, null));
                } else {
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "Employee not found or already inactive.", null));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during deactivation for EID: " + eidToDeactivate, e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "A database error occurred.", null));
        }
    }

    private void reactivateEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        String eidStr = request.getParameter("eid");
        JSONObject jsonResponse = new JSONObject();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        int eidToReactivate = -1;
        try {
            eidToReactivate = Integer.parseInt(eidStr);
        } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("success", false).put("error", "Invalid Employee ID format.");
            response.getWriter().print(jsonResponse.toString());
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            int maxUsers = 25;
            try (PreparedStatement ps = con.prepareStatement("SELECT MaxUsers FROM tenants WHERE TenantID = ?")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        maxUsers = rs.getInt("MaxUsers");
                    }
                }
            }

            int activeUserCount = 0;
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        activeUserCount = rs.getInt(1);
                    }
                }
            }
            
            if (activeUserCount >= maxUsers) {
                logger.warning("Reactivation denied for TenantID " + tenantId + ". Limit of " + maxUsers + " reached.");
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("success", false);
                jsonResponse.put("error", "user_limit_exceeded");
                jsonResponse.put("message", "You have reached your maximum of " + maxUsers + " active users. Please upgrade your plan to add more employees.");
                response.getWriter().print(jsonResponse.toString());
                return;
            }

            String sql = "UPDATE employee_data SET ACTIVE = TRUE, DeactivationReason = NULL, DeactivationDate = NULL WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, eidToReactivate);
                ps.setInt(2, tenantId);
                int rowsAffected = ps.executeUpdate();

                if (rowsAffected > 0) {
                    response.setStatus(HttpServletResponse.SC_OK);
                    jsonResponse.put("success", true).put("message", "Employee successfully reactivated.");
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("success", false).put("error", "Employee not found or could not be reactivated.");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during reactivation for EID: " + eidToReactivate, e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("success", false).put("error", "A database error occurred during reactivation.");
        }
        response.getWriter().print(jsonResponse.toString());
    }

    private int getNextTenantEmployeeNumber(Connection con, int tenantId) throws SQLException {
       String sqlMax = "SELECT MAX(TenantEmployeeNumber) FROM employee_data WHERE TenantID = ?";
        try (PreparedStatement psMax = con.prepareStatement(sqlMax)) {
            psMax.setInt(1, tenantId);
            try (ResultSet rs = psMax.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1; 
            }
        }
    }
}