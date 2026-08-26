package se.flowkeeper.api.statistics;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import se.flowkeeper.api.event.Event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only reporting queries over Event — deliberately not a JpaRepository,
 * since this has no business exposing CRUD methods for a domain it doesn't
 * own.
 */
public interface EventStatisticsRepository extends Repository<Event, UUID> {

	@Query("""
		select new se.flowkeeper.api.statistics.OverallCounts(
			count(e),
			sum(case when e.status = se.flowkeeper.api.event.EventStatus.COMPLETED then 1L else 0L end),
			avg(e.ingoingEnergy),
			avg(e.outgoingEnergy - e.ingoingEnergy)
		)
		from Event e
		where e.account.id = :accountId and e.startedAt >= :start and e.startedAt < :end
		""")
	OverallCounts aggregateOverall(@Param("accountId") UUID accountId, @Param("start") Instant start, @Param("end") Instant end);

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
