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
    private static final Logger jspTimeDayRestrictLogger = Logger.getLogger("configureTimeDayRestrictions_jsp_wizard_v2");

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
    String saveSuccessMessage = (String) request.getAttribute("saveSuccessMessage");
    String errorMessageFromPost = (String) request.getAttribute("errorMessageJSP");

    // Determine if in wizard mode based on attributes set by the servlet
    boolean pageIsInWizardMode = Boolean.TRUE.equals(request.getAttribute("pageIsInWizardMode"));
    String wizardReturnStepForJSP = (String) request.getAttribute("wizardReturnStepForJSP"); // e.g., "settings_setup"

    String cancelUrl = request.getContextPath() + "/settings.jsp"; // Default cancel
    if (pageIsInWizardMode && wizardReturnStepForJSP != null && !wizardReturnStepForJSP.isEmpty()) {
        cancelUrl = request.getContextPath() + "/settings.jsp?setup_wizard=true&step=" +
                    URLEncoder.encode(wizardReturnStepForJSP, StandardCharsets.UTF_8.name());
        jspTimeDayRestrictLogger.info("Page in wizard mode. Cancel URL set to: " + cancelUrl);
    } else if (pageIsInWizardMode) {
        // Fallback if wizardReturnStepForJSP isn't set but pageIsInWizardMode is true (shouldn't happen ideally)
        cancelUrl = request.getContextPath() + "/settings.jsp?setup_wizard=true&step=settings_setup";
        jspTimeDayRestrictLogger.warning("Page in wizard mode but wizardReturnStepForJSP is null. Defaulting Cancel URL.");
    }


    @SuppressWarnings("unchecked")
    Map<String, Map<String, Object>> restrictions = (Map<String, Map<String, Object>>) request.getAttribute("timeRestrictions");
    if (restrictions == null) {
        restrictions = new HashMap<>();
        if (pageLoadErrorMessage == null && errorMessageFromPost == null && saveSuccessMessage == null ) {
            // Avoid overwriting error message if one already exists due to tenantId check in servlet
            Object tenantIdAttr = request.getSession(false) != null ? request.getSession(false).getAttribute("TenantID") : null;
            if (tenantIdAttr != null) { // Only show this if tenant context was valid
                 pageLoadErrorMessage = "Could not load current time restriction settings. Defaults are shown.";
            }
        }
    }
    List<String> daysOfWeek = Arrays.asList("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
%>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Configure Time/Day Punch Restrictions<% if(pageIsInWizardMode){ %> - Setup Wizard<% } %></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/settings.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .config-container { padding: 20px; max-width: 950px; margin: 20px auto; background-color:#fff; border-radius:8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }
        .day-restriction-block { margin-bottom: 20px; padding-bottom: 20px; border-bottom: 1px solid #eee; }
        .day-restriction-block:last-child { border-bottom: none; }
        .day-header { font-size: 1.2em; font-weight: bold; color: #005A9C; margin-bottom: 12px; }
        .day-settings-row { display: flex; flex-wrap: wrap; align-items: center; gap: 10px 20px; margin-bottom: 10px; }
        .day-settings-row .toggle-group { display: flex; align-items: center; flex-basis: 280px; flex-shrink: 0; }
        .day-settings-row .slider-label { margin-right: 10px; font-weight: normal; color: #333; }
        .day-settings-row .switch { vertical-align: middle; }
        .time-inputs-group { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; flex-grow: 1; }
        .time-inputs-group .time-window-label { font-size: 0.9em; color: #555; font-weight: 500; margin-right: 5px; white-space: nowrap; flex-shrink: 0; }
        .time-inputs-group .time-input-pair { display: flex; align-items: center; gap: 5px; }
        .time-inputs-group .time-input-pair label { font-weight: normal; color: #555; font-size:0.9em; }
        .time-inputs-group input[type="time"] { padding: 7px 10px; border: 1px solid #ccc; border-radius: 4px; width: 110px; font-family: inherit; font-size: 0.9em; }
        .time-inputs-group input[type="time"]:disabled { background-color: #e9ecef; color: #6c757d; cursor: not-allowed; border-color: #ced4da; }
        .form-actions { margin-top: 30px; text-align: right; }
        .page-message { padding: 10px 15px; margin-bottom: 20px !important; border-radius: 4px; display: flex; align-items: center;}
        .page-message .fas { margin-right: 10px; font-size: 1.2em;}
        .success-message { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .error-message { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        h1 .far, h1 .fas { margin-right: 10px; } 
        .modal { display: none; /* ... existing modal styles ... */ }
        .modal-content { /* ... existing modal styles ... */ }
        #configSaveSuccessModal .modal-content h2 .fas { margin-right: 10px; }
    </style>
</head>
<body class="reports-page">
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
        <p class="setting-info" style="margin-bottom:25px;">
            For each day, enable restrictions and set the "Allowed Time Window" for punches.
            If enabled, "From" and "To" times are required, and "From" must be before "To".
            If disabled, employees can punch any time on that day (subject to other global punch restrictions from the main settings page).
        </p>

        <%-- This modal is for non-wizard mode success messages that keep user on this page --%>
        <div id="configSaveSuccessModal" class="modal">
            <div class="modal-content">
                <h2 id="configSaveSuccessTitle"><i class="fas fa-check-circle"></i> Settings Saved!</h2>
                <p id="configSaveSuccessMessage"></p>
                <div class="button-row">
                    <button type="button" id="configSaveOkButton" class="glossy-button text-blue">OK</button>
                </div>
            </div>
        </div>

        <% if (pageLoadErrorMessage != null && !pageLoadErrorMessage.isEmpty()) { %>
            <div class="page-message error-message"><i class="fas fa-times-circle"></i> <%= escapeHtml(pageLoadErrorMessage) %></div>
        <% } %>
         <% if (errorMessageFromPost != null && !errorMessageFromPost.isEmpty()) { %>
            <div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i> <%= escapeHtml(errorMessageFromPost) %></div>
        <% } %>
         <%-- This success message is for non-wizard POSTs that re-render this page --%>
         <% if (saveSuccessMessage != null && !saveSuccessMessage.isEmpty() && !pageIsInWizardMode) { %>
            <div class="page-message success-message"><i class="fas fa-check-circle"></i> <%= escapeHtml(saveSuccessMessage) %></div>
        <% } %>


        <%
        boolean canDisplayForm = true;
        if (pageLoadErrorMessage != null && !pageLoadErrorMessage.toLowerCase().contains("defaults are shown")) {
             // If there's a critical page load error (not just "defaults shown"), don't show form.
             canDisplayForm = false;
        }
        Object tenantIdForCheck = request.getSession(false) != null ? request.getSession(false).getAttribute("TenantID") : null;
        if (tenantIdForCheck == null) {
            canDisplayForm = false; // No tenant, no form.
        }
        %>

        <% if (canDisplayForm) { %>
        <form id="timeDayRestrictionsForm" action="<%= request.getContextPath() %>/TimeDayRestrictionServlet" method="POST">
            <input type="hidden" name="action" value="saveTimeDayRestrictions">
            <%-- Add hidden fields if in wizard mode --%>
            <% if (pageIsInWizardMode && wizardReturnStepForJSP != null) { %>
                <input type="hidden" name="wizardModeActive" value="true">
                <input type="hidden" name="wizardReturnStep" value="<%= escapeHtml(wizardReturnStepForJSP) %>">
            <% } %>

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
                            <input type="checkbox"
                                   id="isRestricted_<%= day %>"
                                   name="isRestricted_<%= day %>"
                                   value="true"
                                   onchange="toggleTimeInputsState('<%= day %>')"
                                   <%= isDayRestricted ? "checked" : "" %>>
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
                <a href="<%= cancelUrl %>" class="cancel-link"><i class="fas fa-times"></i> Cancel</a>
                <button type="submit" id="saveTimeDaySettingsBtn" class="submit-btn glossy-button text-green"><i class="fas fa-save"></i> Save Changes</button>
            </div>
        </form>
        <% } else if (pageLoadErrorMessage == null) { // Only show this if no other specific error already displayed
            %><div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i>Cannot load configuration page. Invalid session or tenant.</div> <%
        }
        %>
    </div>

    <script>
        function toggleTimeInputsState(day) {
            const checkbox = document.getElementById('isRestricted_' + day);
            const startTimeInput = document.getElementById('startTime_' + day);
            const endTimeInput = document.getElementById('endTime_' + day);
            const dayToggleLabel = document.querySelector('label[for="isRestricted_' + day + '"].slider-label');
            const timeWindowLabelSpan = document.getElementById('timeWindowHeading_' + day);
            const timeInputsGroupDiv = document.getElementById('timeInputsGroup_' + day);


            if (checkbox && startTimeInput && endTimeInput && timeWindowLabelSpan && timeInputsGroupDiv) {
                const isChecked = checkbox.checked;
                startTimeInput.disabled = !isChecked;
                endTimeInput.disabled = !isChecked;
                startTimeInput.required = isChecked;
                endTimeInput.required = isChecked;
                
                timeInputsGroupDiv.style.opacity = isChecked ? '1' : '0.5';
                timeInputsGroupDiv.style.pointerEvents = isChecked ? 'auto' : 'none';


                if (dayToggleLabel) {
                    dayToggleLabel.classList.toggle('disabled-text', !isChecked && checkbox.disabled);
                }
                if (isChecked && document.activeElement !== startTimeInput && document.activeElement !== endTimeInput) {
                    setTimeout(() => { startTimeInput.focus(); }, 50);
                }
            }
        }

        document.addEventListener('DOMContentLoaded', function() {
            const days = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];
            days.forEach(day => {
                const checkbox = document.getElementById('isRestricted_' + day);
                if (checkbox) { toggleTimeInputsState(day); } // Initialize state on load
            });

            const form = document.getElementById('timeDayRestrictionsForm');
            if (form) {
                form.addEventListener('submit', function(event) {
                    let validationError = false; let firstErrorField = null;
                    days.forEach(day => {
                        const restrictCheckbox = document.getElementById('isRestricted_' + day);
                        if (restrictCheckbox && restrictCheckbox.checked) {
                            const startTimeInput = document.getElementById('startTime_' + day);
                            const endTimeInput = document.getElementById('endTime_' + day);
                            const startTime = startTimeInput.value; const endTime = endTimeInput.value;
                            if (!startTime) { alert("Error for " + day + ": 'From' time is required when restriction is enabled."); if (!firstErrorField) firstErrorField = startTimeInput; validationError = true; }
                            if (!endTime) { alert("Error for " + day + ": 'To' time is required when restriction is enabled."); if (!firstErrorField) firstErrorField = endTimeInput; validationError = true; }
                            if (startTime && endTime && startTime >= endTime) { alert("Error for " + day + ": 'From' time must be before 'To' time."); if (!firstErrorField) firstErrorField = startTimeInput; validationError = true; }
                        }
                    });
                    if (validationError) { 
                        event.preventDefault(); 
                        if (firstErrorField) { setTimeout(() => { firstErrorField.focus(); }, 50);}
                    } else {
                        const submitButton = document.getElementById('saveTimeDaySettingsBtn');
                        if(submitButton) {
                             submitButton.disabled = true;
                             submitButton.innerHTML = '<i class="fas fa-spinner fa-spin"></i> Saving...';
                        }
                    }
                });
            }

            // Non-wizard success message display logic (modal)
            const saveSuccessMsgFromServletJS = "<%= saveSuccessMessage != null ? escapeHtml(saveSuccessMessage) : "" %>";
            const pageIsInWizardModeJS = <%= pageIsInWizardMode %>;

            const successModalEl = document.getElementById('configSaveSuccessModal');
            const successModalMsgEl = document.getElementById('configSaveSuccessMessage');
            const successModalOkBtnEl = document.getElementById('configSaveOkButton');

            // Only show modal if NOT in wizard mode and there's a success message
            if (!pageIsInWizardModeJS && saveSuccessMsgFromServletJS && successModalEl && successModalMsgEl && successModalOkBtnEl) {
                successModalMsgEl.textContent = saveSuccessMsgFromServletJS;
                if(typeof window.showModal === 'function') window.showModal(successModalEl); else successModalEl.style.display = 'flex';

                // Re-attach listener to handle dynamic scenarios if needed, or ensure one-time use.
                const newOkButton = successModalOkBtnEl.cloneNode(true);
                successModalOkBtnEl.parentNode.replaceChild(newOkButton, successModalOkBtnEl);
                
                newOkButton.addEventListener('click', function() {
                    if(typeof window.hideModal === 'function') window.hideModal(successModalEl); else successModalEl.style.display = 'none';
                    // For non-wizard, we might just stay on the page or reload to reflect changes
                    // No automatic redirect to settings.jsp here, servlet forward handles page re-render.
                    // We can clear URL params if any message was passed via URL
                    if (window.history.replaceState && (window.location.search.includes("message=") || window.location.search.includes("error="))) {
                         window.history.replaceState({}, document.title, window.location.pathname);
                    }
                }, { once: true }); // Ensure listener is only active once per modal display
            }
        });
    </script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>