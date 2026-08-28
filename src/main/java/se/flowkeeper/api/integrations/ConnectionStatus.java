package se.flowkeeper.api.integrations;

public enum ConnectionStatus {
	CONNECTED,
	/** The provider rejected a token refresh or API call — see lastError. Reconnecting (re-authorizing) clears this. */
	ERROR,
	DISCONNECTED
}
