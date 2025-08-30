package timeclock.scheduling;

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
import java.sql.Time;
import java.sql.Types;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/AddEditAndDeleteSchedulesServlet")
public class AddEditAndDeleteSchedulesServlet extends HttpServlet {
    private static final long serialVersionUID = 6L; // Version updated
    private static final Logger logger = Logger.getLogger(AddEditAndDeleteSchedulesServlet.class.getName());
    private static final DateTimeFormatter INPUT_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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
            if (itemAddedInWizard && "scheduling.jsp".equals(page)) {
                url.append("&scheduleAdded=true");
            }
        }

        if (error != null && !error.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("error=").append(encodeUrlParam(error));
        } else if (success != null && !success.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("message=").append(encodeUrlParam(success));
        }
        logger.info("[AddEditAndDeleteSchedulesServlet] Redirecting to: " + url.toString());
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
            response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Access Denied.", null));
            return;
        }

        String action = request.getParameter("action");
        logger.info("...SchedulesServlet doPost received action: " + action + " for TenantID: " + tenantId);
        
        boolean isWizardModeActive = (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard")));
        String currentWizardStepInSession = isWizardModeActive ? (String) session.getAttribute("wizardStep") : null;

        switch (action != null ? action.trim() : "") {
            case "add":
                addSchedule(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            case "edit":
                editSchedule(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            case "deleteAndReassignSchedule":
                handleDeleteAndReassignSchedule(request, response, tenantId, session, isWizardModeActive, currentWizardStepInSession);
                break;
            default:
                logger.warning("doPost called with unknown or missing 'action' parameter: " + action);
                response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Invalid form submission.", null));
                break;
        }
    }

    private Time parseTime(String timeString) {
        if (timeString == null || timeString.trim().isEmpty()) return null;
        try {
            return Time.valueOf(LocalTime.parse(timeString.trim(), INPUT_TIME_FORMATTER));
        } catch (DateTimeParseException e) {
            logger.warning("Invalid time format for string: " + timeString + ". Expected HH:mm. " + e.getMessage());
            return null;
        }
    }
    
    // *** MODIFICATION: Corrected this method to build the SMTWHFA string format. ***
    private String getDaysWorkedString(String[] checkedDaysArray) {
        char[] days = {'-', '-', '-', '-', '-', '-', '-'}; // Index 0=Sun, 1=Mon, ..., 6=Sat
        if (checkedDaysArray != null) {
            for (String day : checkedDaysArray) {
                switch (day.toLowerCase()) {
                    case "sun": days[0] = 'S'; break;
                    case "mon": days[1] = 'M'; break;
                    case "tue": days[2] = 'T'; break;
                    case "wed": days[3] = 'W'; break;
                    case "thu": days[4] = 'H'; break; // H for Thursday
                    case "fri": days[5] = 'F'; break;
                    case "sat": days[6] = 'A'; break; // A for Saturday
                }
            }
        }
        return new String(days);
    }

    private void addSchedule(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session, 
                             boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String scheduleName = request.getParameter("scheduleName");
        Time shiftStart = parseTime(request.getParameter("shiftStart"));
        Time lunchStart = parseTime(request.getParameter("lunchStart"));
        Time lunchEnd = parseTime(request.getParameter("lunchEnd"));
        Time shiftEnd = parseTime(request.getParameter("shiftEnd"));
        String[] checkedDaysArray = request.getParameterValues("days");
        String daysWorked = getDaysWorkedString(checkedDaysArray);
        boolean autoLunch = "true".equalsIgnoreCase(request.getParameter("autoLunch"));
        String hoursRequiredStr = request.getParameter("hoursRequired");
        String lunchLengthStr = request.getParameter("lunchLength");
        
        String redirectPage = "scheduling.jsp";
        String errorMessage = null; 
        
        if (!isValid(scheduleName)) { errorMessage = "Schedule Name is required."; }
        
        if (errorMessage == null && (scheduleName.equalsIgnoreCase("Open") || scheduleName.equalsIgnoreCase("Open w/ Auto Lunch"))) {
            daysWorked = "SMTWHFA"; 
        }

        double hoursRequired = 0.0; 
        int lunchLength = 0;
        if (errorMessage == null && autoLunch) {
            if (!isValid(hoursRequiredStr) || !isValid(lunchLengthStr)) {
                errorMessage = "If Auto Lunch is enabled, 'Hours Required' and 'Lunch Length' must be specified.";
            } else {
                try {
                    hoursRequired = Double.parseDouble(hoursRequiredStr);
                    lunchLength = Integer.parseInt(lunchLengthStr);
                    if (hoursRequired <= 0 || lunchLength <= 0) {
                         errorMessage = "For Auto Lunch, 'Hours Required' and 'Lunch Length' must be positive values.";
                    }
                } catch (NumberFormatException e) {
                    errorMessage = "Invalid number format for Hours Required or Lunch Length.";
                }
            }
        }

        if (errorMessage != null) {
            String url = buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, currentWizardStepInSession, false);
            url += (url.contains("?") ? "&" : "?") + "reopenModal=addSchedule&scheduleName=" + encodeUrlParam(scheduleName);
            response.sendRedirect(url);
            return;
        }

        String sqlCheck = "SELECT NAME FROM SCHEDULES WHERE TenantID = ? AND LOWER(NAME) = LOWER(?)";
        String sqlInsert = "INSERT INTO SCHEDULES (TenantID, NAME, SHIFT_START, LUNCH_START, LUNCH_END, SHIFT_END, DAYS_WORKED, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DatabaseConnection.getConnection()) {
            try (PreparedStatement psCheck = con.prepareStatement(sqlCheck)) {
                psCheck.setInt(1, tenantId);
                psCheck.setString(2, scheduleName.trim().toLowerCase());
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) {
                        errorMessage = "Schedule '" + scheduleName.trim() + "' already exists.";
                        String url = buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, currentWizardStepInSession, false);
                        url += (url.contains("?") ? "&" : "?") + "reopenModal=addSchedule&scheduleName=" + encodeUrlParam(scheduleName.trim());
                        response.sendRedirect(url);
                        return;
                    }
                }
            }

            try (PreparedStatement psInsert = con.prepareStatement(sqlInsert)) {
                int paramIdx = 1;
                psInsert.setInt(paramIdx++, tenantId);
                psInsert.setString(paramIdx++, scheduleName.trim());
                psInsert.setTime(paramIdx++, shiftStart);
                psInsert.setTime(paramIdx++, lunchStart);
                psInsert.setTime(paramIdx++, lunchEnd);
                psInsert.setTime(paramIdx++, shiftEnd);
                psInsert.setString(paramIdx++, daysWorked);
                psInsert.setBoolean(paramIdx++, autoLunch);
                if (autoLunch) {
                    psInsert.setDouble(paramIdx++, hoursRequired);
                    psInsert.setInt(paramIdx++, lunchLength);
                } else {
                    psInsert.setNull(paramIdx++, Types.DOUBLE); 
                    psInsert.setNull(paramIdx++, Types.INTEGER);
                }
                
                if (psInsert.executeUpdate() > 0) {
                    String successMessage = "Schedule '" + scheduleName.trim() + "' added successfully.";
                    boolean itemAdded = isWizardModeActive && (currentWizardStepInSession.equals("schedules_prompt") || currentWizardStepInSession.equals("schedules_after_add_prompt"));
                    String nextStep = itemAdded ? "schedules_after_add_prompt" : currentWizardStepInSession;
                    if (itemAdded && session != null) session.setAttribute("wizardStep", nextStep);
                    response.sendRedirect(buildRedirectUrl(request, redirectPage, null, successMessage, isWizardModeActive, nextStep, itemAdded));
                } else {
                    response.sendRedirect(buildRedirectUrl(request, redirectPage, "Failed to add schedule.", null, isWizardModeActive, currentWizardStepInSession, false));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error adding schedule for TenantID: " + tenantId, e);
            response.sendRedirect(buildRedirectUrl(request, redirectPage, "Database error: " + e.getMessage(), null, isWizardModeActive, currentWizardStepInSession, false));
        }
    }

    private void editSchedule(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session, 
                              boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String originalScheduleName = request.getParameter("originalScheduleName");
        
        if (!isValid(originalScheduleName)) {
            response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Original schedule name not provided.", null, isWizardModeActive, currentWizardStepInSession, false));
            return;
        }

        String sqlUpdate;
        boolean isAutoLunchOpen = originalScheduleName.trim().equalsIgnoreCase("open w/ auto lunch");

        if (originalScheduleName.trim().equalsIgnoreCase("open")) {
            response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "The default 'Open' schedule cannot be edited.", null, isWizardModeActive, currentWizardStepInSession, false));
            return;
        }
        
        if (isAutoLunchOpen) {
            sqlUpdate = "UPDATE SCHEDULES SET AUTO_LUNCH = TRUE, HRS_REQUIRED = ?, LUNCH_LENGTH = ? WHERE TenantID = ? AND NAME = ?";
        } else {
            sqlUpdate = "UPDATE SCHEDULES SET SHIFT_START = ?, LUNCH_START = ?, LUNCH_END = ?, SHIFT_END = ?, " +
                        "DAYS_WORKED = ?, AUTO_LUNCH = ?, HRS_REQUIRED = ?, LUNCH_LENGTH = ? " +
                        "WHERE TenantID = ? AND NAME = ?";
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement psUpdate = con.prepareStatement(sqlUpdate)) {
            
            double hoursRequired = 0.0;
            int lunchLength = 0;
            boolean autoLunch = "true".equalsIgnoreCase(request.getParameter("autoLunch"));
            String errorMessage = null;

            if (autoLunch || isAutoLunchOpen) {
                try {
                    hoursRequired = Double.parseDouble(request.getParameter("hoursRequired"));
                    lunchLength = Integer.parseInt(request.getParameter("lunchLength"));
                    if (hoursRequired <= 0 || lunchLength <= 0) {
                        errorMessage = "Hours Required and Lunch Length must be positive values.";
                    }
                } catch (NumberFormatException e) {
                    errorMessage = "Invalid number format for Hours Required or Lunch Length.";
                }
            }
            
            if (errorMessage != null) {
                response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", errorMessage, null, isWizardModeActive, currentWizardStepInSession, false));
                return;
            }

            if (isAutoLunchOpen) {
                psUpdate.setDouble(1, hoursRequired);
                psUpdate.setInt(2, lunchLength);
                psUpdate.setInt(3, tenantId);
                psUpdate.setString(4, originalScheduleName);
            } else {
                psUpdate.setTime(1, parseTime(request.getParameter("shiftStart")));
                psUpdate.setTime(2, parseTime(request.getParameter("lunchStart")));
                psUpdate.setTime(3, parseTime(request.getParameter("lunchEnd")));
                psUpdate.setTime(4, parseTime(request.getParameter("shiftEnd")));
                psUpdate.setString(5, getDaysWorkedString(request.getParameterValues("days")));
                psUpdate.setBoolean(6, autoLunch);
                if (autoLunch) {
                    psUpdate.setDouble(7, hoursRequired);
                    psUpdate.setInt(8, lunchLength);
                } else {
                    psUpdate.setNull(7, Types.DOUBLE);
                    psUpdate.setNull(8, Types.INTEGER);
                }
                psUpdate.setInt(9, tenantId);
                psUpdate.setString(10, originalScheduleName);
            }

            if (psUpdate.executeUpdate() > 0) {
                 response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", null, "Schedule '" + originalScheduleName + "' updated.", isWizardModeActive, currentWizardStepInSession, false));
            } else {
                response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Schedule not found or no changes made.", null, isWizardModeActive, currentWizardStepInSession, false));
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "DB error updating schedule '" + originalScheduleName + "' for T:" + tenantId, e);
            response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Database error: " + e.getMessage(), null, isWizardModeActive, currentWizardStepInSession, false));
        }
    }
    
    private void handleDeleteAndReassignSchedule(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session,
                                                 boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        String scheduleNameToDelete = request.getParameter("scheduleNameToDelete");
        String targetScheduleForReassignment = request.getParameter("targetScheduleForReassignment");
        String errorMessage = null;

        if (!isValid(scheduleNameToDelete) || !isValid(targetScheduleForReassignment)) {
            errorMessage = "Required schedules for delete/reassign not provided.";
        } else if (scheduleNameToDelete.equalsIgnoreCase(targetScheduleForReassignment)) {
            errorMessage = "Cannot reassign to the schedule being deleted.";
        } else if (scheduleNameToDelete.toLowerCase().startsWith("open")) {
            errorMessage = "Default 'Open' schedules cannot be deleted.";
        }
        
        if (errorMessage != null) {
            response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", errorMessage, null, isWizardModeActive, currentWizardStepInSession, false));
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(false);
            
            String updateEmployeesSql = "UPDATE EMPLOYEE_DATA SET SCHEDULE = ? WHERE TenantID = ? AND SCHEDULE = ?";
            int employeesReassigned;
            try (PreparedStatement psUpdateEmp = con.prepareStatement(updateEmployeesSql)) {
                psUpdateEmp.setString(1, targetScheduleForReassignment);
                psUpdateEmp.setInt(2, tenantId);
                psUpdateEmp.setString(3, scheduleNameToDelete);
                employeesReassigned = psUpdateEmp.executeUpdate();
            }

            String deleteSql = "DELETE FROM SCHEDULES WHERE TenantID = ? AND NAME = ?";
            try (PreparedStatement psDelete = con.prepareStatement(deleteSql)) {
                psDelete.setInt(1, tenantId);
                psDelete.setString(2, scheduleNameToDelete);
                if (psDelete.executeUpdate() > 0) {
                    con.commit();
                    String successMessage = "Schedule '" + scheduleNameToDelete + "' deleted. " + employeesReassigned + " employees reassigned.";
                    response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", null, successMessage, isWizardModeActive, currentWizardStepInSession, false));
                } else {
                    con.rollback();
                    response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Schedule '" + scheduleNameToDelete + "' not found.", null, isWizardModeActive, currentWizardStepInSession, false));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error during schedule deletion/reassignment for T:" + tenantId, e);
            response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Database error during deletion: " + e.getMessage(), null, isWizardModeActive, currentWizardStepInSession, false));
        }
    }
}