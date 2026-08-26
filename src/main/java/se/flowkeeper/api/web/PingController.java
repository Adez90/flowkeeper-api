package se.flowkeeper.api.web;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * First endpoint behind auth — exists to confirm a Keycloak-issued token is
 * actually accepted end-to-end before any real domain endpoints are built.
 */
@RestController
public class PingController {

	@GetMapping("/api/v1/ping")
	public Map<String, Object> ping(Jwt jwt) {
		return Map.of(
			"message", "pong",
			"subject", jwt.getSubject(),
			"time", Instant.now().toString()
		);
	}

}
