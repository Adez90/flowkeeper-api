package se.flowkeeper.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** The one real Clock bean the app wires up — everything else takes a Clock as a constructor dependency so tests can fix "now". */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
