package se.flowkeeper.api.avatar;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * Serves stored avatar images. Deliberately public (see SecurityConfig) —
 * these are profile pictures rendered in plain <img> tags on both clients,
 * which can't attach an Authorization header. The filename itself is an
 * unguessable UUID, so this doesn't leak anything beyond "someone has this
 * exact avatar file".
 */
@RestController
public class AvatarFileController {

	private final AvatarStorageService avatarStorageService;

	public AvatarFileController(AvatarStorageService avatarStorageService) {
		this.avatarStorageService = avatarStorageService;
	}

	@GetMapping("/api/v1/avatars/{filename}")
	public ResponseEntity<Resource> getAvatar(@PathVariable String filename) {
		Resource resource = avatarStorageService.load(filename);
		return ResponseEntity.ok()
			.contentType(avatarStorageService.mediaTypeFor(filename))
			.cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
			.body(resource);
	}

}
