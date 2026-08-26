package se.flowkeeper.api.event;

import java.util.UUID;

public record EventTypeResponse(UUID id, String code, String label, String icon, boolean isDefault) {

	public static EventTypeResponse from(EventType eventType) {
		return new EventTypeResponse(
			eventType.getId(),
			eventType.getCode(),
			eventType.getLabel(),
			eventType.getIcon(),
			eventType.isDefault()
		);
	}

}
