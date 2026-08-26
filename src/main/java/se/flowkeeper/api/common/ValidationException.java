package se.flowkeeper.api.common;

/** For validation that can't be expressed declaratively (e.g. Bean Validation). */
public class ValidationException extends RuntimeException {

	public ValidationException(String message) {
		super(message);
	}

}
