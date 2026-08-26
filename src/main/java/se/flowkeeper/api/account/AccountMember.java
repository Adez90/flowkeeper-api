package se.flowkeeper.api.account;

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
import jakarta.persistence.Table;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Which account a user belongs to, and their role within it. department_id
 * and group_id are kept as raw ids rather than relations for now — mapped
 * to real Department/Group entities once those domain modules exist.
 */
@Entity
@Table(name = "account_members")
public class AccountMember {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "department_id")
	private UUID departmentId;

	@Column(name = "group_id")
	private UUID groupId;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private MemberRole role;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AccountMember() {
	}

	public AccountMember(Account account, User user, MemberRole role) {
		this.account = account;
		this.user = user;
		this.role = role;
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

	public Account getAccount() {
		return account;
	}

	public User getUser() {
		return user;
	}

	public MemberRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
