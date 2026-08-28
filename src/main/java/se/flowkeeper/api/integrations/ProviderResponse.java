package se.flowkeeper.api.integrations;

/** The catalog GET /api/v1/integrations/providers returns — the client only offers a "Connect" button for available=true entries. */
public record ProviderResponse(ExternalProvider provider, boolean available) {
}
