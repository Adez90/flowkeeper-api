package se.flowkeeper.api.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import se.flowkeeper.api.billing.PaymentProviderNotConfiguredException;
import se.flowkeeper.api.integrations.IntegrationProviderNotConfiguredException;

/**
 * Maps the domain's own exceptions to a consistent JSON error body. Genuine
 * 5xx failures are left to Spring Boot's default handling — logging them
 * here too would just duplicate what it already does.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex) {
		log.debug("Resource not found: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
			.body(ApiError.of(ex.getMessage(), HttpStatus.NOT_FOUND.value()));
	}

	@ExceptionHandler(ConflictException.class)
	public ResponseEntity<ApiError> handleConflict(ConflictException ex) {
		log.debug("Conflict: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.CONFLICT)
			.body(ApiError.of(ex.getMessage(), HttpStatus.CONFLICT.value()));
	}

	@ExceptionHandler(ValidationException.class)
	public ResponseEntity<ApiError> handleValidation(ValidationException ex) {
		log.debug("Validation failed: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
			.body(ApiError.of(ex.getMessage(), HttpStatus.BAD_REQUEST.value()));
	}

	/**
	 * Without this, an oversized upload (e.g. a full-resolution phone photo
	 * with no client-side resize) surfaces as Spring Boot's default,
	 * generic 500 — indistinguishable to the caller from a real server
	 * failure. This turns it into a specific, actionable 413 instead.
	 */
	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ResponseEntity<ApiError> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
		log.debug("Upload exceeded the configured size limit: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
			.body(ApiError.of("File is too large — please choose a smaller image", HttpStatus.PAYLOAD_TOO_LARGE.value()));
	}

	@ExceptionHandler(PaymentProviderNotConfiguredException.class)
	public ResponseEntity<ApiError> handlePaymentProviderNotConfigured(PaymentProviderNotConfiguredException ex) {
		log.warn("Payment provider not usable: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(ApiError.of(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE.value()));
	}

	@ExceptionHandler(IntegrationProviderNotConfiguredException.class)
	public ResponseEntity<ApiError> handleIntegrationProviderNotConfigured(IntegrationProviderNotConfiguredException ex) {
		log.warn("Integration provider not usable: {}", ex.getMessage());
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
			.body(ApiError.of(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE.value()));
	}

}
