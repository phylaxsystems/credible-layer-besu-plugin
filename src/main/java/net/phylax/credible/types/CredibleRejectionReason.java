package net.phylax.credible.types;

public enum CredibleRejectionReason {
    // Timeout hit on processing a transaction
    PROCESSING_TIMEOUT,
    // Aggregated timeout
    AGGREGATED_TIMEOUT,
    // Credible Layer didn't return any result
    NO_RESULT,
    // There is not active transport
    NO_ACTIVE_TRANSPORT,
    // Error while processing the transaction
    ERROR
}
