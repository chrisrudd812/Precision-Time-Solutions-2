package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;
import timeclock.Configuration; // Assuming this is your class for app config

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.UUID;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mindrot.jbcrypt.BCrypt;
import org.json.JSONObject;

// PayPal SDK Imports (Ensure these are in your project's classpath)
import com.paypal.core.PayPalEnvironment;
import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.http.exceptions.HttpException; // For PayPal HTTP errors
// Remove if not directly using Order objects, but token might be an Order ID
// import com.paypal.orders.Order; 
// import com.paypal.orders.OrdersGetRequest; 
import com.paypal.subscriptions.Subscription; // Main object for subscription response
import com.paypal.subscriptions.SubscriptionRequest; // To build the request
import com.paypal.subscriptions.SubscriptionsCreateRequest; // The actual request object
import com.paypal.subscriptions.PaymentSource;
import com.paypal.subscriptions.Token; // For tokenized payment source
import com.paypal.subscriptions.Subscriber; // For payer details
import com.paypal.subscriptions.Name as PayPalName; // Alias to avoid conflict if you have a Name class
import com.paypal.subscriptions.AddressPortable;
import com.paypal.subscriptions.ApplicationContext; // Optional for return URLs etc.


@WebServlet("/SignupServlet")
public class SignupServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SignupServlet.class.getName());

    // === PayPal Configuration ===
    // Client ID is from your previous input
    private static final String PAYPAL_CLIENT_ID = "AVvNXTc0v0M4aRIwgNKSEtQFmTc06PnQLHtQxBeIw0hUAi-jwDO-rSdMJB_KxVp05bvrdfCnnXJR7rTC";
    
    //vvvvv --- YOU MUST REPLACE THESE TWO PLACEHOLDERS --- vvvvv
    private static final String PAYPAL_CLIENT_SECRET = "YOUR_PAYPAL_SANDBOX_SECRET_KEY_HERE"; 
    private static final String PAYPAL_SUBSCRIPTION_PLAN_ID = "P-YOUR_ACTUAL_PAYPAL_PLAN_ID_HERE"; 
    //^^^^^ --- YOU MUST REPLACE THESE TWO PLACEHOLDERS --- ^^^^^

    private static PayPalHttpClient payPalHttpClientInstance;

    static {
        // Initialize PayPal Client (Choose Sandbox or Live)
        if (PAYPAL_CLIENT_ID != null && !PAYPAL_CLIENT_ID.startsWith("AVvNXT") && // Basic check, improve if needed
            PAYPAL_CLIENT_SECRET != null && !PAYPAL_CLIENT_SECRET.equals("YOUR_PAYPAL_SANDBOX_SECRET_KEY_HERE") && !PAYPAL_CLIENT_SECRET.trim().isEmpty()) {
            
            PayPalEnvironment environment = new PayPalEnvironment.Sandbox(PAYPAL_CLIENT_ID, PAYPAL_CLIENT_SECRET);
            payPalHttpClientInstance = new PayPalHttpClient(environment);
            logger.info("[SignupServlet] PayPal HTTP Client initialized for Sandbox.");
        } else {
            logger.severe("[SignupServlet] CRITICAL: PayPal Client ID or Secret is missing or still a placeholder. PayPal client NOT initialized.");
        }

        if (PAYPAL_SUBSCRIPTION_PLAN_ID == null || PAYPAL_SUBSCRIPTION_PLAN_ID.trim().isEmpty() || "P-YOUR_ACTUAL_PAYPAL_PLAN_ID_HERE".equals(PAYPAL_SUBSCRIPTION_PLAN_ID)) {
            logger.severe("CRITICAL: PayPal Subscription Plan ID IS NOT CONFIGURED or is a placeholder: " + PAYPAL_SUBSCRIPTION_PLAN_ID);
        } else {
            logger.info("[SignupServlet] PayPal Subscription Plan ID configured: " + PAYPAL_SUBSCRIPTION_PLAN_ID);
        }
    }

    private String escapeHtml(String input) { 
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JSONObject jsonResponse = new JSONObject(); 
        String action = request.getParameter("action");
        logger.info("[SignupServlet] doPost received action: " + action);

        try (PrintWriter out = response.getWriter()) { 
            if ("registerCompanyAdmin".equals(action)) {
                // Renamed the method to be clear it's now for PayPal
                registerCompanyAndAdminWithPayPal(request, response, jsonResponse);
            } else {
                logger.warning("[SignupServlet] Invalid action specified: " + action);
                jsonResponse.put("success", false).put("error", "Invalid action specified.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            out.print(jsonResponse.toString());
        } catch (Exception e) { 
            logger.log(Level.SEVERE, "[SignupServlet] Critical error processing POST request for action " + action, e);
            if (!response.isCommitted()) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                JSONObject errorJson = new JSONObject();
                errorJson.put("success", false).put("error", "A critical server error occurred processing your request.");
                // Avoid nested try-with-resources for writer if outer one is still in scope and might fail
                try {
                    response.getWriter().print(errorJson.toString());
                } catch (IOException ioex) {
                     logger.log(Level.SEVERE, "[SignupServlet] Failed to write critical error response.", ioex);
                }
            }
        }
    }
    
    // --- Database Helper methods (generateCompanyIdentifierFromName, generateUUIDIdentifier, getNextTenantEmployeeNumber) ---
    // These are assumed to be the same as in your original servlet.
    private String generateCompanyIdentifierFromName(Connection con, String companyName) throws SQLException { /* ... your existing code ... */ 
        String baseIdentifier; String finalIdentifier;
        String sanitizedCompanyName = companyName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (sanitizedCompanyName.length() >= 3) baseIdentifier = sanitizedCompanyName.substring(0, Math.min(sanitizedCompanyName.length(), 3));
        else if (sanitizedCompanyName.length() > 0) { baseIdentifier = sanitizedCompanyName; while (baseIdentifier.length() < 3) baseIdentifier += "x"; }
        else baseIdentifier = "com"; 
        
        String checkSql = "SELECT COUNT(*) FROM tenants WHERE CompanyIdentifier = ?";
        try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
            int attempts = 0; Random random = new Random();
            do {
                String randomSuffix = String.format("%04d", random.nextInt(10000)); 
                finalIdentifier = (baseIdentifier + randomSuffix);
                if (finalIdentifier.length() > 7) { 
                    finalIdentifier = finalIdentifier.substring(0, 7);
                }
                psCheck.setString(1, finalIdentifier);
                try (ResultSet rs = psCheck.executeQuery()) { 
                    if (rs.next() && rs.getInt(1) == 0) { 
                        logger.info("[SignupServlet] Generated CID from name: " + finalIdentifier); 
                        return finalIdentifier; 
                    }
                }
                attempts++; 
                if (attempts > 20 && baseIdentifier.length() < 7) { 
                    baseIdentifier = (sanitizedCompanyName + randomSuffix).toLowerCase();
                    if (baseIdentifier.length() > 7) baseIdentifier = baseIdentifier.substring(0,7);
                    else if (baseIdentifier.isEmpty()) baseIdentifier = "cpy"; 
                }
                if (attempts > 50) { 
                    logger.warning("[SignupServlet] Failed to gen CID from name after " + attempts + " attempts. Falling back to UUID-based."); 
                    return generateUUIDIdentifier(con); 
                }
            } while (true);
        }
    }
    private String generateUUIDIdentifier(Connection con) throws SQLException { /* ... your existing code ... */
        String identifier; 
        String checkSql = "SELECT COUNT(*) FROM tenants WHERE CompanyIdentifier = ?";
        try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
            int attempts = 0;
            do {
                identifier = UUID.randomUUID().toString().replace("-", "").substring(0, 7).toLowerCase();
                psCheck.setString(1, identifier);
                try (ResultSet rs = psCheck.executeQuery()) { 
                    if (rs.next() && rs.getInt(1) == 0) { 
                        logger.info("[SignupServlet] Generated UUID-based CID: " + identifier); 
                        return identifier; 
                    }
                }
                attempts++; 
                if (attempts > 10) { 
                    logger.severe("[SignupServlet] Failed to generate unique UUID-based CID after " + attempts + " attempts.");
                    throw new SQLException("Failed to generate unique UUID-based CompanyIdentifier."); 
                }
            } while (true);
        }
    }
    private int getNextTenantEmployeeNumber(Connection con, int tenantId) throws SQLException { /* ... your existing code ... */
        String sqlMax = "SELECT MAX(TenantEmployeeNumber) FROM EMPLOYEE_DATA WHERE TenantID = ?";
        try (PreparedStatement psMax = con.prepareStatement(sqlMax)) { 
            psMax.setInt(1, tenantId); 
            try (ResultSet rs = psMax.executeQuery()) { 
                return rs.next() ? rs.getInt(1) + 1 : 1; 
            }
        }
    }


    private void registerCompanyAndAdminWithPayPal(HttpServletRequest request, HttpServletResponse response, JSONObject jsonResponse)
            throws IOException { // Removed ServletException as we're handling responses directly or throwing general Exception
        HttpSession session = request.getSession(true);
        // Retrieve all form parameters
        String companyName = request.getParameter("companyName");
        String companyPhone = request.getParameter("companyPhone");
        String companyAddress = request.getParameter("companyAddress");
        String companyCity = request.getParameter("companyCity");
        String companyState = request.getParameter("companyState");
        String companyZip = request.getParameter("companyZip");
        String browserTimeZoneIdParam = request.getParameter("browserTimeZoneId");
        String adminFirstName = request.getParameter("adminFirstName");
        String adminLastName = request.getParameter("adminLastName");
        String adminEmail = request.getParameter("adminEmail");
        String adminPassword = request.getParameter("adminPassword"); 
        String acceptTerms = request.getParameter("acceptTerms");
        
        // <<<<---- NEW: Get PayPal Payment Token ---->>>>
        String payPalPaymentToken = request.getParameter("paypalPaymentToken"); 

        String cardholderName = request.getParameter("cardholderName");
        String billingAddress = request.getParameter("billingAddress");
        String billingCity = request.getParameter("billingCity");
        String billingState = request.getParameter("billingState");
        String billingZip = request.getParameter("billingZip");

        // --- Basic Server-Side Validations ---
        if (companyName == null || companyName.trim().isEmpty() ||
            adminFirstName == null || adminFirstName.trim().isEmpty() ||
            adminLastName == null || adminLastName.trim().isEmpty() ||
            adminEmail == null || adminEmail.trim().isEmpty() ||
            adminPassword == null || adminPassword.isEmpty() ||
            cardholderName == null || cardholderName.trim().isEmpty() ||
            billingAddress == null || billingAddress.trim().isEmpty() ||
            billingCity == null || billingCity.trim().isEmpty() ||
            billingState == null || billingState.trim().isEmpty() ||
            billingZip == null || billingZip.trim().isEmpty()) { 
            jsonResponse.put("success", false).put("error", "Required company, admin, or billing information is missing.");
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!"true".equals(acceptTerms) && !"on".equals(acceptTerms)) { 
            jsonResponse.put("success", false).put("error", "You must accept the Terms of Service to proceed."); 
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); 
            return; 
        }
        if (payPalPaymentToken == null || payPalPaymentToken.trim().isEmpty()) { 
            logger.warning("[SignupServlet] PayPal payment token was missing from the request.");
            jsonResponse.put("success", false).put("error", "Payment processing token is missing. Please try signing up again."); 
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); 
            return; 
        }
        
        String defaultTimeZone = "America/Denver"; // Your default
        if (browserTimeZoneIdParam != null && !browserTimeZoneIdParam.trim().isEmpty() && !"Unknown".equalsIgnoreCase(browserTimeZoneIdParam)) {
            defaultTimeZone = browserTimeZoneIdParam.trim();
        }
        
        Connection con = null; 
        String actualPayPalSubscriptionId = null; 
        String payPalPayerId = null; 

        try {
            con = DatabaseConnection.getConnection(); 
            con.setAutoCommit(false); 

            // Check for existing email (same as your original logic)
            String checkEmailSql = "SELECT t.TenantID FROM tenants t WHERE LOWER(t.AdminEmail)=LOWER(?) UNION SELECT ed.TenantID FROM EMPLOYEE_DATA ed WHERE LOWER(ed.EMAIL)=LOWER(?)";
            try (PreparedStatement psCE = con.prepareStatement(checkEmailSql)) { 
                String lAdminEmail = adminEmail.trim().toLowerCase(); 
                psCE.setString(1,lAdminEmail); psCE.setString(2,lAdminEmail); 
                try(ResultSet rs=psCE.executeQuery()){ 
                    if(rs.next()){ 
                        jsonResponse.put("success",false).put("error","The email address '"+escapeHtml(adminEmail.trim())+"' is already registered."); 
                        response.setStatus(HttpServletResponse.SC_CONFLICT); 
                        if(con!=null) con.rollback(); 
                        return; 
                    }
                }
            }

            // === Create PayPal Subscription ===
            if (payPalHttpClientInstance == null) {
                 logger.severe("PayPal client was not initialized. Cannot proceed with payment.");
                 throw new Exception("Payment system (PayPal) is not properly configured on the server.");
            }
            if (PAYPAL_SUBSCRIPTION_PLAN_ID.equals("P-YOUR_ACTUAL_PAYPAL_PLAN_ID_HERE") || PAYPAL_SUBSCRIPTION_PLAN_ID.trim().isEmpty()){
                logger.severe("PayPal Subscription Plan ID is not correctly configured in the servlet.");
                throw new Exception("Server payment configuration error (Plan ID).");
            }


            SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
            subscriptionRequest.planId(PAYPAL_SUBSCRIPTION_PLAN_ID);

            // ** CRITICAL **: Construct the PaymentSource using the payPalPaymentToken.
            // The exact structure depends on what 'payPalPaymentToken' represents (e.g., an Order ID from
            // a vaulting flow, or a direct payment method token like BA_TOKEN, CARD_TOKEN).
            // This example assumes it's a generic token that can be used directly.
            // You MUST consult PayPal's Java SDK documentation for creating subscriptions
            // with the specific type of token your client-side Hosted Fields `submit()` provides.
            PaymentSource paymentSource = new PaymentSource();
            Token token = new Token();
            token.id(payPalPaymentToken); // This is the 'payload.id' or 'payload.nonce' from client-side
            token.type("TOKEN"); 
            // Common types: "TOKEN" (generic), "CARD", "PAYMENT_METHOD_TOKEN", "BILLING_AGREEMENT"
            // If payload.id is an Order ID from which a payment method was vaulted, the flow might differ,
            // possibly involving setting up a payment method on a customer first or specific source in order.
            // For direct card token from Hosted Fields intended for subscription, "TOKEN" with type "PAYMENT_METHOD_TOKEN"
            // or using a vaulted instrument might be more appropriate. **VERIFY THIS!**
            paymentSource.token(token);
            subscriptionRequest.paymentSource(paymentSource);

            Subscriber subscriber = new Subscriber();
            PayPalName payerName = new PayPalName();
            String[] nameParts = cardholderName.trim().split("\\s+", 2);
            payerName.givenName(nameParts.length > 0 ? nameParts[0] : cardholderName.trim());
            if (nameParts.length > 1) payerName.surname(nameParts[1]);
            subscriber.name(payerName);
            subscriber.emailAddress(adminEmail.trim().toLowerCase());
            
            AddressPortable billingAddr = new AddressPortable();
            billingAddr.addressLine1(billingAddress.trim());
            billingAddr.adminArea2(billingCity.trim()); // City
            billingAddr.adminArea1(billingState.trim()); // State
            billingAddr.postalCode(billingZip.trim());
            billingAddr.countryCode("US"); // Assuming US. Change if you have international customers.
            // For digital subscriptions, PayPal often expects billing address in shipping_address for subscriber
            subscriber.shippingAddress(new com.paypal.subscriptions.ShippingAddress().address(billingAddr)); 
            subscriptionRequest.subscriber(subscriber);

            // Optional: Define application context for return/cancel URLs if needed by a specific flow
            // ApplicationContext appContext = new ApplicationContext();
            // appContext.brandName("YourTimeClock");
            // appContext.userAction("SUBSCRIBE_NOW");
            // appContext.returnUrl("YOUR_SUCCESS_URL_HERE"); // e.g., after PayPal processes something async
            // appContext.cancelUrl("YOUR_CANCEL_URL_HERE"); // e.g., if user cancels on PayPal page
            // subscriptionRequest.applicationContext(appContext);

            SubscriptionsCreateRequest createSubscriptionRequest = new SubscriptionsCreateRequest();
            createSubscriptionRequest.requestBody(subscriptionRequest);

            logger.info("Attempting to create PayPal subscription. Plan ID: " + PAYPAL_SUBSCRIPTION_PLAN_ID + ". Token starts with: " + (payPalPaymentToken.length() > 4 ? payPalPaymentToken.substring(0,4) : payPalPaymentToken));
            
            HttpResponse<Subscription> payPalSubscriptionResponse = payPalHttpClientInstance.execute(createSubscriptionRequest);
            Subscription createdPayPalSubscription = payPalSubscriptionResponse.result();

            if (createdPayPalSubscription != null && 
                ("ACTIVE".equalsIgnoreCase(createdPayPalSubscription.status()) || 
                 "APPROVAL_PENDING".equalsIgnoreCase(createdPayPalSubscription.status()))) { // APPROVAL_PENDING might need user action on PayPal

                actualPayPalSubscriptionId = createdPayPalSubscription.id();
                if (createdPayPalSubscription.subscriber() != null) {
                    payPalPayerId = createdPayPalSubscription.subscriber().payerId(); // PayPal's Payer ID
                }
                logger.info("PayPal Subscription created successfully (or pending approval). ID: " + actualPayPalSubscriptionId + 
                            ", Status: " + createdPayPalSubscription.status() + 
                            ", PayPal Payer ID: " + payPalPayerId);

                createTenantAndAdminInDB(con, request, session, defaultTimeZone, 
                                         payPalPayerId, // Store this (e.g., in StripeCustomerID column or new PayPalPayerID)
                                         createdPayPalSubscription.status(), // Store the status
                                         adminPassword,
                                         actualPayPalSubscriptionId); // Store the PayPal Subscription ID
                
                con.commit(); 
                logger.info("Tenant/Admin records created in DB and transaction committed.");
                
                jsonResponse.put("success", true)
                            .put("redirect_url", request.getContextPath() + "/set_initial_pin.jsp") 
                            .put("message", "Account created successfully! Subscription is " + createdPayPalSubscription.status().toLowerCase() + ". Please set your initial PIN.");
                response.setStatus(HttpServletResponse.SC_OK); 
                // Return is implicit as this is the end of the try block's successful path for this if condition

            } else {
                String errorDetails = "PayPal subscription creation returned an unexpected status or null object.";
                if (createdPayPalSubscription != null) {
                    errorDetails = "PayPal Subscription Status: " + createdPayPalSubscription.status() + 
                                   (createdPayPalSubscription.id() != null ? ", ID: " + createdPayPalSubscription.id() : "");
                    // Consider logging the full response object for detailed debugging if available:
                    // logger.warning("Full PayPal Subscription Response (on failure/unexpected status): " + new JSONObject(createdPayPalSubscription).toString());
                } else {
                     logger.warning("createdPayPalSubscription object was null after API call.");
                }
                throw new Exception(errorDetails); // Caught by general Exception block below
            }

        } catch (HttpException he) { // PayPal HTTP Exception
            logger.log(Level.SEVERE, "PayPal API HTTP error: " + he.getMessage() + " Status Code: " + he.getStatusCode(), he);
            // String debugId = he.headers().firstValue("PayPal-Debug-Id").orElse(null); // Example to get debug ID
            // logger.severe("PayPal Debug ID: " + debugId);
            // The message from HttpException often contains the JSON error from PayPal
            if (con != null) { try { con.rollback(); } catch (SQLException exSQL) { logger.log(Level.WARNING, "DB rollback failed after PayPal HttpException.", exSQL); } }
            jsonResponse.put("success", false).put("error", "Payment processing error with PayPal (Code: " + he.getStatusCode() + "). " + (he.getMessage() != null ? extractPayPalErrorMessage(he.getMessage()) : "Please check details or try again."));
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST); 
        } catch (IOException ioe) { // Lower-level IO Exception from PayPal client execute
            logger.log(Level.SEVERE, "PayPal API communication (IO) error: " + ioe.getMessage(), ioe);
            if (con != null) { try { con.rollback(); } catch (SQLException exSQL) { logger.log(Level.WARNING, "DB rollback failed after PayPal IOException.", exSQL); } }
            jsonResponse.put("success", false).put("error", "A communication error occurred with the payment gateway. Please try again shortly.");
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } catch (SQLException dbEx) { 
            if (con != null) { try { con.rollback(); } catch (SQLException exSQL) { logger.log(Level.WARNING, "DB rollback failed after main SQLException.", exSQL); } }
            logger.log(Level.SEVERE, "Database error during registration: " + dbEx.getMessage(), dbEx); 
            jsonResponse.put("success", false).put("error", "A database error occurred during registration. Please try again later."); 
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); 
        } catch (Exception e) { 
            if (con != null) { try { con.rollback(); } catch (SQLException exSQL) { logger.log(Level.WARNING, "DB rollback failed after general Exception.", exSQL); } }
            logger.log(Level.SEVERE, "General error during registration: " + e.getMessage(), e); 
            jsonResponse.put("success", false).put("error", "An unexpected error occurred: " + (e.getMessage() != null ? escapeHtml(e.getMessage()) : "Please try again.")); 
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR); 
        } finally { 
            if (con != null) {
                try {
                    if (!con.getAutoCommit()) con.setAutoCommit(true); 
                    if (!con.isClosed()) con.close();
                } catch(SQLException e) {
                    logger.log(Level.WARNING, "Error closing DB connection in registerCompanyAndAdminWithPayPal finally block.", e);
                }
            }
        }
    }
    
    // Modified to accept PayPal Actual Subscription ID
    private void createTenantAndAdminInDB(Connection con, HttpServletRequest request, HttpSession session, 
                                          String defaultTimeZone, String payPalPayerId, // Changed from stripeCustomerId
                                          String payPalSubscriptionStatus, String rawAdminPassword,
                                          String actualPayPalSubscriptionId) throws SQLException { // Added actualPayPalSubscriptionId
        
        String companyName=request.getParameter("companyName");
        // ... (get other request parameters as before: companyPhone, companyAddress, etc.) ...
        String companyPhone=request.getParameter("companyPhone");
        String companyAddress=request.getParameter("companyAddress");
        String companyCity=request.getParameter("companyCity");
        String companyState=request.getParameter("companyState");
        String companyZip=request.getParameter("companyZip");
        String adminFirstName=request.getParameter("adminFirstName");
        String adminLastName=request.getParameter("adminLastName");
        String adminEmail=request.getParameter("adminEmail");

        String localCompanyIdentifier = generateCompanyIdentifierFromName(con, companyName.trim());
        String hashedPassword = BCrypt.hashpw(rawAdminPassword, BCrypt.gensalt(12)); 

        // IMPORTANT: Add a new column `PayPalSubscriptionID` (e.g., VARCHAR(255)) to your `tenants` table.
        // You can repurpose `StripeCustomerID` for `PayPalPayerID` or add a `PayPalPayerID` column too.
        // This example assumes you repurpose StripeCustomerID and add PayPalSubscriptionID.
        String insertTenantSql = "INSERT INTO tenants (CompanyName, CompanyIdentifier, AdminEmail, AdminPasswordHash, " +
                                 "PhoneNumber, Address, City, State, ZipCode, " +
                                 "StripeCustomerID, SubscriptionStatus, PayPalSubscriptionID, " + // Added PayPalSubscriptionID
                                 "CreatedAt, UpdatedAt) VALUES (?,?,?,?,?,?,?,?,?,?,?,?, NOW(), NOW())";
        int generatedTenantId = -1; 
        try(PreparedStatement psT=con.prepareStatement(insertTenantSql,Statement.RETURN_GENERATED_KEYS)){ 
            psT.setString(1,companyName.trim()); 
            psT.setString(2,localCompanyIdentifier); 
            psT.setString(3,adminEmail.trim().toLowerCase()); 
            psT.setString(4,hashedPassword); 
            psT.setString(5,(companyPhone!=null&&!companyPhone.trim().isEmpty())?companyPhone.trim():null); 
            psT.setString(6,(companyAddress!=null&&!companyAddress.trim().isEmpty())?companyAddress.trim():null); 
            psT.setString(7,(companyCity!=null&&!companyCity.trim().isEmpty())?companyCity.trim():null); 
            psT.setString(8,(companyState!=null&&!companyState.trim().isEmpty())?companyState.trim():null); 
            psT.setString(9,(companyZip!=null&&!companyZip.trim().isEmpty())?companyZip.trim():null); 
            psT.setString(10, payPalPayerId); // Store PayPal Payer ID (was stripeCustomerId)
            psT.setString(11, payPalSubscriptionStatus); // Store PayPal status (e.g., "ACTIVE")
            psT.setString(12, actualPayPalSubscriptionId); // Store the actual PayPal Subscription ID
            
            psT.executeUpdate(); 
            try(ResultSet gk=psT.getGeneratedKeys()){
                if(gk.next()) generatedTenantId=gk.getInt(1);
                else throw new SQLException("Tenant creation failed, no ID obtained.");
            }
        }
        logger.info("DB: Tenant created with ID "+generatedTenantId + ". PayPal Sub ID: " + actualPayPalSubscriptionId); 
        Configuration.saveProperty(con,generatedTenantId,"DefaultTimeZone",defaultTimeZone); 

        // ... (rest of your DB setup for DEPARTMENTS, SCHEDULES, ACCRUALS, EMPLOYEE_DATA remains the same) ...
        // Ensure these subsequent inserts use `generatedTenantId` correctly.
        try(PreparedStatement psD=con.prepareStatement("INSERT INTO DEPARTMENTS (TenantID,NAME,DESCRIPTION) VALUES (?,?,?)")){psD.setInt(1,generatedTenantId);psD.setString(2,"None");psD.setString(3,"Default department");psD.executeUpdate();}
        try(PreparedStatement psS=con.prepareStatement("INSERT INTO SCHEDULES (TenantID,NAME,DAYS_WORKED,AUTO_LUNCH) VALUES (?,?,?,?)")){psS.setInt(1,generatedTenantId);psS.setString(2,"Open");psS.setString(3,"SMTWHFA");psS.setBoolean(4,false);psS.executeUpdate();}
        try(PreparedStatement psA=con.prepareStatement("INSERT INTO ACCRUALS (TenantID,NAME,VACATION,SICK,PERSONAL) VALUES (?,?,?,?,?)")){psA.setInt(1,generatedTenantId);psA.setString(2,"None");psA.setInt(3,0);psA.setInt(4,0);psA.setInt(5,0);psA.executeUpdate();}
        
        String insertAdminSql="INSERT INTO EMPLOYEE_DATA (TenantID,TenantEmployeeNumber,FIRST_NAME,LAST_NAME,EMAIL,PERMISSIONS,HIRE_DATE,PasswordHash,RequiresPasswordChange,ACTIVE,DEPT_ID,ACCRUAL_ID, CreatedAt, UpdatedAt) VALUES (?,?,?,?,?,?,?,?,TRUE,TRUE, (SELECT ID FROM DEPARTMENTS WHERE TenantID=? AND NAME='None' LIMIT 1), (SELECT ID FROM ACCRUALS WHERE TenantID=? AND NAME='None' LIMIT 1), NOW(), NOW())";
        String defaultPinHash = BCrypt.hashpw("1234",BCrypt.gensalt(12)); 
        int adminTenantEmpNo = getNextTenantEmployeeNumber(con,generatedTenantId); 
        int generatedAdminEID = -1;
        try(PreparedStatement psAdm=con.prepareStatement(insertAdminSql,Statement.RETURN_GENERATED_KEYS)){
            psAdm.setInt(1,generatedTenantId); psAdm.setInt(2,adminTenantEmpNo);
            psAdm.setString(3,adminFirstName.trim()); psAdm.setString(4,adminLastName.trim());
            psAdm.setString(5,adminEmail.trim().toLowerCase()); psAdm.setString(6,"Administrator"); 
            psAdm.setDate(7,Date.valueOf(LocalDate.now())); psAdm.setString(8,defaultPinHash); 
            psAdm.setInt(9, generatedTenantId); psAdm.setInt(10, generatedTenantId); 
            psAdm.executeUpdate();
            try(ResultSet gk=psAdm.getGeneratedKeys()){
                if(gk.next()) generatedAdminEID=gk.getInt(1);
                else throw new SQLException("Admin (Employee_Data) creation failed, no EID obtained.");
            }
        }
        logger.info("DB: Admin (Employee_Data) created with EID "+generatedAdminEID + " for TenantID " + generatedTenantId);
        
        // Set session attributes for successful signup and login
        session.setAttribute("TenantID",generatedTenantId); 
        session.setAttribute("EID",generatedAdminEID); 
        session.setAttribute("UserFirstName",adminFirstName.trim()); 
        session.setAttribute("Permissions","Administrator"); 
        session.setAttribute("startSetupWizard",true); 
        session.setAttribute("wizardStep","initialPinSetRequired"); 
        // ... (other session attributes as in your original code) ...
        session.setAttribute("wizardAdminEid",generatedAdminEID); 
        session.setAttribute("wizardAdminEmail",adminEmail.trim().toLowerCase()); 
        session.setAttribute("wizardAdminFirstName",adminFirstName.trim()); 
        session.setAttribute("CompanyNameSignup",companyName.trim()); 
        session.setAttribute("GeneratedCompanyID",localCompanyIdentifier); 
        session.setAttribute("SignupCompanyState",companyState); 
        session.setAttribute("signupSuccessfulCompanyInfo","Company '"+escapeHtml(companyName.trim())+"' created! Your Company ID is: <strong id='copyCompanyIdValue'>"+escapeHtml(localCompanyIdentifier)+"</strong>.");
        
        // Clear any temporary Stripe SCA session attributes (though they shouldn't exist now)
        session.removeAttribute("temp_stripe_customer_id_for_sca");
        session.removeAttribute("temp_signup_original_form_data");
    }

    // Helper to extract a more user-friendly message from PayPal's JSON error string
    private String extractPayPalErrorMessage(String payPalErrorJson) {
        if (payPalErrorJson == null || payPalErrorJson.trim().isEmpty()) {
            return "An unspecified error occurred with the payment processor.";
        }
        try {
            // PayPal error messages are often JSON strings within the HttpException message
            // This attempts to parse it if it looks like JSON.
            String potentialJson = payPalErrorJson;
            if (payPalErrorJson.contains("Response:") && payPalErrorJson.indexOf('{') > -1) {
                 potentialJson = payPalErrorJson.substring(payPalErrorJson.indexOf('{'));
            }

            if (potentialJson.trim().startsWith("{")) {
                JSONObject errorObj = new JSONObject(potentialJson);
                if (errorObj.has("details") && errorObj.getJSONArray("details").length() > 0) {
                    JSONObject firstDetail = errorObj.getJSONArray("details").getJSONObject(0);
                    if (firstDetail.has("issue")) {
                        String issue = firstDetail.getString("issue");
                        String description = firstDetail.optString("description", "");
                        return issue + (description.isEmpty() ? "" : ": " + description);
                    }
                }
                if (errorObj.has("message")) { // Fallback to top-level message
                    return errorObj.getString("message");
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Could not parse detailed PayPal error message from: " + payPalErrorJson, e);
        }
        // If parsing fails, return a generic part of the message or a default
        if (payPalErrorJson.length() > 150) { // Avoid showing huge raw responses
            return "Payment error. Please check details or try again. (Ref: PP_GEN_ERR)";
        }
        return payPalErrorJson; // Or a more generic message
    }
    
    // The finalizeSubscription method was Stripe-specific for SCA flow.
    // It is removed as the PayPal flow with Hosted Fields and server-side subscription
    // creation with a token is typically a single server-side transaction attempt.
    // If PayPal API indicates a need for user redirection (e.g., for 3D Secure via PayPal),
    // the flow would be more complex and might require a similar "finalize" step,
    // but that's an advanced scenario beyond this direct token-to-subscription model.
}