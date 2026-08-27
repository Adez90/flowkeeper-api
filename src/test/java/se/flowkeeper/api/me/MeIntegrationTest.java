package se.flowkeeper.api.me;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import se.flowkeeper.api.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MeIntegrationTest extends AbstractIntegrationTest {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ObjectMapper objectMapper;

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

	@Test
	void notificationPreferencesPersistAndAreReflectedOnNextGet() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notifyprefs-subject")
					.claim("name", "Notify Prefs")
					.claim("email", "notify-prefs@example.com"))))
			.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/v1/me/notification-preferences")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notifyprefs-subject")
					.claim("name", "Notify Prefs")
					.claim("email", "notify-prefs@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"notifyInApp":true,"notifyPush":true,"notifyEmail":false}
					"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifyInApp").value(true))
			.andExpect(jsonPath("$.notifyPush").value(true))
			.andExpect(jsonPath("$.notifyEmail").value(false));

		mockMvc.perform(get("/api/v1/me")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-notifyprefs-subject")
					.claim("name", "Notify Prefs")
					.claim("email", "notify-prefs@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.notifyInApp").value(true))
			.andExpect(jsonPath("$.notifyPush").value(true));
	}

	@Test
	void pushTokenCanBeRegistered() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-pushtoken-subject")
					.claim("name", "Push Token")
					.claim("email", "push-token@example.com"))))
			.andExpect(status().isCreated());

		mockMvc.perform(patch("/api/v1/me/push-token")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-pushtoken-subject")
					.claim("name", "Push Token")
					.claim("email", "push-token@example.com")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{"expoPushToken":"ExponentPushToken[abc123]"}
					"""))
			.andExpect(status().isOk());
	}

	@Test
	void uploadedAvatarIsPersistedAndPubliclyDownloadable() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-avatar-subject")
					.claim("name", "Avatar Test")
					.claim("email", "avatar-test@example.com"))))
			.andExpect(status().isCreated());

		MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", new byte[] {1, 2, 3, 4});

		MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/me/avatar").file(file)
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-avatar-subject")
					.claim("name", "Avatar Test")
					.claim("email", "avatar-test@example.com"))))
			.andExpect(status().isOk())
			.andReturn();

		String avatarUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("avatarUrl").asText();
		assertThat(avatarUrl).contains("/api/v1/avatars/");
		String avatarPath = avatarUrl.substring(avatarUrl.indexOf("/api/v1/avatars/"));

		// No jwt() — this is the plain <img src> request a browser makes, no Authorization header available.
		mockMvc.perform(get(avatarPath))
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.IMAGE_PNG))
			.andExpect(content().bytes(new byte[] {1, 2, 3, 4}));

		mockMvc.perform(get("/api/v1/me")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-avatar-subject")
					.claim("name", "Avatar Test")
					.claim("email", "avatar-test@example.com"))))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.avatarUrl").value(avatarUrl));
	}

	@Test
	void avatarUploadRejectsAnUnsupportedFileType() throws Exception {
		mockMvc.perform(post("/api/v1/registration")
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-badavatar-subject")
					.claim("name", "Bad Avatar")
					.claim("email", "bad-avatar@example.com"))))
			.andExpect(status().isCreated());

		MockMultipartFile file = new MockMultipartFile("file", "me.gif", "image/gif", new byte[] {1});

		mockMvc.perform(multipart("/api/v1/me/avatar").file(file)
				.with(jwt().jwt(jwtBuilder -> jwtBuilder
					.subject("kc-badavatar-subject")
					.claim("name", "Bad Avatar")
					.claim("email", "bad-avatar@example.com"))))
			.andExpect(status().isBadRequest());
	}

	@Test
	void avatarLookupForAnUnknownFilenameIs404() throws Exception {
		mockMvc.perform(get("/api/v1/avatars/11111111-1111-1111-1111-111111111111.jpg"))
			.andExpect(status().isNotFound());
	}

}
