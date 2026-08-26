package se.flowkeeper.api.registration;

import java.util.UUID;

public record RegistrationResponse(
	UUID userId,
	UUID personalAccountId,
	String role,
	boolean alreadyRegistered
) {
}
