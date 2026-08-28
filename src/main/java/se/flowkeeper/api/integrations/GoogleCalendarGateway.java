package se.flowkeeper.api.integrations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Map;

/** Read-only calendar access — see https://developers.google.com/identity/protocols/oauth2/web-server. */
@Component
public class GoogleCalendarGateway implements OAuthCalendarGateway {

	private static final Logger log = LoggerFactory.getLogger(GoogleCalendarGateway.class);
	private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
	private static final String SCOPE = "openid email https://www.googleapis.com/auth/calendar.readonly";
	private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
	};

	private final String clientId;
	private final String clientSecret;
	private final RestClient restClient = RestClient.create();

	public GoogleCalendarGateway(
			@Value("${app.integrations.google.client-id}") String clientId,
			@Value("${app.integrations.google.client-secret}") String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public ExternalProvider provider() {
		return ExternalProvider.GOOGLE_CALENDAR;
	}

	@Override
	public boolean isConfigured() {
		return !clientId.isBlank() && !clientSecret.isBlank();
	}

	@Override
	public String buildAuthorizationUrl(String state, String redirectUri) {
		requireConfigured();
		return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
			.queryParam("client_id", clientId)
			.queryParam("redirect_uri", redirectUri)
			.queryParam("response_type", "code")
			.queryParam("scope", SCOPE)
			.queryParam("access_type", "offline")
			.queryParam("prompt", "consent")
			.queryParam("state", state)
			.encode()
			.build()
			.toUriString();
	}

	@Override
	public OAuthTokenResult exchangeCode(String code, String redirectUri) {
		requireConfigured();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("code", code);
		form.add("grant_type", "authorization_code");
		form.add("redirect_uri", redirectUri);

		Map<String, Object> tokenResponse = restClient.post()
			.uri(TOKEN_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(JSON_MAP);

		if (tokenResponse == null || tokenResponse.get("access_token") == null) {
			throw new IllegalStateException("Google did not return an access token");
		}

		String accessToken = (String) tokenResponse.get("access_token");
		String refreshToken = (String) tokenResponse.get("refresh_token");
		Number expiresIn = (Number) tokenResponse.get("expires_in");
		Instant expiresAt = expiresIn != null ? Instant.now().plusSeconds(expiresIn.longValue()) : null;

		return new OAuthTokenResult(accessToken, refreshToken, expiresAt, fetchLabel(accessToken));
	}

	private String fetchLabel(String accessToken) {
		try {
			Map<String, Object> userInfo = restClient.get()
				.uri(USERINFO_URL)
				.headers(h -> h.setBearerAuth(accessToken))
				.retrieve()
				.body(JSON_MAP);
			return userInfo != null ? (String) userInfo.get("email") : null;
		} catch (Exception e) {
			log.warn("Couldn't fetch Google account label: {}", e.getMessage());
			return null;
		}
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new IntegrationProviderNotConfiguredException("Google Calendar is not configured yet");
		}
	}

}
