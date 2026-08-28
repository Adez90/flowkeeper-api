package se.flowkeeper.api.integrations;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record StartAuthorizationRequest(@NotNull UUID accountId) {
}
