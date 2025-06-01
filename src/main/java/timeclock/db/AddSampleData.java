package timeclock.db; // Make sure this is your correct package

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
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.mindrot.jbcrypt.BCrypt;

// Assuming Configuration class is used for settings not hardcoded
// import timeclock.Configuration;
import timeclock.punches.ShowPunches; // For utility methods like setOptionalDouble

public class AddSampleData {

    private static final Logger logger = Logger.getLogger(AddSampleData.class.getName());
    private static final ZoneId SCHEDULE_ZONE = ZoneId.of("America/Denver");
    private static final double ROUNDING_FACTOR = 10000.0;
    private static final int TARGET_MISSING_PUNCHES = 3;
    private static final int DEV_TENANT_ID = 1;

    private record ScheduleInfo(String name, LocalTime shiftStart, LocalTime shiftEnd, boolean autoLunch, Integer hrsRequired, Integer lunchLength) {}

    public static String addSampleData() throws RuntimeException {
        logger.info("Starting to add/reset sample data for Tenant ID: " + DEV_TENANT_ID);
        String currentStep = "Initialization";

        String defaultPassword = "1234";
        String defaultPasswordHash = BCrypt.hashpw(defaultPassword, BCrypt.gensalt(12));
        logger.info("Calculated default password hash for '1234'.");

        Object[][] employees = {
            {"Administrator", "", "None", "Open", "None", "Administrator", "1 Admin Way", "Adminville", "AS", "00000", "(000)000-0000", "admin@example.com", "Executive", LocalDate.now().minusYears(2), "Full Time", "Salary", 120000.00},
            {"John", "Candy", "Cinema", "Open", "John Candy", "Administrator", "4462 Murietta Ave", "Sherman Oaks", "CA", "91340", "(818)990-4450", "jcandy@example.com", "Executive", LocalDate.now().minusMonths(22), "Full Time", "Salary", 100000.00},
            {"George", "Peppard", "Cinema", "Open", "John Candy", "User", "4483 Stern Ave", "Sherman Oaks", "CA", "91343", "(818)990-4457", "gpeppard@example.com", "Standard", LocalDate.now().minusMonths(21), "Full Time", "Hourly", 20.20},
            {"Ringo", "Starr", "Music", "First Shift", "Ringo Starr", "Administrator", "14350 Addison St", "Van Nuys", "CA", "91342", "212-859-7752", "rstarr@example.com", "Executive", LocalDate.now().minusMonths(12), "Full Time", "Salary", 90000.00},
            {"Jeff", "Beck", "Music", "Weekend Warriors", "Ringo Starr", "User", "4540 Alpine Ave", "Boulder", "CO", "80305", "(720)648-5588", "jbeck@example.com", "Standard", LocalDate.now().minusMonths(1), "Part Time", "Hourly", 28.60},
            {"Janice", "Joplin", "Music", "First Shift", "Ringo Starr", "User", "1847 Whittier St", "N Hills", "CA", "91320", "(818)556-9988", "jjoplin@example.com", "Standard", LocalDate.now().minusMonths(51), "Part Time", "Hourly", 20.50},
            {"Steve", "Luthaker", "Music", "Weekend Warriors", "Ringo Starr", "User", "16555 Yankee Ct", "Las Vegas", "NV", "87556", "215-888-9632", "sluthaker@example.com", "Standard", LocalDate.now().minusMonths(21), "Part Time", "Hourly", 19.40},
            {"Donald", "Fagen", "Music", "First Shift", "Ringo Starr", "User", "7350 Lankershim Bl", "N Hollywood", "CA", "91340", "(818)785-8895", "dfagen@example.com", "Standard", LocalDate.now().minusMonths(11), "Full Time", "Hourly", 15.65},
            {"Jessica", "Alba", "Cinema", "First Shift", "John Candy", "User", "4877 Spring Lane", "Studio City", "CA", "91054", "(818)996-9885", "jalba@example.com", "Standard", LocalDate.now().minusMonths(20), "Full Time", "Hourly", 25.00},
            {"Charlie", "Sheen", "Television", "Open", "Alan Alda", "User", "5849 Ute St", "Salt Lake City", "UT", "85996", "850-998-8475", "csheen@example.com", "Standard", LocalDate.now().minusMonths(31), "Full Time", "Hourly", 22.50},
            {"Jack", "Nicholson", "Cinema", "Open", "John Candy", "User", "4458 Deer Ln", "Park City", "UT", "94117", "404-885-9856", "jnicholson@example.com", "Standard", LocalDate.now().minusMonths(32), "Full Time", "Hourly", 25.00},
            {"Adam", "Sandler", "Cinema", "Second Shift", "John Candy", "User", "4699 Rio Ave", "Las Vegas", "NV", "85633", "818-899-7532", "asandler@example.com", "Standard", LocalDate.now().minusMonths(1), "Full Time", "Hourly", 15.25},
            {"Michael", "Jackson", "Music", "Second Shift", "Ringo Starr", "User", "15425 Havenhurst Ave", "Encino", "CA", "91033", "(818)855-9663", "mjackson@example.com", "Standard", LocalDate.now().minusMonths(11), "Full Time", "Hourly", 26.50},
            {"Robert", "Plant", "Music", "Second Shift", "Ringo Starr", "User", "4650 Wilbur Ave", "Tarzana", "CA", "94115", "(818)665-9886", "rplant@example.com", "Standard", LocalDate.now().minusMonths(32), "Part Time", "Hourly", 16.75},
            {"Sandra", "Bullock", "Cinema", "Second Shift", "John Candy", "User", "22150 Roscoe Bl", "Canoga Park", "CA", "91346", "213-995-6877", "sbullock@example.com", "Standard", LocalDate.now().minusMonths(21), "Full Time", "Hourly", 16.90},
            {"Mila", "Kunis", "Television", "Second Shift", "Alan Alda", "User", "4410 Sale Ave", "Woodland Hills", "CA", "91323", "720-558-9665", "mkunis@example.com", "Standard", LocalDate.now().minusMonths(21), "Part Time", "Hourly", 16.00},
            {"John", "Lithgow", "Television", "Third Shift", "Alan Alda", "User", "21565 Arapahoe Trail", "Chatsworth", "CA", "91365", "(818)996-3352", "jlithgow@example.com", "Standard", LocalDate.now().minusMonths(21), "Full Time", "Hourly", 21.00},
            {"Ozzy", "Osbourne", "Music", "Third Shift", "Ringo Starr", "User", "940 W 3900 S", "Salt Lake City", "UT", "84115", "415-589-9632", "oosbourne@example.com", "Standard", LocalDate.now().minusMonths(11), "Full Time", "Hourly", 19.50},
            {"Alan", "Alda", "Television", "First Shift", "Alan Alda", "Administrator", "4563 Samual Colt Ct", "Park City", "UT", "93155", "715-869-8552", "aalda@example.com", "Executive", LocalDate.now().minusMonths(51), "Full Time", "Salary", 100000.00},
            {"Drew", "Barrymore", "Cinema", "Weekend Warriors", "John Candy", "User", "880 W Moorhead Cir", "Boulder", "CO", "80305", "(720)855-9647", "dbarrymore@example.com", "Standard", LocalDate.now().minusMonths(21), "Full Time", "Hourly", 18.65}
        };

        int daysToGeneratePunches = 14;
        int totalPunchesToGenerate = employees.length * daysToGeneratePunches;
        Set<Integer> missingPunchIndices = new HashSet<>();
        if (TARGET_MISSING_PUNCHES > 0 && totalPunchesToGenerate > 0) {
            Random r = new Random(); int t = Math.min(TARGET_MISSING_PUNCHES, totalPunchesToGenerate);
            while (missingPunchIndices.size() < t) { missingPunchIndices.add(r.nextInt(totalPunchesToGenerate)); }
            logger.info("Pre-selected " + missingPunchIndices.size() + " indices for missing OUT punches.");
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            con.setAutoCommit(true);

            currentStep = "Data Clearing for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
            try (Statement stmt = con.createStatement()) {
                String[] tablesToDeleteFrom = {"PUNCHES", "ARCHIVED_PUNCHES", "PAYROLL_HISTORY", "EMPLOYEE_DATA", "SETTINGS", "SCHEDULES", "DEPARTMENTS", "ACCRUALS"};
                for (String tbl : tablesToDeleteFrom) {
                    try { stmt.executeUpdate("DELETE FROM " + tbl + " WHERE TenantID = " + DEV_TENANT_ID); logger.info("Deleted from " + tbl + " for TenantID " + DEV_TENANT_ID); }
                    catch (SQLException e) { logger.log(Level.INFO, "Info: Could not delete from " + tbl + " (may not exist/no data): " + e.getMessage()); }
                }
                try { stmt.executeUpdate("DELETE FROM Tenants WHERE TenantID = " + DEV_TENANT_ID); logger.info("Deleted from Tenants for TenantID " + DEV_TENANT_ID); }
                catch (SQLException e) { logger.log(Level.INFO, "Info: Could not delete from Tenants: " + e.getMessage());}
                logger.info("Data clearing attempt for Tenant " + DEV_TENANT_ID + " complete.");
            }

            currentStep = "Ensuring All Tables Exist";
            logger.info(currentStep + "...");
            try (Statement stmt = con.createStatement()) {
                logger.fine("Creating Tenants table if not exists...");
                String createTenantsTableSQL = "CREATE TABLE IF NOT EXISTS Tenants (" +
                    "TenantID INT AUTO_INCREMENT PRIMARY KEY, " +
                    "CompanyName VARCHAR(255) NOT NULL, " +
                    "CompanyIdentifier VARCHAR(100) NULL, " +
                    "PhoneNumber VARCHAR(30) NULL, Address VARCHAR(255) NULL, City VARCHAR(100) NULL, State VARCHAR(50) NULL, ZipCode VARCHAR(20) NULL, " +
                    "SignupDate TIMESTAMP DEFAULT CURRENT_TIMESTAMP, SubscriptionStatus VARCHAR(50) DEFAULT 'Active', StripeCustomerID VARCHAR(255) NULL, " +
                    "UNIQUE INDEX idx_company_name_tenant (CompanyName), UNIQUE INDEX idx_company_identifier_tenant (CompanyIdentifier) " +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createTenantsTableSQL);

                logger.fine("Creating SETTINGS table if not exists...");
                String createSettingsTableSQL = "CREATE TABLE IF NOT EXISTS SETTINGS (TenantID INT NOT NULL, setting_key VARCHAR(255) NOT NULL, setting_value TEXT NULL, PRIMARY KEY (TenantID, setting_key), FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createSettingsTableSQL);

                logger.fine("Creating DEPARTMENTS table if not exists...");
                String createDepartmentsTableSQL = "CREATE TABLE IF NOT EXISTS DEPARTMENTS (TenantID INT NOT NULL, NAME VARCHAR(50) NOT NULL, DESCRIPTION VARCHAR(255) NULL, SUPERVISOR VARCHAR(100) NULL, PRIMARY KEY (TenantID, NAME), FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createDepartmentsTableSQL);

                logger.fine("Creating ACCRUALS table if not exists...");
                String createAccrualsTableSQL = "CREATE TABLE IF NOT EXISTS ACCRUALS (TenantID INT NOT NULL, NAME VARCHAR(50) NOT NULL, VACATION INT DEFAULT 0, SICK INT DEFAULT 0, PERSONAL INT DEFAULT 0, PRIMARY KEY (TenantID, NAME), FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createAccrualsTableSQL);

                logger.fine("Creating SCHEDULES table if not exists...");
                String createSchedulesTableSQL = "CREATE TABLE IF NOT EXISTS SCHEDULES (TenantID INT NOT NULL, NAME VARCHAR(50) NOT NULL, SHIFT_START TIME NULL, LUNCH_START TIME NULL, LUNCH_END TIME NULL, SHIFT_END TIME NULL, DAYS_WORKED VARCHAR(100) NULL, AUTO_LUNCH TINYINT(1) DEFAULT 0, HRS_REQUIRED INT DEFAULT 0, LUNCH_LENGTH INT DEFAULT 0, WORK_SCHEDULE VARCHAR(20) NULL, PRIMARY KEY (TenantID, NAME), FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createSchedulesTableSQL);

                logger.fine("Creating EMPLOYEE_DATA table if not exists...");
                String createEmployeeDataTableSQL = "CREATE TABLE IF NOT EXISTS EMPLOYEE_DATA (" +
                    "TenantID INT NOT NULL, EID INT AUTO_INCREMENT PRIMARY KEY, " +
                    "FIRST_NAME VARCHAR(50), LAST_NAME VARCHAR(50), DEPT VARCHAR(50), SCHEDULE VARCHAR(50), " +
                    "SUPERVISOR VARCHAR(100), PERMISSIONS VARCHAR(50), ADDRESS VARCHAR(100), CITY VARCHAR(50), " +
                    "STATE VARCHAR(2), ZIP VARCHAR(10), PHONE VARCHAR(20), EMAIL VARCHAR(100), " +
                    "ACCRUAL_POLICY VARCHAR(50), " +
                    "VACATION_HOURS DECIMAL(10, 2) DEFAULT 0.00, SICK_HOURS DECIMAL(10, 2) DEFAULT 0.00, PERSONAL_HOURS DECIMAL(10, 2) DEFAULT 0.00, " +
                    "HIRE_DATE DATE, WORK_SCHEDULE VARCHAR(15), WAGE_TYPE VARCHAR(10), WAGE DOUBLE, " +
                    "ACTIVE TINYINT(1) DEFAULT 1, " +
                    "PasswordHash VARCHAR(60) NULL, " +
                    "RequiresPasswordChange TINYINT(1) DEFAULT 1, " + // Default to 1 (TRUE)
                    "TenantEmployeeNumber INT NULL, " +
                    "FOREIGN KEY (TenantID, DEPT) REFERENCES DEPARTMENTS(TenantID, NAME) ON DELETE RESTRICT ON UPDATE CASCADE, " +
                    "FOREIGN KEY (TenantID, SCHEDULE) REFERENCES SCHEDULES(TenantID, NAME) ON DELETE RESTRICT ON UPDATE CASCADE, " +
                    "FOREIGN KEY (TenantID, ACCRUAL_POLICY) REFERENCES ACCRUALS(TenantID, NAME) ON DELETE RESTRICT ON UPDATE CASCADE, " +
                    "FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE, " +
                    "UNIQUE INDEX uq_employee_email_tenant (TenantID, EMAIL), " +
                    "UNIQUE INDEX uq_tenant_employee_number (TenantID, TenantEmployeeNumber) " +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createEmployeeDataTableSQL);

                logger.fine("Creating PUNCHES table if not exists...");
                String createPunchesTableSQL = "CREATE TABLE IF NOT EXISTS PUNCHES (PUNCH_ID INT AUTO_INCREMENT PRIMARY KEY, TenantID INT NOT NULL, EID INT NOT NULL, DATE DATE NOT NULL, IN_1 TIMESTAMP NULL, OUT_1 TIMESTAMP NULL, TOTAL DOUBLE DEFAULT 0, OT DOUBLE DEFAULT 0, LATE TINYINT(1) DEFAULT 0, EARLY_OUTS TINYINT(1) DEFAULT 0, PUNCH_TYPE VARCHAR(25), FOREIGN KEY (EID) REFERENCES EMPLOYEE_DATA(EID) ON DELETE CASCADE ON UPDATE CASCADE, FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE, INDEX idx_punches_tenant_eid_date (TenantID, EID, DATE)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createPunchesTableSQL);

                logger.fine("Creating ARCHIVED_PUNCHES table if not exists...");
                String createArchivedPunchesTableSQL = "CREATE TABLE IF NOT EXISTS ARCHIVED_PUNCHES (ARCHIVE_ID INT AUTO_INCREMENT PRIMARY KEY, TenantID INT NOT NULL, PUNCH_ID INT NULL, EID INT NOT NULL, DATE DATE NOT NULL, IN_1 TIMESTAMP NULL, OUT_1 TIMESTAMP NULL, TOTAL DOUBLE DEFAULT 0, OT DOUBLE DEFAULT 0, LATE TINYINT(1) DEFAULT 0, EARLY_OUTS TINYINT(1) DEFAULT 0, PUNCH_TYPE VARCHAR(25), ARCHIVED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE, INDEX idx_archived_tenant_eid_date (TenantID, EID, DATE)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createArchivedPunchesTableSQL);

                logger.fine("Creating payroll_history table if not exists...");
                String createPayrollHistoryTableSQL = "CREATE TABLE IF NOT EXISTS payroll_history (history_id INT AUTO_INCREMENT PRIMARY KEY, TenantID INT NOT NULL, processed_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, period_start_date DATE NOT NULL, period_end_date DATE NOT NULL, grand_total DECIMAL(12, 2) NOT NULL, FOREIGN KEY (TenantID) REFERENCES Tenants(TenantID) ON DELETE CASCADE, INDEX idx_history_tenant_period_end (TenantID, period_end_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";
                stmt.execute(createPayrollHistoryTableSQL);

                logger.info("All tables checked/created successfully.");
                logger.info("Resetting Auto Increments (best effort)...");
                String[] tablesToResetAI = {"Tenants", "EMPLOYEE_DATA", "PUNCHES", "payroll_history", "ARCHIVED_PUNCHES"};
                for (String tableName : tablesToResetAI) {
                    try { stmt.executeUpdate("ALTER TABLE " + tableName + " AUTO_INCREMENT = 1;"); }
                    catch (SQLException e) { logger.warning("Could not reset AI on " + tableName + ": " + e.getMessage()); }
                }
                logger.info("Auto Increments reset attempt complete.");
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "CRITICAL FAILURE: Could not create or verify tables.", e);
                throw new RuntimeException("Table creation/verification failed: " + e.getMessage(), e);
            }

            currentStep = "Adding/Updating Development Tenant (ID=" + DEV_TENANT_ID + ")";
            logger.info(currentStep + "...");
            String tenantSQL = "INSERT INTO Tenants (TenantID, CompanyName, CompanyIdentifier, PhoneNumber, Address, City, State, ZipCode, SubscriptionStatus, SignupDate) " +
                               "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW()) " +
                               "ON DUPLICATE KEY UPDATE CompanyName=VALUES(CompanyName), CompanyIdentifier=VALUES(CompanyIdentifier), PhoneNumber=VALUES(PhoneNumber), " +
                               "Address=VALUES(Address), City=VALUES(City), State=VALUES(State), ZipCode=VALUES(ZipCode), SubscriptionStatus=VALUES(SubscriptionStatus)";
            try (PreparedStatement ps = con.prepareStatement(tenantSQL)) {
                ps.setInt(1, DEV_TENANT_ID); ps.setString(2, "Development Tenant"); ps.setString(3, "DEVTENANT001");
                ps.setString(4, "(555) 123-0001"); ps.setString(5, "123 Dev Lane"); ps.setString(6, "Devtown");
                ps.setString(7, "DV"); ps.setString(8, "00001"); ps.setString(9, "Active");
                ps.executeUpdate(); logger.info("Development Tenant inserted/updated.");
            } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            currentStep = "Populating Default Settings for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
            String insertSettingSQL = "INSERT INTO SETTINGS (TenantID, setting_key, setting_value) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value)";
             try (PreparedStatement ps = con.prepareStatement(insertSettingSQL)) {
                 LocalDate today = LocalDate.now(SCHEDULE_ZONE);
                 LocalDate defaultStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY));
                 LocalDate defaultEnd = defaultStart.plusDays(6);
                 BiConsumer<String, String> saveSetting = (key, value) -> { try { ps.clearParameters(); ps.setInt(1, DEV_TENANT_ID); ps.setString(2, key); ps.setString(3, value); ps.executeUpdate(); } catch (SQLException ex) { logger.log(Level.WARNING, "Failed to save setting '" + key + "' for tenant " + DEV_TENANT_ID + ": " + ex.getMessage()); }};
                 saveSetting.accept("CompanyName", "Development Tenant"); saveSetting.accept("CompanyIdentifier", "DEVTENANT001");
                 saveSetting.accept("PayPeriodStartDate", defaultStart.toString()); saveSetting.accept("PayPeriodEndDate", defaultEnd.toString());
                 saveSetting.accept("PayPeriodType", "WEEKLY"); saveSetting.accept("GracePeriod", "1");
                 saveSetting.accept("Overtime", "true"); saveSetting.accept("OvertimeRate", "1.5");
                 saveSetting.accept("OvertimeDaily", "false"); saveSetting.accept("OvertimeDailyThreshold", "8.0");
                 saveSetting.accept("FirstDayOfWeek", "SUNDAY");
                 logger.info("Default settings populated for Tenant " + DEV_TENANT_ID);
             } catch (Exception e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            currentStep = "Adding Departments for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
            try(PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO DEPARTMENTS (TenantID, NAME, DESCRIPTION, SUPERVISOR) VALUES (?, ?, ?, ?)")) {
                Object[][] depts = {{"None", "No Department Assignment", "None"}, {"Cinema", "Movie Stars", "John Candy"}, {"Music", "Rock Stars", "Ringo Starr"}, {"Television", "TV Personalities", "Alan Alda"}};
                for(Object[] dept : depts){ ps.setInt(1, DEV_TENANT_ID); ps.setString(2, (String)dept[0]); ps.setString(3, (String)dept[1]); ps.setString(4, (String)dept[2]); ps.addBatch(); logger.fine("Dept Added to Batch: " + dept[0]);}
                int[] results = ps.executeBatch(); logger.info("Departments batch executed. Records affected per statement: " + Arrays.toString(results));
            } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            currentStep = "Adding Accrual Policies for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
             try(PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO ACCRUALS (TenantID, NAME, VACATION, SICK, PERSONAL) VALUES (?, ?, ?, ?, ?)")) {
                Object[][] accruals = {{"None", 0, 0, 0}, {"Standard", 5, 5, 5}, {"Executive", 30, 30, 30}};
                for(Object[] acc : accruals){ ps.setInt(1, DEV_TENANT_ID); ps.setString(2, (String)acc[0]); ps.setInt(3, (Integer)acc[1]); ps.setInt(4, (Integer)acc[2]); ps.setInt(5, (Integer)acc[3]); ps.addBatch(); logger.fine("Accrual Added to Batch: " + acc[0]);}
                int[] results = ps.executeBatch(); logger.info("Accruals batch executed. Records affected per statement: " + Arrays.toString(results));
            } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            currentStep = "Adding Schedules for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
            Object[][] schedulesData = {
                {"Open", null, null, null, null, "Mon, Tue, Wed, Thu, Fri, Sat, Sun", false, 0, 0, "Flexible"},
                {"Open with Auto Lunch", null, null, null, null, "Mon, Tue, Wed, Thu, Fri, Sat, Sun", true, 6, 30, "Flexible"},
                {"First Shift", Time.valueOf("07:00:00"), Time.valueOf("11:00:00"), Time.valueOf("11:30:00"), Time.valueOf("15:30:00"), "Mon, Tue, Wed, Thu, Fri", true, 6, 30, "Full Time"},
                {"Weekend Warriors", Time.valueOf("09:00:00"), Time.valueOf("13:00:00"), Time.valueOf("13:30:00"), Time.valueOf("17:30:00"), "Sat, Sun", true, 8, 30, "Part Time"},
                {"Second Shift", Time.valueOf("12:00:00"), Time.valueOf("16:00:00"), Time.valueOf("16:30:00"), Time.valueOf("20:30:00"), "Mon, Tue, Wed, Thu, Fri", true, 6, 30, "Full Time"},
                {"Third Shift", Time.valueOf("20:00:00"), Time.valueOf("00:00:00"), Time.valueOf("01:00:00"), Time.valueOf("05:00:00"), "Mon, Tue, Wed, Thu, Fri", false, 0, 0, "Full Time"}
            };
            String scheduleInsertSql = "INSERT IGNORE INTO SCHEDULES (TenantID, NAME, SHIFT_START, LUNCH_START, LUNCH_END, SHIFT_END, DAYS_WORKED, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH, WORK_SCHEDULE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement psAddSchedules = con.prepareStatement(scheduleInsertSql)) {
                for(Object[] sched : schedulesData) {
                    psAddSchedules.clearParameters(); psAddSchedules.setInt(1, DEV_TENANT_ID); psAddSchedules.setString(2, (String) sched[0]);
                    if (sched[1] != null) psAddSchedules.setTime(3, (Time) sched[1]); else psAddSchedules.setNull(3, Types.TIME);
                    if (sched[2] != null) psAddSchedules.setTime(4, (Time) sched[2]); else psAddSchedules.setNull(4, Types.TIME);
                    if (sched[3] != null) psAddSchedules.setTime(5, (Time) sched[3]); else psAddSchedules.setNull(5, Types.TIME);
                    if (sched[4] != null) psAddSchedules.setTime(6, (Time) sched[4]); else psAddSchedules.setNull(6, Types.TIME);
                    psAddSchedules.setString(7, (String) sched[5]); psAddSchedules.setBoolean(8, (Boolean) sched[6]);
                    psAddSchedules.setInt(9, (Integer) sched[7]); psAddSchedules.setInt(10, (Integer) sched[8]);
                    psAddSchedules.setString(11, (String) sched[9]);
                    psAddSchedules.addBatch(); logger.fine("Schedule Added to Batch: " + sched[0]);
                }
                int[] results = psAddSchedules.executeBatch(); logger.info("Schedules batch executed. Records affected per statement: " + Arrays.toString(results));
            } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            currentStep = "Adding Employees for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
            String employeeSql = "INSERT INTO EMPLOYEE_DATA " +
                                 "(TenantID, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, SUPERVISOR, PERMISSIONS, ADDRESS, CITY, STATE, ZIP, PHONE, EMAIL, ACCRUAL_POLICY, " +
                                 "VACATION_HOURS, SICK_HOURS, PERSONAL_HOURS, HIRE_DATE, WORK_SCHEDULE, WAGE_TYPE, WAGE, " +
                                 "PasswordHash, TenantEmployeeNumber) " + // Removed RequiresPasswordChange, ACTIVE (rely on DB defaults)
                                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"; // 23 placeholders
            int employeesInsertedCount = 0; Map<Integer, Integer> arrayIndexToGlobalEidMap = new HashMap<>();
            try (PreparedStatement psAddEmployees = con.prepareStatement(employeeSql, Statement.RETURN_GENERATED_KEYS)) {
                Random randomAccrual = new Random(); double maxAccrualHours = 20.0;
                for (int i = 0; i < employees.length; i++) {
                    Object[] emp = employees[i]; String empNameForLog = emp[0] + " " + emp[1];
                    logger.fine("Preparing to insert employee: " + empNameForLog);
                    try {
                        int paramIndex = 1;
                        psAddEmployees.setInt(paramIndex++, DEV_TENANT_ID);
                        psAddEmployees.setString(paramIndex++, (String)emp[0]); psAddEmployees.setString(paramIndex++, (String)emp[1]);
                        psAddEmployees.setString(paramIndex++, (String)emp[2]); psAddEmployees.setString(paramIndex++, (String)emp[3]);
                        psAddEmployees.setString(paramIndex++, (String)emp[4]); psAddEmployees.setString(paramIndex++, (String)emp[5]);
                        psAddEmployees.setString(paramIndex++, (String)emp[6]); psAddEmployees.setString(paramIndex++, (String)emp[7]);
                        psAddEmployees.setString(paramIndex++, (String)emp[8]); psAddEmployees.setString(paramIndex++, (String)emp[9]);
                        psAddEmployees.setString(paramIndex++, (String)emp[10]); psAddEmployees.setString(paramIndex++, (String)emp[11]);
                        psAddEmployees.setString(paramIndex++, (String)emp[12]);
                        psAddEmployees.setDouble(paramIndex++, Math.round(randomAccrual.nextDouble()*maxAccrualHours*100.0)/100.0);
                        psAddEmployees.setDouble(paramIndex++, Math.round(randomAccrual.nextDouble()*maxAccrualHours*100.0)/100.0);
                        psAddEmployees.setDouble(paramIndex++, Math.round(randomAccrual.nextDouble()*maxAccrualHours*100.0)/100.0);
                        psAddEmployees.setDate(paramIndex++, java.sql.Date.valueOf((LocalDate)emp[13]));
                        psAddEmployees.setString(paramIndex++, (String)emp[14]); psAddEmployees.setString(paramIndex++, (String)emp[15]);
                        psAddEmployees.setDouble(paramIndex++, (Double)emp[16]);
                        psAddEmployees.setString(paramIndex++, defaultPasswordHash);
                        psAddEmployees.setInt(paramIndex++, i + 1); // TenantEmployeeNumber (1-based for sample data)

                        int affectedRows = psAddEmployees.executeUpdate();
                        if (affectedRows > 0) { try (ResultSet generatedKeys = psAddEmployees.getGeneratedKeys()) { if (generatedKeys.next()) { arrayIndexToGlobalEidMap.put(i, generatedKeys.getInt(1)); employeesInsertedCount++; logger.fine("Inserted employee: " + empNameForLog + " Global EID: " + generatedKeys.getInt(1) + " TenantEmployeeNumber: " + (i+1));} else {logger.warning("No Global EID obtained for " + empNameForLog);}}} else {logger.warning("Insert failed for " + empNameForLog + ", no rows affected.");}
                    } catch (SQLException e) { logger.log(Level.SEVERE, "SQLException inserting employee: " + empNameForLog + ". Data: " + Arrays.toString(emp), e); throw new RuntimeException("Failed on " + empNameForLog, e); }
                }
                if (employeesInsertedCount < employees.length) { logger.warning("Not all employees inserted. Expected: " + employees.length + ", Inserted: " + employeesInsertedCount); }
                else { logger.info("All " + employeesInsertedCount + " employees added successfully for Tenant " + DEV_TENANT_ID); }
            } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            currentStep = "Pre-loading Schedule Times Map for Punches for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
            Map<String, ScheduleInfo> scheduleTimesMap = new HashMap<>();
            String scheduleQuery = "SELECT NAME, SHIFT_START, SHIFT_END, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH FROM SCHEDULES WHERE TenantID = ?";
            try (PreparedStatement psSchedMap = con.prepareStatement(scheduleQuery)) {
                psSchedMap.setInt(1, DEV_TENANT_ID);
                try(ResultSet rs = psSchedMap.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("NAME"); Time st = rs.getTime("SHIFT_START"); Time et = rs.getTime("SHIFT_END");
                        boolean autoLunch = rs.getBoolean("AUTO_LUNCH"); int hrsRequiredInt = rs.getInt("HRS_REQUIRED");
                        Integer hrsRequired = rs.wasNull() ? null : hrsRequiredInt; int lunchLengthInt = rs.getInt("LUNCH_LENGTH");
                        Integer lunchLength = rs.wasNull() ? null : lunchLengthInt;
                        scheduleTimesMap.put(name, new ScheduleInfo(name, st != null ? st.toLocalTime() : null, et != null ? et.toLocalTime() : null, autoLunch, hrsRequired, lunchLength));
                    }
                }
                logger.info("Loaded " + scheduleTimesMap.size() + " schedule entries into map for Tenant " + DEV_TENANT_ID);
                 if (scheduleTimesMap.isEmpty() && employees.length > 0) {
                    int scheduleCountInDb = 0;
                    try(Statement s = con.createStatement(); ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM SCHEDULES WHERE TenantID="+DEV_TENANT_ID)){ if(rs.next()) scheduleCountInDb = rs.getInt(1); }
                    if(scheduleCountInDb > 0) throw new RuntimeException("Schedules exist in DB ("+scheduleCountInDb+") but failed to load into map for Tenant " + DEV_TENANT_ID);
                    else logger.warning("No schedules found in DB to load into map for Tenant " + DEV_TENANT_ID + ", punch generation for non-Open schedules may be limited.");
                }
            } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            currentStep = "Adding Sample Punches for Tenant " + DEV_TENANT_ID;
            logger.info(currentStep + "...");
            String punchInsertSql = "INSERT INTO PUNCHES (TenantID, EID, DATE, IN_1, OUT_1, TOTAL, OT, LATE, EARLY_OUTS, PUNCH_TYPE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            int punchesAddedCount = 0; int gracePeriod = 1;
            try (PreparedStatement psAddPunches = con.prepareStatement(punchInsertSql)) {
                int currentPunchIndex = 0; Random timeRand = new Random(); LocalDate todayInScheduleZone = LocalDate.now(SCHEDULE_ZONE);
                for (Map.Entry<Integer, Integer> entry : arrayIndexToGlobalEidMap.entrySet()) { // Use globalEID map
                    int originalArrayIndex = entry.getKey(); int globalEID = entry.getValue(); Object[] emp = employees[originalArrayIndex];
                    String scheduleName = (String)emp[3]; ScheduleInfo currentSchedule = scheduleTimesMap.get(scheduleName);
                    if (currentSchedule == null) { logger.warning("No schedule info in map for " + scheduleName + " (Global EID: " + globalEID + "). Skipping punches."); currentPunchIndex += daysToGeneratePunches; continue; }
                    LocalTime baseInTime = currentSchedule.shiftStart(); LocalTime baseOutTime = currentSchedule.shiftEnd(); boolean scheduleHasTimes = (baseInTime != null && baseOutTime != null);
                    for (int daysAgo=daysToGeneratePunches -1; daysAgo >= 0; daysAgo--) {
                        LocalDate punchDateLocal = todayInScheduleZone.minusDays(daysAgo); DayOfWeek dayOfWeek = punchDateLocal.getDayOfWeek();
                        String daysWorkedFromSched = currentSchedule.name().equals("Weekend Warriors") ? "Sat,Sun" : "Mon,Tue,Wed,Thu,Fri"; // Simplified based on name convention
                        if (!currentSchedule.name().toLowerCase().contains("open") && !daysWorkedFromSched.toLowerCase().contains(dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH).toLowerCase())) { currentPunchIndex++; continue; }

                        LocalTime randomInTimeLocal, randomOutTimeLocal;
                        if (scheduleHasTimes) { long inOffsetSeconds = (long) ((timeRand.nextDouble() - 0.5) * 20 * 60); long outOffsetSeconds = (long) ((timeRand.nextDouble() - 0.5) * 20 * 60); randomInTimeLocal = baseInTime.plusSeconds(inOffsetSeconds); randomOutTimeLocal = baseOutTime.plusSeconds(outOffsetSeconds); }
                        else { int startHour = 8 + timeRand.nextInt(2); randomInTimeLocal = LocalTime.of(startHour, timeRand.nextInt(60)); randomOutTimeLocal = randomInTimeLocal.plusHours(7 + timeRand.nextInt(2)).plusMinutes(timeRand.nextInt(60)); }
                        ZonedDateTime punchInZoned = punchDateLocal.atTime(randomInTimeLocal).atZone(SCHEDULE_ZONE); ZonedDateTime punchOutZoned = null;
                        boolean makeMissingOutPunch = missingPunchIndices.contains(currentPunchIndex);
                        if (!makeMissingOutPunch) { punchOutZoned = punchDateLocal.atTime(randomOutTimeLocal).atZone(SCHEDULE_ZONE); if (punchOutZoned.isBefore(punchInZoned) || punchOutZoned.equals(punchInZoned)) { punchOutZoned = punchInZoned.plusHours(8).plusMinutes((long) (timeRand.nextDouble() * 30)); }}
                        Instant punchInInstant = punchInZoned.toInstant(); Instant punchOutInstant = (punchOutZoned != null) ? punchOutZoned.toInstant() : null;
                        LocalDate punchUtcDate = punchInInstant.atZone(ZoneOffset.UTC).toLocalDate();
                        boolean isLateForSample = false; boolean isEarlyForSample = false;
                        if (scheduleHasTimes && baseInTime != null) { LocalTime lateCutoff = baseInTime.plusMinutes(gracePeriod); isLateForSample = randomInTimeLocal.isAfter(lateCutoff); }
                        if (scheduleHasTimes && baseOutTime != null && punchOutZoned != null) { LocalTime earlyCutoff = baseOutTime.minusMinutes(gracePeriod); isEarlyForSample = randomOutTimeLocal.isBefore(earlyCutoff); }
                        double rawTotalHours = 0.0; double adjustedTotalHours = 0.0;
                        if (punchInInstant != null && punchOutInstant != null && punchOutInstant.isAfter(punchInInstant)) {
                            Duration duration = Duration.between(punchInInstant, punchOutInstant); rawTotalHours = duration.toMillis() / 3_600_000.0;
                            rawTotalHours = Math.round(rawTotalHours * ROUNDING_FACTOR) / ROUNDING_FACTOR;
                            if (currentSchedule.autoLunch() && currentSchedule.hrsRequired() != null && currentSchedule.lunchLength() != null && rawTotalHours > currentSchedule.hrsRequired() && currentSchedule.lunchLength() > 0) {
                                double lunchDeduction = currentSchedule.lunchLength() / 60.0; adjustedTotalHours = Math.max(0, rawTotalHours - lunchDeduction);
                            } else { adjustedTotalHours = rawTotalHours; }
                            adjustedTotalHours = Math.round(adjustedTotalHours * 100.0) / 100.0;
                        }
                        psAddPunches.setInt(1, DEV_TENANT_ID); psAddPunches.setInt(2, globalEID); // Use Global EID
                        psAddPunches.setDate(3, java.sql.Date.valueOf(punchUtcDate));
                        psAddPunches.setTimestamp(4, java.sql.Timestamp.from(punchInInstant)); ShowPunches.setOptionalTimestamp(psAddPunches, 5, (punchOutInstant != null) ? Timestamp.from(punchOutInstant) : null);
                        ShowPunches.setOptionalDouble(psAddPunches, 6, adjustedTotalHours); ShowPunches.setOptionalDouble(psAddPunches, 7, 0.0); // OT
                        psAddPunches.setBoolean(8, isLateForSample); psAddPunches.setBoolean(9, isEarlyForSample); psAddPunches.setString(10, "Sample Data");
                        psAddPunches.addBatch(); punchesAddedCount++; currentPunchIndex++;
                    }
                }
                int[] punchResults = psAddPunches.executeBatch(); logger.info(punchesAddedCount + " Punch records prepared. Batch results length: " + punchResults.length + ". Sum of results: " + Arrays.stream(punchResults).sum());
            } catch (SQLException e) { logger.log(Level.SEVERE, "CRITICAL FAILURE during " + currentStep, e); throw new RuntimeException(currentStep + " failed: " + e.getMessage(), e); }

            logger.info("Sample data insertion steps COMPLETED SUCCESSFULLY for Tenant " + DEV_TENANT_ID);

        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, "CRITICAL HALT in sample data step: " + currentStep + ". Error: " + e.getMessage(), e.getCause() != null ? e.getCause() : e);
            if (e.getCause() instanceof SQLException) {
                SQLException sqlEx = (SQLException) e.getCause();
                logger.severe("SQLState: " + sqlEx.getSQLState() + ", Error Code: " + sqlEx.getErrorCode());
            }
            throw e;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected FATAL exception during sample data step: " + currentStep, e);
            throw new RuntimeException("Unexpected error during " + currentStep + ": " + e.getMessage(), e);
        }
        logger.info("Sample data addition process finished successfully for Tenant " + DEV_TENANT_ID + ".");
        return "Sample data added/reset successfully for Tenant " + DEV_TENANT_ID + ".";
    }
}