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

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each test method uses its own uniquely-suffixed org/subjects — see
 * OrganisationIntegrationTest's class javadoc for why (AbstractIntegrationTest's
 * Postgres container is shared across the whole suite run, so cross-test state
 * leakage is a real, previously-hit bug here, not a hypothetical).
 */
@AutoConfigureMockMvc
class OrganisationTypeStatisticsIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void organisationTypeStatisticsAggregateAcrossMembersOnceAboveTenMembers() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-ots-owner-1", "Owner", "ots-owner-1@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());

		// 10 members total (owner + 9) -> at the minimum, by-type breakdown should compute.
		List<Consumer<Jwt.Builder>> members = new java.util.ArrayList<>();
		for (int i = 1; i <= 9; i++) {
			Consumer<Jwt.Builder> member = userJwt("kc-ots-member-" + i, "Member " + i, "ots-member-" + i + "@example.com");
			members.add(member);
			mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(member))).andExpect(status().isCreated());
			addMember(accountId, asOwner, "ots-member-" + i + "@example.com");
		}

		logAndCompleteOneEvent(accountId, members.get(0), 3, 3); // sum 6 -> in flow
		logAndCompleteOneEvent(accountId, members.get(1), 1, 1); // sum 2 -> not in flow

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/organisation/by-type")
				.param("accountId", accountId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asOwner)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = readJson(result);
		assertThat(body.get("belowMinimumSize").asBoolean()).isFalse();
		assertThat(body.get("memberCount").asInt()).isEqualTo(10);
		JsonNode byType = body.get("byType");
		assertThat(byType).hasSize(1);
		assertThat(byType.get(0).get("count").asLong()).isEqualTo(2);
	}

	@Test
	void organisationTypeStatisticsWithheldBelowTenMembers() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-ots-owner-2", "Owner", "ots-owner-2@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Tiny Co"}
					""")).andReturn()).get("accountId").asText());
		// Just the owner -> 1 member, well below the minimum of 10.

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/organisation/by-type")
				.param("accountId", accountId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asOwner)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = readJson(result);
		assertThat(body.get("belowMinimumSize").asBoolean()).isTrue();
		assertThat(body.get("memberCount").asInt()).isEqualTo(1);
		assertThat(body.get("byType")).isEmpty();
	}

	@Test
	void organisationTypeStatisticsDeniedToNonOwner() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-ots-owner-3", "Owner", "ots-owner-3@example.com");
		Consumer<Jwt.Builder> asAdmin = userJwt("kc-ots-admin-3", "Admin", "ots-admin-3@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asAdmin))).andExpect(status().isCreated());
		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"ots-admin-3@example.com","role":"ADMIN"}
					""")).andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/statistics/organisation/by-type")
				.param("accountId", accountId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asAdmin)))
			.andExpect(status().isForbidden());
	}

	private void addMember(UUID accountId, Consumer<Jwt.Builder> asOwner, String email) throws Exception {
		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"%s","role":"MEMBER"}
					""".formatted(email)))
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
