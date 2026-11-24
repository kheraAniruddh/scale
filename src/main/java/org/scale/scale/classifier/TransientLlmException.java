package org.scale.scale.classifier;

/** A failed LLM call that may succeed if retried. */
public class TransientLlmException extends RuntimeException {

    public TransientLlmException(String message) {
        super(message);
    }

    public TransientLlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
