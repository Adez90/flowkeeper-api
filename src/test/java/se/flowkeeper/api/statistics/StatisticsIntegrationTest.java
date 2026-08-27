package se.flowkeeper.api.statistics;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
 * Each test method sets up its own uniquely-suffixed account (not a shared
 * @BeforeEach) — AbstractIntegrationTest's Postgres container is shared
 * across the whole suite run with no per-test rollback, and both tests here
 * query the default "today" DAY period, so a shared account would let one
 * test's events leak into the other's totals (confirmed live: exactly this,
 * "expected: 2L but was: 3L" once a second test method was added sharing the
 * first one's fixed subject).
 */
@AutoConfigureMockMvc
class StatisticsIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private Consumer<Jwt.Builder> asUser;
	private UUID accountId;
	private UUID eventTypeId;

	@Test
	void personalStatisticsReflectOneCompletedAndOneOpenEvent() throws Exception {
		setUpAccount("kc-stats-subject-1", "stats-tester-1@example.com");

		UUID completedEventId = createEvent(4);
		completeEvent(completedEventId, 2); // delta -2
		createEvent(3); // left open

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/personal")
				.param("accountId", accountId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("totalEvents").asLong()).isEqualTo(2);
		assertThat(body.get("completedEvents").asLong()).isEqualTo(1);
		assertThat(body.get("openEvents").asLong()).isEqualTo(1);
		assertThat(body.get("averageIngoingEnergy").asDouble()).isEqualTo(3.5);
		assertThat(body.get("averageEnergyDelta").asDouble()).isEqualTo(-2.0);
		// ingoing 4 + outgoing 2 = 6, inside the "in flow" 4-6 band.
		assertThat(body.get("flowPercentage").asDouble()).isEqualTo(100.0);
		assertThat(body.get("byType").get(0).get("count").asLong()).isEqualTo(2);
	}

	@Test
	void flowPercentageOnlyCountsCompletedEventsInTheFourToSixBand() throws Exception {
		setUpAccount("kc-stats-subject-2", "stats-tester-2@example.com");

		UUID inFlowEventId = createEvent(3);
		completeEvent(inFlowEventId, 3); // sum 6 -> in flow

		UUID notInFlowEventId = createEvent(1);
		completeEvent(notInFlowEventId, 1); // sum 2 -> not in flow

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/personal")
				.param("accountId", accountId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		assertThat(body.get("completedEvents").asLong()).isEqualTo(2);
		assertThat(body.get("flowPercentage").asDouble()).isEqualTo(50.0);
	}

	private void setUpAccount(String subject, String email) throws Exception {
		asUser = b -> b.subject(subject).claim("name", "Stats Tester").claim("email", email);

		MvcResult registration = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asUser))).andReturn();
		accountId = UUID.fromString(
			objectMapper.readTree(registration.getResponse().getContentAsString()).get("personalAccountId").asText());

		MvcResult types = mockMvc.perform(get("/api/v1/event-types").param("accountId", accountId.toString()).with(jwt().jwt(asUser)))
			.andReturn();
		eventTypeId = UUID.fromString(objectMapper.readTree(types.getResponse().getContentAsString()).get(0).get("id").asText());
	}

	private UUID createEvent(int ingoingEnergy) throws Exception {
		String body = """
			{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":%d,"ingoingNote":null}
			""".formatted(accountId, eventTypeId, ingoingEnergy);
		MvcResult result = mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
	}

	private void completeEvent(UUID eventId, int outgoingEnergy) throws Exception {
		String body = """
			{"outgoingEnergy":%d,"outgoingNote":null}
			""".formatted(outgoingEnergy);
		mockMvc.perform(post("/api/v1/events/" + eventId + "/complete")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isOk());
	}

}
