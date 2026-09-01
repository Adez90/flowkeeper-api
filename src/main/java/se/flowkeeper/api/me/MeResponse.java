package se.flowkeeper.api.me;

import java.util.List;
import java.util.UUID;

public record MeResponse(
	UUID userId,
	String displayName,
	String email,
	String timezone,
	String locale,
	String avatarUrl,
	boolean notifyInApp,
	boolean notifyPush,
	boolean notifyEmail,
	List<AccountSummary> accounts,
	boolean isPlatformAdmin
) {

	public record AccountSummary(UUID accountId, String name, String type, String role) {
	}

}
