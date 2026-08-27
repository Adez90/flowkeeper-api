package se.flowkeeper.api.organisation;

import java.util.UUID;

public record GroupResponse(UUID id, String name, UUID departmentId, boolean shareFlowWithPeers) {

	public static GroupResponse from(Group group) {
		return new GroupResponse(
			group.getId(),
			group.getName(),
			group.getDepartment() != null ? group.getDepartment().getId() : null,
			group.isShareFlowWithPeers());
	}

}
