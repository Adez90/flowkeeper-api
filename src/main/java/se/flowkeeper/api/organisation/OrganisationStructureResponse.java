package se.flowkeeper.api.organisation;

import java.util.List;

public record OrganisationStructureResponse(List<DepartmentResponse> departments, List<GroupResponse> groups) {
}
