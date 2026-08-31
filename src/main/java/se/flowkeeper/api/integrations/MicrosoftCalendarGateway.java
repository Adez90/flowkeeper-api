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

/** Outlook/Microsoft 365 calendars via Microsoft Graph — "common" tenant covers both personal and work/school accounts. */
@Component
public class MicrosoftCalendarGateway implements OAuthCalendarGateway {

	private static final Logger log = LoggerFactory.getLogger(MicrosoftCalendarGateway.class);
	private static final String AUTHORIZE_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize";
	private static final String TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
	private static final String ME_URL = "https://graph.microsoft.com/v1.0/me";
	private static final String CALENDAR_VIEW_URL = "https://graph.microsoft.com/v1.0/me/calendarView";
	private static final String SCOPE = "offline_access Calendars.Read User.Read";
	private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
	};
	// Query params use this UTC, Z-suffixed form regardless of the response
	// timezone below — Graph always accepts calendarView bounds as literal UTC.
	private static final DateTimeFormatter UTC_QUERY_FORMAT = DateTimeFormatter.ISO_INSTANT;

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

	@Override
	public OAuthTokenResult refreshAccessToken(String refreshToken) {
		requireConfigured();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("refresh_token", refreshToken);
		form.add("grant_type", "refresh_token");
		form.add("scope", SCOPE);

		Map<String, Object> tokenResponse = restClient.post()
			.uri(TOKEN_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(JSON_MAP);

		if (tokenResponse == null || tokenResponse.get("access_token") == null) {
			throw new IllegalStateException("Microsoft did not return an access token on refresh");
		}

		String accessToken = (String) tokenResponse.get("access_token");
		// Microsoft rotates the refresh token like Strava does — the old one stops working.
		String newRefreshToken = (String) tokenResponse.get("refresh_token");
		Number expiresIn = (Number) tokenResponse.get("expires_in");
		Instant expiresAt = expiresIn != null ? Instant.now().plusSeconds(expiresIn.longValue()) : null;

		return new OAuthTokenResult(accessToken, newRefreshToken, expiresAt, null);
	}

	@Override
	@SuppressWarnings("unchecked")
	public List<ImportableItem> fetchDayItems(String accessToken, LocalDate date, ZoneId zone) {
		requireConfigured();

		String start = UTC_QUERY_FORMAT.format(date.atStartOfDay(zone).toInstant());
		String end = UTC_QUERY_FORMAT.format(date.plusDays(1).atStartOfDay(zone).toInstant());

		Map<String, Object> response = restClient.get()
			.uri(UriComponentsBuilder.fromUriString(CALENDAR_VIEW_URL)
				.queryParam("startDateTime", start)
				.queryParam("endDateTime", end)
				.queryParam("$top", 100)
				.encode()
				.build()
				.toUri())
			.headers(h -> {
				h.setBearerAuth(accessToken);
				// Normalizes every returned dateTime to UTC regardless of the mailbox's own timezone — otherwise start/end come back in a per-event local zone that would need separate parsing per item.
				h.set("Prefer", "outlook.timezone=\"UTC\"");
			})
			.retrieve()
			.body(JSON_MAP);

		Object value = response != null ? response.get("value") : null;
		if (!(value instanceof List<?> list)) {
			return List.of();
		}

		return list.stream()
			.map(item -> (Map<String, Object>) item)
			.filter(item -> !Boolean.TRUE.equals(item.get("isAllDay")))
			.map(this::toImportableItem)
			.toList();
	}

	@SuppressWarnings("unchecked")
	private ImportableItem toImportableItem(Map<String, Object> item) {
		Instant startedAt = parseGraphTime((Map<String, Object>) item.get("start"));
		Instant endedAt = parseGraphTime((Map<String, Object>) item.get("end"));
		String title = (String) item.get("subject");
		return new ImportableItem((String) item.get("id"), title != null ? title : "Untitled event", startedAt, endedAt);
	}

	/** With Prefer: outlook.timezone="UTC" set, dateTime comes back as e.g. "2024-01-15T10:00:00.0000000" — UTC, but with no offset/Z of its own. */
	private Instant parseGraphTime(Map<String, Object> startOrEnd) {
		if (startOrEnd == null) {
			return null;
		}
		Object dateTime = startOrEnd.get("dateTime");
		return dateTime instanceof String s ? Instant.parse(s + "Z") : null;
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new IntegrationProviderNotConfiguredException("Microsoft Calendar is not configured yet");
		}
	}

}
