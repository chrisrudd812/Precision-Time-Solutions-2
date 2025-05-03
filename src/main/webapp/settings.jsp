<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<%@ page import="timeclock.Configuration"%>
<%@ page import="java.time.LocalDate" %>
<%@ page import="java.time.format.DateTimeFormatter" %>
<%@ page import="java.time.format.DateTimeParseException" %>
<%@ page import="java.util.Locale" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.text.NumberFormat" %>

<%
    // --- Load ALL current settings ---
    // Ensure Configuration class is loaded and properties file is accessible
    String currentFirstDay = Configuration.getProperty("FirstDayOfWeek", "Sunday");
    String currentGracePeriod = Configuration.getProperty("GracePeriod", "0");
    String currentPayPeriod = Configuration.getProperty("PayPeriodType", "Weekly");
    boolean currentOvertimeEnabled = "true".equalsIgnoreCase(Configuration.getProperty("Overtime", "true"));
    boolean currentHolidayPayEnabled = "true".equalsIgnoreCase(Configuration.getProperty("HolidayPay", "true"));
    boolean currentOvertimeDailyEnabled = "true".equalsIgnoreCase(Configuration.getProperty("OvertimeDaily", "false"));
    String currentOvertimeRate = Configuration.getProperty("OvertimeRate", "1.5");
    // Default Holiday Pay Rate might now logically be "1.0" if that's more common? Adjust default if needed.
    String currentHolidayPayRate = Configuration.getProperty("HolidayPayRate", "1.0"); // Changed default assumption
    String currentOvertimeDailyThreshold = Configuration.getProperty("OvertimeDailyThreshold", "8");
%>

<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Settings</title>
<link rel="stylesheet" href="css/settings.css?v=6"> <%-- Link to settings.css --%>
<link rel="stylesheet" href="css/navbar.css">
</head>
<body>
    <%-- Consider adding Navbar include here if it's missing --%>
    <%-- <%@ include file="/WEB-INF/includes/navbar.jspf" %> --%>

    <div class="parent-container">
    <%@ include file="/WEB-INF/includes/navbar.jspf" %>
        <h1>Settings</h1>

        <form id="settingsForm" onsubmit="return false;">

            <%-- Section 1: Pay Period & Work Week --%>
            <div class="setting-item">
                 <h4 class="section-heading">Pay Period & Work Week</h4>
                 <div class="form-row">
                    <div class="form-item">
                        <label for="payPeriod">Pay Period Type:</label>
                        <select id="payPeriod" name="PayPeriodType">
                            <option value="Daily" <% if ("Daily".equals(currentPayPeriod)) out.print(" selected"); %>>Daily</option>
                            <option value="Weekly" <% if ("Weekly".equals(currentPayPeriod)) out.print(" selected"); %>>Weekly</option>
                            <option value="Bi-Weekly" <% if ("Bi-Weekly".equals(currentPayPeriod)) out.print(" selected"); %>>Bi-Weekly</option>
                            <option value="Semi-Monthly" <% if ("Semi-Monthly".equals(currentPayPeriod)) out.print(" selected"); %>>Semi-Monthly</option>
                            <option value="Monthly" <% if ("Monthly".equals(currentPayPeriod)) out.print(" selected"); %>>Monthly</option>
                        </select>
                        <span id="payPeriod-status" class="save-status"></span>
                    </div>
                    <div class="form-item">
                        <label for="firstDay">First Day of Work Week:</label>
                        <select id="firstDay" name="FirstDayOfWeek">
                             <option value="Sunday" <% if ("Sunday".equals(currentFirstDay)) out.print(" selected"); %>>Sunday</option>
                             <option value="Monday" <% if ("Monday".equals(currentFirstDay)) out.print(" selected"); %>>Monday</option>
                             <option value="Tuesday" <% if ("Tuesday".equals(currentFirstDay)) out.print(" selected"); %>>Tuesday</option>
                             <option value="Wednesday" <% if ("Wednesday".equals(currentFirstDay)) out.print(" selected"); %>>Wednesday</option>
                             <option value="Thursday" <% if ("Thursday".equals(currentFirstDay)) out.print(" selected"); %>>Thursday</option>
                             <option value="Friday" <% if ("Friday".equals(currentFirstDay)) out.print(" selected"); %>>Friday</option>
                             <option value="Saturday" <% if ("Saturday".equals(currentFirstDay)) out.print(" selected"); %>>Saturday</option>
                        </select>
                        <span id="firstDay-status" class="save-status"></span>
                    </div>
                 </div>
                 <div class="setting-warning">
                    <strong>Note:</strong> Changing 'Pay Period Type' or 'First Day of Work Week' mid-period may affect calculations. It's recommended to make these changes only after closing the current pay period.
                 </div>
            </div>

            <%-- Section 2: Grace Period --%>
            <div class="setting-item">
                 <h4 class="section-heading">Tardy / Early Out Rules</h4>
                <div class="form-row">
                    <div class="form-item">
                        <label for="gracePeriod">Grace Period (Minutes):</label>
                        <select id="gracePeriod" name="GracePeriod">
                            <option value="0" <% if ("0".equals(currentGracePeriod)) out.print(" selected"); %>>0</option>
                            <option value="1" <% if ("1".equals(currentGracePeriod)) out.print(" selected"); %>>1</option>
                            <option value="2" <% if ("2".equals(currentGracePeriod)) out.print(" selected"); %>>2</option>
                            <option value="3" <% if ("3".equals(currentGracePeriod)) out.print(" selected"); %>>3</option>
                            <option value="4" <% if ("4".equals(currentGracePeriod)) out.print(" selected"); %>>4</option>
                            <option value="5" <% if ("5".equals(currentGracePeriod)) out.print(" selected"); %>>5</option>
                            <option value="10" <% if ("10".equals(currentGracePeriod)) out.print(" selected"); %>>10</option>
                            <option value="15" <% if ("15".equals(currentGracePeriod)) out.print(" selected"); %>>15</option>
                            <option value="30" <% if ("30".equals(currentGracePeriod)) out.print(" selected"); %>>30</option>
                            <option value="60" <% if ("60".equals(currentGracePeriod)) out.print(" selected"); %>>60</option>
                        </select>
                        <span id="gracePeriod-status" class="save-status"></span>
                    </div>
                     <div class="form-item"></div> <%-- Empty item for spacing --%>
                </div>
                 <div class="setting-info">
                    <strong>Info:</strong> This Grace Period is used to determine how many minutes after the scheduled start (or before the scheduled end) an employee can clock in/out before being marked as tardy or leaving early.
                 </div>
            </div>

            <%-- Section 3: Overtime Rules (Combined Weekly & Daily) --%>
             <div class="setting-item">
                 <h4 class="section-heading">Overtime Rules</h4>

                 <%-- Row 3a: Weekly Overtime --%>
                 <div class="form-row">
                    <div class="form-item" style="flex-basis: 30%; flex-grow:0.5;">
                        <label for="overtime">Enable Overtime (Weekly):</label>
                        <span class="styled-checkbox">
                              <input type="checkbox" id="overtime" name="Overtime" value="true" <% if (currentOvertimeEnabled) out.print(" checked"); %>>
                              <label for="overtime">Enabled</label>
                        </span>
                    </div>
                    <div class="form-item">
                         <label>Overtime Rate:</label>
                         <div class="radio-group">
                             <span class="styled-radio">
                                 <input type="radio" id="overtimeRate1.5" name="OvertimeRate" value="1.5" <% if ("1.5".equals(currentOvertimeRate)) out.print(" checked"); %>>
                                 <label for="overtimeRate1.5">1.5x Rate</label>
                             </span>
                             <span class="styled-radio">
                                 <input type="radio" id="overtimeRate2.0" name="OvertimeRate" value="2.0" <% if ("2.0".equals(currentOvertimeRate)) out.print(" checked"); %>>
                                 <label for="overtimeRate2.0">2.0x Rate</label>
                             </span>
                             <span id="OvertimeRate-status" class="save-status" style="margin-left: 0; display: block; width: 100%;"></span>
                         </div>
                    </div>
                 </div>

                 <%-- Row 3b: Daily Overtime --%>
                 <div class="form-row" style="margin-top: 15px;">
                      <div class="form-item" style="flex-basis: 30%; flex-grow:0.5;">
                          <label for="overtimeDaily">Enable Daily Overtime:</label>
                          <span class="styled-checkbox">
                              <input type="checkbox" id="overtimeDaily" name="OvertimeDaily" value="true" <% if (currentOvertimeDailyEnabled) out.print(" checked"); %>>
                              <label for="overtimeDaily">Enabled (After X hours)</label>
                          </span>
                      </div>
                       <div class="form-item">
                           <label for="overtimeDailyThreshold">Daily Overtime Threshold:</label>
                           <input type="number" id="overtimeDailyThreshold" name="OvertimeDailyThreshold" min="1" step="1" placeholder="e.g., 8"
                                  value="<%= currentOvertimeDailyThreshold %>"
                                  <% if (!currentOvertimeDailyEnabled) out.print(" disabled"); %>
                                  <% if (currentOvertimeDailyEnabled) out.print(" required"); %>>
                           <span id="overtimeDailyThreshold-status" class="save-status"></span>
                       </div>
                 </div>
            </div> <%-- End Overtime Rules setting-item --%>


            <%-- Section 4: Holiday Pay Settings --%>
            <div class="setting-item">
                <h4 class="section-heading">Holiday Pay Rules</h4>
                 <div class="form-row">
                    <div class="form-item" style="flex-basis: 30%; flex-grow:0.5;">
                        <label for="holidayPay">Enable Holiday Pay:</label>
                         <span class="styled-checkbox">
                              <input type="checkbox" id="holidayPay" name="HolidayPay" value="true" <% if (currentHolidayPayEnabled) out.print(" checked"); %>>
                              <label for="holidayPay">Enabled</label>
                        </span>
                    </div>
                    <div class="form-item">
                        <label>Holiday Pay Rate:</label>
                        <div class="radio-group">
                             <%-- **** ADDED 1.0x Rate Option **** --%>
                             <span class="styled-radio">
                                 <input type="radio" id="holidayPayRate1.0" name="HolidayPayRate" value="1.0" <% if ("1.0".equals(currentHolidayPayRate)) out.print(" checked"); %> <% if (!currentHolidayPayEnabled) out.print(" disabled"); %>>
                                 <label for="holidayPayRate1.0">1.0x Rate (Regular)</label>
                             </span>
                             <%-- **** End New Option **** --%>

                             <span class="styled-radio">
                                 <input type="radio" id="holidayPayRate1.5" name="HolidayPayRate" value="1.5" <% if ("1.5".equals(currentHolidayPayRate)) out.print(" checked"); %> <% if (!currentHolidayPayEnabled) out.print(" disabled"); %>>
                                 <label for="holidayPayRate1.5">1.5x Rate</label>
                             </span>
                             <span class="styled-radio">
                                 <input type="radio" id="holidayPayRate2.0" name="HolidayPayRate" value="2.0" <% if ("2.0".equals(currentHolidayPayRate)) out.print(" checked"); %> <% if (!currentHolidayPayEnabled) out.print(" disabled"); %>>
                                 <label for="holidayPayRate2.0">2.0x Rate</label>
                             </span>
                              <span id="HolidayPayRate-status" class="save-status" style="margin-left: 0; display: block; width: 100%;"></span>
                         </div>
                    </div>
                </div>
            </div>

        </form>
    </div>

    <%-- Link the External JavaScript file --%>
    <script src="js/settings.js?v=7"></script> <%-- Increment version if JS changes needed (none for this) --%>

</body>
</html>