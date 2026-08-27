package se.flowkeeper.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base for tests that need the full Spring context against a real,
 * Flyway-migrated Postgres. One container, shared by every subclass in a
 * given JVM.
 *
 * Deliberately NOT using @Testcontainers/@Container: that JUnit extension
 * ties container teardown to each concrete subclass's own lifecycle, not
 * to the shared static field's — so the first subclass to finish stops the
 * container out from under every class after it (confirmed live: CI passed
 * every test up through EventIntegrationTest, then every remaining
 * Testcontainers-backed test failed with a Postgres connection refused).
 * The static initializer below is Testcontainers' own documented pattern
 * for a container genuinely shared across multiple test classes — start it
 * once, never stop it early, let the JVM exit (and Ryuk) clean it up.
 * @ServiceConnection still works without @Container; it discovers the
 * annotated field by reflection, independent of the lifecycle annotations.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	static {
		POSTGRES.start();
	}

}
