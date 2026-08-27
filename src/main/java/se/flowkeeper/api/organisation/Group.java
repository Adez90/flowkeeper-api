package se.flowkeeper.api.organisation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import se.flowkeeper.api.account.Account;

import java.time.Instant;
import java.util.UUID;

/**
 * Sits under a Department, or directly under an Organisation account when
 * there's no department layer — how a single coach (a small Organisation
 * with one Group) is represented without forcing an empty Department in
 * between. Maps onto the `groups` table created in V1__init_schema.sql,
 * unused by any Java code until now.
 */
@Entity
@Table(name = "groups")
public class Group {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "department_id")
	private Department department;

	@Column(name = "name", nullable = false, length = 200)
	private String name;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "share_flow_with_peers", nullable = false)
	private boolean shareFlowWithPeers = false;

	protected Group() {
	}

	public Group(Account account, Department department, String name) {
		this.account = account;
		this.department = department;
		this.name = name;
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

	public Department getDepartment() {
		return department;
	}

	public String getName() {
		return name;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public boolean isShareFlowWithPeers() {
		return shareFlowWithPeers;
	}

	/** Only this group's own manager (COACH) makes this call. */
	public void updateSharePreference(boolean shareFlowWithPeers) {
		this.shareFlowWithPeers = shareFlowWithPeers;
	}

}
