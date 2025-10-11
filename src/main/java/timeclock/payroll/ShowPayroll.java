package timeclock.payroll;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.Configuration;
import timeclock.db.DatabaseConnection;
import timeclock.punches.ShowPunches;
import timeclock.util.Helpers;
import timeclock.util.HolidayCalculator;
import timeclock.util.ScheduleUtils;
import timeclock.settings.StateOvertimeRules;
import timeclock.settings.StateOvertimeRuleDetail;
import timeclock.subscription.SubscriptionUtils;

public class ShowPayroll {

    private static final Logger logger = Logger.getLogger(ShowPayroll.class.getName());
    private static final String UTC_ZONE_ID = "UTC";
    private static final String SCHEDULE_DEFAULT_ZONE_ID_STR = "America/Denver"; // Fallback
    public static final double ROUNDING_FACTOR = 100.0;
    public static final double WEEKLY_OT_THRESHOLD_FLSA = 40.0;
    private static final String NOT_APPLICABLE_DISPLAY = "N/A";

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    public static boolean isPunchTypeConsideredWorkForOT(String punchType) {
        if (punchType == null) return false;
        String pTypeLower = punchType.trim().toLowerCase();
        return pTypeLower.equals("user initiated") ||
               pTypeLower.equals("supervisor override") ||
               pTypeLower.equals("sample data") ||
               pTypeLower.equals("regular");
    }

    public static boolean isHoursOnlyType(String punchType) {
        if (punchType == null) return false;
        String pTypeLower = punchType.trim().toLowerCase();
        return pTypeLower.equals("vacation") ||
               pTypeLower.equals("sick") ||
               pTypeLower.equals("personal") ||
               pTypeLower.equals("holiday") ||
               pTypeLower.equals("bereavement") ||
               pTypeLower.equals("other");
    }
    
    /**
     * Helper method to reliably fetch metadata for ALL active employees.
     */
    private static Map<Integer, Map<String, Object>> getAllActiveEmployeeMetaInfo(Connection con, int tenantId) throws SQLException {
        Map<Integer, Map<String, Object>> employeeMetaInfo = new HashMap<>();
        // --- MODIFIED: Added e.TimeZoneId and e.STATE to the SELECT statement ---
        String sql = "SELECT e.EID, e.TenantEmployeeNumber, e.FIRST_NAME, e.LAST_NAME, e.WAGE_TYPE, e.WAGE, e.TimeZoneId, e.STATE, " +
                     "s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH " +
                     "FROM employee_data e " +
                     "LEFT JOIN schedules s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME " +
                     "WHERE e.TenantID = ? AND e.ACTIVE = TRUE ORDER BY e.LAST_NAME, e.FIRST_NAME";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int globalEID = rs.getInt("EID");
                    Map<String, Object> empData = new HashMap<>();
                    empData.put("EID", globalEID);
                    empData.put("TenantEmployeeNumber", rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : globalEID);
                    empData.put("FirstName", rs.getString("FIRST_NAME"));
                    empData.put("LastName", rs.getString("LAST_NAME"));
                    empData.put("WageType", rs.getString("WAGE_TYPE"));
                    empData.put("Wage", rs.getDouble("WAGE"));
                    // --- MODIFIED: Fetch the new TimeZoneId and STATE fields ---
                    empData.put("TimeZoneId", rs.getString("TimeZoneId"));
                    empData.put("State", rs.getString("STATE"));
                    empData.put("AutoLunch", rs.getBoolean("AUTO_LUNCH"));
                    Object hrObj = rs.getObject("HRS_REQUIRED"); empData.put("HoursRequired", hrObj != null ? ((Number)hrObj).doubleValue() : null);
                    Object llObj = rs.getObject("LUNCH_LENGTH"); empData.put("LunchLength", llObj != null ? ((Number)llObj).intValue() : null);
                    employeeMetaInfo.put(globalEID, empData);
                }
            }
        }
        return employeeMetaInfo;
    }

    /**
     * Helper method that fetches only punch data for the specified period.
     */
    private static Map<Integer, List<Map<String, Object>>> getPunchesForPeriod(Connection con, int tenantId, LocalDate periodStartDate, LocalDate periodEndDate) throws SQLException {
        Map<Integer, List<Map<String, Object>>> punchesByGlobalEid = new HashMap<>();
        String sql = "SELECT p.EID, p.PUNCH_ID, p.DATE AS PUNCH_UTC_DATE, p.IN_1 AS IN_UTC, p.OUT_1 AS OUT_UTC, p.TOTAL AS STORED_TOTAL, p.OT AS STORED_OT, p.PUNCH_TYPE " +
                     "FROM punches p " +
                     "WHERE p.TenantID = ? AND p.DATE BETWEEN ? AND ? " +
                     "ORDER BY p.EID, p.DATE, p.IN_1";

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setDate(2, Date.valueOf(periodStartDate));
            ps.setDate(3, Date.valueOf(periodEndDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int globalEID = rs.getInt("EID");
                    Map<String, Object> punch = new HashMap<>();
                    punch.put("PunchID", rs.getLong("PUNCH_ID"));
                    Timestamp inTs = rs.getTimestamp("IN_UTC"); punch.put("In", (inTs != null) ? inTs.toInstant() : null);
                    Timestamp outTs = rs.getTimestamp("OUT_UTC"); punch.put("Out", (outTs != null) ? outTs.toInstant() : null);
                    punch.put("PunchUTCDate", rs.getDate("PUNCH_UTC_DATE").toLocalDate());
                    punch.put("PunchType", rs.getString("PUNCH_TYPE"));
                    double storedTotal = rs.getDouble("STORED_TOTAL"); punch.put("StoredTotal", rs.wasNull() ? null : storedTotal);
                    punchesByGlobalEid.computeIfAbsent(globalEID, k -> new ArrayList<>()).add(punch);
                }
            }
        }
        return punchesByGlobalEid;
    }

    public static List<Map<String, Object>> calculatePayrollData(int tenantId, LocalDate payPeriodStartDate, LocalDate payPeriodEndDate) {
        List<Map<String, Object>> payrollResults = new ArrayList<>();
        
        try (Connection con = DatabaseConnection.getConnection()) {
            Map<Integer, Map<String, Object>> employeeMetaInfo = getAllActiveEmployeeMetaInfo(con, tenantId);
            Map<Integer, List<Map<String, Object>>> punchesByEid = getPunchesForPeriod(con, tenantId, payPeriodStartDate, payPeriodEndDate);
            
            if (employeeMetaInfo.isEmpty()) {
                return payrollResults;
            }
            
            String tenantDefaultTimeZoneId = Configuration.getProperty(tenantId, "DefaultTimeZone", SCHEDULE_DEFAULT_ZONE_ID_STR);
            
            // Check if tenant has Pro plan for state-based overtime
            boolean hasProPlan = SubscriptionUtils.hasProPlan(tenantId);
            String overtimeType = Configuration.getProperty(tenantId, "OvertimeType", "manual"); // manual, company_state, employee_state

            boolean configWeeklyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "Overtime", "true"));
            double configStandardOtRateMultiplier = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeRate", "1.5"));
            boolean configDailyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDaily", "false"));
            double configDailyOtThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDailyThreshold", "8.0"));
            boolean configDoubleTimeEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDoubleTimeEnabled", "false"));
            double configDoubleTimeThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDoubleTimeThreshold", "12.0"));
            String configFirstDayOfWeekSetting = Configuration.getProperty(tenantId, "FirstDayOfWeek", "SUNDAY").toUpperCase(Locale.ENGLISH);
            
            // Holiday overtime settings
            boolean holidayOTEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeHolidayEnabled", "false"));
            double holidayOTRate = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeHolidayRate", "1.5"));
            
            // Days off overtime settings
            boolean daysOffOTEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDaysOffEnabled", "false"));
            double daysOffOTRate = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDaysOffRate", "1.5"));

            for (Map.Entry<Integer, Map<String, Object>> empMetaEntry : employeeMetaInfo.entrySet()) {
                int globalEID = empMetaEntry.getKey();
                Map<String, Object> empInfo = empMetaEntry.getValue();
                List<Map<String, Object>> employeePunches = punchesByEid.getOrDefault(globalEID, new ArrayList<>());

                // --- MODIFIED: Use the employee's specific time zone, with fallbacks ---
                String employeeTimeZoneIdStr = (String) empInfo.get("TimeZoneId");
                ZoneId employeeProcessingZone;
                try {
                    if (!Helpers.isStringValid(employeeTimeZoneIdStr)) throw new Exception("Employee TimeZoneId is null or invalid.");
                    employeeProcessingZone = ZoneId.of(employeeTimeZoneIdStr);
                } catch (Exception e) {
                    employeeProcessingZone = ZoneId.of(tenantDefaultTimeZoneId); // Fallback to tenant default
                }

                String wageType = (String) empInfo.getOrDefault("WageType", "Hourly");
                double wage = (Double) empInfo.getOrDefault("Wage", 0.0);
                String employeeState = (String) empInfo.get("State");
                
                StateOvertimeRuleDetail stateRules = null;
                
                if ("company_state".equals(overtimeType)) {
                    String companyState = Configuration.getProperty(tenantId, "OvertimeState", null);
                    if (companyState != null && !companyState.trim().isEmpty()) {
                        stateRules = StateOvertimeRules.getRulesForState(companyState);
                    }
                } else if ("employee_state".equals(overtimeType) && employeeState != null && !employeeState.trim().isEmpty()) {
                    stateRules = StateOvertimeRules.getRulesForState(employeeState);
                }
                
                boolean effectiveDailyOtEnabled, effectiveDoubleTimeEnabled;
                double effectiveDailyOtThreshold, effectiveDoubleTimeThreshold;
                
                if (stateRules != null) {
                    effectiveDailyOtEnabled = stateRules.isDailyOTEnabled();
                    effectiveDailyOtThreshold = stateRules.getDailyOTThreshold();
                    effectiveDoubleTimeEnabled = stateRules.isDoubleTimeEnabled();
                    effectiveDoubleTimeThreshold = stateRules.getDoubleTimeThreshold();
                } else if ("company_state".equals(overtimeType) || "employee_state".equals(overtimeType)) {
                    effectiveDailyOtEnabled = false;
                    effectiveDailyOtThreshold = 0.0;
                    effectiveDoubleTimeEnabled = false;
                    effectiveDoubleTimeThreshold = 0.0;
                } else {
                    effectiveDailyOtEnabled = configDailyOtEnabled;
                    effectiveDailyOtThreshold = configDailyOtThreshold;
                    effectiveDoubleTimeEnabled = configDoubleTimeEnabled;
                    effectiveDoubleTimeThreshold = configDoubleTimeThreshold;
                }

                Map<LocalDate, Double> dailyAggregatedWorkHours = new LinkedHashMap<>();
                Map<LocalDate, Double> dailyHolidayWorkHours = new LinkedHashMap<>();
                Map<LocalDate, Double> dailyDaysOffWorkHours = new LinkedHashMap<>();
                double periodTotalPaidNonWorkHours = 0.0;

                for (Map<String, Object> punch : employeePunches) {
                    Instant iI = (Instant) punch.get("In");
                    Instant oI = (Instant) punch.get("Out");
                    String punchType = (String) punch.get("PunchType");
                    double hoursForThisEntry = 0.0;
                    Double storedTotal = (Double) punch.get("StoredTotal");

                    if (storedTotal != null) {
                        hoursForThisEntry = storedTotal;
                    } else if (iI != null && oI != null && oI.isAfter(iI)) {
                         Duration dr = Duration.between(iI, oI);
                         double rawHours = dr.toMillis() / 3_600_000.0;
                         hoursForThisEntry = rawHours;
                         boolean empAutoLunch = (Boolean) empInfo.getOrDefault("AutoLunch", false);
                         Double empHrsRequired = (Double) empInfo.get("HoursRequired");
                         Integer empLunchLength = (Integer) empInfo.get("LunchLength");
                         if (empAutoLunch && empHrsRequired != null && empLunchLength != null && empHrsRequired > 0 && empLunchLength > 0 && rawHours > empHrsRequired) {
                             hoursForThisEntry = Math.max(0, rawHours - (empLunchLength / 60.0));
                         }
                         hoursForThisEntry = Math.round(hoursForThisEntry * ROUNDING_FACTOR) / ROUNDING_FACTOR;
                    }

                    if (isPunchTypeConsideredWorkForOT(punchType)) {
                        if (iI != null && hoursForThisEntry > 0) {
                            // --- MODIFIED: Use the employee-specific zone to determine the date ---
                            LocalDate punchDate = ZonedDateTime.ofInstant(iI, employeeProcessingZone).toLocalDate();
                            dailyAggregatedWorkHours.put(punchDate, dailyAggregatedWorkHours.getOrDefault(punchDate, 0.0) + hoursForThisEntry);
                            
                            // Check if this is holiday work
                            if (holidayOTEnabled && HolidayCalculator.isConfiguredHoliday(punchDate, tenantId)) {
                                dailyHolidayWorkHours.put(punchDate, dailyHolidayWorkHours.getOrDefault(punchDate, 0.0) + hoursForThisEntry);
                            }
                            // Check if this is days off work (only if not already a holiday)
                            else if (daysOffOTEnabled && ScheduleUtils.isScheduledDayOff(tenantId, globalEID, punchDate)) {
                                dailyDaysOffWorkHours.put(punchDate, dailyDaysOffWorkHours.getOrDefault(punchDate, 0.0) + hoursForThisEntry);
                            }
                        }
                    } else if (isHoursOnlyType(punchType)) {
                        periodTotalPaidNonWorkHours += hoursForThisEntry;
                    }
                }

                double calculatedPeriodRegular = 0.0;
                double calculatedPeriodOt = 0.0;
                double calculatedPeriodDt = 0.0;
                double calculatedPeriodHolidayOt = 0.0;
                double calculatedPeriodDaysOffOt = 0.0;

                if ("Hourly".equalsIgnoreCase(wageType)) {
                    Map<LocalDate, Double> dailyRegHours = new LinkedHashMap<>();
                    Map<LocalDate, Double> dailyOtHours = new LinkedHashMap<>();
                    Map<LocalDate, Double> dailyDtHours = new LinkedHashMap<>();

                    for (LocalDate date = payPeriodStartDate; !date.isAfter(payPeriodEndDate); date = date.plusDays(1)) {
                        double hoursWorkedToday = dailyAggregatedWorkHours.getOrDefault(date, 0.0);
                        if (hoursWorkedToday <= 0) continue;

                        double todaysReg = hoursWorkedToday;
                        double todaysOt = 0;
                        double todaysDt = 0;
                        
                        // Check if this is a holiday - if so, all hours are holiday overtime
                        if (holidayOTEnabled && dailyHolidayWorkHours.containsKey(date)) {
                            calculatedPeriodHolidayOt += hoursWorkedToday;
                            continue; // Skip normal overtime calculations for holiday work
                        }
                        
                        // Check if this is a scheduled day off - if so, all hours are days off overtime
                        if (daysOffOTEnabled && dailyDaysOffWorkHours.containsKey(date)) {
                            calculatedPeriodDaysOffOt += hoursWorkedToday;
                            continue; // Skip normal overtime calculations for days off work
                        }

                        if (effectiveDoubleTimeEnabled && todaysReg > effectiveDoubleTimeThreshold) {
                            todaysDt = todaysReg - effectiveDoubleTimeThreshold;
                            todaysReg = effectiveDoubleTimeThreshold;
                        }
                        if (effectiveDailyOtEnabled && todaysReg > effectiveDailyOtThreshold) {
                            todaysOt = todaysReg - effectiveDailyOtThreshold;
                            todaysReg = effectiveDailyOtThreshold;
                        }
                        dailyRegHours.put(date, todaysReg);
                        dailyOtHours.put(date, todaysOt);
                        dailyDtHours.put(date, todaysDt);
                    }

                    LocalDate currentScanDate = payPeriodStartDate;
                    while (!currentScanDate.isAfter(payPeriodEndDate)) {
                        LocalDate weekStart = ShowPunches.calculateWeekStart(currentScanDate, configFirstDayOfWeekSetting);
                        LocalDate weekEnd = weekStart.plusDays(6);
                        double weeklyRegHoursPool = 0;
                        double weeklyOtHoursPool = 0;
                        double weeklyDtHoursPool = 0;

                        for (int i = 0; i < 7; i++) {
                            LocalDate dayInFLSAWeek = weekStart.plusDays(i);
                            if (dayInFLSAWeek.isBefore(payPeriodStartDate) || dayInFLSAWeek.isAfter(payPeriodEndDate)) continue;
                            
                            // Skip holiday and days off from weekly overtime calculations since they're already handled
                            if (holidayOTEnabled && dailyHolidayWorkHours.containsKey(dayInFLSAWeek)) {
                                continue;
                            }
                            if (daysOffOTEnabled && dailyDaysOffWorkHours.containsKey(dayInFLSAWeek)) {
                                continue;
                            }

                            weeklyRegHoursPool += dailyRegHours.getOrDefault(dayInFLSAWeek, 0.0);
                            weeklyOtHoursPool += dailyOtHours.getOrDefault(dayInFLSAWeek, 0.0);
                            weeklyDtHoursPool += dailyDtHours.getOrDefault(dayInFLSAWeek, 0.0);
                        }

                        if (configWeeklyOtEnabled && weeklyRegHoursPool > WEEKLY_OT_THRESHOLD_FLSA) {
                            double weeklyOt = weeklyRegHoursPool - WEEKLY_OT_THRESHOLD_FLSA;
                            weeklyOtHoursPool += weeklyOt;
                            weeklyRegHoursPool = WEEKLY_OT_THRESHOLD_FLSA;
                        }

                        calculatedPeriodRegular += weeklyRegHoursPool;
                        calculatedPeriodOt += weeklyOtHoursPool;
                        calculatedPeriodDt += weeklyDtHoursPool;

                        currentScanDate = weekEnd.plusDays(1);
                    }
                } else {
                    calculatedPeriodRegular = dailyAggregatedWorkHours.values().stream().mapToDouble(Double::doubleValue).sum();
                }

                calculatedPeriodRegular += periodTotalPaidNonWorkHours;
                calculatedPeriodRegular = Math.round(Math.max(0, calculatedPeriodRegular) * 100.0) / 100.0;
                calculatedPeriodOt = Math.round(Math.max(0, calculatedPeriodOt) * 100.0) / 100.0;
                calculatedPeriodDt = Math.round(Math.max(0, calculatedPeriodDt) * 100.0) / 100.0;
                calculatedPeriodHolidayOt = Math.round(Math.max(0, calculatedPeriodHolidayOt) * 100.0) / 100.0;
                calculatedPeriodDaysOffOt = Math.round(Math.max(0, calculatedPeriodDaysOffOt) * 100.0) / 100.0;
                
                double totalPaidHours = calculatedPeriodRegular + calculatedPeriodOt + calculatedPeriodDt + calculatedPeriodHolidayOt + calculatedPeriodDaysOffOt;

                double totalPay = 0.0;
                if ("Hourly".equalsIgnoreCase(wageType)) {
                    totalPay = (calculatedPeriodRegular * wage) +
                               (calculatedPeriodOt * wage * configStandardOtRateMultiplier) +
                               (calculatedPeriodDt * wage * 2.0) +
                               (calculatedPeriodHolidayOt * wage * holidayOTRate) +
                               (calculatedPeriodDaysOffOt * wage * daysOffOTRate);
                } else {
                    int payPeriodsPerYear = 52;
                    try {
                        String payPeriodTypeSetting = Configuration.getProperty(tenantId, "PayPeriodType", "WEEKLY").toUpperCase(Locale.ENGLISH);
                        switch(payPeriodTypeSetting) {
                            case "WEEKLY": payPeriodsPerYear = 52; break;
                            case "BIWEEKLY": payPeriodsPerYear = 26; break;
                            case "SEMIMONTHLY": payPeriodsPerYear = 24; break;
                            case "MONTHLY": payPeriodsPerYear = 12; break;
                        }
                    } catch (Exception e) { payPeriodsPerYear = 52;}
                    if (payPeriodsPerYear > 0) totalPay = wage / payPeriodsPerYear;
                }
                totalPay = Math.floor(totalPay * 100.0) / 100.0;
                
                Map<String, Object> employeeResult = new HashMap<>(empInfo);
                employeeResult.put("RegularHours", calculatedPeriodRegular);
                employeeResult.put("OvertimeHours", calculatedPeriodOt);
                employeeResult.put("DoubleTimeHours", calculatedPeriodDt);
                employeeResult.put("HolidayOvertimeHours", calculatedPeriodHolidayOt);
                employeeResult.put("DaysOffOvertimeHours", calculatedPeriodDaysOffOt);
                employeeResult.put("TotalPaidHours", Math.round(totalPaidHours * 100.0)/100.0);
                employeeResult.put("TotalPay", totalPay);
                payrollResults.add(employeeResult);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL error during payroll calculation for TenantID: " + tenantId, e);
        }


        return payrollResults;
    }

    public static Map<String, Object> showPayroll(List<Map<String, Object>> calculatedData) {
        Map<String, Object> result = new HashMap<>();
        StringBuilder tableRows = new StringBuilder();
        double grandTotalPay = 0.0;

        if (calculatedData == null) {
             calculatedData = new ArrayList<>();
        }

        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
        NumberFormat hoursFormatter = NumberFormat.getNumberInstance(Locale.US);
        hoursFormatter.setMinimumFractionDigits(2);
        hoursFormatter.setMaximumFractionDigits(2);

        for (Map<String, Object> rowData : calculatedData) {
            Object tenEmpNoObj = rowData.get("TenantEmployeeNumber");
            Object globalEidObj = rowData.getOrDefault("EID", 0);
            String displayEid;
            if (tenEmpNoObj instanceof Number && ((Number)tenEmpNoObj).intValue() > 0) {
                displayEid = String.valueOf(tenEmpNoObj);
            } else {
                displayEid = String.valueOf(globalEidObj);
            }

            String fN = (String)rowData.getOrDefault("FirstName", "");
            String lN = (String)rowData.getOrDefault("LastName", "");
            String wt = (String)rowData.getOrDefault("WageType", NOT_APPLICABLE_DISPLAY);
            double w = (Double)rowData.getOrDefault("Wage", 0.0);
            double rh = (Double)rowData.getOrDefault("RegularHours", 0.0);
            double ot = (Double)rowData.getOrDefault("OvertimeHours", 0.0);
            double dt = (Double)rowData.getOrDefault("DoubleTimeHours", 0.0);
            double hot = (Double)rowData.getOrDefault("HolidayOvertimeHours", 0.0);
            double dot = (Double)rowData.getOrDefault("DaysOffOvertimeHours", 0.0);
            double totalOT = ot + hot + dot; // Exclude double time
            double tph = (Double)rowData.getOrDefault("TotalPaidHours", 0.0);
            double tp = (Double)rowData.getOrDefault("TotalPay", 0.0);

            grandTotalPay += tp;

            String fw = ("Salary".equalsIgnoreCase(wt))?(currencyFormatter.format(w)+"/yr"):(currencyFormatter.format(w)+"/hr");
            String ftp = currencyFormatter.format(tp);

            tableRows.append("<tr>");
            tableRows.append("<td>").append(displayEid).append("</td>");
            tableRows.append("<td>").append(escapeHtml(fN)).append("</td>");
            tableRows.append("<td>").append(escapeHtml(lN)).append("</td>");
            tableRows.append("<td>").append(escapeHtml(wt)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(rh)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(ot)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(dt)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(hot)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(dot)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(totalOT)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(tph)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(fw).append("</td>");
            tableRows.append("<td style='text-align:right;font-weight:bold;'>").append(ftp).append("</td>");
            tableRows.append("</tr>\n");
        }
        
        if (tableRows.length() == 0) {
            tableRows.append("<tr><td colspan='13' class='report-message-row'>No payroll data for active employees in this period.</td></tr>");
        }

        result.put("payrollHtml", tableRows.toString());
        result.put("grandTotal", Math.round(grandTotalPay*100.0)/100.0);
        return result;
    }

    public static List<Map<String, Object>> getRawPayrollData(List<Map<String, Object>> calculatedData) {
        List<Map<String, Object>> exportData = new ArrayList<>();
        if (calculatedData != null) {
            for (Map<String, Object> row : calculatedData) {
                Map<String, Object> exportRow = new HashMap<>();
                Object tenEmpNoObj = row.get("TenantEmployeeNumber");
                Object globalEidObj = row.get("EID");
                String displayEid;
                 if (tenEmpNoObj instanceof Number && ((Number)tenEmpNoObj).intValue() > 0) {
                    displayEid = String.valueOf(tenEmpNoObj);
                } else if (globalEidObj != null) {
                    displayEid = String.valueOf(globalEidObj);
                } else {
                    displayEid = "N/A";
                }
                exportRow.put("EID", displayEid);
                exportRow.put("FirstName", row.getOrDefault("FirstName", ""));
                exportRow.put("LastName", row.getOrDefault("LastName", ""));
                exportRow.put("WageType", row.getOrDefault("WageType", ""));
                exportRow.put("RegularHours", row.getOrDefault("RegularHours", 0.0));
                exportRow.put("OvertimeHours", row.getOrDefault("OvertimeHours", 0.0));
                exportRow.put("DoubleTimeHours", row.getOrDefault("DoubleTimeHours", 0.0));
                exportRow.put("HolidayOTHours", row.getOrDefault("HolidayOvertimeHours", 0.0));
                exportRow.put("DaysOffOTHours", row.getOrDefault("DaysOffOvertimeHours", 0.0));
                double totalOvertimeForExport = ((Double)row.getOrDefault("OvertimeHours", 0.0)) + 
                                               ((Double)row.getOrDefault("HolidayOvertimeHours", 0.0)) + 
                                               ((Double)row.getOrDefault("DaysOffOvertimeHours", 0.0)); // Exclude double time
                exportRow.put("TotalOvertimeHours", totalOvertimeForExport);
                exportRow.put("TotalPaidHours", row.getOrDefault("TotalPaidHours",0.0));
                exportRow.put("Wage", row.getOrDefault("Wage", 0.0));
                exportRow.put("TotalPay", row.getOrDefault("TotalPay", 0.0));
                exportData.add(exportRow);
            }
        }
        return exportData;
    }

    public static String showExceptionReport(int tenantId, LocalDate periodStartDate, LocalDate periodEndDate, String userTimeZoneIdStr) {
        DateTimeFormatter dateFormatForReport = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);
        DateTimeFormatter timeFormatForReport = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

        ZoneId displayZoneId;
        try {
            if (!Helpers.isStringValid(userTimeZoneIdStr)) {
                throw new IllegalArgumentException("User TimeZoneId string is invalid or null for Exception Report.");
            }
            displayZoneId = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[ShowPayroll.showExceptionReport] Invalid userTimeZoneIdStr: '" + userTimeZoneIdStr + "'. Defaulting to UTC. Error: " + e.getMessage());
            displayZoneId = ZoneId.of(UTC_ZONE_ID);
        }

        StringBuilder html = new StringBuilder();
        boolean foundExceptions = false;

        String sql = "SELECT p.PUNCH_ID, ed.EID, ed.TenantEmployeeNumber, ed.FIRST_NAME, ed.LAST_NAME, p.DATE AS PUNCH_TABLE_UTC_DATE, p.IN_1 AS IN_UTC " +
                     "FROM employee_data ed JOIN punches p ON ed.TenantID = p.TenantID AND ed.EID = p.EID " +
                     "WHERE p.TenantID = ? AND p.OUT_1 IS NULL AND ed.ACTIVE = TRUE AND p.DATE BETWEEN ? AND ? " +
                     "AND p.PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Sample Data', 'Regular') " +
                     "ORDER BY ed.LAST_NAME, ed.FIRST_NAME, p.DATE, p.IN_1";

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setDate(2, Date.valueOf(periodStartDate));
            ps.setDate(3, Date.valueOf(periodEndDate));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    foundExceptions = true;
                    long pId = rs.getLong("PUNCH_ID");
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber")!=null?rs.getInt("TenantEmployeeNumber"):null;
                    String dEID = (tENo!=null&&tENo>0)?String.valueOf(tENo):String.valueOf(gEID);
                    String fN=rs.getString("FIRST_NAME");
                    String lN=rs.getString("LAST_NAME");
                    Timestamp iTsUtc=rs.getTimestamp("IN_UTC");

                    String formattedDisplayDate = ZonedDateTime.ofInstant(iTsUtc.toInstant(), displayZoneId).format(dateFormatForReport);
                    String iC = ZonedDateTime.ofInstant(iTsUtc.toInstant(), displayZoneId).format(timeFormatForReport);
                    String oC = "<span class='missing-punch-placeholder'>Missing OUT</span>";

                    html.append("<tr data-punch-id=\"").append(pId).append("\" data-eid=\"").append(gEID).append("\">")
                        .append("<td>").append(dEID).append("</td>")
                        .append("<td>").append(escapeHtml(fN)).append("</td>")
                        .append("<td>").append(escapeHtml(lN)).append("</td>")
                        .append("<td>").append(formattedDisplayDate).append("</td>")
                        .append("<td>").append(iC).append("</td>")
                        .append("<td class='empty-cell'>").append(oC).append("</td>")
                        .append("</tr>\n");
                }
            }
            if (!foundExceptions) {
                return "NO_EXCEPTIONS";
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error generating ExceptionReport for T:"+tenantId, e);
            return "<tr><td colspan='6' class='report-error-row'>Error generating exception report from database.</td></tr>";
        }
        return html.toString();
    }
}