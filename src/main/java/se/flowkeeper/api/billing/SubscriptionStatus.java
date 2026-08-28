package se.flowkeeper.api.billing;

public enum SubscriptionStatus {
	/** Checkout started but not yet confirmed by the provider (e.g. a bank-auth step still pending). */
	INCOMPLETE,
	ACTIVE,
	/** A recurring charge failed; the provider is retrying (dunning) before cancelling. */
	PAST_DUE,
	CANCELED,
	/** Never became active and the provider gave up (e.g. checkout abandoned past its expiry). */
	EXPIRED
}
