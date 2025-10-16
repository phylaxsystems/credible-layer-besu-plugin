package net.phylax.credible.types;

public enum CredibleRejectionReason {
    // Credible Layer timed out
    TIMEOUT,
    // Credible Layer didn't return any result
    NO_RESULT,
    // Error while processing the transaction
    ERROR
}
