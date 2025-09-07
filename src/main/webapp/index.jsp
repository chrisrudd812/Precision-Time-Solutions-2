<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.sql.*, java.util.*, timeclock.db.DatabaseConnection" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Welcome to Precision Time Solutions!</title>
    <link rel="icon" href="<%= request.getContextPath() %>/favicon.ico" type="image/x-icon">
    <link rel="stylesheet" href="css/landing-page.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
</head>
<body>

    <header class="landing-header">
        <div class="container">
            <div class="logo">
                <img src="<%= request.getContextPath() %>/Images/logo2.png" alt="Precision Time Solutions Logo" class="logo-image-header">
            </div>
            <div class="header-title">
                Precision Time Solutions
            </div>
            <nav class="landing-nav">
                 <a href="login.jsp" class="btn btn-secondary">Log In</a>
                 <a href="#pricing" class="btn btn-primary">Sign Up</a>
            </nav>
        </div>
    </header>

    <main>
        <section class="hero-section">
             <div class="container">
                <h1>Streamline Your Business with Smart Time Tracking</h1>
                <p class="subtitle">Accurate, easy-to-use, and affordable online time clock solution for your team.</p>
                <%-- [NEW] "View Pricing Plans" button that scrolls to the pricing section --%>
                <div class="cta-buttons">
                    <a href="#pricing" class="btn btn-primary btn-large">View Pricing Plans</a>
                </div>
            </div>
        </section>
        
        <section class="features-section">
            <div class="container">
                <h2>Everything You Need, Nothing You Don't</h2>
                <div class="features-grid">
                    <div class="feature-item"><div class="feature-icon"><i class="fas fa-clock"></i></div><h3>Real-Time Tracking</h3><p>Employees can clock in and out from any authorized device, from anywhere with a single click.</p></div>
                    <div class="feature-item"><div class="feature-icon"><i class="fas fa-calculator"></i></div><h3>Automated Calculations</h3><p>Overtime, double time, and PTO are calculated automatically, reducing payroll errors.</p></div>
                    <div class="feature-item"><div class="feature-icon"><i class="fas fa-chart-bar"></i></div><h3>Insightful Reporting</h3><p>Generate detailed reports on employee hours, attendance and PTO.</p></div>
                </div>
            </div>
        </section>
     
        <section class="app-preview-section">
            <div class="container">
                <h2>Simple and Intuitive Interface</h2>
                <div class="preview-grid">
                    <div class="preview-item">
                        <img src="<%= request.getContextPath() %>/Images/employees_main.png" alt="Employee Management preview">
                        <div class="preview-text"><h4>Employee Management</h4><p>Add, Edit and Delete employee details or Reset PINs all in one place.</p></div>
                    </div>
                    <div class="preview-item">
                        <img src="<%= request.getContextPath() %>/Images/punches_edit.png" alt="Edit Punches preview">
                        <div class="preview-text"><h4>Easy Timesheet Edits</h4><p>Administrators can easily review and edit employee timesheets with a clear, user-friendly interface.</p></div>
                    </div>
                    <div class="preview-item">
                        <img src="<%= request.getContextPath() %>/Images/timecards_individual.png" alt="Time Clock preview">
                        <div class="preview-text"><h4>Simple "Time Card" Interface</h4><p>Punching IN or OUT is as simple as clicking a button and details such as accrued PTO, schedule and department info, are available at a glance.</p></div>
                    </div>
                	<div class="preview-item">
                        <img src="<%= request.getContextPath() %>/Images/settings_punch_restrictions.png" alt="Location (geofence) Preview">
                        <div class="preview-text"><h4>Configure Punch Restrictions</h4><p>Restrict "Buddy Punching" with optional punch restrictions. Optional restrictions include Time / Lockout restrictions, Locations Restrictions (Geofencing), and Device Restrictioins.</p></div>
                    </div>
                </div>
            </div>
        </section>
        
        <section id="pricing" class="pricing-section">
            <div class="container">
                <h2>Find the Perfect Plan for Your Business</h2>
                
                <%-- [NEW] Promotional banner for the free trial --%>
                <div class="promo-banner">
                    <i class="fas fa-tag"></i> Limited Time Offer: Get a <strong>30-day free trial</strong> with code <span class="promo-code">30FREE</span>!
                </div>

                <div class="pricing-grid">
                    <%
                        List<Map<String, Object>> plans = new ArrayList<>();
                        String planSql = "SELECT planName, stripePriceId, price, features, maxUsers FROM subscription_plans ORDER BY price ASC";
                        try (Connection conn = DatabaseConnection.getConnection();
                             Statement stmt = conn.createStatement();
                             ResultSet rs = stmt.executeQuery(planSql)) {
                            while (rs.next()) {
                                Map<String, Object> plan = new HashMap<>();
                                String planName = rs.getString("planName");
                                plan.put("name", planName);
                                plan.put("priceId", rs.getString("stripePriceId"));
                                plan.put("price", String.format("%.2f", rs.getDouble("price")));
                                plan.put("maxUsers", rs.getInt("maxUsers"));
                                plan.put("title", "Precision Time " + rs.getInt("maxUsers"));
                                plan.put("description", "Time and Attendance Software for up to " + rs.getInt("maxUsers") + " employees.");
                                if ("Starter".equals(planName)) {
                                    plan.put("mostPopular", true);
                                } else {
                                    plan.put("mostPopular", false);
                                }
                                plans.add(plan);
                            }
                        } catch (Exception e) {
                            out.println("<p style='color:red; text-align:center;'>Could not load pricing plans at this time.</p>");
                            e.printStackTrace();
                        }
                        for (Map<String, Object> plan : plans) {
                            boolean isMostPopular = (boolean) plan.get("mostPopular");
                    %>
                    <div class="pricing-box <%= isMostPopular ? "most-popular" : "" %>">
                        <% if (isMostPopular) { %>
                            <div class="popular-badge">Most Popular</div>
                        <% } %>
                        <img src="<%= request.getContextPath() %>/Images/logo2.png" alt="Logo" class="pricing-logo">
                        <h3><%= plan.get("title") %></h3>
                        <p class="description"><%= plan.get("description") %></p>
                        <p class="price">$<%= plan.get("price") %><span>/ per month</span></p>
                        <a href="signup_company_info.jsp?priceId=<%= plan.get("priceId") %>" class="btn btn-primary btn-block">Start Trial</a>
                    </div>
                    <%
                        }
                    %>
                </div>
            </div>
        </section>
    </main>

    <footer class="landing-footer">
        <div class="container">
            <p>&copy; <%= java.time.Year.now().getValue() %> Precision Time Solutions. All rights reserved.</p>
        </div>
    </footer>

    <script>
        document.addEventListener("DOMContentLoaded", function() {
            if (window.location.hash) {
                document.documentElement.style.scrollBehavior = 'auto';
                setTimeout(function() {
                    window.scrollTo(0, 0);
                    document.documentElement.style.scrollBehavior = 'smooth';
                }, 1);
            }
        });
    </script>
</body>
</html>