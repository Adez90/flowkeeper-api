package se.flowkeeper.api.integrations;

import java.time.Instant;

/** One activity/event a provider reports for the requested day, not yet brought into FlowKeeper. */
public record ImportableItem(String externalId, String title, Instant startedAt, Instant endedAt) {
}
