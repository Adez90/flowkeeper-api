package se.flowkeeper.api.statistics;

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

@AutoConfigureMockMvc
class StatisticsIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private static final String SUBJECT = "kc-stats-subject";
	private final Consumer<Jwt.Builder> asUser = b -> b
		.subject(SUBJECT).claim("name", "Stats Tester").claim("email", "stats-tester@example.com");

	private UUID accountId;
	private UUID eventTypeId;

	@BeforeEach
	void registerAndFetchAccountAndType() throws Exception {
		MvcResult registration = mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asUser))).andReturn();
		accountId = UUID.fromString(
			objectMapper.readTree(registration.getResponse().getContentAsString()).get("personalAccountId").asText());

		MvcResult types = mockMvc.perform(get("/api/v1/event-types").param("accountId", accountId.toString()).with(jwt().jwt(asUser)))
			.andReturn();
		eventTypeId = UUID.fromString(objectMapper.readTree(types.getResponse().getContentAsString()).get(0).get("id").asText());
	}

	@Test
	void personalStatisticsReflectOneCompletedAndOneOpenEvent() throws Exception {
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
		assertThat(body.get("byType").get(0).get("count").asLong()).isEqualTo(2);
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
