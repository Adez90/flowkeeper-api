package se.flowkeeper.api.organisation;

import java.util.UUID;

public record OrganisationResponse(UUID accountId, String name, String role) {
}
