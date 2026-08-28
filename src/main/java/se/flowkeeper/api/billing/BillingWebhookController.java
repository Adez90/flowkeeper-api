package se.flowkeeper.api.billing;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Separate from BillingController since this endpoint is deliberately
 * public — Stripe has no bearer token, it authenticates the delivery via
 * the Stripe-Signature header instead (verified inside
 * BillingService/StripeGateway). See SecurityConfig for the permit-all
 * rule this needs.
 */
@RestController
public class BillingWebhookController {

	private final BillingService billingService;

	public BillingWebhookController(BillingService billingService) {
		this.billingService = billingService;
	}

	@PostMapping("/api/v1/billing/webhook/stripe")
	public ResponseEntity<Void> stripeWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
		billingService.handleWebhookEvent(payload, signature);
		return ResponseEntity.ok().build();
	}

}
