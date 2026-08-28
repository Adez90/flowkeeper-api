package se.flowkeeper.api.billing;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionResponse(
		UUID accountId,
		UUID priceId,
		PlanScope planScope,
		String planCode,
		BillingPeriod period,
		BillingType billingType,
		Integer seatCount,
		SubscriptionStatus status,
		Instant currentPeriodEnd) {

	public static SubscriptionResponse from(Subscription subscription) {
		Price price = subscription.getPrice();
		Plan plan = price.getPlan();
		return new SubscriptionResponse(
			subscription.getAccount().getId(),
			price.getId(),
			plan.getScope(),
			plan.getCode(),
			price.getPeriod(),
			price.getBillingType(),
			subscription.getSeatCount(),
			subscription.getStatus(),
			subscription.getCurrentPeriodEnd());
	}

}
