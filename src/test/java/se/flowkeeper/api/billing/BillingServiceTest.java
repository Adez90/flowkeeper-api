package se.flowkeeper.api.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ConflictException;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

	@Mock PlanRepository planRepository;
	@Mock PriceRepository priceRepository;
	@Mock SubscriptionRepository subscriptionRepository;
	@Mock PaymentEventRepository paymentEventRepository;
	@Mock PromoCodeRepository promoCodeRepository;
	@Mock PromoCodeRedemptionRepository promoCodeRedemptionRepository;
	@Mock AccountRepository accountRepository;
	@Mock AccountMemberRepository accountMemberRepository;
	@Mock CurrentUserResolver currentUserResolver;
	@Mock PaymentGateway paymentGateway;

	private final User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
	// Mocked, not `new Account(...)` — a freshly-constructed entity has a
	// null id until JPA assigns one on save, but these tests need a
	// stable non-null id to match webhook metadata against, the same
	// reason Plan/Price below are mocked rather than constructed.
	private final Account account = mockAccount();
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private static Account mockAccount() {
		Account account = mock(Account.class);
		lenient().when(account.getId()).thenReturn(UUID.randomUUID());
		lenient().when(account.getName()).thenReturn("Anders Johansson");
		return account;
	}

	private BillingService service() {
		return new BillingService(planRepository, priceRepository, subscriptionRepository, paymentEventRepository,
			promoCodeRepository, promoCodeRedemptionRepository, accountRepository, accountMemberRepository,
			currentUserResolver, paymentGateway, "http://localhost:5173");
	}

	private Plan plan(PlanScope scope, String code) {
		Plan plan = mock(Plan.class);
		lenient().when(plan.getId()).thenReturn(UUID.randomUUID());
		lenient().when(plan.getCode()).thenReturn(code);
		lenient().when(plan.getScope()).thenReturn(scope);
		lenient().when(plan.getName()).thenReturn(code);
		return plan;
	}

	private Price price(Plan plan, BillingPeriod period, BillingType billingType, boolean perSeat, long amount) {
		Price price = mock(Price.class);
		lenient().when(price.getId()).thenReturn(UUID.randomUUID());
		lenient().when(price.getPlan()).thenReturn(plan);
		lenient().when(price.getPeriod()).thenReturn(period);
		lenient().when(price.getBillingType()).thenReturn(billingType);
		lenient().when(price.isPerSeat()).thenReturn(perSeat);
		lenient().when(price.getAmountMinorUnits()).thenReturn(amount);
		lenient().when(price.getCurrency()).thenReturn("SEK");
		lenient().when(price.isActive()).thenReturn(true);
		return price;
	}

	@Test
	void listPlansReturnsEachPlanWithItsActivePrices() {
		Plan personal = plan(PlanScope.PERSONAL, "personal");
		Price monthly = price(personal, BillingPeriod.ONE_MONTH, BillingType.RECURRING, false, 9900);
		when(planRepository.findAllByOrderByScopeAsc()).thenReturn(List.of(personal));
		when(priceRepository.findByActiveTrueAndPlan_IdOrderByPeriodAsc(personal.getId())).thenReturn(List.of(monthly));

		List<PlanResponse> plans = service().listPlans();

		assertThat(plans).hasSize(1);
		assertThat(plans.get(0).code()).isEqualTo("personal");
		assertThat(plans.get(0).prices()).hasSize(1);
		assertThat(plans.get(0).prices().get(0).amountMinorUnits()).isEqualTo(9900);
	}

	@Test
	void listPlansReturnsNothingWhenTheCatalogIsEmpty() {
		when(planRepository.findAllByOrderByScopeAsc()).thenReturn(List.of());

		assertThat(service().listPlans()).isEmpty();
	}

	@Test
	void getSubscriptionReturnsNullWhenAccountHasNeverSubscribed() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(subscriptionRepository.findByAccount_Id(any())).thenReturn(Optional.empty());

		assertThat(service().getSubscription(jwt, account.getId())).isNull();
	}

	@Test
	void getSubscriptionRejectsANonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().getSubscription(jwt, account.getId()))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void createCheckoutSessionRejectsANonOwner() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.MEMBER)));

		assertThatThrownBy(() -> service().createCheckoutSession(jwt, new CreateCheckoutSessionRequest(account.getId(), UUID.randomUUID(), null)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void createCheckoutSessionRejectsAPerSeatPriceWithNoSeatCount() {
		Plan business = plan(PlanScope.BUSINESS, "business");
		Price perSeatPrice = price(business, BillingPeriod.THREE_MONTHS, BillingType.RECURRING, true, 19900);
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(priceRepository.findById(perSeatPrice.getId())).thenReturn(Optional.of(perSeatPrice));

		assertThatThrownBy(() -> service().createCheckoutSession(jwt, new CreateCheckoutSessionRequest(account.getId(), perSeatPrice.getId(), null)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void createCheckoutSessionRejectsASeatCountOnAFlatPrice() {
		Plan personal = plan(PlanScope.PERSONAL, "personal");
		Price flatPrice = price(personal, BillingPeriod.ONE_MONTH, BillingType.RECURRING, false, 9900);
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(priceRepository.findById(flatPrice.getId())).thenReturn(Optional.of(flatPrice));

		assertThatThrownBy(() -> service().createCheckoutSession(jwt, new CreateCheckoutSessionRequest(account.getId(), flatPrice.getId(), 5)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void createCheckoutSessionRejectsAnUnknownPrice() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(priceRepository.findById(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().createCheckoutSession(jwt, new CreateCheckoutSessionRequest(account.getId(), UUID.randomUUID(), null)))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void createCheckoutSessionCallsTheGatewayAndReturnsItsUrlForAnOwner() {
		Plan business = plan(PlanScope.BUSINESS, "business");
		Price perSeatPrice = price(business, BillingPeriod.THREE_MONTHS, BillingType.RECURRING, true, 19900);
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(priceRepository.findById(perSeatPrice.getId())).thenReturn(Optional.of(perSeatPrice));
		when(paymentGateway.createCheckoutSession(any())).thenReturn("https://checkout.stripe.com/session/abc");

		CheckoutSessionResponse response = service().createCheckoutSession(jwt,
			new CreateCheckoutSessionRequest(account.getId(), perSeatPrice.getId(), 7));

		assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/session/abc");
		ArgumentCaptor<CheckoutSessionContext> captor = ArgumentCaptor.forClass(CheckoutSessionContext.class);
		verify(paymentGateway).createCheckoutSession(captor.capture());
		assertThat(captor.getValue().accountId()).isEqualTo(account.getId());
		assertThat(captor.getValue().seatCount()).isEqualTo(7);
		assertThat(captor.getValue().price()).isEqualTo(perSeatPrice);
	}

	@Test
	void handleWebhookEventSkipsAnAlreadyProcessedDelivery() {
		PaymentWebhookEvent event = new PaymentWebhookEvent("evt_1", "checkout.session.completed",
			UUID.randomUUID(), UUID.randomUUID(), null, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, null);
		when(paymentGateway.parseWebhookEvent(any(), any())).thenReturn(event);
		when(paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_1")).thenReturn(true);

		service().handleWebhookEvent("{}", "sig");

		verify(paymentEventRepository, never()).save(any());
		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void handleWebhookEventCreatesASubscriptionForANewlyPaidAccount() {
		Plan personal = plan(PlanScope.PERSONAL, "personal");
		Price monthlyPrice = price(personal, BillingPeriod.ONE_MONTH, BillingType.RECURRING, false, 9900);
		PaymentWebhookEvent event = new PaymentWebhookEvent("evt_2", "checkout.session.completed",
			account.getId(), monthlyPrice.getId(), null, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, null);
		when(paymentGateway.parseWebhookEvent(any(), any())).thenReturn(event);
		when(paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_2")).thenReturn(false);
		when(priceRepository.findById(monthlyPrice.getId())).thenReturn(Optional.of(monthlyPrice));
		when(subscriptionRepository.findByAccount_Id(account.getId())).thenReturn(Optional.empty());
		when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));

		service().handleWebhookEvent("{}", "sig");

		verify(paymentEventRepository).save(any(PaymentEvent.class));
		ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
		verify(subscriptionRepository).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(captor.getValue().getProviderSubscriptionId()).isEqualTo("sub_1");
	}

	@Test
	void handleWebhookEventResolvesTheAccountByProviderCustomerIdWhenNoAccountIdIsCarried() {
		Plan personal = plan(PlanScope.PERSONAL, "personal");
		Price monthlyPrice = price(personal, BillingPeriod.ONE_MONTH, BillingType.RECURRING, false, 9900);
		Subscription existing = new Subscription(account, monthlyPrice, null, SubscriptionStatus.ACTIVE);
		PaymentWebhookEvent event = new PaymentWebhookEvent("evt_3", "invoice.payment_failed",
			null, null, null, "cus_1", null, SubscriptionStatus.PAST_DUE, null);
		when(paymentGateway.parseWebhookEvent(any(), any())).thenReturn(event);
		when(paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_3")).thenReturn(false);
		when(subscriptionRepository.findByProviderCustomerId("cus_1")).thenReturn(Optional.of(existing));
		when(subscriptionRepository.findByAccount_Id(account.getId())).thenReturn(Optional.of(existing));

		service().handleWebhookEvent("{}", "sig");

		assertThat(existing.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
		verify(subscriptionRepository).save(existing);
	}

	@Test
	void handleWebhookEventRecordsButDoesNotApplyAnEventWithNoStatusChange() {
		PaymentWebhookEvent event = new PaymentWebhookEvent("evt_4", "customer.subscription.trial_will_end",
			account.getId(), null, null, null, null, null, null);
		when(paymentGateway.parseWebhookEvent(any(), any())).thenReturn(event);
		when(paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_4")).thenReturn(false);

		service().handleWebhookEvent("{}", "sig");

		verify(paymentEventRepository).save(any(PaymentEvent.class));
		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void handleWebhookEventIgnoresAStateChangeWithNoResolvableAccount() {
		PaymentWebhookEvent event = new PaymentWebhookEvent("evt_5", "invoice.payment_failed",
			null, null, null, "cus_unknown", null, SubscriptionStatus.PAST_DUE, null);
		when(paymentGateway.parseWebhookEvent(any(), any())).thenReturn(event);
		when(paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_5")).thenReturn(false);
		when(subscriptionRepository.findByProviderCustomerId("cus_unknown")).thenReturn(Optional.empty());

		service().handleWebhookEvent("{}", "sig");

		verify(paymentEventRepository).save(any(PaymentEvent.class));
		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void handleWebhookEventForANewAccountWithNoKnownPriceIsSkipped() {
		PaymentWebhookEvent event = new PaymentWebhookEvent("evt_6", "checkout.session.completed",
			account.getId(), null, null, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, null);
		when(paymentGateway.parseWebhookEvent(any(), any())).thenReturn(event);
		when(paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_6")).thenReturn(false);
		when(subscriptionRepository.findByAccount_Id(account.getId())).thenReturn(Optional.empty());

		service().handleWebhookEvent("{}", "sig");

		verify(paymentEventRepository).save(any(PaymentEvent.class));
		verify(subscriptionRepository, never()).save(any());
	}

	@Test
	void handleWebhookEventUpdatesAnExistingSubscriptionsPeriodEnd() {
		Plan personal = plan(PlanScope.PERSONAL, "personal");
		Price monthlyPrice = price(personal, BillingPeriod.ONE_MONTH, BillingType.RECURRING, false, 9900);
		Subscription existing = new Subscription(account, monthlyPrice, null, SubscriptionStatus.ACTIVE);
		Instant newPeriodEnd = Instant.now().plusSeconds(2_592_000);
		PaymentWebhookEvent event = new PaymentWebhookEvent("evt_7", "customer.subscription.updated",
			account.getId(), monthlyPrice.getId(), null, "cus_1", "sub_1", SubscriptionStatus.ACTIVE, newPeriodEnd);
		when(paymentGateway.parseWebhookEvent(any(), any())).thenReturn(event);
		when(paymentEventRepository.existsByProviderAndProviderEventId("STRIPE", "evt_7")).thenReturn(false);
		when(priceRepository.findById(monthlyPrice.getId())).thenReturn(Optional.of(monthlyPrice));
		when(subscriptionRepository.findByAccount_Id(account.getId())).thenReturn(Optional.of(existing));

		service().handleWebhookEvent("{}", "sig");

		assertThat(existing.getCurrentPeriodEnd()).isEqualTo(newPeriodEnd);
		verify(subscriptionRepository).save(existing);
	}

	@Test
	void redeemPromoCodeRejectsANonOwner() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.MEMBER)));

		assertThatThrownBy(() -> service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234")))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void redeemPromoCodeRejectsAnUnknownCode() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), " abcd-1234 ")))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void redeemPromoCodeRejectsARevokedCode() {
		PromoCode promoCode = new PromoCode("ABCD-1234", 90, 1, null, null, "admin@flowkeeper.se");
		promoCode.revoke();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.of(promoCode));

		assertThatThrownBy(() -> service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234")))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void redeemPromoCodeRejectsAnExpiredCode() {
		PromoCode promoCode = new PromoCode("ABCD-1234", 90, 1, Instant.now().minusSeconds(60), null, "admin@flowkeeper.se");
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.of(promoCode));

		assertThatThrownBy(() -> service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234")))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void redeemPromoCodeRejectsAnExhaustedCode() {
		PromoCode promoCode = new PromoCode("ABCD-1234", 90, 1, null, null, "admin@flowkeeper.se");
		promoCode.recordRedemption();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.of(promoCode));

		assertThatThrownBy(() -> service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234")))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void redeemPromoCodeRejectsADoubleRedemptionByTheSameAccount() {
		PromoCode promoCode = new PromoCode("ABCD-1234", 90, 5, null, null, "admin@flowkeeper.se");
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.of(promoCode));
		when(promoCodeRedemptionRepository.existsByPromoCode_IdAndAccount_Id(any(), any())).thenReturn(true);

		assertThatThrownBy(() -> service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234")))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void redeemPromoCodeGrantsANewSubscriptionWhenNoneExists() {
		PromoCode promoCode = new PromoCode("ABCD-1234", 90, 1, null, null, "admin@flowkeeper.se");
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.of(promoCode));
		when(promoCodeRedemptionRepository.existsByPromoCode_IdAndAccount_Id(any(), any())).thenReturn(false);
		when(subscriptionRepository.findByAccount_Id(account.getId())).thenReturn(Optional.empty());

		SubscriptionResponse response = service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234"));

		assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(response.provider()).isEqualTo("PROMO_CODE");
		assertThat(response.priceId()).isNull();
		assertThat(response.currentPeriodEnd()).isAfter(Instant.now().plus(89, java.time.temporal.ChronoUnit.DAYS));

		ArgumentCaptor<Subscription> captor = ArgumentCaptor.forClass(Subscription.class);
		verify(subscriptionRepository).save(captor.capture());
		assertThat(captor.getValue().getProvider()).isEqualTo("PROMO_CODE");
		assertThat(promoCode.getRedemptionCount()).isEqualTo(1);
		verify(promoCodeRepository).save(promoCode);
		verify(promoCodeRedemptionRepository).save(any(PromoCodeRedemption.class));
	}

	@Test
	void redeemPromoCodeExtendsAnExistingSubscriptionFromItsCurrentPeriodEnd() {
		PromoCode promoCode = new PromoCode("ABCD-1234", 30, 1, null, null, "admin@flowkeeper.se");
		Plan personal = plan(PlanScope.PERSONAL, "personal");
		Price monthlyPrice = price(personal, BillingPeriod.ONE_MONTH, BillingType.RECURRING, false, 9900);
		Instant farFuture = Instant.now().plus(60, java.time.temporal.ChronoUnit.DAYS);
		Subscription existing = new Subscription(account, monthlyPrice, null, SubscriptionStatus.ACTIVE);
		existing.applyProviderState(null, null, null, farFuture, "cus_1", "sub_1");
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.of(promoCode));
		when(promoCodeRedemptionRepository.existsByPromoCode_IdAndAccount_Id(any(), any())).thenReturn(false);
		when(subscriptionRepository.findByAccount_Id(account.getId())).thenReturn(Optional.of(existing));

		SubscriptionResponse response = service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234"));

		// Extends from the existing (still-in-the-future) period end, not from now.
		assertThat(response.currentPeriodEnd()).isEqualTo(farFuture.plus(30, java.time.temporal.ChronoUnit.DAYS));
	}

	@Test
	void redeemPromoCodeExtendsFromNowWhenTheExistingSubscriptionHasLapsed() {
		PromoCode promoCode = new PromoCode("ABCD-1234", 30, 1, null, null, "admin@flowkeeper.se");
		Plan personal = plan(PlanScope.PERSONAL, "personal");
		Price monthlyPrice = price(personal, BillingPeriod.ONE_MONTH, BillingType.RECURRING, false, 9900);
		Subscription existing = new Subscription(account, monthlyPrice, null, SubscriptionStatus.CANCELED);
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(promoCodeRepository.findByCode("ABCD-1234")).thenReturn(Optional.of(promoCode));
		when(promoCodeRedemptionRepository.existsByPromoCode_IdAndAccount_Id(any(), any())).thenReturn(false);
		when(subscriptionRepository.findByAccount_Id(account.getId())).thenReturn(Optional.of(existing));

		SubscriptionResponse response = service().redeemPromoCode(jwt, new RedeemPromoCodeRequest(account.getId(), "ABCD-1234"));

		assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
		assertThat(response.currentPeriodEnd()).isAfter(Instant.now().plus(29, java.time.temporal.ChronoUnit.DAYS));
	}

}
