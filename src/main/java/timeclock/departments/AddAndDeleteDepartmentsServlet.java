package timeclock.departments;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/AddAndDeleteDepartmentsServlet")
public class AddAndDeleteDepartmentsServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddAndDeleteDepartmentsServlet.class.getName());

    private Integer getTenantIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object tenantIdObj = session.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) {
            return (Integer) tenantIdObj;
        }
        return null;
    }

    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }
    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    private String encodeUrlParam(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private String buildRedirectUrl(HttpServletRequest request, String page, String successMessage, String errorMessage, String wizardNextAction, boolean itemAddedInWizard) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page); // Use contextPath
        boolean firstParam = true;

        if (isValid(wizardNextAction)) {
            url.append(firstParam ? "?" : "&").append("setup_wizard=true");
            firstParam = false;
            url.append("&wizardStep=").append(encodeUrlParam(wizardNextAction));
        }
        
        if (itemAddedInWizard) {
            if ("departments.jsp".equals(page)) {
                 url.append(firstParam ? "?" : "&").append("deptAdded=true");
            } else if ("scheduling.jsp".equals(page)) {
                 url.append(firstParam ? "?" : "&").append("scheduleAdded=true");
            } else if ("accruals.jsp".equals(page)) {
                 url.append(firstParam ? "?" : "&").append("accrualAdded=true");
            } else if ("employees.jsp".equals(page)) {
                url.append(firstParam ? "?" : "&").append("employeeAdded=true"); 
            }
            firstParam = false; 
        }

        if (errorMessage != null && !errorMessage.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("error=").append(encodeUrlParam(errorMessage));
            firstParam = false;
        } else if (successMessage != null && !successMessage.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("message=").append(encodeUrlParam(successMessage));
        }
        logger.info("[AddAndDeleteDepartmentsServlet] Redirecting to: " + url.toString());
        return url.toString();
    }
    
    private String buildRedirectUrl(HttpServletRequest request, String page, String successMessage, String errorMessage) throws IOException {
        return buildRedirectUrl(request, page, successMessage, errorMessage, null, false);
    }


    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            logger.warning("POST request failed: Missing TenantID in session for AddAndDeleteDepartmentsServlet.");
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeUrlParam("Session error. Please log in again."));
            return;
        }
        
        String userPermissions = (String) session.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            response.sendRedirect(buildRedirectUrl(request, "departments.jsp", null, "Access Denied."));
            return;
        }

        String action = request.getParameter("action");
        logger.info("[AddAndDeleteDepartmentsServlet] Action received: " + action + " for TenantID: " + tenantId);
        
        boolean isWizardModeActive = (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard")));
        String currentWizardStepInSession = isWizardModeActive ? (String) session.getAttribute("wizardStep") : null;

        switch (action != null ? action.trim() : "") {
            case "addDepartment":
                addDepartment(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            case "editDepartment":
                editDepartment(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            case "deleteAndReassignDepartment":
                deleteAndReassignDepartment(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            default:
                logger.warning("POST unknown action in AddAndDeleteDepartmentsServlet: " + action + " T:" + tenantId);
                response.sendRedirect(buildRedirectUrl(request, "departments.jsp", null, "Invalid action specified."));
                break;
        }
    }

    private void addDepartment(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session, 
                               boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String redirectPage = "departments.jsp";
        String successMessage = null;
        String errorMessage = null;
        boolean itemAddedInWizardFlow = false;
        String wizardRedirectNextStep = null;

        String departmentName = request.getParameter("addDepartmentName");
        String description = request.getParameter("addDescription");
        String supervisor = request.getParameter("addSupervisor");

        if (!isValid(departmentName)) {
            errorMessage = "Department Name is required.";
        } else {
            Connection con = null;
            try {
                con = DatabaseConnection.getConnection();
                con.setAutoCommit(false);

                String checkSql = "SELECT NAME FROM DEPARTMENTS WHERE TenantID = ? AND LOWER(NAME) = LOWER(?)";
                try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                    psCheck.setInt(1, tenantId);
                    psCheck.setString(2, departmentName.trim().toLowerCase());
                    try (ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next()) {
                            errorMessage = "Department '" + departmentName.trim() + "' already exists.";
                        }
                    }
                }

                if (errorMessage == null) {
                    String insertSql = "INSERT INTO DEPARTMENTS (TenantID, NAME, DESCRIPTION, SUPERVISOR) VALUES (?, ?, ?, ?)";
                    try (PreparedStatement psInsert = con.prepareStatement(insertSql)) {
                        psInsert.setInt(1, tenantId);
                        psInsert.setString(2, departmentName.trim());
                        psInsert.setString(3, isEmpty(description) ? null : description.trim());
                        psInsert.setString(4, isEmpty(supervisor) || "N/A".equalsIgnoreCase(supervisor.trim()) ? null : supervisor.trim());
                        
                        int rowsAffected = psInsert.executeUpdate();
                        if (rowsAffected > 0) {
                            con.commit();
                            successMessage = "Department '" + departmentName.trim() + "' added successfully.";
                            logger.info(successMessage + " For TenantID: " + tenantId);

                            if (isWizardModeActive && currentWizardStepInSession != null && 
                                (currentWizardStepInSession.equals("departments") || currentWizardStepInSession.equals("departments_initial") || currentWizardStepInSession.equals("departments_after_add"))) {
                                itemAddedInWizardFlow = true;
                                wizardRedirectNextStep = "departments"; // JS will use deptAdded=true to show "add another"
                                if(session != null) session.setAttribute("wizardStep", wizardRedirectNextStep);
                                logger.info("[AddAndDeleteDepartmentsServlet] Department added during wizard. Next step (for JS): " + wizardRedirectNextStep);
                            }
                        } else {
                            con.rollback();
                            errorMessage = "Failed to add department. No rows affected.";
                        }
                    }
                } else {
                    if (con != null && !con.getAutoCommit()) con.rollback(); // Rollback if name existed
                }
            } catch (SQLException e) {
                if(con != null) try { if(!con.getAutoCommit()) con.rollback();} catch (SQLException ex) {logger.log(Level.WARNING, "Rollback failed", ex);}
                logger.log(Level.SEVERE, "Database error adding department for T:" + tenantId, e);
                errorMessage = "Database error: " + e.getMessage();
            } catch (Exception e) {
                 if(con != null) try { if(!con.getAutoCommit()) con.rollback();} catch (SQLException ex) {logger.log(Level.WARNING, "Rollback failed", ex);}
                logger.log(Level.SEVERE, "Unexpected error adding department for T:" + tenantId, e);
                errorMessage = "Unexpected server error: " + e.getMessage();
            } finally {
                if (con != null) { try { if (!con.isClosed()) con.setAutoCommit(true); con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); }}
            }
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, successMessage, errorMessage, wizardRedirectNextStep, itemAddedInWizardFlow));
    }

    private void editDepartment(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session,
                                boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String redirectPage = "departments.jsp";
        String successMessage = null;
        String errorMessage = null;
        String wizardRedirectNextStep = isWizardModeActive ? currentWizardStepInSession : null;

        String originalDepartmentName = request.getParameter("originalDepartmentName");
        String newDescription = request.getParameter("editDescription");
        String newSupervisor = request.getParameter("editSupervisor");

        if (!isValid(originalDepartmentName)) {
            errorMessage = "Original department name is missing. Cannot edit.";
        } else if (originalDepartmentName.equalsIgnoreCase("None")) {
            errorMessage = "The default 'None' department cannot be edited.";
        } else {
            Connection con = null;
            try {
                con = DatabaseConnection.getConnection();
                // Editing doesn't usually change transaction state for a single update unless complex logic is involved
                // con.setAutoCommit(true); // Default, usually fine for single updates

                String updateSql = "UPDATE DEPARTMENTS SET DESCRIPTION = ?, SUPERVISOR = ? WHERE TenantID = ? AND NAME = ?";
                try (PreparedStatement psUpdate = con.prepareStatement(updateSql)) {
                    psUpdate.setString(1, isEmpty(newDescription) ? null : newDescription.trim());
                    psUpdate.setString(2, isEmpty(newSupervisor) || "N/A".equalsIgnoreCase(newSupervisor.trim()) ? null : newSupervisor.trim());
                    psUpdate.setInt(3, tenantId);
                    psUpdate.setString(4, originalDepartmentName);

                    int rowsAffected = psUpdate.executeUpdate();
                    if (rowsAffected > 0) {
                        successMessage = "Department '" + originalDepartmentName + "' updated successfully.";
                        // No change to itemAddedInWizardFlow or wizardRedirectNextStep for edit in this simple case
                    } else {
                        errorMessage = "Department '" + originalDepartmentName + "' not found or no changes made.";
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database error editing department '" + originalDepartmentName + "' for T:" + tenantId, e);
                errorMessage = "Database error: " + e.getMessage();
            } finally {
                if (con != null) { try { if (!con.isClosed()) con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); }}
            }
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, successMessage, errorMessage, wizardRedirectNextStep, false));
    }

    private void deleteAndReassignDepartment(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session,
                                             boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String redirectPage = "departments.jsp";
        String successMessage = null;
        String errorMessage = null;
        String wizardRedirectNextStep = isWizardModeActive ? currentWizardStepInSession : null;

        String departmentNameToDelete = request.getParameter("departmentNameToDelete");
        String targetDepartmentForReassignment = request.getParameter("targetDepartmentForReassignment");

        if (!isValid(departmentNameToDelete) || !isValid(targetDepartmentForReassignment)) {
            errorMessage = "Department to delete and target department for reassignment are required.";
        } else if (departmentNameToDelete.equalsIgnoreCase("None")) {
            errorMessage = "The default 'None' department cannot be deleted.";
        } else if (departmentNameToDelete.equalsIgnoreCase(targetDepartmentForReassignment)) {
            errorMessage = "Cannot reassign employees to the same department you are deleting.";
        } else {
            Connection con = null;
            try {
                con = DatabaseConnection.getConnection();
                con.setAutoCommit(false);

                String updateEmployeesSql = "UPDATE EMPLOYEE_DATA SET DEPT = ? WHERE TenantID = ? AND DEPT = ?";
                int employeesReassigned = 0;
                try (PreparedStatement psUpdateEmp = con.prepareStatement(updateEmployeesSql)) {
                    psUpdateEmp.setString(1, targetDepartmentForReassignment);
                    psUpdateEmp.setInt(2, tenantId);
                    psUpdateEmp.setString(3, departmentNameToDelete);
                    employeesReassigned = psUpdateEmp.executeUpdate();
                    logger.info(employeesReassigned + " employees (if any) reassigned from '" + departmentNameToDelete + "' to '" + targetDepartmentForReassignment + "' for TenantID: " + tenantId);
                }

                String deleteDeptSql = "DELETE FROM DEPARTMENTS WHERE TenantID = ? AND NAME = ?";
                try (PreparedStatement psDeleteDept = con.prepareStatement(deleteDeptSql)) {
                    psDeleteDept.setInt(1, tenantId);
                    psDeleteDept.setString(2, departmentNameToDelete);
                    int rowsAffected = psDeleteDept.executeUpdate();
                    if (rowsAffected > 0) {
                        con.commit();
                        successMessage = "Department '" + departmentNameToDelete + "' deleted successfully. " + employeesReassigned + " employees reassigned to '" + targetDepartmentForReassignment + "'.";
                    } else {
                        con.rollback();
                        errorMessage = "Department '" + departmentNameToDelete + "' not found or could not be deleted. Employee reassignment rolled back.";
                    }
                }
            } catch (SQLException e) {
                if (con != null) try { if(!con.getAutoCommit()) con.rollback();} catch (SQLException ex) {logger.log(Level.WARNING, "Rollback failed", ex);}
                logger.log(Level.SEVERE, "Database error deleting/reassigning department '" + departmentNameToDelete + "' for T:" + tenantId, e);
                errorMessage = "Database error: " + e.getMessage();
            } finally {
                if (con != null) { try { if (!con.isClosed()) con.setAutoCommit(true); con.close(); } catch (SQLException ex) { logger.log(Level.WARNING, "Error closing connection", ex); }}
            }
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, successMessage, errorMessage, wizardRedirectNextStep, false));
    }
}