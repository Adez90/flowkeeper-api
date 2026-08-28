package se.flowkeeper.api.integrations;

public enum ExternalProvider {
	GOOGLE_CALENDAR,
	MICROSOFT_CALENDAR,
	/** No working gateway yet — "the iOS calendar" is ambiguous between on-device EventKit and iCloud CalDAV; see the Blueprint. Always unavailable for now. */
	APPLE_CALENDAR,
	STRAVA
}
