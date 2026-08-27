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
import se.flowkeeper.api.organisation.Department;
import se.flowkeeper.api.organisation.DepartmentRepository;
import se.flowkeeper.api.organisation.Group;
import se.flowkeeper.api.organisation.GroupRepository;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

	@Mock EventStatisticsRepository eventStatisticsRepository;
	@Mock AccountMemberRepository accountMemberRepository;
	@Mock DepartmentRepository departmentRepository;
	@Mock GroupRepository groupRepository;
	@Mock CurrentUserResolver currentUserResolver;

	private final User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
	private final Account account = new Account(AccountType.PERSONAL, "Anders Johansson");
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private StatisticsService service() {
		return new StatisticsService(
			eventStatisticsRepository, accountMemberRepository, departmentRepository, groupRepository, currentUserResolver);
	}

	@Test
	void computesRangeAndMapsAggregatesForAMember() {
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventStatisticsRepository.aggregateOverall(any(), any(), any()))
			.thenReturn(new OverallCounts(5L, 3L, 2L, 3.4, -0.5));
		when(eventStatisticsRepository.aggregateByType(any(), any(), any()))
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
	void zeroEventsInRangeReturnsZeroesNotNulls() {
		UUID accountId = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventStatisticsRepository.aggregateOverall(any(), any(), any()))
			.thenReturn(new OverallCounts(0L, 0L, 0L, null, null));
		when(eventStatisticsRepository.aggregateByType(any(), any(), any()))
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
		when(eventStatisticsRepository.aggregateOverall(any(), any(), any()))
			.thenReturn(new OverallCounts(0L, 0L, 0L, null, null));
		when(eventStatisticsRepository.aggregateByType(any(), any(), any())).thenReturn(List.of());

		// Stockholm is UTC+2 in June (daylight saving) — chosen so this
		// test actually fails if the code ever reverts to hardcoded UTC.
		service().personalStatistics(jwt, accountId, StatisticsPeriod.DAY, LocalDate.of(2026, 6, 15));

		ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
		verify(eventStatisticsRepository).aggregateOverall(any(), startCaptor.capture(), any());
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
	void rejectsNonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().personalStatistics(jwt, UUID.randomUUID(), StatisticsPeriod.WEEK, null))
			.isInstanceOf(AccessDeniedException.class);
	}

}
