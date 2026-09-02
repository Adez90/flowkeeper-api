package se.flowkeeper.api.statistics;

import java.util.UUID;

/** One opted-in group member's own Flow % — identified by name, unlike the anonymous aggregates. */
public record MemberFlow(UUID userId, String displayName, long completedEvents, double flowPercentage) {
}
