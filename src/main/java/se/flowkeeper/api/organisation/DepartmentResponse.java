package se.flowkeeper.api.organisation;

import java.util.UUID;

public record DepartmentResponse(UUID id, String name) {

	public static DepartmentResponse from(Department department) {
		return new DepartmentResponse(department.getId(), department.getName());
	}

}
