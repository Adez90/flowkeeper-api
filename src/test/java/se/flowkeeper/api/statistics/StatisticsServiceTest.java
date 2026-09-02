package se.flowkeeper.api.statistics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.billing.PlatformAdmins;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.event.EventStatus;
import se.flowkeeper.api.organisation.Department;
import se.flowkeeper.api.organisation.DepartmentRepository;
import se.flowkeeper.api.organisation.Group;
import se.flowkeeper.api.organisation.GroupRepository;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserTimezones;

import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

	@Mock EventStatisticsRepository eventStatisticsRepository;
	@Mock AccountMemberRepository accountMemberRepository;
	@Mock DepartmentRepository departmentRepository;
	@Mock GroupRepository groupRepository;
	@Mock CurrentUserResolver currentUserResolver;
	@Mock PlatformAdmins platformAdmins;

	private final User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
	private final Account account = new Account(AccountType.PERSONAL, "Anders Johansson");
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private StatisticsService service() {
		return new StatisticsService(
			eventStatisticsRepository, accountMemberRepository, departmentRepository, groupRepository, currentUserResolver,
			new UserTimezones(), platformAdmins);
	}

	@Test
	void computesRangeAndMapsAggregatesForAMember() {
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventStatisticsRepository.aggregateOverall(any(), any(), any(), any()))
			.thenReturn(new OverallCounts(5L, 3L, 2L, 3.4, -0.5));
		when(eventStatisticsRepository.aggregateByType(any(), any(), any(), any()))
			.thenReturn(List.of(new TypeCounts(UUID.randomUUID(), "Meeting", 2L, -1.0)));

		PersonalStatisticsResponse response = service().personalStatistics(
			jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.rangeStart()).isEqualTo(LocalDate.of(2026, 3, 12));
		assertThat(response.rangeEndExclusive()).isEqualTo(LocalDate.of(2026, 3, 13));
		assertThat(response.totalEvents()).isEqualTo(5);
		assertThat(response.completedEvents()).isEqualTo(3);
		assertThat(response.openEvents()).isEqualTo(2);
		assertThat(response.averageEnergyDelta()).isEqualTo(-0.5);
		// 2 of 3 completed events "in flow" -> 66.67%
		assertThat(response.flowPercentage()).isCloseTo(66.6667, org.assertj.core.data.Offset.offset(0.01));
		assertThat(response.byType()).hasSize(1);
		assertThat(response.byType().get(0).label()).isEqualTo("Meeting");
	}

	@Test
	void personalStatisticsIsScopedToTheCallersOwnUserIdNotJustTheAccount() {
		// An organisation account can hold many members' events — personal
		// statistics must only ever be the caller's own, never leak the rest
		// of the account's data in under "my" numbers.
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.MEMBER)));
		when(eventStatisticsRepository.aggregateOverall(any(), any(), any(), any()))
			.thenReturn(new OverallCounts(0L, 0L, 0L, null, null));
		when(eventStatisticsRepository.aggregateByType(any(), any(), any(), any())).thenReturn(List.of());

		service().personalStatistics(jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		verify(eventStatisticsRepository).aggregateOverall(eq(accountId), eq(user.getId()), any(), any());
		verify(eventStatisticsRepository).aggregateByType(eq(accountId), eq(user.getId()), any(), any());
	}

	@Test
	void zeroEventsInRangeReturnsZeroesNotNulls() {
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventStatisticsRepository.aggregateOverall(any(), any(), any(), any()))
			.thenReturn(new OverallCounts(0L, 0L, 0L, null, null));
		when(eventStatisticsRepository.aggregateByType(any(), any(), any(), any()))
			.thenReturn(List.of());

		PersonalStatisticsResponse response = service().personalStatistics(
			jwt, accountId, StatisticsPeriod.MONTH, LocalDate.of(2026, 2, 18));

		assertThat(response.totalEvents()).isZero();
		assertThat(response.completedEvents()).isZero();
		assertThat(response.openEvents()).isZero();
		assertThat(response.averageIngoingEnergy()).isNull();
		assertThat(response.flowPercentage()).isZero();
		assertThat(response.byType()).isEmpty();
	}

	@Test
	void usesTheUsersOwnTimezoneToComputeTheRangeNotUtc() {
		User stockholmUser = new User("kc-subject-2", "Someone", "someone@example.com");
		stockholmUser.updateProfile("Someone", "Europe/Stockholm", null, null);
		UUID accountId = UUID.randomUUID();

		when(currentUserResolver.require(jwt)).thenReturn(stockholmUser);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, stockholmUser, MemberRole.OWNER)));
		when(eventStatisticsRepository.aggregateOverall(any(), any(), any(), any()))
			.thenReturn(new OverallCounts(0L, 0L, 0L, null, null));
		when(eventStatisticsRepository.aggregateByType(any(), any(), any(), any())).thenReturn(List.of());

		// Stockholm is UTC+2 in June (daylight saving) — chosen so this
		// test actually fails if the code ever reverts to hardcoded UTC.
		service().personalStatistics(jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 6, 15));

		ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(eventStatisticsRepository).aggregateOverall(any(), any(), startCaptor.capture(), any());
		Instant expectedStart = LocalDate.of(2026, 6, 15).atStartOfDay(ZoneId.of("Europe/Stockholm")).toInstant();
		assertThat(startCaptor.getValue()).isEqualTo(expectedStart);
	}

	@Test
	void groupStatisticsBelowMinimumSizeWithholdNumbers() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		AccountMember coachMembership = new AccountMember(account, user, MemberRole.COACH, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(coachMembership));
		when(groupRepository.findByIdAndAccount_Id(groupId, accountId)).thenReturn(Optional.of(group));
		// Only 2 members — below MIN_MEMBERS_FOR_AGGREGATE (4).
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(
			new AccountMember(account, user, MemberRole.COACH, null, group),
			new AccountMember(account, new User("kc-x", "X", "x@example.com"), MemberRole.MEMBER, null, group)));

		AggregateStatisticsResponse response = service().groupStatistics(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.belowMinimumSize()).isTrue();
		assertThat(response.memberCount()).isEqualTo(2);
		assertThat(response.flowPercentage()).isNull();
		assertThat(response.totalEvents()).isNull();
	}

	@Test
	void groupStatisticsShowsRealNumbersBelowMinimumSizeForAPlatformAdmin() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		AccountMember coachMembership = new AccountMember(account, user, MemberRole.COACH, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(coachMembership));
		when(groupRepository.findByIdAndAccount_Id(groupId, accountId)).thenReturn(Optional.of(group));
		when(platformAdmins.isAdmin(user)).thenReturn(true);
		// Same 2-member group as the withheld case above — below MIN_MEMBERS_FOR_AGGREGATE (4).
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(
			new AccountMember(account, user, MemberRole.COACH, null, group),
			new AccountMember(account, new User("kc-x", "X", "x@example.com"), MemberRole.MEMBER, null, group)));
		when(eventStatisticsRepository.aggregateOverallForUsers(any(), any(), any(), any()))
			.thenReturn(new OverallCounts(4L, 2L, 1L, 3.0, 0.5));

		AggregateStatisticsResponse response = service().groupStatistics(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.belowMinimumSize()).isFalse();
		assertThat(response.memberCount()).isEqualTo(2);
		assertThat(response.totalEvents()).isEqualTo(4);
		assertThat(response.flowPercentage()).isEqualTo(50.0); // 1 of 2 completed in flow
	}

	@Test
	void groupStatisticsVisibleToOwnCoachAboveMinimumSize() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		AccountMember coachMembership = new AccountMember(account, user, MemberRole.COACH, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(coachMembership));
		when(groupRepository.findByIdAndAccount_Id(groupId, accountId)).thenReturn(Optional.of(group));
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(
			new AccountMember(account, new User("kc-1", "A", "a@example.com"), MemberRole.MEMBER, null, group),
			new AccountMember(account, new User("kc-2", "B", "b@example.com"), MemberRole.MEMBER, null, group),
			new AccountMember(account, new User("kc-3", "C", "c@example.com"), MemberRole.MEMBER, null, group),
			new AccountMember(account, new User("kc-4", "D", "d@example.com"), MemberRole.MEMBER, null, group)));
		when(eventStatisticsRepository.aggregateOverallForUsers(any(), any(), any(), any()))
			.thenReturn(new OverallCounts(10L, 8L, 6L, 3.0, 0.5));

		AggregateStatisticsResponse response = service().groupStatistics(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.belowMinimumSize()).isFalse();
		assertThat(response.memberCount()).isEqualTo(4);
		assertThat(response.flowPercentage()).isEqualTo(75.0); // 6 of 8 completed in flow
	}

	@Test
	void groupStatisticsDeniedToAnUnrelatedMember() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		AccountMember plainMembership = new AccountMember(account, user, MemberRole.MEMBER);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(plainMembership));
		when(groupRepository.findByIdAndAccount_Id(groupId, accountId)).thenReturn(Optional.of(group));

		assertThatThrownBy(() -> service().groupStatistics(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void organisationStatisticsOnlyVisibleToOwner() {
		UUID accountId = UUID.randomUUID();
		Department department = new Department(account, "Engineering");
		AccountMember adminMembership = new AccountMember(account, user, MemberRole.ADMIN, department, null);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(adminMembership));

		assertThatThrownBy(() -> service().organisationStatistics(jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void organisationTypeStatisticsWithheldBelowTenMembers() {
		UUID accountId = UUID.randomUUID();
		AccountMember ownerMembership = new AccountMember(account, user, MemberRole.OWNER);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(ownerMembership));
		// Only 3 members total — below MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS (10).
		when(accountMemberRepository.findByAccount_Id(accountId)).thenReturn(List.of(
			new AccountMember(account, user, MemberRole.OWNER),
			new AccountMember(account, new User("kc-a", "A", "a@example.com"), MemberRole.MEMBER),
			new AccountMember(account, new User("kc-b", "B", "b@example.com"), MemberRole.MEMBER)));

		OrganisationTypeStatisticsResponse response = service().organisationTypeStatistics(
			jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.belowMinimumSize()).isTrue();
		assertThat(response.memberCount()).isEqualTo(3);
		assertThat(response.byType()).isEmpty();
	}

	@Test
	void organisationTypeStatisticsShowsRealDataBelowTenMembersForAPlatformAdmin() {
		UUID accountId = UUID.randomUUID();
		AccountMember ownerMembership = new AccountMember(account, user, MemberRole.OWNER);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(ownerMembership));
		when(platformAdmins.isAdmin(user)).thenReturn(true);
		when(accountMemberRepository.findByAccount_Id(accountId)).thenReturn(List.of(
			new AccountMember(account, user, MemberRole.OWNER),
			new AccountMember(account, new User("kc-a", "A", "a@example.com"), MemberRole.MEMBER)));
		when(eventStatisticsRepository.aggregateByTypeForUsers(any(), any(), any(), any()))
			.thenReturn(List.of(new TypeCounts(UUID.randomUUID(), "Meeting", 2L, -1.0)));

		OrganisationTypeStatisticsResponse response = service().organisationTypeStatistics(
			jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.belowMinimumSize()).isFalse();
		assertThat(response.memberCount()).isEqualTo(2);
		assertThat(response.byType()).hasSize(1);
	}

	@Test
	void organisationTypeStatisticsOnlyVisibleToOwner() {
		UUID accountId = UUID.randomUUID();
		Department department = new Department(account, "Engineering");
		AccountMember adminMembership = new AccountMember(account, user, MemberRole.ADMIN, department, null);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(adminMembership));

		assertThatThrownBy(() -> service().organisationTypeStatistics(jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void organisationFeedbackWithheldBelowTenMembers() {
		UUID accountId = UUID.randomUUID();
		AccountMember ownerMembership = new AccountMember(account, user, MemberRole.OWNER);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(ownerMembership));
		when(accountMemberRepository.findByAccount_Id(accountId)).thenReturn(List.of(ownerMembership));

		OrganisationFeedbackResponse response = service().organisationFeedback(jwt, accountId);

		assertThat(response.belowMinimumSize()).isTrue();
		assertThat(response.memberCount()).isEqualTo(1);
		assertThat(response.items()).isEmpty();
	}

	@Test
	void organisationFeedbackShowsRealNotesBelowTenMembersForAPlatformAdmin() {
		UUID accountId = UUID.randomUUID();
		AccountMember ownerMembership = new AccountMember(account, user, MemberRole.OWNER);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(ownerMembership));
		when(platformAdmins.isAdmin(user)).thenReturn(true);
		when(accountMemberRepository.findByAccount_Id(accountId)).thenReturn(List.of(ownerMembership));
		when(eventStatisticsRepository.findAnonymousFeedback(accountId)).thenReturn(List.of(
			new AnonymousFeedbackItem("Meeting", "felt rushed", "still rushed", Instant.now())));

		OrganisationFeedbackResponse response = service().organisationFeedback(jwt, accountId);

		assertThat(response.belowMinimumSize()).isFalse();
		assertThat(response.memberCount()).isEqualTo(1);
		assertThat(response.items()).hasSize(1);
	}

	@Test
	void organisationFeedbackReturnsOptedInNotesOnceAboveTenMembers() {
		UUID accountId = UUID.randomUUID();
		AccountMember ownerMembership = new AccountMember(account, user, MemberRole.OWNER);
		List<AccountMember> tenMembers = java.util.stream.Stream.concat(
			java.util.stream.Stream.of(ownerMembership),
			java.util.stream.IntStream.range(0, 9)
				.mapToObj(i -> new AccountMember(account, new User("kc-" + i, "U" + i, "u" + i + "@example.com"), MemberRole.MEMBER)))
			.toList();

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(ownerMembership));
		when(accountMemberRepository.findByAccount_Id(accountId)).thenReturn(tenMembers);
		when(eventStatisticsRepository.findAnonymousFeedback(accountId)).thenReturn(List.of(
			new AnonymousFeedbackItem("Meeting", "felt rushed", "still rushed", Instant.now())));

		OrganisationFeedbackResponse response = service().organisationFeedback(jwt, accountId);

		assertThat(response.belowMinimumSize()).isFalse();
		assertThat(response.memberCount()).isEqualTo(10);
		assertThat(response.items()).hasSize(1);
		assertThat(response.items().get(0).eventTypeLabel()).isEqualTo("Meeting");
	}

	@Test
	void organisationFeedbackOnlyVisibleToOwner() {
		UUID accountId = UUID.randomUUID();
		Department department = new Department(account, "Engineering");
		AccountMember adminMembership = new AccountMember(account, user, MemberRole.ADMIN, department, null);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(adminMembership));

		assertThatThrownBy(() -> service().organisationFeedback(jwt, accountId))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void personalTrendBucketsEventsByLocalDayAndComputesFlowPercentagePerDay() {
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));

		// Day 1 (Mar 12): one completed in-flow (3+3=6), one completed not in-flow (1+1=2).
		// Day 2 (Mar 13): nothing.
		// Day 3 (Mar 14): one still-open event (no outgoingEnergy).
		when(eventStatisticsRepository.findTrendRows(any(), any(), any(), any())).thenReturn(List.of(
			new TrendRow(LocalDate.of(2026, 3, 12).atStartOfDay(ZoneId.of("UTC")).plusHours(9).toInstant(),
				EventStatus.COMPLETED, (short) 3, (short) 3),
			new TrendRow(LocalDate.of(2026, 3, 12).atStartOfDay(ZoneId.of("UTC")).plusHours(15).toInstant(),
				EventStatus.COMPLETED, (short) 1, (short) 1),
			new TrendRow(LocalDate.of(2026, 3, 14).atStartOfDay(ZoneId.of("UTC")).plusHours(8).toInstant(),
				EventStatus.OPEN, (short) 2, null)));

		PersonalTrendResponse response = service().personalTrend(
			jwt, accountId, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 15));

		verify(eventStatisticsRepository).findTrendRows(eq(accountId), eq(user.getId()), any(), any());
		assertThat(response.rangeStart()).isEqualTo(LocalDate.of(2026, 3, 12));
		assertThat(response.rangeEndExclusive()).isEqualTo(LocalDate.of(2026, 3, 15));
		assertThat(response.points()).hasSize(3);

		TrendPoint day1 = response.points().get(0);
		assertThat(day1.date()).isEqualTo(LocalDate.of(2026, 3, 12));
		assertThat(day1.totalEvents()).isEqualTo(2);
		assertThat(day1.completedEvents()).isEqualTo(2);
		assertThat(day1.flowPercentage()).isEqualTo(50.0); // 1 of 2 completed in flow

		TrendPoint day2 = response.points().get(1);
		assertThat(day2.date()).isEqualTo(LocalDate.of(2026, 3, 13));
		assertThat(day2.totalEvents()).isZero();
		assertThat(day2.flowPercentage()).isZero();

		TrendPoint day3 = response.points().get(2);
		assertThat(day3.date()).isEqualTo(LocalDate.of(2026, 3, 14));
		assertThat(day3.totalEvents()).isEqualTo(1);
		assertThat(day3.completedEvents()).isZero();
		assertThat(day3.flowPercentage()).isZero();
	}

	@Test
	void personalTrendRejectsAnEndDateThatIsNotAfterStart() {
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));

		assertThatThrownBy(() -> service().personalTrend(jwt, accountId, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 12)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void personalTrendRejectsARangeLongerThanTheMaximum() {
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));

		assertThatThrownBy(() -> service().personalTrend(jwt, accountId, LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void groupTrendBelowMinimumSizeWithholdsPoints() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		AccountMember coachMembership = new AccountMember(account, user, MemberRole.COACH, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(coachMembership));
		when(groupRepository.findByIdAndAccount_Id(groupId, accountId)).thenReturn(Optional.of(group));
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(
			new AccountMember(account, user, MemberRole.COACH, null, group)));

		AggregateTrendResponse response = service().groupTrend(
			jwt, accountId, groupId, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 15));

		assertThat(response.belowMinimumSize()).isTrue();
		assertThat(response.memberCount()).isEqualTo(1);
		assertThat(response.points()).isNull();
	}

	@Test
	void groupTrendShowsRealPointsBelowMinimumSizeForAPlatformAdmin() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		AccountMember coachMembership = new AccountMember(account, user, MemberRole.COACH, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(coachMembership));
		when(groupRepository.findByIdAndAccount_Id(groupId, accountId)).thenReturn(Optional.of(group));
		when(platformAdmins.isAdmin(user)).thenReturn(true);
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(
			new AccountMember(account, user, MemberRole.COACH, null, group)));
		when(eventStatisticsRepository.findTrendRowsForUsers(any(), any(), any(), any())).thenReturn(List.of());

		AggregateTrendResponse response = service().groupTrend(
			jwt, accountId, groupId, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 15));

		assertThat(response.belowMinimumSize()).isFalse();
		assertThat(response.memberCount()).isEqualTo(1);
		assertThat(response.points()).isNotNull();
	}

	@Test
	void organisationTrendOnlyVisibleToOwner() {
		UUID accountId = UUID.randomUUID();
		Department department = new Department(account, "Engineering");
		AccountMember adminMembership = new AccountMember(account, user, MemberRole.ADMIN, department, null);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(adminMembership));

		assertThatThrownBy(() -> service().organisationTrend(jwt, accountId, LocalDate.of(2026, 3, 12), LocalDate.of(2026, 3, 15)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void rejectsNonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().personalStatistics(jwt, UUID.randomUUID(), StatisticsPeriod.WEEK, null))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void groupMemberFlowReturnsOnlyTheOptedInMembersWithTheirOwnFlowPercentage() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		// The viewer here is a plain MEMBER of the group, not its coach —
		// this view is for teammates, not just managers.
		AccountMember viewerMembership = new AccountMember(account, user, MemberRole.MEMBER, null, group);

		User sharingUser = new User("kc-sharing", "Sharing Person", "sharing@example.com");
		AccountMember sharingMember = new AccountMember(account, sharingUser, MemberRole.MEMBER, null, group);
		sharingMember.updateSharePreference(true);

		User quietUser = new User("kc-quiet", "Quiet Person", "quiet@example.com");
		AccountMember quietMember = new AccountMember(account, quietUser, MemberRole.MEMBER, null, group);
		// quietMember never opts in — shareFlowWithPeers stays false.

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(viewerMembership));
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(viewerMembership, sharingMember, quietMember));
		when(eventStatisticsRepository.aggregateOverallPerUser(eq(accountId), any(), any(), any()))
			.thenReturn(List.of(new MemberFlowRow(sharingUser.getId(), 4L, 3L)));

		GroupMemberFlowResponse response = service().groupMemberFlow(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.members()).hasSize(1);
		MemberFlow member = response.members().get(0);
		assertThat(member.userId()).isEqualTo(sharingUser.getId());
		assertThat(member.displayName()).isEqualTo("Sharing Person");
		assertThat(member.completedEvents()).isEqualTo(4);
		// 3 of 4 completed "in flow" -> 75%.
		assertThat(member.flowPercentage()).isEqualTo(75.0);
	}

	@Test
	void groupMemberFlowReturnsAnOptedInMemberWithZeroEventsRatherThanOmittingThem() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		AccountMember viewerMembership = new AccountMember(account, user, MemberRole.MEMBER, null, group);
		viewerMembership.updateSharePreference(true);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(viewerMembership));
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(viewerMembership));
		// Opted in, but hasn't logged anything in this period.
		when(eventStatisticsRepository.aggregateOverallPerUser(eq(accountId), any(), any(), any())).thenReturn(List.of());

		GroupMemberFlowResponse response = service().groupMemberFlow(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.members()).hasSize(1);
		assertThat(response.members().get(0).completedEvents()).isZero();
		assertThat(response.members().get(0).flowPercentage()).isZero();
	}

	@Test
	void groupMemberFlowReturnsEmptyWhenNobodyHasOptedIn() {
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Group group = new Group(account, null, "Backend team");
		ReflectionTestUtils.setField(group, "id", groupId);
		AccountMember viewerMembership = new AccountMember(account, user, MemberRole.MEMBER, null, group);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(viewerMembership));
		when(accountMemberRepository.findByGroup_Id(groupId)).thenReturn(List.of(viewerMembership));

		GroupMemberFlowResponse response = service().groupMemberFlow(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12));

		assertThat(response.members()).isEmpty();
	}

	@Test
	void groupMemberFlowRejectsAViewerWhoIsNotAMemberOfThisExactGroup() {
		// A department ADMIN (or org OWNER, or a coach of a different group)
		// supervises this group's anonymous rollup but must never reach the
		// named, individual-level peer view — that's the whole point of
		// keeping it scoped to "am I actually in this group".
		UUID accountId = UUID.randomUUID();
		UUID groupId = UUID.randomUUID();
		Department department = new Department(account, "Engineering");
		AccountMember adminMembership = new AccountMember(account, user, MemberRole.ADMIN, department, null);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(accountId, user)).thenReturn(Optional.of(adminMembership));

		assertThatThrownBy(() -> service().groupMemberFlow(jwt, accountId, groupId, StatisticsPeriod.DAY, LocalDate.of(2026, 3, 12)))
			.isInstanceOf(AccessDeniedException.class);
	}

}
