package se.flowkeeper.api.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;

/** completedAt is optional — omit it to finish the activity now, same as before. Set it to record when the activity actually ended (e.g. an imported event's known end time). */
public record CompleteEventRequest(
	@Min(1) @Max(5) short outgoingEnergy,
	String outgoingNote,
	Instant completedAt
) {
}
