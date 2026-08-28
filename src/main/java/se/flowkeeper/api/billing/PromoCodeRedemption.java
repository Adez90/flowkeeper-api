package se.flowkeeper.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.UUID;

/** One account's redemption of one promo code — the unique (promoCode, account) pair blocks redeeming the same code twice. */
@Entity
@Table(name = "promo_code_redemptions")
public class PromoCodeRedemption {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "promo_code_id", nullable = false)
	private PromoCode promoCode;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "redeemed_by", nullable = false)
	private User redeemedBy;

	@Column(name = "redeemed_at", nullable = false, updatable = false)
	private Instant redeemedAt;

	protected PromoCodeRedemption() {
	}

	public PromoCodeRedemption(PromoCode promoCode, Account account, User redeemedBy) {
		this.promoCode = promoCode;
		this.account = account;
		this.redeemedBy = redeemedBy;
	}

	@PrePersist
	void onCreate() {
		if (redeemedAt == null) {
			redeemedAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public PromoCode getPromoCode() {
		return promoCode;
	}

	public Account getAccount() {
		return account;
	}

	public User getRedeemedBy() {
		return redeemedBy;
	}

	public Instant getRedeemedAt() {
		return redeemedAt;
	}

}
