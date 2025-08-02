package timeclock.employees;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private String buildRedirectUrl(HttpServletRequest request, String page, int eid, String message, String error, String wizardAction) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page);
        StringBuilder params = new StringBuilder();

        if (isValid(wizardAction)) {
            params.append("setup_wizard=true&action=").append(wizardAction);
        }
        if (eid > 0) {
            params.append(params.length() > 0 ? "&" : "").append("eid=").append(eid);
        }
        if (message != null) {
            params.append(params.length() > 0 ? "&" : "").append("message=").append(URLEncoder.encode(message, StandardCharsets.UTF_8));
        }
        if (error != null) {
            params.append(params.length() > 0 ? "&" : "").append("error=").append(URLEncoder.encode(error, StandardCharsets.UTF_8));
        }

        if (params.length() > 0) {
            url.append("?").append(params);
        }
        logger.info("[AddEditAndDeleteEmployeesServlet] Redirecting to: " + url.toString());
        return url.toString();
    }

    private void rollback(Connection con) {
        if (con != null) {
            try { if (!con.getAutoCommit()) { con.rollback(); } }
            catch (SQLException e) { logger.log(Level.SEVERE, "Transaction rollback failed!", e); }
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
        String wizardStep = (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) ? (String) session.getAttribute("wizardStep") : null;

        try {
            switch (action != null ? action.trim() : "") {
                case "addEmployee":
                    addEmployee(request, response, tenantId, wizardStep, session);
                    break;
                case "editEmployee":
                    editEmployee(request, response, tenantId, wizardStep, session);
                    break;
                case "deactivateEmployee":
                    deactivateEmployee(request, response, tenantId);
                    break;
                default:
                    logger.warning("POST unknown action: " + action + " T:" + tenantId);
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid action specified.", null));
                    break;
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error processing employee POST action '" + action + "'", e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "A server error occurred.", null));
        }
    }

    private void addEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId, String wizardStep, HttpSession session) throws IOException {
        String firstName = request.getParameter("addFirstName");
        String lastName = request.getParameter("addLastName");
        String email = request.getParameter("addEmail");
        String hireDateStr = request.getParameter("addHireDate");
        String wageStr = request.getParameter("addWage");

        if (!isValid(firstName) || !isValid(lastName) || !isValid(email) || !isValid(hireDateStr) || !isValid(wageStr)) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Required fields are missing.", "addMoreEmployees".equals(wizardStep) ? "prompt_add_employees" : null));
            return;
        }

        Connection con = null;
        try {
            con = DatabaseConnection.getConnection(); 
            con.setAutoCommit(false);

            try (PreparedStatement psChkE = con.prepareStatement("SELECT EID FROM EMPLOYEE_DATA WHERE EMAIL = ? AND TenantID = ?")) { 
                psChkE.setString(1, email.trim().toLowerCase()); 
                psChkE.setInt(2, tenantId); 
                if (psChkE.executeQuery().next()) { 
                    throw new SQLException("Email address '" + email.trim() + "' is already registered.");
                }
            }
            
            int tenEmpNo = getNextTenantEmployeeNumber(con, tenantId);
            int newGlobalEid = -1;

            String sqlIns = "INSERT INTO EMPLOYEE_DATA (TenantID,TenantEmployeeNumber,FIRST_NAME,LAST_NAME,DEPT,SCHEDULE,SUPERVISOR,PERMISSIONS,ADDRESS,CITY,STATE,ZIP,PHONE,EMAIL,ACCRUAL_POLICY,HIRE_DATE,WORK_SCHEDULE,WAGE_TYPE,WAGE,PasswordHash,RequiresPasswordChange,ACTIVE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,TRUE)";
            try (PreparedStatement psAdd = con.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
                psAdd.setInt(1, tenantId);
                psAdd.setInt(2, tenEmpNo);
                psAdd.setString(3, firstName.trim());
                psAdd.setString(4, lastName.trim());
                psAdd.setString(5, request.getParameter("addDepartmentsDropDown"));
                psAdd.setString(6, isEmpty(request.getParameter("addSchedulesDropDown")) ? null : request.getParameter("addSchedulesDropDown"));
                psAdd.setString(7, isEmpty(request.getParameter("addSupervisor")) || "None".equalsIgnoreCase(request.getParameter("addSupervisor")) ? null : request.getParameter("addSupervisor"));
                psAdd.setString(8, request.getParameter("addPermissionsDropDown"));
                psAdd.setString(9, isEmpty(request.getParameter("addAddress")) ? null : request.getParameter("addAddress"));
                psAdd.setString(10, isEmpty(request.getParameter("addCity")) ? null : request.getParameter("addCity"));
                psAdd.setString(11, isEmpty(request.getParameter("addState")) ? null : request.getParameter("addState"));
                psAdd.setString(12, isEmpty(request.getParameter("addZip")) ? null : request.getParameter("addZip"));
                psAdd.setString(13, isEmpty(request.getParameter("addPhone")) ? null : request.getParameter("addPhone"));
                psAdd.setString(14, email.trim().toLowerCase());
                psAdd.setString(15, request.getParameter("addAccrualsDropDown"));
                psAdd.setDate(16, Date.valueOf(LocalDate.parse(hireDateStr)));
                psAdd.setString(17, request.getParameter("addWorkScheduleDropDown"));
                psAdd.setString(18, request.getParameter("addWageTypeDropDown"));
                psAdd.setDouble(19, Double.parseDouble(wageStr));
                psAdd.setString(20, BCrypt.hashpw("1234", BCrypt.gensalt(12)));
                psAdd.setBoolean(21, true);

                if (psAdd.executeUpdate() > 0) {
                    try (ResultSet gK = psAdd.getGeneratedKeys()) { 
                        if (gK.next()) newGlobalEid = gK.getInt(1); 
                    }
                    con.commit();
                    // ** FIX: Changed "ID" to "Emp ID" **
                    String successMessage = "Employee " + firstName.trim() + " " + lastName.trim() + " (Emp ID:" + tenEmpNo + ") added.";
                    if ("addMoreEmployees".equals(wizardStep)) {
                        session.setAttribute("employeeJustAddedInWizardName", firstName.trim() + " " + lastName.trim());
                    }
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", newGlobalEid, successMessage, null, "addMoreEmployees".equals(wizardStep) ? "prompt_add_employees" : null));
                } else { 
                    throw new SQLException("Add employee failed, no rows affected."); 
                }
            }
        } catch (Exception e) { 
            rollback(con); 
            logger.log(Level.SEVERE, "Error adding employee for TenantID: " + tenantId, e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Error: " + e.getMessage(), "addMoreEmployees".equals(wizardStep) ? "prompt_add_employees" : null));
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); } }
        }
    }
    
    private void editEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId, String wizardStep, HttpSession session) throws IOException {
        String eidStr = request.getParameter("eid");
        int globalEID = -1;
        try {
            globalEID = Integer.parseInt(eidStr);
        } catch (NumberFormatException e) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid Employee ID.", wizardStep != null ? "edit_admin_profile" : null));
            return;
        }

        String sqlUpd = "UPDATE EMPLOYEE_DATA SET FIRST_NAME=?,LAST_NAME=?,DEPT=?,SCHEDULE=?,SUPERVISOR=?,PERMISSIONS=?,ADDRESS=?,CITY=?,STATE=?,ZIP=?,PHONE=?,EMAIL=?,ACCRUAL_POLICY=?,HIRE_DATE=?,WORK_SCHEDULE=?,WAGE_TYPE=?,WAGE=? WHERE EID=? AND TenantID=?";
        String sqlGetEmpId = "SELECT TenantEmployeeNumber FROM EMPLOYEE_DATA WHERE EID = ? AND TenantID = ?";
        
        try (Connection con = DatabaseConnection.getConnection()) {
            
            // ** FIX: Get the TenantEmployeeNumber to display in the success message **
            int tenantEmployeeNumber = 0;
            try (PreparedStatement psGetId = con.prepareStatement(sqlGetEmpId)) {
                psGetId.setInt(1, globalEID);
                psGetId.setInt(2, tenantId);
                try (ResultSet rs = psGetId.executeQuery()) {
                    if (rs.next()) {
                        tenantEmployeeNumber = rs.getInt("TenantEmployeeNumber");
                    }
                }
            }
            if (tenantEmployeeNumber == 0) {
                // If we didn't find the employee, throw an error before trying to update
                throw new SQLException("Employee with EID " + globalEID + " not found for this tenant.");
            }

            try (PreparedStatement psU = con.prepareStatement(sqlUpd)) {
                psU.setString(1, request.getParameter("firstName").trim());
                psU.setString(2, request.getParameter("lastName").trim());
                psU.setString(3, request.getParameter("departmentsDropDown"));
                psU.setString(4, isEmpty(request.getParameter("schedulesDropDown")) ? null : request.getParameter("schedulesDropDown"));
                psU.setString(5, isEmpty(request.getParameter("supervisor")) || "None".equalsIgnoreCase(request.getParameter("supervisor")) ? null : request.getParameter("supervisor"));
                psU.setString(6, request.getParameter("permissionsDropDown"));
                psU.setString(7, isEmpty(request.getParameter("address")) ? null : request.getParameter("address"));
                psU.setString(8, isEmpty(request.getParameter("city")) ? null : request.getParameter("city"));
                psU.setString(9, isEmpty(request.getParameter("state")) ? null : request.getParameter("state"));
                psU.setString(10, isEmpty(request.getParameter("zip")) ? null : request.getParameter("zip"));
                psU.setString(11, isEmpty(request.getParameter("phone")) ? null : request.getParameter("phone"));
                psU.setString(12, request.getParameter("email").trim().toLowerCase());
                psU.setString(13, request.getParameter("accrualsDropDown"));
                psU.setDate(14, Date.valueOf(LocalDate.parse(request.getParameter("hireDate"))));
                psU.setString(15, request.getParameter("workScheduleDropDown"));
                psU.setString(16, request.getParameter("wageTypeDropDown"));
                psU.setDouble(17, Double.parseDouble(request.getParameter("wage")));
                psU.setInt(18, globalEID);
                psU.setInt(19, tenantId);
                
                int rowsAffected = psU.executeUpdate();
                if (rowsAffected > 0) {
                    // ** FIX: Use the fetched TenantEmployeeNumber and "Emp ID" label **
                    String successMessage = "Employee (Emp ID:" + tenantEmployeeNumber + ") updated successfully.";
                    String wizardRedirectAction = null;
                    if ("editAdminProfile".equals(wizardStep)) {
                        session.setAttribute("wizardStep", "addMoreEmployees");
                        wizardRedirectAction = "prompt_add_employees";
                    }
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, successMessage, null, wizardRedirectAction));
                } else {
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "Update failed. No changes were made.", wizardStep != null ? "edit_admin_profile" : null));
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error editing employee EID:" + globalEID, e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", globalEID, null, "Error: " + e.getMessage(), wizardStep != null ? "edit_admin_profile" : null));
        }
    }
    
    private void deactivateEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        String eidStr = request.getParameter("eid");
        int eidToDeactivate = -1;
        try {
            eidToDeactivate = Integer.parseInt(eidStr);
        } catch (NumberFormatException e) {
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid Employee ID for deactivation.", null));
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT Permissions FROM EMPLOYEE_DATA WHERE EID = ? AND TenantID = ? AND ACTIVE = TRUE";
            try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                psCheck.setInt(1, eidToDeactivate);
                psCheck.setInt(2, tenantId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next() && "Administrator".equalsIgnoreCase(rs.getString("Permissions"))) {
                        String countSql = "SELECT COUNT(*) FROM EMPLOYEE_DATA WHERE TenantID = ? AND ACTIVE = TRUE AND Permissions = 'Administrator'";
                        try (PreparedStatement psCount = con.prepareStatement(countSql)) {
                            psCount.setInt(1, tenantId);
                            try (ResultSet rsCount = psCount.executeQuery()) {
                                if (rsCount.next() && rsCount.getInt(1) <= 1) {
                                    String errorMsg = "Action denied: Cannot deactivate the only remaining administrator account.";
                                    logger.warning(errorMsg + " TenantID: " + tenantId);
                                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, errorMsg, null));
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            String deactivateSql = "UPDATE EMPLOYEE_DATA SET ACTIVE = FALSE WHERE EID = ? AND TenantID = ?";
            try (PreparedStatement psDeactivate = con.prepareStatement(deactivateSql)) {
                psDeactivate.setInt(1, eidToDeactivate);
                psDeactivate.setInt(2, tenantId);
                int rowsAffected = psDeactivate.executeUpdate();
                if (rowsAffected > 0) {
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, "Employee successfully deactivated.", null, null));
                } else {
                    response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "Employee not found or already inactive.", null));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error during deactivation for EID: " + eidToDeactivate, e);
            response.sendRedirect(buildRedirectUrl(request, "employees.jsp", eidToDeactivate, null, "A database error occurred during deactivation.", null));
        }
    }

    private int getNextTenantEmployeeNumber(Connection con, int tenantId) throws SQLException {
       String sqlMax = "SELECT MAX(TenantEmployeeNumber) FROM EMPLOYEE_DATA WHERE TenantID = ?";
        try (PreparedStatement psMax = con.prepareStatement(sqlMax)) {
            psMax.setInt(1, tenantId);
            try (ResultSet rs = psMax.executeQuery()) {
                return rs.next() ? rs.getInt(1) + 1 : 1; 
            }
        }
    }
}