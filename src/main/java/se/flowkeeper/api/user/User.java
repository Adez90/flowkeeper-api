package se.flowkeeper.api.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

	@Column(name = "timezone", nullable = false, length = 50)
	private String timezone;

	@Column(name = "locale", length = 10)
	private String locale;

	@Column(name = "avatar_url", length = 500)
	private String avatarUrl;

	@Column(name = "notify_in_app", nullable = false)
	private boolean notifyInApp;

	@Column(name = "notify_push", nullable = false)
	private boolean notifyPush;

	@Column(name = "notify_email", nullable = false)
	private boolean notifyEmail;

	@Column(name = "expo_push_token", length = 200)
	private String expoPushToken;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected User() {
	}

	public User(String keycloakSubject, String displayName, String email) {
		this.keycloakSubject = keycloakSubject;
		this.displayName = displayName;
		this.email = email;
		this.timezone = "UTC";
	}

	public void updateProfile(String displayName, String timezone, String locale, String avatarUrl) {
		this.displayName = displayName;
		this.timezone = timezone;
		this.locale = locale;
		this.avatarUrl = avatarUrl;
	}

	public void updateAvatarUrl(String avatarUrl) {
		this.avatarUrl = avatarUrl;
	}

	public void updateNotificationPreferences(boolean notifyInApp, boolean notifyPush, boolean notifyEmail) {
		this.notifyInApp = notifyInApp;
		this.notifyPush = notifyPush;
		this.notifyEmail = notifyEmail;
	}

	public void updateExpoPushToken(String expoPushToken) {
		this.expoPushToken = expoPushToken;
	}

	/** Whether any reminder-nudge channel is switched on at all — the cheap check the reminder jobs filter candidates with before doing per-user timezone work. */
	public boolean hasAnyNotificationChannelEnabled() {
		return notifyInApp || notifyPush || notifyEmail;
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

	public String getTimezone() {
		return timezone;
	}

	public String getLocale() {
		return locale;
	}

	public String getAvatarUrl() {
		return avatarUrl;
	}

	public boolean isNotifyInApp() {
		return notifyInApp;
	}

	public boolean isNotifyPush() {
		return notifyPush;
	}

	public boolean isNotifyEmail() {
		return notifyEmail;
	}

	public String getExpoPushToken() {
		return expoPushToken;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
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
