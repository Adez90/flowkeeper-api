package se.flowkeeper.api.statistics;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import se.flowkeeper.api.event.Event;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Read-only reporting queries over Event — deliberately not a JpaRepository,
 * since this has no business exposing CRUD methods for a domain it doesn't
 * own.
 */
public interface EventStatisticsRepository extends Repository<Event, UUID> {

	// "In flow" — completed with ingoing+outgoing energy summing to 4-6 —
	// is the same formula the old FlowKeeper apps used for "Flow %", recovered
	// from oldflowkeeper/ResultHandler.java. Our 1-5 energy scale matches the
	// old app's scale, so the band applies unchanged.
	//
	// Scoped to a single userId, not just the account: an organisation
	// account can hold many members' events, and "personal" statistics must
	// only ever be that one caller's own numbers, never the whole account's.
	@Query("""
		select new se.flowkeeper.api.statistics.OverallCounts(
			count(e),
			sum(case when e.status = se.flowkeeper.api.event.EventStatus.COMPLETED then 1L else 0L end),
			sum(case when e.status = se.flowkeeper.api.event.EventStatus.COMPLETED
				and (e.ingoingEnergy + e.outgoingEnergy) between 4 and 6 then 1L else 0L end),
			avg(e.ingoingEnergy),
			avg(e.outgoingEnergy - e.ingoingEnergy)
		)
		from Event e
		where e.account.id = :accountId and e.user.id = :userId and e.startedAt >= :start and e.startedAt < :end
		""")
	OverallCounts aggregateOverall(
		@Param("accountId") UUID accountId, @Param("userId") UUID userId,
		@Param("start") Instant start, @Param("end") Instant end);

	// Same shape as aggregateOverall, but scoped to a specific set of users
	// rather than every member of the account — how a group/department/org
	// rollup is computed (see StatisticsService's group/department/org
	// statistics methods). Only ever called with a non-empty userIds once
	// the caller's already confirmed the minimum-size-for-privacy threshold
	// is met, so an empty IN clause is never actually reached.
	@Query("""
		select new se.flowkeeper.api.statistics.OverallCounts(
			count(e),
			sum(case when e.status = se.flowkeeper.api.event.EventStatus.COMPLETED then 1L else 0L end),
			sum(case when e.status = se.flowkeeper.api.event.EventStatus.COMPLETED
				and (e.ingoingEnergy + e.outgoingEnergy) between 4 and 6 then 1L else 0L end),
			avg(e.ingoingEnergy),
			avg(e.outgoingEnergy - e.ingoingEnergy)
		)
		from Event e
		where e.account.id = :accountId and e.user.id in :userIds and e.startedAt >= :start and e.startedAt < :end
		""")
	OverallCounts aggregateOverallForUsers(
		@Param("accountId") UUID accountId, @Param("userIds") Collection<UUID> userIds,
		@Param("start") Instant start, @Param("end") Instant end);

	// Scoped to a single userId — same reason as aggregateOverall above.
	@Query("""
		select new se.flowkeeper.api.statistics.TypeCounts(
			et.id, et.label, count(e), avg(e.outgoingEnergy - e.ingoingEnergy)
		)
		from Event e join e.eventType et
		where e.account.id = :accountId and e.user.id = :userId and e.startedAt >= :start and e.startedAt < :end
		group by et.id, et.label
		order by count(e) desc
		""")
	List<TypeCounts> aggregateByType(
		@Param("accountId") UUID accountId, @Param("userId") UUID userId,
		@Param("start") Instant start, @Param("end") Instant end);

	// Same shape as aggregateByType, scoped to a set of users — how the
	// organisation-wide anonymous by-type breakdown is computed (see
	// StatisticsService#organisationTypeStatistics). "Anonymous" here means
	// no single user's numbers are ever returned on their own — only counts
	// grouped by event type across everyone in scope — which is also why
	// this is gated at a much higher minimum-size threshold than the other
	// aggregates: a type with only one or two events in it can still narrow
	// down who logged it even when the account itself is large.
	@Query("""
		select new se.flowkeeper.api.statistics.TypeCounts(
			et.id, et.label, count(e), avg(e.outgoingEnergy - e.ingoingEnergy)
		)
		from Event e join e.eventType et
		where e.account.id = :accountId and e.user.id in :userIds and e.startedAt >= :start and e.startedAt < :end
		group by et.id, et.label
		order by count(e) desc
		""")
	List<TypeCounts> aggregateByTypeForUsers(
		@Param("accountId") UUID accountId, @Param("userIds") Collection<UUID> userIds,
		@Param("start") Instant start, @Param("end") Instant end);

	// One query for the whole trend range rather than one per day: fetch the
	// minimal per-event fields needed and bucket by local day in Java (see
	// StatisticsService#bucketByDay), since JPQL/Postgres date_trunc can't be
	// parameterized by a per-user IANA zone the way a repeated Instant-range
	// query already handles elsewhere in this class.
	// Scoped to a single userId — same reason as aggregateOverall above.
	@Query("""
		select new se.flowkeeper.api.statistics.TrendRow(e.startedAt, e.status, e.ingoingEnergy, e.outgoingEnergy)
		from Event e
		where e.account.id = :accountId and e.user.id = :userId and e.startedAt >= :start and e.startedAt < :end
		""")
	List<TrendRow> findTrendRows(
		@Param("accountId") UUID accountId, @Param("userId") UUID userId,
		@Param("start") Instant start, @Param("end") Instant end);

	// Per-member counts for a small, already-authorized set of users (a
	// group's peer-sharing view) — same shape as aggregateOverallForUsers but
	// grouped by user instead of pooled, since each opted-in member's own
	// number is shown individually here rather than rolled into one figure.
	@Query("""
		select new se.flowkeeper.api.statistics.MemberFlowRow(
			e.user.id,
			sum(case when e.status = se.flowkeeper.api.event.EventStatus.COMPLETED then 1L else 0L end),
			sum(case when e.status = se.flowkeeper.api.event.EventStatus.COMPLETED
				and (e.ingoingEnergy + e.outgoingEnergy) between 4 and 6 then 1L else 0L end)
		)
		from Event e
		where e.account.id = :accountId and e.user.id in :userIds and e.startedAt >= :start and e.startedAt < :end
		group by e.user.id
		""")
	List<MemberFlowRow> aggregateOverallPerUser(
		@Param("accountId") UUID accountId, @Param("userIds") Collection<UUID> userIds,
		@Param("start") Instant start, @Param("end") Instant end);

	// Same shape as findTrendRows, scoped to a specific set of users — how a
	// group/department/organisation trend is computed. Only ever called once
	// the caller's already confirmed the minimum-size-for-privacy threshold
	// is met, same guarantee as aggregateOverallForUsers.
	@Query("""
		select new se.flowkeeper.api.statistics.TrendRow(e.startedAt, e.status, e.ingoingEnergy, e.outgoingEnergy)
		from Event e
		where e.account.id = :accountId and e.user.id in :userIds and e.startedAt >= :start and e.startedAt < :end
		""")
	List<TrendRow> findTrendRowsForUsers(
		@Param("accountId") UUID accountId, @Param("userIds") Collection<UUID> userIds,
		@Param("start") Instant start, @Param("end") Instant end);

	// Every event its own owner has opted at least one note in to anonymous
	// organisation-wide feedback (Event.shareIngoingNoteAnonymously /
	// shareOutgoingNoteAnonymously — independent per note) — deliberately
	// selects no user/event id, only the type label, whichever note(s) were
	// actually opted in, and when it happened. A note not opted in comes
	// back null rather than the row being split or omitted, so a "shared
	// only the post-note" item still shows its type/time with just the one
	// note. Not time-boxed by period like the other aggregates: feedback is
	// a standing "what's working, what's not" view, not a per-day/week/month
	// rollup.
	@Query("""
		select new se.flowkeeper.api.statistics.AnonymousFeedbackItem(
			et.label,
			case when e.shareIngoingNoteAnonymously = true then e.ingoingNote else null end,
			case when e.shareOutgoingNoteAnonymously = true then e.outgoingNote else null end,
			e.startedAt
		)
		from Event e join e.eventType et
		where e.account.id = :accountId and (e.shareIngoingNoteAnonymously = true or e.shareOutgoingNoteAnonymously = true)
		order by e.startedAt desc
		""")
	List<AnonymousFeedbackItem> findAnonymousFeedback(@Param("accountId") UUID accountId);

}
