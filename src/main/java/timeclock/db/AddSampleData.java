package timeclock.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Date;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.Configuration;
import timeclock.punches.ShowPunches; // For helper

public class AddSampleData {

    private static final Logger logger = Logger.getLogger(AddSampleData.class.getName());
    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("America/Denver");
    private static final double ROUNDING_FACTOR = 10000.0; // For intermediate rounding
    private static final int TARGET_MISSING_PUNCHES = 3;

    // Simple record to hold schedule times
    private record ScheduleInfo(String name, LocalTime shiftStart, LocalTime shiftEnd) {}

    public static String addSampleData() {
        logger.info("Starting to add sample data...");
        Map<String, Object> configSettings = new HashMap<>();
        int gracePeriod = 0;
        // Read configuration settings
        try {
            configSettings.put("dailyOtEnabled", "true".equalsIgnoreCase(Configuration.getProperty("OvertimeDaily", "false")));
            configSettings.put("dailyThreshold", Double.parseDouble(Configuration.getProperty("OvertimeDailyThreshold", "8.0")));
            configSettings.put("autoLunch", true); configSettings.put("hrsRequired", 6); configSettings.put("lunchLength", 30);
            gracePeriod = Integer.parseInt(Configuration.getProperty("GracePeriod", "0").trim());
            logger.info("Config settings read (GracePeriod=" + gracePeriod + ").");
        } catch (Exception e) {
             logger.log(Level.WARNING, "Could not read all config settings.", e);
             gracePeriod = 0; // Default to 0 on error
             configSettings.putIfAbsent("dailyOtEnabled", false); configSettings.putIfAbsent("dailyThreshold", 8.0); configSettings.putIfAbsent("autoLunch", true); configSettings.putIfAbsent("hrsRequired", 6); configSettings.putIfAbsent("lunchLength", 30);
        }

        // --- Setup ---
        Object[][] employees = { // Full Employee data array
             {"Administrator", "", "None", "Open", "None", "Administrator", "4462 Murietta Ave", "Sherman Oaks", "CA", "91340", "(818)990-4450", "admin@gmail.com", "Executive", 18, 21, 24, LocalDate.now().minusMonths(17), "Full Time", "Salary", 1000000.0},
             {"John", "Candy", "Cinema", "Open", "John Candy", "Administrator", "4462 Murietta Ave", "Sherman Oaks", "CA", "91340", "(818)990-4450", "admin@gmail.com", "Executive", 18, 21, 24, LocalDate.now().minusMonths(22), "Full Time", "Salary", 1000000.0},
             {"George", "Peppard", "Cinema", "Open", "John Candy", "User", "4483 Stern Ave", "Sherman Oaks", "CA", "91343", "(818)990-4457", "gpeppard@gmail.com", "Standard", 18, 21, 24, LocalDate.now().minusMonths(21), "Full Time", "Hourly", 10.20},
             {"Ringo", "Starr", "Music", "First Shift", "Ringo Starr", "Administrator", "14350 Addison St", "Van Nuys", "CA", "91342", "212-859-7752", "rstarr@gmail.com", "Executive", 18, 21, 24, LocalDate.now().minusMonths(12), "Full Time", "Salary", 1000000.0},
             {"Jeff", "Beck", "Music", "Weekend Warriors", "Ringo Starr", "User", "4540 Alpine Ave", "Boulder", "CO", "80305", "(720-648-5588)", "jbeck@gmail.com", "Standard", 5, 5, 5, LocalDate.now().minusMonths(1), "Part Time", "Hourly", 18.60},
             {"Janice", "Joplin", "Music", "First Shift", "Ringo Starr", "User", "1847 Whittier St", "N Hills", "CA", "91320", "(818)556-9988", "jjoplin@gmail.com", "Standard", 5, 5, 5, LocalDate.now().minusMonths(51), "Part Time", "Hourly", 20.50},
             {"Steve", "Luthaker", "Music", "Weekend Warriors", "Ringo Starr", "User", "16555 Yankee Ct", "Las Vegas", "NV", "87556", "215-888-9632", "sluthaker", "Standard", 5, 5, 5, LocalDate.now().minusMonths(21), "Part Time", "Hourly", 19.40},
             {"Donald", "Fagen", "Music", "First Shift", "Ringo Starr", "User", "7350 Lankershim Bl", "N Hollywood", "CA", "91340", "(818)785-8895", "dfagen@gmail.com", "Standard", 4, 3, 4, LocalDate.now().minusMonths(11), "Full Time", "Hourly", 15.65},
             {"Jessica", "Alba", "Cinema", "First Shift", "John Candy", "User", "4877 Spring Lane", "Studio City", "CA", "91054", "(818)996-9885", "jalba@gmail.com", "Standard", 6, 7, 2, LocalDate.now().minusMonths(20), "Full Time", "Hourly", 25.00},
             {"Charlie", "Sheen", "Television", "Open", "Alan Alda", "User", "5849 Ute St", "Salt Lake City", "UT", "85996", "850-998-8475", "csheen@gmail.com", "Standard", 1, 1, 0, LocalDate.now().minusMonths(31), "Full Time", "Hourly", 22.50},
             {"Jack", "Nicholson", "Cinema", "Open", "John Candy", "User", "4458 Deer Ln", "Park City", "UT", "94117", "404-885-9856", "jnicholson@gmail.com", "Standard", 4, 3, 5, LocalDate.now().minusMonths(32), "Full Time", "Hourly", 25.00},
             {"Adam", "Sandler", "Cinema", "Second Shift", "John Candy", "User", "4699 Rio Ave", "Las Vegas", "NV", "85633", "818-899-7532", "asandler@gmail.com", "Standard", 4, 6, 3, LocalDate.now().minusMonths(1), "Full Time", "Hourly", 15.25},
             {"Michael", "Jackson", "Music", "Second Shift", "Ringo Starr", "User", "15425 Havenhurst Ave", "Encino", "CA", "91033", "(818)855-9663", "mjackson@gmail.com", "Standard", 18, 21, 24, LocalDate.now().minusMonths(11), "Full Time", "Hourly", 26.50},
             {"Robert", "Plant", "Music", "Second Shift", "Ringo Starr", "User", "4650 Wilbur Ave", "Tarzana", "CA", "94115", "(818)665-9886", "rplant@gmail.com", "Standard", 9, 2, 2, LocalDate.now().minusMonths(32), "Part Time", "Hourly", 16.75},
             {"Sandra", "Bullock", "Cinema", "Second Shift", "John Candy", "User", "22150 Roscoe Bl", "Canoga Park", "CA", "91346", "213-995-6877", "sbullock@gmail.com", "Standard", 10, 11, 2, LocalDate.now().minusMonths(21), "Full Time", "Hourly", 16.90},
             {"Mila", "Kunis", "Television", "Second Shift", "Alan Alda", "User", "4410 Sale Ave", "Woodland Hills", "CA", "91323", "720-558-9665", "mkunis@gmail.com", "Standard", 8, 1, 4, LocalDate.now().minusMonths(21), "Part Time", "Hourly", 16.00},
             {"John", "Lithgow", "Television", "Third Shift", "Alan Alda", "User", "21565 Arapahoe Trail", "Chatsworth", "CA", "91365", "(818)996-3352", "jlithgow@gmail.com", "Standard", 2, 2, 3, LocalDate.now().minusMonths(21), "Full Time", "Hourly", 21.00},
             {"Ozzy", "Osbourne", "Music", "Third Shift", "Ringo Starr", "User", "940 W 3900 S", "Salt Lake City", "UT", "84115", "415-589-9632", "oosbourne@gmail.com", "Standard", 1, 1, 2, LocalDate.now().minusMonths(11), "Full Time", "Hourly", 19.50},
             {"Alan", "Alda", "Television", "First Shift", "Alan Alda", "Administrator", "4563 Samual Colt Ct", "Park City", "UT", "93155", "715-869-8552", "aalda@gmail.com", "Executive", 18, 21, 24, LocalDate.now().minusMonths(51), "Full Time", "Salary", 1000000.0},
             {"Drew", "Barrymore", "Cinema", "Weekend Warriors", "John Candy", "User", "880 W Moorhead Cir", "Boulder", "CO", "80305", "(720)855-9647", "dbarrymore@gmail.com", "Standard", 6, 2, 3, LocalDate.now().minusMonths(21), "Full Time", "Hourly", 18.65}
        };
        int daysToGenerate = 5;
        int totalPunchesToGenerate = employees.length * daysToGenerate;
        Set<Integer> missingPunchIndices = new HashSet<>();
        if (TARGET_MISSING_PUNCHES > 0 && totalPunchesToGenerate > 0) { Random r=new Random(); int t=Math.min(TARGET_MISSING_PUNCHES,totalPunchesToGenerate); while(missingPunchIndices.size()<t){missingPunchIndices.add(r.nextInt(totalPunchesToGenerate));} logger.info("Pre-selected missing OUT indices: " + missingPunchIndices); }

        Connection con = null;
        String currentStep = "Initialization";
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(true);

            // --- Robust Data Clearing Order ---
            currentStep = "Data Clearing";
            logger.info("Clearing existing data (respecting FK constraints)...");
            try (Statement stmt = con.createStatement()) {
                int delCount; String tbl;
                tbl = "PUNCHES"; try { delCount = stmt.executeUpdate("DELETE FROM "+tbl); logger.info("Deleted " + delCount + " from "+tbl); } catch (SQLException e) { logger.log(Level.WARNING, "Could not delete from "+tbl+" (continuing): "+e.getMessage());}
                tbl = "ARCHIVED_PUNCHES"; try { delCount = stmt.executeUpdate("DELETE FROM "+tbl); logger.info("Deleted " + delCount + " from "+tbl); } catch (SQLException e) { logger.log(Level.WARNING, "Could not delete from "+tbl+" (continuing): "+e.getMessage());}
                tbl = "EMPLOYEE_DATA"; try { delCount = stmt.executeUpdate("DELETE FROM "+tbl); logger.info("Deleted " + delCount + " from "+tbl); } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL: Could not delete from "+tbl, e); throw e; }
                tbl = "SCHEDULES"; try { delCount = stmt.executeUpdate("DELETE FROM "+tbl); logger.info("Deleted " + delCount + " from "+tbl); } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL: Could not delete from "+tbl, e); throw e; }
                tbl = "DEPARTMENTS"; try { delCount = stmt.executeUpdate("DELETE FROM "+tbl); logger.info("Deleted " + delCount + " from "+tbl); } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL: Could not delete from "+tbl, e); throw e; }
                tbl = "ACCRUALS"; try { delCount = stmt.executeUpdate("DELETE FROM "+tbl); logger.info("Deleted " + delCount + " from "+tbl); } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL: Could not delete from "+tbl, e); throw e; }
                logger.info("Data clearing complete.");

                 logger.info("Resetting Auto Increments...");
                 try { stmt.executeUpdate("ALTER TABLE EMPLOYEE_DATA AUTO_INCREMENT = 1;"); } catch (SQLException e) { logger.warning("Could not reset AI on EMPLOYEE_DATA: "+e.getMessage()); }
                 try { stmt.executeUpdate("ALTER TABLE PUNCHES AUTO_INCREMENT = 1;"); } catch (SQLException e) { logger.warning("Could not reset AI on PUNCHES: "+e.getMessage()); }
                 logger.info("Auto Increments reset attempt complete.");
            }


            // --- Add Static Data ---
            currentStep = "Adding Departments"; logger.info(currentStep + "...");
            try(PreparedStatement ps = con.prepareStatement("INSERT INTO DEPARTMENTS VALUES (?, ?, ?)")) {
                 ps.setString(1, "None"); ps.setString(2, "No Department Assignment"); ps.setString(3, "None"); ps.addBatch();
                 ps.setString(1, "Cinema"); ps.setString(2, "Movie Stars"); ps.setString(3, "John Candy"); ps.addBatch();
                 ps.setString(1, "Music"); ps.setString(2, "Rock Stars"); ps.setString(3, "Ringo Starr"); ps.addBatch();
                 ps.setString(1, "Television"); ps.setString(2, "TV Personalities"); ps.setString(3, "Alan Alda"); ps.addBatch();
                 ps.executeBatch();
            } logger.info("Departments added.");

            currentStep = "Adding Accrual Policies"; logger.info(currentStep + "...");
             try(PreparedStatement ps = con.prepareStatement("INSERT INTO ACCRUALS (NAME, VACATION, SICK, PERSONAL) VALUES (?, ?, ?, ?)")) {
                ps.setString(1, "None"); ps.setInt(2, 0); ps.setInt(3, 0); ps.setInt(4, 0); ps.executeUpdate();
                ps.setString(1, "Standard"); ps.setInt(2, 5); ps.setInt(3, 5); ps.setInt(4, 5); ps.executeUpdate();
                ps.setString(1, "Executive"); ps.setInt(2, 30); ps.setInt(3, 30); ps.setInt(4, 30); ps.executeUpdate();
             } logger.info("Accrual Policies added.");

            currentStep = "Adding Schedules"; logger.info(currentStep + "...");
            int schedulesInsertedCount = 0; boolean scheduleInsertFailed = false;
            try {
                try(PreparedStatement psAddOpen = con.prepareStatement("INSERT INTO SCHEDULES (NAME) VALUES ('Open')")) { if(psAddOpen.executeUpdate() > 0) schedulesInsertedCount++; } catch(SQLException e){ logger.log(Level.FINE, "Ignoring potential duplicate insert for 'Open': " + e.getMessage()); }
                try(PreparedStatement psAddOpenAuto = con.prepareStatement("INSERT INTO SCHEDULES (NAME, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH) VALUES ('Open with Auto Lunch', TRUE, 6, 30)")) { if(psAddOpenAuto.executeUpdate() > 0) schedulesInsertedCount++; } catch(SQLException e){ logger.log(Level.FINE, "Ignoring potential duplicate insert for 'Open with Auto Lunch': " + e.getMessage()); }
                String scheduleInsertSql = "INSERT INTO SCHEDULES (NAME, SHIFT_START, LUNCH_START, LUNCH_END, SHIFT_END, DAYS_WORKED, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH, WORK_SCHEDULE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement psAddSchedules = con.prepareStatement(scheduleInsertSql)) {
                    Object[][] schedules = {
                       {"First Shift", Time.valueOf("07:00:00"), Time.valueOf("11:00:00"), Time.valueOf("11:30:00"), Time.valueOf("15:30:00"), "Mon, Tue, Wed, Thu, Fri", true, 6, 30, "Full Time"},
                       {"Weekend Warriors", null, null, null, null, "Sat, Sun", true, 8, 30, "Part Time"},
                       {"Second Shift", Time.valueOf("12:00:00"), Time.valueOf("16:00:00"), Time.valueOf("16:30:00"), Time.valueOf("20:30:00"), "Mon, Tue, Wed, Thu, Fri", true, 6, 30, "Full Time"},
                       {"Third Shift", Time.valueOf("20:00:00"), Time.valueOf("00:00:00"), Time.valueOf("01:00:00"), Time.valueOf("05:00:00"), "Mon, Tue, Wed, Thu, Fri", false, 0, 0, "Full Time"}
                    };
                    for(Object[] sched : schedules) {
                        String currentSchedName = (String) sched[0];
                        try {
                             psAddSchedules.clearParameters(); psAddSchedules.setString(1, currentSchedName);
                             Time startTime = (Time) sched[1]; Time lunchStart = (Time) sched[2]; Time lunchEnd = (Time) sched[3]; Time endTime = (Time) sched[4];
                             if (startTime != null) psAddSchedules.setTime(2, startTime); else psAddSchedules.setNull(2, Types.TIME);
                             if (lunchStart != null) psAddSchedules.setTime(3, lunchStart); else psAddSchedules.setNull(3, Types.TIME);
                             if (lunchEnd != null) psAddSchedules.setTime(4, lunchEnd); else psAddSchedules.setNull(4, Types.TIME);
                             if (endTime != null) psAddSchedules.setTime(5, endTime); else psAddSchedules.setNull(5, Types.TIME);
                             psAddSchedules.setString(6, (String) sched[5]); psAddSchedules.setBoolean(7, (Boolean) sched[6]);
                             psAddSchedules.setInt(8, (Integer) sched[7]); psAddSchedules.setInt(9, (Integer) sched[8]); psAddSchedules.setString(10, (String) sched[9]);
                             if (psAddSchedules.executeUpdate() > 0) schedulesInsertedCount++; else { logger.warning("executeUpdate returned 0 for schedule: " + currentSchedName); scheduleInsertFailed = true;}
                        } catch (Exception paramEx) { logger.log(Level.SEVERE, "Error inserting schedule: " + currentSchedName, paramEx); scheduleInsertFailed = true; break; }
                    }
                } logger.info("Schedules insertion attempt complete. Total successful Inserts/Updates: " + schedulesInsertedCount);
                if (scheduleInsertFailed) { throw new SQLException("Schedule insertion process failed."); }
            } catch (SQLException schedEx) { logger.log(Level.SEVERE, "SQLException during schedule insertion.", schedEx); throw schedEx; }
            int expectedScheduleCount = 6; int actualScheduleCount = -1; logger.info("Verifying schedule count...");
            try (Statement stmt = con.createStatement(); ResultSet rsCount = stmt.executeQuery("SELECT COUNT(*) FROM SCHEDULES")) { if (rsCount.next()) actualScheduleCount = rsCount.getInt(1); else logger.severe("COUNT query returned no rows!"); } catch(SQLException countEx) { logger.log(Level.SEVERE, "Error executing schedule count query", countEx); }
            logger.info("VERIFICATION: Actual SCHEDULES count = " + actualScheduleCount); if (actualScheduleCount < expectedScheduleCount) { throw new SQLException("Schedule verification failed (Count=" + actualScheduleCount + ", Expected=" + expectedScheduleCount + ")."); }


            // --- Add Employees (with robust verification) ---
            currentStep = "Adding Employees"; logger.info(currentStep + "...");
            String employeeSql = "INSERT INTO EMPLOYEE_DATA (FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, SUPERVISOR, PERMISSIONS, ADDRESS, CITY, STATE, ZIP, PHONE, EMAIL, ACCRUAL_POLICY, VACATION_HOURS, SICK_HOURS, PERSONAL_HOURS, HIRE_DATE, WORK_SCHEDULE, WAGE_TYPE, WAGE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            boolean employeeInsertFailed = false; int employeesInsertedCount = 0;
            try(PreparedStatement psAddEmployees = con.prepareStatement(employeeSql)) {
                Random randomAccrual = new Random(); double maxAccrualHours = 10.0;
                for (Object[] emp : employees) { String empNameForLog = emp[0] + " " + emp[1]; try { psAddEmployees.setString(1,(String)emp[0]); psAddEmployees.setString(2,(String)emp[1]); psAddEmployees.setString(3,(String)emp[2]); psAddEmployees.setString(4,(String)emp[3]); psAddEmployees.setString(5,(String)emp[4]); psAddEmployees.setString(6,(String)emp[5]); psAddEmployees.setString(7,(String)emp[6]); psAddEmployees.setString(8,(String)emp[7]); psAddEmployees.setString(9,(String)emp[8]); psAddEmployees.setString(10,(String)emp[9]); psAddEmployees.setString(11,(String)emp[10]); psAddEmployees.setString(12,(String)emp[11]); psAddEmployees.setString(13,(String)emp[12]); psAddEmployees.setDouble(14, Math.round(randomAccrual.nextDouble()*maxAccrualHours*100.0)/100.0); psAddEmployees.setDouble(15, Math.round(randomAccrual.nextDouble()*maxAccrualHours*100.0)/100.0); psAddEmployees.setDouble(16, Math.round(randomAccrual.nextDouble()*maxAccrualHours*100.0)/100.0); psAddEmployees.setDate(17, java.sql.Date.valueOf((LocalDate)emp[16])); psAddEmployees.setString(18,(String)emp[17]); psAddEmployees.setString(19,(String)emp[18]); psAddEmployees.setDouble(20,(Double)emp[19]); psAddEmployees.addBatch(); } catch (Exception paramEx) { logger.log(Level.SEVERE, "Error setting parameters for employee: " + empNameForLog, paramEx); employeeInsertFailed = true; break; } }
                if (!employeeInsertFailed) { int[] res = psAddEmployees.executeBatch(); logger.info("Employee batch execution results: " + Arrays.toString(res)); for (int r : res) { if (r == Statement.EXECUTE_FAILED) employeeInsertFailed = true; else if (r >= 0) employeesInsertedCount += r; } } else { logger.severe("Skipped employee batch execution."); }
                if (employeeInsertFailed || employeesInsertedCount < employees.length) { throw new SQLException("Employee insertion failed or count mismatch. Expected=" + employees.length + ", Success=" + employeesInsertedCount); }
                else { logger.info("Employees added successfully ("+employeesInsertedCount+" reported rows)."); }
            }


            // --- Pre-load Schedule Times Map ---
            currentStep = "Loading Schedule Map"; Map<String, ScheduleInfo> scheduleTimesMap = new HashMap<>(); logger.info("Loading schedule times into map...");
            String scheduleQuery = "SELECT NAME, SHIFT_START, SHIFT_END FROM SCHEDULES";
            try (Statement stmt = con.createStatement(); ResultSet rs = stmt.executeQuery(scheduleQuery)) { while (rs.next()) { String name=null; try { name=rs.getString(1); Time st=rs.getTime(2); Time et=rs.getTime(3); scheduleTimesMap.put(name, new ScheduleInfo(name, st!=null?st.toLocalTime():null, et!=null?et.toLocalTime():null));} catch (Exception e){logger.log(Level.WARNING,"Error processing schedule row: "+name,e);}} } logger.info("Loaded " + scheduleTimesMap.size() + " schedule time entries into map.");
            if (scheduleTimesMap.size() < actualScheduleCount) { logger.warning("Did not load all verified schedules into map! Count: " + scheduleTimesMap.size() + ", Verified: " + actualScheduleCount); }
            if (scheduleTimesMap.isEmpty()) { throw new SQLException("Failed to load any schedule times into map."); }


            // --- Add Sample Punches (Calculates Flags using CONFIGURED Grace Period)---
            currentStep = "Adding Punches"; logger.info(currentStep + " (Calculating Flags & Totals w/ Grace=" + gracePeriod + ")...");
            String punchInsertSql = "INSERT INTO PUNCHES (EID, DATE, IN_1, OUT_1, TOTAL, OT, LATE, EARLY_OUTS, PUNCH_TYPE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psAddPunches = con.prepareStatement(punchInsertSql)) {
                 int currentPunchIndex = 0; Random timeRand = new Random(); LocalDate todayInScheduleZone = LocalDate.now(SCHEDULE_ZONE);
                 for (int i=0; i < employees.length; i++) {
                     int eid=i+1; Object[] emp = employees[i]; String scheduleName = (String)emp[3]; ScheduleInfo currentSchedule = scheduleTimesMap.get(scheduleName);
                     LocalTime baseInTime = (currentSchedule != null && currentSchedule.shiftStart() != null) ? currentSchedule.shiftStart() : null;
                     LocalTime baseOutTime = (currentSchedule != null && currentSchedule.shiftEnd() != null) ? currentSchedule.shiftEnd() : null;
                     boolean scheduleHasTimes = (baseInTime != null && baseOutTime != null);

                     for (int daysAgo=daysToGenerate; daysAgo >= 1; daysAgo--) {
                         LocalDate punchDateLocal = todayInScheduleZone.minusDays(daysAgo); DayOfWeek dayOfWeek = punchDateLocal.getDayOfWeek();
                         if ("Weekend Warriors".equals(scheduleName) && !(dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)) { currentPunchIndex++; continue; }

                         // Generate times
                         LocalTime randomInTimeLocal; LocalTime randomOutTimeLocal; if (scheduleHasTimes) { long io=(long)((timeRand.nextDouble()-0.5)*10*60); long oo=(long)((timeRand.nextDouble()-0.5)*10*60); randomInTimeLocal = baseInTime.plusSeconds(io); randomOutTimeLocal = baseOutTime.plusSeconds(oo); } else { int rih=8+timeRand.nextInt(4); int roh=15+timeRand.nextInt(4); randomInTimeLocal = LocalTime.of(rih, timeRand.nextInt(60), timeRand.nextInt(60)); randomOutTimeLocal = LocalTime.of(roh, timeRand.nextInt(60), timeRand.nextInt(60)); }
                         ZonedDateTime punchInZoned = punchDateLocal.atTime(randomInTimeLocal).atZone(SCHEDULE_ZONE); ZonedDateTime punchOutZoned = null; boolean makeMissingCheck = missingPunchIndices.contains(currentPunchIndex); if (!makeMissingCheck) { punchOutZoned = punchDateLocal.atTime(randomOutTimeLocal).atZone(SCHEDULE_ZONE); if (punchOutZoned.isBefore(punchInZoned)) { punchOutZoned = punchInZoned.plusHours(8).plusMinutes((long)((timeRand.nextDouble() - 0.5) * 10)); } }
                         Instant punchInInstant = punchInZoned.toInstant(); Instant punchOutInstant = (punchOutZoned != null) ? punchOutZoned.toInstant() : null; LocalDate punchUtcDate = punchInInstant.atZone(ZoneOffset.UTC).toLocalDate();

                         // Calculate Flags using Configured Grace
                         boolean isLateForSample = false; boolean isEarlyForSample = false;
                         if (scheduleHasTimes) { LocalTime gst=null; LocalTime get=null; if (gracePeriod>0) { if(baseInTime!=null)gst=baseInTime.plusMinutes(gracePeriod); if(baseOutTime!=null)get=baseOutTime.minusMinutes(gracePeriod); } LocalTime lct=(gst!=null)?gst:baseInTime; LocalTime ect=(get!=null)?get:baseOutTime; if(lct!=null){isLateForSample=randomInTimeLocal.isAfter(lct);} if(ect!=null && punchOutZoned!=null){isEarlyForSample=randomOutTimeLocal.isBefore(ect);} }

                         // Calculate TOTAL Hours using PunchOut Logic
                         double rawTotalHours = 0.0; double adjustedTotalHours = 0.0; double otHours = 0.0;
                         if (punchInInstant != null && punchOutInstant != null && punchOutInstant.isAfter(punchInInstant)) {
                             Duration duration = Duration.between(punchInInstant, punchOutInstant); rawTotalHours = duration.toMillis() / 3_600_000.0; rawTotalHours = Math.round(rawTotalHours * ROUNDING_FACTOR) / ROUNDING_FACTOR;
                             try { adjustedTotalHours = ShowPunches.applyAutoLunch(con, eid, rawTotalHours); adjustedTotalHours = Math.round(adjustedTotalHours * 100.0) / 100.0; logger.finest("EID " + eid + " Date " + punchUtcDate + ": RawHrs=" + rawTotalHours + ", AdjustedHrs=" + adjustedTotalHours); } catch (Exception lunchEx) { logger.log(Level.WARNING, "Auto-lunch failed for EID "+eid, lunchEx); adjustedTotalHours = Math.round(rawTotalHours * 100.0) / 100.0; }
                         } // adjustedTotalHours remains 0.0 if no valid duration or OUT is missing
                         otHours = 0.0; // Keep OT simple

                         logger.fine("EID " + eid + " Date " + punchUtcDate + ": Saving TOTAL=" + adjustedTotalHours + ", OT=" + otHours + ", LATE=" + isLateForSample + ", EARLY=" + isEarlyForSample);

                         // Set parameters
                         psAddPunches.setInt(1, eid); psAddPunches.setDate(2, java.sql.Date.valueOf(punchUtcDate)); psAddPunches.setTimestamp(3, java.sql.Timestamp.from(punchInInstant)); ShowPunches.setOptionalTimestamp(psAddPunches, 4, (punchOutInstant != null) ? Timestamp.from(punchOutInstant) : null);
                         ShowPunches.setOptionalDouble(psAddPunches, 5, adjustedTotalHours); // TOTAL
                         ShowPunches.setOptionalDouble(psAddPunches, 6, otHours);            // OT
                         psAddPunches.setBoolean(7, isLateForSample); // LATE flag
                         psAddPunches.setBoolean(8, isEarlyForSample); // EARLY_OUTS flag
                         psAddPunches.setString(9, "Sample Data"); psAddPunches.addBatch();
                         currentPunchIndex++;
                     } // End days loop
                     psAddPunches.executeBatch();
                 } // End employee loop
            } logger.info("Punches added.");

        } catch (SQLException e) { logger.log(Level.SEVERE, "SQLException during sample data step: " + currentStep, e); e.printStackTrace(); return "Error: SQL Error during " + currentStep + " - " + e.getMessage();}
        catch (Exception e) { logger.log(Level.SEVERE, "Unexpected exception during sample data step: " + currentStep, e); e.printStackTrace(); return "Error: Unexpected error during " + currentStep + " - " + e.getMessage(); }
        finally { if (con != null) { try { con.close(); logger.fine("DB connection closed."); } catch (SQLException ce) {} } }
         logger.info("Sample data addition process complete.");
        return "Sample data added successfully.";
    }

    // REMOVED calculateSamplePunchHours helper method - logic moved inline above

} // End Class