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

    private String getSettingValue(Map<String, Object> daySetting, String key, String defaultValue) {
        if (daySetting == null || daySetting.get(key) == null || String.valueOf(daySetting.get(key)).isEmpty()) {
            return defaultValue;
        }
        return String.valueOf(daySetting.get(key));
    }

    private boolean getBooleanSettingValue(Map<String, Object> daySetting, String key, boolean defaultValue) {
        if (daySetting == null || daySetting.get(key) == null) {
            return defaultValue;
        }
        Object val = daySetting.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return "true".equalsIgnoreCase(String.valueOf(val));
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
%>

<%
    String pageLoadErrorMessage = (String) request.getAttribute("pageLoadErrorMessage");
    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP"); 
    boolean allowUnselectedDays = Boolean.TRUE.equals(request.getAttribute("allowUnselectedDays"));

    String cancelUrl = request.getContextPath() + "/settings.jsp";
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        cancelUrl = request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" +
                     URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
    }

    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> restrictions = (Map<String, Map<String, Object>>) request.getAttribute("timeRestrictions");
    if (restrictions == null) {
        restrictions = new HashMap<>();
    }
    List<String> daysOfWeek = Arrays.asList("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Time/Day Punch Restrictions<% if(pageIsInWizardMode){ %> - Setup Wizard<% } %></title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/settings.css?v=<%= System.currentTimeMillis() %>">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
    <style>
        .config-container { padding: 20px;
        max-width: 950px; margin: 20px auto; background-color:#fff; border-radius:8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1);
        }
        #timeDayRestrictionsForm {
            display: flex;
            flex-direction: column;
            align-items: center; 
        }
        #timeDayRestrictionsForm > .setting-item,
        #timeDayRestrictionsForm > .day-restriction-block {
             width: 100%;
             max-width: 750px;
        }
        .setting-item { margin-bottom: 25px; padding-bottom: 25px; border-bottom: 1px solid #eee;
        }
        .setting-item:last-of-type { border-bottom: none;
        }
        .section-heading { font-size: 1.3em; color: #004080; margin-bottom: 15px; padding-bottom: 10px;
        border-bottom: 1px solid #f0f0f0; }
        .section-heading .fas, .section-heading .far { margin-right: 12px;
        }
        .day-restriction-block { margin-bottom: 20px; padding-bottom: 20px; border-bottom: 1px solid #f0f0f0;
        }
        .day-restriction-block:last-child { border-bottom: none;
        }
        .day-header { font-size: 1.2em; font-weight: bold; color: #005A9C; margin-bottom: 12px;
        }
        .day-settings-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 20px;
        margin-bottom: 10px;
        }
        .day-settings-row .toggle-group { display: flex; align-items: center; flex-basis: auto;
        flex-shrink: 0; }
        .day-settings-row .slider-label { margin-right: 10px; font-weight: normal; color: #333;
        }
        .day-settings-row .switch { vertical-align: middle;
        }
        .time-inputs-group { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; flex-grow: 1;
        }
        .time-inputs-group .time-window-label { font-size: 0.9em; color: #555; font-weight: 500; margin-right: 5px;
        white-space: nowrap; flex-shrink: 0; }
        .time-inputs-group .time-input-pair { display: flex; align-items: center;
        gap: 5px; white-space: nowrap; }
        .time-inputs-group .time-input-pair label { font-weight: normal; color: #555;
        font-size:0.9em; }
        .time-inputs-group input[type="time"] { padding: 7px 10px; border: 1px solid #ccc;
        border-radius: 4px; width: 140px;
        font-size: 0.9em; font-family: 'Roboto', sans-serif; }
        .time-inputs-group input[type="time"]:disabled { background-color: #e9ecef;
        color: #6c757d; cursor: not-allowed; border-color: #ced4da; }
        .form-actions { margin-top: 30px; padding-top: 20px;
        border-top: 1px solid #ddd; text-align: right; }
        .page-message { padding: 10px 15px;
        margin-bottom: 20px !important; border-radius: 4px; display: flex; align-items: center;}
        .page-message .fas { margin-right: 10px;
        font-size: 1.2em;}
        .error-message { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb;
        }
        h1 .far, h1 .fas { margin-right: 10px;
        } 
    </style>
</head>
<body class="reports-page">
<div id="toast-container" class="toast-notification-container"></div>
    <% if (!pageIsInWizardMode) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
         <div class="wizard-header" style="background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; border-radius: 0 0 5px 5px;">
            <h2>Company Setup: Time/Day Punch Restrictions</h2>
             <p>Configure when employees are allowed to punch in/out based on day and time.</p>
        </div>
    <% } %>

    <div class="parent-container config-container">
        <h1><i class="far fa-calendar-alt"></i> Configure Time/Day Punch Restrictions</h1>
        
        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div><% } %>
        
        <form id="timeDayRestrictionsForm" action="<%= request.getContextPath() %>/TimeDayRestrictionServlet" method="POST" data-cancel-url="<%= cancelUrl %>">
           <input type="hidden" name="action" value="saveTimeDayRestrictions">
            <% if (pageIsInWizardMode && wizardReturnStepForJSP != null) { %>
                 <input type="hidden" name="wizardModeActive" value="true">
                <input type="hidden" name="wizardReturnStep" value="<%= escapeHtml(wizardReturnStepForJSP) %>">
            <% } %>

            <div class="setting-item">
                 <h4 class="section-heading"><i class="fas fa-cogs"></i> Global Rule for Disabled Days</h4>
                <div class="day-settings-row">
                    <div class="toggle-group">
                        <label for="allowUnselectedDays" class="slider-label">Rule for days with restriction&nbsp;<strong>disabled</strong>:</label>
                         <label class="switch">
                            <input type="checkbox" id="allowUnselectedDays" name="allowUnselectedDays" value="true" <%= allowUnselectedDays ? "checked" : "" %> data-save-url="${pageContext.request.contextPath}/SettingsServlet">
                            <span class="slider round"></span>
                        </label>
                        <span id="unselectedDayActionText" style="margin-left: 15px; font-weight: 500; color: #005A9C;">
                            <%= allowUnselectedDays ? "Allow punches all day" : "Do not allow punches" %>
                        </span>
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
                         <span class="time-window-label">Set time for all days:</span>
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
                 <div class="setting-info" style="margin-top: 10px;">
                    Use this to quickly set the same time window for all seven days. The individual day settings below will be hidden when this is active.
                </div>
            </div>

            <% for (String day : daysOfWeek) {
                Map<String, Object> currentDaySetting = restrictions.getOrDefault(day, new HashMap<>());
                boolean isDayRestricted = getBooleanSettingValue(currentDaySetting, "isRestricted", false);
                String startTime = getSettingValue(currentDaySetting, "startTime", "");
                String endTime = getSettingValue(currentDaySetting, "endTime", "");
            %>
            <div class="day-restriction-block">
                <div class="day-header"><%= day %></div>
                <div class="day-settings-row">
                    <div class="toggle-group">
                        <label for="isRestricted_<%= day %>" class="slider-label">Enable Restriction:</label>
                         <label class="switch">
                            <input type="checkbox" id="isRestricted_<%= day %>" name="isRestricted_<%= day %>" value="true" <%= isDayRestricted ? "checked" : "" %>>
                            <span class="slider round"></span>
                        </label>
                    </div>
                    <div class="time-inputs-group" id="timeInputsGroup_<%= day %>">
                        <span class="time-window-label" id="timeWindowHeading_<%= day %>">Allowed Time Window:</span>
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
            <% } %>
            <div class="form-actions">
                <a href="<%= cancelUrl %>" class="glossy-button text-grey"><i class="fas fa-arrow-left"></i> Back to Settings</a>
                <button type="button" id="saveTimeDaySettingsBtn" class="submit-btn glossy-button text-green"><i class="fas fa-save"></i> Save Time/Day Settings</button>
            </div>
        </form>
    </div>

    <%@ include file="/WEB-INF/includes/notification-modals.jspf" %>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
    <script src="${pageContext.request.contextPath}/js/timeDayRestrictions.js?v=<%= System.currentTimeMillis() %>"></script>
</body>
</html>