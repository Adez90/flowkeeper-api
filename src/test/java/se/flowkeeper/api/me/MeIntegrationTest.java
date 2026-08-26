package se.flowkeeper.api.me;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import se.flowkeeper.api.AbstractIntegrationTest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

}
