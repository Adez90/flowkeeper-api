package se.flowkeeper.api.integrations;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.flowkeeper.api.event.EventResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class IntegrationsController {

	private final IntegrationsService integrationsService;

	public IntegrationsController(IntegrationsService integrationsService) {
		this.integrationsService = integrationsService;
	}

	/** Which providers are actually usable right now — the client only shows a Connect button for available=true entries. */
	@GetMapping("/api/v1/integrations/providers")
	public List<ProviderResponse> listProviders() {
		return integrationsService.listProviders();
	}

	/** The caller's own connections for this account. */
	@GetMapping("/api/v1/integrations/connections")
	public List<ConnectionResponse> listConnections(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID accountId) {
		return integrationsService.listConnections(jwt, accountId);
	}

	@PostMapping("/api/v1/integrations/connections/{provider}/authorize")
	public AuthorizationUrlResponse authorize(@AuthenticationPrincipal Jwt jwt, @PathVariable ExternalProvider provider,
			@Valid @RequestBody StartAuthorizationRequest request) {
		return integrationsService.startAuthorization(jwt, provider, request);
	}

	@DeleteMapping("/api/v1/integrations/connections/{connectionId}")
	public ResponseEntity<Void> disconnect(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID connectionId) {
		integrationsService.disconnect(jwt, connectionId);
		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	/** What's importable from every connected provider for one day (defaults to today, in the caller's own timezone) — grouped by provider, already-imported items excluded. */
	@GetMapping("/api/v1/integrations/importable")
	public List<ImportableGroupResponse> listImportable(
			@AuthenticationPrincipal Jwt jwt,
			@RequestParam UUID accountId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return integrationsService.listImportableItems(jwt, accountId, date);
	}

	@PostMapping("/api/v1/integrations/import")
	public List<EventResponse> importEvents(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportEventsRequest request) {
		return integrationsService.importEvents(jwt, request);
	}

}
