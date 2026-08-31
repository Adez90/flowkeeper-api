package se.flowkeeper.api.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequiredConfigValidatorTest {

	@Test
	void startsCleanlyWhenEveryRequiredValueIsSet() {
		var validator = new RequiredConfigValidator(
			"https://staging.flowkeeper.se",
			"https://api.staging.flowkeeper.se",
			"https://auth.staging.flowkeeper.se/realms/flowkeeper");

		assertThatCode(validator::validate).doesNotThrowAnyException();
	}

	@Test
	void refusesToStartWhenAppOriginIsBlank() {
		var validator = new RequiredConfigValidator(
			"", "https://api.staging.flowkeeper.se", "https://auth.staging.flowkeeper.se/realms/flowkeeper");

		assertThatThrownBy(validator::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("APP_ORIGIN");
	}

	@Test
	void refusesToStartWhenMultipleValuesAreBlank() {
		var validator = new RequiredConfigValidator("", "", "https://auth.staging.flowkeeper.se/realms/flowkeeper");

		assertThatThrownBy(validator::validate)
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("APP_ORIGIN")
			.hasMessageContaining("API_ORIGIN");
	}

}
