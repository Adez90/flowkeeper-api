package se.flowkeeper.api.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	List<Event> findByAccount_IdAndStatusOrderByStartedAtDesc(UUID accountId, EventStatus status);

	List<Event> findByAccount_IdOrderByStartedAtDesc(UUID accountId);

	/** One specific member's own events in one account — used by the coach-feedback event picker, not the general account-wide listing above. */
	List<Event> findByAccount_IdAndUser_IdOrderByStartedAtDesc(UUID accountId, UUID userId);

	/** Does this user have an event they haven't completed yet — the unfinished-event reminder's trigger condition. */
	boolean existsByUser_IdAndStatus(UUID userId, EventStatus status);

	/** Has this user started any event in the given window — the unused-account reminder checks this against "today" in the user's own timezone. */
	boolean existsByUser_IdAndStartedAtBetween(UUID userId, Instant start, Instant end);

}
