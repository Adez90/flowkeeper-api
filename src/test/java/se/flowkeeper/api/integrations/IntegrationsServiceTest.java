package se.flowkeeper.api.integrations;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntegrationsServiceTest {

	@Mock ExternalConnectionRepository connectionRepository;
	@Mock OAuthStateRepository oauthStateRepository;
	@Mock AccountMemberRepository accountMemberRepository;
	@Mock CurrentUserResolver currentUserResolver;
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
			currentUserResolver, List.of(googleGateway, microsoftGateway),
			"http://localhost:8080", "http://localhost:5173", appleEnabled);
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
		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, null, "some-state");

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
		verify(oauthStateRepository, never()).findById(any());
	}

	@Test
	void handleCallbackReturnsTheErrorUrlForAnUnknownState() {
		when(oauthStateRepository.findById("bad-state")).thenReturn(Optional.empty());

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "bad-state");

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
	}

	@Test
	void handleCallbackConsumesTheStateEvenWhenItHasExpired() {
		OAuthState expired = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().minusSeconds(60));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(expired));

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1");

		assertThat(result).isEqualTo("http://localhost:5173/app/integrations?connected=error");
		verify(oauthStateRepository).delete(expired);
	}

	@Test
	void handleCallbackRejectsAProviderMismatch() {
		OAuthState state = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().plusSeconds(600));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(state));

		String result = service(false).handleCallback(ExternalProvider.MICROSOFT_CALENDAR, "code", "state-1");

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

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1");

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

		service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1");

		verify(connectionRepository).save(existing);
		assertThat(existing.getAccessToken()).isEqualTo("access-2");
	}

	@Test
	void handleCallbackReturnsTheErrorUrlWhenTokenExchangeFails() {
		OAuthState state = new OAuthState("state-1", user, account, ExternalProvider.GOOGLE_CALENDAR,
			"http://localhost:8080/api/v1/integrations/oauth/GOOGLE_CALENDAR/callback", Instant.now().plusSeconds(600));
		when(oauthStateRepository.findById("state-1")).thenReturn(Optional.of(state));
		when(googleGateway.exchangeCode(any(), any())).thenThrow(new RuntimeException("boom"));

		String result = service(false).handleCallback(ExternalProvider.GOOGLE_CALENDAR, "code", "state-1");

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

}
