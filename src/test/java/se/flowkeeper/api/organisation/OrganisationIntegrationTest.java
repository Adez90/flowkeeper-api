package se.flowkeeper.api.organisation;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every test method here uses its own uniquely-suffixed Keycloak subjects —
 * AbstractIntegrationTest's Postgres container is shared across the whole
 * suite run with no per-test rollback, so reusing a fixed subject across
 * multiple @Test methods makes the second one's registration a genuine
 * "already registered" 200 instead of a fresh 201 (confirmed live: this
 * broke exactly that way on the first real CI run, once with a shared
 * subject across two @Test methods in this class).
 */
@AutoConfigureMockMvc
class OrganisationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	@Test
	void ownerBuildsStructureAndAddsAnAlreadyRegisteredMember() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-org-owner-1", "Org Owner", "org-owner-1@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-org-member-1", "Org Member", "org-member-1@example.com");

		// Both users have to exist as FlowKeeper profiles first — org
		// membership is added to an existing profile, not used to create one.
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());

		MvcResult orgResult = mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Acme AB"}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("OWNER"))
			.andReturn();
		UUID accountId = UUID.fromString(readJson(orgResult).get("accountId").asText());

		MvcResult deptResult = mockMvc.perform(post("/api/v1/organisations/" + accountId + "/departments")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Engineering"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID departmentId = UUID.fromString(readJson(deptResult).get("id").asText());

		MvcResult groupResult = mockMvc.perform(post("/api/v1/organisations/" + accountId + "/groups")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Backend team","departmentId":"%s"}
					""".formatted(departmentId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.departmentId").value(departmentId.toString()))
			.andReturn();
		UUID groupId = UUID.fromString(readJson(groupResult).get("id").asText());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"org-member-1@example.com","role":"MEMBER","departmentId":"%s","groupId":"%s"}
					""".formatted(departmentId, groupId)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.groupId").value(groupId.toString()));

		mockMvc.perform(get("/api/v1/organisations/" + accountId + "/structure").with(jwt().jwt(asOwner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.departments[0].name").value("Engineering"))
			.andExpect(jsonPath("$.groups[0].name").value("Backend team"));

		mockMvc.perform(get("/api/v1/organisations/" + accountId + "/members").with(jwt().jwt(asOwner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));

		// The newly added member can now see the org's structure too.
		mockMvc.perform(get("/api/v1/organisations/" + accountId + "/structure").with(jwt().jwt(asMember)))
			.andExpect(status().isOk());
	}

	@Test
	void aPlainMemberCannotCreateADepartment() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-org-owner-2", "Org Owner", "org-owner-2@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-org-member-2", "Org Member", "org-member-2@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());

		MvcResult orgResult = mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Acme AB"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID accountId = UUID.fromString(readJson(orgResult).get("accountId").asText());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"org-member-2@example.com","role":"MEMBER"}
					"""))
			.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/departments")
				.with(jwt().jwt(asMember))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Engineering"}
					"""))
			.andExpect(status().isForbidden());
	}

	@Test
	void addingSomeoneWhosNeverLoggedInFails() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-org-owner-3", "Org Owner", "org-owner-3@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());

		MvcResult orgResult = mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Acme AB"}
					"""))
			.andExpect(status().isCreated())
			.andReturn();
		UUID accountId = UUID.fromString(readJson(orgResult).get("accountId").asText());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"never-logged-in@example.com","role":"MEMBER"}
					"""))
			.andExpect(status().isNotFound());
	}

	@Test
	void sharingConsentIsScopedToItsOwnLevel() throws Exception {
		Consumer<Jwt.Builder> asOwner = userJwt("kc-org-owner-4", "Org Owner", "org-owner-4@example.com");
		Consumer<Jwt.Builder> asCoach = userJwt("kc-org-coach-4", "Org Coach", "org-coach-4@example.com");
		Consumer<Jwt.Builder> asMember = userJwt("kc-org-member-4", "Org Member", "org-member-4@example.com");

		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asCoach))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());

		MvcResult orgResult = mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Acme AB"}
					"""))
			.andReturn();
		UUID accountId = UUID.fromString(readJson(orgResult).get("accountId").asText());

		MvcResult groupResult = mockMvc.perform(post("/api/v1/organisations/" + accountId + "/groups")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Backend team"}
					"""))
			.andReturn();
		UUID groupId = UUID.fromString(readJson(groupResult).get("id").asText());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"org-coach-4@example.com","role":"COACH","groupId":"%s"}
					""".formatted(groupId)))
			.andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"org-member-4@example.com","role":"MEMBER","groupId":"%s"}
					""".formatted(groupId)))
			.andExpect(status().isCreated());

		// The member sets their own personal consent.
		mockMvc.perform(patch("/api/v1/organisations/" + accountId + "/members/me/sharing")
				.with(jwt().jwt(asMember))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"shareFlowWithPeers":true}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shareFlowWithPeers").value(true));

		// The group's own coach sets the group's consent.
		mockMvc.perform(patch("/api/v1/organisations/" + accountId + "/groups/" + groupId + "/sharing")
				.with(jwt().jwt(asCoach))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"shareFlowWithPeers":true}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.shareFlowWithPeers").value(true));

		// A plain member (not this group's coach) can't set the group's consent.
		mockMvc.perform(patch("/api/v1/organisations/" + accountId + "/groups/" + groupId + "/sharing")
				.with(jwt().jwt(asMember))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"shareFlowWithPeers":false}
					"""))
			.andExpect(status().isForbidden());
	}

	private Consumer<Jwt.Builder> userJwt(String subject, String name, String email) {
		return b -> b.subject(subject).claim("name", name).claim("email", email);
	}

	private JsonNode readJson(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
