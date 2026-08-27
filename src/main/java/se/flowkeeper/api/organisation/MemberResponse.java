package se.flowkeeper.api.organisation;

import se.flowkeeper.api.account.AccountMember;

import java.util.UUID;

public record MemberResponse(
	UUID userId,
	String displayName,
	String email,
	String role,
	UUID departmentId,
	UUID groupId
) {

	public static MemberResponse from(AccountMember member) {
		return new MemberResponse(
			member.getUser().getId(),
			member.getUser().getDisplayName(),
			member.getUser().getEmail(),
			member.getRole().name(),
			member.getDepartment() != null ? member.getDepartment().getId() : null,
			member.getGroup() != null ? member.getGroup().getId() : null);
	}

}
