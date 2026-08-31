package se.flowkeeper.api.integrations;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ImportEventsRequest(
	@NotNull UUID accountId,
	@NotEmpty List<@Valid ImportSelectionRequest> selections
) {
}
