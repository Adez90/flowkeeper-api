package se.flowkeeper.api.organisation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

	List<Group> findByAccount_Id(UUID accountId);

	Optional<Group> findByIdAndAccount_Id(UUID id, UUID accountId);

}
