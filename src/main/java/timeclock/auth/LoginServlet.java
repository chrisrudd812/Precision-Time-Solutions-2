package timeclock.auth;

import com.stripe.model.Subscription;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.mindrot.jbcrypt.BCrypt;
import timeclock.db.DatabaseConnection;
import timeclock.util.Helpers; // Import the new helpers class

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@WebServlet("/LoginServlet")
public class LoginServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(LoginServlet.class.getName());

	protected void doPost(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		String companyIdentifier = request.getParameter("companyIdentifier");
		String email = request.getParameter("email");
		String password = request.getParameter("password");
		HttpSession session = request.getSession(true);

		logger.info("--- LOGIN ATTEMPT INITIATED ---");
		logger.info("[DEBUG] 1. Received Company ID from form: '" + companyIdentifier + "'");
		logger.info("[DEBUG] 2. Received Email from form: '" + email + "'");
		logger.info("[DEBUG] 3. Received PIN/Password from form: '" + password + "'");

		// MODIFIED: Replaced ShowPunches.isValid with Helpers.isStringValid
		if (!Helpers.isStringValid(companyIdentifier) || !Helpers.isStringValid(email) || !Helpers.isStringValid(password)) {
			response.sendRedirect("login.jsp?error="
					+ URLEncoder.encode("Company ID, email, and PIN are required.", StandardCharsets.UTF_8));
			return;
		}

		try (Connection conn = DatabaseConnection.getConnection()) {
			String tenantSql = "SELECT TenantID FROM tenants WHERE CompanyIdentifier = ?";
			PreparedStatement psTenant = conn.prepareStatement(tenantSql);
			psTenant.setString(1, companyIdentifier.trim());
			ResultSet rsTenant = psTenant.executeQuery();

			if (!rsTenant.next()) {
				logger.warning("[DEBUG] 4. TENANT LOOKUP FAILED. No tenant found for Company ID: '"
						+ companyIdentifier.trim() + "'");
				response.sendRedirect(
						"login.jsp?error=" + URLEncoder.encode("Invalid Company ID.", StandardCharsets.UTF_8));
				return;
			}
			int tenantId = rsTenant.getInt("TenantID");
			logger.info("[DEBUG] 4. TENANT LOOKUP SUCCESS. Found TenantID: " + tenantId);

			String userSql = "SELECT EID, PasswordHash, PERMISSIONS, FIRST_NAME, LAST_NAME, ACTIVE, RequiresPasswordChange FROM employee_data WHERE LOWER(EMAIL) = LOWER(?) AND TenantID = ?";
			PreparedStatement psUser = conn.prepareStatement(userSql);
			psUser.setString(1, email.trim().toLowerCase());
			psUser.setInt(2, tenantId);
			ResultSet rsUser = psUser.executeQuery();

			if (rsUser.next()) {
				logger.info("[DEBUG] 5. EMPLOYEE LOOKUP SUCCESS. Found user record for email: '"
						+ email.trim().toLowerCase() + "'");
				String storedHash = rsUser.getString("PasswordHash");
				logger.info("[DEBUG] 6. Stored PasswordHash from DB: '" + storedHash + "'");

				boolean passwordMatch = (storedHash != null) && BCrypt.checkpw(password, storedHash);
				logger.info("[DEBUG] 7. Result of BCrypt.checkpw(): " + passwordMatch);

				if (passwordMatch) {
					logger.info("--- LOGIN SUCCESSFUL ---");

					if (!rsUser.getBoolean("ACTIVE")) {
						response.sendRedirect(
								"login.jsp?error=" + URLEncoder.encode("Account is inactive.", StandardCharsets.UTF_8));
						return;
					}

					int eid = rsUser.getInt("EID");
					String userPermissions = rsUser.getString("PERMISSIONS");
					
					logger.info("[DEBUG] 8. SETTING SESSION ATTRIBUTES:");
					logger.info("[DEBUG] - Session ID: " + session.getId());
					logger.info("[DEBUG] - User-Agent: " + request.getHeader("User-Agent"));
					logger.info("[DEBUG] - Remote Address: " + request.getRemoteAddr());
					
					session.setAttribute("EID", eid);
					session.setAttribute("UserFirstName", rsUser.getString("FIRST_NAME"));
					session.setAttribute("UserLastName", rsUser.getString("LAST_NAME"));
					session.setAttribute("Permissions", userPermissions);
					session.setAttribute("TenantID", tenantId);
					session.setAttribute("CompanyIdentifier", companyIdentifier.trim());
					session.setAttribute("Email", email.trim().toLowerCase());
					
					logger.info("[DEBUG] 9. SESSION ATTRIBUTES SET:");
					logger.info("[DEBUG] - EID: " + eid);
					logger.info("[DEBUG] - Permissions: '" + userPermissions + "'");
					logger.info("[DEBUG] - TenantID: " + tenantId);
					logger.info("[DEBUG] - CompanyIdentifier: '" + companyIdentifier.trim() + "'");
					
                    // NEW: Determine if location checks are needed BEFORE redirecting
                    boolean locationCheckIsRequired = Helpers.isLocationCheckRequired(tenantId);
                    session.setAttribute("locationCheckIsRequired", locationCheckIsRequired);
                    logger.info("[Performance] Location check required for this session: " + locationCheckIsRequired);

					if ("Administrator".equalsIgnoreCase(userPermissions)) {
						session.setMaxInactiveInterval(4 * 60 * 60);
						logger.info("[DEBUG] 10. Session timeout set to 4 hours for Administrator");
					} else {
						session.setMaxInactiveInterval(30);
						logger.info("[DEBUG] 10. Session timeout set to 30 seconds for Employee");
					}

					String subscriptionStatus = syncSubscriptionStatus(conn, tenantId);
					session.setAttribute("SubscriptionStatus", subscriptionStatus);
                    checkForAndClearLoginMessages(conn, eid, session);

					if ("Administrator".equalsIgnoreCase(userPermissions)
							&& ("canceled".equalsIgnoreCase(subscriptionStatus)
									|| "unpaid".equalsIgnoreCase(subscriptionStatus)
									|| "past_due".equalsIgnoreCase(subscriptionStatus))) {
						session.setAttribute("errorMessage",
								"Your subscription is inactive. Please update your billing information to restore access.");
						response.sendRedirect("account.jsp");
						return;
					}

					if (rsUser.getBoolean("RequiresPasswordChange")) {
						session.setAttribute("pinChangeRequired", true);
						logger.info("[DEBUG] 11. REDIRECTING to change_password.jsp (password change required)");
						response.sendRedirect("change_password.jsp");
					} else {
						session.removeAttribute("startSetupWizard");
						String targetPage = "Administrator".equalsIgnoreCase(userPermissions) ? "employees.jsp"
								: "timeclock.jsp";
						logger.info("[DEBUG] 11. REDIRECTING to: " + targetPage);
						response.sendRedirect(targetPage);
					}
				} else {
					logger.warning("[DEBUG] 8. PASSWORD MISMATCH. BCrypt check failed.");
					response.sendRedirect(
							"login.jsp?error=" + URLEncoder.encode("Invalid credentials.", StandardCharsets.UTF_8));
				}
			} else {
				logger.warning("[DEBUG] 5. EMPLOYEE LOOKUP FAILED. No user found for email: '"
						+ email.trim().toLowerCase() + "' in TenantID " + tenantId);
				response.sendRedirect(
						"login.jsp?error=" + URLEncoder.encode("Invalid credentials.", StandardCharsets.UTF_8));
			}

		} catch (Exception e) {
			logger.log(Level.SEVERE, "[DEBUG] CRITICAL ERROR in login process.", e);
			response.sendRedirect("login.jsp?error="
					+ URLEncoder.encode("A server error occurred. Please try again.", StandardCharsets.UTF_8));
		}
	}
	
    // The rest of the methods in this class are unchanged...
    private void checkForAndClearLoginMessages(Connection conn, int eid, HttpSession session) {
        List<Map<String, String>> messages = new ArrayList<>();
        List<Integer> messageIdsToDelete = new ArrayList<>();
        String selectSql = "SELECT MessageID, Subject, Body FROM login_messages WHERE RecipientEID = ? ORDER BY CreatedAt ASC";

        try {
            conn.setAutoCommit(false); 
            try (PreparedStatement psSelect = conn.prepareStatement(selectSql)) {
                psSelect.setInt(1, eid);
                try (ResultSet rs = psSelect.executeQuery()) {
                    while (rs.next()) {
                        Map<String, String> message = new HashMap<>();
                        message.put("subject", rs.getString("Subject"));
                        message.put("body", rs.getString("Body"));
                        messages.add(message);
                        messageIdsToDelete.add(rs.getInt("MessageID"));
                    }
                }
            }
            if (!messageIdsToDelete.isEmpty()) {
                logger.info("Found " + messages.size() + " login messages for EID: " + eid + ". Deleting them now.");
                String deleteSql = "DELETE FROM login_messages WHERE MessageID IN (" +
                                   messageIdsToDelete.stream().map(String::valueOf).collect(Collectors.joining(",")) + ")";
                try (PreparedStatement psDelete = conn.prepareStatement(deleteSql)) {
                    psDelete.executeUpdate();
                }
                session.setAttribute("unreadLoginMessages", messages);
            }
            conn.commit();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Error fetching or deleting login messages for EID " + eid, e);
            try { conn.rollback(); } catch (SQLException ex) { logger.log(Level.SEVERE, "Failed to rollback login message transaction", ex); }
        } finally {
             try { conn.setAutoCommit(true); } catch (SQLException e) { logger.log(Level.SEVERE, "Failed to restore autocommit state", e); }
        }
    }

	private String syncSubscriptionStatus(Connection conn, int tenantId) {
		String currentDbStatus = "error";
		try {
			String sqlInfo = "SELECT StripeSubscriptionID, SubscriptionStatus, StripePriceID FROM tenants WHERE TenantID = ?";
			String stripeSubscriptionId = null;
			String currentDbPriceId = null;
			try (PreparedStatement pstmtInfo = conn.prepareStatement(sqlInfo)) {
				pstmtInfo.setInt(1, tenantId);
				ResultSet rsTenant = pstmtInfo.executeQuery();
				if (rsTenant.next()) {
					stripeSubscriptionId = rsTenant.getString("StripeSubscriptionID");
					currentDbStatus = rsTenant.getString("SubscriptionStatus");
					currentDbPriceId = rsTenant.getString("StripePriceID");
				} else { throw new SQLException("Tenant not found for ID: " + tenantId); }
			}
			if (stripeSubscriptionId == null || stripeSubscriptionId.trim().isEmpty()) return currentDbStatus;
			Subscription stripeSubscription = Subscription.retrieve(stripeSubscriptionId);
			String newStripeStatus = stripeSubscription.getStatus();
			String newPriceId = stripeSubscription.getItems().getData().get(0).getPrice().getId();
			boolean needsUpdate = !newStripeStatus.equalsIgnoreCase(currentDbStatus)
					|| (currentDbPriceId != null && !newPriceId.equals(currentDbPriceId));
			if (needsUpdate) {
				Timestamp newPeriodEnd = new Timestamp(stripeSubscription.getCurrentPeriodEnd() * 1000L);
				int newMaxUsers = getMaxUsersForPriceId(conn, newPriceId);
				String sqlUpdate = "UPDATE tenants SET SubscriptionStatus = ?, StripePriceID = ?, CurrentPeriodEnd = ?, MaxUsers = ? WHERE TenantID = ?";
				try (PreparedStatement pstmtUpdate = conn.prepareStatement(sqlUpdate)) {
					pstmtUpdate.setString(1, newStripeStatus);
					pstmtUpdate.setString(2, newPriceId);
					pstmtUpdate.setTimestamp(3, newPeriodEnd);
					pstmtUpdate.setInt(4, newMaxUsers);
					pstmtUpdate.setInt(5, tenantId);
					pstmtUpdate.executeUpdate();
				}
				return newStripeStatus;
			}
			return currentDbStatus;
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Could not sync subscription status for TenantID " + tenantId, e);
			return currentDbStatus;
		}
	}

	private int getMaxUsersForPriceId(Connection conn, String priceId) throws SQLException {
		String sql = "SELECT maxUsers FROM subscription_plans WHERE stripePriceId = ?";
		try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setString(1, priceId);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) return rs.getInt("maxUsers");
			}
		}
		return 25; // Fallback default
	}
}