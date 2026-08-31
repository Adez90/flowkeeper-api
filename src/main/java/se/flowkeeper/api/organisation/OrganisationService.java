package se.flowkeeper.api.organisation;

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
import se.flowkeeper.api.common.ConflictException;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;
import se.flowkeeper.api.user.UserRepository;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class OrganisationService {

	private static final Logger log = LoggerFactory.getLogger(OrganisationService.class);

	// Roles that can manage an org's structure and membership. Anyone
	// further down the ladder (COACH, MEMBER) can't add people or create
	// departments/groups — that's a supervisory action.
	private static final MemberRole[] MANAGER_ROLES = { MemberRole.OWNER, MemberRole.ADMIN };

	private final AccountRepository accountRepository;
	private final AccountMemberRepository accountMemberRepository;
	private final DepartmentRepository departmentRepository;
	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final CurrentUserResolver currentUserResolver;

	public OrganisationService(AccountRepository accountRepository,
			AccountMemberRepository accountMemberRepository,
			DepartmentRepository departmentRepository,
			GroupRepository groupRepository,
			UserRepository userRepository,
			CurrentUserResolver currentUserResolver) {
		this.accountRepository = accountRepository;
		this.accountMemberRepository = accountMemberRepository;
		this.departmentRepository = departmentRepository;
		this.groupRepository = groupRepository;
		this.userRepository = userRepository;
		this.currentUserResolver = currentUserResolver;
	}

	@Transactional
	public OrganisationResponse createOrganisation(Jwt jwt, CreateOrganisationRequest request) {
		User user = currentUserResolver.require(jwt);

		Account account = accountRepository.save(new Account(AccountType.ORGANISATION, request.name()));
		accountMemberRepository.save(new AccountMember(account, user, MemberRole.OWNER));

		log.info("User {} created organisation {} ({})", user.getId(), account.getId(), account.getName());

		return new OrganisationResponse(account.getId(), account.getName(), MemberRole.OWNER.name());
	}

	@Transactional
	public DepartmentResponse createDepartment(Jwt jwt, UUID accountId, CreateDepartmentRequest request) {
		User user = currentUserResolver.require(jwt);
		Account account = requireOrganisation(accountId);
		requireRole(accountId, user, MANAGER_ROLES);

		Department department = departmentRepository.save(new Department(account, request.name()));

		log.info("User {} created department {} in organisation {}", user.getId(), department.getId(), accountId);

		return DepartmentResponse.from(department);
	}

	@Transactional
	public GroupResponse createGroup(Jwt jwt, UUID accountId, CreateGroupRequest request) {
		User user = currentUserResolver.require(jwt);
		Account account = requireOrganisation(accountId);
		requireRole(accountId, user, MANAGER_ROLES);

		Department department = resolveDepartment(accountId, request.departmentId());
		Group group = groupRepository.save(new Group(account, department, request.name()));

		log.info("User {} created group {} in organisation {}", user.getId(), group.getId(), accountId);

		return GroupResponse.from(group);
	}

	@Transactional
	public MemberResponse addMember(Jwt jwt, UUID accountId, AddMemberRequest request) {
		User actor = currentUserResolver.require(jwt);
		Account account = requireOrganisation(accountId);
		AccountMember actorMembership = requireRole(accountId, actor, MANAGER_ROLES);

		// Otherwise a department-scoped ADMIN could add a member as OWNER —
		// granting them full control of the organisation, above the actor's
		// own authority. Only an existing OWNER may create another one.
		if (request.role() == MemberRole.OWNER && actorMembership.getRole() != MemberRole.OWNER) {
			throw new AccessDeniedException(
				"User %s is not the OWNER of account %s and cannot add another OWNER".formatted(actor.getId(), accountId));
		}

		User invitee = userRepository.findByEmailIgnoreCase(request.email())
			.orElseThrow(() -> new ResourceNotFoundException(
				"No FlowKeeper account for %s yet — they need to log in at least once before they can be added."
					.formatted(request.email())));

		if (accountMemberRepository.existsByAccount_IdAndUser(accountId, invitee)) {
			throw new ConflictException("%s is already a member of this organisation".formatted(request.email()));
		}

		Department department = resolveDepartment(accountId, request.departmentId());
		Group group = resolveGroup(accountId, request.groupId(), department);

		AccountMember membership = accountMemberRepository.save(
			new AccountMember(account, invitee, request.role(), department, group));

		log.info("User {} added {} to organisation {} as {}", actor.getId(), invitee.getId(), accountId, request.role());

		return MemberResponse.from(membership);
	}

	@Transactional(readOnly = true)
	public OrganisationStructureResponse structure(Jwt jwt, UUID accountId) {
		User user = currentUserResolver.require(jwt);
		requireOrganisation(accountId);
		requireMembership(accountId, user);

		List<DepartmentResponse> departments = departmentRepository.findByAccount_Id(accountId).stream()
			.map(DepartmentResponse::from)
			.toList();
		List<GroupResponse> groups = groupRepository.findByAccount_Id(accountId).stream()
			.map(GroupResponse::from)
			.toList();

		return new OrganisationStructureResponse(departments, groups);
	}

	@Transactional(readOnly = true)
	public List<MemberResponse> members(Jwt jwt, UUID accountId) {
		User user = currentUserResolver.require(jwt);
		requireOrganisation(accountId);
		requireMembership(accountId, user);

		return accountMemberRepository.findByAccount_Id(accountId).stream()
			.map(MemberResponse::from)
			.toList();
	}

	/** A member's own choice to share their personal Flow % with the rest of their group. Nobody else can make this call for them. */
	@Transactional
	public MemberResponse updateMemberSharing(Jwt jwt, UUID accountId, UpdateSharingRequest request) {
		User user = currentUserResolver.require(jwt);
		requireOrganisation(accountId);
		AccountMember membership = requireMembership(accountId, user);

		membership.updateSharePreference(request.shareFlowWithPeers());
		log.info("User {} set share-with-peers={} in organisation {}", user.getId(), request.shareFlowWithPeers(), accountId);

		return MemberResponse.from(membership);
	}

	/** Only the group's own manager (a COACH scoped to that exact group) can set this. */
	@Transactional
	public GroupResponse updateGroupSharing(Jwt jwt, UUID accountId, UUID groupId, UpdateSharingRequest request) {
		User user = currentUserResolver.require(jwt);
		requireOrganisation(accountId);
		AccountMember membership = requireMembership(accountId, user);

		Group group = groupRepository.findByIdAndAccount_Id(groupId, accountId)
			.orElseThrow(() -> new ResourceNotFoundException("No such group: " + groupId));
		boolean managesThisGroup = membership.getRole() == MemberRole.COACH
			&& membership.getGroup() != null && groupId.equals(membership.getGroup().getId());
		if (!managesThisGroup) {
			throw new AccessDeniedException("User %s does not manage group %s".formatted(user.getId(), groupId));
		}

		group.updateSharePreference(request.shareFlowWithPeers());
		log.info("User {} set share-with-peers={} for group {}", user.getId(), request.shareFlowWithPeers(), groupId);

		return GroupResponse.from(group);
	}

	/** Only the department's own manager (an ADMIN scoped to that exact department) can set this. */
	@Transactional
	public DepartmentResponse updateDepartmentSharing(Jwt jwt, UUID accountId, UUID departmentId, UpdateSharingRequest request) {
		User user = currentUserResolver.require(jwt);
		requireOrganisation(accountId);
		AccountMember membership = requireMembership(accountId, user);

		Department department = departmentRepository.findByIdAndAccount_Id(departmentId, accountId)
			.orElseThrow(() -> new ResourceNotFoundException("No such department: " + departmentId));
		boolean managesThisDepartment = membership.getRole() == MemberRole.ADMIN
			&& membership.getDepartment() != null && departmentId.equals(membership.getDepartment().getId());
		if (!managesThisDepartment) {
			throw new AccessDeniedException("User %s does not manage department %s".formatted(user.getId(), departmentId));
		}

		department.updateSharePreference(request.shareFlowWithPeers());
		log.info("User {} set share-with-peers={} for department {}", user.getId(), request.shareFlowWithPeers(), departmentId);

		return DepartmentResponse.from(department);
	}

	private Account requireOrganisation(UUID accountId) {
		Account account = accountRepository.findById(accountId)
			.orElseThrow(() -> new ResourceNotFoundException("No such account: " + accountId));
		if (account.getType() != AccountType.ORGANISATION) {
			throw new ValidationException("Account %s is not an Organisation".formatted(accountId));
		}
		return account;
	}

	private AccountMember requireMembership(UUID accountId, User user) {
		return accountMemberRepository.findByAccount_IdAndUser(accountId, user)
			.orElseThrow(() -> new AccessDeniedException(
				"User %s is not a member of account %s".formatted(user.getId(), accountId)));
	}

	private AccountMember requireRole(UUID accountId, User user, MemberRole... allowed) {
		AccountMember membership = requireMembership(accountId, user);
		if (Arrays.stream(allowed).noneMatch(role -> role == membership.getRole())) {
			throw new AccessDeniedException(
				"User %s's role in account %s does not permit this action".formatted(user.getId(), accountId));
		}
		return membership;
	}

	private Department resolveDepartment(UUID accountId, UUID departmentId) {
		if (departmentId == null) {
			return null;
		}
		return departmentRepository.findByIdAndAccount_Id(departmentId, accountId)
			.orElseThrow(() -> new ValidationException(
				"Department %s does not belong to organisation %s".formatted(departmentId, accountId)));
	}

	private Group resolveGroup(UUID accountId, UUID groupId, Department department) {
		if (groupId == null) {
			return null;
		}
		Group group = groupRepository.findByIdAndAccount_Id(groupId, accountId)
			.orElseThrow(() -> new ValidationException(
				"Group %s does not belong to organisation %s".formatted(groupId, accountId)));
		if (department != null && (group.getDepartment() == null || !group.getDepartment().getId().equals(department.getId()))) {
			throw new ValidationException("Group %s is not under department %s".formatted(groupId, department.getId()));
		}
		return group;
	}

}
