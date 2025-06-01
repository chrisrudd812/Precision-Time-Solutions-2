package timeclock.scheduling;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest; // Import HttpServletRequest
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
    private static final long serialVersionUID = 1L;
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
    private boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }
    private String encodeUrlParam(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    // UPDATED buildRedirectUrl to include HttpServletRequest and wizard parameters
    private String buildRedirectUrl(HttpServletRequest request, String page, String error, String success,
                                    boolean inWizard, String wizardNextStep, boolean itemAddedInWizard) throws IOException {
        StringBuilder url = new StringBuilder(request.getContextPath() + "/" + page); // Use contextPath
        boolean firstParam = true;

        if (inWizard && wizardNextStep != null) {
            url.append(firstParam ? "?" : "&").append("setup_wizard=true");
            firstParam = false;
            url.append("&wizardStep=").append(encodeUrlParam(wizardNextStep));
            if (itemAddedInWizard) {
                if ("scheduling.jsp".equals(page)) {
                    url.append("&scheduleAdded=true"); 
                }
                // Add other page-specific "added" flags here
            }
        }

        if (error != null && !error.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("error=").append(encodeUrlParam(error));
            firstParam = false;
        } else if (success != null && !success.isEmpty()) {
            url.append(firstParam ? "?" : "&").append("message=").append(encodeUrlParam(success));
        }
        logger.info("[AddEditAndDeleteSchedulesServlet] Redirecting to: " + url.toString());
        return url.toString();
    }
    
    // Simpler overload for non-wizard redirects
     private String buildRedirectUrl(HttpServletRequest request, String page, String error, String success) throws IOException {
        return buildRedirectUrl(request, page, error, success, false, null, false);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        logger.warning("AddEditAndDeleteSchedulesServlet received GET request. Action: " + request.getParameter("action") + ". Data modifying actions should use POST.");
        response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Invalid request method for this action.", null));
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
        logger.info("AddEditAndDeleteSchedulesServlet doPost received action: " + action + " for TenantID: " + tenantId);
        
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
            case "delete": 
                handleSimpleScheduleDeleteIfEmpty(request, response, tenantId);
                break;
            default:
                logger.warning("doPost called with unknown or missing 'action' parameter in AddEditAndDeleteSchedulesServlet: " + action);
                response.sendRedirect(buildRedirectUrl(request, "scheduling.jsp", "Invalid form submission.", null));
                break;
        }
    }

    private Time parseTime(String timeString) {
        if (isEmpty(timeString)) return null;
        try {
            return Time.valueOf(LocalTime.parse(timeString.trim(), INPUT_TIME_FORMATTER));
        } catch (DateTimeParseException e) {
            logger.warning("Invalid time format for string: " + timeString + ". Expected HH:mm. " + e.getMessage());
            return null;
        }
    }
    
    private String getDaysWorkedString(String[] checkedDaysArray) {
        if (checkedDaysArray == null || checkedDaysArray.length == 0) return "-------";
        char[] days = {'-', '-', '-', '-', '-', '-', '-'}; 
        for (String day : checkedDaysArray) {
            switch (day.toLowerCase()) {
                case "sun": days[0] = 'S'; break; case "mon": days[1] = 'M'; break;
                case "tue": days[2] = 'T'; break; case "wed": days[3] = 'W'; break;
                case "thu": days[4] = 'H'; break; case "fri": days[5] = 'F'; break;
                case "sat": days[6] = 'A'; break;
            }
        } return new String(days);
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
        String successMessage = null;
        String wizardRedirectNextStep = null;
        boolean itemAddedInWizardFlow = false;

        if (!isValid(scheduleName)) { errorMessage = "Schedule Name is required."; }
        
        boolean isOpenTypeSchedule = false;
        if (errorMessage == null && scheduleName != null) {
            isOpenTypeSchedule = "Open".equalsIgnoreCase(scheduleName.trim()) || 
                                 "Open with Auto Lunch".equalsIgnoreCase(scheduleName.trim()) || 
                                 "Open w/ Auto Lunch".equalsIgnoreCase(scheduleName.trim());
            if (isOpenTypeSchedule) {
                shiftStart = null; lunchStart = null; lunchEnd = null; shiftEnd = null;
                daysWorked = "SMTWHFA"; 
            }
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
            // If wizard is active, redirect with current step to allow user to correct and re-submit
            String stepForRedirect = isWizardModeActive ? currentWizardStepInSession : null;
            response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, stepForRedirect, false));
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
                        errorMessage = "Schedule '" + scheduleName.trim() + "' already exists for your company.";
                        String stepForRedirect = isWizardModeActive ? currentWizardStepInSession : null;
                        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, stepForRedirect, false));
                        return;
                    }
                }
            }

            try (PreparedStatement psInsert = con.prepareStatement(sqlInsert)) {
                int paramIdx = 1;
                psInsert.setInt(paramIdx++, tenantId);
                psInsert.setString(paramIdx++, scheduleName.trim());
                if (shiftStart != null) psInsert.setTime(paramIdx++, shiftStart); else psInsert.setNull(paramIdx++, Types.TIME);
                if (lunchStart != null) psInsert.setTime(paramIdx++, lunchStart); else psInsert.setNull(paramIdx++, Types.TIME);
                if (lunchEnd != null) psInsert.setTime(paramIdx++, lunchEnd); else psInsert.setNull(paramIdx++, Types.TIME);
                if (shiftEnd != null) psInsert.setTime(paramIdx++, shiftEnd); else psInsert.setNull(paramIdx++, Types.TIME);
                psInsert.setString(paramIdx++, daysWorked);
                psInsert.setBoolean(paramIdx++, autoLunch);
                if (autoLunch) {
                    psInsert.setDouble(paramIdx++, hoursRequired);
                    psInsert.setInt(paramIdx++, lunchLength);
                } else {
                    psInsert.setNull(paramIdx++, Types.DOUBLE); 
                    psInsert.setNull(paramIdx++, Types.INTEGER);
                }
                
                int rowsAffected = psInsert.executeUpdate();
                if (rowsAffected > 0) {
                    successMessage = "Schedule '" + scheduleName.trim() + "' added successfully.";
                    logger.info(successMessage + " For TenantID: " + tenantId);

                    if (isWizardModeActive && currentWizardStepInSession != null && 
                        (currentWizardStepInSession.equals("schedules_prompt") || currentWizardStepInSession.equals("schedules_after_add_prompt"))) {
                        itemAddedInWizardFlow = true;
                        wizardRedirectNextStep = "schedules_after_add_prompt"; 
                        if(session != null) session.setAttribute("wizardStep", wizardRedirectNextStep); 
                        logger.info("[AddEditAndDeleteSchedulesServlet] Schedule added in wizard. Next step in session: " + wizardRedirectNextStep);
                    }
                } else {
                    errorMessage = "Failed to add schedule. Please try again.";
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Database error adding schedule for TenantID: " + tenantId, e);
            errorMessage = "Database error: " + e.getMessage();
        }
        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, successMessage, isWizardModeActive, wizardRedirectNextStep, itemAddedInWizardFlow));
    }

    private void editSchedule(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session, 
                              boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        // ... (Your existing editSchedule logic) ...
        // Ensure all calls to buildRedirectUrl pass 'request' and wizard parameters
        String redirectPage = "scheduling.jsp";
        String successMessage = null; String errorMessage = null;
        String wizardRedirectNextStep = isWizardModeActive ? currentWizardStepInSession : null; // Stay on current wizard prompt

        // ... (Parameter retrieval and validation as in your provided code) ...
        String originalScheduleName = request.getParameter("originalScheduleName");
        // ...
        
        // Example of redirect on error:
        // if (errorMessage != null) {
        //    response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, wizardRedirectNextStep, false));
        //    return;
        // }

        // ... (DB update logic as in your provided code) ...
        
        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, successMessage, isWizardModeActive, wizardRedirectNextStep, false));
    }
    
    private void handleDeleteAndReassignSchedule(HttpServletRequest request, HttpServletResponse response, int tenantId, HttpSession session,
                                                 boolean isWizardModeActive, String currentWizardStepInSession) throws IOException {
        // ... (Your existing handleDeleteAndReassignSchedule logic) ...
        // Ensure all calls to buildRedirectUrl pass 'request' and wizard parameters
        String redirectPage = "scheduling.jsp";
        String successMessage = null; String errorMessage = null;
        String wizardRedirectNextStep = isWizardModeActive ? currentWizardStepInSession : null;

        // ... (Parameter retrieval and validation as in your provided code) ...
        
        // Example of redirect on error:
        // if (errorMessage != null) {
        //    response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, null, isWizardModeActive, wizardRedirectNextStep, false));
        //    return;
        // }

        // ... (DB logic for reassignment and deletion as in your provided code) ...
        
        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, successMessage, isWizardModeActive, wizardRedirectNextStep, false));
    }

    private void handleSimpleScheduleDeleteIfEmpty(HttpServletRequest request, HttpServletResponse response, int tenantId) throws IOException {
        // ... (Your existing handleSimpleScheduleDeleteIfEmpty logic) ...
        // Ensure all calls to buildRedirectUrl pass 'request'
        // This method is less likely to be part of the "Add Item Now" wizard flow, so wizard params might be less critical here.
        String redirectPage = "scheduling.jsp";
        String successMessage = null; String errorMessage = null;

        // ... (Logic) ...
        response.sendRedirect(buildRedirectUrl(request, redirectPage, errorMessage, successMessage)); // No wizard params passed here for simple delete
    }
}