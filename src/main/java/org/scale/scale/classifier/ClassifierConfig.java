package org.scale.scale.classifier;

import java.net.http.HttpClient;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ClassifierConfig {

    @Bean
    public LlmClient llmClient(RestClient.Builder builder, LlmProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        return new LiteLlmClient(builder.requestFactory(requestFactory).build(), properties);
    }

    @Bean
    public Sleeper sleeper() {
        return Sleeper.threadSleeper();
    }
}
