package timeclock.employees;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest; // Import HttpServletRequest
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import org.mindrot.jbcrypt.BCrypt;

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
        if (tenantIdObj instanceof Integer) {
            Integer id = (Integer) tenantIdObj;
            return (id > 0) ? id : null;
        }
        return null;
    }

    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }
    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    // UPDATED buildRedirectUrl
    private String buildRedirectUrl(HttpServletRequest request, String page, int globalEid, String successMessage, String errorMessage, String wizardAction) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page); // ADDED request.getContextPath()
        boolean firstParam = true;

        if (isValid(wizardAction)) {
            url.append(firstParam ? "?" : "&").append("setup_wizard=true");
            firstParam = false;
            url.append("&action=").append(URLEncoder.encode(wizardAction, StandardCharsets.UTF_8.name()));
            if (globalEid > 0 && ("edit_admin_profile".equals(wizardAction) || "prompt_add_employees".equals(wizardAction) || "setup_complete".equals(wizardAction) )) {
                url.append("&eid=").append(globalEid);
            }
        } else if (globalEid > 0 && (wizardAction == null || !"setup_complete".equals(wizardAction))) { 
            // Don't add eid if not in wizard and action is setup_complete (e.g. general page load after completion)
             url.append(firstParam ? "?" : "&").append("eid=").append(globalEid);
             firstParam = false;
        }

        if (errorMessage != null && !errorMessage.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("error=").append(URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.name()));
            firstParam = false; 
        } else if (successMessage != null && !successMessage.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("message=").append(URLEncoder.encode(successMessage, StandardCharsets.UTF_8.name()));
        }
        logger.info("[AddEditAndDeleteEmployeesServlet] Redirecting to: " + url.toString());
        return url.toString();
    }
    
    private void writeJsonResponse(HttpServletResponse response, boolean success, String message, int statusCode) throws IOException {
        if (response.isCommitted()) { logger.warning("Response committed! Cannot send JSON: " + message); return; }
        response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); response.setStatus(statusCode);
        StringBuilder json = new StringBuilder("{");
        json.append("\"success\": ").append(success).append(",");
        String key = success ? "message" : "error";
        String escapedMessage = (message == null) ? "" : message.replace("\\", "\\\\").replace("\"", "\\\"");
        json.append("\"").append(key).append("\": \"").append(escapedMessage).append("\"");
        json.append("}");
        try (PrintWriter out = response.getWriter()) { out.print(json.toString()); out.flush(); }
        catch (IllegalStateException e) { logger.log(Level.SEVERE, "Failed to get writer for JSON: " + json.toString(), e); }
    }

    private void rollback(Connection con) {
        if (con != null) {
            try { if (!con.isClosed() && !con.getAutoCommit()) { logger.warning("Rolling back transaction."); con.rollback(); } }
            catch (SQLException rbEx) { logger.log(Level.SEVERE, "Transaction rollback failed!", rbEx); }
        } else { logger.warning("Rollback requested but connection was null."); }
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String eidStr = request.getParameter("eid");
        String redirectPage = "employees.jsp";

        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            response.sendRedirect(buildRedirectUrl(request, redirectPage, -1, null, "Session expired. Please log in.", null));
            return;
        }
        handleDeactivate(request, response, tenantId, eidStr, redirectPage);
    }
    
    private void handleDeactivate(HttpServletRequest request, HttpServletResponse response, int tenantId, String eidStr, String redirectPage) throws IOException {
        String successMessage = null; String errorMessage = null; int globalEid = -1;
        if (!isValid(eidStr)) { 
            errorMessage = "Employee ID missing for deactivation."; 
            response.sendRedirect(buildRedirectUrl(request, redirectPage, -1, null, errorMessage, null));
            return; 
        }
        try {
            globalEid = Integer.parseInt(eidStr);
            if (globalEid <= 0) throw new NumberFormatException("Global EID must be positive.");
            logger.info("Attempting DEACTIVATE for T:" + tenantId + ", GlobalEID:" + globalEid);
            String sql = "UPDATE EMPLOYEE_DATA SET ACTIVE = FALSE WHERE EID = ? AND TenantID = ?";
            try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setInt(1, globalEid); ps.setInt(2, tenantId);
                int rows = ps.executeUpdate();
                if (rows > 0) { successMessage = "Employee (Global ID: " + globalEid + ") deactivated."; }
                else { errorMessage = "Employee not found for your company or already inactive."; }
            } catch (SQLException e) { logger.log(Level.SEVERE, "DB error deactivating EID:" + globalEid + " T:" + tenantId, e); errorMessage = "DB error: " + e.getMessage(); }
        } catch (NumberFormatException nfe) { logger.log(Level.WARNING, "Invalid EID for deactivation: " + eidStr, nfe); errorMessage = "Invalid Employee ID format."; }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, globalEid, successMessage, errorMessage, null)); // Pass globalEid to retain context
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            logger.warning("POST request failed: Missing TenantID.");
            response.sendRedirect(buildRedirectUrl(request, "login.jsp", -1, null, "Session error. Please log in again.", null));
            return;
        }

        String action = request.getParameter("action");
        String wizardStep = null;
        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
            wizardStep = (String) session.getAttribute("wizardStep");
        }

        switch (action != null ? action.trim() : "") {
            case "addEmployee":
                addEmployee(request, response, tenantId, wizardStep, session);
                break;
            case "editEmployee":
                editEmployee(request, response, tenantId, wizardStep, session);
                break;
            case "reactivateEmployee": 
                handleReactivateAjax(request, response, tenantId); 
                break;
            default:
                logger.warning("POST unknown action: " + action + " T:" + tenantId);
                response.sendRedirect(buildRedirectUrl(request, "employees.jsp", -1, null, "Invalid action specified.", null));
                break;
        }
    }

    private int getNextTenantEmployeeNumber(Connection con, int tenantId) throws SQLException {
       String sqlMax = "SELECT MAX(TenantEmployeeNumber) FROM EMPLOYEE_DATA WHERE TenantID = ?";
        try (PreparedStatement psMax = con.prepareStatement(sqlMax)) {
            psMax.setInt(1, tenantId);
            try (ResultSet rs = psMax.executeQuery()) {
                if (rs.next()) { return rs.getInt(1) + 1; } else { return 1; } 
            }
        }
    }

    private String processStringParam(String paramValue, String defaultValueIfExplicitMatch, boolean treatEmptyAsNull) {
        if (isEmpty(paramValue)) { 
            return treatEmptyAsNull ? null : paramValue;
        }
        if (defaultValueIfExplicitMatch != null && defaultValueIfExplicitMatch.equalsIgnoreCase(paramValue.trim())) {
            return defaultValueIfExplicitMatch; 
        }
        return paramValue.trim(); 
    }

    private void addEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId, String wizardStep, HttpSession session) throws IOException {
        String redirectPage = "employees.jsp"; 
        String successMessage = null; 
        String errorMessage = null; 
        int newGlobalEid = -1;
        String wizardRedirectAction = null;

        String firstName = request.getParameter("addFirstName");
        String lastName = request.getParameter("addLastName");
        String departmentParam = request.getParameter("addDepartmentsDropDown");
        String scheduleParam = request.getParameter("addSchedulesDropDown"); 
        String supervisor = request.getParameter("addSupervisor");
        String permissions = request.getParameter("addPermissionsDropDown");
        String address = request.getParameter("addAddress"); 
        String city = request.getParameter("addCity");
        String stateValParam = request.getParameter("addState"); 
        String zip = request.getParameter("addZip");
        String phone = request.getParameter("addPhone"); 
        String email = request.getParameter("addEmail");
        String accrualPolicyParam = request.getParameter("addAccrualsDropDown");
        String hireDateStr = request.getParameter("addHireDate");
        String workSchedule = request.getParameter("addWorkScheduleDropDown"); 
        String wageType = request.getParameter("addWageTypeDropDown");
        String wageStr = request.getParameter("addWage"); 

        if (!isValid(firstName) || !isValid(lastName) || !isValid(email) || !isValid(hireDateStr) || !isValid(wageStr)) {
            errorMessage = "First Name, Last Name, Email, Hire Date, and Wage are all required.";
            if ("addMoreEmployees".equals(wizardStep) || "editAdminProfile".equals(wizardStep)) { // Consider current step
                wizardRedirectAction = "prompt_add_employees"; // Or stay on editAdminProfile if error there
            }
            response.sendRedirect(buildRedirectUrl(request, redirectPage, -1, null, errorMessage, wizardRedirectAction));
            return;
        }
        Date hireDate;
        double wageValue;
        try {
            hireDate = Date.valueOf(LocalDate.parse(hireDateStr));
            wageValue = Double.parseDouble(wageStr);
            if (wageValue <= 0) {
                errorMessage = "Wage must be a positive number greater than 0.00.";
                 if ("addMoreEmployees".equals(wizardStep)) wizardRedirectAction = "prompt_add_employees";
                response.sendRedirect(buildRedirectUrl(request, redirectPage, -1, null, errorMessage, wizardRedirectAction));
                return;
            }
            wageValue = Math.round(wageValue * 100.0) / 100.0; 
        } 
        catch (DateTimeParseException e) {
            errorMessage = "Invalid Hire Date format. Please use YYYY-MM-DD.";
             if ("addMoreEmployees".equals(wizardStep)) wizardRedirectAction = "prompt_add_employees";
            response.sendRedirect(buildRedirectUrl(request, redirectPage, -1, null, errorMessage, wizardRedirectAction));
            return;
        }
        catch (NumberFormatException e) {
            errorMessage = "Invalid Wage format. Please enter a valid number greater than 0.00.";
             if ("addMoreEmployees".equals(wizardStep)) wizardRedirectAction = "prompt_add_employees";
            response.sendRedirect(buildRedirectUrl(request, redirectPage, -1, null, errorMessage, wizardRedirectAction));
            return;
        }

        String finalDepartment = processStringParam(departmentParam, "None", true);
        String finalSchedule = isEmpty(scheduleParam) ? null : scheduleParam.trim(); 
        String finalSupervisor = isEmpty(supervisor) || "None".equalsIgnoreCase(supervisor.trim()) ? null : supervisor.trim(); 
        String finalAccrualPolicy = processStringParam(accrualPolicyParam, "None", true);
        String finalStateVal = processStringParam(stateValParam, null, true); 
        permissions = isEmpty(permissions) ? "User" : permissions.trim();
        workSchedule = isEmpty(workSchedule) ? "Full Time" : workSchedule.trim(); 
        wageType = isEmpty(wageType) ? "Hourly" : wageType.trim();
        address = isEmpty(address) ? null : address.trim();
        city = isEmpty(city) ? null : city.trim();
        zip = isEmpty(zip) ? null : zip.trim();
        phone = isEmpty(phone) ? null : phone.trim();
        String defaultPwdPlain = "1234"; // Default PIN for new users
        String defaultPwdHash = BCrypt.hashpw(defaultPwdPlain, BCrypt.gensalt(12));

        String sqlIns = "INSERT INTO EMPLOYEE_DATA (TenantID,TenantEmployeeNumber,FIRST_NAME,LAST_NAME,DEPT,SCHEDULE,SUPERVISOR,PERMISSIONS,ADDRESS,CITY,STATE,ZIP,PHONE,EMAIL,ACCRUAL_POLICY,VACATION_HOURS,SICK_HOURS,PERSONAL_HOURS,HIRE_DATE,WORK_SCHEDULE,WAGE_TYPE,WAGE,PasswordHash,RequiresPasswordChange,ACTIVE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0.0,0.0,0.0,?,?,?,?,?,TRUE,TRUE)";
        String sqlChkEmail = "SELECT EID FROM EMPLOYEE_DATA WHERE EMAIL = ? AND TenantID = ?"; 
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection(); 
            con.setAutoCommit(false);
            
            try (PreparedStatement psChkE = con.prepareStatement(sqlChkEmail)) { 
                psChkE.setString(1,email.trim().toLowerCase()); 
                psChkE.setInt(2, tenantId); 
                try(ResultSet rsE = psChkE.executeQuery()){ 
                    if(rsE.next()){ 
                        rollback(con);
                        errorMessage="Email address '" + email.trim() + "' is already registered for an employee in this company."; 
                        if ("addMoreEmployees".equals(wizardStep)) wizardRedirectAction = "prompt_add_employees"; 
                        response.sendRedirect(buildRedirectUrl(request, redirectPage,-1,null,errorMessage, wizardRedirectAction));
                        return;
                    }
                }
            }
            
            int tenEmpNo = getNextTenantEmployeeNumber(con, tenantId);
            try (PreparedStatement psAdd = con.prepareStatement(sqlIns, Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                psAdd.setInt(i++, tenantId); psAdd.setInt(i++, tenEmpNo); 
                psAdd.setString(i++, firstName.trim()); psAdd.setString(i++, lastName.trim());
                if (finalDepartment != null) psAdd.setString(i++, finalDepartment); else psAdd.setNull(i++, Types.VARCHAR);
                if (finalSchedule != null) psAdd.setString(i++, finalSchedule); else psAdd.setNull(i++, Types.VARCHAR);
                if (finalSupervisor != null) psAdd.setString(i++, finalSupervisor); else psAdd.setNull(i++, Types.VARCHAR);
                psAdd.setString(i++, permissions);
                if (address != null) psAdd.setString(i++, address); else psAdd.setNull(i++, Types.VARCHAR);
                if (city != null) psAdd.setString(i++, city); else psAdd.setNull(i++, Types.VARCHAR);
                if (finalStateVal != null) psAdd.setString(i++, finalStateVal); else psAdd.setNull(i++, Types.VARCHAR);
                if (zip != null) psAdd.setString(i++, zip); else psAdd.setNull(i++, Types.VARCHAR);
                if (phone != null) psAdd.setString(i++, phone); else psAdd.setNull(i++, Types.VARCHAR);
                psAdd.setString(i++, email.trim().toLowerCase());
                if (finalAccrualPolicy != null) psAdd.setString(i++, finalAccrualPolicy); else psAdd.setNull(i++, Types.VARCHAR);
                psAdd.setDate(i++, hireDate); 
                psAdd.setString(i++, workSchedule); 
                psAdd.setString(i++, wageType);
                psAdd.setDouble(i++, wageValue); 
                psAdd.setString(i++, defaultPwdHash);
                
                if (psAdd.executeUpdate() > 0) {
                    try (ResultSet gK = psAdd.getGeneratedKeys()) { 
                        if (gK.next()) newGlobalEid = gK.getInt(1); 
                        else throw new SQLException("Add employee failed, no ID obtained.");
                    }
                    successMessage = "Employee " + firstName.trim() + " " + lastName.trim() + " (ID:" + tenEmpNo + ") added. Default PIN: " + defaultPwdPlain + ".";
                    logger.info("Added EID:" + newGlobalEid + ", TenantEmployeeNumber:" + tenEmpNo + " for TenantID:" + tenantId);
                    
                    if ("addMoreEmployees".equals(wizardStep) && session != null) {
                        session.setAttribute("employeeJustAddedInWizardName", firstName.trim() + " " + lastName.trim());
                        logger.info("[AddEmpServlet] Set session 'employeeJustAddedInWizardName' for wizardStep: " + wizardStep);
                    } else if (session != null) {
                        session.removeAttribute("employeeJustAddedInWizardName");
                    }
                } else { 
                    throw new SQLException("Add employee failed, no rows affected."); 
                }
                con.commit();
            } catch (SQLException e) { 
                rollback(con); 
                logger.log(Level.SEVERE, "Error adding employee T:" + tenantId + ". Details: " + e.getMessage(), e); 
                errorMessage = "Database Add Error: " + e.getMessage(); 
            }
        } catch (SQLException e) { 
            logger.log(Level.SEVERE, "DB connection/transaction error add employee T:" + tenantId, e); 
            errorMessage = "Database Error: " + e.getMessage(); 
        }
        finally { 
            if (con != null) { 
                try { 
                    if (!con.isClosed() && !con.getAutoCommit()) con.setAutoCommit(true); 
                    con.close(); 
                } catch (SQLException ex) { 
                    logger.log(Level.WARNING, "Error closing connection", ex); 
                }
            }
        }
        
        if ("addMoreEmployees".equals(wizardStep)) { 
            wizardRedirectAction = "prompt_add_employees"; 
        }
        // After adding, redirect including newGlobalEid so JS can highlight
        response.sendRedirect(buildRedirectUrl(request, redirectPage, newGlobalEid, successMessage, errorMessage, wizardRedirectAction));
    }

    private void editEmployee(HttpServletRequest request, HttpServletResponse response, int tenantId, String wizardStep, HttpSession session) throws IOException {
        String redirectPage = "employees.jsp"; 
        String successMessage = null; 
        String errorMessage = null; 
        int globalEID = -1;
        String wizardRedirectAction = null; 

        String eidStr = request.getParameter("eid"); 
        if (isValid(eidStr)) { try { globalEID = Integer.parseInt(eidStr); } catch (NumberFormatException e) { logger.warning("Invalid EID format in editEmployee: " + eidStr); globalEID = -1; } }
        else { logger.warning("EID is missing in editEmployee request."); globalEID = -1; }

        String fn = request.getParameter("firstName"); String ln = request.getParameter("lastName");
        String email = request.getParameter("email");
        String hdS = request.getParameter("hireDate"); 
        String wgS = request.getParameter("wage"); 
        // ... (retrieve all other form parameters as in your original method)
        String departmentParam = request.getParameter("departmentsDropDown");
        String scheduleParam = request.getParameter("schedulesDropDown"); 
        String sup = request.getParameter("supervisor"); 
        String perm = request.getParameter("permissionsDropDown");
        String addr = request.getParameter("address"); String cityParam = request.getParameter("city");
        String stValParam = request.getParameter("state"); String zipParam = request.getParameter("zip");
        String phParam = request.getParameter("phone"); 
        String accPParam = request.getParameter("accrualsDropDown");
        String workScheduleParam = request.getParameter("workScheduleDropDown"); 
        String wageTypeParam = request.getParameter("wageTypeDropDown"); 

        if (globalEID <=0 || !isValid(fn) || !isValid(ln) || !isValid(email) || !isValid(hdS) || !isValid(wgS) ) {
            errorMessage = "Employee ID, Name, Email, Hire Date, and Wage are required for editing.";
            if ("editAdminProfile".equals(wizardStep)) wizardRedirectAction = "edit_admin_profile"; 
            response.sendRedirect(buildRedirectUrl(request, redirectPage, globalEID, null, errorMessage, wizardRedirectAction));
            return;
        }
        Date hireDate; double wageValue; 
        try {
            hireDate = Date.valueOf(LocalDate.parse(hdS));
            wageValue = Double.parseDouble(wgS);
            if (wageValue <= 0) throw new NumberFormatException("Wage must be a positive value greater than 0.00.");
            wageValue = Math.round(wageValue * 100.0) / 100.0;
        } catch (DateTimeParseException | NumberFormatException e) {
            errorMessage = "Invalid Date or Wage format/value. Wage must be greater than 0.00.";
            if (e.getMessage() != null && e.getMessage().contains("Wage must be a positive value greater than 0.00")) {
                errorMessage = "Wage must be a number greater than 0.00.";
            }
            if ("editAdminProfile".equals(wizardStep)) wizardRedirectAction = "edit_admin_profile";
            response.sendRedirect(buildRedirectUrl(request, redirectPage, globalEID, null, errorMessage, wizardRedirectAction));
            return;
        }

        String finalDept = processStringParam(departmentParam, "None", true);
        String finalSched = isEmpty(scheduleParam) ? null : scheduleParam.trim(); 
        String finalSup = isEmpty(sup) || "None".equalsIgnoreCase(sup.trim()) ? null : sup.trim(); 
        String finalAccP = processStringParam(accPParam, "None", true);
        String finalStVal = processStringParam(stValParam, null, true);
        String finalWorkSched = isEmpty(workScheduleParam) ? "Full Time" : workScheduleParam.trim(); 
        String finalWageType = isEmpty(wageTypeParam) ? "Hourly" : wageTypeParam.trim();
        perm = isEmpty(perm) ? "User" : perm.trim(); 
        addr = isEmpty(addr) ? null : addr.trim();
        cityParam = isEmpty(cityParam) ? null : cityParam.trim();
        zipParam = isEmpty(zipParam) ? null : zipParam.trim();
        phParam = isEmpty(phParam) ? null : phParam.trim();
        String newEmailClean = email.trim().toLowerCase();

        String sqlUpd = "UPDATE EMPLOYEE_DATA SET FIRST_NAME=?,LAST_NAME=?,DEPT=?,SCHEDULE=?,SUPERVISOR=?,PERMISSIONS=?,ADDRESS=?,CITY=?,STATE=?,ZIP=?,PHONE=?,EMAIL=?,ACCRUAL_POLICY=?,HIRE_DATE=?,WORK_SCHEDULE=?,WAGE_TYPE=?,WAGE=? WHERE EID=? AND TenantID=?";
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection(); con.setAutoCommit(false);
            String currentEmailFromDB = ""; 
            try (PreparedStatement psGetCurrentEmail = con.prepareStatement("SELECT EMAIL FROM EMPLOYEE_DATA WHERE EID = ? AND TenantID = ?")) {
                psGetCurrentEmail.setInt(1, globalEID); psGetCurrentEmail.setInt(2, tenantId);
                try (ResultSet rs = psGetCurrentEmail.executeQuery()) {
                    if (rs.next()) { currentEmailFromDB = rs.getString("EMAIL"); }
                    else { throw new SQLException("Employee to edit (EID: " + globalEID + ") not found for this tenant (" + tenantId + ")."); }
                }
            }
            if (!newEmailClean.equalsIgnoreCase(currentEmailFromDB)) {
                String sqlCheckEmail = "SELECT EID FROM EMPLOYEE_DATA WHERE EMAIL = ? AND EID <> ? AND TenantID = ?";
                try (PreparedStatement psCheck = con.prepareStatement(sqlCheckEmail)) {
                    psCheck.setString(1, newEmailClean); psCheck.setInt(2, globalEID); psCheck.setInt(3, tenantId);
                    try (ResultSet rsCheck = psCheck.executeQuery()) {
                        if (rsCheck.next()) { throw new SQLException("The new email address '" + newEmailClean + "' is already in use by another employee in this company."); }
                    }
                }
            }

            try (PreparedStatement psU = con.prepareStatement(sqlUpd)) {
                int i = 1;
                psU.setString(i++, fn.trim()); psU.setString(i++, ln.trim());
                if (finalDept != null) psU.setString(i++, finalDept); else psU.setNull(i++, Types.VARCHAR);
                if (finalSched != null) psU.setString(i++, finalSched); else psU.setNull(i++, Types.VARCHAR);
                if (finalSup != null) psU.setString(i++, finalSup); else psU.setNull(i++, Types.VARCHAR);
                psU.setString(i++, perm);
                if (addr != null) psU.setString(i++, addr); else psU.setNull(i++, Types.VARCHAR);
                if (cityParam != null) psU.setString(i++, cityParam); else psU.setNull(i++, Types.VARCHAR);
                if (finalStVal != null) psU.setString(i++, finalStVal); else psU.setNull(i++, Types.VARCHAR);
                if (zipParam != null) psU.setString(i++, zipParam); else psU.setNull(i++, Types.VARCHAR);
                if (phParam != null) psU.setString(i++, phParam); else psU.setNull(i++, Types.VARCHAR);
                psU.setString(i++, newEmailClean);
                if (finalAccP != null) psU.setString(i++, finalAccP); else psU.setNull(i++, Types.VARCHAR);
                psU.setDate(i++, hireDate); 
                psU.setString(i++, finalWorkSched); 
                psU.setString(i++, finalWageType);
                psU.setDouble(i++, wageValue); 
                psU.setInt(i++, globalEID); psU.setInt(i++, tenantId);

                int rowsAffected = psU.executeUpdate();
                if (rowsAffected > 0) { 
                    successMessage = "Employee (Global ID:" + globalEID + ") updated successfully.";
                    logger.info("Updated EID:" + globalEID + " T:" + tenantId);
                    if ("editAdminProfile".equals(wizardStep) && session != null) { 
                        session.setAttribute("wizardStep", "addMoreEmployees"); // Set next logical step
                        session.removeAttribute("admin_profile_intro_shown_employees_wizard"); 
                        wizardRedirectAction = "prompt_add_employees"; // Action for employees.jsp to show correct modal
                        successMessage += " Your profile is updated. Next: add other employees or complete setup.";
                        logger.info("Wizard: Admin profile EID " + globalEID + " updated. Session wizardStep now 'addMoreEmployees'. Redirecting to prompt_add_employees.");
                    }
                } else { 
                    errorMessage = "Employee not found for your company or no changes made."; 
                    if ("editAdminProfile".equals(wizardStep)) wizardRedirectAction = "edit_admin_profile"; // Stay on edit if failed
                }
                con.commit();
            }
        } catch (SQLException e) {
            rollback(con); logger.log(Level.SEVERE, "Error editing employee EID:" + globalEID + " T:" + tenantId, e);
            errorMessage = "Database Edit Error: " + e.getMessage();
            if ("editAdminProfile".equals(wizardStep)) wizardRedirectAction = "edit_admin_profile";
        } finally {
            if (con != null) { try { if (!con.isClosed() && !con.getAutoCommit()) con.setAutoCommit(true); con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); }}
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, globalEID, successMessage, errorMessage, wizardRedirectAction));
    }

    private void handleReactivateAjax(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        // ... (Your existing method - it uses writeJsonResponse, no redirects to update) ...
        String eidStr = request.getParameter("eid");
        logger.info("Reactivate attempt for T:" + tenantId + ", EID_Param:" + eidStr);
        int gEID = -1; String msg = null; boolean suc = false; int statC = HttpServletResponse.SC_OK;
        try {
            if (!isValid(eidStr)) throw new IllegalArgumentException("Missing Employee ID for reactivation.");
            gEID = Integer.parseInt(eidStr.trim());
            if (gEID <= 0) throw new IllegalArgumentException("Invalid Employee ID for reactivation.");

            String sqlR = "UPDATE EMPLOYEE_DATA SET ACTIVE=TRUE WHERE EID=? AND TenantID=? AND ACTIVE=FALSE";
            try (Connection con = DatabaseConnection.getConnection(); PreparedStatement psR = con.prepareStatement(sqlR)) {
                psR.setInt(1, gEID); psR.setInt(2, tenantId);
                int rA = psR.executeUpdate();
                if (rA > 0) { msg = "Employee (Global ID:" + gEID + ") reactivated successfully."; suc = true; logger.info(msg + " T:" + tenantId); }
                else { msg = "Employee (Global ID:" + gEID + ") not reactivated. May not be found, already active, or invalid tenant."; suc = false; logger.warning(msg + " T:" + tenantId); }
            } catch (SQLException e) { logger.log(Level.SEVERE, "Database error during reactivation of EID:" + gEID + " T:" + tenantId, e); msg = "Database error: " + e.getMessage(); statC = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; suc = false; }
        } catch (IllegalArgumentException e) { logger.log(Level.WARNING, "Input error during reactivation for T:" + tenantId + ": " + e.getMessage()); msg = "Error: " + e.getMessage(); statC = HttpServletResponse.SC_BAD_REQUEST; suc = false;
        } catch (Exception e) { logger.log(Level.SEVERE, "Unexpected error during reactivation for EID: " + (gEID > 0 ? gEID : eidStr) + ", T:" + tenantId, e); msg = "An unexpected server error occurred."; statC = HttpServletResponse.SC_INTERNAL_SERVER_ERROR; suc = false; }
        writeJsonResponse(response, suc, msg, statC);
    }
}