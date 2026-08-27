package se.flowkeeper.api.statistics;

import java.time.LocalDate;
import java.util.List;

/**
 * The organisation-wide "what's working, what's not" view: event counts and
 * average energy delta grouped by type, across everyone in the organisation
 * — never one person's numbers on their own. Gated at a much higher minimum
 * headcount than the group/department/organisation aggregates (see
 * StatisticsService#organisationTypeStatistics): a lopsided type breakdown
 * can narrow down who logged what even in an account that's otherwise big
 * enough for a plain Flow % rollup to be safe.
 */
public record OrganisationTypeStatisticsResponse(
	StatisticsPeriod period,
	LocalDate rangeStart,
	LocalDate rangeEndExclusive,
	int memberCount,
	boolean belowMinimumSize,
	List<TypeBreakdown> byType
) {
}
