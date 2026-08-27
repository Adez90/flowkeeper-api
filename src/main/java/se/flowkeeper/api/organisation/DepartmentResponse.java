package se.flowkeeper.api.organisation;

import java.util.UUID;

public record DepartmentResponse(UUID id, String name, boolean shareFlowWithPeers) {

	public static DepartmentResponse from(Department department) {
		return new DepartmentResponse(department.getId(), department.getName(), department.isShareFlowWithPeers());
	}

}
