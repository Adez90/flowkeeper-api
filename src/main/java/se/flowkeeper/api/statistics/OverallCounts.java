package se.flowkeeper.api.statistics;

/**
 * JPQL constructor-expression target for the ungrouped aggregate query.
 * Must be public — Spring Data's repository proxy lives in a different JPMS
 * module and can't reflectively construct a package-private record (confirmed
 * live: IllegalAccessError from jdk.proxy2 with this record package-private).
 */
public record OverallCounts(
	Long total, Long completed, Long inFlow, Double averageIngoingEnergy, Double averageEnergyDelta
) {
}
