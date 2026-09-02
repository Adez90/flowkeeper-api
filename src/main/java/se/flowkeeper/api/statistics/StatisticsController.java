package se.flowkeeper.api.statistics;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
public class StatisticsController {

	private final StatisticsService statisticsService;

	public StatisticsController(StatisticsService statisticsService) {
		this.statisticsService = statisticsService;
	}

	/**
	 * date just needs to fall anywhere inside the period you want — the
	 * actual range is derived from it. Defaults to today (UTC) if omitted.
	 */
	@GetMapping("/api/v1/statistics/personal")
	public PersonalStatisticsResponse personal(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam StatisticsPeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return statisticsService.personalStatistics(jwt, accountId, period, date);
	}

	/** Day-by-day Flow % trend for the caller's own personal statistics, over an explicit [rangeStart, rangeEndExclusive) range. */
	@GetMapping("/api/v1/statistics/personal/trend")
	public PersonalTrendResponse personalTrend(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeEndExclusive) {
		return statisticsService.personalTrend(jwt, accountId, rangeStart, rangeEndExclusive);
	}

	/** A group's rolled-up Flow % — never one individual's number. See AggregateStatisticsResponse. */
	@GetMapping("/api/v1/statistics/group")
	public AggregateStatisticsResponse group(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam UUID groupId,
			@RequestParam StatisticsPeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return statisticsService.groupStatistics(jwt, accountId, groupId, period, date);
	}

	/** Each opted-in group member's own name and Flow % — only for a viewer who is themselves a member of this exact group. */
	@GetMapping("/api/v1/statistics/group/members")
	public GroupMemberFlowResponse groupMembers(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam UUID groupId,
			@RequestParam StatisticsPeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return statisticsService.groupMemberFlow(jwt, accountId, groupId, period, date);
	}

	/** A department's rolled-up Flow % (every member under it, directly or via one of its groups). */
	@GetMapping("/api/v1/statistics/department")
	public AggregateStatisticsResponse department(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam UUID departmentId,
			@RequestParam StatisticsPeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return statisticsService.departmentStatistics(jwt, accountId, departmentId, period, date);
	}

	/** Same scope and authorization as {@code /api/v1/statistics/group}, but a day-by-day trend over an explicit date range. */
	@GetMapping("/api/v1/statistics/group/trend")
	public AggregateTrendResponse groupTrend(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam UUID groupId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeEndExclusive) {
		return statisticsService.groupTrend(jwt, accountId, groupId, rangeStart, rangeEndExclusive);
	}

	/** Same scope and authorization as {@code /api/v1/statistics/department}, but a day-by-day trend over an explicit date range. */
	@GetMapping("/api/v1/statistics/department/trend")
	public AggregateTrendResponse departmentTrend(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam UUID departmentId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeEndExclusive) {
		return statisticsService.departmentTrend(jwt, accountId, departmentId, rangeStart, rangeEndExclusive);
	}

	/** Same scope and authorization as {@code /api/v1/statistics/organisation}, but a day-by-day trend over an explicit date range. */
	@GetMapping("/api/v1/statistics/organisation/trend")
	public AggregateTrendResponse organisationTrend(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeEndExclusive) {
		return statisticsService.organisationTrend(jwt, accountId, rangeStart, rangeEndExclusive);
	}

	/** The whole organisation's rolled-up Flow % — the OWNER's view only. */
	@GetMapping("/api/v1/statistics/organisation")
	public AggregateStatisticsResponse organisation(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam StatisticsPeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return statisticsService.organisationStatistics(jwt, accountId, period, date);
	}

	/** Anonymous by-event-type breakdown across the whole organisation — the OWNER's view only. See OrganisationTypeStatisticsResponse. */
	@GetMapping("/api/v1/statistics/organisation/by-type")
	public OrganisationTypeStatisticsResponse organisationByType(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam StatisticsPeriod period,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return statisticsService.organisationTypeStatistics(jwt, accountId, period, date);
	}

	/** Anonymous, opted-in event notes across the whole organisation — the OWNER's view only. See OrganisationFeedbackResponse. */
	@GetMapping("/api/v1/statistics/organisation/feedback")
	public OrganisationFeedbackResponse organisationFeedback(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId) {
		return statisticsService.organisationFeedback(jwt, accountId);
	}

}
