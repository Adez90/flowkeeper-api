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

/** Outlook/Microsoft 365 calendars via Microsoft Graph — "common" tenant covers both personal and work/school accounts. */
@Component
public class MicrosoftCalendarGateway implements OAuthCalendarGateway {

	private static final Logger log = LoggerFactory.getLogger(MicrosoftCalendarGateway.class);
	private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
	private static final String ME_URL = "https://graph.microsoft.com/v1.0/me";
	private static final String SCOPE = "offline_access Calendars.Read User.Read";
	private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
	};

	private final String clientId;
	private final String clientSecret;
	private final RestClient restClient = RestClient.create();

	public MicrosoftCalendarGateway(
			@Value("${app.integrations.microsoft.client-id}") String clientId,
			@Value("${app.integrations.microsoft.client-secret}") String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public ExternalProvider provider() {
		return ExternalProvider.MICROSOFT_CALENDAR;
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
			.queryParam("response_mode", "query")
			.queryParam("scope", SCOPE)
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
		form.add("scope", SCOPE);

		Map<String, Object> tokenResponse = restClient.post()
			.uri(TOKEN_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(JSON_MAP);

		if (tokenResponse == null || tokenResponse.get("access_token") == null) {
			throw new IllegalStateException("Microsoft did not return an access token");
		}

		String accessToken = (String) tokenResponse.get("access_token");
		String refreshToken = (String) tokenResponse.get("refresh_token");
		Number expiresIn = (Number) tokenResponse.get("expires_in");
		Instant expiresAt = expiresIn != null ? Instant.now().plusSeconds(expiresIn.longValue()) : null;

		return new OAuthTokenResult(accessToken, refreshToken, expiresAt, fetchLabel(accessToken));
	}

	private String fetchLabel(String accessToken) {
		try {
			Map<String, Object> me = restClient.get()
				.uri(ME_URL)
				.headers(h -> h.setBearerAuth(accessToken))
				.retrieve()
				.body(JSON_MAP);
			if (me == null) {
				return null;
			}
			// "mail" is null for some personal Microsoft accounts — userPrincipalName always exists.
			Object mail = me.get("mail");
			return mail != null ? (String) mail : (String) me.get("userPrincipalName");
		} catch (Exception e) {
			log.warn("Couldn't fetch Microsoft account label: {}", e.getMessage());
			return null;
		}
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new IntegrationProviderNotConfiguredException("Microsoft Calendar is not configured yet");
		}
	}

}
