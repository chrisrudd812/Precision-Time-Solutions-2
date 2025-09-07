/*
 * package timeclock.util; // Or replace with your chosen package name
 * 
 * import jakarta.servlet.ServletContextEvent; import
 * jakarta.servlet.ServletContextListener; import
 * jakarta.servlet.annotation.WebListener; import timeclock.db.DBInitializer; //
 * Optional: Import database shutdown utilities or connection pool managers if
 * used // import timeclock.db.DatabaseShutdownUtil; // Example if you create
 * this
 * 
 *//**
	 * Application Lifecycle Listener implementation class AppLifecycleListener.
	 * This listener runs once when the web application starts and stops. It checks
	 * for the Derby sequence preallocator system property and triggers the database
	 * initialization logic.
	 */
/*
 * @WebListener // This annotation registers the listener automatically with the
 * server public class AppLifecycleListener implements ServletContextListener {
 * 
 *//**
	 * Default constructor.
	 */
/*
 * public AppLifecycleListener() { // Constructor - nothing special needed here
 * }
 * 
 *//**
	 * @see ServletContextListener#contextInitialized(ServletContextEvent) This
	 *      method is called by the servlet container when the application starts
	 *      up.
	 */
/*
 * @Override public void contextInitialized(ServletContextEvent sce) { // Log
 * messages to server console
 * System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++");
 * System.out.println("++ TimeClock Application Starting Up...           ++");
 * System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++");
 * 
 * // --- Verify Derby Sequence Preallocator Setting --- // This section CHECKS
 * if the property was set when the server (JVM) started. // It does NOT set the
 * property itself - that must be done externally (e.g., JAVA_OPTS).
 * System.out.println("---- Verifying Derby Sequence Preallocator Setting ----"
 * ); String preallocator = null; try { // Attempt to read the system property
 * preallocator = System.getProperty("derby.language.sequence.preallocator");
 * 
 * if (preallocator != null) { // Property was found System.out.
 * println("    System Property 'derby.language.sequence.preallocator' = " +
 * preallocator); if (!"1".equals(preallocator)) { System.out.
 * println("    WARNING: Property is set, but not to '1'. Default caching might still apply."
 * ); } } else { // Property was not found System.out.
 * println("    System Property 'derby.language.sequence.preallocator' is NOT SET."
 * ); System.out.
 * println("    WARNING: Derby will use default sequence caching (potential gaps on restart)."
 * ); } } catch (SecurityException e) { // Handle case where reading system
 * properties is restricted
 * System.err.println("    SecurityException trying to read system property: " +
 * e.getMessage()); System.out.
 * println("    Unable to verify 'derby.language.sequence.preallocator'."); }
 * System.out.println("----------------------------------------------------");
 * 
 * 
 * // --- Initialize Database --- // Now, call your existing database
 * initializer class. try { System.out.
 * println("Attempting database initialization check (DBInitializer.initialize())..."
 * ); // This will run your logic to check/create schema and tables. // If you
 * followed previous advice, it might also add sample data // ONLY if all tables
 * exist and employee_data is empty. DBInitializer.initialize();
 * System.out.println("Database initialization check completed successfully.");
 * 
 * // Optional: Initialize connection pool here if you implement one later //
 * ConnectionPoolManager.initialize();
 * 
 * } catch (Exception e) { // Log errors prominently. Using a real logger
 * (Log4j, SLF4j) is better practice.
 * System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
 * System.err.println("!! FATAL ERROR DURING DATABASE INITIALIZATION     !!");
 * System.err.println("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
 * e.printStackTrace(); // Print stack trace to console // You might want to
 * prevent the application from fully starting // by throwing a RuntimeException
 * here if the DB is critical. // throw new
 * RuntimeException("Database initialization failed, application cannot start.",
 * e); }
 * 
 * System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++");
 * System.out.println("++ TimeClock Application Startup Sequence Complete  ++");
 * System.out.println("++++++++++++++++++++++++++++++++++++++++++++++++++++"); }
 * 
 *//**
	 * @see ServletContextListener#contextDestroyed(ServletContextEvent) This method
	 *      is called by the servlet container when the application is shutting
	 *      down.
	 *//*
		 * @Override public void contextDestroyed(ServletContextEvent sce) {
		 * System.out.println("----------------------------------------------------");
		 * System.out.println("-- TimeClock Application Shutting Down...         --");
		 * System.out.println("----------------------------------------------------");
		 * 
		 * // Add cleanup code here if necessary: // 1. Shutdown your connection pool if
		 * you have one. // ConnectionPoolManager.shutdown();
		 * 
		 * // 2. Explicitly shut down embedded Derby. // This is often required for
		 * embedded databases to release file locks // and ensure data is fully written.
		 * You'd need a separate utility class // or method for this, which typically
		 * involves connecting with a special URL. // Example (requires you to create
		 * DatabaseShutdownUtil): // DatabaseShutdownUtil.shutdownDerby();
		 * 
		 * System.out.println("----------------------------------------------------");
		 * System.out.println("-- TimeClock Application Shutdown Complete        --");
		 * System.out.println("----------------------------------------------------"); }
		 * }
		 */