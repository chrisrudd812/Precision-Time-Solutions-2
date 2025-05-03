package timeclock.scheduling;

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
import java.sql.Time;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection; // Use connection pooling

@WebServlet("/AddEditAndDeleteSchedulesServlet")
public class AddEditAndDeleteSchedulesServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(AddEditAndDeleteSchedulesServlet.class.getName());

	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		if (request.getParameter("action") != null) {
			deleteSchedule(request, response);
		}
	}

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		// 1. Get the value from the hidden 'action' field
		String action = request.getParameter("action");

		// 2. Route based on the action value
		if ("add".equals(action)) {
		    // The 'add' action was submitted
		    addSchedule(request, response); // Call your existing add method

		} else if ("edit".equals(action)) {
		    // The 'edit' action was submitted
		    editSchedule(request, response); // Call your existing edit method

		} else {
		    // Handle cases where action is missing or invalid
		    logger.warning("doPost called in AddEditAndDeleteSchedulesServlet with invalid or missing action: " + action);
		    // Redirect back to the page with a generic error
		    response.sendRedirect("scheduling.jsp?error=Invalid+request.");
		}
	}

	private void addSchedule(HttpServletRequest request, HttpServletResponse response)
	        throws ServletException, IOException {

	    DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");

	    String scheduleName = request.getParameter("scheduleName");
	    Time shiftStart = null; // OK
	    Time lunchStart = null; // OK
	    Time lunchEnd = null;   // OK
	    Time shiftEnd = null;   // OK

	    int hoursRequired = 0; // OK
	    int lunchLength = 0;   // OK

	    // Populate Time Columns (Loop looks okay, gets 'shiftStart', etc.)
	    String[] times = { "shiftStart", "lunchStart", "lunchEnd", "shiftEnd" }; 

	    for (int i = 0; i < times.length; i++) {
	        String timeString = request.getParameter(times[i]); // Use correct param names

	        switch (times[i]) {
	        case "shiftStart": { // OK
	            if (timeString != null && !timeString.isEmpty()) {
	                // ... Time parsing logic ...
	                shiftStart = Time.valueOf(LocalTime.parse(timeString, dtf)); // Simplified
	            }
	            break;
	        }
	        case "lunchStart": { // OK
	            if (timeString != null && !timeString.isEmpty()) {
	                lunchStart = Time.valueOf(LocalTime.parse(timeString, dtf)); // Simplified
	            }
	            break;
	        }
	        // ... similar for lunchEnd, shiftEnd (assuming parsing logic is correct) ...
	         case "lunchEnd": { // OK
	             if (timeString != null && !timeString.isEmpty()) {
	                 lunchEnd = Time.valueOf(LocalTime.parse(timeString, dtf)); // Simplified
	             }
	            break;
	         }
	         case "shiftEnd": { // OK
	             if (timeString != null && !timeString.isEmpty()) {
	                 shiftEnd = Time.valueOf(LocalTime.parse(timeString, dtf)); // Simplified
	             }
	             break;
	         }
	        default: {
	            break;
	        }
	        }
	    }

	    // Build daysWorked (Loop looks okay, gets "Sun", "Mon", etc.)
	    String[] checkedDays = request.getParameterValues("days");

	 // 2. Process the array into a single string for database storage
	 String daysWorked = null; // Start with null (or "" depending on DB preference)

	 if (checkedDays != null && checkedDays.length > 0) {
	     // If the array is not null and has elements, join them with ", "
	     // The order depends on browser submission but usually matches HTML order.
	     daysWorked = String.join(", ", checkedDays);
	     logger.info("Processed days worked: " + daysWorked); // Example logging
	 } else {
	     // No checkboxes were checked
	     logger.info("No days worked checkboxes were checked.");
	     // daysWorked remains null (or set to "" if your DB requires it)
	 }

	    // *** PROBLEM AREA 1: Auto Lunch Logic ***
	    String autoLunchStr = request.getParameter("autoLunch"); // Correct param name
	    // Compare against "true" (string), not "On"
	    boolean autoLunch = "true".equalsIgnoreCase(autoLunchStr); // Case-insensitive comparison

	    // *** PROBLEM AREA 2: Hours/Length Parsing & Defaulting ***
	    String hr = request.getParameter("hoursRequired"); // Correct param name
	    if (hr != null && !hr.isBlank()) { // Check isBlank() instead of isEmpty()
	        try {
	            hoursRequired = Integer.parseInt(hr); // Use parseInt
	        } catch (NumberFormatException e) {
	            logger.log(Level.WARNING, "Invalid number format for Hours Required: " + hr, e);
	            // Decide on default or error handling. Let's default to 0 if invalid.
	            hoursRequired = 0;
	        }
	    } else if (autoLunch) {
	        // Maybe set a default if autoLunch is true but hours are blank?
	        // hoursRequired = 8; // Example default
	        // Or handle as an error:
	         response.sendRedirect("scheduling.jsp?addSuccess=false&error=Hours Required must be set if Auto Lunch is On.");
	         return;
	    }

	    String ll = request.getParameter("lunchLength"); // Correct param name
	    if (ll != null && !ll.isBlank()) { // Check isBlank()
	        try {
	            lunchLength = Integer.parseInt(ll); // Use parseInt
	        } catch (NumberFormatException e) {
	            logger.log(Level.WARNING, "Invalid number format for Lunch Length: " + ll, e);
	            // Default to 0 if invalid
	            lunchLength = 0;
	        }
	    } else if (autoLunch) {
	        // Handle case where autoLunch is true but length is blank
	        // lunchLength = 30; // Example default
	        // Or handle as an error:
	         response.sendRedirect("scheduling.jsp?addSuccess=false&error=Lunch Length must be set if Auto Lunch is On.");
	         return;
	    }

	    String workSchedule = request.getParameter("workSchedule"); // Correct param name

	    
	    try (Connection con = DatabaseConnection.getConnection();
	         PreparedStatement psCheck = con.prepareStatement("SELECT 1 FROM SCHEDULES WHERE NAME = ?"); // Use correct check statement
	         PreparedStatement psAddSchedule = con.prepareStatement( // Use correct insert statement
	                    "INSERT INTO SCHEDULES (NAME, SHIFT_START, LUNCH_START, LUNCH_END, SHIFT_END, DAYS_WORKED, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH, WORK_SCHEDULE) "
	                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))
	    {

	        // Check for Duplicate Name
	        psCheck.setString(1, scheduleName);
	        try (ResultSet rs = psCheck.executeQuery()) {
	            if (rs.next()) {
	                response.sendRedirect(
	                        "scheduling.jsp?addSuccess=false&error=" + "A schedule with this name already exists.");
	                return;
	            }
	        }

	        // Proceed with insertion
	        psAddSchedule.setString(1, scheduleName);
	        psAddSchedule.setTime(2, shiftStart);
	        psAddSchedule.setTime(3, lunchStart);
	        psAddSchedule.setTime(4, lunchEnd);
	        psAddSchedule.setTime(5, shiftEnd);
	        psAddSchedule.setString(6, daysWorked);
	        psAddSchedule.setBoolean(7, autoLunch); // Use the boolean value
	        psAddSchedule.setInt(8, hoursRequired);
	        psAddSchedule.setInt(9, lunchLength);
	        psAddSchedule.setString(10, workSchedule);

	        int rowsAffected = psAddSchedule.executeUpdate(); // Execute the INSERT

	        // Now check rowsAffected
	        if (rowsAffected > 0) {
	            response.sendRedirect("scheduling.jsp?addSuccess=true");
	        } else {
	            response.sendRedirect(
	                    "scheduling.jsp?addSuccess=false&error=" + "No schedules were added. Unexpected error.");
	        }

	    } catch (SQLException e) {
	        logger.log(Level.SEVERE, "Error adding schedule", e);
	        response.sendRedirect(
	                "scheduling.jsp?addSuccess=false&error=" + "A database error occurred: " + e.getMessage());
	    }
	}

	// Edit Schedule

	private void editSchedule(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		// --- Get parameters from the EDIT form ---
		String originalScheduleName = request.getParameter("originalScheduleName"); // From hidden field
		String ss = request.getParameter("shiftStart");                     
		String ls = request.getParameter("lunchStart");                     
		String le = request.getParameter("lunchEnd");                        
		String se = request.getParameter("shiftEnd");                        
		String autoLunchStr = request.getParameter("autoLunch");                   
		String hoursRequiredStr = request.getParameter("hoursRequired");     
		String lunchLengthStr = request.getParameter("lunchLength");         
		String workSchedule = request.getParameter("workSchedule");     

		// --- Basic Input Validation ---
		if (originalScheduleName == null || originalScheduleName.trim().isEmpty()) {
			response.sendRedirect("scheduling.jsp?editSuccess=false&error=Schedule Name (original and new) is required.");
			return;
		}

		Time shiftStart = null;
		Time lunchStart = null;
		Time lunchEnd = null;
		Time shiftEnd = null;
		int hoursRequired = 0;
		int lunchLength = 0;

		// --- Parse Time inputs (with error handling) ---
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm");
		try {
			if (ss != null && !ss.isEmpty()) shiftStart = Time.valueOf(LocalTime.parse(ss, dtf));
			if (ls != null && !ls.isEmpty()) lunchStart = Time.valueOf(LocalTime.parse(ls, dtf));
			if (le != null && !le.isEmpty()) lunchEnd = Time.valueOf(LocalTime.parse(le, dtf));
			if (se != null && !se.isEmpty()) shiftEnd = Time.valueOf(LocalTime.parse(se, dtf));
		} catch (DateTimeParseException | IllegalArgumentException e) {
			logger.log(Level.WARNING, "Invalid time format during edit", e);
			response.sendRedirect("scheduling.jsp?editSuccess=false&error=Invalid time format entered. Use HH:mm.");
			return;
		}

		// Build daysWorked (Loop looks okay, gets "Sun", "Mon", etc.)
	    String[] checkedDays = request.getParameterValues("days");

	 // 2. Process the array into a single string for database storage
	 String daysWorked = null; // Start with null (or "" depending on DB preference)

	 if (checkedDays != null && checkedDays.length > 0) {
	     // If the array is not null and has elements, join them with ", "
	     // The order depends on browser submission but usually matches HTML order.
	     daysWorked = String.join(", ", checkedDays);
	     logger.info("Processed days worked: " + daysWorked); // Example logging
	 } else {
	     // No checkboxes were checked
	     logger.info("No days worked checkboxes were checked.");
	     // daysWorked remains null (or set to "" if your DB requires it)
	 }
		// --- Parse Auto Lunch and related numbers ---
		boolean autoLunch = "true".equalsIgnoreCase(autoLunchStr); // Correct comparison

		try {
            if (hoursRequiredStr != null && !hoursRequiredStr.isBlank()) {
                hoursRequired = Integer.parseInt(hoursRequiredStr);
                 if (hoursRequired < 0) throw new NumberFormatException("Hours Required cannot be negative");
            } else if (autoLunch) {
                // Default or error if autoLunch is true but hours are blank?
                 response.sendRedirect("scheduling.jsp?editSuccess=false&error=Hours Required must be set if Auto Lunch is On.");
                 return;
            }

            if (lunchLengthStr != null && !lunchLengthStr.isBlank()) {
                lunchLength = Integer.parseInt(lunchLengthStr);
                 if (lunchLength < 0) throw new NumberFormatException("Lunch Length cannot be negative");
            } else if (autoLunch) {
                 // Default or error if autoLunch is true but length is blank?
                 response.sendRedirect("scheduling.jsp?editSuccess=false&error=Lunch Length must be set if Auto Lunch is On.");
                 return;
            }
        } catch (NumberFormatException e) {
             logger.log(Level.WARNING, "Invalid number format during edit", e);
             response.sendRedirect("scheduling.jsp?editSuccess=false&error=Please enter valid positive numbers for Hours Required and Lunch Length.");
             return;
         }


		// --- Database Update Logic ---
		String sqlUpdate = "UPDATE SCHEDULES SET SHIFT_START = ?, LUNCH_START = ?, LUNCH_END = ?, SHIFT_END = ?, DAYS_WORKED = ?, AUTO_LUNCH = ?, HRS_REQUIRED = ?, LUNCH_LENGTH = ?, WORK_SCHEDULE = ? WHERE NAME = ?"; // Correct update statement
		String sqlCheck = "SELECT 1 FROM SCHEDULES WHERE NAME = ? AND NAME <> ?"; // Correct check statement for schedules
		String sqlUpdateEmployees = "UPDATE EMPLOYEE_DATA SET SCHEDULE = ?"; 

		try (Connection con = DatabaseConnection.getConnection();
				PreparedStatement psCheck = con.prepareStatement(sqlCheck);
				PreparedStatement psUpdateSchedule = con.prepareStatement(sqlUpdate);
				PreparedStatement psUpdateEmps = con.prepareStatement(sqlUpdateEmployees)) {

			// --- Proceed with update ---
			psUpdateSchedule.setTime(1, shiftStart);
			psUpdateSchedule.setTime(2, lunchStart);
			psUpdateSchedule.setTime(3, lunchEnd);
			psUpdateSchedule.setTime(4, shiftEnd);
			psUpdateSchedule.setString(5, daysWorked);
			psUpdateSchedule.setBoolean(6, autoLunch);
			psUpdateSchedule.setInt(7, hoursRequired);
			psUpdateSchedule.setInt(8, lunchLength);
			psUpdateSchedule.setString(9, workSchedule);
			psUpdateSchedule.setString(10, originalScheduleName); // Set the schedule name for WHERE clause

			int rowsAffected = psUpdateSchedule.executeUpdate();

			response.sendRedirect("scheduling.jsp?editSuccess=true"); // Redirect with success
			
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error editing schedule", e);
			response.sendRedirect("scheduling.jsp?editSuccess=false&error=A database error occurred: " + e.getMessage());
		}
	}

    // deleteSchedule method 
	
    private void deleteSchedule(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String scheduleName = request.getParameter("scheduleName"); // Get the schedule name to delete

		// --- Validate the input (IMPORTANT!) ---
		if (scheduleName == null || scheduleName.trim().isEmpty()) {
			response.sendRedirect("scheduling.jsp?deleteSuccess=false&error= Schedule name is required for deletion.");
			return;
		}
		// Check if trying to delete "Open" (case-insensitive)
		if ("open".equalsIgnoreCase(scheduleName)) {
			response.sendRedirect(
					"scheduling.jsp?deleteSuccess=false&error=Cannot delete the default 'Open' schedule.");
			return; // Stop processing
		}

		try (Connection con = DatabaseConnection.getConnection();
				PreparedStatement ps = con.prepareStatement("DELETE FROM SCHEDULES WHERE NAME = ?")) {

			ps.setString(1, scheduleName);
			int rowsAffected = ps.executeUpdate();

			if (rowsAffected > 0) {
				response.sendRedirect("scheduling.jsp?deleteSuccess=true"); // Redirect with success
			} else {
				response.sendRedirect("scheduling.jsp?deleteSuccess=false&error=No Schedules were deleted");
			}

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Error deleting schedule", e);
			response.sendRedirect(
					"scheduling.jsp?deleteSuccess=false&error=A database error occurred: " + e.getMessage()); // Redirect with error
		}
	}
}
