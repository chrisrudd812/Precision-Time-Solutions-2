package timeclock.settings;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.util.IPAddressUtil; // Assuming you have this utility class

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
import org.json.JSONObject; // For JSON responses

@WebServlet("/NetworkRestrictionServlet")
public class NetworkRestrictionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(NetworkRestrictionServlet.class.getName());
    private static final String WIZARD_RETURN_STEP_SETTINGS = "settings_setup"; // Target step on settings.jsp

    private Integer getTenantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("TenantID") instanceof Integer) {
            return (Integer) session.getAttribute("TenantID");
        }
        return null;
    }

    private boolean isAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return "Administrator".equalsIgnoreCase((String) session.getAttribute("Permissions"));
        }
        return false;
    }
    
    private String encodeURL(String value) throws IOException {
        if (value == null) return "";
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private String escapeJson(String s) {
        if (s == null) return "null";
        return JSONObject.quote(s);
    }

    private void loadAndForward(HttpServletRequest request, HttpServletResponse response, Integer tenantId,
                                String initialPageLoadError, boolean fromWizardContextOnGet)
            throws ServletException, IOException {
        String logPrefix = "[NetworkRestrictionServlet loadAndForward T:" + (tenantId != null ? tenantId : "null") + "] ";
        logger.info(logPrefix + "Called. initialPageLoadError: '" + initialPageLoadError + "', fromWizardContextOnGet: " + fromWizardContextOnGet);
        
        List<Map<String, Object>> allowedNetworks = new ArrayList<>();
        String effectivePageLoadError = initialPageLoadError;

        HttpSession session = request.getSession(false);
        boolean actualWizardModeForJSP = false;
        String wizardReturnStepForJSP = null;

        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
             String currentSessionWizardStep = (String) session.getAttribute("wizardStep");
             // If this page was reached from settings.jsp during wizard's "settings_setup" step
             // OR if it's another specific wizard step that should lead back to settings_setup for restrictions
             if (WIZARD_RETURN_STEP_SETTINGS.equals(currentSessionWizardStep) || fromWizardContextOnGet) {
                 actualWizardModeForJSP = true;
                 wizardReturnStepForJSP = WIZARD_RETURN_STEP_SETTINGS; // This page always returns to settings_setup for now
             }
        }
        request.setAttribute("pageIsInWizardMode", actualWizardModeForJSP);
        request.setAttribute("wizardReturnStepForJSP", wizardReturnStepForJSP);
        logger.info(logPrefix + "Request attributes set: pageIsInWizardMode=" + actualWizardModeForJSP + ", wizardReturnStepForJSP='" + wizardReturnStepForJSP + "'");

        if (tenantId != null && tenantId > 0) {
            String sql = "SELECT NetworkID, NetworkName, CIDR, Description, IsEnabled FROM allowed_networks WHERE TenantID = ? ORDER BY NetworkName ASC";
            try (Connection conn = DatabaseConnection.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, tenantId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> network = new HashMap<>();
                        network.put("NetworkID", rs.getInt("NetworkID"));
                        network.put("NetworkName", rs.getString("NetworkName"));
                        network.put("CIDR", rs.getString("CIDR"));
                        network.put("Description", rs.getString("Description"));
                        network.put("IsEnabled", rs.getBoolean("IsEnabled"));
                        allowedNetworks.add(network);
                    }
                }
                logger.info(logPrefix + "Fetched " + allowedNetworks.size() + " allowed networks.");
            } catch (SQLException e) {
                logger.log(Level.SEVERE, logPrefix + "Error fetching allowed networks.", e);
                if (effectivePageLoadError == null) effectivePageLoadError = "Error loading network configurations: " + e.getMessage();
            }
        } else {
            if (effectivePageLoadError == null) { // Only set if no specific error passed in
                 effectivePageLoadError = isAdmin(request) ? "Invalid tenant context." : "Access Denied.";
            }
            logger.warning(logPrefix + "TenantId null or invalid. Error: " + effectivePageLoadError);
        }

        request.setAttribute("allowedNetworks", allowedNetworks);
        if (effectivePageLoadError != null) request.setAttribute("pageLoadErrorMessage", effectivePageLoadError);
        // Success/error messages from POST are set as request attributes directly in doPost before calling this
        
        logger.info(logPrefix + "Forwarding to /configureNetworkRestrictions.jsp");
        request.getRequestDispatcher("/configureNetworkRestrictions.jsp").forward(request, response);
    }


    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Integer tenantId = getTenantId(request);
        String logPrefix = "[NetworkRestrictionServlet doGet T:" + (tenantId != null ? tenantId : "null") + "] ";
        logger.info(logPrefix + "Called.");
        String pageLoadError = null;

        if (tenantId == null) {
            logger.warning(logPrefix + "Session expired or invalid tenantId. Redirecting to login.");
            response.sendRedirect(request.getContextPath() + "/login.jsp?error=" + encodeURL("Session expired or invalid."));
            return;
        }
        if (!isAdmin(request)) {
            pageLoadError = "Access Denied. Administrator privileges required.";
            logger.warning(logPrefix + pageLoadError);
        }
        
        boolean fromWizardOnGet = false;
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute("startSetupWizard"))) {
            String sessionWizardStep = (String) session.getAttribute("wizardStep");
            // If we are coming to configure this as part of the settings_setup phase
            if (WIZARD_RETURN_STEP_SETTINGS.equals(sessionWizardStep)){
                 fromWizardOnGet = true;
            }
             logger.info(logPrefix + "Wizard mode check: startSetupWizard=" + session.getAttribute("startSetupWizard") + ", sessionWizardStep='" + sessionWizardStep + "', fromWizardOnGet=" + fromWizardOnGet);
        } else {
            logger.info(logPrefix + "Not in wizard mode (session flags not set for this stage).");
        }
        
        // All SQLExceptions from loadAndForward are now declared and must be handled here or re-thrown
        try {
            loadAndForward(request, response, tenantId, pageLoadError, fromWizardOnGet);
        } catch (Exception e_all) {
            logger.log(Level.SEVERE, logPrefix + "Unexpected error during page load dispatch.", e_all);
            request.setAttribute("pageLoadErrorMessage", "An unexpected error occurred: " + e_all.getMessage());
            request.setAttribute("pageIsInWizardMode", fromWizardOnGet);
            request.setAttribute("wizardReturnStepForJSP", fromWizardOnGet ? WIZARD_RETURN_STEP_SETTINGS : null);
            request.getRequestDispatcher("/configureNetworkRestrictions.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        Integer tenantId = getTenantId(request);
        String action = request.getParameter("action");
        String logPrefix = "[NetworkRestrictionServlet doPost T:" + (tenantId != null ? tenantId : "null") + " A:" + action + "] ";
        logger.info(logPrefix + "Called.");

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        PrintWriter out = response.getWriter();
        JSONObject jsonResponse = new JSONObject();

        if (tenantId == null) { sendJsonResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "error", "Session expired."); return; }
        if (!isAdmin(request)) { sendJsonResponse(response, HttpServletResponse.SC_FORBIDDEN, "error", "Access Denied."); return; }

        String networkName = request.getParameter("networkName");
        String cidr = request.getParameter("cidr");
        String description = request.getParameter("description");
        boolean isEnabled = "true".equalsIgnoreCase(request.getParameter("isEnabled")) || "on".equalsIgnoreCase(request.getParameter("isEnabled"));

        Connection conn = null;
        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            if ("addNetwork".equals(action) || "editNetwork".equals(action)) {
                if (networkName == null || networkName.trim().isEmpty() || cidr == null || cidr.trim().isEmpty()) {
                    throw new IllegalArgumentException("Network Name and CIDR/IP are required.");
                }
                String normalizedCidr = IPAddressUtil.normalizeToCIDR(cidr.trim());
                if (!IPAddressUtil.isValidCIDROrIP(normalizedCidr)) {
                    throw new IllegalArgumentException("Invalid CIDR or IP address format: " + cidr);
                }

                if ("addNetwork".equals(action)) {
                    String sql = "INSERT INTO allowed_networks (TenantID, NetworkName, CIDR, Description, IsEnabled) VALUES (?, ?, ?, ?, ?)";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setInt(1, tenantId); pstmt.setString(2, networkName.trim()); pstmt.setString(3, normalizedCidr);
                        pstmt.setString(4, description != null ? description.trim() : null); pstmt.setBoolean(5, isEnabled);
                        pstmt.executeUpdate();
                        jsonResponse.put("status", "success").put("message", "Network '" + networkName.trim() + "' added successfully.");
                    }
                } else { // editNetwork
                    int networkId = Integer.parseInt(request.getParameter("networkID"));
                    String sql = "UPDATE allowed_networks SET NetworkName = ?, CIDR = ?, Description = ?, IsEnabled = ? WHERE NetworkID = ? AND TenantID = ?";
                    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                        pstmt.setString(1, networkName.trim()); pstmt.setString(2, normalizedCidr);
                        pstmt.setString(3, description != null ? description.trim() : null); pstmt.setBoolean(4, isEnabled);
                        pstmt.setInt(5, networkId); pstmt.setInt(6, tenantId);
                        if (pstmt.executeUpdate() > 0) {
                            jsonResponse.put("status", "success").put("message", "Network '" + networkName.trim() + "' updated.");
                        } else {
                            jsonResponse.put("status", "error").put("message", "Network not found or no changes made.");
                            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                        }
                    }
                }
            } else if ("deleteNetwork".equals(action)) {
                int networkId = Integer.parseInt(request.getParameter("networkID"));
                String sql = "DELETE FROM allowed_networks WHERE NetworkID = ? AND TenantID = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setInt(1, networkId); pstmt.setInt(2, tenantId);
                    if (pstmt.executeUpdate() > 0) jsonResponse.put("status", "success").put("message", "Network deleted.");
                    else { jsonResponse.put("status", "error").put("message", "Network not found."); response.setStatus(HttpServletResponse.SC_NOT_FOUND); }
                }
            } else if ("toggleNetworkStatus".equals(action)) {
                int networkId = Integer.parseInt(request.getParameter("networkID"));
                // isEnabled parameter is parsed at the top for all actions
                String sql = "UPDATE allowed_networks SET IsEnabled = ? WHERE NetworkID = ? AND TenantID = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setBoolean(1, isEnabled); pstmt.setInt(2, networkId); pstmt.setInt(3, tenantId);
                    if (pstmt.executeUpdate() > 0) jsonResponse.put("status", "success").put("message", "Network status updated.");
                    else { jsonResponse.put("status", "error").put("message", "Network not found."); response.setStatus(HttpServletResponse.SC_NOT_FOUND); }
                }
            } else {
                jsonResponse.put("status", "error").put("message", "Invalid action specified.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            conn.commit();
        } catch (IllegalArgumentException e_input) {
            logger.log(Level.WARNING, logPrefix + "Invalid input: " + e_input.getMessage(), e_input);
            try { if (conn != null) conn.rollback(); } catch (SQLException ex_rb) { /* log */ }
            jsonResponse.put("status", "error").put("message", "Invalid input: " + escapeJson(e_input.getMessage()));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } catch (SQLException e_sql) {
            logger.log(Level.SEVERE, logPrefix + "Database error: " + e_sql.getMessage(), e_sql);
            try { if (conn != null) conn.rollback(); } catch (SQLException ex_rb) { /* log */ }
            jsonResponse.put("status", "error").put("message", "Database error: " + escapeJson(e_sql.getMessage()));
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } catch (Exception e_gen) {
            logger.log(Level.SEVERE, logPrefix + "Unexpected error: " + e_gen.getMessage(), e_gen);
            try { if (conn != null) conn.rollback(); } catch (SQLException ex_rb) { /* log */ }
            jsonResponse.put("status", "error").put("message", "Unexpected server error: " + escapeJson(e_gen.getMessage()));
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (conn != null) { try { conn.setAutoCommit(true); conn.close(); } catch (SQLException e_close) { /* log */ } }
        }
        
        if (!response.isCommitted()) {
             out.print(jsonResponse.toString());
             out.flush();
        }
    }
    
    // Helper method to send JSON response and set status code
    private void sendJsonResponse(HttpServletResponse response, int statusCode, String statusType, String message) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);
        JSONObject json = new JSONObject();
        json.put("status", statusType);
        json.put("message", message); // Already escaped by JSONObject.quote if message comes from escapeJson
        response.getWriter().write(json.toString());
        response.getWriter().flush();
    }
}