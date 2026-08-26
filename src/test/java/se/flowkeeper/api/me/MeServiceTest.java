package se.flowkeeper.api.me;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeServiceTest {

	@Mock
	UserRepository userRepository;
	@Mock
	AccountMemberRepository accountMemberRepository;
	@Mock
	CurrentUserResolver currentUserResolver;

	private MeService service() {
		return new MeService(userRepository, accountMemberRepository, currentUserResolver);
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
