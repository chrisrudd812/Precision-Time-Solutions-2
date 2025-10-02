package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.Configuration;

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
import java.util.Enumeration;
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
    private static final String WIZARD_RETURN_STEP_settings = "settings_setup";

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

    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\").replace("\"", "\\\"").replace("\b", "\\b").replace("\f", "\\f").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer tenantId = getTenantId(request);
        if (tenantId == null || tenantId <= 0) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeURL("Session expired or invalid."));
            return;
        }
        if (!isAdmin(request)) {
            request.setAttribute("pageLoadErrorMessage", "Access Denied. Administrator privileges required.");
            request.getRequestDispatcher("/configureTimeDayRestrictions.jsp").forward(request, response);
            return;
        }
        
        // Check for wizard mode parameters
        boolean isWizardMode = "true".equalsIgnoreCase(request.getParameter("setup_wizard"));
        String returnStep = request.getParameter("return_step");
        if (isWizardMode && returnStep != null) {
            request.setAttribute("pageIsInWizardMode", true);
            request.setAttribute("wizardReturnStepForJSP", returnStep);
        }
        
        loadDataAndForwardToJsp(request, response, tenantId, null);
    }

    private void loadDataAndForwardToJsp(HttpServletRequest request, HttpServletResponse response, Integer tenantId, String pageLoadError) throws ServletException, IOException {
        // Check if wizard mode was already set by doGet from URL parameters
        boolean pageIsActuallyInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
        String wizardStepToReturnToOnSettingsPage = (String) request.getAttribute("wizardReturnStepForJSP");
        
        // If not set from URL parameters, check session (fallback)
        if (!pageIsActuallyInWizardMode) {
            HttpSession session = request.getSession(false);
            if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
                String currentSessionWizardStep = (String) session.getAttribute("wizardStep");
                if (WIZARD_RETURN_STEP_settings.equals(currentSessionWizardStep)) {
                    pageIsActuallyInWizardMode = true;
                    wizardStepToReturnToOnSettingsPage = WIZARD_RETURN_STEP_settings;
                }
            }
        }

        Map<String, Map<String, Object>> currentSettings = new HashMap<>();
        String effectivePageLoadError = pageLoadError;

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
        }

        request.setAttribute("timeRestrictions", currentSettings);
        if (effectivePageLoadError != null) request.setAttribute("pageLoadErrorMessage", effectivePageLoadError);
        
        String allowUnselectedStr = "true";
        if (tenantId != null && tenantId > 0) {
            try {
            	allowUnselectedStr = Configuration.getProperty(tenantId, "allowUnselectedDays", "true");
            } catch (Exception e) {
                logger.log(Level.WARNING, "Could not load TimeDayAllowUnselected setting for TenantID: " + tenantId, e);
                if (effectivePageLoadError == null) request.setAttribute("pageLoadErrorMessage", "Could not load global rule for disabled days.");
            }
        }
        request.setAttribute("allowUnselectedDays", "true".equalsIgnoreCase(allowUnselectedStr));

        // Only set if not already set
        if (request.getAttribute("pageIsInWizardMode") == null) {
            request.setAttribute("pageIsInWizardMode", pageIsActuallyInWizardMode);
        }
        if (request.getAttribute("wizardReturnStepForJSP") == null) {
            request.setAttribute("wizardReturnStepForJSP", wizardStepToReturnToOnSettingsPage);
        }

        request.getRequestDispatcher("/configureTimeDayRestrictions.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Integer tenantId = getTenantId(request);
        String action = request.getParameter("action");
        String successMessage = null;
        String errorMessage = null;

        if (tenantId == null || tenantId <= 0) {
            errorMessage = "Your session has expired. Please log in again.";
        } else if (!isAdmin(request)) {
            errorMessage = "Access Denied. You do not have permission to perform this action.";
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
                        pstmtInsert.setInt(3, isRestricted ? 1 : 0);

                        Time startTimeSql = null; Time endTimeSql = null;
                        if (isRestricted) {
                            if (!isValidTime(startTimeStr) || !isValidTime(endTimeStr)) {
                                throw new IllegalArgumentException("For " + day + ", if restriction is enabled, both start and end times must be valid (HH:mm).");
                            }
                            startTimeSql = Time.valueOf(LocalTime.parse(startTimeStr.trim()));
                            endTimeSql = Time.valueOf(LocalTime.parse(endTimeStr.trim()));
                            if (!startTimeSql.toLocalTime().isBefore(endTimeSql.toLocalTime())) {
                                throw new IllegalArgumentException("For " + day + ", start time (" + startTimeStr + ") must be before end time (" + endTimeStr + ").");
                            }
                        }
                        pstmtInsert.setTime(4, startTimeSql);
                        pstmtInsert.setTime(5, endTimeSql);
                        pstmtInsert.addBatch();
                    }
                    pstmtInsert.executeBatch();
                }
                
				/*
				 * String allowUnselectedParam = request.getParameter("allowUnselectedDays");
				 * boolean allow = "true".equalsIgnoreCase(allowUnselectedParam);
				 * Configuration.saveProperty(tenantId, "TimeDayAllowUnselected",
				 * String.valueOf(allow));
				 * " as: " + allow);
				 */

                conn.commit();
                successMessage = "Time/Day punch restrictions saved successfully!";
            } catch (SQLException | IllegalArgumentException e) {
                errorMessage = e.getMessage();
                if (e instanceof SQLException) {
                    errorMessage = "Database error: " + e.getMessage();
                }
                
                StringBuilder logMessage = new StringBuilder("Save failed for TenantID: " + tenantId + ". Error: " + e.getMessage() + "\nReceived Parameters:\n");
                Enumeration<String> parameterNames = request.getParameterNames();
                while (parameterNames.hasMoreElements()) {
                    String paramName = parameterNames.nextElement();
                    logMessage.append(paramName).append(" = ").append(request.getParameter(paramName)).append("\n");
                }
                logger.log(Level.SEVERE, logMessage.toString(), e);

                if (conn != null) try { conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Rollback failed.", ex); }
            } finally {
                if (conn != null) {
                    try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e) { logger.log(Level.WARNING, "Error closing connection.", e); }
                }
            }
        } else {
            errorMessage = "Invalid server action requested.";
        }
        
        if (errorMessage != null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"success\": false, \"message\": \"" + escapeJson(errorMessage) + "\"}");
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write("{\"success\": true, \"message\": \"" + escapeJson(successMessage) + "\"}");
        }
    }
}
