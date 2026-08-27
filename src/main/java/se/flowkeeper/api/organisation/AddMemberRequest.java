package se.flowkeeper.api.organisation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import se.flowkeeper.api.account.MemberRole;

import java.util.UUID;

/**
 * Adds an existing FlowKeeper user to an Organisation by email. Deliberately
 * not a full invitation flow (no pending-invite state, no email sent) — the
 * invitee must already have a FlowKeeper profile (i.e. have logged in at
 * least once, even just to their own Personal account) before they can be
 * added. A proper invite-by-email-before-first-login flow is a follow-up.
 */
public record AddMemberRequest(
	@NotBlank String email,
	@NotNull MemberRole role,
	UUID departmentId,
	UUID groupId
) {
}
