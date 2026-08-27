package se.flowkeeper.api.coachfeedback;

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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Each test method uses its own uniquely-suffixed Keycloak subjects — same shared-container reasoning as OrganisationIntegrationTest. */
@AutoConfigureMockMvc
class CoachFeedbackIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void coachLeavesFreeformAndEventAttachedFeedbackForTheirGroupsMember() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-cf-owner-1", "Owner", "cf-owner-1@example.com");
		Consumer<Jwt.Builder> asCoach = userJwt("kc-cf-coach-1", "Coach", "cf-coach-1@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-cf-member-1", "Member", "cf-member-1@example.com");
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asCoach))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());

		UUID accountId = createOrganisation(asOwner);
		UUID groupId = createGroup(asOwner, accountId, "Backend team", null);
		addMember(asOwner, accountId, "cf-coach-1@example.com", "COACH", null, groupId);
		UUID memberId = addMember(asOwner, accountId, "cf-member-1@example.com", "MEMBER", null, groupId);

		UUID eventTypeId = firstEventTypeId(asMember, accountId);
		UUID eventId = logEvent(asMember, accountId, eventTypeId);

		// The coach can see the member's own events to pick one to attach feedback to.
		mockMvc.perform(get("/api/v1/organisations/" + accountId + "/members/" + memberId + "/events").with(jwt().jwt(asCoach)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].id").value(eventId.toString()));

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members/" + memberId + "/feedback")
				.with(jwt().jwt(asCoach))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"note":"Keep checking in daily, it's working"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.eventId").doesNotExist());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members/" + memberId + "/feedback")
				.with(jwt().jwt(asCoach))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"note":"Great energy on this one","eventId":"%s"}
					""".formatted(eventId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.eventId").value(eventId.toString()));

		mockMvc.perform(get("/api/v1/organisations/" + accountId + "/members/" + memberId + "/feedback").with(jwt().jwt(asCoach)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2))
			.andExpect(jsonPath("$[0].note").value("Great energy on this one")) // newest first
			.andExpect(jsonPath("$[1].note").value("Keep checking in daily, it's working"));

		// The member themselves can read their own feedback too.
		mockMvc.perform(get("/api/v1/organisations/" + accountId + "/members/" + memberId + "/feedback").with(jwt().jwt(asMember)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
	}

	@Test
	void anotherMemberCannotReadSomeoneElsesFeedback() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-cf-owner-2", "Owner", "cf-owner-2@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-cf-member-2", "Member", "cf-member-2@example.com");
		Consumer<Jwt.Builder> asOther = userJwt("kc-cf-other-2", "Other", "cf-other-2@example.com");
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOther))).andExpect(status().isCreated());

		UUID accountId = createOrganisation(asOwner);
		UUID memberId = addMember(asOwner, accountId, "cf-member-2@example.com", "MEMBER", null, null);
		addMember(asOwner, accountId, "cf-other-2@example.com", "MEMBER", null, null);

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members/" + memberId + "/feedback")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"note":"Private note"}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/organisations/" + accountId + "/members/" + memberId + "/feedback").with(jwt().jwt(asOther)))
			.andExpect(status().isForbidden());
	}

	@Test
	void aPlainMemberCannotLeaveFeedbackForAnyone() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-cf-owner-3", "Owner", "cf-owner-3@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-cf-member-3", "Member", "cf-member-3@example.com");
		Consumer<Jwt.Builder> asOther = userJwt("kc-cf-other-3", "Other", "cf-other-3@example.com");
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOther))).andExpect(status().isCreated());

		UUID accountId = createOrganisation(asOwner);
		addMember(asOwner, accountId, "cf-member-3@example.com", "MEMBER", null, null);
		UUID otherId = addMember(asOwner, accountId, "cf-other-3@example.com", "MEMBER", null, null);

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members/" + otherId + "/feedback")
				.with(jwt().jwt(asMember))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"note":"Trying to leave feedback"}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void eventAttachedFeedbackForSomeoneElsesEventIsRejected() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-cf-owner-4", "Owner", "cf-owner-4@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-cf-member-4", "Member", "cf-member-4@example.com");
		Consumer<Jwt.Builder> asOther = userJwt("kc-cf-other-4", "Other", "cf-other-4@example.com");
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOther))).andExpect(status().isCreated());

		UUID accountId = createOrganisation(asOwner);
		UUID memberId = addMember(asOwner, accountId, "cf-member-4@example.com", "MEMBER", null, null);
		addMember(asOwner, accountId, "cf-other-4@example.com", "MEMBER", null, null);

		UUID eventTypeId = firstEventTypeId(asOther, accountId);
		UUID othersEventId = logEvent(asOther, accountId, eventTypeId);

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members/" + memberId + "/feedback")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"note":"Wrong event","eventId":"%s"}
					""".formatted(othersEventId)))
			.andExpect(status().isBadRequest());
	}

	private UUID createOrganisation(Consumer<Jwt.Builder> asOwner) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Acme AB"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(readJson(result).get("accountId").asText());
	}

	private UUID createGroup(Consumer<Jwt.Builder> asOwner, UUID accountId, String name, UUID departmentId) throws Exception {
		String body = departmentId == null
			? "{\"name\":\"%s\"}".formatted(name)
			: "{\"name\":\"%s\",\"departmentId\":\"%s\"}".formatted(name, departmentId);
		MvcResult result = mockMvc.perform(post("/api/v1/organisations/" + accountId + "/groups")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(readJson(result).get("id").asText());
	}

	private UUID addMember(Consumer<Jwt.Builder> asOwner, UUID accountId, String email, String role, UUID departmentId, UUID groupId) throws Exception {
		StringBuilder body = new StringBuilder("{\"email\":\"" + email + "\",\"role\":\"" + role + "\"");
		if (departmentId != null) {
			body.append(",\"departmentId\":\"").append(departmentId).append('"');
		}
		if (groupId != null) {
			body.append(",\"groupId\":\"").append(groupId).append('"');
		}
		body.append('}');
		MvcResult result = mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body.toString()))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(readJson(result).get("userId").asText());
	}

	private UUID firstEventTypeId(Consumer<Jwt.Builder> asUser, UUID accountId) throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/event-types").param("accountId", accountId.toString()).with(jwt().jwt(asUser)))
			.andReturn();
		return UUID.fromString(readJson(result).get(0).get("id").asText());
	}

	private UUID logEvent(Consumer<Jwt.Builder> asUser, UUID accountId, UUID eventTypeId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/events")
				.with(jwt().jwt(asUser))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"accountId":"%s","eventTypeId":"%s","ingoingEnergy":3,"ingoingNote":null}
					""".formatted(accountId, eventTypeId)))
			.andExpect(status().isCreated())
			.andReturn();
		return UUID.fromString(readJson(result).get("id").asText());
	}

	private Consumer<Jwt.Builder> userJwt(String subject, String name, String email) {
		return b -> b.subject(subject).claim("name", name).claim("email", email);
	}

	private JsonNode readJson(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
