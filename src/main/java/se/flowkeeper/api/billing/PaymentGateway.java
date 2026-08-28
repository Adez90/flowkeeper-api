package se.flowkeeper.api.billing;

/**
 * Abstraction over the actual payment processor so BillingService doesn't
 * depend on Stripe's API shape directly — adding or swapping a processor
 * later means a new implementation of this, not a rewrite of the billing
 * domain logic.
 */
public interface PaymentGateway {

	/** Starts a hosted checkout flow for one Price; returns the URL to redirect the browser to. */
	String createCheckoutSession(CheckoutSessionContext context);

	/** Verifies the delivery's signature and parses it into a normalized event; throws if the signature doesn't check out. */
	PaymentWebhookEvent parseWebhookEvent(String payload, String signatureHeader);

}
