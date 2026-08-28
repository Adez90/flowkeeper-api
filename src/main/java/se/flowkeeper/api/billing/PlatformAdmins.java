package se.flowkeeper.api.billing;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import se.flowkeeper.api.user.User;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * There's no per-account role for "runs FlowKeeper itself" — platform
 * admin is a fixed allowlist of emails set via app.admin.emails
 * (ADMIN_EMAILS), the same config-not-code posture as every other
 * environment-specific setting in this app. Empty by default, so nobody
 * can generate promo codes until it's set.
 */
@Component
public class PlatformAdmins {

	private final Set<String> adminEmails;

	public PlatformAdmins(@Value("${app.admin.emails:}") String adminEmailsCsv) {
		this.adminEmails = Arrays.stream(adminEmailsCsv.split(","))
			.map(String::trim)
			.map(String::toLowerCase)
			.filter(email -> !email.isBlank())
			.collect(Collectors.toSet());
	}

	public boolean isAdmin(User user) {
		return adminEmails.contains(user.getEmail().toLowerCase());
	}

	public void requireAdmin(User user) {
		if (!isAdmin(user)) {
			throw new AccessDeniedException("User %s is not a platform admin".formatted(user.getId()));
		}
	}

}
