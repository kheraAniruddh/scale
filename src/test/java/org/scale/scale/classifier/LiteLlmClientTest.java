package org.scale.scale.classifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class LiteLlmClientTest {

    private static final String URL = "https://llm.test/v1/chat/completions";
    private static final LlmProperties PROPERTIES =
            new LlmProperties(URI.create(URL), "openai/gpt-4o", "test-key", Duration.ofSeconds(5));

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final LiteLlmClient client = new LiteLlmClient(builder.build(), PROPERTIES);

    @Test
    void sendsChatCompletionAndReturnsContent() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().json("""
                        {"model":"openai/gpt-4o","messages":[{"role":"user","content":"Hello!"}],"temperature":0.0}
                        """, JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"id":"x","choices":[{"index":0,"message":{"role":"assistant","content":"Legal"},"finish_reason":"stop"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.complete("Hello!")).isEqualTo("Legal");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void retryableStatusIsTransient(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> client.complete("p")).isInstanceOf(TransientLlmException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    void otherClientErrorIsRejectedWithSameStatus(int status) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)).body("{\"error\":\"secret details\"}"));

        assertThatThrownBy(() -> client.complete("p"))
                .isInstanceOf(LlmRejectedException.class)
                .hasMessage("LLM request rejected with status " + status)
                .extracting(e -> ((LlmRejectedException) e).statusCode()).isEqualTo(status);
    }

    @Test
    void timeoutIsTransient() {
        server.expect(requestTo(URL)).andRespond(withException(new SocketTimeoutException("read timed out")));

        assertThatThrownBy(() -> client.complete("p")).isInstanceOf(TransientLlmException.class);
    }

    @Test
    void connectionFailureIsTransient() {
        server.expect(requestTo(URL)).andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.complete("p")).isInstanceOf(TransientLlmException.class);
    }

    @Test
    void unreadableBodyIsTransient() {
        server.expect(requestTo(URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("p")).isInstanceOf(TransientLlmException.class);
    }

    @Test
    void emptyChoicesIsTransient() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("p")).isInstanceOf(TransientLlmException.class);
    }

    @Test
    void missingContentIsTransient() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\"}}]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("p")).isInstanceOf(TransientLlmException.class);
    }

    @Test
    void apiKeyIsNotInToString() {
        assertThat(PROPERTIES.toString()).doesNotContain("test-key");
    }
}
