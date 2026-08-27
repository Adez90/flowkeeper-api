package se.flowkeeper.api.event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
	UUID id,
	UUID accountId,
	UUID eventTypeId,
	String eventTypeLabel,
	String status,
	short ingoingEnergy,
	String ingoingNote,
	Short outgoingEnergy,
	String outgoingNote,
	boolean shareAnonymously,
	Instant startedAt,
	Instant completedAt
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
			event.isShareAnonymously(),
			event.getStartedAt(),
			event.getCompletedAt()
		);
	}

}
