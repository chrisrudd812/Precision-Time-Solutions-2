package timeclock.employees;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.db.DatabaseConnection;
import timeclock.payroll.ShowPayroll;

import java.io.IOException;
import java.net.URLEncoder; // Added for encoding messages
import java.nio.charset.StandardCharsets; // Added for encoding messages
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/AddEditAndDeleteEmployeesServlet")
public class AddEditAndDeleteEmployeesServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddEditAndDeleteEmployeesServlet.class.getName());

    // --- doGet remains for Delete ---
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String eidStr = request.getParameter("eid"); // Get EID from hidden input name 'eid'
        String redirectPage = "employees.jsp";

        // --- Use helper for consistent parameter encoding ---
        String successMessage = null;
        String errorMessage = null;

        if (eidStr == null || eidStr.trim().isEmpty()) {
            errorMessage = "Missing Employee ID for deletion.";
            response.sendRedirect(buildRedirectUrl(redirectPage, -1, null, errorMessage)); // Use -1 for EID if invalid
            return;
        }

        int eid = -1; // Initialize eid for redirect helper even on format error
        try {
            eid = Integer.parseInt(eidStr);

            try (Connection con = DatabaseConnection.getConnection();
                 PreparedStatement psDeleteEmployee = con.prepareStatement("DELETE FROM EMPLOYEE_DATA WHERE EID = ?");) {

                psDeleteEmployee.setInt(1, eid);
                int rowsAffected = psDeleteEmployee.executeUpdate();

                if (rowsAffected > 0) {
                    logger.info("Successfully deleted employee EID: " + eid);
                    successMessage = "Employee " + eid + " deleted successfully.";
                } else {
                    logger.warning("Failed to delete employee EID: " + eid + " (not found).");
                    errorMessage = "Employee not found or could not be deleted.";
                }

            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error deleting employee with EID: " + eid, e);
                errorMessage = "Database error during deletion: " + e.getMessage();
            }
        } catch (NumberFormatException nfe) {
            logger.log(Level.WARNING, "Invalid EID format for deletion: " + eidStr, nfe);
            errorMessage = "Invalid Employee ID format.";
            eid = -1; // Ensure eid is invalid for redirect
        }
        // Redirect at the end
        response.sendRedirect(buildRedirectUrl(redirectPage, eid, successMessage, errorMessage));

    } // End doGet

    // --- doPost now routes based on hidden parameter ---
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding(StandardCharsets.UTF_8.name()); // Ensure encoding

        if (request.getParameter("addEmployee") != null) {
            addEmployee(request, response);
        } else if (request.getParameter("editEmployee") != null) {
            editEmployee(request, response);
        } else {
            logger.warning("doPost called without addEmployee or editEmployee parameter.");
            response.sendRedirect(buildRedirectUrl("employees.jsp", -1, null, "Invalid action specified.")); // Use helper
        }
    }

    // --- Add Employee Logic ---
    private void addEmployee(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String redirectPage = "employees.jsp";
        String successMessage = null;
        String errorMessage = null;
        int newEid = -1; // To potentially select the new employee after adding

        // Parameter retrieval
        String firstName = request.getParameter("addFirstName"); String lastName = request.getParameter("addLastName"); String department = request.getParameter("addDepartmentsDropDown"); String schedule = request.getParameter("addSchedulesDropDown"); String permissions = request.getParameter("addPermissionsDropDown"); String address = request.getParameter("addAddress"); String city = request.getParameter("addCity"); String state = request.getParameter("addState"); String zip = request.getParameter("addZip"); String phone = request.getParameter("addPhone"); String email = request.getParameter("addEmail"); String accrualPolicy = request.getParameter("addAccrualsDropDown"); String hd = request.getParameter("addHireDate"); String workSchedule = request.getParameter("addWorkScheduleDropDown"); String wageType = request.getParameter("addWageTypeDropDown"); String wg = request.getParameter("addWage");

        // Basic Validation
        if (!ShowPayroll.isValid(firstName) || !ShowPayroll.isValid(lastName) || !ShowPayroll.isValid(wg)) { // Reuse helper
            errorMessage = "First Name, Last Name, and Wage are required.";
            response.sendRedirect(buildRedirectUrl(redirectPage, -1, null, errorMessage));
            return;
        }

        Date hireDate = null; double wage = 0.0;
        try { // Parse Date and Wage
            if (hd != null && !hd.isEmpty()) { hireDate = Date.valueOf(hd); }
            wage = Double.parseDouble(wg); if (wage < 0) throw new NumberFormatException("Wage cannot be negative");
        } catch (IllegalArgumentException e) { errorMessage = "Invalid format for Hire Date or Wage."; logger.log(Level.WARNING, errorMessage, e); response.sendRedirect(buildRedirectUrl(redirectPage, -1, null, errorMessage)); return; }

        // Null checks/defaults
        address = (address != null) ? address.trim() : ""; city = (city != null) ? city.trim() : ""; state = (state != null) ? state.trim() : ""; zip = (zip != null) ? zip.trim() : ""; phone = (phone != null) ? phone.trim() : ""; email = (email != null) ? email.trim() : ""; department = (department != null && !department.isEmpty()) ? department : "None"; schedule = (schedule != null && !schedule.isEmpty()) ? schedule : "Open"; accrualPolicy = (accrualPolicy != null && !accrualPolicy.isEmpty()) ? accrualPolicy : "None"; permissions = (permissions != null && !permissions.isEmpty()) ? permissions : "User"; workSchedule = (workSchedule != null && !workSchedule.isEmpty()) ? workSchedule : "Full Time"; wageType = (wageType != null && !wageType.isEmpty()) ? wageType : "Hourly";

        // Database Insert - Use RETURN_GENERATED_KEYS to get the new EID
        String sqlInsert = "INSERT INTO EMPLOYEE_DATA (FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, PERMISSIONS, ADDRESS, CITY, STATE, ZIP, PHONE, EMAIL, ACCRUAL_POLICY, HIRE_DATE, WORK_SCHEDULE, WAGE_TYPE, WAGE, ACTIVE) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?, TRUE)";

        try(Connection con = DatabaseConnection.getConnection();
            PreparedStatement psAddEmployee = con.prepareStatement(sqlInsert, Statement.RETURN_GENERATED_KEYS);) { // Request generated keys

            psAddEmployee.setString(1, firstName.trim()); psAddEmployee.setString(2, lastName.trim()); psAddEmployee.setString(3, department); psAddEmployee.setString(4, schedule); psAddEmployee.setString(5, permissions); psAddEmployee.setString(6, address); psAddEmployee.setString(7, city); psAddEmployee.setString(8, state); psAddEmployee.setString(9, zip); psAddEmployee.setString(10, phone); psAddEmployee.setString(11, email); psAddEmployee.setString(12, accrualPolicy); psAddEmployee.setDate(13, hireDate); psAddEmployee.setString(14, workSchedule); psAddEmployee.setString(15, wageType); psAddEmployee.setDouble(16, wage);

            int rowsAffected = psAddEmployee.executeUpdate();

            if (rowsAffected > 0) {
                // Retrieve the generated EID
                try (ResultSet generatedKeys = psAddEmployee.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        newEid = generatedKeys.getInt(1); // Get the first generated key (EID)
                        logger.info("Successfully added employee. New EID: " + newEid);
                        successMessage = "Employee added successfully (EID: " + newEid + ").";
                    } else {
                         logger.warning("Employee added, but could not retrieve generated EID.");
                         successMessage = "Employee added successfully (EID unknown).";
                         newEid = -1; // Indicate unknown EID for redirect
                    }
                }
            } else {
                errorMessage = "Employee was not added. Unexpected database error.";
                logger.warning(errorMessage);
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error adding employee", e);
            if (e.getSQLState().startsWith("23")) { errorMessage = "Database constraint error (perhaps duplicate data?): " + e.getMessage(); }
            else { errorMessage = "Database error adding employee: " + e.getMessage(); }
            newEid = -1; // Ensure no EID on error
        }
        // Redirect at the end
        response.sendRedirect(buildRedirectUrl(redirectPage, newEid, successMessage, errorMessage));

    } // End addEmployee

    // --- Edit Employee Logic ---
    private void editEmployee(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String redirectPage = "employees.jsp";
        String successMessage = null;
        String errorMessage = null;
        int eid = -1; // Initialize EID for redirect

        // Parameter retrieval
        String eidStr = request.getParameter("eid"); String firstName = request.getParameter("firstName"); String lastName = request.getParameter("lastName"); String department = request.getParameter("departmentsDropDown"); String schedule = request.getParameter("schedulesDropDown"); String permissions = request.getParameter("permissionsDropDown"); String address = request.getParameter("address"); String city = request.getParameter("city"); String state = request.getParameter("state"); String zip = request.getParameter("zip"); String phone = request.getParameter("phone"); String email = request.getParameter("email"); String accrualPolicy = request.getParameter("accrualsDropDown"); String hd = request.getParameter("hireDate"); String workSchedule = request.getParameter("workScheduleDropDown"); String wageType = request.getParameter("wageTypeDropDown"); String wg = request.getParameter("wage");

        // Validate required fields (EID is crucial)
        if (!ShowPayroll.isValid(eidStr) || !ShowPayroll.isValid(firstName) || !ShowPayroll.isValid(lastName) || !ShowPayroll.isValid(wg)) {
             errorMessage = "EID, First Name, Last Name, and Wage are required for update.";
             response.sendRedirect(buildRedirectUrl(redirectPage, -1, null, errorMessage)); // EID unknown/invalid
             return;
        }

        Date hireDate = null; double wage = 0.0;
        try { // Parse EID, Date and Wage
            eid = Integer.parseInt(eidStr);
            if (hd != null && !hd.isEmpty()) { hireDate = Date.valueOf(hd); }
            wage = Double.parseDouble(wg); if (wage < 0) throw new NumberFormatException("Wage cannot be negative");
        } catch (IllegalArgumentException e) { errorMessage = "Invalid format for EID, Hire Date or Wage."; logger.log(Level.WARNING, errorMessage, e); response.sendRedirect(buildRedirectUrl(redirectPage, eid, null, errorMessage)); return; } // Use parsed EID if possible

        // Null checks/defaults
        address = (address != null) ? address.trim() : ""; city = (city != null) ? city.trim() : ""; state = (state != null) ? state.trim() : ""; zip = (zip != null) ? zip.trim() : ""; phone = (phone != null) ? phone.trim() : ""; email = (email != null) ? email.trim() : ""; department = (department != null && !department.isEmpty()) ? department : "None"; schedule = (schedule != null && !schedule.isEmpty()) ? schedule : "Open"; accrualPolicy = (accrualPolicy != null && !accrualPolicy.isEmpty()) ? accrualPolicy : "None"; permissions = (permissions != null && !permissions.isEmpty()) ? permissions : "User"; workSchedule = (workSchedule != null && !workSchedule.isEmpty()) ? workSchedule : "Full Time"; wageType = (wageType != null && !wageType.isEmpty()) ? wageType : "Hourly";

        // Database Update
        String sqlUpdate = "UPDATE EMPLOYEE_DATA SET FIRST_NAME = ?, LAST_NAME = ?, DEPT = ?, SCHEDULE = ?, PERMISSIONS = ?, ADDRESS = ?, CITY = ?, STATE = ?, ZIP = ?, PHONE = ?, EMAIL = ?, ACCRUAL_POLICY = ?, HIRE_DATE = ?, WORK_SCHEDULE = ?, WAGE_TYPE = ?, WAGE = ? WHERE EID = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement psUpdateEmployee = con.prepareStatement(sqlUpdate)) {

            psUpdateEmployee.setString(1, firstName.trim()); psUpdateEmployee.setString(2, lastName.trim()); psUpdateEmployee.setString(3, department); psUpdateEmployee.setString(4, schedule); psUpdateEmployee.setString(5, permissions); psUpdateEmployee.setString(6, address); psUpdateEmployee.setString(7, city); psUpdateEmployee.setString(8, state); psUpdateEmployee.setString(9, zip); psUpdateEmployee.setString(10, phone); psUpdateEmployee.setString(11, email); psUpdateEmployee.setString(12, accrualPolicy); psUpdateEmployee.setDate(13, hireDate); psUpdateEmployee.setString(14, workSchedule); psUpdateEmployee.setString(15, wageType); psUpdateEmployee.setDouble(16, wage);
            psUpdateEmployee.setInt(17, eid); // WHERE clause parameter

            int rowsAffected = psUpdateEmployee.executeUpdate();

            if (rowsAffected > 0) {
                logger.info("Successfully updated employee EID: " + eid);
                successMessage = "Employee " + eid + " updated successfully.";
            } else {
                 logger.warning("Failed to update employee EID: " + eid + " (not found or no changes made).");
                errorMessage = "Employee not found or no changes were made.";
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error updating employee EID: " + eid, e);
            if (e.getSQLState().startsWith("23")) { errorMessage = "Database constraint error during update: " + e.getMessage(); }
            else { errorMessage = "Database error during update: " + e.getMessage(); }
        }
        // Redirect at the end
        response.sendRedirect(buildRedirectUrl(redirectPage, eid, successMessage, errorMessage));

    } // End editEmployee

    // --- Helper method for consistent redirects ---
    private String buildRedirectUrl(String page, int eid, String message, String error) throws IOException {
        StringBuilder url = new StringBuilder(page);
        boolean firstParam = true;

        if (eid > 0) { // Add EID if valid to potentially re-select the row
            url.append(firstParam ? "?" : "&").append("eid=").append(eid);
            firstParam = false;
        }

        if (message != null && !message.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("message=").append(encodeUrlParam(message));
            firstParam = false;
        } else if (error != null && !error.isEmpty()) {
             url.append(firstParam ? "?" : "&").append("error=").append(encodeUrlParam(error));
             firstParam = false;
        }
        logger.fine("Redirecting to: " + url.toString());
        return url.toString();
    }

    /** Helper for URL encoding */
    private String encodeUrlParam(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
    }

} // End Servlet