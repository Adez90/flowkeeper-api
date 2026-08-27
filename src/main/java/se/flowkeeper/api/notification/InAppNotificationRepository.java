package se.flowkeeper.api.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {

	List<InAppNotification> findByUser_IdOrderByCreatedAtDesc(UUID userId);

}
