package se.flowkeeper.api.event;

import se.flowkeeper.api.integrations.ExternalProvider;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
	UUID id,
	UUID accountId,
	UUID eventTypeId,
	String eventTypeLabel,
	String status,
	/** Null only for an imported event nobody has started yet — see Event#start. */
	Short ingoingEnergy,
	String ingoingNote,
	Short outgoingEnergy,
	String outgoingNote,
	boolean shareIngoingNoteAnonymously,
	boolean shareOutgoingNoteAnonymously,
	Instant startedAt,
	Instant completedAt,
	/** Set only for an event brought in from a connected provider. */
	ExternalProvider externalProvider,
	/** The provider's own end time — offered as the default when finalizing, never applied on its own. */
	Instant externalEndedAt
) {

	public static EventResponse from(Event event) {
		return new EventResponse(
			event.getId(),
			event.getAccount().getId(),
			event.getEventType().getId(),
			event.getEventType().getLabel(),
			event.getStatus().name(),
			event.getIngoingEnergy(),
			event.getIngoingNote(),
			event.getOutgoingEnergy(),
			event.getOutgoingNote(),
			event.isShareIngoingNoteAnonymously(),
			event.isShareOutgoingNoteAnonymously(),
			event.getStartedAt(),
			event.getCompletedAt(),
			event.getExternalProvider(),
			event.getExternalEndedAt()
		);
	}

}
