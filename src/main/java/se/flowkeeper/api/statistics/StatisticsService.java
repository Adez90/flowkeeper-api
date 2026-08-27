package se.flowkeeper.api.statistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.organisation.Department;
import se.flowkeeper.api.organisation.DepartmentRepository;
import se.flowkeeper.api.organisation.Group;
import se.flowkeeper.api.organisation.GroupRepository;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class StatisticsService {

	private static final Logger log = LoggerFactory.getLogger(StatisticsService.class);

	// "More than 3" per the confirmed design — an aggregate built from fewer
	// people than this risks effectively exposing one individual's number.
	// A simple named constant deliberately, not configurable yet: revisit if
	// a real organisation's size ever calls for it.
	private static final int MIN_MEMBERS_FOR_AGGREGATE = 4;

	// Anonymous by-type breakdown needs a much bigger crowd than a single
	// rolled-up Flow % does: splitting activity by type re-introduces the
	// same re-identification risk a small aggregate has, just one dimension
	// over. Starting point per the confirmed design ("let's start with 10");
	// flagged there as something to revisit once a larger real organisation
	// actually exercises it.
	private static final int MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS = 10;

	private final EventStatisticsRepository eventStatisticsRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final DepartmentRepository departmentRepository;
	private final GroupRepository groupRepository;
	private final CurrentUserResolver currentUserResolver;

	public StatisticsService(EventStatisticsRepository eventStatisticsRepository,
			AccountMemberRepository accountMemberRepository,
			DepartmentRepository departmentRepository,
			GroupRepository groupRepository,
			CurrentUserResolver currentUserResolver) {
		this.eventStatisticsRepository = eventStatisticsRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.departmentRepository = departmentRepository;
		this.groupRepository = groupRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional(readOnly = true)
	public PersonalStatisticsResponse personalStatistics(Jwt jwt, UUID accountId, StatisticsPeriod period, LocalDate referenceDate) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);

		ZoneId zone = resolveZone(user);
		LocalDate date = referenceDate != null ? referenceDate : LocalDate.now(zone);
		LocalDate rangeStart = period.startOf(date);
		LocalDate rangeEnd = period.endOf(date);
		Instant start = rangeStart.atStartOfDay(zone).toInstant();
		Instant end = rangeEnd.atStartOfDay(zone).toInstant();

		OverallCounts overall = eventStatisticsRepository.aggregateOverall(accountId, start, end);
		List<TypeBreakdown> byType = eventStatisticsRepository.aggregateByType(accountId, start, end).stream()
			.map(TypeCounts::toBreakdown)
			.toList();

		long total = overall.total() != null ? overall.total() : 0L;
		long completed = overall.completed() != null ? overall.completed() : 0L;
		long inFlow = overall.inFlow() != null ? overall.inFlow() : 0L;
		double flowPercentage = completed != 0 ? (inFlow * 100.0 / completed) : 0.0;

		log.debug("Computed {} statistics for account {} over [{}, {}) in {}: {} event(s), {} completed, {}% in flow",
			period, accountId, rangeStart, rangeEnd, zone, total, completed, flowPercentage);

		return new PersonalStatisticsResponse(
			period, rangeStart, rangeEnd,
			total, completed, total - completed,
			overall.averageIngoingEnergy(), overall.averageEnergyDelta(), flowPercentage,
			byType);
	}

	/**
	 * Visible to: that group's own manager (a COACH scoped to it, always —
	 * their supervisory view), any ADMIN who supervises it (department-scoped
	 * to the group's department, or an org-wide ADMIN, or the OWNER), or a
	 * fellow COACH in the same department if the group has opted into
	 * peer-sharing. Gated by MIN_MEMBERS_FOR_AGGREGATE regardless of which of
	 * those applies — the privacy concern is the same either way.
	 */
	@Transactional(readOnly = true)
	public AggregateStatisticsResponse groupStatistics(
			Jwt jwt, UUID accountId, UUID groupId, StatisticsPeriod period, LocalDate referenceDate) {
		User viewer = currentUserResolver.require(jwt);
		AccountMember viewerMembership = requireMembership(accountId, viewer);

		Group group = groupRepository.findByIdAndAccount_Id(groupId, accountId)
			.orElseThrow(() -> new ResourceNotFoundException("No such group: " + groupId));
		UUID groupDepartmentId = group.getDepartment() != null ? group.getDepartment().getId() : null;

		boolean isOwnCoach = viewerMembership.getRole() == MemberRole.COACH
			&& viewerMembership.getGroup() != null && groupId.equals(viewerMembership.getGroup().getId());
		boolean isSupervisor = isOrgWideSupervisor(viewerMembership) || isDepartmentAdminOf(viewerMembership, groupDepartmentId);
		boolean isPeerWithConsent = group.isShareFlowWithPeers()
			&& isPeerCoachInSameDepartment(viewerMembership, groupId, groupDepartmentId);

		if (!isOwnCoach && !isSupervisor && !isPeerWithConsent) {
			throw new AccessDeniedException("User %s cannot view group %s's statistics".formatted(viewer.getId(), groupId));
		}

		// No .distinct() needed: account_members has a unique(account_id,
		// user_id) constraint, so a user can hold at most one membership row
		// per account — never two rows in the same group to collapse.
		List<UUID> memberUserIds = accountMemberRepository.findByGroup_Id(groupId).stream()
			.map(member -> member.getUser().getId())
			.toList();

		return buildAggregateResponse(viewer, accountId, memberUserIds, period, referenceDate);
	}

	/**
	 * Visible to: that department's own manager (an ADMIN scoped to it,
	 * always), the OWNER (always), or a fellow ADMIN elsewhere in the org if
	 * the department has opted into peer-sharing. "Under" this department
	 * means scoped to it directly, or scoped to one of its groups.
	 */
	@Transactional(readOnly = true)
	public AggregateStatisticsResponse departmentStatistics(
			Jwt jwt, UUID accountId, UUID departmentId, StatisticsPeriod period, LocalDate referenceDate) {
		User viewer = currentUserResolver.require(jwt);
		AccountMember viewerMembership = requireMembership(accountId, viewer);

		Department department = departmentRepository.findByIdAndAccount_Id(departmentId, accountId)
			.orElseThrow(() -> new ResourceNotFoundException("No such department: " + departmentId));

		boolean isOwnAdmin = isDepartmentAdminOf(viewerMembership, departmentId);
		boolean isSupervisor = viewerMembership.getRole() == MemberRole.OWNER;
		boolean isPeerWithConsent = department.isShareFlowWithPeers()
			&& viewerMembership.getRole() == MemberRole.ADMIN
			&& viewerMembership.getDepartment() != null
			&& !departmentId.equals(viewerMembership.getDepartment().getId());

		if (!isOwnAdmin && !isSupervisor && !isPeerWithConsent) {
			throw new AccessDeniedException(
				"User %s cannot view department %s's statistics".formatted(viewer.getId(), departmentId));
		}

		// Same reasoning as the group case: one row per user per account, so
		// no dedup needed even across the two isUnderDepartment paths — a
		// single row is either directly department-scoped or group-scoped,
		// never matched twice.
		List<UUID> memberUserIds = accountMemberRepository.findByAccount_Id(accountId).stream()
			.filter(member -> isUnderDepartment(member, departmentId))
			.map(member -> member.getUser().getId())
			.toList();

		return buildAggregateResponse(viewer, accountId, memberUserIds, period, referenceDate);
	}

	/** Only the organisation's OWNER — nothing sits above them in the ladder to peer-share with. */
	@Transactional(readOnly = true)
	public AggregateStatisticsResponse organisationStatistics(Jwt jwt, UUID accountId, StatisticsPeriod period, LocalDate referenceDate) {
		User viewer = currentUserResolver.require(jwt);
		AccountMember viewerMembership = requireMembership(accountId, viewer);

		if (viewerMembership.getRole() != MemberRole.OWNER) {
			throw new AccessDeniedException(
				"User %s is not the OWNER of account %s".formatted(viewer.getId(), accountId));
		}

		List<UUID> memberUserIds = accountMemberRepository.findByAccount_Id(accountId).stream()
			.map(member -> member.getUser().getId())
			.toList();

		return buildAggregateResponse(viewer, accountId, memberUserIds, period, referenceDate);
	}

	/**
	 * The organisation OWNER's anonymous, by-event-type view across everyone
	 * in the organisation — never one member's numbers on their own. Gated at
	 * MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS, a higher bar than the plain
	 * aggregate rollups: a type only one or two people ever log can still
	 * point back to them even in an account otherwise big enough to be safe.
	 */
	@Transactional(readOnly = true)
	public OrganisationTypeStatisticsResponse organisationTypeStatistics(
			Jwt jwt, UUID accountId, StatisticsPeriod period, LocalDate referenceDate) {
		User viewer = currentUserResolver.require(jwt);
		AccountMember viewerMembership = requireMembership(accountId, viewer);

		if (viewerMembership.getRole() != MemberRole.OWNER) {
			throw new AccessDeniedException(
				"User %s is not the OWNER of account %s".formatted(viewer.getId(), accountId));
		}

		List<UUID> memberUserIds = accountMemberRepository.findByAccount_Id(accountId).stream()
			.map(member -> member.getUser().getId())
			.toList();

		ZoneId zone = resolveZone(viewer);
		LocalDate date = referenceDate != null ? referenceDate : LocalDate.now(zone);
		LocalDate rangeStart = period.startOf(date);
		LocalDate rangeEnd = period.endOf(date);

		int memberCount = memberUserIds.size();
		if (memberCount < MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS) {
			log.debug("Anonymous type stats for account {} have only {} member(s), below the minimum of {} — withholding",
				accountId, memberCount, MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS);
			return new OrganisationTypeStatisticsResponse(period, rangeStart, rangeEnd, memberCount, true, List.of());
		}

		Instant start = rangeStart.atStartOfDay(zone).toInstant();
		Instant end = rangeEnd.atStartOfDay(zone).toInstant();
		List<TypeBreakdown> byType = eventStatisticsRepository.aggregateByTypeForUsers(accountId, memberUserIds, start, end)
			.stream()
			.map(TypeCounts::toBreakdown)
			.toList();

		return new OrganisationTypeStatisticsResponse(period, rangeStart, rangeEnd, memberCount, false, byType);
	}

	/**
	 * The organisation OWNER's "what's working, what's not" view: every
	 * event note its own owner has opted in to anonymous sharing
	 * (Event.shareAnonymously), never attributed back to whoever wrote it.
	 * Gated the same as the by-type breakdown: withheld below
	 * MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS members. Not time-boxed by
	 * period — a standing view, not a per-day/week/month rollup.
	 */
	@Transactional(readOnly = true)
	public OrganisationFeedbackResponse organisationFeedback(Jwt jwt, UUID accountId) {
		User viewer = currentUserResolver.require(jwt);
		AccountMember viewerMembership = requireMembership(accountId, viewer);

		if (viewerMembership.getRole() != MemberRole.OWNER) {
			throw new AccessDeniedException(
				"User %s is not the OWNER of account %s".formatted(viewer.getId(), accountId));
		}

		int memberCount = accountMemberRepository.findByAccount_Id(accountId).size();
		if (memberCount < MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS) {
			log.debug("Anonymous feedback for account {} has only {} member(s), below the minimum of {} — withholding",
				accountId, memberCount, MIN_MEMBERS_FOR_ANONYMOUS_TYPE_STATS);
			return new OrganisationFeedbackResponse(memberCount, true, List.of());
		}

		return new OrganisationFeedbackResponse(memberCount, false, eventStatisticsRepository.findAnonymousFeedback(accountId));
	}

	private AggregateStatisticsResponse buildAggregateResponse(
			User viewer, UUID accountId, List<UUID> memberUserIds, StatisticsPeriod period, LocalDate referenceDate) {
		ZoneId zone = resolveZone(viewer);
		LocalDate date = referenceDate != null ? referenceDate : LocalDate.now(zone);
		LocalDate rangeStart = period.startOf(date);
		LocalDate rangeEnd = period.endOf(date);

		int memberCount = memberUserIds.size();
		if (memberCount < MIN_MEMBERS_FOR_AGGREGATE) {
			log.debug("Aggregate for account {} has only {} member(s), below the minimum of {} — withholding numbers",
				accountId, memberCount, MIN_MEMBERS_FOR_AGGREGATE);
			return new AggregateStatisticsResponse(period, rangeStart, rangeEnd, memberCount, true, null, null, null, null);
		}

		Instant start = rangeStart.atStartOfDay(zone).toInstant();
		Instant end = rangeEnd.atStartOfDay(zone).toInstant();
		OverallCounts overall = eventStatisticsRepository.aggregateOverallForUsers(accountId, memberUserIds, start, end);

		long total = overall.total() != null ? overall.total() : 0L;
		long completed = overall.completed() != null ? overall.completed() : 0L;
		long inFlow = overall.inFlow() != null ? overall.inFlow() : 0L;
		double flowPercentage = completed != 0 ? (inFlow * 100.0 / completed) : 0.0;

		return new AggregateStatisticsResponse(
			period, rangeStart, rangeEnd, memberCount, false, total, completed, flowPercentage, overall.averageEnergyDelta());
	}

	private AccountMember requireMembership(UUID accountId, User user) {
		return accountMemberRepository.findByAccount_IdAndUser(accountId, user)
			.orElseThrow(() -> new AccessDeniedException(
				"User %s is not a member of account %s".formatted(user.getId(), accountId)));
	}

	/** OWNER, or an ADMIN with no department scope of their own — either way, they supervise the whole org's structure. */
	private boolean isOrgWideSupervisor(AccountMember membership) {
		return membership.getRole() == MemberRole.OWNER
			|| (membership.getRole() == MemberRole.ADMIN && membership.getDepartment() == null);
	}

	private boolean isDepartmentAdminOf(AccountMember membership, UUID departmentId) {
		return departmentId != null && membership.getRole() == MemberRole.ADMIN
			&& membership.getDepartment() != null && departmentId.equals(membership.getDepartment().getId());
	}

	private boolean isPeerCoachInSameDepartment(AccountMember membership, UUID excludeGroupId, UUID departmentId) {
		if (membership.getRole() != MemberRole.COACH || membership.getGroup() == null) {
			return false;
		}
		if (excludeGroupId.equals(membership.getGroup().getId())) {
			return false; // that's the group's own coach, handled separately
		}
		UUID viewerGroupDepartmentId = membership.getGroup().getDepartment() != null
			? membership.getGroup().getDepartment().getId() : null;
		return Objects.equals(viewerGroupDepartmentId, departmentId);
	}

	private boolean isUnderDepartment(AccountMember member, UUID departmentId) {
		if (member.getDepartment() != null && departmentId.equals(member.getDepartment().getId())) {
			return true;
		}
		return member.getGroup() != null && member.getGroup().getDepartment() != null
			&& departmentId.equals(member.getGroup().getDepartment().getId());
	}

	private ZoneId resolveZone(User user) {
		try {
			return ZoneId.of(user.getTimezone());
		} catch (DateTimeException e) {
			// Shouldn't happen — timezone is validated on write — but a
			// "day" still has to mean something if it somehow does.
			log.warn("User {} has an invalid stored timezone '{}', falling back to UTC", user.getId(), user.getTimezone());
			return ZoneOffset.UTC;
		}
	}

}
