package se.flowkeeper.api.config;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Docker Compose passes an unset env var through as an empty string, not
 * "absent" — so Spring's {@code ${VAR:default}} placeholder never kicks in
 * for it, and the app boots successfully with a blank value that silently
 * breaks whatever depends on it. This is exactly how API_ORIGIN and
 * APP_ORIGIN went missing from production without anyone noticing until a
 * user hit the broken feature days later. Fail loudly at startup instead,
 * for every property where blank is always a deployment mistake and never
 * a legitimate choice.
 */
@Component
public class RequiredConfigValidator {

	private final String appOrigin;
	private final String apiOrigin;
	private final String issuerUri;

	public RequiredConfigValidator(
			@Value("${app.cors.allowed-origin}") String appOrigin,
			@Value("${app.integrations.api-origin}") String apiOrigin,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
		this.appOrigin = appOrigin;
		this.apiOrigin = apiOrigin;
		this.issuerUri = issuerUri;
	}

	@PostConstruct
	void validate() {
		Map<String, String> required = new LinkedHashMap<>();
		required.put("APP_ORIGIN", appOrigin);
		required.put("API_ORIGIN", apiOrigin);
		required.put("OAUTH2_ISSUER_URI", issuerUri);

		StringBuilder blank = new StringBuilder();
		required.forEach((name, value) -> {
			if (value == null || value.isBlank()) {
				blank.append("\n  - ").append(name);
			}
		});

		if (!blank.isEmpty()) {
			throw new IllegalStateException(
				"Refusing to start: the following environment variables are unset or blank on the "
					+ "server, which would silently break the features that depend on them instead of "
					+ "failing loudly:" + blank + "\nSet them in the server's .env, then redeploy.");
		}
	}

}
