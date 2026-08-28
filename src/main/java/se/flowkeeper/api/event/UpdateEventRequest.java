package se.flowkeeper.api.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** Full correction of an already-completed event — every field is required, since this replaces the whole record rather than patching part of it. */
public record UpdateEventRequest(
	@NotNull UUID eventTypeId,
	@Min(1) @Max(5) short ingoingEnergy,
	String ingoingNote,
	@NotNull Instant startedAt,
	@Min(1) @Max(5) short outgoingEnergy,
	String outgoingNote,
	@NotNull Instant completedAt
) {
}
