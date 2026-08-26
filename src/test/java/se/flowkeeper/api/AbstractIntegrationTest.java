package se.flowkeeper.api;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for tests that need the full Spring context against a real,
 * Flyway-migrated Postgres. One container, shared by every subclass in a
 * given JVM — Testcontainers reuses it rather than starting one per class.
 */
@Testcontainers
@SpringBootTest
public abstract class AbstractIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

}
