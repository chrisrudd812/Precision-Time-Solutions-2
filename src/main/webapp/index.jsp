<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.sql.*" %>
<%@ page import="java.util.*" %>
<%@ page import="timeclock.db.DatabaseConnection" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    
    <!-- SEO Meta Tags -->
    <title>Employee Time Clock Software | Precision Time Solutions - Free Trial</title>
    <meta name="description" content="Professional employee time tracking software with GPS location tracking, automated payroll calculations, and comprehensive reporting. Start your free 30-day trial today!">
    <meta name="keywords" content="employee time clock, time tracking software, payroll software, employee attendance, GPS time tracking, punch clock software, workforce management">
    <meta name="author" content="Precision Time Solutions">
    
    <!-- Open Graph / Social Media Meta Tags -->
    <meta name="og:type" content="website">
    <meta name="og:title" content="Employee Time Clock Software | Precision Time Solutions">
    <meta name="og:description" content="Streamline your business with smart time tracking. Accurate, easy-to-use, and affordable online time clock solution for your team.">
    <meta name="og:url" content="<%= request.getRequestURL() %>">
    <meta name="og:site_name" content="Precision Time Solutions">
    
    <!-- Twitter Card Meta Tags -->
    <meta name="twitter:card" content="summary_large_image">
    <meta name="twitter:title" content="Employee Time Clock Software | Precision Time Solutions">
    <meta name="twitter:description" content="Professional time tracking software with GPS location tracking and automated payroll calculations.">
    
    <!-- Canonical URL -->
    <link rel="canonical" href="<%= request.getRequestURL() %>">
    
    <!-- Favicon and Icons -->
    <link rel="icon" href="<%= request.getContextPath() %>/favicon.ico" type="image/x-icon">
    <link rel="apple-touch-icon" href="<%= request.getContextPath() %>/images/apple-touch-icon.png">
    
    <!-- Stylesheets -->
    <link rel="stylesheet" href="css/landing-page.css?v=<%= System.currentTimeMillis() %>">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
    
    <!-- Structured Data for SEO -->
    <script type="application/ld+json">
    {
      "@context": "https://schema.org",
      "@type": "SoftwareApplication",
      "name": "Precision Time Solutions",
      "description": "Professional employee time tracking and attendance software with GPS location tracking, automated payroll calculations, and comprehensive reporting.",
      "applicationCategory": "BusinessApplication",
      "operatingSystem": "Web Browser",
      "offers": {
        "@type": "Offer",
        "price": "0",
        "priceCurrency": "USD",
        "description": "30-day free trial"
      },
      "aggregateRating": {
        "@type": "AggregateRating",
        "ratingValue": "4.8",
        "ratingCount": "150"
      }
    }
    </script>
</head>
<body>

    <header class="landing-header">
        <div class="container">
            <div class="logo">
                <img src="<%= request.getContextPath() %>/Images/logo.webp" alt="Precision Time Solutions Logo" class="logo-image-header">
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
                <h1>Stop Payroll Headaches with Professional Time Tracking</h1>
                <p class="subtitle">Eliminate buddy punching, reduce payroll errors by 95%, and save 8+ hours per week on time management. Trusted by 500+ businesses nationwide.</p>
                <div class="hero-benefits">
                    <div class="benefit-item"><i class="fas fa-check-circle"></i> GPS Location Tracking</div>
                    <div class="benefit-item"><i class="fas fa-check-circle"></i> Automated Overtime Calculations</div>
                    <div class="benefit-item"><i class="fas fa-check-circle"></i> Mobile and Desktop Ready</div>
                </div>
                <div class="cta-buttons">
                    <a href="#pricing" class="btn btn-primary btn-large">Start Free 30-Day Trial</a>
                    <a href="#features" class="btn btn-secondary btn-large">See How It Works</a>
                </div>
                <p class="trial-note">✓ Setup in under 5 minutes ✓ Cancel anytime</p>
            </div>
        </section>
        
        <section id="features" class="features-section">
            <div class="container">
                <h2>Powerful Features That Save You Time and Money</h2>
                <div class="features-grid">
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-map-marker-alt"></i></div>
                        <h3>GPS Location Tracking</h3>
                        <p>Prevent buddy punching with geofencing. Employees can optionally, only clock in from approved locations, ensuring accurate attendance records.</p>
                    </div>
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-calculator"></i></div>
                        <h3>Smart Payroll Integration</h3>
                        <p>Automatically calculate overtime, double-time, and PTO based on federal and state labor laws. Export directly to XLSX file.</p>
                    </div>
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-mobile-alt"></i></div>
                        <h3>Works Everywhere</h3>
                        <p>Clock in from any device - smartphone, tablet, or computer. Perfect for remote teams, field workers, and office employees.</p>
                    </div>
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-shield-alt"></i></div>
                        <h3>Prevent Time Theft</h3>
                        <p>Device restrictions and biometric verification on mobile devices, help ensure only authorized employees can punch in, saving you thousands in labor costs.</p>
                    </div>
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-chart-line"></i></div>
                        <h3>Detailed Analytics</h3>
                        <p>Track productivity trends, identify attendance patterns, and make data-driven decisions with comprehensive reporting tools.</p>
                    </div>
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-headset"></i></div>
                        <h3>Expert Support</h3>
                        <p>Get help when you need it with our responsive customer support team and comprehensive help documentation.</p>
                    </div>
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-calendar-check"></i></div>
                        <h3>Smart Scheduling and Tardy Tracking</h3>
                        <p>Create employee schedules and automatically track tardiness. Tardies are highlighted in red on time card, and the Tardy Report helps you maintain accountability and improve punctuality.</p>
                    </div>
                    <div class="feature-item">
                        <div class="feature-icon"><i class="fas fa-utensils"></i></div>
                        <h3>Automatic Lunch Deduction</h3>
                        <p>Set a time threshold and the system automatically deducts lunch breaks - no need for employees to punch out. Ensures compliance with labor laws while simplifying the punch process.</p>
                    </div>
                </div>
            </div>
        </section>
     
        <section class="testimonials-section">
            <div class="container">
                <h2>Join 500+ Happy Businesses</h2>
                <div class="testimonials-grid">
                    <div class="testimonial-item">
                        <div class="stars">★★★★★</div>
                        <p>"Precision Time Solutions cut our payroll processing time from 4 hours to 30 minutes. The GPS tracking eliminated our buddy punching problem completely."</p>
                        <div class="testimonial-author">- Sarah M., Restaurant Manager</div>
                    </div>
                    <div class="testimonial-item">
                        <div class="stars">★★★★★</div>
                        <p>"Setup was incredibly easy and the customer support is outstanding. We've saved over $2,000 per month in labor costs since switching."</p>
                        <div class="testimonial-author">- Mike R., Construction Company Owner</div>
                    </div>
                    <div class="testimonial-item">
                        <div class="stars">★★★★★</div>
                        <p>"Punching on a mobile phone works perfectly for our field teams. Automated overtime calculations have eliminated payroll errors completely."</p>
                        <div class="testimonial-author">- Jennifer L., HR Director</div>
                    </div>
                </div>
            </div>
        </section>
        
        <section class="app-preview-section">
            <div class="container">
                <h2>See It In Action - Simple and Powerful Interface</h2>
                <div class="preview-grid">
                    <div class="preview-item">
                        <div class="preview-text"><h4>Employee Management</h4><p>Add, Edit and Delete employee details or Reset PINs all in one place.</p></div>
                        <img src="<%= request.getContextPath() %>/Images/employees_main.webp" alt="Employee Management preview">
                    </div>
                    <div class="preview-item">
                        <div class="preview-text"><h4>Easy Time sheet Edits</h4><p>Administrators can easily review and edit employee time sheets with a clear, user-friendly interface.</p></div>
                        <img src="<%= request.getContextPath() %>/Images/punches_main.webp" alt="Edit Punches preview">
                    </div>
                    <div class="preview-item">
                        <div class="preview-text"><h4>Simple "Time Card" Interface</h4><p>Punching IN or OUT is as simple as clicking a button and details such as accrued PTO, schedule and department info, are available at a glance.</p></div>
                        <img src="<%= request.getContextPath() %>/Images/timecards_individual.webp" alt="Time Clock preview">
                    </div>
                	<div class="preview-item">
                        <div class="preview-text"><h4>Configure Punch Restrictions</h4><p>Restrict "Buddy Punching" with optional punch restrictions. Optional restrictions include Time / Lockout restrictions, Locations Restrictions (Geofencing), and Device Restrictioins.</p></div>
                        <img src="<%= request.getContextPath() %>/Images/settings_punch_restrictions.webp" alt="Location (geofence) Preview">
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

                <div class="value-props">
                    <div class="value-item">
                        <i class="fas fa-dollar-sign"></i>
                        <h4>Save Money</h4>
                        <p>Reduce labor costs by up to 8% by eliminating time theft and buddy punching</p>
                    </div>
                    <div class="value-item">
                        <i class="fas fa-clock"></i>
                        <h4>Save Time</h4>
                        <p>Cut payroll processing time by 90% with automated calculations and reporting</p>
                    </div>
                    <div class="value-item">
                        <i class="fas fa-shield-alt"></i>
                        <h4>Stay Compliant</h4>
                        <p>Automatic compliance with federal and state labor laws, including overtime rules</p>
                    </div>
                </div>
                
                <div class="pricing-grid">
                        <!-- Basic Plan -->
                    <div class="pricing-box">
                        <img src="<%= request.getContextPath() %>/Images/logo.webp" alt="Logo" class="pricing-logo">
                        <h3>Basic Plan</h3>
                        <p class="description">Essential time tracking for small teams</p>
                        <p class="price">$19.99<span>/ per month</span></p>
                        <div class="plan-features">• Time clock with GPS tracking<br>• Basic reporting<br>• Automated overtime calculations<br>• Up to 25 active employees</div>
                        <a href="signup_company_info.jsp?priceId=price_1S2mCSBtvyYfb2KWetkP2Tcf" class="btn btn-primary btn-block">Start Trial</a>
                    </div>
                    
                    <!-- Business Plan -->
                    <div class="pricing-box most-popular">
                        <div class="popular-badge">Most Popular</div>
                        <img src="<%= request.getContextPath() %>/Images/logo.webp" alt="Logo" class="pricing-logo">
                        <h3>Business Plan</h3>
                        <p class="description">Advanced features for growing businesses</p>
                        <p class="price">$29.99<span>/ per month</span></p>
                        <div class="plan-features">• Everything in Basic<br>• Employee messaging capabilities<br>• Advanced reporting and analytics<br>• Up to 50 active employees</div>
                        <a href="signup_company_info.jsp?priceId=price_1S2mCXBtvyYfb2KWGl0R6gXA" class="btn btn-primary btn-block">Start Trial</a>
                    </div>
                    
                    <!-- Pro Plan -->
                    <div class="pricing-box">
                        <img src="<%= request.getContextPath() %>/Images/logo.webp" alt="Logo" class="pricing-logo">
                        <h3>Pro Plan</h3>
                        <p class="description">Complete workforce management solution</p>
                        <p class="price">$39.99<span>/ per month</span></p>
                        <div class="plan-features">• Everything in Business<br>• Overtime by state capability<br>• Priority support<br>• Up to 100 employees</div>
                        <a href="signup_company_info.jsp?priceId=price_1S2mCbBtvyYfb2KWSQz9TkXt" class="btn btn-primary btn-block">Start Trial</a>
                    </div>
                </div>
            </div>
        </section>
    </main>

    <section class="final-cta-section">
        <div class="container">
            <h2>Ready to Transform Your Time Tracking?</h2>
            <p>Join hundreds of businesses who've already made the switch to smarter time management.</p>
            <a href="#pricing" class="btn btn-primary btn-large">Start Your Free Trial Now</a>
            <p class="guarantee">No setup fees • Cancel anytime</p>
        </div>
    </section>
    
    <footer class="landing-footer">
        <div class="container">
            <div class="footer-content">
                <div class="footer-section">
                    <h4>Product</h4>
                    <ul>
                        <li><a href="#features">Features</a></li>
                        <li><a href="#pricing">Pricing</a></li>
                        <li><a href="help.jsp">Help Center</a></li>
                    </ul>
                </div>
                <div class="footer-section">
                    <h4>Company</h4>
                    <ul>
                        <li><a href="contact.jsp">Contact Us</a></li>
                        <li><a href="privacy.jsp">Privacy Policy</a></li>
                        <li><a href="terms.jsp">Terms of Service</a></li>
                    </ul>
                </div>
                <div class="footer-section">
                    <h4>Support</h4>
                    <ul>
                        <li><a href="login.jsp">Login</a></li>
                        <li><a href="contact.jsp">Get Help</a></li>
                        <li><a href="help.jsp">Documentation</a></li>
                    </ul>
                </div>
            </div>
            <div class="footer-bottom">
                <p>&copy; <%= java.time.Year.now().getValue() %> Precision Time Solutions. All rights reserved.</p>
            </div>
        </div>
    </footer>



</body>
</html>