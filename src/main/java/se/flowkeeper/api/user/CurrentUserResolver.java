package se.flowkeeper.api.user;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import se.flowkeeper.api.common.ResourceNotFoundException;

/**
 * Resolves the authenticated Jwt to a FlowKeeper User profile. Used by
 * endpoints that require registration to already have happened —
 * Registration itself doesn't use this, since "not found" there means
 * "create one" rather than a 404.
 */
@Component
public class CurrentUserResolver {

	private final UserRepository userRepository;

	public CurrentUserResolver(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User require(Jwt jwt) {
		return userRepository.findByKeycloakSubject(jwt.getSubject())
			.orElseThrow(() -> new ResourceNotFoundException(
				"No FlowKeeper profile yet — call POST /api/v1/registration first."));
	}

}
