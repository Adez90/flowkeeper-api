package se.flowkeeper.api.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

	@Mock InAppNotificationRepository inAppNotificationRepository;
	@Mock CurrentUserResolver currentUserResolver;

	private final User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private NotificationService service() {
		return new NotificationService(inAppNotificationRepository, currentUserResolver);
	}

	@Test
	void listsTheCallersOwnNotificationsNewestFirst() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		InAppNotification notification = new InAppNotification(user, NotificationType.UNFINISHED_EVENT, "hi");
		when(inAppNotificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId())).thenReturn(List.of(notification));

		List<NotificationResponse> response = service().list(jwt);

		assertThat(response).hasSize(1);
		assertThat(response.get(0).message()).isEqualTo("hi");
		assertThat(response.get(0).type()).isEqualTo("UNFINISHED_EVENT");
	}

	@Test
	void markReadSetsTheReadTimestamp() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		InAppNotification notification = new InAppNotification(user, NotificationType.UNFINISHED_EVENT, "hi");
		UUID notificationId = UUID.randomUUID();
		when(inAppNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

		NotificationResponse response = service().markRead(jwt, notificationId);

		assertThat(response.readAt()).isNotNull();
	}

	@Test
	void rejectsMarkingSomeoneElsesNotificationRead() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		User someoneElse = new User("kc-subject-2", "Other Person", "other@example.com");
		InAppNotification notification = new InAppNotification(someoneElse, NotificationType.UNFINISHED_EVENT, "hi");
		UUID notificationId = UUID.randomUUID();
		when(inAppNotificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

		assertThatThrownBy(() -> service().markRead(jwt, notificationId))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void markReadUnknownIdIsNotFound() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		UUID notificationId = UUID.randomUUID();
		when(inAppNotificationRepository.findById(notificationId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().markRead(jwt, notificationId))
			.isInstanceOf(ResourceNotFoundException.class);
	}

}
