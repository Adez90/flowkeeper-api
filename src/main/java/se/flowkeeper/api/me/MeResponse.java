package se.flowkeeper.api.me;

import java.util.List;
import java.util.UUID;

public record MeResponse(
	UUID userId,
	String displayName,
	String email,
	List<AccountSummary> accounts
) {

	public record AccountSummary(UUID accountId, String name, String type, String role) {
	}

}
