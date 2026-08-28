package se.flowkeeper.api.billing;

import java.util.UUID;

public record PriceResponse(
		UUID id,
		BillingPeriod period,
		BillingType billingType,
		boolean perSeat,
		long amountMinorUnits,
		String currency) {

	public static PriceResponse from(Price price) {
		return new PriceResponse(
			price.getId(),
			price.getPeriod(),
			price.getBillingType(),
			price.isPerSeat(),
			price.getAmountMinorUnits(),
			price.getCurrency());
	}

}
