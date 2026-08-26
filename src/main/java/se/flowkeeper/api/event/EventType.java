package se.flowkeeper.api.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * accountId null = one of the seeded global defaults, available to every
 * account. Non-null = an account's own custom type.
 */
@Entity
@Table(name = "event_types")
public class EventType {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "account_id")
	private UUID accountId;

	@Column(name = "code", nullable = false, length = 50)
	private String code;

	@Column(name = "label", nullable = false, length = 100)
	private String label;

	@Column(name = "icon", length = 50)
	private String icon;

	@Column(name = "is_default", nullable = false)
	private boolean isDefault;

	protected EventType() {
	}

	public UUID getId() {
		return id;
	}

	public UUID getAccountId() {
		return accountId;
	}

	public String getCode() {
		return code;
	}

	public String getLabel() {
		return label;
	}

	public String getIcon() {
		return icon;
	}

	public boolean isDefault() {
		return isDefault;
	}

}
