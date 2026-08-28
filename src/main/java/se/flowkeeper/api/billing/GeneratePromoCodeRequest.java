package se.flowkeeper.api.billing;

import jakarta.validation.constraints.Min;

import java.time.Instant;

public record GeneratePromoCodeRequest(
		@Min(1) int durationDays,
		/** Omit for a single-use (private-person) code; set higher for a company-wide code. */
		@Min(1) int maxRedemptions,
		/** Omit for no redeem-by deadline. */
		Instant expiresAt,
		String note) {

}
