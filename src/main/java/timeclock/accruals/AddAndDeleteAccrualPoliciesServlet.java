package timeclock.accruals;

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

@WebServlet("/AddAndDeleteAccrualPoliciesServlet")
public class AddAndDeleteAccrualPoliciesServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddAndDeleteAccrualPoliciesServlet.class.getName());

    private Integer getTenantIdFromSession(HttpSession session) {
        if (session == null) return null;
        Object tenantIdObj = session.getAttribute("TenantID");
        return (tenantIdObj instanceof Integer) ? (Integer) tenantIdObj : null;
    }

    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }

    private String encodeUrlParam(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private String buildRedirectUrl(HttpServletRequest request, String page, String error, String success,
                                    boolean inWizard, String wizardNextStep, boolean itemAddedInWizard) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page);
        boolean firstParam = true;

        if (inWizard && wizardNextStep != null) {
            url.append(firstParam ? "?" : "&").append("setup_wizard=true");
            firstParam = false;
            url.append("&wizardStep=").append(encodeUrlParam(wizardNextStep));
            if (itemAddedInWizard) {
                if ("accruals.jsp".equals(page)) {
                    url.append("&accrualAdded=true"); 
                }
            }
        }

        if (error != null && !error.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("error=").append(encodeUrlParam(error));
            firstParam = false;
        } else if (success != null && !success.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("message=").append(encodeUrlParam(success));
        }
        return url.toString();
    }
    
    private String buildRedirectUrl(HttpServletRequest request, String page, String error, String success) throws IOException {
        return buildRedirectUrl(request, page, error, success, false, null, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        HttpSession session = request.getSession(false);
        Integer tenantId = getTenantIdFromSession(session);

        if (tenantId == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeUrlParam("Session error. Please log in again."));
            return;
        }
        String userPermissions = (String) session.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            response.sendRedirect(buildRedirectUrl(request, "accruals.jsp", "Access Denied.", null));
            return;
        }

        String action = request.getParameter("action");

        boolean isWizardModeActive = (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard")));
        String currentWizardStepInSession = isWizardModeActive ? (String) session.getAttribute("wizardStep") : null;

        switch (action != null ? action.trim() : "") {
            case "addAccrual":
                addAccrualPolicy(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            case "editAccrual":
                editAccrualPolicy(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            case "deleteAndReassignAccrualPolicy":
                handleDeleteAndReassignAccrualPolicy(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            default:
                logger.warning("Unknown POST action in AccrualPoliciesServlet: " + action);
                response.sendRedirect(buildRedirectUrl(request, "accruals.jsp", "Invalid form submission.", null));
                break;
        }
    }

    private void addAccrualPolicy(HttpServletRequest request, HttpServletResponse response, Integer tenantId, HttpSession session,
                                  boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String policyName = request.getParameter("addAccrualName");
        String vacationDaysStr = request.getParameter("addVacationDays");
        String sickDaysStr = request.getParameter("addSickDays");
        String personalDaysStr = request.getParameter("addPersonalDays");
        
        String redirectPage = "accruals.jsp";
        String errorMessage = null; 
        String successMessage = null;
        String wizardRedirectNextStep = null; 
        boolean itemAddedInWizardFlow = false;

        if (!isValid(policyName) || !isValid(vacationDaysStr) || !isValid(sickDaysStr) || !isValid(personalDaysStr)) {
            errorMessage = "Policy Name and all day counts are required.";
        } else {
            try {
                int vacationDays = Integer.parseInt(vacationDaysStr);
                int sickDays = Integer.parseInt(sickDaysStr);
                int personalDays = Integer.parseInt(personalDaysStr);
                if (vacationDays < 0 || sickDays < 0 || personalDays < 0) {
                    throw new NumberFormatException("Day counts cannot be negative.");
                }

                String sqlCheck = "SELECT NAME FROM accruals WHERE TenantID = ? AND LOWER(NAME) = LOWER(?)";
                String sqlInsert = "INSERT INTO accruals (TenantID, NAME, VACATION, SICK, PERSONAL) VALUES (?, ?, ?, ?, ?)";
                
                try (Connection con = DatabaseConnection.getConnection()) {
                    try (PreparedStatement psCheck = con.prepareStatement(sqlCheck)) {
                        psCheck.setInt(1, tenantId);
                        psCheck.setString(2, policyName.trim().toLowerCase());
                        try (ResultSet rs = psCheck.executeQuery()) {
                            if (rs.next()) {
                                // **FIX START**: This block is modified to handle the duplicate name error specifically.
                                errorMessage = "PTO policy '" + policyName.trim() + "' already exists.";
                                String stepForRedirect = isWizardModeActive ? currentWizardStepInSession : null;
                                String url = buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, stepForRedirect, false);
                                
                                url += (url.contains("?") ? "&" : "?") + "reopenModal=addAccrual";
                                url += "&policyName=" + encodeUrlParam(policyName.trim());

                                response.sendRedirect(url);
                                return;
                                // **FIX END**
                            }
                        }
                    }

                    if (errorMessage == null) {
                        try (PreparedStatement psInsert = con.prepareStatement(sqlInsert)) {
                            psInsert.setInt(1, tenantId);
                            psInsert.setString(2, policyName.trim());
                            psInsert.setInt(3, vacationDays);
                            psInsert.setInt(4, sickDays);
                            psInsert.setInt(5, personalDays);
                            int rowsAffected = psInsert.executeUpdate();
                            if (rowsAffected > 0) {
                                successMessage = "PTO policy '" + policyName.trim() + "' added successfully.";

                                if (isWizardModeActive && currentWizardStepInSession != null &&
                                    (currentWizardStepInSession.equals("accruals_prompt") || currentWizardStepInSession.equals("accruals_after_add_prompt"))) {
                                    itemAddedInWizardFlow = true;
                                    wizardRedirectNextStep = "accruals_after_add_prompt"; 
                                    if(session != null) session.setAttribute("wizardStep", wizardRedirectNextStep);
                                }
                            } else {
                                errorMessage = "Failed to add PTO policy. No rows affected.";
                            }
                        }
                    }
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Error adding accrual policy for T:" + tenantId + ": " + e.getMessage(), e);
                    errorMessage = "Database error occurred while adding policy.";
                }
            } catch (NumberFormatException e) {
                errorMessage = "Invalid number format for days: " + e.getMessage();
            }
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, successMessage, isWizardModeActive, wizardRedirectNextStep, itemAddedInWizardFlow));
    }

    private void editAccrualPolicy(HttpServletRequest request, HttpServletResponse response, Integer tenantId, HttpSession session,
                                   boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String originalPolicyName = request.getParameter("originalAccrualName");
        String vacationDaysStr = request.getParameter("editVacationDays");
        String sickDaysStr = request.getParameter("editSickDays");
        String personalDaysStr = request.getParameter("editPersonalDays");
        
        String redirectPage = "accruals.jsp";
        String errorMessage = null; 
        String successMessage = null;
        String wizardRedirectNextStep = isWizardModeActive ? currentWizardStepInSession : null;

        if (!isValid(originalPolicyName) || !isValid(vacationDaysStr) || !isValid(sickDaysStr) || !isValid(personalDaysStr)) {
            errorMessage = "Original Policy name and all day counts are required for editing.";
        } else if ("None".equalsIgnoreCase(originalPolicyName.trim())) {
             errorMessage = "The 'None' policy cannot be edited.";
        } else {
            try {
                int vacationDays = Integer.parseInt(vacationDaysStr);
                int sickDays = Integer.parseInt(sickDaysStr);
                int personalDays = Integer.parseInt(personalDaysStr);
                if (vacationDays < 0 || sickDays < 0 || personalDays < 0) {
                    throw new NumberFormatException("Day counts cannot be negative.");
                }
                
                String sqlUpdate = "UPDATE accruals SET VACATION = ?, SICK = ?, PERSONAL = ? WHERE NAME = ? AND TenantID = ?";
                try (Connection con = DatabaseConnection.getConnection(); PreparedStatement psUpdate = con.prepareStatement(sqlUpdate)) {
                    psUpdate.setInt(1, vacationDays);
                    psUpdate.setInt(2, sickDays);
                    psUpdate.setInt(3, personalDays);
                    psUpdate.setString(4, originalPolicyName.trim());
                    psUpdate.setInt(5, tenantId);
                    int rowsAffected = psUpdate.executeUpdate();
                    if (rowsAffected > 0) {
                        successMessage = "PTO policy '" + originalPolicyName.trim() + "' updated successfully.";
                    } else {
                        errorMessage = "Failed to update policy '" + originalPolicyName.trim() + "'. Record not found or no changes made.";
                    }
                } catch (SQLException e) {
                    logger.log(Level.SEVERE, "Error editing accrual policy '" + originalPolicyName + "' for T:" + tenantId + ": " + e.getMessage(), e);
                    errorMessage = "Database error occurred during update.";
                }
            } catch (NumberFormatException e) {
                errorMessage = "Invalid number format for days: " + e.getMessage();
            }
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, successMessage, isWizardModeActive, wizardRedirectNextStep, false));
    }
    
    private void handleDeleteAndReassignAccrualPolicy(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session,
                                                     boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String policyNameToDelete = request.getParameter("hiddenAccrualNameToDelete");
        String targetPolicyForReassignment = request.getParameter("targetAccrualPolicyForReassignment");
        
        String successMessage = null;
        String errorMessage = null;
        String redirectPage = "accruals.jsp";
        String wizardRedirectNextStep = isWizardModeActive ? currentWizardStepInSession : null;

        if (!isValid(policyNameToDelete)) errorMessage = "PTO Policy to delete was not specified.";
        else if (!isValid(targetPolicyForReassignment)) errorMessage = "Target PTO Policy for reassignment not specified.";
        else if ("None".equalsIgnoreCase(policyNameToDelete.trim())) errorMessage = "The 'None' policy cannot be deleted.";
        else if (policyNameToDelete.trim().equalsIgnoreCase(targetPolicyForReassignment.trim())) errorMessage = "Cannot reassign to the same policy being deleted.";

        if (errorMessage != null) {
            response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, wizardRedirectNextStep, false));
            return;
        }

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); 

            String reassignSql = "UPDATE employee_data SET ACCRUAL_POLICY = ? WHERE ACCRUAL_POLICY = ? AND TenantID = ?";
            int employeesReassignedCount = 0;
            try (PreparedStatement pstmtReassign = conn.prepareStatement(reassignSql)) {
                pstmtReassign.setString(1, targetPolicyForReassignment.trim());
                pstmtReassign.setString(2, policyNameToDelete.trim());
                pstmtReassign.setInt(3, tenantId);
                employeesReassignedCount = pstmtReassign.executeUpdate();
            }

            String deletePolicySql = "DELETE FROM accruals WHERE NAME = ? AND TenantID = ?";
            try (PreparedStatement pstmtDelete = conn.prepareStatement(deletePolicySql)) {
                pstmtDelete.setString(1, policyNameToDelete.trim());
                pstmtDelete.setInt(2, tenantId);
                int policyRowsDeleted = pstmtDelete.executeUpdate();
                if (policyRowsDeleted > 0) {
                    conn.commit(); 
                    successMessage = "Policy '" + policyNameToDelete.trim() + "' deleted. " + employeesReassignedCount + " employee(s) reassigned to '" + targetPolicyForReassignment.trim() + "'.";
                } else {
                    conn.rollback();
                    errorMessage = "Failed to delete policy '" + policyNameToDelete.trim() + "'. It might have been already deleted. Reassignment rolled back.";
                }
            }
        } catch (SQLException e) {
            if (conn != null) { try { conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Rollback failed.", ex); }}
            errorMessage = "Database error: " + e.getMessage();
        } finally {
            if (conn != null) { try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { logger.log(Level.WARNING, "Error closing conn.", e); }}
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, successMessage, isWizardModeActive, wizardRedirectNextStep, false));
    }
}
