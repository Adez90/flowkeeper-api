package se.flowkeeper.api.registration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure logic tests with mocked repositories — fast, no database. The
 * end-to-end path (real Postgres, real HTTP, real security filter chain)
 * is covered separately by RegistrationIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

	@Mock
	UserRepository userRepository;
	@Mock
	AccountRepository accountRepository;
	@Mock
	AccountMemberRepository accountMemberRepository;

	@Test
	void firstLoginProvisionsUserPersonalAccountAndOwnerMembership() {
		RegistrationService service = new RegistrationService(userRepository, accountRepository, accountMemberRepository);
		Jwt jwt = jwtFor("kc-subject-1", "Anders Johansson", "anders@example.com");

		when(userRepository.findByKeycloakSubject("kc-subject-1")).thenReturn(Optional.empty());
		User savedUser = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		when(userRepository.save(any(User.class))).thenReturn(savedUser);
		Account savedAccount = new Account(AccountType.PERSONAL, "Anders Johansson");
		when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);
		AccountMember savedMembership = new AccountMember(savedAccount, savedUser, MemberRole.OWNER);
		when(accountMemberRepository.save(any(AccountMember.class))).thenReturn(savedMembership);

		RegistrationResponse response = service.registerCurrentUser(jwt);

		assertThat(response.alreadyRegistered()).isFalse();
		assertThat(response.role()).isEqualTo("OWNER");
		verify(userRepository).save(any(User.class));
		verify(accountRepository).save(any(Account.class));
		verify(accountMemberRepository).save(any(AccountMember.class));
	}

	@Test
	void repeatedLoginIsIdempotentAndCreatesNothing() {
		RegistrationService service = new RegistrationService(userRepository, accountRepository, accountMemberRepository);
		Jwt jwt = jwtFor("kc-subject-1", "Anders Johansson", "anders@example.com");

		User existingUser = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
		Account existingAccount = new Account(AccountType.PERSONAL, "Anders Johansson");
		AccountMember existingMembership = new AccountMember(existingAccount, existingUser, MemberRole.OWNER);

		when(userRepository.findByKeycloakSubject("kc-subject-1")).thenReturn(Optional.of(existingUser));
		when(accountMemberRepository.findByUser(existingUser)).thenReturn(List.of(existingMembership));

		RegistrationResponse response = service.registerCurrentUser(jwt);

		assertThat(response.alreadyRegistered()).isTrue();
		verify(userRepository, never()).save(any());
		verify(accountRepository, never()).save(any());
		verify(accountMemberRepository, never()).save(any());
	}

	@Test
	void fallsBackToEmailForDisplayNameWhenNameClaimMissing() {
		RegistrationService service = new RegistrationService(userRepository, accountRepository, accountMemberRepository);
		Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
			.subject("kc-subject-2")
			.claim("email", "noname@example.com")
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(60))
			.build();

		when(userRepository.findByKeycloakSubject("kc-subject-2")).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(accountRepository.save(any(Account.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));
		when(accountMemberRepository.save(any(AccountMember.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		service.registerCurrentUser(jwt);

		var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(userCaptor.capture());
		assertThat(userCaptor.getValue().getDisplayName()).isEqualTo("noname@example.com");
	}

	private Jwt jwtFor(String subject, String name, String email) {
		Instant now = Instant.now();
		return Jwt.withTokenValue("test-token")
			.header("alg", "none")
			.subject(subject)
			.claim("name", name)
			.claim("email", email)
			.issuedAt(now)
			.expiresAt(now.plusSeconds(300))
			.build();
	}

}
