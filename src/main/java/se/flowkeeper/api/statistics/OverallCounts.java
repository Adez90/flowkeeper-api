package se.flowkeeper.api.statistics;

/** JPQL constructor-expression target for the ungrouped aggregate query. */
record OverallCounts(Long total, Long completed, Double averageIngoingEnergy, Double averageEnergyDelta) {
}
