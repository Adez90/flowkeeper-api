package se.flowkeeper.api.statistics;

import se.flowkeeper.api.event.EventStatus;

import java.time.Instant;

/**
 * JPQL constructor-expression target for the trend queries — the minimal
 * per-event fields needed to bucket by local day and compute Flow % in
 * Java, one query for the whole range rather than one per day. Must be
 * public — same reason as OverallCounts.
 */
public record TrendRow(Instant startedAt, EventStatus status, short ingoingEnergy, Short outgoingEnergy) {
}
