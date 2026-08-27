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

}
