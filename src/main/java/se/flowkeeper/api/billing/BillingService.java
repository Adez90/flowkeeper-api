package se.flowkeeper.api.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.util.List;
import java.util.UUID;

@Service
public class BillingService {

	private static final Logger log = LoggerFactory.getLogger(BillingService.class);

	private final PlanRepository planRepository;
	private final PriceRepository priceRepository;
	private final SubscriptionRepository subscriptionRepository;
	private final PaymentEventRepository paymentEventRepository;
	private final AccountRepository accountRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final CurrentUserResolver currentUserResolver;
	private final PaymentGateway paymentGateway;
	private final String appOrigin;

	public BillingService(PlanRepository planRepository,
			PriceRepository priceRepository,
			SubscriptionRepository subscriptionRepository,
			PaymentEventRepository paymentEventRepository,
			AccountRepository accountRepository,
			AccountMemberRepository accountMemberRepository,
			CurrentUserResolver currentUserResolver,
			PaymentGateway paymentGateway,
			@Value("${app.cors.allowed-origin}") String appOrigin) {
		this.planRepository = planRepository;
		this.priceRepository = priceRepository;
		this.subscriptionRepository = subscriptionRepository;
		this.paymentEventRepository = paymentEventRepository;
		this.accountRepository = accountRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.currentUserResolver = currentUserResolver;
		this.paymentGateway = paymentGateway;
		this.appOrigin = appOrigin;
	}

	/** The full pricing catalog — Personal and Business, each with its active prices. Not account-scoped. */
	@Transactional(readOnly = true)
	public List<PlanResponse> listPlans() {
		return planRepository.findAllByOrderByScopeAsc().stream()
			.map(plan -> PlanResponse.from(plan, priceRepository.findByActiveTrueAndPlan_IdOrderByPeriodAsc(plan.getId())))
			.toList();
	}

	/** Null means the account has never subscribed — free/no plan, not an error. */
	@Transactional(readOnly = true)
	public SubscriptionResponse getSubscription(Jwt jwt, UUID accountId) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);

		return subscriptionRepository.findByAccount_Id(accountId)
			.map(SubscriptionResponse::from)
			.orElse(null);
	}

	/** Only the account's OWNER can start a checkout — billing is not a per-member action. */
	@Transactional
	public CheckoutSessionResponse createCheckoutSession(Jwt jwt, CreateCheckoutSessionRequest request) {
		User user = currentUserResolver.require(jwt);
		Account account = requireOwner(request.accountId(), user);

		Price price = priceRepository.findById(request.priceId())
			.orElseThrow(() -> new ResourceNotFoundException("Unknown price: " + request.priceId()));
		if (!price.isActive()) {
			throw new ValidationException("This price is no longer available");
		}
		if (price.isPerSeat() && request.seatCount() == null) {
			throw new ValidationException("seatCount is required for a per-seat price");
		}
		if (!price.isPerSeat() && request.seatCount() != null) {
			throw new ValidationException("seatCount does not apply to this price");
		}

		String successUrl = appOrigin + "/app/billing?checkout=success";
		String cancelUrl = appOrigin + "/app/billing?checkout=cancelled";

		String checkoutUrl = paymentGateway.createCheckoutSession(new CheckoutSessionContext(
			account.getId(), account.getName(), user.getEmail(), price, request.seatCount(), successUrl, cancelUrl));

		log.info("User {} started a checkout for account {} price {}", user.getId(), account.getId(), price.getId());

		return new CheckoutSessionResponse(checkoutUrl);
	}

	/**
	 * Applies one verified webhook delivery. Idempotent — a redelivered
	 * event (same provider + providerEventId) is recognised via
	 * payment_events and skipped. Most event types carry no state change
	 * (status == null); they're still recorded for audit.
	 */
	@Transactional
	public void handleWebhookEvent(String payload, String signatureHeader) {
		PaymentWebhookEvent event = paymentGateway.parseWebhookEvent(payload, signatureHeader);

		if (paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", event.providerEventId())) {
			log.debug("Ignoring already-processed Stripe event {}", event.providerEventId());
			return;
		}

		UUID resolvedAccountId = resolveAccountId(event);
		paymentEventRepository.save(new PaymentEvent(resolvedAccountId, "STRIPE", event.providerEventId(), event.eventType(), payload));

		if (resolvedAccountId == null || event.status() == null) {
			log.debug("Stripe event {} ({}) recorded; no subscription state to apply", event.providerEventId(), event.eventType());
			return;
		}

		applyToSubscription(resolvedAccountId, event);
	}

	private UUID resolveAccountId(PaymentWebhookEvent event) {
		if (event.accountId() != null) {
			return event.accountId();
		}
		if (event.providerSubscriptionId() != null) {
			return subscriptionRepository.findByProviderSubscriptionId(event.providerSubscriptionId())
				.map(s -> s.getAccount().getId())
				.orElse(null);
		}
		if (event.providerCustomerId() != null) {
			return subscriptionRepository.findByProviderCustomerId(event.providerCustomerId())
				.map(s -> s.getAccount().getId())
				.orElse(null);
		}
		return null;
	}

	private void applyToSubscription(UUID accountId, PaymentWebhookEvent event) {
		Price price = event.priceId() != null ? priceRepository.findById(event.priceId()).orElse(null) : null;
		Subscription subscription = subscriptionRepository.findByAccount_Id(accountId).orElse(null);

		if (subscription == null) {
			if (price == null) {
				log.warn("Stripe event {} would create a new subscription for account {} but carries no known priceId — skipping",
					event.providerEventId(), accountId);
				return;
			}
			Account account = accountRepository.findById(accountId).orElse(null);
			if (account == null) {
				log.warn("Stripe event {} references unknown account {}", event.providerEventId(), accountId);
				return;
			}
			subscription = new Subscription(account, price, event.seatCount(), event.status());
		}

		subscription.applyProviderState(price, event.seatCount(), event.status(), event.currentPeriodEnd(),
			event.providerCustomerId(), event.providerSubscriptionId());
		subscriptionRepository.save(subscription);

		log.info("Applied Stripe event {} ({}) to account {}: status={}",
			event.providerEventId(), event.eventType(), accountId, subscription.getStatus());
	}

	private Account requireOwner(UUID accountId, User user) {
		AccountMember membership = accountMemberRepository.findByAccount_IdAndUser(accountId, user)
			.orElseThrow(() -> new AccessDeniedException(
				"User %s is not a member of account %s".formatted(user.getId(), accountId)));
		if (membership.getRole() != MemberRole.OWNER) {
			throw new AccessDeniedException(
				"Only the account owner can manage billing for account %s".formatted(accountId));
		}
		return membership.getAccount();
	}

	private void requireMembership(UUID accountId, User user) {
		accountMemberRepository.findByAccount_IdAndUser(accountId, user)
			.orElseThrow(() -> new AccessDeniedException(
				"User %s is not a member of account %s".formatted(user.getId(), accountId)));
	}

}
