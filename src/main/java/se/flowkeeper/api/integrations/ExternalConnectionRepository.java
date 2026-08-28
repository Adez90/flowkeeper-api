package se.flowkeeper.api.integrations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExternalConnectionRepository extends JpaRepository<ExternalConnection, UUID> {

	List<ExternalConnection> findByAccount_Id(UUID accountId);

	List<ExternalConnection> findByUser_IdAndAccount_Id(UUID userId, UUID accountId);

	Optional<ExternalConnection> findByUser_IdAndAccount_IdAndProvider(UUID userId, UUID accountId, ExternalProvider provider);

}
