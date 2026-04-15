package com.coderhino.query;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

public final class ModelClientFactory {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private ModelClientFactory() {
    }

    public static ModelClient create(String model) {
        return create(model, System.getenv("ANTHROPIC_API_KEY"), Optional.ofNullable(System.getenv("ANTHROPIC_BASE_URL")).orElse("https://api.anthropic.com"));
    }

    public static ModelClient create(String model, String apiKey, String baseUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            return new LocalEchoModelClient();
        }

        return new AgentModelClient(
            HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build(),
            new ObjectMapper(),
            baseUrl,
            apiKey,
            model
        );
    }
}
