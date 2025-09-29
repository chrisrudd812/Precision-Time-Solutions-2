package timeclock.messaging;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;

public class MessagingDataService {

    private static final Logger logger = Logger.getLogger(MessagingDataService.class.getName());

    public static List<Map<String, String>> getDepartmentsForTenant(int tenantId) {
        List<Map<String, String>> departments = new ArrayList<>();
        if (tenantId <= 0) { return departments; }
        String sql = "SELECT NAME FROM departments WHERE TenantID = ? ORDER BY NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> dept = new HashMap<>();
                    dept.put("name", rs.getString("NAME"));
                    departments.add(dept);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching departments for messaging dropdown for TenantID: " + tenantId, e);
        }
        return departments;
    }

    public static List<Map<String, String>> getSchedulesForTenant(int tenantId) {
        List<Map<String, String>> schedules = new ArrayList<>();
        if (tenantId <= 0) { return schedules; }
        String sql = "SELECT NAME FROM schedules WHERE TenantID = ? ORDER BY NAME ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> sched = new HashMap<>();
                    sched.put("name", rs.getString("NAME"));
                    schedules.add(sched);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching schedules for messaging dropdown for TenantID: " + tenantId, e);
        }
        return schedules;
    }

    public static List<String> getSupervisorsForTenant(int tenantId) {
        List<String> supervisors = new ArrayList<>();
        if (tenantId <= 0) { return supervisors; }
        String sql = "SELECT DISTINCT SUPERVISOR FROM employee_data WHERE TenantID = ? AND SUPERVISOR IS NOT NULL AND SUPERVISOR <> '' ORDER BY SUPERVISOR ASC";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    supervisors.add(rs.getString("SUPERVISOR"));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching distinct supervisors for messaging dropdown for TenantID: " + tenantId, e);
        }
        return supervisors;
    }

    public static List<Map<String, String>> getActiveEmployeesSimple(int tenantId) {
        List<Map<String, String>> employees = new ArrayList<>();
        String sql = "SELECT EID, FIRST_NAME, LAST_NAME FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE ORDER BY LAST_NAME, FIRST_NAME";
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> emp = new HashMap<>();
                    emp.put("EID", String.valueOf(rs.getInt("EID")));
                    emp.put("name", rs.getString("LAST_NAME") + ", " + rs.getString("FIRST_NAME"));
                    employees.add(emp);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching active employees for messaging dropdown for TenantID: " + tenantId, e);
        }
        return employees;
    }
    
    public static List<Integer> getRecipientEIDs(int tenantId, String type, String target) throws SQLException {
        List<Integer> eids = new ArrayList<>();
        String sql = "SELECT EID FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE";

        if (type == null) return eids;
        
        String additionalClause = "";
        switch (type) {
            case "all":
                break;
            case "department":
                additionalClause = " AND DEPT = ?";
                break;
            case "schedule":
                additionalClause = " AND SCHEDULE = ?";
                break;
            case "supervisor":
                additionalClause = " AND SUPERVISOR = ?";
                break;
            case "individual":
                additionalClause = " AND EID = ?";
                break;
            default:
                return eids;
        }
        sql += additionalClause;

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            if (!"all".equals(type)) {
                if ("individual".equals(type)) {
                    ps.setInt(2, Integer.parseInt(target));
                } else {
                    ps.setString(2, target);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    eids.add(rs.getInt("EID"));
                }
            }
        }
        return eids;
    }
}