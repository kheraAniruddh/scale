package org.scale.scale.classifier;

/** Sends one prompt to a language model and returns its text reply. */
public interface LlmClient {

    /**
     * @throws TransientLlmException on failures worth retrying (timeout, 429, 5xx, unreadable reply)
     * @throws LlmRejectedException  when the LLM service rejects the request outright (e.g. 400, 401)
     */
    String complete(String prompt);
}
