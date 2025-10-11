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
<%@ page import="timeclock.subscription.SubscriptionUtils" %>

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
    // This entire block of Java scriptlet code remains unchanged.
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
    if(tenantId_settings != null && tenantId_settings > 0 && pageLevelError_settings == null && !inSetupWizardMode_JSP) { std_configuredFirstDayInitial = Configuration.getProperty(tenantId_settings, "FirstDayOfWeek", STD_DEFAULT_FIRST_DAY_OF_WEEK);
    }
    try { if (std_configuredFirstDayInitial != null && !std_configuredFirstDayInitial.isEmpty()) { std_defaultFirstDayEnum = DayOfWeek.valueOf(std_configuredFirstDayInitial.toUpperCase(Locale.ENGLISH));
    }} catch (Exception e) { std_defaultFirstDayEnum = DayOfWeek.SUNDAY; }
    final String STD_DEFAULT_PAY_PERIOD_START_DATE = today.with(TemporalAdjusters.previousOrSame(std_defaultFirstDayEnum)).format(DateTimeFormatter.ISO_DATE);
    final String STD_DEFAULT_OVERTIME_RULE_MODE = "Manual"; final String STD_DEFAULT_OVERTIME_STATE = ""; final String STD_FIXED_OVERTIME_ENABLED_VALUE = "true"; final String STD_DEFAULT_OVERTIME_RATE = "1.5";
    final String STD_DEFAULT_OVERTIME_DAILY_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_DAILY_THRESHOLD = "8.0"; final String STD_DEFAULT_OVERTIME_DOUBLE_TIME_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_DOUBLE_TIME_THRESHOLD = "12.0";
    final String STD_DEFAULT_OVERTIME_SEVENTH_DAY_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_SEVENTH_DAY_OT_THRESHOLD = "8.0"; final String STD_DEFAULT_OVERTIME_SEVENTH_DAY_DT_THRESHOLD = "8.0";
    final String STD_DEFAULT_OVERTIME_HOLIDAY_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_HOLIDAY_RATE = "1.5";
    final String STD_DEFAULT_OVERTIME_DAYSOFF_ENABLED = "false"; final String STD_DEFAULT_OVERTIME_DAYSOFF_RATE = "1.5";
    final String STD_DEFAULT_CUSTOM_HOLIDAY_DATE = ""; final String STD_DEFAULT_CUSTOM_HOLIDAY_NAME = "";
    
    String currentPayPeriod = STD_DEFAULT_PAY_PERIOD_TYPE;
    String currentFirstDayOfWeek = STD_DEFAULT_FIRST_DAY_OF_WEEK; String currentPayPeriodStartDate = STD_DEFAULT_PAY_PERIOD_START_DATE;
    String currentGracePeriod = "0"; String currentOvertimeRuleMode = STD_DEFAULT_OVERTIME_RULE_MODE;
    String currentOvertimeState = STD_DEFAULT_OVERTIME_STATE;
    String currentOvertimeType = "manual"; // manual, company_state, employee_state
    boolean hasProPlan = false;
    boolean currentRestrictByTimeDay = false; boolean currentRestrictByLocation = false; boolean currentRestrictByNetwork = false;
    boolean currentRestrictByDevice = false; String currentOvertimeRate = STD_DEFAULT_OVERTIME_RATE;
    boolean currentOvertimeDailyEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_DAILY_ENABLED); String currentOvertimeDailyThreshold = STD_DEFAULT_OVERTIME_DAILY_THRESHOLD; boolean currentOvertimeDoubleTimeEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_DOUBLE_TIME_ENABLED);
    String currentOvertimeDoubleTimeThreshold = STD_DEFAULT_OVERTIME_DOUBLE_TIME_THRESHOLD; boolean currentOvertimeSeventhDayEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_SEVENTH_DAY_ENABLED);
    String currentOvertimeSeventhDayOTThreshold = STD_DEFAULT_OVERTIME_SEVENTH_DAY_OT_THRESHOLD; String currentOvertimeSeventhDayDTThreshold = STD_DEFAULT_OVERTIME_SEVENTH_DAY_DT_THRESHOLD;
    boolean currentOvertimeHolidayEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_HOLIDAY_ENABLED); String currentOvertimeHolidayRate = STD_DEFAULT_OVERTIME_HOLIDAY_RATE;
    String currentOvertimeHolidays = "";
    String currentCustomHolidayDate = STD_DEFAULT_CUSTOM_HOLIDAY_DATE; String currentCustomHolidayName = STD_DEFAULT_CUSTOM_HOLIDAY_NAME;
    boolean currentOvertimeDaysOffEnabled = Boolean.parseBoolean(STD_DEFAULT_OVERTIME_DAYSOFF_ENABLED); String currentOvertimeDaysOffRate = STD_DEFAULT_OVERTIME_DAYSOFF_RATE;
    
    boolean hasBusinessPlan = false;
    if (tenantId_settings != null && tenantId_settings > 0 && pageLevelError_settings == null) {
        hasProPlan = SubscriptionUtils.hasProPlan(tenantId_settings);
        hasBusinessPlan = SubscriptionUtils.hasBusinessPlan(tenantId_settings);
        boolean isFirstWizardSettingsLoad = false;
        if (inSetupWizardMode_JSP) {
            String firstLoadCheck = Configuration.getProperty(tenantId_settings, "WizardSettingsInitialized");
            if (firstLoadCheck == null) {
                isFirstWizardSettingsLoad = true;
            }
        }

        if (inSetupWizardMode_JSP && isFirstWizardSettingsLoad) {
            currentPayPeriod = "Weekly"; currentFirstDayOfWeek = "Sunday"; currentPayPeriodStartDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE); currentGracePeriod = "0"; currentOvertimeRuleMode = "AutoByState";
            currentOvertimeType = hasProPlan ? "employee_state" : "company_state";
            currentOvertimeState = (companyStateFromSignup != null && !companyStateFromSignup.isEmpty()) ? companyStateFromSignup : "FLSA"; currentRestrictByTimeDay = false;
            currentRestrictByLocation = false;
            currentRestrictByNetwork = false;
            currentRestrictByDevice = false; currentOvertimeRate = "1.5"; currentOvertimeDailyEnabled = false; currentOvertimeDailyThreshold = "8.0";
            currentOvertimeDoubleTimeEnabled = false;
            currentOvertimeDoubleTimeThreshold = "12.0";
            currentOvertimeSeventhDayEnabled = false; currentOvertimeSeventhDayOTThreshold = "8.0"; currentOvertimeSeventhDayDTThreshold = "8.0";
            currentOvertimeHolidayEnabled = false; currentOvertimeHolidayRate = "1.5"; currentOvertimeHolidays = "";
            currentOvertimeDaysOffEnabled = false; currentOvertimeDaysOffRate = "1.5";
            try {
                Configuration.saveProperty(tenantId_settings, "PayPeriodType", currentPayPeriod);
                Configuration.saveProperty(tenantId_settings, "FirstDayOfWeek", currentFirstDayOfWeek); Configuration.saveProperty(tenantId_settings, "PayPeriodStartDate", currentPayPeriodStartDate); Configuration.saveProperty(tenantId_settings, "GracePeriod", currentGracePeriod); Configuration.saveProperty(tenantId_settings, "OvertimeRuleMode", currentOvertimeRuleMode); Configuration.saveProperty(tenantId_settings, "OvertimeType", currentOvertimeType); Configuration.saveProperty(tenantId_settings, "OvertimeState", currentOvertimeState);
                Configuration.saveProperty(tenantId_settings, "RestrictByTimeDay", "false");
                Configuration.saveProperty(tenantId_settings, "RestrictByLocation", "false"); Configuration.saveProperty(tenantId_settings, "RestrictByNetwork", "false"); Configuration.saveProperty(tenantId_settings, "RestrictByDevice", "false"); Configuration.saveProperty(tenantId_settings, "Overtime", STD_FIXED_OVERTIME_ENABLED_VALUE);
                Configuration.saveProperty(tenantId_settings, "OvertimeRate", currentOvertimeRate);
                Configuration.saveProperty(tenantId_settings, "OvertimeDaily", String.valueOf(currentOvertimeDailyEnabled));
                Configuration.saveProperty(tenantId_settings, "OvertimeDailyThreshold", currentOvertimeDailyThreshold); Configuration.saveProperty(tenantId_settings, "OvertimeDoubleTimeEnabled", String.valueOf(currentOvertimeDoubleTimeEnabled)); Configuration.saveProperty(tenantId_settings, "OvertimeDoubleTimeThreshold", currentOvertimeDoubleTimeThreshold);
                Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayEnabled", String.valueOf(currentOvertimeSeventhDayEnabled)); Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayOTThreshold", currentOvertimeSeventhDayOTThreshold);
                Configuration.saveProperty(tenantId_settings, "OvertimeSeventhDayDTThreshold", currentOvertimeSeventhDayDTThreshold);
                Configuration.saveProperty(tenantId_settings, "OvertimeHolidayEnabled", String.valueOf(currentOvertimeHolidayEnabled));
                Configuration.saveProperty(tenantId_settings, "OvertimeHolidayRate", currentOvertimeHolidayRate);
                Configuration.saveProperty(tenantId_settings, "OvertimeHolidays", currentOvertimeHolidays);
                Configuration.saveProperty(tenantId_settings, "OvertimeDaysOffEnabled", String.valueOf(currentOvertimeDaysOffEnabled));
                Configuration.saveProperty(tenantId_settings, "OvertimeDaysOffRate", currentOvertimeDaysOffRate);
                Configuration.saveProperty(tenantId_settings, "WizardSettingsInitialized", "true");
                if (pageLevelSuccess_settings == null) pageLevelSuccess_settings = "Initial company settings populated. Review and click 'Next'.";
            } catch (SQLException e) {
            pageLevelError_settings = "Error saving initial settings: " + e.getMessage(); }
        } else { 
            currentPayPeriod = Configuration.getProperty(tenantId_settings, "PayPeriodType", STD_DEFAULT_PAY_PERIOD_TYPE);
            currentFirstDayOfWeek = Configuration.getProperty(tenantId_settings, "FirstDayOfWeek", STD_DEFAULT_FIRST_DAY_OF_WEEK);
            currentPayPeriodStartDate = Configuration.getProperty(tenantId_settings, "PayPeriodStartDate", STD_DEFAULT_PAY_PERIOD_START_DATE); 
            currentGracePeriod = Configuration.getProperty(tenantId_settings, "GracePeriod", "0"); 
            currentOvertimeRuleMode = Configuration.getProperty(tenantId_settings, "OvertimeRuleMode", STD_DEFAULT_OVERTIME_RULE_MODE);
            currentOvertimeState = Configuration.getProperty(tenantId_settings, "OvertimeState", STD_DEFAULT_OVERTIME_STATE);
            currentOvertimeType = Configuration.getProperty(tenantId_settings, "OvertimeType", "manual");
            currentRestrictByTimeDay = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByTimeDay", "false"));
            currentRestrictByLocation = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByLocation", "false"));
            currentRestrictByNetwork = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByNetwork", "false"));
            currentRestrictByDevice = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "RestrictByDevice", "false")); 
            currentOvertimeRate = Configuration.getProperty(tenantId_settings, "OvertimeRate", STD_DEFAULT_OVERTIME_RATE);
            currentOvertimeDailyEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeDaily", STD_DEFAULT_OVERTIME_DAILY_ENABLED));
            currentOvertimeDailyThreshold = Configuration.getProperty(tenantId_settings, "OvertimeDailyThreshold", STD_DEFAULT_OVERTIME_DAILY_THRESHOLD);
            currentOvertimeDoubleTimeEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeDoubleTimeEnabled", STD_DEFAULT_OVERTIME_DOUBLE_TIME_ENABLED)); 
            currentOvertimeDoubleTimeThreshold = Configuration.getProperty(tenantId_settings, "OvertimeDoubleTimeThreshold", STD_DEFAULT_OVERTIME_DOUBLE_TIME_THRESHOLD);
            currentOvertimeSeventhDayEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayEnabled", STD_DEFAULT_OVERTIME_SEVENTH_DAY_ENABLED));
            currentOvertimeSeventhDayOTThreshold = Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayOTThreshold", STD_DEFAULT_OVERTIME_SEVENTH_DAY_OT_THRESHOLD);
            currentOvertimeSeventhDayDTThreshold = Configuration.getProperty(tenantId_settings, "OvertimeSeventhDayDTThreshold", STD_DEFAULT_OVERTIME_SEVENTH_DAY_DT_THRESHOLD);
            currentOvertimeHolidayEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeHolidayEnabled", STD_DEFAULT_OVERTIME_HOLIDAY_ENABLED));
            currentOvertimeHolidayRate = Configuration.getProperty(tenantId_settings, "OvertimeHolidayRate", STD_DEFAULT_OVERTIME_HOLIDAY_RATE);
            currentOvertimeHolidays = Configuration.getProperty(tenantId_settings, "OvertimeHolidays", "");
            currentCustomHolidayDate = Configuration.getProperty(tenantId_settings, "CustomHolidayDate", STD_DEFAULT_CUSTOM_HOLIDAY_DATE);
            currentCustomHolidayName = Configuration.getProperty(tenantId_settings, "CustomHolidayName", STD_DEFAULT_CUSTOM_HOLIDAY_NAME);
            currentOvertimeDaysOffEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId_settings, "OvertimeDaysOffEnabled", STD_DEFAULT_OVERTIME_DAYSOFF_ENABLED));
            currentOvertimeDaysOffRate = Configuration.getProperty(tenantId_settings, "OvertimeDaysOffRate", STD_DEFAULT_OVERTIME_DAYSOFF_RATE);
        }
    }
%>
<!DOCTYPE html>
<html>
<head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Global Settings<% if(inSetupWizardMode_JSP) { %> - Company Setup Wizard<% } %></title>
    <%@ include file="/WEB-INF/includes/common-head.jspf" %>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/settings.css?v=<%= System.currentTimeMillis() %>">
</head>
<body class="reports-page">
   <% if (!inSetupWizardMode_JSP) { %>
        <%@ include file="/WEB-INF/includes/navbar.jspf" %>
    <% } else { %>
        <div class="wizard-header">
            <h2>Company Setup: Initial Settings for <%= escapeHtml(companyNameForWizardHeader) %></h2>
            <p>Please review and confirm these settings. You can change them any time later.</p>
        </div>
    <% } %>
    <div class="parent-container">

        <h1><i class="fas fa-cogs"></i>Global Settings<% if(inSetupWizardMode_JSP) { %> <span style="font-size:0.8em; color:#555;">(Setup Step)</span><% } %></h1>
        <% if (pageLevelSuccess_settings != null && !pageLevelSuccess_settings.isEmpty()) { %><div class="page-message success-message"><i class="fas fa-check-circle"></i> <%= escapeHtml(pageLevelSuccess_settings) %></div><% } %>
        <% if (pageLevelError_settings != null && !pageLevelError_settings.isEmpty()) { %><div class="page-message error-message"><i class="fas fa-exclamation-triangle"></i> <%= escapeHtml(pageLevelError_settings) %></div><% } %>

        <% if (tenantId_settings != null && tenantId_settings > 0 && pageLevelError_settings == null) { %>
        
        <form id="settingsForm" onsubmit="return false;">
            <div class="setting-item">
                <h4 class="section-heading"><i class="fas fa-calendar-alt"></i>Pay Period</h4>
                <div class="form-body-container">
                    <div class="form-row">
                        <div class="setting-block">
                            <label for="payPeriodType" class="setting-label-fixed">Pay Period Type:</label>
                            <div class="setting-controls-wrapper">
                                <select id="payPeriodType" name="PayPeriodType">
                                    <option value="Daily" <% if ("Daily".equals(currentPayPeriod)) out.print(" selected"); %>>Daily</option>
                                    <option value="Weekly" <% if ("Weekly".equals(currentPayPeriod)) out.print(" selected"); %>>Weekly</option>
                                    <option value="Bi-Weekly" <% if ("Bi-Weekly".equals(currentPayPeriod)) out.print(" selected"); %>>Bi-Weekly</option>
                                    <option value="Semi-Monthly" <% if ("Semi-Monthly".equals(currentPayPeriod)) out.print(" selected"); %>>Semi-Monthly</option>
                                    <option value="Monthly" <% if ("Monthly".equals(currentPayPeriod)) out.print(" selected"); %>>Monthly</option>
                                </select>
                                <span id="PayPeriodType-status" class="save-status"></span>
                            </div>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="setting-block" id="firstDayOfWeekBlock">
                            <label for="firstDayOfWeek" class="setting-label-fixed">First Day of Work Week:</label>
                            <div class="setting-controls-wrapper">
                                <select id="firstDayOfWeek" name="FirstDayOfWeek">
                                    <option value="Sunday" <% if ("Sunday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Sunday</option>
                                    <option value="Monday" <% if ("Monday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Monday</option>
                                    <option value="Tuesday" <% if ("Tuesday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Tuesday</option>
                                    <option value="Wednesday" <% if ("Wednesday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Wednesday</option>
                                    <option value="Thursday" <% if ("Thursday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Thursday</option>
                                    <option value="Friday" <% if ("Friday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Friday</option>
                                    <option value="Saturday" <% if ("Saturday".equalsIgnoreCase(currentFirstDayOfWeek)) out.print(" selected"); %>>Saturday</option>
                                </select>
                                <span id="FirstDayOfWeek-status" class="save-status"></span>
                            </div>
                        </div>
                    </div>
                    <div class="setting-info" id="firstDayOfWeekNote">
                        <strong>Note:</strong> This setting is used to determine the 7-day period for calculating weekly overtime.
                    </div>
                    <div class="form-row">
                        <div class="setting-block" id="payPeriodStartDateBlock">
                            <label for="payPeriodStartDate" class="setting-label-fixed">Pay Period Start Date:</label>
                            <div class="setting-controls-wrapper">
                                <input type="date" id="payPeriodStartDate" name="PayPeriodStartDate" value="<%= escapeHtml(currentPayPeriodStartDate) %>">
                                <span id="PayPeriodStartDate-status" class="save-status"></span>
                            </div>
                        </div>
                    </div>
                    <div class="form-row">
                        <div class="setting-block" id="payPeriodEndDateBlock">
                            <label class="setting-label-fixed">Calculated Pay Period End:</label>
                            <div class="setting-controls-wrapper">
                                <span id="payPeriodEndDateDisplay" class="calculated-date-display"></span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="setting-item">
                <h4 class="section-heading"><i class="fas fa-user-clock"></i>Tardy / Early Out Rules</h4>
                <div class="form-body-container">
                    <div class="form-row">
                        <div class="setting-block">
                            <label for="gracePeriod" class="setting-label-fixed">Grace Period (Minutes):</label>
                            <div class="setting-controls-wrapper">
                                <select id="gracePeriod" name="GracePeriod">
                                    <% for (int i : new int[]{0, 1, 2, 3, 4, 5, 10, 15, 30, 60}) { %>
                                        <option value="<%= i %>" <% if (String.valueOf(i).equals(currentGracePeriod)) out.print(" selected"); %>>
                                            <%= i %>
                                        </option>
                                    <% } %>
                                </select>
                                <span id="GracePeriod-status" class="save-status"></span>
                            </div>
                        </div>
                    </div>
                    <div class="setting-info">
                        <strong>Note:</strong> Tardy rules are only applied to employees assigned to a schedule with defined start/end times.
                    </div>
                </div>
            </div>
            <div class="setting-item">
                <h4 class="section-heading"><i class="fas fa-hourglass-half"></i>Overtime Rules</h4>
                <div class="form-body-container">
                    <div class="setting-info" style="margin-top:0;">
                        <strong>Note:</strong> Overtime rules can be complex.
                        <a href="https://www.dol.gov/agencies/whd/minimum-wage/state" target="_blank" rel="noopener noreferrer">Click here for current Overtime rules by State <i class="fas fa-external-link-alt"></i></a>.
                    </div>
                    <div class="form-row">
                        <div class="setting-block">
                            <label class="setting-label-fixed">Overtime Calculation Mode:</label>
                            <div class="setting-controls-wrapper">
                                <div class="radio-group">
                                    <span class="styled-radio">
                                        <input type="radio" id="otTypeManual" name="OvertimeType" value="manual" <% if ("manual".equals(currentOvertimeType)) out.print(" checked"); %>> 
                                        <label for="otTypeManual">Manual</label>
                                    </span> 
                                    <span class="styled-radio">
                                        <input type="radio" id="otTypeCompanyState" name="OvertimeType" value="company_state" <% if ("company_state".equals(currentOvertimeType)) out.print(" checked"); %>> 
                                        <label for="otTypeCompanyState">By Company State</label>
                                    </span>
                                    <% if (hasProPlan) { %>
                                    <span class="styled-radio">
                                        <input type="radio" id="otTypeEmployeeState" name="OvertimeType" value="employee_state" <% if ("employee_state".equals(currentOvertimeType)) out.print(" checked"); %>> 
                                        <label for="otTypeEmployeeState">By Employee's State</label>
                                    </span>
                                    <% } else { %>
                                    <span class="styled-radio" style="opacity: 0.6;">
                                        <input type="radio" id="otTypeEmployeeState" name="OvertimeType" value="employee_state" disabled> 
                                        <label for="otTypeEmployeeState">By Employee State <span class="pro-badge">PRO</span></label>
                                    </span>
                                    <% } %>
                                </div> 
                                <span id="OvertimeType-status" class="save-status radio-group-status"></span>
                            </div>
                        </div>
                    </div>
                    <div id="autoStateOvertimeSection" style="margin-top:15px; <% if (!"company_state".equals(currentOvertimeType) && !"employee_state".equals(currentOvertimeType)) out.print("display:none;"); %>">
                        <div class="form-row">
                            <div class="setting-block">
                                <label for="overtimeStateSelect" class="setting-label-fixed">Select State:</label>
                                <div class="setting-controls-wrapper"> 
                                    <select id="overtimeStateSelect" name="OvertimeState" style="min-width:250px;" <% if ("employee_state".equals(currentOvertimeType)) out.print("disabled"); %>></select> 
                                    <span id="OvertimeState-status" class="save-status"></span> 
                                </div>
                            </div>
                            
                            <div id="stateSpecificNotesDisplay" class="setting-info" style="margin-top: 10px;"></div>
                        </div>
                    </div>
                    <div id="employeeStateNote" class="setting-info" style="margin-top: 5px; <% if (!"employee_state".equals(currentOvertimeType)) out.print("display:none;"); %>">
                        <strong>Note:</strong> When using "By Employee's State", overtime rules are determined by each employee's individual state setting.
                    </div>
                    <div id="manualOvertimeSettings" style="margin-top:20px; border-top:1px dashed #ccc; padding-top:15px; <% if (!"manual".equals(currentOvertimeType)) { out.print("opacity:0.7;"); } %>">
                        <div class="form-row">
                            <div class="setting-block">
                                <label class="setting-label-fixed">Weekly Overtime (FLSA):</label> 
                                <div class="setting-controls-wrapper">
                                    <label class="switch">
                                        <input type="checkbox" id="overtimeWeeklyEnabled" name="Overtime" value="true" checked disabled>
                                        <span class="slider round"></span>
                                    </label>
                                    <span style="margin-left: 5px;">Enabled (After 40 hours)</span>
                                    <small class="fixed-setting-note" style="margin-left:10px;">Fixed</small>
                                </div>
                            </div>
                            <div class="setting-block">
                                <label class="setting-label-fixed">Std. Overtime Rate:</label>
                                <div class="setting-controls-wrapper">
                                    <div class="radio-group">
                                        <span class="styled-radio">
                                            <input type="radio" id="overtimeRate1.5" name="OvertimeRate" value="1.5" <% if ("1.5".equals(currentOvertimeRate)) out.print(" checked"); %>>
                                            <label for="overtimeRate1.5">1.5x</label>
                                        </span> 
                                        <span class="styled-radio">
                                            <input type="radio" id="overtimeRate2.0" name="OvertimeRate" value="2.0" <% if ("2.0".equals(currentOvertimeRate)) out.print(" checked"); %>>
                                            <label for="overtimeRate2.0">2.0x</label>
                                        </span>
                                    </div>
                                    <span id="OvertimeRate-status" class="save-status radio-group-status"></span>
                                </div>
                            </div>
                        </div>
                    </div>
                    <% if (hasBusinessPlan) { %>
                    <hr style="border:0; border-top: 1px dashed #eee; margin: 15px 0;">
                    <div id="holidayOvertimeSection" style="margin-top:20px; border-top:1px dashed #ccc; padding-top:15px;">
                        <div class="form-row holiday-overtime-row">
                            <div class="setting-block">
                                <label for="overtimeHolidayEnabled" class="setting-label-fixed">Enable Holiday OT:</label>
                                <div class="setting-controls-wrapper">
                                    <label class="switch">
                                        <input type="checkbox" id="overtimeHolidayEnabled" name="OvertimeHolidayEnabled" value="true" <% if (currentOvertimeHolidayEnabled) out.print(" checked"); %>>
                                        <span class="slider round"></span>
                                    </label>
                                    <span id="OvertimeHolidayEnabled-status" class="save-status checkbox-status"></span>
                                </div>
                            </div>
                            <div class="setting-block" id="overtimeHolidayRateBlock" style="opacity: 1 !important; <% if (!currentOvertimeHolidayEnabled) out.print("display:none;"); %>">
                                <label class="setting-label-fixed">Holiday OT Rate:</label>
                                <div class="setting-controls-wrapper">
                                    <div class="radio-group">
                                        <span class="styled-radio">
                                            <input type="radio" id="overtimeHolidayRate1.5" name="OvertimeHolidayRate" value="1.5" <% if ("1.5".equals(currentOvertimeHolidayRate)) out.print(" checked"); %>>
                                            <label for="overtimeHolidayRate1.5">1.5x</label>
                                        </span> 
                                        <span class="styled-radio">
                                            <input type="radio" id="overtimeHolidayRate2.0" name="OvertimeHolidayRate" value="2.0" <% if ("2.0".equals(currentOvertimeHolidayRate)) out.print(" checked"); %>>
                                            <label for="overtimeHolidayRate2.0">2.0x</label>
                                        </span>
                                    </div>
                                    <span id="OvertimeHolidayRate-status" class="save-status radio-group-status"></span>
                                </div>
                            </div>
                        </div>
                        <div id="holidaySelectionBlock" style="margin-top: 10px; opacity: 1 !important; <% if (!currentOvertimeHolidayEnabled) out.print("display:none;"); %>">
                            <label class="setting-label-fixed" style="margin-bottom: 10px; display: block;">Select Holidays for OT:</label>
                            <div id="holidayCheckboxList" style="display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 10px; width: 100%; margin-bottom: 15px;"></div>
                            <div style="display: flex; gap: 10px;">
                                <button type="button" id="selectAllHolidays" class="configure-button" style="padding: 5px 10px; font-size: 0.9em;">Select All</button>
                                <button type="button" id="deselectAllHolidays" class="configure-button" style="padding: 5px 10px; font-size: 0.9em;">Deselect All</button>
                            </div>
                            <span id="OvertimeHolidays-status" class="save-status" style="margin-top: 10px;"></span>
                            <hr style="border:0; border-top: 1px dashed #eee; margin: 15px 0;">
                            <label class="setting-label-fixed" style="margin-bottom: 10px; display: block;">Custom Holiday:</label>
                            <div style="display: flex; gap: 10px; align-items: center; flex-wrap: wrap;">
                                <input type="checkbox" id="customHolidayCheckbox" class="holiday-checkbox" style="width: 20px; height: 20px;">
                                <input type="date" id="customHolidayDate" name="CustomHolidayDate" value="<%= escapeHtml(currentCustomHolidayDate) %>" style="flex: 0 0 150px;" <% if (currentCustomHolidayDate.isEmpty()) out.print("disabled"); %>>
                                <input type="text" id="customHolidayName" name="CustomHolidayName" value="<%= escapeHtml(currentCustomHolidayName) %>" placeholder="Holiday Description" style="flex: 1; min-width: 200px;" <% if (currentCustomHolidayName.isEmpty()) out.print("disabled"); %>>
                                <span id="customHolidayCheckbox-status" class="save-status"></span>
                            </div>
                        </div><br>
                        <div class="setting-info" style="margin-top: 5px; margin-bottom: 15px;">
                            <strong>Note:</strong> Holiday overtime applies only to worked holidays, with In/Out times. PTO holiday entries (non-worked) are paid the regular rate.<br>
                            Enable to select applicable holidays.
                        </div>
                        <hr style="border:0; border-top: 1px dashed #eee; margin: 15px 0;">
                        <div class="form-row">
                            <div class="setting-block">
                                <label for="overtimeDaysOffEnabled" class="setting-label-fixed">Enable Days Off OT:</label>
                                <div class="setting-controls-wrapper">
                                    <label class="switch">
                                        <input type="checkbox" id="overtimeDaysOffEnabled" name="OvertimeDaysOffEnabled" value="true" <% if (currentOvertimeDaysOffEnabled) out.print(" checked"); %>>
                                        <span class="slider round"></span>
                                    </label>
                                    <span id="OvertimeDaysOffEnabled-status" class="save-status checkbox-status"></span>
                                </div>
                            </div>
                            <div class="setting-block" id="overtimeDaysOffRateBlock" style="<% if (!currentOvertimeDaysOffEnabled) out.print("display:none;"); %>">
                                <label class="setting-label-fixed">Days Off OT Rate:</label>
                                <div class="setting-controls-wrapper">
                                    <div class="radio-group">
                                        <span class="styled-radio">
                                            <input type="radio" id="overtimeDaysOffRate1.5" name="OvertimeDaysOffRate" value="1.5" <% if ("1.5".equals(currentOvertimeDaysOffRate)) out.print(" checked"); %>>
                                            <label for="overtimeDaysOffRate1.5">1.5x</label>
                                        </span> 
                                        <span class="styled-radio">
                                            <input type="radio" id="overtimeDaysOffRate2.0" name="OvertimeDaysOffRate" value="2.0" <% if ("2.0".equals(currentOvertimeDaysOffRate)) out.print(" checked"); %>>
                                            <label for="overtimeDaysOffRate2.0">2.0x</label>
                                        </span>
                                    </div>
                                    <span id="OvertimeDaysOffRate-status" class="save-status radio-group-status"></span>
                                </div>
                            </div>
                        </div>
                        <div class="setting-info" style="margin-top: 5px; margin-bottom: 15px;">
                            <strong>Note:</strong> Days Off overtime applies only to employees on a defined schedule with specific work days. Employees working on their scheduled days off will receive the selected overtime rate.
                        </div>
                    </div>
                    <% } %>
                    <div id="advancedOvertimeSettings" style="margin-top:20px; border-top:1px dashed #ccc; padding-top:15px; <% if (!"manual".equals(currentOvertimeType)) { out.print("opacity:0.7;"); } %>">
                        <div class="form-row">
                            <div class="setting-block">
                                <label for="overtimeDaily" class="setting-label-fixed">Enable Daily OT:</label>
                                <div class="setting-controls-wrapper">
                                    <label class="switch">
                                        <input type="checkbox" id="overtimeDaily" name="OvertimeDaily" value="true" <% if (currentOvertimeDailyEnabled) out.print(" checked"); %>>
                                        <span class="slider round"></span>
                                    </label>
                                    <span id="OvertimeDaily-status" class="save-status checkbox-status"></span>
                                </div>
                            </div>
                            <div class="setting-block" id="overtimeDailyThresholdBlock" style="<% if (!currentOvertimeDailyEnabled && "Manual".equals(currentOvertimeRuleMode)) out.print("opacity:0.5;"); %>">
                                <label for="overtimeDailyThreshold" class="setting-label-fixed">Daily OT After (Hrs):</label>
                                <div class="setting-controls-wrapper">
                                    <input type="number" id="overtimeDailyThreshold" name="OvertimeDailyThreshold" min="0.5" max="23.5" step="0.5" placeholder="e.g., 8.0" value="<%= currentOvertimeDailyThreshold %>" class="short-input" <% if (!"manual".equals(currentOvertimeType)) out.print("disabled"); %>>
                                    <span id="OvertimeDailyThreshold-status" class="save-status"></span>
                                </div>
                            </div>
                        </div>
                        <div class="form-row">
                            <div class="setting-block">
                                <label for="overtimeDoubleTimeEnabled" class="setting-label-fixed">Enable Daily DT:</label>
                                <div class="setting-controls-wrapper">
                                    <label class="switch">
                                        <input type="checkbox" id="overtimeDoubleTimeEnabled" name="OvertimeDoubleTimeEnabled" value="true" <% if (currentOvertimeDoubleTimeEnabled) out.print(" checked"); %>>
                                        <span class="slider round"></span>
                                    </label>
                                    <span id="OvertimeDoubleTimeEnabled-status" class="save-status checkbox-status"></span>
                                </div>
                            </div>
                            <div class="setting-block" id="overtimeDoubleTimeThresholdBlock" style="<% if (!currentOvertimeDoubleTimeEnabled && "Manual".equals(currentOvertimeRuleMode)) out.print("opacity:0.5;"); %>">
                                <label for="overtimeDoubleTimeThreshold" class="setting-label-fixed">Daily DT After (Hrs):</label>
                                <div class="setting-controls-wrapper">
                                    <input type="number" id="overtimeDoubleTimeThreshold" name="OvertimeDoubleTimeThreshold" min="0.5" max="23.5" step="0.5" placeholder="e.g., 12.0" value="<%= currentOvertimeDoubleTimeThreshold %>" class="short-input" <% if (!currentOvertimeDoubleTimeEnabled) out.print("disabled"); %>>
                                    <span id="OvertimeDoubleTimeThreshold-status" class="save-status"></span>
                                </div>
                            </div>
                        </div>
                        <hr style="border:0; border-top: 1px dashed #eee; margin: 15px 0;">
                        <div class="form-row">
                            <div class="setting-block">
                                <label for="overtimeSeventhDayEnabled" class="setting-label-fixed">Enable 7th Day OT:</label>
                                <div class="setting-controls-wrapper">
                                    <label class="switch">
                                        <input type="checkbox" id="overtimeSeventhDayEnabled" name="OvertimeSeventhDayEnabled" value="true" <% if (currentOvertimeSeventhDayEnabled) out.print(" checked"); %>>
                                        <span class="slider round"></span>
                                    </label>
                                    <span id="OvertimeSeventhDayEnabled-status" class="save-status checkbox-status"></span>
                                </div>
                            </div>
                        </div>
                        <div class="form-row" id="seventhDayOTDetailsBlock" style="padding-left:20px; <% if(!currentOvertimeSeventhDayEnabled) out.print("display:none;"); %> <% if (!currentOvertimeSeventhDayEnabled && "Manual".equals(currentOvertimeRuleMode)) out.print("opacity:0.5;"); %>">
                            <div class="setting-block">
                                <label for="overtimeSeventhDayOTThreshold" class="setting-label-fixed" style="font-weight:normal;">7th Day 1.5x Up To (Hrs):</label>
                                <div class="setting-controls-wrapper">
                                    <input type="number" id="overtimeSeventhDayOTThreshold" name="OvertimeSeventhDayOTThreshold" min="0.5" max="24" step="0.5" placeholder="e.g., 8.0" value="<%= currentOvertimeSeventhDayOTThreshold %>" class="short-input" <% if (!currentOvertimeSeventhDayEnabled) out.print("disabled"); %>>
                                    <span style="margin-left:5px;">hours</span>
                                    <span id="OvertimeSeventhDayOTThreshold-status" class="save-status"></span>
                                </div>
                            </div>
                            <div class="setting-block">
                                <label for="overtimeSeventhDayDTThreshold" class="setting-label-fixed" style="font-weight:normal;">7th Day 2.0x After (Hrs):</label>
                                <div class="setting-controls-wrapper">
                                    <input type="number" id="overtimeSeventhDayDTThreshold" name="OvertimeSeventhDayDTThreshold" min="0.5" max="24" step="0.5" placeholder="e.g., 8.0" value="<%= currentOvertimeSeventhDayDTThreshold %>" class="short-input" <% if (!currentOvertimeSeventhDayEnabled) out.print("disabled"); %>>
                                    <span style="margin-left:5px;">hours</span>
                                    <span id="OvertimeSeventhDayDTThreshold-status" class="save-status"></span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
            <div class="setting-item">
                <h4 class="section-heading"><i class="fas fa-fingerprint"></i>Punch Restrictions</h4>
                <div class="form-body-container">
                    <p style="margin-top:0; margin-bottom: 15px; font-size: 0.9em; color: #475569;">Restrictions may be applied in any combination, alone or not at all.</p>
                    <div class="setting-info">
                        <strong>Note:</strong> Settings enabled here will apply to ALL active employees and describe when, where and how a user can punch In or Out.
                    </div>
                    
                    <div class="sub-form-section"> 
                        <div id="specificPunchRestrictionsGroup" class="sub-settings-group">
                            <div class="punch-restriction-item">
                                <label class="switch">
                                     <input type="checkbox" id="restrictByTimeDay" name="RestrictByTimeDay" value="true" <% if (currentRestrictByTimeDay) out.print(" checked"); %>>
                                    <span class="slider round"></span>
                                </label>
                                <label for="restrictByTimeDay" class="slider-label">
                                     <i class="far fa-calendar-alt"></i>Restrict by Time/Day
                                </label>
                                <span class="spacer"></span>
                                <button type="button" class="configure-button" id="configureTimeDayBtn" <% if(!currentRestrictByTimeDay) out.print("disabled");%>>Configure</button>
                            </div>
                            <div class="punch-restriction-item">
                                <label class="switch">
                                    <input type="checkbox" id="restrictByLocation" name="RestrictByLocation" value="true" <% if (currentRestrictByLocation) out.print(" checked"); %>>
                                    <span class="slider round"></span>
                                </label>
                                <label for="restrictByLocation" class="slider-label">
                                     <i class="fas fa-map-marker-alt"></i>Restrict by Location<span class="required-asterisk">*</span>
                                </label>
                                <span class="spacer"></span>
                                <button type="button" class="configure-button" id="configureLocationBtn" <% if(!currentRestrictByLocation) out.print("disabled");%>>Configure</button>
                            </div>
                            <div class="punch-restriction-item">
                                <label class="switch">
                                    <input type="checkbox" id="restrictByDevice" name="RestrictByDevice" value="true" <% if (currentRestrictByDevice) out.print(" checked"); %>>
                                    <span class="slider round"></span>
                                </label>
                                <label for="restrictByDevice" class="slider-label">
                                     <i class="fas fa-mobile-alt"></i>Restrict by Device
                                </label>
                                <span class="spacer"></span>
                                <button type="button" class="configure-button" id="configureDeviceBtn" <% if(!currentRestrictByDevice) out.print("disabled");%>>Configure</button>
                            </div>
                        </div>
                    </div>
                    <div class="setting-info" style="margin-top: 20px; padding-left: 10px;">
                        <span class="required-asterisk" style="font-size:1em; margin-right: 5px; font-style:normal; font-weight:bold;">*</span> Wi-Fi or device GPS is recommended for employee use on mobile devices for this restriction.
                    </div>
                </div>
            </div>
        </form>
        <% } %>

        <% if (inSetupWizardMode_JSP) { %>
            <div class="wizard-navigation" style="text-align: right; margin-top: 30px;
 padding-top:20px; border-top: 1px solid #eee;">
                <button type="button" id="wizardSettingsNextButton" class="glossy-button text-green" style="padding: 10px 20px;
 font-size: 1.1em;">
                    Next: Departments Setup <i class="fas fa-arrow-right"></i>
                </button>
            </div>
        <% } %>

    </div>
    
    <%@ include file="/WEB-INF/includes/modals.jspf" %>
    
    <script>
        window.settingsConfig = {
            payPeriodType: "<%= escapeForJavaScriptString(currentPayPeriod) %>",
            firstDayOfWeek: "<%= escapeForJavaScriptString(currentFirstDayOfWeek) %>",
            payPeriodStartDate: "<%= escapeForJavaScriptString(currentPayPeriodStartDate) %>",
            gracePeriod: "<%= escapeForJavaScriptString(currentGracePeriod) %>",
            overtimeType: "<%= escapeForJavaScriptString(currentOvertimeType) %>",
            overtimeState: "<%= escapeForJavaScriptString(currentOvertimeState) %>",
            hasProPlan: <%= hasProPlan %>,
            overtimeRate: "<%= escapeForJavaScriptString(currentOvertimeRate) %>",
            overtimeDailyEnabled: <%= currentOvertimeDailyEnabled %>,
            overtimeDailyThreshold: "<%= escapeForJavaScriptString(currentOvertimeDailyThreshold) %>",
            overtimeDoubleTimeEnabled: <%= currentOvertimeDoubleTimeEnabled %>,
            overtimeDoubleTimeThreshold: "<%= escapeForJavaScriptString(currentOvertimeDoubleTimeThreshold) %>",
            overtimeSeventhDayEnabled: <%= currentOvertimeSeventhDayEnabled %>,
            overtimeSeventhDayOTThreshold: "<%= escapeForJavaScriptString(currentOvertimeSeventhDayOTThreshold) %>",
            overtimeSeventhDayDTThreshold: "<%= escapeForJavaScriptString(currentOvertimeSeventhDayDTThreshold) %>",
            overtimeHolidayEnabled: <%= currentOvertimeHolidayEnabled %>,
            overtimeHolidayRate: "<%= escapeForJavaScriptString(currentOvertimeHolidayRate) %>",
            overtimeHolidays: "<%= escapeForJavaScriptString(currentOvertimeHolidays) %>",
            customHolidayDate: "<%= escapeForJavaScriptString(currentCustomHolidayDate) %>",
            customHolidayName: "<%= escapeForJavaScriptString(currentCustomHolidayName) %>",
            overtimeDaysOffEnabled: <%= currentOvertimeDaysOffEnabled %>,
            overtimeDaysOffRate: "<%= escapeForJavaScriptString(currentOvertimeDaysOffRate) %>",
            restrictByTimeDay: <%= currentRestrictByTimeDay %>,
            restrictByLocation: <%= currentRestrictByLocation %>,
            restrictByDevice: <%= currentRestrictByDevice %>
        };
        window.inWizardMode_Page = <%= inSetupWizardMode_JSP %>;
        window.currentWizardStep_Page = "<%= wizardStepForPage != null ? escapeForJavaScriptString(wizardStepForPage) : "" %>";
        window.appRootPath = "<%= request.getContextPath() %>";
    </script>
    <script src="${pageContext.request.contextPath}/js/settings.js?v=<%= System.currentTimeMillis() %>"></script>
    <script src="${pageContext.request.contextPath}/js/settings_custom_holiday.js?v=<%= System.currentTimeMillis() %>"></script>
    <%@ include file="/WEB-INF/includes/common-scripts.jspf" %>
</body>
</html>