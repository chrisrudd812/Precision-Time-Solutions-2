<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="jakarta.servlet.http.HttpSession" %>
<%@ page import="timeclock.Configuration"%>
<%@ page import="java.sql.SQLException" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="java.util.logging.Logger" %>
<%@ page import="java.util.logging.Level" %>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.DayOfWeek" %>
<%@ page import="java.time.temporal.TemporalAdjusters" %>
<%@ page import="java.util.Locale" %>

<%!
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    private String escapeForJavaScriptString(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t")
                    .replace("<", "\\u003C") 
                    .replace(">", "\\u003E");
    }
    private static final Logger jspSettingsPageLogger = Logger.getLogger("settings_jsp_wizard_v5_preserve_changes");
%>
<%
    String pageLevelError_settings = null;
    String pageLevelSuccess_settings = null;

    String paramError = request.getParameter("error");
    String paramMessage = request.getParameter("message");
    String restrictionConfigured = request.getParameter("restrictionConfigured");
    if (paramError != null && !paramError.isEmpty()) pageLevelError_settings = paramError;
    if (paramMessage != null && !paramMessage.isEmpty()) pageLevelSuccess_settings = paramMessage;
    if (restrictionConfigured != null && !restrictionConfigured.isEmpty() && pageLevelSuccess_settings != null) {
        pageLevelSuccess_settings = "Successfully configured " + restrictionConfigured.replace("timeDay", "Time/Day") + " restrictions. " + pageLevelSuccess_settings;
    }


    HttpSession currentSession_settings = request.getSession(false);
    Integer tenantId_settings = null;
    String userPermissions_settings = null;
    String companyNameForWizardHeader = "Your Company";

    if (currentSession_settings != null) {
        Object tenantIdObj = currentSession_settings.getAttribute("TenantID");
        if (tenantIdObj instanceof Integer) tenantId_settings = (Integer) tenantIdObj;
        userPermissions_settings = (String) currentSession_settings.getAttribute("Permissions");
        Object companyNameSessionObj = currentSession_settings.getAttribute("CompanyNameSignup");
        if (companyNameSessionObj instanceof String && !((String)companyNameSessionObj).isEmpty()) {
            companyNameForWizardHeader = (String) companyNameSessionObj;
        }
        if (!"Administrator".equalsIgnoreCase(userPermissions_settings)) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Access Denied.", StandardCharsets.UTF_8.name()));
            return;
        }
    } else {
        response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + URLEncoder.encode("Session expired.", StandardCharsets.UTF_8.name()));
        return;
    }
    if (tenantId_settings == null || tenantId_settings <= 0) {
        pageLevelError_settings = (pageLevelError_settings == null ? "" : pageLevelError_settings + " ") + "Invalid session.";
    }

    boolean inSetupWizardMode_JSP = false;
    String companyStateFromSignup = null;
    String wizardStepForPage = null;
    if (currentSession_settings != null && Boolean.TRUE.equals(currentSession_settings.getAttribute("startSetupWizard"))) {
        String requestWizardParam = request.getParameter("setup_wizard");
        String requestStepParam = request.getParameter("step");
        String sessionWizardStep = (String) currentSession_settings.getAttribute("wizardStep");
        if ("true".equalsIgnoreCase(requestWizardParam) && "settings_setup".equals(sessionWizardStep) && ("settings_setup".equals(requestStepParam) || requestStepParam == null) ) {
            inSetupWizardMode_JSP = true;
            wizardStepForPage = sessionWizardStep;
            companyStateFromSignup = (String) currentSession_settings.getAttribute("SignupCompanyState");
        }
    }

    final String STD_DEFAULT_PAY_PERIOD_TYPE = "Weekly";
    final String STD_DEFAULT_FIRST_DAY_OF_WEEK = "Sunday";
    LocalDate today = LocalDate.now();
    DayOfWeek std_defaultFirstDayEnum = DayOfWeek.SUNDAY;
    String std_configuredFirstDayInitial = STD_DEFAULT_FIRST_DAY_OF_WEEK;
    if(tenantId_settings != null && tenantId_settings > 0 && pageLevelError_settings == null && !inSetupWizardMode_JSP) { std_configuredFirstDayInitial = Configuration.getProperty(tenantId_settings, "FirstDayOfWeek", STD_DEFAULT_FIRST_DAY_OF_WEEK); }
    try { if (std_configuredFirstDayInitial != null && !std_configuredFirstDayInitial.isEmpty()) { std_defaultFirstDayEnum = DayOfWeek.valueOf(std_configuredFirstDayInitial.toUpperCase(Locale.ENGLISH)); }} catch (Exception e) { std_defaultFirstDayEnum = DayOfWeek.SUNDAY; }
    final String STD_DEFAULT_PAY_PERIOD_START_DATE = today.with(TemporalAdjusters.previousOrSame(std_defaultFirstDayEnum)).format(DateTimeFormatter.ISO_DATE);
    final String STD_DEFAULT_OVERTIME_RULE_MODE = "Manual"; final String STD_DEFAULT_OVERTIME_STATE = ""; final String STD_FIXED_OVERTIME_ENABLED_VALUE = "true"; final String STD_DEFAULT_OVERTIME_RATE = "1.5";
    final String STD_DEFAULT_OVERTIME_DAILY_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_DAILY_THRESHOLD = "8.0"; final String STD_DEFAULT_OVERTIME_DOUBLE_TIME_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_DOUBLE_TIME_THRESHOLD = "12.0";
    final String STD_DEFAULT_OVERTIME_SEVENTH_DAY_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_SEVENTH_DAY_OT_THRESHOLD = "8.0"; final String STD_DEFAULT_OVERTIME_SEVENTH_DAY_DT_THRESHOLD = "8.0";
    final String STD_DEFAULT_PUNCH_RESTRICTIONS_ENABLED = "false";

    String currentPayPeriod = STD_DEFAULT_PAY_PERIOD_TYPE; String currentFirstDayOfWeek = STD_DEFAULT_FIRST_DAY_OF_WEEK; String currentPayPeriodStartDate = STD_DEFAULT_PAY_PERIOD_START_DATE;
    String currentGracePeriod = "0"; String currentOvertimeRuleMode = STD_DEFAULT_OVERTIME_RULE_MODE; String currentOvertimeState = STD_DEFAULT_OVERTIME_STATE; boolean currentPunchRestrictionsEnabled = Boolean.parseBoolean(STD_DEFAULT_PUNCH_RESTRICTIONS_ENABLED);
    boolean currentRestrictByTimeDay = false; boolean currentRestrictByLocation = false; boolean currentRestrictByNetwork = false; boolean currentRestrictByDevice = false; String currentOvertimeRate = STD_DEFAULT_OVERTIME_RATE;
    boolean currentOvertimeDailyEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_DAILY_ENABLED); String currentOvertimeDailyThreshold = STD_DEFAULT_OVERTIME_DAILY_THRESHOLD; boolean currentOvertimeDoubleTimeEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_DOUBLE_TIME_ENABLED); String currentOvertimeDoubleTimeThreshold = STD_DEFAULT_OVERTIME_DOUBLE_TIME_THRESHOLD; boolean currentOvertimeSeventhDayEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_SEVENTH_DAY_ENABLED);
    String currentOvertimeSeventhDayOTThreshold = STD_DEFAULT_OVERTIME_SEVENTH_DAY_OT_THRESHOLD; String currentOvertimeSeventhDayDTThreshold = STD_DEFAULT_OVERTIME_SEVENTH_DAY_DT_THRESHOLD;

    if (tenantId_settings != null && tenantId_settings > 0 && pageLevelError_settings == null) {
        boolean isFirstWizardSettingsLoad = false;
        if (inSetupWizardMode_JSP) {
            String firstLoadCheck = Configuration.getProperty(tenantId_settings, "WizardSettingsInitialized");
            if (firstLoadCheck == null) {
                isFirstWizardSettingsLoad = true;
            }
        }

        if (inSetupWizardMode_JSP && isFirstWizardSettingsLoad) {
            jspSettingsPageLogger.info("[settings.jsp] FIRST WIZARD LOAD: Applying and SAVING Wizard Defaults for TenantID: " + tenantId_settings);
            currentPayPeriod = "Weekly"; currentFirstDayOfWeek = "Sunday"; currentPayPeriodStartDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE); currentGracePeriod = "0"; currentOvertimeRuleMode = "AutoByState";
            currentOvertimeState = (companyStateFromSignup != null && !companyStateFromSignup.isEmpty()) ? companyStateFromSignup : "FLSA"; currentPunchRestrictionsEnabled = false; currentRestrictByTimeDay = false;
            currentRestrictByLocation = false; currentRestrictByNetwork = false; currentRestrictByDevice = false; currentOvertimeRate = "1.5"; currentOvertimeDailyEnabled = false; currentOvertimeDailyThreshold = "8.0";
            currentOvertimeDoubleTimeEnabled = false; currentOvertimeDoubleTimeThreshold = "12.0"; currentOvertimeSeventhDayEnabled = false; currentOvertimeSeventhDayOTThreshold = "8.0"; currentOvertimeSeventhDayDTThreshold = "8.0";
            try {
                Configuration.saveProperty(tenantId_settings, "PayPeriodType", currentPayPeriod);
                Configuration.saveProperty(tenantId_settings, "FirstDayOfWeek", currentFirstDayOfWeek); Configuration.saveProperty(tenantId_settings, "PayPeriodStartDate", currentPayPeriodStartDate); Configuration.saveProperty(tenantId_settings, "GracePeriod", currentGracePeriod); Configuration.saveProperty(tenantId_settings, "OvertimeRuleMode", currentOvertimeRuleMode); Configuration.saveProperty(tenantId_settings, "OvertimeState", currentOvertimeState);
                Configuration.saveProperty(tenantId_settings, "PunchRestrictionsEnabled", String.valueOf(currentPunchRestrictionsEnabled)); Configuration.saveProperty(tenantId_settings, "RestrictByTimeDay", "false"); Configuration.saveProperty(tenantId_settings, "RestrictByLocation", "false"); Configuration.saveProperty(tenantId_settings, "RestrictByNetwork", "false"); Configuration.saveProperty(tenantId_settings, "RestrictByDevice", "false"); Configuration.saveProperty(tenantId_settings, "Overtime", STD_FIXED_OVERTIME_ENABLED_VALUE);
                Configuration.saveProperty(tenantId_settings, "OvertimeRate", currentOvertimeRate); Configuration.saveProperty(tenantId_settings, "OvertimeDaily", String.valueOf(currentOvertimeDailyEnabled)); Configuration.saveProperty(tenantId_settings, "OvertimeDailyThreshold", currentOvertimeDailyThreshold); Configuration.saveProperty(tenantId_settings, "OvertimeDoubleTimeEnabled", String.valueOf(currentOvertimeDoubleTimeEnabled)); Configuration.saveProperty(tenantId_settings, "OvertimeDoubleTimeThreshold", currentOvertimeDoubleTimeThreshold);
                Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayEnabled", String.valueOf(currentOvertimeSeventhDayEnabled)); Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayOTThreshold", currentOvertimeSeventhDayOTThreshold); Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayDTThreshold", currentOvertimeSeventhDayDTThreshold);
                Configuration.saveProperty(tenantId_settings, "WizardSettingsInitialized", "true");
                if (pageLevelSuccess_settings == null) pageLevelSuccess_settings = "Initial company settings populated. Review and click 'Next'.";
            } catch (SQLException e) { jspSettingsPageLogger.log(Level.SEVERE, "[settings.jsp] WIZARD DEFAULTS SAVE FAILED T:" + tenantId_settings, e);
            pageLevelError_settings = "Error saving initial settings: " + e.getMessage(); }
        } else { 
            try {
                 if (Configuration.getProperty(tenantId_settings, "PayPeriodType") == null) Configuration.saveProperty(tenantId_settings, "PayPeriodType", STD_DEFAULT_PAY_PERIOD_TYPE);
                 if (Configuration.getProperty(tenantId_settings, "FirstDayOfWeek") == null) Configuration.saveProperty(tenantId_settings, "FirstDayOfWeek", STD_DEFAULT_FIRST_DAY_OF_WEEK); if (Configuration.getProperty(tenantId_settings, "PayPeriodStartDate") == null) Configuration.saveProperty(tenantId_settings, "PayPeriodStartDate", STD_DEFAULT_PAY_PERIOD_START_DATE);
                 if (Configuration.getProperty(tenantId_settings, "GracePeriod") == null) Configuration.saveProperty(tenantId_settings, "GracePeriod", "0"); if (Configuration.getProperty(tenantId_settings, "OvertimeRuleMode") == null) Configuration.saveProperty(tenantId_settings, "OvertimeRuleMode", STD_DEFAULT_OVERTIME_RULE_MODE);
                 if (Configuration.getProperty(tenantId_settings, "OvertimeState") == null) Configuration.saveProperty(tenantId_settings, "OvertimeState", STD_DEFAULT_OVERTIME_STATE);
                 if (Configuration.getProperty(tenantId_settings, "PunchRestrictionsEnabled") == null) Configuration.saveProperty(tenantId_settings, "PunchRestrictionsEnabled", STD_DEFAULT_PUNCH_RESTRICTIONS_ENABLED); if (!STD_FIXED_OVERTIME_ENABLED_VALUE.equals(Configuration.getProperty(tenantId_settings, "Overtime"))) Configuration.saveProperty(tenantId_settings, "Overtime", STD_FIXED_OVERTIME_ENABLED_VALUE);
                 if (Configuration.getProperty(tenantId_settings, "OvertimeRate") == null) Configuration.saveProperty(tenantId_settings, "OvertimeRate", STD_DEFAULT_OVERTIME_RATE); if (Configuration.getProperty(tenantId_settings, "OvertimeDaily") == null) Configuration.saveProperty(tenantId_settings, "OvertimeDaily", STD_DEFAULT_OVERTIME_DAILY_ENABLED);
                 if (Configuration.getProperty(tenantId_settings, "OvertimeDailyThreshold") == null) Configuration.saveProperty(tenantId_settings, "OvertimeDailyThreshold", STD_DEFAULT_OVERTIME_DAILY_THRESHOLD); if (Configuration.getProperty(tenantId_settings, "OvertimeDoubleTimeEnabled") == null) Configuration.saveProperty(tenantId_settings, "OvertimeDoubleTimeEnabled", STD_DEFAULT_OVERTIME_DOUBLE_TIME_ENABLED);
                 if (Configuration.getProperty(tenantId_settings, "OvertimeDoubleTimeThreshold") == null) Configuration.saveProperty(tenantId_settings, "OvertimeDoubleTimeThreshold", STD_DEFAULT_OVERTIME_DOUBLE_TIME_THRESHOLD); if (Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayEnabled") == null) Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayEnabled", STD_DEFAULT_OVERTIME_SEVENTH_DAY_ENABLED);
                 if (Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayOTThreshold") == null) Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayOTThreshold", STD_DEFAULT_OVERTIME_SEVENTH_DAY_OT_THRESHOLD); if (Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayDTThreshold") == null) Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayDTThreshold", STD_DEFAULT_OVERTIME_SEVENTH_DAY_DT_THRESHOLD);
                 if (Configuration.getProperty(tenantId_settings, "RestrictByTimeDay") == null) Configuration.saveProperty(tenantId_settings, "RestrictByTimeDay", "false");
                 if (Configuration.getProperty(tenantId_settings, "RestrictByLocation") == null) Configuration.saveProperty(tenantId_settings, "RestrictByLocation", "false"); if (Configuration.getProperty(tenantId_settings, "RestrictByNetwork") == null) Configuration.saveProperty(tenantId_settings, "RestrictByNetwork", "false");
                 if (Configuration.getProperty(tenantId_settings, "RestrictByDevice") == null) Configuration.saveProperty(tenantId_settings, "RestrictByDevice", "false");
            } catch (SQLException e) { jspSettingsPageLogger.log(Level.SEVERE, "[settings.jsp] SQLException during standard default initialization T:" + tenantId_settings, e);
            pageLevelError_settings = "Error initializing settings: " + e.getMessage(); }

            currentPayPeriod = Configuration.getProperty(tenantId_settings, "PayPeriodType", STD_DEFAULT_PAY_PERIOD_TYPE);
            currentFirstDayOfWeek = Configuration.getProperty(tenantId_settings, "FirstDayOfWeek", STD_DEFAULT_FIRST_DAY_OF_WEEK);
            currentPayPeriodStartDate = Configuration.getProperty(tenantId_settings, "PayPeriodStartDate", STD_DEFAULT_PAY_PERIOD_START_DATE); currentGracePeriod = Configuration.getProperty(tenantId_settings, "GracePeriod", "0"); currentOvertimeRuleMode = Configuration.getProperty(tenantId_settings, "OvertimeRuleMode", STD_DEFAULT_OVERTIME_RULE_MODE);
            currentOvertimeState = Configuration.getProperty(tenantId_settings, "OvertimeState", STD_DEFAULT_OVERTIME_STATE); currentPunchRestrictionsEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "PunchRestrictionsEnabled", STD_DEFAULT_PUNCH_RESTRICTIONS_ENABLED)); currentRestrictByTimeDay = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByTimeDay", "false"));
            currentRestrictByLocation = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByLocation", "false")); currentRestrictByNetwork = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByNetwork", "false")); currentRestrictByDevice = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByDevice", "false")); currentOvertimeRate = Configuration.getProperty(tenantId_settings, "OvertimeRate", STD_DEFAULT_OVERTIME_RATE);
            currentOvertimeDailyEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeDaily", STD_DEFAULT_OVERTIME_DAILY_ENABLED)); currentOvertimeDailyThreshold = Configuration.getProperty(tenantId_settings, "OvertimeDailyThreshold", STD_DEFAULT_OVERTIME_DAILY_THRESHOLD); currentOvertimeDoubleTimeEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeDoubleTimeEnabled", STD_DEFAULT_OVERTIME_DOUBLE_TIME_ENABLED)); currentOvertimeDoubleTimeThreshold = Configuration.getProperty(tenantId_settings, "OvertimeDoubleTimeThreshold", STD_DEFAULT_OVERTIME_DOUBLE_TIME_THRESHOLD);
            currentOvertimeSeventhDayEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayEnabled", STD_DEFAULT_OVERTIME_SEVENTH_DAY_ENABLED)); currentOvertimeSeventhDayOTThreshold = Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayOTThreshold", STD_DEFAULT_OVERTIME_SEVENTH_DAY_OT_THRESHOLD); currentOvertimeSeventhDayDTThreshold = Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayDTThreshold", STD_DEFAULT_OVERTIME_SEVENTH_DAY_DT_THRESHOLD);
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Global Settings<% if(inSetupWizardMode_JSP) { %> - Company Setup Wizard<% } %></title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/settings.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/navbar.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <style>
        .wizard-header { background-color: #004080; color: white; padding: 15px 20px; text-align: center; margin-bottom:20px; } .page-message { padding: 10px 15px; margin-bottom: 15px; border-radius: 4px; display: flex; align-items: center; } .page-message i { margin-right: 8px; font-size: 1.2em; } .success-message { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; } .error-message { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .modal { display: none; position: fixed; z-index: 1055; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background-color: rgba(0,0,0,0.5); justify-content: center; align-items: center; } .modal.modal-visible { display: flex !important; } .modal-content { background-color: #fefefe; margin: auto; padding: 20px 25px; border: 1px solid #888; border-radius: 8px; width: 90%; max-width: 480px; box-shadow: 0 4px 8px 0 rgba(0,0,0,0.2),0 6px 20px 0 rgba(0,0,0,0.19); text-align:left; } .modal-content h2 { margin-top: 0; font-size: 1.6em; color: #005A9C; padding-bottom: 10px; border-bottom: 1px solid #eee; text-align:center;} .close { color: #aaa; float: right; font-size: 28px; font-weight: bold; cursor:pointer; }
    </style>
</head>
<body>
    <div class="parent-container">
    <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Initial Settings for <%= escapeHtml(companyNameForWizardHeader) %></h2>
            <p>Please review and confirm these settings. You can change them anytime later.</p>
        </div>
    <% } %>
        <h1><i class="fas fa-cogs"></i>Global Settings<% if(inSetupWizardMode_JSP) { %> <span style="font-size:0.8em; color:#555;">(Setup Step)</span><% } %></h1>
        <% if (pageLevelSuccess_settings != null && !pageLevelSuccess_settings.isEmpty()) { %><div class="page-message success-message"><i class="fas fa-check-circle"></i> <%= escapeHtml(pageLevelSuccess_settings) %></div><% } %>
        <% if (pageLevelError_settings != null && !pageLevelError_settings.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i> <%= escapeHtml(pageLevelError_settings) %></div><% } %>

        <% if (tenantId_settings != null && tenantId_settings > 0 && pageLevelError_settings == null) { %>
        <form id="settingsForm" onsubmit="return false;">
            <div class="setting-item">
                <h4 class="section-heading"><i class="fas fa-calendar-alt"></i>Pay Period</h4>
                <div class="form-row">
                    <div class="setting-block"><label for="payPeriodType" class="setting-label-fixed">Pay Period Type:</label><div class="setting-controls-wrapper"><select id="payPeriodType" name="PayPeriodType"><option value="Daily" <% if ("Daily".equals(currentPayPeriod)) out.print(" selected"); %>>Daily</option><option value="Weekly" <% if ("Weekly".equals(currentPayPeriod)) out.print(" selected"); %>>Weekly</option><option value="Bi-Weekly" <% if ("Bi-Weekly".equals(currentPayPeriod)) out.print(" selected"); %>>Bi-Weekly</option><option value="Semi-Monthly" <% if ("Semi-Monthly".equals(currentPayPeriod)) out.print(" selected"); %>>Semi-Monthly</option><option value="Monthly" <% if ("Monthly".equals(currentPayPeriod)) out.print(" selected"); %>>Monthly</option></select><span id="PayPeriodType-status" class="save-status"></span></div></div>
                </div>
                <div class="form-row">
                    <div class="setting-block" id="firstDayOfWeekBlock"><label for="firstDayOfWeek" class="setting-label-fixed">First Day of Work Week:</label><div class="setting-controls-wrapper"><select id="firstDayOfWeek" name="FirstDayOfWeek"><option value="Sunday" <% if ("Sunday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Sunday</option><option value="Monday" <% if ("Monday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Monday</option><option value="Tuesday" <% if ("Tuesday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Tuesday</option><option value="Wednesday" <% if ("Wednesday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Wednesday</option><option value="Thursday" <% if ("Thursday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Thursday</option><option value="Friday" <% if ("Friday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Friday</option><option value="Saturday" <% if ("Saturday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Saturday</option></select><span id="FirstDayOfWeek-status" class="save-status"></span></div></div>
                </div>
                <div class="setting-info">
                    <strong>Note:</strong> This setting is used to determine the 7-day period for calculating weekly overtime.
                </div>
                <div class="form-row">
                    <div class="setting-block" id="payPeriodStartDateBlock"><label for="payPeriodStartDate" class="setting-label-fixed">Pay Period Start Date:</label><div class="setting-controls-wrapper"><input type="date" id="payPeriodStartDate" name="PayPeriodStartDate" value="<%= escapeHtml(currentPayPeriodStartDate) %>"><span id="PayPeriodStartDate-status" class="save-status"></span></div></div>
                </div>
                <div class="form-row">
                    <div class="setting-block" id="payPeriodEndDateBlock"><label class="setting-label-fixed">Calculated Pay Period End:</label><div class="setting-controls-wrapper"><span id="payPeriodEndDateDisplay" class="calculated-date-display"></span></div></div>
                </div>
            </div>
            <div class="setting-item">
                <h4 class="section-heading"><i class="fas fa-user-clock"></i>Tardy / Early Out Rules</h4>
                <div class="form-row">
                    <div class="setting-block"><label for="gracePeriod" class="setting-label-fixed">Grace Period (Minutes):</label><div class="setting-controls-wrapper"><select id="gracePeriod" name="GracePeriod"><% for (int i : new int[]{0, 1, 2, 3, 4, 5, 10, 15, 30, 60}) { %><option value="<%= i %>" <% if (String.valueOf(i).equals(currentGracePeriod)) out.print(" selected"); %>><%= i %></option><% } %></select><span id="GracePeriod-status" class="save-status"></span></div></div>
                </div>
                <div class="setting-info">
                    <strong>Note:</strong> Tardy rules are only applied to employees assigned to a schedule with defined start/end times.
                </div>
            </div>
            <div class="setting-item">
                <h4 class="section-heading"><i class="fas fa-hourglass-half"></i>Overtime Rules</h4>
                <div class="setting-info" style="margin-bottom: 20px;">
                    <strong>Note:</strong> Overtime rules can be complex. For the most current information, please refer to the official guidelines. 
                    <a href="https://www.dol.gov/agencies/whd/minimum-wage/state" target="_blank" rel="noopener noreferrer">Click here for current Overtime rules by State <i class="fas fa-external-link-alt"></i></a>.
                </div>
                <div class="form-row"><div class="setting-block"><label class="setting-label-fixed">Overtime Calculation Mode:</label><div class="setting-controls-wrapper"><div class="radio-group"><span class="styled-radio"><input type="radio" id="otModeManual" name="OvertimeRuleMode" value="Manual" <% if ("Manual".equals(currentOvertimeRuleMode)) out.print(" checked"); %>> <label for="otModeManual">Manual</label></span> <span class="styled-radio"><input type="radio" id="otModeAuto" name="OvertimeRuleMode" value="AutoByState" <% if ("AutoByState".equals(currentOvertimeRuleMode)) out.print(" checked"); %>> <label for="otModeAuto">Automatic by State</label></span></div> <span id="OvertimeRuleMode-status" class="save-status radio-group-status"></span></div></div></div>
                <div id="autoStateOvertimeSection" style="margin-top:15px; <% if (!"AutoByState".equals(currentOvertimeRuleMode)) out.print("display:none;"); %>">
                    <div class="form-row">
                        <div class="setting-block"><label for="overtimeStateSelect" class="setting-label-fixed">Select State:</label><div class="setting-controls-wrapper"> <select id="overtimeStateSelect" name="OvertimeState" style="min-width:250px;"></select> <span id="OvertimeState-status" class="save-status"></span> </div></div>
                    </div>
                    <div id="stateSpecificNotesDisplay" class="setting-info" style="margin-top: 10px;"></div>
                </div>
                <div id="manualOvertimeSettings" style="margin-top:20px; border-top:1px dashed #ccc; padding-top:15px; <% if ("AutoByState".equals(currentOvertimeRuleMode)) { out.print("opacity:0.7;"); } %>"><div class="form-row"><div class="setting-block"><label class="setting-label-fixed">Weekly Overtime (FLSA):</label> <div class="setting-controls-wrapper"><label class="switch"><input type="checkbox" id="overtimeWeeklyEnabled" name="Overtime" value="true" checked disabled><span class="slider round"></span></label><span style="margin-left: 5px;">Enabled (After 40 hours)</span><small class="fixed-setting-note" style="margin-left:10px;">Fixed</small></div></div><div class="setting-block"><label class="setting-label-fixed">Std. Overtime Rate:</label><div class="setting-controls-wrapper"><div class="radio-group"><span class="styled-radio"><input type="radio" id="overtimeRate1.5" name="OvertimeRate" value="1.5" <% if ("1.5".equals(currentOvertimeRate)) out.print(" checked"); %>><label for="overtimeRate1.5">1.5x</label></span> <span class="styled-radio"><input type="radio" id="overtimeRate2.0" name="OvertimeRate" value="2.0" <% if ("2.0".equals(currentOvertimeRate)) out.print(" checked"); %>><label for="overtimeRate2.0">2.0x</label></span></div><span id="OvertimeRate-status" class="save-status radio-group-status"></span></div></div></div><hr style="border:0; border-top: 1px dashed #eee; margin: 15px 0;"><div class="form-row"><div class="setting-block"><label for="overtimeDaily" class="setting-label-fixed">Enable Daily OT:</label><div class="setting-controls-wrapper"><label class="switch"><input type="checkbox" id="overtimeDaily" name="OvertimeDaily" value="true" <% if (currentOvertimeDailyEnabled) out.print(" checked"); %>><span class="slider round"></span></label><span id="OvertimeDaily-status" class="save-status checkbox-status"></span></div></div><div class="setting-block" id="overtimeDailyThresholdBlock" style="<% if (!currentOvertimeDailyEnabled && "Manual".equals(currentOvertimeRuleMode)) out.print("opacity:0.5;"); %>"><label for="overtimeDailyThreshold" class="setting-label-fixed">Daily OT After (Hrs):</label><div class="setting-controls-wrapper"><input type="number" id="overtimeDailyThreshold" name="OvertimeDailyThreshold" min="0.5" max="23.5" step="0.5" placeholder="e.g., 8.0" value="<%= currentOvertimeDailyThreshold %>" class="short-input"><span id="OvertimeDailyThreshold-status" class="save-status"></span></div></div></div><div class="form-row"><div class="setting-block"><label for="overtimeDoubleTimeEnabled" class="setting-label-fixed">Enable Daily DT:</label><div class="setting-controls-wrapper"><label class="switch"><input type="checkbox" id="overtimeDoubleTimeEnabled" name="OvertimeDoubleTimeEnabled" value="true" <% if (currentOvertimeDoubleTimeEnabled) out.print(" checked"); %>><span class="slider round"></span></label><span id="OvertimeDoubleTimeEnabled-status" class="save-status checkbox-status"></span></div></div><div class="setting-block" id="overtimeDoubleTimeThresholdBlock" style="<% if (!currentOvertimeDoubleTimeEnabled && "Manual".equals(currentOvertimeRuleMode)) out.print("opacity:0.5;"); %>"><label for="overtimeDoubleTimeThreshold" class="setting-label-fixed">Daily DT After (Hrs):</label><div class="setting-controls-wrapper"><input type="number" id="overtimeDoubleTimeThreshold" name="OvertimeDoubleTimeThreshold" min="0.5" max="23.5" step="0.5" placeholder="e.g., 12.0" value="<%= currentOvertimeDoubleTimeThreshold %>" class="short-input"><span id="OvertimeDoubleTimeThreshold-status" class="save-status"></span></div></div></div><hr style="border:0; border-top: 1px dashed #eee; margin: 15px 0;"><div class="form-row"><div class="setting-block"><label for="overtimeSeventhDayEnabled" class="setting-label-fixed">Enable 7th Day OT:</label><div class="setting-controls-wrapper"><label class="switch"><input type="checkbox" id="overtimeSeventhDayEnabled" name="OvertimeSeventhDayEnabled" value="true" <% if (currentOvertimeSeventhDayEnabled) out.print(" checked"); %>><span class="slider round"></span></label><span id="OvertimeSeventhDayEnabled-status" class="save-status checkbox-status"></span></div></div></div><div class="form-row" id="seventhDayOTDetailsBlock" style="padding-left:20px; <% if(!currentOvertimeSeventhDayEnabled) out.print("display:none;"); %> <% if (!currentOvertimeSeventhDayEnabled && "Manual".equals(currentOvertimeRuleMode)) out.print("opacity:0.5;"); %>"><div class="setting-block"><label for="overtimeSeventhDayOTThreshold" class="setting-label-fixed" style="font-weight:normal;">7th Day 1.5x Up To (Hrs):</label><div class="setting-controls-wrapper"><input type="number" id="overtimeSeventhDayOTThreshold" name="OvertimeSeventhDayOTThreshold" min="0.5" max="24" step="0.5" placeholder="e.g., 8.0" value="<%= currentOvertimeSeventhDayOTThreshold %>" class="short-input"><span style="margin-left:5px;">hours</span><span id="OvertimeSeventhDayOTThreshold-status" class="save-status"></span></div></div><div class="setting-block"><label for="overtimeSeventhDayDTThreshold" class="setting-label-fixed" style="font-weight:normal;">7th Day 2.0x After (Hrs):</label><div class="setting-controls-wrapper"><input type="number" id="overtimeSeventhDayDTThreshold" name="OvertimeSeventhDayDTThreshold" min="0.5" max="24" step="0.5" placeholder="e.g., 8.0" value="<%= currentOvertimeSeventhDayDTThreshold %>" class="short-input"><span style="margin-left:5px;">hours</span><span id="OvertimeSeventhDayDTThreshold-status" class="save-status"></span></div></div></div></div>
            </div>
            <div class="setting-item"><h4 class="section-heading"><i class="fas fa-fingerprint"></i>Punch Restrictions</h4><div class="setting-info" style="margin-bottom: 15px;"><strong>Note:</strong> Global settings enabled here will apply to ALL employees unless a more specific restriction is set on their individual schedule.</div><div class="setting-block" style="margin-bottom:10px;"><label for="punchRestrictionsEnabled" class="setting-label-fixed">Enable Punch Restrictions:</label><div class="setting-controls-wrapper"><label class="switch"><input type="checkbox" id="punchRestrictionsEnabled" name="PunchRestrictionsEnabled" value="true" <% if (currentPunchRestrictionsEnabled) out.print(" checked"); %>><span class="slider round"></span></label><span id="PunchRestrictionsEnabled-status" class="save-status checkbox-status"></span></div></div><div id="specificPunchRestrictionsGroup" class="sub-settings-group" style="<% if (!currentPunchRestrictionsEnabled) out.print("opacity:0.6;"); %>"><div class="punch-restriction-item"><label class="switch"><input type="checkbox" id="restrictByTimeDay" name="RestrictByTimeDay" value="true" <% if (currentRestrictByTimeDay) out.print(" checked"); %> <% if(!currentPunchRestrictionsEnabled) out.print("disabled");%>><span class="slider round"></span></label><label for="restrictByTimeDay" class="slider-label <% if(!currentPunchRestrictionsEnabled) out.print("disabled-text");%>"><i class="far fa-calendar-alt"></i>Restrict by Time/Day</label><span class="spacer"></span><button type="button" class="configure-button" id="configureTimeDayBtn" <% if(!currentPunchRestrictionsEnabled || !currentRestrictByTimeDay) out.print("disabled");%>>Configure</button></div><div class="punch-restriction-item"><label class="switch"><input type="checkbox" id="restrictByLocation" name="RestrictByLocation" value="true" <% if (currentRestrictByLocation) out.print(" checked"); %> <% if(!currentPunchRestrictionsEnabled) out.print("disabled");%>><span class="slider round"></span></label><label for="restrictByLocation" class="slider-label <% if(!currentPunchRestrictionsEnabled) out.print("disabled-text");%>"><i class="fas fa-map-marker-alt"></i>Restrict by Location<span class="required-asterisk">*</span></label><span class="spacer"></span><button type="button" class="configure-button" id="configureLocationBtn" <% if(!currentPunchRestrictionsEnabled || !currentRestrictByLocation) out.print("disabled");%>>Configure</button></div><div class="punch-restriction-item"><label class="switch"><input type="checkbox" id="restrictByNetwork" name="RestrictByNetwork" value="true" <% if (currentRestrictByNetwork) out.print(" checked"); %> <% if(!currentPunchRestrictionsEnabled) out.print("disabled");%>><span class="slider round"></span></label><label for="restrictByNetwork" class="slider-label <% if(!currentPunchRestrictionsEnabled) out.print("disabled-text");%>"><i class="fas fa-network-wired"></i>Restrict by Network</label><span class="spacer"></span><button type="button" class="configure-button" id="configureNetworkBtn" <% if(!currentPunchRestrictionsEnabled || !currentRestrictByNetwork) out.print("disabled");%>>Configure</button></div><div class="punch-restriction-item"><label class="switch"><input type="checkbox" id="restrictByDevice" name="RestrictByDevice" value="true" <% if (currentRestrictByDevice) out.print(" checked"); %> <% if(!currentPunchRestrictionsEnabled) out.print("disabled");%>><span class="slider round"></span></label><label for="restrictByDevice" class="slider-label <% if(!currentPunchRestrictionsEnabled) out.print("disabled-text");%>"><i class="fas fa-mobile-alt"></i>Restrict by Device</label><span class="spacer"></span><button type="button" class="configure-button" id="configureDeviceBtn" <% if(!currentPunchRestrictionsEnabled || !currentRestrictByDevice) out.print("disabled");%>>Configure</button></div></div><div class="setting-info" style="margin-top: 20px; padding-left: 10px;"><span class="required-asterisk" style="font-size:1em; margin-right: 5px; font-style:normal; font-weight:bold;">*</span> Wi-Fi or device GPS is recommended for employee use on mobile devices for this restriction.</div></div>
        </form>
        <% if (inSetupWizardMode_JSP) { %>
            <div class="wizard-navigation" style="text-align: right; margin-top: 30px; padding-top:20px; border-top: 1px solid #eee;">
                <button type="button" id="wizardSettingsNextButton" class="glossy-button text-green" style="padding: 10px 20px; font-size: 1.1em;">
                    Next: Departments Setup <i class="fas fa-arrow-right"></i>
                </button>
            </div>
        <% } %>
        <% } else {
             if (pageLevelError_settings == null || pageLevelError_settings.isEmpty()) { pageLevelError_settings = "Settings cannot be loaded due to an invalid session or tenant configuration."; } %>
             <div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i><%= escapeHtml(pageLevelError_settings) %></div>
        <% } %>
    </div>
    <div id="notificationModalGeneral" class="modal" style="z-index: 10005;">
        <div class="modal-content">
            <span class="close" data-close-modal-id="notificationModalGeneral">&times;</span>
            <h2 id="notificationModalGeneralTitle">Notification</h2>
            <p id="notificationModalGeneralMessage" style="padding: 15px 20px; text-align: center; line-height:1.6;"></p>
            <div class="button-row" style="justify-content: center;">
                <button type="button" id="okButtonNotificationModalGeneral" data-close-modal-id="notificationModalGeneral" class="glossy-button text-blue">OK</button>
            </div>
        </div>
    </div>
    <script>
        window.settingsConfig = { payPeriodType: "<%= escapeForJavaScriptString(currentPayPeriod) %>", firstDayOfWeek: "<%= escapeForJavaScriptString(currentFirstDayOfWeek) %>", payPeriodStartDate: "<%= escapeForJavaScriptString(currentPayPeriodStartDate) %>", gracePeriod: "<%= escapeForJavaScriptString(currentGracePeriod) %>", overtimeRuleMode: "<%= escapeForJavaScriptString(currentOvertimeRuleMode) %>", overtimeState: "<%= escapeForJavaScriptString(currentOvertimeState) %>", punchRestrictionsEnabled: <%= currentPunchRestrictionsEnabled %>, overtimeRate: "<%= escapeForJavaScriptString(currentOvertimeRate) %>", overtimeDailyEnabled: <%= currentOvertimeDailyEnabled %>, overtimeDailyThreshold: "<%= escapeForJavaScriptString(currentOvertimeDailyThreshold) %>", overtimeDoubleTimeEnabled: <%= currentOvertimeDoubleTimeEnabled %>, overtimeDoubleTimeThreshold: "<%= escapeForJavaScriptString(currentOvertimeDoubleTimeThreshold) %>", overtimeSeventhDayEnabled: <%= currentOvertimeSeventhDayEnabled %>, overtimeSeventhDayOTThreshold: "<%= escapeForJavaScriptString(currentOvertimeSeventhDayOTThreshold) %>", overtimeSeventhDayDTThreshold: "<%= escapeForJavaScriptString(currentOvertimeSeventhDayDTThreshold) %>", restrictByTimeDay: <%= currentRestrictByTimeDay %>, restrictByLocation: <%= currentRestrictByLocation %>, restrictByNetwork: <%= currentRestrictByNetwork %>, restrictByDevice: <%= currentRestrictByDevice %> };
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= wizardStepForPage != null ? escapeForJavaScriptString(wizardStepForPage) : "" %>";
        window.appRootPath = "<%= request.getContextPath() %>";
    </script>
    <script src="${pageContext.request.contextPath}/js/settings.js?v=<%= System.currentTimeMillis() %>"></script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>