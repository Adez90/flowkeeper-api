package se.flowkeeper.api.billing;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.Invoice;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.SubscriptionItem;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * The real payment processor. Both stripe.secret-key and
 * stripe.webhook-secret default to blank until flowkeeper-infra's
 * environment sets STRIPE_SECRET_KEY / STRIPE_WEBHOOK_SECRET — every call
 * here fails clearly (PaymentProviderNotConfiguredException) rather than
 * attempting a request with an empty key, the same "configured or a
 * clear failure, never a confusing low-level error" posture as
 * EmailNotificationSender.
 */
@Component
public class StripeGateway implements PaymentGateway {

	private static final Logger log = LoggerFactory.getLogger(StripeGateway.class);

	private final String secretKey;
	private final String webhookSecret;

	public StripeGateway(
			@Value("${app.billing.stripe.secret-key}") String secretKey,
			@Value("${app.billing.stripe.webhook-secret}") String webhookSecret) {
		this.secretKey = secretKey;
		this.webhookSecret = webhookSecret;
	}

	@PostConstruct
	void configure() {
		if (!secretKey.isBlank()) {
			Stripe.apiKey = secretKey;
		}
	}

	@Override
	public String createCheckoutSession(CheckoutSessionContext context) {
		if (secretKey.isBlank()) {
			throw new PaymentProviderNotConfiguredException("Stripe is not configured yet (no secret key set)");
		}

		Price price = context.price();
		SessionCreateParams.LineItem.PriceData.Builder priceDataBuilder = SessionCreateParams.LineItem.PriceData.builder()
			.setCurrency(price.getCurrency().toLowerCase())
			.setUnitAmount(price.getAmountMinorUnits())
			.setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
				.setName(price.getPlan().getName() + " – " + periodLabel(price.getPeriod()))
				.build());

		SessionCreateParams.Builder builder = SessionCreateParams.builder()
			.setSuccessUrl(context.successUrl())
			.setCancelUrl(context.cancelUrl())
			.setClientReferenceId(context.accountId().toString())
			.putMetadata("accountId", context.accountId().toString())
			.putMetadata("priceId", price.getId().toString());

		if (context.customerEmail() != null) {
			builder.setCustomerEmail(context.customerEmail());
		}

		if (price.getBillingType() == BillingType.RECURRING) {
			priceDataBuilder.setRecurring(SessionCreateParams.LineItem.PriceData.Recurring.builder()
				.setInterval(stripeIntervalFor(price.getPeriod()))
				.setIntervalCount(stripeIntervalCountFor(price.getPeriod()))
				.build());
			builder.setMode(SessionCreateParams.Mode.SUBSCRIPTION);
			SessionCreateParams.SubscriptionData.Builder subscriptionData = SessionCreateParams.SubscriptionData.builder()
				.putMetadata("accountId", context.accountId().toString())
				.putMetadata("priceId", price.getId().toString());
			if (context.seatCount() != null) {
				subscriptionData.putMetadata("seatCount", context.seatCount().toString());
			}
			builder.setSubscriptionData(subscriptionData.build());
		} else {
			builder.setMode(SessionCreateParams.Mode.PAYMENT);
		}

		if (context.seatCount() != null) {
			builder.putMetadata("seatCount", context.seatCount().toString());
		}

		builder.addLineItem(SessionCreateParams.LineItem.builder()
			.setQuantity(context.seatCount() != null ? (long) context.seatCount() : 1L)
			.setPriceData(priceDataBuilder.build())
			.build());

		try {
			Session session = Session.create(builder.build());
			return session.getUrl();
		} catch (StripeException e) {
			log.warn("Stripe checkout session creation failed for account {}: {}", context.accountId(), e.getMessage());
			throw new PaymentProviderNotConfiguredException("Could not start checkout: " + e.getMessage());
		}
	}

	@Override
	public PaymentWebhookEvent parseWebhookEvent(String payload, String signatureHeader) {
		if (webhookSecret.isBlank()) {
			throw new PaymentProviderNotConfiguredException("Stripe webhook secret is not configured yet");
		}

		Event event;
		try {
			event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
		} catch (SignatureVerificationException e) {
			throw new PaymentProviderNotConfiguredException("Invalid Stripe webhook signature");
		}

		EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
		StripeObject stripeObject = deserializer.getObject().orElse(null);

		return switch (event.getType()) {
			case "checkout.session.completed" -> fromCheckoutSession(event, (Session) stripeObject);
			case "customer.subscription.updated", "customer.subscription.created" ->
				fromSubscription(event, (Subscription) stripeObject, mapStripeStatus(((Subscription) stripeObject).getStatus()));
			case "customer.subscription.deleted" ->
				fromSubscription(event, (Subscription) stripeObject, SubscriptionStatus.CANCELED);
			case "invoice.payment_failed" -> fromInvoicePaymentFailed(event, (Invoice) stripeObject);
			default -> new PaymentWebhookEvent(event.getId(), event.getType(), null, null, null, null, null, null, null);
		};
	}

	private PaymentWebhookEvent fromCheckoutSession(Event event, Session session) {
		if (session == null) {
			return new PaymentWebhookEvent(event.getId(), event.getType(), null, null, null, null, null, null, null);
		}
		UUID accountId = parseUuid(session.getClientReferenceId());
		UUID priceId = parseUuid(session.getMetadata() != null ? session.getMetadata().get("priceId") : null);
		Integer seatCount = parseInt(session.getMetadata() != null ? session.getMetadata().get("seatCount") : null);
		return new PaymentWebhookEvent(
			event.getId(), event.getType(), accountId, priceId, seatCount,
			session.getCustomer(), session.getSubscription(),
			// A one-time payment is settled the moment checkout completes; a
			// recurring one is confirmed shortly after by
			// customer.subscription.updated, which also carries the real
			// period end — this event alone doesn't have it.
			SubscriptionStatus.ACTIVE, null);
	}

	private PaymentWebhookEvent fromSubscription(Event event, Subscription subscription, SubscriptionStatus status) {
		if (subscription == null) {
			return new PaymentWebhookEvent(event.getId(), event.getType(), null, null, null, null, null, null, null);
		}
		UUID accountId = parseUuid(subscription.getMetadata() != null ? subscription.getMetadata().get("accountId") : null);
		UUID priceId = parseUuid(subscription.getMetadata() != null ? subscription.getMetadata().get("priceId") : null);
		Integer seatCount = parseInt(subscription.getMetadata() != null ? subscription.getMetadata().get("seatCount") : null);
		Instant periodEnd = subscription.getItems() != null && !subscription.getItems().getData().isEmpty()
			? periodEndOf(subscription.getItems().getData().get(0))
			: null;
		return new PaymentWebhookEvent(
			event.getId(), event.getType(), accountId, priceId, seatCount,
			subscription.getCustomer(), subscription.getId(), status, periodEnd);
	}

	private PaymentWebhookEvent fromInvoicePaymentFailed(Event event, Invoice invoice) {
		if (invoice == null) {
			return new PaymentWebhookEvent(event.getId(), event.getType(), null, null, null, null, null, null, null);
		}
		// Invoices reference their customer directly but not their
		// subscription by a simple id field in this API version — BillingService
		// resolves the affected Subscription by provider customer id instead.
		return new PaymentWebhookEvent(
			event.getId(), event.getType(), null, null, null,
			invoice.getCustomer(), null, SubscriptionStatus.PAST_DUE, null);
	}

	private static Instant periodEndOf(SubscriptionItem item) {
		return item.getCurrentPeriodEnd() != null ? Instant.ofEpochSecond(item.getCurrentPeriodEnd()) : null;
	}

	private static SubscriptionStatus mapStripeStatus(String stripeStatus) {
		if (stripeStatus == null) {
			return SubscriptionStatus.INCOMPLETE;
		}
		return switch (stripeStatus) {
			case "active", "trialing" -> SubscriptionStatus.ACTIVE;
			case "past_due", "unpaid" -> SubscriptionStatus.PAST_DUE;
			case "canceled" -> SubscriptionStatus.CANCELED;
			case "incomplete_expired" -> SubscriptionStatus.EXPIRED;
			default -> SubscriptionStatus.INCOMPLETE;
		};
	}

	private static SessionCreateParams.LineItem.PriceData.Recurring.Interval stripeIntervalFor(BillingPeriod period) {
		return switch (period) {
			case ONE_MONTH, THREE_MONTHS, SIX_MONTHS -> SessionCreateParams.LineItem.PriceData.Recurring.Interval.MONTH;
			case TWELVE_MONTHS, TWO_YEARS, THREE_YEARS, FOUR_YEARS, FIVE_YEARS ->
				SessionCreateParams.LineItem.PriceData.Recurring.Interval.YEAR;
		};
	}

	private static long stripeIntervalCountFor(BillingPeriod period) {
		return switch (period) {
			case ONE_MONTH -> 1;
			case THREE_MONTHS -> 3;
			case SIX_MONTHS -> 6;
			case TWELVE_MONTHS -> 1;
			case TWO_YEARS -> 2;
			case THREE_YEARS -> 3;
			case FOUR_YEARS -> 4;
			case FIVE_YEARS -> 5;
		};
	}

	private static String periodLabel(BillingPeriod period) {
		return switch (period) {
			case ONE_MONTH -> "1 month";
			case THREE_MONTHS -> "3 months";
			case SIX_MONTHS -> "6 months";
			case TWELVE_MONTHS -> "12 months";
			case TWO_YEARS -> "2 years";
			case THREE_YEARS -> "3 years";
			case FOUR_YEARS -> "4 years";
			case FIVE_YEARS -> "5 years";
		};
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static Integer parseInt(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
