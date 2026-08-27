package se.flowkeeper.api.notification.reminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.flowkeeper.api.event.EventRepository;
import se.flowkeeper.api.notification.NotificationDispatcher;
import se.flowkeeper.api.notification.NotificationType;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;
import se.flowkeeper.api.user.UserTimezones;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UnusedAccountReminderJobTest {

	@Mock UserRepository userRepository;
	@Mock EventRepository eventRepository;
	@Mock NotificationDispatcher dispatcher;
	private final UserTimezones userTimezones = new UserTimezones();

	// 08:00 UTC — a UTC user's own local morning, sidestepping DST.
	private static final Instant MORNING = Instant.parse("2026-03-12T08:00:00Z");

	private UnusedAccountReminderJob job(Instant now) {
		return new UnusedAccountReminderJob(
			userRepository, eventRepository, userTimezones, dispatcher, Clock.fixed(now, ZoneOffset.UTC));
	}

	@Test
	void sendsAReminderAtTheUsersLocalMorningWhenNothingIsLoggedYetToday() {
		User user = new User("kc-1", "Anders", "anders@example.com");
		user.updateNotificationPreferences(true, false, false);
		when(userRepository.findByNotifyInAppTrueOrNotifyPushTrueOrNotifyEmailTrue()).thenReturn(List.of(user));
		when(eventRepository.existsByUser_IdAndStartedAtBetween(eq(user.getId()), any(), any())).thenReturn(false);

		job(MORNING).run();

		verify(dispatcher).notify(eq(user), eq(NotificationType.UNUSED_ACCOUNT), any(), any());
	}

	@Test
	void doesNothingOutsideTheReminderHour() {
		User user = new User("kc-1", "Anders", "anders@example.com");
		user.updateNotificationPreferences(true, false, false);
		when(userRepository.findByNotifyInAppTrueOrNotifyPushTrueOrNotifyEmailTrue()).thenReturn(List.of(user));

		job(Instant.parse("2026-03-12T20:00:00Z")).run();

		verify(dispatcher, never()).notify(any(), any(), any(), any());
	}

	@Test
	void doesNothingWhenTheUserHasAlreadyLoggedSomethingToday() {
		User user = new User("kc-1", "Anders", "anders@example.com");
		user.updateNotificationPreferences(true, false, false);
		when(userRepository.findByNotifyInAppTrueOrNotifyPushTrueOrNotifyEmailTrue()).thenReturn(List.of(user));
		when(eventRepository.existsByUser_IdAndStartedAtBetween(eq(user.getId()), any(), any())).thenReturn(true);

		job(MORNING).run();

		verify(dispatcher, never()).notify(any(), any(), any(), any());
	}

}
