package timeclock.punches;

import com.google.gson.Gson;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import timeclock.Configuration;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@WebServlet("/api/mobile/timecard")
public class MobileTimecardServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(MobileTimecardServlet.class.getName());
    private final Gson gson = new Gson();
    
    private String formatTimeWithoutSeconds(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return "";
        try {
            // Parse time like "07:15:00 AM" and format to "7:15 AM"
            java.time.LocalTime time = java.time.LocalTime.parse(timeStr, 
                java.time.format.DateTimeFormatter.ofPattern("hh:mm:ss a"));
            return time.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
        } catch (Exception e) {
            // If parsing fails, try to remove seconds manually
            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                if (parts.length >= 2) {
                    String ampm = timeStr.contains("AM") ? " AM" : timeStr.contains("PM") ? " PM" : "";
                    return parts[0] +":" + parts[1] + ampm;
                }
            }
            return timeStr;
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> responseMap = new HashMap<>();

        try {
            String tenantIdStr = request.getParameter("tenantId");
            String eidStr = request.getParameter("eid");
            
            if (tenantIdStr == null || eidStr == null) {
                responseMap.put("success", false);
                responseMap.put("message", "Missing parameters");
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(responseMap));
                }
                return;
            }

            int tenantId = Integer.parseInt(tenantIdStr);
            int eid = Integer.parseInt(eidStr);

            // Get employee info using ShowPunches method
            Map<String, Object> employeeInfo = ShowPunches.getEmployeeTimecardInfo(tenantId, eid);
            if (employeeInfo == null) {
                responseMap.put("success", false);
                responseMap.put("message", "Employee not found");
                try (PrintWriter out = response.getWriter()) {
                    out.print(gson.toJson(responseMap));
                }
                return;
            }

            // Build employee data for mobile response
            Map<String, Object> employee = new HashMap<>();
            String[] nameParts = ((String) employeeInfo.get("employeeName")).split(" ", 2);
            employee.put("firstName", nameParts.length > 0 ? nameParts[0] : "");
            employee.put("lastName", nameParts.length > 1 ? nameParts[1] : "");
            employee.put("department", employeeInfo.get("department"));
            employee.put("supervisor", employeeInfo.get("supervisor"));
            employee.put("wageType", employeeInfo.get("wageType"));
            employee.put("schedule", employeeInfo.get("scheduleName"));
            
            // Show shift times as schedule hours (7:00 AM - 3:30 PM)
            java.sql.Time shiftStart = (java.sql.Time) employeeInfo.get("shiftStart");
            java.sql.Time shiftEnd = (java.sql.Time) employeeInfo.get("shiftEnd");
            if (shiftStart != null && shiftEnd != null) {
                employee.put("scheduleHours", shiftStart.toString() + " - " + shiftEnd.toString());
            } else {
                employee.put("scheduleHours", "8.0");
            }
            
            // Format auto lunch properly
            Boolean autoLunch = (Boolean) employeeInfo.get("autoLunch");
            Object hoursReq = employeeInfo.get("hoursRequired");
            Object lunchLength = employeeInfo.get("lunchLength");
            
            logger.info("DEBUG: autoLunch=" + autoLunch + ", hoursReq=" + hoursReq + ", lunchLength=" + lunchLength);
            
            if (Boolean.TRUE.equals(autoLunch) && hoursReq != null && lunchLength != null) {
                employee.put("autoLunch", "On");
                employee.put("lunchThreshold", hoursReq);
                employee.put("lunchLength", lunchLength);
                logger.info("DEBUG: Setting autoLunch=On, lunchThreshold=" + hoursReq + ", lunchLength=" + lunchLength);
            } else {
                employee.put("autoLunch", "Off");
                logger.info("DEBUG: Setting autoLunch=Off");
            }
            
            employee.put("vacationHours", employeeInfo.get("vacationHours"));
            employee.put("sickHours", employeeInfo.get("sickHours"));
            employee.put("personalHours", employeeInfo.get("personalHours"));

            // Get current pay period from Configuration
            Map<String, LocalDate> payPeriodInfo = ShowPunches.getCurrentPayPeriodInfo(tenantId);
            LocalDate payPeriodStart, payPeriodEnd;
            
            if (payPeriodInfo != null) {
                payPeriodStart = payPeriodInfo.get("startDate");
                payPeriodEnd = payPeriodInfo.get("endDate");
            } else {
                // Fallback to current week if no pay period configured
                LocalDate today = LocalDate.now();
                payPeriodStart = today.minusDays(today.getDayOfWeek().getValue() - 1);
                payPeriodEnd = payPeriodStart.plusDays(6);
            }

            // Get timezone for mobile display
            String timeZone = Configuration.getProperty(tenantId, "DefaultTimeZone", "America/Denver");
            
            // Get punch data using ShowPunches (same as web app)
            Map<String, Object> punchData = ShowPunches.getTimecardPunchData(
                tenantId, eid, payPeriodStart, payPeriodEnd, employeeInfo, timeZone
            );

            // Transform web format to mobile format
            @SuppressWarnings("unchecked")
            List<Map<String, String>> webPunches = (List<Map<String, String>>) punchData.get("punches");
            List<Map<String, Object>> mobilePunches = new ArrayList<>();
            
            if (webPunches != null) {
                for (Map<String, String> punch : webPunches) {
                    Map<String, Object> mobilePunch = new HashMap<>();
                    mobilePunch.put("id", punch.get("punchId"));
                    // Fix date format for mobile (MM/dd instead of full date)
                    String punchDate = punch.get("punchDate");
                    if (punchDate != null && punchDate.length() > 5) {
                        try {
                            java.time.LocalDate date = java.time.LocalDate.parse(punchDate);
                            mobilePunch.put("date", date.format(java.time.format.DateTimeFormatter.ofPattern("MM/dd")));
                        } catch (Exception e) {
                            mobilePunch.put("date", punchDate);
                        }
                    } else {
                        mobilePunch.put("date", punchDate);
                    }
                    mobilePunch.put("dow", punch.get("dayOfWeek"));
                    // Format times without seconds (h:mm a)
                    String timeIn = punch.get("timeIn");
                    String timeOut = punch.get("timeOut");
                    mobilePunch.put("in", formatTimeWithoutSeconds(timeIn));
                    mobilePunch.put("out", formatTimeWithoutSeconds(timeOut));
                    mobilePunch.put("hours", punch.get("totalHours"));
                    mobilePunch.put("type", punch.get("punchType"));
                    mobilePunch.put("isLate", !punch.get("inTimeCssClass").isEmpty());
                    mobilePunch.put("isEarlyOut", !punch.get("outTimeCssClass").isEmpty());
                    mobilePunches.add(mobilePunch);
                }
            }

            responseMap.put("success", true);
            responseMap.put("employee", employee);
            // Add row banding for mobile app
            String currentDate = null;
            String currentBand = "bandA";
            for (Map<String, Object> punch : mobilePunches) {
                String punchDate = (String) punch.get("date");
                if (!punchDate.equals(currentDate)) {
                    currentDate = punchDate;
                    currentBand = "bandA".equals(currentBand) ? "bandB" : "bandA";
                }
                punch.put("band", currentBand);
            }
            
            responseMap.put("punches", mobilePunches);
            responseMap.put("payPeriodStart", payPeriodStart.toString());
            responseMap.put("payPeriodEnd", payPeriodEnd.toString());
            responseMap.put("totalRegularHours", punchData.get("totalRegularHours"));
            responseMap.put("totalOvertimeHours", punchData.get("totalOvertimeHours"));
            responseMap.put("totalDoubleTimeHours", punchData.get("totalDoubleTimeHours"));
            
            double regular = (Double) punchData.get("totalRegularHours");
            double overtime = (Double) punchData.get("totalOvertimeHours");
            double doubletime = (Double) punchData.get("totalDoubleTimeHours");
            responseMap.put("periodTotal", regular + overtime + doubletime);

        } catch (Exception e) {
            logger.severe("Mobile timecard error: " + e.getMessage());
            e.printStackTrace();
            responseMap.put("success", false);
            responseMap.put("message", e.getMessage());
            logger.info("DEBUG: Error response: " + gson.toJson(responseMap));
        }

        try (PrintWriter out = response.getWriter()) {
            String jsonResponse = gson.toJson(responseMap);
            logger.info("DEBUG: Full JSON response: " + jsonResponse);
            out.print(jsonResponse);
            out.flush();
        }
    }
}