package se.flowkeeper.api.statistics;

import java.time.LocalDate;

/** One day's counts within a trend — every day in the requested range gets a point, even one with zero events. */
public record TrendPoint(
	LocalDate date,
	long totalEvents,
	long completedEvents,
	/** Share of completed events "in flow" (ingoing+outgoing energy summing to 4-6) — 0 if none completed that day. */
	double flowPercentage
) {
}
