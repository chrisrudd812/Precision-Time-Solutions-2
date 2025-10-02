package timeclock.departments;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet("/AddAndDeleteDepartmentsServlet")
public class AddAndDeleteDepartmentsServlet extends HttpServlet {

    private static final long serialVersionUID = 3L; // Version updated
    private static final Logger logger = Logger.getLogger(AddAndDeleteDepartmentsServlet.class.getName());

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object tenantIdObj = session.getAttribute("TenantID");
        return (tenantIdObj instanceof Integer) ? (Integer) tenantIdObj : null;
    }

    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }

    private String encode(String value) {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Integer tenantId = getTenantId(request);
        HttpSession session = request.getSession(false);

        if (tenantId == null || session == null) {
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encode("Session expired."));
            return;
        }
        
        String userPermissions = (String) session.getAttribute("Permissions");
        if (!"Administrator".equalsIgnoreCase(userPermissions)) {
            response.sendRedirect(request.getContextPath() + "/departments.jsp?error=" + encode("Access Denied."));
            return;
        }

        String action = request.getParameter("action");
        boolean isWizardMode = "true".equalsIgnoreCase(request.getParameter("setup_wizard"));

        switch (action != null ? action.trim() : "") {
            case "addDepartment":
                addDepartment(request, response, tenantId, isWizardMode);
                break;
            case "editDepartment":
                editDepartment(request, response, tenantId, isWizardMode);
                break;
            case "deleteAndReassignDepartment":
                deleteAndReassignDepartment(request, response, tenantId, isWizardMode);
                break;
            default:
                String redirectUrl = request.getContextPath() + "/departments.jsp?error=" + encode("Invalid action.");
                if (isWizardMode) redirectUrl += "&setup_wizard=true";
                response.sendRedirect(redirectUrl);
                break;
        }
    }

    private void addDepartment(HttpServletRequest request, HttpServletResponse response, int tenantId, boolean isWizardMode) throws IOException {
        String departmentName = request.getParameter("addDepartmentName");
        String description = request.getParameter("addDescription");
        String supervisor = request.getParameter("addSupervisor");
        
        StringBuilder redirectUrl = new StringBuilder(request.getContextPath() + "/departments.jsp");
        String separator = "?";

        if (isWizardMode) {
            redirectUrl.append(separator).append("setup_wizard=true");
            separator = "&";
        }

        if (!isValid(departmentName)) {
            redirectUrl.append(separator).append("error=").append(encode("Department Name is required."));
            response.sendRedirect(redirectUrl.toString());
            return;
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            String checkSql = "SELECT NAME FROM departments WHERE TenantID = ? AND LOWER(NAME) = LOWER(?)";
            try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
                psCheck.setInt(1, tenantId);
                psCheck.setString(2, departmentName.trim().toLowerCase());
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) {
                        redirectUrl.append(separator).append("error=").append(encode("Department '" + departmentName.trim() + "' already exists."));
                        redirectUrl.append("&reopenModal=addDepartment&departmentName=").append(encode(departmentName.trim()));
                        response.sendRedirect(redirectUrl.toString());
                        return;
                    }
                }
            }

            String insertSql = "INSERT INTO departments (TenantID, NAME, DESCRIPTION, SUPERVISOR) VALUES (?, ?, ?, ?)";
            try (PreparedStatement psInsert = con.prepareStatement(insertSql)) {
                psInsert.setInt(1, tenantId);
                psInsert.setString(2, departmentName.trim());
                psInsert.setString(3, isValid(description) ? description.trim() : null);
                psInsert.setString(4, isValid(supervisor) ? supervisor.trim() : null);
                
                if (psInsert.executeUpdate() > 0) {
                    redirectUrl.append(separator).append("message=").append(encode("Department '" + departmentName.trim() + "' added."));
                    if (isWizardMode) redirectUrl.append("&deptAdded=true");
                } else {
                    redirectUrl.append(separator).append("error=").append(encode("Failed to add department."));
                }
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "DB error adding department for T:" + tenantId, e);
            redirectUrl.append(separator).append("error=").append(encode("Database error: " + e.getMessage()));
        }
        response.sendRedirect(redirectUrl.toString());
    }

    private void editDepartment(HttpServletRequest request, HttpServletResponse response, int tenantId, boolean isWizardMode) throws IOException {
        String originalName = request.getParameter("originalDepartmentName");
        String newDescription = request.getParameter("editDescription");
        String newSupervisor = request.getParameter("editSupervisor");
        
        StringBuilder redirectUrl = new StringBuilder(request.getContextPath() + "/departments.jsp");
        String separator = "?";

        if (isWizardMode) {
            redirectUrl.append(separator).append("setup_wizard=true");
            separator = "&";
        }

        if (!isValid(originalName) || "None".equalsIgnoreCase(originalName)) {
            redirectUrl.append(separator).append("error=").append(encode("Cannot edit this department."));
        } else {
            try (Connection con = DatabaseConnection.getConnection()) {
                String sql = "UPDATE departments SET DESCRIPTION = ?, SUPERVISOR = ? WHERE TenantID = ? AND NAME = ?";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, isValid(newDescription) ? newDescription.trim() : null);
                    ps.setString(2, isValid(newSupervisor) ? newSupervisor.trim() : null);
                    ps.setInt(3, tenantId);
                    ps.setString(4, originalName);
                    if (ps.executeUpdate() > 0) {
                        redirectUrl.append(separator).append("message=").append(encode("Department '" + originalName + "' updated."));
                    } else {
                        redirectUrl.append(separator).append("error=").append(encode("Department not found or no changes made."));
                    }
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "DB error editing dept for T:" + tenantId, e);
                redirectUrl.append(separator).append("error=").append(encode("Database error: " + e.getMessage()));
            }
        }
        response.sendRedirect(redirectUrl.toString());
    }

    private void deleteAndReassignDepartment(HttpServletRequest request, HttpServletResponse response, int tenantId, boolean isWizardMode) throws IOException {
        String toDelete = request.getParameter("departmentNameToDelete");
        String reassignTo = request.getParameter("targetDepartmentForReassignment");
        
        StringBuilder redirectUrl = new StringBuilder(request.getContextPath() + "/departments.jsp");
        String separator = "?";

        if (isWizardMode) {
            redirectUrl.append(separator).append("setup_wizard=true");
            separator = "&";
        }

        if (!isValid(toDelete) || !isValid(reassignTo) || "None".equalsIgnoreCase(toDelete) || toDelete.equalsIgnoreCase(reassignTo)) {
            redirectUrl.append(separator).append("error=").append(encode("Invalid deletion request."));
        } else {
            try (Connection con = DatabaseConnection.getConnection()) {
                con.setAutoCommit(false);
                try {
                    String reassignSql = "UPDATE employee_data SET DEPT = ? WHERE TenantID = ? AND DEPT = ?";
                    int employeesReassigned;
                    try (PreparedStatement ps = con.prepareStatement(reassignSql)) {
                        ps.setString(1, reassignTo);
                        ps.setInt(2, tenantId);
                        ps.setString(3, toDelete);
                        employeesReassigned = ps.executeUpdate();
                    }

                    String deleteSql = "DELETE FROM departments WHERE TenantID = ? AND NAME = ?";
                    try (PreparedStatement ps = con.prepareStatement(deleteSql)) {
                        ps.setInt(1, tenantId);
                        ps.setString(2, toDelete);
                        if (ps.executeUpdate() > 0) {
                            con.commit();
                            redirectUrl.append(separator).append("message=").append(encode("Dept '" + toDelete + "' deleted. " + employeesReassigned + " employees reassigned."));
                        } else {
                            con.rollback();
                            redirectUrl.append(separator).append("error=").append(encode("Department not found. Deletion failed."));
                        }
                    }
                } catch (SQLException e) {
                    con.rollback();
                    throw e; 
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "DB error deleting dept for T:" + tenantId, e);
                redirectUrl.append(separator).append("error=").append(encode("Database error: " + e.getMessage()));
            }
        }
        response.sendRedirect(redirectUrl.toString());
    }
}
