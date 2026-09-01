package se.flowkeeper.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsOversizedUploadToA413WithAClearReason() {
		ResponseEntity<ApiError> response = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(15_000_000));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().message()).isEqualTo("File is too large — please choose a smaller image");
	}

}
