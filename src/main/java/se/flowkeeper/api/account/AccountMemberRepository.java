package se.flowkeeper.api.account;

import org.springframework.data.jpa.repository.JpaRepository;
import se.flowkeeper.api.user.User;

import java.util.List;
import java.util.UUID;

public interface AccountMemberRepository extends JpaRepository<AccountMember, UUID> {

	List<AccountMember> findByUser(User user);

}
