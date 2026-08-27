package se.flowkeeper.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Sends to Expo's push service directly over HTTP — no SDK dependency
 * needed, and Expo-managed push doesn't require a service credential for a
 * standard send (unlike raw FCM/APNs). A delivery failure is logged and
 * swallowed, never propagated: one broken token shouldn't stop the
 * in-app/email channels for the same user, or the next user in a reminder
 * job's loop.
 */
@Service
public class PushNotificationSender {

	private static final Logger log = LoggerFactory.getLogger(PushNotificationSender.class);

	private final RestClient restClient = RestClient.create("https://exp.host");

	public void send(String expoPushToken, String title, String body) {
		try {
			restClient.post()
				.uri("/--/api/v2/push/send")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("to", expoPushToken, "title", title, "body", body))
				.retrieve()
				.toBodilessEntity();
		} catch (Exception e) {
			log.warn("Failed to send push notification: {}", e.getMessage());
		}
	}

}
