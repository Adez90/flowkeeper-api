package se.flowkeeper.api.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Resolves a User's stored timezone to a real ZoneId, falling back to UTC
 * for a corrupted value. Shared between anything that needs to reason about
 * "today" in a user's own timezone — StatisticsService's day/week/month
 * ranges, and the reminder jobs' "is it this user's evening yet" check.
 */
@Component
public class UserTimezones {

	private static final Logger log = LoggerFactory.getLogger(UserTimezones.class);

	public ZoneId resolve(User user) {
		try {
			return ZoneId.of(user.getTimezone());
		} catch (DateTimeException e) {
			// Shouldn't happen — timezone is validated on write — but a
			// "day" still has to mean something if it somehow does.
			log.warn("User {} has an invalid stored timezone '{}', falling back to UTC", user.getId(), user.getTimezone());
			return ZoneOffset.UTC;
		}
	}

}
