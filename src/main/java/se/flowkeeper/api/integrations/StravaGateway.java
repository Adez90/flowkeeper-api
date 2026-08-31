package se.flowkeeper.api.integrations;

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
import java.util.List;
import java.util.Map;

/** Fitness activities — see https://developers.strava.com/docs/authentication/. */
@Component
public class StravaGateway implements OAuthCalendarGateway {

	private static final String AUTHORIZE_URL = "https://www.strava.com/oauth/authorize";
	private static final String TOKEN_URL = "https://www.strava.com/oauth/token";
	private static final String ACTIVITIES_URL = "https://www.strava.com/api/v3/athlete/activities";
	// activity:read_all (not just activity:read) so private activities are included, not just public ones.
	private static final String SCOPE = "read,activity:read_all";
	private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
	};
	private static final ParameterizedTypeReference<List<Map<String, Object>>> JSON_LIST = new ParameterizedTypeReference<>() {
	};

	private final String clientId;
	private final String clientSecret;
	private final RestClient restClient = RestClient.create();

	public StravaGateway(
			@Value("${app.integrations.strava.client-id}") String clientId,
			@Value("${app.integrations.strava.client-secret}") String clientSecret) {
		this.clientId = clientId;
		this.clientSecret = clientSecret;
	}

	@Override
	public ExternalProvider provider() {
		return ExternalProvider.STRAVA;
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
			.queryParam("approval_prompt", "auto")
			.queryParam("scope", SCOPE)
			.queryParam("state", state)
			.encode()
			.build()
			.toUriString();
	}

	@Override
	@SuppressWarnings("unchecked")
	public OAuthTokenResult exchangeCode(String code, String redirectUri) {
		requireConfigured();

		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("client_id", clientId);
		form.add("client_secret", clientSecret);
		form.add("code", code);
		form.add("grant_type", "authorization_code");

		Map<String, Object> tokenResponse = restClient.post()
			.uri(TOKEN_URL)
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(form)
			.retrieve()
			.body(JSON_MAP);

		if (tokenResponse == null || tokenResponse.get("access_token") == null) {
			throw new IllegalStateException("Strava did not return an access token");
		}

		String accessToken = (String) tokenResponse.get("access_token");
		String refreshToken = (String) tokenResponse.get("refresh_token");
		// Strava returns an absolute expires_at (epoch seconds) directly, unlike the
		// expires_in-relative-seconds shape Google/Microsoft use.
		Number expiresAt = (Number) tokenResponse.get("expires_at");
		Instant expiresAtInstant = expiresAt != null ? Instant.ofEpochSecond(expiresAt.longValue()) : null;

		String label = null;
		Object athlete = tokenResponse.get("athlete");
		if (athlete instanceof Map<?, ?> athleteMap) {
			Map<String, Object> a = (Map<String, Object>) athleteMap;
			String first = (String) a.get("firstname");
			String last = (String) a.get("lastname");
			String joined = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
			label = joined.isBlank() ? null : joined;
		}

		return new OAuthTokenResult(accessToken, refreshToken, expiresAtInstant, label);
	}

	@Override
	@SuppressWarnings("unchecked")
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
			throw new IllegalStateException("Strava did not return an access token on refresh");
		}

		String accessToken = (String) tokenResponse.get("access_token");
		// Strava always rotates the refresh token — the old one stops working.
		String newRefreshToken = (String) tokenResponse.get("refresh_token");
		Number expiresAt = (Number) tokenResponse.get("expires_at");
		Instant expiresAtInstant = expiresAt != null ? Instant.ofEpochSecond(expiresAt.longValue()) : null;

		return new OAuthTokenResult(accessToken, newRefreshToken, expiresAtInstant, null);
	}

	@Override
	public List<ImportableItem> fetchDayItems(String accessToken, LocalDate date, ZoneId zone) {
		requireConfigured();

		long after = date.atStartOfDay(zone).toEpochSecond();
		long before = date.plusDays(1).atStartOfDay(zone).toEpochSecond();

		List<Map<String, Object>> activities = restClient.get()
			.uri(UriComponentsBuilder.fromUriString(ACTIVITIES_URL)
				.queryParam("after", after)
				.queryParam("before", before)
				.queryParam("per_page", 100)
				.build()
				.toUri())
			.headers(h -> h.setBearerAuth(accessToken))
			.retrieve()
			.body(JSON_LIST);

		if (activities == null) {
			return List.of();
		}

		return activities.stream()
			.map(this::toImportableItem)
			.toList();
	}

	private ImportableItem toImportableItem(Map<String, Object> activity) {
		String externalId = String.valueOf(activity.get("id"));
		String title = (String) activity.get("name");
		Instant startedAt = Instant.parse((String) activity.get("start_date"));
		long elapsedSeconds = ((Number) activity.getOrDefault("elapsed_time", 0)).longValue();
		return new ImportableItem(externalId, title, startedAt, startedAt.plusSeconds(elapsedSeconds));
	}

	private void requireConfigured() {
		if (!isConfigured()) {
			throw new IntegrationProviderNotConfiguredException("Strava is not configured yet");
		}
	}

}
