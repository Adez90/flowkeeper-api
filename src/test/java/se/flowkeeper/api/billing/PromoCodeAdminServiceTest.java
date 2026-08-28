package se.flowkeeper.api.billing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.common.ResourceNotFoundException;
import se.flowkeeper.api.common.ValidationException;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoCodeAdminServiceTest {

	@Mock PromoCodeRepository promoCodeRepository;
	@Mock CurrentUserResolver currentUserResolver;

	private final User adminUser = new User("kc-admin-1", "Anders Johansson", "admin@flowkeeper.se");
	private final User regularUser = new User("kc-user-1", "Someone Else", "someone@example.com");
	private final Jwt jwt = Jwt.withTokenValue("t").header("alg", "none")
		.subject("kc-admin-1").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

	private PromoCodeAdminService service() {
		return new PromoCodeAdminService(promoCodeRepository, currentUserResolver, new PlatformAdmins("admin@flowkeeper.se"));
	}

	@Test
	void generateRejectsANonAdmin() {
		when(currentUserResolver.require(jwt)).thenReturn(regularUser);

		assertThatThrownBy(() -> service().generate(jwt, new GeneratePromoCodeRequest(90, 1, null, null)))
			.isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void generateRejectsAnExpiresAtInThePast() {
		when(currentUserResolver.require(jwt)).thenReturn(adminUser);

		assertThatThrownBy(() -> service().generate(jwt,
			new GeneratePromoCodeRequest(90, 1, Instant.now().minusSeconds(60), null)))
			.isInstanceOf(ValidationException.class);
	}

	@Test
	void generateCreatesAUniqueCodeStampedWithTheAdminsEmail() {
		when(currentUserResolver.require(jwt)).thenReturn(adminUser);
		when(promoCodeRepository.findByCode(any())).thenReturn(Optional.empty());
		when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		PromoCodeResponse response = service().generate(jwt, new GeneratePromoCodeRequest(90, 1, null, "Private trial"));

		assertThat(response.code()).matches("[A-Z0-9]{4}-[A-Z0-9]{4}");
		assertThat(response.durationDays()).isEqualTo(90);
		assertThat(response.maxRedemptions()).isEqualTo(1);
		assertThat(response.createdByEmail()).isEqualTo("admin@flowkeeper.se");
		assertThat(response.note()).isEqualTo("Private trial");

		ArgumentCaptor<PromoCode> captor = ArgumentCaptor.forClass(PromoCode.class);
		verify(promoCodeRepository).save(captor.capture());
		assertThat(captor.getValue().getCode()).isEqualTo(response.code());
	}

	@Test
	void generateRetriesOnACollisionUntilAFreeCodeIsFound() {
		when(currentUserResolver.require(jwt)).thenReturn(adminUser);
		PromoCode existing = new PromoCode("TAKEN", 30, 1, null, null, "admin@flowkeeper.se");
		when(promoCodeRepository.findByCode(any()))
			.thenReturn(Optional.of(existing))
			.thenReturn(Optional.empty());
		when(promoCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service().generate(jwt, new GeneratePromoCodeRequest(30, 10, null, "Company pilot"));

		verify(promoCodeRepository, org.mockito.Mockito.times(2)).findByCode(any());
	}

	@Test
	void listRejectsANonAdmin() {
		when(currentUserResolver.require(jwt)).thenReturn(regularUser);

		assertThatThrownBy(() -> service().list(jwt)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void listReturnsEveryCodeForAnAdmin() {
		PromoCode code = new PromoCode("ABCD-1234", 90, 1, null, "note", "admin@flowkeeper.se");
		when(currentUserResolver.require(jwt)).thenReturn(adminUser);
		when(promoCodeRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(code));

		List<PromoCodeResponse> codes = service().list(jwt);

		assertThat(codes).hasSize(1);
		assertThat(codes.get(0).code()).isEqualTo("ABCD-1234");
	}

	@Test
	void revokeRejectsANonAdmin() {
		when(currentUserResolver.require(jwt)).thenReturn(regularUser);

		assertThatThrownBy(() -> service().revoke(jwt, UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void revokeRejectsAnUnknownCode() {
		when(currentUserResolver.require(jwt)).thenReturn(adminUser);
		when(promoCodeRepository.findById(any())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service().revoke(jwt, UUID.randomUUID())).isInstanceOf(ResourceNotFoundException.class);
	}

	@Test
	void revokeMarksTheCodeRevoked() {
		PromoCode code = new PromoCode("ABCD-1234", 90, 1, null, null, "admin@flowkeeper.se");
		UUID id = UUID.randomUUID();
		when(currentUserResolver.require(jwt)).thenReturn(adminUser);
		when(promoCodeRepository.findById(id)).thenReturn(Optional.of(code));

		service().revoke(jwt, id);

		assertThat(code.getRevokedAt()).isNotNull();
		verify(promoCodeRepository, never()).save(any());
	}

}
