package se.flowkeeper.api.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

	Optional<Subscription> findByAccount_Id(UUID accountId);

	Optional<Subscription> findByProviderSubscriptionId(String providerSubscriptionId);

	Optional<Subscription> findByProviderCustomerId(String providerCustomerId);

}
