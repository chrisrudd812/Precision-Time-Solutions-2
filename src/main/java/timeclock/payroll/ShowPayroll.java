package timeclock.payroll;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
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

    // Using ShowPunches version is better, but keep local as fallback/reference.
    private static LocalDate calculateWeekStart(LocalDate currentDate, String firstDayOfWeekSetting) {
       return ShowPunches.calculateWeekStart(currentDate, firstDayOfWeekSetting);
    }


    private static Map<Integer, List<Map<String, Object>>> getPunchesAndMetaData(
        int tenantId, LocalDate periodStartDate, LocalDate periodEndDate,
        Map<Integer, Map<String, Object>> employeeMetaInfoOutput) {

        Map<Integer, List<Map<String, Object>>> punchesByGlobalEid = new HashMap<>();
        // Removed p.DT AS STORED_DT - THIS IS THE FIX FOR THE SQL ERROR
        // We will read OT, but won't rely on it for calculation, but can use it as a check if needed.
        String sql = "SELECT e.EID, e.TenantEmployeeNumber, e.FIRST_NAME, e.LAST_NAME, e.WAGE_TYPE, e.WAGE, " +
                     "s.AUTO_LUNCH, s.HRS_REQUIRED, s.LUNCH_LENGTH, " +
                     "p.PUNCH_ID, p.DATE AS PUNCH_UTC_DATE, p.IN_1 AS IN_UTC, p.OUT_1 AS OUT_UTC, p.TOTAL AS STORED_TOTAL, p.OT AS STORED_OT, p.PUNCH_TYPE " + // Removed p.DT
                     "FROM EMPLOYEE_DATA e " +
                     "LEFT JOIN SCHEDULES s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME " +
                     "JOIN PUNCHES p ON e.EID = p.EID AND e.TenantID = p.TenantID " +
                     "WHERE e.TenantID = ? AND e.ACTIVE = TRUE AND p.DATE BETWEEN ? AND ? " +
                     "ORDER BY e.EID, p.DATE, p.IN_1";

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setDate(2, Date.valueOf(periodStartDate));
            ps.setDate(3, Date.valueOf(periodEndDate));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int globalEID = rs.getInt("EID");
                    employeeMetaInfoOutput.computeIfAbsent(globalEID, k -> {
                        Map<String, Object> empData = new HashMap<>();
                        try {
                            empData.put("EID", globalEID);
                            empData.put("TenantEmployeeNumber", rs.getObject("TenantEmployeeNumber") != null ? rs.getInt("TenantEmployeeNumber") : globalEID);
                            empData.put("FirstName", rs.getString("FIRST_NAME"));
                            empData.put("LastName", rs.getString("LAST_NAME"));
                            empData.put("WageType", rs.getString("WAGE_TYPE"));
                            empData.put("Wage", rs.getDouble("WAGE"));
                            empData.put("AutoLunch", rs.getBoolean("AUTO_LUNCH"));
                            Object hrObj = rs.getObject("HRS_REQUIRED"); empData.put("HoursRequired", hrObj != null ? ((Number)hrObj).doubleValue() : null);
                            Object llObj = rs.getObject("LUNCH_LENGTH"); empData.put("LunchLength", llObj != null ? ((Number)llObj).intValue() : null);
                        } catch (SQLException sqle) { throw new RuntimeException("Error reading employee meta info from DB: " + sqle.getMessage(), sqle); }
                        return empData;
                    });
                    Map<String, Object> punch = new HashMap<>();
                    punch.put("PunchID", rs.getLong("PUNCH_ID"));
                    Timestamp inTs = rs.getTimestamp("IN_UTC"); punch.put("In", (inTs != null) ? inTs.toInstant() : null);
                    Timestamp outTs = rs.getTimestamp("OUT_UTC"); punch.put("Out", (outTs != null) ? outTs.toInstant() : null);
                    punch.put("PunchUTCDate", rs.getDate("PUNCH_UTC_DATE").toLocalDate());
                    punch.put("PunchType", rs.getString("PUNCH_TYPE"));
                    double storedTotal = rs.getDouble("STORED_TOTAL"); punch.put("StoredTotal", rs.wasNull() ? null : storedTotal);
                    double storedOt = rs.getDouble("STORED_OT"); punch.put("StoredOt", rs.wasNull() ? null : storedOt);
                    punch.put("StoredDt", 0.0); // DT not read from PUNCHES table
                    punchesByGlobalEid.computeIfAbsent(globalEID, k -> new ArrayList<>()).add(punch);
                }
            }
        } catch (SQLException e) { logger.log(Level.SEVERE, "Error fetching payroll punch data for TenantID: " + tenantId, e); }
          catch (RuntimeException e) { logger.log(Level.SEVERE, "Error processing fetched payroll punch data during meta info for TenantID: " + tenantId, e); }
        return punchesByGlobalEid;
    }


    public static List<Map<String, Object>> calculatePayrollData(int tenantId, LocalDate payPeriodStartDate, LocalDate payPeriodEndDate) {
        List<Map<String, Object>> payrollResults = new ArrayList<>();
        Map<Integer, Map<String, Object>> employeeMetaInfo = new HashMap<>();
        Map<Integer, List<Map<String, Object>>> punchesByEid = getPunchesAndMetaData(tenantId, payPeriodStartDate, payPeriodEndDate, employeeMetaInfo);

        // --- Fetch Configuration ---
        String tenantProcessingTimeZoneIdStr = Configuration.getProperty(tenantId, "DefaultTimeZone", SCHEDULE_DEFAULT_ZONE_ID_STR);
        ZoneId processingZone;
        try {
            if (!ShowPunches.isValid(tenantProcessingTimeZoneIdStr)) throw new Exception("Tenant DefaultTimeZone is invalid.");
            processingZone = ZoneId.of(tenantProcessingTimeZoneIdStr);
        } catch (Exception e) {
            processingZone = ZoneId.of(SCHEDULE_DEFAULT_ZONE_ID_STR); // Hard fallback
            logger.log(Level.WARNING, "Invalid Tenant DefaultTimeZone '" + tenantProcessingTimeZoneIdStr + "'. Using fallback: " + processingZone, e);
        }
        logger.info("[ShowPayroll.calculatePayrollData] Using processingZone: " + processingZone + " for T:" + tenantId);

        boolean configWeeklyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "Overtime", "true"));
        double configStandardOtRateMultiplier = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeRate", "1.5"));
        boolean configDailyOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDaily", "false"));
        double configDailyOtThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDailyThreshold", "8.0"));
        boolean configDoubleTimeEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeDoubleTimeEnabled", "false"));
        double configDoubleTimeThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeDoubleTimeThreshold", "12.0"));
        boolean configSeventhDayOtEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "OvertimeSeventhDayEnabled", "false"));
        double configSeventhDayOTThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeSeventhDayOTThreshold", "0.0"));
        double configSeventhDayDTThreshold = Double.parseDouble(Configuration.getProperty(tenantId, "OvertimeSeventhDayDTThreshold", "8.0"));
        String configFirstDayOfWeekSetting = Configuration.getProperty(tenantId, "FirstDayOfWeek", "SUNDAY").toUpperCase(Locale.ENGLISH);

        // --- Include Active Employees without Punches ---
        List<Map<String,Object>> allActiveEmps = ShowPunches.getActiveEmployeesForDropdown(tenantId);
        for(Map<String,Object> empStub : allActiveEmps) {
            Integer eid = (Integer)empStub.get("eid");
            if(eid != null && !employeeMetaInfo.containsKey(eid)) {
                Map<String, Object> fullEmpInfo = ShowPunches.getEmployeeTimecardInfo(tenantId, eid);
                if (fullEmpInfo != null) { employeeMetaInfo.put(eid, fullEmpInfo); }
            }
        }
        if(employeeMetaInfo.isEmpty()){ return payrollResults; }

        // --- Process Each Employee ---
        for (Map.Entry<Integer, Map<String, Object>> empMetaEntry : employeeMetaInfo.entrySet()) {
            int globalEID = empMetaEntry.getKey();
            Map<String, Object> empInfo = empMetaEntry.getValue();
            List<Map<String, Object>> employeePunches = punchesByEid.getOrDefault(globalEID, new ArrayList<>());

            String wageType = (String) empInfo.getOrDefault("WageType", empInfo.get("wageType"));
            if (wageType == null) wageType = "Hourly";
            Object wageObj = empInfo.getOrDefault("Wage", empInfo.get("wage"));
            double wage = 0.0;
            if (wageObj instanceof Number) { wage = ((Number)wageObj).doubleValue(); }
            else if (wageObj != null) { try { wage = Double.parseDouble(wageObj.toString()); } catch (NumberFormatException e) { wage = 0.0; } }

            Map<LocalDate, Double> dailyAggregatedWorkHours = new LinkedHashMap<>();
            double periodTotalPaidNonWorkHours = 0.0;
            Map<LocalDate, List<Map<String, Object>>> punchesByLocalDate = new LinkedHashMap<>();

            // --- Calculate Punch Total and Aggregate by Local Date ---
            for (Map<String, Object> punch : employeePunches) {
                Instant iI = (Instant) punch.get("In"); Instant oI = (Instant) punch.get("Out");
                String punchType = (String) punch.get("PunchType");
                double hoursForThisEntry = 0.0;
                Double storedTotal = (Double) punch.get("StoredTotal");

                if (storedTotal != null) {
                    hoursForThisEntry = storedTotal;
                } else if (iI != null && oI != null && oI.isAfter(iI)) { // Calculate if StoredTotal is missing
                     Duration dr = Duration.between(iI, oI); double rawHours = dr.toMillis() / 3_600_000.0;
                     hoursForThisEntry = rawHours;
                     boolean empAutoLunch = (Boolean) empInfo.getOrDefault("AutoLunch", false);
                     Double empHrsRequired = (Double) empInfo.get("HoursRequired");
                     Integer empLunchLength = (Integer) empInfo.get("LunchLength");
                     if (empAutoLunch && empHrsRequired != null && empLunchLength != null && empHrsRequired > 0 && empLunchLength > 0 && rawHours > empHrsRequired) {
                         hoursForThisEntry = Math.max(0, rawHours - (empLunchLength / 60.0));
                     }
                     hoursForThisEntry = Math.round(hoursForThisEntry * ROUNDING_FACTOR) / ROUNDING_FACTOR;
                }
                punch.put("CalculatedTotal", hoursForThisEntry); // Store calculated total on punch map

                if (isPunchTypeConsideredWorkForOT(punchType)) {
                    if (iI != null && hoursForThisEntry > 0) {
                        LocalDate punchDate = ZonedDateTime.ofInstant(iI, processingZone).toLocalDate();
                        dailyAggregatedWorkHours.put(punchDate, dailyAggregatedWorkHours.getOrDefault(punchDate, 0.0) + hoursForThisEntry);
                        punchesByLocalDate.computeIfAbsent(punchDate, k -> new ArrayList<>()).add(punch);
                    }
                } else if (isHoursOnlyType(punchType)) {
                    periodTotalPaidNonWorkHours += hoursForThisEntry;
                }
            }

            double calculatedPeriodRegular = 0.0;
            double calculatedPeriodOt = 0.0;
            double calculatedPeriodDt = 0.0;

            if ("Hourly".equalsIgnoreCase(wageType)) {
                Map<LocalDate, Double> dailyRegHours = new LinkedHashMap<>();
                Map<LocalDate, Double> dailyOtHours = new LinkedHashMap<>();
                Map<LocalDate, Double> dailyDtHours = new LinkedHashMap<>();

                // --- Daily Rules Pass ---
                for (LocalDate date = payPeriodStartDate; !date.isAfter(payPeriodEndDate); date = date.plusDays(1)) {
                    double hoursWorkedToday = dailyAggregatedWorkHours.getOrDefault(date, 0.0);
                    if (hoursWorkedToday <= 0) continue;

                    double todaysReg = hoursWorkedToday;
                    double todaysOt = 0;
                    double todaysDt = 0;

                    if (configDoubleTimeEnabled && todaysReg > configDoubleTimeThreshold) {
                        todaysDt = todaysReg - configDoubleTimeThreshold;
                        todaysReg = configDoubleTimeThreshold;
                    }
                    if (configDailyOtEnabled && todaysReg > configDailyOtThreshold) {
                        todaysOt = todaysReg - configDailyOtThreshold;
                        todaysReg = configDailyOtThreshold;
                    }
                    dailyRegHours.put(date, todaysReg);
                    dailyOtHours.put(date, todaysOt);
                    dailyDtHours.put(date, todaysDt);
                }

                // --- Weekly & 7th Day Pass ---
                LocalDate currentScanDate = payPeriodStartDate;
                while (!currentScanDate.isAfter(payPeriodEndDate)) {
                    LocalDate weekStart = calculateWeekStart(currentScanDate, configFirstDayOfWeekSetting);
                    LocalDate weekEnd = weekStart.plusDays(6);
                    double weeklyRegHoursPool = 0;
                    double weeklyOtHoursPool = 0;
                    double weeklyDtHoursPool = 0;
                    int daysWorkedThisFLSAWeek = 0;
                    List<LocalDate> actualWorkDaysInFLSAWeek = new ArrayList<>();

                    for (int i = 0; i < 7; i++) {
                        LocalDate dayInFLSAWeek = weekStart.plusDays(i);
                        if (dayInFLSAWeek.isBefore(payPeriodStartDate) || dayInFLSAWeek.isAfter(payPeriodEndDate)) continue;

                        weeklyRegHoursPool += dailyRegHours.getOrDefault(dayInFLSAWeek, 0.0);
                        weeklyOtHoursPool += dailyOtHours.getOrDefault(dayInFLSAWeek, 0.0);
                        weeklyDtHoursPool += dailyDtHours.getOrDefault(dayInFLSAWeek, 0.0);

                        if (dailyAggregatedWorkHours.getOrDefault(dayInFLSAWeek, 0.0) > 0.001) {
                            daysWorkedThisFLSAWeek++;
                            actualWorkDaysInFLSAWeek.add(dayInFLSAWeek);
                        }
                    }

                    // 7th Day Logic (Apply 7th day rules if enabled and applicable)
                    if (configSeventhDayOtEnabled && daysWorkedThisFLSAWeek >= 7) {
                       LocalDate seventhDayDate = actualWorkDaysInFLSAWeek.get(6); // Assumes sorted list
                       double seventhDayReg = dailyRegHours.getOrDefault(seventhDayDate, 0.0);
                       double seventhDayOt = dailyOtHours.getOrDefault(seventhDayDate, 0.0);
                       double seventhDayDt = dailyDtHours.getOrDefault(seventhDayDate, 0.0);
                       double seventhDayTotal = seventhDayReg + seventhDayOt + seventhDayDt;

                       if (seventhDayTotal > 0) {
                           double hoursToReclassifyOt = 0;
                           double hoursToReclassifyDt = 0;

                           // Check if 7th day rules push hours into DT
                           if (seventhDayTotal > configSeventhDayDTThreshold) {
                               hoursToReclassifyDt = seventhDayTotal - configSeventhDayDTThreshold;
                               // Take from REG first, then OT
                               double dtFromReg = Math.min(seventhDayReg, hoursToReclassifyDt);
                               weeklyRegHoursPool -= dtFromReg; dailyRegHours.put(seventhDayDate, seventhDayReg - dtFromReg);
                               weeklyDtHoursPool += dtFromReg; dailyDtHours.put(seventhDayDate, seventhDayDt + dtFromReg);
                               hoursToReclassifyDt -= dtFromReg;
                               if(hoursToReclassifyDt > 0){
                                 double dtFromOt = Math.min(seventhDayOt, hoursToReclassifyDt);
                                 weeklyOtHoursPool -= dtFromOt; dailyOtHours.put(seventhDayDate, seventhDayOt - dtFromOt);
                                 weeklyDtHoursPool += dtFromOt; dailyDtHours.put(seventhDayDate, dailyDtHours.get(seventhDayDate)+dtFromOt);
                               }
                           }
                           // Update total and check OT rules
                           seventhDayTotal = dailyRegHours.get(seventhDayDate) + dailyOtHours.get(seventhDayDate); // Don't include DT now
                           if (seventhDayTotal > configSeventhDayOTThreshold) {
                               hoursToReclassifyOt = seventhDayTotal - configSeventhDayOTThreshold;
                               double otFromReg = Math.min(dailyRegHours.get(seventhDayDate), hoursToReclassifyOt);
                               weeklyRegHoursPool -= otFromReg; dailyRegHours.put(seventhDayDate, dailyRegHours.get(seventhDayDate) - otFromReg);
                               weeklyOtHoursPool += otFromReg; dailyOtHours.put(seventhDayDate, dailyOtHours.get(seventhDayDate) + otFromReg);
                           }
                       }
                    }

                    // Weekly FLSA OT (on remaining REG hours)
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

            } else { // Salary or other non-hourly
                calculatedPeriodRegular = dailyAggregatedWorkHours.values().stream().mapToDouble(Double::doubleValue).sum();
            }

            calculatedPeriodRegular += periodTotalPaidNonWorkHours;

            calculatedPeriodRegular = Math.round(Math.max(0, calculatedPeriodRegular) * 100.0) / 100.0;
            calculatedPeriodOt = Math.round(Math.max(0, calculatedPeriodOt) * 100.0) / 100.0;
            calculatedPeriodDt = Math.round(Math.max(0, calculatedPeriodDt) * 100.0) / 100.0;

            // --- Pay Calculation ---
            double totalPay = 0.0;
            if ("Hourly".equalsIgnoreCase(wageType)) {
                totalPay = (calculatedPeriodRegular * wage) +
                           (calculatedPeriodOt * wage * configStandardOtRateMultiplier) +
                           (calculatedPeriodDt * wage * 2.0);
            } else {
                 // ... (Salary calculation as before) ...
                int payPeriodsPerYear = 52;
                try {
                    String payPeriodTypeSetting = Configuration.getProperty(tenantId, "PayPeriodType", "WEEKLY").toUpperCase(Locale.ENGLISH);
                    switch(payPeriodTypeSetting) {
                        case "DAILY": payPeriodsPerYear = 365; break;
                        case "WEEKLY": payPeriodsPerYear = 52; break;
                        case "BIWEEKLY": payPeriodsPerYear = 26; break;
                        case "SEMIMONTHLY": payPeriodsPerYear = 24; break;
                        case "MONTHLY": payPeriodsPerYear = 12; break;
                        default: payPeriodsPerYear = 52; break;
                    }
                } catch (Exception e) { payPeriodsPerYear = 52;}
                if (payPeriodsPerYear > 0) totalPay = wage / payPeriodsPerYear;
                else { totalPay = 0; }
            }
            totalPay = Math.floor(totalPay * 100.0) / 100.0;

            // --- Store Results ---
            Map<String, Object> employeeResult = new HashMap<>(empInfo); // Start with meta info
            employeeResult.put("RegularHours", calculatedPeriodRegular);
            employeeResult.put("OvertimeHours", calculatedPeriodOt);
            employeeResult.put("DoubleTimeHours", calculatedPeriodDt);
            employeeResult.put("TotalPaidHours", Math.round((calculatedPeriodRegular + calculatedPeriodOt + calculatedPeriodDt) * 100.0)/100.0);
            employeeResult.put("TotalPay", totalPay);
            payrollResults.add(employeeResult);
        }
        logger.info("Payroll calculation complete for TenantID: " + tenantId + ". Employees processed: " + employeeMetaInfo.size());
        return payrollResults;
    }

    // --- showPayroll method remains the same ---
    public static Map<String, Object> showPayroll(List<Map<String, Object>> calculatedData) {
        // ... (implementation as provided by user) ...
        Map<String, Object> result = new HashMap<>();
        StringBuilder tableRows = new StringBuilder();
        double grandTotalPay = 0.0;
        final int numberOfColumns = 10;

        result.put("payrollHtml", "<tr><td colspan='" + numberOfColumns + "' style='text-align:center;'>No payroll data to display.</td></tr>");
        result.put("grandTotal", 0.0);

        if (calculatedData == null || calculatedData.isEmpty()) {
            logger.warning("showPayroll called with empty or null data.");
            return result;
        }
        NumberFormat currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
        NumberFormat hoursFormatter = NumberFormat.getNumberInstance(Locale.US);
        hoursFormatter.setMinimumFractionDigits(2);
        hoursFormatter.setMaximumFractionDigits(2);

        boolean foundData = false;
        for (Map<String, Object> rowData : calculatedData) {
            foundData = true;
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
            double tph = (Double)rowData.getOrDefault("TotalPaidHours", rh + ot + dt);
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
            tableRows.append("<td style='text-align:right;'>").append(hoursFormatter.format(tph)).append("</td>");
            tableRows.append("<td style='text-align:right;'>").append(fw).append("</td>");
            tableRows.append("<td style='text-align:right;font-weight:bold;'>").append(ftp).append("</td>");
            tableRows.append("</tr>\n");
        }
        if(foundData) result.put("payrollHtml", tableRows.toString());
        result.put("grandTotal", Math.round(grandTotalPay*100.0)/100.0);
        return result;
    }

    // --- getRawPayrollData method remains the same ---
     public static List<Map<String, Object>> getRawPayrollData(List<Map<String, Object>> calculatedData) {
        // ... (implementation as provided by user) ...
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
                exportRow.put("TotalPaidHours", row.getOrDefault("TotalPaidHours",0.0));
                exportRow.put("Wage", row.getOrDefault("Wage", 0.0));
                exportRow.put("TotalPay", row.getOrDefault("TotalPay", 0.0));
                exportData.add(exportRow);
            }
        }
        return exportData;
    }

    // --- showExceptionReport method remains the same (with its previous TZ fix) ---
    public static String showExceptionReport(int tenantId, LocalDate periodStartDate, LocalDate periodEndDate, String userTimeZoneIdStr) {
        // ... (implementation as provided previously) ...
         DateTimeFormatter dateFormatForReport = DateTimeFormatter.ofPattern("MM/dd/yyyy", Locale.ENGLISH);
        DateTimeFormatter timeFormatForReport = DateTimeFormatter.ofPattern("hh:mm:ss a", Locale.ENGLISH);

        ZoneId displayZoneId;
        try {
            if (!ShowPunches.isValid(userTimeZoneIdStr)) {
                throw new IllegalArgumentException("User TimeZoneId string is invalid or null for Exception Report.");
            }
            displayZoneId = ZoneId.of(userTimeZoneIdStr);
        } catch (Exception e) {
            logger.log(Level.WARNING, "[ShowPayroll.showExceptionReport] Invalid userTimeZoneIdStr: '" + userTimeZoneIdStr + "'. Defaulting to UTC. Error: " + e.getMessage());
            displayZoneId = ZoneId.of(UTC_ZONE_ID);
        }
        logger.info("[ShowPayroll.showExceptionReport] Using displayZoneId: " + displayZoneId + " for TenantID: " + tenantId);

        StringBuilder html = new StringBuilder();
        boolean foundExceptions = false;

        if (tenantId <= 0) {
            return "<tr><td colspan='6' class='report-error-row'>Invalid tenant context.</td></tr>";
        }
        if (periodStartDate == null || periodEndDate == null) {
            logger.warning("showExceptionReport called with null dates for TenantID: " + tenantId);
            return "<tr><td colspan='6' class='report-error-row'>Pay period dates not set. Cannot generate report.</td></tr>";
        }
         if (periodStartDate.isAfter(periodEndDate)) {
             logger.warning("showExceptionReport called with start date after end date for TenantID: " + tenantId);
            return "<tr><td colspan='6' class='report-error-row'>Start date cannot be after end date.</td></tr>";
        }

        String sql = "SELECT p.PUNCH_ID, ed.EID, ed.TenantEmployeeNumber, ed.FIRST_NAME, ed.LAST_NAME, p.DATE AS PUNCH_TABLE_UTC_DATE, p.IN_1 AS IN_UTC, p.OUT_1 AS OUT_UTC " +
                     "FROM EMPLOYEE_DATA ed JOIN PUNCHES p ON ed.TenantID = p.TenantID AND ed.EID = p.EID " +
                     "WHERE p.TenantID = ? " +
                     "  AND p.OUT_1 IS NULL " +
                     "  AND ed.ACTIVE = TRUE " +
                     "  AND p.DATE BETWEEN ? AND ? " +
                     "  AND p.PUNCH_TYPE IN ('User Initiated', 'Supervisor Override', 'Sample Data', 'Regular') " +
                     "ORDER BY ed.LAST_NAME, ed.FIRST_NAME, p.DATE, p.IN_1";

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setDate(2, Date.valueOf(periodStartDate));
            ps.setDate(3, Date.valueOf(LocalDate.now().minusDays(1)));

            logger.info("Executing Exception Report query for TenantID: " + tenantId + " from " + periodStartDate + " to " + periodEndDate + " using TZ: " + displayZoneId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    foundExceptions = true;
                    long pId = rs.getLong("PUNCH_ID");
                    int gEID = rs.getInt("EID");
                    Integer tENo = rs.getObject("TenantEmployeeNumber")!=null?rs.getInt("TenantEmployeeNumber"):null;
                    String dEID = (tENo!=null&&tENo>0)?String.valueOf(tENo):String.valueOf(gEID);
                    String fN=rs.getString("FIRST_NAME");
                    String lN=rs.getString("LAST_NAME");
                    Date punchTableUtcDate = rs.getDate("PUNCH_TABLE_UTC_DATE");
                    Timestamp iTsUtc=rs.getTimestamp("IN_UTC");

                    String formattedDisplayDate;
                    String iC;

                    if (iTsUtc != null) {
                        ZonedDateTime zdtIn = ZonedDateTime.ofInstant(iTsUtc.toInstant(), displayZoneId);
                        formattedDisplayDate = zdtIn.format(dateFormatForReport);
                        iC = zdtIn.format(timeFormatForReport);
                    } else if (punchTableUtcDate != null) {
                        formattedDisplayDate = punchTableUtcDate.toLocalDate().format(dateFormatForReport);
                        iC = "<span class='missing-punch-placeholder'>Missing IN</span>";
                    } else {
                        formattedDisplayDate = NOT_APPLICABLE_DISPLAY;
                        iC = "<span class='missing-punch-placeholder'>Missing IN</span>";
                    }
                    String oC = "<span class='missing-punch-placeholder'>Missing OUT</span>";
                    String iCls=(iTsUtc==null)?" class='empty-cell missing-punch-placeholder'":"";
                    String oCls=" class='empty-cell missing-punch-placeholder'";

                    html.append("<tr data-punch-id=\"").append(pId).append("\" data-eid=\"").append(gEID).append("\">")
                        .append("<td>").append(dEID).append("</td>")
                        .append("<td>").append(escapeHtml(fN)).append("</td>")
                        .append("<td>").append(escapeHtml(lN)).append("</td>")
                        .append("<td>").append(formattedDisplayDate).append("</td>")
                        .append("<td").append(iCls).append(">").append(iC).append("</td>")
                        .append("<td").append(oCls).append(">").append(oC).append("</td>")
                        .append("</tr>\n");
                }
            }
            if (!foundExceptions) {
                logger.info("No exceptions found within pay period for TenantID: " + tenantId);
                return "NO_EXCEPTIONS";
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error generating ExceptionReport for T:"+tenantId, e);
            return "<tr><td colspan='6' class='report-error-row'>Error generating exception report from database.</td></tr>";
        }
        return html.toString();
    }
}