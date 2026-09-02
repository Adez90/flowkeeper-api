package se.flowkeeper.api.integrations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.MemberRole;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationsServiceTest {

	@Mock ExternalConnectionRepository connectionRepository;
	@Mock OAuthStateRepository oauthStateRepository;
	@Mock AccountMemberRepository accountMemberRepository;
	@Mock CurrentUserResolver currentUserResolver;
	@Mock EventRepository eventRepository;
	@Mock EventTypeRepository eventTypeRepository;
	@Mock UserTimezones userTimezones;
	@Mock OAuthCalendarGateway googleGateway;
	@Mock OAuthCalendarGateway microsoftGateway;

	// Mocked, not `new User(...)` — IntegrationsService#disconnect compares
	// user ids, and a freshly-constructed entity has a null id until JPA
	// assigns one on save (same reason Account below is mocked).
	private final User user = mockUser();
	private final Account account = mockAccount();
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private static User mockUser() {
		User user = mock(User.class);
		lenient().when(user.getId()).thenReturn(UUID.randomUUID());
		return user;
	}

	private static Account mockAccount() {
		Account account = mock(Account.class);
		lenient().when(account.getId()).thenReturn(UUID.randomUUID());
		return account;
	}

	// The constructor eagerly builds a Map<ExternalProvider, Gateway>
	// keyed by gateway.provider() — every test needs this stubbed just to
	// construct the service, whether or not that particular test cares
	// which provider each mock represents. An unstubbed mock returns null
	// for provider(), and two nulls collide as a duplicate map key.
	@BeforeEach
	void stubGatewayIdentities() {
		lenient().when(googleGateway.provider()).thenReturn(ExternalProvider.GOOGLE_CALENDAR);
		lenient().when(microsoftGateway.provider()).thenReturn(ExternalProvider.MICROSOFT_CALENDAR);
	}

	private IntegrationsService service(boolean appleEnabled) {
		return new IntegrationsService(connectionRepository, oauthStateRepository, accountMemberRepository,
			currentUserResolver, eventRepository, eventTypeRepository, userTimezones, List.of(googleGateway, microsoftGateway),
			"http://localhost:8080", "http://localhost:5173", appleEnabled);
	}

	private static EventType mockEventType(UUID accountId) {
		EventType eventType = mock(EventType.class);
		lenient().when(eventType.getId()).thenReturn(UUID.randomUUID());
		lenient().when(eventType.getAccountId()).thenReturn(accountId);
		lenient().when(eventType.getLabel()).thenReturn("Physical activity");
		return eventType;
	}

	@Test
	void listProvidersReportsOnlyConfiguredGatewaysAsAvailable() {
		when(googleGateway.isConfigured()).thenReturn(true);
		when(microsoftGateway.isConfigured()).thenReturn(false);

		List<ProviderResponse> providers = service(false).listProviders();

		assertThat(providers).hasSize(4);
		assertThat(providers).filteredOn(p -> p.provider() == ExternalProvider.GOOGLE_CALENDAR)
			.singleElement().satisfies(p -> assertThat(p.available()).isTrue());
		assertThat(providers).filteredOn(p -> p.provider() == ExternalProvider.MICROSOFT_CALENDAR)
			.singleElement().satisfies(p -> assertThat(p.available()).isFalse());
		// No gateway at all — Strava isn't in the mocked gateway list for this test.
		assertThat(providers).filteredOn(p -> p.provider() == ExternalProvider.STRAVA)
			.singleElement().satisfies(p -> assertThat(p.available()).isFalse());
		assertThat(providers).filteredOn(p -> p.provider() == ExternalProvider.APPLE_CALENDAR)
			.singleElement().satisfies(p -> assertThat(p.available()).isFalse());
	}

	@Test
	void listProvidersReflectsAppleEnabledFlagIndependentlyOfGateways() {
		lenient().when(googleGateway.isConfigured()).thenReturn(false);
		lenient().when(microsoftGateway.isConfigured()).thenReturn(false);

		List<ProviderResponse> providers = service(true).listProviders();

		assertThat(providers).filteredOn(p -> p.provider() == ExternalProvider.APPLE_CALENDAR)
			.singleElement().satisfies(p -> assertThat(p.available()).isTrue());
	}

	@Test
	void listConnectionsRejectsANonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service(false).listConnections(jwt, account.getId()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void startAuthorizationRejectsANonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service(false).startAuthorization(jwt, ExternalProvider.GOOGLE_CALENDAR,
			new StartAuthorizationRequest(account.getId())))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void startAuthorizationRejectsAnUnconfiguredProvider() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(googleGateway.isConfigured()).thenReturn(false);

		assertThatThrownBy(() -> service(false).startAuthorization(jwt, ExternalProvider.GOOGLE_CALENDAR,
			new StartAuthorizationRequest(account.getId())))
			.isInstanceOf(IntegrationProviderNotConfiguredException.class);
	}

	@Test
	void startAuthorizationRejectsAProviderWithNoGatewayAtAll() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));

		assertThatThrownBy(() -> service(false).startAuthorization(jwt, ExternalProvider.STRAVA,
			new StartAuthorizationRequest(account.getId())))
			.isInstanceOf(IntegrationProviderNotConfiguredException.class);
	}

	@Test
	void startAuthorizationSavesAStateAndReturnsTheGatewaysUrlUsingTheExactEnumNameInTheRedirectUri() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(googleGateway.isConfigured()).thenReturn(true);
		when(googleGateway.buildAuthorizationUrl(any(), any())).thenReturn("https://accounts.google.com/o/oauth2/v2/auth?...");

		AuthorizationUrlResponse response = service(false).startAuthorization(jwt, ExternalProvider.GOOGLE_CALENDAR,
			new StartAuthorizationRequest(account.getId()));

		assertThat(response.authorizationUrl()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth?...");
		verify(oauthStateRepository).save(any(OAuthState.class));
		// Not lowercased — Spring's default enum @PathVariable binding is case-sensitive.
		verify(googleGateway).buildAuthorizationUrl(any(), org.mockito.ArgumentMatchers.eq(
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback"));
	}

	@Test
	void handleCallbackReturnsTheErrorUrlWhenCodeOrStateIsMissing() {
		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, null, "some-state", null);

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
		verify(oauthStateRepository, never()).findById(any());
	}

	@Test
	void handleCallbackAcceptsAProviderErrorReasonWithoutThrowing() {
		// e.g. the user clicked "Deny" on Strava/Google's own consent screen
		// — no code, but a real reason instead of silence.
		String result = service(false).handleCallback(ExternalProvider.STRAVA, null, "some-state", "access_denied");

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
	}

	@Test
	void handleCallbackReturnsTheErrorUrlForAnUnknownState() {
		when(oauthStateRepository.findById("bad-state")).thenReturn(Optional.empty());

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "bad-state", null);

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
	}

	@Test
	void handleCallbackConsumesTheStateEvenWhenItHasExpired() {
		OAuthState expired = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().minusSeconds(60));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(expired));

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1", null);

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
		verify(oauthStateRepository).delete(expired);
	}

	@Test
	void handleCallbackRejectsAProviderMismatch() {
		OAuthState state = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().plusSeconds(600));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(state));

		String result = service(false).handleCallback(ExternalProvider.MICROSOFT_CALENDAR, "code", "state-1", null);

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
	}

	@Test
	void handleCallbackCreatesANewConnectionOnFirstSuccess() {
		OAuthState state = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().plusSeconds(600));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(state));
		when(googleGateway.exchangeCode("code", state.getRedirectUri()))
			.thenReturn(new OAuthTokenResult("access", "refresh", Instant.now().plusSeconds(3600), "anders@gmail.com"));
		when(connectionRepository.findByUser_IdAndAccount_IdAndProvider(any(), any(), any())).thenReturn(Optional.empty());

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1", null);

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=success");
		org.mockito.ArgumentCaptor<ExternalConnection> captor = org.mockito.ArgumentCaptor.forClass(ExternalConnection.class);
		verify(connectionRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
		assertThat(captor.getValue().getExternalAccountLabel()).isEqualTo("anders@gmail.com");
	}

	@Test
	void handleCallbackUpdatesAnExistingConnectionRatherThanDuplicatingIt() {
		OAuthState state = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().plusSeconds(600));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(state));
		when(googleGateway.exchangeCode(any(), any()))
			.thenReturn(new OAuthTokenResult("access-2", "refresh-2", Instant.now().plusSeconds(3600), "anders@gmail.com"));
		ExternalConnection existing = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		when(connectionRepository.findByUser_IdAndAccount_IdAndProvider(any(), any(), any())).thenReturn(Optional.of(existing));

		service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1", null);

		verify(connectionRepository).save(existing);
		assertThat(existing.getAccessToken()).isEqualTo("access-2");
	}

	@Test
	void handleCallbackReturnsTheErrorUrlWhenTokenExchangeFails() {
		OAuthState state = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().plusSeconds(600));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(state));
		when(googleGateway.exchangeCode(any(), any())).thenThrow(new RuntimeException("boom"));

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1", null);

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
		verify(connectionRepository, never()).save(any());
	}

	@Test
	void disconnectRejectsSomeoneElsesConnection() {
		User otherUser = mockUser();
		ExternalConnection connection = new ExternalConnection(otherUser, account, ExternalProvider.GOOGLE_CALENDAR);
		UUID connectionId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

		assertThatThrownBy(() -> service(false).disconnect(jwt, connectionId)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void disconnectRejectsAnUnknownConnection() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(connectionRepository.findById(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service(false).disconnect(jwt, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void disconnectMarksTheConnectionDisconnected() {
		ExternalConnection connection = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		UUID connectionId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(connectionRepository.findById(connectionId)).thenReturn(Optional.of(connection));

		service(false).disconnect(jwt, connectionId);

		assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.DISCONNECTED);
	}

	@Test
	void listImportableItemsRejectsANonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service(false).listImportableItems(jwt, account.getId(), null))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void listImportableItemsOnlyFetchesFromConnectedProviders() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(userTimezones.resolve(user)).thenReturn(ZoneOffset.UTC);

		ExternalConnection connected = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		connected.applyTokens(new OAuthTokenResult("access", "refresh", Instant.now().plusSeconds(3600), "a@b.com"));
		ExternalConnection disconnected = new ExternalConnection(user, account, ExternalProvider.MICROSOFT_CALENDAR);
		disconnected.disconnect();
		when(connectionRepository.findByUser_IdAndAccount_Id(user.getId(), account.getId()))
			.thenReturn(List.of(connected, disconnected));
		when(eventRepository.findExternalIdByUser_IdAndExternalProvider(any(), any())).thenReturn(List.of());
		when(googleGateway.fetchDayItems(eq("access"), any(), any())).thenReturn(List.of());

		List<ImportableGroupResponse> groups = service(false).listImportableItems(jwt, account.getId(), null);

		assertThat(groups).hasSize(1);
		assertThat(groups.get(0).provider()).isEqualTo(ExternalProvider.GOOGLE_CALENDAR);
		verify(microsoftGateway, never()).fetchDayItems(any(), any(), any());
	}

	@Test
	void listImportableItemsFiltersOutAlreadyImportedIds() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(userTimezones.resolve(user)).thenReturn(ZoneOffset.UTC);

		ExternalConnection connection = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		connection.applyTokens(new OAuthTokenResult("access", "refresh", Instant.now().plusSeconds(3600), "a@b.com"));
		when(connectionRepository.findByUser_IdAndAccount_Id(user.getId(), account.getId())).thenReturn(List.of(connection));

		ImportableItem alreadyImported = new ImportableItem("ext-1", "Standup", Instant.now(), Instant.now().plusSeconds(1800));
		ImportableItem fresh = new ImportableItem("ext-2", "Run", Instant.now(), Instant.now().plusSeconds(1800));
		when(googleGateway.fetchDayItems(eq("access"), any(), any())).thenReturn(List.of(alreadyImported, fresh));
		when(eventRepository.findExternalIdByUser_IdAndExternalProvider(user.getId(), ExternalProvider.GOOGLE_CALENDAR))
			.thenReturn(List.of("ext-1"));

		List<ImportableGroupResponse> groups = service(false).listImportableItems(jwt, account.getId(), null);

		assertThat(groups.get(0).items()).extracting(ImportableItem::externalId).containsExactly("ext-2");
	}

	@Test
	void listImportableItemsRefreshesAnExpiredToken() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(userTimezones.resolve(user)).thenReturn(ZoneOffset.UTC);

		ExternalConnection connection = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		connection.applyTokens(new OAuthTokenResult("stale-access", "refresh", Instant.now().minusSeconds(60), "a@b.com"));
		when(connectionRepository.findByUser_IdAndAccount_Id(user.getId(), account.getId())).thenReturn(List.of(connection));
		when(googleGateway.refreshAccessToken("refresh"))
			.thenReturn(new OAuthTokenResult("fresh-access", null, Instant.now().plusSeconds(3600), null));
		when(eventRepository.findExternalIdByUser_IdAndExternalProvider(any(), any())).thenReturn(List.of());
		when(googleGateway.fetchDayItems(eq("fresh-access"), any(), any())).thenReturn(List.of());

		service(false).listImportableItems(jwt, account.getId(), null);

		assertThat(connection.getAccessToken()).isEqualTo("fresh-access");
		verify(connectionRepository).save(connection);
	}

	@Test
	void listImportableItemsSkipsRefreshingAStillValidToken() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(userTimezones.resolve(user)).thenReturn(ZoneOffset.UTC);

		ExternalConnection connection = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		connection.applyTokens(new OAuthTokenResult("access", "refresh", Instant.now().plusSeconds(3600), "a@b.com"));
		when(connectionRepository.findByUser_IdAndAccount_Id(user.getId(), account.getId())).thenReturn(List.of(connection));
		when(eventRepository.findExternalIdByUser_IdAndExternalProvider(any(), any())).thenReturn(List.of());
		when(googleGateway.fetchDayItems(eq("access"), any(), any())).thenReturn(List.of());

		service(false).listImportableItems(jwt, account.getId(), null);

		verify(googleGateway, never()).refreshAccessToken(any());
	}

	@Test
	void listImportableItemsMarksNeedsReconnectWhenAProviderFailsWithoutBreakingOthers() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(userTimezones.resolve(user)).thenReturn(ZoneOffset.UTC);

		ExternalConnection google = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		google.applyTokens(new OAuthTokenResult("access", "refresh", Instant.now().minusSeconds(60), "a@b.com"));
		ExternalConnection microsoft = new ExternalConnection(user, account, ExternalProvider.MICROSOFT_CALENDAR);
		microsoft.applyTokens(new OAuthTokenResult("ms-access", "ms-refresh", Instant.now().plusSeconds(3600), "b@c.com"));
		when(connectionRepository.findByUser_IdAndAccount_Id(user.getId(), account.getId())).thenReturn(List.of(google, microsoft));
		when(googleGateway.refreshAccessToken("refresh")).thenThrow(new RuntimeException("revoked"));
		when(eventRepository.findExternalIdByUser_IdAndExternalProvider(any(), any())).thenReturn(List.of());
		when(microsoftGateway.fetchDayItems(eq("ms-access"), any(), any())).thenReturn(List.of());

		List<ImportableGroupResponse> groups = service(false).listImportableItems(jwt, account.getId(), null);

		assertThat(groups).hasSize(2);
		assertThat(groups).filteredOn(g -> g.provider() == ExternalProvider.GOOGLE_CALENDAR)
			.singleElement().satisfies(g -> {
				assertThat(g.needsReconnect()).isTrue();
				assertThat(g.items()).isEmpty();
			});
		assertThat(groups).filteredOn(g -> g.provider() == ExternalProvider.MICROSOFT_CALENDAR)
			.singleElement().satisfies(g -> assertThat(g.needsReconnect()).isFalse());
	}

	@Test
	void listImportableItemsRetriesAConnectionThatPreviouslyErrored() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(userTimezones.resolve(user)).thenReturn(ZoneOffset.UTC);

		// A prior failed attempt left this ERROR — it must still be tried again, not
		// silently dropped forever, since whatever failed before may now be fine.
		ExternalConnection connection = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		connection.applyTokens(new OAuthTokenResult("access", "refresh", Instant.now().plusSeconds(3600), "a@b.com"));
		connection.markError("revoked");
		when(connectionRepository.findByUser_IdAndAccount_Id(user.getId(), account.getId())).thenReturn(List.of(connection));
		when(eventRepository.findExternalIdByUser_IdAndExternalProvider(any(), any())).thenReturn(List.of());
		when(googleGateway.fetchDayItems(eq("access"), any(), any())).thenReturn(List.of());

		List<ImportableGroupResponse> groups = service(false).listImportableItems(jwt, account.getId(), null);

		assertThat(groups).hasSize(1);
		assertThat(groups.get(0).needsReconnect()).isFalse();
	}

	@Test
	void listImportableItemsClearsErrorStatusOnceAConnectionRecovers() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(userTimezones.resolve(user)).thenReturn(ZoneOffset.UTC);

		ExternalConnection connection = new ExternalConnection(user, account, ExternalProvider.GOOGLE_CALENDAR);
		connection.applyTokens(new OAuthTokenResult("access", "refresh", Instant.now().plusSeconds(3600), "a@b.com"));
		connection.markError("revoked");
		when(connectionRepository.findByUser_IdAndAccount_Id(user.getId(), account.getId())).thenReturn(List.of(connection));
		when(eventRepository.findExternalIdByUser_IdAndExternalProvider(any(), any())).thenReturn(List.of());
		when(googleGateway.fetchDayItems(eq("access"), any(), any())).thenReturn(List.of());

		service(false).listImportableItems(jwt, account.getId(), null);

		assertThat(connection.getStatus()).isEqualTo(ConnectionStatus.CONNECTED);
		assertThat(connection.getLastError()).isNull();
	}

	@Test
	void importEventsRejectsANonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());
		ImportEventsRequest request = new ImportEventsRequest(account.getId(),
			List.of(new ImportSelectionRequest(ExternalProvider.STRAVA, "ext-1", UUID.randomUUID(), Instant.now(), Instant.now())));

		assertThatThrownBy(() -> service(false).importEvents(jwt, request)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void importEventsCreatesAnOpenEventWithNoIngoingEnergyYet() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		EventType eventType = mockEventType(null);
		when(eventTypeRepository.findById(eventType.getId())).thenReturn(Optional.of(eventType));
		when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

		Instant startedAt = Instant.now().minusSeconds(1800);
		Instant endedAt = Instant.now();
		ImportEventsRequest request = new ImportEventsRequest(account.getId(),
			List.of(new ImportSelectionRequest(ExternalProvider.STRAVA, "ext-1", eventType.getId(), startedAt, endedAt)));

		List<EventResponse> created = service(false).importEvents(jwt, request);

		assertThat(created).hasSize(1);
		assertThat(created.get(0).ingoingEnergy()).isNull();
		assertThat(created.get(0).status()).isEqualTo("OPEN");
		assertThat(created.get(0).externalProvider()).isEqualTo(ExternalProvider.STRAVA);
		assertThat(created.get(0).externalEndedAt()).isEqualTo(endedAt);
	}

	@Test
	void importEventsRejectsAnEventTypeFromAnotherAccount() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		EventType otherAccountsType = mockEventType(UUID.randomUUID());
		when(eventTypeRepository.findById(otherAccountsType.getId())).thenReturn(Optional.of(otherAccountsType));
		ImportEventsRequest request = new ImportEventsRequest(account.getId(),
			List.of(new ImportSelectionRequest(ExternalProvider.STRAVA, "ext-1", otherAccountsType.getId(), Instant.now(), Instant.now())));

		assertThatThrownBy(() -> service(false).importEvents(jwt, request)).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void importEventsRejectsAFutureStartedAt() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		ImportEventsRequest request = new ImportEventsRequest(account.getId(),
			List.of(new ImportSelectionRequest(ExternalProvider.STRAVA, "ext-1", UUID.randomUUID(),
				Instant.now().plusSeconds(3600), Instant.now().plusSeconds(7200))));

		assertThatThrownBy(() -> service(false).importEvents(jwt, request)).isInstanceOf(ValidationException.class);
	}

	@Test
	void importEventsSkipsAnItemAlreadyImportedRatherThanFailingTheWholeBatch() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		EventType eventType = mockEventType(null);
		when(eventTypeRepository.findById(eventType.getId())).thenReturn(Optional.of(eventType));
		when(eventRepository.saveAndFlush(any(Event.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate"))
			.thenAnswer(inv -> inv.getArgument(0));

		ImportEventsRequest request = new ImportEventsRequest(account.getId(), List.of(
			new ImportSelectionRequest(ExternalProvider.STRAVA, "ext-1", eventType.getId(), Instant.now(), Instant.now()),
			new ImportSelectionRequest(ExternalProvider.STRAVA, "ext-2", eventType.getId(), Instant.now(), Instant.now())));

		List<EventResponse> created = service(false).importEvents(jwt, request);

		assertThat(created).hasSize(1);
		verify(eventRepository, times(2)).saveAndFlush(any(Event.class));
	}

}
