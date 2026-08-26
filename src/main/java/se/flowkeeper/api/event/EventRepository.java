package se.flowkeeper.api.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

	List<Event> findByAccount_IdAndStatusOrderByStartedAtDesc(UUID accountId, EventStatus status);

	List<Event> findByAccount_IdOrderByStartedAtDesc(UUID accountId);

}
