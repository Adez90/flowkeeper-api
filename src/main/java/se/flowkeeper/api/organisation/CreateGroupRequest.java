package se.flowkeeper.api.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateGroupRequest(
	@NotBlank @Size(max = 200) String name,
	/** Omit for a Group directly under the Organisation, no Department layer. */
	UUID departmentId
) {
}
