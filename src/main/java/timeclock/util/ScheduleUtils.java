package timeclock.util;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;

/**
 * Utility class for schedule-related operations.
 */
public class ScheduleUtils {
    
    private static final Logger logger = Logger.getLogger(ScheduleUtils.class.getName());
    
    /**
     * Checks if a given date is a scheduled day off for an employee.
     * @param tenantId The tenant ID
     * @param employeeId The employee ID (EID)
     * @param date The date to check
     * @return true if the date is a scheduled day off, false otherwise
     */
    public static boolean isScheduledDayOff(int tenantId, int employeeId, LocalDate date) {
        String sql = "SELECT s.DAYS_WORKED FROM employee_data e " +
                     "JOIN schedules s ON e.TenantID = s.TenantID AND e.SCHEDULE = s.NAME " +
                     "WHERE e.TenantID = ? AND e.EID = ?";
        
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            
            ps.setInt(1, tenantId);
            ps.setInt(2, employeeId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String daysWorked = rs.getString("DAYS_WORKED");
                    return !isWorkDay(daysWorked, date);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error checking scheduled day off for EID: " + employeeId, e);
        }
        
        // If no schedule found or error, assume it's not a day off (safer default)
        return false;
    }
    
    /**
     * Checks if a date is a work day according to the schedule's DAYS_WORKED pattern.
     * @param daysWorked The DAYS_WORKED string from schedule (format: SMTWHFA)
     * @param date The date to check
     * @return true if it's a work day, false if it's a day off
     */
    private static boolean isWorkDay(String daysWorked, LocalDate date) {
        if (daysWorked == null || daysWorked.length() != 7) {
            return true; // Default to work day if pattern is invalid
        }
        
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        int dayIndex;
        
        // Map DayOfWeek to SMTWHFA index
        switch (dayOfWeek) {
            case SUNDAY: dayIndex = 0; break;    // S
            case MONDAY: dayIndex = 1; break;    // M
            case TUESDAY: dayIndex = 2; break;   // T
            case WEDNESDAY: dayIndex = 3; break; // W
            case THURSDAY: dayIndex = 4; break;  // H
            case FRIDAY: dayIndex = 5; break;    // F
            case SATURDAY: dayIndex = 6; break;  // A
            default: return true; // Fallback
        }
        
        char dayChar = daysWorked.charAt(dayIndex);
        return dayChar != '-'; // Work day if not a dash
    }
}