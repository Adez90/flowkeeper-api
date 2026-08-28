package se.flowkeeper.api.integrations;

import java.time.Instant;
import java.util.UUID;

public record ConnectionResponse(
		UUID id,
		ExternalProvider provider,
		ConnectionStatus status,
		String externalAccountLabel,
		Instant lastSyncedAt,
		Instant createdAt) {

	public static ConnectionResponse from(ExternalConnection connection) {
		return new ConnectionResponse(
			connection.getId(),
			connection.getProvider(),
			connection.getStatus(),
			connection.getExternalAccountLabel(),
			connection.getLastSyncedAt(),
			connection.getCreatedAt());
	}

}
