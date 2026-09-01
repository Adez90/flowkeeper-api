package se.flowkeeper.api.common;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Exercises logback-spring.xml the same way Spring Boot itself loads it at
 * startup (LoggingSystem, not a plain LoggerFactory.getLogger call, which
 * would silently skip the Spring-aware "-spring.xml" file entirely) — a
 * typo or a wrong appender class here would otherwise only surface once
 * deployed, since nothing else in the test suite boots real logging.
 */
class LoggingConfigurationTest {

	@Test
	void logbackSpringXmlInitializesAndLogsWithoutASentryDsnConfigured() {
		LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

		assertThatCode(() -> {
			loggingSystem.beforeInitialize();
			loggingSystem.initialize(new LoggingInitializationContext(new StandardEnvironment()), "classpath:logback-spring.xml", null);
			LoggerFactory.getLogger(LoggingConfigurationTest.class)
				.warn("Verifying the Sentry appender no-ops safely with no SENTRY_DSN set");
		}).doesNotThrowAnyException();
	}

	@Test
	void logbackSpringXmlInitializesAndLogsWithARealLookingDsnConfigured() {
		// A syntactically valid DSN pointed at a host this sandbox can't
		// reach — the SDK queues events on a background transport rather
		// than sending synchronously during init/log, so this must complete
		// without blocking on or failing from that unreachable host.
		System.setProperty("SENTRY_DSN", "https://examplePublicKey@o0.ingest.sentry.io/0");
		try {
			LoggingSystem loggingSystem = LoggingSystem.get(getClass().getClassLoader());

			assertThatCode(() -> {
				loggingSystem.beforeInitialize();
				loggingSystem.initialize(new LoggingInitializationContext(new StandardEnvironment()), "classpath:logback-spring.xml", null);
				LoggerFactory.getLogger(LoggingConfigurationTest.class)
					.warn("Verifying the Sentry appender accepts a real-looking DSN without blocking");
			}).doesNotThrowAnyException();
		} finally {
			System.clearProperty("SENTRY_DSN");
		}
	}

}
