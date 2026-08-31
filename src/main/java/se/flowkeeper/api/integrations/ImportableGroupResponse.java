package se.flowkeeper.api.integrations;

import java.util.List;

/**
 * One connected provider's importable items for the requested day.
 * needsReconnect is true when the stored connection couldn't be refreshed
 * (the user revoked access, or the refresh token expired) — items is empty
 * in that case, and the client should point them back at Connect.
 */
public record ImportableGroupResponse(ExternalProvider provider, boolean needsReconnect, List<ImportableItem> items) {
}
