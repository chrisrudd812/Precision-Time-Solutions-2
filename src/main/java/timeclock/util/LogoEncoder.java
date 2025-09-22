package timeclock.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LogoEncoder {
    private static final Logger logger = Logger.getLogger(LogoEncoder.class.getName());
    private static String cachedLogoBase64 = null;
    
    public static String getLogoBase64() {
        if (cachedLogoBase64 != null) {
            return cachedLogoBase64;
        }
        
        try {
            InputStream logoStream = LogoEncoder.class.getResourceAsStream("/logo.png");
            if (logoStream == null) {
                // Try webapp path
                String webappPath = System.getProperty("catalina.base") + "/webapps/ROOT/Images/logo.png";
                java.io.File logoFile = new java.io.File(webappPath);
                if (logoFile.exists()) {
                    logoStream = new java.io.FileInputStream(logoFile);
                }
            }
            
            if (logoStream != null) {
                byte[] logoBytes = logoStream.readAllBytes();
                cachedLogoBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(logoBytes);
                logoStream.close();
                logger.info("Logo successfully encoded to base64");
                return cachedLogoBase64;
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Could not load logo for email", e);
        }
        
        // Fallback: transparent 1x1 pixel
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==";
    }
}