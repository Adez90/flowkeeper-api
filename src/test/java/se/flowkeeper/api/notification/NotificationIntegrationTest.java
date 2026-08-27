package se.flowkeeper.api.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import se.flowkeeper.api.AbstractIntegrationTest;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * There's no user-facing way to create an in-app notification — only the
 * reminder jobs populate one, via NotificationDispatcher. So these tests
 * register a real user through the HTTP flow, then seed a notification
 * directly through the repository (the same thing NotificationDispatcher
 * itself does), and exercise the read/mark-read endpoints through MockMvc.
 *
 * Each test uses its own uniquely-suffixed subject — see
 * OrganisationIntegrationTest's class javadoc for why (AbstractIntegrationTest's
 * Postgres container is shared across the whole suite run).
 */
@AutoConfigureMockMvc
class NotificationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Autowired
	InAppNotificationRepository inAppNotificationRepository;

	@Test
	void listsTheCallersOwnNotificationsNewestFirst() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-list")
					.claim("name", "Notif List")
					.claim("email", "notif-list@example.com"))))
			.andExpect(status().isCreated());

		User user = userRepository.findByKeycloakSubject("kc-notif-list").orElseThrow();
		inAppNotificationRepository.save(new InAppNotification(user, NotificationType.UNFINISHED_EVENT, "First"));
		inAppNotificationRepository.save(new InAppNotification(user, NotificationType.UNUSED_ACCOUNT, "Second"));

		mockMvc.perform(get("/api/v1/notifications")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-list")
					.claim("name", "Notif List")
					.claim("email", "notif-list@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].message").value("Second"))
			.andExpect(jsonPath("$[0].readAt").doesNotExist());
	}

	@Test
	void markingANotificationReadPersists() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-read")
					.claim("name", "Notif Read")
					.claim("email", "notif-read@example.com"))))
			.andExpect(status().isCreated());

		User user = userRepository.findByKeycloakSubject("kc-notif-read").orElseThrow();
		InAppNotification notification = inAppNotificationRepository.save(
			new InAppNotification(user, NotificationType.UNFINISHED_EVENT, "Complete your activity"));

		mockMvc.perform(patch("/api/v1/notifications/" + notification.getId() + "/read")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-read")
					.claim("name", "Notif Read")
					.claim("email", "notif-read@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.readAt").exists());
	}

	@Test
	void cannotMarkSomeoneElsesNotificationRead() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-owner")
					.claim("name", "Owner")
					.claim("email", "notif-owner@example.com"))))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-other")
					.claim("name", "Other")
					.claim("email", "notif-other@example.com"))))
			.andExpect(status().isCreated());

		User owner = userRepository.findByKeycloakSubject("kc-notif-owner").orElseThrow();
		InAppNotification notification = inAppNotificationRepository.save(
			new InAppNotification(owner, NotificationType.UNFINISHED_EVENT, "Owner's own notification"));

		mockMvc.perform(patch("/api/v1/notifications/" + notification.getId() + "/read")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-other")
					.claim("name", "Other")
					.claim("email", "notif-other@example.com"))))
			.andExpect(status().isForbidden());
	}

	@Test
	void unknownNotificationIdIsNotFound() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-missing")
					.claim("name", "Missing")
					.claim("email", "notif-missing@example.com"))))
			.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/v1/notifications/" + UUID.randomUUID() + "/read")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notif-missing")
					.claim("name", "Missing")
					.claim("email", "notif-missing@example.com"))))
			.andExpect(status().isNotFound());
	}

}
