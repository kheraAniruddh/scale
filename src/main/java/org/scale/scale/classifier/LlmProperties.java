package org.scale.scale.classifier;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param url     chat-completions endpoint
 * @param model   model name sent in each request
 * @param apiKey  bearer token; read from {@code API_SECRET_KEY} in {@code .env}
 * @param timeout connect and read timeout per call
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(URI url, String model, String apiKey, Duration timeout) {

    public LlmProperties {
        if (url == null || model == null || model.isBlank() || timeout == null) {
            throw new IllegalArgumentException("llm.url, llm.model and llm.timeout are required");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("llm.api-key is required: set API_SECRET_KEY in .env or the environment");
        }
    }

    @Override
    public String toString() {
        return "LlmProperties[url=" + url + ", model=" + model + ", apiKey=***, timeout=" + timeout + "]";
    }
}
