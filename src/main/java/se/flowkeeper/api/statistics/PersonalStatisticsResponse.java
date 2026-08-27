package se.flowkeeper.api.statistics;

import java.time.LocalDate;
import java.util.List;

public record PersonalStatisticsResponse(
	StatisticsPeriod period,
	LocalDate rangeStart,
	LocalDate rangeEndExclusive,
	long totalEvents,
	long completedEvents,
	long openEvents,
	Double averageIngoingEnergy,
	/** Outgoing minus ingoing, averaged over completed events — the "did this net energize or drain you" signal. */
	Double averageEnergyDelta,
	/** Share of completed events with ingoing+outgoing energy summing to 4-6 ("in flow") — 0 if none completed. */
	double flowPercentage,
	List<TypeBreakdown> byType
) {
}
