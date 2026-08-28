package se.flowkeeper.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PaymentEventRepository extends JpaRepository<PaymentEvent, UUID> {

	boolean existsByProviderAndProviderEventId(String provider, String providerEventId);

}
