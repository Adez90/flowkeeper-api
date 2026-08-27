package se.flowkeeper.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends over whatever SMTP relay app.notifications.smtp-* (spring.mail.*)
 * points at. No relay is configured by default — spring.mail.host defaults
 * to localhost, so a send simply fails and is logged, the same
 * fail-open-and-log posture as PushNotificationSender. Real delivery needs
 * an actual SMTP relay (SendGrid, SES, Mailgun, ...) configured via
 * flowkeeper-infra's environment, not code — see the Blueprint.
 */
@Service
public class EmailNotificationSender {

	private static final Logger log = LoggerFactory.getLogger(EmailNotificationSender.class);

	private final JavaMailSender mailSender;
	private final String fromAddress;

	public EmailNotificationSender(JavaMailSender mailSender, @Value("${app.notifications.from-email}") String fromAddress) {
		this.mailSender = mailSender;
		this.fromAddress = fromAddress;
	}

	public void send(String toAddress, String subject, String body) {
		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(fromAddress);
			message.setTo(toAddress);
			message.setSubject(subject);
			message.setText(body);
			mailSender.send(message);
		} catch (Exception e) {
			log.warn("Failed to send email notification: {}", e.getMessage());
		}
	}

}
