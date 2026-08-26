package se.flowkeeper.api.me;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import se.flowkeeper.api.AbstractIntegrationTest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Test
	void unknownSubjectGetsNotFoundUntilRegistered() throws Exception {
		mockMvc.perform(get("/api/v1/me")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-me-subject")
					.claim("name", "Me Test")
					.claim("email", "me-test@example.com"))))
			.andExpect(status().isNotFound());

		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-me-subject")
					.claim("name", "Me Test")
					.claim("email", "me-test@example.com"))))
			.andExpect(status().isCreated());

		mockMvc.perform(get("/api/v1/me")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-me-subject")
					.claim("name", "Me Test")
					.claim("email", "me-test@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Me Test"))
			.andExpect(jsonPath("$.accounts[0].role").value("OWNER"));
	}

	@Test
	void unauthenticatedCallIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/me"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void updateProfilePersistsAndIsReflectedOnNextGet() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-profile-subject")
					.claim("name", "Profile Test")
					.claim("email", "profile-test@example.com"))))
			.andExpect(status().isCreated());

		String updateBody = """
			{"displayName":"Profile Test Updated","timezone":"Europe/Stockholm","locale":"sv","avatarUrl":"https://example.com/me.png"}
			""";
		mockMvc.perform(patch("/api/v1/me")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-profile-subject")
					.claim("name", "Profile Test")
					.claim("email", "profile-test@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateBody))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.timezone").value("Europe/Stockholm"))
			.andExpect(jsonPath("$.locale").value("sv"));

		mockMvc.perform(get("/api/v1/me")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-profile-subject")
					.claim("name", "Profile Test")
					.claim("email", "profile-test@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.displayName").value("Profile Test Updated"))
			.andExpect(jsonPath("$.avatarUrl").value("https://example.com/me.png"));
	}

	@Test
	void updateProfileRejectsAnInvalidTimezone() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-badtz-subject")
					.claim("name", "Bad TZ")
					.claim("email", "bad-tz@example.com"))))
			.andExpect(status().isCreated());

		String updateBody = """
			{"displayName":"Bad TZ","timezone":"Not/AZone","locale":null,"avatarUrl":null}
			""";
		mockMvc.perform(patch("/api/v1/me")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-badtz-subject")
					.claim("name", "Bad TZ")
					.claim("email", "bad-tz@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content(updateBody))
			.andExpect(status().isBadRequest());
	}

}
