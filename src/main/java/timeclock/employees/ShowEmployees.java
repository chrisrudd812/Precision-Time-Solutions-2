package timeclock.employees;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date; // Import java.sql.Date
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter; // *** ADDED Import for ISO Date ***
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection; // Use connection pooling

public class ShowEmployees {

    private static final Logger logger = Logger.getLogger(ShowEmployees.class.getName());
    private static final SimpleDateFormat DATE_FORMAT_DISPLAY = new SimpleDateFormat("MM/dd/yyyy"); // For display
    // *** ADDED Formatter for input[type=date] (YYYY-MM-DD) ***
    private static final DateTimeFormatter DATE_FORMAT_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DecimalFormat WAGE_FORMAT_DISPLAY = new DecimalFormat("$#,##0.00"); // For displaying wage

    public static String showEmployees() {
        StringBuilder tableRows = new StringBuilder();
        // Ensure correct table and schema names
        String sql = "SELECT EID, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, PERMISSIONS, " +
                     "ADDRESS, CITY, STATE, ZIP, PHONE, EMAIL, ACCRUAL_POLICY, " +
                     "VACATION_HOURS, SICK_HOURS, PERSONAL_HOURS, HIRE_DATE, " +
                     "WORK_SCHEDULE, WAGE_TYPE, WAGE " +
                     "FROM EMPLOYEE_DATA WHERE ACTIVE = TRUE ORDER BY EID";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                int eid = rs.getInt("EID");
                String firstName = rs.getString("FIRST_NAME");
                String lastName = rs.getString("LAST_NAME");
                String dept = rs.getString("DEPT");
                String schedule = rs.getString("SCHEDULE");
                String permissions = rs.getString("PERMISSIONS");
                String address = rs.getString("ADDRESS");
                String city = rs.getString("CITY");
                String state = rs.getString("STATE");
                String zip = rs.getString("ZIP");
                String phone = rs.getString("PHONE");
                String email = rs.getString("EMAIL");
                String accrualPolicy = rs.getString("ACCRUAL_POLICY");
                double vacHours = rs.getDouble("VACATION_HOURS");
                double sickHours = rs.getDouble("SICK_HOURS");
                double persHours = rs.getDouble("PERSONAL_HOURS");
                Date hireDate = rs.getDate("HIRE_DATE"); // Get as java.sql.Date
                String workSchedule = rs.getString("WORK_SCHEDULE");
                String wageType = rs.getString("WAGE_TYPE");
                double wage = rs.getDouble("WAGE");

                // Formatting for display and data attributes
                String displayHireDate = (hireDate != null) ? DATE_FORMAT_DISPLAY.format(hireDate) : "";
                // *** Calculate ISO Date format for data attribute ***
                String isoHireDate = (hireDate != null) ? hireDate.toLocalDate().format(DATE_FORMAT_ISO) : "";
                String displayWage = WAGE_FORMAT_DISPLAY.format(wage); // Format wage with $ and commas

                // Append row - Assuming event delegation in employees.js, no onclick here
                tableRows.append("<tr>") // If using inline onclick, add it back here
                       .append("<td>").append(eid).append("</td>")
                       .append("<td>").append(firstName != null ? firstName : "").append("</td>")
                       .append("<td>").append(lastName != null ? lastName : "").append("</td>")
                       .append("<td>").append(dept != null ? dept : "").append("</td>")
                       .append("<td>").append(schedule != null ? schedule : "").append("</td>")
                       .append("<td>").append(permissions != null ? permissions : "").append("</td>")
                       .append("<td>").append(address != null ? address : "").append("</td>")
                       .append("<td>").append(city != null ? city : "").append("</td>")
                       .append("<td>").append(state != null ? state : "").append("</td>")
                       .append("<td>").append(zip != null ? zip : "").append("</td>")
                       .append("<td>").append(phone != null ? phone : "").append("</td>")
                       .append("<td>").append(email != null ? email : "").append("</td>")
                       .append("<td>").append(accrualPolicy != null ? accrualPolicy : "").append("</td>")
                       .append("<td>").append(vacHours).append("</td>")
                       .append("<td>").append(sickHours).append("</td>")
                       .append("<td>").append(persHours).append("</td>")
                       // *** Add data-iso-date attribute for Hire Date ***
                       .append("<td data-iso-date='").append(isoHireDate).append("'>")
                       .append(displayHireDate) // Display MM/dd/yyyy
                       .append("</td>")
                       .append("<td>").append(workSchedule != null ? workSchedule : "").append("</td>")
                       .append("<td>").append(wageType != null ? wageType : "").append("</td>")
                       // *** Add data-wage attribute for Wage ***
                       .append("<td data-wage='").append(wage).append("'>")
                       .append(displayWage) // Display formatted wage (e.g., $1,234.00)
                       .append("</td>")
                       .append("</tr>");
            }

            if (tableRows.length() == 0) {
                tableRows.append("<tr><td colspan='20'>No active employees found.</td></tr>"); // Correct colspan
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error retrieving employees", e);
            tableRows.setLength(0);
            tableRows.append("<tr><td colspan='20'>Error retrieving employee data: ").append(e.getMessage()).append("</td></tr>"); // Correct colspan
        }

        return tableRows.toString();
    }
}