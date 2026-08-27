package se.flowkeeper.api.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String type, String message, Instant createdAt, Instant readAt) {

	public static NotificationResponse from(InAppNotification notification) {
		return new NotificationResponse(
			notification.getId(), notification.getType().name(), notification.getMessage(),
			notification.getCreatedAt(), notification.getReadAt());
	}

}
