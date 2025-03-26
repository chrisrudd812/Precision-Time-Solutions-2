package timeclock.accruals;

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

@WebServlet("/AddAndDeleteAccrualPoliciesServlet")
public class AddAndDeleteAccrualPoliciesServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(AddAndDeleteAccrualPoliciesServlet.class.getName());

	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		 if (request.getParameter("delete") != null) {
	            deleteAccrualPolicy(request, response);
	     }
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		//Check for the correct action, based on hidden fields in form
		if (request.getParameter("addAccrual") != null) {
            addAccrualPolicy(request, response); // Delegate to add method
        } else if (request.getParameter("editAccrual") != null) {
            editAccrualPolicy(request, response); // Delegate to edit method
        }
	}

	private void addAccrualPolicy(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		String accrualName = request.getParameter("addAccrualName");
	    String vacationDaysStr = request.getParameter("addVacationDays");
	    String sickDaysStr = request.getParameter("addSickDays");
	    String personalDaysStr = request.getParameter("addPersonalDays");

	    // Validate that required fields are not empty
	    if (accrualName == null || accrualName.trim().isEmpty() ||
	        vacationDaysStr == null || vacationDaysStr.trim().isEmpty() ||
	        sickDaysStr == null || sickDaysStr.trim().isEmpty() ||
	        personalDaysStr == null || personalDaysStr.trim().isEmpty()) {
	    	response.sendRedirect("accruals.jsp?addSuccess=false&error=" +
	    		    "All fields are required. Please enter valid values.");
	        return; // Stop processing
	    }
		// Check if trying to add a policy named "None" (case-insensitive)
        if ("none".equalsIgnoreCase(accrualName)) {
            response.sendRedirect("accruals.jsp?addSuccess=false&error=Cannot add a policy named 'None'.");
            return;
        }
		// Convert to correct data types, with error handling
		int vacationDays;
		int sickDays;
		int personalDays;
		try {
			vacationDays = Integer.parseInt(vacationDaysStr);
			sickDays = Integer.parseInt(sickDaysStr);
			personalDays = Integer.parseInt(personalDaysStr);

			// Check if days are positive
		    if (vacationDays < 0 || sickDays < 0 || personalDays < 0) {
		        response.sendRedirect("accruals.jsp?addSuccess=false&error=Days cannot be negative.");
		        return;
		    }
		} catch (NumberFormatException e) {
			response.sendRedirect("accruals.jsp?addSuccess=false&error=" +
				    "Please enter valid integers for days.");
		    return; // Stop processing
		}

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "INSERT INTO CHRIS.ACCRUALS (NAME, VACATION, SICK, PERSONAL) VALUES (?, ?, ?, ?)");
             PreparedStatement checkStmt = con.prepareStatement("SELECT 1 FROM CHRIS.ACCRUALS WHERE NAME = ?")) { // Check for duplicate names
            // --- Check for Duplicate Name (IMPORTANT) ---
            checkStmt.setString(1, accrualName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
					//Name already exists
					response.sendRedirect("accruals.jsp?addSuccess=false&error=" +
                		    "An accrual policy with this name already exists.");
                    return; // IMPORTANT: Stop execution
                }
            }
            // --- Proceed with insertion if no duplicate name exists ---
            ps.setString(1, accrualName);
            ps.setInt(2, vacationDays); // Use the integer values
            ps.setInt(3, sickDays);    // Use the integer values
            ps.setInt(4, personalDays);  // Use the integer values

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                response.sendRedirect("accruals.jsp?addSuccess=true"); // Redirect with success message
            } else {
                // Handle insertion failure (unlikely, but good practice)
				response.sendRedirect("accruals.jsp?addSuccess=false&error=" +
            		    "No rows were added. Unexpected error.");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error adding accrual policy", e);
			response.sendRedirect("accruals.jsp?addSuccess=false&error=" +
        		    "A database error occurred: " + e.getMessage()); //pass error

        }
    }
	private void editAccrualPolicy(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
		String originalAccrualName = request.getParameter("originalAccrualName");
        String accrualName = request.getParameter("editAccrualName");
        String vacation = request.getParameter("editVacationDays");
        String sick = request.getParameter("editSickDays");
        String personal = request.getParameter("editPersonalDays");

        // Validate input
        if (originalAccrualName == null || originalAccrualName.trim().isEmpty() ||
   			 accrualName == null || accrualName.trim().isEmpty() ||
   			 vacation == null || vacation.trim().isEmpty() || sick == null
   			 || sick.trim().isEmpty() || personal == null || personal.trim().isEmpty()
   			 ) {
			   response.sendRedirect("accruals.jsp?editSuccess=false&error=Missing required parameters for editing.");
			   return; // Stop processing
            }
        
        // Check if trying to rename to "None" (case-insensitive)
        if ("none".equalsIgnoreCase(accrualName)) {
            response.sendRedirect("accruals.jsp?editSuccess=false&error=Cannot rename a policy to 'None'.");
             return;
        }
     // Convert to correct data types, with error handling
     		int vacationDays;
     		int sickDays;
     		int personalDays;
     		try {
     			vacationDays = Integer.parseInt(vacation);
     			sickDays = Integer.parseInt(sick);
     			personalDays = Integer.parseInt(personal);

     			// Check if days are positive
     		    if (vacationDays < 0 || sickDays < 0 || personalDays < 0) {
     		    	response.sendRedirect("accruals.jsp?editSuccess=false&error=Days cannot be negative.");
     		        return;
     		    }
     		} catch (NumberFormatException e) {
     			response.sendRedirect("accruals.jsp?editSuccess=false&error=Please enter valid integers for days.");
     		    return;
     		}

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                "UPDATE CHRIS.ACCRUALS SET NAME = ?, VACATION = ?, SICK = ?, PERSONAL = ? WHERE NAME = ?");
             PreparedStatement checkStmt = con.prepareStatement("SELECT 1 FROM CHRIS.ACCRUALS WHERE NAME = ? AND NAME <> ?")) {

             // Check for duplicate name (excluding the current record being edited)
            checkStmt.setString(1, accrualName);
            checkStmt.setString(2, originalAccrualName);
            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next()) {
                    response.sendRedirect("accruals.jsp?editSuccess=false&error=An accrual policy with this name already exists.");
                    return;
                }
            }

            // Proceed with update
            ps.setString(1, accrualName);
            ps.setInt(2, Integer.parseInt(vacation));
            ps.setInt(3, Integer.parseInt(sick));
            ps.setInt(4, Integer.parseInt(personal));
            ps.setString(5, originalAccrualName); // Use original name in WHERE clause

            int rowsAffected = ps.executeUpdate();
            if (rowsAffected > 0) {
                response.sendRedirect("accruals.jsp?editSuccess=true");
            } else {
				response.sendRedirect("accruals.jsp?editSuccess=false&error=No rows were edited. Unexpected error.");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error editing accrual policy", e);
			response.sendRedirect("accruals.jsp?editSuccess=false&error=A database error occurred: " + e.getMessage()); //pass error
        }
    }

    private void deleteAccrualPolicy(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
		String accrualName = request.getParameter("delete");
		
		// --- Validate the input (IMPORTANT!) ---
		if (accrualName == null || accrualName.trim().isEmpty()) {
			response.sendRedirect("accruals.jsp?deleteSuccess=false&error=Accrual policy name is required for deletion.");
		    return;
		}
		// Check if trying to delete "None" (case-insensitive)
        if ("none".equalsIgnoreCase(accrualName)) {
            response.sendRedirect("accruals.jsp?deleteSuccess=false&error=Cannot delete the default 'None' policy.");
            return; // Stop processing
        }

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("DELETE FROM CHRIS.ACCRUALS WHERE NAME = ?")) {

            ps.setString(1, accrualName);
            int rowsAffected = ps.executeUpdate();

            if (rowsAffected > 0) {
                response.sendRedirect("accruals.jsp?deleteSuccess=true"); // Redirect with success
            } else {
            	response.sendRedirect("accruals.jsp?deleteSuccess=false&error=No Policies were deleted");
            }

        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error deleting accrual policy", e);
            response.sendRedirect("accruals.jsp?deleteSuccess=false&error=A database error occurred: " + e.getMessage()); // Redirect with error
        }
    }
}