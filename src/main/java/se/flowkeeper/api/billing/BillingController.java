package se.flowkeeper.api.billing;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class BillingController {

	private final BillingService billingService;

	public BillingController(BillingService billingService) {
		this.billingService = billingService;
	}

	/** The pricing catalog for a plans/pricing page — Personal and Business, each with its active prices. */
	@GetMapping("/api/v1/billing/plans")
	public List<PlanResponse> listPlans() {
		return billingService.listPlans();
	}

	/** A null body means the account has never subscribed — free, not an error. */
	@GetMapping("/api/v1/billing/subscription")
	public SubscriptionResponse getSubscription(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID accountId) {
		return billingService.getSubscription(jwt, accountId);
	}

	/** Starts a hosted checkout for one price. Only the account's OWNER may call this. */
	@PostMapping("/api/v1/billing/checkout-session")
	public CheckoutSessionResponse createCheckoutSession(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateCheckoutSessionRequest request) {
		return billingService.createCheckoutSession(jwt, request);
	}

	/** Redeems a trial/promo code for the account. Only the account's OWNER may call this. */
	@PostMapping("/api/v1/billing/redeem-promo-code")
	public SubscriptionResponse redeemPromoCode(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RedeemPromoCodeRequest request) {
		return billingService.redeemPromoCode(jwt, request);
	}

}
