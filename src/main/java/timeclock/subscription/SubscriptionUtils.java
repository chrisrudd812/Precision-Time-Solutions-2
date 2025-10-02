package timeclock.subscription;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import timeclock.db.DatabaseConnection;

/**
 * Utility class for subscription-related operations.
 */
public class SubscriptionUtils {
    
    private static final Logger logger = Logger.getLogger(SubscriptionUtils.class.getName());
    
    /**
     * Check if a tenant has the Pro plan (100 max users).
     * Pro plan users get state-based overtime calculations.
     */
    public static boolean hasProPlan(int tenantId) {
        String sql = "SELECT MaxUsers FROM tenants WHERE TenantID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int maxUsers = rs.getInt("MaxUsers");
                    // Pro plan has 100 max users
                    return maxUsers == 100;
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error checking Pro plan status for TenantID: " + tenantId, e);
        }
        
        return false; // Default to false if unable to determine
    }
    
    /**
     * Get the plan name for a tenant.
     */
    public static String getPlanName(int tenantId) {
        String sql = "SELECT sp.planName FROM tenants t " +
                     "JOIN subscription_plans sp ON t.StripePriceID = sp.stripePriceId " +
                     "WHERE t.TenantID = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, tenantId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("planName");
                }
            }
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Error getting plan name for TenantID: " + tenantId, e);
        }
        
        return "Unknown";
    }
}