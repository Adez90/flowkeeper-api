package se.flowkeeper.api.user;

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
 * A profile keyed by the Keycloak subject that authenticated it. Identity
 * (credentials, MFA, email verification) is owned entirely by Keycloak —
 * this row never holds a password.
 */
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "keycloak_subject", nullable = false, unique = true, length = 64)
	private String keycloakSubject;

	@Column(name = "display_name", nullable = false, length = 200)
	private String displayName;

	@Column(name = "email", nullable = false, length = 320)
	private String email;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	public User(String keycloakSubject, String displayName, String email) {
		this.keycloakSubject = keycloakSubject;
		this.displayName = displayName;
		this.email = email;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public UUID getId() {
		return id;
	}

	public String getKeycloakSubject() {
		return keycloakSubject;
	}

	public String getDisplayName() {
		return displayName;
	}

	public String getEmail() {
		return email;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	// Equality on the natural key (keycloakSubject), not the generated id —
	// the id is null until Hibernate assigns it on persist, which would
	// otherwise make any not-yet-persisted or detached instance compare
	// unequal to everything, itself included.
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof User other)) {
			return false;
		}
		return keycloakSubject != null && keycloakSubject.equals(other.keycloakSubject);
	}

	@Override
	public int hashCode() {
		return getClass().hashCode();
	}

}
