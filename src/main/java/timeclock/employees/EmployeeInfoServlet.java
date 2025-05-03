package timeclock.employees; // Adjust package if needed

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.punches.ShowPunches; // Ensure this class exists and is accessible

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.JSONObject; // Ensure org.json JAR is in WEB-INF/lib

@WebServlet("/EmployeeInfoServlet")
public class EmployeeInfoServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(EmployeeInfoServlet.class.getName());
    private static final SimpleDateFormat SCHEDULE_TIME_FORMAT = new SimpleDateFormat("hh:mm a");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        logger.info("EmployeeInfoServlet received GET action: " + action);
        if ("getScheduleInfo".equals(action)) { handleGetScheduleInfo(request, response); }
        else { response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown or missing action parameter."); }
    }

    private void handleGetScheduleInfo(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String eidStr = request.getParameter("eid"); int eid = 0; JSONObject jsonResponse = new JSONObject();
        try { eid = Integer.parseInt(eidStr); }
        catch (NumberFormatException | NullPointerException e) { logger.warning("Invalid or missing EID for getScheduleInfo."); response.setStatus(HttpServletResponse.SC_BAD_REQUEST); jsonResponse.put("success", false); jsonResponse.put("message", "Invalid Employee ID provided."); writeJsonResponse(response, jsonResponse.toString()); return; }
        logger.info("Fetching schedule info for EID: " + eid);
        try {
             // Ensure ShowPunches class has the getEmployeeTimecardInfo method
             Map<String, Object> info = ShowPunches.getEmployeeTimecardInfo(eid);
            if (info != null) {
                jsonResponse.put("success", true); jsonResponse.put("employeeName", info.getOrDefault("employeeName", "N/A")); jsonResponse.put("scheduleName", info.getOrDefault("scheduleName", "N/A"));
                Time shiftStart = (Time) info.get("shiftStart"); Time shiftEnd = (Time) info.get("shiftEnd");
                jsonResponse.put("shiftStart", shiftStart != null ? SCHEDULE_TIME_FORMAT.format(shiftStart) : JSONObject.NULL);
                jsonResponse.put("shiftEnd", shiftEnd != null ? SCHEDULE_TIME_FORMAT.format(shiftEnd) : JSONObject.NULL);
                 response.setStatus(HttpServletResponse.SC_OK);
            } else { logger.warning("No info found for EID: " + eid); response.setStatus(HttpServletResponse.SC_NOT_FOUND); jsonResponse.put("success", false); jsonResponse.put("message", "Employee information not found."); }
        } catch (Exception e) { logger.log(Level.SEVERE, "Error fetching schedule info for EID: " + eid, e); response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); jsonResponse.put("success", false); jsonResponse.put("message", "Server error retrieving employee data: " + e.getMessage()); }
         writeJsonResponse(response, jsonResponse.toString());
    }

    private void writeJsonResponse(HttpServletResponse response, String jsonString) throws IOException { response.setContentType("application/json"); response.setCharacterEncoding("UTF-8"); PrintWriter out = response.getWriter(); out.print(jsonString); out.flush(); }
}