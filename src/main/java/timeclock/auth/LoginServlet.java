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

		// Regular validation for PIN login
		if (!Helpers.isStringValid(companyIdentifier) || !Helpers.isStringValid(email) || !Helpers.isStringValid(password)) {
			Helpers.secureRedirect(response, request, "login.jsp?error="
					+ URLEncoder.encode("Company ID, email, and PIN are required.", StandardCharsets.UTF_8));
			return;
		}

		try (Connection conn = DatabaseConnection.getConnection()) {
			String tenantSql = "SELECT TenantID FROM tenants WHERE CompanyIdentifier = ?";
			PreparedStatement psTenant = conn.prepareStatement(tenantSql);
			psTenant.setString(1, companyIdentifier.trim());
			ResultSet rsTenant = psTenant.executeQuery();

			if (!rsTenant.next()) {
				Helpers.secureRedirect(response, request,
						"login.jsp?error=" + URLEncoder.encode("Invalid Company ID.", StandardCharsets.UTF_8));
				return;
			}
			int tenantId = rsTenant.getInt("TenantID");

			String userSql = "SELECT EID, PasswordHash, PERMISSIONS, FIRST_NAME, LAST_NAME, ACTIVE, RequiresPasswordChange FROM employee_data WHERE LOWER(EMAIL) = LOWER(?) AND TenantID = ?";
			PreparedStatement psUser = conn.prepareStatement(userSql);
			psUser.setString(1, email.trim().toLowerCase());
			psUser.setInt(2, tenantId);
			ResultSet rsUser = psUser.executeQuery();

			if (rsUser.next()) {
				String storedHash = rsUser.getString("PasswordHash");
				boolean passwordMatch = (storedHash != null) && BCrypt.checkpw(password, storedHash);

				if (passwordMatch) {

					if (!rsUser.getBoolean("ACTIVE")) {
						Helpers.secureRedirect(response, request,
								"login.jsp?error=" + URLEncoder.encode("Account is inactive.", StandardCharsets.UTF_8));
						return;
					}

					int eid = rsUser.getInt("EID");
					String userPermissions = rsUser.getString("PERMISSIONS");
					
					session.setAttribute("EID", eid);
					session.setAttribute("UserFirstName", rsUser.getString("FIRST_NAME"));
					session.setAttribute("UserLastName", rsUser.getString("LAST_NAME"));
					session.setAttribute("Permissions", userPermissions);
					session.setAttribute("TenantID", tenantId);
					session.setAttribute("CompanyIdentifier", companyIdentifier.trim());
					session.setAttribute("Email", email.trim().toLowerCase());
					
                    // NEW: Determine if location checks are needed BEFORE redirecting
                    boolean locationCheckIsRequired = Helpers.isLocationCheckRequired(tenantId);
                    session.setAttribute("locationCheckIsRequired", locationCheckIsRequired);

					if ("Administrator".equalsIgnoreCase(userPermissions)) {
						session.setMaxInactiveInterval(4 * 60 * 60);
					} else {
						session.setMaxInactiveInterval(30);
					}

					String subscriptionStatus = syncSubscriptionStatus(conn, tenantId);
					session.setAttribute("SubscriptionStatus", subscriptionStatus);

					if ("Administrator".equalsIgnoreCase(userPermissions)
							&& ("canceled".equalsIgnoreCase(subscriptionStatus)
									|| "unpaid".equalsIgnoreCase(subscriptionStatus)
									|| "past_due".equalsIgnoreCase(subscriptionStatus))) {
						session.setAttribute("errorMessage",
								"Your subscription is inactive. Please update your billing information to restore access.");
						Helpers.secureRedirect(response, request, "account.jsp");
						return;
					}

					if (rsUser.getBoolean("RequiresPasswordChange")) {
						session.setAttribute("pinChangeRequired", true);
						Helpers.secureRedirect(response, request, "change_password.jsp");
					} else {
						session.removeAttribute("startSetupWizard");
						
						// Check if administrator and pay period has ended
						if ("Administrator".equalsIgnoreCase(userPermissions)) {
							boolean payPeriodEnded = isPayPeriodEnded(conn, tenantId);
							if (payPeriodEnded) {
								Helpers.secureRedirect(response, request, "employees.jsp?showPayrollModal=true");
							} else {
								Helpers.secureRedirect(response, request, "employees.jsp");
							}
						} else {
							Helpers.secureRedirect(response, request, "timeclock.jsp");
						}
					}
				} else {
					Helpers.secureRedirect(response, request,
							"login.jsp?error=" + URLEncoder.encode("Invalid credentials.", StandardCharsets.UTF_8));
				}
			} else {
				Helpers.secureRedirect(response, request,
						"login.jsp?error=" + URLEncoder.encode("Invalid credentials.", StandardCharsets.UTF_8));
			}

		} catch (Exception e) {
			logger.log(Level.SEVERE, "Error in login process.", e);
			Helpers.secureRedirect(response, request, "login.jsp?error="
					+ URLEncoder.encode("A server error occurred. Please try again.", StandardCharsets.UTF_8));
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
	
	private boolean isPayPeriodEnded(Connection conn, int tenantId) {
		try {
			String tzSql = "SELECT DefaultTimeZone FROM tenants WHERE TenantID = ?";
			String timezone = "America/New_York";
			try (PreparedStatement pstmt = conn.prepareStatement(tzSql)) {
				pstmt.setInt(1, tenantId);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next() && rs.getString("DefaultTimeZone") != null) {
						timezone = rs.getString("DefaultTimeZone");
					}
				}
			}
			
			String sql = "SELECT setting_value FROM settings WHERE TenantID = ? AND setting_key = 'PayPeriodEndDate'";
			try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
				pstmt.setInt(1, tenantId);
				try (ResultSet rs = pstmt.executeQuery()) {
					if (rs.next()) {
						String payPeriodEndStr = rs.getString("setting_value");
						if (payPeriodEndStr != null && !payPeriodEndStr.trim().isEmpty()) {
							java.time.LocalDate payPeriodEnd = java.time.LocalDate.parse(payPeriodEndStr.trim());
							java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of(timezone));
							logger.info("PAY PERIOD CHECK - Timezone: " + timezone + ", Today: " + today + ", PayPeriodEnd: " + payPeriodEnd + ", isAfter: " + today.isAfter(payPeriodEnd));
							return today.isAfter(payPeriodEnd);
						}
					}
				}
			}
		} catch (Exception e) {
			logger.log(Level.WARNING, "Error checking pay period end date for TenantID " + tenantId, e);
		}
		return false;
	}
}