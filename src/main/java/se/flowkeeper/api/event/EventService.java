package se.flowkeeper.api.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.common.ConflictException;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserTimezones;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class EventService {

	private static final Logger log = LoggerFactory.getLogger(EventService.class);

	private final EventRepository eventRepository;
	private final EventTypeRepository eventTypeRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final CurrentUserResolver currentUserResolver;
	private final UserTimezones userTimezones;

	public EventService(EventRepository eventRepository,
			EventTypeRepository eventTypeRepository,
			AccountMemberRepository accountMemberRepository,
			CurrentUserResolver currentUserResolver,
			UserTimezones userTimezones) {
		this.eventRepository = eventRepository;
		this.eventTypeRepository = eventTypeRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.currentUserResolver = currentUserResolver;
		this.userTimezones = userTimezones;
	}

	@Transactional
	public EventResponse createEvent(Jwt jwt, CreateEventRequest request) {
		User user = currentUserResolver.require(jwt);
		Account account = requireMembership(request.accountId(), user);

		EventType eventType = eventTypeRepository.findById(request.eventTypeId())
			.orElseThrow(() -> new ResourceNotFoundException("Unknown event type: " + request.eventTypeId()));
		if (eventType.getAccountId() != null && !eventType.getAccountId().equals(account.getId())) {
			throw new ResourceNotFoundException("Event type does not belong to this account: " + request.eventTypeId());
		}

		Instant now = Instant.now();
		Instant startedAt = request.startedAt() != null ? request.startedAt() : now;
		if (startedAt.isAfter(now)) {
			throw new ValidationException("startedAt cannot be in the future");
		}

		Event event = new Event(user, account, eventType, request.ingoingEnergy(), request.ingoingNote(), startedAt);

		if (request.outgoingEnergy() != null) {
			Instant completedAt = request.completedAt() != null ? request.completedAt() : now;
			if (completedAt.isAfter(now)) {
				throw new ValidationException("completedAt cannot be in the future");
			}
			if (completedAt.isBefore(startedAt)) {
				throw new ValidationException("completedAt cannot be before startedAt");
			}
			event.complete(request.outgoingEnergy(), request.outgoingNote(), completedAt);
		} else if (request.completedAt() != null) {
			throw new ValidationException("completedAt requires outgoingEnergy");
		}

		event = eventRepository.save(event);

		log.info("User {} logged event {} ({}) in account {}{}",
			user.getId(), event.getId(), eventType.getCode(), account.getId(),
			event.getStatus() == EventStatus.COMPLETED ? " (historical, already completed)" : "");

		return EventResponse.from(event);
	}

	@Transactional
	public EventResponse completeEvent(Jwt jwt, UUID eventId, CompleteEventRequest request) {
		User user = currentUserResolver.require(jwt);
		Event event = eventRepository.findById(eventId)
			.orElseThrow(() -> new ResourceNotFoundException("No such event: " + eventId));

		if (!event.getUser().equals(user)) {
			// Only the person who logged it can close it out for now — a
			// coach/admin editing someone else's event is a later concern
			// once the role model actually needs it.
			throw new AccessDeniedException("Not your event");
		}
		if (event.getStatus() == EventStatus.COMPLETED) {
			throw new ConflictException("Event %s is already completed".formatted(eventId));
		}
		if (event.getIngoingEnergy() == null) {
			throw new ConflictException("Event %s hasn't been started yet — set an ingoing energy first".formatted(eventId));
		}

		event.complete(request.outgoingEnergy(), request.outgoingNote());

		log.info("User {} completed event {}", user.getId(), event.getId());

		return EventResponse.from(event);
	}

	/**
	 * The first real interaction with an imported event — sets the ingoing
	 * reading a manually-created event already has from the moment it's
	 * logged. Only valid once, on an event that doesn't have one yet.
	 */
	@Transactional
	public EventResponse startEvent(Jwt jwt, UUID eventId, StartEventRequest request) {
		User user = currentUserResolver.require(jwt);
		Event event = eventRepository.findById(eventId)
			.orElseThrow(() -> new ResourceNotFoundException("No such event: " + eventId));

		if (!event.getUser().equals(user)) {
			throw new AccessDeniedException("Not your event");
		}
		if (event.getIngoingEnergy() != null) {
			throw new ConflictException("Event %s already has an ingoing energy set".formatted(eventId));
		}

		event.start(request.ingoingEnergy(), request.ingoingNote());

		log.info("User {} started imported event {}", user.getId(), event.getId());

		return EventResponse.from(event);
	}

	/** Removes a logged event outright — the "I don't want this one at all" case, distinct from editing it. The event's own owner only. */
	@Transactional
	public void deleteEvent(Jwt jwt, UUID eventId) {
		User user = currentUserResolver.require(jwt);
		Event event = eventRepository.findById(eventId)
			.orElseThrow(() -> new ResourceNotFoundException("No such event: " + eventId));

		if (!event.getUser().equals(user)) {
			throw new AccessDeniedException("Not your event");
		}

		eventRepository.delete(event);

		log.info("User {} deleted event {}", user.getId(), eventId);
	}

	/**
	 * Full correction of an already-completed event — the "I logged this
	 * wrong" case. Requires the event to already be COMPLETED (an OPEN event
	 * is finished via completeEvent instead) and, like completeEvent, only
	 * the event's own owner may do it.
	 */
	@Transactional
	public EventResponse editCompletedEvent(Jwt jwt, UUID eventId, UpdateEventRequest request) {
		User user = currentUserResolver.require(jwt);
		Event event = eventRepository.findById(eventId)
			.orElseThrow(() -> new ResourceNotFoundException("No such event: " + eventId));

		if (!event.getUser().equals(user)) {
			throw new AccessDeniedException("Not your event");
		}
		if (event.getStatus() != EventStatus.COMPLETED) {
			throw new ConflictException("Event %s is not completed yet".formatted(eventId));
		}

		EventType eventType = eventTypeRepository.findById(request.eventTypeId())
			.orElseThrow(() -> new ResourceNotFoundException("Unknown event type: " + request.eventTypeId()));
		if (eventType.getAccountId() != null && !eventType.getAccountId().equals(event.getAccount().getId())) {
			throw new ResourceNotFoundException("Event type does not belong to this account: " + request.eventTypeId());
		}

		Instant now = Instant.now();
		if (request.startedAt().isAfter(now)) {
			throw new ValidationException("startedAt cannot be in the future");
		}
		if (request.completedAt().isAfter(now)) {
			throw new ValidationException("completedAt cannot be in the future");
		}
		if (request.completedAt().isBefore(request.startedAt())) {
			throw new ValidationException("completedAt cannot be before startedAt");
		}

		event.edit(eventType, request.ingoingEnergy(), request.ingoingNote(), request.startedAt(),
			request.outgoingEnergy(), request.outgoingNote(), request.completedAt());

		log.info("User {} edited completed event {}", user.getId(), event.getId());

		return EventResponse.from(event);
	}

	/** The caller's own completed events in a date range (their own timezone) — backs the Completed list's edit screen. */
	@Transactional(readOnly = true)
	public List<EventResponse> listMyCompletedEvents(Jwt jwt, UUID accountId, LocalDate rangeStart, LocalDate rangeEndExclusive) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);

		ZoneId zone = userTimezones.resolve(user);
		Instant start = rangeStart.atStartOfDay(zone).toInstant();
		Instant end = rangeEndExclusive.atStartOfDay(zone).toInstant();

		List<Event> events = eventRepository.findByAccount_IdAndUser_IdAndStatusAndStartedAtBetweenOrderByStartedAtDesc(
			accountId, user.getId(), EventStatus.COMPLETED, start, end);

		return events.stream().map(EventResponse::from).toList();
	}

	/** Only the event's own owner can opt its notes in or out of anonymous organisation-wide feedback. */
	@Transactional
	public EventResponse updateSharing(Jwt jwt, UUID eventId, UpdateEventSharingRequest request) {
		User user = currentUserResolver.require(jwt);
		Event event = eventRepository.findById(eventId)
			.orElseThrow(() -> new ResourceNotFoundException("No such event: " + eventId));

		if (!event.getUser().equals(user)) {
			throw new AccessDeniedException("Not your event");
		}

		event.updateAnonymousSharing(request.shareAnonymously());
		log.info("User {} set share-anonymously={} on event {}", user.getId(), request.shareAnonymously(), eventId);

		return EventResponse.from(event);
	}

	@Transactional(readOnly = true)
	public List<EventResponse> listEvents(Jwt jwt, UUID accountId, EventStatus statusFilter) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);

		List<Event> events = statusFilter != null
			? eventRepository.findByAccount_IdAndStatusOrderByStartedAtDesc(accountId, statusFilter)
			: eventRepository.findByAccount_IdOrderByStartedAtDesc(accountId);

		return events.stream().map(EventResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<EventTypeResponse> listEventTypes(Jwt jwt, UUID accountId) {
		User user = currentUserResolver.require(jwt);
		requireMembership(accountId, user);

		return eventTypeRepository.findByAccountIdIsNullOrAccountId(accountId).stream()
			.map(EventTypeResponse::from)
			.toList();
	}

	private Account requireMembership(UUID accountId, User user) {
		return accountMemberRepository.findByAccount_IdAndUser(accountId, user)
			.map(AccountMember::getAccount)
			.orElseThrow(() -> new AccessDeniedException(
				"User %s is not a member of account %s".formatted(user.getId(), accountId)));
	}

}
