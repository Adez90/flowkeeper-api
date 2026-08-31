package se.flowkeeper.api.integrations;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * One OAuth2-based provider (Google, Microsoft, Strava — Apple's path is
 * different and isn't wired up yet). Each implementation reads its own
 * client-id/secret via config, blank by default: isConfigured() is what
 * IntegrationsService uses to decide whether a provider is offered at all.
 */
public interface OAuthCalendarGateway {

	ExternalProvider provider();

	boolean isConfigured();

	String buildAuthorizationUrl(String state, String redirectUri);

	OAuthTokenResult exchangeCode(String code, String redirectUri);

	/**
	 * A new access token from a stored refresh token — access tokens are
	 * short-lived (an hour or so for every provider here), so this runs
	 * before every importable-items fetch once the stored one is expired
	 * or close to it. externalAccountLabel is always null on the result;
	 * the caller keeps whatever label it already has.
	 */
	OAuthTokenResult refreshAccessToken(String refreshToken);

	/** Everything this provider reports for the given local day, in the caller's own timezone. */
	List<ImportableItem> fetchDayItems(String accessToken, LocalDate date, ZoneId zone);

}
