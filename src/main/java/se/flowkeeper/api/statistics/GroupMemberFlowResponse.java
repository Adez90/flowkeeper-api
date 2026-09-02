package se.flowkeeper.api.statistics;

import java.time.LocalDate;
import java.util.List;

/**
 * A single group's members, by name and individual Flow % — unlike every
 * other response in this package, deliberately NOT anonymous. Only ever
 * includes members who have opted in via AccountMember.shareFlowWithPeers,
 * and only ever returned to a viewer who is themselves a member of this
 * exact group (see StatisticsService#groupMemberFlow) — nobody above group
 * level (a department admin, org owner, or a peer coach in another group)
 * can reach this, only the pooled, anonymous aggregate.
 */
public record GroupMemberFlowResponse(
	StatisticsPeriod period,
	LocalDate rangeStart,
	LocalDate rangeEndExclusive,
	List<MemberFlow> members
) {
}
