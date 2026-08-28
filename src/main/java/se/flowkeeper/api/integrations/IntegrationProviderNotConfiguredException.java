package se.flowkeeper.api.integrations;

/** No client id/secret set yet (or, for Apple, no gateway exists at all) — see application.yml's app.integrations block. */
public class IntegrationProviderNotConfiguredException extends RuntimeException {

	public IntegrationProviderNotConfiguredException(String message) {
		super(message);
	}

}
