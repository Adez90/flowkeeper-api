package se.flowkeeper.api.coachfeedback;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.event.Event;
import se.flowkeeper.api.event.EventRepository;
import se.flowkeeper.api.event.EventResponse;
import se.flowkeeper.api.event.EventType;
import se.flowkeeper.api.organisation.Department;
import se.flowkeeper.api.organisation.Group;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoachFeedbackServiceTest {

	@Mock CoachFeedbackRepository coachFeedbackRepository;
	@Mock AccountRepository accountRepository;
	@Mock AccountMemberRepository accountMemberRepository;
	@Mock EventRepository eventRepository;
	@Mock CurrentUserResolver currentUserResolver;

	private final UUID accountId = UUID.randomUUID();
	// Same null-id gotcha as User (see userWithId below): account.getId() is
	// compared against accountId in create()'s event-ownership check, so it
	// needs a real value here too, not the null a bare `new Account(...)` has.
	private final Account account = accountWithId(accountId);
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private static Account accountWithId(UUID id) {
		Account account = new Account(AccountType.ORGANISATION, "Acme AB");
		ReflectionTestUtils.setField(account, "id", id);
		return account;
	}

	private CoachFeedbackService service() {
		return new CoachFeedbackService(coachFeedbackRepository, accountRepository, accountMemberRepository, eventRepository, currentUserResolver);
	}

	private void stubOrganisation() {
		when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));
	}

	// User.id is a JPA @GeneratedValue, null until actually persisted — a
	// real User's id is always non-null at runtime, but a bare `new User(...)`
	// in a Mockito test isn't, and findByAccount_IdAndUser_Id is stubbed by
	// that id: two different users both stubbed with a null id collide (the
	// second when(...) call silently overwrites the first). Giving every test
	// user a real id sidesteps that, same as ReflectionTestUtils is already
	// used for Group/Department ids elsewhere in this suite.
	private User userWithId(String subject, String name, String email) {
		User user = new User(subject, name, email);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		return user;
	}

	@Test
	void coachCanLeaveFreeformFeedbackForTheirOwnGroupsMember() {
		stubOrganisation();
		User coach = userWithId("kc-coach", "Coach", "coach@example.com");
		User member = userWithId("kc-member", "Member", "member@example.com");
		Group group = new Group(account, null, "Backend");
		ReflectionTestUtils.setField(group, "id", UUID.randomUUID());
		AccountMember coachMembership = new AccountMember(account, coach, MemberRole.COACH, null, group);
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(coach);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, coach.getId())).thenReturn(Optional.of(coachMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));
		when(coachFeedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CoachFeedbackResponse response = service().create(jwt, accountId, member.getId(), new CreateCoachFeedbackRequest("Great focus this week", null));

		assertThat(response.note()).isEqualTo("Great focus this week");
		assertThat(response.eventId()).isNull();
		ArgumentCaptor<CoachFeedback> captor = ArgumentCaptor.forClass(CoachFeedback.class);
		verify(coachFeedbackRepository).save(captor.capture());
		assertThat(captor.getValue().getEvent()).isNull();
		assertThat(captor.getValue().getMember()).isEqualTo(member);
	}

	@Test
	void coachCannotLeaveFeedbackForAMemberOutsideTheirGroup() {
		stubOrganisation();
		User coach = userWithId("kc-coach", "Coach", "coach@example.com");
		User member = userWithId("kc-member", "Member", "member@example.com");
		Group coachGroup = new Group(account, null, "Backend");
		ReflectionTestUtils.setField(coachGroup, "id", UUID.randomUUID());
		Group otherGroup = new Group(account, null, "Frontend");
		ReflectionTestUtils.setField(otherGroup, "id", UUID.randomUUID());
		AccountMember coachMembership = new AccountMember(account, coach, MemberRole.COACH, null, coachGroup);
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER, null, otherGroup);

		when(currentUserResolver.require(jwt)).thenReturn(coach);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, coach.getId())).thenReturn(Optional.of(coachMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));

		assertThatThrownBy(() -> service().create(jwt, accountId, member.getId(), new CreateCoachFeedbackRequest("Note", null)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void adminCanLeaveFeedbackForAMemberInTheirDepartmentViaAGroup() {
		stubOrganisation();
		User admin = userWithId("kc-admin", "Admin", "admin@example.com");
		User member = userWithId("kc-member", "Member", "member@example.com");
		Department department = new Department(account, "Engineering");
		ReflectionTestUtils.setField(department, "id", UUID.randomUUID());
		Group group = new Group(account, department, "Backend");
		AccountMember adminMembership = new AccountMember(account, admin, MemberRole.ADMIN, department, null);
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(admin);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, admin.getId())).thenReturn(Optional.of(adminMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));
		when(coachFeedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CoachFeedbackResponse response = service().create(jwt, accountId, member.getId(), new CreateCoachFeedbackRequest("Solid quarter", null));

		assertThat(response.note()).isEqualTo("Solid quarter");
	}

	@Test
	void plainMemberCannotLeaveFeedbackForAnyone() {
		stubOrganisation();
		User member = userWithId("kc-member", "Member", "member@example.com");
		User other = userWithId("kc-other", "Other", "other@example.com");
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER);
		AccountMember otherMembership = new AccountMember(account, other, MemberRole.MEMBER);

		when(currentUserResolver.require(jwt)).thenReturn(member);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, other.getId())).thenReturn(Optional.of(otherMembership));

		assertThatThrownBy(() -> service().create(jwt, accountId, other.getId(), new CreateCoachFeedbackRequest("Note", null)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void eventAttachedFeedbackMustBelongToTheMemberInThisAccount() {
		stubOrganisation();
		User owner = userWithId("kc-owner", "Owner", "owner@example.com");
		User member = userWithId("kc-member", "Member", "member@example.com");
		User someoneElse = userWithId("kc-else", "Else", "else@example.com");
		AccountMember ownerMembership = new AccountMember(account, owner, MemberRole.OWNER);
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER);

		when(currentUserResolver.require(jwt)).thenReturn(owner);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, owner.getId())).thenReturn(Optional.of(ownerMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));

		UUID eventId = UUID.randomUUID();
		Event someoneElsesEvent = new Event(someoneElse, account, Mockito.mock(EventType.class), (short) 3, null);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(someoneElsesEvent));

		assertThatThrownBy(() -> service().create(jwt, accountId, member.getId(), new CreateCoachFeedbackRequest("Note", eventId)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void ownerCanLeaveEventAttachedFeedbackForAnyMember() {
		stubOrganisation();
		User owner = userWithId("kc-owner", "Owner", "owner@example.com");
		User member = userWithId("kc-member", "Member", "member@example.com");
		AccountMember ownerMembership = new AccountMember(account, owner, MemberRole.OWNER);
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER);

		when(currentUserResolver.require(jwt)).thenReturn(owner);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, owner.getId())).thenReturn(Optional.of(ownerMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));

		UUID eventId = UUID.randomUUID();
		EventType type = Mockito.mock(EventType.class);
		when(type.getLabel()).thenReturn("Meeting");
		Event memberEvent = new Event(member, account, type, (short) 3, null);
		when(eventRepository.findById(eventId)).thenReturn(Optional.of(memberEvent));
		when(coachFeedbackRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		CoachFeedbackResponse response = service().create(jwt, accountId, member.getId(), new CreateCoachFeedbackRequest("Nice recovery", eventId));

		assertThat(response.eventTypeLabel()).isEqualTo("Meeting");
	}

	@Test
	void memberCanListTheirOwnFeedback() {
		stubOrganisation();
		User member = userWithId("kc-member", "Member", "member@example.com");
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER);

		when(currentUserResolver.require(jwt)).thenReturn(member);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));
		when(coachFeedbackRepository.findByAccount_IdAndMember_IdOrderByCreatedAtDesc(accountId, member.getId())).thenReturn(List.of());

		List<CoachFeedbackResponse> response = service().list(jwt, accountId, member.getId());

		assertThat(response).isEmpty();
	}

	@Test
	void anUnrelatedMemberCannotListSomeoneElsesFeedback() {
		stubOrganisation();
		User member = userWithId("kc-member", "Member", "member@example.com");
		User other = userWithId("kc-other", "Other", "other@example.com");
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER);
		AccountMember otherMembership = new AccountMember(account, other, MemberRole.MEMBER);

		when(currentUserResolver.require(jwt)).thenReturn(member);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, other.getId())).thenReturn(Optional.of(otherMembership));

		assertThatThrownBy(() -> service().list(jwt, accountId, other.getId()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void supervisorCanListAMembersEventsForThePicker() {
		stubOrganisation();
		User coach = userWithId("kc-coach", "Coach", "coach@example.com");
		User member = userWithId("kc-member", "Member", "member@example.com");
		Group group = new Group(account, null, "Backend");
		ReflectionTestUtils.setField(group, "id", UUID.randomUUID());
		AccountMember coachMembership = new AccountMember(account, coach, MemberRole.COACH, null, group);
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(coach);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, coach.getId())).thenReturn(Optional.of(coachMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));
		Event event = new Event(member, account, Mockito.mock(EventType.class), (short) 3, null);
		when(eventRepository.findByAccount_IdAndUser_IdOrderByStartedAtDesc(accountId, member.getId())).thenReturn(List.of(event));

		List<EventResponse> response = service().listMemberEvents(jwt, accountId, member.getId());

		assertThat(response).hasSize(1);
	}

	@Test
	void anUnrelatedMemberCannotListSomeoneElsesEvents() {
		stubOrganisation();
		User member = userWithId("kc-member", "Member", "member@example.com");
		User other = userWithId("kc-other", "Other", "other@example.com");
		AccountMember memberMembership = new AccountMember(account, member, MemberRole.MEMBER);
		AccountMember otherMembership = new AccountMember(account, other, MemberRole.MEMBER);

		when(currentUserResolver.require(jwt)).thenReturn(member);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, member.getId())).thenReturn(Optional.of(memberMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, other.getId())).thenReturn(Optional.of(otherMembership));

		assertThatThrownBy(() -> service().listMemberEvents(jwt, accountId, other.getId()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void nonOrganisationAccountIsRejected() {
		Account personal = new Account(AccountType.PERSONAL, "Anders Johansson");
		when(accountRepository.findById(accountId)).thenReturn(Optional.of(personal));
		User user = userWithId("kc-1", "Anders", "anders@example.com");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		assertThatThrownBy(() -> service().create(jwt, accountId, UUID.randomUUID(), new CreateCoachFeedbackRequest("Note", null)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void unknownMemberIsA404NotADenial() {
		stubOrganisation();
		User owner = userWithId("kc-owner", "Owner", "owner@example.com");
		AccountMember ownerMembership = new AccountMember(account, owner, MemberRole.OWNER);
		UUID unknownMemberId = UUID.randomUUID();

		when(currentUserResolver.require(jwt)).thenReturn(owner);
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, owner.getId())).thenReturn(Optional.of(ownerMembership));
		when(accountMemberRepository.findByAccount_IdAndUser_Id(accountId, unknownMemberId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().create(jwt, accountId, unknownMemberId, new CreateCoachFeedbackRequest("Note", null)))
			.isInstanceOf(ResourceNotFoundException.class);
	}

}
