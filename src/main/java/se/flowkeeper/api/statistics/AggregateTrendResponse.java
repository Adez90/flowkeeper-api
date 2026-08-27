package se.flowkeeper.api.statistics;

import java.time.LocalDate;
import java.util.List;

/**
 * A group/department/organisation's day-by-day rolled-up trend — never one
 * individual's numbers. When memberCount is below the minimum-size-for-
 * privacy threshold, belowMinimumSize is true and points is null: genuinely
 * not computed, not just hidden, matching AggregateStatisticsResponse.
 */
public record AggregateTrendResponse(
	LocalDate rangeStart,
	LocalDate rangeEndExclusive,
	int memberCount,
	boolean belowMinimumSize,
	List<TrendPoint> points
) {
}
