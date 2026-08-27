package se.flowkeeper.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.user.User;

/**
 * The one place that turns "notify this user of X" into actual delivery —
 * fans out to whichever of in-app/push/email the user has opted into.
 * Callers (the reminder jobs, and eventually anything else that wants to
 * nudge a user) never touch the individual senders directly.
 */
@Service
public class NotificationDispatcher {

	private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

	private final InAppNotificationRepository inAppNotificationRepository;
	private final PushNotificationSender pushNotificationSender;
	private final EmailNotificationSender emailNotificationSender;

	public NotificationDispatcher(InAppNotificationRepository inAppNotificationRepository,
			PushNotificationSender pushNotificationSender,
			EmailNotificationSender emailNotificationSender) {
		this.inAppNotificationRepository = inAppNotificationRepository;
		this.pushNotificationSender = pushNotificationSender;
		this.emailNotificationSender = emailNotificationSender;
	}

	@Transactional
	public void notify(User user, NotificationType type, String title, String message) {
		if (user.isNotifyInApp()) {
			inAppNotificationRepository.save(new InAppNotification(user, type, message));
		}
		if (user.isNotifyPush() && user.getExpoPushToken() != null) {
			pushNotificationSender.send(user.getExpoPushToken(), title, message);
		}
		if (user.isNotifyEmail()) {
			emailNotificationSender.send(user.getEmail(), title, message);
		}
		log.debug("Dispatched {} notification to user {} (inApp={}, push={}, email={})",
			type, user.getId(), user.isNotifyInApp(), user.isNotifyPush(), user.isNotifyEmail());
	}

}
