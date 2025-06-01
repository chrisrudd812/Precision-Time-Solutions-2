package timeclock.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class IPAddressUtil {

    private static final Logger logger = Logger.getLogger(IPAddressUtil.class.getName());
    private static final Pattern IPV4_CIDR_REGEX = Pattern.compile(
        "^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})(?:/(\\d{1,2}))?$"
    );

    public static String getClientIpAddr(HttpServletRequest request) {
        // ***** START TEMPORARY TEST CODE *****
        String testIp = "10.0.0.114";
        logger.info("IPAddressUtil.getClientIpAddr is returning HARDCODED IP: " + testIp + " FOR TESTING");
        return testIp;
        // ***** END TEMPORARY TEST CODE *****

        /* --- ORIGINAL PRODUCTION LOGIC ---
        if (request == null) { return "unknown"; }
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress != null && !ipAddress.isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) {
            StringTokenizer tokenizer = new StringTokenizer(ipAddress, ",");
            if (tokenizer.hasMoreTokens()) { return tokenizer.nextToken().trim(); }
        }
        ipAddress = request.getHeader("X-Real-IP");
        if (ipAddress != null && !ipAddress.isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) { return ipAddress.trim(); }
        ipAddress = request.getHeader("Proxy-Client-IP");
        if (ipAddress != null && !ipAddress.isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) { return ipAddress.trim(); }
        ipAddress = request.getHeader("WL-Proxy-Client-IP");
        if (ipAddress != null && !ipAddress.isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) { return ipAddress.trim(); }
        ipAddress = request.getHeader("HTTP_X_FORWARDED_FOR");
        if (ipAddress != null && !ipAddress.isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) {
             StringTokenizer tokenizer = new StringTokenizer(ipAddress, ",");
            if (tokenizer.hasMoreTokens()) { return tokenizer.nextToken().trim(); }
        }
        ipAddress = request.getHeader("HTTP_CLIENT_IP");
        if (ipAddress != null && !ipAddress.isEmpty() && !"unknown".equalsIgnoreCase(ipAddress)) { return ipAddress.trim(); }
        return request.getRemoteAddr();
        --- END ORIGINAL PRODUCTION LOGIC --- */
    }

    public static boolean isValidCIDROrIP(String cidrOrIp) {
        if (cidrOrIp == null || cidrOrIp.trim().isEmpty()) { return false; }
        return IPV4_CIDR_REGEX.matcher(cidrOrIp.trim()).matches();
    }

    public static String normalizeToCIDR(String ipOrCidr) {
        if (ipOrCidr == null) { return null; }
        String trimmed = ipOrCidr.trim();
        if (trimmed.contains("/")) { return trimmed; }
        if (isValidCIDROrIP(trimmed)) { return trimmed + "/32"; }
        return trimmed;
    }

    public static boolean isIpInCidr(String clientIpStr, String cidrRuleStr) {
        logger.info("isIpInCidr: Checking client IP '" + clientIpStr + "' against rule '" + cidrRuleStr + "'");
        if (clientIpStr == null || cidrRuleStr == null) {
            logger.warning("isIpInCidr: Null input provided. ClientIP: " + clientIpStr + ", Rule: " + cidrRuleStr);
            return false;
        }

        clientIpStr = clientIpStr.trim();
        cidrRuleStr = cidrRuleStr.trim();

        if (!isValidCIDROrIP(clientIpStr)) {
            logger.warning("isIpInCidr: Invalid client IP format: " + clientIpStr);
            return false;
        }
        String normalizedCidrRule = normalizeToCIDR(cidrRuleStr);
        if (!isValidCIDROrIP(normalizedCidrRule)) {
            logger.warning("isIpInCidr: Invalid CIDR rule format (after normalization): " + normalizedCidrRule + " from original: " + cidrRuleStr);
            return false;
        }

        try {
            String[] cidrParts = normalizedCidrRule.split("/");
            String cidrNetworkAddressStr = cidrParts[0];
            int prefixLength = Integer.parseInt(cidrParts[1]);

            if (prefixLength < 0 || prefixLength > 32) {
                logger.warning("isIpInCidr: Invalid prefix length " + prefixLength + " in rule: " + normalizedCidrRule);
                return false;
            }

            InetAddress clientInetAddress = InetAddress.getByName(clientIpStr);
            InetAddress cidrNetworkInetAddress = InetAddress.getByName(cidrNetworkAddressStr);

            if (clientInetAddress.getAddress().length != 4 || cidrNetworkInetAddress.getAddress().length != 4) {
                logger.info("isIpInCidr: IPv4 check only. Client: " + clientIpStr + " or Rule: " + normalizedCidrRule + " is not IPv4.");
                return false;
            }

            if (prefixLength == 32) {
                boolean match = clientInetAddress.equals(cidrNetworkInetAddress);
                logger.info("isIpInCidr (/32): Result for '" + clientIpStr + "' in '" + normalizedCidrRule + "': " + match);
                return match;
            }

            long clientIpLong = ipToLong(clientInetAddress);
            long networkAddressLong = ipToLong(cidrNetworkInetAddress);
            long mask = (-1L) << (32 - prefixLength);

            boolean match = (clientIpLong & mask) == (networkAddressLong & mask);
            logger.info("isIpInCidr (/" + prefixLength + "): Result for '" + clientIpStr + "' in '" + normalizedCidrRule + "': " + match +
                         " (ClientLong&Mask: " + (clientIpLong & mask) + ", NetAddrLong&Mask: " + (networkAddressLong & mask) + ")");
            return match;

        } catch (UnknownHostException e) {
            logger.log(Level.WARNING, "isIpInCidr: Unknown host for client='" + clientIpStr + "' or rule='" + normalizedCidrRule + "'", e);
        } catch (NumberFormatException e) {
            logger.log(Level.WARNING, "isIpInCidr: Error parsing prefix from " + normalizedCidrRule, e);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "isIpInCidr: Unexpected error for client='" + clientIpStr + "', rule='" + normalizedCidrRule + "'", e);
        }
        logger.warning("isIpInCidr: Defaulting to false due to an error or no match for client '" + clientIpStr + "' in rule '" + cidrRuleStr + "'");
        return false;
    }

    private static long ipToLong(InetAddress ip) {
        byte[] octets = ip.getAddress();
        long result = 0;
        if (octets.length == 4) {
            for (byte octet : octets) {
                result <<= 8;
                result |= octet & 0xffL;
            }
        }
        return result;
    }
}