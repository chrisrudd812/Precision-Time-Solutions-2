package timeclock.db;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

import timeclock.db.AddSampleData; // Assuming this is the correct class/package

@WebServlet("/AddSampleDataServlet") // URL mapping
public class AddSampleDataServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(AddSampleDataServlet.class.getName());

	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        String jsonResponse;

		try {
            
            AddSampleData.addSampleData();
            logger.info("Sample data added successfully.");

            // Send a success JSON response
            jsonResponse = "{\"success\": true}";

		} catch (Exception e) { // Catch broader exceptions during data adding
            logger.log(Level.SEVERE, "Error calling AddSampleData.addSampleData()", e);

            // Send an error JSON response
            String errorMessage = (e.getMessage() != null) ? e.getMessage().replace("\"", "\\\"") : "Unknown error occurred";
            jsonResponse = "{\"success\": false, \"error\": \"" + errorMessage + "\"}";
            // Optionally set a server error status if appropriate
            // response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
		}

        out.print(jsonResponse);
        out.flush();
	}

}