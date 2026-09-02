package se.flowkeeper.api.integrations;

import org.junit.jupiter.api.Test;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleCalendarGatewayTest {

	@Test
	void toGoogleTimestampNeverProducesACharacterThatSurvivesAsALiteralPlusInTheQueryString() {
		// A positive UTC offset (e.g. Stockholm in summer) formatted as
		// "+02:00" survives UriComponentsBuilder's RFC 3986 encoding as a
		// literal '+' — which Google's query parser then decodes as a
		// space, corrupting timeMin/timeMax and failing with a 400. Every
		// offset should format as a '+'-free UTC instant instead.
		ZonedDateTime stockholmMidnight = LocalDate.of(2026, 9, 2).atStartOfDay(ZoneId.of("Europe/Stockholm"));

		String formatted = GoogleCalendarGateway.toGoogleTimestamp(stockholmMidnight);

		assertThat(formatted).doesNotContain("+");
		assertThat(formatted).isEqualTo("2026-09-01T22:00:00Z");
	}

	@Test
	void theBuiltQueryStringNeverContainsALiteralPlus() {
		ZonedDateTime stockholmMidnight = LocalDate.of(2026, 9, 2).atStartOfDay(ZoneId.of("Europe/Stockholm"));
		String timeMin = GoogleCalendarGateway.toGoogleTimestamp(stockholmMidnight);

		String uri = UriComponentsBuilder.fromUriString("https://www.googleapis.com/calendar/v3/calendars/primary/events")
			.queryParam("timeMin", timeMin)
			.encode()
			.build()
			.toUri()
			.toString();

		assertThat(uri).doesNotContain("+");
	}

}
