package se.flowkeeper.api.billing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PromoCodeAdminService {

	private static final Logger log = LoggerFactory.getLogger(PromoCodeAdminService.class);

	// Excludes 0/O and 1/I — easy to misread when a code is read aloud or retyped from a screenshot.
	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final SecureRandom RANDOM = new SecureRandom();

	private final PromoCodeRepository promoCodeRepository;
	private final CurrentUserResolver currentUserResolver;
	private final PlatformAdmins platformAdmins;

	public PromoCodeAdminService(PromoCodeRepository promoCodeRepository, CurrentUserResolver currentUserResolver,
			PlatformAdmins platformAdmins) {
		this.promoCodeRepository = promoCodeRepository;
		this.currentUserResolver = currentUserResolver;
		this.platformAdmins = platformAdmins;
	}

	@Transactional
	public PromoCodeResponse generate(Jwt jwt, GeneratePromoCodeRequest request) {
		User user = currentUserResolver.require(jwt);
		platformAdmins.requireAdmin(user);

		if (request.expiresAt() != null && request.expiresAt().isBefore(Instant.now())) {
			throw new ValidationException("expiresAt cannot be in the past");
		}

		PromoCode promoCode = new PromoCode(generateUniqueCode(), request.durationDays(), request.maxRedemptions(),
			request.expiresAt(), request.note(), user.getEmail());
		promoCode = promoCodeRepository.save(promoCode);

		log.info("Platform admin {} generated promo code {} ({} day(s), max {} redemption(s))",
			user.getEmail(), promoCode.getCode(), request.durationDays(), request.maxRedemptions());

		return PromoCodeResponse.from(promoCode);
	}

	@Transactional(readOnly = true)
	public List<PromoCodeResponse> list(Jwt jwt) {
		User user = currentUserResolver.require(jwt);
		platformAdmins.requireAdmin(user);

		return promoCodeRepository.findAllByOrderByCreatedAtDesc().stream()
			.map(PromoCodeResponse::from)
			.toList();
	}

	@Transactional
	public void revoke(Jwt jwt, UUID promoCodeId) {
		User user = currentUserResolver.require(jwt);
		platformAdmins.requireAdmin(user);

		PromoCode promoCode = promoCodeRepository.findById(promoCodeId)
			.orElseThrow(() -> new ResourceNotFoundException("Unknown promo code: " + promoCodeId));
		promoCode.revoke();

		log.info("Platform admin {} revoked promo code {}", user.getEmail(), promoCode.getCode());
	}

	private String generateUniqueCode() {
		String code;
		do {
			code = randomCode();
		} while (promoCodeRepository.findByCode(code).isPresent());
		return code;
	}

	private static String randomCode() {
		StringBuilder code = new StringBuilder();
		for (int i = 0; i < 8; i++) {
			if (i == 4) {
				code.append('-');
			}
			code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
		}
		return code.toString();
	}

}
