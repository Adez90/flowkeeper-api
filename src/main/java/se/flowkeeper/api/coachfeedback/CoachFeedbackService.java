package se.flowkeeper.api.coachfeedback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.flowkeeper.api.account.Account;
import se.flowkeeper.api.account.AccountMember;
import se.flowkeeper.api.account.AccountMemberRepository;
import se.flowkeeper.api.account.AccountRepository;
import se.flowkeeper.api.account.AccountType;
import se.flowkeeper.api.account.MemberRole;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.event.Event;
import se.flowkeeper.api.event.EventRepository;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.util.List;
import java.util.UUID;

/**
 * Coach-to-member 1:1 feedback. Authorization mirrors the supervisory
 * ladder used everywhere else in the organisation feature (statistics,
 * sharing consent): whoever supervises a member — their group's own COACH,
 * their department's own ADMIN, an org-wide ADMIN, or the OWNER — can write
 * and read feedback about them. The member themselves can always read their
 * own. One direction only: a plain MEMBER can't write feedback about
 * anyone, including themselves.
 */
@Service
public class CoachFeedbackService {

	private static final Logger log = LoggerFactory.getLogger(CoachFeedbackService.class);

	private final CoachFeedbackRepository coachFeedbackRepository;
	private final AccountRepository accountRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final EventRepository eventRepository;
	private final CurrentUserResolver currentUserResolver;

	public CoachFeedbackService(CoachFeedbackRepository coachFeedbackRepository,
			AccountRepository accountRepository,
			AccountMemberRepository accountMemberRepository,
			EventRepository eventRepository,
			CurrentUserResolver currentUserResolver) {
		this.coachFeedbackRepository = coachFeedbackRepository;
		this.accountRepository = accountRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.eventRepository = eventRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional
	public CoachFeedbackResponse create(Jwt jwt, UUID accountId, UUID memberId, CreateCoachFeedbackRequest request) {
		User coach = currentUserResolver.require(jwt);
		Account account = requireOrganisation(accountId);
		AccountMember coachMembership = requireMembership(accountId, coach.getId());
		AccountMember memberMembership = requireMembership(accountId, memberId);

		if (!supervises(coachMembership, memberMembership)) {
			throw new AccessDeniedException("User %s does not supervise member %s".formatted(coach.getId(), memberId));
		}

		Event event = null;
		if (request.eventId() != null) {
			event = eventRepository.findById(request.eventId())
				.orElseThrow(() -> new ResourceNotFoundException("No such event: " + request.eventId()));
			if (!event.getAccount().getId().equals(accountId) || !event.getUser().getId().equals(memberId)) {
				throw new ValidationException("Event %s does not belong to member %s in this account".formatted(request.eventId(), memberId));
			}
		}

		CoachFeedback feedback = coachFeedbackRepository.save(
			new CoachFeedback(account, coach, memberMembership.getUser(), event, request.note()));

		log.info("User {} left feedback for member {} in account {}{}",
			coach.getId(), memberId, accountId, event != null ? " on event " + event.getId() : " (freeform)");

		return CoachFeedbackResponse.from(feedback);
	}

	@Transactional(readOnly = true)
	public List<CoachFeedbackResponse> list(Jwt jwt, UUID accountId, UUID memberId) {
		User viewer = currentUserResolver.require(jwt);
		requireOrganisation(accountId);
		AccountMember viewerMembership = requireMembership(accountId, viewer.getId());
		AccountMember memberMembership = requireMembership(accountId, memberId);

		boolean isSelf = viewer.getId().equals(memberId);
		if (!isSelf && !supervises(viewerMembership, memberMembership)) {
			throw new AccessDeniedException("User %s cannot view feedback for member %s".formatted(viewer.getId(), memberId));
		}

		return coachFeedbackRepository.findByAccount_IdAndMember_IdOrderByCreatedAtDesc(accountId, memberId).stream()
			.map(CoachFeedbackResponse::from)
			.toList();
	}

	/** Whether `supervisor` sits above `member` in the OWNER/ADMIN/COACH/MEMBER ladder — same shape as StatisticsService's group/department checks, applied to one member instead of a whole group/department. */
	private boolean supervises(AccountMember supervisor, AccountMember member) {
		if (supervisor.getRole() == MemberRole.OWNER) {
			return true;
		}
		if (supervisor.getRole() == MemberRole.ADMIN) {
			if (supervisor.getDepartment() == null) {
				return true; // org-wide ADMIN, no department scope of their own
			}
			UUID memberDepartmentId = memberDepartmentId(member);
			return supervisor.getDepartment().getId().equals(memberDepartmentId);
		}
		if (supervisor.getRole() == MemberRole.COACH) {
			return supervisor.getGroup() != null && member.getGroup() != null
				&& supervisor.getGroup().getId().equals(member.getGroup().getId());
		}
		return false;
	}

	private UUID memberDepartmentId(AccountMember member) {
		if (member.getDepartment() != null) {
			return member.getDepartment().getId();
		}
		if (member.getGroup() != null && member.getGroup().getDepartment() != null) {
			return member.getGroup().getDepartment().getId();
		}
		return null;
	}

	private Account requireOrganisation(UUID accountId) {
		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new ResourceNotFoundException("No such account: " + accountId));
		if (account.getType() != AccountType.ORGANISATION) {
			throw new ValidationException("Account %s is not an Organisation".formatted(accountId));
		}
		return account;
	}

	private AccountMember requireMembership(UUID accountId, UUID userId) {
		return accountMemberRepository.findByAccount_IdAndUser_Id(accountId, userId)
			.orElseThrow(() -> new ResourceNotFoundException(
				"User %s is not a member of account %s".formatted(userId, accountId)));
	}

}
