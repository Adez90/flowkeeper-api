package se.flowkeeper.api.event;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateEventRequest(
	@NotNull UUID accountId,
	@NotNull UUID eventTypeId,
	@Min(1) @Max(5) short ingoingEnergy,
	String ingoingNote
) {
}
