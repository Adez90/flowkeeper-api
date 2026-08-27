package se.flowkeeper.api.organisation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ConflictException;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Pure logic tests with mocked repositories. The end-to-end path (real
 * Postgres, real HTTP, real security filter chain) is covered separately by
 * OrganisationIntegrationTest.
 */
@ExtendWith(MockitoExtension.class)
class OrganisationServiceTest {

	@Mock
	AccountRepository accountRepository;
	@Mock
	AccountMemberRepository accountMemberRepository;
	@Mock
	DepartmentRepository departmentRepository;
	@Mock
	GroupRepository groupRepository;
	@Mock
	UserRepository userRepository;
	@Mock
	CurrentUserResolver currentUserResolver;

	private OrganisationService service() {
		return new OrganisationService(accountRepository, accountMemberRepository, departmentRepository,
			groupRepository, userRepository, currentUserResolver);
	}

	@Test
	void creatingAnOrganisationMakesTheCallerItsOwner() {
		User owner = userFixture("kc-owner");
		when(currentUserResolver.require(any())).thenReturn(owner);
		Account savedAccount = new Account(AccountType.ORGANISATION, "Acme AB");
		when(accountRepository.save(any(Account.class))).thenReturn(savedAccount);

		OrganisationResponse response = service().createOrganisation(jwt(), new CreateOrganisationRequest("Acme AB"));

		assertThat(response.name()).isEqualTo("Acme AB");
		assertThat(response.role()).isEqualTo("OWNER");
	}

	@Test
	void onlyOwnerOrAdminCanCreateADepartment() {
		User member = userFixture("kc-member");
		Account org = orgFixture();
		AccountMember membership = new AccountMember(org, member, MemberRole.MEMBER);

		when(currentUserResolver.require(any())).thenReturn(member);
		when(accountRepository.findById(org.getId())).thenReturn(Optional.of(org));
		when(accountMemberRepository.findByAccount_IdAndUser(org.getId(), member)).thenReturn(Optional.of(membership));

		assertThatThrownBy(() -> service().createDepartment(jwt(), org.getId(), new CreateDepartmentRequest("Engineering")))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void cannotCreateADepartmentUnderAPersonalAccount() {
		User owner = userFixture("kc-owner");
		Account personal = new Account(AccountType.PERSONAL, "Anders Johansson");
		when(currentUserResolver.require(any())).thenReturn(owner);
		when(accountRepository.findById(any())).thenReturn(Optional.of(personal));

		assertThatThrownBy(() -> service().createDepartment(jwt(), UUID.randomUUID(), new CreateDepartmentRequest("Engineering")))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void addingAnUnregisteredEmailFails() {
		User owner = userFixture("kc-owner");
		Account org = orgFixture();
		AccountMember ownerMembership = new AccountMember(org, owner, MemberRole.OWNER);

		when(currentUserResolver.require(any())).thenReturn(owner);
		when(accountRepository.findById(org.getId())).thenReturn(Optional.of(org));
		when(accountMemberRepository.findByAccount_IdAndUser(org.getId(), owner)).thenReturn(Optional.of(ownerMembership));
		when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

		AddMemberRequest request = new AddMemberRequest("nobody@example.com", MemberRole.MEMBER, null, null);

		assertThatThrownBy(() -> service().addMember(jwt(), org.getId(), request))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void addingAnAlreadyPresentMemberConflicts() {
		User owner = userFixture("kc-owner");
		User invitee = userFixture("kc-invitee");
		Account org = orgFixture();
		AccountMember ownerMembership = new AccountMember(org, owner, MemberRole.OWNER);

		when(currentUserResolver.require(any())).thenReturn(owner);
		when(accountRepository.findById(org.getId())).thenReturn(Optional.of(org));
		when(accountMemberRepository.findByAccount_IdAndUser(org.getId(), owner)).thenReturn(Optional.of(ownerMembership));
		when(userRepository.findByEmailIgnoreCase("invitee@example.com")).thenReturn(Optional.of(invitee));
		when(accountMemberRepository.existsByAccount_IdAndUser(org.getId(), invitee)).thenReturn(true);

		AddMemberRequest request = new AddMemberRequest("invitee@example.com", MemberRole.MEMBER, null, null);

		assertThatThrownBy(() -> service().addMember(jwt(), org.getId(), request))
			.isInstanceOf(ConflictException.class);
	}

	private User userFixture(String subject) {
		return new User(subject, "Test User " + subject, subject + "@example.com");
	}

	private Account orgFixture() {
		Account account = new Account(AccountType.ORGANISATION, "Acme AB");
		return account;
	}

	private Jwt jwt() {
		Instant now = Instant.now();
		return Jwt.withTokenValue("t").header("alg", "none")
			.subject("kc-subject")
			.issuedAt(now)
			.expiresAt(now.plusSeconds(300))
			.build();
	}

}
