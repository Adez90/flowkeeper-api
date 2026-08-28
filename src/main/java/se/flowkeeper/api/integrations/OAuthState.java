package se.flowkeeper.api.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.user.User;

import java.time.Instant;

/**
 * Issued when a user starts an OAuth authorization, consumed (single use)
 * by the callback. The callback carries no bearer token — just whatever
 * the provider redirects back with — so this state row is what ties that
 * request back to a real user/account instead of trusting the request itself.
 */
@Entity
@Table(name = "oauth_states")
public class OAuthState {

	@Id
	@Column(name = "state", length = 64)
	private String state;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@Enumerated(EnumType.STRING)
	@Column(name = "provider", nullable = false, length = 30)
	private ExternalProvider provider;

	@Column(name = "redirect_uri", nullable = false)
	private String redirectUri;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	protected OAuthState() {
	}

	public OAuthState(String state, User user, Account account, ExternalProvider provider, String redirectUri, Instant expiresAt) {
		this.state = state;
		this.user = user;
		this.account = account;
		this.provider = provider;
		this.redirectUri = redirectUri;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean isExpired(Instant now) {
		return now.isAfter(expiresAt);
	}

	public String getState() {
		return state;
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

	public String getRedirectUri() {
		return redirectUri;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

}
