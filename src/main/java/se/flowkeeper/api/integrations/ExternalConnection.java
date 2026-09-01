package se.flowkeeper.api.integrations;

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
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * One user's connection to one external provider, feeding events into one
 * account. Pulling the provider's actual events/activities into the
 * events table is a later phase — this only tracks the connection and
 * its tokens.
 */
@Entity
@Table(name = "external_connections")
public class ExternalConnection {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 30)
	private ExternalProvider provider;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ConnectionStatus status;

	@Column(name = "external_account_label", length = 320)
	private String externalAccountLabel;

	/** Plain text for now — see the migration's own note on encrypting these before this ever handles a real token. */
	@Column(name = "access_token")
	private String accessToken;

	@Column(name = "refresh_token")
	private String refreshToken;

	@Column(name = "token_expires_at")
	private Instant tokenExpiresAt;

	@Column(name = "last_synced_at")
	private Instant lastSyncedAt;

	@Column(name = "last_error")
	private String lastError;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ExternalConnection() {
	}

	public ExternalConnection(User user, Account account, ExternalProvider provider) {
		this.user = user;
		this.account = account;
		this.provider = provider;
		this.status = ConnectionStatus.CONNECTED;
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

	public void applyTokens(OAuthTokenResult tokens) {
		this.status = ConnectionStatus.CONNECTED;
		this.accessToken = tokens.accessToken();
		this.refreshToken = tokens.refreshToken();
		this.tokenExpiresAt = tokens.expiresAt();
		this.externalAccountLabel = tokens.externalAccountLabel();
		this.lastError = null;
	}

	/**
	 * Same as {@link #applyTokens}, but for a token refresh rather than a
	 * fresh authorization: the label is left as-is (refresh responses don't
	 * carry one), and the refresh token itself is only replaced when the
	 * provider actually issued a new one — Google reuses the original
	 * indefinitely, Strava and Microsoft rotate it on every refresh.
	 */
	public void applyRefreshedTokens(OAuthTokenResult tokens) {
		this.status = ConnectionStatus.CONNECTED;
		this.accessToken = tokens.accessToken();
		if (tokens.refreshToken() != null) {
			this.refreshToken = tokens.refreshToken();
		}
		this.tokenExpiresAt = tokens.expiresAt();
		this.lastError = null;
	}

	public void markError(String message) {
		this.status = ConnectionStatus.ERROR;
		this.lastError = message;
	}

	/** A provider call succeeded after a previous failure had left this in ERROR — the connection has recovered. */
	public void clearError() {
		this.status = ConnectionStatus.CONNECTED;
		this.lastError = null;
	}

	public void disconnect() {
		this.status = ConnectionStatus.DISCONNECTED;
		this.accessToken = null;
		this.refreshToken = null;
	}

	public UUID getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Account getAccount() {
		return account;
	}

	public ExternalProvider getProvider() {
		return provider;
	}

	public ConnectionStatus getStatus() {
		return status;
	}

	public String getExternalAccountLabel() {
		return externalAccountLabel;
	}

	public String getAccessToken() {
		return accessToken;
	}

	public String getRefreshToken() {
		return refreshToken;
	}

	public Instant getTokenExpiresAt() {
		return tokenExpiresAt;
	}

	public Instant getLastSyncedAt() {
		return lastSyncedAt;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
