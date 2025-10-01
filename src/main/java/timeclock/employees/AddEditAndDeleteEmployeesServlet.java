package timeclock.employees;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.auth.EmailService;
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
    }
    
    private String buildRedirectUrlWithParams(HttpServletRequest request, String page, String error) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page + "?reopenModal=add&error=" + URLEncoder.encode(error, StandardCharsets.UTF_8.name()));
        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String paramName = paramNames.nextElement();
            if (!"action".equals(paramName) && !"error".equals(paramName)) {
                url.append("&").append(paramName).append("=").append(URLEncoder.encode(request.getParameter(paramName), StandardCharsets.UTF_8.name()));
            }
        }
        return url.toString();
    }

    private String buildRedirectUrl(HttpServletRequest request, String page, int eid, String message, String error, String wizardAction, boolean itemAddedInWizard) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page);
        StringBuilder params = new StringBuilder();
        
        if (isValid(wizardAction)) {
            params.append("setup_wizard=true&step=").append(URLEncoder.encode(wizardAction, StandardCharsets.UTF_8.name()));
        }
        if (itemAddedInWizard) {
            params.append(params.length() > 0 ? "&" : "").append("empAdded=true");
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
                if (!con.getAutoCommit()) { con.rollback(); } 
            }
            catch (SQLException e) { logger.log(Level.SEVERE, "Transaction rollback failed!", e); }
        }
    }
    
    private String getTimeZoneForState(String stateCode) {
        if (stateCode == null || stateCode.trim().isEmpty()) { return "America/New_York"; }
        switch (stateCode.trim().toUpperCase()) {
            case "CA": case "WA": case "OR": case "NV": return "America/Los_Angeles";
            case "MT": case "WY": case "CO": case "UT": case "ID": case "NM": return "America/Denver";
            case "AZ": return "America/Phoenix";
            case "ND": case "SD": case "NE": case "KS": case "OK": case "TX":
            case "MN": case "IA": case "MO": case "AR": case "LA": case "WI":
            case "IL": case "MS": case "AL": case "TN": case "KY": return "America/Chicago";
            case "MI": case "IN": case "OH": case "PA": case "NY": case "VT":
            case "NH": case "ME": case "MA": case "RI": case "CT": case "NJ":
            case "DE": case "MD": case "WV": case "VA": case "NC": case "SC":
            case "GA": case "DC": case "FL": return "America/New_York";
            case "AK": return "America/Anchorage";
            case "HI": return "Pacific/Honolulu";
            default: return "America/New_York";
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);
        if (tenantId == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=Session+expired.");
            return;
        }
        String action = request.getParameter("action");
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
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid action specified.", null, false));
                    break;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Top-level error processing employee POST action '" + action + "'", e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "A critical server error occurred.", null, false));
        }
    }

    private void addEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session) throws IOException {
        String firstName = request.getParameter("firstName");
        String lastName = request.getParameter("lastName");
        String email = request.getParameter("email");
        String hireDateStr = request.getParameter("hireDate");
        String state = request.getParameter("state");
        String permissions = request.getParameter("permissionsDropDown");

        if (!isValid(firstName) || !isValid(lastName) || !isValid(email) || !isValid(hireDateStr) || !isValid(state)) {
            String errorMsg = "First Name, Last Name, Email, Hire Date, and State are required fields.";
            response.sendRedirect(buildRedirectUrlWithParams(request, "employees.jsp", errorMsg));
            return;
        }
        String finalEmail = email.trim().toLowerCase();
        
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection(); 
            con.setAutoCommit(false);
            
            int maxUsers = 25;
            try (PreparedStatement ps = con.prepareStatement("SELECT MaxUsers FROM tenants WHERE TenantID = ?")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) maxUsers = rs.getInt("MaxUsers"); }
            }
            int activeUserCount = 0;
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) activeUserCount = rs.getInt(1); }
            }
            if (activeUserCount >= maxUsers) {
                String errorMsg = "user_limit_exceeded:You have reached your maximum of " + maxUsers + " active users.";
                response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, errorMsg, null, false));
                return;
            }
            
            ensureDefaultLookupsExist(con, tenantId);
            
            String sqlIns = "INSERT INTO employee_data (TenantID,TenantEmployeeNumber,FIRST_NAME,LAST_NAME,DEPT,SCHEDULE,SUPERVISOR,PERMISSIONS,ADDRESS,CITY,STATE,ZIP,PHONE,EMAIL,ACCRUAL_POLICY,HIRE_DATE,WORK_SCHEDULE,WAGE_TYPE,WAGE,PasswordHash,RequiresPasswordChange,ACTIVE,TimeZoneId) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE,?)";
            try (PreparedStatement psAdd = con.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
                String timeZoneId = getTimeZoneForState(state);
                int tenEmpNo = getNextTenantEmployeeNumber(con, tenantId);
                psAdd.setInt(1, tenantId);
                psAdd.setInt(2, tenEmpNo);
                psAdd.setString(3, firstName.trim());
                psAdd.setString(4, lastName.trim());
                psAdd.setString(5, request.getParameter("departmentsDropDown"));
                psAdd.setString(6, request.getParameter("schedulesDropDown"));
                psAdd.setString(7, request.getParameter("supervisor"));
                psAdd.setString(8, permissions);
                psAdd.setString(9, request.getParameter("address"));
                psAdd.setString(10, request.getParameter("city"));
                psAdd.setString(11, state);
                psAdd.setString(12, request.getParameter("zip"));
                psAdd.setString(13, request.getParameter("addEmployeePhone"));
                psAdd.setString(14, finalEmail);
                psAdd.setString(15, request.getParameter("accrualsDropDown"));
                psAdd.setDate(16, Date.valueOf(LocalDate.parse(hireDateStr)));
                psAdd.setString(17, request.getParameter("workScheduleDropDown"));
                psAdd.setString(18, request.getParameter("wageTypeDropDown"));
                String wageStr = request.getParameter("wage");
                psAdd.setDouble(19, isValid(wageStr) ? Double.parseDouble(wageStr.replace(',', '.')) : 0.0);
                psAdd.setString(20, BCrypt.hashpw("1234", BCrypt.gensalt(12)));
                psAdd.setBoolean(21, true);
                psAdd.setString(22, timeZoneId);

                if (psAdd.executeUpdate() > 0) {
                    int newGlobalEid = -1;
                    try (ResultSet gK = psAdd.getGeneratedKeys()) { if (gK.next()) newGlobalEid = gK.getInt(1); }
                    con.commit();
                    
                    String successMessage = String.format("Employee %s %s (#%d) added successfully.", 
                                                          firstName.trim(), lastName.trim(), tenEmpNo);
                    
                    // Send welcome email if requested and not in wizard mode
                    boolean inWizard = "true".equals(request.getParameter("setup_wizard"));
                    boolean sendWelcomeEmail = "true".equals(request.getParameter("sendWelcomeEmail"));
                    
                    if (!inWizard && sendWelcomeEmail && isValid(finalEmail)) {
                        // Use a separate thread for email sending to prevent blocking
                        new Thread(() -> {
                            try {
                                sendIndividualWelcomeEmail(tenantId, finalEmail, permissions, firstName.trim(), lastName.trim());
                                logger.info("Welcome email sent successfully to " + finalEmail);
                            } catch (Exception emailEx) {
                                logger.log(Level.WARNING, "Failed to send welcome email to " + finalEmail + ": " + emailEx.getMessage(), emailEx);
                            }
                        }).start();
                        successMessage += " Welcome email will be sent shortly.";
                    }
                    
                    if (inWizard) {
                        logger.info("Wizard mode: Adding employee completed, setting step to after_add_employee_prompt");
                        
                        session.setAttribute("wizardStep", "after_add_employee_prompt");
                        String wizardRedirectAction = "after_add_employee_prompt";
                        String redirectUrl = buildRedirectUrl(request, "employees.jsp", newGlobalEid, successMessage, null, wizardRedirectAction, true);
                        redirectUrl += "&lastAddedPerms=" + URLEncoder.encode(permissions, StandardCharsets.UTF_8.name());
                        
                        logger.info("Wizard redirect URL: " + redirectUrl);
                        response.sendRedirect(redirectUrl);
                    } else {
                        response.sendRedirect(buildRedirectUrl(request, "employees.jsp", newGlobalEid, successMessage, null, null, false));
                    }
                } else { 
                    throw new SQLException("Add employee failed, no rows affected."); 
                }
            }
        } catch (SQLException e) { 
            rollback(con); 
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate entry")) {
                String checkActiveSql = "SELECT EID, ACTIVE, FIRST_NAME, LAST_NAME FROM employee_data WHERE TenantID = ? AND EMAIL = ?";
                try (Connection checkCon = DatabaseConnection.getConnection(); PreparedStatement psCheck = checkCon.prepareStatement(checkActiveSql)) {
                    psCheck.setInt(1, tenantId);
                    psCheck.setString(2, finalEmail);
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            if (rs.getBoolean("ACTIVE")) {
                                String errorMsg = "An active employee with this email address already exists.";
                                response.sendRedirect(buildRedirectUrlWithParams(request, "employees.jsp", errorMsg));
                            } else {
                                int inactiveEid = rs.getInt("EID");
                                String employeeName = rs.getString("FIRST_NAME") + " " + rs.getString("LAST_NAME");
                                String redirectUrl = buildRedirectUrl(request, "employees.jsp", 0, null, null, null, false);
                                redirectUrl += (redirectUrl.contains("?") ? "&" : "?") + "reactivatePrompt=true&eid=" + inactiveEid + "&email=" + URLEncoder.encode(finalEmail, StandardCharsets.UTF_8.name()) + "&name=" + URLEncoder.encode(employeeName, StandardCharsets.UTF_8.name());
                                response.sendRedirect(redirectUrl);
                            }
                            return; 
                        }
                    }
                } catch (Exception checkEx) {
                    logger.log(Level.WARNING, "Failed during duplicate email check", checkEx);
                }
            }
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "A database error occurred.", null, false));
        } catch (Exception e) {
            rollback(con);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "An unexpected error occurred.", null, false));
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) {} }
        }
    }
    
    private void editEmployeeAsRedirect(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session) throws IOException {
        String eidStr = request.getParameter("eid");
        String state = request.getParameter("state");
        String email = request.getParameter("email");
        String newPermissions = request.getParameter("permissionsDropDown");
        if (!isValid(state)) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", 0, null, "State is a required field.", null, false));
            return;
        }
        int globalEID = -1;
        try { globalEID = Integer.parseInt(eidStr); } catch (NumberFormatException e) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid Employee ID.", null, false)); return;
        }
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);
            String currentPermissions = null;
            String checkPermsSql = "SELECT Permissions FROM employee_data WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement psCheck = con.prepareStatement(checkPermsSql)) {
                psCheck.setInt(1, globalEID);
                psCheck.setInt(2, tenantId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) currentPermissions = rs.getString("Permissions");
                }
            }
            if ("Administrator".equalsIgnoreCase(currentPermissions) && !"Administrator".equalsIgnoreCase(newPermissions)) {
                String countSql = "SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE AND Permissions = 'Administrator'";
                try (PreparedStatement psCount = con.prepareStatement(countSql)) {
                    psCount.setInt(1, tenantId);
                    try (ResultSet rsCount = psCount.executeQuery()) {
                        if (rsCount.next() && rsCount.getInt(1) <= 1) {
                            String errorMsg = "Action denied: Cannot remove administrator permissions from the only remaining administrator.";
                            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, errorMsg, null, false));
                            return;
                        }
                    }
                }
            }
            ensureDefaultLookupsExist(con, tenantId);
            String sqlUpd = "UPDATE employee_data SET FIRST_NAME=?,LAST_NAME=?,DEPT=?,SCHEDULE=?,SUPERVISOR=?,PERMISSIONS=?,ADDRESS=?,CITY=?,STATE=?,ZIP=?,PHONE=?,EMAIL=?,ACCRUAL_POLICY=?,HIRE_DATE=?,WORK_SCHEDULE=?,WAGE_TYPE=?,WAGE=?,TimeZoneId=? WHERE EID=? AND TenantID=?";
            try(PreparedStatement psU = con.prepareStatement(sqlUpd)) {
                String timeZoneId = getTimeZoneForState(state);
                psU.setString(1, request.getParameter("firstName").trim());
                psU.setString(2, request.getParameter("lastName").trim());
                psU.setString(3, request.getParameter("departmentsDropDown"));
                psU.setString(4, request.getParameter("schedulesDropDown"));
                psU.setString(5, request.getParameter("supervisor"));
                psU.setString(6, newPermissions);
                psU.setString(7, request.getParameter("address"));
                psU.setString(8, request.getParameter("city"));
                psU.setString(9, state);
                psU.setString(10, request.getParameter("zip"));
                psU.setString(11, request.getParameter("editEmployeePhone"));
                psU.setString(12, email.trim().toLowerCase());
                psU.setString(13, request.getParameter("accrualsDropDown"));
                psU.setDate(14, Date.valueOf(LocalDate.parse(request.getParameter("hireDate"))));
                psU.setString(15, request.getParameter("workScheduleDropDown"));
                psU.setString(16, request.getParameter("wageTypeDropDown"));
                String wageStr = request.getParameter("wage");
                psU.setDouble(17, isValid(wageStr) ? Double.parseDouble(wageStr.replace(',', '.')) : 0.0);
                psU.setString(18, timeZoneId);
                psU.setInt(19, globalEID);
                psU.setInt(20, tenantId);
                
                if (psU.executeUpdate() > 0) {
                    con.commit();
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, "Employee updated successfully.", null, null, false));
                } else {
                    con.rollback();
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "Update failed.", null, false));
                }
            }
        } catch (SQLException e) {
            rollback(con);
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate entry")) {
                String checkActiveSql = "SELECT EID, ACTIVE, FIRST_NAME, LAST_NAME FROM employee_data WHERE TenantID = ? AND EMAIL = ?";
                try (Connection checkCon = DatabaseConnection.getConnection(); PreparedStatement psCheck = checkCon.prepareStatement(checkActiveSql)) {
                    psCheck.setInt(1, tenantId);
                    psCheck.setString(2, email.trim().toLowerCase());
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            if (rs.getBoolean("ACTIVE")) {
                                String errorMsg = "An active employee with that email address already exists.";
                                response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, errorMsg, null, false));
                            } else {
                                String employeeName = rs.getString("FIRST_NAME") + " " + rs.getString("LAST_NAME");
                                String errorMsg = "An inactive employee (" + employeeName + ") with that email address already exists.";
                                response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, errorMsg, null, false));
                            }
                            return;
                        }
                    }
                } catch (Exception checkEx) {
                    logger.log(Level.WARNING, "Failed during duplicate email check in edit", checkEx);
                }
                response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "An employee with that email address already exists.", null, false));
            } else {
                response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "A database error occurred while saving.", null, false));
            }
        } catch (Exception e) {
            rollback(con);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "An unexpected error occurred.", null, false));
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) {} }
        }
    }

    private void editEmployeeAsJson(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session) throws IOException {
        String state = request.getParameter("state");
        String email = request.getParameter("email");
        String newPermissions = request.getParameter("permissionsDropDown");
        if (!isValid(state)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().print("{\"success\": false, \"error\": \"State is a required field.\"}");
            return;
        }
        Connection con = null;
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);
            int globalEID = Integer.parseInt(request.getParameter("eid"));
            String currentPermissions = null;
            String checkPermsSql = "SELECT Permissions FROM employee_data WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement psCheck = con.prepareStatement(checkPermsSql)) {
                psCheck.setInt(1, globalEID);
                psCheck.setInt(2, tenantId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) currentPermissions = rs.getString("Permissions");
                }
            }
            if ("Administrator".equalsIgnoreCase(currentPermissions) && !"Administrator".equalsIgnoreCase(newPermissions)) {
                String countSql = "SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE AND Permissions = 'Administrator'";
                try (PreparedStatement psCount = con.prepareStatement(countSql)) {
                    psCount.setInt(1, tenantId);
                    try (ResultSet rsCount = psCount.executeQuery()) {
                        if (rsCount.next() && rsCount.getInt(1) <= 1) {
                            String errorMsg = "Action denied: Cannot remove administrator permissions from the only remaining administrator.";
                            jsonResponse.put("success", false).put("error", errorMsg);
                            out.print(jsonResponse.toString());
                            return;
                        }
                    }
                }
            }
            ensureDefaultLookupsExist(con, tenantId);
            String sqlUpd = "UPDATE employee_data SET FIRST_NAME=?,LAST_NAME=?,DEPT=?,SCHEDULE=?,SUPERVISOR=?,PERMISSIONS=?,ADDRESS=?,CITY=?,STATE=?,ZIP=?,PHONE=?,EMAIL=?,ACCRUAL_POLICY=?,HIRE_DATE=?,WORK_SCHEDULE=?,WAGE_TYPE=?,WAGE=?,TimeZoneId=? WHERE EID=? AND TenantID=?";
            try (PreparedStatement psU = con.prepareStatement(sqlUpd)) {
                String timeZoneId = getTimeZoneForState(state);
                psU.setString(1, request.getParameter("firstName").trim());
                psU.setString(2, request.getParameter("lastName").trim());
                psU.setString(3, request.getParameter("departmentsDropDown"));
                psU.setString(4, request.getParameter("schedulesDropDown"));
                psU.setString(5, request.getParameter("supervisor"));
                psU.setString(6, newPermissions);
                psU.setString(7, request.getParameter("address"));
                psU.setString(8, request.getParameter("city"));
                psU.setString(9, state);
                psU.setString(10, request.getParameter("zip"));
                psU.setString(11, request.getParameter("editEmployeePhone"));
                psU.setString(12, email.trim().toLowerCase());
                psU.setString(13, request.getParameter("accrualsDropDown"));
                psU.setDate(14, Date.valueOf(LocalDate.parse(request.getParameter("hireDate"))));
                psU.setString(15, request.getParameter("workScheduleDropDown"));
                psU.setString(16, request.getParameter("wageTypeDropDown"));
                String wageStr = request.getParameter("wage");
                psU.setDouble(17, isValid(wageStr) ? Double.parseDouble(wageStr.replace(',', '.')) : 0.0);
                psU.setString(18, timeZoneId);
                psU.setInt(19, globalEID);
                psU.setInt(20, tenantId);
                if (psU.executeUpdate() > 0) {
                    con.commit();
                    session.setAttribute("wizardStep", "prompt_add_employees");
                    jsonResponse.put("success", true).put("message", "Administrator details verified!");
                } else {
                    con.rollback();
                    jsonResponse.put("success", false).put("error", "Update failed. Admin record not found.");
                }
            }
        } catch (SQLException e) {
            rollback(con);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("success", false).put("error", "A database error occurred.");
        } catch (Exception e) {
            rollback(con);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            jsonResponse.put("success", false).put("error", "Server Error: " + e.getMessage());
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) {} }
        }
        out.print(jsonResponse.toString());
    }
    
    private void deactivateEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        String eidStr = request.getParameter("eid");
        String deactivationReason = request.getParameter("deactivationReason");
        int eidToDeactivate = -1;
        try { eidToDeactivate = Integer.parseInt(eidStr); } catch (NumberFormatException e) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid Employee ID.", null, false)); return;
        }
        if (!isValid(deactivationReason)) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "A reason for deactivation is required.", null, false)); return;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT Permissions FROM employee_data WHERE EID = ? AND TenantID = ? AND ACTIVE = TRUE";
            try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                psCheck.setInt(1, eidToDeactivate); psCheck.setInt(2, tenantId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next() && "Administrator".equalsIgnoreCase(rs.getString("Permissions"))) {
                        String countSql = "SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE AND Permissions = 'Administrator'";
                        try (PreparedStatement psCount = con.prepareStatement(countSql)) {
                            psCount.setInt(1, tenantId);
                            try (ResultSet rsCount = psCount.executeQuery()) {
                                if (rsCount.next() && rsCount.getInt(1) <= 1) {
                                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "Action denied: Cannot deactivate the only remaining administrator account.", null, false)); return;
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
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, "Employee successfully deactivated.", null, null, false));
                } else {
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "Employee not found or already inactive.", null, false));
                }
            }
        } catch (SQLException e) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "A database error occurred.", null, false));
        }
    }

    private void reactivateEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        String eidStr = request.getParameter("eid");
        JSONObject jsonResponse = new JSONObject();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        int eidToReactivate = -1;
        try { eidToReactivate = Integer.parseInt(eidStr); } catch (NumberFormatException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            jsonResponse.put("success", false).put("error", "Invalid Employee ID format.");
            response.getWriter().print(jsonResponse.toString()); return;
        }
        try (Connection con = DatabaseConnection.getConnection()) {
            int maxUsers = 25;
            try (PreparedStatement ps = con.prepareStatement("SELECT MaxUsers FROM tenants WHERE TenantID = ?")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) maxUsers = rs.getInt("MaxUsers"); }
            }
            int activeUserCount = 0;
            try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE")) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) activeUserCount = rs.getInt(1); }
            }
            if (activeUserCount >= maxUsers) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                jsonResponse.put("success", false).put("error", "user_limit_exceeded").put("message", "You have reached your maximum of " + maxUsers + " active users.");
                response.getWriter().print(jsonResponse.toString()); return;
            }
            String sql = "UPDATE employee_data SET ACTIVE = TRUE, DeactivationReason = NULL, DeactivationDate = NULL WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, eidToReactivate);
                ps.setInt(2, tenantId);
                if (ps.executeUpdate() > 0) {
                    // Get employee name for success message
                    String employeeName = "Employee";
                    try (PreparedStatement psName = con.prepareStatement("SELECT FIRST_NAME, LAST_NAME FROM employee_data WHERE EID = ? AND TenantID = ?")) {
                        psName.setInt(1, eidToReactivate);
                        psName.setInt(2, tenantId);
                        try (ResultSet rsName = psName.executeQuery()) {
                            if (rsName.next()) {
                                employeeName = rsName.getString("FIRST_NAME") + " " + rsName.getString("LAST_NAME");
                            }
                        }
                    }
                    response.setStatus(HttpServletResponse.SC_OK);
                    jsonResponse.put("success", true).put("message", employeeName + " has been successfully reactivated.");
                    jsonResponse.put("eid", eidToReactivate);
                } else {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    jsonResponse.put("success", false).put("error", "Employee not found or could not be reactivated.");
                }
            }
        } catch (SQLException e) {
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
    
    private void sendIndividualWelcomeEmail(int tenantId, String email, String permissions, String firstName, String lastName) throws Exception {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Email address is required");
        }
        
        // Get company information
        String companyName = "Your Company";
        String companyId = "";
        
        try (Connection con = DatabaseConnection.getConnection()) {
            String sql = "SELECT CompanyName, CompanyIdentifier FROM tenants WHERE TenantID = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String dbCompanyName = rs.getString("CompanyName");
                        String dbCompanyId = rs.getString("CompanyIdentifier");
                        if (dbCompanyName != null && !dbCompanyName.trim().isEmpty()) {
                            companyName = dbCompanyName.trim();
                        }
                        if (dbCompanyId != null && !dbCompanyId.trim().isEmpty()) {
                            companyId = dbCompanyId.trim();
                        } else {
                            companyId = String.valueOf(tenantId);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Could not retrieve company information for welcome email", e);
            // Continue with default values
            companyId = String.valueOf(tenantId);
        }
        
        // Build email content
        String subject = "Welcome to " + companyName + "!";
        String loginLink = getBaseUrl() + "/login.jsp?companyId=" + java.net.URLEncoder.encode(companyId, "UTF-8") + "&focus=email";
        
        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(firstName).append(",\n\n");
        body.append("Welcome to your new time and attendance system!\n\n");
        body.append("To log in, please use the following link. We recommend bookmarking it for easy access:\n");
        body.append(loginLink).append("\n\n");
        body.append("Your login details are:\n");
        body.append("• Company ID: ").append(companyId).append("\n");
        body.append("• Username: Your Email Address\n");
        body.append("• Temporary PIN: 1234\n\n");
        body.append("You will be required to change this PIN on your first login for security.\n\n");
        
        if ("Administrator".equalsIgnoreCase(permissions)) {
            body.append("As an administrator, you have full access to manage employees, schedules, and payroll.\n\n");
            body.append("For help getting started, visit our administrator help center:\n");
            body.append(getBaseUrl()).append("/help.jsp");
        } else {
            body.append("You can punch in and out using our easy-to-use time clock.\n\n");
            body.append("For help using the time clock, visit our user guide:\n");
            body.append(getBaseUrl()).append("/help_user.jsp");
        }
        
        List<String> recipients = new ArrayList<>();
        recipients.add(email);
        EmailService.send(recipients, subject, body.toString());
    }
    
    private String getBaseUrl() {
        // Use localhost for development - in production this should be configurable
        return "http://localhost:8080"; // TODO: Make this configurable via properties
    }
}