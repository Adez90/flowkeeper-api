package se.flowkeeper.api.notification.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.event.EventRepository;
import se.flowkeeper.api.notification.NotificationDispatcher;
import se.flowkeeper.api.notification.NotificationType;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;
import se.flowkeeper.api.user.UserTimezones;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Nudges anyone who hasn't logged any activity yet today, once it's
 * morning in their own timezone — recovered from the old FlowKeeper apps'
 * UnUsedAccountJob, same per-user-timezone adaptation as
 * UnfinishedEventReminderJob.
 */
@Component
public class UnusedAccountReminderJob {

	private static final Logger log = LoggerFactory.getLogger(UnusedAccountReminderJob.class);
	private static final int REMINDER_HOUR = 8;

	private final UserRepository userRepository;
	private final EventRepository eventRepository;
	private final UserTimezones userTimezones;
	private final NotificationDispatcher dispatcher;
	private final Clock clock;

	public UnusedAccountReminderJob(UserRepository userRepository, EventRepository eventRepository,
			UserTimezones userTimezones, NotificationDispatcher dispatcher, Clock clock) {
		this.userRepository = userRepository;
		this.eventRepository = eventRepository;
		this.userTimezones = userTimezones;
		this.dispatcher = dispatcher;
		this.clock = clock;
	}

	@Scheduled(cron = "0 0 * * * *")
	@Transactional
	public void run() {
		int sent = 0;
		for (User user : userRepository.findByNotifyInAppTrueOrNotifyPushTrueOrNotifyEmailTrue()) {
			ZoneId zone = userTimezones.resolve(user);
			Clock userClock = clock.withZone(zone);
			if (LocalTime.now(userClock).getHour() != REMINDER_HOUR) {
				continue;
			}
			Instant startOfDay = LocalDate.now(userClock).atStartOfDay(zone).toInstant();
			if (eventRepository.existsByUser_IdAndStartedAtBetween(user.getId(), startOfDay, clock.instant())) {
				continue;
			}
			dispatcher.notify(user, NotificationType.UNUSED_ACCOUNT,
				"Nothing logged yet today",
				"You haven't logged an activity yet today — a quick check-in keeps your flow up to date.");
			sent++;
		}
		if (sent > 0) {
			log.info("Sent {} unused-account reminder(s)", sent);
		}
	}

}
