package se.flowkeeper.api.registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.util.Optional;

/**
 * Keycloak owns sign-up (credentials, email verification); this is what
 * turns a verified identity into a usable FlowKeeper account. Called once
 * per client after first login — idempotent, so calling it again is safe
 * and simply returns what already exists.
 */
@Service
public class RegistrationService {

	private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

	private final UserRepository userRepository;
	private final AccountRepository accountRepository;
	private final AccountMemberRepository accountMemberRepository;

	public RegistrationService(UserRepository userRepository,
			AccountRepository accountRepository,
			AccountMemberRepository accountMemberRepository) {
		this.userRepository = userRepository;
		this.accountRepository = accountRepository;
		this.accountMemberRepository = accountMemberRepository;
	}

	@Transactional
	public RegistrationResponse registerCurrentUser(Jwt jwt) {
		String subject = jwt.getSubject();

		Optional<User> existing = userRepository.findByKeycloakSubject(subject);
		if (existing.isPresent()) {
			return alreadyRegisteredResponse(existing.get());
		}

		String email = jwt.getClaimAsString("email");
		String displayName = jwt.getClaimAsString("name");
		if (displayName == null || displayName.isBlank()) {
			displayName = (email != null && !email.isBlank()) ? email : subject;
		}

		User user = userRepository.save(new User(subject, displayName, email));
		Account personalAccount = accountRepository.save(new Account(AccountType.PERSONAL, displayName));
		AccountMember membership = accountMemberRepository.save(
			new AccountMember(personalAccount, user, MemberRole.OWNER));

		log.info("Registered new user {} with personal account {}", user.getId(), personalAccount.getId());
		log.debug("New user {} registered with email {}", user.getId(), email);

		return new RegistrationResponse(user.getId(), personalAccount.getId(), membership.getRole().name(), false);
	}

	private RegistrationResponse alreadyRegisteredResponse(User user) {
		AccountMember membership = accountMemberRepository.findByUser(user)
			.stream()
			.findFirst()
			.orElseThrow(() -> {
				// Should be unreachable — a User row is only ever created
				// alongside its membership, in the same transaction above.
				log.error("User {} exists with no account membership — data inconsistency", user.getId());
				return new IllegalStateException(
					"User %s exists with no account membership".formatted(user.getId()));
			});

		log.debug("Registration no-op for existing user {}", user.getId());
		return new RegistrationResponse(user.getId(), membership.getAccount().getId(), membership.getRole().name(), true);
	}

}
