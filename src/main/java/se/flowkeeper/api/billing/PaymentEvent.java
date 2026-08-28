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
 * A raw webhook delivery from the payment provider, kept for audit and to
 * make processing idempotent — the unique (provider, providerEventId)
 * pair lets BillingService recognise (and skip) a redelivered event.
 */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "account_id")
	private UUID accountId;

	@Column(name = "provider", nullable = false, length = 20)
	private String provider;

	@Column(name = "provider_event_id", nullable = false)
	private String providerEventId;

	@Column(name = "type", nullable = false, length = 100)
	private String type;

	@Column(name = "payload", nullable = false)
	private String payload;

	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	protected PaymentEvent() {
	}

	public PaymentEvent(UUID accountId, String provider, String providerEventId, String type, String payload) {
		this.accountId = accountId;
		this.provider = provider;
		this.providerEventId = providerEventId;
		this.type = type;
		this.payload = payload;
	}

	@PrePersist
	void onCreate() {
		if (receivedAt == null) {
			receivedAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public String getProvider() {
		return provider;
	}

	public String getProviderEventId() {
		return providerEventId;
	}

	public String getType() {
		return type;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

}
