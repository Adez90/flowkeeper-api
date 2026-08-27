package se.flowkeeper.api.notification.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.event.EventRepository;
import se.flowkeeper.api.event.EventStatus;
import se.flowkeeper.api.notification.NotificationDispatcher;
import se.flowkeeper.api.notification.NotificationType;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;
import se.flowkeeper.api.user.UserTimezones;

import java.time.Clock;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Nudges anyone with an event they haven't completed yet, once it's evening
 * in their own timezone — recovered from the old FlowKeeper apps'
 * UnfinishedEventJob (which ran on a single fixed server hour for
 * everyone; this version checks each user's own local time instead, since
 * we already track per-user timezones for statistics ranges).
 *
 * Runs hourly rather than computing a per-user delay: with an hourly cron,
 * REMINDER_HOUR only ever matches once per user per day, so this fires
 * exactly once — no separate "already sent today" bookkeeping needed.
 */
@Component
public class UnfinishedEventReminderJob {

	private static final Logger log = LoggerFactory.getLogger(UnfinishedEventReminderJob.class);
	private static final int REMINDER_HOUR = 18;

	private final UserRepository userRepository;
	private final EventRepository eventRepository;
	private final UserTimezones userTimezones;
	private final NotificationDispatcher dispatcher;
	private final Clock clock;

	public UnfinishedEventReminderJob(UserRepository userRepository, EventRepository eventRepository,
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
			if (LocalTime.now(clock.withZone(zone)).getHour() != REMINDER_HOUR) {
				continue;
			}
			if (!eventRepository.existsByUser_IdAndStatus(user.getId(), EventStatus.OPEN)) {
				continue;
			}
			dispatcher.notify(user, NotificationType.UNFINISHED_EVENT,
				"You have an activity still open",
				"Don't forget to complete the activity you started earlier today.");
			sent++;
		}
		if (sent > 0) {
			log.info("Sent {} unfinished-event reminder(s)", sent);
		}
	}

}
