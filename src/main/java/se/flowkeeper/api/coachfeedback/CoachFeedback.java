package se.flowkeeper.api.coachfeedback;

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
import se.flowkeeper.api.event.Event;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.UUID;

/**
 * One supervisor's note about one specific member — either attached to a
 * particular event of that member's (event non-null) or a freeform,
 * periodic check-in note (event null). One direction only: written by
 * whoever supervises the member (their group's COACH, department's ADMIN,
 * or the org OWNER), read by the member and anyone who supervises them.
 */
@Entity
@Table(name = "coach_feedback")
public class CoachFeedback {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "coach_id", nullable = false)
	private User coach;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private User member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "event_id")
	private Event event;

	@Column(name = "note", nullable = false, length = 2000)
	private String note;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected CoachFeedback() {
	}

	public CoachFeedback(Account account, User coach, User member, Event event, String note) {
		this.account = account;
		this.coach = coach;
		this.member = member;
		this.event = event;
		this.note = note;
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

	public User getCoach() {
		return coach;
	}

	public User getMember() {
		return member;
	}

	public Event getEvent() {
		return event;
	}

	public String getNote() {
		return note;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
