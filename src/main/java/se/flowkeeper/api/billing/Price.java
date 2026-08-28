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
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One purchasable option on a Plan: a period, whether it's a one-time
 * prepay or a recurring subscription, and — for Business — whether
 * amountMinorUnits is a flat account price or a per-seat price
 * multiplied by seat count at checkout. Seeded by migration with
 * placeholder amounts; not created at runtime.
 */
@Entity
@Table(name = "prices")
public class Price {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "plan_id", nullable = false)
	private Plan plan;

	@Enumerated(EnumType.STRING)
	@Column(name = "period", nullable = false, length = 20)
	private BillingPeriod period;

	@Enumerated(EnumType.STRING)
	@Column(name = "billing_type", nullable = false, length = 20)
	private BillingType billingType;

	@Column(name = "per_seat", nullable = false)
	private boolean perSeat;

	@Column(name = "amount_minor_units", nullable = false)
	private long amountMinorUnits;

	@Column(name = "currency", nullable = false, length = 3)
	private String currency;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Price() {
	}

	public UUID getId() {
		return id;
	}

	public Plan getPlan() {
		return plan;
	}

	public BillingPeriod getPeriod() {
		return period;
	}

	public BillingType getBillingType() {
		return billingType;
	}

	public boolean isPerSeat() {
		return perSeat;
	}

	public long getAmountMinorUnits() {
		return amountMinorUnits;
	}

	public String getCurrency() {
		return currency;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
