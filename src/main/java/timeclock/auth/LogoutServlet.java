package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

@WebServlet("/LogoutServlet")
public class LogoutServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(LogoutServlet.class.getName());

    private void handleLogout(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        HttpSession session = request.getSession(false); 
        String autoLogoutFlag = request.getParameter("autoLogout");
        String autoLogoutReason = request.getParameter("reason");



        if (session != null) {
            session.invalidate(); 

        } else {

        }

        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate"); 
        response.setHeader("Pragma", "no-cache"); 
        response.setDateHeader("Expires", 0); 

        String loginPageUrl = request.getContextPath() + "/login.jsp";
        
        if (!"true".equalsIgnoreCase(autoLogoutFlag)) {
            String successMessage = URLEncoder.encode("You have been logged out successfully.", StandardCharsets.UTF_8.name());
            loginPageUrl += "?message=" + successMessage + "&msgType=logout";
        } else if (autoLogoutReason != null && !autoLogoutReason.isEmpty()){
            // MODIFIED: Changed parameter name from autoLogoutMessage to "reason" for consistency with JS
            loginPageUrl += "?reason=" + URLEncoder.encode(autoLogoutReason, StandardCharsets.UTF_8.name());
        }
        

        response.sendRedirect(loginPageUrl);
    }

    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException { handleLogout(request, response); }
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException { handleLogout(request, response); }
}