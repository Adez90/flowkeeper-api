package se.flowkeeper.api.billing;

import java.time.Instant;
import java.util.UUID;

/**
 * A payment provider's webhook delivery, normalized so BillingService
 * doesn't depend on Stripe's event shape directly. status == null means
 * "log this for audit, no subscription state change" (most event types
 * fall in that bucket — we only act on the handful that change what an
 * account is entitled to).
 */
public record PaymentWebhookEvent(
		String providerEventId,
		String eventType,
		/** Resolved from checkout-session metadata; null once we're past checkout (e.g. later invoice events), when accountId is instead resolved via providerSubscriptionId. */
		UUID accountId,
		UUID priceId,
		Integer seatCount,
		String providerCustomerId,
		String providerSubscriptionId,
		SubscriptionStatus status,
		Instant currentPeriodEnd) {

}
