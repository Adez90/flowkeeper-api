package se.flowkeeper.api.account;

import org.springframework.data.jpa.repository.JpaRepository;
import se.flowkeeper.api.user.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountMemberRepository extends JpaRepository<AccountMember, UUID> {

	List<AccountMember> findByUser(User user);

	Optional<AccountMember> findByAccount_IdAndUser(UUID accountId, User user);

	Optional<AccountMember> findByAccount_IdAndUser_Id(UUID accountId, UUID userId);

	List<AccountMember> findByAccount_Id(UUID accountId);

	boolean existsByAccount_IdAndUser(UUID accountId, User user);

	List<AccountMember> findByGroup_Id(UUID groupId);

}
