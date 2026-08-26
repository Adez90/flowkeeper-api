package se.flowkeeper.api.me;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.util.List;
import java.util.Optional;

/**
 * The read counterpart to Registration: called on every app open after
 * login (not just the first one) to resolve who's signed in and which
 * accounts they can act in.
 */
@Service
public class MeService {

	private static final Logger log = LoggerFactory.getLogger(MeService.class);

	private final UserRepository userRepository;
	private final AccountMemberRepository accountMemberRepository;

	public MeService(UserRepository userRepository, AccountMemberRepository accountMemberRepository) {
		this.userRepository = userRepository;
		this.accountMemberRepository = accountMemberRepository;
	}

	@Transactional(readOnly = true)
	public Optional<MeResponse> currentUser(Jwt jwt) {
		return userRepository.findByKeycloakSubject(jwt.getSubject())
			.map(this::toResponse);
	}

	private MeResponse toResponse(User user) {
		List<MeResponse.AccountSummary> accounts = accountMemberRepository.findByUser(user).stream()
			.map(member -> new MeResponse.AccountSummary(
				member.getAccount().getId(),
				member.getAccount().getName(),
				member.getAccount().getType().name(),
				member.getRole().name()))
			.toList();

		log.debug("Resolved profile for user {} with {} account(s)", user.getId(), accounts.size());
		return new MeResponse(user.getId(), user.getDisplayName(), user.getEmail(), accounts);
	}

}
