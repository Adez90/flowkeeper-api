package se.flowkeeper.api.diagnostics;

/** Thrown on purpose by DiagnosticsService#triggerTestError — never a real bug, always a manual check that error tracking actually reaches Sentry. */
public class DeliberateTestException extends RuntimeException {

	public DeliberateTestException(String message) {
		super(message);
	}

}
