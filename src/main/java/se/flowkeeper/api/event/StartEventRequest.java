package se.flowkeeper.api.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StartEventRequest(
	@Min(1) @Max(5) short ingoingEnergy,
	String ingoingNote
) {
}
