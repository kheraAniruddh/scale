package org.scale.scale.classifier;

/** The LLM service refused the request with a non-retryable HTTP status. */
public class LlmRejectedException extends RuntimeException {

    private final int statusCode;

    public LlmRejectedException(int statusCode, Throwable cause) {
        super("LLM request rejected with status " + statusCode, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
