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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class OrganisationIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

	private final Consumer<Jwt.Builder> asOwner = b -> b
		.subject("kc-org-owner").claim("name", "Org Owner").claim("email", "org-owner@example.com");
	private final Consumer<Jwt.Builder> asMember = b -> b
		.subject("kc-org-member").claim("name", "Org Member").claim("email", "org-member@example.com");

	@Test
	void ownerBuildsStructureAndAddsAnAlreadyRegisteredMember() throws Exception {
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
					{"email":"org-member@example.com","role":"MEMBER","departmentId":"%s","groupId":"%s"}
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
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asMember))).andExpect(status().isCreated());

		MvcResult orgResult = mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Acme AB"}
					"""))
			.andReturn();
		UUID accountId = UUID.fromString(readJson(orgResult).get("accountId").asText());

		mockMvc.perform(post("/api/v1/organisations/" + accountId + "/members")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"email":"org-member@example.com","role":"MEMBER"}
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
		mockMvc.perform(post("/api/v1/registration").with(jwt().jwt(asOwner))).andExpect(status().isCreated());

		MvcResult orgResult = mockMvc.perform(post("/api/v1/organisations")
				.with(jwt().jwt(asOwner))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"name":"Acme AB"}
					"""))
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

	private JsonNode readJson(MvcResult result) throws Exception {
		return objectMapper.readTree(result.getResponse().getContentAsString());
	}

}
