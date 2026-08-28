package se.flowkeeper.api.billing;

import jakarta.validation.Valid;
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

/** Platform-admin only (see PlatformAdmins) — not scoped to any one account, so every method resolves the caller itself. */
@RestController
@RequestMapping("/api/v1/admin/promo-codes")
public class PromoCodeAdminController {

	private final PromoCodeAdminService promoCodeAdminService;

	public PromoCodeAdminController(PromoCodeAdminService promoCodeAdminService) {
		this.promoCodeAdminService = promoCodeAdminService;
	}

	@PostMapping
	public PromoCodeResponse generate(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody GeneratePromoCodeRequest request) {
		return promoCodeAdminService.generate(jwt, request);
	}

	@GetMapping
	public List<PromoCodeResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return promoCodeAdminService.list(jwt);
	}

	@PostMapping("/{promoCodeId}/revoke")
	public void revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID promoCodeId) {
		promoCodeAdminService.revoke(jwt, promoCodeId);
	}

}
