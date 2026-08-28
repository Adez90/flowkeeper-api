package se.flowkeeper.api.event;

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
 * One activity, logged with an ingoing energy reading and optionally closed
 * out later with an outcome. This single shape replaces what the legacy
 * app modelled as a separate table+servlet per assessment type.
 */
@Entity
@Table(name = "events")
public class Event {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_type_id", nullable = false)
	private EventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private EventStatus status;

	@Column(name = "ingoing_energy", nullable = false)
	private short ingoingEnergy;

	@Column(name = "ingoing_note")
	private String ingoingNote;

	@Column(name = "outgoing_energy")
	private Short outgoingEnergy;

	@Column(name = "outgoing_note")
	private String outgoingNote;

	@Column(name = "share_anonymously", nullable = false)
	private boolean shareAnonymously;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Event() {
	}

	public Event(User user, Account account, EventType eventType, short ingoingEnergy, String ingoingNote) {
		this(user, account, eventType, ingoingEnergy, ingoingNote, Instant.now());
	}

	/** Same as the 5-arg constructor, but with an explicit startedAt — how a historical (backdated) activity is logged. */
	public Event(User user, Account account, EventType eventType, short ingoingEnergy, String ingoingNote, Instant startedAt) {
		this.user = user;
		this.account = account;
		this.eventType = eventType;
		this.status = EventStatus.OPEN;
		this.ingoingEnergy = ingoingEnergy;
		this.ingoingNote = ingoingNote;
		this.startedAt = startedAt;
	}

	public void complete(short outgoingEnergy, String outgoingNote) {
		complete(outgoingEnergy, outgoingNote, Instant.now());
	}

	/** Same as the 2-arg overload, but with an explicit completedAt — how a historical activity is marked done at the same time it's logged. */
	public void complete(short outgoingEnergy, String outgoingNote, Instant completedAt) {
		this.outgoingEnergy = outgoingEnergy;
		this.outgoingNote = outgoingNote;
		this.completedAt = completedAt;
		this.status = EventStatus.COMPLETED;
	}

	/** Only the event's own owner may opt its notes in or out of anonymous organisation-wide feedback — see AnonymousFeedbackService. */
	public void updateAnonymousSharing(boolean shareAnonymously) {
		this.shareAnonymously = shareAnonymously;
	}

	/**
	 * Full correction of an already-completed event — the "I logged this
	 * wrong" case. Every field is replaceable, including which activity type
	 * it was; status stays COMPLETED (this never reopens an event, see
	 * EventService.editCompletedEvent).
	 */
	public void edit(EventType eventType, short ingoingEnergy, String ingoingNote, Instant startedAt,
			short outgoingEnergy, String outgoingNote, Instant completedAt) {
		this.eventType = eventType;
		this.ingoingEnergy = ingoingEnergy;
		this.ingoingNote = ingoingNote;
		this.startedAt = startedAt;
		this.outgoingEnergy = outgoingEnergy;
		this.outgoingNote = outgoingNote;
		this.completedAt = completedAt;
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

	public User getUser() {
		return user;
	}

	public Account getAccount() {
		return account;
	}

	public EventType getEventType() {
		return eventType;
	}

	public EventStatus getStatus() {
		return status;
	}

	public short getIngoingEnergy() {
		return ingoingEnergy;
	}

	public String getIngoingNote() {
		return ingoingNote;
	}

	public Short getOutgoingEnergy() {
		return outgoingEnergy;
	}

	public String getOutgoingNote() {
		return outgoingNote;
	}

	public boolean isShareAnonymously() {
		return shareAnonymously;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

}
