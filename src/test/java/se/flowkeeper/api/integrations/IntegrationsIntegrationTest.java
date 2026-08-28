package se.flowkeeper.api.integrations;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import se.flowkeeper.api.AbstractIntegrationTest;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No real Google/Microsoft/Strava credentials exist in CI, same as
 * locally — so these assert the "not configured yet" behaviour (hidden
 * from the provider catalog, 503 on authorize) rather than a real OAuth
 * round-trip. That's the correct behaviour for an environment with none
 * of those set up.
 */
@AutoConfigureMockMvc
class IntegrationsIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private static final String SUBJECT = "kc-integrations-subject";
	private final Consumer<Jwt.Builder> asUser = b -> b
		.subject(SUBJECT).claim("name", "Integrations Tester").claim("email", "integrations-tester@example.com");

	private UUID accountId;

	@BeforeEach
	void registerAndFetchPersonalAccountId() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asUser))).andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		accountId = UUID.fromString(body.get("personalAccountId").asText());
	}

	@Test
	void everyProviderIsUnavailableWithNoCredentialsConfigured() throws Exception {
		JsonNode providers = objectMapper.readTree(mockMvc.perform(get("/api/v1/integrations/providers").with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());

		assertThat(providers.isArray()).isTrue();
		assertThat(providers.size()).isEqualTo(4);
		for (JsonNode provider : providers) {
			assertThat(provider.get("available").asBoolean())
				.as("%s should be unavailable with no credentials configured", provider.get("provider").asText())
				.isFalse();
		}
	}

	@Test
	void aFreshAccountHasNoConnectionsYet() throws Exception {
		JsonNode connections = objectMapper.readTree(mockMvc.perform(get("/api/v1/integrations/connections")
				.param("accountId", accountId.toString())
				.with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());

		assertThat(connections.isArray()).isTrue();
		assertThat(connections.size()).isEqualTo(0);
	}

	@Test
	void startingAuthorizationForAnUnconfiguredProviderFailsClearly() throws Exception {
		String requestBody = """
			{"accountId":"%s"}
			""".formatted(accountId);

		mockMvc.perform(post("/api/v1/integrations/connections/GOOGLE_CALENDAR/authorize")
				.with(jwt().jwt(asUser))
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isServiceUnavailable());
	}

	@Test
	void theOAuthCallbackIsReachableWithoutABearerTokenAndRedirectsOnAMissingState() throws Exception {
		// No bearer token at all — this is the one deliberately public route.
		// Also confirms the callback's @PathVariable enum binding accepts the
		// exact uppercase constant name IntegrationsService actually sends
		// providers as the redirect_uri.
		mockMvc.perform(get("/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback").param("code", "abc"))
			.andExpect(status().isFound())
			.andExpect(header().string("Location", "http://localhost:5173/app/integrations?connected=error"));
	}

	@Test
	void disconnectingAnUnknownConnectionReturnsNotFound() throws Exception {
		mockMvc.perform(delete("/api/v1/integrations/connections/" + UUID.randomUUID()).with(jwt().jwt(asUser)))
			.andExpect(status().isNotFound());
	}

}
