# HTTPS Setup Guide - Elastic Beanstalk Load Balancer

## Prerequisites
- ✅ Domain: precisiontimesolutions.com
- ✅ Route 53 hosted zone configured
- ✅ Elastic Beanstalk environment running

## Step 1: Request SSL Certificate (AWS Certificate Manager)

1. **Go to AWS Certificate Manager (ACM)**
   - Navigate to: https://console.aws.amazon.com/acm/
   - Ensure you're in **us-east-1** region (required for EB load balancers)

2. **Request Certificate**
   - Click "Request a certificate"
   - Choose "Request a public certificate"
   - Domain names:
     - `precisiontimesolutions.com`
     - `www.precisiontimesolutions.com`
   - Validation method: **DNS validation** (recommended)
   - Click "Request"

3. **Complete DNS Validation**
   - Click on the certificate request
   - For each domain, click "Create record in Route 53"
   - This automatically adds CNAME records for validation
   - Wait 5-30 minutes for validation to complete

## Step 2: Enable Load Balancer in Elastic Beanstalk

1. **Go to Elastic Beanstalk Console**
   - Navigate to your environment: `Precisiontimesolutions-env`

2. **Configuration → Load Balancer**
   - Click "Edit" in the Load Balancer section
   - **Load balancer type**: Application Load Balancer
   - **Listeners**:
     - Keep existing HTTP:80 listener
     - Add new listener: **HTTPS:443**
   - **SSL Certificate**: Select your ACM certificate
   - **SSL Policy**: ELBSecurityPolicy-TLS-1-2-2017-01 (recommended)
   - Click "Apply"

## Step 3: Update Route 53 DNS Records

1. **Go to Route 53 Console**
   - Navigate to your hosted zone: `precisiontimesolutions.com`

2. **Update A Records**
   - Edit existing A record for `precisiontimesolutions.com`
   - Change target to the new load balancer DNS name
   - Do the same for `www.precisiontimesolutions.com` if it exists

## Step 4: Configure HTTP to HTTPS Redirect (Optional but Recommended)

### Option A: Application-Level Redirect (Recommended)
Create a servlet filter to redirect HTTP to HTTPS:

```java
// Add to src/main/java/timeclock/filters/HttpsRedirectFilter.java
@WebFilter("/*")
public class HttpsRedirectFilter implements Filter {
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) 
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        
        if (!req.isSecure() && req.getHeader("X-Forwarded-Proto") != null) {
            String httpsUrl = "https://" + req.getServerName() + req.getRequestURI();
            if (req.getQueryString() != null) {
                httpsUrl += "?" + req.getQueryString();
            }
            res.sendRedirect(httpsUrl);
            return;
        }
        chain.doFilter(request, response);
    }
}
```

### Option B: Load Balancer Rules (Alternative)
- In EB Console → Load Balancer → Listeners
- Add rule to HTTP:80 listener to redirect to HTTPS:443

## Step 5: Test HTTPS Setup

1. **Wait for deployment** (5-10 minutes)
2. **Test URLs**:
   - https://precisiontimesolutions.com
   - https://www.precisiontimesolutions.com
   - http://precisiontimesolutions.com (should redirect to HTTPS)

## Step 6: Update Application Configuration

### Update any hardcoded HTTP URLs in your application:
- Check JSP files for HTTP links
- Update any API endpoints
- Verify form actions use HTTPS

### Security Headers (Optional Enhancement)
Add to web.xml:
```xml
<filter>
    <filter-name>SecurityHeadersFilter</filter-name>
    <filter-class>timeclock.filters.SecurityHeadersFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>SecurityHeadersFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>
```

## Troubleshooting

### Certificate Issues
- Ensure certificate is in **us-east-1** region
- Verify DNS validation completed successfully
- Check certificate status is "Issued"

### Load Balancer Issues
- Verify health checks are passing
- Check security groups allow HTTPS (port 443)
- Ensure target group is healthy

### DNS Issues
- Verify A records point to load balancer
- Use `nslookup` or `dig` to test DNS resolution
- Allow 5-10 minutes for DNS propagation

## Cost Impact
- **Load Balancer**: ~$18/month
- **SSL Certificate**: FREE (AWS Certificate Manager)
- **Data Transfer**: Standard EB rates

## Security Benefits
- ✅ Encrypted data transmission
- ✅ Browser security indicators
- ✅ SEO ranking improvement
- ✅ Required for modern web standards

## Next Steps After HTTPS
1. Update Google Analytics/Search Console for HTTPS
2. Update any external integrations (Stripe, etc.)
3. Consider implementing HSTS headers
4. Monitor SSL certificate expiration (auto-renewed by ACM)