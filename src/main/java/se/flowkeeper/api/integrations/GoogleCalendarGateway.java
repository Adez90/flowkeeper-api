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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/** Read-only calendar access — see https://developers.google.com/identity/protocols/oauth2/web-server. */
@Component
public class GoogleCalendarGateway implements OAuthCalendarGateway {

	private static final Logger log = LoggerFactory.getLogger(GoogleCalendarGateway.class);
	private static final String AUTHORIZE_URL = "https://accounts.google.com/o/oauth2/v2/auth";
	private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
	private static final String USERINFO_URL = "https://www.googleapis.com/oauth2/v3/userinfo";
	private static final String EVENTS_URL = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
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

	@Override
	public OAuthTokenResult refreshAccessToken(String refreshToken) {
		requireConfigured();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("refresh_token", refreshToken);
		form.add("grant_type", "refresh_token");

		Map<String, Object> tokenResponse = restClient.post()
			.uri(TOKEN_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(JSON_MAP);

		if (tokenResponse == null || tokenResponse.get("access_token") == null) {
			throw new IllegalStateException("Google did not return an access token on refresh");
		}

		String accessToken = (String) tokenResponse.get("access_token");
		// Google does not reissue a refresh token here — the original stays valid and gets reused.
		Number expiresIn = (Number) tokenResponse.get("expires_in");
		Instant expiresAt = expiresIn != null ? Instant.now().plusSeconds(expiresIn.longValue()) : null;

		return new OAuthTokenResult(accessToken, null, expiresAt, null);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<ImportableItem> fetchDayItems(String accessToken, LocalDate date, ZoneId zone) {
		requireConfigured();

		String timeMin = date.atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		String timeMax = date.plusDays(1).atStartOfDay(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

		Map<String, Object> response = restClient.get()
			.uri(UriComponentsBuilder.fromUriString(EVENTS_URL)
				.queryParam("timeMin", timeMin)
				.queryParam("timeMax", timeMax)
				.queryParam("singleEvents", true)
				.queryParam("orderBy", "startTime")
				.encode()
				.build()
				.toUri())
			.headers(h -> h.setBearerAuth(accessToken))
			.retrieve()
			.body(JSON_MAP);

		Object items = response != null ? response.get("items") : null;
		if (!(items instanceof List<?> list)) {
			return List.of();
		}

		return list.stream()
			.map(item -> (Map<String, Object>) item)
			.map(this::toImportableItem)
			// All-day entries (a "date" instead of a "dateTime") have no
			// specific time window to log against — skip them rather than
			// guessing a start/end.
			.filter(item -> item != null)
			.toList();
	}

	private ImportableItem toImportableItem(Map<String, Object> item) {
		Instant startedAt = parseEventTime(item.get("start"));
		Instant endedAt = parseEventTime(item.get("end"));
		if (startedAt == null || endedAt == null) {
			return null;
		}
		String title = (String) item.get("summary");
		return new ImportableItem((String) item.get("id"), title != null ? title : "Untitled event", startedAt, endedAt);
	}

	@SuppressWarnings("unchecked")
	private Instant parseEventTime(Object startOrEnd) {
		if (!(startOrEnd instanceof Map<?, ?> map)) {
			return null;
		}
		// Google always includes a numeric UTC offset (e.g. +01:00) or Z,
		// which Instant.parse accepts as-is — unlike Microsoft's format below.
		Object dateTime = ((Map<String, Object>) map).get("dateTime");
		return dateTime instanceof String s ? Instant.parse(s) : null;
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new IntegrationProviderNotConfiguredException("Google Calendar is not configured yet");
		}
	}

}
