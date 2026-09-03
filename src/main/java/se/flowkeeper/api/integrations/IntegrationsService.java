package se.flowkeeper.api.integrations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.event.Event;
import se.flowkeeper.api.event.EventRepository;
import se.flowkeeper.api.event.EventResponse;
import se.flowkeeper.api.event.EventType;
import se.flowkeeper.api.event.EventTypeRepository;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserTimezones;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
	private final EventRepository eventRepository;
	private final EventTypeRepository eventTypeRepository;
	private final UserTimezones userTimezones;
	private final Map<ExternalProvider, OAuthCalendarGateway> gatewaysByProvider;
	private final String apiOrigin;
	private final String appOrigin;
	private final boolean appleEnabled;

	public IntegrationsService(ExternalConnectionRepository connectionRepository,
			OAuthStateRepository oauthStateRepository,
			AccountMemberRepository accountMemberRepository,
			CurrentUserResolver currentUserResolver,
			EventRepository eventRepository,
			EventTypeRepository eventTypeRepository,
			UserTimezones userTimezones,
			List<OAuthCalendarGateway> gateways,
			@Value("${app.integrations.api-origin}") String apiOrigin,
			@Value("${app.cors.allowed-origin}") String appOrigin,
			@Value("${app.integrations.apple.enabled}") boolean appleEnabled) {
		this.connectionRepository = connectionRepository;
		this.oauthStateRepository = oauthStateRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.currentUserResolver = currentUserResolver;
		this.eventRepository = eventRepository;
		this.eventTypeRepository = eventTypeRepository;
		this.userTimezones = userTimezones;
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
	public String handleCallback(ExternalProvider provider, String code, String state, String error) {
		String successUrl = appOrigin + "/app/integrations?connected=success";
		String errorUrl = appOrigin + "/app/integrations?connected=error";

		if (code == null || state == null) {
			// error is typically "access_denied" (the user declined on the
			// provider's own consent screen) but could be anything the
			// provider sends — logged as-is rather than assumed.
			log.warn("OAuth callback for {} came back without a code (error={}, state present={})", provider, error, state != null);
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
			log.warn("OAuth callback for {} but no gateway is registered for it", provider);
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
			log.warn("OAuth token exchange failed for {}: {}", provider, e.getMessage(), e);
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

	/**
	 * Everything the caller's connected providers report for one day,
	 * minus whatever's already been imported. Safe to call repeatedly
	 * through the day — pressing "Import events" again only ever surfaces
	 * what's new since the last check.
	 */
	@Transactional
	public List<ImportableGroupResponse> listImportableItems(Jwt jwt, UUID accountId, LocalDate date) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);
		ZoneId zone = userTimezones.resolve(user);
		LocalDate day = date != null ? date : LocalDate.now(zone);

		// Retry ERROR connections too, not just CONNECTED ones — a past failure (an expired
		// token, a transient outage) shouldn't permanently hide a provider from future checks;
		// only an explicit disconnect (no tokens left to use) should. See fetchGroup: a
		// successful fetch clears ERROR back to CONNECTED, so this is self-healing.
		List<ExternalConnection> connections = connectionRepository.findByUser_IdAndAccount_Id(user.getId(), accountId).stream()
			.filter(c -> c.getStatus() != ConnectionStatus.DISCONNECTED)
			.toList();

		List<ImportableGroupResponse> groups = new ArrayList<>();
		for (ExternalConnection connection : connections) {
			OAuthCalendarGateway gateway = gatewaysByProvider.get(connection.getProvider());
			if (gateway == null) {
				continue;
			}
			groups.add(fetchGroup(connection, gateway, user, day, zone));
		}
		return groups;
	}

	private ImportableGroupResponse fetchGroup(ExternalConnection connection, OAuthCalendarGateway gateway, User user, LocalDate day, ZoneId zone) {
		try {
			String accessToken = ensureFreshToken(connection, gateway);
			List<ImportableItem> items = gateway.fetchDayItems(accessToken, day, zone);
			connection.clearError();

			Set<String> alreadyImported = new HashSet<>(
				eventRepository.findExternalIdByUser_IdAndExternalProvider(user.getId(), connection.getProvider()));
			List<ImportableItem> unseen = items.stream().filter(item -> !alreadyImported.contains(item.externalId())).toList();

			return new ImportableGroupResponse(connection.getProvider(), false, unseen);
		} catch (Exception e) {
			// A single provider failing (revoked access, an expired refresh
			// token, a transient outage) shouldn't take the whole request
			// down — the other connected providers still return normally.
			log.warn("Couldn't fetch importable items from {} for user {}: {}", connection.getProvider(), user.getId(), e.getMessage(), e);
			connection.markError(e.getMessage());
			return new ImportableGroupResponse(connection.getProvider(), true, List.of());
		}
	}

	private String ensureFreshToken(ExternalConnection connection, OAuthCalendarGateway gateway) {
		Instant expiresAt = connection.getTokenExpiresAt();
		if (expiresAt == null || expiresAt.isBefore(Instant.now().plus(2, ChronoUnit.MINUTES))) {
			OAuthTokenResult refreshed = gateway.refreshAccessToken(connection.getRefreshToken());
			connection.applyRefreshedTokens(refreshed);
			connectionRepository.save(connection);
		}
		return connection.getAccessToken();
	}

	/**
	 * The caller's own already-imported external IDs for one provider. Only
	 * needed for a provider with no server-side connection to drive
	 * {@link #listImportableItems}'s own dedup (on-device calendar import:
	 * the client reads its own calendar locally and has nothing to fetch
	 * from here, but still needs to know what it's already imported so it
	 * doesn't keep re-offering the same items).
	 */
	@Transactional(readOnly = true)
	public List<String> listImportedExternalIds(Jwt jwt, UUID accountId, ExternalProvider provider) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);
		return eventRepository.findExternalIdByUser_IdAndExternalProvider(user.getId(), provider);
	}

	/** Turns a set of previously-listed importable items into real, open events — each starts with no ingoing energy yet, see Event#start. */
	@Transactional
	public List<EventResponse> importEvents(Jwt jwt, ImportEventsRequest request) {
		User user = currentUserResolver.require(jwt);
		Account account = requireMembership(request.accountId(), user);
		Instant now = Instant.now();

		List<EventResponse> created = new ArrayList<>();
		for (ImportSelectionRequest selection : request.selections()) {
			if (selection.startedAt().isAfter(now)) {
				throw new ValidationException("startedAt cannot be in the future");
			}
			EventType eventType = eventTypeRepository.findById(selection.eventTypeId())
				.orElseThrow(() -> new ResourceNotFoundException("Unknown event type: " + selection.eventTypeId()));
			if (eventType.getAccountId() != null && !eventType.getAccountId().equals(account.getId())) {
				throw new ResourceNotFoundException("Event type does not belong to this account: " + selection.eventTypeId());
			}

			Event event = new Event(user, account, eventType, selection.startedAt(), selection.endedAt(),
				selection.provider(), selection.externalId());
			try {
				event = eventRepository.saveAndFlush(event);
			} catch (DataIntegrityViolationException e) {
				// Already imported — a second "Import events" press racing
				// this one, or the same item selected twice in one batch.
				// Skip it rather than failing the whole request.
				continue;
			}
			created.add(EventResponse.from(event));
		}

		log.info("User {} imported {} event(s) into account {}", user.getId(), created.size(), account.getId());
		return created;
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
