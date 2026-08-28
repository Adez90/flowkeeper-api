package se.flowkeeper.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import se.flowkeeper.api.account.Account;

import java.time.Instant;
import java.util.UUID;

/**
 * An account's current plan. One row per account — a plan change updates
 * this row in place rather than creating a new one; past subscriptions'
 * raw history lives in payment_events, not here.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false, unique = true)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "price_id", nullable = false)
	private Price price;

	/** Only meaningful when price.perSeat is true. */
	@Column(name = "seat_count")
	private Integer seatCount;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private SubscriptionStatus status;

	@Column(name = "current_period_end")
	private Instant currentPeriodEnd;

	@Column(name = "provider", nullable = false, length = 20)
	private String provider = "STRIPE";

	@Column(name = "provider_customer_id")
	private String providerCustomerId;

	@Column(name = "provider_subscription_id")
	private String providerSubscriptionId;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Subscription() {
	}

	public Subscription(Account account, Price price, Integer seatCount, SubscriptionStatus status) {
		this.account = account;
		this.price = price;
		this.seatCount = seatCount;
		this.status = status;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	/** Applied from a webhook event once the provider confirms (or revises) this subscription's state. */
	public void applyProviderState(Price price, Integer seatCount, SubscriptionStatus status, Instant currentPeriodEnd,
			String providerCustomerId, String providerSubscriptionId) {
		if (price != null) {
			this.price = price;
		}
		if (seatCount != null) {
			this.seatCount = seatCount;
		}
		if (status != null) {
			this.status = status;
		}
		if (currentPeriodEnd != null) {
			this.currentPeriodEnd = currentPeriodEnd;
		}
		if (providerCustomerId != null) {
			this.providerCustomerId = providerCustomerId;
		}
		if (providerSubscriptionId != null) {
			this.providerSubscriptionId = providerSubscriptionId;
		}
	}

	public UUID getId() {
		return id;
	}

	public Account getAccount() {
		return account;
	}

	public Price getPrice() {
		return price;
	}

	public Integer getSeatCount() {
		return seatCount;
	}

	public SubscriptionStatus getStatus() {
		return status;
	}

	public Instant getCurrentPeriodEnd() {
		return currentPeriodEnd;
	}

	public String getProvider() {
		return provider;
	}

	public String getProviderCustomerId() {
		return providerCustomerId;
	}

	public String getProviderSubscriptionId() {
		return providerSubscriptionId;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
