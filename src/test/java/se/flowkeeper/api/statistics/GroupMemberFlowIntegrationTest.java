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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Each test method uses its own uniquely-suffixed org/subjects — see
 * GroupStatisticsIntegrationTest's javadoc for why (AbstractIntegrationTest's
 * Postgres container is shared across the whole suite run).
 */
@AutoConfigureMockMvc
class GroupMemberFlowIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void showsOnlyOptedInMembersByNameEvenBelowTheAnonymousAggregateMinimum() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-gmf-owner-1", "Owner", "gmf-owner-1@example.com");
		Consumer<Jwt.Builder> asCoach = userJwt("kc-gmf-coach-1", "Coach", "gmf-coach-1@example.com");
		Consumer<Jwt.Builder> asSharer = userJwt("kc-gmf-sharer-1", "Sharer", "gmf-sharer-1@example.com");
		Consumer<Jwt.Builder> asQuiet = userJwt("kc-gmf-quiet-1", "Quiet", "gmf-quiet-1@example.com");

		for (Consumer<Jwt.Builder> u : java.util.List.of(asOwner, asCoach, asSharer, asQuiet)) {
			mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(u))).andExpect(status().isCreated());
		}

		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());

		UUID groupId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations/" + accountId + "/groups")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Backend team"}
					""")).andReturn()).get("id").asText());

		// Only 3 members total — below MIN_MEMBERS_FOR_AGGREGATE (4), so the
		// anonymous rollup would be withheld, but the opted-in peer view
		// doesn't have that floor.
		addMember(accountId, asOwner, "gmf-coach-1@example.com", "COACH", groupId);
		addMember(accountId, asOwner, "gmf-sharer-1@example.com", "MEMBER", groupId);
		addMember(accountId, asOwner, "gmf-quiet-1@example.com", "MEMBER", groupId);

		logAndCompleteOneEvent(accountId, asSharer, 3, 3); // sum 6 -> in flow
		logAndCompleteOneEvent(accountId, asQuiet, 1, 1); // sum 2 -> not in flow, but never opts in below

		// Only the sharer opts in to peer-sharing.
		mockMvc.perform(patch("/api/v1/organisations/" + accountId + "/members/me/sharing")
				.with(jwt().jwt(asSharer))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"shareFlowWithPeers":true}
					"""))
			.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/group/members")
				.param("accountId", accountId.toString())
				.param("groupId", groupId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asCoach)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = readJson(result);
		assertThat(body.get("members")).hasSize(1);
		JsonNode sharer = body.get("members").get(0);
		assertThat(sharer.get("displayName").asText()).isEqualTo("Sharer");
		assertThat(sharer.get("completedEvents").asLong()).isEqualTo(1);
		assertThat(sharer.get("flowPercentage").asDouble()).isEqualTo(100.0);
	}

	@Test
	void rejectsAnAdminWhoSupervisesTheGroupButIsNotActuallyInIt() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-gmf-owner-2", "Owner", "gmf-owner-2@example.com");
		Consumer<Jwt.Builder> asAdmin = userJwt("kc-gmf-admin-2", "Admin", "gmf-admin-2@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asAdmin))).andExpect(status().isCreated());

		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());

		UUID departmentId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations/" + accountId + "/departments")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Engineering"}
					""")).andReturn()).get("id").asText());

		UUID groupId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations/" + accountId + "/groups")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Backend team","departmentId":"%s"}
					""".formatted(departmentId))).andReturn()).get("id").asText());

		// The admin supervises the whole department (and so the group's
		// anonymous rollup too) but was never added as a member of the group
		// itself — the named peer view must still refuse them.
		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"gmf-admin-2@example.com","role":"ADMIN","departmentId":"%s"}
					""".formatted(departmentId)))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/statistics/group/members")
				.param("accountId", accountId.toString())
				.param("groupId", groupId.toString())
				.param("period", "DAY")
				.with(jwt().jwt(asAdmin)))
			.andExpect(status().isForbidden());
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
