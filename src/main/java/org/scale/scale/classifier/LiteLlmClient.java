package org.scale.scale.classifier;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** {@link LlmClient} for an OpenAI-compatible chat-completions endpoint (the litellm proxy). */
public class LiteLlmClient implements LlmClient {

    private final RestClient restClient;
    private final LlmProperties properties;

    public LiteLlmClient(RestClient restClient, LlmProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    @Override
    public String complete(String prompt) {
        ChatRequest request = new ChatRequest(properties.model(), List.of(new ChatMessage("user", prompt)), 0.0);
        ChatResponse response;
        try {
            response = restClient.post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (isTransient(status)) {
                throw new TransientLlmException("LLM returned status " + status, e);
            }
            throw new LlmRejectedException(status, e);
        } catch (ResourceAccessException e) {
            throw new TransientLlmException("LLM call failed: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new TransientLlmException("LLM reply could not be read: " + e.getMessage(), e);
        }
        return contentOf(response);
    }

    private static boolean isTransient(int status) {
        return status == 408 || status == 429 || status >= 500;
    }

    private static String contentOf(ChatResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new TransientLlmException("LLM reply has no choices");
        }
        ChatMessage message = response.choices().getFirst().message();
        if (message == null || message.content() == null) {
            throw new TransientLlmException("LLM reply has no message content");
        }
        return message.content();
    }

    public record ChatRequest(String model, List<ChatMessage> messages, double temperature) {
    }

    public record ChatMessage(String role, String content) {
    }

    public record ChatResponse(List<Choice> choices) {
    }

    public record Choice(ChatMessage message) {
    }
}
