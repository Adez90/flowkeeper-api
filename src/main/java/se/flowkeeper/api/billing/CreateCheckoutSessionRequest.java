package se.flowkeeper.api.billing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCheckoutSessionRequest(
		@NotNull UUID accountId,
		@NotNull UUID priceId,
		/** Required (and only meaningful) when the chosen price is per-seat. */
		@Min(1) Integer seatCount) {

}
