package se.flowkeeper.api.registration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import se.flowkeeper.api.AbstractIntegrationTest;
import se.flowkeeper.api.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: real HTTP request through the actual security filter chain,
 * real Postgres, real Flyway-migrated schema. The JWT is synthetic (no
 * real Keycloak involved) but exercises the same OAuth2 resource server
 * path a real token would.
 */
@AutoConfigureMockMvc
class RegistrationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	UserRepository userRepository;

	@Test
	void firstCallProvisionsUserSecondCallIsIdempotent() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-integration-subject")
					.claim("name", "Test User")
					.claim("email", "test@example.com"))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("OWNER"))
			.andExpect(jsonPath("$.alreadyRegistered").value(false));

		assertThat(userRepository.findByKeycloakSubject("kc-integration-subject")).isPresent();

		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-integration-subject")
					.claim("name", "Test User")
					.claim("email", "test@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.alreadyRegistered").value(true));
	}

	@Test
	void unauthenticatedCallIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/registration"))
			.andExpect(status().isUnauthorized());
	}

}
