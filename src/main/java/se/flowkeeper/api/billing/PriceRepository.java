package se.flowkeeper.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PriceRepository extends JpaRepository<Price, UUID> {

	List<Price> findByActiveTrueAndPlan_IdOrderByPeriodAsc(UUID planId);

	List<Price> findByActiveTrueOrderByPeriodAsc();

}
