package se.flowkeeper.api.statistics;

import java.time.LocalDate;

/**
 * A group/department/organisation's rolled-up Flow % — never one
 * individual's number. When memberCount is below the minimum-size-for-
 * privacy threshold, belowMinimumSize is true and every numeric field is
 * null: genuinely not computed, not just hidden, so a client that ignores
 * the flag still can't see a number built from too few people.
 */
public record AggregateStatisticsResponse(
	StatisticsPeriod period,
	LocalDate rangeStart,
	LocalDate rangeEndExclusive,
	int memberCount,
	boolean belowMinimumSize,
	Long totalEvents,
	Long completedEvents,
	Double flowPercentage,
	Double averageEnergyDelta
) {
}
