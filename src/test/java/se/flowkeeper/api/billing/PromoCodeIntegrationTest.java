package se.flowkeeper.api.billing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import se.flowkeeper.api.AbstractIntegrationTest;

import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Configures a fixed platform-admin allowlist just for this test class
 * (real deployments set app.admin.emails/ADMIN_EMAILS via
 * flowkeeper-infra) so both the admin and non-admin paths can be
 * exercised against a real, migrated Postgres.
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.admin.emails=admin-tester@example.com")
class PromoCodeIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private static final String ADMIN_SUBJECT = "kc-promo-admin";
	private static final String OWNER_SUBJECT = "kc-promo-owner";
	private final Consumer<Jwt.Builder> asAdmin = b -> b
		.subject(ADMIN_SUBJECT).claim("name", "Admin Tester").claim("email", "admin-tester@example.com");
	private final Consumer<Jwt.Builder> asOwner = b -> b
		.subject(OWNER_SUBJECT).claim("name", "Owner Tester").claim("email", "owner-tester@example.com");

	private UUID ownerAccountId;

	@BeforeEach
	void registerBothUsers() throws Exception {
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asAdmin)));
		MvcResult result = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		ownerAccountId = UUID.fromString(body.get("personalAccountId").asText());
	}

	@Test
	void nonAdminCannotGenerateOrListCodes() throws Exception {
		mockMvc.perform(post("/api/v1/admin/promo-codes")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"durationDays":90,"maxRedemptions":1}
					"""))
			.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/admin/promo-codes").with(jwt().jwt(asOwner)))
			.andExpect(status().isForbidden());
	}

	@Test
	void adminGeneratesListsAndTheOwnerRedeemsACode() throws Exception {
		MvcResult generateResult = mockMvc.perform(post("/api/v1/admin/promo-codes")
				.with(jwt().jwt(asAdmin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"durationDays":90,"maxRedemptions":1,"note":"Private trial"}
					"""))
			.andExpect(status().isOk())
			.andReturn();
		JsonNode generated = objectMapper.readTree(generateResult.getResponse().getContentAsString());
		String code = generated.get("code").asText();
		assertThat(code).matches("[A-Z0-9]{4}-[A-Z0-9]{4}");

		JsonNode list = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/promo-codes").with(jwt().jwt(asAdmin)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		assertThat(list.isArray()).isTrue();
		boolean sawIt = false;
		for (JsonNode c : list) {
			if (c.get("code").asText().equals(code)) {
				sawIt = true;
			}
		}
		assertThat(sawIt).isTrue();

		String redeemBody = """
			{"accountId":"%s","code":"%s"}
			""".formatted(ownerAccountId, code);
		JsonNode subscription = objectMapper.readTree(mockMvc.perform(post("/api/v1/billing/redeem-promo-code")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(redeemBody))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		assertThat(subscription.get("status").asText()).isEqualTo("ACTIVE");
		assertThat(subscription.get("provider").asText()).isEqualTo("PROMO_CODE");
		assertThat(subscription.get("priceId").isNull()).isTrue();

		// A second redemption of the same single-use code by the same account fails.
		mockMvc.perform(post("/api/v1/billing/redeem-promo-code")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(redeemBody))
			.andExpect(status().isConflict());
	}

	@Test
	void redeemingAnUnknownCodeReturnsNotFound() throws Exception {
		String redeemBody = """
			{"accountId":"%s","code":"NOSUCH-CODE"}
			""".formatted(ownerAccountId);

		mockMvc.perform(post("/api/v1/billing/redeem-promo-code")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(redeemBody))
			.andExpect(status().isNotFound());
	}

}
