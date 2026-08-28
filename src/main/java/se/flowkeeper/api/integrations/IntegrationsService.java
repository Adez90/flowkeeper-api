package se.flowkeeper.api.integrations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class IntegrationsService {

	private static final Logger log = LoggerFactory.getLogger(IntegrationsService.class);
	private static final SecureRandom RANDOM = new SecureRandom();

	private final ExternalConnectionRepository connectionRepository;
	private final OAuthStateRepository oauthStateRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final CurrentUserResolver currentUserResolver;
	private final Map<ExternalProvider, OAuthCalendarGateway> gatewaysByProvider;
	private final String apiOrigin;
	private final String appOrigin;
	private final boolean appleEnabled;

	public IntegrationsService(ExternalConnectionRepository connectionRepository,
			OAuthStateRepository oauthStateRepository,
			AccountMemberRepository accountMemberRepository,
			CurrentUserResolver currentUserResolver,
			List<OAuthCalendarGateway> gateways,
			@Value("${app.integrations.api-origin}") String apiOrigin,
			@Value("${app.cors.allowed-origin}") String appOrigin,
			@Value("${app.integrations.apple.enabled}") boolean appleEnabled) {
		this.connectionRepository = connectionRepository;
		this.oauthStateRepository = oauthStateRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.currentUserResolver = currentUserResolver;
		this.gatewaysByProvider = gateways.stream().collect(Collectors.toMap(OAuthCalendarGateway::provider, Function.identity()));
		this.apiOrigin = apiOrigin;
		this.appOrigin = appOrigin;
		this.appleEnabled = appleEnabled;
	}

	/** Which providers the client should offer a "Connect" button for — driven entirely by config, not by who's asking. */
	public List<ProviderResponse> listProviders() {
		List<ProviderResponse> result = new ArrayList<>();
		for (ExternalProvider provider : ExternalProvider.values()) {
			boolean available = provider == ExternalProvider.APPLE_CALENDAR
				? appleEnabled
				: gatewaysByProvider.containsKey(provider) && gatewaysByProvider.get(provider).isConfigured();
			result.add(new ProviderResponse(provider, available));
		}
		return result;
	}

	/** The caller's own connections for this account — not other members'; a connected email is otherwise private. */
	@Transactional(readOnly = true)
	public List<ConnectionResponse> listConnections(Jwt jwt, UUID accountId) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);

		return connectionRepository.findByUser_IdAndAccount_Id(user.getId(), accountId).stream()
			.map(ConnectionResponse::from)
			.toList();
	}

	@Transactional
	public AuthorizationUrlResponse startAuthorization(Jwt jwt, ExternalProvider provider, StartAuthorizationRequest request) {
		User user = currentUserResolver.require(jwt);
		Account account = requireMembership(request.accountId(), user);

		OAuthCalendarGateway gateway = gatewaysByProvider.get(provider);
		if (gateway == null || !gateway.isConfigured()) {
			throw new IntegrationProviderNotConfiguredException(provider + " is not configured yet");
		}

		// Spring's default enum @PathVariable binding is case-sensitive
		// (Enum.valueOf) — this must stay the exact constant name, not a
		// prettied-up lowercase form, or IntegrationsCallbackController
		// fails to parse it back out of the real provider's redirect.
		String redirectUri = apiOrigin + "/api/v1/integrations/oauth/" + provider.name() + "/callback";
		String state = generateState();
		oauthStateRepository.save(new OAuthState(state, user, account, provider, redirectUri,
			Instant.now().plus(10, ChronoUnit.MINUTES)));

		return new AuthorizationUrlResponse(gateway.buildAuthorizationUrl(state, redirectUri));
	}

	/**
	 * Returns the URL to redirect the browser to — success or error land on
	 * the same web-app page either way, since this is a browser redirect
	 * response, not a JSON API call the caller can branch on.
	 */
	@Transactional
	public String handleCallback(ExternalProvider provider, String code, String state) {
		String successUrl = appOrigin + "/app/integrations?connected=success";
		String errorUrl = appOrigin + "/app/integrations?connected=error";

		if (code == null || state == null) {
			return errorUrl;
		}

		OAuthState oauthState = oauthStateRepository.findById(state).orElse(null);
		if (oauthState == null) {
			log.warn("OAuth callback for {} with an unknown or already-used state", provider);
			return errorUrl;
		}
		// Single-use regardless of what happens next — consumed the moment it's looked up.
		oauthStateRepository.delete(oauthState);

		if (oauthState.isExpired(Instant.now()) || oauthState.getProvider() != provider) {
			log.warn("OAuth callback for {} with an expired or mismatched state", provider);
			return errorUrl;
		}

		OAuthCalendarGateway gateway = gatewaysByProvider.get(provider);
		if (gateway == null) {
			return errorUrl;
		}

		try {
			OAuthTokenResult tokens = gateway.exchangeCode(code, oauthState.getRedirectUri());
			ExternalConnection connection = connectionRepository
				.findByUser_IdAndAccount_IdAndProvider(oauthState.getUser().getId(), oauthState.getAccount().getId(), provider)
				.orElseGet(() -> new ExternalConnection(oauthState.getUser(), oauthState.getAccount(), provider));
			connection.applyTokens(tokens);
			connectionRepository.save(connection);
			log.info("User {} connected {} for account {}", oauthState.getUser().getId(), provider, oauthState.getAccount().getId());
			return successUrl;
		} catch (Exception e) {
			log.warn("OAuth token exchange failed for {}: {}", provider, e.getMessage());
			return errorUrl;
		}
	}

	@Transactional
	public void disconnect(Jwt jwt, UUID connectionId) {
		User user = currentUserResolver.require(jwt);
		ExternalConnection connection = connectionRepository.findById(connectionId)
			.orElseThrow(() -> new ResourceNotFoundException("Unknown connection: " + connectionId));
		if (!connection.getUser().getId().equals(user.getId())) {
			throw new AccessDeniedException("Not your connection");
		}

		connection.disconnect();
		log.info("User {} disconnected {} ({})", user.getId(), connection.getProvider(), connectionId);
	}

	private Account requireMembership(UUID accountId, User user) {
		return accountMemberRepository.findByAccount_IdAndUser(accountId, user)
			.map(AccountMember::getAccount)
			.orElseThrow(() -> new AccessDeniedException(
				"User %s is not a member of account %s".formatted(user.getId(), accountId)));
	}

	private static String generateState() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

}
