package se.flowkeeper.api.billing;

import java.time.Instant;
import java.util.UUID;

public record PromoCodeResponse(
		UUID id,
		String code,
		int durationDays,
		int maxRedemptions,
		int redemptionCount,
		Instant expiresAt,
		String note,
		String createdByEmail,
		Instant createdAt,
		Instant revokedAt) {

	public static PromoCodeResponse from(PromoCode promoCode) {
		return new PromoCodeResponse(
			promoCode.getId(),
			promoCode.getCode(),
			promoCode.getDurationDays(),
			promoCode.getMaxRedemptions(),
			promoCode.getRedemptionCount(),
			promoCode.getExpiresAt(),
			promoCode.getNote(),
			promoCode.getCreatedByEmail(),
			promoCode.getCreatedAt(),
			promoCode.getRevokedAt());
	}

}
