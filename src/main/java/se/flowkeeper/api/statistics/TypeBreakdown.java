package se.flowkeeper.api.statistics;

import java.util.UUID;

public record TypeBreakdown(UUID eventTypeId, String label, long count, Double averageEnergyDelta) {
}
