package se.flowkeeper.api.organisation;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organisations")
public class OrganisationController {

	private final OrganisationService organisationService;

	public OrganisationController(OrganisationService organisationService) {
		this.organisationService = organisationService;
	}

	/** Creates a new Organisation account; the caller becomes its OWNER. */
	@PostMapping
	public ResponseEntity<OrganisationResponse> create(
			@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateOrganisationRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.createOrganisation(jwt, request));
	}

	@PostMapping("/{accountId}/departments")
	public ResponseEntity<DepartmentResponse> createDepartment(
			@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @Valid @RequestBody CreateDepartmentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.createDepartment(jwt, accountId, request));
	}

	/** Omit departmentId for a Group directly under the Organisation. */
	@PostMapping("/{accountId}/groups")
	public ResponseEntity<GroupResponse> createGroup(
			@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @Valid @RequestBody CreateGroupRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.createGroup(jwt, accountId, request));
	}

	/**
	 * Adds an existing FlowKeeper user (found by email) to the organisation.
	 * See AddMemberRequest for why this isn't a full pending-invite flow yet.
	 */
	@PostMapping("/{accountId}/members")
	public ResponseEntity<MemberResponse> addMember(
			@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId, @Valid @RequestBody AddMemberRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(organisationService.addMember(jwt, accountId, request));
	}

	/** The full Department/Group tree — what an org-management screen renders. */
	@GetMapping("/{accountId}/structure")
	public OrganisationStructureResponse structure(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
		return organisationService.structure(jwt, accountId);
	}

	@GetMapping("/{accountId}/members")
	public List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID accountId) {
		return organisationService.members(jwt, accountId);
	}

}
