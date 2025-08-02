<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Welcome to Your Time Clock Solution!</title>
    <link rel="stylesheet" href="css/landing-page.css?v=1"> <%-- We will create this CSS file --%>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css" integrity="sha512-SnH5WK+bZxgPHs44uWIX+LLJAJ9/2PkPKZ5QiAj6Ta86w+fsb2TkcmfRyVX3pBnMFcV7oQPJkl9QevSCWr3W6A==" crossorigin="anonymous" referrerpolicy="no-referrer" />
    <link rel="icon" href="<%= request.getContextPath() %>/favicon.png" type="image/png">
    
    <%-- Optional: Link to a common font if you have one --%>
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
</head>
<body>

    <header class="landing-header">
        <div class="container">
            <div class="logo">
                <%-- You can put your logo image or text here --%>
                <i class="fas fa-clock"></i> Precision Time Solutions
            </div>
            <nav class="landing-nav">
                <a href="login.jsp" class="nav-link login-link">Log In</a>
                <a href="signup_company_info.jsp" class="nav-link signup-link-header btn btn-secondary">Sign Up</a>
            </nav>
        </div>
    </header>

    <main>
        <section class="hero-section">
            <div class="container">
                <h1>Streamline Your Business with Smart Time Tracking</h1>
                <p class="subtitle">Accurate, easy-to-use, and affordable online time clock solution for your team. Focus on your business, let us handle the hours.</p>
                <div class="cta-buttons">
                    <a href="signup_company_info.jsp" class="btn btn-primary btn-large">Get Started for $19.99/month</a>
                    <%-- Optional: <a href="#features" class="btn btn-outline">Learn More</a> --%>
                </div>
            </div>
        </section>

        <section id="features" class="features-section">
            <div class="container">
                <h2>Key Features</h2>
                <div class="features-grid">
                    <div class="feature-item">
                        <i class="fas fa-user-clock feature-icon"></i>
                        <h3>Easy Punch In/Out</h3>
                        <p>Simple interface for employees to clock in and out from any device.</p>
                    </div>
                    <div class="feature-item">
                        <i class="fas fa-tasks feature-icon"></i>
                        <h3>Admin Management</h3>
                        <p>Manage employees, departments, schedules, and view comprehensive reports.</p>
                    </div>
                    <div class="feature-item">
                        <i class="fas fa-file-invoice-dollar feature-icon"></i>
                        <h3>Payroll Ready</h3>
                        <p>Accurate hour tracking makes payroll processing a breeze.</p>
                    </div>
                     <div class="feature-item">
                        <i class="fas fa-cloud feature-icon"></i>
                        <h3>Cloud-Based</h3>
                        <p>Access your data anytime, anywhere. Secure and reliable.</p>
                    </div>
                </div>
            </div>
        </section>

        <section class="pricing-section">
            <div class="container">
                <h2>Simple, Transparent Pricing</h2>
                <div class="pricing-box">
                    <h3>Monthly Subscription</h3>
                    <p class="price"><span>$19.99</span>/month</p>
                    <p>Includes all features for your entire company.</p>
                    <ul>
                        <li><i class="fas fa-check"></i> Unlimited Employees</li>
                        <li><i class="fas fa-check"></i> Full Admin Dashboard</li>
                        <li><i class="fas fa-check"></i> Comprehensive Reporting</li>
                        <li><i class="fas fa-check"></i> Secure Data Storage</li>
                        <li><i class="fas fa-check"></i> Auto-Pay Option Available</li>
                    </ul>
                    <a href="signup_company_info.jsp" class="btn btn-primary btn-block">Sign Up Now</a>
                </div>
            </div>
        </section>

         <section class="final-cta-section">
            <div class="container">
                <h2>Ready to Simplify Your Time Tracking?</h2>
                <a href="signup_company_info.jsp" class="btn btn-secondary btn-large">Create Your Account</a>
            </div>
        </section>
    </main>

    <footer class="landing-footer">
        <div class="container">
            <p>&copy; <%= java.time.Year.now().getValue() %> YourTimeClock. All rights reserved.</p>
            <%-- Optional: Links to Privacy Policy, Terms of Service --%>
            <%-- <p><a href="privacy.jsp">Privacy Policy</a> | <a href="terms.jsp">Terms of Service</a></p> --%>
        </div>
    </footer>

</body>
</html>