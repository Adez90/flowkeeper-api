package se.flowkeeper.api.event;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping("/api/v1/events")
	public ResponseEntity<EventResponse> create(Jwt jwt, @Valid @RequestBody CreateEventRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(jwt, request));
	}

	@PostMapping("/api/v1/events/{eventId}/complete")
	public EventResponse complete(Jwt jwt, @PathVariable UUID eventId, @Valid @RequestBody CompleteEventRequest request) {
		return eventService.completeEvent(jwt, eventId, request);
	}

	/** The landing page's "ongoing events" list — status defaults to OPEN. */
	@GetMapping("/api/v1/events")
	public List<EventResponse> list(
			Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam(required = false) EventStatus status) {
		return eventService.listEvents(jwt, accountId, status);
	}

	/** What the "create event" screen offers to choose from. */
	@GetMapping("/api/v1/event-types")
	public List<EventTypeResponse> listTypes(Jwt jwt, @RequestParam UUID accountId) {
		return eventService.listEventTypes(jwt, accountId);
	}

}
