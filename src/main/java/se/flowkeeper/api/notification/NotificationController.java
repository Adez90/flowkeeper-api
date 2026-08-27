package se.flowkeeper.api.notification;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	/** The in-app inbox — everything notify_in_app has ever populated for the caller, newest first. */
	@GetMapping("/api/v1/notifications")
	public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return notificationService.list(jwt);
	}

	@PatchMapping("/api/v1/notifications/{notificationId}/read")
	public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID notificationId) {
		return notificationService.markRead(jwt, notificationId);
	}

}
