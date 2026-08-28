package se.flowkeeper.api.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** Personal or Business — the top of the pricing catalog; seeded by migration, not created at runtime. */
@Entity
@Table(name = "plans")
public class Plan {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "code", nullable = false, length = 50, unique = true)
	private String code;

	@Enumerated(EnumType.STRING)
	@Column(name = "scope", nullable = false, length = 20)
	private PlanScope scope;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Plan() {
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public PlanScope getScope() {
		return scope;
	}

	public String getName() {
		return name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
