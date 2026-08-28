package se.flowkeeper.api.billing;

/** No Stripe API key (or webhook secret) is set yet — real payment processing needs flowkeeper-infra's environment configured first. */
public class PaymentProviderNotConfiguredException extends RuntimeException {

	public PaymentProviderNotConfiguredException(String message) {
		super(message);
	}

}
