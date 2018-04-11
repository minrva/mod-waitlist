package org.folio.rest.utils;

import java.util.Properties;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.PasswordAuthentication;

public class NotifyEmail {

	/** smtp */
    private final String SMTP_USERNAME = "username";
    private final String SMTP_PASSWORD = "password";
    private final String SMTP_HOST = "smtp.gmail.com";
    private final String SMTP_PORT = "587";
    
	/** e-mail settings */
    private final String FROM_ADDRESS = "techcirc@folio.org";
    private final int NOTIFY_EXPIRE_TIME = 2;

    /** templates */
    private final String SUBJECT_TEMPLATE = "%s is now available.\n";
    private final String TXT_BODY_TEMPLATE = "Dear %s,\n\n"
            + "%s is available to pick up at the circulation desk. Please pick up your reserve within %d minutes. Thanks!\n\n"
            + "Sincerely,\n" 
            + "Circulation Desk";

	/** reserved item details */	
    public final String username; 
    public final String email; 
    public final String title;

    public NotifyEmail(String username, String email, String title) {
        this.username = username;
        this.email = email;
        this.title = title;    
    }

    public Message compose() throws MessagingException {
        InternetAddress fromAddress = new InternetAddress(FROM_ADDRESS);
        InternetAddress[] toAddresses = InternetAddress.parse(this.email);
        String subject = String.format(SUBJECT_TEMPLATE, this.title);
        String text = String.format(TXT_BODY_TEMPLATE, this.username, this.title, NOTIFY_EXPIRE_TIME);
        Session session = createSession(SMTP_USERNAME, SMTP_PASSWORD);
        Message message = createMessage(fromAddress, toAddresses, subject, text, session);
        System.out.println("username: " + this.username);
        System.out.println("email: " + this.email);
        System.out.println("title: " + this.title);
        return message;
    }

    private Message createMessage(InternetAddress fromAddress, InternetAddress[] toAddresses, String subject,
            String text, Session session) throws MessagingException {
        Message message = new MimeMessage(session);
        message.setFrom(fromAddress);
        message.setRecipients(Message.RecipientType.TO, toAddresses);
        message.setSubject(subject);
        message.setText(text);
        return message;
    }

    private Session createSession(String username, String password) {
        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);
        Authenticator authenticator = new javax.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };
        return Session.getInstance(props, authenticator);
    }
}
