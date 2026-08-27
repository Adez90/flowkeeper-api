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
 * Each test method uses its own uniquely-suffixed org/subjects — see
 * OrganisationIntegrationTest and StatisticsIntegrationTest's class javadocs
 * for why (AbstractIntegrationTest's Postgres container is shared across the
 * whole suite run, so cross-test state leakage is a real, previously-hit bug
 * here, not a hypothetical).
 */
@AutoConfigureMockMvc
class GroupStatisticsIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void groupStatisticsAggregateAcrossMembersOnceAboveMinimumSize() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-gs-owner-1", "Owner", "gs-owner-1@example.com");
		Consumer<Jwt.Builder> asCoach = userJwt("kc-gs-coach-1", "Coach", "gs-coach-1@example.com");
		Consumer<Jwt.Builder> asMemberA = userJwt("kc-gs-member-a-1", "Member A", "gs-member-a-1@example.com");
		Consumer<Jwt.Builder> asMemberB = userJwt("kc-gs-member-b-1", "Member B", "gs-member-b-1@example.com");
		Consumer<Jwt.Builder> asMemberC = userJwt("kc-gs-member-c-1", "Member C", "gs-member-c-1@example.com");

		for (Consumer<Jwt.Builder> user : java.util.List.of(asOwner, asCoach, asMemberA, asMemberB, asMemberC)) {
			mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(user))).andExpect(status().isCreated());
		}

		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());

		UUID groupId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations/" + accountId + "/groups")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Backend team"}
					""")).andReturn()).get("id").asText());

		addMember(accountId, asOwner, "gs-coach-1@example.com", "COACH", groupId);
		addMember(accountId, asOwner, "gs-member-a-1@example.com", "MEMBER", groupId);
		addMember(accountId, asOwner, "gs-member-b-1@example.com", "MEMBER", groupId);
		addMember(accountId, asOwner, "gs-member-c-1@example.com", "MEMBER", groupId);
		// 4 members total (coach + 3) -> at the minimum, aggregate should compute.

		logAndCompleteOneEvent(accountId, asMemberA, 3, 3); // sum 6 -> in flow
		logAndCompleteOneEvent(accountId, asMemberB, 1, 1); // sum 2 -> not in flow

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/group")
				.param("accountId", accountId.toString())
				.param("groupId", groupId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asCoach)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = readJson(result);
		assertThat(body.get("belowMinimumSize").asBoolean()).isFalse();
		assertThat(body.get("memberCount").asInt()).isEqualTo(4);
		assertThat(body.get("completedEvents").asLong()).isEqualTo(2);
		assertThat(body.get("flowPercentage").asDouble()).isEqualTo(50.0);
	}

	@Test
	void groupStatisticsWithheldBelowMinimumSize() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-gs-owner-2", "Owner", "gs-owner-2@example.com");
		Consumer<Jwt.Builder> asCoach = userJwt("kc-gs-coach-2", "Coach", "gs-coach-2@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asCoach))).andExpect(status().isCreated());

		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());

		UUID groupId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations/" + accountId + "/groups")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Tiny team"}
					""")).andReturn()).get("id").asText());

		addMember(accountId, asOwner, "gs-coach-2@example.com", "COACH", groupId);
		// Just the coach -> 1 member, below the minimum of 4.

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/group")
				.param("accountId", accountId.toString())
				.param("groupId", groupId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asCoach)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = readJson(result);
		assertThat(body.get("belowMinimumSize").asBoolean()).isTrue();
		assertThat(body.get("memberCount").asInt()).isEqualTo(1);
		assertThat(body.get("flowPercentage").isNull()).isTrue();
	}

	private void addMember(UUID accountId, Consumer<Jwt.Builder> asOwner, String email, String role, UUID groupId) throws Exception {
		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","role":"%s","groupId":"%s"}
					""".formatted(email, role, groupId)))
			.andExpect(status().isCreated());
	}

	private void logAndCompleteOneEvent(UUID accountId, Consumer<Jwt.Builder> asUser, int ingoing, int outgoing) throws Exception {
		MvcResult types = mockMvc.perform(get("/api/v1/event-types").param("accountId", accountId.toString()).with(jwt().jwt(asUser)))
			.andReturn();
		UUID eventTypeId = UUID.fromString(readJson(types).get(0).get("id").asText());

		MvcResult created = mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":%d,"ingoingNote":null}
					""".formatted(accountId, eventTypeId, ingoing)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID eventId = UUID.fromString(readJson(created).get("id").asText());

		mockMvc.perform(post("/api/v1/events/" + eventId + "/complete")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"outgoingEnergy":%d,"outgoingNote":null}
					""".formatted(outgoing)))
			.andExpect(status().isOk());
	}

	private Consumer<Jwt.Builder> userJwt(String subject, String name, String email) {
		return b -> b.subject(subject).claim("name", name).claim("email", email);
	}

	private JsonNode readJson(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
