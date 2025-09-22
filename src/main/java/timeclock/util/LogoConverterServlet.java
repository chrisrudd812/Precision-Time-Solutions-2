package timeclock.util;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Base64;

@WebServlet("/convert-logo")
public class LogoConverterServlet extends HttpServlet {
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        response.setContentType("text/html");
        PrintWriter out = response.getWriter();
        
        String logoPath = getServletContext().getRealPath("/Images/logo.png");
        File logoFile = new File(logoPath);
        
        out.println("<html><head><title>Logo Converter</title></head><body>");
        out.println("<h2>Logo to Base64 Converter</h2>");
        
        if (logoFile.exists()) {
            try (FileInputStream fis = new FileInputStream(logoFile)) {
                byte[] logoBytes = fis.readAllBytes();
                String base64Logo = Base64.getEncoder().encodeToString(logoBytes);
                
                out.println("<p>Logo converted successfully!</p>");
                out.println("<p><strong>Copy this base64 string:</strong></p>");
                out.println("<textarea style='width:100%;height:200px;font-family:monospace;'>" + base64Logo + "</textarea>");
                out.println("<br><br><button onclick='navigator.clipboard.writeText(document.querySelector(\"textarea\").value)'>Copy to Clipboard</button>");
            } catch (Exception e) {
                out.println("<p style='color:red;'>Error: " + e.getMessage() + "</p>");
            }
        } else {
            out.println("<p style='color:red;'>Logo file not found at: " + logoPath + "</p>");
        }
        
        out.println("</body></html>");
    }
}