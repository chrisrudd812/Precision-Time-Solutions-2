package timeclock.accruals;

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

public class ShowAccruals {
    private static final Logger logger = Logger.getLogger(ShowAccruals.class.getName());

    public static String showAccruals(int tenantId) {
        StringBuilder html = new StringBuilder();
        String sql = "SELECT NAME, VACATION, SICK, PERSONAL FROM ACCRUALS WHERE TenantID = ? ORDER BY NAME ASC";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {

            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.isBeforeFirst()) {
                    html.append("<tr><td colspan='4' class='report-message-row'>No accrual policies found.</td></tr>");
                } else {
                    while (rs.next()) {
                        String name = rs.getString("NAME");
                        int vacation = rs.getInt("VACATION");
                        int sick = rs.getInt("SICK");
                        int personal = rs.getInt("PERSONAL");

                        html.append("<tr data-name='").append(escapeHtml(name)).append("' ")
                            .append("data-vacation='").append(vacation).append("' ")
                            .append("data-sick='").append(sick).append("' ")
                            .append("data-personal='").append(personal).append("'>");
                        html.append("<td>").append(escapeHtml(name)).append("</td>");
                        html.append("<td>").append(vacation).append("</td>");
                        html.append("<td>").append(sick).append("</td>");
                        html.append("<td>").append(personal).append("</td>");
                        html.append("</tr>");
                    }
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error in showAccruals for TenantID: " + tenantId, e);
            html.append("<tr><td colspan='4' class='report-error-row'>Error loading accrual policies.</td></tr>");
        }
        return html.toString();
    }

    public static List<Map<String, String>> getAccrualPoliciesForTenant(int tenantId) {
        List<Map<String, String>> policies = new ArrayList<>();
        String sql = "SELECT NAME FROM ACCRUALS WHERE TenantID = ? ORDER BY NAME ASC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> policy = new HashMap<>();
                    policy.put("name", rs.getString("NAME"));
                    policies.add(policy);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error fetching accrual policies for dropdown for T:" + tenantId, e);
        }
        return policies;
    }

    public static List<Map<String, String>> getEmployeesForTenant(int tenantId, boolean includeInactive) {
        List<Map<String, String>> employees = new ArrayList<>();
        String sql = "SELECT EID, FIRST_NAME, LAST_NAME FROM EMPLOYEE_DATA WHERE TenantID = ?" +
                     (includeInactive ? "" : " AND ACTIVE = TRUE") +
                     " ORDER BY LAST_NAME ASC, FIRST_NAME ASC";
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement pstmt = con.prepareStatement(sql)) {
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> employee = new HashMap<>();
                    employee.put("id", String.valueOf(rs.getInt("EID")));
                    employee.put("firstName", rs.getString("FIRST_NAME"));
                    employee.put("lastName", rs.getString("LAST_NAME"));
                    employees.add(employee);
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "SQL Error fetching employees for dropdown for T:" + tenantId, e);
        }
        return employees;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}