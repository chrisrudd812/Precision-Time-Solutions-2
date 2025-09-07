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

    public static boolean sendEmail(String to, String subject, String body, Part filePart) {
        // [FIX] Using your exact environment variable names
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

            if (filePart == null || filePart.getSize() == 0) {
                message.setContent(body.replace("\n", "<br>"), "text/html; charset=utf-8");
            } else {
                Multipart multipart = new MimeMultipart();
                BodyPart messageBodyPart = new MimeBodyPart();
                messageBodyPart.setContent(body.replace("\n", "<br>"), "text/html; charset=utf-8");
                multipart.addBodyPart(messageBodyPart);

                BodyPart attachmentBodyPart = new MimeBodyPart();
                ByteArrayDataSource dataSource = new ByteArrayDataSource(filePart.getInputStream(), filePart.getContentType());
                attachmentBodyPart.setDataHandler(new DataHandler(dataSource));
                attachmentBodyPart.setFileName(filePart.getSubmittedFileName());
                multipart.addBodyPart(attachmentBodyPart);
                message.setContent(multipart);
            }

            Transport.send(message);
            logger.info("Email sent successfully to " + to);
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
        message.setContent(body.replace("\n", "<br>"), "text/html; charset=utf-8");

        logger.info("Attempting to send bulk email to " + recipients.size() + " recipients via " + smtpHost);
        Transport.send(message);
        logger.info("Bulk email sent successfully.");
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

            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(body.replace("\n", "<br>"), "text/html; charset=utf-8");
            multipart.addBodyPart(messageBodyPart);

            if (attachmentData != null && attachmentData.length > 0) {
                BodyPart attachmentBodyPart = new MimeBodyPart();
                ByteArrayDataSource dataSource = new ByteArrayDataSource(attachmentData, "application/pdf");
                attachmentBodyPart.setDataHandler(new DataHandler(dataSource));
                attachmentBodyPart.setFileName(attachmentFilename);
                multipart.addBodyPart(attachmentBodyPart);
            }
            
            message.setContent(multipart);
            Transport.send(message);
            logger.info("Email with attachment sent successfully to " + recipient);
        } catch (MessagingException e) {
            logger.log(Level.SEVERE, "Error sending email with attachment", e);
            throw e;
        }
    }
}