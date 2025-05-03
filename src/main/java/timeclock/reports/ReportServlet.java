package timeclock.reports; // Or your appropriate package

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

// Using simple JSON manually. For complex apps, consider a library like Gson or Jackson.
// import com.google.gson.Gson;

@WebServlet("/ReportServlet") // Maps requests to /ReportServlet
public class ReportServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(ReportServlet.class.getName());
    // private static final Gson gson = new Gson(); // If using Gson

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Allow GET for simplicity in fetching reports, POST might be preferred depending on context
        handleRequest(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        handleRequest(request, response);
    }

    private void handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String reportType = request.getParameter("reportType");
        logger.info("ReportServlet received request for reportType: " + reportType);

        String reportHtml = null;
        String message = null;
        boolean success = false;
        int statusCode = HttpServletResponse.SC_OK; // Default OK

        try {
            if (reportType == null || reportType.trim().isEmpty()) {
                message = "Error: reportType parameter is missing.";
                statusCode = HttpServletResponse.SC_BAD_REQUEST;
                logger.warning(message);
            } else {
                switch (reportType) {
                    case "exception":
                        reportHtml = ShowReports.showExceptionReport();
                        if ("NO_EXCEPTIONS".equals(reportHtml)) {
                            message = "No exceptions found.";
                            reportHtml = null; // Don't send the marker string as HTML
                        } else if (reportHtml.contains("report-error-row")) {
                             // If ShowReports returned an error row, treat it as an error message
                            message = "Error generating exception report. Check server logs.";
                            reportHtml = null; // Don't send the error row as regular HTML
                            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                        }
                        success = (message == null || message.equals("No exceptions found.")); // Success if no errors or just no exceptions
                        break;

                    case "tardy":
                        reportHtml = ShowReports.showTardyReport();
                         if (reportHtml.contains("report-message-row")) { // Check if ShowReports indicated no data
                            message = "No tardiness or early outs found.";
                            reportHtml = null;
                        } else if (reportHtml.contains("report-error-row")) {
                            message = "Error generating tardy report. Check server logs.";
                            reportHtml = null;
                            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                        }
                        success = (message == null || message.equals("No tardiness or early outs found."));
                        break;

                    case "whosin":
                        reportHtml = ShowReports.showWhosInReport();
                        if (reportHtml.contains("report-message-row")) {
                            message = "No employees currently clocked in.";
                            reportHtml = null;
                        } else if (reportHtml.contains("report-error-row")) {
                            message = "Error generating who's in report. Check server logs.";
                            reportHtml = null;
                            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
                        }
                        success = (message == null || message.equals("No employees currently clocked in."));
                        break;

                    // Add cases for other reports here as implemented in ShowReports
                    // case "timecard":
                    //     // Need employee ID parameter for this one
                    //     break;

                    default:
                        message = "Error: Unknown reportType specified: " + reportType;
                        statusCode = HttpServletResponse.SC_BAD_REQUEST;
                        logger.warning(message);
                        break;
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Unexpected error generating report: " + reportType, e);
            message = "An unexpected server error occurred.";
            reportHtml = null;
            success = false;
            statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
        }

        // Send JSON response
        writeJsonResponse(response, success, reportHtml, message, statusCode);
    }

    private void writeJsonResponse(HttpServletResponse response, boolean success, String html, String message, int statusCode) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);

        // Basic manual JSON creation (Replace with library like Gson/Jackson for robustness)
        StringBuilder json = new StringBuilder("{");
        json.append("\"success\": ").append(success).append(",");
        json.append("\"html\": ");
        if (html != null) {
            // Basic escaping for HTML within JSON string
            json.append("\"").append(html.replace("\\", "\\\\")
                                         .replace("\"", "\\\"")
                                         .replace("\n", "\\n")
                                         .replace("\r", "") // Remove carriage returns
                                         .replace("\t", "\\t"))
                .append("\"");
        } else {
            json.append("null");
        }
        json.append(",");
        json.append("\"message\": ");
        if (message != null) {
            json.append("\"").append(message.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        } else {
            json.append("null");
        }
        json.append("}");

        PrintWriter out = response.getWriter();
        out.print(json.toString());
        out.flush();
        logger.fine("Sent JSON Response for report: " + json.toString());
    }

    // Helper from previous servlet - kept for potential use
    private boolean isValid(String s) { return s != null && !s.trim().isEmpty(); }

}