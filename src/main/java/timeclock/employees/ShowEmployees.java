package timeclock.employees;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;

public class ShowEmployees {

    private static final Logger logger = Logger.getLogger(ShowEmployees.class.getName());
    private static final DateTimeFormatter DATE_FORMAT_ISO = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }

    public static String showEmployees(int tenantId) {
        StringBuilder tableRows = new StringBuilder();
        final int VISIBLE_COLUMNS = 9; 

        if (tenantId <= 0) {
            logger.warning("[ShowEmployees] showEmployees called with invalid TenantID: " + tenantId);
            return "<tr><td colspan='" + VISIBLE_COLUMNS + "' class='report-error-row'>Invalid tenant context.</td></tr>";
        }

        SimpleDateFormat dateFormatDisplay = new SimpleDateFormat("MM/dd/yyyy");
        DecimalFormat hoursFormat = new DecimalFormat("0.00");

        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, SUPERVISOR, PERMISSIONS, " +
                     "ADDRESS, CITY, STATE, ZIP, PHONE, EMAIL, ACCRUAL_POLICY, " +
                     "VACATION_HOURS, SICK_HOURS, PERSONAL_HOURS, HIRE_DATE, " +
                     "WORK_SCHEDULE, WAGE_TYPE, WAGE " +
                     "FROM employee_data WHERE TenantID = ? AND ACTIVE = TRUE ORDER BY TenantEmployeeNumber ASC";



        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true;
                    int globalEid = rs.getInt("EID");
                    Integer tenantEmployeeNumberObj = (Integer) rs.getObject("TenantEmployeeNumber");
                    String displayEid = (tenantEmployeeNumberObj != null && tenantEmployeeNumberObj > 0) ? tenantEmployeeNumberObj.toString() : String.valueOf(globalEid);
                    
                    String firstName = rs.getString("FIRST_NAME");
                    String lastName = rs.getString("LAST_NAME");
                    String dept = rs.getString("DEPT");
                    String schedule = rs.getString("SCHEDULE");
                    String supervisor = rs.getString("SUPERVISOR");
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
                    Date hireDate = rs.getDate("HIRE_DATE");
                    String workSchedule = rs.getString("WORK_SCHEDULE");
                    String wageType = rs.getString("WAGE_TYPE");
                    double wage = rs.getDouble("WAGE");

                    String displayHireDate = (hireDate != null) ? dateFormatDisplay.format(hireDate) : "";
                    String isoHireDate = (hireDate != null) ? hireDate.toLocalDate().format(DATE_FORMAT_ISO) : "";
                    
                    String displayVac = hoursFormat.format(vacHours);
                    String displaySick = hoursFormat.format(sickHours);
                    String displayPers = hoursFormat.format(persHours);

                    tableRows.append("<tr data-eid=\"").append(globalEid)
                            .append("\" data-tenantemployeenumber=\"").append(escapeHtml(displayEid))
                            .append("\" data-firstname=\"").append(escapeHtml(firstName != null ? firstName : ""))
                            .append("\" data-lastname=\"").append(escapeHtml(lastName != null ? lastName : ""))
                            .append("\" data-dept=\"").append(escapeHtml(dept != null ? dept : ""))
                            .append("\" data-schedule=\"").append(escapeHtml(schedule != null ? schedule : ""))
                            .append("\" data-supervisor=\"").append(escapeHtml(supervisor != null ? supervisor : ""))
                            .append("\" data-permissions=\"").append(escapeHtml(permissions != null ? permissions : ""))
                            .append("\" data-address=\"").append(escapeHtml(address != null ? address : ""))
                            .append("\" data-city=\"").append(escapeHtml(city != null ? city : ""))
                            .append("\" data-state=\"").append(escapeHtml(state != null ? state : ""))
                            .append("\" data-zip=\"").append(escapeHtml(zip != null ? zip : ""))
                            .append("\" data-phone=\"").append(escapeHtml(phone != null ? phone : ""))
                            .append("\" data-email=\"").append(escapeHtml(email != null ? email : ""))
                            .append("\" data-accrualpolicy=\"").append(escapeHtml(accrualPolicy != null ? accrualPolicy : ""))
                            .append("\" data-vachours=\"").append(displayVac) 
                            .append("\" data-sickhours=\"").append(displaySick)
                            .append("\" data-pershours=\"").append(displayPers)
                            .append("\" data-hiredate=\"").append(escapeHtml(displayHireDate)) 
                            .append("\" data-iso-date=\"").append(escapeHtml(isoHireDate)).append("\"")
                            .append("\" data-workschedule=\"").append(escapeHtml(workSchedule != null ? workSchedule : "")) 
                            .append("\" data-wagetype=\"").append(escapeHtml(wageType != null ? wageType : ""))
                            .append("\" data-wage=\"").append(wage) 
                            .append("\">");
                    tableRows.append("<td>").append(escapeHtml(displayEid)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(firstName)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(lastName)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(dept)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(schedule)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(supervisor)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(permissions)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(email)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(workSchedule)).append("</td>");
                    tableRows.append("</tr>\n");
                }
                if (!found) {
                    tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-message-row'>No active employees found for your company.</td></tr>");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[ShowEmployees] Error retrieving employees for TenantID: " + tenantId, e);
            tableRows.setLength(0); tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-error-row'>Error: ").append(escapeHtml(e.getMessage())).append("</td></tr>");
        } catch (Throwable t) { 
            logger.log(Level.SEVERE, "[ShowEmployees] Unexpected error or missing library in showEmployees for TenantID: " + tenantId, t);
            tableRows.setLength(0); tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-error-row'>An unexpected application error occurred. Check server logs.</td></tr>");
        }
        return tableRows.toString();
    }

    public static String showInactiveEmployees(int tenantId) {


        StringBuilder tableRows = new StringBuilder();
        final int VISIBLE_COLUMNS = 8; 
        if (tenantId <= 0) { return "<tr><td colspan='" + VISIBLE_COLUMNS + "' class='report-error-row'>Invalid tenant.</td></tr>"; }
        SimpleDateFormat dateFormatDisplay = new SimpleDateFormat("MM/dd/yyyy");
        
        String sql = "SELECT EID, TenantEmployeeNumber, FIRST_NAME, LAST_NAME, DEPT, SCHEDULE, EMAIL, DeactivationReason, DeactivationDate " +
                     "FROM employee_data WHERE TenantID = ? AND ACTIVE = FALSE ORDER BY DeactivationDate DESC, LAST_NAME ASC";
        
        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean found = false;
                while (rs.next()) {
                    found = true; 
                    int globalEid = rs.getInt("EID"); 
                    Integer tenEmpNo = (Integer) rs.getObject("TenantEmployeeNumber");
                    String dEid = (tenEmpNo!=null && tenEmpNo>0) ? tenEmpNo.toString() : String.valueOf(globalEid);
                    
                    Date deactivationDate = rs.getDate("DeactivationDate");
                    String displayDeactivationDate = (deactivationDate != null) ? dateFormatDisplay.format(deactivationDate) : "N/A";
                    String deactivationReason = rs.getString("DeactivationReason");
                    


                    tableRows.append("<tr data-eid=\"").append(globalEid).append("\">");
                    tableRows.append("<td>").append(escapeHtml(dEid)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(rs.getString("FIRST_NAME"))).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(rs.getString("LAST_NAME"))).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(rs.getString("DEPT"))).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(rs.getString("SCHEDULE"))).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(rs.getString("EMAIL"))).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(displayDeactivationDate)).append("</td>");
                    tableRows.append("<td>").append(escapeHtml(deactivationReason)).append("</td>");
                    tableRows.append("</tr>\n");
                }
                if (!found) { tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-message-row'>No inactive employees.</td></tr>"); }
            }
        } catch (SQLException e) { 
            logger.log(Level.SEVERE, "[ShowEmployees] Error retrieving inactive employees T:" + tenantId, e); 
            tableRows.setLength(0); 
            tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-error-row'>Error retrieving inactive employee data.</td></tr>"); 
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "[ShowEmployees] Unexpected error in showInactiveEmployees for TenantID: " + tenantId, t);
            tableRows.setLength(0); tableRows.append("<tr><td colspan='").append(VISIBLE_COLUMNS).append("' class='report-error-row'>An unexpected application error occurred. Check server logs.</td></tr>");
        }
        return tableRows.toString();
    }
}