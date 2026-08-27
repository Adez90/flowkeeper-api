package se.flowkeeper.api.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByKeycloakSubject(String keycloakSubject);

	/** Matches the unique index on lower(email) — case-insensitive by design. */
	Optional<User> findByEmailIgnoreCase(String email);

	/** Candidates for a reminder nudge — anyone with at least one channel switched on. */
	List<User> findByNotifyInAppTrueOrNotifyPushTrueOrNotifyEmailTrue();

}
