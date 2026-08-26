package se.flowkeeper.api;

import org.junit.jupiter.api.Test;

class FlowkeeperApiApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoadsAgainstAMigratedDatabase() {
		// Confirms the full application context boots, and Flyway's
		// migrations apply cleanly, against a real Postgres — not just
		// that the class files compile.
	}

}
