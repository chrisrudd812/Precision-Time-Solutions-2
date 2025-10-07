<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" session="true" %>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>

<%!
    private static final Logger jspTimeDayRestrictLogger = Logger.getLogger("configureTimeDayRestrictions_jsp_wizard_v7_final");
    private String getSettingValue(Map<String, Object> daySetting, String key, String defaultValue) { if (daySetting == null || daySetting.get(key) == null || String.valueOf(daySetting.get(key)).isEmpty()) { return defaultValue; } return String.valueOf(daySetting.get(key)); }
    private boolean getBooleanSettingValue(Map<String, Object> daySetting, String key, boolean defaultValue) { if (daySetting == null || daySetting.get(key) == null) { return defaultValue; } Object val = daySetting.get(key); if (val instanceof Boolean) return (Boolean) val; return "true".equalsIgnoreCase(String.valueOf(val)); }
    private String escapeHtml(String text) { if (text == null) return ""; return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
%>
<%
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP"); 
    boolean allowUnselectedDays = Boolean.TRUE.equals(request.getAttribute("allowUnselectedDays"));
    String cancelUrl = request.getContextPath() + "/settings.jsp";
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        cancelUrl = request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" + URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
    }
    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> restrictions = (Map<String, Map<String, Object>>) request.getAttribute("timeRestrictions");
    if (restrictions == null) { restrictions = new HashMap<>(); }
    List<String> daysOfWeek = Arrays.asList("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Time/Day Punch Restrictions<% if(pageIsInWizardMode){ %> - Setup Wizard<% } %></title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/common.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/modals.css?v=<%= System.currentTimeMillis() %>">
    <%-- MODIFIED: Added missing link to settings.css for the 'Saved!' message style --%>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/settings.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/configureTimeDayRestrictions.css?v=<%= System.currentTimeMillis() %>">
</head>
<body>
    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
         <div class="wizard-header">
            <h2>Company Setup: Time/Day Punch Restrictions</h2>
             <p>Configure when employees are allowed to punch in/out based on day and time.</p>
        </div>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="far fa-calendar-alt"></i> Configure Time/Day Punch Restrictions</h1>
        
        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>
        
        <div class="content-display-area">
            <form id="timeDayRestrictionsForm" action="<%= request.getContextPath() %>/TimeDayRestrictionServlet" method="POST" data-cancel-url="<%= cancelUrl %>">
                <input type="hidden" name="action" value="saveTimeDayRestrictions">
                <% if (pageIsInWizardMode && wizardReturnStepForJSP != null) { %>
                     <input type="hidden" name="wizardModeActive" value="true">
                    <input type="hidden" name="wizardReturnStep" value="<%= escapeHtml(wizardReturnStepForJSP) %>">
                <% } %>
    
                <div class="setting-item no-border">
                    <h4 class="section-heading"><i class="fas fa-cogs"></i> Global Rule for Disabled Days</h4>
                    <div class="day-settings-row">
                        <div class="toggle-group" style="align-items: center;">
                            <label for="allowUnselectedDays" class="slider-label">Days with restriction <strong>disabled</strong>:</label>
                            <label class="switch">
                                <input type="checkbox" id="allowUnselectedDays" name="allowUnselectedDays" value="true" <%= allowUnselectedDays ? "checked" : "" %> data-save-url="${pageContext.request.contextPath}/SettingsServlet">
                                <span class="slider round"></span>
                            </label>
                            <span id="unselectedDayActionText" style="margin-left: 15px; font-weight: 500; color: #005A9C;">
                                <%= allowUnselectedDays ? "Allow punches all day" : "Do not allow punches" %>
                            </span>
                            <%-- MODIFIED: Added span for the status message --%>
                            <span id="allowUnselectedDays-status" class="save-status"></span>
                        </div>
                    </div>
                    <div class="setting-info" style="margin-top: 10px;">
                        This controls the behavior for any day below where "Enable Restriction" is turned off (Allow or Reject All Day).
                    </div>
                </div>
    
                <div class="setting-item">
                     <h4 class="section-heading"><i class="fas fa-layer-group"></i> Apply to All Days</h4>
                     <div class="day-settings-row">
                        <div class="toggle-group">
                            <label for="applyToAllToggle" class="slider-label">Enable "Apply to All":</label>
                            <label class="switch">
                                <input type="checkbox" id="applyToAllToggle">
                                <span class="slider round"></span>
                            </label>
                        </div>
                     </div>
                     <div class="day-settings-row" id="masterRow" style="display: none; margin-top: 15px; background-color: #f7f7f7; padding: 15px; border-radius: 5px;">
                         <div class="time-inputs-group">
                             <%-- MODIFIED: Moved this label to be above the inputs --%>
                             <label class="time-window-label">Set time for all days:</label>
                             <div class="time-input-pair-wrapper">
                                 <div class="time-input-pair">
                                     <label for="masterStartTime">From:</label>
                                     <input type="time" class="time-input-field" id="masterStartTime">
                                 </div>
                                 <div class="time-input-pair">
                                     <label for="masterEndTime">To:</label>
                                     <input type="time" class="time-input-field" id="masterEndTime">
                                 </div>
                            </div>
                         </div>
                     </div>
                     <div class="setting-info" style="margin-top: 10px;">
                        Use this to quickly set the same time window for all seven days.
                     </div>
                </div>
    
                <% for (String day : daysOfWeek) {
                    Map<String, Object> currentDaySetting = restrictions.getOrDefault(day, new HashMap<>());
                    boolean isDayRestricted = getBooleanSettingValue(currentDaySetting, "isRestricted", false);
                    String startTime = getSettingValue(currentDaySetting, "startTime", "");
                    String endTime = getSettingValue(currentDaySetting, "endTime", "");
                %>
                <div class="day-restriction-block">
                    <div class="day-header-row">
                        <div class="day-header"><%= day %></div>
                        <label class="time-window-label" id="timeWindowHeading_<%= day %>">Allowed Time Window:</label>
                    </div>
                    <div class="day-settings-row">
                        <div class="toggle-group">
                            <label for="isRestricted_<%= day %>" class="slider-label">Enable Restriction:</label>
                            <label class="switch">
                                <input type="checkbox" id="isRestricted_<%= day %>" name="isRestricted_<%= day %>" value="true" <%= isDayRestricted ? "checked" : "" %>>
                                <span class="slider round"></span>
                            </label>
                        </div>
                        <div class="time-inputs-group" id="timeInputsGroup_<%= day %>">
                             <div class="time-input-pair-wrapper">
                                <div class="time-input-pair">
                                    <label for="startTime_<%= day %>">From:</label>
                                    <input type="time" class="time-input-field" id="startTime_<%= day %>" name="startTime_<%= day %>" value="<%= startTime %>">
                                </div>
                                <div class="time-input-pair">
                                    <label for="endTime_<%= day %>">To:</label>
                                    <input type="time" class="time-input-field" id="endTime_<%= day %>" name="endTime_<%= day %>" value="<%= endTime %>">
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
                <% } %>
                <div class="form-actions">
                    <button type="button" id="saveTimeDaySettingsBtn" class="submit-btn glossy-button text-green"><i class="fas fa-save"></i> Save Settings</button>
                    <a href="<%= cancelUrl %>" class="glossy-button text-red"><i class="fas fa-arrow-left"></i> Back to Settings</a>
                </div>
            </form>
        </div>
    </div>

    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="${pageContext.request.contextPath}/js/timeDayRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>