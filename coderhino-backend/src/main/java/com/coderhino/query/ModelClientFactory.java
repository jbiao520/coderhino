package com.coderhino.query;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

public final class ModelClientFactory {
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final long DEFAULT_CONTEXT_WINDOW = 128000L;

    private ModelClientFactory() {
    }

    public static ModelClient create(String model) {
        return create(
            model,
            System.getenv("ANTHROPIC_API_KEY"),
            Optional.ofNullable(System.getenv("ANTHROPIC_BASE_URL")).orElse("https://api.anthropic.com"),
            ProviderApiType.CLAUDE_CODE,
            DEFAULT_CONTEXT_WINDOW
        );
    }

    public static ModelClient create(String model, String apiKey, String baseUrl) {
        return create(model, apiKey, baseUrl, ProviderApiType.CLAUDE_CODE, DEFAULT_CONTEXT_WINDOW);
    }

    public static ModelClient create(String model, String apiKey, String baseUrl, ProviderApiType apiType) {
        return create(model, apiKey, baseUrl, apiType, DEFAULT_CONTEXT_WINDOW);
    }

    public static ModelClient create(String model, String apiKey, String baseUrl, ProviderApiType apiType, long contextWindow) {
        if (apiKey == null || apiKey.isBlank()) {
            return new LocalEchoModelClient();
        }

        return new AgentModelClient(
            HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build(),
            new ObjectMapper(),
            baseUrl,
            apiKey,
            model,
            apiType,
            contextWindow
        );
    }
}
