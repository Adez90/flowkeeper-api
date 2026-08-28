package se.flowkeeper.api.integrations;

import java.time.Instant;

public record OAuthTokenResult(String accessToken, String refreshToken, Instant expiresAt, String externalAccountLabel) {
}
