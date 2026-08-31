package se.flowkeeper.api.integrations;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

/** One item the client picked from a prior GET .../importable response, echoed back rather than re-fetched from the provider a second time. */
public record ImportSelectionRequest(
	@NotNull ExternalProvider provider,
	@NotBlank String externalId,
	@NotNull UUID eventTypeId,
	@NotNull Instant startedAt,
	@NotNull Instant endedAt
) {
}
