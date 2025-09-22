package timeclock.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import timeclock.db.DBInitializer; // Import your DBInitializer class

@WebListener() // This annotation is VERY important
public class ApplicationListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // This method is called when the web application starts up
        DBInitializer.initialize();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // This method is called when the web application shuts down
        // Add cleanup code here (if needed)
    }
}