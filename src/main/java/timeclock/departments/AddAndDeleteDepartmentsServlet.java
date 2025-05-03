package timeclock.departments;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection; // Use connection pooling

@WebServlet("/AddAndDeleteDepartmentsServlet")
public class AddAndDeleteDepartmentsServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(AddAndDeleteDepartmentsServlet.class.getName());

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		 if (request.getParameter("delete") != null) {
	            deleteDepartment(request, response);
	     }
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//Check for the correct action, based on hidden fields in form
		if (request.getParameter("addDepartment") != null) {
            addDepartment(request, response); // Delegate to add method
        } else if (request.getParameter("editDepartment") != null) {
            editDepartment(request, response); // Delegate to edit method
        }
	}

	private void addDepartment(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		
		String departmentName = request.getParameter("addDepartmentName");
	    String description = request.getParameter("addDescription");
	    String supervisor = request.getParameter("addSupervisor");

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "INSERT INTO DEPARTMENTS (NAME, DESCRIPTION, SUPERVISOR) VALUES (?, ?, ?)");
             PreparedStatement checkStmt = con.prepareStatement("SELECT 1 FROM DEPARTMENTS WHERE NAME = ?")) { // Check for duplicate names
            // --- Check for Duplicate Name (IMPORTANT) ---
            checkStmt.setString(1, departmentName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
					//Name already exists
					response.sendRedirect("departments.jsp?addSuccess=false&error=" +
                		    "A department with this name already exists.");
                    return; // IMPORTANT: Stop execution
                }
            }
            // --- Proceed with insertion if no duplicate name exists ---
            ps.setString(1, departmentName);
            ps.setString(2, description); 
            ps.setString(3, supervisor);

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                response.sendRedirect("departments.jsp?addSuccess=true"); // Redirect with success message
            } else {
                // Handle insertion failure (unlikely, but good practice)
				response.sendRedirect("departments.jsp?addSuccess=false&error=" +
            		    "No departments were added. Unexpected error.");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error adding department", e);
			response.sendRedirect("departments.jsp?addSuccess=false&error=" +
        		    "A database error occurred: " + e.getMessage()); //pass error

        }
    }
	
	private void editDepartment(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
		String originalDepartmentName = request.getParameter("originalDepartmentName");
        String departmentName = request.getParameter("editDepartmentName");
        String description = request.getParameter("editDescription");
        String supervisor = request.getParameter("editSupervisor");
		
        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "UPDATE DEPARTMENTS SET NAME = ?, DESCRIPTION = ?, SUPERVISOR = ? WHERE NAME = ?");
             PreparedStatement checkStmt = con.prepareStatement("SELECT 1 FROM DEPARTMENTS WHERE NAME = ? AND NAME <> ?")) {

             // Check for duplicate name (excluding the current record being edited)
            checkStmt.setString(1, departmentName);
            checkStmt.setString(2, originalDepartmentName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    response.sendRedirect("departments.jsp?editSuccess=false&error=A department with this name already exists.");
                    return;
                }
            }

            // Proceed with update
            ps.setString(1, departmentName);
            ps.setString(2, description);
            ps.setString(3, supervisor);
            ps.setString(4, originalDepartmentName); // Use original name in WHERE clause

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                response.sendRedirect("departments.jsp?editSuccess=true");
            } else {
				response.sendRedirect("departments.jsp?editSuccess=false&error=No rows were edited. Unexpected error.");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error editing department", e);
			response.sendRedirect("departments.jsp?editSuccess=false&error=A database error occurred: " + e.getMessage()); //pass error
        }
    }

    private void deleteDepartment(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
		String departmentName = request.getParameter("delete");
		
		// --- Validate the input (IMPORTANT!) ---
		if (departmentName == null || departmentName.trim().isEmpty()) {
			response.sendRedirect("departments.jsp?deleteSuccess=false&error= Department name is required for deletion.");
		    return;
		}
		// Check if trying to delete "None" (case-insensitive)
        if ("none".equalsIgnoreCase(departmentName)) {
            response.sendRedirect("departments.jsp?deleteSuccess=false&error=Cannot delete the default 'None' department.");
            return; // Stop processing
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM DEPARTMENTS WHERE NAME = ?")) {

            ps.setString(1, departmentName);
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                response.sendRedirect("departments.jsp?deleteSuccess=true"); // Redirect with success
            } else {
            	response.sendRedirect("departments.jsp?deleteSuccess=false&error=No Departments were deleted");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting department", e);
            response.sendRedirect("departments.jsp?deleteSuccess=false&error=A database error occurred: " + e.getMessage()); // Redirect with error
        }
    }
}