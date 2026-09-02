package se.flowkeeper.api.statistics;

import java.util.UUID;

/**
 * JPQL constructor-expression target for the per-member group-by-user query.
 * Must be public — same reason as OverallCounts/TypeCounts.
 */
public record MemberFlowRow(UUID userId, Long completed, Long inFlow) {
}
