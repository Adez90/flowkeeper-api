package se.flowkeeper.api.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ConflictException;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

	@Mock EventRepository eventRepository;
	@Mock EventTypeRepository eventTypeRepository;
	@Mock AccountMemberRepository accountMemberRepository;
	@Mock CurrentUserResolver currentUserResolver;

	private final User user = new User("kc-subject-1", "Anders Johansson", "anders@example.com");
	private final Account account = new Account(AccountType.PERSONAL, "Anders Johansson");
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-subject-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private EventService service() {
		return new EventService(eventRepository, eventTypeRepository, accountMemberRepository, currentUserResolver);
	}

	@Test
	void createEventSavesOpenEventWhenUserIsAMember() {
		EventType type = defaultEventType();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventTypeRepository.findById(any())).thenReturn(Optional.of(type));
		when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

		EventResponse response = service().createEvent(jwt,
			new CreateEventRequest(UUID.randomUUID(), UUID.randomUUID(), (short) 3, "starting a call", null, null, null, null));

		assertThat(response.status()).isEqualTo("OPEN");
		assertThat(response.ingoingEnergy()).isEqualTo((short) 3);
	}

	@Test
	void createEventRejectsNonMember() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().createEvent(jwt,
			new CreateEventRequest(UUID.randomUUID(), UUID.randomUUID(), (short) 3, null, null, null, null, null)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void createEventAcceptsABackdatedStartTime() {
		EventType type = defaultEventType();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventTypeRepository.findById(any())).thenReturn(Optional.of(type));
		when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

		Instant yesterday = Instant.now().minusSeconds(86_400);
		EventResponse response = service().createEvent(jwt,
			new CreateEventRequest(UUID.randomUUID(), UUID.randomUUID(), (short) 3, null, yesterday, null, null, null));

		assertThat(response.status()).isEqualTo("OPEN");
		assertThat(response.startedAt()).isEqualTo(yesterday);
	}

	@Test
	void createEventRejectsAStartTimeInTheFuture() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventTypeRepository.findById(any())).thenReturn(Optional.of(defaultEventType()));

		Instant tomorrow = Instant.now().plusSeconds(86_400);
		assertThatThrownBy(() -> service().createEvent(jwt,
			new CreateEventRequest(UUID.randomUUID(), UUID.randomUUID(), (short) 3, null, tomorrow, null, null, null)))
			.isInstanceOf(se.flowkeeper.api.common.ValidationException.class);
	}

	@Test
	void createEventLogsAFullyHistoricalActivityAlreadyCompleted() {
		EventType type = defaultEventType();
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventTypeRepository.findById(any())).thenReturn(Optional.of(type));
		when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

		Instant start = Instant.now().minusSeconds(7200);
		Instant end = Instant.now().minusSeconds(3600);
		EventResponse response = service().createEvent(jwt,
			new CreateEventRequest(UUID.randomUUID(), UUID.randomUUID(), (short) 3, "felt rushed", start, (short) 4, "better after", end));

		assertThat(response.status()).isEqualTo("COMPLETED");
		assertThat(response.startedAt()).isEqualTo(start);
		assertThat(response.completedAt()).isEqualTo(end);
		assertThat(response.outgoingEnergy()).isEqualTo((short) 4);
		assertThat(response.outgoingNote()).isEqualTo("better after");
	}

	@Test
	void createEventRejectsACompletedTimeBeforeTheStartTime() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventTypeRepository.findById(any())).thenReturn(Optional.of(defaultEventType()));

		Instant start = Instant.now().minusSeconds(3600);
		Instant end = Instant.now().minusSeconds(7200); // before start
		assertThatThrownBy(() -> service().createEvent(jwt,
			new CreateEventRequest(UUID.randomUUID(), UUID.randomUUID(), (short) 3, null, start, (short) 4, null, end)))
			.isInstanceOf(se.flowkeeper.api.common.ValidationException.class);
	}

	@Test
	void createEventRejectsACompletedTimeWithoutOutgoingEnergy() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(accountMemberRepository.findByAccount_IdAndUser(any(), any()))
			.thenReturn(Optional.of(new AccountMember(account, user, MemberRole.OWNER)));
		when(eventTypeRepository.findById(any())).thenReturn(Optional.of(defaultEventType()));

		assertThatThrownBy(() -> service().createEvent(jwt,
			new CreateEventRequest(UUID.randomUUID(), UUID.randomUUID(), (short) 3, null, null, null, null, Instant.now())))
			.isInstanceOf(se.flowkeeper.api.common.ValidationException.class);
	}

	@Test
	void completeEventRejectsSomeoneElsesEvent() {
		User someoneElse = new User("kc-subject-2", "Other Person", "other@example.com");
		Event event = new Event(someoneElse, account, defaultEventType(), (short) 3, null);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(eventRepository.findById(any())).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> service().completeEvent(jwt, UUID.randomUUID(),
			new CompleteEventRequest((short) 4, "done")))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void completeEventRejectsAlreadyCompletedEvent() {
		Event event = new Event(user, account, defaultEventType(), (short) 3, null);
		event.complete((short) 4, "already done");

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(eventRepository.findById(any())).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> service().completeEvent(jwt, UUID.randomUUID(),
			new CompleteEventRequest((short) 4, "done again")))
			.isInstanceOf(ConflictException.class);
	}

	@Test
	void completeEventUnknownIdIsNotFound() {
		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(eventRepository.findById(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().completeEvent(jwt, UUID.randomUUID(),
			new CompleteEventRequest((short) 4, null)))
			.isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void updateSharingLetsTheOwnerOptTheirEventIn() {
		Event event = new Event(user, account, defaultEventType(), (short) 3, "note");

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(eventRepository.findById(any())).thenReturn(Optional.of(event));

		EventResponse response = service().updateSharing(jwt, UUID.randomUUID(), new UpdateEventSharingRequest(true));

		assertThat(response.shareAnonymously()).isTrue();
		assertThat(event.isShareAnonymously()).isTrue();
	}

	@Test
	void updateSharingRejectsSomeoneElsesEvent() {
		User someoneElse = new User("kc-subject-2", "Other Person", "other@example.com");
		Event event = new Event(someoneElse, account, defaultEventType(), (short) 3, null);

		when(currentUserResolver.require(jwt)).thenReturn(user);
		when(eventRepository.findById(any())).thenReturn(Optional.of(event));

		assertThatThrownBy(() -> service().updateSharing(jwt, UUID.randomUUID(), new UpdateEventSharingRequest(true)))
			.isInstanceOf(AccessDeniedException.class);
	}

	private EventType defaultEventType() {
		return org.mockito.Mockito.mock(EventType.class);
	}

}
