package se.flowkeeper.api.diagnostics;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DiagnosticsController {

	private final DiagnosticsService diagnosticsService;

	public DiagnosticsController(DiagnosticsService diagnosticsService) {
		this.diagnosticsService = diagnosticsService;
	}

	/** Platform-admin only (see PlatformAdmins) — anyone else gets a 403 before the deliberate throw is ever reached. */
	@PostMapping("/api/v1/diagnostics/test-error")
	public void triggerTestError(@AuthenticationPrincipal Jwt jwt) {
		diagnosticsService.triggerTestError(jwt);
	}

}
