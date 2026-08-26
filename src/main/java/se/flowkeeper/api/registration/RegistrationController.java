package se.flowkeeper.api.registration;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RegistrationController {

	private final RegistrationService registrationService;

	public RegistrationController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	/**
	 * Called once by a client right after a user's first successful
	 * Keycloak login. Idempotent — safe to call again.
	 */
	@PostMapping("/api/v1/registration")
	public ResponseEntity<RegistrationResponse> register(Jwt jwt) {
		RegistrationResponse response = registrationService.registerCurrentUser(jwt);
		HttpStatus status = response.alreadyRegistered() ? HttpStatus.OK : HttpStatus.CREATED;
		return ResponseEntity.status(status).body(response);
	}

}
