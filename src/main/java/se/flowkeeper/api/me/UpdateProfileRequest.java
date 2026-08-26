package se.flowkeeper.api.me;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
	@NotBlank @Size(max = 200) String displayName,
	/** Must be a real IANA zone id (e.g. "Europe/Stockholm") — checked in the service. */
	@NotBlank @Size(max = 50) String timezone,
	@Size(max = 10) String locale,
	@Size(max = 500) String avatarUrl
) {
}
