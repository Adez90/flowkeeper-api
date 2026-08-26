package se.flowkeeper.api.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {

	/** The seeded global defaults (accountId null) plus this account's own. */
	List<EventType> findByAccountIdIsNullOrAccountId(UUID accountId);

}
