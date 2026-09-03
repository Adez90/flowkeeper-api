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
 * OrganisationIntegrationTest's class javadoc for why (AbstractIntegrationTest's
 * Postgres container is shared across the whole suite run, so cross-test state
 * leakage is a real, previously-hit bug here, not a hypothetical).
 */
@AutoConfigureMockMvc
class OrganisationFeedbackIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void onlyEventsOptedIntoSharingAppearOnceAboveTenMembers() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-ofb-owner-1", "Owner", "ofb-owner-1@example.com");
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());

		Consumer<Jwt.Builder> firstMember = null;
		for (int i = 1; i <= 9; i++) {
			Consumer<Jwt.Builder> member = userJwt("kc-ofb-member-" + i, "Member " + i, "ofb-member-" + i + "@example.com");
			if (i == 1) {
				firstMember = member;
			}
			mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(member))).andExpect(status().isCreated());
			mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
					.with(jwt().jwt(asOwner))
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
						{"email":"ofb-member-%d@example.com","role":"MEMBER"}
						""".formatted(i)))
				.andExpect(status().isCreated());
		}
		// 10 members total (owner + 9) -> at the minimum, feedback should surface.

		UUID sharedEventId = logAndCompleteOneEvent(accountId, firstMember, "felt great about this one", "energised");
		logAndCompleteOneEvent(accountId, firstMember, "a private note", "kept private"); // never opted in

		// Opt in only the pre-activity note — the post-activity one stays private.
		mockMvc.perform(patch("/api/v1/events/" + sharedEventId + "/sharing")
				.with(jwt().jwt(firstMember))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"shareIngoingNoteAnonymously":true,"shareOutgoingNoteAnonymously":false}
					"""))
			.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/organisation/feedback")
				.param("accountId", accountId.toString())
				.with(jwt().jwt(asOwner)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = readJson(result);
		assertThat(body.get("belowMinimumSize").asBoolean()).isFalse();
		assertThat(body.get("memberCount").asInt()).isEqualTo(10);
		JsonNode items = body.get("items");
		assertThat(items).hasSize(1);
		assertThat(items.get(0).get("ingoingNote").asText()).isEqualTo("felt great about this one");
		assertThat(items.get(0).get("outgoingNote").isNull()).isTrue();
	}

	@Test
	void feedbackWithheldBelowTenMembers() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-ofb-owner-2", "Owner", "ofb-owner-2@example.com");
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Tiny Co"}
					""")).andReturn()).get("accountId").asText());
		// Just the owner -> 1 member, well below the minimum of 10.

		UUID eventId = logAndCompleteOneEvent(accountId, asOwner, "note", "note");
		mockMvc.perform(patch("/api/v1/events/" + eventId + "/sharing")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"shareIngoingNoteAnonymously":true,"shareOutgoingNoteAnonymously":true}
					"""))
			.andExpect(status().isOk());

		MvcResult result = mockMvc.perform(get("/api/v1/statistics/organisation/feedback")
				.param("accountId", accountId.toString())
				.with(jwt().jwt(asOwner)))
			.andExpect(status().isOk())
			.andReturn();

		JsonNode body = readJson(result);
		assertThat(body.get("belowMinimumSize").asBoolean()).isTrue();
		assertThat(body.get("items")).isEmpty();
	}

	@Test
	void aMemberCannotOptInSomeoneElsesEvent() throws Exception {
		// Deliberately not "-member-3" — the first test's loop already
		// registers kc-ofb-member-1..9, and this shared Postgres has no
		// per-test rollback (see class javadoc), so that subject would
		// already exist here.
		Consumer<Jwt.Builder> asOwner = userJwt("kc-ofb-owner-solo", "Owner", "ofb-owner-solo@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-ofb-member-solo", "Member", "ofb-member-solo@example.com");
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());
		UUID accountId = UUID.fromString(readJson(mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner)).contentType(MediaType.APPLICATION_JSON).content("""
					{"name":"Acme AB"}
					""")).andReturn()).get("accountId").asText());
		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"ofb-member-solo@example.com","role":"MEMBER"}
					""")).andExpect(status().isCreated());

		UUID eventId = logAndCompleteOneEvent(accountId, asOwner, "owner's note", "owner's outcome");

		mockMvc.perform(patch("/api/v1/events/" + eventId + "/sharing")
				.with(jwt().jwt(asMember))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"shareIngoingNoteAnonymously":true,"shareOutgoingNoteAnonymously":true}
					"""))
			.andExpect(status().isForbidden());
	}

	private UUID logAndCompleteOneEvent(UUID accountId, Consumer<Jwt.Builder> asUser, String ingoingNote, String outgoingNote) throws Exception {
		MvcResult types = mockMvc.perform(get("/api/v1/event-types").param("accountId", accountId.toString()).with(jwt().jwt(asUser)))
			.andReturn();
		UUID eventTypeId = UUID.fromString(readJson(types).get(0).get("id").asText());

		MvcResult created = mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":3,"ingoingNote":"%s"}
					""".formatted(accountId, eventTypeId, ingoingNote)))
			.andExpect(status().isCreated())
			.andReturn();
		UUID eventId = UUID.fromString(readJson(created).get("id").asText());

		mockMvc.perform(post("/api/v1/events/" + eventId + "/complete")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"outgoingEnergy":3,"outgoingNote":"%s"}
					""".formatted(outgoingNote)))
			.andExpect(status().isOk());

		return eventId;
	}

	private Consumer<Jwt.Builder> userJwt(String subject, String name, String email) {
		return b -> b.subject(subject).claim("name", name).claim("email", email);
	}

	private JsonNode readJson(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
