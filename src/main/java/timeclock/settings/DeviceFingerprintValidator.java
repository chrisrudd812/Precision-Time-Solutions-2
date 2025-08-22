package timeclock.settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import timeclock.Configuration;

public class DeviceFingerprintValidator {
    private static final Logger logger = Logger.getLogger(DeviceFingerprintValidator.class.getName());

    public static class ValidationResult {
        public final boolean isAllowed;
        public final String message;
        public ValidationResult(boolean isAllowed, String message) {
            this.isAllowed = isAllowed;
            this.message = message;
        }
    }

    public ValidationResult validateDevice(Connection conn, int tenantId, int employeeId, String fingerprintHash, String userAgent) throws SQLException {
        boolean isRestrictionEnabled = "true".equalsIgnoreCase(Configuration.getProperty(tenantId, "RestrictByDevice", "false"));
        if (!isRestrictionEnabled) {
            return new ValidationResult(true, "Device restriction is not enabled.");
        }

        if (fingerprintHash == null || fingerprintHash.trim().isEmpty()) {
            return new ValidationResult(false, "Device ID could not be determined. Punch rejected.");
        }

        int maxDevices = Integer.parseInt(Configuration.getProperty(tenantId, "MaxDevicesPerUserGlobal", "2"));
        List<String> registeredDeviceDescriptions = new ArrayList<>();
        
        String getDevicesSql = "SELECT DeviceFingerprintHash, IsEnabled, DeviceDescription FROM employee_devices WHERE TenantID = ? AND EID = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(getDevicesSql)) {
            pstmt.setInt(1, tenantId);
            pstmt.setInt(2, employeeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    String hash = rs.getString("DeviceFingerprintHash").trim();
                    boolean isEnabled = rs.getBoolean("IsEnabled");
                    String description = rs.getString("DeviceDescription");

                    if (hash.equals(fingerprintHash)) {
                        if (isEnabled) {
                            updateLastUsedDate(conn, tenantId, fingerprintHash);
                            return new ValidationResult(true, "Known device verified.");
                        } else {
                            return new ValidationResult(false, "This device has been disabled by an administrator.");
                        }
                    }
                    if (isEnabled) {
                        registeredDeviceDescriptions.add(description != null ? description : "Unnamed Device");
                    }
                }
            }
        }

        if (registeredDeviceDescriptions.size() >= maxDevices) {
            StringBuilder errorMessage = new StringBuilder("Device limit of " + maxDevices + " has been reached.");
            if (!registeredDeviceDescriptions.isEmpty()) {
                errorMessage.append("\n\nYour previously approved devices are:\n");
                errorMessage.append(String.join("\n", registeredDeviceDescriptions));
            }
            return new ValidationResult(false, errorMessage.toString());
        }

        String description = generateDeviceDescription(userAgent);
        String insertSql = "INSERT INTO employee_devices (TenantID, EID, DeviceFingerprintHash, DeviceDescription, UserAgentAtRegistration, RegisteredDate, LastUsedDate, IsEnabled) VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            Timestamp now = Timestamp.from(Instant.now());
            pstmt.setInt(1, tenantId);
            pstmt.setInt(2, employeeId);
            pstmt.setString(3, fingerprintHash);
            pstmt.setString(4, description);
            pstmt.setString(5, userAgent);
            pstmt.setTimestamp(6, now);
            pstmt.setTimestamp(7, now);
            pstmt.executeUpdate();
        }
        return new ValidationResult(true, "New device registered successfully.");
    }
    
    private String generateDeviceDescription(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) return "Web Browser";
        String ua = userAgent.toLowerCase();
        if (ua.contains("iphone")) return "iPhone";
        if (ua.contains("ipad")) return "iPad";
        if (ua.contains("android")) return "Android Device";
        if (ua.contains("windows")) return "Windows PC";
        if (ua.contains("macintosh")) return "Apple Mac";
        if (ua.contains("linux")) return "Linux PC";
        return "Web Browser";
    }

    private void updateLastUsedDate(Connection conn, int tenantId, String fingerprintHash) throws SQLException {
        String sql = "UPDATE employee_devices SET LastUsedDate = ? WHERE TenantID = ? AND DeviceFingerprintHash = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setTimestamp(1, Timestamp.from(Instant.now()));
            pstmt.setInt(2, tenantId);
            pstmt.setString(3, fingerprintHash);
            pstmt.executeUpdate();
        }
    }
}