<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Welcome to Precision Time Solutions!</title>
    <link rel="stylesheet" href="css/landing-page.css?v=2">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
    <link href="https://fonts.googleapis.com/css2?family=Roboto:wght@300;400;500;700&display=swap" rel="stylesheet">
    <style>
        .logo { display: flex; align-items: center; font-size: 1.5em; font-weight: 500; }
        .logo-image { height: 40px; margin-right: 10px; }
        .pricing-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(300px, 1fr)); gap: 30px; }
    </style>
</head>
<body>

    <header class="landing-header">
        <div class="container">
            <div class="logo">
                <img src="<%= request.getContextPath() %>/images/logo.png" alt="Precision Time Solutions Logo" class="logo-image">
                Precision Time Solutions
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
                <p class="subtitle">Accurate, easy-to-use, and affordable online time clock solution for your team.</p>
                <div class="cta-buttons">
                    <a href="#pricing" class="btn btn-primary btn-large">View Pricing Plans</a>
                </div>
            </div>
        </section>

        <section id="pricing" class="pricing-section">
            <div class="container">
                <h2>Simple, Transparent Pricing</h2>
                <div class="pricing-grid">
                    <div class="pricing-box">
                        <h3>Starter Plan</h3>
                        <p class="price"><span>$19.99</span>/month</p>
                        <p>Perfect for small teams and startups.</p>
                        <ul>
                            <li><i class="fas fa-check"></i> Up to <strong>25</strong> Employees</li>
                            <li><i class="fas fa-check"></i> Full Admin Dashboard</li>
                            <li><i class="fas fa-check"></i> Comprehensive Reporting</li>
                            <li><i class="fas fa-check"></i> Secure Data Storage</li>
                        </ul>
                        <a href="signup_company_info.jsp?priceId=price_123abc_starter" class="btn btn-primary btn-block">Sign Up Now</a>
                    </div>
                    <div class="pricing-box">
                        <h3>Business Plan</h3>
                        <p class="price"><span>$29.99</span>/month</p>
                        <p>Ideal for growing businesses.</p>
                        <ul>
                            <li><i class="fas fa-check"></i> Up to <strong>50</strong> Employees</li>
                            <li><i class="fas fa-check"></i> Full Admin Dashboard</li>
                            <li><i class="fas fa-check"></i> Comprehensive Reporting</li>
                            <li><i class="fas fa-check"></i> Secure Data Storage</li>
                        </ul>
                        <a href="signup_company_info.jsp?priceId=price_123abc_business" class="btn btn-primary btn-block">Sign Up Now</a>
                    </div>
                    <div class="pricing-box">
                        <h3>Pro Plan</h3>
                        <p class="price"><span>$39.99</span>/month</p>
                        <p>For established companies and large teams.</p>
                        <ul>
                            <li><i class="fas fa-check"></i> Up to <strong>100</strong> Employees</li>
                            <li><i class="fas fa-check"></i> Full Admin Dashboard</li>
                            <li><i class="fas fa-check"></i> Comprehensive Reporting</li>
                            <li><i class="fas fa-check"></i> Secure Data Storage</li>
                        </ul>
                        <a href="signup_company_info.jsp?priceId=price_123abc_pro" class="btn btn-primary btn-block">Sign Up Now</a>
                    </div>
                </div>
                <p style="text-align:center; margin-top:10px; font-size:0.8em;">Note: Replace the `priceId` in the links above with your actual Price IDs from your Stripe Dashboard.</p>
            </div>
        </section>

         <section class="final-cta-section">
            <div class="container">
                <h2>Ready to Simplify Your Time Tracking?</h2>
                <a href="#pricing" class="btn btn-secondary btn-large">Choose Your Plan</a>
             </div>
        </section>
    </main>

    <footer class="landing-footer">
        <div class="container">
            <p>&copy; <%= java.time.Year.now().getValue() %> Precision Time Solutions. All rights reserved.</p>
        </div>
    </footer>

</body>
</html>