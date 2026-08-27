package se.flowkeeper.api.notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.flowkeeper.api.user.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

	@Mock InAppNotificationRepository inAppNotificationRepository;
	@Mock PushNotificationSender pushNotificationSender;
	@Mock EmailNotificationSender emailNotificationSender;

	private NotificationDispatcher dispatcher() {
		return new NotificationDispatcher(inAppNotificationRepository, pushNotificationSender, emailNotificationSender);
	}

	@Test
	void deliversOnlyToChannelsTheUserOptedInto() {
		User user = new User("kc-1", "Anders", "anders@example.com");
		user.updateNotificationPreferences(true, false, false);

		dispatcher().notify(user, NotificationType.UNFINISHED_EVENT, "title", "message");

		ArgumentCaptor<InAppNotification> saved = ArgumentCaptor.forClass(InAppNotification.class);
		verify(inAppNotificationRepository).save(saved.capture());
		assertThat(saved.getValue().getMessage()).isEqualTo("message");
		assertThat(saved.getValue().getType()).isEqualTo(NotificationType.UNFINISHED_EVENT);
		verify(pushNotificationSender, never()).send(any(), any(), any());
		verify(emailNotificationSender, never()).send(any(), any(), any());
	}

	@Test
	void sendsPushOnlyWhenATokenIsOnFile() {
		User user = new User("kc-1", "Anders", "anders@example.com");
		user.updateNotificationPreferences(false, true, false);

		dispatcher().notify(user, NotificationType.UNFINISHED_EVENT, "title", "message");

		verify(pushNotificationSender, never()).send(any(), any(), any());

		user.updateExpoPushToken("ExponentPushToken[abc]");
		dispatcher().notify(user, NotificationType.UNFINISHED_EVENT, "title", "message");

		verify(pushNotificationSender).send("ExponentPushToken[abc]", "title", "message");
	}

	@Test
	void sendsEmailWhenOptedIn() {
		User user = new User("kc-1", "Anders", "anders@example.com");
		user.updateNotificationPreferences(false, false, true);

		dispatcher().notify(user, NotificationType.UNUSED_ACCOUNT, "title", "message");

		verify(emailNotificationSender).send("anders@example.com", "title", "message");
	}

	@Test
	void deliversNothingWhenNoChannelIsOptedIn() {
		User user = new User("kc-1", "Anders", "anders@example.com");

		dispatcher().notify(user, NotificationType.UNFINISHED_EVENT, "title", "message");

		verifyNoInteractions(inAppNotificationRepository, pushNotificationSender, emailNotificationSender);
	}

}
