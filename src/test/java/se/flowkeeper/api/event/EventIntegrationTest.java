package se.flowkeeper.api.event;

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

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class EventIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private static final String SUBJECT = "kc-event-subject";
	private final Consumer<Jwt.Builder> asUser = b -> b
		.subject(SUBJECT).claim("name", "Event Tester").claim("email", "event-tester@example.com");

	private UUID accountId;

	@BeforeEach
	void registerAndFetchPersonalAccountId() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asUser)))
			.andReturn();
		JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
		accountId = UUID.fromString(body.get("personalAccountId").asText());
	}

	@Test
	void createListCompleteAndListAgain() throws Exception {
		JsonNode types = objectMapper.readTree(mockMvc.perform(
				get("/api/v1/event-types").param("accountId", accountId.toString()).with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString());
		assertThat(types.isArray()).isTrue();
		assertThat(types.size()).isGreaterThan(0);
		UUID eventTypeId = UUID.fromString(types.get(0).get("id").asText());

		String createBody = """
			{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":4,"ingoingNote":"heading into a meeting"}
			""".formatted(accountId, eventTypeId);

		MvcResult createResult = mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(createBody))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("OPEN"))
			.andReturn();
		UUID eventId = UUID.fromString(
			objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText());

		mockMvc.perform(get("/api/v1/events").param("accountId", accountId.toString()).param("status", "OPEN")
				.with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(eventId.toString()));

		String completeBody = """
			{"outgoingEnergy":2,"outgoingNote":"drained afterwards"}
			""";
		mockMvc.perform(post("/api/v1/events/" + eventId + "/complete")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(completeBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.outgoingEnergy").value(2));

		mockMvc.perform(get("/api/v1/events").param("accountId", accountId.toString()).param("status", "OPEN")
				.with(jwt().jwt(asUser)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	/**
	 * Uses its own subject/account rather than the class's shared one — the
	 * shared account above accumulates events across every @Test method in
	 * this class (same Postgres container, no per-test rollback), and this
	 * test's assertions on exactly which events exist would break if it ran
	 * after (or before) another test that also touches that account.
	 */
	@Test
	void logsAFullyHistoricalActivityAlreadyCompletedInOneCall() throws Exception {
		Consumer<Jwt.Builder> asHistoricalUser = b -> b
			.subject("kc-event-historical").claim("name", "Historical Tester").claim("email", "event-historical@example.com");
		MvcResult registration = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asHistoricalUser))).andReturn();
		UUID historicalAccountId = UUID.fromString(
			objectMapper.readTree(registration.getResponse().getContentAsString()).get("personalAccountId").asText());

		JsonNode types = objectMapper.readTree(mockMvc.perform(
				get("/api/v1/event-types").param("accountId", historicalAccountId.toString()).with(jwt().jwt(asHistoricalUser)))
			.andReturn().getResponse().getContentAsString());
		UUID eventTypeId = UUID.fromString(types.get(0).get("id").asText());

		Instant startedAt = Instant.now().minusSeconds(7200);
		Instant completedAt = Instant.now().minusSeconds(3600);
		String body = """
			{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":3,"ingoingNote":"felt rushed",
			 "startedAt":"%s","outgoingEnergy":4,"outgoingNote":"better after","completedAt":"%s"}
			""".formatted(historicalAccountId, eventTypeId, startedAt, completedAt);

		mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asHistoricalUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("COMPLETED"))
			.andExpect(jsonPath("$.outgoingEnergy").value(4))
			.andExpect(jsonPath("$.outgoingNote").value("better after"));

		// Already completed the moment it's logged — never shows up as OPEN.
		mockMvc.perform(get("/api/v1/events").param("accountId", historicalAccountId.toString()).param("status", "OPEN")
				.with(jwt().jwt(asHistoricalUser)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$").isEmpty());
	}

	@Test
	void rejectsAStartTimeInTheFuture() throws Exception {
		Consumer<Jwt.Builder> asFutureUser = b -> b
			.subject("kc-event-future").claim("name", "Future Tester").claim("email", "event-future@example.com");
		MvcResult registration = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asFutureUser))).andReturn();
		UUID futureAccountId = UUID.fromString(
			objectMapper.readTree(registration.getResponse().getContentAsString()).get("personalAccountId").asText());

		JsonNode types = objectMapper.readTree(mockMvc.perform(
				get("/api/v1/event-types").param("accountId", futureAccountId.toString()).with(jwt().jwt(asFutureUser)))
			.andReturn().getResponse().getContentAsString());
		UUID eventTypeId = UUID.fromString(types.get(0).get("id").asText());

		Instant tomorrow = Instant.now().plusSeconds(86_400);
		String body = """
			{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":3,"ingoingNote":null,"startedAt":"%s"}
			""".formatted(futureAccountId, eventTypeId, tomorrow);

		mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asFutureUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest());
	}

	@Test
	void cannotCreateEventForAnAccountYouAreNotAMemberOf() throws Exception {
		UUID foreignAccountId = UUID.randomUUID();
		String body = """
			{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":3,"ingoingNote":null}
			""".formatted(foreignAccountId, UUID.randomUUID());

		mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isForbidden());
	}

}
