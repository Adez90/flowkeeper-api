package se.flowkeeper.api.billing;

import java.util.UUID;

/** Everything a PaymentGateway needs to start a hosted checkout, gathered so BillingService doesn't hand entities around piecemeal. */
public record CheckoutSessionContext(
		UUID accountId,
		String accountName,
		String customerEmail,
		Price price,
		Integer seatCount,
		String successUrl,
		String cancelUrl) {

}
