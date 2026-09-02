package se.flowkeeper.api.me;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.avatar.AvatarStorageService;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

	@Mock
	UserRepository userRepository;
	@Mock
	AccountMemberRepository accountMemberRepository;
	@Mock
	CurrentUserResolver currentUserResolver;
	@Mock
	AvatarStorageService avatarStorageService;

	// uploadAvatar builds an absolute URL from the current request (Caddy's
	// forwarded scheme/host in production) via ServletUriComponentsBuilder,
	// which needs a request bound to this thread even in a plain unit test.
	@BeforeEach
	void bindMockRequest() {
		RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
	}

	@AfterEach
	void unbindMockRequest() {
		RequestContextHolder.resetRequestAttributes();
	}

	private MeService service() {
		return new MeService(userRepository, accountMemberRepository, currentUserResolver, avatarStorageService);
	}

	@Test
	void returnsProfileWithAccountsForRegisteredUser() {
		Jwt jwt = jwtFor("kc-subject-1");

		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		Account account = new Account(AccountType.PERSONAL, "Anders Johansson");
		AccountMember membership = new AccountMember(account, user, MemberRole.OWNER);

		when(userRepository.findByKeycloakSubject("kc-subject-1")).thenReturn(Optional.of(user));
		when(accountMemberRepository.findByUser(user)).thenReturn(List.of(membership));

		Optional<MeResponse> response = service().currentUser(jwt);

		assertThat(response).isPresent();
		assertThat(response.get().displayName()).isEqualTo("Anders Johansson");
		assertThat(response.get().timezone()).isEqualTo("UTC");
		assertThat(response.get().accounts()).hasSize(1);
		assertThat(response.get().accounts().get(0).role()).isEqualTo("OWNER");
		assertThat(response.get().accounts().get(0).type()).isEqualTo("PERSONAL");
	}

	@Test
	void returnsEmptyForUnregisteredSubject() {
		Jwt jwt = jwtFor("kc-unknown-subject");

		when(userRepository.findByKeycloakSubject("kc-unknown-subject")).thenReturn(Optional.empty());

		assertThat(service().currentUser(jwt)).isEmpty();
	}

	@Test
	void updateProfileAppliesEveryField() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		MeResponse response = service().updateProfile(jwt,
			new UpdateProfileRequest("Anders J.", "Europe/Stockholm", "sv", "https://example.com/me.png"));

		assertThat(response.displayName()).isEqualTo("Anders J.");
		assertThat(response.timezone()).isEqualTo("Europe/Stockholm");
		assertThat(response.locale()).isEqualTo("sv");
		assertThat(response.avatarUrl()).isEqualTo("https://example.com/me.png");
	}

	@Test
	void updateProfileRejectsAnInvalidTimezone() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		assertThatThrownBy(() -> service().updateProfile(jwt,
			new UpdateProfileRequest("Anders Johansson", "Not/AZone", null, null)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void notificationPreferencesDefaultToAllOptedOut() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		MeResponse response = service().updateProfile(jwt,
			new UpdateProfileRequest("Anders Johansson", "UTC", null, null));

		assertThat(response.notifyInApp()).isFalse();
		assertThat(response.notifyPush()).isFalse();
		assertThat(response.notifyEmail()).isFalse();
	}

	@Test
	void updateNotificationPreferencesAppliesEveryChannel() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		MeResponse response = service().updateNotificationPreferences(jwt,
			new UpdateNotificationPreferencesRequest(true, true, false));

		assertThat(response.notifyInApp()).isTrue();
		assertThat(response.notifyPush()).isTrue();
		assertThat(response.notifyEmail()).isFalse();
	}

	@Test
	void updatePushTokenPersistsTheToken() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		service().updatePushToken(jwt, new UpdatePushTokenRequest("ExponentPushToken[abc123]"));

		assertThat(user.getExpoPushToken()).isEqualTo("ExponentPushToken[abc123]");
	}

	@Test
	void uploadAvatarStoresTheFileAndPointsAvatarUrlAtIt() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[] {1, 2, 3});
		when(avatarStorageService.store(file)).thenReturn("11111111-1111-1111-1111-111111111111.jpg");

		MeResponse response = service().uploadAvatar(jwt, file);

		assertThat(response.avatarUrl()).endsWith("/api/v1/avatars/11111111-1111-1111-1111-111111111111.jpg");
		assertThat(user.getAvatarUrl()).isEqualTo(response.avatarUrl());
	}

	@Test
	void uploadAvatarDeletesThePreviousServerStoredAvatar() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		user.updateAvatarUrl("http://localhost/api/v1/avatars/22222222-2222-2222-2222-222222222222.png");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[] {1, 2, 3});
		when(avatarStorageService.store(file)).thenReturn("11111111-1111-1111-1111-111111111111.jpg");

		service().uploadAvatar(jwt, file);

		verify(avatarStorageService).delete("22222222-2222-2222-2222-222222222222.png");
	}

	@Test
	void uploadAvatarNeverDeletesALegacyPastedUrlAvatar() {
		Jwt jwt = jwtFor("kc-subject-1");
		User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		user.updateAvatarUrl("https://gravatar.com/avatar/deadbeef");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		MockMultipartFile file = new MockMultipartFile("file", "me.jpg", "image/jpeg", new byte[] {1, 2, 3});
		when(avatarStorageService.store(file)).thenReturn("11111111-1111-1111-1111-111111111111.jpg");

		service().uploadAvatar(jwt, file);

		verify(avatarStorageService, never()).delete(any());
	}

	private Jwt jwtFor(String subject) {
		Instant now = Instant.now();
		return Jwt.withTokenValue("test-token")
			.header("alg", "none")
			.subject(subject)
			.issuedAt(now)
			.expiresAt(now.plusSeconds(300))
			.build();
	}

}
