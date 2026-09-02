package se.flowkeeper.api.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import se.flowkeeper.api.integrations.ExternalProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	List<Event> findByAccount_IdAndStatusOrderByStartedAtDesc(UUID accountId, EventStatus status);

	List<Event> findByAccount_IdOrderByStartedAtDesc(UUID accountId);

	/** One specific member's own events in one account — used by the coach-feedback event picker, not the general account-wide listing above. */
	List<Event> findByAccount_IdAndUser_IdOrderByStartedAtDesc(UUID accountId, UUID userId);

	/** The caller's own completed events in a date range (their timezone) — backs the Completed list's edit screen. */
	List<Event> findByAccount_IdAndUser_IdAndStatusAndStartedAtBetweenOrderByStartedAtDesc(
		UUID accountId, UUID userId, EventStatus status, Instant startedAtFrom, Instant startedAtToExclusive);

	/** Does this user have an event they haven't completed yet — the unfinished-event reminder's trigger condition. */
	boolean existsByUser_IdAndStatus(UUID userId, EventStatus status);

	/** Has this user started any event in the given window — the unused-account reminder checks this against "today" in the user's own timezone. */
	boolean existsByUser_IdAndStartedAtBetween(UUID userId, Instant start, Instant end);

	/**
	 * Which of this provider's items this user has already imported — what
	 * the importable list filters out so nothing is offered twice.
	 * Needs an explicit projection: derived-query naming ("findExternalIdBy...")
	 * doesn't actually select just that column — without @Query, Spring Data
	 * runs "SELECT e FROM Event e WHERE ..." regardless of the method name's
	 * leading words, returning full Event entities that then fail to convert
	 * to List<String> as soon as there's at least one match (an empty result
	 * needs no per-element conversion, which is why this went unnoticed until
	 * someone had a previously-imported item on file for a given provider).
	 */
	@Query("SELECT e.externalId FROM Event e WHERE e.user.id = :userId AND e.externalProvider = :externalProvider")
	List<String> findExternalIdByUser_IdAndExternalProvider(@Param("userId") UUID userId, @Param("externalProvider") ExternalProvider externalProvider);

}
