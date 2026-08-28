package se.flowkeeper.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A trial code a platform admin generates and an account's OWNER later
 * redeems for N days of full access — see BillingService#redeemPromoCode.
 * maxRedemptions=1 for a single person; a higher cap lets one code cover
 * a company's several accounts.
 */
@Entity
@Table(name = "promo_codes")
public class PromoCode {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "code", nullable = false, unique = true, length = 32)
	private String code;

	@Column(name = "duration_days", nullable = false)
	private int durationDays;

	@Column(name = "max_redemptions", nullable = false)
	private int maxRedemptions;

	@Column(name = "redemption_count", nullable = false)
	private int redemptionCount;

	/** Null = no redeem-by deadline, just capped by maxRedemptions. */
	@Column(name = "expires_at")
	private Instant expiresAt;

	/** The admin's own label for what this code was for, e.g. "Acme AB pilot". */
	@Column(name = "note", length = 500)
	private String note;

	@Column(name = "created_by_email", nullable = false, length = 320)
	private String createdByEmail;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** Null = active; set to invalidate a code early, independent of expiresAt/maxRedemptions. */
	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected PromoCode() {
	}

	public PromoCode(String code, int durationDays, int maxRedemptions, Instant expiresAt, String note, String createdByEmail) {
		this.code = code;
		this.durationDays = durationDays;
		this.maxRedemptions = maxRedemptions;
		this.expiresAt = expiresAt;
		this.note = note;
		this.createdByEmail = createdByEmail;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean isRedeemable(Instant now) {
		return revokedAt == null
			&& redemptionCount < maxRedemptions
			&& (expiresAt == null || !now.isAfter(expiresAt));
	}

	public void recordRedemption() {
		this.redemptionCount++;
	}

	public void revoke() {
		this.revokedAt = Instant.now();
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public int getDurationDays() {
		return durationDays;
	}

	public int getMaxRedemptions() {
		return maxRedemptions;
	}

	public int getRedemptionCount() {
		return redemptionCount;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public String getNote() {
		return note;
	}

	public String getCreatedByEmail() {
		return createdByEmail;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

}
