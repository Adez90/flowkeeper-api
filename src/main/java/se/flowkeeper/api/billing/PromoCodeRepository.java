package se.flowkeeper.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

	Optional<PromoCode> findByCode(String code);

	List<PromoCode> findAllByOrderByCreatedAtDesc();

}
