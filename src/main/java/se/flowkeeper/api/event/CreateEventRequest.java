package se.flowkeeper.api.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/**
 * startedAt, outgoingEnergy, outgoingNote, and completedAt are all optional
 * — omit them all to log an ongoing activity starting now, same as before.
 * Set startedAt to log something that already happened (must not be in the
 * future); also set outgoingEnergy (and optionally outgoingNote/completedAt)
 * to log a fully-finished historical activity, already completed, in one
 * call — see EventService#createEvent for the validation.
 */
public record CreateEventRequest(
	@NotNull UUID accountId,
	@NotNull UUID eventTypeId,
	@Min(1) @Max(5) short ingoingEnergy,
	String ingoingNote,
	Instant startedAt,
	@Min(1) @Max(5) Short outgoingEnergy,
	String outgoingNote,
	Instant completedAt
) {
}
