package se.flowkeeper.api.integrations;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The OAuth redirect target — public (see SecurityConfig), since the
 * provider redirects the user's browser here with no bearer token. The
 * state param (issued by IntegrationsService#startAuthorization) is what
 * ties this back to a real user/account, not anything else in the request.
 */
@RestController
public class IntegrationsCallbackController {

	private final IntegrationsService integrationsService;

	public IntegrationsCallbackController(IntegrationsService integrationsService) {
		this.integrationsService = integrationsService;
	}

	@GetMapping("/api/v1/integrations/oauth/{provider}/callback")
	public ResponseEntity<Void> callback(
			@PathVariable ExternalProvider provider,
			@RequestParam(required = false) String code,
			@RequestParam(required = false) String state) {
		String redirectTo = integrationsService.handleCallback(provider, code, state);
		return ResponseEntity.status(HttpStatus.FOUND)
			.headers(h -> h.add(HttpHeaders.LOCATION, redirectTo))
			.build();
	}

}
