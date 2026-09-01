package se.flowkeeper.api.diagnostics;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import se.flowkeeper.api.billing.PlatformAdmins;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

/**
 * Lets a platform admin deliberately trigger a real, unhandled exception —
 * the only reliable way to confirm error tracking actually reaches Sentry
 * end to end, rather than trusting that it silently works.
 */
@Service
public class DiagnosticsService {

	private final CurrentUserResolver currentUserResolver;
	private final PlatformAdmins platformAdmins;

	public DiagnosticsService(CurrentUserResolver currentUserResolver, PlatformAdmins platformAdmins) {
		this.currentUserResolver = currentUserResolver;
		this.platformAdmins = platformAdmins;
	}

	/**
	 * Not caught anywhere — goes through exactly the same unhandled
	 * -exception path a real bug would: Spring's default error handling
	 * logs it at ERROR, which logback-spring.xml's SentryAppender reports
	 * as a real Sentry event.
	 */
	public void triggerTestError(Jwt jwt) {
		User user = currentUserResolver.require(jwt);
		platformAdmins.requireAdmin(user);
		throw new DeliberateTestException("Deliberate test error triggered by " + user.getEmail() + " to verify Sentry delivery");
	}

}
