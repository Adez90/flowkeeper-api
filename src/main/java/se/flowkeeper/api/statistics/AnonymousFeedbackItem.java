package se.flowkeeper.api.statistics;

import java.time.Instant;

/**
 * JPQL constructor-expression target for the anonymous-feedback query. Must
 * be public — same reason as OverallCounts and TypeCounts. Deliberately
 * carries no user/event id: nothing here should ever be traceable back to
 * whoever opted the note in.
 */
public record AnonymousFeedbackItem(String eventTypeLabel, String ingoingNote, String outgoingNote, Instant startedAt) {
}
