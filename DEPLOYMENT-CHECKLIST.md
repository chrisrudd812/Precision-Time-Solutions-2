# AWS Deployment Checklist

## Pre-Deployment Steps
- [ ] Test application locally (verify all features work)
- [ ] Commit and push all changes to Git
- [ ] Clean project in Eclipse (Project → Clean)

## Creating WAR File in Eclipse

### Method 1: Export WAR
1. [ ] Right-click on project in Eclipse
2. [ ] Select **Export** → **Web** → **WAR file**
3. [ ] Choose destination (e.g., Desktop)
4. [ ] Name: `Clockify.war`
5. [ ] Click **Finish**

### Method 2: Maven Build (if using Maven)
1. [ ] Right-click project → **Run As** → **Maven build...**
2. [ ] Goals: `clean package`
3. [ ] Click **Run**
4. [ ] WAR file created in `target/` folder

## AWS Elastic Beanstalk Deployment

### Upload New Version
1. [ ] Go to **AWS Console** → **Elastic Beanstalk**
2. [ ] Select your environment: **PTS-env-1**
3. [ ] Click **Upload and deploy**
4. [ ] Choose your WAR file
5. [ ] Version label: `v1.x-YYYY-MM-DD` (e.g., v1.2-2024-12-19)
6. [ ] Click **Deploy**

### Monitor Deployment
1. [ ] Wait for deployment to complete (green checkmark)
2. [ ] Check **Health** tab (should be "Ok")
3. [ ] Check **Logs** if any issues

## Post-Deployment Testing
- [ ] Test AWS URL: `http://pts-env-1.eba-jdv94b9h.us-east-1.elasticbeanstalk.com`
- [ ] Test domain: `http://precisiontimesolutions.com`
- [ ] Test key features:
  - [ ] Login page loads
  - [ ] Settings page displays properly
  - [ ] Time Day Restrictions page works
  - [ ] Database connections work
- [ ] Check browser console for errors

## Troubleshooting
- [ ] If deployment fails, check **Logs** → **Request Logs** → **Last 100 Lines**
- [ ] If 500 errors, check application logs for database connection issues
- [ ] If CSS/styling issues, clear browser cache

## Notes
- WAR file should be under 512MB for Elastic Beanstalk
- Deployment typically takes 2-5 minutes
- Keep previous version available for rollback if needed