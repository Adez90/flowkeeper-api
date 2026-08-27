package se.flowkeeper.api.coachfeedback;

import java.time.Instant;
import java.util.UUID;

public record CoachFeedbackResponse(
	UUID id,
	UUID coachId,
	String coachDisplayName,
	/** Null for a freeform note not attached to any specific event. */
	UUID eventId,
	String eventTypeLabel,
	String note,
	Instant createdAt
) {

	public static CoachFeedbackResponse from(CoachFeedback feedback) {
		return new CoachFeedbackResponse(
			feedback.getId(),
			feedback.getCoach().getId(),
			feedback.getCoach().getDisplayName(),
			feedback.getEvent() != null ? feedback.getEvent().getId() : null,
			feedback.getEvent() != null ? feedback.getEvent().getEventType().getLabel() : null,
			feedback.getNote(),
			feedback.getCreatedAt());
	}

}
