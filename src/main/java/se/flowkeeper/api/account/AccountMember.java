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
import se.flowkeeper.api.organisation.Department;
import se.flowkeeper.api.organisation.Group;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * Which account a user belongs to, their role within it, and — for
 * Organisation accounts — which Department/Group scopes that to.
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

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "group_id")
	private Group group;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false, length = 20)
	private MemberRole role;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AccountMember() {
	}

	/** A Personal account's own OWNER membership, or an Organisation-level
	 *  membership with no Department/Group scope (e.g. an ADMIN who manages
	 *  the whole org rather than one group). */
	public AccountMember(Account account, User user, MemberRole role) {
		this(account, user, role, null, null);
	}

	public AccountMember(Account account, User user, MemberRole role, Department department, Group group) {
		this.account = account;
		this.user = user;
		this.role = role;
		this.department = department;
		this.group = group;
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

	public Department getDepartment() {
		return department;
	}

	public Group getGroup() {
		return group;
	}

	public MemberRole getRole() {
		return role;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
