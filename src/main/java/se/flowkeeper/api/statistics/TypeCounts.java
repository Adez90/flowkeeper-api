package se.flowkeeper.api.statistics;

import java.util.UUID;

/**
 * JPQL constructor-expression target for the group-by-type query. Must be
 * public — same reason as OverallCounts.
 */
public record TypeCounts(UUID eventTypeId, String label, Long count, Double averageEnergyDelta) {

	TypeBreakdown toBreakdown() {
		return new TypeBreakdown(eventTypeId, label, count, averageEnergyDelta);
	}

}
