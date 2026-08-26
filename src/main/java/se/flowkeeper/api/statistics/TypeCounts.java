package se.flowkeeper.api.statistics;

import java.util.UUID;

/** JPQL constructor-expression target for the group-by-type query. */
record TypeCounts(UUID eventTypeId, String label, Long count, Double averageEnergyDelta) {

	TypeBreakdown toBreakdown() {
		return new TypeBreakdown(eventTypeId, label, count, averageEnergyDelta);
	}

}
