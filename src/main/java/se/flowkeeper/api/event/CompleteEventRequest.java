package se.flowkeeper.api.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CompleteEventRequest(
	@Min(1) @Max(5) short outgoingEnergy,
	String outgoingNote
) {
}
