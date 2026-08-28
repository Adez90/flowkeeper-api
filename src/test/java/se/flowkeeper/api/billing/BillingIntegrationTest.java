package se.flowkeeper.api.billing;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
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
 * Exercises the billing endpoints against a real (migrated) Postgres.
 * STRIPE_SECRET_KEY/STRIPE_WEBHOOK_SECRET are unset in CI, same as
 * locally — so checkout/webhook calls here deliberately assert the
 * "not configured yet" 503, not a real Stripe round-trip. That's the
 * correct behaviour for an environment with no Stripe account wired up.
 */
@AutoConfigureMockMvc
class BillingIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private static final String SUBJECT = "kc-billing-subject";
	private final Consumer<Jwt.Builder> asUser = b -> b
		.subject(SUBJECT).claim("name", "Billing Tester").claim("email", "billing-tester@example.com");

	private UUID accountId;

	@BeforeEach
	void registerAndFetchPersonalAccountId() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asUser)))
			.andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		accountId = UUID.fromString(body.get("personalAccountId").asText());
	}

	@Test
	void listsTheSeededPricingCatalog() throws Exception {
		JsonNode plans = objectMapper.readTree(mockMvc.perform(get("/api/v1/billing/plans").with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());

		assertThat(plans.isArray()).isTrue();
		assertThat(plans.size()).isEqualTo(2);
		boolean sawPersonal = false;
		boolean sawBusiness = false;
		for (JsonNode plan : plans) {
			assertThat(plan.get("prices").size()).isGreaterThan(0);
			if (plan.get("code").asText().equals("personal")) {
				sawPersonal = true;
			}
			if (plan.get("code").asText().equals("business")) {
				sawBusiness = true;
				boolean sawPerSeat = false;
				for (JsonNode price : plan.get("prices")) {
					if (price.get("perSeat").asBoolean()) {
						sawPerSeat = true;
					}
				}
				assertThat(sawPerSeat).isTrue();
			}
		}
		assertThat(sawPersonal).isTrue();
		assertThat(sawBusiness).isTrue();
	}

	@Test
	void aFreshAccountHasNoSubscriptionYet() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/billing/subscription")
				.param("accountId", accountId.toString())
				.with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andReturn();

		String rawBody = result.getResponse().getContentAsString();
		assertThat(rawBody == null || rawBody.isBlank() || rawBody.equals("null")).isTrue();
	}

	@Test
	void checkoutSessionFailsClearlyWhenStripeIsNotConfigured() throws Exception {
		JsonNode plans = objectMapper.readTree(mockMvc.perform(get("/api/v1/billing/plans").with(jwt().jwt(asUser)))
			.andReturn().getResponse().getContentAsString());
		String flatPriceId = null;
		for (JsonNode plan : plans) {
			if (plan.get("code").asText().equals("personal")) {
				flatPriceId = plan.get("prices").get(0).get("id").asText();
			}
		}
		assertThat(flatPriceId).isNotNull();

		String requestBody = """
			{"accountId":"%s","priceId":"%s"}
			""".formatted(accountId, flatPriceId);

		mockMvc.perform(post("/api/v1/billing/checkout-session")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isServiceUnavailable());
	}

	@Test
	void checkoutSessionRejectsAPerSeatPriceWithNoSeatCountBeforeEverReachingStripe() throws Exception {
		JsonNode plans = objectMapper.readTree(mockMvc.perform(get("/api/v1/billing/plans").with(jwt().jwt(asUser)))
			.andReturn().getResponse().getContentAsString());
		String perSeatPriceId = null;
		for (JsonNode plan : plans) {
			if (plan.get("code").asText().equals("business")) {
				for (JsonNode price : plan.get("prices")) {
					if (price.get("perSeat").asBoolean()) {
						perSeatPriceId = price.get("id").asText();
						break;
					}
				}
			}
		}
		assertThat(perSeatPriceId).isNotNull();

		String requestBody = """
			{"accountId":"%s","priceId":"%s"}
			""".formatted(accountId, perSeatPriceId);

		mockMvc.perform(post("/api/v1/billing/checkout-session")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(requestBody))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theStripeWebhookEndpointIsReachableWithoutABearerTokenButFailsWithoutAConfiguredSecret() throws Exception {
		mockMvc.perform(post("/api/v1/billing/webhook/stripe")
				.header("Stripe-Signature", "t=1,v1=deadbeef")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			// Not 401/403 — SecurityConfig permits this path without auth.
			// 503 because STRIPE_WEBHOOK_SECRET isn't set in this environment.
			.andExpect(status().isServiceUnavailable());
	}

	// The OWNER-only rule on createCheckoutSession is covered at the unit
	// level (BillingServiceTest#createCheckoutSessionRejectsANonOwner) —
	// the registration flow used by this test class always makes the
	// caller OWNER of their own Personal account, so exercising the
	// rejection here would mean duplicating add-member integration
	// coverage that already exists for OrganisationController.

}
