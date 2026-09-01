package se.flowkeeper.api.diagnostics;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import se.flowkeeper.api.billing.PlatformAdmins;
import se.flowkeeper.api.user.CurrentUserResolver;
import se.flowkeeper.api.user.User;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiagnosticsServiceTest {

	private final CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
	private final PlatformAdmins platformAdmins = mock(PlatformAdmins.class);
	private final DiagnosticsService service = new DiagnosticsService(currentUserResolver, platformAdmins);

	@Test
	void aPlatformAdminGetsTheDeliberateExceptionNamingThem() {
		Jwt jwt = mock(Jwt.class);
		User user = mock(User.class);
		when(user.getEmail()).thenReturn("anders@up2u.se");
		when(currentUserResolver.require(jwt)).thenReturn(user);

		assertThatThrownBy(() -> service.triggerTestError(jwt))
			.isInstanceOf(DeliberateTestException.class)
			.hasMessageContaining("anders@up2u.se");
	}

	@Test
	void anyoneWhoIsNotAPlatformAdminIsDeniedBeforeTheThrow() {
		Jwt jwt = mock(Jwt.class);
		User user = mock(User.class);
		when(currentUserResolver.require(jwt)).thenReturn(user);
		doThrow(new AccessDeniedException("not an admin")).when(platformAdmins).requireAdmin(user);

		assertThatThrownBy(() -> service.triggerTestError(jwt)).isInstanceOf(AccessDeniedException.class);
	}

	@Test
	void checksAdminStatusForTheUserResolvedFromTheJwt() {
		Jwt jwt = mock(Jwt.class);
		User user = mock(User.class);
		when(currentUserResolver.require(jwt)).thenReturn(user);

		assertThatThrownBy(() -> service.triggerTestError(jwt)).isInstanceOf(DeliberateTestException.class);

		verify(platformAdmins).requireAdmin(user);
	}

}
