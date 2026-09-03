package se.flowkeeper.api.integrations;

public enum ExternalProvider {
	GOOGLE_CALENDAR,
	MICROSOFT_CALENDAR,
	/**
	 * No OAuthCalendarGateway, and none is planned: this is imported via
	 * on-device EventKit in the mobile app (see calendar-sync.tsx and
	 * import-events.tsx in flowkeeper-mobile), which reads the phone's own
	 * calendar locally and never goes through a server-side connection —
	 * hence no {@link OAuthCalendarGateway} implementation, and {@code
	 * listProviders}/{@code appleEnabled} below only govern whether the web
	 * app's connect flow (a genuinely different, still-hypothetical iCloud
	 * CalDAV integration) offers a button, which it doesn't yet.
	 */
	APPLE_CALENDAR,
	STRAVA
}
