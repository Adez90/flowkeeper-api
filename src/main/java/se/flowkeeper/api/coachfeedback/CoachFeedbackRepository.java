package se.flowkeeper.api.coachfeedback;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoachFeedbackRepository extends JpaRepository<CoachFeedback, UUID> {

	List<CoachFeedback> findByAccount_IdAndMember_IdOrderByCreatedAtDesc(UUID accountId, UUID memberId);

}
