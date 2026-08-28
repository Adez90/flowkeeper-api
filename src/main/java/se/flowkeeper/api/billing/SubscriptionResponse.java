package se.flowkeeper.api.billing;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
		UUID accountId,
		/** Null for a promo-code-granted trial — it isn't tied to any specific paid price. */
		UUID priceId,
		PlanScope planScope,
		String planCode,
		BillingPeriod period,
		BillingType billingType,
		Integer seatCount,
		SubscriptionStatus status,
		Instant currentPeriodEnd,
		String provider) {

	public static SubscriptionResponse from(Subscription subscription) {
		Price price = subscription.getPrice();
		Plan plan = price != null ? price.getPlan() : null;
		return new SubscriptionResponse(
			subscription.getAccount().getId(),
			price != null ? price.getId() : null,
			plan != null ? plan.getScope() : null,
			plan != null ? plan.getCode() : null,
			price != null ? price.getPeriod() : null,
			price != null ? price.getBillingType() : null,
			subscription.getSeatCount(),
			subscription.getStatus(),
			subscription.getCurrentPeriodEnd(),
			subscription.getProvider());
	}

}
