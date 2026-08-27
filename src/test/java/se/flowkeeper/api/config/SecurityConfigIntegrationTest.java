package se.flowkeeper.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import se.flowkeeper.api.AbstractIntegrationTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression guard for a real production bug: SecurityConfig once had no
 * CorsConfigurationSource at all, which passed every unit and integration
 * test clean (neither Mockito nor MockMvc-through-real-HTTP enforces
 * browser CORS) and only surfaced once a real browser called the deployed
 * API from staging.flowkeeper.se — "Couldn't load your account" right
 * after a successful login. MockMvc does dispatch through the actual
 * CorsFilter Spring Security registers, so it can catch a regression here
 * even though it can't catch the original bug's category in general.
 */
@AutoConfigureMockMvc
class SecurityConfigIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void preflightFromAllowedOriginIsPermitted() throws Exception {
		mockMvc.perform(options("/actuator/health")
				.header("Origin", "http://localhost:5173")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isOk())
			.andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
	}

	@Test
	void preflightFromUnknownOriginIsRejected() throws Exception {
		mockMvc.perform(options("/actuator/health")
				.header("Origin", "https://not-flowkeeper.example")
				.header("Access-Control-Request-Method", "GET"))
			.andExpect(status().isForbidden());
	}

}
