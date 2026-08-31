package se.flowkeeper.api.event;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class EventController {

	private final EventService eventService;

	public EventController(EventService eventService) {
		this.eventService = eventService;
	}

	@PostMapping("/api/v1/events")
	public ResponseEntity<EventResponse> create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateEventRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(eventService.createEvent(jwt, request));
	}

	@PostMapping("/api/v1/events/{eventId}/complete")
	public EventResponse complete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @Valid @RequestBody CompleteEventRequest request) {
		return eventService.completeEvent(jwt, eventId, request);
	}

	/** Opt this event's notes in or out of anonymous organisation-wide feedback — the event's own owner only. */
	@PatchMapping("/api/v1/events/{eventId}/sharing")
	public EventResponse updateSharing(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @Valid @RequestBody UpdateEventSharingRequest request) {
		return eventService.updateSharing(jwt, eventId, request);
	}

	/** Full correction of an already-completed event — the "I logged this wrong" case. The event's own owner only. */
	@PatchMapping("/api/v1/events/{eventId}")
	public EventResponse edit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @Valid @RequestBody UpdateEventRequest request) {
		return eventService.editCompletedEvent(jwt, eventId, request);
	}

	/** The first interaction with an imported event — sets the ingoing energy a manually-created one already has from the start. */
	@PatchMapping("/api/v1/events/{eventId}/start")
	public EventResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId, @Valid @RequestBody StartEventRequest request) {
		return eventService.startEvent(jwt, eventId, request);
	}

	/** Removes a logged event outright. The event's own owner only. */
	@DeleteMapping("/api/v1/events/{eventId}")
	public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
		eventService.deleteEvent(jwt, eventId);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	/** The caller's own completed events in a date range (their own timezone) — backs the Completed list's edit screen. */
	@GetMapping("/api/v1/events/completed")
	public List<EventResponse> listMyCompleted(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeStart,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rangeEndExclusive) {
		return eventService.listMyCompletedEvents(jwt, accountId, rangeStart, rangeEndExclusive);
	}

	/** The landing page's "ongoing events" list — status defaults to OPEN. */
	@GetMapping("/api/v1/events")
	public List<EventResponse> list(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam(required = false) EventStatus status) {
		return eventService.listEvents(jwt, accountId, status);
	}

	/** What the "create event" screen offers to choose from. */
	@GetMapping("/api/v1/event-types")
	public List<EventTypeResponse> listTypes(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID accountId) {
		return eventService.listEventTypes(jwt, accountId);
	}

}
