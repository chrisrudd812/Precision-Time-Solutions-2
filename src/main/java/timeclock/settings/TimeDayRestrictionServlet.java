package timeclock.settings; // Adjust to your package structure

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
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/TimeDayRestrictionServlet")
public class TimeDayRestrictionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(TimeDayRestrictionServlet.class.getName());
    private static final List<String> DAYS_OF_WEEK = Arrays.asList("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
    private static final String WIZARD_RETURN_STEP_SETTINGS = "settings_setup";


    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            Object tenantIdObj = session.getAttribute("TenantID");
            if (tenantIdObj instanceof Integer) {
                return (Integer) tenantIdObj;
            }
        }
        return null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String permissions = (String) session.getAttribute("Permissions");
            return "Administrator".equalsIgnoreCase(permissions);
        }
        return false;
    }

    private boolean isValidTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return false;
        try { LocalTime.parse(timeStr.trim()); return true; }
        catch (DateTimeParseException e) { return false; }
    }
    
    private String encodeURL(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }


    private void loadDataAndForward(HttpServletRequest request, HttpServletResponse response,
                                    Integer tenantId, String saveSuccessMsg, String saveErrorMsg, String initialLoadErrorMsg)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        boolean pageIsActuallyInWizardMode = false; // Determine if the current context IS wizard
        String wizardStepToReturnToOnSettingsPage = null;

        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
            String currentSessionWizardStep = (String) session.getAttribute("wizardStep");
            // If we are configuring restrictions launched from settings_setup wizard step
            if (WIZARD_RETURN_STEP_SETTINGS.equals(currentSessionWizardStep)) {
                pageIsActuallyInWizardMode = true;
                wizardStepToReturnToOnSettingsPage = WIZARD_RETURN_STEP_SETTINGS;
            }
        }
        
        // Check if the POST request indicated it was part of wizard
        boolean submittedInWizardMode = "true".equalsIgnoreCase(request.getParameter("wizardModeActive"));
        if(submittedInWizardMode && wizardStepToReturnToOnSettingsPage == null){ // If form says wizard, but session doesn't confirm, trust form's intent
             wizardStepToReturnToOnSettingsPage = request.getParameter("wizardReturnStep");
             if(wizardStepToReturnToOnSettingsPage != null) pageIsActuallyInWizardMode = true;
        }


        // --- REDIRECT LOGIC ---
        // If save was successful AND this operation was part of the wizard flow
        if (saveSuccessMsg != null && !saveSuccessMsg.isEmpty() && pageIsActuallyInWizardMode && wizardStepToReturnToOnSettingsPage != null) {
            String redirectUrl = request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" +
                                 encodeURL(wizardStepToReturnToOnSettingsPage) +
                                 "&message=" + encodeURL(saveSuccessMsg) +
                                 "&restrictionConfigured=timeDay"; // Indicate which restriction was just done
            logger.info("[TimeDayRestrictionServlet] Successful save in wizard. Redirecting to: " + redirectUrl);
            response.sendRedirect(redirectUrl);
            return; 
        }
        // If save had an error AND this operation was part of the wizard flow
        if (saveErrorMsg != null && !saveErrorMsg.isEmpty() && pageIsActuallyInWizardMode && wizardStepToReturnToOnSettingsPage != null && "saveTimeDayRestrictions".equals(request.getParameter("action"))) {
             String redirectUrl = request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" +
                                 encodeURL(wizardStepToReturnToOnSettingsPage) +
                                 "&error=" + encodeURL(saveErrorMsg) +
                                 "&restrictionConfigured=timeDay";
            logger.info("[TimeDayRestrictionServlet] Error during save in wizard. Redirecting to: " + redirectUrl);
            response.sendRedirect(redirectUrl);
            return;
        }

        // --- Regular Forward to configureTimeDayRestrictions.jsp ---
        Map<String, Map<String, Object>> currentSettings = new HashMap<>();
        String effectivePageLoadError = initialLoadErrorMsg;

        if (tenantId != null && tenantId > 0) {
            String sql = "SELECT DayOfWeek, IsRestricted, AllowedStartTime, AllowedEndTime FROM day_time_restrictions WHERE TenantID = ?";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, tenantId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> daySetting = new HashMap<>();
                        String day = rs.getString("DayOfWeek");
                        daySetting.put("isRestricted", rs.getBoolean("IsRestricted"));
                        Time startTime = rs.getTime("AllowedStartTime");
                        Time endTime = rs.getTime("AllowedEndTime");
                        daySetting.put("startTime", startTime != null ? startTime.toLocalTime().toString() : "");
                        daySetting.put("endTime", endTime != null ? endTime.toLocalTime().toString() : "");
                        currentSettings.put(day, daySetting);
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error fetching time/day restrictions for TenantID: " + tenantId, e);
                if (effectivePageLoadError == null) effectivePageLoadError = "Error loading existing settings: " + e.getMessage();
            }
        } else {
             if (effectivePageLoadError == null && isAdmin(request)) effectivePageLoadError = "Invalid tenant context for loading settings.";
             else if (effectivePageLoadError == null && !isAdmin(request)) effectivePageLoadError = "Access Denied."; // Should be caught before
        }

        request.setAttribute("timeRestrictions", currentSettings);
        if (saveSuccessMsg != null) request.setAttribute("saveSuccessMessage", saveSuccessMsg);
        if (saveErrorMsg != null) request.setAttribute("errorMessageJSP", saveErrorMsg); // For POST errors on this page
        if (effectivePageLoadError != null) request.setAttribute("pageLoadErrorMessage", effectivePageLoadError);
        
        // Pass wizard state to the JSP so it can build its "Cancel" link and form correctly
        request.setAttribute("pageIsInWizardMode", pageIsActuallyInWizardMode); // JSP uses this
        request.setAttribute("wizardReturnStepForJSP", wizardStepToReturnToOnSettingsPage); // JSP uses this

        request.getRequestDispatcher("/configureTimeDayRestrictions.jsp").forward(request, response);
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        logger.info("TimeDayRestrictionServlet doGet called.");
        Integer tenantId = getTenantId(request);
        String pageLoadErr = null;

        if (tenantId == null || tenantId <= 0) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeURL("Session expired or invalid."));
            return;
        }
        if (!isAdmin(request)) {
            pageLoadErr = "Access Denied. Administrator privileges required.";
            loadDataAndForward(request, response, 0, null, null, pageLoadErr);
            return;
        }
        loadDataAndForward(request, response, tenantId, null, null, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        logger.info("TimeDayRestrictionServlet doPost called for saving settings.");
        Integer tenantId = getTenantId(request);
        String action = request.getParameter("action");
        String successMessage = null;
        String errorMessage = null;

        if (tenantId == null || tenantId <= 0) {
            errorMessage = "Session expired or invalid. Cannot save settings.";
        } else if (!isAdmin(request)) {
            errorMessage = "Access Denied. Administrator privileges required.";
        } else if ("saveTimeDayRestrictions".equals(action)) {
            Connection conn = null;
            try {
                conn = DatabaseConnection.getConnection();
                conn.setAutoCommit(false);

                String deleteSql = "DELETE FROM day_time_restrictions WHERE TenantID = ?";
                try (PreparedStatement pstmtDelete = conn.prepareStatement(deleteSql)) {
                    pstmtDelete.setInt(1, tenantId);
                    pstmtDelete.executeUpdate();
                }

                String insertSql = "INSERT INTO day_time_restrictions (TenantID, DayOfWeek, IsRestricted, AllowedStartTime, AllowedEndTime) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement pstmtInsert = conn.prepareStatement(insertSql)) {
                    for (String day : DAYS_OF_WEEK) {
                        boolean isRestricted = "true".equalsIgnoreCase(request.getParameter("isRestricted_" + day));
                        String startTimeStr = request.getParameter("startTime_" + day);
                        String endTimeStr = request.getParameter("endTime_" + day);

                        pstmtInsert.setInt(1, tenantId);
                        pstmtInsert.setString(2, day);
                        pstmtInsert.setBoolean(3, isRestricted);

                        Time startTimeSql = null; Time endTimeSql = null;
                        if (isRestricted) {
                            if (!isValidTime(startTimeStr) || !isValidTime(endTimeStr)) {
                                throw new IllegalArgumentException("For " + day + ", if restriction is enabled, both start and end times must be valid (HH:mm).");
                            }
                            startTimeSql = Time.valueOf(LocalTime.parse(startTimeStr.trim()));
                            endTimeSql = Time.valueOf(LocalTime.parse(endTimeStr.trim()));
                            if (!startTimeSql.toLocalTime().isBefore(endTimeSql.toLocalTime())) { // Allow same time? No, usually before.
                                throw new IllegalArgumentException("For " + day + ", start time (" + startTimeStr + ") must be before end time (" + endTimeStr + ").");
                            }
                        }
                        pstmtInsert.setTime(4, startTimeSql);
                        pstmtInsert.setTime(5, endTimeSql);
                        pstmtInsert.addBatch();
                    }
                    pstmtInsert.executeBatch();
                }
                conn.commit();
                successMessage = "Time/Day punch restrictions saved successfully!";
                logger.info("Time/Day restrictions saved for TenantID: " + tenantId);
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Database error saving time/day restrictions for TenantID: " + tenantId, e);
                errorMessage = "Database error: " + e.getMessage();
                if (conn != null) try { conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Rollback failed.", ex); }
            } catch (IllegalArgumentException e) {
                logger.log(Level.WARNING, "Invalid input saving for TenantID: " + tenantId + ". Error: " + e.getMessage());
                errorMessage = "Invalid input: " + e.getMessage();
                 if (conn != null) try { conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Rollback failed on bad input.", ex); }
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { logger.log(Level.WARNING, "Error closing connection.", e); }
                }
            }
        } else {
            logger.warning("Unknown POST action in TimeDayRestrictionServlet: " + action);
            errorMessage = "Invalid server action.";
        }
        // Let loadDataAndForward handle the redirect or forward
        loadDataAndForward(request, response, tenantId, successMessage, errorMessage, null);
    }
}