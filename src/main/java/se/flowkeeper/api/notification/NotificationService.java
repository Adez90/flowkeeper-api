package se.flowkeeper.api.notification;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

	private final InAppNotificationRepository inAppNotificationRepository;
	private final CurrentUserResolver currentUserResolver;

	public NotificationService(InAppNotificationRepository inAppNotificationRepository, CurrentUserResolver currentUserResolver) {
		this.inAppNotificationRepository = inAppNotificationRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional(readOnly = true)
	public List<NotificationResponse> list(Jwt jwt) {
		User user = currentUserResolver.require(jwt);
		return inAppNotificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
			.map(NotificationResponse::from)
			.toList();
	}

	@Transactional
	public NotificationResponse markRead(Jwt jwt, UUID notificationId) {
		User user = currentUserResolver.require(jwt);
		InAppNotification notification = inAppNotificationRepository.findById(notificationId)
			.orElseThrow(() -> new ResourceNotFoundException("No such notification: " + notificationId));

		if (!notification.getUser().equals(user)) {
			throw new AccessDeniedException("Not your notification");
		}

		notification.markRead();
		return NotificationResponse.from(notification);
	}

}
