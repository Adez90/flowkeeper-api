package se.flowkeeper.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromoCodeRedemptionRepository extends JpaRepository<PromoCodeRedemption, UUID> {

	boolean existsByPromoCode_IdAndAccount_Id(UUID promoCodeId, UUID accountId);

}
