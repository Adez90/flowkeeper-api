package se.flowkeeper.api.me;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * The read counterpart to Registration: called on every app open after
 * login (not just the first one) to resolve who's signed in and which
 * accounts they can act in. Also owns updating the basic profile fields
 * (display name, timezone, locale, avatar).
 */
@Service
public class MeService {

	private static final Logger log = LoggerFactory.getLogger(MeService.class);

	private final UserRepository userRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final CurrentUserResolver currentUserResolver;

	public MeService(UserRepository userRepository,
			AccountMemberRepository accountMemberRepository,
			CurrentUserResolver currentUserResolver) {
		this.userRepository = userRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional(readOnly = true)
	public Optional<MeResponse> currentUser(Jwt jwt) {
		return userRepository.findByKeycloakSubject(jwt.getSubject())
			.map(this::toResponse);
	}

	@Transactional
	public MeResponse updateProfile(Jwt jwt, UpdateProfileRequest request) {
		User user = currentUserResolver.require(jwt);

		try {
			ZoneId.of(request.timezone());
		} catch (DateTimeException e) {
			throw new ValidationException("Not a valid timezone: " + request.timezone());
		}

		user.updateProfile(request.displayName(), request.timezone(), request.locale(), request.avatarUrl());
		log.info("User {} updated their profile", user.getId());

		return toResponse(user);
	}

	@Transactional
	public MeResponse updateNotificationPreferences(Jwt jwt, UpdateNotificationPreferencesRequest request) {
		User user = currentUserResolver.require(jwt);
		user.updateNotificationPreferences(request.notifyInApp(), request.notifyPush(), request.notifyEmail());
		log.info("User {} set notification preferences (inApp={}, push={}, email={})",
			user.getId(), request.notifyInApp(), request.notifyPush(), request.notifyEmail());
		return toResponse(user);
	}

	@Transactional
	public MeResponse updatePushToken(Jwt jwt, UpdatePushTokenRequest request) {
		User user = currentUserResolver.require(jwt);
		user.updateExpoPushToken(request.expoPushToken());
		log.info("User {} registered a push token", user.getId());
		return toResponse(user);
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
		return new MeResponse(
			user.getId(), user.getDisplayName(), user.getEmail(),
			user.getTimezone(), user.getLocale(), user.getAvatarUrl(),
			user.isNotifyInApp(), user.isNotifyPush(), user.isNotifyEmail(),
			accounts);
	}

}
