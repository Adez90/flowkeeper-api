package se.flowkeeper.api.organisation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

	List<Department> findByAccount_Id(UUID accountId);

	Optional<Department> findByIdAndAccount_Id(UUID id, UUID accountId);

}
