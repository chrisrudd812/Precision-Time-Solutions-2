package timeclock.auth;

import java.io.IOException;
import java.util.List;
import java.util.Properties;
import jakarta.activation.DataHandler;
import jakarta.mail.Authenticator;
import jakarta.mail.BodyPart;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.servlet.http.Part;
import java.util.logging.Level;
import java.util.logging.Logger;


public class EmailService {

    private static final Logger logger = Logger.getLogger(EmailService.class.getName());

    public static boolean sendEmail(String to, String subject, String body) {
        return sendEmail(to, subject, body, null);
    }


    
    private static String createStyledEmailBody(String content) {
        return "<!DOCTYPE html>" +
               "<html><head><meta charset='UTF-8'><title>Precision Time Solutions</title></head>" +
               "<body style='margin:0;padding:0;font-family:Arial,sans-serif;background-color:#ffffff;width:100%;'>" +
               "<div style='width:100%;background-color:#ffffff;'>" +
               "<div style='background:linear-gradient(135deg,rgba(59,130,246,0.1),rgba(16,185,129,0.1));" +
               "padding:40px 30px 30px 30px;border-bottom:3px solid #16a34a;width:100%;box-sizing:border-box;'>" +
               "<h1 style='color:#065f46;margin:0;font-size:24px;font-weight:600;text-align:center;'>Precision Time Solutions</h1>" +
               "<p style='color:#6b7280;margin:0;font-size:14px;text-align:center;'>Professional Time & Attendance Management</p>" +
               "</div>" +
               "<div style='padding:30px;line-height:1.6;color:#374151;width:100%;box-sizing:border-box;'>" +
               content.replace("\n", "<br>") +
               "</div>" +
               "<div style='background-color:#f9fafb;padding:20px 30px;border-top:1px solid #e5e7eb;text-align:center;width:100%;box-sizing:border-box;'>" +
               "<p style='margin:0;font-size:12px;color:#6b7280;'>" +
               "© 2024 Precision Time Solutions. All rights reserved.<br>" +
               "<a href='https://precisiontimesolutions.com' style='color:#16a34a;text-decoration:none;'>" +
               "precisiontimesolutions.com</a></p>" +
               "</div></div></body></html>";
    }

    public static boolean sendEmail(String to, String subject, String body, Part filePart) {
        final String smtpUser = System.getenv("SMTP_USER");
        final String smtpPass = System.getenv("SMTP_PASSWORD");
        final String smtpFrom = System.getenv("SMTP_FROM_ADDRESS");
        final String smtpHost = System.getenv("SMTP_HOST");
        final String smtpPort = System.getenv("SMTP_PORT");

        if (smtpUser == null || smtpPass == null || smtpFrom == null || smtpHost == null || smtpPort == null) {
            logger.severe("Error: Missing one or more required SMTP environment variables for EmailService.");
            return false;
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Authenticator authenticator = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPass);
            }
        };

        Session session = Session.getInstance(props, authenticator);

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            message.setSubject(subject);

            String styledBody = createStyledEmailBody(body);
            Multipart multipart = new MimeMultipart();
            
            // Add HTML content
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(styledBody, "text/html; charset=utf-8");
            multipart.addBodyPart(messageBodyPart);
            
            // Add file attachment if provided
            if (filePart != null && filePart.getSize() > 0) {
                BodyPart attachmentBodyPart = new MimeBodyPart();
                ByteArrayDataSource dataSource = new ByteArrayDataSource(filePart.getInputStream(), filePart.getContentType());
                attachmentBodyPart.setDataHandler(new DataHandler(dataSource));
                attachmentBodyPart.setFileName(filePart.getSubmittedFileName());
                multipart.addBodyPart(attachmentBodyPart);
            }
            
            message.setContent(multipart);

            Transport.send(message);
            return true;

        } catch (MessagingException | IOException e) {
            logger.log(Level.SEVERE, "Error sending single-recipient email", e);
            return false;
        }
    }
    
    public static void send(List<String> recipients, String subject, String body) throws MessagingException {
        // [FIX] Using your exact environment variable names
        final String smtpUser = System.getenv("SMTP_USER");
        final String smtpPass = System.getenv("SMTP_PASSWORD");
        final String smtpFrom = System.getenv("SMTP_FROM_ADDRESS");
        final String smtpHost = System.getenv("SMTP_HOST");
        final String smtpPort = System.getenv("SMTP_PORT");

        if (smtpUser == null || smtpPass == null || smtpFrom == null || smtpHost == null || smtpPort == null) {
            logger.severe("Error: Missing one or more required SMTP environment variables for EmailService.");
            throw new MessagingException("Server email configuration is incomplete.");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Authenticator auth = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPass);
            }
        };

        Session session = Session.getInstance(props, auth);

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(smtpFrom));

        for (String recipient : recipients) {
            message.addRecipient(Message.RecipientType.BCC, new InternetAddress(recipient));
        }

        message.setSubject(subject);
        
        Multipart multipart = new MimeMultipart();
        
        // Add HTML content
        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setContent(createStyledEmailBody(body), "text/html; charset=utf-8");
        multipart.addBodyPart(messageBodyPart);
        
        message.setContent(multipart);

        Transport.send(message);
    }
    
    public static void send(String recipient, String subject, String body, byte[] attachmentData, String attachmentFilename) throws MessagingException {
        // [FIX] Using your exact environment variable names
        final String smtpUser = System.getenv("SMTP_USER");
        final String smtpPass = System.getenv("SMTP_PASSWORD");
        final String smtpFrom = System.getenv("SMTP_FROM_ADDRESS");
        final String smtpHost = System.getenv("SMTP_HOST");
        final String smtpPort = System.getenv("SMTP_PORT");

        if (smtpUser == null || smtpPass == null || smtpFrom == null || smtpHost == null || smtpPort == null) {
            logger.severe("Error: Missing one or more required SMTP environment variables for EmailService.");
            throw new MessagingException("Server email configuration is incomplete.");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", smtpHost);
        props.put("mail.smtp.port", smtpPort);
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");

        Authenticator auth = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUser, smtpPass);
            }
        };

        Session session = Session.getInstance(props, auth);

        try {
            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(smtpFrom));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
            message.setSubject(subject);

            Multipart multipart = new MimeMultipart();

            // Add HTML content
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(createStyledEmailBody(body), "text/html; charset=utf-8");
            multipart.addBodyPart(messageBodyPart);

            // Add file attachment if provided
            if (attachmentData != null && attachmentData.length > 0) {
                BodyPart attachmentBodyPart = new MimeBodyPart();
                ByteArrayDataSource dataSource = new ByteArrayDataSource(attachmentData, "application/pdf");
                attachmentBodyPart.setDataHandler(new DataHandler(dataSource));
                attachmentBodyPart.setFileName(attachmentFilename);
                multipart.addBodyPart(attachmentBodyPart);
            }
            
            message.setContent(multipart);
            Transport.send(message);
        } catch (MessagingException e) {
            logger.log(Level.SEVERE, "Error sending email with attachment", e);
            throw e;
        }
    }
}