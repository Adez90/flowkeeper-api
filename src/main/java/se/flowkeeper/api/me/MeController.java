package se.flowkeeper.api.me;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MeController {

	private final MeService meService;

	public MeController(MeService meService) {
		this.meService = meService;
	}

	/**
	 * A 404 here means this Keycloak subject has never registered —
	 * clients should call POST /api/v1/registration first, then retry.
	 */
	@GetMapping("/api/v1/me")
	public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return meService.currentUser(jwt)
			.map(ResponseEntity::ok)
			.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PatchMapping("/api/v1/me")
	public MeResponse updateProfile(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
		return meService.updateProfile(jwt, request);
	}

	@PatchMapping("/api/v1/me/notification-preferences")
	public MeResponse updateNotificationPreferences(
			@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
		return meService.updateNotificationPreferences(jwt, request);
	}

	/** Registers/replaces the device Expo push notifications should target — no-op for notify_push until this has been called at least once. */
	@PatchMapping("/api/v1/me/push-token")
	public MeResponse updatePushToken(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdatePushTokenRequest request) {
		return meService.updatePushToken(jwt, request);
	}

	/** Stores the uploaded image and points avatarUrl at it — replaces any previous server-stored avatar. */
	@PostMapping("/api/v1/me/avatar")
	public MeResponse uploadAvatar(@AuthenticationPrincipal Jwt jwt, @RequestParam("file") MultipartFile file) {
		return meService.uploadAvatar(jwt, file);
	}

}
