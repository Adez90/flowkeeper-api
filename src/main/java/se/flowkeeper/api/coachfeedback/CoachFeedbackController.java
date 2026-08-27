package se.flowkeeper.api.coachfeedback;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations/{accountId}/members/{memberId}/feedback")
public class CoachFeedbackController {

	private final CoachFeedbackService coachFeedbackService;

	public CoachFeedbackController(CoachFeedbackService coachFeedbackService) {
		this.coachFeedbackService = coachFeedbackService;
	}

	/** Only whoever supervises this member (their group's COACH, department's ADMIN, or the OWNER) can leave feedback for them. */
	@PostMapping
	public ResponseEntity<CoachFeedbackResponse> create(
			@AuthenticationPrincipal Jwt jwt,
			@PathVariable UUID accountId,
			@PathVariable UUID memberId,
			@Valid @RequestBody CreateCoachFeedbackRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(coachFeedbackService.create(jwt, accountId, memberId, request));
	}

	/** Newest first. Visible to the member themselves, and to whoever supervises them. */
	@GetMapping
	public List<CoachFeedbackResponse> list(
			@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @PathVariable UUID memberId) {
		return coachFeedbackService.list(jwt, accountId, memberId);
	}

}
