package timeclock.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import timeclock.db.DatabaseConnection;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.mindrot.jbcrypt.BCrypt;
import org.json.JSONObject;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.SubscriptionCreateParams;

@WebServlet("/SignupServlet")
public class SignupServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(SignupServlet.class.getName());

	private static final String STRIPE_PRICE_ID = "price_1RWNdXBtvyYfb2KWWt6p9F4X";

    static {
        // Read the secret key from an environment variable
        String stripeApiKey = System.getenv("STRIPE_API_KEY");

        // Check if the key was found
        if (stripeApiKey == null || stripeApiKey.trim().isEmpty()) {
            logger.severe("CRITICAL ERROR: STRIPE_API_KEY environment variable not set!");
            // In a real application, you might want to prevent the servlet from initializing
        } else {
            Stripe.apiKey = stripeApiKey;
            logger.info("[SignupServlet] Stripe API Key initialized successfully.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        JSONObject jsonResponse = new JSONObject();
        String action = request.getParameter("action");
        logger.info("[SignupServlet] doPost received action: " + action);
        
        try (PrintWriter out = response.getWriter()) {
            if ("registerCompanyAdmin".equals(action)) {
                registerCompanyAndAdmin(request, response, jsonResponse);
            } else if ("finalizeSubscription".equals(action)) {
                finalizeSubscription(request, response, jsonResponse);
            } else {
                jsonResponse.put("success", false).put("error", "Invalid action specified.");
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            }
            out.print(jsonResponse.toString());
        } catch (Exception e) { 
            logger.log(Level.SEVERE, "Critical error in SignupServlet doPost", e);
            if (!response.isCommitted()) {
                jsonResponse.put("success", false).put("error", "A critical server error occurred.");
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    private void registerCompanyAndAdmin(HttpServletRequest request, HttpServletResponse response, JSONObject jsonResponse) {
        HttpSession session = request.getSession(true);
        String appliedPromoCode = request.getParameter("appliedPromoCode");
        
        Connection con = null;
        try {
            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false); 

            Integer promoCodeId = null;
            String discountType = null;
            Long trialDays = null;
            
            if (appliedPromoCode != null && !appliedPromoCode.trim().isEmpty()) {
                String promoSql = "SELECT PromoCodeID, DiscountType, DiscountValue FROM promo_codes WHERE Code = ? AND IsEnabled = TRUE AND (ExpirationDate IS NULL OR ExpirationDate >= CURDATE()) AND (MaxUses IS NULL OR CurrentUses < MaxUses)";
                try(PreparedStatement psPromo = con.prepareStatement(promoSql)) {
                    psPromo.setString(1, appliedPromoCode);
                    ResultSet rsPromo = psPromo.executeQuery();
                    if(rsPromo.next()) {
                        promoCodeId = rsPromo.getInt("PromoCodeID");
                        discountType = rsPromo.getString("DiscountType");
                        if ("TRIAL".equals(discountType)) {
                            trialDays = (long) rsPromo.getInt("DiscountValue");
                        }
                    } else {
                        throw new Exception("Submitted promo code is invalid or has expired.");
                    }
                }
            }

            if ("LIFETIME".equals(discountType)) {
                logger.info("Processing LIFETIME promo signup.");
                createTenantAndAdminFromRequest(con, request, session, promoCodeId, "Active - Lifetime Promo", null, null);
                con.commit();
                session.setAttribute("wizardStep", "initialPinSetRequired");
                jsonResponse.put("success", true).put("action_required", false)
                    .put("redirect_url", request.getContextPath() + "/set_initial_pin.jsp");
            } else {
                logger.info("Processing standard or trial signup.");

                validateRequiredParameter(request, "stripePaymentMethodId");
                validateRequiredParameter(request, "cardholderName");
                validateRequiredParameter(request, "billingAddress");
                validateRequiredParameter(request, "billingCity");
                validateRequiredParameter(request, "billingState");
                validateRequiredParameter(request, "billingZip");
                logger.info("Server-side validation passed for payment fields.");

                Customer customer = createStripeCustomer(request);
                logger.info("Stripe Customer created: " + customer.getId());

                SubscriptionCreateParams.Builder subParamsBuilder = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder().setPrice(STRIPE_PRICE_ID).build())
                    .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                    .addAllExpand(List.of("latest_invoice.payment_intent"))
                    .setCollectionMethod(SubscriptionCreateParams.CollectionMethod.CHARGE_AUTOMATICALLY);

                if ("TRIAL".equals(discountType) && trialDays != null && trialDays > 0) {
                    logger.info("Applying " + trialDays + "-day trial to subscription.");
                    subParamsBuilder.setTrialPeriodDays(trialDays);
                }
                
                Subscription stripeSubscription = Subscription.create(subParamsBuilder.build());
                logger.info("Stripe Subscription " + stripeSubscription.getId() + " created with status: " + stripeSubscription.getStatus());

                String subStatus = stripeSubscription.getStatus();
                String dbSubStatus = "trialing".equalsIgnoreCase(subStatus) ? "Trialing" : "Active";
                
                if ("active".equalsIgnoreCase(subStatus) || "trialing".equalsIgnoreCase(subStatus)) {
                    createTenantAndAdminFromRequest(con, request, session, promoCodeId, dbSubStatus, customer.getId(), stripeSubscription.getId());
                    con.commit();
                    session.setAttribute("wizardStep", "initialPinSetRequired");
                    jsonResponse.put("success", true).put("action_required", false)
                        .put("redirect_url", request.getContextPath() + "/set_initial_pin.jsp");
                } else if ("incomplete".equalsIgnoreCase(subStatus)) {
                    Invoice latestInvoice = stripeSubscription.getLatestInvoiceObject();
                    if (latestInvoice != null && latestInvoice.getPaymentIntent() != null) {
                        PaymentIntent paymentIntent = latestInvoice.getPaymentIntentObject();
                        saveTempDataToSession(session, request, customer.getId(), stripeSubscription.getId());
                        con.rollback();
                        jsonResponse.put("success", true).put("action_required", true)
                                      .put("client_secret", paymentIntent.getClientSecret())
                                      .put("subscription_id", stripeSubscription.getId());
                    } else {
                         throw new Exception("Subscription requires authentication but invoice information is missing.");
                    }
                } else {
                     throw new Exception("Unhandled subscription status: " + subStatus);
                }
            }
        } catch (Exception e) {
            if (con != null) { try { con.rollback(); } catch (SQLException ex) { logger.log(Level.WARNING, "Rollback failed", ex); } }
            logger.log(Level.SEVERE, "Error in registerCompanyAndAdmin", e);
            jsonResponse.put("success", false).put("error", e.getMessage());
            if (e instanceof IllegalArgumentException) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            } else {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) { /* ignore */ } }
        }
    }

    private void finalizeSubscription(HttpServletRequest request, HttpServletResponse response, JSONObject jsonResponse) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            jsonResponse.put("success", false).put("error", "Your session has expired. Please try signing up again.");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String paymentIntentId = request.getParameter("payment_intent_id");
        String stripeCustomerId = (String) session.getAttribute("temp_stripe_customer_id_for_sca");
        String stripeSubscriptionId = (String) session.getAttribute("temp_stripe_subscription_id_for_sca");
        @SuppressWarnings("unchecked")
        Map<String, String> formData = (Map<String, String>) session.getAttribute("temp_signup_original_form_data");

        if (paymentIntentId == null || stripeCustomerId == null || formData == null) {
            jsonResponse.put("success", false).put("error", "Essential information missing to finalize subscription. Please start over.");
            return;
        }

        Connection con = null;
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
            if (!"succeeded".equalsIgnoreCase(paymentIntent.getStatus())) {
                throw new Exception("Payment authentication was not successful. Final Status: " + paymentIntent.getStatus());
            }
            
            Subscription subscription = Subscription.retrieve(stripeSubscriptionId);
            String finalStatus = "trialing".equalsIgnoreCase(subscription.getStatus()) ? "Trialing" : "Active";

            con = DatabaseConnection.getConnection();
            con.setAutoCommit(false);
            
            Integer promoCodeId = null;
            String appliedPromoCode = formData.get("appliedPromoCode");
            if (appliedPromoCode != null && !appliedPromoCode.isEmpty()) {
                String promoSql = "SELECT PromoCodeID FROM promo_codes WHERE Code = ?";
                try(PreparedStatement psPromo = con.prepareStatement(promoSql)) {
                    psPromo.setString(1, appliedPromoCode);
                    ResultSet rsPromo = psPromo.executeQuery();
                    if(rsPromo.next()) promoCodeId = rsPromo.getInt("PromoCodeID");
                }
            }

            createTenantAndAdminFromMap(con, session, formData, promoCodeId, finalStatus, stripeCustomerId, stripeSubscriptionId);
            con.commit();
            logger.info("Tenant/Admin DB records created successfully post-SCA.");

            session.removeAttribute("temp_stripe_customer_id_for_sca");
            session.removeAttribute("temp_stripe_subscription_id_for_sca");
            session.removeAttribute("temp_signup_original_form_data");

            jsonResponse.put("success", true)
                        .put("redirect_url", request.getContextPath() + "/set_initial_pin.jsp")
                        .put("message", "Payment confirmed! Account created successfully.");

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error in finalizeSubscription", e);
            if (con != null) { try { con.rollback(); } catch (SQLException ex) { logger.log(Level.WARNING, "Rollback failed", ex); } }
            jsonResponse.put("success", false).put("error", e.getMessage());
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            if (con != null) { try { con.close(); } catch (SQLException ex) { /* ignore */ } }
        }
    }

    private void createTenantAndAdminFromRequest(Connection con, HttpServletRequest request, HttpSession session, Integer appliedPromoCodeID, String subscriptionStatus, String stripeCustomerId, String stripeSubscriptionId) throws SQLException {
        String companyName = request.getParameter("companyName");
        String adminFirstName = request.getParameter("adminFirstName");
        String adminLastName = request.getParameter("adminLastName");
        String adminEmail = request.getParameter("adminEmail");
        String rawAdminPassword = request.getParameter("adminPassword");
        String companyPhone = request.getParameter("companyPhone");
        String companyAddress = request.getParameter("companyAddress");
        String companyCity = request.getParameter("companyCity");
        String companyState = request.getParameter("companyState");
        String companyZip = request.getParameter("companyZip");
        String defaultTimeZone = request.getParameter("browserTimeZoneId");

        String localCompanyIdentifier = generateCompanyIdentifierFromName(con, companyName.trim());
        String hashedPassword = BCrypt.hashpw(rawAdminPassword, BCrypt.gensalt(12));

        String insertTenantSql = "INSERT INTO tenants (CompanyName, CompanyIdentifier, AdminEmail, AdminPasswordHash, PhoneNumber, Address, City, State, ZipCode, DefaultTimeZone, StripeCustomerID, SubscriptionStatus, StripeSubscriptionID, SignupDate, AppliedPromoCodeID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, CURDATE(), ?)"; 
        
        int generatedTenantId = -1;
        try (PreparedStatement psT = con.prepareStatement(insertTenantSql, Statement.RETURN_GENERATED_KEYS)) {
            psT.setString(1, companyName.trim()); psT.setString(2, localCompanyIdentifier);
            psT.setString(3, adminEmail.trim().toLowerCase()); psT.setString(4, hashedPassword);
            psT.setString(5, companyPhone); psT.setString(6, companyAddress);
            psT.setString(7, companyCity); psT.setString(8, companyState);
            psT.setString(9, companyZip); psT.setString(10, defaultTimeZone);
            psT.setString(11, stripeCustomerId); psT.setString(12, subscriptionStatus);
            psT.setString(13, stripeSubscriptionId);
            
            if (appliedPromoCodeID != null) {
                psT.setInt(14, appliedPromoCodeID);
            } else {
                psT.setNull(14, Types.INTEGER);
            }
            psT.executeUpdate();
            try (ResultSet gk = psT.getGeneratedKeys()) {
                if (gk.next()) generatedTenantId = gk.getInt(1);
                else throw new SQLException("Tenant creation failed, no ID obtained.");
            }
        }

        if (appliedPromoCodeID != null) {
            String updatePromoSql = "UPDATE promo_codes SET CurrentUses = CurrentUses + 1 WHERE PromoCodeID = ?";
            try (PreparedStatement psUpdate = con.prepareStatement(updatePromoSql)) {
                psUpdate.setInt(1, appliedPromoCodeID);
                psUpdate.executeUpdate();
            }
        }
        
        createDefaultEntities(con, generatedTenantId);
        initializeDefaultSettings(con, generatedTenantId);

        String insertAdminSql="INSERT INTO EMPLOYEE_DATA (TenantID,TenantEmployeeNumber,FIRST_NAME,LAST_NAME,EMAIL,PERMISSIONS,HIRE_DATE,PasswordHash,RequiresPasswordChange,ACTIVE,DEPT,ACCRUAL_POLICY) VALUES (?,?,?,?,?,?,?,?,TRUE,TRUE,?,?)";
        String defaultPinHash = BCrypt.hashpw("1234",BCrypt.gensalt(12)); 
        int adminTenantEmpNo = getNextTenantEmployeeNumber(con,generatedTenantId);
        int generatedAdminEID = -1;
        try(PreparedStatement psAdm=con.prepareStatement(insertAdminSql,Statement.RETURN_GENERATED_KEYS)){
             psAdm.setInt(1,generatedTenantId); psAdm.setInt(2,adminTenantEmpNo); psAdm.setString(3,adminFirstName.trim()); psAdm.setString(4,adminLastName.trim()); psAdm.setString(5,adminEmail.trim().toLowerCase()); psAdm.setString(6,"Administrator"); psAdm.setDate(7,Date.valueOf(LocalDate.now())); psAdm.setString(8,defaultPinHash); psAdm.setString(9, "None"); psAdm.setString(10, "None"); 
            psAdm.executeUpdate();
            try(ResultSet gk=psAdm.getGeneratedKeys()){ if(gk.next()) generatedAdminEID=gk.getInt(1); else throw new SQLException("Admin creation failed."); }
        }

        session.setAttribute("TenantID", generatedTenantId); 
        session.setAttribute("EID", generatedAdminEID); 
        session.setAttribute("UserFirstName", adminFirstName.trim()); 
        session.setAttribute("Permissions", "Administrator"); 
        session.setAttribute("startSetupWizard", true); 
        session.setAttribute("wizardStep","initialPinSetRequired"); 
        session.setAttribute("wizardAdminEid",generatedAdminEID); 
        session.setAttribute("wizardAdminEmail",adminEmail.trim().toLowerCase()); 
        session.setAttribute("wizardAdminFirstName",adminFirstName.trim()); 
        session.setAttribute("CompanyNameSignup",companyName.trim()); 
        session.setAttribute("GeneratedCompanyID",localCompanyIdentifier); 
        session.setAttribute("SignupCompanyState",companyState); 
        session.setAttribute("signupSuccessfulCompanyInfo","Company '"+escapeHtml(companyName.trim())+"' created! Company ID: <strong id='copyCompanyIdValue'>"+escapeHtml(localCompanyIdentifier)+"</strong>.");
    }

    private void createTenantAndAdminFromMap(Connection con, HttpSession session, Map<String, String> formData, Integer appliedPromoCodeID, String subscriptionStatus, String stripeCustomerId, String stripeSubscriptionId) throws SQLException {
        String companyName = formData.get("companyName");
        String adminFirstName = formData.get("adminFirstName");
        String adminLastName = formData.get("adminLastName");
        String adminEmail = formData.get("adminEmail");
        String rawAdminPassword = formData.get("adminPassword");
        String companyPhone = formData.get("companyPhone");
        String companyAddress = formData.get("companyAddress");
        String companyCity = formData.get("companyCity");
        String companyState = formData.get("companyState");
        String companyZip = formData.get("companyZip");
        String defaultTimeZone = formData.get("browserTimeZoneId");

        String localCompanyIdentifier = generateCompanyIdentifierFromName(con, companyName.trim());
        String hashedPassword = BCrypt.hashpw(rawAdminPassword, BCrypt.gensalt(12));
        String insertTenantSql = "INSERT INTO tenants (CompanyName, CompanyIdentifier, AdminEmail, AdminPasswordHash, PhoneNumber, Address, City, State, ZipCode, DefaultTimeZone, StripeCustomerID, SubscriptionStatus, StripeSubscriptionID, SignupDate, AppliedPromoCodeID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, CURDATE(), ?)"; 
        int generatedTenantId = -1;
        try (PreparedStatement psT = con.prepareStatement(insertTenantSql, Statement.RETURN_GENERATED_KEYS)) {
            psT.setString(1, companyName.trim()); psT.setString(2, localCompanyIdentifier);
            psT.setString(3, adminEmail.trim().toLowerCase()); psT.setString(4, hashedPassword);
            psT.setString(5, companyPhone); psT.setString(6, companyAddress);
            psT.setString(7, companyCity); psT.setString(8, companyState);
            psT.setString(9, companyZip); psT.setString(10, defaultTimeZone);
            psT.setString(11, stripeCustomerId); psT.setString(12, subscriptionStatus);
            psT.setString(13, stripeSubscriptionId);
            if (appliedPromoCodeID != null) psT.setInt(14, appliedPromoCodeID); else psT.setNull(14, Types.INTEGER);
            psT.executeUpdate();
            try (ResultSet gk = psT.getGeneratedKeys()) { if (gk.next()) generatedTenantId = gk.getInt(1); else throw new SQLException("Tenant creation failed, no ID obtained."); }
        }
        if (appliedPromoCodeID != null) {
            String updatePromoSql = "UPDATE promo_codes SET CurrentUses = CurrentUses + 1 WHERE PromoCodeID = ?";
            try (PreparedStatement psUpdate = con.prepareStatement(updatePromoSql)) { psUpdate.setInt(1, appliedPromoCodeID); psUpdate.executeUpdate(); }
        }
        
        createDefaultEntities(con, generatedTenantId);
        initializeDefaultSettings(con, generatedTenantId);

        String insertAdminSql="INSERT INTO EMPLOYEE_DATA (TenantID,TenantEmployeeNumber,FIRST_NAME,LAST_NAME,EMAIL,PERMISSIONS,HIRE_DATE,PasswordHash,RequiresPasswordChange,ACTIVE,DEPT,ACCRUAL_POLICY) VALUES (?,?,?,?,?,?,?,?,TRUE,TRUE,?,?)";
        String defaultPinHash = BCrypt.hashpw("1234",BCrypt.gensalt(12)); 
        int adminTenantEmpNo = getNextTenantEmployeeNumber(con,generatedTenantId);
        int generatedAdminEID = -1;
        try(PreparedStatement psAdm=con.prepareStatement(insertAdminSql,Statement.RETURN_GENERATED_KEYS)){
             psAdm.setInt(1,generatedTenantId); psAdm.setInt(2,adminTenantEmpNo); psAdm.setString(3,adminFirstName.trim()); psAdm.setString(4,adminLastName.trim()); psAdm.setString(5,adminEmail.trim().toLowerCase()); psAdm.setString(6,"Administrator"); psAdm.setDate(7,Date.valueOf(LocalDate.now())); psAdm.setString(8,defaultPinHash); psAdm.setString(9, "None"); psAdm.setString(10, "None"); 
            psAdm.executeUpdate();
            try(ResultSet gk=psAdm.getGeneratedKeys()){ if(gk.next()) generatedAdminEID=gk.getInt(1); else throw new SQLException("Admin creation failed."); }
        }

        session.setAttribute("TenantID", generatedTenantId); session.setAttribute("EID", generatedAdminEID); session.setAttribute("UserFirstName", adminFirstName.trim()); session.setAttribute("Permissions", "Administrator"); session.setAttribute("startSetupWizard", true); session.setAttribute("wizardStep","initialPinSetRequired"); session.setAttribute("wizardAdminEid",generatedAdminEID); session.setAttribute("wizardAdminEmail",adminEmail.trim().toLowerCase()); session.setAttribute("wizardAdminFirstName",adminFirstName.trim()); session.setAttribute("CompanyNameSignup",companyName.trim()); session.setAttribute("GeneratedCompanyID",localCompanyIdentifier); session.setAttribute("SignupCompanyState",companyState); 
        session.setAttribute("signupSuccessfulCompanyInfo","Company '"+escapeHtml(companyName.trim())+"' created! Company ID: <strong id='copyCompanyIdValue'>"+escapeHtml(localCompanyIdentifier)+"</strong>.");
    }
    
    private void createDefaultEntities(Connection con, int tenantId) throws SQLException {
        String deptSql = "INSERT INTO DEPARTMENTS (TenantID, NAME, DESCRIPTION, SUPERVISOR) VALUES (?, ?, ?, ?)";
        try(PreparedStatement psD = con.prepareStatement(deptSql)){ psD.setInt(1, tenantId); psD.setString(2, "None"); psD.setNull(3, Types.VARCHAR); psD.setNull(4, Types.VARCHAR); psD.executeUpdate(); }
        String schedSql = "INSERT INTO SCHEDULES (TenantID, NAME, AUTO_LUNCH, HRS_REQUIRED, LUNCH_LENGTH) VALUES (?, ?, ?, ?, ?)";
        try(PreparedStatement psS_Open = con.prepareStatement(schedSql)){ psS_Open.setInt(1, tenantId); psS_Open.setString(2, "Open"); psS_Open.setBoolean(3, false); psS_Open.setInt(4, 0); psS_Open.setInt(5, 0); psS_Open.executeUpdate(); }
        try(PreparedStatement psS_Auto = con.prepareStatement(schedSql)){ psS_Auto.setInt(1, tenantId); psS_Auto.setString(2, "Open w/ Auto Lunch"); psS_Auto.setBoolean(3, true); psS_Auto.setInt(4, 6); psS_Auto.setInt(5, 30); psS_Auto.executeUpdate(); }
        String accrualSql = "INSERT INTO ACCRUALS (TenantID, NAME, VACATION, SICK, PERSONAL) VALUES (?, ?, ?, ?, ?)";
        try(PreparedStatement psA_None = con.prepareStatement(accrualSql)){ psA_None.setInt(1, tenantId); psA_None.setString(2, "None"); psA_None.setInt(3, 0); psA_None.setInt(4, 0); psA_None.setInt(5, 0); psA_None.executeUpdate(); }
        try(PreparedStatement psA_Std = con.prepareStatement(accrualSql)){ psA_Std.setInt(1, tenantId); psA_Std.setString(2, "Standard"); psA_Std.setInt(3, 5); psA_Std.setInt(4, 5); psA_Std.setInt(5, 0); psA_Std.executeUpdate(); }
    }
    
    private void initializeDefaultSettings(Connection con, int tenantId) throws SQLException {
        String payPeriodType = "Weekly";
        String firstDayOfWeek = "Sunday";
        Date payPeriodStartDate = calculatePayPeriodStartDate(payPeriodType, firstDayOfWeek);

        saveConfigurationProperty(con, tenantId, "PayPeriodType", payPeriodType);
        saveConfigurationProperty(con, tenantId, "FirstDayOfWeek", firstDayOfWeek);
        saveConfigurationProperty(con, tenantId, "PayPeriodStartDate", payPeriodStartDate.toString());
        
        logger.info("Saved default settings for TenantID " + tenantId + 
                    ". PayPeriodType: " + payPeriodType + 
                    ", FirstDayOfWeek: " + firstDayOfWeek + 
                    ", Calculated PayPeriodStartDate: " + payPeriodStartDate);
    }

    private void saveConfigurationProperty(Connection con, int tenantId, String key, String value) throws SQLException {
        String sql = "INSERT INTO settings (TenantID, setting_key, setting_value) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, tenantId);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    private Date calculatePayPeriodStartDate(String payPeriodType, String firstDayOfWeekStr) {
        LocalDate today = LocalDate.now();
        DayOfWeek firstDay = DayOfWeek.valueOf(firstDayOfWeekStr.toUpperCase());
        LocalDate startDate = today.with(TemporalAdjusters.previousOrSame(firstDay));

        if ("Bi-Weekly".equalsIgnoreCase(payPeriodType)) {
            startDate = startDate.minusWeeks(1);
        }
        
        return Date.valueOf(startDate);
    }
    
    private String escapeHtml(String input) { 
        if (input == null) return "";
        return input.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String generateCompanyIdentifierFromName(Connection con, String companyName) throws SQLException {
        String baseIdentifier; String finalIdentifier;
        String sanitizedCompanyName = companyName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        if (sanitizedCompanyName.length() >= 3) { baseIdentifier = sanitizedCompanyName.substring(0, Math.min(sanitizedCompanyName.length(), 3)); }
        else if (sanitizedCompanyName.length() > 0) { baseIdentifier = sanitizedCompanyName; while (baseIdentifier.length() < 3) { baseIdentifier += "x"; } }
        else { baseIdentifier = "com"; }
        String checkSql = "SELECT COUNT(*) FROM tenants WHERE CompanyIdentifier = ?";
        try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
            Random random = new Random(); int attempts = 0;
            do {
                finalIdentifier = baseIdentifier + String.format("%04d", random.nextInt(10000));
                psCheck.setString(1, finalIdentifier);
                try (ResultSet rs = psCheck.executeQuery()) { if (rs.next() && rs.getInt(1) == 0) return finalIdentifier; }
                if(++attempts > 50) return generateUUIDIdentifier(con);
            } while (true);
        }
    }

    private String generateUUIDIdentifier(Connection con) throws SQLException { 
        String identifier;
        String checkSql = "SELECT COUNT(*) FROM tenants WHERE CompanyIdentifier = ?";
        try (PreparedStatement psCheck = con.prepareStatement(checkSql)) {
            int attempts = 0;
            do {
                identifier = UUID.randomUUID().toString().replace("-", "").substring(0, 7).toLowerCase();
                psCheck.setString(1, identifier);
                try (ResultSet rs = psCheck.executeQuery()) { if (rs.next() && rs.getInt(1) == 0) return identifier; }
                if(++attempts > 10) throw new SQLException("Failed to generate unique UUID-based CompanyIdentifier.");
            } while (true);
        }
    }

    private int getNextTenantEmployeeNumber(Connection con, int tenantId) throws SQLException { 
        String sqlMax = "SELECT MAX(TenantEmployeeNumber) FROM EMPLOYEE_DATA WHERE TenantID = ?";
        try (PreparedStatement psMax = con.prepareStatement(sqlMax)) {
            psMax.setInt(1, tenantId);
            try (ResultSet rs = psMax.executeQuery()) { return rs.next() ? rs.getInt(1) + 1 : 1; }
        }
    }
    
    private Customer createStripeCustomer(HttpServletRequest request) throws StripeException {
        String cardholderName = request.getParameter("cardholderName");
        String adminEmail = request.getParameter("adminEmail");
        String companyName = request.getParameter("companyName");
        String companyPhone = request.getParameter("companyPhone");
        String billingAddressLine1 = request.getParameter("billingAddress");
        String billingCity = request.getParameter("billingCity");
        String billingState = request.getParameter("billingState");
        String billingZip = request.getParameter("billingZip");
        String stripePaymentMethodId = request.getParameter("stripePaymentMethodId");
        
        CustomerCreateParams.Address stripeBillingAddress = CustomerCreateParams.Address.builder()
            .setLine1(billingAddressLine1).setCity(billingCity).setState(billingState)
            .setPostalCode(billingZip).setCountry("US").build();
            
        return Customer.create(CustomerCreateParams.builder()
            .setName(cardholderName)
            .setEmail(adminEmail)
            .setPaymentMethod(stripePaymentMethodId)
            .setAddress(stripeBillingAddress)
            .setInvoiceSettings(CustomerCreateParams.InvoiceSettings.builder().setDefaultPaymentMethod(stripePaymentMethodId).build())
            .setDescription("Customer for " + companyName)
            .setPhone(companyPhone)
            .build());
    }

    private void saveTempDataToSession(HttpSession session, HttpServletRequest request, String customerId, String subscriptionId) {
        session.setAttribute("temp_stripe_customer_id_for_sca", customerId);
        session.setAttribute("temp_stripe_subscription_id_for_sca", subscriptionId);
        
        Map<String, String> tempFormData = new HashMap<>();
        tempFormData.put("companyName", request.getParameter("companyName"));
        tempFormData.put("adminFirstName", request.getParameter("adminFirstName"));
        tempFormData.put("adminLastName", request.getParameter("adminLastName"));
        tempFormData.put("adminEmail", request.getParameter("adminEmail"));
        tempFormData.put("adminPassword", request.getParameter("adminPassword"));
        tempFormData.put("companyPhone", request.getParameter("companyPhone"));
        tempFormData.put("companyAddress", request.getParameter("companyAddress"));
        tempFormData.put("companyCity", request.getParameter("companyCity"));
        tempFormData.put("companyState", request.getParameter("companyState"));
        tempFormData.put("companyZip", request.getParameter("companyZip"));
        tempFormData.put("browserTimeZoneId", request.getParameter("browserTimeZoneId"));
        tempFormData.put("appliedPromoCode", request.getParameter("appliedPromoCode"));
        session.setAttribute("temp_signup_original_form_data", tempFormData);
    }
    
    private void validateRequiredParameter(HttpServletRequest request, String paramName) throws IllegalArgumentException {
        String value = request.getParameter(paramName);
        if (value == null || value.trim().isEmpty()) {
            logger.warning("Server-side validation failed: Missing required parameter '" + paramName + "'.");
            throw new IllegalArgumentException("A required field is missing from the server: " + paramName);
        }
    }
}
