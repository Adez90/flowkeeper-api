package se.flowkeeper.api.coachfeedback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Omit eventId for a freeform, periodic check-in note not tied to any one activity. */
public record CreateCoachFeedbackRequest(@NotBlank @Size(max = 2000) String note, UUID eventId) {
}
