package se.flowkeeper.api.integrations;

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

}
