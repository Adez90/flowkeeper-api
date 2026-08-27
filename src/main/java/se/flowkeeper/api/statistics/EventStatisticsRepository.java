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
		where e.account.id = :accountId and e.startedAt >= :start and e.startedAt < :end
		""")
	OverallCounts aggregateOverall(@Param("accountId") UUID accountId, @Param("start") Instant start, @Param("end") Instant end);

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

	@Query("""
		select new se.flowkeeper.api.statistics.TypeCounts(
			et.id, et.label, count(e), avg(e.outgoingEnergy - e.ingoingEnergy)
		)
		from Event e join e.eventType et
		where e.account.id = :accountId and e.startedAt >= :start and e.startedAt < :end
		group by et.id, et.label
		order by count(e) desc
		""")
	List<TypeCounts> aggregateByType(@Param("accountId") UUID accountId, @Param("start") Instant start, @Param("end") Instant end);

}
